package me.matsubara.vehicles.gui;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.InventoryUpdate;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Customization;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class CustomizationGUI implements InventoryHolder {

    private final VehiclesPlugin plugin;
    private final Vehicle vehicle;
    private final Inventory inventory;
    private final Player player;
    private final List<Customization> customizations;
    private final String keyword;

    private int current;
    private int pages;

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    private final ItemStack previous;
    private final ItemStack next;
    private final ItemStack search;
    private final ItemStack clearSearch;
    private final ItemStack close;
    private final ItemStack background;

    public CustomizationGUI(@NotNull VehiclesPlugin plugin, @NotNull Vehicle vehicle, Player player, @Nullable String keyword) {
        this.plugin = plugin;
        this.vehicle = vehicle;
        this.inventory = Bukkit.createInventory(this, 36, getTitle());

        this.player = player;

        String path = "gui.customizations.items.";
        previous = plugin.getItem(path + "previous-page").build();
        next = plugin.getItem(path + "next-page").build();
        search = plugin.getItem(path + "search").build();
        clearSearch = keyword != null ? plugin.getItem(path + "search").replace("%keyword%", keyword).build() : null;
        close = plugin.getItem(path + "close").build();
        background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName("&7").build();

        this.customizations = new ArrayList<>(vehicle.getCustomizations());
        this.keyword = keyword;

        if (keyword != null && !keyword.isEmpty()) {
            this.customizations.removeIf(customization -> !customization.getCustomizationName().toLowerCase().contains(keyword.toLowerCase()));
        }

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        pages = (int) (Math.ceil((double) customizations.size() / SLOTS.length));

        for (int i = 0; i < 36; i++) {
            if (ArrayUtils.contains(SLOTS, i) || ArrayUtils.contains(HOTBAR, i)) continue;
            inventory.setItem(i, background);
        }

        if (current > 0) inventory.setItem(19, previous);
        inventory.setItem(22, keyword != null ? clearSearch : search);
        inventory.setItem(35, close);
        if (current < pages - 1) inventory.setItem(25, next);

        if (customizations.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = current * SLOTS.length;
        boolean isLastPage = current == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < customizations.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Customization customization = customizations.get(aux);

            String name = customization.getCustomizationName();
            String nameFromConfig = customization.getCustomizationNameFromConfig();

            List<String> validMaterialNames = plugin.typesToString(customization.getValidTypes(), vehicle.getType(), false);

            Material type = customization.getNewType() != null ? customization.getNewType() : customization.getDefaultType();
            inventory.setItem(slotIndex.get(index), plugin.getItem("gui.customizations.items.customization")
                    .setType(type)
                    .setAmount(customization.size())
                    .setData(plugin.getCustomizationKey(), PersistentDataType.STRING, name)
                    .replace("%customization%", nameFromConfig != null ? nameFromConfig : name)
                    .applyMultiLineLore(validMaterialNames, "%material%", plugin.getConfig().getString("translations.no-types"))
                    .build());
        }

        InventoryUpdate.updateInventory(player, getTitle());
    }

    private @NotNull String getTitle() {
        String title = plugin.getConfig().getString("gui.customizations.title");
        if (title == null) return "";

        return PluginUtils.translate(title
                .replace("%page%", String.valueOf(current + 1))
                .replace("%max-page%", String.valueOf(pages == 0 ? 1 : pages)));
    }

    public void previousPage(boolean isShiftClick) {
        // If shift clicking, go to the first page; otherwise, go to the previous page.
        current = isShiftClick ? 0 : current - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        // If shift clicking, go to the last page; otherwise, go to the next page.
        current = isShiftClick ? pages - 1 : current + 1;
        updateInventory();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}