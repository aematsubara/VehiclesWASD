package me.matsubara.vehicles.vehicle.type;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.gps.GPSTick;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@Getter
public class Generic extends Vehicle {

    private float previousForward;
    private @Setter int previousDistance = Integer.MIN_VALUE;
    private @Setter int currentDistance = Integer.MIN_VALUE;
    private GPSTick gpsTick;

    public Generic(@NotNull VehiclesPlugin plugin, VehicleData data, @NotNull Model model) {
        super(plugin, data, model);
    }

    public Generic setGPSTick(GPSTick gpsTick) {
        this.gpsTick = gpsTick;
        this.forceActionBarMessage = true;
        return this;
    }

    @Override
    public boolean canPlaySound() {
        return canMove(true) && driver != null;
    }

    @Override
    public boolean canMove() {
        return canMove(false);
    }

    public boolean canMove(boolean ignoreGPS) {
        if (!super.canMove() || (!ignoreGPS && gpsTick != null)) return false;

        Material type = velocityStand.getLocation().getBlock().getType();
        return type != Material.WATER && type != Material.LAVA;
    }

    @Override
    public boolean canRotate() {
        return super.canRotate() && velocityStand.isOnGround();
    }

    @Override
    protected void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround) {
        float forward = input.forward();

        boolean hasZMovement = forward != 0.0f && canMove();
        if (hasZMovement) previousForward = forward;

        boolean backwards = (hasZMovement ? forward : previousForward) < 0.0f;
        boolean hasSpeed = currentSpeed != 0.0f;

        if (!hasZMovement && !hasSpeed && onGround) return;

        if (hasZMovement) {
            if (!backwards) {
                if (currentSpeed < 0.0f) currentSpeed = 0.0f - acceleration;
                currentSpeed = Math.min(currentSpeed + acceleration * 3.5f, maxSpeed);
            } else {
                if (currentSpeed > 0.0f)
                    currentSpeed = 0.0f + acceleration; // Removing this will take more time to reduce speed and go backwards.
                reduceSpeed(minSpeed);
            }
        } else if (onGround || hasSpeed) {
            if (currentSpeed > 0.0f) {
                reduceSpeed(0.0f);
            } else {
                currentSpeed = Math.min(currentSpeed + acceleration * 3.5f, 0.0f);
            }
        }

        moveUpOrDownIfNeeded(backwards);

        // We could make this easier using the direction of the stand like we do in GPSTick,
        // but this implementation works fine with the GPS.
        Location standLocation = velocityStand.getLocation();

        Vector offset;
        float multiplier;
        if (hasZMovement || hasSpeed) {
            float zVel = hasZMovement ? -forward : -previousForward;
            offset = PluginUtils.offsetVector(new Vector(0.0f, 0.0f, zVel), standLocation.getYaw(), 0.0f);
            multiplier = (currentSpeed < 0.0f || backwards ? -currentSpeed : currentSpeed) / 100.0f / (backwards ? 2.0f : 1.0f);
        } else {
            // Go down until the vehicle is on the ground.
            offset = PluginUtils.offsetVector(new Vector(0.0f, 0.5f, 0.0f), standLocation.getYaw(), 0.0f);
            multiplier = .75f;
        }

        Vector velocity = standLocation
                .clone()
                .subtract(standLocation.clone().add(offset))
                .toVector()
                .normalize()
                .multiply(multiplier)
                .setY(-0.5d);

        velocityStand.setVelocity(velocity);
    }

    public void moveUpOrDownIfNeeded(boolean backwards) {
        Vector direction = velocityStand.getLocation()
                .getDirection()
                .multiply(0.5d)
                .multiply(backwards ? -1.0d : 1.0d);

        Location frontOrBack = velocityStand.getLocation().clone().add(direction);

        Block block = frontOrBack.getBlock();
        Material blockType = block.getType();

        Block top = block.getRelative(BlockFace.UP);

        BlockCollisionResult blockResult = isPassable(block);
        BlockCollisionResult topResult = isPassable(top);

        if (blockResult.allow() && topResult.allow()) return;

        double upY = Double.MIN_VALUE;
        double currentY = frontOrBack.getY();

        if (blockResult.deny() && topResult.deny()) {
            if (currentY % 1 == 0) return;
            if (BlockUtils.getMaterialHeight(top) != Double.MIN_VALUE) return;

            // At this point, we know both, BOTTOM and TOP are solid 1x1 blocks.
            // The situation here is almost the same as Bottom#DENY and Top#UP but in this case, we also need to consider the top block of the CURRENT top.

            Block kingTop = top.getRelative(BlockFace.UP);

            BlockCollisionResult kingTopResult = isPassable(kingTop);
            if (kingTopResult.deny()) return; // If top of top can't be climbed up, we just ignore it.

            double kingTopHeight = kingTopResult.allow() ? 0.0d : BlockUtils.getMaterialHeight(kingTop);
            if (kingTopHeight == Double.MIN_VALUE) return;

            double extra = 1.0d + kingTopHeight;

            double overflow = top.getY() + extra - currentY;
            if (overflow > 1.25d) return;

            double collisionAt = kingTop.getY() - currentY;

            if (kingTopResult.up() && collides(top, frontOrBack.clone().add(0.0d, collisionAt, 0.0d))) {
                upY = kingTop.getY() + kingTopHeight;
            } else {
                upY = kingTop.getY();
            }
        }

        // The front block is obstructed, but it's a block that can be climbed up; the top block is passable.
        if (blockResult.up() && topResult.allow()) {
            // Work with block (bottom) here, not top.
            Double extra = BlockUtils.getMaterialHeight(block);
            if (extra == Double.MIN_VALUE) return;
            if (!collides(block, frontOrBack)) return;

            upY = block.getY() + extra;
        }

        // Here, we put the blocks the players can climb if the bottom is obstructed and the top block too. Jumping normally lets the player climb up 1.25 (1+1/4) block.
        if (blockResult.deny() && topResult.up()) {
            // Work with top here, not block (bottom).
            Double extra = BlockUtils.getMaterialHeight(top);
            if (extra == Double.MIN_VALUE) return;

            double collisionAt;
            if (extra > 0.25d) {
                double overflow = top.getY() + extra - currentY;
                if (overflow > 1.25d) return;

                collisionAt = top.getY() - currentY;
            } else collisionAt = 1.0d;

            if (collides(top, frontOrBack.clone().add(0.0d, collisionAt, 0.0d))) {
                upY = top.getY() + extra;
            } else {
                upY = top.getY();
            }
        }

        // The front is obstructed and can't be climbed up; the top is empty.
        if (blockResult.deny() && topResult.allow()) {
            // At this point, the trapdoor is open.
            if (BlockUtils.BUGGY_HEIGHTS.contains(blockType) || Tag.TRAPDOORS.isTagged(blockType)) return;

            // Work with top here, not block (bottom).
            upY = top.getY();
        }

        // There are other cases that we should handle, for example,
        // if the bottom is empty (allow) but the top can be climbed up (up).
        // This is good for now.
        if (upY == Double.MIN_VALUE) return;

        Vector slightOffset = new Vector(0.0d, 0.0d, backwards ? 0.475d : -0.475d);
        frontOrBack.add(PluginUtils.offsetVector(slightOffset, frontOrBack.getYaw(), frontOrBack.getPitch()));
        frontOrBack.setY(upY + 0.02d);

        velocityStand.teleport(frontOrBack);
    }

    private boolean collides(@NotNull Block block, @NotNull Location location) {
        BoundingBox standBox = standBoxShift(location);

        Collection<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
        if (boxes.isEmpty())
            return block.getBoundingBox().shift(block.getX(), block.getY(), block.getZ()).overlaps(standBox);

        for (BoundingBox box : boxes) {
            if (box.shift(block.getX(), block.getY(), block.getZ()).overlaps(standBox)) return true;
        }

        return false;
    }

    private @NotNull BoundingBox standBoxShift(@NotNull Location location) {
        BoundingBox box = velocityStand.getBoundingBox();

        double x = box.getWidthX() / 2;
        double y = box.getHeight() / 2;
        double z = box.getWidthZ() / 2;

        return BoundingBox.of(location.clone().add(0.0d, y, 0.0d), x, y, z);
    }
}