package me.matsubara.vehicles.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.VehicleGUI;
import me.matsubara.vehicles.manager.VehicleManager;
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

        VehicleManager vehicleManager = plugin.getVehicleManager();

        // The player is already inside a vehicle.
        if (player.isInsideVehicle()) {
            Vehicle vehicle = vehicleManager.getPlayerVehicle(player);
            if (vehicle == null) return;

            event.setCancelled(true);

            PlayerInventory inventory = player.getInventory();
            ItemStack handItem = inventory.getItemInMainHand();

            if (!left && vehicle.is(VehicleType.TANK) && handItem.getType() == Material.FIRE_CHARGE) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {

                    int newAmount = handItem.getAmount() - 1;
                    if (newAmount == 0) {
                        inventory.setItemInMainHand(null);
                    } else {
                        handItem.setAmount(newAmount);
                        inventory.setItemInMainHand(handItem);
                    }

                    double speedMultiplier = plugin.getConfig().getDouble("tank-fire.speed-multiplier", 3.0d);
                    boolean incendiary = plugin.getConfig().getBoolean("tank-fire.incendiary", false);
                    double radius = plugin.getConfig().getDouble("tank-fire.radius", 1.0f);

                    Fireball fireball = player.launchProjectile(Fireball.class);
                    fireball.setDirection(player.getLocation().getDirection().multiply(speedMultiplier));
                    fireball.setIsIncendiary(incendiary);
                    fireball.setYield((float) radius);
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
        UUID playerUUID = player.getUniqueId();
        Messages messages = plugin.getMessages();

        if (left) {
            if (!player.isSneaking()) return;

            if (!playerUUID.equals(vehicle.getOwner()) && !player.hasPermission("vehicleswasd.remove")) {
                messages.send(player, Messages.Message.NOT_YOUR_VEHICLE);
                return;
            }

            plugin.getVehicleManager().removeVehicle(vehicle, player);

            iterator.remove();
            return;
        }

        if (player.isSneaking()) {
            if (!playerUUID.equals(vehicle.getOwner())) return;

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

        boolean notOwnerLocked = !playerUUID.equals(vehicle.getOwner()) && vehicle.isLocked();
        if (firstChair && notOwnerLocked) {
            firstChair = false;
        }

        Pair<ArmorStand, StandSettings> chair = null;
        if (firstChair) {
            chair = vehicle.getChair(0);
        } else {
            if (notOwnerLocked && vehicle.getChairs().size() == 1) {
                messages.send(player, Messages.Message.VEHICLE_LOCKED);
                return;
            }

            for (Pair<ArmorStand, StandSettings> pair : vehicle.getChairs()) {
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