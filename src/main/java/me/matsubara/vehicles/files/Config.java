package me.matsubara.vehicles.files;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public enum Config {
    RENDER_DISTANCE,
    OPPOSITE_FACE_SPAWN,
    ECONOMY_PROVIDER,
    CLOSE_CUSTOMIZATION_GUI_IF_SUCCESSFUL,
    FOLLOW_PLAYER_TELEPORT,
    PICK_UP_NEARBY_ENTITIES,
    CONFIRM_SHOP("shop.confirm-shop"),
    SHOP_PREVIEW_ENABLED("shop.preview.enabled"),
    SHOP_PREVIEW_SECONDS("shop.preview.seconds"),
    SHOP_PREVIEW_MESSAGE("shop.preview.message"),
    SHOP_PREVIEW_RAINBOW_MESSAGE("shop.preview.rainbow-message"),
    SHOP_ITEM_LORE("shop.vehicle-item-lore"),
    CUSTOMIZATION_SEARCH_TITLE("input-gui.customization-search.title"),
    CUSTOMIZATION_SEARCH_TEXT("input-gui.customization-search.text"),
    TRANSFER_SEARCH_TITLE("input-gui.transfer-search.title"),
    TRANSFER_SEARCH_TEXT("input-gui.transfer-search.text"),
    GPS_ENABLED("gps.enabled"),
    GPS_MAX_DISTANCE("gps.max-distance"),
    GPS_MIN_DISTANCE("gsp.min-distance"),
    ACTION_BAR_ENABLED("action-bar.enabled"),
    ACTION_BAR_SEPARATOR("action-bar.message.separator"),
    ACTION_BAR_FUEL("action-bar.message.fuel"),
    ACTION_BAR_SPEED("action-bar.message.speed"),
    ACTION_BAR_GPS("action-bar.message.gps"),
    ACTION_BAR_SYMBOL("action-bar.fuel-bar.symbol"),
    ACTION_BAR_COMPLETED("action-bar.fuel-bar.color.completed"),
    ACTION_BAR_EMPTY("action-bar.fuel-bar.color.empty"),
    TANK_FIRE_ENABLED("tank-fire.enabled"),
    TANK_FIRE_SPEED_MULTIPLIER("tank-fire.speed-multiplier"),
    TANK_FIRE_INCENDIARY("tank-fire.incendiary"),
    TANK_FIRE_RADIUS("tank-fire.radius"),
    TANK_FIRE_COOLDOWN("tank-fire.cooldown");

    private final String path;
    private final VehiclesPlugin plugin = JavaPlugin.getPlugin(VehiclesPlugin.class);

    Config() {
        this.path = name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    Config(String path) {
        this.path = path;
    }

    public boolean asBool() {
        return plugin.getConfig().getBoolean(path);
    }

    public boolean asBool(boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }

    public int asInt() {
        return plugin.getConfig().getInt(path);
    }

    public String asString() {
        return plugin.getConfig().getString(path);
    }

    public String asString(String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public @NotNull String asStringTranslated(String defaultValue) {
        return PluginUtils.translate(asString(defaultValue));
    }

    public double asDouble() {
        return plugin.getConfig().getDouble(path);
    }

    public double asDouble(double defaultValue) {
        return plugin.getConfig().getDouble(path, defaultValue);
    }

    public long asLong() {
        return plugin.getConfig().getLong(path);
    }

    public float asFloat() {
        return (float) asDouble();
    }

    public @NotNull List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }
}