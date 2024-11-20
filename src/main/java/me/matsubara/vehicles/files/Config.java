package me.matsubara.vehicles.files;

import lombok.experimental.UtilityClass;
import me.matsubara.vehicles.files.config.ConfigValue;

@UtilityClass
public class Config {
    // General.
    public final ConfigValue RENDER_DISTANCE = new ConfigValue("render-distance");
    public final ConfigValue OPPOSITE_FACE_SPAWN = new ConfigValue("opposite-face-spawn");
    public final ConfigValue ECONOMY_PROVIDER = new ConfigValue("economy-provider");
    public final ConfigValue CLOSE_CUSTOMIZATION_GUI_IF_SUCCESSFUL = new ConfigValue("close-customization-gui-if-successful");
    public final ConfigValue PICK_UP_ON_DISMOUNT = new ConfigValue("pick-up-on-dismount");
    public final ConfigValue PICK_UP_ON_REMOVE = new ConfigValue("pick-up-on-remove");
    public final ConfigValue PICK_UP_NEARBY_ENTITIES = new ConfigValue("pick-up-nearby-entities");
    public final ConfigValue ENTITIES_FILTER_TYPE = new ConfigValue("entities-filter.type");
    public final ConfigValue ENTITIES_FILTER_WORLDS = new ConfigValue("entities-filter.entities");
    public final ConfigValue WORLDS_FILTER_TYPE = new ConfigValue("worlds-filter.type");
    public final ConfigValue WORLDS_FILTER_WORLDS = new ConfigValue("worlds-filter.worlds");
    public final ConfigValue STOP_VEHICLE_ON_DISMOUNT = new ConfigValue("stop-vehicle-on-dismount");
    public final ConfigValue SAFE_DISMOUNT_TELEPORT = new ConfigValue("safe-dismount-teleport");

    // Tractor.
    public final ConfigValue TRACTOR_TICK_DELAY = new ConfigValue("tractor-features.tick-delay");
    public final ConfigValue TRACTOR_WORK_ON_ROTATION = new ConfigValue("tractor-features.work-on-rotation");

    // Keybinds.
    public final ConfigValue KEYBINDS_OPEN_MENU = new ConfigValue("vehicle-keybinds.open-menu");
    public final ConfigValue KEYBINDS_SHOOT_WEAPON = new ConfigValue("vehicle-keybinds.shoot-weapon");
    public final ConfigValue KEYBINDS_ACTION_BAR_MESSAGE_ENABLED = new ConfigValue("vehicle-keybinds.action-bar-message.enabled");
    public final ConfigValue KEYBINDS_ACTION_BAR_MESSAGE_SECONDS = new ConfigValue("vehicle-keybinds.action-bar-message.seconds");
    public final ConfigValue KEYBINDS_ACTION_BAR_MESSAGE_SEPARATOR = new ConfigValue("vehicle-keybinds.action-bar-message.separator");

    // Shop.
    public final ConfigValue CONFIRM_SHOP = new ConfigValue("shop.confirm-shop");
    public final ConfigValue SHOP_PREVIEW_ENABLED = new ConfigValue("shop.preview.enabled");
    public final ConfigValue SHOP_PREVIEW_SECONDS = new ConfigValue("shop.preview.seconds");
    public final ConfigValue SHOP_PREVIEW_MESSAGE = new ConfigValue("shop.preview.message");
    public final ConfigValue SHOP_PREVIEW_RAINBOW_MESSAGE = new ConfigValue("shop.preview.rainbow-message");
    public final ConfigValue SHOP_ITEM_LORE = new ConfigValue("shop.vehicle-item-lore");

    // Customization.
    public final ConfigValue CUSTOMIZATION_SEARCH_TITLE = new ConfigValue("input-gui.customization-search.title");
    public final ConfigValue CUSTOMIZATION_SEARCH_TEXT = new ConfigValue("input-gui.customization-search.text");

    // Transfership.
    public final ConfigValue TRANSFER_SEARCH_TITLE = new ConfigValue("input-gui.transfer-search.title");
    public final ConfigValue TRANSFER_SEARCH_TEXT = new ConfigValue("input-gui.transfer-search.text");

    // Tractor.
    public final ConfigValue TRACTOR_STACK_WHEAT = new ConfigValue("vehicles.tractor.settings.stack-wheat");
    public final ConfigValue TRACTOR_BLOCK_TO_BONE_MEAL = new ConfigValue("vehicles.tractor.settings.block-to-bone-meal");

    // Gps.
    public final ConfigValue GPS_ENABLED = new ConfigValue("gps.enabled");
    public final ConfigValue GPS_MAX_DISTANCE = new ConfigValue("gps.max-distance");
    public final ConfigValue GPS_MIN_DISTANCE = new ConfigValue("gps.min-distance");

    // Action bar.
    public final ConfigValue ACTION_BAR_ENABLED = new ConfigValue("action-bar.enabled");
    public final ConfigValue ACTION_BAR_SEPARATOR = new ConfigValue("action-bar.message.separator");
    public final ConfigValue ACTION_BAR_FUEL = new ConfigValue("action-bar.message.fuel");
    public final ConfigValue ACTION_BAR_SPEED = new ConfigValue("action-bar.message.speed");
    public final ConfigValue ACTION_BAR_GPS = new ConfigValue("action-bar.message.gps");
    public final ConfigValue ACTION_BAR_PLANE_TARGET = new ConfigValue("action-bar.message.plane-target");
    public final ConfigValue ACTION_BAR_SYMBOL = new ConfigValue("action-bar.fuel-bar.symbol");
    public final ConfigValue ACTION_BAR_WARNING_FUEL_BELOW = new ConfigValue("action-bar.fuel-bar.warning.fuel-below");
    public final ConfigValue ACTION_BAR_WARNING_DELAY = new ConfigValue("action-bar.fuel-bar.warning.delay");
    public final ConfigValue ACTION_BAR_COMPLETED = new ConfigValue("action-bar.fuel-bar.color.completed");
    public final ConfigValue ACTION_BAR_EMPTY = new ConfigValue("action-bar.fuel-bar.color.empty");
    public final ConfigValue ACTION_BAR_WARNING = new ConfigValue("action-bar.fuel-bar.color.warning");

