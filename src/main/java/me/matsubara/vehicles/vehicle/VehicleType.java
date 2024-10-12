package me.matsubara.vehicles.vehicle;


import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum VehicleType {
    BIKE,
    BOAT,
    CYBERCAR,
    HELICOPTER,
    KART,
    PLANE,
    QUAD,
    TANK;

    public @NotNull String toPath() {
        return name().toLowerCase(Locale.ROOT).replace("_", "-");
    }
}