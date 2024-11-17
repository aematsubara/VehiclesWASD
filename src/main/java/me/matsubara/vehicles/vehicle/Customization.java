package me.matsubara.vehicles.vehicle;

import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import org.bukkit.Material;

@Getter
@Setter
public class Customization {

    private final Map<String, EquipmentSlot> stands = new LinkedHashMap<>();
    private final String customizationName;
    private final String customizationNameFromConfig;
    private final Material defaultType;
    private final Set<TypeTarget> validTypes;
    private final int priority;

    private int stack = 1;
    private String parent;
    private Material newType;

    public Customization(String customizationName, String customizationNameFromConfig, Material defaultType, Set<TypeTarget> validTypes, int priority) {
        this.customizationName = customizationName;
        this.customizationNameFromConfig = customizationNameFromConfig;
        this.defaultType = defaultType;
        this.validTypes = validTypes;
        this.priority = priority;
    }

    public int size() {
        return Math.min(stands.size(), defaultType.getMaxStackSize());
    }

    public boolean isTagged(Material material) {
        for (TypeTarget type : validTypes) {
            if (type.getType() == material) return true;
        }
        return false;
    }
}
