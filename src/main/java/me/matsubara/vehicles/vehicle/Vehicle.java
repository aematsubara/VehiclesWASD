package me.matsubara.vehicles.vehicle;

import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.Particles;
import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.jeff_media.morepersistentdatatypes.DataType;
import com.jeff_media.morepersistentdatatypes.datatypes.collections.CollectionDataType;
import com.jeff_media.morepersistentdatatypes.datatypes.serializable.ConfigurationSerializableDataType;
import io.papermc.lib.PaperLib;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import me.matsubara.vehicles.data.SoundWrapper;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.hook.WGExtension;
import me.matsubara.vehicles.manager.StandManager;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.BukkitStand;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.model.stand.ModelLocation;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.model.stand.data.ItemSlot;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.InventoryUpdate;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.util.Reflection;
import me.matsubara.vehicles.vehicle.task.VehicleTick;
import me.matsubara.vehicles.vehicle.type.Generic;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Getter
@Setter
public abstract class Vehicle implements InventoryHolder {

    public static final PersistentDataType<byte[], VehicleData> VEHICLE_DATA = new ConfigurationSerializableDataType<>(VehicleData.class);
    public static final CollectionDataType<ArrayList<VehicleData>, VehicleData> VEHICLE_DATA_LIST = DataType.asArrayList(VEHICLE_DATA);

    protected final VehiclesPlugin plugin;
    protected final Model model;
    protected final VehicleType type;

    // Config values.
    protected float minSpeed;
    protected float maxSpeed;
    protected float acceleration;
    protected float upSpeed; // HELICOPTER
    protected float downSpeed; // HELICOPTER
    protected float liftingSpeed; // PLANE
    protected int storageRows;
    protected float maxFuel;
    protected float fuelReduction;
    protected SoundWrapper engineSound;
    protected SoundWrapper refuelSound;
    protected SoundWrapper onSound;
    protected SoundWrapper offSound;
    protected SoundWrapper planeFirePrimarySound;
    protected SoundWrapper planeFireSecondarySound;
    protected SoundWrapper tankFireSound;

    private ModelLocation tractorMuffler;
    protected LivingEntity currentTarget;
    private int targetDistance = Integer.MIN_VALUE;
    private Float wheelRotation;

    protected @Getter(AccessLevel.NONE) Inventory inventory;
    protected String base64Storage;
    protected String shopDisplayName;

    protected UUID owner;
    protected Player driver;
    protected Map<Entity, String> passengers = new HashMap<>();
    protected Multimap<UUID, Pair<Material, Integer>> cooldowns = Multimaps.synchronizedMultimap(MultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build());
    protected float fuel;
    protected int tick;
    protected boolean locked;
    private VehicleTick vehicleTick;
    private Chunk previousChunk;
    private int previousSpeed = Integer.MIN_VALUE;
    private int previousProgressed = Integer.MIN_VALUE;
    protected boolean forceActionBarMessage;
    protected boolean removed;
    protected boolean fuelWarning;

    protected ArmorStand velocityStand;
    protected final List<Pair<ArmorStand, StandSettings>> chairs = new ArrayList<>();
    protected float currentSpeed;

    protected final List<Customization> customizations = new ArrayList<>();

    private final boolean pickUpNearbyEntities = Config.PICK_UP_NEARBY_ENTITIES.asBool();
    private final int tractorParticleDelay = Config.TRACTOR_PARTICLE_DELAY.asInt();
    private final int vehicleSpawnDelay = Math.max(1, Config.VEHICLE_SPAWN_DELAY.asInt());
    private final boolean rotateWheels = Config.ROTATE_WHEELS.asBool();

    private final boolean planeTargetEnabled = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_ENABLED.asBool();
    private final boolean planeTargetGlowing = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_ENABLED.asBool();
    private final String planeTargetGlowingColor = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_COLOR.asString(TARGET_COLOR.name());
    private final double planeTargetRange = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_RANGE.asDouble();
    private final boolean planeTargetIgnoreWater = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_WATER.asBool();
    private final boolean planeTargetIgnoreTamed = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_TAMED.asBool();
    private final boolean planeTargetIgnoreInvisible = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_INVISIBLE.asBool();

    private final boolean actionBarEnabled = Config.ACTION_BAR_ENABLED.asBool();
    private final int actionBarwarningDelay = (int) (Config.ACTION_BAR_WARNING_DELAY.asDouble() * 20);
    private final double actionBarfuelBelow = Config.ACTION_BAR_WARNING_FUEL_BELOW.asDouble();
    private final String actionBarGPS = Config.ACTION_BAR_GPS.asString();
    private final String actionBarPlaneTarget = Config.ACTION_BAR_PLANE_TARGET.asString();
    private final String actionBarSeparator = Config.ACTION_BAR_SEPARATOR.asString();
    private final String actionBarFuel = Config.ACTION_BAR_FUEL.asString();
    private final String actionBarSpeed = Config.ACTION_BAR_SPEED.asString();
    private final String actionBarSymbol = Config.ACTION_BAR_SYMBOL.asString();
    private final String actionBarCompleted = Config.ACTION_BAR_COMPLETED.asString();
    private final String actionBarEmpty = Config.ACTION_BAR_EMPTY.asString();
    private final String actionBarWarning = Config.ACTION_BAR_WARNING.asString();

