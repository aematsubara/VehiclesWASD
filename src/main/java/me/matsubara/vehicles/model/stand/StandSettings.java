package me.matsubara.vehicles.model.stand;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3f;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
@Setter
public final class StandSettings implements Cloneable {

    // Model data.
    private String partName;
    private Vector offset;
    private float extraYaw;
    private final List<String> tags = new ArrayList<>();

    // Entity settings.
    private boolean invisible;
    private boolean small;
    private boolean basePlate;
    private boolean arms;
    private boolean fire;
    private boolean marker;
    private boolean glow;
    private String customName;
    private boolean customNameVisible;

    // Entity poses.
    private Vector3f headPose;
    private Vector3f bodyPose;
    private Vector3f leftArmPose;
    private Vector3f rightArmPose;
    private Vector3f leftLegPose;
    private Vector3f rightLegPose;

    // Entity equipment.
    private final Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public StandSettings() {
        // Default settings.
        this.invisible = false;
        this.small = false;
        this.basePlate = true;
        this.arms = false;
        this.fire = false;
        this.marker = false;
        this.glow = false;
        this.partName = null;
        this.customName = null;
        this.customNameVisible = false;

        // Default poses.
        this.headPose = Vector3f.zero();
        this.bodyPose = Vector3f.zero();
        this.leftArmPose = Vector3f.zero();
        this.rightArmPose = Vector3f.zero();
        this.leftLegPose = Vector3f.zero();
        this.rightLegPose = Vector3f.zero();
    }

    @NotNull
    public StandSettings clone() {
        try {
            StandSettings copy = (StandSettings) super.clone();
            copy.setCustomName(null);
            return copy;
        } catch (CloneNotSupportedException exception) {
            throw new Error(exception);
        }
    }

    public enum Pose {
        HEAD(16, StandSettings::getHeadPose),
        BODY(17, StandSettings::getBodyPose),
        LEFT_ARM(18, StandSettings::getLeftArmPose),
        RIGHT_ARM(19, StandSettings::getRightArmPose),
        LEFT_LEG(20, StandSettings::getLeftLegPose),
        RIGHT_LEG(21, StandSettings::getRightLegPose);

        private final @Getter int index;
        private final Function<StandSettings, Vector3f> getter;

        Pose(int index, Function<StandSettings, Vector3f> getter) {
            this.index = index;
            this.getter = getter;
        }

        public Vector3f get(StandSettings settings) {
            return getter.apply(settings);
        }
    }
}