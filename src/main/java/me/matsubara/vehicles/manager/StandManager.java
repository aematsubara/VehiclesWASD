package me.matsubara.vehicles.manager;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.vehicle.Vehicle;
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

import java.util.Collection;

public final class StandManager implements Listener {

    private final VehiclesPlugin plugin;

    public StandManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        handleMovement(event, true);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        handleMovement(event, false);
    }

    private void handleMovement(@NotNull PlayerMoveEvent event, boolean isSpawn) {
        Location to = event.getTo();
        if (to == null) return;

        // Only handle renders if the player moved at least 1 block.
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        handleStandRender(player, to, isSpawn);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    public void handleStandRender(@NotNull Player player, Location location, boolean isSpawn) {
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.isDriver(player) || vehicle.isPassenger(player)) continue;

            boolean shouldShow = true;

            for (PacketStand stand : vehicle.getModel().getStands()) {
                if (!stand.isInRange(location)) shouldShow = false;
            }

            // Show/hide model stands.
            handleStandRender(player, vehicle.getModel().getStands(), shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, @NotNull Collection<PacketStand> stands, boolean shouldShow, boolean isSpawn) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean shouldShow, boolean isSpawn) {
        if (shouldShow) {
            if (stand.isIgnored(player) || isSpawn) stand.spawn(player, true);
        } else {
            if (!stand.isIgnored(player)) stand.destroy(player);
        }
    }
}