    private static final String[] VALID_REGISTRIES = {Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS};
    private static final float WHEEL_ROTATION_SPEED_MULTIPLIER = 5.0f;

    private static final int SAVE_INVENTORY_INTERVAL = 6000;
    private static float VEHICLE_FOV = 85.0f;
    private static final ChatColor TARGET_COLOR = ChatColor.RED;

    private static final MethodHandle SET_CAN_TICK = Reflection.getMethod(ArmorStand.class, "setCanTick", MethodType.methodType(void.class, boolean.class), false, false);
    private static final MethodHandle SET_CAN_MOVE = Reflection.getMethod(ArmorStand.class, "setCanMove", MethodType.methodType(void.class, boolean.class), false, false);

    private static final Multimap<VehicleType, Triple<String, ItemStack, ItemStack>> LIGHTS = MultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build();
    private static final Map<VehicleType, Float> MODEL_WHEEL_Y = ImmutableMap.of(
            VehicleType.CYBERCAR, 90.0f,
            VehicleType.KART, 0.0f,
            VehicleType.QUAD, 90.0f);

    public static boolean MY_VEHICLES_FEATURE_MODERN = XReflection.supports(18, 1);
    public static final Map<VehicleType, Vector> VEHICLE_BOX = Map.of(
            VehicleType.BIKE, new Vector(1.5d, 1.0d, 1.5d),
            VehicleType.BOAT, new Vector(1.5d, 1.0d, 1.5d),
            VehicleType.CYBERCAR, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.HELICOPTER, new Vector(3.0d, 1.5d, 3.0d),
            VehicleType.KART, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.PLANE, new Vector(2.5d, 1.0d, 2.5d),
            VehicleType.QUAD, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.TANK, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.TRACTOR, new Vector(2.0d, 1.0d, 2.0d));

    // We want ListenMode to ignore our entities.
    public static final BiConsumer<JavaPlugin, Metadatable> LISTEN_MODE_IGNORE = (plugin, living) -> living.setMetadata("RemoveGlow", new FixedMetadataValue(plugin, true));

