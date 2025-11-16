package me.matsubara.vehicles.vehicle;

import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.type.Slab;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Holds a block type that may have different variants.
public record CustomizationGroup(Material whole, Material half, @Nullable Material extra) {

    // Items allowed for customization.
    public static final Map<Family, List<CustomizationGroup>> GROUPS = new HashMap<>();

    @Contract(pure = true)
    public static @Nullable CustomizationGroup getByAny(@Nullable Family family, Material check) {
        Family[] families = family != null ? new Family[]{family} : Family.values();

        for (Family temp : families) {
            for (CustomizationGroup group : GROUPS.get(temp)) {
                if (group.whole == check
                        || group.half == check
                        || group.extra == check) return group;
            }
        }

        return null;
    }

    private static final Material[] GLASS = {
            Material.GLASS,
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS};

    static {
        List<CustomizationGroup> glass = new ArrayList<>();
        for (Material whole : GLASS) {
            Material half = PluginUtils.getOrNull(Material.class, whole.name() + "_PANE");
            glass.add(new CustomizationGroup(whole, half, null));
        }
        GROUPS.put(Family.GLASS, glass);

        List<CustomizationGroup> wools = new ArrayList<>();
        for (Material whole : Tag.WOOL.getValues()) {
            Material half = PluginUtils.getOrNull(Material.class, whole.name().replace("_WOOL", "_CARPET"));
            wools.add(new CustomizationGroup(whole, half, null));
        }
        GROUPS.put(Family.WOOL, wools);

        GROUPS.put(Family.OTHER, getOther());
    }

    private static @NotNull List<CustomizationGroup> getOther() {
        List<CustomizationGroup> groups = new ArrayList<>();

        // We need to add some of the materials manually or they won't be complete.
        groups.add(new CustomizationGroup(Material.PURPUR_BLOCK, Material.PURPUR_SLAB, Material.PURPUR_STAIRS));
        groups.add(new CustomizationGroup(Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_STAIRS));
        if (PluginUtils.getOrNull(Material.class, "BAMBOO_PLANKS") != null) {
            groups.add(new CustomizationGroup(Material.BAMBOO_PLANKS, Material.BAMBOO_SLAB, Material.BAMBOO_STAIRS));
        }
        groups.add(new CustomizationGroup(Material.BRICKS, Material.BRICK_SLAB, Material.BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.QUARTZ_BLOCK, Material.QUARTZ_SLAB, Material.QUARTZ_STAIRS));
        groups.add(new CustomizationGroup(Material.NETHER_BRICKS, Material.NETHER_BRICK_SLAB, Material.NETHER_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.STONE, Material.STONE_SLAB, Material.STONE_STAIRS));
        groups.add(new CustomizationGroup(Material.STONE_BRICKS, Material.STONE_BRICK_SLAB, Material.STONE_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.MOSSY_STONE_BRICKS, Material.MOSSY_STONE_BRICK_SLAB, Material.MOSSY_STONE_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.GRANITE, Material.GRANITE_SLAB, Material.GRANITE_STAIRS));
        groups.add(new CustomizationGroup(Material.ANDESITE, Material.ANDESITE_SLAB, Material.ANDESITE_STAIRS));
        groups.add(new CustomizationGroup(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_STAIRS));
        if (PluginUtils.getOrNull(Material.class, "MUD_BRICKS") != null) {
            groups.add(new CustomizationGroup(Material.MUD_BRICKS, Material.MUD_BRICK_SLAB, Material.MUD_BRICK_STAIRS));
        }
        groups.add(new CustomizationGroup(Material.PRISMARINE_BRICKS, Material.PRISMARINE_BRICK_SLAB, Material.PRISMARINE_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.RED_NETHER_BRICKS, Material.RED_NETHER_BRICK_SLAB, Material.RED_NETHER_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICK_SLAB, Material.POLISHED_BLACKSTONE_BRICK_STAIRS));
        groups.add(new CustomizationGroup(Material.END_STONE_BRICKS, Material.END_STONE_BRICK_SLAB, Material.END_STONE_BRICK_STAIRS));

        for (Material slab : Material.values()) {
            // Ignore legacies.
            @SuppressWarnings("deprecation") boolean legacy = slab.isLegacy();
            if (legacy) continue;

            // Ignore the already existing.
            if (groups.stream()
                    .map(CustomizationGroup::half)
                    .anyMatch(material -> material == slab)) continue;

            // Ignore non-slabs.
            try {
                if (!(slab.createBlockData() instanceof Slab)) continue;
            } catch (Exception exception) {
                continue;
            }

            String slabName = slab.name();
            String blockName = slabName.replace("_SLAB", "") + (slabName.contains("_BRICK_") ? "S" : "");

            Material block = PluginUtils.getOrNull(Material.class, blockName);
            Material planks = PluginUtils.getOrNull(Material.class, blockName + "_PLANKS");
            Material stairs = PluginUtils.getOrNull(Material.class, blockName + "_STAIRS");
            if (block == null && planks == null) continue;

            Material origin = block != null ? block : planks;
            groups.add(new CustomizationGroup(origin, slab, stairs));
        }

        // For these types of blocks, we need the 3 parts (unlike GLASS and WOOL).
        groups.removeIf(group -> group.whole == null
                || group.half == null
                || group.extra == null);

        return groups;
    }

    public enum Type {
        WHOLE(CustomizationGroup::whole),
        HALF(CustomizationGroup::half),
        EXTRA(CustomizationGroup::extra);

        private final Function<CustomizationGroup, Material> getter;

        Type(Function<CustomizationGroup, Material> getter) {
            this.getter = getter;
        }

        public Material getFromGroup(CustomizationGroup group) {
            return getter.apply(group);
        }
    }

    public enum Family {
        GLASS,
        WOOL,
        OTHER
    }
}