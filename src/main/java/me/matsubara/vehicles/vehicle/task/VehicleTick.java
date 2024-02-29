package me.matsubara.vehicles.vehicle.task;

import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.scheduler.BukkitRunnable;

public class VehicleTick extends BukkitRunnable {

    private final Vehicle vehicle;

    public VehicleTick(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public void run() {
        vehicle.tick();
    }
}