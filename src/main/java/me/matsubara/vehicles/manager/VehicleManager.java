package me.matsubara.vehicles.manager;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.event.VehicleSpawnEvent;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
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
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.task.VehicleTick;
import me.matsubara.vehicles.vehicle.type.Boat;
import me.matsubara.vehicles.vehicle.type.Generic;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import net.md_5.bungee.api.ChatMessageType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Gate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.*;

@Getter
public class VehicleManager implements Listener {

    private final VehiclesPlugin plugin;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Map<String, Pair<File, FileConfiguration>> models = new HashMap<>();
    private final Map<UUID, PreviewTick> previews = new HashMap<>();
    private final Map<UUID, VehicleType> selectedShopCategory = new HashMap<>();
    private final Map<UUID, GPSResultHandler> runningPaths = new HashMap<>();

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

        Vehicle vehicle = getPlayerVehicle(player);
        if (!(vehicle instanceof Helicopter) || vehicle.getVelocityStand().isOnGround()) return;

        Location highest = BlockUtils.getHighestLocation(player.getLocation());
        if (highest != null) PluginUtils.teleportWithPassengers(player, highest);
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
                Location location = data.location();
                location.setWorld(world);

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

            // vehicle.resetRealEntities(world);
        }
    }

    public @Nullable Vehicle getVehicleFromLlama(@NotNull Llama llama) {
        EntityEquipment equipment = llama.getEquipment();
        if (equipment == null) return null;

        ItemStack shulkerItem = equipment.getHelmet();
        if (shulkerItem == null || shulkerItem.getType() != Material.STONE) return null;

        ItemMeta meta = shulkerItem.getItemMeta();
        if (meta == null) return null;

        String uuidString = meta.getPersistentDataContainer().get(plugin.getVehicleModelIdKey(), PersistentDataType.STRING);
        if (uuidString == null) return null;

        return getVehicleByModelId(UUID.fromString(uuidString));
    }

    public @Nullable Vehicle getVehicleByModelId(UUID modelId) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getModel().getModelUniqueId().equals(modelId)) return vehicle;
        }
        return null;
    }

    public @Nullable Vehicle getPlayerVehicle(@NotNull Player player) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.isDriver(player)) return vehicle;
        }
        return null;
    }

    public void removeVehicle(@NotNull Vehicle vehicle, @Nullable Player picker) {
        VehicleTick vehicleTick = vehicle.getVehicleTick();
        if (vehicleTick != null && !vehicleTick.isCancelled()) vehicleTick.cancel();

        if (picker != null) vehicle.removeFromChunk(); // Remove vehicle from chunk BEFORE removing velocity stand.

        Location dropAt = picker != null ? vehicle.getBox().getCenter().toLocation(picker.getWorld()) : null;

        List<Pair<LivingEntity, StandSettings>> chairs = vehicle.getChairs();
        chairs.stream().map(Pair::getKey).forEach(vehicle::clearChair);
        chairs.clear();

        vehicle.getVelocityStand().remove();

        Model model = vehicle.getModel();
        model.kill();

        if (picker != null) {
            picker.getWorld().dropItemNaturally(dropAt, plugin.createVehicleItem(model.getName(), vehicle.createSaveData()));
        }
    }

    public void createVehicle(@Nullable Player player, @NotNull VehicleData data) {
        Location location = data.location();

        // This will only happen when a player spawns the vehicle with vehicle item that contains data.
        if (location.getYaw() == Float.MIN_VALUE && player != null) {
            BlockFace face = PluginUtils.getFace(player.getLocation().getYaw(), false);
            location.setDirection(PluginUtils.getDirection(Config.OPPOSITE_FACE_SPAWN.asBool() ? face.getOppositeFace() : face));
        }

        VehicleType type = data.type();
        String modelName = type.name().toLowerCase();

        VehicleSpawnEvent spawnEvent = new VehicleSpawnEvent(player, location, type);
        plugin.getServer().getPluginManager().callEvent(spawnEvent);
        if (spawnEvent.isCancelled()) return;

        // This may be null if it's a new vehicle.
        UUID modelUniqueId = data.modelUniqueId();

        Model model = new Model(plugin, modelName, modelUniqueId, location, Config.RENDER_DISTANCE.asInt());

        Vehicle vehicle;
        if (type == VehicleType.HELICOPTER) {
            vehicle = new Helicopter(plugin, data, model);
        } else if (type == VehicleType.BOAT) {
            vehicle = new Boat(plugin, data, model);
        } else {
            vehicle = new Generic(plugin, data, model);
        }

        // Start after 10 ticks to add small velocity to the helicopter ONLY if the chunk is loaded.
        VehicleTick tick = new VehicleTick(vehicle);
        tick.runTaskTimer(plugin, 10L, 1L);
        vehicle.setVehicleTick(tick);

        // We only save the vehicle if it's newly created.
        if (player != null) vehicle.saveToChunk();

        vehicles.add(vehicle);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        handleInteractAndDamage(event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        handleInteractAndDamage(event.getEntity(), event);
    }

    private void handleInteractAndDamage(@NotNull Entity entity, Cancellable cancellable) {
        if (!entity.getPersistentDataContainer().has(plugin.getVehicleModelIdKey(), PersistentDataType.STRING)) return;
        cancellable.setCancelled(true);
    }

    @EventHandler
    public void onEntityDismount(@NotNull EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof Llama)) return;

        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            boolean driver;
            if ((driver = vehicle.isDriver(player)) || vehicle.getPassengers().containsKey(player.getUniqueId())) {
                handleDismount(player, vehicle, driver);
                break;
            }
        }
    }

    private void handleDismount(@NotNull Player player, Vehicle vehicle, boolean driver) {
        UUID playerUUID = player.getUniqueId();

        if (vehicle instanceof Helicopter helicopter) {
            if (helicopter.getTransfers().remove(playerUUID)) return;

            if (driver) {
                helicopter.setOutsideDriver(null);
                helicopter.getPassengers().remove(playerUUID);
            }
        }

        player.teleport(vehicle.getVelocityStand()
                .getLocation()
                .clone()
                .setDirection(player.getLocation().getDirection()));

        if (driver) {
            vehicle.setDriver(null);
            invalidateGPSResult(playerUUID);
        } else {
            vehicle.getPassengers().remove(playerUUID);
        }

        if (Config.ACTION_BAR_ENABLED.asBool()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR); // Clear message.
        }
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

        PersistentDataContainer container = meta.getPersistentDataContainer();

        String modelName = container.get(plugin.getVehicleTypeKey(), PersistentDataType.STRING);
        if (modelName == null) return;

        VehicleType vehicleType = PluginUtils.getOrNull(VehicleType.class, modelName.toUpperCase());
        if (vehicleType == null) return;

        event.setCancelled(true);

        Block block = event.getClickedBlock();

        boolean waterReplaced = false;

        Messages messages = plugin.getMessages();

        if (block == null) {
            Block temp;
            if (interactedWithFluid(player, Material.LAVA) != null) {
                messages.send(player, Messages.Message.PLACE_NOT_ON_LAVA);
                return;
            } else if ((temp = interactedWithFluid(player, Material.WATER)) != null) {
                if (vehicleType == VehicleType.BOAT) {
                    block = temp;
                    waterReplaced = true;
                } else {
                    messages.send(player, Messages.Message.PLACE_NOT_ON_WATER);
                    return;
                }
            } else return; // Interacted with air.
        }

        if (interactedWithFluid(player, Material.LAVA) != null) {
            messages.send(player, Messages.Message.PLACE_NOT_ON_LAVA);
            return;
        }

        if (!waterReplaced) {
            Block temp;
            if ((temp = interactedWithFluid(player, Material.WATER)) != null) {
                if (vehicleType == VehicleType.BOAT) {
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

        if (vehicleType == VehicleType.BOAT && blockType != Material.WATER) {
            messages.send(player, Messages.Message.PLACE_BOAT_ON_WATER);
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

            data = new VehicleData(
                    temp.owner(),
                    temp.fuel(),
                    temp.locked(),
                    temp.modelUniqueId(),
                    location,
                    temp.type(),
                    temp.base64Storage(),
                    temp.shopDisplayName(),
                    temp.customizationChanges());
        } else {
            data = VehicleData.createDefault(player.getUniqueId(), null, location, vehicleType);
        }

        item.setAmount(item.getAmount() - 1);

        plugin.getVehicleManager().createVehicle(player, data);
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

    private @Nullable Block interactedWithFluid(@NotNull Player player, Material... materials) {
        for (Block block : player.getLineOfSight(null, 5)) {
            Material type = block.getType();
            for (Material material : materials) {
                if (material == type) return block;
            }
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

            PacketStand.ItemSlot slot = PluginUtils.getOrNull(PacketStand.ItemSlot.class, data[2].toUpperCase());
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
                        settings.getEquipment().get(slot).getType(),
                        typeTargets,
                        override != null ? override.getRight() : Integer.MAX_VALUE);
                customization.setParent(parent);
                customizations.add(customization);
            }

            customization.getStands().put(settings.getPartName(), slot);
        }
    }

    private @Nullable Triple<String, Boolean, Integer> getCustomizationOverride(String customizationName, Set<TypeTarget> typeTargets, @NotNull VehicleType type) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("customizations." + type.toConfigPath());
        if (section == null) return null;

        for (String partName : section.getKeys(false)) {
            if (!partName.equalsIgnoreCase(customizationName)) continue;

            String configPath = customizationName.toLowerCase();
            String nameFromConfig = plugin.getConfig().getString("customizations." + type.toConfigPath() + "." + configPath + ".name");

            int priority = plugin.getConfig().getInt("customizations." + type.toConfigPath() + "." + configPath + ".priority", Integer.MAX_VALUE);

            boolean filled = false;
            List<String> items = plugin.getConfig().getStringList("customizations." + type.toConfigPath() + "." + configPath + ".items");
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
        Customization customization = plugin.getVehicleManager().getCustomizationByName(customizations, customizationName);
        if (customization != null) applyCustomization(model, customization, newType);
    }

    public void applyCustomization(Model model, @NotNull Customization customization, Material newType) {
        customization.setNewType(newType);

        for (Map.Entry<String, PacketStand.ItemSlot> standEntry : customization.getStands().entrySet()) {
            String standName = standEntry.getKey();
            PacketStand.ItemSlot slot = standEntry.getValue();

            PacketStand stand = model.getByName(standName);
            if (stand == null) continue;

            stand.setEquipment(new ItemStack(newType), slot);
        }
    }
}