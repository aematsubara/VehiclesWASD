package me.matsubara.vehicles.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.SessionManager;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.event.VehicleSpawnEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WGExtension implements AVExtension<WGExtension>, Listener {

    private StateFlag placeFlag;
    private StateFlag useFlag;

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

        // If the player is null, then the server placed this vehicle.
        Player player = event.getPlayer();
        if (player == null) return;

        if (!allowed(player, event.getLocation(), placeFlag)) {
            event.setCancelled(true);
        }
    }

    public boolean canMoveHere(Player player, Location location) {
        return useFlag != null && allowed(player, location, useFlag, Flags.ENTRY);
    }

    public boolean canBreakHere(Player player, Location location) {
        return allowed(player, location, Flags.BUILD);
    }

    private boolean allowed(@Nullable Player player, @NotNull Location location, StateFlag... flags) {
        WorldGuardPlatform platform = WorldGuard.getInstance().getPlatform();

        RegionContainer container = platform.getRegionContainer();
        RegionQuery query = container.createQuery();

        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
        if (set == null) return true;

        LocalPlayer wrapped = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
        if (set.testState(wrapped, flags)) return true;

        if (wrapped == null) return false;

        SessionManager manager = platform.getSessionManager();
        return manager.hasBypass(wrapped, wrapped.getWorld());
    }
}