package me.matsubara.vehicles.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.VehicleGUI;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class UseEntity extends SimplePacketListenerAbstract {

    private final VehiclesPlugin plugin;

    public UseEntity(VehiclesPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
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
            handleTank(event, player, left);
            return;
        }

        if (handlePreviews(player, entityId, left)) return;

        Iterator<Vehicle> iterator = plugin.getVehicleManager().getVehicles().iterator();
        while (iterator.hasNext()) {
            Vehicle vehicle = iterator.next();
            if (vehicle.isRemoved()) continue;

            Model model = vehicle.getModel();
            if (notChair(entityId, vehicle.getChairs())
                    && (model.getById(entityId) == null)
                    && (vehicle.getVelocityStand() == null || vehicle.getVelocityStand().getEntityId() != entityId)) {
                continue;
            }

            plugin.getServer().getScheduler().runTask(plugin,
                    () -> handleOutsideVehicleInteract(player, vehicle, iterator, left));

            event.setCancelled(true);
            break;
        }
    }

    private void handleTank(PacketPlayReceiveEvent event, Player player, boolean left) {
        Vehicle vehicle = plugin.getVehicleManager().getVehicleByEntity(player);
        if (vehicle == null || !Config.TANK_FIRE_ENABLED.asBool()) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItemInMainHand();

        if (left || !vehicle.is(VehicleType.TANK) || item.getType() != Material.FIRE_CHARGE) return;

        event.setCancelled(true);

        int cooldown = player.getCooldown(Material.FIRE_CHARGE);
        if (cooldown > 0) {
            double seconds = cooldown / 20.0d;
            plugin.getMessages().send(
                    player,
                    Messages.Message.TANK_COOLDOWN,
                    line -> line.replace("%seconds%", String.format("%.2f", seconds)));
            return;
        }

        // Update item in the hand.
        item.setAmount(item.getAmount() - 1);
        inventory.setItemInMainHand(item.getAmount() == 0 ? null : item);

        // We need to apply the cooldown and spawn the fireball SYNC.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (item.getAmount() > 0 || inventory.contains(Material.FIRE_CHARGE)) {
                int cooldownTicks = (int) (Config.TANK_FIRE_COOLDOWN.asDouble() * 20);
                player.setCooldown(Material.FIRE_CHARGE, cooldownTicks);
            }

            double speedMultiplier = Config.TANK_FIRE_SPEED_MULTIPLIER.asDouble(3.0d);
            boolean incendiary = Config.TANK_FIRE_INCENDIARY.asBool(false);
            float radius = (float) Config.TANK_FIRE_RADIUS.asDouble(1.0d);

            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setMetadata("VehicleSource", new FixedMetadataValue(plugin, vehicle));
            fireball.setDirection(player.getLocation().getDirection().multiply(speedMultiplier));
            fireball.setIsIncendiary(incendiary);
            fireball.setYield(radius);
            fireball.setShooter(player);
        });
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private boolean handlePreviews(@NotNull Player player, int entityId, boolean left) {
        if (!player.isSneaking() || !left) return false;

        Collection<PreviewTick> previews = plugin.getVehicleManager().getPreviews().values();
        if (previews.isEmpty()) return false;

        Iterator<PreviewTick> iterator = previews.iterator();
        while (iterator.hasNext()) { // We use an iterator because, when doing PreviewTick#cancel, we're removing the preview from the map.
            PreviewTick preview = iterator.next();

            PacketStand stand = preview.getModel().getById(entityId);
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

            plugin.getVehicleManager().removeVehicle(vehicle, player);

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
                plugin.getServer().getScheduler().runTask(plugin, () -> new VehicleGUI(plugin, player, vehicle));
            }
            return;
        }

        if (vehicle.getType() != VehicleType.BOAT && vehicle.getVelocityStand().getLocation().getBlock().isLiquid()) {
            messages.send(player, Messages.Message.VEHICLE_IN_WATER);
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
            vehicle.setDriver(playerUUID);

            // Re-apply cooldown.
            Integer cooldown = vehicle.getFireballCooldown().get(playerUUID);
            if (cooldown != null && cooldown > 0) player.setCooldown(Material.FIRE_CHARGE, cooldown);
        } else {
            vehicle.getPassengers().put(playerUUID, chair.getValue().getPartName());
            handleOwnerLeftOut(player, vehicle, false);
        }

        chair.getKey().addPassenger(player);
    }

    private boolean kickDriverIfPossible(@NotNull Player player, @NotNull Vehicle vehicle) {
        UUID ownerUUID = vehicle.getOwner();
        UUID driverUUID = vehicle.getDriver();

        UUID temp = driverUUID != null ? driverUUID : vehicle instanceof Helicopter helicopter ? helicopter.getOutsideDriver() : null;
        if (temp == null
                || temp.equals(ownerUUID)
                || !ownerUUID.equals(player.getUniqueId())) return false;

        Player driver = Bukkit.getPlayer(temp);
        if (driver == null) return false;

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
}