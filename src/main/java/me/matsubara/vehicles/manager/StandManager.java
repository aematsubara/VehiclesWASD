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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        handleStandRender(player, to, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    public void handleStandRender(@NotNull Player player, Location location, boolean isSpawn) {
        UUID playerUUID = player.getUniqueId();

        for (Vehicle game : plugin.getVehicleManager().getVehicles()) {
            if (playerUUID.equals(game.getDriver()) || game.getPassengers().containsKey(playerUUID)) continue;

            boolean shouldShow = true;

            for (PacketStand stand : game.getModel().getStands()) {
                if (!stand.isInRange(location)) shouldShow = false;
            }

            // Show/hide model stands.
            handleStandRender(player, game.getModel().getStands(), shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, @NotNull Collection<PacketStand> stands, boolean shouldShow, boolean isSpawn) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean shouldShow, boolean isSpawn) {
        if (shouldShow) {
            if (stand.isIgnored(player) || isSpawn) stand.spawn(player);
        } else {
            if (!stand.isIgnored(player)) stand.destroy(player);
        }
    }
}