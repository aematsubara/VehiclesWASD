package me.matsubara.vehicles.listener;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.ConfirmShopGUI;
import me.matsubara.vehicles.gui.CustomizationGUI;
import me.matsubara.vehicles.gui.ShopGUI;
import me.matsubara.vehicles.gui.VehicleGUI;
import me.matsubara.vehicles.hook.VaultExtension;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Customization;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import me.matsubara.vehicles.vehicle.task.PreviewTick;
import me.matsubara.vehicles.vehicle.type.Helicopter;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public final class InventoryListener implements Listener {

    private final VehiclesPlugin plugin;

    private static final List<AnvilGUI.ResponseAction> CLOSE_RESPONSE = Collections.singletonList(AnvilGUI.ResponseAction.close());

    public InventoryListener(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();

        InventoryHolder holder = inventory.getHolder();
        if (holder == null) return;

        if (holder instanceof ConfirmShopGUI confirm) {
            if (confirm.isOpenBack()) {
                runTask(() -> new ShopGUI(plugin, player, confirm.getData().type(), confirm.getPreviousPage()));
            }
            return;
        }

        if (holder instanceof Vehicle vehicle) {
            Inventory storage = vehicle.getInventory();
            String base64Storage = PluginUtils.itemStackArrayToBase64(storage.getContents());
            vehicle.setBase64Storage(base64Storage);
        }
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof CustomizationGUI || holder instanceof ShopGUI) {
            if (event.getRawSlots().stream().anyMatch(integer -> integer < inventory.getSize())) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(holder instanceof VehicleGUI gui)) return;

        Vehicle vehicle = gui.getVehicle();
        if (vehicle == null || !vehicle.fuelEnabled()) return;

        ItemStack item;
        int slot, removed;
        boolean ignoreSaddle = inventory.getSize() == 7;

        Set<Integer> slots = event.getRawSlots();
        if (slots.size() != 1
                || (slot = slots.iterator().next()) != vehicle.getFuelDepositSlot()
                || (removed = handleFuel(vehicle, item = event.getNewItems().get(slot))) == -1) {
            event.setCancelled(true);
            return;
        }

        runTask(() -> handleRemainingItemAmount(item, player::setItemOnCursor, removed));
    }

    public boolean isCustomItem(@NotNull ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && Objects.equals(meta.getPersistentDataContainer().get(plugin.getItemIdKey(), PersistentDataType.STRING), name);
    }

    private void handleRemainingItemAmount(
            @NotNull ItemStack item,
            Consumer<ItemStack> action,
            int removed) {
        int newAmount = item.getAmount() - removed;
        if (newAmount == 0) {
            action.accept(null);
            return;
        }

        ItemStack temp = item.clone();
        temp.setAmount(newAmount);
        action.accept(temp);
    }

    private int getFuelByItem(@NotNull Vehicle vehicle, @NotNull ItemStack item) {
        TypeTarget target = plugin.getTypeTargetManager().applies(plugin.getFuelItems(), vehicle.getType(), item.getType());
        return target != null ? target.getAmount() : -1;
    }

    private int handleFuel(Vehicle vehicle, ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;
        if (vehicle.getFuel() >= vehicle.getMaxFuel()) return -1;

        int energyByFuel;
        if ((energyByFuel = getFuelByItem(vehicle, item)) == -1) return -1;

        int amountOfEnergy = 0, count = 0;
        for (int i = 1; i <= item.getAmount(); i++) {
            amountOfEnergy += energyByFuel;
            count++;
            if (vehicle.getFuel() + amountOfEnergy >= vehicle.getMaxFuel()) break;
        }

        vehicle.setFuel(Math.min(vehicle.getFuel() + amountOfEnergy, vehicle.getMaxFuel()));
        vehicle.getRefuelSound().playAt(vehicle.getModel().getLocation());

        if (vehicle.getDriver() != null && vehicle.canMove()) {
            vehicle.toggleLights(true);
        }

        return count;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        ItemStack current = event.getCurrentItem();

        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof VehicleGUI gui) {
            handleVehicleGUI(player, gui, current, event);
        }

        // Prevent moving items from player inventory to custom inventories by shift-clicking (except for storage GUI).
        InventoryHolder topHolder;
        if (event.getClick().isShiftClick() && ((topHolder = event.getView().getTopInventory().getHolder()) instanceof CustomizationGUI
                || (topHolder instanceof VehicleGUI gui && gui.getVehicle() != null))) {
            event.setCancelled(true);
            return;
        }

        if (holder instanceof ConfirmShopGUI confirm) {
            event.setCancelled(true);

            if (current == null) return;

            if (isCustomItem(current, "confirm")) {
                confirm.setOpenBack(false);
                completePurchase(player, confirm.getMoney(), confirm.getData(), confirm.getShopDisplayName());
            } else if (isCustomItem(current, "cancel")) {
                closeInventory(player);
            }

            return;
        }

        Messages messages = plugin.getMessages();

        if (holder instanceof ShopGUI shop) {
            event.setCancelled(true);

            if (current == null) return;

            if (isCustomItem(current, "close")) {
                closeInventory(player);
            } else if (isCustomItem(current, "previous-page")) {
                shop.previousPage(event.isShiftClick());
            } else if (isCustomItem(current, "next-page")) {
                shop.nextPage(event.isShiftClick());
            } else if (isCustomItem(current, "previous-type")) {
                int currentOrdinal = shop.getCurrentType().ordinal();

                VehicleType[] values = VehicleType.values();
                VehicleType newType = values[currentOrdinal - 1 < 0 ? values.length - 1 : currentOrdinal - 1];

                shop.setCurrentType(newType);
            } else if (isCustomItem(current, "next-type")) {
                int currentOrdinal = shop.getCurrentType().ordinal();

                VehicleType[] values = VehicleType.values();
                VehicleType newType = values[currentOrdinal + 1 == values.length ? 0 : currentOrdinal + 1];

                shop.setCurrentType(newType);
            }

            if (current.getItemMeta() == null) return;

            ItemMeta meta = current.getItemMeta();
            PersistentDataContainer container = meta.getPersistentDataContainer();

            String typeCategory = container.get(plugin.getVehicleTypeKey(), PersistentDataType.STRING);
            if (typeCategory != null) {
                VehicleType type = PluginUtils.getOrNull(VehicleType.class, typeCategory.toUpperCase());
                if (type != null && type != shop.getCurrentType()) {
                    shop.setCurrentType(type);
                }
                return;
            }

            VehicleData data = container.get(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA);
            if (data == null) return;

            if (event.getClick().isLeftClick()) {
                Double money = container.get(plugin.getMoneyKey(), PersistentDataType.DOUBLE);
                if (money == null) return;

                VaultExtension vaultExtension = plugin.getVaultExtension();
                if (!vaultExtension.has(player, money)) {
                    messages.send(player, Messages.Message.SHOP_NOT_ENOUGH_MONEY, string -> string.replace("%money%", vaultExtension.format(money)));
                    closeInventory(player);
                    return;
                }

                if (Config.CONFIRM_SHOP.asBool()) {
                    runTask(() -> new ConfirmShopGUI(plugin, player, current, data, money, shop.getCurrentPage()));
                } else {
                    String shopDisplayName;
                    if (meta.hasDisplayName()) shopDisplayName = meta.getDisplayName();
                    else shopDisplayName = null;

                    completePurchase(player, money, data, shopDisplayName);
                }

                return;
            }

            if (!event.getClick().isRightClick()) return;
            if (!Config.SHOP_PREVIEW_ENABLED.asBool()) return;

            startPreview(player, data);
            closeInventory(player);

            return;
        }

        if (!(holder instanceof CustomizationGUI customization)) return;

        event.setCancelled(true);

        if (current == null) return;

        boolean isShiftClick = event.isShiftClick();

        if (isCustomItem(current, "close")) {
            closeInventory(player);
        } else if (isCustomItem(current, "previous-page")) {
            customization.previousPage(isShiftClick);
        } else if (isCustomItem(current, "next-page")) {
            customization.nextPage(isShiftClick);
        } else if (isCustomItem(current, "search")) {
            new AnvilGUI.Builder()
                    .onClick((slot, snapshot) -> {
                        if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                        runTask(() -> new CustomizationGUI(plugin, customization.getVehicle(), snapshot.getPlayer(), snapshot.getText()));
                        return CLOSE_RESPONSE;
                    })
                    .title(Config.CUSTOMIZATION_SEARCH_TITLE.asStringTranslated())
                    .text(Config.CUSTOMIZATION_SEARCH_TEXT.asStringTranslated())
                    .itemLeft(new ItemStack(Material.PAPER))
                    .plugin(plugin)
                    .open(player);
        } else if (isCustomItem(current, "clear-search")) {
            runTask(() -> new CustomizationGUI(plugin, customization.getVehicle(), player, null));
        }

        if (current.getItemMeta() == null) return;

        ItemMeta meta = current.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String customizationName = container.get(plugin.getCustomizationKey(), PersistentDataType.STRING);
        if (customizationName == null) return;

        Vehicle vehicle = customization.getVehicle();

        Customization customizationByName = plugin.getVehicleManager().getCustomizationByName(vehicle.getCustomizations(), customizationName);
        if (customizationByName == null) return;

        int hotbarButton = event.getHotbarButton();
        ItemStack cursor = hotbarButton != -1 ? player.getInventory().getItem(hotbarButton) : event.getCursor();

        Material newType;
        if (cursor == null || cursor.getType().isAir() || !customizationByName.isTagged(newType = cursor.getType())) {
            messages.send(player, Messages.Message.CUSTOMIZATION_INVALID_CURSOR);
            closeInventory(player);
            return;
        }

        if (newType == current.getType()) {
            messages.send(player, Messages.Message.CUSTOMIZATION_ALREADY_IN_USE);
            closeInventory(player);
            return;
        }

        int requiredAmount = customizationByName.size();

        if (cursor.getAmount() < requiredAmount) {
            messages.send(player, Messages.Message.CUSTOMIZATION_DIFFERENT_AMOUNT);
            closeInventory(player);
            return;
        }

        handleRemainingItemAmount(
                cursor,
                hotbarButton != -1 ?
                        item -> player.getInventory().setItem(hotbarButton, item) :
                        event::setCursor,
                requiredAmount);

        if (Config.CLOSE_CUSTOMIZATION_GUI_IF_SUCCESSFUL.asBool()) {
            closeInventory(player);
        } else {
            current.setType(newType);
            current.setAmount(requiredAmount);
        }

        BoundingBox box = vehicle.getBox();
        double y = box.getHeight() / 2;
        player.getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                vehicle.getVelocityStand().getLocation().clone().add(0.0d, y, 0.0d),
                20,
                box.getWidthX() / 2,
                y,
                box.getWidthZ() / 2);

        plugin.getVehicleManager().applyCustomization(vehicle.getModel(), customizationByName, newType);
    }

    private void completePurchase(Player player, double money, @NotNull VehicleData data, String shopDisplayName) {
        Messages messages = plugin.getMessages();

        VaultExtension vaultExtension = plugin.getVaultExtension();
        if (!vaultExtension.takeMoney(player, money)) {
            messages.send(player, Messages.Message.AN_ERROR_OCURRED);
            closeInventory(player);
            return;
        }

        messages.send(player, Messages.Message.SHOP_SUCCESSFUL_PURCHASE, string -> string.replace("%money%", vaultExtension.format(money)));

        VehicleData temp = new VehicleData(
                player.getUniqueId(), // We need to assign the owner here, otherwise we won't be able to use the vehicle.
                data.fuel(),
                data.locked(),
                data.modelUniqueId(),
                data.location(),
                data.type(),
                data.base64Storage(),
                shopDisplayName,
                data.customizationChanges());

        player.getInventory().addItem(plugin.createVehicleItem(temp.type().toFilePath(), temp));

        closeInventory(player);
    }

    private void startPreview(@NotNull Player player, VehicleData data) {
        PreviewTick currentPreview = plugin.getVehicleManager().getPreviews().remove(player.getUniqueId());
        if (currentPreview != null && !currentPreview.isCancelled()) {
            currentPreview.cancel();
        }

        new PreviewTick(plugin, player, data).runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    @SuppressWarnings("deprecation")
    private void handleVehicleGUI(Player player, @NotNull VehicleGUI gui, ItemStack current, InventoryClickEvent event) {
        Vehicle vehicle = gui.getVehicle();
        if (vehicle == null) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == vehicle.getFuelDepositSlot() && vehicle.fuelEnabled()) {

            int hotbarButton = event.getHotbarButton();
            ItemStack cursor = hotbarButton != -1 ? player.getInventory().getItem(hotbarButton) : event.getCursor();

            int removed = handleFuel(vehicle, cursor);
            if (removed != -1 && cursor != null) {
                handleRemainingItemAmount(
                        cursor,
                        hotbarButton != -1 ?
                                item -> player.getInventory().setItem(hotbarButton, item) :
                                event::setCursor,
                        removed);
            }
            return;
        }

        if (current == null) return;

        Inventory inventory = gui.getInventory();
        UUID playerUUID = player.getUniqueId();
        boolean isOwner = playerUUID.equals(vehicle.getOwner());

        if (isCustomItem(current, "lock") || isCustomItem(current, "unlock")) {
            if (!isOwner) return;

            if (vehicle.isLocked()) {
                vehicle.setLocked(false);
                inventory.setItem(slot, plugin.getItem("gui.vehicle.items.lock").build());
            } else {
                vehicle.setLocked(true);
                inventory.setItem(slot, plugin.getItem("gui.vehicle.items.unlock").build());

                UUID driver = vehicle.getDriver();
                if (driver != null && !driver.equals(vehicle.getOwner())) {
                    Pair<ArmorStand, StandSettings> primaryChair = vehicle.getChair(0);
                    if (primaryChair != null) primaryChair.getKey().eject();
                }
            }
        } else if (isCustomItem(current, "storage")) {
            if (!isOwner) return;

            if (vehicle.getStorageRows() > 0) {
                runTask(() -> player.openInventory(vehicle.getInventory()));
            }
        } else if (isCustomItem(current, "customization")) {
            if (!isOwner) return;

            if (!vehicle.getCustomizations().isEmpty()) {
                runTask(() -> new CustomizationGUI(plugin, vehicle, player, null));
            }
        } else if (isCustomItem(current, "transfer-ownership")) {
            if (!isOwner) return;

            new AnvilGUI.Builder()
                    .onClick((at, snapshot) -> {
                        if (at != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                        String text = snapshot.getText();
                        Player clicker = snapshot.getPlayer();

                        Messages messages = plugin.getMessages();

                        if (text.isEmpty()) {
                            messages.send(clicker, Messages.Message.TRANSFER_SPECIFY_PLAYER);
                            return CLOSE_RESPONSE;
                        }

                        Player newOwner = Bukkit.getPlayer(text);
                        if (newOwner != null && newOwner.isOnline()) {
                            if (newOwner.getUniqueId().equals(vehicle.getOwner())) {
                                messages.send(clicker, Messages.Message.TRANSFER_SAME_OWNER);
                            } else {
                                UUID ownerUUID = newOwner.getUniqueId();

                                // Eject every passenger (except new owner).
                                for (Pair<ArmorStand, StandSettings> chair : vehicle.getChairs()) {
                                    String partName = chair.getValue().getPartName();

                                    String newOwnerChair = vehicle.getPassengers().get(ownerUUID);
                                    if (newOwnerChair != null && newOwnerChair.equals(partName)) {
                                        continue;
                                    }

                                    chair.getKey().eject();
                                }

                                vehicle.setLocked(true);
                                vehicle.setOwner(ownerUUID);

                                messages.send(clicker, Messages.Message.TRANSFER_NEW_OWNER, string -> string.replace("%player%", newOwner.getName()));
                            }
                        } else {
                            messages.send(clicker, Messages.Message.TRANSFER_OFFLINE_PLAYER);
                        }

                        return CLOSE_RESPONSE;
                    })
                    .title(Config.TRANSFER_SEARCH_TITLE.asStringTranslated())
                    .text(Config.TRANSFER_SEARCH_TEXT.asStringTranslated())
                    .itemLeft(new ItemStack(Material.PAPER))
                    .plugin(plugin)
                    .open(player);
        }

        if (!(vehicle instanceof Helicopter helicopter)) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        int chair = meta.getPersistentDataContainer().getOrDefault(plugin.getChairNumbeKey(), PersistentDataType.INTEGER, -1);
        if (chair == -1) return;

        Pair<ArmorStand, StandSettings> chairPair = vehicle.getChair(chair);
        if (chairPair == null) return;

        List<Entity> passengers = chairPair.getKey().getPassengers();
        if (passengers.isEmpty()) {
            sitOnChair(helicopter, player, chair);
            return;
        }

        UUID passengerUUID;
        if ((passengerUUID = passengers.get(0).getUniqueId()).equals(helicopter.getOutsideDriver())
                && passengerUUID.equals(playerUUID)) { // Go back to the main chair if the player that clicked is the driver.
            helicopter.setOutsideDriver(null);
            helicopter.getTransfers().add(playerUUID);

            // Sit player on the driver sit.
            Pair<ArmorStand, StandSettings> driverPair = vehicle.getChair(0);
            if (driverPair != null) driverPair.getKey().addPassenger(player);

            vehicle.getPassengers().remove(playerUUID);
            vehicle.setDriverRaw(playerUUID);
        }

        closeInventory(player);
    }

    private void sitOnChair(@NotNull Helicopter helicopter, @NotNull Player player, int chair) {
        UUID playerUUID = player.getUniqueId();

        // The driver is moving to outside (or was outside, and it's moving to another outside chair).
        if (helicopter.isDriver(playerUUID)) {
            helicopter.setOutsideDriver(playerUUID);
            helicopter.setDriverRaw(null);
        }

        // Mark as transfer.
        helicopter.getTransfers().add(playerUUID);

        Pair<ArmorStand, StandSettings> pair = helicopter.getChairs().get(chair);
        if (pair != null) {
            pair.getKey().addPassenger(player);
            helicopter.getPassengers().put(playerUUID, pair.getValue().getPartName());
        }

        closeInventory(player);
    }

    private void closeInventory(@NotNull HumanEntity player) {
        runTask(player::closeInventory);
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}