package me.matsubara.vehicles.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.google.common.collect.Multimap;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.ActionKeybind;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.files.config.ConfigValue;
import me.matsubara.vehicles.gui.VehicleGUI;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.vehicle.FireballWeapon;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.task.FireballTask;
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class UseEntity extends SimplePacketListenerAbstract implements Listener {

    private final VehiclesPlugin plugin;

    public UseEntity(VehiclesPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileLaunch(@NotNull ProjectileLaunchEvent event) {
        if (!Config.PLANE_FIRE_SECONDARY_FOLLOW_TARGET_ENABLED.asBool()) return;

        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player player)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleByProjectile(fireball, player);
        if (vehicle == null) return;

        LivingEntity target = vehicle.getCurrentTarget();
        if (target != null) new FireballTask(plugin, fireball, target);
    }

    private void handleKeybinds(Player player,
                                boolean left,
                                Runnable runnable) {
        Vehicle vehicle = plugin.getVehicleManager().getVehicleByEntity(player);
        if (vehicle == null) return;

        // Shoot has priority over menu.
        ActionKeybind keybind = left ? ActionKeybind.LEFT_CLICK : ActionKeybind.RIGHT_CLICK;
        if (plugin.getShootWeaponKeybind() == keybind
                && vehicle.hasWeapon()
                && !(!handlePlanePrimary(runnable, player, vehicle)
                && !handleFireballWeapon(runnable, player, vehicle, FireballWeapon.PLANE)
                && !handleFireballWeapon(runnable, player, vehicle, FireballWeapon.TANK))) return;

        if (plugin.getOpenMenuKeybind() != keybind) return;

        runTask(() -> new VehicleGUI(plugin, player, vehicle));
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        int entityId = wrapper.getEntityId();

        WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
        boolean left = action == WrapperPlayClientInteractEntity.InteractAction.ATTACK;

        InteractionHand hand = left ? null : wrapper.getHand();

        // We only need to listen to ATTACK (left) or INTERACT_AT (right).
        if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT) return;
        if (hand == InteractionHand.OFF_HAND) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // The player is already inside a vehicle.
        if (player.isInsideVehicle()) {
            handleKeybinds(player, left, () -> event.setCancelled(true));
            return;
        }

        if (handlePreviews(player, entityId, left)) return;

        Iterator<Vehicle> iterator = plugin.getVehicleManager().getVehicles().iterator();
        while (iterator.hasNext()) {
            Vehicle vehicle = iterator.next();
            if (vehicle.isRemoved()) continue;

            Model model = vehicle.getModel();
            if (notChair(entityId, vehicle.getChairs())
                    && (model.getStandById(entityId) == null)
                    && (vehicle.getVelocityStand() == null || vehicle.getVelocityStand().getEntityId() != entityId)) {
                continue;
            }

            runTask(() -> handleOutsideVehicleInteract(player, vehicle, iterator, left));

            event.setCancelled(true);
            break;
        }
    }

    private boolean hasCooldown(@NotNull Player player, Material type, ConfigValue sendCooldown) {
        if (!player.hasCooldown(type)) return false;
        if (!sendCooldown.asBool()) return true;

        double seconds = player.getCooldown(type) / 20.0d;
        plugin.getMessages().send(
                player,
                Messages.Message.WEAPON_COOLDOWN,
                line -> line.replace("%seconds%", String.format("%.2f", seconds)));
        return true;
    }

    private boolean handlePlanePrimary(Runnable runnable, Player player, Vehicle vehicle) {
        if (!Config.PLANE_FIRE_PRIMARY_ENABLED.asBool()) return false;

        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItemInMainHand();
        Material type = item.getType();

        if (!vehicle.is(VehicleType.PLANE) || !Tag.ITEMS_ARROWS.isTagged(type)) return false;
        if (!vehicle.canMove()) return true;

        // We need at least 2 arrows.
        if (item.getAmount() < 2) return true;

        Model model = vehicle.getModel();

        IStand firstScope = model.getStandByName("FRONT_PART_5");
        IStand secondScope = model.getStandByName("FRONT_PART_6");
        if (firstScope == null || secondScope == null) return true;

        runnable.run();

        if (hasCooldown(player, type, Config.PLANE_FIRE_PRIMARY_COOLDOWN_MESSAGE)) {
            return true;
        }

        // Update item in the hand.
        handleItemAmount(inventory, 2, item);

        // We need to apply the cooldown and spawn the arrows SYNC.
        runTask(() -> {
            spawnArrow(player, item, firstScope.getLocation(), vehicle);
            spawnArrow(player, item, secondScope.getLocation(), vehicle);
        });
        return true;
    }

    private void handleItemAmount(@NotNull PlayerInventory inventory, int amount, @NotNull ItemStack item) {
        item.setAmount(item.getAmount() - amount);

        // PlayerInventory#getItemInMainHand returns a copy, we need to apply the modification here.
        inventory.setItemInMainHand(item.getAmount() <= 0 ? null : item);
    }

    private void spawnArrow(@NotNull Player player, @NotNull ItemStack item, @NotNull Location location, Vehicle vehicle) {
        Material type = item.getType();

        if (!player.hasCooldown(type) && (item.getAmount() > 0 || player.getInventory().contains(type))) {
            int cooldownTicks = (int) (Config.PLANE_FIRE_PRIMARY_COOLDOWN.asDouble() * 20);
            player.setCooldown(type, cooldownTicks);
        }

        Location spawnAt = location.clone().add(0.0d, 2.0d, 0.0d);
        spawnAt.setPitch(0.0f); // We want the plane bullets to go straight.

        Class<? extends AbstractArrow> clazz = type == Material.SPECTRAL_ARROW ? SpectralArrow.class : Arrow.class;
        Vector direction = spawnAt.getDirection().multiply(Config.PLANE_FIRE_PRIMARY_SPEED_MULTIPLIER.asDouble());

        AbstractArrow base = player.getWorld().spawnArrow(spawnAt, direction, 0.6f, 12.0f, clazz);
        base.setMetadata("VehicleSource", new FixedMetadataValue(plugin, vehicle));
        base.setVelocity(direction);

        if (base instanceof Arrow arrow && item.getItemMeta() instanceof PotionMeta meta) {
            arrow.setBasePotionData(meta.getBasePotionData());
            if (meta.hasCustomEffects()) {
                for (PotionEffect effect : meta.getCustomEffects()) {
                    arrow.addCustomEffect(effect, true);
                }
            }
            if (meta.hasColor()) arrow.setColor(meta.getColor());
        }

        int power = Math.max(0, Math.min(127, Config.PLANE_FIRE_PRIMARY_POWER_LEVEL.asInt()));
        base.setDamage(power == 0 ? 2.0d : 2.0d + 0.5d * (1 + power));
        base.setCritical(Config.PLANE_FIRE_PRIMARY_CRITICAL.asBool());
        base.setPierceLevel(Math.max(0, Math.min(127, Config.PLANE_FIRE_PRIMARY_PIERCE_LEVEL.asInt())));
        base.setFireTicks(Math.max(0, Config.PLANE_FIRE_PRIMARY_FIRE_TICKS.asInt()));
        base.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        base.setShooter(player);

        Vehicle.LISTEN_MODE_IGNORE.accept(plugin, base);

        // Play sound.
        vehicle.getPlaneFirePrimarySound().playAt(location);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean handleFireballWeapon(Runnable runnable,
                                         Player player,
                                         Vehicle vehicle,
                                         @NotNull FireballWeapon weapon) {
        if (!weapon.getEnabled().asBool()) return false;

        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItemInMainHand();

        VehicleType type = weapon.getType();
        if (!vehicle.is(type) || item.getType() != Material.FIRE_CHARGE) return false;

        // If it's a plane, we don't want to shoot if we're on the ground.
        boolean plane = type == VehicleType.PLANE;
        if (!vehicle.canMove() || (plane && vehicle.isOnGround())) return true;

        IStand scope = vehicle.getModel().getStandByName(weapon.getScope());
        if (scope == null) return true;

        runnable.run();

        if (hasCooldown(player, Material.FIRE_CHARGE, weapon.getSendCooldownMessage())) {
            return true;
        }

        // Update item in the hand.
        handleItemAmount(inventory, 1, item);

        // We need to apply the cooldown and spawn the fireball SYNC.
        runTask(() -> {
            if (item.getAmount() > 0 || inventory.contains(Material.FIRE_CHARGE)) {
                int cooldownTicks = (int) (weapon.getCooldown().asDouble() * 20);
                player.setCooldown(Material.FIRE_CHARGE, cooldownTicks);
            }

            Location spawnAt = scope.getLocation().clone().add(0.0d, weapon.getYOffset(), 0.0d);
            spawnAt.setPitch(0.0f);

            player.getWorld().spawn(
                    spawnAt,
                    LargeFireball.class,
                    temp -> {
                        // If it's a plane, then we want the direction to be straight ahead.
                        // If it's a tank, we want it to go where the player is looking.
                        Vector direction = (plane ? spawnAt : player.getEyeLocation())
                                .getDirection()
                                .multiply(weapon.getSpeedMultiplier().asDouble());

                        temp.setDirection(direction);

                        temp.setMetadata("VehicleSource", new FixedMetadataValue(plugin, vehicle));
                        temp.setDisplayItem(weapon.getFireballItem().asItem());
                        temp.setIsIncendiary(weapon.getIncendiary().asBool());
                        temp.setYield(weapon.getRadius().asFloat());
                        temp.setShooter(player);

                        Vehicle.LISTEN_MODE_IGNORE.accept(plugin, temp);
                    });

            // Play sound.
            weapon.getSoundGetter().apply(vehicle).playAt(spawnAt);
        });

        return true;
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private boolean handlePreviews(@NotNull Player player, int entityId, boolean left) {
        if (!player.isSneaking() || !left) return false;

        Collection<PreviewTick> previews = plugin.getVehicleManager().getPreviews().values();
        if (previews.isEmpty()) return false;

        Iterator<PreviewTick> iterator = previews.iterator();
        while (iterator.hasNext()) { // We use an iterator because, when doing PreviewTick#cancel, we're removing the preview from the map.
            PreviewTick preview = iterator.next();

            IStand stand = preview.getModel().getStandById(entityId);
            if (stand == null) continue;

            preview.cancel();
            return true;
        }

        return false;
    }

    private void handleOutsideVehicleInteract(@NotNull Player player, @NotNull Vehicle vehicle, Iterator<Vehicle> iterator, boolean left) {
        if (vehicle.isRemoved()) return;

        UUID playerUUID = player.getUniqueId();
        Messages messages = plugin.getMessages();

        boolean notOwner = !vehicle.isOwner(playerUUID);

        if (left) {
            if (!player.isSneaking()) return;

            if (notOwner && !player.hasPermission("vehicleswasd.remove")) {
                messages.send(player, Messages.Message.NOT_YOUR_VEHICLE);
                return;
            }

            VehicleManager manager = plugin.getVehicleManager();
            if (Config.PICK_UP_ON_REMOVE.asBool()) {
                if (player.getInventory().firstEmpty() == -1) {
                    plugin.getMessages().send(player, Messages.Message.PICK_NOT_ENOUGH_SPACE);
                    return;
                }
                manager.removeVehicle(vehicle, null, true);
                player.getInventory().addItem(vehicle.createVehicleItem());
            } else {
                manager.removeVehicle(vehicle, player);
            }

            manager.cancelKeybindTask(player, vehicle);
            iterator.remove();
            return;
        }

        // Before handling the chairs, we need to make sure they're spawned.
        World world = player.getWorld();
        vehicle.resetRealEntities(world);

        if (player.isSneaking()) {
            if (notOwner) return;

            Pair<ArmorStand, StandSettings> primaryChair = vehicle.getChair(0);
            if (primaryChair != null) {
                if (kickDriverIfPossible(player, vehicle)) return;
                runTask(() -> new VehicleGUI(plugin, player, vehicle));
            }
            return;
        }

        Block block = vehicle.getVelocityStand().getLocation().getBlock();
        if (vehicle.getType() != VehicleType.BOAT && block.isLiquid()) {
            Messages.Message message = block.getType() == Material.WATER ?
                    Messages.Message.VEHICLE_IN_WATER :
                    Messages.Message.VEHICLE_IN_LAVA;
            messages.send(player, message);
            return;
        }

        boolean firstChair = vehicle.getDriver() == null && (!(vehicle instanceof Helicopter helicopter) || helicopter.getOutsideDriver() == null);
        if (!firstChair && vehicle.getChairs().size() == 1) {
            // The driver slot is already occupied and there aren't any more chairs.
            handleOwnerLeftOut(player, vehicle, true);
            return;
        }

        boolean notOwnerLocked = notOwner && vehicle.isLocked();
        if (firstChair && notOwnerLocked) {
            firstChair = false;
        }

        Pair<ArmorStand, StandSettings> chair;
        if (firstChair) {
            chair = vehicle.getChair(0);
        } else {
            if (notOwnerLocked && vehicle.getChairs().size() == 1) {
                messages.send(player, Messages.Message.VEHICLE_LOCKED);
                return;
            }

            // The driver slot is already occupied and the passenger slots are all occupied.
            chair = vehicle.getFreePassengerSeat();
            if (chair == null) {
                handleOwnerLeftOut(player, vehicle, true);
                return;
            }

            // Driver slot is empty but the vehicle it's locked.
            if (notOwnerLocked && vehicle.getDriver() == null && (!(vehicle instanceof Helicopter helicopter) || helicopter.getOutsideDriver() == null)) {
                messages.send(player, Messages.Message.VEHICLE_ALLOWED_AS_PASSENGER);
            }
        }

        if (chair == null) return;

        if (firstChair) {
            vehicle.setDriver(player);

            // Re-apply cooldown.
            Multimap<UUID, Pair<Material, Integer>> cooldowns = vehicle.getCooldowns();
            for (Pair<Material, Integer> pair : cooldowns.get(playerUUID)) {
                player.setCooldown(pair.getKey(), pair.getValue());
            }
            cooldowns.removeAll(playerUUID);
        } else {
            vehicle.getPassengers().put(player, chair.getValue().getPartName());
            handleOwnerLeftOut(player, vehicle, false);
        }

        chair.getKey().addPassenger(player);
    }

    private boolean kickDriverIfPossible(@NotNull Player player, @NotNull Vehicle vehicle) {
        UUID ownerUUID = vehicle.getOwner();

        Player driver = vehicle.getDriver() != null ? vehicle.getDriver() : vehicle instanceof Helicopter helicopter ? helicopter.getOutsideDriver() : null;
        if (driver == null
                || driver.getUniqueId().equals(ownerUUID)
                || !ownerUUID.equals(player.getUniqueId())) return false;

        Entity chair = driver.getVehicle();
        if (chair == null) return false;

        plugin.getMessages().send(player, Messages.Message.OCCUPANT_KICKED);
        chair.eject();
        return true;
    }

    private void handleOwnerLeftOut(@NotNull Player player, @NotNull Vehicle vehicle, boolean full) {
        Messages messages = plugin.getMessages();
        if (!vehicle.isOwner(player)) {
            if (full) messages.send(player, Messages.Message.VEHICLE_FULL);
            return;
        }
        messages.send(player, Messages.Message.VEHICLE_OCCUPIED_BY_UNKNOWN);
    }

    private boolean notChair(int entityId, @NotNull List<Pair<ArmorStand, StandSettings>> chairs) {
        for (Pair<ArmorStand, StandSettings> chair : chairs) {
            if (!notChair(chair, entityId)) return false;
        }
        return true;
    }

    private boolean notChair(Pair<ArmorStand, StandSettings> pair, int entityId) {
        return pair == null || pair.getKey().getEntityId() != entityId;
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}