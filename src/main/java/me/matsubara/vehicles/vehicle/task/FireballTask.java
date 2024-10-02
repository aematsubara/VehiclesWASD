package me.matsubara.vehicles.vehicle.task;

import me.matsubara.vehicles.VehiclesPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

// Credits to SethBling! https://www.youtube.com/watch?v=FyLDKWmYKjE
public class FireballTask extends BukkitRunnable {

    private final Fireball fireball;
    private final LivingEntity target;

    private static final double MAX_ROTATION_ANGLE = 0.12d;

    public FireballTask(VehiclesPlugin plugin, Fireball fireball, LivingEntity target) {
        this.fireball = fireball;
        this.target = target;
        runTaskTimer(plugin, 1L, 1L);
    }

    public void run() {
        if (fireball.isOnGround() || !fireball.isValid() || !target.isValid()) {
            cancel();
            return;
        }

        Location fireballLocation = fireball.getLocation();

        Vector targetOffset = new Vector(0.0d, target.getBoundingBox().getHeight() / 2, 0.0d);
        Location targetLocation = target.getLocation().add(targetOffset);

        double speed = fireball.getVelocity().length(), newSpeed;
        if (target instanceof Player player
                && player.isBlocking()
                && fireballLocation.distance(targetLocation) < 8.0d) {
            newSpeed = speed * 0.6d;
        } else {
            newSpeed = 0.9d * speed + 0.14d;
        }

        Vector dirVelocity = fireball.getVelocity().clone().normalize();

        Vector dirToTarget = targetLocation.clone()
                .add(new Vector(0.0d, 0.5d, 0.0d))
                .subtract(fireballLocation)
                .toVector()
                .normalize();

        double angle = dirVelocity.angle(dirToTarget);

        Vector newVelocity;
        if (angle < MAX_ROTATION_ANGLE) {
            newVelocity = dirVelocity.multiply(newSpeed);
        } else {
            newVelocity = dirVelocity
                    .multiply((angle - MAX_ROTATION_ANGLE) / angle)
                    .add(dirToTarget.multiply(MAX_ROTATION_ANGLE / angle))
                    .normalize()
                    .multiply(newSpeed);
        }

        fireball.setVelocity(newVelocity.add(new Vector(0.0d, 0.03d, 0.0d)));
    }
}