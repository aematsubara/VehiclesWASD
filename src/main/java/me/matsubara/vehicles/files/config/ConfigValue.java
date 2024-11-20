package me.matsubara.vehicles.files.config;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class ConfigValue {

    private final VehiclesPlugin plugin = JavaPlugin.getPlugin(VehiclesPlugin.class);
    private final String path;
    private Object value;

    public static final Set<ConfigValue> ALL_VALUES = new HashSet<>();

    public ConfigValue(@NotNull String path) {
        this.path = path;
        reloadValue();
        ALL_VALUES.add(this);
    }

    public void reloadValue() {
        value = plugin.getConfig().get(path);
    }

    public <T> T getValue(@NotNull Class<T> type) {
        return type.cast(value);
    }

    public <T> T getValue(@NotNull Class<T> type, T defaultValue) {
        return type.cast(value != null ? value : defaultValue);
    }

    public String asString() {
        return getValue(String.class);
    }

    public String asString(String defaultString) {
        return getValue(String.class, defaultString);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public boolean asBool() {
        return getValue(Boolean.class);
    }

    public int asInt() {
        return NumberConversions.toInt(value);
    }

    public double asDouble() {
        return NumberConversions.toDouble(value);
    }

    public float asFloat() {
        return NumberConversions.toFloat(value);
    }

    public @NotNull ItemStack asItem() {
        if (value instanceof ItemStack item) {
            return item.clone();
        }

        ItemStack item = asItemBuilder().build();

        value = item;

        return item.clone();
    }

    public ItemBuilder asItemBuilder() {
        return plugin.getItem(path);
    }
}