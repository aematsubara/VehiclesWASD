package me.matsubara.vehicles.model;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.ModelLocation;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

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

    // Set with all stands associated with a name.
    private final List<PacketStand> stands = new ArrayList<>();

    // Set with all locations associated with a name, mostly used to spawn particles.
    private final List<ModelLocation> locations = new ArrayList<>();

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private final Set<UUID> ignored = new ObjectOpenHashSet<>();

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
        PacketStand chair = getStandByName("CHAIR_1");
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
        return getStandByName(name) != null || name.contains(".") || name.contains(" ");
    }

    public void addNew(String name, StandSettings settings, @Nullable Location copyLocation, @Nullable Float yaw) {
        if (isInvalidName(name)) return;

        Location finalLocation = copyLocation != null ? copyLocation : location;
        if (yaw != null) finalLocation.setYaw(finalLocation.getYaw() + yaw);

        // Save the name of the current part.
        settings.setPartName(name);

        // We only require the location of this armor stand.
        if (settings.getTags().contains("LOCATION")) {
            locations.add(new ModelLocation(settings, finalLocation));
            return;
        }

        // Spawn model but don't show it to anyone, we want to apply customizations first.
        stands.add(new PacketStand(this, finalLocation, settings));
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
                settings.getEquipment().put(slot.getSlot(), loadEquipment(path, slot.getPath()));
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
            item = SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(material));
        }

        if (item != null && item.getType() == ItemTypes.PLAYER_HEAD && configuration.get(defaultPath + ".url") != null) {
            item = SpigotConversionUtil.fromBukkitItemStack(PluginUtils.createHead(configuration.getString(defaultPath + ".url"), true));
        }

        return item;
    }

    private @NotNull Vector3f loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new Vector3f(
                    (float) Math.toRadians(x),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(z));
        }

        return Vector3f.zero();
    }

    public @Nullable PacketStand getStandByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }

    public @Nullable PacketStand getStandById(int id) {
        for (PacketStand stand : stands) {
            if (stand.getId() == id) return stand;
        }
        return null;
    }

    public @Nullable ModelLocation getLocationByName(String name) {
        for (ModelLocation location : locations) {
            String partName = location.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return location;
        }
        return null;
    }
}