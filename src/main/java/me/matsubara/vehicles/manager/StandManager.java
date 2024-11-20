package me.matsubara.vehicles.manager;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StandManager implements Listener {

    private final VehiclesPlugin plugin;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public StandManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), HandleCause.SPAWN);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getCause() == VehicleManager.CONFLICT_CAUSE) return; // Ignore dismount teleport?
        handleMovementEvent(event, HandleCause.TELEPORT);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        handleMovementEvent(event, HandleCause.MOVE);
    }

    private void handleMovementEvent(@NotNull PlayerMoveEvent event, HandleCause cause) {
        Location to = event.getTo();
        if (to == null) return;

        // Only handle renders if the player moved at least 1 block.
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        handleStandRender(player, to, cause);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), HandleCause.SPAWN);
    }

    public boolean isInRange(@NotNull Location location, @NotNull Location check) {
        double distance = Config.RENDER_DISTANCE.asDouble();
        double distanceSquared = Math.min(distance * distance, BUKKIT_VIEW_DISTANCE);

        return Objects.equals(location.getWorld(), check.getWorld())
                && location.distanceSquared(check) <= distanceSquared;
    }

    public void handleStandRender(Player player, Location location, HandleCause cause) {
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.isDriver(player) || vehicle.isPassenger(player)) continue;

            Model model = vehicle.getModel();

            // The vehicle is in another world, there is no need to send packets.
            if (!Objects.equals(player.getWorld(), model.getLocation().getWorld())) {
                continue;
            }

            Set<UUID> out = model.getOut();

            boolean ignored = out.contains(player.getUniqueId());
            boolean show = isInRange(vehicle.getVelocityStand().getLocation(), location);
            boolean spawn = cause == HandleCause.SPAWN || (cause == HandleCause.TELEPORT && !show);

            if (show && (ignored || spawn)) {
                out.remove(player.getUniqueId());
            } else if (!show) {
                out.add(player.getUniqueId());
            }

            // Spawn the entire model in another thread.
            plugin.getPool().execute(() -> {
                for (PacketStand stand : model.getStands()) {
                    if (show && (ignored || spawn)) {
                        stand.spawn(player);
                    } else if (!show) {
                        stand.destroy(player);
                    }
                }
            });
        }
    }

    public enum HandleCause {
        SPAWN,
        TELEPORT,
        MOVE
    }
}