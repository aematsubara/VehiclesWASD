package me.matsubara.vehicles.model;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.*;
import me.matsubara.vehicles.model.stand.data.ItemSlot;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Stream;

@Getter
@Setter
public final class Model {

    // Instance of the plugin.
    private final VehiclesPlugin plugin;

    // The UUID of this model.
    private final UUID modelUniqueId;

    // Center point of the model.
    private Location location;

    // If this model is part of a preview. We want to use packet stands for previews.
    private final boolean isPreview;

    // Set with all stands associated with a name.
    private final List<IStand> stands = new ArrayList<>();

    // Set with all locations associated with a name, mostly used to spawn particles.
    private final List<ModelLocation> locations = new ArrayList<>();

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private final Set<UUID> out = new ObjectOpenHashSet<>();

    // Cached model parts.
    private static final Set<String> LOCATION_STAND = Set.of("CENTER");
    private static final Multimap<VehicleType, StandSettings> MODEL_CACHE = MultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build();

    // Configuration file.
    private FileConfiguration configuration;

    public Model(VehiclesPlugin plugin, @NotNull VehicleType type, @Nullable UUID oldUniqueId, Location location, boolean isPreview) {
        this.plugin = plugin;
        this.modelUniqueId = (oldUniqueId != null) ? oldUniqueId : UUID.randomUUID();
        this.location = location;
        this.isPreview = isPreview;
        handleModel(type);
        // After loading the model, we want to add another stand which will be used for interactions.
        // The INTERACTIONS armor stand will be on the same location of the main chair.
        spawnInteractions();
    }

    private void handleModel(VehicleType type) {
        Collection<StandSettings> settings = MODEL_CACHE.get(type);
        if (settings.isEmpty()) {
            loadFile(type);
            loadModel();
            MODEL_CACHE.putAll(type, Stream.concat(
                            stands.stream().map(IStand::getSettings),
                            locations.stream().map(ModelLocation::getSettings))
                    .map(StandSettings::clone)
                    .toList());
            return;
        }

        for (StandSettings setting : settings) {
            Location copy = location.clone().add(PluginUtils.offsetVector(setting.getOffset(), location.getYaw(), location.getPitch()));
            addNew(setting.getPartName(), setting.clone(), copy, setting.getExtraYaw());
        }
    }

    private void spawnInteractions() {
        ModelLocation chair = getLocationByName("CHAIR_1");
        if (chair == null) return;

        StandSettings settings = new StandSettings();
        settings.setInvisible(true);
        settings.setExtraYaw(0.0f);

        Vector offset = new Vector(0.0d, chair.getSettings().getOffset().getY() + 0.49375d, 0.0d);
        settings.setOffset(offset);

        Location at = location.clone().add(PluginUtils.offsetVector(offset, location.getYaw(), location.getPitch()));
        addNew("INTERACTIONS", settings, at, null);
    }

    private void loadFile(@NotNull VehicleType type) {
        configuration = plugin.getVehicleManager().getModels().computeIfAbsent(type.toPath() + ".yml", name -> {
            File file = new File(plugin.getModelFolder(), name);
            return YamlConfiguration.loadConfiguration(file);
        });
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
        if (settings.getTags().contains("LOCATION") || LOCATION_STAND.contains(name) || name.startsWith("CHAIR_")) {
            locations.add(new ModelLocation(settings, finalLocation));
            return;
        }

        // Spawn model but don't show it to anyone, we want to apply customizations first.
        stands.add(plugin.getStandManager().isBukkitArmorStand() && !isPreview ?
                new BukkitStand(plugin, finalLocation, settings) :
                new PacketStand(plugin, finalLocation, settings));
    }

    public void kill() {
        stands.forEach(IStand::destroy);
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
            for (ItemSlot slot : ItemSlot.values()) {
                settings.getEquipment().put(slot, loadEquipment(path, slot.getPath()));
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

    private @NotNull EulerAngle loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
        }

        return EulerAngle.ZERO;
    }

    public @Nullable IStand getStandByName(String name) {
        for (IStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }

    public @Nullable IStand getStandById(int id) {
        for (IStand stand : stands) {
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