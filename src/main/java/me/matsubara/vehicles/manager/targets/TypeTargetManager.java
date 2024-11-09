package me.matsubara.vehicles.manager.targets;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TypeTargetManager {

    private final VehiclesPlugin plugin;

    public TypeTargetManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull Set<TypeTarget> getTargetsFromConfig(String path) {
        return getTargets(path, plugin.getConfig().getStringList(path));
    }

    public @NotNull Set<TypeTarget> getTargets(String path, @NotNull List<String> list) {
        Set<TypeTarget> tags = new HashSet<>();
        for (String materialOrTag : list) {
            fillTargets(path, tags, materialOrTag);
        }
        return tags;
    }

    public @Nullable TypeTarget applies(@NotNull Set<TypeTarget> typeTargets, VehicleType targetType, @NotNull Material type) {
        for (TypeTarget typeTarget : typeTargets) {
            if (!typeTarget.is(type)) continue;
            if (targetType == null
                    || !(typeTarget instanceof TypeTarget.TargetWithCondition condition)
                    || condition.test(targetType)) return typeTarget;
        }
        return null;
    }

    public void fillTargets(@Nullable String path, Set<TypeTarget> tags, @NotNull String materialOrTag) {
        VehicleType type;
        if (materialOrTag.startsWith("?")) {
            String[] data = materialOrTag.substring(1).split(":");
            if (data.length != 2) {
                log(path, materialOrTag);
                return;
            }

            VehicleType temp = PluginUtils.getOrNull(VehicleType.class, data[0].toUpperCase(Locale.ROOT));
            if (temp != null) {
                type = temp;
            } else {
                log(path, materialOrTag);
                return;
            }
        } else {
            type = null;
        }

        int amount = 1;
        String amountString = StringUtils.substringBetween(materialOrTag, "(", ")");
        if (amountString != null) {
            materialOrTag = materialOrTag.replace("(" + amountString + ")", "");
            amount = amountString.equalsIgnoreCase("$RANDOM") ? -1 : PluginUtils.getRangedAmount(amountString);
        }

        int indexOf = type != null ? materialOrTag.indexOf(":") : -1;
        if (materialOrTag.startsWith("$") || (indexOf != -1 && materialOrTag.substring(indexOf + 1).startsWith("$"))) {
            String tagName = (indexOf != -1 ? materialOrTag.substring(indexOf + 2) : materialOrTag.substring(1));

            if (addMaterialsFromRegistry(
                    tags,
                    type,
                    tagName.toLowerCase(Locale.ROOT),
                    amount,
                    Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS)) return;

            Collection<Material> extra = plugin.getExtraTags().get(tagName.toLowerCase(Locale.ROOT));
            if (extra.isEmpty()) {
                log(path, materialOrTag);
                return;
            }

            for (Material material : extra) {
                addAndOverride(tags, createTarget(amount, material, tagName, type));
            }

            return;
        }

        if (materialOrTag.startsWith(";") || (indexOf != -1 && materialOrTag.substring(indexOf + 1).startsWith(";"))) {
            String regex = materialOrTag.substring(indexOf != -1 ? indexOf + 2 : 1);

            for (Material value : Material.values()) {
                if (value.name().matches(regex)) {
                    addAndOverride(tags, createTarget(amount, value, null, type));
                }
            }

            return;
        }

        String materialName = materialOrTag.substring(indexOf != -1 ? indexOf + 1 : 0);
        Material material = PluginUtils.getOrNull(Material.class, materialName.toUpperCase(Locale.ROOT));
        if (material != null) {
            addAndOverride(tags, createTarget(amount, material, null, type));
        } else {
            log(path, materialOrTag);
        }
    }

    private void log(@Nullable String path, String materialOrTag) {
        if (path != null) plugin.getLogger().info("Invalid material for " + "{" + path + "}! " + materialOrTag);
    }

    private @NotNull TypeTarget createTarget(int amount, Material material, @Nullable String fromTag, VehicleType type) {
        return type != null ? new TypeTarget.TargetWithCondition(amount, material, fromTag, type) : new TypeTarget(amount, material, fromTag);
    }

    @SuppressWarnings("SameParameterValue")
    private boolean addMaterialsFromRegistry(Set<TypeTarget> typeTargets, VehicleType type, String tagName, int amount, String @NotNull ... registries) {
        boolean found = false;
        for (String registry : registries) {
            Tag<Material> tag = Bukkit.getTag(registry, NamespacedKey.minecraft(tagName), Material.class);
            if (tag == null) continue;

            for (Material material : tag.getValues()) {
                addAndOverride(typeTargets, createTarget(amount, material, tagName, type));
            }
            found = true;
        }
        return found;
    }

    private void addAndOverride(Set<TypeTarget> typeTargets, @NotNull TypeTarget newTarget) {
        Material type = newTarget.getType();
        if (getTarget(typeTargets, type, true) != null) return;

        TypeTarget withoutCondition = getTarget(typeTargets, type, false);
        if (withoutCondition != null) {
            if (!(newTarget instanceof TypeTarget.TargetWithCondition)) return;
            typeTargets.remove(withoutCondition);
        }

        typeTargets.add(newTarget);
    }

    private @Nullable TypeTarget getTarget(@NotNull Set<TypeTarget> typeTargets, Material type, boolean condition) {
        for (TypeTarget typeTarget : typeTargets) {
            if (typeTarget.is(type) && (!condition || typeTarget instanceof TypeTarget.TargetWithCondition)) {
                return typeTarget;
            }
        }
        return null;
    }
}