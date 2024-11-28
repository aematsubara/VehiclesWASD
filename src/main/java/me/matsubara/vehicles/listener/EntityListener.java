package me.matsubara.vehicles.listener;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

public class EntityListener implements Listener {

    private final VehiclesPlugin plugin;

    public EntityListener(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        // Prevent suffocation/contact when a player is inside a vehicle.
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.SUFFOCATION
                && cause != EntityDamageEvent.DamageCause.CONTACT) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleByEntity(event.getEntity(), true);
        if (vehicle != null) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityCombustByEntity(@NotNull EntityCombustByEntityEvent event) {
        // Prevent combustion by own vehicle weapon when driving.
        handleVehicleDamage(event.getEntity(), event.getCombuster(), event);
    }

    @EventHandler
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        // Prevent damage by own vehicle weapon when driving.
        handleVehicleDamage(event.getEntity(), event.getDamager(), event);

        if (!Config.PLANE_FIRE_CREEPER_EXPLODE.asBool()) return;
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (!(event.getDamager() instanceof Projectile projectile)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleByProjectile(projectile, null);
        if (vehicle == null) return;

        creeper.explode();
    }

    private void handleVehicleDamage(Entity entity, Entity damager, Cancellable cancellable) {
        if (!(damager instanceof Projectile projectile)) return;
        if (!entity.equals(projectile.getShooter()) || !(entity instanceof Player player)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleByProjectile(projectile, player);
        if (vehicle != null) cancellable.setCancelled(true);
    }
}