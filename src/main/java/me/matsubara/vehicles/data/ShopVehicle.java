package me.matsubara.vehicles.data;

import me.matsubara.vehicles.vehicle.VehicleData;
import org.bukkit.inventory.ItemStack;

public record ShopVehicle(String shopId, ItemStack item, VehicleData data) {
}