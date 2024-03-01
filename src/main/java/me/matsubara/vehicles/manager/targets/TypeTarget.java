package me.matsubara.vehicles.manager.targets;

import lombok.Getter;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

@Getter
public class TypeTarget {

    private final int amount;
    private final Material type;
    private final @Nullable String fromTag;

    public TypeTarget(int amount, Material type, @Nullable String fromTag) {
        this.amount = amount;
        this.type = type;
        this.fromTag = fromTag;
    }

    public boolean is(Material type) {
        return this.type == type;
    }

    public static class TargetWithCondition extends TypeTarget {

        private final VehicleType targetType;

        public TargetWithCondition(int amount, Material type, String fromTag, VehicleType targetType) {
            super(amount, type, fromTag);
            this.targetType = targetType;
        }

        public boolean test(VehicleType targetType) {
            return this.targetType == targetType;
        }
    }
}