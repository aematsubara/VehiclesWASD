package me.matsubara.vehicles.vehicle.type;

import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class UpAndDown extends Vehicle {

    private static final float BLADES_ROTATION_SPEED = 25.0f;

    private Player outsideDriver;
    private final Set<UUID> transfers = new HashSet<>();

    public UpAndDown(@NotNull VehiclesPlugin plugin, VehicleData data, @NotNull Model model) {
        super(plugin, data, model);
        currentSpeed = downSpeed;
        if (velocityStand != null && velocityStand.isValid()) {
            velocityStand.setVelocity(new Vector(0.0d, -0.5d, 0.0d));
        }
    }

    @Override
    protected void postModelUpdate() {
        handleRotors();
    }

    @Override
    public boolean canPlayEngineSound() {
        return canMove() && (driver != null || outsideDriver != null);
    }

    @Override
    public boolean isDriver(@NotNull Player player) {
        return super.isDriver(player) || player.equals(outsideDriver);
    }

    private void handleRotors() {
        if (!is(VehicleType.HELICOPTER)
                || !canMove()
                || (driver == null && outsideDriver == null)) return;

        for (IStand stand : model.getStands()) {
            StandSettings settings = stand.getSettings();
            if (!settings.getPartName().startsWith("ROTOR")) continue;

            float rotation = settings.getExtraYaw() + BLADES_ROTATION_SPEED;
            settings.setExtraYaw(rotation);
        }
    }

    @Override
    public void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround) {
        if (!canMove() || (driver == null && outsideDriver == null)) {
            velocityStand.setGravity(true);
            velocityStand.setVelocity(new Vector(0.0d, -0.5d, 0.0d));
            currentSpeed = downSpeed;
            return;
        }

        float forward = input.forward();
        boolean jump = input.jump();

        boolean hasZMovement = jump || forward != 0.0f;
        boolean backwards = forward < 0.0f;

        if (hasZMovement) {
            if (!jump && onGround) return;
            if (!backwards) {
                if (currentSpeed < 0.0f) currentSpeed = 0.0f - acceleration;

                boolean higherIncrease = forward > 0.0f && !onGround && !jump;
                currentSpeed = Math.min(currentSpeed + acceleration * 3.5f, higherIncrease ? maxSpeed : upSpeed);
            } else {
                if (currentSpeed > 0.0f) currentSpeed = 0.0f;
                reduceSpeed(downSpeed);
            }
        } else {
            if (currentSpeed < 0.0f) {
                currentSpeed = Math.min(currentSpeed + acceleration * 5.0f, 0.0f);
            } else if (currentSpeed > 0.0f) {
                reduceSpeed(0.0f);
            }
        }

        if (jump || forward < 0.0f) {
            velocityStand.setGravity(true);

            double upDownSpeed = Math.abs(currentSpeed / 2.0d / 100.0d);
            velocityStand.setVelocity(new Vector(0.0d, jump ? upDownSpeed : -upDownSpeed, 0.0d));
            return;
        }

        velocityStand.setGravity(false);

        if (forward <= 0.0f || currentSpeed <= 0.0f) return;

        Location temp = velocityStand.getLocation();

        if (!temp.getBlock().isPassable()) {
            Location frontOrBack = temp.clone().add(temp.getDirection().multiply(0.75d));

            Block block = frontOrBack.getBlock();
            if (!block.isPassable()) return;
        }

        Vector offset = new Vector(0.0d, 0.0d, currentSpeed / 100.0d);

        Location target = temp.add(PluginUtils.offsetVector(offset, temp.getYaw(), temp.getPitch()));
        if (notAllowedHere(target)) return;

        PaperLib.teleportAsync(velocityStand, target);
    }
}