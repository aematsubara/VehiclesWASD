package me.matsubara.vehicles.vehicle.gps.filter;

import lombok.NonNull;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.patheloper.api.pathing.filter.PathFilter;
import org.patheloper.api.pathing.filter.PathValidationContext;
import org.patheloper.mapping.bukkit.BukkitMapper;

/**
 * A PathFilter that excludes nodes which are located inside a world border.
 */
public class WorldBorderFilter implements PathFilter {

    private final Vehicle vehicle;

    /**
     * Constructor to initialize the filter with a vehicle.
     *
     * @param vehicle The vehicle to use.
     */
    public WorldBorderFilter(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    /**
     * Filters out nodes that are located inside a world border.
     *
     * @param context The context of the current pathfinding validation.
     * @return true if the node is safe (i.e., not inside a world border), false otherwise.
     */
    @Override
    public boolean filter(@NonNull PathValidationContext context) {
        return !vehicle.notAllowedHere(BukkitMapper.toLocation(context.getPosition()));
    }
}
