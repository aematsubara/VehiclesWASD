package me.matsubara.vehicles.manager;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.google.common.collect.Multimap;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.event.VehicleDespawnEvent;
import me.matsubara.vehicles.event.VehicleSpawnEvent;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.files.config.ConfigValue;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Customization;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.gps.GPSResultHandler;
import me.matsubara.vehicles.vehicle.task.KeybindTask;
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.task.VehicleTick;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Gate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class VehicleManager implements Listener {

    private final VehiclesPlugin plugin;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Map<String, Pair<File, FileConfiguration>> models = new HashMap<>();
    private final Map<UUID, PreviewTick> previews = new HashMap<>();
    private final Map<UUID, VehicleType> selectedShopCategory = new HashMap<>();
    private final Map<UUID, GPSResultHandler> runningPaths = new HashMap<>();
    private final Set<UUID> conflictTeleport = new HashSet<>();
    private final Map<UUID, KeybindTask> keybindTasks = new ConcurrentHashMap<>();

    public static final PlayerTeleportEvent.TeleportCause CONFLICT_CAUSE = XReflection.supports(19, 3) ?
            PlayerTeleportEvent.TeleportCause.DISMOUNT :
            PlayerTeleportEvent.TeleportCause.UNKNOWN;

    private static final Material[] ARROWS = Tag.ITEMS_ARROWS.getValues().toArray(Material[]::new);
    private static final List<String> FILTER_TYPES = List.of("WHITELIST", "BLACKLIST");

    public VehicleManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public VehicleType getSelectedType(@NotNull Player player) {
        return selectedShopCategory.computeIfAbsent(player.getUniqueId(), uuid -> VehicleType.BIKE);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        selectedShopCategory.remove(playerUUID);
        invalidateGPSResult(playerUUID);

        Vehicle vehicle = getVehicleByEntity(player, true);
        if (vehicle != null) handleDismountLocation(player, vehicle, true);
    }

    public boolean invalidateGPSResult(UUID uuid) {
        GPSResultHandler result = runningPaths.remove(uuid);
        if (result == null) return false;

        result.invalidate();
        return true;
    }

    @EventHandler
    public void onChunkLoadEvent(@NotNull ChunkLoadEvent event) {
        // NOTE: For some reason, sometimes this event is called twice per chunk?
        Chunk chunk = event.getChunk();
        World world = event.getWorld();

        PersistentDataContainer container = chunk.getPersistentDataContainer();

        ArrayList<VehicleData> datas = container.get(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA_LIST);
        if (datas == null) return;

        if (datas.isEmpty()) {
            container.remove(plugin.getSaveDataKey());
            return;
        }

        for (VehicleData data : datas) {
            Vehicle vehicle = getVehicleByModelId(data.modelUniqueId());
            if (vehicle == null) {
                data.location().setWorld(world);
                createVehicle(null, data);
                continue;
            }

            // Restart tick if needed.
            VehicleTick tick = vehicle.getVehicleTick();
            if (tick == null || tick.isCancelled()) {
                VehicleTick newtick = new VehicleTick(vehicle);
                newtick.runTaskTimer(plugin, 10L, 1L);
                vehicle.setVehicleTick(newtick);
            }
        }
    }

    public @Nullable Vehicle getVehicleByProjectile(@NotNull Projectile projectile, @Nullable Entity driver) {
        for (MetadataValue value : projectile.getMetadata("VehicleSource")) {
            if (!(value.value() instanceof Vehicle vehicle)) continue;
            if (driver != null && !vehicle.isDriver(driver)) continue;
            return vehicle;
        }
        return null;
    }

    public @Nullable Vehicle getVehicleByModelId(UUID modelId) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getModelUUID().equals(modelId)) return vehicle;
        }
        return null;
    }

    public @Nullable Vehicle getVehicleByEntity(@NotNull Entity entity) {
        return getVehicleByEntity(entity, false);
    }

    public Vehicle getVehicleByEntity(Entity entity, boolean checkPassenger) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.isDriver(entity) || (checkPassenger && vehicle.isPassenger(entity))) {
                return vehicle;
            }
        }
        return null;
    }

    public void removeVehicle(@NotNull Vehicle vehicle, @Nullable Player picker) {
        removeVehicle(vehicle, picker, false);
    }

    public void removeVehicle(@NotNull Vehicle vehicle, @Nullable Player picker, boolean forceRemove) {
        // We need to change the removed state so the passengers are teleported correctly.
        vehicle.setRemoved(true);

        VehicleTick vehicleTick = vehicle.getVehicleTick();
        if (vehicleTick != null && !vehicleTick.isCancelled()) vehicleTick.cancel();

        if (forceRemove || picker != null) {
            vehicle.removeFromChunk(); // Remove vehicle from chunk BEFORE removing velocity stand.
        }

        List<Pair<ArmorStand, StandSettings>> chairs = vehicle.getChairs();
        chairs.stream().map(Pair::getKey).toList().forEach(vehicle::clearChair);
        chairs.clear();

        vehicle.getVelocityStand().remove();

        Model model = vehicle.getModel();
        model.kill();

        vehicle.invalidateCurrentTarget();

        VehicleDespawnEvent despawnEvent = new VehicleDespawnEvent(vehicle);
        plugin.getServer().getPluginManager().callEvent(despawnEvent);

        if (picker == null) return;

        Location dropAt = vehicle.getBox().getCenter().toLocation(picker.getWorld());
        picker.getWorld().dropItemNaturally(dropAt, vehicle.createVehicleItem(),
                        temp -> Vehicle.LISTEN_MODE_IGNORE.accept(plugin, temp))
                .setThrower(picker.getUniqueId());
    }

    public Vehicle createVehicle(@Nullable Player player, @NotNull VehicleData data) {
        Location location = data.location();

        // This will only happen when a player spawns the vehicle with vehicle item that contains data.
        if (location.getYaw() == Float.MIN_VALUE && player != null) {
            BlockFace face = PluginUtils.getFace(player.getLocation().getYaw(), false);
            location.setDirection(PluginUtils.getDirection(Config.OPPOSITE_FACE_SPAWN.getValue(Boolean.class) ? face.getOppositeFace() : face));
        }

        VehicleType type = data.type();
        String modelName = type.name().toLowerCase(Locale.ROOT);

        VehicleSpawnEvent spawnEvent = new VehicleSpawnEvent(player, location, type);
        plugin.getServer().getPluginManager().callEvent(spawnEvent);
        if (spawnEvent.isCancelled()) return null;

        // This may be null if it's a new vehicle.
        UUID modelUniqueId = data.modelUniqueId();

        Model model = new Model(plugin, modelName, modelUniqueId, location);
        Vehicle vehicle = type.create(plugin, data, model);

        // Start after 10 ticks to add small velocity to the helicopter ONLY if the chunk is loaded.
        VehicleTick tick = new VehicleTick(vehicle);
        tick.runTaskTimer(plugin, 10L, 1L);
        vehicle.setVehicleTick(tick);

        if (player != null) {
            // We only save the vehicle if it's newly created.
            vehicle.saveToChunk();

            // Let the player know the controls.
            handleKeybindMessage(player, vehicle);
        }

        vehicles.add(vehicle);
        return vehicle;
    }

    private void handleKeybindMessage(Player player, Vehicle vehicle) {
        if (!Config.KEYBINDS_ACTION_BAR_MESSAGE_ENABLED.getValue(Boolean.class)) return;

        int ticks = (int) (Config.KEYBINDS_ACTION_BAR_MESSAGE_SECONDS.getValue(Double.class) * 20);

        // Cancel current task before starting a new one.
        cancelKeybindTask(player);
        keybindTasks.put(player.getUniqueId(), new KeybindTask(player, vehicle, ticks));
    }

    public void cancelKeybindTask(@NotNull Entity entity) {
        KeybindTask task = keybindTasks.get(entity.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
    }

    public boolean canBePickedUp(@NotNull Entity entity) {
        return contains(Config.ENTITIES_FILTER_TYPE, Config.ENTITIES_FILTER_WORLDS, entity.getType().name());
    }

    public boolean isEnabledIn(@NotNull World world) {
        return contains(Config.WORLDS_FILTER_TYPE, Config.WORLDS_FILTER_WORLDS, world.getName());
    }

    private boolean contains(@NotNull ConfigValue filterConfig, ConfigValue listConfig, String check) {
        String filter = filterConfig.getValue(String.class);
        if (filter == null || !FILTER_TYPES.contains(filter.toUpperCase(Locale.ROOT))) return true;

        List<String> list = listConfig.getValue(List.class);
        return filter.equalsIgnoreCase("WHITELIST") == list.contains(check);
    }

    @EventHandler
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        // Prevent interactions with the (real) stands of a vehicle.
        handleRealStands(event.getRightClicked(), event);
    }

    @EventHandler
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        // We don't want to send vehicles to another world through a portal.
        handleRealStands(event);
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        // Prevent damage for vehicles.
        handleRealStands(event);
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        // Prevent combust for vehicles.
        handleRealStands(event);
    }

    private void handleRealStands(@NotNull EntityEvent event) {
        if (!(event instanceof Cancellable cancellable)) return;
        handleRealStands(event.getEntity(), cancellable);
    }

    private void handleRealStands(@NotNull Entity entity, Cancellable cancellable) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (!container.has(plugin.getVehicleModelIdKey(), PersistentDataType.STRING)) return;

        cancellable.setCancelled(true);
    }

    private void handleTeleportConflict(@NotNull PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        Vehicle vehicle = getVehicleByEntity(player, true);
        if (vehicle == null || !player.isInsideVehicle()) return;

        if (event.getCause() != CONFLICT_CAUSE) {
            conflictTeleport.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        handleTeleportConflict(event);
    }

    @EventHandler // We can keep using this event if we stay in 1.20.1.
    public void onEntityDismount(@NotNull EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof ArmorStand)) return;

        Entity entity = event.getEntity();

        Vehicle vehicle = getVehicleByEntity(entity, true);
        if (vehicle != null) handleDismount(entity, vehicle);
    }

    private void handleDismount(@NotNull Entity entity, @NotNull Vehicle vehicle) {
        boolean driver = vehicle.isDriver(entity);
        UUID entityUUID = entity.getUniqueId();

        if (vehicle instanceof Helicopter helicopter) {
            if (helicopter.getTransfers().remove(entityUUID)) return;

            if (driver) {
                helicopter.setOutsideDriver(null);
                helicopter.getPassengers().remove(entityUUID);
            }
        }

        boolean valid = entity.isValid();
        if (valid && !conflictTeleport.remove(entityUUID)) {
            handleDismountLocation(entity, vehicle, false);
        }

        if (driver) {
            vehicle.setDriver(null);
            invalidateGPSResult(entityUUID);
        } else {
            vehicle.getPassengers().remove(entityUUID);
        }

        // Remove speed and velocity.
        if (Config.STOP_VEHICLE_ON_DISMOUNT.getValue(Boolean.class)) {
            vehicle.setCurrentSpeed(0.0f);
            vehicle.getVelocityStand().setVelocity(new Vector());
        }

        if (!(entity instanceof Player player)) return;

        // Remove cooldowns.
        removeCooldown(player, vehicle, Config.PLANE_FIRE_PRIMARY_ENABLED, Config.PLANE_FIRE_PRIMARY_COOLDOWN, ARROWS);
        removeCooldown(player, vehicle, Config.PLANE_FIRE_SECONDARY_ENABLED, Config.PLANE_FIRE_SECONDARY_COOLDOWN, Material.FIRE_CHARGE);
        removeCooldown(player, vehicle, Config.TANK_FIRE_ENABLED, Config.TANK_FIRE_COOLDOWN, Material.FIRE_CHARGE);

        if (Config.ACTION_BAR_ENABLED.getValue(Boolean.class)) {
            player.sendActionBar(Component.empty()); // Clear message.
        }

        if (!valid || !driver || !Config.PICK_UP_ON_DISMOUNT.getValue(Boolean.class)) return;

        saveVehicleOnInventory(player, vehicle);
    }

    public void saveVehicleOnInventory(@NotNull Player player, Vehicle vehicle) {
        Messages messages = plugin.getMessages();

        PlayerInventory inventory = player.getInventory();
        if (inventory.firstEmpty() == -1) {
            messages.send(player, Messages.Message.PICK_NOT_ENOUGH_SPACE);
            return;
        }

        // Remove the vehicle.
        vehicles.remove(vehicle);
        removeVehicle(vehicle, null, true);

        inventory.addItem(vehicle.createVehicleItem());

        messages.send(player, Messages.Message.PICK_SAVED_IN_INVENTORY);
    }

    private void removeCooldown(Player player,
                                Vehicle vehicle,
                                @NotNull ConfigValue enabledConfig,
                                ConfigValue cooldownConfig,
                                Material... materials) {
        if (!enabledConfig.getValue(Boolean.class) || cooldownConfig.getValue(Double.class) <= 0.0d) return;

        UUID playerUUID = player.getUniqueId();

        // Save cooldown for later.
        for (Material material : materials) {
            int cooldown = player.getCooldown(material);
            if (cooldown <= 0) continue;

            Multimap<UUID, Pair<Material, Integer>> cooldowns = vehicle.getCooldowns();
            cooldowns.get(playerUUID).removeIf(next -> next.getKey() == material);
            cooldowns.put(playerUUID, Pair.of(material, cooldown));

            player.setCooldown(material, 0);
        }
    }

    private void handleDismountLocation(@NotNull Entity entity, @NotNull Vehicle vehicle, boolean bypass) {
        Location entityLocation = entity.getLocation();

        boolean useCurrent = !Config.SAFE_DISMOUNT_TELEPORT.getValue(Boolean.class) && (vehicle.isOnGround() || (!bypass
                && !vehicle.isRemoved()
                && !vehicle.is(VehicleType.HELICOPTER)
                && !vehicle.is(VehicleType.PLANE)));

        Location highest = useCurrent ? null : BlockUtils.getHighestLocation(entityLocation);
        Location current = vehicle.getVelocityStand().getLocation()
                .clone()
                .setDirection(entityLocation.getDirection());

        Location location = Objects.requireNonNullElse(highest, current);

        // We need to do it instantly when a player quits.
        Runnable runnable = () -> entity.teleport(location, CONFLICT_CAUSE);

        if (bypass) {
            runnable.run();
            return;
        }

        // We need to teleport the entity on the next tick.
        plugin.getServer().getScheduler().runTask(plugin,
                runnable);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL
                || action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Prevent interacting with a fuel item.
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(plugin.getFuelItemKey(), PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            return;
        }

        String modelName = container.get(plugin.getVehicleTypeKey(), PersistentDataType.STRING);
        if (modelName == null) return;

        VehicleType vehicleType = PluginUtils.getOrNull(VehicleType.class, modelName.toUpperCase(Locale.ROOT));
        if (vehicleType == null) return;

        event.setCancelled(true);

        Block block = event.getClickedBlock();

        boolean isBoat = vehicleType == VehicleType.BOAT;
        boolean waterReplaced = false;

        Messages messages = plugin.getMessages();

        if (!isEnabledIn(player.getWorld())) {
            messages.send(player, Messages.Message.PLACE_DISABLED_WORLD);
            return;
        }

        List<Block> sight = player.getLineOfSight(null, 5);

        if (block == null) {
            Block temp;
            if (interactedWithFluid(sight, Material.LAVA) != null) {
                messages.send(player, Messages.Message.PLACE_NOT_ON_LAVA);
                return;
            } else if ((temp = interactedWithFluid(sight, Material.WATER)) != null) {
                if (isBoat) {
                    block = temp;
                    waterReplaced = true;
                } else {
                    messages.send(player, Messages.Message.PLACE_NOT_ON_WATER);
                    return;
                }
            } else return; // Interacted with air.
        }

        if (interactedWithFluid(sight, Material.LAVA) != null) {
            messages.send(player, Messages.Message.PLACE_NOT_ON_LAVA);
            return;
        }

        if (!waterReplaced) {
            Block temp;
            if ((temp = interactedWithFluid(sight, Material.WATER)) != null) {
                if (isBoat) {
                    block = temp;
                } else {
                    messages.send(player, Messages.Message.PLACE_NOT_ON_WATER);
                    return;
                }
            }
        }

        Material blockType = block.getType();
        if (BlockUtils.BUGGY_HEIGHTS.contains(blockType)) {
            messages.send(player, Messages.Message.PLACE_INVALID_BLOCK);
            return;
        }

        if (isBoat && blockType != Material.WATER) {
            messages.send(player, Messages.Message.PLACE_BOAT_ON_WATER);
            return;
        }

        if (isBoat && block.getRelative(BlockFace.UP).getType() == Material.WATER) {
            messages.send(player, Messages.Message.PLACE_BOAT_ON_TOP_SURFACE);
            return;
        }

        double extraY;

        if (blockType == Material.WATER) {
            extraY = 0.725d;
        } else {
            Double temp = getExtraYFromBlock(player, block);
            if (temp == null) return;
            extraY = temp;
        }

        Location location = block.getLocation().add(0.5d, extraY, 0.5d);
        location.setYaw(Float.MIN_VALUE);

        Location clickLocation = location.clone();
        if (blockType != Material.WATER) {
            Vector position = BlockUtils.getClickedPosition(event);
            if (position != null) {
                clickLocation.setX(block.getX() + position.getX());
                clickLocation.setZ(block.getZ() + position.getZ());
            }
        }

        boolean occupied = false;
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getBox().contains(clickLocation.toVector())) {
                vehicle.showBox(player, Color.RED);
                occupied = true;
            }
        }

        if (occupied) {
            messages.send(player, Messages.Message.PLACE_OCCUPIED);
            return;
        }

        VehicleData data, temp = container.get(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA);
        if (temp != null) {
            if (temp.modelUniqueId() != null) {
                Vehicle vehicle = getVehicleByModelId(temp.modelUniqueId());
                if (vehicle != null) {
                    messages.send(player, Messages.Message.PLACE_DUPLICATED);
                    return;
                }
            }
            data = duplicateData(temp, location);
        } else {
            data = VehicleData.createDefault(player.getUniqueId(), null, location, vehicleType);
        }

        if (createVehicle(player, data) != null) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    @Contract("_, _ -> new")
    private @NotNull VehicleData duplicateData(@NotNull VehicleData temp, Location location) {
        return new VehicleData(
                temp.owner(),
                temp.fuel(),
                temp.locked(),
                temp.modelUniqueId(),
                location,
                temp.type(),
                temp.base64Storage(),
                temp.shopDisplayName(),
                temp.customizationChanges(),
                temp.tractorMode());
    }

    private @Nullable Double getExtraYFromBlock(Player player, @NotNull Block block) {
        Material blockType = block.getType();

        Double fromMap = BlockUtils.getMaterialHeight(block);
        if (fromMap != null && fromMap != Double.MIN_VALUE) {
            return fromMap;
        } else if (Tag.TRAPDOORS.isTagged(blockType)) {
            // At this point, the trapdoor is open.
            return 0.0d;
        } else if (block.isPassable()) {
            return 0.0d;
        }

        Block topBlock = block.getRelative(BlockFace.UP);
        boolean allowOnTop = allowTopPlacing(topBlock);

        Material topType = topBlock.getType();
        if ((topType.isSolid() && !allowOnTop) || (BlockUtils.BUGGY_HEIGHTS.contains(topType) && !allowOnTop)) {
            plugin.getMessages().send(player, Messages.Message.PLACE_TOP_BLOCKED_OR_INVALID);
            return null;
        }

        if (topType == Material.BIG_DRIPLEAF
                || topType == Material.FLOWER_POT
                || topType == Material.SEA_PICKLE
                || topType == Material.END_ROD
                || BlockUtils.HEADS.contains(topType)
                || Tag.CANDLES.isTagged(topType)) {
            return 1.0d + BlockUtils.getMaterialHeight(topBlock);
        }

        return 1.0d;
    }

    private boolean allowTopPlacing(@NotNull Block block) {
        Material type = block.getType();
        return Tag.DOORS.isTagged(type) || (Tag.FENCE_GATES.isTagged(type) && block.getBlockData() instanceof Gate gate && gate.isOpen());
    }

    private @Nullable Block interactedWithFluid(@NotNull List<Block> blocks, Material check) {
        for (Block block : blocks) {
            Material type = block.getType();
            if (check == type) return block;
        }
        return null;
    }

    public void initCustomizations(@NotNull Model model, @NotNull List<Customization> customizations, VehicleType type) {
        for (PacketStand stand : model.getStands()) {
            handleCustomizations(customizations, stand, type);
        }

        Iterator<Customization> iterator = customizations.iterator();
        while (iterator.hasNext()) {
            Customization customization = iterator.next();

            String parentName = customization.getParent();
            if (parentName == null) continue;

            Customization parent = getCustomizationByName(customizations, parentName);
            if (parent == null) continue;

            parent.getStands().putAll(customization.getStands());
            parent.setStack(parent.getStack() + 1);
            iterator.remove();
        }

        // Sort by priority from config.
        customizations.sort(Comparator.comparingInt(Customization::getPriority));
    }

    private void handleCustomizations(List<Customization> customizations, @NotNull PacketStand stand, VehicleType type) {
        // Format: CUSTOMIZABLE:{NAME}:{SLOT}:{PARENT(optional)}
        StandSettings settings = stand.getSettings();

        for (String tagValue : settings.getTags()) {
            if (!tagValue.startsWith("CUSTOMIZABLE")) continue;

            String[] data = tagValue.split(":");
            if (data.length < 3 || data.length > 4) continue;

            String customizationName = data[1].replace("_", "-");

            PacketStand.ItemSlot slot = PluginUtils.getOrNull(PacketStand.ItemSlot.class, data[2].toUpperCase(Locale.ROOT));
            if (slot == null) continue;

            Set<TypeTarget> typeTargets = new HashSet<>();

            String customizationNameFromConfig;
            Triple<String, Boolean, Integer> override = getCustomizationOverride(customizationName, typeTargets, type);
            if (override != null && override.getLeft() != null) {
                customizationNameFromConfig = override.getLeft();
            } else {
                customizationNameFromConfig = null;
            }

            boolean isChild = data.length == 4;

            if (override == null || !override.getMiddle()) {
                // This may be a child customization.
                if (!isChild) continue;
            }

            if (typeTargets.isEmpty() && !isChild) continue;

            String parent = isChild ? data[3] : null;

            Customization customization = getCustomizationByName(customizations, customizationName);
            if (customization == null) {
                customization = new Customization(
                        customizationName,
                        customizationNameFromConfig,
                        SpigotConversionUtil.toBukkitItemMaterial(settings.getEquipment().get(slot.getSlot()).getType()),
                        typeTargets,
                        override != null ? override.getRight() : Integer.MAX_VALUE);
                customization.setParent(parent);
                customizations.add(customization);
            }

            customization.getStands().put(settings.getPartName(), slot.getSlot());
        }
    }

    private @Nullable Triple<String, Boolean, Integer> getCustomizationOverride(String customizationName, Set<TypeTarget> typeTargets, @NotNull VehicleType type) {
        String pathName = type.toPath();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection section = config.getConfigurationSection("customizations." + pathName);
        if (section == null) return null;

        for (String partName : section.getKeys(false)) {
            if (!partName.equalsIgnoreCase(customizationName)) continue;

            String path = "customizations." + pathName + "." + customizationName.toLowerCase(Locale.ROOT) + ".";
            String nameFromConfig = config.getString(path + "name");

            int priority = config.getInt(path + "priority", Integer.MAX_VALUE);

            boolean filled = false;
            List<String> items = config.getStringList(path + "items");
            if (!items.isEmpty()) {
                for (String item : items) {
                    plugin.getTypeTargetManager().fillTargets(null, typeTargets, item);
                    filled = true;
                }
            }

            return Triple.of(nameFromConfig, filled, priority);
        }

        return null;
    }

    public @Nullable Customization getCustomizationByName(@NotNull List<Customization> customizations, String name) {
        for (Customization customization : customizations) {
            if (customization.getCustomizationName().equals(name)) return customization;
        }
        return null;
    }

    public void applyCustomization(Model model, List<Customization> customizations, String customizationName, Material newType) {
        Customization customization = getCustomizationByName(customizations, customizationName);
        if (customization != null) applyCustomization(model, customization, newType);
    }

    public void applyCustomization(Model model, @NotNull Customization customization, Material newType) {
        customization.setNewType(newType);

        for (Map.Entry<String, EquipmentSlot> standEntry : customization.getStands().entrySet()) {
            String standName = standEntry.getKey();
            EquipmentSlot slot = standEntry.getValue();

            PacketStand stand = model.getStandByName(standName);
            if (stand == null) continue;

            stand.getSettings().getEquipment().put(slot, SpigotConversionUtil.fromBukkitItemStack(new ItemStack(newType)));
            stand.sendEquipment();
        }
    }

    public void startPreview(@NotNull Player player, VehicleData data) {
        PreviewTick current = previews.remove(player.getUniqueId());
        if (current != null && !current.isCancelled()) current.cancel();

        new PreviewTick(plugin, player, data).runTaskTimer(plugin, 1L, 1L);
    }
}