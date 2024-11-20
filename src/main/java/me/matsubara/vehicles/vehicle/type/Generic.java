package me.matsubara.vehicles.vehicle.type;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.util.Vector3i;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import me.matsubara.vehicles.event.protocol.WrapperPlayServerEffect;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.ModelLocation;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.Crop;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.TractorMode;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.gps.GPSTick;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class Generic extends Vehicle {

    private final Set<Material> farmlandTopRemove;
    private float previousForward;
    private @Setter int previousDistance = Integer.MIN_VALUE;
    private @Setter int currentDistance = Integer.MIN_VALUE;
    private GPSTick gpsTick;
    private @Setter TractorMode tractorMode;
    private final List<ModelLocation> tractorLocations = new ArrayList<>();

    private final int tractorTickDelay = Math.max(1, Config.TRACTOR_TICK_DELAY.asInt());
    private final boolean workOnRotation = Config.TRACTOR_WORK_ON_ROTATION.asBool();
    private final boolean tractorStackWheat = Config.TRACTOR_STACK_WHEAT.asBool();
    private final boolean tractorBlockToMeal = Config.TRACTOR_BLOCK_TO_BONE_MEAL.asBool();

    private static final float UP_EXTRA_PITCH = 5.0f;
    private static final Set<Material> PLANT = Stream.of(
                    Crop.WHEAT,
                    Crop.CARROT,
                    Crop.POTATO,
                    Crop.BEETROOT,
                    Crop.WART)
            .map(Crop::getSeeds)
            .collect(Collectors.toSet());

    public Generic(@NotNull VehiclesPlugin plugin, VehicleData data, @NotNull Model model) {
        super(plugin, data, model);
        this.farmlandTopRemove = BlockUtils.builder()
                .add(Tag.LEAVES)
                .add(Tag.SAPLINGS)
                .add(Tag.FLOWERS)
                .addString(
                        "GRASS",
                        "FERN",
                        "DEAD_BUSH",
                        "VINE",
                        "GLOW_LICHEN",
                        "SUNFLOWER",
                        "LILAC",
                        "ROSE_BUSH",
                        "PEONY",
                        "TALL_GRASS",
                        "LARGE_FERN",
                        "HANGING_ROOTS",
                        "PITCHER_PLANT",
                        "WATER",
                        "SEAGRASS",
                        "TALL_SEAGRASS",
                        "WARPED_ROOTS",
                        "NETHER_SPROUTS",
                        "CRIMSON_ROOTS")
                .get();
        this.tractorMode = data.tractorMode();
        for (int i = 1; i <= 3; i++) {
            ModelLocation location = model.getLocationByName("TRACTOR_CHECK_" + i);
            if (location != null) this.tractorLocations.add(location);
        }
    }

    public Generic setGPSTick(GPSTick gpsTick) {
        this.gpsTick = gpsTick;
        forceActionBarMessage();
        return this;
    }

    @Override
    public boolean canPlayEngineSound() {
        return canMove(true) && driver != null;
    }

    @Override
    public boolean canMove() {
        return canMove(false);
    }

    public boolean canMove(boolean ignoreGPS) {
        return super.canMove()
                && (ignoreGPS || gpsTick == null)
                && !isOnLiquid();
    }

    @Override
    public boolean canRotate() {
        return super.canRotate() && (isOnGround() || is(VehicleType.PLANE));
    }

    @Override
    protected void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround) {
        float forward = input.forward();

        boolean hasZMovement = forward != 0.0f && canMove();
        if (hasZMovement) previousForward = forward;

        boolean backwards = (hasZMovement ? forward : previousForward) < 0.0f;
        boolean hasSpeed = currentSpeed != 0.0f;

        if (!hasZMovement && !hasSpeed && onGround) {
            if (input.sideways() != 0.0f && workOnRotation) {
                handleTractor();
            }
            return;
        }

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

        Player player;
        if (driver != null
                && is(VehicleType.PLANE)
                && currentSpeed > liftingSpeed
                && (player = Bukkit.getPlayer(driver)) != null) {

            float pitch = player.getLocation().getPitch(), alternate;
            if (pitch >= 0.0f && pitch <= UP_EXTRA_PITCH) {
                alternate = UP_EXTRA_PITCH - pitch;
            } else {
                alternate = pitch * -1 + UP_EXTRA_PITCH;
            }

            velocity.setY(1 * (alternate / 100.0f));
        }

        Location temp = velocityStand.getLocation().clone().add(velocity);
        if (notAllowedHere(temp)) return;

        velocityStand.setVelocity(velocity);
    }

    @Override
    public boolean hasWeapon() {
        return is(VehicleType.PLANE) || is(VehicleType.TANK);
    }

    private boolean breakBlock(@NotNull Block block) {
        if (is(VehicleType.TRACTOR)) return false;
        return plugin.getTypeTargetManager().applies(plugin.getBreakBlocks(), type, block.getType()) != null;
    }

    private void breakAndPlaySound(@NotNull Block block, boolean breakBlock) {
        // Get id before breaking the block.
        int id = BlockUtils.getBlockStateId(block);

        // If the block was destroyed and the id is valid, then proceed.
        if ((breakBlock && !block.breakNaturally()) || id == -1) return;

        // Send particles and sound.
        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        WrapperPlayServerEffect wrapper = new WrapperPlayServerEffect(
                2001,
                new Vector3i(block.getX(), block.getY(), block.getZ()),
                id,
                false);

        for (Player player : block.getWorld().getPlayers()) {
            manager.sendPacket(SpigotReflectionUtil.getChannel(player), wrapper);
        }
    }

    private void handleTractor() {
        if (tractorMode == null || tick % tractorTickDelay != 0) return;
        if (!is(VehicleType.TRACTOR) || !hasFuel()) return;

        // For DIRT_PATH & FARMLAND we don't need storage.
        if (storageRows <= 0
                && tractorMode != TractorMode.DIRT_PATH
                && tractorMode != TractorMode.FARMLAND) return;

        // We need at least 1 seed for PLANT mode.
        if (tractorMode == TractorMode.PLANT && PLANT.stream().noneMatch(seed -> inventory.contains(seed))) {
            return;
        }

        // We need at least 1 bone meal for BONE_MEAL mode.
        if (tractorMode == TractorMode.BONE_MEAL && !hasBoneMeal()) return;

        handleTractor(0);
        handleTractor(1);

        if (tractorStackWheat) stackWheat();
    }

    private boolean hasBoneMeal() {
        return inventory.contains(Material.BONE_MEAL) || (tractorBlockToMeal
                && inventory.contains(Material.BONE_BLOCK)
                && inventory.firstEmpty() != -1);
    }

    private void handleTractor(int step) {
        Set<Block> checked = new HashSet<>();

        // The location of these parts is where we want to break the crops.
        for (ModelLocation location : tractorLocations) {
            Location clone = location.getLocation().clone();
            clone.setY(velocityStand.getLocation().getY());

            for (int j = 0; j < step; j++) {
                clone.setY(clone.getY() - 1);
            }

            Block block = clone.getBlock();
            if (!checked.add(block)) continue;

            Block up = block.getRelative(BlockFace.UP);
            BlockData upData = up.getBlockData();

            if (tractorMode == TractorMode.DIRT_PATH || tractorMode == TractorMode.FARMLAND) {
                handlePathOrFarmland(block, up, upData);
                continue;
            }

            if (tractorMode == TractorMode.PLANT) {
                handlePlant(block, up, upData);
                continue;
            }

            // For BONE_MEAL & HARVEST we need an Ageable.
            if (!(upData instanceof Ageable ageable)) continue;

            // For BONE_MEAL & HARVEST we need a crop.
            Crop crop = Crop.getByPlaceType(up.getType());
            if (crop == null || !crop.getOn().contains(block.getType())) continue;

            if (tractorMode == TractorMode.BONE_MEAL) {
                handleBoneMeal(ageable, crop, up);
                continue;
            }

            handleHarvest(ageable, crop, up);
        }
    }

    private void handlePathOrFarmland(@NotNull Block block, @NotNull Block up, BlockData upData) {
        if (upData instanceof Ageable) return;

        Material upType = up.getType();
        if (!upType.isAir() && !up.isPassable()) return;

        if (!tractorMode.getFrom().contains(block.getType())) return;

        block.setType(tractorMode.getTo());
        playBlockSound(block, block.getBlockData(), group -> tractorMode.getConvertSound());

        // Remove block on top (only passable grass/flowers) when creating a farmland.
        if (tractorMode == TractorMode.FARMLAND
                && !upType.isAir()
                && farmlandTopRemove.contains(upType)) {
            breakAndPlaySound(up, true);
        }
    }

    private void handlePlant(@NotNull Block block, @NotNull Block up, BlockData upData) {
        if (upData instanceof Ageable || !up.getType().isAir()) return;

        Crop crop = Crop.getBySeeds(block.getType(), inventory);
        if (crop == null) return;

        up.setType(crop.getPlace());
        playBlockSound(up, upData, SoundGroup::getPlaceSound);

        inventory.removeItem(new ItemStack(crop.getSeeds()));
    }

    private void handleBoneMeal(@NotNull Ageable ageable, Crop crop, Block up) {
        if (ageable.getAge() == ageable.getMaximumAge()) return;
        if (tick % tractorTickDelay * 2 != 0 || crop == Crop.WART) return;

        boolean hasBoneMeal = inventory.contains(Material.BONE_MEAL);
        if (!hasBoneMeal && (!tractorBlockToMeal
                || !inventory.contains(Material.BONE_BLOCK)
                || inventory.firstEmpty() == -1))
            return;

        if (!hasBoneMeal) { // Convert BONE_BLOCK (1) to BONE_MEAL (9).
            inventory.removeItem(new ItemStack(Material.BONE_BLOCK));
            inventory.addItem(new ItemStack(Material.BONE_MEAL, 9));
        }

        up.applyBoneMeal(BlockFace.UP);
        inventory.removeItem(new ItemStack(Material.BONE_MEAL));
    }

    private void handleHarvest(@NotNull Ageable ageable, @NotNull Crop crop, @NotNull Block up) {
        if (ageable.getAge() != ageable.getMaximumAge()) return;

        ageable.setAge(0);
        up.setBlockData(ageable);

        ThreadLocalRandom random = ThreadLocalRandom.current();

        int minSeedAmount = crop.getMinSeedAmount();
        int maxSeedAmount = crop.getMaxSeedAmount();
        int seedAmount = minSeedAmount == 0 && maxSeedAmount == 0 ?
                0 :
                random.nextInt(minSeedAmount, maxSeedAmount + 1);

        // Save items to the inventory.

        int dropAmount = random.nextInt(crop.getMinDropAmount(), crop.getMaxDropAmount() + 1);
        saveToInventoryOrDrop(up, crop.getDrop(), dropAmount);

        if (seedAmount - 1 > 0) { // We remove a seed since when we break it we replant it.
            saveToInventoryOrDrop(up, crop.getSeeds(), seedAmount);
        }

        // Small chance of getting a poisoned potato.
        if (crop == Crop.POTATO && random.nextFloat() <= 0.02d) {
            saveToInventoryOrDrop(up, Material.POISONOUS_POTATO, 1);
        }

        breakAndPlaySound(up, false);
    }

    public void stackWheat() {
        int count = inventory.all(Material.WHEAT).values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum(), hay;

        if (count == 0 || (hay = count / 9) == 0) return;

        int remaining = count % 9;

        // Remove all wheat and add hay blocks.
        inventory.remove(Material.WHEAT);
        inventory.addItem(new ItemStack(Material.HAY_BLOCK, hay));

        // Save remaining wheat.
        if (remaining > 0) inventory.addItem(new ItemStack(Material.WHEAT, remaining));
    }

    private void playBlockSound(@NotNull Block block, @NotNull BlockData data, @NotNull Function<SoundGroup, Sound> getter) {
        SoundGroup group = data.getSoundGroup();
        velocityStand.getWorld().playSound(
                block.getLocation().add(0.5d, 0.5d, 0.5d),
                getter.apply(group),
                SoundCategory.BLOCKS,
                1.0f,
                group.getPitch());
    }

    private void saveToInventoryOrDrop(@NotNull Block block, Material drop, int amount) {
        HashMap<Integer, ItemStack> left = inventory.addItem(new ItemStack(drop, amount));
        if (left.isEmpty()) return;

        Location at = block.getLocation().add(0.5d, 0.5d, 0.5d);
        for (ItemStack item : left.values()) {
            velocityStand.getWorld().dropItemNaturally(at, item);
        }
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

        if (breakBlock(block)) {
            breakAndPlaySound(block, true);
            return;
        }

        handleTractor();

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

        if (notAllowedHere(frontOrBack)) return;

        PaperLib.teleportAsync(velocityStand, frontOrBack);
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