package me.matsubara.vehicles.vehicle;

import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.XParticle;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
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
import me.matsubara.vehicles.manager.StandManager;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
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
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.*;

@Getter
@Setter
public abstract class Vehicle implements InventoryHolder {

    public static final PersistentDataType<byte[], VehicleData> VEHICLE_DATA = new ConfigurationSerializableDataType<>(VehicleData.class);
    public static final CollectionDataType<ArrayList<VehicleData>, VehicleData> VEHICLE_DATA_LIST = DataType.asArrayList(Vehicle.VEHICLE_DATA);

    protected final VehiclesPlugin plugin;
    protected final Model model;
    protected final VehicleType type;

    // Config values.
    protected float minSpeed;
    protected float maxSpeed;
    protected float acceleration;
    protected float upSpeed;
    protected float downSpeed;
    protected int storageRows;
    protected float maxFuel;
    protected float fuelReduction;
    protected SoundWrapper engineSound;
    protected SoundWrapper refuelSound;
    protected SoundWrapper onSound;
    protected SoundWrapper offSound;

    protected @Getter(AccessLevel.NONE) Inventory inventory;
    protected String base64Storage;
    protected String shopDisplayName;

    protected UUID owner;
    protected UUID driver;
    protected Map<UUID, String> passengers = new HashMap<>();
    protected float fuel;
    protected int tick;
    protected boolean locked;
    private VehicleTick vehicleTick;
    private Chunk previousChunk;
    private int previousSpeed = Integer.MIN_VALUE;
    private int previousProgressed = Integer.MIN_VALUE;
    protected boolean forceActionBarMessage;

    protected ArmorStand velocityStand;
    protected final List<Pair<LivingEntity, StandSettings>> chairs = new ArrayList<>();
    protected float currentSpeed;

    protected final List<Customization> customizations = new ArrayList<>();

    private static final String[] VALID_REGISTRIES = {Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS};
    private static final double WHEEL_ROTATION_SPEED_MULTIPLIER = 5.0d;

    private static final Multimap<String, Triple<String, ItemStack, ItemStack>> LIGHTS = MultimapBuilder.hashKeys().arrayListValues().build();
    private static final Map<String, Float> MODEL_WHEEL_Y = Map.of(
            "cybercar", 90.0f,
            "quad", 90.0f,
            "kart", 0.0f);

    public static final Map<VehicleType, Vector> VEHICLE_BOX = Map.of(
            VehicleType.BIKE, new Vector(1.5d, 1.0d, 1.5d),
            VehicleType.CYBERCAR, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.KART, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.QUAD, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.TANK, new Vector(2.0d, 1.0d, 2.0d),
            VehicleType.HELICOPTER, new Vector(3.0d, 1.5d, 3.0d),
            VehicleType.BOAT, new Vector(1.5d, 1.0d, 1.5d));

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

        String storageTitle = plugin.getConfig().getString("gui.vehicle.title");
        String typeFormatted = plugin.getVehicleTypeFormatted(type);

        this.inventory = storageRows == 0 ? null : Bukkit.createInventory(this, storageRows * 9, storageTitle != null ? PluginUtils.translate(storageTitle
                .replace("%owner%", Optional.of(Bukkit.getOfflinePlayer(owner)).map(OfflinePlayer::getName).orElse("???"))
                .replace("%type%", typeFormatted)) : typeFormatted);

        initStorage(data);

        this.shopDisplayName = data.shopDisplayName();

        resetVelocityStand(world);

        // Init customizations BEFORE spawning llama chair with inventory.
        plugin.getVehicleManager().initCustomizations(model, customizations, type);

        Map<String, Material> changes = data.customizationsChanges();
        if (changes != null) {
            for (Map.Entry<String, Material> entry : changes.entrySet()) {
                plugin.getVehicleManager().applyCustomization(model, customizations, entry.getKey(), entry.getValue());
            }
        }

        // Cache every stand metadata, except for wheels.
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

