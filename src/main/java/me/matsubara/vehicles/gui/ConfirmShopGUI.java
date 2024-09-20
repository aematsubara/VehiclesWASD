package me.matsubara.vehicles.gui;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.VehicleData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@Getter
public class ConfirmShopGUI implements InventoryHolder {

    private final Inventory inventory;
    private final VehicleData data;
    private final String shopDisplayName;
    private final double money;
    private final int previousPage;

    @Setter
    private boolean openBack = true;

    public ConfirmShopGUI(VehiclesPlugin plugin, Player player, @NotNull ItemStack shopItem, VehicleData data, double money, int previousPage) {
        ItemMeta meta = shopItem.getItemMeta();

        if (meta != null && meta.hasDisplayName()) this.shopDisplayName = meta.getDisplayName();
        else this.shopDisplayName = null;

        String title = plugin.getConfig().getString("gui.shop-confirm.title", shopDisplayName);
        this.inventory = Bukkit.createInventory(this, 9, title != null && shopDisplayName != null ?
                PluginUtils.translate(title.replace("%name%", shopDisplayName)) : "");

        this.data = data;
        this.money = money;
        this.previousPage = previousPage;

        for (int i = 0; i < 4; i++) {
            inventory.setItem(i, plugin.getItem("gui.shop-confirm.items.confirm")
                    .replace("%money%", plugin.getEconomyExtension().format(money))
                    .build());
        }

        inventory.setItem(4, new ItemBuilder(shopItem)
                .clearLore()
                .build());

        for (int i = 5; i < 9; i++) {
            inventory.setItem(i, plugin.getItem("gui.shop-confirm.items.cancel").build());
        }

        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}