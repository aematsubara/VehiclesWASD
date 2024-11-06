package me.matsubara.vehicles.util;

import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@Getter
public enum Crop {
    WHEAT(Material.FARMLAND, Material.WHEAT, Material.WHEAT_SEEDS, Material.WHEAT, 1, 4, 1, 1),
    CARROT(Material.FARMLAND, Material.CARROTS, Material.CARROT, Material.CARROT, 0, 0, 2, 5),
    POTATO(Material.FARMLAND, Material.POTATOES, Material.POTATO, Material.POTATO, 0, 0, 2, 5),
    BEETROOT(Material.FARMLAND, Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.BEETROOT, 1, 4, 1, 1),
    WART(Material.SOUL_SAND, Material.NETHER_WART, Material.NETHER_WART, Material.NETHER_WART, 0, 0, 2, 4),
    SWEET_BERRIES(Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.FARMLAND,
            Material.MOSS_BLOCK), Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES, Material.SWEET_BERRIES, 0, 0, 2, 3);

    private final Set<Material> on;
    private final Material place;
    private final Material seeds;
    private final Material drop;
    private final int minSeedAmount;
    private final int maxSeedAmount;
    private final int minDropAmount;
    private final int maxDropAmount;

    Crop(Material on,
         Material place,
         Material seeds,
         Material drop,
         int minSeedAmount,
         int maxSeedAmount,
         int minDropAmount,
         int maxDropAmount) {
        this(Set.of(on), place, seeds, drop, minSeedAmount, maxSeedAmount, minDropAmount, maxDropAmount);
    }

    Crop(Set<Material> on,
         Material place,
         Material seeds,
         Material drop,
         int minSeedAmount,
         int maxSeedAmount,
         int minDropAmount,
         int maxDropAmount) {
        this.on = on;
        this.place = place;
        this.seeds = seeds;
        this.drop = drop;
        this.minSeedAmount = minSeedAmount;
        this.maxSeedAmount = maxSeedAmount;
        this.minDropAmount = minDropAmount;
        this.maxDropAmount = maxDropAmount;
    }

    @Contract(pure = true)
    public static @Nullable Crop getByPlaceType(Material place) {
        for (Crop crop : values()) {
            if (crop.place == place) return crop;
        }
        return null;
    }

    public static @Nullable Crop getBySeeds(Material below, @NotNull Inventory inventory) {
        // We use this method since we want to place the first seeds in the inventory.

        Set<Crop> valid = new HashSet<>();

        for (Crop crop : values()) {
            // We only want to place crops that can be placed in a single specific block.
            Set<Material> on = crop.on;
            if (on.size() == 1 && on.contains(below)) {
                valid.add(crop);
            }
        }

        if (valid.isEmpty()) return null;

        for (ItemStack item : inventory.getContents()) {
            if (item == null) continue;

            for (Crop crop : valid) {
                if (item.getType() == crop.seeds) return crop;
            }
        }

        return null;
    }
}