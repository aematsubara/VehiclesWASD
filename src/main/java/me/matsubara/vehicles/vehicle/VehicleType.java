package me.matsubara.vehicles.vehicle;


import org.jetbrains.annotations.NotNull;

public enum VehicleType {
    BIKE,
    BOAT,
    CYBERCAR,
    HELICOPTER,
    KART,
    QUAD,
    TANK;

    public @NotNull String toConfigPath() {
        return name().toLowerCase().replace("_", "-");
    }

    public @NotNull String toFilePath() {
        return name().toLowerCase();
    }
}