package me.matsubara.vehicles.vehicle;

import lombok.Getter;
import me.matsubara.vehicles.data.SoundWrapper;
import me.matsubara.vehicles.files.Config;

import java.util.function.Function;

@Getter
public enum FireballWeapon {
    PLANE(
            VehicleType.PLANE,
            0.1d,
            "SCOPE",
            Config.PLANE_FIRE_SECONDARY_ENABLED,
            Config.PLANE_FIRE_SECONDARY_SPEED_MULTIPLIER,
            Config.PLANE_FIRE_SECONDARY_INCENDIARY,
            Config.PLANE_FIRE_SECONDARY_RADIUS,
            Config.PLANE_FIRE_SECONDARY_COOLDOWN,
            Config.PLANE_FIRE_SECONDARY_COOLDOWN_MESSAGE,
            Config.PLANE_FIRE_SECONDARY_FIREBALL_ITEM,
            Vehicle::getPlaneFireSecondarySound),
    TANK(
            VehicleType.TANK,
            0.25d,
            "TOP_PART_8",
            Config.TANK_FIRE_ENABLED,
            Config.TANK_FIRE_SPEED_MULTIPLIER,
            Config.TANK_FIRE_INCENDIARY,
            Config.TANK_FIRE_RADIUS,
            Config.TANK_FIRE_COOLDOWN,
            Config.TANK_FIRE_COOLDOWN_MESSAGE,
            Config.TANK_FIRE_FIREBALL_ITEM,
            Vehicle::getTankFireSound);

    private final VehicleType type;
    private final double yOffset;
    private final String scope;
    private final Config enabled;
    private final Config speedMultiplier;
    private final Config incendiary;
    private final Config radius;
    private final Config cooldown;
    private final Config sendCooldownMessage;
    private final Config fireballItem;
    private final Function<Vehicle, SoundWrapper> soundGetter;

    FireballWeapon(VehicleType type,
                   double yOffset,
                   String scope,
                   Config enabled,
                   Config speedMultiplier,
                   Config incendiary,
                   Config radius,
                   Config cooldown,
                   Config sendCooldownMessage,
                   Config fireballItem,
                   Function<Vehicle, SoundWrapper> soundGetter) {
        this.type = type;
        this.yOffset = yOffset;
        this.scope = scope;
        this.enabled = enabled;
        this.speedMultiplier = speedMultiplier;
        this.incendiary = incendiary;
        this.radius = radius;
        this.cooldown = cooldown;
        this.sendCooldownMessage = sendCooldownMessage;
        this.fireballItem = fireballItem;
        this.soundGetter = soundGetter;
    }
}