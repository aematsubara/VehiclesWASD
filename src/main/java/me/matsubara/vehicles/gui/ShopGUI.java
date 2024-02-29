package me.matsubara.vehicles.gui;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.InventoryUpdate;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ShopGUI implements InventoryHolder {

    private final VehiclesPlugin plugin;
    private final Inventory inventory;
    private final Player player;

    private List<ItemStack> items;
    private VehicleType currentType;
    private int currentPage;
    private int pages;

    private static final int[] TOP_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int[] HOTBAR = {37, 38, 39, 40, 41, 42, 43};

    private final ItemStack previousType;
    private final ItemStack nextType;
    private final ItemStack previous;
    private final ItemStack next;
    private final ItemStack close;
    private final ItemStack background;
    private final ItemStack selected;

    public ShopGUI(@NotNull VehiclesPlugin plugin, @NotNull Player player, VehicleType currentType) {
        this(plugin, player, currentType, 0);
    }

    public ShopGUI(@NotNull VehiclesPlugin plugin, @NotNull Player player, VehicleType currentType, int currentPage) {
        this.plugin = plugin;
        this.currentType = currentType;
        this.inventory = Bukkit.createInventory(this, 54, getTitle());
        this.player = player;
        this.currentPage = currentPage;

        String path = "gui.shop.items.";
        previousType = plugin.getItem(path + "previous-type").build();
        nextType = plugin.getItem(path + "next-type").build();
        previous = plugin.getItem(path + "previous-page").build();
        next = plugin.getItem(path + "next-page").build();
        close = plugin.getItem(path + "close").build();
        background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName("&7").build();
        selected = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName("&7").build();

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        this.items = plugin.getTypeVehicles().get(currentType);

        pages = (int) (Math.ceil((double) items.size() / SLOTS.length));

        for (int i = 0; i < 53; i++) {
            if (ArrayUtils.contains(TOP_SLOTS, i)
                    || ArrayUtils.contains(SLOTS, i)
                    || ArrayUtils.contains(HOTBAR, i)) continue;
            inventory.setItem(i, background);
        }

        inventory.setItem(4, selected);
        inventory.setItem(22, selected);

        inventory.setItem(10, previousType);
        inventory.setItem(16, nextType);

        if (currentPage > 0) inventory.setItem(37, previous);
        if (currentPage < pages - 1) inventory.setItem(43, next);

        inventory.setItem(53, close);

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
            inventory.setItem(slotIndex.get(index), items.get(aux));
        }

        InventoryUpdate.updateInventory(player, getTitle());
    }

    private @NotNull String getTitle() {
        String title = plugin.getConfig().getString("gui.shop.title");
        if (title == null) return "";

        return PluginUtils.translate(title
                .replace("%type%", plugin.getVehicleTypeFormatted(currentType))
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max-page%", String.valueOf(pages == 0 ? 1 : pages)));
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