    static {
        ItemStack carLightsOn = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        ItemStack carLightsOff = new ItemStack(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        for (int i = 1; i <= 2; i++) {
            LIGHTS.put(VehicleType.CYBERCAR, Triple.of("FRONT_LIGHTS_" + i, carLightsOn, carLightsOff));
            LIGHTS.put(VehicleType.CYBERCAR, Triple.of("BACK_LIGHTS_" + i, carLightsOn, carLightsOff));
        }

        ItemStack frontBikeOn = PluginUtils.createHead("d9c394fd624e447d125ef3fb54d82a6fa4b0c6188e304d33352d13fbdb0c751b", true);
        ItemStack frontBikeOff = PluginUtils.createHead("933799ab30ca9b167c0ee5456c7b12b1247714ff29fb16a491e7d3a636a9aaa3", true);

        LIGHTS.put(VehicleType.BIKE, Triple.of("FRONT_LIGHTS_1", frontBikeOn, frontBikeOff));
    }

    public Vehicle(@NotNull VehiclesPlugin plugin, @NotNull VehicleData data, @NotNull Model model) {
        this.owner = data.owner();
        this.type = data.type();
        World world = model.getLocation().getWorld();
        Preconditions.checkNotNull(world);

        this.plugin = plugin;
        this.model = model;
        this.tractorMuffler = is(VehicleType.TRACTOR) ? model.getLocationByName("MUFFLER_PARTICLES") : null;
        this.wheelRotation = MODEL_WHEEL_Y.get(type);

        initConfigValues();

        this.fuel = data.fuel() != null ? Math.min(data.fuel(), maxFuel) : maxFuel;
        this.locked = data.locked();

        this.inventory = storageRows == 0 ? null : Bukkit.createInventory(this, storageRows * 9, getTitle());
        initStorage(data);

        this.shopDisplayName = data.shopDisplayName();

        resetVelocityStand(world);

        // Init customizations BEFORE spawning a chair with inventory.
        VehicleManager manager = plugin.getVehicleManager();
        manager.initCustomizations(model, customizations, type);
        manager.applyCustomization(data, model, customizations);

        // AFTER finishing customizations, now we can spawn the model.
        handleSpawn(world);

        // Fill chairs and sort them by name.
        for (ModelLocation location : model.getLocations()) {
            String partName = location.getSettings().getPartName();
            if (!partName.startsWith("CHAIR_")) continue;

            chairs.add(spawnChair(world, partName));
        }
        chairs.sort(Comparator.comparing(pair -> pair.getValue().getPartName()));
    }

    private void handleSpawn(@NotNull World world) {
        StandManager manager = plugin.getStandManager();

        if (manager.isBukkitArmorStand()) {
            model.getStands().forEach(stand -> stand.spawn(null));
            return;
        }

        for (Player player : world.getPlayers()) {
            manager.handleStandRender(this, player, player.getLocation(), StandManager.HandleCause.SPAWN);
        }
    }

    private void initStorage(@NotNull VehicleData data) {
        if (inventory == null) return;

        String base64Storage = data.base64Storage();
        if (base64Storage == null) return;

        ItemStack[] content = PluginUtils.itemStackArrayFromBase64(this.base64Storage = base64Storage);
        if (content == null) return;

        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == content.length) break;
            inventory.setItem(i, content[i]);
        }
    }

    public void resetVelocityStand(World world) {
        if (velocityStand != null) velocityStand.remove();

        velocityStand = world.spawn(
                model.getLocation().clone(),
                ArmorStand.class,
                stand -> {
                    stand.setCollidable(false);
                    stand.setVisible(false);
                    stand.setPersistent(false);
                    stand.setInvulnerable(true);
                    stand.setSilent(true);
                    stand.getPersistentDataContainer().set(plugin.getVehicleModelIdKey(), PersistentDataType.STRING, getModelUUID().toString());

                    lockSlots(stand);
                    LISTEN_MODE_IGNORE.accept(plugin, stand);

                    // Fix for paper: config -> paper-world-defaults.yml -> entities.armor-stands.tick = false
                    setTick(stand, true);
                });
    }

    public void clearChair(@NotNull LivingEntity living) {
        living.eject();
        living.remove();
    }

    private void initConfigValues() {
        FileConfiguration config = plugin.getConfig();

        String configPath = "vehicles." + type.toPath();
        this.minSpeed = (float) config.getDouble(configPath + ".min-speed");
        this.maxSpeed = (float) config.getDouble(configPath + ".max-speed");
        this.acceleration = (float) config.getDouble(configPath + ".acceleration");
        this.upSpeed = (float) config.getDouble(configPath + ".up-speed");
        this.downSpeed = (float) config.getDouble(configPath + ".down-speed");
        this.liftingSpeed = (float) config.getDouble(configPath + ".lifting-speed");
        int rows = config.getInt(configPath + ".storage-rows");
        this.storageRows = Math.min(rows, 6);
        this.maxFuel = (float) plugin.getMaxFuel(type);
        this.fuelReduction = (float) config.getDouble(configPath + ".fuel.reduction-per-second");
        this.engineSound = new SoundWrapper(config.getString(configPath + ".sounds.engine"));
        this.refuelSound = new SoundWrapper(config.getString(configPath + ".sounds.refuel"));
        this.onSound = new SoundWrapper(config.getString(configPath + ".sounds.turn-on"));
        this.offSound = new SoundWrapper(config.getString(configPath + ".sounds.turn-off"));
        this.planeFirePrimarySound = new SoundWrapper(Config.PLANE_FIRE_PRIMARY_SOUND.asString());
        this.planeFireSecondarySound = new SoundWrapper(Config.PLANE_FIRE_SECONDARY_SOUND.asString());
        this.tankFireSound = new SoundWrapper(Config.TANK_FIRE_SOUND.asString());
    }

    public @Nullable Pair<ArmorStand, StandSettings> spawnChair(World world, String chairName) {
        ModelLocation temp = model.getLocationByName(chairName);
        if (temp == null) return null;

        // Fix visual issue since 1.20.2.
        Location standLocation = temp.getLocation().clone();
        if (XReflection.supports(20, 2)) standLocation.subtract(0.0d, 0.3d, 0.0d);

        return Pair.of(world.spawn(
                standLocation,
                ArmorStand.class,
                stand -> {
                    stand.setInvulnerable(true);
                    stand.setCollidable(false);
                    stand.setPersistent(false);
                    stand.setGravity(false);
                    stand.setSilent(true);
                    stand.setInvisible(true);
                    stand.setSmall(true);

                    stand.getPersistentDataContainer().set(plugin.getVehicleModelIdKey(), PersistentDataType.STRING, getModelUUID().toString());

                    AttributeInstance attribute = stand.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (attribute != null) attribute.setBaseValue(1);

                    lockSlots(stand);
                    LISTEN_MODE_IGNORE.accept(plugin, stand);

                    // For these armor stands, we don't want the tick (if possible).
                    setTick(stand, false);
                }
        ), temp.getSettings());
    }

    public void resetRealEntities(World world) {
        if (!velocityStand.isValid()) {
            resetVelocityStand(world);
        }

        // Reset bukkit stands.
        if (plugin.getStandManager().isBukkitArmorStand()) {
            for (IStand temp : model.getStands()) {
                if (!(temp instanceof BukkitStand stand)) continue;
                stand.spawn(null);
            }
        }

        for (int i = 0; i < chairs.size(); i++) {
            Pair<ArmorStand, StandSettings> pair = chairs.get(i);

            LivingEntity living = pair.getKey();
            if (living.isValid()) continue;

            clearChair(living);

            String partName = pair.getValue().getPartName();
            chairs.set(i, spawnChair(world, partName));
        }
    }

    public boolean canRotate() {
        return canMove();
    }

    public boolean canMove() {
        return !notAllowedHere(velocityStand.getLocation()) && hasFuel();
    }

    public boolean canPlayEngineSound() {
        return canMove() && driver != null;
    }

    public boolean isOnGround() {
        return velocityStand.isOnGround();
    }

    public boolean isOnLiquid() {
        Material type = velocityStand.getLocation().getBlock().getType();
        return type == Material.LAVA || (type == Material.WATER && velocityStand.isInWater());
    }

    public boolean isMovingBackwards(PlayerInput input) {
        return getDirectionDot() < 0.0d;
    }

    public double getDirectionDot() {
        return velocityStand.getVelocity().dot(velocityStand.getLocation().getDirection());
    }

    public boolean atUnloadedChunk(@NotNull Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return !velocityStand.getWorld().isChunkLoaded(chunkX, chunkZ);
    }

    public void tick() {
        if (atUnloadedChunk(velocityStand.getLocation())) {
            if (vehicleTick != null && !vehicleTick.isCancelled()) vehicleTick.cancel();
            return;
        }

        // Here we will handle the visibility of the vehicle to the players in the world.
        // This approach should be much better than doing it in PlayerMoveEvent.
        StandManager manager = plugin.getStandManager();
        if (manager.isPacketArmorStand() && tick % vehicleSpawnDelay == 0) {
            for (Player player : velocityStand.getWorld().getPlayers()) {
                manager.handleStandRender(this, player, player.getLocation(), StandManager.HandleCause.MOVE);
            }
        }

        // Every 5 seconds we want to ensure that all parts of the model are present.
        if (tick % 100 == 0) {
            resetRealEntities(velocityStand.getWorld());
        }

        if (++tick == Integer.MAX_VALUE) tick = 0;

        handleFuel();

        PlayerInput input = plugin.getInputManager().getInput(driver);

        float side = input.sideways();

        // Rotate vehicle.
        if (canRotate() && side != 0.0f) {
            float rotation = (isMovingBackwards(input) ? side : -side) * 5.0f;

            Location standLocation = velocityStand.getLocation();
            velocityStand.setRotation(BlockUtils.yaw(standLocation.getYaw() + rotation), standLocation.getPitch());

            rotateWheels(side);
        } else if (canMove()) {
            rotateWheels(0.0f);
        }

        handleVehicleMovement(input, isOnGround());
        updateModelLocation();
        postModelUpdate();

        // Play engine sound at the velocity stand eye location.
        if (canPlayEngineSound() && tick % 5 == 0) {
            engineSound.playAt(velocityStand.getEyeLocation());
        }

        // Save inventory.
        if (storageRows > 0 && tick % SAVE_INVENTORY_INTERVAL == 0) {
            saveInventory();
        }

        // Spawn particles.
        if (driver != null
                && tractorMuffler != null
                && tractorParticleDelay > 0
                && tick % tractorParticleDelay == 0
                && hasFuel()) {
            velocityStand.getWorld().spawnParticle(
                    Particle.CAMPFIRE_COSY_SMOKE,
                    tractorMuffler.getLocation(),
                    0,
                    (Math.random() * 0.15d) * (Math.random() < 0.5d ? 1 : -1),
                    1.0d,
                    (Math.random() * 0.15d) * (Math.random() < 0.5d ? 1 : -1),
                    0.025d);
        }

        handleCurrentTarget();

        if (currentTarget != null) {
            int temp = targetDistance;
            targetDistance = (int) currentTarget.getLocation().distance(velocityStand.getLocation());
            if (temp != targetDistance) forceActionBarMessage();
        }

        if (actionBarEnabled) {
            plugin.getPool().execute(this::showSpeedAndFuel);
        }

        Chunk temp = velocityStand.getLocation().getChunk();
        if (previousChunk != null && !previousChunk.equals(temp)) {
            removeFromChunk(previousChunk);
            saveToChunk(temp);
        }

        previousChunk = temp;

        // We want to make nearby entities ride the vehicle while no one is driving and the vehicle has extra seats.
        if (locked || driver != null || !pickUpNearbyEntities) return;

        handleNearbyEntitiesRide();
    }

    private void handleCurrentTarget() {
        if (!is(VehicleType.PLANE) || !planeTargetEnabled) return;

        if (!canMove()
                || driver == null
                || driver.getInventory().getItemInMainHand().getType() != Material.FIRE_CHARGE
                || isOnGround()) {
            invalidateCurrentTarget();
            return;
        }

        // Check every 0.5 seconds.
        if (tick % 10 != 0) return;

        LivingEntity closest = findTarget(driver);
        if (closest == null) {
            invalidateCurrentTarget();
            return;
        }

        if (currentTarget != null && !currentTarget.equals(closest)) {
            invalidateCurrentTarget();
        }

        if (currentTarget == null || currentTarget.equals(closest)) {
            forceActionBarMessage();
        }

        currentTarget = closest;

        if (!planeTargetGlowing) return;

        try {
            ChatColor color = PluginUtils.getOrDefault(
                    ChatColor.class,
                    planeTargetGlowingColor,
                    TARGET_COLOR);

            plugin.getGlowingEntities().setGlowing(closest, driver, color.isColor() ? color : TARGET_COLOR);
        } catch (Throwable ignored) {
        }
    }

    public void invalidateCurrentTarget() {
        if (currentTarget == null) return;

        try {
            plugin.getGlowingEntities().unsetGlowing(currentTarget);
        } catch (Throwable ignored) {
        }

        currentTarget = null;
        targetDistance = Integer.MIN_VALUE;
        forceActionBarMessage();
    }

    public void forceActionBarMessage() {
        forceActionBarMessage = true;
    }

    // Credits to blablubbabc!
    private LivingEntity findTarget(@NotNull Player player) {
        double minDot = Double.MIN_VALUE;
        LivingEntity target = null;


        for (Entity nearby : player.getNearbyEntities(planeTargetRange, planeTargetRange, planeTargetRange)) {
            // Ignore non-living entities.
            if (!(nearby instanceof LivingEntity living)) continue;

            // Ignore armor stand (non-living).
            if (living instanceof ArmorStand) continue;

            // Ignore invalid entities or entities out of sight.
            if (!player.hasLineOfSight(living) || !living.isValid()) continue;

            // Ignore passengers of this vehicle.
            if (living instanceof Player temp && (isDriver(temp) || isPassenger(temp))) continue;

            // Ignore water mobs?
            if (living instanceof WaterMob && planeTargetIgnoreWater) {
                continue;
            }

            // Ignore tamed entities?
            if (living instanceof Tameable tameable
                    && tameable.isTamed()
                    && planeTargetIgnoreTamed) {
                continue;
            }

            // Ignore invisible entities?
            if ((living.isInvisible() || living.hasPotionEffect(PotionEffectType.INVISIBILITY))
                    && planeTargetIgnoreInvisible) {
                continue;
            }

            Location targetLocation = living.getEyeLocation();
            Location playerLocation = player.getEyeLocation();

            // Target must be in front of the vehicle.
            Location location = velocityStand.getLocation();
            float rotated = lookAt(location, targetLocation), yaw = location.getYaw();
            float angleDifference = Math.abs((rotated - yaw + 540) % 360 - 180);

            // Target is out of the FOV of the vehicle.
            if (angleDifference > VEHICLE_FOV) continue;

            Vector direction = playerLocation.getDirection().normalize();
            Vector targetDirection = targetLocation.toVector().subtract(playerLocation.toVector()).normalize();

            double dot = targetDirection.dot(direction);
            if (dot > 0.97d && dot > minDot) {
                minDot = dot;
                target = living;
            }
        }

        return target;
    }

    private float lookAt(@NotNull Location npcLocation, @NotNull Location location) {
        double xDifference = location.getX() - npcLocation.getX();
        double zDifference = location.getZ() - npcLocation.getZ();
        float yaw = (float) (-Math.atan2(xDifference, zDifference) / Math.PI * 180.0d);
        return yaw < 0 ? yaw + 360 : yaw;
    }

    private void handleNearbyEntitiesRide() {
        resetRealEntities(velocityStand.getWorld());
        if (chairs.size() <= 1) return;

        // Try to find an empty passenger seat.
        Pair<ArmorStand, StandSettings> chair = getFreePassengerSeat();
        if (chair == null) return;

        for (Entity near : velocityStand.getWorld().getNearbyEntities(getBox())) {
            // Ignore non-living entities, water entities, bosses, stands and players.
            if (!(near instanceof LivingEntity)
                    || near instanceof WaterMob
                    || near instanceof Boss
                    || near instanceof ArmorStand
                    || near instanceof Player) continue;

            // Ignore by config.
            if (!plugin.getVehicleManager().canBePickedUp(near)) continue;

            // Ignore wardens.
            if (XReflection.supports(19)
                    && near instanceof Warden) continue;

            // Ignore baby sniffers.
            if (XReflection.supports(20)
                    && near instanceof Sniffer sniffer
                    && !sniffer.isAdult()) continue;

            // Ignore big entities.
            if (!hasEnoughSpaceFor(near)) continue;

            // Ignore entities riding a vehicle or entities with passengers.
            if (near.isInsideVehicle() || !near.getPassengers().isEmpty()) continue;

            passengers.put(near, chair.getValue().getPartName());
            chair.getKey().addPassenger(near);
            break;
        }
    }

    public @Nullable Pair<ArmorStand, StandSettings> getFreePassengerSeat() {
        for (Pair<ArmorStand, StandSettings> pair : chairs) {
            String partName = pair.getValue().getPartName();
            if (partName.equals("CHAIR_1")) continue;
            if (passengers.containsValue(partName)) continue;
            return pair;
        }
        return null;
    }

    private boolean hasEnoughSpaceFor(@NotNull Entity entity) {
        return entity.getBoundingBox().getVolume() < getBox().getVolume();
    }

    public boolean isOwner(@NotNull Entity entity) {
        return isOwner(entity.getUniqueId());
    }

    public boolean isOwner(@NotNull UUID uuid) {
        return uuid.equals(owner);
    }

    public boolean isDriver(@NotNull Player player) {
        return player.equals(driver);
    }

    public boolean isPassenger(@NotNull Entity entity) {
        return passengers.containsKey(entity);
    }

    public void saveToChunk() {
        saveToChunk(velocityStand.getLocation().getChunk());
    }

    public void saveToChunk(@NotNull Chunk chunk) {
        plugin.getPool().execute(() -> saveToChunkInternal(chunk));
    }

    @ApiStatus.Internal
    public void saveToChunkInternal() {
        saveToChunkInternal(velocityStand.getLocation().getChunk());
    }

    @ApiStatus.Internal
    public void saveToChunkInternal(@NotNull Chunk chunk) {
        saveToContainer(chunk.getPersistentDataContainer());
        if (!MY_VEHICLES_FEATURE_MODERN) return;

        saveToContainer(chunk.getWorld().getPersistentDataContainer());
    }

    public void removeFromChunk() {
        removeFromChunk(velocityStand.getLocation().getChunk());
    }

    public void removeFromChunk(@NotNull Chunk chunk) {
        removeFromContainer(chunk.getPersistentDataContainer());
        if (!MY_VEHICLES_FEATURE_MODERN) return;

        removeFromContainer(chunk.getWorld().getPersistentDataContainer());
    }

    private void saveToContainer(@NotNull PersistentDataContainer container) {
        NamespacedKey key = plugin.getSaveDataKey();

        ArrayList<VehicleData> datas = container.getOrDefault(key, VEHICLE_DATA_LIST, new ArrayList<>());

        // Remove if already present and save with current data.
        datas.removeIf(data -> data.modelUniqueId().equals(getModelUUID()));
        datas.add(createSaveData());

        container.set(key, Vehicle.VEHICLE_DATA_LIST, datas);
    }

    private void removeFromContainer(@NotNull PersistentDataContainer container) {
        plugin.getPool().execute(() -> {
            NamespacedKey key = plugin.getSaveDataKey();

            ArrayList<VehicleData> datas = container.get(key, VEHICLE_DATA_LIST);
            if (datas == null) return;

            if (!datas.removeIf(data -> data.modelUniqueId().equals(getModelUUID()))) return;

            if (datas.isEmpty()) container.remove(key);
            else container.set(key, VEHICLE_DATA_LIST, datas);
        });
    }

    private void handleFuel() {
        if (!fuelEnabled()) return;

        // If the vehicle doesn't have a driver, then it's not moving.
        if ((driver == null && (!(this instanceof Helicopter helicopter) || helicopter.getOutsideDriver() == null))
                || fuel <= 0.0f
                || tick % 20 != 0) return;

        fuel = Math.max(0.0f, fuel - fuelReduction);
        if (fuel == 0.0f) toggleLights(false); // Disable lights.
    }

    public boolean fuelEnabled() {
        return maxFuel > 0.0f;
    }

    private void showSpeedAndFuel() {
        if (driver == null && (!(this instanceof Helicopter helicopter) || helicopter.getOutsideDriver() == null)) {
            return;
        }

        boolean gpsRunning = this instanceof Generic generic && generic.getGpsTick() != null;
        int distance = gpsRunning ? ((Generic) this).getCurrentDistance() : Integer.MIN_VALUE;

        int bars = 20;
        float percent = (float) (int) fuel / (int) maxFuel;
        int filled = (int) (bars * percent);

        int speedAsInt = (int) currentSpeed;
        boolean previousWarning = fuelWarning;

        if (fuel < maxFuel * actionBarfuelBelow && tick % actionBarwarningDelay == 0) {
            fuelWarning = !fuelWarning; // Alternate the color.
        }

        // We only want to update the message when the fuel or the speed changed or every 2 seconds.
        if (previousProgressed == filled
                && (gpsRunning || previousSpeed == speedAsInt)
                && (!gpsRunning || ((Generic) this).getPreviousDistance() == distance)
                && tick % 40 != 0
                && previousWarning == fuelWarning
                && !forceActionBarMessage) {
            return;
        }
        forceActionBarMessage = false;

        if (gpsRunning) {
            ((Generic) this).setPreviousDistance(distance);
        }
        previousSpeed = speedAsInt;
        previousProgressed = filled;

        String speed = gpsRunning ? actionBarGPS : String.valueOf(speedAsInt);
        String home = gpsRunning ? (((Generic) this).getGpsTick().getHomeName()) : "";

        String planeTarget;
        if (is(VehicleType.PLANE) && currentTarget != null) {
            planeTarget = actionBarPlaneTarget
                    .replace("%name%", currentTarget.getName())
                    .replace("%distance%", String.valueOf(targetDistance));
        } else planeTarget = null;

        String separator = actionBarSeparator;
        String message = (fuelEnabled() ? actionBarFuel + separator : "")
                + actionBarSpeed
                + (planeTarget != null ? separator + planeTarget : "");

        @SuppressWarnings("DataFlowIssue") Player driver = this.driver != null ?
                this.driver :
                ((Helicopter) this).getOutsideDriver();
        if (driver == null) return;

        BaseComponent[] components = TextComponent.fromLegacyText(PluginUtils.translate(message
                .replace("%bar%", getProgressBar(filled, bars, actionBarfuelBelow))
                .replace("%speed%", speed)
                .replace("%distance%", String.valueOf(distance))
                .replace("%home%", home)));

        VehicleManager manager = plugin.getVehicleManager();
        manager.cancelKeybindTask(driver);

        driver.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);

        for (Entity passenger : getPassengers().keySet()) {
            if (!(passenger instanceof Player player)) continue;

            manager.cancelKeybindTask(player);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }
    }

    public String getProgressBar(int filled, int bars, double fuelBelowPercentage) {
        String usedColor;
        if (fuel < maxFuel * fuelBelowPercentage) { // The remaining fuel is less than the X% of the total.
            usedColor = fuelWarning ? actionBarWarning : actionBarEmpty;
        } else {
            usedColor = actionBarEmpty;
            fuelWarning = false;
        }

        return Strings.repeat(actionBarCompleted + actionBarSymbol, filled) + Strings.repeat(usedColor + actionBarSymbol, bars - filled);
    }

    public void rotateWheels(float side) {
        if (!rotateWheels || wheelRotation == null || model == null) return;

        Float wheelY = MODEL_WHEEL_Y.get(type);

        float minWheelY = wheelY - 45.0f;
        float maxWheelY = wheelY + 45.0f;

        boolean notMoving = side == 0.0f;
        boolean right = side < 0.0f;

        if (notMoving && wheelRotation.equals(wheelY)) return; // Already centered.

        if (notMoving) {
            wheelRotation = createRotation(wheelRotation, wheelRotation < wheelY, 0.95f, -0.95f, wheelY, wheelY);
        } else {
            wheelRotation = createRotation(wheelRotation, right, Math.abs(side), -side, minWheelY, maxWheelY);
        }

        EulerAngle head = new EulerAngle(0.0d, Math.toRadians(wheelRotation), 0.0d);

        plugin.getPool().execute(() -> {
            Set<Player> to = getSeeingPlayers();
            for (int i = 1; i <= 2; i++) {
                IStand wheel = model.getStandByName("FRONT_WHEELS_" + i);
                if (wheel == null) continue;

                wheel.getSettings().setHeadPose(head);
                wheel.sendMetadata(to);
            }
        });
    }

    private float createRotation(float previousY, boolean right, float rightRot, float leftRot, float minY, float maxY) {
        float y = previousY + (right ? rightRot : leftRot) * WHEEL_ROTATION_SPEED_MULTIPLIER;
        return right ? Math.min(y, maxY) : Math.max(y, minY);
    }

    protected void postModelUpdate() {

    }

    protected abstract void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround);

    public void setDriver(@Nullable Player driver) {
        toggleLights((this.driver = driver) != null && hasFuel());
    }

    public void setDriverRaw(@Nullable Player driver) {
        this.driver = driver;
    }

    public boolean hasFuel() {
        return !fuelEnabled() || fuel > 0.0f;
    }

    public void toggleLights(boolean on) {
        // Play sound sync.
        plugin.getServer().getScheduler().runTask(plugin, () -> (on ? onSound : offSound).playAt(model.getLocation()));

        Collection<Triple<String, ItemStack, ItemStack>> lights = LIGHTS.get(type);
        if (lights.isEmpty()) return;

        plugin.getPool().execute(() -> {
            Set<Player> to = getSeeingPlayers();
            for (Triple<String, ItemStack, ItemStack> triple : lights) {
                IStand stand = model.getStandByName(triple.getLeft());
                if (stand == null) continue;

                ItemStack item = on ? triple.getMiddle() : triple.getRight();
                stand.getSettings().getEquipment().put(ItemSlot.HEAD, item);

                stand.sendEquipment(to);
            }
        });
    }

    protected BlockCollisionResult isPassable(@NotNull Block block) {
        Material type = block.getType();

        if (BlockUtils.BUGGY_HEIGHTS.contains(type)) {
            return BlockCollisionResult.DENY;
        }

        if (block.isPassable()
                || type.isAir()
                || (!type.isSolid() && block.getCollisionShape().getBoundingBoxes().isEmpty())) {
            return BlockCollisionResult.ALLOW;
        }

        if (BlockUtils.getMaterialHeight(block) != Double.MIN_VALUE) {
            return BlockCollisionResult.UP;
        }

        return BlockCollisionResult.DENY;
    }

    public enum BlockCollisionResult {
        ALLOW,
        DENY,
        UP;

        public boolean allow() {
            return this == ALLOW;
        }

        public boolean deny() {
            return this == DENY;
        }

        public boolean up() {
            return this == UP;
        }

    }

    protected void reduceSpeed(float minSpeed) {
        currentSpeed = Math.max(minSpeed, currentSpeed - acceleration * 5.0f);
    }

    private void handleBukkitStands(Location location) {
        Set<CompletableFuture<Boolean>> futures = new HashSet<>();

        // First, we teleport the parts of the model (PAPER = ASYNC / SPIGOT = SYNC).
        for (IStand temp : model.getStands()) {
            if (!(temp instanceof BukkitStand stand)) continue;

            Location correct = BlockUtils.getCorrectLocation(driver, type, location, temp.getSettings());
            if (temp.getLocation().equals(correct)) continue;

            futures.add(PaperLib.teleportAsync(stand.getStand(), correct));
        }

        // Once ALL the model parts have been teleported, we want to teleport the locations and chairs (SYNC).
        CompletableFuture
                .allOf(futures.toArray(CompletableFuture[]::new))
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    teleportLocations(location);
                    teleportChairs(location);
                }));
    }

    private void updateModelLocation() {
        // Update model location based on the velocity stand.
        Location location = velocityStand.getLocation();
        if (model.getLocation().equals(location)
                && !is(VehicleType.TANK)
                && !is(VehicleType.HELICOPTER)) return;

        model.setLocation(location);

        if (plugin.getStandManager().isBukkitArmorStand()) {
            handleBukkitStands(location);
            return;
        }

        // First, we teleport the parts of the model (ASYNC).
        Set<Player> to = getSeeingPlayers();
        model.getPlugin().getPool().submit(() -> handleLocations(model.getStands(),
                location,
                IStand::getSettings,
                IStand::getLocation,
                (stand, temp) -> stand.teleport(to, temp)));

        // Once ALL the model parts have been teleported, we want to teleport the locations and chairs (SYNC).
        teleportLocations(location);
        teleportChairs(location);
    }

    private void teleportLocations(Location location) {
        handleLocations(
                model.getLocations(),
                location,
                ModelLocation::getSettings,
                ModelLocation::getLocation,
                ModelLocation::setLocation);
    }

    private void teleportChairs(Location location) {
        for (Pair<ArmorStand, StandSettings> chair : chairs) {
            if (chair != null) teleportChair(location, chair);
        }
    }

    public Set<Player> getSeeingPlayers() {
        if (plugin.getStandManager().isBukkitArmorStand()) return null;

        Set<Player> to = new HashSet<>(velocityStand.getWorld().getPlayers());
        to.removeIf(player -> model.getOut().contains(player.getUniqueId()));
        return to;
    }

    private <T> void handleLocations(@NotNull Collection<T> list,
                                     Location location,
                                     Function<T, StandSettings> getSettings,
                                     Function<T, Location> getLocation,
                                     BiConsumer<T, Location> setLocation) {
        for (T temp : list) {
            Location correct = BlockUtils.getCorrectLocation(driver, type, location, getSettings.apply(temp));
            if (getLocation.apply(temp).equals(correct)) continue;
            setLocation.accept(temp, correct);
        }
    }

    public boolean hasWeapon() {
        return false;
    }

    public boolean notAllowedHere(Location location) {
        if (XReflection.supports(18, 2)
                && driver != null
                && driver.isValid()
                && driver.isOnline()) {
            WorldBorder border = driver.getWorldBorder();
            if (border != null && !border.isInside(location)) return true;
        }

        WorldBorder border = velocityStand.getWorld().getWorldBorder();
        if (!border.isInside(location)) return true;

        WGExtension wgExtension = plugin.getWgExtension();
        if (wgExtension == null) return false;

        return !wgExtension.canMoveHere(driver, location);
    }

    protected void teleportChair(Location location, @NotNull Pair<ArmorStand, StandSettings> pair) {
        LivingEntity entity = pair.getKey();

        Location correctLocation = BlockUtils.getCorrectLocation(driver, type, location, pair.getValue());
        if (entity.getLocation().equals(correctLocation)) return;

        PluginUtils.teleportWithPassengers(entity, correctLocation);
    }

    public boolean is(VehicleType type) {
        return this.type == type;
    }

    public Pair<ArmorStand, StandSettings> getChair(int chair) {
        if (chairs.isEmpty() || chair < 0 || chair > chairs.size() - 1) return null;
        return chairs.get(chair);
    }

    public BoundingBox getBox() {
        ModelLocation center = model.getLocationByName("CENTER");

        Vector box = VEHICLE_BOX.get(type);
        double x = box.getX();
        double y = box.getY();
        double z = box.getZ();

        Location at = (center != null ? center.getLocation() : model.getLocation()).clone().add(0.0d, y, 0.0d);
        return BoundingBox.of(at, x, y, z);
    }

    public void showBox(@NotNull Player player, Color color) {
        BoundingBox box = getBox();
        World world = player.getWorld();

        Particles.structuredCube(
                box.getMin().toLocation(world),
                box.getMax().toLocation(world),
                0.5d,
                ParticleDisplay.of(XParticle.DUST)
                        .withColor(color, 1.5f)
                        .onlyVisibleTo(player));
    }

    public int getFuelDepositSlot() {
        return 13;
    }

    public VehicleData createSaveData() {
        return createSaveData(true);
    }

    public VehicleData createSaveData(boolean inventory) {
        return new VehicleData(
                owner,
                fuel,
                locked,
                getModelUUID(),
                velocityStand.getLocation().clone(),
                type,
                inventory ? saveInventory() : base64Storage,
                shopDisplayName,
                getCustomizationChanges(),
                this instanceof Generic generic ?
                        generic.getTractorMode() :
                        null);
    }

    public String saveInventory() {
        return base64Storage = PluginUtils.itemStackArrayToBase64(inventory.getContents());
    }

    public ItemStack createVehicleItem() {
        return plugin.createVehicleItem(type, createSaveData());
    }

    public Map<String, Material> getCustomizationChanges() {
        Map<String, Material> changes = new HashMap<>();
        for (Customization customization : customizations) {
            Material newType = customization.getNewType();
            if (newType != null && newType != customization.getDefaultType()) {
                changes.put(customization.getCustomizationName(), newType);
            }
        }
        return changes;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private @NotNull String getTitle() {
        String typeFormatted = plugin.getVehicleTypeFormatted(type);
        String storageTitle = plugin.getConfig().getString("gui.vehicle.title", typeFormatted)
                .replace("%owner%", Optional.of(Bukkit.getOfflinePlayer(owner))
                        .map(OfflinePlayer::getName)
                        .orElse("???"))
                .replace("%type%", typeFormatted);
        return PluginUtils.translate(storageTitle);
    }

    public void openInventory(@NotNull Player player) {
        player.openInventory(inventory);
        InventoryUpdate.updateInventory(player, getTitle());
    }

    public UUID getModelUUID() {
        return model.getModelUniqueId();
    }

    public static void setTick(ArmorStand stand, boolean tick) {
        try {
            if (SET_CAN_TICK != null) SET_CAN_TICK.invoke(stand, tick);
            if (SET_CAN_MOVE != null) SET_CAN_MOVE.invoke(stand, tick);
        } catch (Throwable ignored) {

        }
    }

    public static void lockSlots(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType type : ArmorStand.LockType.values()) {
                stand.addEquipmentLock(slot, type);
            }
        }
    }
}