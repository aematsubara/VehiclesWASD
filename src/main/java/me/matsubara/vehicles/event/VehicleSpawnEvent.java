package me.matsubara.vehicles.event;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class VehicleSpawnEvent extends Event implements Cancellable {

    // The player is null if the vehicle was spawned during a chunk load or a reload.
    private final @Nullable Player player;
    private final Location location;
    private final VehicleType type;
    private boolean cancelled;

    private static final HandlerList handlers = new HandlerList();

    public VehicleSpawnEvent(@Nullable Player player, Location location, VehicleType type) {
        this.player = player;
        this.location = location;
        this.type = type;
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