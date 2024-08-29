package me.matsubara.vehicles.hook;

import me.matsubara.vehicles.VehiclesPlugin;

public interface AVExtension<T> {

    T init(VehiclesPlugin plugin);

    default void onEnable(VehiclesPlugin plugin) {

    }
}