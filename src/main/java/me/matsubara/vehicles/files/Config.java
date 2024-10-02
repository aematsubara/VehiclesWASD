package me.matsubara.vehicles.files;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@Getter
public enum Config {
    RENDER_DISTANCE,
    OPPOSITE_FACE_SPAWN,
    ECONOMY_PROVIDER,
    CLOSE_CUSTOMIZATION_GUI_IF_SUCCESSFUL,
    FOLLOW_PLAYER_TELEPORT,
    PICK_UP_NEARBY_ENTITIES,
    ENTITIES_FILTER_TYPE("entities-filter.type"),
    ENTITIES_FILTER_WORLDS("entities-filter.entities"),
    WORLDS_FILTER_TYPE("worlds-filter.type"),
    WORLDS_FILTER_WORLDS("worlds-filter.worlds"),
    STOP_VEHICLE_ON_DISMOUNT,
    SAFE_DISMOUNT_TELEPORT,

    // Keybinds.
    KEYBINDS_OPEN_MENU("vehicle-keybinds.open-menu"),
    KEYBINDS_SHOOT_WEAPON("vehicle-keybinds.shoot-weapon"),
    KEYBINDS_ACTION_BAR_MESSAGE_ENABLED("vehicle-keybinds.action-bar-message.enabled"),
    KEYBINDS_ACTION_BAR_MESSAGE_SECONDS("vehicle-keybinds.action-bar-message.seconds"),
    KEYBINDS_ACTION_BAR_MESSAGE_SEPARATOR("vehicle-keybinds.action-bar-message.separator"),

    // Shop.
    CONFIRM_SHOP("shop.confirm-shop"),
    SHOP_PREVIEW_ENABLED("shop.preview.enabled"),
    SHOP_PREVIEW_SECONDS("shop.preview.seconds"),
    SHOP_PREVIEW_MESSAGE("shop.preview.message"),
    SHOP_PREVIEW_RAINBOW_MESSAGE("shop.preview.rainbow-message"),
    SHOP_ITEM_LORE("shop.vehicle-item-lore"),

    // Customization.
    CUSTOMIZATION_SEARCH_TITLE("input-gui.customization-search.title"),
    CUSTOMIZATION_SEARCH_TEXT("input-gui.customization-search.text"),

    // Transfership.
    TRANSFER_SEARCH_TITLE("input-gui.transfer-search.title"),
    TRANSFER_SEARCH_TEXT("input-gui.transfer-search.text"),

    // Gps.
    GPS_ENABLED("gps.enabled"),
    GPS_MAX_DISTANCE("gps.max-distance"),
    GPS_MIN_DISTANCE("gps.min-distance"),

    // Action bar.
    ACTION_BAR_ENABLED("action-bar.enabled"),
    ACTION_BAR_SEPARATOR("action-bar.message.separator"),
    ACTION_BAR_FUEL("action-bar.message.fuel"),
    ACTION_BAR_SPEED("action-bar.message.speed"),
    ACTION_BAR_GPS("action-bar.message.gps"),
    ACTION_BAR_PLANE_TARGET("action-bar.message.plane-target"),
    ACTION_BAR_SYMBOL("action-bar.fuel-bar.symbol"),
    ACTION_BAR_WARNING_FUEL_BELOW("action-bar.fuel-bar.warning.fuel-below"),
    ACTION_BAR_WARNING_DELAY("action-bar.fuel-bar.warning.delay"),
    ACTION_BAR_COMPLETED("action-bar.fuel-bar.color.completed"),
    ACTION_BAR_EMPTY("action-bar.fuel-bar.color.empty"),
    ACTION_BAR_WARNING("action-bar.fuel-bar.color.warning"),

    // Plane general.
    PLANE_FIRE_CREEPER_EXPLODE("plane-fire.creeper-explode"),

    // Plane primary weapon.
    PLANE_FIRE_PRIMARY_ENABLED("plane-fire.primary.enabled"),
    PLANE_FIRE_PRIMARY_SPEED_MULTIPLIER("plane-fire.primary.speed-multiplier"),
    PLANE_FIRE_PRIMARY_CRITICAL("plane-fire.primary.critical"),
    PLANE_FIRE_PRIMARY_POWER_LEVEL("plane-fire.primary.power-level"),
    PLANE_FIRE_PRIMARY_PIERCE_LEVEL("plane-fire.primary.pierce-level"),
    PLANE_FIRE_PRIMARY_FIRE_TICKS("plane-fire.primary.fire-ticks"),
    PLANE_FIRE_PRIMARY_COOLDOWN("plane-fire.primary.cooldown"),
    PLANE_FIRE_PRIMARY_COOLDOWN_MESSAGE("plane-fire.primary.cooldown-message"),
    PLANE_FIRE_PRIMARY_SOUND("plane-fire.primary.sound"),

    // Plane secondary weapon.
    PLANE_FIRE_SECONDARY_ENABLED("plane-fire.secondary.enabled"),
    PLANE_FIRE_SECONDARY_SPEED_MULTIPLIER("plane-fire.secondary.speed-multiplier"),
    PLANE_FIRE_SECONDARY_INCENDIARY("plane-fire.secondary.incendiary"),
    PLANE_FIRE_SECONDARY_RADIUS("plane-fire.secondary.radius"),
    PLANE_FIRE_SECONDARY_COOLDOWN("plane-fire.secondary.cooldown"),
    PLANE_FIRE_SECONDARY_COOLDOWN_MESSAGE("plane-fire.secondary.cooldown-message"),
    PLANE_FIRE_SECONDARY_SOUND("plane-fire.secondary.sound"),
    PLANE_FIRE_SECONDARY_FIREBALL_ITEM("plane-fire.secondary.fireball-item"),

    // Play secondary weapon special ability.
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_ENABLED("plane-fire.secondary.follow-target.enabled"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_RANGE("plane-fire.secondary.follow-target.range"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_WATER("plane-fire.secondary.follow-target.ignore.water"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_TAMED("plane-fire.secondary.follow-target.ignore.tamed"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_INVISIBLE("plane-fire.secondary.follow-target.ignore.invisible"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_ENABLED("plane-fire.secondary.follow-target.glowing.enabled"),
    PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_COLOR("plane-fire.secondary.follow-target.glowing.color"),

    // Tank primary (and only) weapon.
    TANK_FIRE_ENABLED("tank-fire.enabled"),
    TANK_FIRE_SPEED_MULTIPLIER("tank-fire.speed-multiplier"),
    TANK_FIRE_INCENDIARY("tank-fire.incendiary"),
    TANK_FIRE_RADIUS("tank-fire.radius"),
    TANK_FIRE_COOLDOWN("tank-fire.cooldown"),
    TANK_FIRE_COOLDOWN_MESSAGE("tank-fire.cooldown-message"),
    TANK_FIRE_SOUND("tank-fire.sound"),
    TANK_FIRE_FIREBALL_ITEM("tank-fire.fireball-item"),

    BREAK_BLOCKS_CROPS("break-blocks.crops"),
    PREMIUM_FUEL("premium-fuel");

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

    public double asDouble() {
        return plugin.getConfig().getDouble(path);
    }

    public @NotNull List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }

    public ItemStack asItem() {
        return asItemBuilder().build();
    }

    public ItemBuilder asItemBuilder() {
        return plugin.getItem(path);
    }
}