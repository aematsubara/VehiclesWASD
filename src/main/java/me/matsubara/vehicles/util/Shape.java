package me.matsubara.vehicles.util;

import com.google.common.base.Strings;
import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

public final class Shape {

    private final VehiclesPlugin plugin;
    private final boolean shaped;
    private final List<String> ingredients;
    private final List<String> shape;
    private final @Getter ItemStack result;
    private final @Getter NamespacedKey key;

    public Shape(VehiclesPlugin plugin, String name, boolean shaped, @NotNull List<String> ingredients, List<String> shape, ItemStack result) {
        this.plugin = plugin;
        this.shaped = shaped;
        this.ingredients = ingredients;
        this.shape = shape;
        this.result = result;
        this.key = new NamespacedKey(plugin, name);
        if (!ingredients.isEmpty() && (!shaped || !shape.isEmpty())) {
            register(result);
        }
    }

    public void register(ItemStack item) {
        Recipe recipe = shaped ? new ShapedRecipe(key, item) : new ShapelessRecipe(key, item);

        // Set shaped recipe.
        if (shaped) ((ShapedRecipe) recipe).shape(shape.toArray(new String[0]));

        for (String ingredient : ingredients) {
            if (Strings.isNullOrEmpty(ingredient)) continue;
            String[] split = PluginUtils.splitData(ingredient);

            Material type = Material.valueOf(split[0]);
            char key = split.length > 1 ? split[1].charAt(0) : ' ';

            if (shaped) {
                // Empty space is used for AIR.
                if (key == ' ') continue;
                ((ShapedRecipe) recipe).setIngredient(key, type);
            } else {
                ((ShapelessRecipe) recipe).addIngredient(type);
            }
        }

        Logger logger = plugin.getLogger();
        try {
            if (Bukkit.addRecipe(recipe)) {
                logger.info("The recipe for {" + key + "} was created!");
                return;
            }
        } catch (IllegalStateException ignored) {

        }
        logger.warning("The recipe for {" + key + "} couldn't be created!");
    }
}