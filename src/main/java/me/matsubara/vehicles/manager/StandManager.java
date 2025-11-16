package me.matsubara.vehicles.manager;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StandManager implements Listener {

    private final VehiclesPlugin plugin;
    private @Getter boolean isBukkitArmorStand;

    private static final Set<String> VALID_TYPES = Set.of("PACKET", "BUKKIT");
    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public StandManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
        handleReload();
    }

    public void handleReload() {
        String type = Config.ARMOR_STAND_TYPE.asString("PACKET").toUpperCase(Locale.ROOT);
        isBukkitArmorStand = VALID_TYPES.contains(type) && type.equals("BUKKIT");

        HandlerList.unregisterAll(this);
        if (isPacketArmorStand()) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    public boolean isPacketArmorStand() {
        return !isBukkitArmorStand;
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            vehicle.getModel().getOut().remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation());
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation());
    }

    private boolean isInRange(@NotNull Location location, @NotNull Location check) {
        double distance = Config.RENDER_DISTANCE.asDouble();
        double distanceSquared = Math.min(distance * distance, BUKKIT_VIEW_DISTANCE);

        return Objects.equals(location.getWorld(), check.getWorld())
                && location.distanceSquared(check) <= distanceSquared;
    }

    private void handleStandRender(Player player, Location location) {
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            handleStandRender(vehicle, player, location, HandleCause.SPAWN);
        }
    }

    public void handleStandRender(@NotNull Vehicle vehicle, Player player, Location location, HandleCause cause) {
        if (vehicle.isDriver(player) || vehicle.isPassenger(player)) {
            return;
        }

        Model model = vehicle.getModel();
        Set<UUID> out = model.getOut();
        UUID playerUUID = player.getUniqueId();

        // The vehicle is in another world, there is no need to send packets.
        if (!Objects.equals(player.getWorld(), model.getLocation().getWorld())) {
            out.add(playerUUID);
            return;
        }

        boolean range = isInRange(vehicle.getVelocityStand().getLocation(), location);
        boolean ignored = out.contains(playerUUID);
        boolean spawn = cause == HandleCause.SPAWN;

        boolean show = range && (ignored || spawn);
        boolean destroy = !range && !ignored;
        if (!show && !destroy) return;

        if (show) {
            out.remove(playerUUID);
        } else {
            out.add(playerUUID);
            if (spawn) return;
        }

        // Spawn the entire model in another thread.
        plugin.getPool().execute(() -> {
            for (IStand stand : model.getStands()) {
                if (show) {
                    stand.spawn(player);
                } else {
                    stand.destroy(player);
                }
            }
        });
    }

    public enum HandleCause {
        SPAWN,
        MOVE
    }
}