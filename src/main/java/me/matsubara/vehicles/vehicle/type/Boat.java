package me.matsubara.vehicles.vehicle.type;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class Boat extends Vehicle {

    private float previousForward;
    private boolean playEngineSound;

    public Boat(@NotNull VehiclesPlugin plugin, VehicleData data, @NotNull Model model) {
        super(plugin, data, model);
    }

    @Override
    public void resetVelocityStand(World world) {
        super.resetVelocityStand(world);
        velocityStand.setGravity(false);
    }

    @Override
    public boolean isMovingBackwards(@NotNull PlayerInput input) {
        float forward = input.forward();
        if (forward < 0.0f) return true;
        if (currentSpeed == 0.0f) return false;
        return previousForward < 0.0f;
    }

    @Override
    public boolean canPlaySound() {
        return super.canPlaySound() && playEngineSound;
    }

    @Override
    protected void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround) {
        float forward = input.forward();

        boolean hasZMovement = forward != 0.0f && canMove();
        if (hasZMovement) previousForward = forward;

        boolean backwards = (hasZMovement ? forward : previousForward) < 0.0f;
        boolean hasSpeed = currentSpeed != 0.0f;

        if (!hasZMovement && !hasSpeed) {
            playEngineSound = false;
            return;
        }

        playEngineSound = true;

        if (hasZMovement) {
            if (!backwards) {
                if (currentSpeed < 0.0f) currentSpeed = 0.0f - acceleration;
                currentSpeed = Math.min(currentSpeed + acceleration * 3.5f, maxSpeed);
            } else {
                if (currentSpeed > 0.0f)
                    currentSpeed = 0.0f + acceleration; // Removing this will take more time to reduce speed and go backwards.
                reduceSpeed(minSpeed);
            }
        } else {
            if (currentSpeed > 0.0f) {
                reduceSpeed(0.0f);
            } else {
                currentSpeed = Math.min(currentSpeed + acceleration * 3.5f, 0.0f);
            }
        }

        Location temp = velocityStand.getLocation();

        if (!velocityStand.isInWater()) {
            Vector direction = temp
                    .getDirection()
                    .multiply(0.75d)
                    .multiply(backwards ? -1.0d : 1.0d);

            Location frontOrBack = temp.clone().add(direction);

            Block block = frontOrBack.getBlock();
            if (!BlockUtils.isBlockFromWater(block)) return;
        }

        float zVel = hasZMovement ? forward : previousForward;
        double finalSpeed = Math.abs(currentSpeed / 100.0d);

        Vector offset = new Vector(0.0d, 0.0d, zVel > 0.0f ? finalSpeed : -finalSpeed);
        velocityStand.teleport(temp.add(PluginUtils.offsetVector(offset, temp.getYaw(), temp.getPitch())));
    }
}