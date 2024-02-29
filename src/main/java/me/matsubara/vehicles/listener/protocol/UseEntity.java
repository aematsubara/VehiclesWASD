package me.matsubara.vehicles.listener.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class UseEntity extends PacketAdapter {

    private final VehiclesPlugin plugin;

    public UseEntity(VehiclesPlugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.USE_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent event) {
        int entityId = event.getPacket().getIntegers().readSafely(0);

        WrappedEnumEntityUseAction useAction = event.getPacket().getEnumEntityUseActions().readSafely(0);

        EnumWrappers.EntityUseAction action = useAction.getAction();
        boolean left = action == EnumWrappers.EntityUseAction.ATTACK;

        EnumWrappers.Hand hand = left ? null : useAction.getHand();

        // We only need to listen to ATTACK (left) or INTERACT_AT (right).
        // For armor stands, only INTERACT_AT is called; for llamas, INTERACT and INTERACT_AT are called.
        if (action == EnumWrappers.EntityUseAction.INTERACT) return;
        if (hand == EnumWrappers.Hand.OFF_HAND) return;

        Player player = event.getPlayer();
        VehicleManager vehicleManager = plugin.getVehicleManager();

        // The player is already inside a vehicle.
        if (player.isInsideVehicle()) {
            Vehicle vehicle = vehicleManager.getPlayerVehicle(player);
            if (vehicle == null) return;

            event.setCancelled(true);

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (!left && vehicle.is(VehicleType.TANK) && handItem.getType() == Material.FIRE_CHARGE) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {

                    int newAmount = handItem.getAmount() - 1;
                    if (newAmount == 0) {
                        player.getInventory().setItemInMainHand(null);
                    } else {
                        handItem.setAmount(newAmount);
                        player.getInventory().setItemInMainHand(handItem);
                    }

                    Fireball fireball = player.launchProjectile(Fireball.class);
                    fireball.setDirection(player.getLocation().getDirection().multiply(3));
                });
            }
            return;
        }

        if (handlePreviews(player, entityId, left)) return;

        Iterator<Vehicle> iterator = vehicleManager.getVehicles().iterator();
        while (iterator.hasNext()) {
            Vehicle vehicle = iterator.next();

            Model model = vehicle.getModel();
            if (notChair(entityId, vehicle.getChairs())
                    && (model.getById(entityId) == null)
                    && (vehicle.getVelocityStand() == null || vehicle.getVelocityStand().getEntityId() != entityId)) {
                continue;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                World world = player.getWorld();
                vehicle.resetRealEntities(world);

                handleOutsideVehicleInteract(player, vehicle, iterator, left);
            });

            event.setCancelled(true);
            break;
        }
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private boolean handlePreviews(@NotNull Player player, int entityId, boolean left) {
        if (!player.isSneaking() || !left) return false;

        Collection<PreviewTick> previews = plugin.getVehicleManager().getPreviews().values();
        if (previews.isEmpty()) return false;

        Iterator<PreviewTick> iterator = previews.iterator();
        while (iterator.hasNext()) { // We use an iterator because when doing PreviewTick#cancel we're removing the preview from the map.
            PreviewTick preview = iterator.next();

            PacketStand stand = preview.getModel().getById(entityId);
            if (stand == null) continue;

            preview.cancel();
            return true;
        }

        return false;
    }

    private void handleOutsideVehicleInteract(@NotNull Player player, @NotNull Vehicle vehicle, Iterator<Vehicle> iterator, boolean left) {
        UUID playerUUID = player.getUniqueId();
        Messages messages = plugin.getMessages();

        if (left) {
            if (!player.isSneaking()) return;

            if (!playerUUID.equals(vehicle.getOwner())) {
                messages.send(player, Messages.Message.NOT_YOUR_VEHICLE);
                return;
            }

            plugin.getVehicleManager().removeVehicle(vehicle, player);

            iterator.remove();
            return;
        }

        if (player.isSneaking()) {
            if (!playerUUID.equals(vehicle.getOwner())) return;

            Pair<LivingEntity, StandSettings> primaryChair = vehicle.getChair(0);
            if (primaryChair != null) {
                if (kickDriverIfPossible(player, vehicle)) return;
                if (primaryChair.getKey() instanceof Llama llama) {
                    PluginUtils.openLlamaInventory(player, llama);
                }
            }
            return;
        }

        if (vehicle.getType() != VehicleType.BOAT && vehicle.getVelocityStand().getLocation().getBlock().isLiquid()) {
            messages.send(player, Messages.Message.VEHICLE_IN_WATER);
            return;
        }

        boolean firstChair = vehicle.getDriver() == null && (!(vehicle instanceof Helicopter helicopter) || helicopter.getOutsideDriver() == null);
        if (!firstChair && vehicle.getChairs().size() == 1) {
            // The driver slot is already occupied and there isn't any more chairs.
            handleOwnerLeftOut(player, vehicle, true);
            return;
        }

        boolean notOwnerLocked = !playerUUID.equals(vehicle.getOwner()) && vehicle.isLocked();
        if (firstChair && notOwnerLocked) {
            firstChair = false;
        }

        Pair<LivingEntity, StandSettings> chair = null;
        if (firstChair) {
            chair = vehicle.getChair(0);
        } else {
            if (notOwnerLocked && vehicle.getChairs().size() == 1) {
                messages.send(player, Messages.Message.VEHICLE_LOCKED);
                return;
            }

            for (Pair<LivingEntity, StandSettings> pair : vehicle.getChairs()) {
                String partName = pair.getValue().getPartName();
                if (partName.equals("CHAIR_1")) continue;
                if (vehicle.getPassengers().containsValue(partName)) continue;
                chair = pair;
                break;
            }

            // The driver slot is already occupied and the passenger slots are all occupied.
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
        if (!player.getUniqueId().equals(vehicle.getOwner())) {
            if (full) messages.send(player, Messages.Message.VEHICLE_FULL);
            return;
        }
        messages.send(player, Messages.Message.VEHICLE_OCCUPIED_BY_UNKNOWN);
    }

    private boolean notChair(int entityId, @NotNull List<Pair<LivingEntity, StandSettings>> chairs) {
        for (Pair<LivingEntity, StandSettings> chair : chairs) {
            if (!notChair(chair, entityId)) return false;
        }
        return true;
    }

    private boolean notChair(Pair<LivingEntity, StandSettings> pair, int entityId) {
        return pair == null || pair.getKey().getEntityId() != entityId;
    }
}