package me.matsubara.vehicles.vehicle;

import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public record VehicleData(
        UUID owner,
        Float fuel,
        boolean locked,
        UUID modelUniqueId,
        Location location,
        VehicleType type,
        String base64Storage,
        String shopDisplayName,
        Map<String, Material> customizationChanges,
        AtomicBoolean keepWorld) implements ConfigurationSerializable {

    public VehicleData(
            UUID owner,
            Float fuel,
            boolean locked,
            UUID modelUniqueId,
            Location location,
            VehicleType type,
            String base64Storage,
            String shopDisplayName,
            Map<String, Material> customizationChanges) {
        this(owner, fuel, locked, modelUniqueId, location, type, base64Storage, shopDisplayName, customizationChanges, new AtomicBoolean());
    }

    public static @NotNull VehicleData createDefault(UUID owner, @Nullable UUID modelUniqueId, Location location, VehicleType type) {
        return new VehicleData(owner, null, true, modelUniqueId, location, type, null, null, null);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();

        if (owner != null) result.put("owner", owner.toString());
        if (fuel != null) result.put("fuel", fuel);
        result.put("locked", locked);
        if (modelUniqueId != null) result.put("model-unique-id", modelUniqueId.toString());
        if (location != null) {
            if (keepWorld == null || !keepWorld.get()) {
                location.setWorld(null);
            }
            result.putAll(location.serialize());
        }
        result.put("type", type.name());
        if (base64Storage != null) result.put("storage", base64Storage);
        if (shopDisplayName != null) result.put("shop-display-name", shopDisplayName);
        result.put("customizations", customizationChanges.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue().name())
                .toList());

        return result;
    }

    @SuppressWarnings({"unused"})
    public static @NotNull VehicleData deserialize(@NotNull Map<String, Object> args) {
        UUID owner = getUUID(args, "owner");
        Float fuel = args.get("fuel") instanceof Float temp ? temp : null;
        boolean locked = (boolean) args.getOrDefault("locked", true);
        UUID modelUniqueId = getUUID(args, "model-unique-id");

        VehicleType type;
        if (!(args.get("type") instanceof String typeString) || (type = PluginUtils.getOrNull(VehicleType.class, typeString)) == null) {
            throw new IllegalArgumentException("Serialized data doesn't contain vehicle type!");
        }

        String base64Storage = args.get("storage") instanceof String storageString ? storageString : null;
        String shopDisplayName = args.get("shop-display-name") instanceof String shopDisplayNameString ? shopDisplayNameString : null;

        Map<String, Material> customizationChanges = new HashMap<>();
        if (args.get("customizations") instanceof List<?> list) {
            fillCustomizations(list, customizationChanges);
        }

        return new VehicleData(owner, fuel, locked, modelUniqueId, Location.deserialize(args), type, base64Storage, shopDisplayName, customizationChanges);
    }

    private static void fillCustomizations(@NotNull List<?> changes, Map<String, Material> saveTo) {
        for (Object change : changes) {
            if (!(change instanceof String string)) continue;

            String[] split = string.split(":");
            if (split.length != 2) continue;

            String name = split[0];
            Material newType = PluginUtils.getOrNull(Material.class, split[1]);

            saveTo.put(name, newType);
        }
    }

    private static @Nullable UUID getUUID(@NotNull Map<String, Object> args, String key) {
        if (!(args.get(key) instanceof String string)) return null;
        try {
            return UUID.fromString(string);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
