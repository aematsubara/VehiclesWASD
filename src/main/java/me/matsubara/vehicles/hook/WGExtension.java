package me.matsubara.vehicles.hook;

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
import com.sk89q.worldguard.protection.regions.RegionContainer;
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

    @Override
    public WGExtension init(@NotNull VehiclesPlugin plugin) {
        placeFlag = registerFlag("av-place", plugin);
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
            if (existing instanceof StateFlag) {
                return (StateFlag) existing;
            } else {
                plugin.getLogger().severe("The flag {" + flagName + "} is already registered by another plugin with a different type!");
            }
        }

        return null;
    }

    @EventHandler
    public void onVehicleSpawn(@NotNull VehicleSpawnEvent event) {
        if (placeFlag == null) return;

        Player player = event.getPlayer();
        if (player == null) return;

        ApplicableRegionSet set = getRegionSet(player, event.getLocation());
        if (set != null && !set.testState(wrapPlayer(player), placeFlag)) {
            event.setCancelled(true);
        }
    }

    private LocalPlayer wrapPlayer(Player bukkitPlayer) {
        return WorldGuardPlugin.inst().wrapPlayer(bukkitPlayer);
    }

    private @Nullable ApplicableRegionSet getRegionSet(Player bukkitPlayer, Location location) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        RegionManager manager = container.get(wrapPlayer(bukkitPlayer).getWorld());
        if (manager == null) return null;

        return manager.getApplicableRegions(BlockVector3.at(location.getX(), location.getY(), location.getZ()));
    }
}