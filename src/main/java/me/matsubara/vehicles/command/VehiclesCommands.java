package me.matsubara.vehicles.command;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.ConfirmShopGUI;
import me.matsubara.vehicles.gui.CustomizationGUI;
import me.matsubara.vehicles.gui.ShopGUI;
import me.matsubara.vehicles.hook.EssentialsExtension;
import me.matsubara.vehicles.hook.VaultExtension;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.gps.GPSResultHandler;
import me.matsubara.vehicles.vehicle.gps.GPSTick;
import me.matsubara.vehicles.vehicle.type.Generic;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patheloper.api.pathing.Pathfinder;
import org.patheloper.api.pathing.result.PathfinderResult;
import org.patheloper.api.pathing.rules.PathingRuleSet;
import org.patheloper.api.pathing.strategy.strategies.WalkablePathfinderStrategy;
import org.patheloper.mapping.PatheticMapper;
import org.patheloper.mapping.bukkit.BukkitMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

public class VehiclesCommands implements CommandExecutor, TabCompleter {

    private final VehiclesPlugin plugin;
    private final Pathfinder pathfinder;

    private static final List<String> COMMAND_ARGS = List.of("reload", "shop", "give", "gps");
    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&9&lVehiclesWASD &f&oCommands &c<required> | [optional]",
            "&e/vwasd reload &f- &7Reload configuration files.",
            "&e/vwasd shop &f- &7Opens the vehicle shop.",
            "&8(requires Vault and an economy provider like EssentialsX, CMI, etc...)",
            "&e/vwasd give <type> [player] &f- &7Gives a vehicle.",
            "&e/vwasd gps <home> &f- &7Automatically drives to a home.",
            "&8(requires EssentialsX)",
            "&8----------------------------------------").map(PluginUtils::translate).toList();

    public VehiclesCommands(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.pathfinder = PatheticMapper.newPathfinder(PathingRuleSet.createAsyncRuleSet()
                .withAllowingFailFast(true)
                .withAllowingFallback(true)
                .withAllowingDiagonal(true)
                .withLoadingChunks(true)
                .withMaxIterations(24000)
                .withAsync(true));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (notAllowed(sender, "vehicleswasd.help")) return true;

        Messages messages = plugin.getMessages();

        String subCommand;
        boolean noArgs = args.length == 0;

        if (noArgs || args.length > 3 || !COMMAND_ARGS.contains((subCommand = args[0]).toLowerCase())) {
            if (noArgs) HELP.forEach(sender::sendMessage);
            else messages.send(sender, Messages.Message.INVALID_COMMAND);
            return true;
        }

        VehicleManager vehicleManager = plugin.getVehicleManager();

        if (subCommand.equalsIgnoreCase("reload")) {
            if (notAllowed(sender, "vehicleswasd.reload")) return true;

            messages.send(sender, Messages.Message.RELOADING);

            for (Player online : Bukkit.getOnlinePlayers()) {
                Inventory top = online.getOpenInventory().getTopInventory();
                InventoryHolder holder = top.getHolder();

                if (holder instanceof Llama llama
                        && vehicleManager.getVehicleFromLlama(llama) == null) {
                    continue;
                }

                if (!(holder instanceof ConfirmShopGUI)
                        && !(holder instanceof CustomizationGUI)
                        && !(holder instanceof ShopGUI)
                        && !(holder instanceof Vehicle)) {
                    continue;
                }

                plugin.getServer().getScheduler().runTask(plugin, online::closeInventory);
            }

            CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.reloadShopItems();
                plugin.reloadFuelItems();
                plugin.reloadExtraTags();
                plugin.reloadCraftings();

                // Save data and remove vehicle(s) from world.
                List<VehicleData> datas = new ArrayList<>();
                for (Vehicle vehicle : vehicleManager.getVehicles()) {
                    datas.add(vehicle.createSaveData());

                    // If passengers are riding a helicopter, teleport them to the ground.
                    if (vehicle instanceof Helicopter helicopter) {
                        helicopter.safePassengerTeleport();
                    }

                    vehicleManager.removeVehicle(vehicle, null);
                }
                vehicleManager.getVehicles().clear();

                // Respawn vehicles.
                for (VehicleData data : datas) {
                    vehicleManager.createVehicle(null, data);
                }

                messages.send(sender, Messages.Message.RELOAD);
            }));
            return true;
        }

        if (subCommand.equalsIgnoreCase("shop")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.shop")) return true;

            VaultExtension vaultExtension = plugin.getVaultExtension();
            if (vaultExtension == null || !vaultExtension.isEnabled()) {
                messages.send(player, Messages.Message.SHOP_ECONOMY_DISABLED);
                return true;
            }

            new ShopGUI(plugin, player, vehicleManager.getSelectedType(player));
        }

        if (subCommand.equalsIgnoreCase("gps")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.gps")) return true;

            EssentialsExtension essentials = plugin.getEssentialsExtension();
            if (essentials == null) {
                messages.send(player, Messages.Message.GPS_ESSENTIALS_NOT_FOUND);
                return true;
            }

            if (!Config.GPS_ENABLED.asBool()) {
                messages.send(player, Messages.Message.GPS_DISABLED);
                return true;
            }

            Vehicle vehicle = vehicleManager.getPlayerVehicle(player);
            if (vehicle == null) {
                messages.send(player, Messages.Message.GPS_NOT_DRIVING);
                return true;
            }

            // We could add GPS for helicopters in a future update using DirectPathfinderStrategy.
            if (!(vehicle instanceof Generic generic)) {
                messages.send(player, Messages.Message.GPS_NOT_GENERIC);
                return true;
            }

            if (args.length == 1) {
                messages.send(player, Messages.Message.GPS_SPECIFY_HOME);
                return true;
            }

            Location home = essentials.getHome(player, args[1]);
            if (home == null) {
                messages.send(player, Messages.Message.GPS_NOT_FOUND);
                return true;
            }

            UUID playerUUID = player.getUniqueId();

            if (vehicleManager.invalidateGPSResult(playerUUID)) {
                messages.send(player, Messages.Message.GPS_STOPPED);
            }

            Location start = vehicle.getVelocityStand().getLocation().clone();

            int maxDistance = Config.GPS_MAX_DISTANCE.asInt();
            int minDistance = Config.GPS_MIN_DISTANCE.asInt();

            if (start.getWorld() != null && !start.getWorld().equals(home.getWorld())) {
                messages.send(player, Messages.Message.GPS_DIFFERENT_WORLD);
                return true;
            }

            double distance = start.distance(home);
            if (distance > maxDistance) {
                messages.send(player,
                        Messages.Message.GPS_TOO_FAR,
                        string -> string.replace("%max-distance%", String.valueOf(maxDistance)));
                return true;
            } else if (distance < minDistance) {
                messages.send(player,
                        Messages.Message.GPS_TOO_CLOSE,
                        string -> string.replace("%min-distance%", String.valueOf(minDistance)));
                return true;
            }

            GPSTick gpsTick;
            if ((gpsTick = generic.getGpsTick()) != null && !gpsTick.isCancelled()) {
                gpsTick.cancel(null);
                messages.send(player, Messages.Message.GPS_PREVIOUS_CANCELLED);
            }

            messages.send(player, Messages.Message.GPS_STARTING);

            // We use a higher height since we don't want the vehicle to get stuck.
            CompletionStage<PathfinderResult> stage = pathfinder.findPath(
                    BukkitMapper.toPathPosition(start),
                    BukkitMapper.toPathPosition(home),
                    new WalkablePathfinderStrategy(4));

            GPSResultHandler result = new GPSResultHandler(player, generic, args[1]);
            stage.thenAccept(result);

            vehicleManager.getRunningPaths().put(playerUUID, result);
        }

        if (!subCommand.equalsIgnoreCase("give")) return true;
        if (notAllowed(sender, "vehicleswasd.give")) return true;

        if (args.length == 1) {
            messages.send(sender, Messages.Message.GIVE_SPECIFY_TYPE);
            return true;
        }

        String modelName = args[1];
        if (!plugin.validModel(modelName)) {
            messages.send(sender, Messages.Message.GIVE_TYPE_NOT_FOUND);
            return true;
        }

        Player target = args.length == 3 ? Bukkit.getPlayer(args[2]) : sender instanceof Player player ? player : null;
        if (target == null) {
            messages.send(sender, Messages.Message.GIVE_PLAYER_NOT_FOUND);
            return true;
        }

        target.getInventory().addItem(plugin.createVehicleItem(modelName, null));
        return true;
    }

    private boolean notAllowed(@NotNull CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        plugin.getMessages().send(sender, Messages.Message.NO_PERMISSION);
        return true;
    }

    private @Nullable Player isPlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.getMessages().send(sender, Messages.Message.NOT_FROM_CONSOLE);
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], COMMAND_ARGS, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return StringUtil.copyPartialMatches(args[1], plugin.getModelList(), new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && plugin.getModelList().contains(args[1])) {
            return null; // Return player list.
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("gps")
                && sender instanceof Player player
                && plugin.getVehicleManager().getPlayerVehicle(player) != null
                && Config.GPS_ENABLED.asBool()) {
            EssentialsExtension essentials = plugin.getEssentialsExtension();

            List<String> homes;
            if (essentials != null) homes = essentials.getHomes(player);
            else homes = Collections.emptyList();

            return StringUtil.copyPartialMatches(args[1], homes, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}