package me.matsubara.vehicles.command;

import com.google.common.collect.Lists;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.ShopVehicle;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.*;
import me.matsubara.vehicles.hook.EconomyExtension;
import me.matsubara.vehicles.hook.EssentialsExtension;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.gps.GPSResultHandler;
import me.matsubara.vehicles.vehicle.gps.GPSTick;
import me.matsubara.vehicles.vehicle.gps.filter.DangerousMaterialsFilter;
import me.matsubara.vehicles.vehicle.gps.filter.WalkableFilter;
import me.matsubara.vehicles.vehicle.gps.filter.WorldBorderFilter;
import me.matsubara.vehicles.vehicle.type.Generic;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.patheloper.api.pathing.Pathfinder;
import org.patheloper.api.pathing.configuration.PathfinderConfiguration;
import org.patheloper.api.pathing.filter.PathFilter;
import org.patheloper.api.pathing.filter.filters.PassablePathFilter;
import org.patheloper.api.pathing.result.PathfinderResult;
import org.patheloper.mapping.PatheticMapper;
import org.patheloper.mapping.bukkit.BukkitMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

public class VehiclesCommands implements CommandExecutor, TabCompleter {

    private final VehiclesPlugin plugin;
    private final Pathfinder pathfinder;
    private final List<PathFilter> defaultFilters = List.of(
            new WalkableFilter(4),
            new PassablePathFilter(),
            new DangerousMaterialsFilter(EnumSet.of(Material.CACTUS, Material.LAVA), 3));