    // Plane general.
    public final ConfigValue PLANE_FIRE_CREEPER_EXPLODE = new ConfigValue("plane-fire.creeper-explode");

    // Plane primary weapon.
    public final ConfigValue PLANE_FIRE_PRIMARY_ENABLED = new ConfigValue("plane-fire.primary.enabled");
    public final ConfigValue PLANE_FIRE_PRIMARY_SPEED_MULTIPLIER = new ConfigValue("plane-fire.primary.speed-multiplier");
    public final ConfigValue PLANE_FIRE_PRIMARY_CRITICAL = new ConfigValue("plane-fire.primary.critical");
    public final ConfigValue PLANE_FIRE_PRIMARY_POWER_LEVEL = new ConfigValue("plane-fire.primary.power-level");
    public final ConfigValue PLANE_FIRE_PRIMARY_PIERCE_LEVEL = new ConfigValue("plane-fire.primary.pierce-level");
    public final ConfigValue PLANE_FIRE_PRIMARY_FIRE_TICKS = new ConfigValue("plane-fire.primary.fire-ticks");
    public final ConfigValue PLANE_FIRE_PRIMARY_COOLDOWN = new ConfigValue("plane-fire.primary.cooldown");
    public final ConfigValue PLANE_FIRE_PRIMARY_COOLDOWN_MESSAGE = new ConfigValue("plane-fire.primary.cooldown-message");
    public final ConfigValue PLANE_FIRE_PRIMARY_SOUND = new ConfigValue("plane-fire.primary.sound");

    // Plane secondary weapon.
    public final ConfigValue PLANE_FIRE_SECONDARY_ENABLED = new ConfigValue("plane-fire.secondary.enabled");
    public final ConfigValue PLANE_FIRE_SECONDARY_SPEED_MULTIPLIER = new ConfigValue("plane-fire.secondary.speed-multiplier");
    public final ConfigValue PLANE_FIRE_SECONDARY_INCENDIARY = new ConfigValue("plane-fire.secondary.incendiary");
    public final ConfigValue PLANE_FIRE_SECONDARY_RADIUS = new ConfigValue("plane-fire.secondary.radius");
    public final ConfigValue PLANE_FIRE_SECONDARY_COOLDOWN = new ConfigValue("plane-fire.secondary.cooldown");
    public final ConfigValue PLANE_FIRE_SECONDARY_COOLDOWN_MESSAGE = new ConfigValue("plane-fire.secondary.cooldown-message");
    public final ConfigValue PLANE_FIRE_SECONDARY_SOUND = new ConfigValue("plane-fire.secondary.sound");
    public final ConfigValue PLANE_FIRE_SECONDARY_FIREBALL_ITEM = new ConfigValue("plane-fire.secondary.fireball-item");

    // Play secondary weapon special ability.
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_ENABLED = new ConfigValue("plane-fire.secondary.follow-target.enabled");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_RANGE = new ConfigValue("plane-fire.secondary.follow-target.range");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_WATER = new ConfigValue("plane-fire.secondary.follow-target.ignore.water");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_TAMED = new ConfigValue("plane-fire.secondary.follow-target.ignore.tamed");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_IGNORE_INVISIBLE = new ConfigValue("plane-fire.secondary.follow-target.ignore.invisible");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_ENABLED = new ConfigValue("plane-fire.secondary.follow-target.glowing.enabled");
    public final ConfigValue PLANE_FIRE_SECONDARY_FOLLOW_TARGET_GLOWING_COLOR = new ConfigValue("plane-fire.secondary.follow-target.glowing.color");

    // Tank primary (and only) weapon.
    public final ConfigValue TANK_FIRE_ENABLED = new ConfigValue("tank-fire.enabled");
    public final ConfigValue TANK_FIRE_SPEED_MULTIPLIER = new ConfigValue("tank-fire.speed-multiplier");
    public final ConfigValue TANK_FIRE_INCENDIARY = new ConfigValue("tank-fire.incendiary");
    public final ConfigValue TANK_FIRE_RADIUS = new ConfigValue("tank-fire.radius");
    public final ConfigValue TANK_FIRE_COOLDOWN = new ConfigValue("tank-fire.cooldown");
    public final ConfigValue TANK_FIRE_COOLDOWN_MESSAGE = new ConfigValue("tank-fire.cooldown-message");
    public final ConfigValue TANK_FIRE_SOUND = new ConfigValue("tank-fire.sound");
    public final ConfigValue TANK_FIRE_FIREBALL_ITEM = new ConfigValue("tank-fire.fireball-item");

    // Other.
    public final ConfigValue PREMIUM_FUEL = new ConfigValue("premium-fuel");
    public final ConfigValue CUSTOMIZATIONS_REQUIRE_PERMISSION = new ConfigValue("customizations.require-permission");
}