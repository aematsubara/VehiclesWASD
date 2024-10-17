package me.matsubara.vehicles.vehicle;

import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.Particles;
import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.jeff_media.morepersistentdatatypes.DataType;
import com.jeff_media.morepersistentdatatypes.datatypes.collections.CollectionDataType;
import com.jeff_media.morepersistentdatatypes.datatypes.serializable.ConfigurationSerializableDataType;
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
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

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

    protected LivingEntity currentTarget;
    private int targetDistance = Integer.MIN_VALUE;

    protected @Getter(AccessLevel.NONE) Inventory inventory;
    protected String base64Storage;
    protected String shopDisplayName;

    protected UUID owner;
    protected UUID driver;
    protected Map<UUID, String> passengers = new HashMap<>();
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
    private boolean onFire;

    protected ArmorStand velocityStand;
    protected final List<Pair<ArmorStand, StandSettings>> chairs = new ArrayList<>();
    protected float currentSpeed;

    protected final List<Customization> customizations = new ArrayList<>();

    private static final String[] VALID_REGISTRIES = {Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS};
    private static final double WHEEL_ROTATION_SPEED_MULTIPLIER = 5.0d;

    private static float VEHICLE_FOV = 85.0f;
    private static final ChatColor TARGET_COLOR = ChatColor.RED;
    private static final MethodHandle SET_CAN_TICK = Reflection.getMethod(ArmorStand.class, "setCanTick", MethodType.methodType(void.class, boolean.class), false, false);
    private static final Multimap<String, Triple<String, ItemStack, ItemStack>> LIGHTS = MultimapBuilder.hashKeys().arrayListValues().build();
    private static final Map<String, Float> MODEL_WHEEL_Y = Map.of(
            "cybercar", 90.0f,
            "quad", 90.0f,
            "kart", 0.0f);

    public static boolean MY_VEHICLES_FEATURE_MODERN = XReflection.supports(18, 1);
    public static final Map<VehicleType, Vector> VEHICLE_BOX = Map.of(
            VehicleType.BIKE, new Vector(1.5d, 1.0d, 1.5d),
            VehicleType.BOAT, new Vector(1.5d, 1.0d, 1.5d),
            VehicleType.CYBERCAR, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.HELICOPTER, new Vector(3.0d, 1.5d, 3.0d),
            VehicleType.KART, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.PLANE, new Vector(2.5d, 1.0d, 2.5d),
            VehicleType.QUAD, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.TANK, new Vector(2.0d, 1.0d, 2.0d));

    // We want ListenMode to ignore our entities.
    public static final BiConsumer<JavaPlugin, Metadatable> LISTEN_MODE_IGNORE = (plugin, living) -> living.setMetadata("RemoveGlow", new FixedMetadataValue(plugin, true));

    static {
        ItemStack carLightsOn = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        ItemStack carLightsOff = new ItemStack(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        for (int i = 1; i <= 2; i++) {
            LIGHTS.put("cybercar", Triple.of("FRONT_LIGHTS_" + i, carLightsOn, carLightsOff));
            LIGHTS.put("cybercar", Triple.of("BACK_LIGHTS_" + i, carLightsOn, carLightsOff));
        }

        ItemStack frontBikeOn = PluginUtils.createHead("d9c394fd624e447d125ef3fb54d82a6fa4b0c6188e304d33352d13fbdb0c751b", true);
        ItemStack frontBikeOff = PluginUtils.createHead("933799ab30ca9b167c0ee5456c7b12b1247714ff29fb16a491e7d3a636a9aaa3", true);

        LIGHTS.put("bike", Triple.of("FRONT_LIGHTS_1", frontBikeOn, frontBikeOff));
    }

    public Vehicle(@NotNull VehiclesPlugin plugin, @NotNull VehicleData data, @NotNull Model model) {
        this.owner = data.owner();
        this.type = data.type();
        World world = model.getLocation().getWorld();
        Preconditions.checkNotNull(world);

        this.plugin = plugin;
        this.model = model;

        initConfigValues();

        this.fuel = data.fuel() != null ? Math.min(data.fuel(), maxFuel) : maxFuel;
        this.locked = data.locked();

        this.inventory = storageRows == 0 ? null : Bukkit.createInventory(this, storageRows * 9, getTitle());
        initStorage(data);

        this.shopDisplayName = data.shopDisplayName();

        resetVelocityStand(world);

        // Init customizations BEFORE spawning a chair with inventory.
        plugin.getVehicleManager().initCustomizations(model, customizations, type);

        Map<String, Material> changes = data.customizationChanges();
        if (changes != null) {
            for (Map.Entry<String, Material> entry : changes.entrySet()) {
                plugin.getVehicleManager().applyCustomization(model, customizations, entry.getKey(), entry.getValue());
            }
        }

        // Cache every stand metadata, except for wheels (otherwise we won't be able to rotate them).
        for (PacketStand stand : model.getStands()) {
            if (stand.getSettings().getPartName().startsWith("FRONT_WHEELS_")) continue;
            stand.setCacheMetadata(true);
        }

        // AFTER finishing customizations, now we can spawn the model.
        model.getStands().forEach(PacketStand::spawn);

        // Fill chairs.
        for (PacketStand stand : model.getStands()) {
            String partName = stand.getSettings().getPartName();
            if (!partName.startsWith("CHAIR_")) continue;

            chairs.add(spawnChair(world, partName));
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
                    if (SET_CAN_TICK != null) {
                        try {
                            SET_CAN_TICK.invoke(stand, true);
                        } catch (Throwable ignored) {

                        }
                    }
                });
    }

    public void clearChair(@NotNull LivingEntity living) {
        living.eject();
        living.remove();
    }

    private void initConfigValues() {
        FileConfiguration config = plugin.getConfig();

        String configPath = "vehicles." + model.getName().replace("_", "-");
        this.minSpeed = (float) config.getDouble(configPath + ".min-speed");
        this.maxSpeed = (float) config.getDouble(configPath + ".max-speed");
        this.acceleration = (float) config.getDouble(configPath + ".acceleration");
        this.upSpeed = (float) config.getDouble(configPath + ".up-speed");
        this.downSpeed = (float) config.getDouble(configPath + ".down-speed");
        this.liftingSpeed = (float) config.getDouble(configPath + ".lifting-speed");
        int rows = config.getInt(configPath + ".storage-rows");
        this.storageRows = Math.min(rows, 6);
        this.maxFuel = (float) config.getDouble(configPath + ".fuel.max-fuel", 0.0f);
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
        PacketStand temp = model.getByName(chairName);
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

                    LISTEN_MODE_IGNORE.accept(plugin, stand);
                }
        ), temp.getSettings());
    }

    public void resetRealEntities(World world) {
        if (!velocityStand.isValid()) {
            resetVelocityStand(world);
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

    private void lockSlots(ArmorStand stand) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (ArmorStand.LockType type : ArmorStand.LockType.values()) {
                stand.addEquipmentLock(slot, type);
            }
        }
    }

    public boolean canRotate() {
        return canMove();
    }

    public boolean canMove() {
        return !notAllowedHere(velocityStand.getLocation()) && hasFuel();
    }

    public boolean canPlaySound() {
        return canMove() && driver != null;
    }

    public boolean isOnGround() {
        return velocityStand.isOnGround();
    }

    public boolean isOnLiquid() {
        return velocityStand.getLocation().getBlock().isLiquid();
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
        updateModelLocation(model, velocityStand.getLocation());
        postModelUpdate();

        if (canPlaySound() && tick % 5 == 0) {
            engineSound.playAt(model.getLocation());
        }

        handleCurrentTarget();

        if (currentTarget != null) {
            int temp = targetDistance;
            targetDistance = (int) currentTarget.getLocation().distance(velocityStand.getLocation());
            if (temp != targetDistance) forceActionBarMessage();
        }

        if (Config.ACTION_BAR_ENABLED.asBool()) showSpeedAndFuel();

        Chunk temp = velocityStand.getLocation().getChunk();
        if (previousChunk != null && !previousChunk.equals(temp)) {
            removeFromChunk(previousChunk);
            saveToChunk(temp);

            // Since the driver (or passenger) is inside a vehicle, PlayerMoveEvent isn't called when driving.
            renderOtherVehicles(driver);
            passengers.keySet().forEach(this::renderOtherVehicles);
        }

        previousChunk = temp;

        handleVehicleOnFire();

        // We want to make nearby entities ride the vehicle while no one is driving and the vehicle has extra seats.
        if (locked || driver != null || !Config.PICK_UP_NEARBY_ENTITIES.asBool()) return;

        handleNearbyEntitiesRide();
    }

    private void handleVehicleOnFire() {
        Block block = velocityStand.getLocation().getBlock();

        boolean setOnFire = block.getType() == Material.LAVA;
        if (onFire == setOnFire) return;

        onFire = setOnFire;

        for (PacketStand stand : model.getStands()) {
            // Ignore same state.
            StandSettings settings = stand.getSettings();
            if (settings.isFire() == setOnFire) continue;

            // Ignore non-named.
            String name = settings.getPartName();
            if (name == null
                    || name.startsWith("CHAIR_")
                    || name.equals("INTERACTIONS")) continue;

            settings.setFire(setOnFire);
            if (stand.isCacheMetadata()) {
                stand.setCachedMetadata(null);
            }
            stand.updateMetadata();
        }
    }

    private void handleCurrentTarget() {
        if (!is(VehicleType.PLANE) || !Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_ENABLED.asBool()) return;

        Player player;
        if (!canMove()
                || driver == null
                || (player = Bukkit.getPlayer(driver)) == null
                || player.getInventory().getItemInMainHand().getType() != Material.FIRE_CHARGE
                || isOnGround()) {
            invalidateCurrentTarget();
            return;
        }

        // Check every 0.5 seconds.
        if (tick % 10 != 0) return;

        LivingEntity closest = findTarget(player);
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

        if (!Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_ENABLED.asBool()) return;

        try {
            ChatColor color = PluginUtils.getOrDefault(
                    ChatColor.class,
                    Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_COLOR.asString(TARGET_COLOR.name()),
                    TARGET_COLOR);

            plugin.getGlowingEntities().setGlowing(closest, player, color.isColor() ? color : TARGET_COLOR);
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

        double range = Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_RANGE.asDouble();
        for (Entity nearby : player.getNearbyEntities(range, range, range)) {
            // Ignore non-living entities.
            if (!(nearby instanceof LivingEntity living)) continue;

            // Ignore armor stand (non-living).
            if (living instanceof ArmorStand) continue;

            // Ignore invalid entities or entities out of sight.
            if (!player.hasLineOfSight(living) || !living.isValid()) continue;

            // Ignore passengers of this vehicle.
            if (isDriver(living) || isPassenger(living)) continue;

            // Ignore water mobs?
            if (living instanceof WaterMob
                    && Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_WATER.asBool()) {
                continue;
            }

            // Ignore tamed entities?
            if (living instanceof Tameable tameable
                    && tameable.isTamed()
                    && Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_TAMED.asBool()) {
                continue;
            }

            // Ignore invisible entities?
            if ((living.isInvisible() || living.hasPotionEffect(PotionEffectType.INVISIBILITY))
                    && Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_INVISIBLE.asBool()) {
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

            passengers.put(near.getUniqueId(), chair.getValue().getPartName());
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

    public boolean isDriver(@NotNull Entity entity) {
        return isDriver(entity.getUniqueId());
    }

    public boolean isDriver(@NotNull UUID uuid) {
        return uuid.equals(driver);
    }

    public boolean isPassenger(@NotNull Entity entity) {
        return isPassenger(entity.getUniqueId());
    }

    public boolean isPassenger(UUID uuid) {
        return passengers.containsKey(uuid);
    }

    private void renderOtherVehicles(UUID uuid) {
        if (uuid == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        StandManager manager = plugin.getStandManager();
        manager.handleStandRender(player, player.getLocation(), false);
    }

    public void saveToChunk() {
        saveToChunk(velocityStand.getLocation().getChunk());
    }

    public void saveToChunk(@NotNull Chunk chunk) {
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
        NamespacedKey key = plugin.getSaveDataKey();

        ArrayList<VehicleData> datas = container.get(key, VEHICLE_DATA_LIST);
        if (datas == null) return;

        if (!datas.removeIf(data -> data.modelUniqueId().equals(getModelUUID()))) return;

        if (datas.isEmpty()) container.remove(key);
        else container.set(key, VEHICLE_DATA_LIST, datas);
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

        int warningDelay = (int) (Config.ACTION_BAR_WARNING_DELAY.asDouble() * 20);
        double fuelBelowPercentage = Config.ACTION_BAR_WARNING_FUEL_BELOW.asDouble();
        if (fuel < maxFuel * fuelBelowPercentage && tick % warningDelay == 0) {
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

        String speed = gpsRunning ? Config.ACTION_BAR_GPS.asString() : String.valueOf(speedAsInt);
        String home = gpsRunning ? (((Generic) this).getGpsTick().getHomeName()) : "";

        String planeTarget;
        if (is(VehicleType.PLANE) && currentTarget != null) {
            planeTarget = Config.ACTION_BAR_PLANE_TARGET.asString()
                    .replace("%name%", currentTarget.getName())
                    .replace("%distance%", String.valueOf(targetDistance));
        } else planeTarget = null;

        String separator = Config.ACTION_BAR_SEPARATOR.asString();
        String message = (fuelEnabled() ? Config.ACTION_BAR_FUEL.asString() + separator : "")
                + Config.ACTION_BAR_SPEED.asString()
                + (planeTarget != null ? separator + planeTarget : "");

        @SuppressWarnings("DataFlowIssue") Player driver = Bukkit.getPlayer(this.driver != null ?
                this.driver :
                ((Helicopter) this).getOutsideDriver());
        if (driver == null) return;

        BaseComponent[] components = TextComponent.fromLegacyText(PluginUtils.translate(message
                .replace("%bar%", getProgressBar(filled, bars, fuelBelowPercentage))
                .replace("%speed%", speed)
                .replace("%distance%", String.valueOf(distance))
                .replace("%home%", home)));

        VehicleManager vehicleManager = plugin.getVehicleManager();
        vehicleManager.cancelKeybindTask(driver);

        driver.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);

        for (UUID uuid : getPassengers().keySet()) {
            Player passenger = Bukkit.getPlayer(uuid);
            if (passenger == null) continue;

            vehicleManager.cancelKeybindTask(passenger);
            passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }
    }

    public String getProgressBar(int filled, int bars, double fuelBelowPercentage) {
        String symbol = Config.ACTION_BAR_SYMBOL.asString();
        String completed = Config.ACTION_BAR_COMPLETED.asString();
        String empty = Config.ACTION_BAR_EMPTY.asString();
        String warning = Config.ACTION_BAR_WARNING.asString();

        String usedColor;
        if (fuel < maxFuel * fuelBelowPercentage) { // The remaining fuel is less than the X% of the total.
            usedColor = fuelWarning ? warning : empty;
        } else {
            usedColor = empty;
            fuelWarning = false;
        }

        return Strings.repeat(completed + symbol, filled) + Strings.repeat(usedColor + symbol, bars - filled);
    }

    public void rotateWheels(float side) {
        if (model == null) return;

        Float wheelY = MODEL_WHEEL_Y.get(model.getName());
        if (wheelY == null) return;

        float minWheelY = wheelY - 45.0f;
        float maxWheelY = wheelY + 45.0f;

        boolean notMoving = side == 0.0f;
        boolean right = side < 0.0f;

        for (int i = 1; i <= 2; i++) {
            PacketStand frontWheel = model.getByName("FRONT_WHEELS_" + i);
            if (frontWheel == null) continue;

            EulerAngle head = frontWheel.getSettings().getHeadPose();

            double previousY = Math.toDegrees(head.getY());
            if (notMoving && previousY == wheelY) continue; // Already centered.

            double rotation;
            if (notMoving) {
                rotation = createRotation(previousY, previousY < wheelY, 0.95d, -0.95d, wheelY, wheelY);
            } else {
                rotation = createRotation(previousY, right, Math.abs(side), -side, minWheelY, maxWheelY);
            }

            frontWheel.getSettings().setHeadPose(head.setY(Math.toRadians(rotation)));
            frontWheel.updateMetadata();
        }
    }

    private double createRotation(double previousY, boolean right, double rightRot, double leftRot, double minY, double maxY) {
        double y = previousY + (right ? rightRot : leftRot) * WHEEL_ROTATION_SPEED_MULTIPLIER;
        return right ? Math.min(y, maxY) : Math.max(y, minY);
    }

    protected void postModelUpdate() {

    }

    protected abstract void handleVehicleMovement(@NotNull PlayerInput input, boolean onGround);

    public void setDriver(@Nullable UUID driver) {
        toggleLights((this.driver = driver) != null && hasFuel());
    }

    public void setDriverRaw(@Nullable UUID driver) {
        this.driver = driver;
    }

    public boolean hasFuel() {
        return !fuelEnabled() || fuel > 0.0f;
    }

    public void toggleLights(boolean on) {
        for (Triple<String, ItemStack, ItemStack> triple : LIGHTS.get(model.getName())) {
            PacketStand stand = model.getByName(triple.getLeft());
            if (stand == null) continue;

            ItemStack item = new ItemStack(on ? triple.getMiddle() : triple.getRight());
            stand.setEquipment(item, PacketStand.ItemSlot.HEAD);
        }

        // Play sound sync.
        plugin.getServer().getScheduler().runTask(plugin, () -> (on ? onSound : offSound).playAt(model.getLocation()));
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

    private void updateModelLocation(@NotNull Model model, Location location) {
        // Update model location based on the moving stand.
        model.setLocation(location);

        for (PacketStand stand : model.getStands()) {
            Location correctLocation = BlockUtils.getCorrectLocation(driver, type, location, stand.getSettings());
            if (stand.getLocation().equals(correctLocation)) continue;

            // No need to send packets if the stand is in the same location.
            stand.teleport(correctLocation);
        }

        for (Pair<ArmorStand, StandSettings> chair : chairs) {
            if (chair != null) teleportChair(location, chair);
        }
    }

    public boolean hasWeapon() {
        return false;
    }

    public boolean notAllowedHere(Location location) {
        Player driver = this.driver != null ? Bukkit.getPlayer(this.driver) : null;
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
        PacketStand center = model.getByName("CENTER");

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
        return createSaveData(velocityStand.getLocation().clone());
    }

    public VehicleData createSaveData(Location location) {
        Map<String, Material> customizationChanges = new HashMap<>();
        for (Customization customization : customizations) {
            Material newType = customization.getNewType();
            if (newType != null && newType != customization.getDefaultType()) {
                customizationChanges.put(customization.getCustomizationName(), newType);
            }
        }

        return new VehicleData(
                owner,
                fuel,
                locked,
                getModelUUID(),
                location,
                type,
                base64Storage,
                shopDisplayName,
                customizationChanges);
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
}