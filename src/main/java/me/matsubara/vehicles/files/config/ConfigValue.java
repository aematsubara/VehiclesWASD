package me.matsubara.vehicles.files.config;

import java.util.Locale;
import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.ComponentUtil;
import me.matsubara.vehicles.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

@Getter
public class ConfigValue {
    private final VehiclesPlugin plugin = JavaPlugin.getPlugin(VehiclesPlugin.class);

    private final String path;
    private Object value;

    public ConfigValue(String path) {
        this.path = path;

        this.value = plugin.getConfig().get(path.toLowerCase(Locale.ROOT).replace("_", "-"));
    }

    public <T> T getValue(Class<T> type) {
        return type.cast(value);
    }

    public <T> T getValue(Class<T> type, T defaultValue) {
        return type.cast(value != null ? value : defaultValue);
    }

    public @NotNull Component asComponentTranslated() {
        if (value instanceof Component) {
            return (Component) value;
        }

        Component component = ComponentUtil.deserialize(getValue(String.class));
        value = component;

        return component;
    }

    public @NotNull ItemStack asItem() {
        if (value instanceof ItemStack) {
            return ((ItemStack) value).clone();
        }
        final ItemStack itemStack = getValue(ItemBuilder.class).build();

        value = itemStack;

        return itemStack.clone();
    }

}
