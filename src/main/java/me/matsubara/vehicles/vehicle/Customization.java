package me.matsubara.vehicles.vehicle;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.model.stand.data.ItemSlot;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
@Setter
public class Customization {

    private final Map<String, ItemSlot> stands = new LinkedHashMap<>();
    private final Map<String, CustomizationGroup.Type> groupHolder = new HashMap<>();
    private final String customizationName;
    private final String customizationNameFromConfig;
    private final Material defaultType;
    private final Set<TypeTarget> validTypes;
    private final int priority;

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

    public List<IStand> getStandList(@NotNull Model model) {
        return stands.keySet().stream()
                .map(model::getStandByName)
                .filter(Objects::nonNull)
                .toList();
    }
}