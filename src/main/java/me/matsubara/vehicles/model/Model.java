package me.matsubara.vehicles.model;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public final class Model {

    // Instance of the plugin.
    private final VehiclesPlugin plugin;

    // The UUID of this model.
    private final UUID modelUniqueId;

    // Name of the model.
    private final String name;

    // Center point of the model.
    private Location location;

    // List with all stands associated with a name.
    private final List<PacketStand> stands = new ArrayList<>();

    // File and configuration.
    private File file;
    private FileConfiguration configuration;

    public Model(VehiclesPlugin plugin, String name, @Nullable UUID oldUniqueId, Location location) {
        this.plugin = plugin;
        this.modelUniqueId = (oldUniqueId != null) ? oldUniqueId : UUID.randomUUID();
        this.name = name;
        this.location = location;

        loadFile();
        loadModel();

        // After loading the model, we want to add another stand which will be used for interactions.
        // The INTERACTIONS armor stand will be on the same location of the main chair.
        spawnInteractions();
    }

    private void spawnInteractions() {
        PacketStand chair = getByName("CHAIR_1");
        if (chair == null) return;

        StandSettings settings = new StandSettings();
        settings.setInvisible(true);
        settings.setExtraYaw(0.0f);

        Vector offset = new Vector(0.0d, chair.getSettings().getOffset().getY() + 0.49375d, 0.0d);
        settings.setOffset(offset);

        Location at = location.clone().add(PluginUtils.offsetVector(offset, location.getYaw(), location.getPitch()));
        addNew("INTERACTIONS", settings, at, null);
    }

    private void loadFile() {
        Map<String, Pair<File, FileConfiguration>> models = plugin.getVehicleManager().getModels();

        Pair<File, FileConfiguration> pair = models.computeIfAbsent(name + ".yml", name -> {
            File temp = new File(plugin.getModelFolder(), name);
            return Pair.of(temp, YamlConfiguration.loadConfiguration(temp));
        });

        file = pair.getKey();
        configuration = pair.getValue();
    }

    public boolean isInvalidName(String name) {
        return getByName(name) != null || name.contains(".") || name.contains(" ");
    }

    public void addNew(String name, StandSettings settings, @Nullable Location copyLocation, @Nullable Float yaw) {
        if (isInvalidName(name)) return;

        Location finalLocation = copyLocation != null ? copyLocation : location;
        if (yaw != null) finalLocation.setYaw(finalLocation.getYaw() + yaw);

        // Save the name of the current part.
        settings.setPartName(name);

        // Spawn model but don't show it to anyone, we want to apply customizations first.
        stands.add(new PacketStand(finalLocation, settings, false));
    }

    public void kill() {
        stands.forEach(PacketStand::destroy);
        stands.clear();
    }

    private void loadModel() {
        ConfigurationSection section = configuration.getConfigurationSection("parts");
        if (section == null) return;

        List<String> keys = new ArrayList<>(section.getKeys(false));
        for (String path : keys) {
            String defaultPath = "parts." + path + ".";

            // Load offsets.
            Vector offset = new Vector(
                    configuration.getDouble(defaultPath + "offset.x"),
                    configuration.getDouble(defaultPath + "offset.y"),
                    configuration.getDouble(defaultPath + "offset.z"));

            // Pitch isn't necessary.
            float yaw = (float) configuration.getDouble(defaultPath + "offset.yaw");

            Location location = this.location.clone().add(PluginUtils.offsetVector(offset, this.location.getYaw(), this.location.getPitch()));

            StandSettings settings = new StandSettings();
            settings.setOffset(offset);
            settings.setExtraYaw(yaw);

            String settingPath = defaultPath + "settings.";

            // Set settings.
            settings.setInvisible(configuration.getBoolean(settingPath + "invisible"));
            settings.setSmall(configuration.getBoolean(settingPath + "small"));
            settings.setBasePlate(configuration.getBoolean(settingPath + "baseplate", true)); // Armor stands have baseplate by default.
            settings.setArms(configuration.getBoolean(settingPath + "arms"));
            settings.setFire(configuration.getBoolean(settingPath + "fire"));
            settings.setMarker(configuration.getBoolean(settingPath + "marker"));

            // Set poses.
            settings.setHeadPose(loadAngle(path, "head"));
            settings.setBodyPose(loadAngle(path, "body"));
            settings.setLeftArmPose(loadAngle(path, "left-arm"));
            settings.setRightArmPose(loadAngle(path, "right-arm"));
            settings.setLeftLegPose(loadAngle(path, "left-leg"));
            settings.setRightLegPose(loadAngle(path, "right-leg"));

            // Set equipment.
            for (PacketStand.ItemSlot slot : PacketStand.ItemSlot.values()) {
                settings.getEquipment().put(slot, loadEquipment(path, slot.getConfigPathName()));
            }

            settings.getTags().addAll(configuration.getStringList("parts." + path + ".tags"));

            addNew(path, settings, location, yaw);
        }
    }

    private @Nullable ItemStack loadEquipment(String path, String equipment) {
        String defaultPath = "parts." + path + ".equipment." + equipment;
        if (configuration.get(defaultPath) == null) return null;

        ItemStack item = null;
        if (configuration.get(defaultPath + ".material") != null) {
            Material material = PluginUtils.getOrDefault(Material.class, configuration.getString(defaultPath + ".material", "STONE"), Material.STONE);
            item = new ItemStack(material);
        }

        if (item != null && item.getType() == Material.PLAYER_HEAD && configuration.get(defaultPath + ".url") != null) {
            item = PluginUtils.createHead(configuration.getString(defaultPath + ".url"), true);
        }

        return item;
    }

    private EulerAngle loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
        }

        return EulerAngle.ZERO;
    }

    public @Nullable PacketStand getByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }

    public @Nullable PacketStand getById(int id) {
        for (PacketStand stand : stands) {
            if (stand.getEntityId() == id) return stand;
        }
        return null;
    }
}