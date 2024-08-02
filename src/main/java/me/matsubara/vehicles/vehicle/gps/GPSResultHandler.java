package me.matsubara.vehicles.vehicle.gps;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.vehicle.type.Generic;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.patheloper.api.pathing.result.PathfinderResult;
import org.patheloper.api.wrapper.PathPosition;
import org.patheloper.mapping.bukkit.BukkitMapper;

import java.util.Iterator;
import java.util.function.Consumer;

public class GPSResultHandler implements Consumer<PathfinderResult> {

    private final Player player;
    private final Generic generic;
    private final String homeName;

    private boolean isValid = true;

    private static final int MAX_WATER = 2;

    public GPSResultHandler(Player player, Generic generic, String homeName) {
        this.player = player;
        this.generic = generic;
        this.homeName = homeName;
    }

    @Override
    public void accept(@NotNull PathfinderResult result) {
        VehiclesPlugin plugin = generic.getPlugin();
        Messages messages = plugin.getMessages();

        if (!isValid) return;

        generic.getPlugin().getVehicleManager().invalidateGPSResult(player.getUniqueId());

        if (!result.successful() && !result.hasFallenBack()) {
            messages.send(player, Messages.Message.GPS_PATH_NOT_FOUND);
            return;
        }

        // The driver left.
        if (generic.getDriver() == null) return;

        Iterable<PathPosition> positions = result.getPath();

        BoundingBox box = generic.getBox();
        Location firstTarget = null;

        Iterator<PathPosition> iterator = positions.iterator();
        while (iterator.hasNext()) {
            PathPosition position = iterator.next();

            Location location = BukkitMapper.toLocation(position).add(0.5d, 0.5d, 0.5d);
            if (box.contains(location.toVector())) {
                iterator.remove();
                continue;
            }

            firstTarget = location;
            break;
        }

        if (firstTarget == null) {
            messages.send(player, Messages.Message.GPS_PATH_NOT_FOUND);
            return;
        }

        if (hasConsecutiveLiquid(positions, MAX_WATER)) {
            messages.send(player, Messages.Message.GPS_FULL_OF_WATER);
            return;
        }

        Location destination = BukkitMapper.toLocation(result.getPath().getEnd());

        GPSTick gpsTick = new GPSTick(iterator, player, generic, destination, homeName, firstTarget);
        gpsTick.runTaskTimer(plugin, 0L, 1L);
    }

    public void invalidate() {
        isValid = false;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean hasConsecutiveLiquid(@NotNull Iterable<PathPosition> positions, int limit) {
        int currentConsecutive = 0;

        for (PathPosition position : positions) {
            Block block = BukkitMapper.toLocation(position).getBlock();

            if (block.isLiquid()) {
                if (++currentConsecutive > limit) return true;
            } else {
                currentConsecutive = 0;
            }
        }

        return false;
    }
}