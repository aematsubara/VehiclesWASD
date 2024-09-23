package me.matsubara.vehicles.listener;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class PlayerListener implements Listener {

    private final VehiclesPlugin plugin;

    public PlayerListener(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        PersistentDataContainer container = event.getEntity().getPersistentDataContainer();
        if (!container.has(plugin.getVehicleModelIdKey(), PersistentDataType.STRING)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (!(byEntity.getDamager() instanceof Fireball fireball)) return;

            for (MetadataValue value : fireball.getMetadata("VehicleSource")) {
                if (!(value.value() instanceof Vehicle vehicle)
                        || !vehicle.isDriver(player)) continue;
                event.setCancelled(true);
                return;
            }
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.SUFFOCATION
                && cause != EntityDamageEvent.DamageCause.CONTACT) return;

        Vehicle vehicle = plugin.getVehicleManager().getPlayerVehicle(player, true);
        if (vehicle != null) event.setCancelled(true);
    }
}