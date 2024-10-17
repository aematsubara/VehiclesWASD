package me.matsubara.vehicles.files;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

@Getter
public class Messages {

    private final VehiclesPlugin plugin;
    private @Setter FileConfiguration configuration;

    public Messages(@NotNull VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveResource("messages.yml");
    }

    public void send(CommandSender sender, Message message) {
        send(sender, message, null);
    }

    public void send(CommandSender sender, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        for (String line : getMessages(message.getPath())) {
            if (!line.isEmpty()) sender.sendMessage(operator != null ? operator.apply(line) : line);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getMessages(String path) {
        if (!configuration.contains(path, true)) return Collections.emptyList();

        List<String> messages;

        Object object = configuration.get(path);
        if (object instanceof String string) {
            messages = Lists.newArrayList(string);
        } else if (object instanceof List<?> list) {
            try {
                messages = Lists.newArrayList((List<String>) list);
            } catch (ClassCastException exception) {
                return Collections.emptyList();
            }
        } else return Collections.emptyList();

        return PluginUtils.translate(messages);
    }

    @Getter
    public enum Message {
        AN_ERROR_OCURRED,
        NOT_FROM_CONSOLE("commands.not-from-console"),
        RELOADING("commands.reloading"),
        RELOAD("commands.reload"),
        NO_PERMISSION("commands.no-permission"),
        INVALID_COMMAND("commands.invalid-command"),
        PLAYER_NOT_FOUND("commands.player-not-found"),
        SHOP_ECONOMY_DISABLED("commands.shop.economy-disabled"),
        GPS_DISABLED("commands.gps.disabled"),
        GPS_ESSENTIALS_NOT_FOUND("commands.gps.essentials-not-found"),
        GPS_NOT_DRIVING("commands.gps.not-driving"),
        GPS_NOT_GENERIC("commands.gps.not-generic"),
        GPS_SPECIFY_HOME("commands.gps.specify-home"),
        GPS_NOT_FOUND("commands.gps.home-not-found"),
        GPS_DIFFERENT_WORLD("commands.gps.different-world"),
        GPS_VEHICLES_DENIED("commands.gps.vehicles-denied"),
        GPS_TOO_FAR("commands.gps.too-far"),
        GPS_TOO_CLOSE("commands.gps.too-close"),
        GPS_PREVIOUS_CANCELLED("commands.gps.previous-cancelled"),
        GPS_STARTING("commands.gps.starting"),
        GPS_PATH_NOT_FOUND("commands.gps.path-not-found"),
        GPS_WENT_CRAZY("commands.gps.went-crazy"),
        GPS_STOPPED("commands.gps.stopped"),
        GPS_ARRIVED("commands.gps.arrived"),
        GPS_FULL_OF_WATER("commands.gps.full-of-water"),
        GIVE_SPECIFY_TYPE("commands.give.specify-type"),
        GIVE_TYPE_NOT_FOUND("commands.give.type-not-found"),
        VEHICLES_EMPTY("commands.vehicles.empty"),
        PREVIEW_SPECIFY_TYPE("commands.preview.specify-type"),
        PREVIEW_TYPE_NOT_FOUND("commands.preview.type-not-found"),
        PREVIEW_SHOP_NOT_FOUND("commands.preview.shop-not-found"),
        PICK_SAVED_IN_INVENTORY("vehicle.pick.saved-in-inventory"),
        PICK_NOT_ENOUGH_SPACE("vehicle.pick.not-enough-space"),
        PICK_NOT_FOUND("vehicle.pick.not-found"),
        PLACE_DISABLED_WORLD("vehicle.place.disabled-world"),
        PLACE_NOT_ON_LAVA("vehicle.place.not-on-lava"),
        PLACE_NOT_ON_WATER("vehicle.place.not-on-water"),
        PLACE_INVALID_BLOCK("vehicle.place.invalid-block"),
        PLACE_BOAT_ON_WATER("vehicle.place.boat-on-water"),
        PLACE_BOAT_ON_TOP_SURFACE("vehicle.place.boat-on-top-surface"),
        PLACE_OCCUPIED("vehicle.place.occupied"),
        PLACE_DUPLICATED("vehicle.place.duplicated"),
        PLACE_TOP_BLOCKED_OR_INVALID("vehicle.place.top-blocked-or-invalid"),
        NOT_YOUR_VEHICLE("vehicle.interact.not-your-vehicle"),
        VEHICLE_IN_WATER("vehicle.interact.vehicle-in-water"),
        VEHICLE_IN_LAVA("vehicle.interact.vehicle-in-lava"),
        VEHICLE_LOCKED("vehicle.interact.vehicle-locked"),
        VEHICLE_FULL("vehicle.interact.vehicle-full"),
        VEHICLE_ALLOWED_AS_PASSENGER("vehicle.interact.allowed-as-passenger"),
        VEHICLE_OCCUPIED_BY_UNKNOWN("vehicle.interact.occupied-by-unknown"),
        OCCUPANT_KICKED("vehicle.interact.occupant-kicked"),
        WEAPON_COOLDOWN("vehicle.interact.weapon-cooldown"),
        TRANSFER_SPECIFY_PLAYER("vehicle.transfer-ownership.specify-player"),
        TRANSFER_SAME_OWNER("vehicle.transfer-ownership.same-owner"),
        TRANSFER_NEW_OWNER("vehicle.transfer-ownership.new-owner"),
        TRANSFER_OFFLINE_PLAYER("vehicle.transfer-ownership.offline-player"),
        SHOP_NOT_ENOUGH_MONEY("shop.not-enough-money"),
        SHOP_SUCCESSFUL_PURCHASE("shop.successful-purchase"),
        CUSTOMIZATION_INVALID_CURSOR("customization.invalid-cursor"),
        CUSTOMIZATION_ALREADY_IN_USE("customization.already-in-use"),
        CUSTOMIZATION_DIFFERENT_AMOUNT("customization.different-amount");

        private final String path;

        Message() {
            this.path = name().toLowerCase(Locale.ROOT).replace("_", "-");
        }

        Message(String path) {
            this.path = path;
        }
    }
}