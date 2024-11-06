package me.matsubara.vehicles.event;

import lombok.Getter;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
public class VehicleDespawnEvent extends Event {

    private final Vehicle vehicle;

    private static final HandlerList handlers = new HandlerList();

    public VehicleDespawnEvent(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}