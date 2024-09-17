package me.matsubara.vehicles.vehicle.gps;

import lombok.Getter;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.vehicle.type.Generic;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patheloper.api.wrapper.PathPosition;
import org.patheloper.mapping.bukkit.BukkitMapper;

import java.util.Iterator;

public class GPSTick extends BukkitRunnable {

    private final Iterator<PathPosition> iterator;
    private final Player player;
    private final Generic generic;
    private final ArmorStand stand;
    private final @Getter Location destination;
    private final @Getter String homeName;

    private Location target;
    private boolean reached;
    private int attempts;
    private int tick;

    public GPSTick(Iterator<PathPosition> iterator, Player player, @NotNull Generic generic, Location destination, String homeName, Location target) {
        this.iterator = iterator;
        this.player = player;
        this.generic = generic.setGPSTick(this);
        this.generic.setCurrentSpeed(0.0f);
        this.generic.getVelocityStand().setVelocity(new Vector(0, 0, 0));
        this.stand = generic.getVelocityStand();
        this.destination = destination;
        this.homeName = homeName;
        this.target = target;
        lookAtTarget(stand, target);
    }

    @Override
    public void run() {
        boolean error = !stand.isValid() || generic.getDriver() == null || !generic.canMove(true);
        if (error || (!iterator.hasNext() && reached)) {
            cancel(error ? Messages.Message.GPS_STOPPED : Messages.Message.GPS_ARRIVED);
            return;
        }

        if (!reached) {
            if (tick % 20 == 0 && attempts++ == 5) { // It shouldn't take more than 5 attempts (and 5 seconds) to get to the closest location.
                generic.getPlugin().getMessages().send(player, Messages.Message.GPS_WENT_CRAZY);
                cancel(null);
                return;
            }

            // If we need to go down, we'll go slower.
            double speed = stand.getLocation().getBlockY() > target.getBlockY() ? 0.175d : 0.35d;

            stand.setVelocity(stand.getLocation()
                    .getDirection()
                    .multiply(speed)
                    .setY(-0.5d));

            if (stand.getLocation().getBlockY() < target.getBlockY()) {
                generic.moveUpOrDownIfNeeded(false);
            }

            // Out of path, attempting to get back.
            if ((int) stand.getLocation().distance(target) > 3) {
                lookAtTarget(stand, target);
            }

            BoundingBox blockBox = BoundingBox.of(target, 0.5d, 1.0d, 0.5d);

            tick++;

            Location actual = stand.getLocation();
            if (blockBox.contains(actual.getX(), actual.getY(), actual.getZ())) {
                reached = true;
                return;
            }

            return;
        }

        if (++tick == Integer.MAX_VALUE) tick = 0;
        attempts = 0;

        Location next = BukkitMapper.toLocation(iterator.next());
        if (next.getBlock().isLiquid()) return;

        target = next.add(0.5d, 0.5d, 0.5d);
        reached = false;

        generic.setCurrentDistance((int) destination.distance(stand.getLocation()));
        lookAtTarget(stand, target);
    }

    public synchronized void cancel(@Nullable Messages.Message message) {
        cancel();

        if (message != null) {
            generic.getPlugin().getMessages().send(player, message);
        }

        generic.setGPSTick(null);
    }

    private void lookAtTarget(@NotNull ArmorStand stand, @NotNull Location currentTarget) {
        Location standLocation = stand.getLocation();
        float yaw = getAngle(new Vector(standLocation.getX(), 0.0d, standLocation.getZ()), currentTarget.toVector());
        stand.setRotation(yaw, standLocation.getPitch());
    }

    private float getAngle(@NotNull Vector first, @NotNull Vector second) {
        double dx = second.getX() - first.getX();
        double dz = second.getZ() - first.getZ();
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        if (angle < 0) angle += 360.0f;
        return angle;
    }
}