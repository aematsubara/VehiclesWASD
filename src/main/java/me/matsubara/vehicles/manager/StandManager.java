package me.matsubara.vehicles.manager;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

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
        handleStandRender(player, player.getLocation(), true);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getCause() == VehicleManager.CONFLICT_CAUSE) return; // Ignore dismount teleport?
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

    public boolean isInRange(@NotNull Location location, Location check) {
        double distance = Config.RENDER_DISTANCE.asDouble();
        double distanceSquared = Math.min(distance * distance, BUKKIT_VIEW_DISTANCE);

        World world = location.getWorld();
        if (world == null) return false;

        return world.equals(check.getWorld())
                && location.distanceSquared(check) <= distanceSquared;
    }

    public void handleStandRender(@NotNull Player player, Location location, boolean spawn) {
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.isDriver(player) || vehicle.isPassenger(player)) continue;

            boolean show = isInRange(vehicle.getVelocityStand().getLocation(), location);
            handleStandRender(player, vehicle.getModel(), show, spawn);
        }
    }

    private void handleStandRender(@NotNull Player player, @NotNull Model model, boolean show, boolean spawn) {
        boolean ignored = model.getIgnored().contains(player.getUniqueId());

        for (PacketStand stand : model.getStands()) {
            if (show) {
                if (ignored || spawn) {
                    stand.spawn(player, true);
                }
                continue;
            }

            if (!ignored) {
                stand.destroy(player);
            }
        }
    }
}