    private static final List<String> NONE = List.of("none");
    private static final List<String> VALID_TYPES = Stream.of(VehicleType.values()).map(VehicleType::toPath).toList();
    private static final List<String> COMMAND_ARGS = List.of("reload", "shop", "give", "gps", "fuel", "preview", "vehicles", "customization");
    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&9&lVehiclesWASD &f&oCommands &c<required> | [optional]",
            "&e/vwasd reload &f- &7Reload configuration files.",
            "&e/vwasd shop &f- &7Opens the vehicle shop.",
            "&8(requires Vault and an economy provider like EssentialsX, CMI, etc...)",
            "&e/vwasd give <type> [player] &f- &7Gives a vehicle.",
            "&e/vwasd preview <type> <none/shop-id> [player] &f- &7Preview a vehicle.",
            "&e/vwasd vehicles &f- &7Manage your vehicles.",
            "&e/vwasd gps <home> &f- &7Automatically drives to a home.",
            "&8(requires EssentialsX)",
            "&e/vwasd fuel [player] &f- &7Gives a fuel can.",
            "&e/vswad customization &f- &7Copy customizations from the current vehicle.",
            "&8----------------------------------------").map(PluginUtils::translate).toList();
    private static final Set<String> TYPE_LIST_USER = Set.of("give", "preview");

    public VehiclesCommands(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.pathfinder = PatheticMapper.newPathfinder(PathfinderConfiguration.createAsyncConfiguration()
                .withAllowingFailFast(true)
                .withAllowingFallback(true)
                .withAllowingDiagonal(true)
                .withLoadingChunks(true)
                .withMaxIterations(24000)
                .withAsync(true));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (notAllowed(sender, "vehicleswasd.command.help")) return true;

        Messages messages = plugin.getMessages();

        String sub;
        boolean empty = args.length == 0;

        if (empty || args.length > 4 || !COMMAND_ARGS.contains((sub = args[0]).toLowerCase(Locale.ROOT))) {
            if (empty) HELP.forEach(sender::sendMessage);
            else messages.send(sender, Messages.Message.INVALID_COMMAND, line -> line.replace("%alias%", label));
            return true;
        }

        VehicleManager manager = plugin.getVehicleManager();

        if (sub.equalsIgnoreCase("customization")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.command.customization")) return true;

            Vehicle vehicle = manager.getVehicleByEntity(player);
            if (vehicle == null) {
                messages.send(player, Messages.Message.CUSTOMIZATION_NOT_DRIVING);
                return true;
            }

            List<String> list = vehicle.getCustomizationChanges().entrySet().stream()
                    .map(entry -> entry.getKey() + ":" + entry.getValue().name())
                    .toList();

            if (list.isEmpty()) {
                messages.send(player, Messages.Message.CUSTOMIZATION_NO_CUSTOMIZATIONS);
                return true;
            }

            String click = String.join("\n", Lists.asList("changes:", list.stream()
                    .map(object -> "  - " + object)
                    .toArray(String[]::new)));

            String hover = String.join("\n", messages.getMessages(Messages.Message.CUSTOMIZATION_CLICK_HOVER));

            Player.Spigot spigot = player.spigot();
            for (String line : messages.getMessages(Messages.Message.CUSTOMIZATION_CLICK_TO_COPY)) {
                BaseComponent[] message = new ComponentBuilder()
                        .append(TextComponent.fromLegacyText(PluginUtils.translate(line)))
                        .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, click))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)))
                        .create();
                spigot.sendMessage(message);
            }

            return true;
        }

        if (sub.equalsIgnoreCase("reload")) {
            if (notAllowed(sender, "vehicleswasd.command.reload")) return true;

            messages.send(sender, Messages.Message.RELOADING);

            for (Player online : Bukkit.getOnlinePlayers()) {
                Inventory top = online.getOpenInventory().getTopInventory();
                InventoryHolder holder = top.getHolder();

                if (!(holder instanceof ConfirmShopGUI)
                        && !(holder instanceof CustomizationGUI)
                        && !(holder instanceof ShopGUI)
                        && !(holder instanceof VehicleGUI)
                        && !(holder instanceof Vehicle)) {
                    continue;
                }

                plugin.getServer().getScheduler().runTask(plugin, online::closeInventory);
            }

            CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.resetEconomyProvider();

                plugin.reloadShopItems();
                plugin.reloadExtraTags();
                plugin.reloadFuelItems();
                plugin.reloadBreakBlocks();
                plugin.reloadRecipes();

                manager.getModels().clear();

                // Save data and remove vehicle(s) from world.
                List<VehicleData> datas = new ArrayList<>();
                for (Vehicle vehicle : manager.getVehicles()) {
                    datas.add(vehicle.createSaveData());
                    manager.removeVehicle(vehicle, null);
                }
                manager.getVehicles().clear();

                // Respawn vehicles.
                for (VehicleData data : datas) {
                    manager.createVehicle(null, data);
                }

                messages.send(sender, Messages.Message.RELOAD);
            }));
            return true;
        }

        if (sub.equalsIgnoreCase("shop")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.command.shop")) return true;

            EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
            if (economyExtension == null || !economyExtension.isEnabled()) {
                messages.send(player, Messages.Message.SHOP_ECONOMY_DISABLED, line -> line.replace("%alias%", label));
                return true;
            }

            // We need to reload this here instead of on VehiclesPlugin#onEnabled()
            // because the provider may not be enabled yet.
            plugin.reloadShopItemsIfNeeded();

            new ShopGUI(plugin, player, manager.getSelectedType(player));
        }

        if (sub.equalsIgnoreCase("gps")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.command.gps")) return true;

            EssentialsExtension essentials = plugin.getEssentialsExtension();
            if (essentials == null) {
                messages.send(player, Messages.Message.GPS_ESSENTIALS_NOT_FOUND);
                return true;
            }

            if (!Config.GPS_ENABLED.asBool()) {
                messages.send(player, Messages.Message.GPS_DISABLED);
                return true;
            }

            Vehicle vehicle = manager.getVehicleByEntity(player);
            if (vehicle == null) {
                messages.send(player, Messages.Message.GPS_NOT_DRIVING);
                return true;
            }

            // We could add GPS for helicopters in a future update using DirectPathfinderStrategy.
            if (!(vehicle instanceof Generic generic) || generic.is(VehicleType.PLANE)) {
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

            if (manager.invalidateGPSResult(playerUUID)) {
                messages.send(player, Messages.Message.GPS_STOPPED);
            }

            Location start = generic.getVelocityStand().getLocation().clone();

            int maxDistance = Config.GPS_MAX_DISTANCE.asInt();
            int minDistance = Config.GPS_MIN_DISTANCE.asInt();

            if (start.getWorld() != null && !start.getWorld().equals(home.getWorld())) {
                messages.send(player, Messages.Message.GPS_DIFFERENT_WORLD);
                return true;
            }

            if (generic.notAllowedHere(home)) {
                messages.send(player, Messages.Message.GPS_VEHICLES_DENIED);
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

            // Create final filters.
            List<PathFilter> filters = new ArrayList<>(defaultFilters);
            filters.add(new WorldBorderFilter(generic));

            CompletionStage<PathfinderResult> stage = pathfinder.findPath(
                    BukkitMapper.toPathPosition(start),
                    BukkitMapper.toPathPosition(home),
                    filters);

            GPSResultHandler result = new GPSResultHandler(player, generic, args[1]);
            stage.thenAccept(result);

            manager.getRunningPaths().put(playerUUID, result);
        }

        if (sub.equalsIgnoreCase("fuel")) {
            Player target = getTargetPlayer(sender, args, 2, "fuel");
            if (target == null) return true;

            target.getInventory().addItem(Config.PREMIUM_FUEL.asItemBuilder()
                    .setData(plugin.getFuelItemKey(), PersistentDataType.INTEGER, 1)
                    .build());
            return true;
        }

        if (sub.equalsIgnoreCase("vehicles")) {
            Player player = isPlayer(sender);
            if (player == null) return true;

            if (notAllowed(player, "vehicleswasd.command.vehicles")) return true;

            // We need to reload this here instead of on VehiclesPlugin#onEnabled()
            // because the provider may not be enabled yet.
            plugin.reloadShopItemsIfNeeded();

            new MyVehiclesGUI(plugin, player);
            return true;
        }

        if (sub.equalsIgnoreCase("preview")) {
            Player target = getTargetPlayer(sender, args, 4, "preview");
            if (target == null) return true;

            if (args.length == 1) {
                messages.send(sender, Messages.Message.PREVIEW_SPECIFY_TYPE);
                return true;
            }

            VehicleType type = PluginUtils.getOrNull(VehicleType.class, args[1].toUpperCase(Locale.ROOT));
            if (type == null) {
                messages.send(sender, Messages.Message.PREVIEW_TYPE_NOT_FOUND);
                return true;
            }

            boolean shopProvided = args.length >= 3;
            VehicleData data = shopProvided ? getPreviewData(type, args) : null;

            if (shopProvided && data == null && !args[2].equalsIgnoreCase("none")) {
                messages.send(sender, Messages.Message.PREVIEW_SHOP_NOT_FOUND);
            }

            VehicleData temp = data != null ? data : VehicleData.createDefault(null, null, null, type);
            manager.startPreview(target, temp);

            return true;
        }

        if (!sub.equalsIgnoreCase("give")) return true;

        Player target = getTargetPlayer(sender, args, 3, "give");
        if (target == null) return true;

        if (args.length == 1) {
            messages.send(sender, Messages.Message.GIVE_SPECIFY_TYPE);
            return true;
        }

        VehicleType type = PluginUtils.getOrNull(VehicleType.class, args[1].toUpperCase(Locale.ROOT));
        if (type == null) {
            messages.send(sender, Messages.Message.GIVE_TYPE_NOT_FOUND);
            return true;
        }

        target.getInventory().addItem(plugin.createVehicleItem(type, null));
        return true;
    }

    private @Nullable Player getTargetPlayer(CommandSender sender, @NotNull String @NotNull [] args, int at, String action) {
        boolean other = args.length >= at;

        Player target;
        if (other) {
            target = Bukkit.getPlayerExact(args[at - 1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else target = null;

        String extra = other && !sender.equals(target) ? ".other" : "";

        String permission = "vehicleswasd.command." + action + extra;
        if (notAllowed(sender, permission)) return null;

        if (target != null) return target;

        plugin.getMessages().send(sender, Messages.Message.PLAYER_NOT_FOUND);
        return null;
    }

    private @NotNull @Unmodifiable List<String> getShopVehicles(@NotNull String typeName, String token) {
        VehicleType type = PluginUtils.getOrNull(VehicleType.class, typeName.toUpperCase(Locale.ROOT));
        if (type == null) return Collections.emptyList();

        List<ShopVehicle> vehicles = getShopVehicles(type);
        if (vehicles == null) return NONE;

        List<String> complete = Stream.concat(
                        NONE.stream(),
                        vehicles.stream().map(ShopVehicle::shopId))
                .toList();

        return StringUtil.copyPartialMatches(token, complete, new ArrayList<>());
    }

    private @Nullable List<ShopVehicle> getShopVehicles(VehicleType type) {
        // We need to reload this here instead of on VehiclesPlugin#onEnabled()
        // because the provider may not be enabled yet.
        plugin.reloadShopItemsIfNeeded();

        List<ShopVehicle> vehicles = plugin.getTypeVehicles().get(type);
        if (vehicles == null || vehicles.isEmpty()) return null;

        return vehicles;
    }

    private @Nullable VehicleData getPreviewData(VehicleType type, String @NotNull [] args) {
        List<ShopVehicle> vehicles = getShopVehicles(type);
        if (vehicles == null) return null;

        for (ShopVehicle vehicle : vehicles) {
            if (vehicle.shopId().equals(args[2])) {
                return vehicle.data();
            }
        }

        return null;
    }

    private boolean notAllowed(@NotNull CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        plugin.getMessages().send(
                sender,
                Messages.Message.NO_PERMISSION,
                line -> line.replace("%permission%", permission));
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

        if (args.length == 2 && args[0].equalsIgnoreCase("fuel")) {
            return null; // Return player list.
        }

        if (args.length == 2 && TYPE_LIST_USER.contains(args[0])) {
            return StringUtil.copyPartialMatches(args[1], VALID_TYPES, new ArrayList<>());
        }

        if (args.length == 3
                && TYPE_LIST_USER.contains(args[0])
                && VALID_TYPES.contains(args[1])) {
            if (args[0].equalsIgnoreCase("give")) {
                return null; // Return player list.
            }
            return getShopVehicles(args[1], args[2]);
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("preview")
                && VALID_TYPES.contains(args[1])) {
            return null; // Return player list.
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("gps")
                && sender instanceof Player player
                && plugin.getVehicleManager().getVehicleByEntity(player) != null
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