            chairs.add(spawnLlamaChair(world, partName));
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
                    stand.getPersistentDataContainer().set(plugin.getVehicleModelIdKey(), PersistentDataType.STRING, model.getModelUniqueId().toString());
                    lockSlots(stand);
                });
    }

    public void clearChair(LivingEntity living) {
        if (living instanceof InventoryHolder holder) {
            holder.getInventory().clear();
        }
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
        int rows = config.getInt(configPath + ".storage-rows");
        this.storageRows = Math.min(rows, 6);
        this.maxFuel = (float) config.getDouble(configPath + ".fuel.max-fuel", 0.0f);
        this.fuelReduction = (float) config.getDouble(configPath + ".fuel.reduction-per-second");
        this.engineSound = new SoundWrapper(config.getString(configPath + ".sounds.engine"));
        this.refuelSound = new SoundWrapper(config.getString(configPath + ".sounds.refuel"));
        this.onSound = new SoundWrapper(config.getString(configPath + ".sounds.turn-on"));
        this.offSound = new SoundWrapper(config.getString(configPath + ".sounds.turn-off"));
    }

    public @Nullable Pair<LivingEntity, StandSettings> spawnLlamaChair(World world, String chairName) {
        PacketStand temp = model.getByName(chairName);
        if (temp == null) return null;

        return Pair.of(world.spawn(
                temp.getLocation(),
                Llama.class,
                llama -> {
                    llama.setInvulnerable(true);
                    llama.setCollidable(false);
                    llama.setPersistent(false);
                    llama.setGravity(false);
                    llama.setAI(false);
                    llama.setSilent(true);
                    llama.setInvisible(true);
                    llama.setCarryingChest(true);
                    llama.setTamed(true);
                    llama.setStrength(is(VehicleType.HELICOPTER) ? 5 : 2);

                    String title = plugin.getConfig().getString("gui.vehicle.title");
                    String typeFormatted = plugin.getVehicleTypeFormatted(type);

                    llama.setCustomName(title != null ? title
                            .replace("%owner%", Optional.of(Bukkit.getOfflinePlayer(owner)).map(OfflinePlayer::getName).orElse("???"))
                            .replace("%type%", typeFormatted) : typeFormatted);
                    llama.setCustomNameVisible(false);

                    llama.getPersistentDataContainer().set(plugin.getVehicleModelIdKey(), PersistentDataType.STRING, model.getModelUniqueId().toString());

                    AttributeInstance attribute = llama.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (attribute != null) attribute.setBaseValue(1);

                    EntityEquipment equipment = llama.getEquipment();
                    if (equipment == null) return;

                    // Dummy item, used to identify this vehicle.
                    equipment.setHelmet(new ItemBuilder(Material.STONE)
                            .setData(plugin.getVehicleModelIdKey(), PersistentDataType.STRING, model.getModelUniqueId().toString())
                            .build());
                }
        ), temp.getSettings());
    }

    public void resetRealEntities(World world) {
        if (!velocityStand.isValid()) {
            resetVelocityStand(world);
        }

        for (int i = 0; i < chairs.size(); i++) {
            Pair<LivingEntity, StandSettings> pair = chairs.get(i);

            LivingEntity living = pair.getKey();
            if (living.isValid()) continue;

            clearChair(living);

            String partName = pair.getValue().getPartName();
            chairs.set(i, spawnLlamaChair(world, partName));
        }
    }

    public void updateLlamaInventory(Player player, @NotNull LlamaInventory inventory) {
        boolean isHelicopter = is(VehicleType.HELICOPTER);

        ItemStack storage = getItem(player, "storage");
        ItemStack noStorage = getItem(player, "no-storage");
        ItemStack lock = getItem(player, "lock");
        ItemStack unlock = getItem(player, "unlock");
        ItemStack customization = getItem(player, "customization");
        ItemStack noCustomization = getItem(player, "no-customization");
        ItemStack transferOwnership = getItem(player, "transfer-ownership");
        ItemStack fuelDisabled = getItem(player, "fuel-disabled");

        List<String> fuelItems = plugin.typesToString(plugin.getFuelItems(), type, true);
        ItemStack fuelBelow = createFuelItem("fuel-below", fuelItems);
        ItemStack fuelAbove = createFuelItem("fuel-above", fuelItems);

        inventory.clear();

        inventory.setItem(index(1, isHelicopter), storageRows == 0 ? noStorage : storage);
        inventory.setItem(index(2, isHelicopter), locked ? unlock : lock);
        inventory.setItem(index(4, isHelicopter), customizations.isEmpty() ? noCustomization : customization);
        inventory.setItem(index(6, isHelicopter), transferOwnership);

        if (isHelicopter) {
            ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                    .setDisplayName("&7")
                    .build();

            inventory.setItem(4, background);
            inventory.setItem(9, background);
            inventory.setItem(14, background);

            Helicopter helicopter = (Helicopter) this;
            UUID outsideDriver = helicopter.getOutsideDriver();

            inventory.setItem(5, createHelicopterChairItem(player, 1, outsideDriver));
            inventory.setItem(10, createHelicopterChairItem(player, 2, outsideDriver));
            inventory.setItem(15, createHelicopterChairItem(player, 3, outsideDriver));

            inventory.setItem(6, createHelicopterChairItem(player, 4, outsideDriver));
            inventory.setItem(11, createHelicopterChairItem(player, 5, outsideDriver));
            inventory.setItem(16, createHelicopterChairItem(player, 6, outsideDriver));
        }

        boolean fuelEnabled = fuelEnabled();

        inventory.setItem(index(3, isHelicopter), fuelEnabled ? fuelBelow : fuelDisabled);
        if (!fuelEnabled) inventory.setItem(getFuelDepositSlot(), fuelDisabled);
        inventory.setItem(index(7, isHelicopter), fuelEnabled ? fuelAbove : fuelDisabled);
    }

    private ItemStack getItem(@NotNull Player player, String itemName) {
        ItemBuilder builder = plugin.getItem("gui.vehicle.items." + itemName);

        if (!player.getUniqueId().equals(owner)) {
            builder.addLore(plugin.getConfig().getString("translations.only-owner"));
        }

        return builder.build();
    }

    private ItemStack createFuelItem(String itemName, List<String> fuelItems) {
        return plugin.getItem("gui.vehicle.items." + itemName)
                .applyMultiLineLore(fuelItems, "%fuel%", PluginUtils.translate(plugin.getConfig().getString("translations.no-fuel")))
                .build();
    }

    private @Nullable ItemStack createHelicopterChairItem(Player player, int chair, UUID outsideDriver) {
        Pair<LivingEntity, StandSettings> firstPair = chairs.get(chair);
        if (firstPair == null) return null;

        List<Entity> passengers = firstPair.getKey().getPassengers();

        Entity passenger;
        ItemBuilder builder;
        if (passengers.isEmpty()) {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-empty");
        } else if ((passenger = passengers.get(0)).getUniqueId().equals(outsideDriver) && outsideDriver.equals(player.getUniqueId())) {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-sitted");
        } else {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-occupied")
                    .replace("%player%", passenger.getName());
        }

        return builder.replace("%chair%", chair).build();
    }

    private int index(int index, boolean isHelicopter) {
        if (!isHelicopter) return index;

        if (PluginUtils.is(index, 1, 2, 3)) return index;
        if (PluginUtils.is(index, 4, 5)) return index + 3;
        if (PluginUtils.is(index, 6, 7)) return index + 6;

        return Integer.MIN_VALUE;
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
        return !fuelEnabled() || fuel > 0.0f;
    }

    public boolean canPlaySound() {
        return canMove() && driver != null;
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

        handleVehicleMovement(input, velocityStand.isOnGround());
        updateModelLocation(model, velocityStand.getLocation());
        postModelUpdate();

        if (canPlaySound() && tick % 5 == 0) {
            engineSound.playAt(model.getLocation());
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
    }

    public boolean isDriver(@NotNull Player player) {
        return isDriver(player.getUniqueId());
    }

    public boolean isDriver(@NotNull UUID uuid) {
        return uuid.equals(driver);
    }

    private void renderOtherVehicles(UUID uuid) {
        if (uuid == null) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        StandManager manager = plugin.getStandManager();
        manager.handleStandRender(player, player.getLocation(), false);
    }

    public void saveToChunk(@NotNull Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();

        ArrayList<VehicleData> datas = container.getOrDefault(plugin.getSaveDataKey(), VEHICLE_DATA_LIST, new ArrayList<>());

        // Remove if already present and save with current data.
        datas.removeIf(data -> data.modelUniqueId().equals(model.getModelUniqueId()));
        datas.add(createSaveData());

        container.set(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA_LIST, datas);
    }

    public void saveToChunk() {
        saveToChunk(velocityStand.getLocation().getChunk());
    }

    public void removeFromChunk(@NotNull Chunk chunk) {
        PersistentDataContainer container = chunk.getPersistentDataContainer();
        NamespacedKey key = plugin.getSaveDataKey();

        ArrayList<VehicleData> datas = container.get(key, VEHICLE_DATA_LIST);
        if (datas == null) return;

        if (!datas.removeIf(data -> data.modelUniqueId().equals(model.getModelUniqueId()))) return;

        if (datas.isEmpty()) {
            container.remove(key);
        } else {
            container.set(key, VEHICLE_DATA_LIST, datas);
        }
    }

    public void removeFromChunk() {
        removeFromChunk(velocityStand.getLocation().getChunk());
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

        // We only want to update the message when the fuel or the speed changed or every 2 seconds.
        if (previousProgressed == filled
                && (gpsRunning || previousSpeed == speedAsInt)
                && (!gpsRunning || ((Generic) this).getPreviousDistance() == distance)
                && tick % 40 != 0
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

        String message = (fuelEnabled() ? Config.ACTION_BAR_FUEL.asString() + Config.ACTION_BAR_SEPARATOR.asString() : "") + Config.ACTION_BAR_SPEED.asString();

        @SuppressWarnings("DataFlowIssue") Player driver = Bukkit.getPlayer(this.driver != null ? this.driver : ((Helicopter) this).getOutsideDriver());
        if (driver == null) return;

        BaseComponent[] components = TextComponent.fromLegacyText(PluginUtils.translate(message
                .replace("%bar%", getProgressBar(filled, bars))
                .replace("%speed%", speed)
                .replace("%distance%", String.valueOf(distance))
                .replace("%home%", home)));

        driver.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);

        for (UUID uuid : getPassengers().keySet()) {
            Player passenger = Bukkit.getPlayer(uuid);
            if (passenger != null) passenger.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }
    }

    public String getProgressBar(int filled, int bars) {
        String symbol = Config.ACTION_BAR_SYMBOL.asString();
        String completed = Config.ACTION_BAR_COMPLETED.asString();
        String empty = Config.ACTION_BAR_EMPTY.asString();
        return Strings.repeat(completed + symbol, filled) + Strings.repeat(empty + symbol, bars - filled);
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
        toggleLights((this.driver = driver) != null && (!fuelEnabled() || fuel > 0.0f));
    }

    public void setDriverRaw(@Nullable UUID driver) {
        this.driver = driver;
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

        for (Pair<LivingEntity, StandSettings> chair : chairs) {
            if (chair != null) teleportChair(location, chair);
        }
    }

    protected void teleportChair(Location location, @NotNull Pair<LivingEntity, StandSettings> pair) {
        LivingEntity entity = pair.getKey();

        Location correctLocation = BlockUtils.getCorrectLocation(driver, type, location, pair.getValue());
        if (entity.getLocation().equals(correctLocation)) return;

        PluginUtils.teleportWithPassengers(entity, correctLocation);
    }

    public boolean is(VehicleType type) {
        return this.type == type;
    }

    public Pair<LivingEntity, StandSettings> getChair(int chair) {
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

        XParticle.structuredCube(
                box.getMin().toLocation(world),
                box.getMax().toLocation(world),
                0.5d,
                ParticleDisplay.of(Particle.REDSTONE)
                        .withColor(color, 1.5f)
                        .onlyVisibleTo(player));
    }

    public int getFuelDepositSlot() {
        return is(VehicleType.HELICOPTER) ? 8 : 5;
    }

    public VehicleData createSaveData() {
        Map<String, Material> customizationsChanges = new HashMap<>();
        for (Customization customization : customizations) {
            Material newType = customization.getNewType();
            if (newType != null && newType != customization.getDefaultType()) {
                customizationsChanges.put(customization.getCustomizationName(), newType);
            }
        }

        return new VehicleData(
                owner,
                fuel,
                locked,
                model.getModelUniqueId(),
                velocityStand.getLocation().clone(),
                type,
                base64Storage,
                shopDisplayName,
                customizationsChanges);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}