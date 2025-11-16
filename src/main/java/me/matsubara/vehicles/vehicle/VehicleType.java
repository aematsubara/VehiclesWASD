package me.matsubara.vehicles.vehicle;


import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.vehicle.type.Generic;
import me.matsubara.vehicles.vehicle.type.UpAndDown;
import me.matsubara.vehicles.vehicle.type.Water;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum VehicleType {
    BIKE,
    BOAT(Water::new),
    CYBERCAR,
    HELICOPTER(5, UpAndDown::new),
    JETSKI(Water::new),
    KART,
    PLANE,
    QUAD,
    TANK,
    TRACTOR(6),
    UFO(UpAndDown::new);

    private final int inventorySize;
    private final TriFunction<VehiclesPlugin, VehicleData, Model, Vehicle> getter;

    VehicleType() {
        this(3);
    }

    VehicleType(int inventorySize) {
        this(inventorySize, Generic::new);
    }

    VehicleType(TriFunction<VehiclesPlugin, VehicleData, Model, Vehicle> getter) {
        this(3, getter);
    }

    VehicleType(int inventorySize, TriFunction<VehiclesPlugin, VehicleData, Model, Vehicle> getter) {
        this.inventorySize = inventorySize;
        this.getter = getter;
    }

    public Vehicle create(VehiclesPlugin plugin, VehicleData data, Model model) {
        return getter.apply(plugin, data, model);
    }

    public @NotNull String toPath() {
        return name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    public int getSize() {
        return inventorySize * 9;
    }

    public boolean isAirVehicle() {
        return this == HELICOPTER || this == PLANE || this == UFO;
    }

    public boolean isWaterVehicle() {
        return this == BOAT || this == JETSKI;
    }
}