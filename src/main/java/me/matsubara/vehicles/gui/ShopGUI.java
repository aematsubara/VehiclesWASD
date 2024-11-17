package me.matsubara.vehicles.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.ShopVehicle;
import me.matsubara.vehicles.util.ComponentUtil;
import me.matsubara.vehicles.util.InventoryUpdate;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.vehicle.VehicleType;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class ShopGUI implements InventoryHolder {

    private final VehiclesPlugin plugin;
    private final Inventory inventory;
    private final Player player;

    private List<ShopVehicle> items;
    private VehicleType currentType;
    private int currentPage;
    private int pages;

    private static final int[] TOP_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int[] HOTBAR = {37, 38, 39, 40, 41, 42, 43};

    public ShopGUI(@NotNull VehiclesPlugin plugin, @NotNull Player player, VehicleType currentType) {
        this(plugin, player, currentType, 0);
    }

    public ShopGUI(@NotNull VehiclesPlugin plugin, @NotNull Player player, VehicleType currentType, int currentPage) {
        this.plugin = plugin;
        this.currentType = currentType;
        this.inventory = Bukkit.createInventory(this, 54, getTitle());
        this.player = player;
        this.currentPage = currentPage;

        player.openInventory(inventory);
        updateInventory();
    }

    public void updateInventory() {
        inventory.clear();

        String path = "gui.shop.items.";

        items = plugin.getTypeVehicles().get(currentType);

        // There are no models available yet.
        if (items == null) {
            ItemStack nothing = plugin.getItem(path + "nothing").build();
            items = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                items.add(new ShopVehicle(null, nothing, null));
            }
        }

        pages = (int) (Math.ceil((double) items.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 53; i++) {
            if (ArrayUtils.contains(TOP_SLOTS, i)
                    || ArrayUtils.contains(SLOTS, i)
                    || ArrayUtils.contains(HOTBAR, i)) continue;
            inventory.setItem(i, background);
        }

        ItemStack selected = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        inventory.setItem(4, selected);
        inventory.setItem(22, selected);

        inventory.setItem(10, plugin.getItem(path + "previous-type").build());
        inventory.setItem(16, plugin.getItem(path + "next-type").build());

        if (currentPage > 0) inventory.setItem(37, plugin.getItem(path + "previous-page").build());
        if (currentPage < pages - 1) inventory.setItem(43, plugin.getItem(path + "next-page").build());

        inventory.setItem(53, plugin.getItem(path + "close").build());

        int currentOrdinal = currentType.ordinal();
        VehicleType[] types = VehicleType.values();

        int startAt = (currentOrdinal - 2) % types.length;
        if (startAt < 0) startAt += types.length;

        for (int i = 0; i < 5; i++) {
            VehicleType type = types[startAt];
            inventory.setItem(TOP_SLOTS[i], plugin.getTypeCategoryItem().get(type));

            startAt++;
            if (startAt == types.length) startAt = 0;
        }

        if (items.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = currentPage * SLOTS.length;
        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < items.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            inventory.setItem(slotIndex.get(index), items.get(aux).item());
        }

        InventoryUpdate.updateInventory(player, getTitle());
    }

    private @NotNull Component getTitle() {
        String title = plugin.getConfig().getString("gui.shop.title");
        if (title == null) return Component.empty();

        return ComponentUtil.deserialize(title, null, "%type%", plugin.getVehicleTypeFormatted(currentType), "%page%", String.valueOf(currentPage + 1), "%max-page%", String.valueOf(pages == 0 ? 1 : pages));
    }

    public void previousPage(boolean isShiftClick) {
        currentPage = isShiftClick ? 0 : currentPage - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        currentPage = isShiftClick ? pages - 1 : currentPage + 1;
        updateInventory();
    }

    public void setCurrentType(VehicleType currentType) {
        plugin.getVehicleManager().getSelectedShopCategory().put(player.getUniqueId(), currentType);
        this.currentType = currentType;
        updateInventory();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}