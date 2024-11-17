package me.matsubara.vehicles.vehicle;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

@Getter
public enum TractorMode {
    HARVEST,
    PLANT,
    BONE_MEAL,
    DIRT_PATH(
            Material.DIRT_PATH,
            List.of(
                    Material.DIRT,
                    Material.GRASS_BLOCK,
                    Material.COARSE_DIRT,
                    Material.MYCELIUM,
                    Material.PODZOL,
                    Material.ROOTED_DIRT),
            Sound.ITEM_SHOVEL_FLATTEN),
    FARMLAND(
            Material.FARMLAND,
            List.of(
                    Material.DIRT,
                    Material.GRASS_BLOCK,
                    Material.DIRT_PATH),
            Sound.ITEM_HOE_TILL);

    private final Material to;
    private final List<Material> from;
    private final Sound convertSound;

    TractorMode() {
        this(Material.AIR, Collections.emptyList(), Sound.ENTITY_PLAYER_BREATH);
    }

    TractorMode(Material to, List<Material> from, Sound convertSound) {
        this.to = to;
        this.from = from;
        this.convertSound = convertSound;
    }

    public @NotNull String toPath() {
        return name().toLowerCase(Locale.ROOT).replace("_", "-");
    }
}