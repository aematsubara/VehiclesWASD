package me.matsubara.vehicles.gui;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VehicleGUI implements InventoryHolder {

    private final VehiclesPlugin plugin;
    private final @Getter Vehicle vehicle;
    private final Inventory inventory;

    public VehicleGUI(@NotNull VehiclesPlugin plugin, Player player, @NotNull Vehicle vehicle) {
        this.plugin = plugin;
        this.vehicle = vehicle;

        boolean isHelicopter = vehicle.is(VehicleType.HELICOPTER);

        this.inventory = Bukkit.createInventory(this, isHelicopter ? 45 : 27, getTitle());

        ItemStack storageItem = vehicle.getStorageRows() == 0 ?
                getItem(player, "no-storage") :
                getItem(player, "storage");
        inventory.setItem(10, storageItem);

        inventory.setItem(11, vehicle.isLocked() ?
                getItem(player, "unlock") :
                getItem(player, "lock"));

        List<String> fuelItems = plugin.typesToString(plugin.getFuelItems(), vehicle.getType(), true);
        ItemStack fuelRight = createFuelItem("fuel-right", fuelItems);
        ItemStack fuelLeft = createFuelItem("fuel-left", fuelItems);

        boolean fuelEnabled = vehicle.fuelEnabled();
        int depositSlot = vehicle.getFuelDepositSlot();

        ItemStack fuelDisabled = getItem(player, "fuel-disabled");
        inventory.setItem(12, fuelEnabled ? fuelRight : fuelDisabled);
        if (!fuelEnabled) inventory.setItem(depositSlot, fuelDisabled); // 13
        inventory.setItem(14, fuelEnabled ? fuelLeft : fuelDisabled);

        inventory.setItem(15, vehicle.getCustomizations().isEmpty() ?
                getItem(player, "no-customization") :
                getItem(player, "customization"));

        ItemStack transferOwnership = getItem(player, "transfer-ownership");
        inventory.setItem(16, transferOwnership);

        if (isHelicopter && vehicle instanceof Helicopter helicopter) {
            UUID outsideDriver = helicopter.getOutsideDriver();

            inventory.setItem(28, createHelicopterChairItem(player, 1, outsideDriver));
            inventory.setItem(29, createHelicopterChairItem(player, 2, outsideDriver));
            inventory.setItem(30, createHelicopterChairItem(player, 3, outsideDriver));

            inventory.setItem(32, createHelicopterChairItem(player, 4, outsideDriver));
            inventory.setItem(33, createHelicopterChairItem(player, 5, outsideDriver));
            inventory.setItem(34, createHelicopterChairItem(player, 6, outsideDriver));
        }

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack itemAtIndex = inventory.getItem(i);
            if (i != depositSlot && (itemAtIndex == null || itemAtIndex.getType().isAir())) {
                inventory.setItem(i, background);
            }
        }

        player.openInventory(inventory);
    }

    private @Nullable ItemStack createHelicopterChairItem(Player player, int chair, UUID outsideDriver) {
        Pair<ArmorStand, StandSettings> firstPair = vehicle.getChairs().get(chair);
        if (firstPair == null) return null;

        List<Entity> passengers = firstPair.getKey().getPassengers();

        Entity passenger;
        ItemBuilder builder;
        if (passengers.isEmpty()) {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-empty");
        } else if ((passenger = passengers.get(0)).getUniqueId().equals(outsideDriver) && outsideDriver.equals(player.getUniqueId())) {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-sitted");
        } else {
            builder = plugin.getItem("gui.vehicle.items.helicopter-chair-occupied")
                    .replace("%player%", passenger.getName());
        }

        return builder.replace("%chair%", chair).setData(plugin.getChairNumbeKey(), PersistentDataType.INTEGER, chair).build();
    }

    private ItemStack getItem(@NotNull Player player, String itemName) {
        ItemBuilder builder = plugin.getItem("gui.vehicle.items." + itemName);

        if (!vehicle.isOwner(player)) {
            builder.addLore(plugin.getConfig().getString("translations.only-owner"));
        }

        return builder.build();
    }

    private ItemStack createFuelItem(String itemName, List<String> fuelItems) {
        return plugin.getItem("gui.vehicle.items." + itemName)
                .applyMultiLineLore(fuelItems, "%fuel%", PluginUtils.translate(plugin.getConfig().getString("translations.no-fuel")))
                .build();
    }

    private String getTitle() {
        String title = plugin.getConfig().getString("gui.vehicle.title");
        String typeFormatted = plugin.getVehicleTypeFormatted(vehicle.getType());

        return title != null ? title
                .replace("%owner%", Optional.of(Bukkit.getOfflinePlayer(vehicle.getOwner())).map(OfflinePlayer::getName).orElse("???"))
                .replace("%type%", typeFormatted) : typeFormatted;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}