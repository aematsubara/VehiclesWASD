package me.matsubara.vehicles.hook;

import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.Particles;
import com.cryptomorin.xseries.particles.XParticle;
import com.google.common.base.Preconditions;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.event.VehicleSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class WGExtension implements AVExtension<WGExtension>, Listener {

    private StateFlag placeFlag;
    private StateFlag useFlag;

    private static final int PARTICLE_DELAY = 30;

    @Override
    public WGExtension init(@NotNull VehiclesPlugin plugin) {
        placeFlag = registerFlag("av-place", plugin);
        useFlag = registerFlag("av-use", plugin);
        return this;
    }

    @Override
    public void onEnable(@NotNull VehiclesPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private @Nullable StateFlag registerFlag(@SuppressWarnings("SameParameterValue") String flagName, VehiclesPlugin plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        try {
            StateFlag flag = new StateFlag(flagName, true);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException exception) {
            Flag<?> existing = registry.get(flagName);
            if (existing instanceof StateFlag) return (StateFlag) existing;

            plugin.getLogger().severe("The flag {" + flagName + "} is already registered by another plugin with a different type!");
        }

        return null;
    }

    @EventHandler
    public void onVehicleSpawn(@NotNull VehicleSpawnEvent event) {
        if (placeFlag == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        if (!allowed(player, event.getLocation(), placeFlag, PARTICLE_DELAY)) {
            event.setCancelled(true);
        }
    }

    public boolean canMoveHere(Player player, Location location, int tick) {
        if (placeFlag == null) return true;
        return allowed(player, location, useFlag, tick);
    }

    private boolean allowed(@Nullable Player player, Location location, StateFlag flag, int tick) {
        ApplicableRegionSet set = getRegionSet(location);
        boolean allowed = set == null || set.testState(wrapPlayer(player), flag);

        if (tick % PARTICLE_DELAY == 0 && set != null && player != null && !allowed) {
            ParticleDisplay display = ParticleDisplay.of(XParticle.DUST)
                    .withColor(Color.RED, 1.5f)
                    .onlyVisibleTo(player);

            for (ProtectedRegion region : set.getRegions()) {
                World world = player.getWorld();
                Particles.structuredCube(
                        BukkitAdapter.adapt(world, region.getMinimumPoint()),
                        BukkitAdapter.adapt(world, region.getMaximumPoint()),
                        0.5d,
                        display);
            }
        }

        return allowed;
    }

    private @Nullable LocalPlayer wrapPlayer(@Nullable Player bukkitPlayer) {
        return bukkitPlayer == null ? null : WorldGuardPlugin.inst().wrapPlayer(bukkitPlayer);
    }

    private @Nullable ApplicableRegionSet getRegionSet(@NotNull Location location) {
        World world = location.getWorld();
        Preconditions.checkNotNull(world);

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) return null;

        return manager.getApplicableRegions(BlockVector3.at(location.getX(), location.getY(), location.getZ()));
    }
}