package me.matsubara.vehicles.listener;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.ConfirmShopGUI;
import me.matsubara.vehicles.gui.CustomizationGUI;
import me.matsubara.vehicles.gui.ShopGUI;
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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;
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
    private static final List<Integer> HELICOPTER_CHAIR_SLOTS = List.of(5, 10, 15, 6, 11, 16);

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
    public void onInventoryOpen(@NotNull InventoryOpenEvent event) {
        if (!((event.getPlayer()) instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Llama llama)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleFromLlama(llama);
        if (vehicle == null) return;

        vehicle.updateLlamaInventory(player, llama.getInventory());
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CustomizationGUI || holder instanceof ShopGUI) {
            if (event.getRawSlots().stream().anyMatch(integer -> integer < 36)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(holder instanceof Llama llama)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleFromLlama(llama);
        if (vehicle == null || !vehicle.fuelEnabled()) return;

        Set<Integer> rawSlots = event.getRawSlots();
        for (Integer rawSlot : new HashSet<>(rawSlots)) {
            if (rawSlot >= 2 + 3 * llama.getStrength()) continue;

            int removed;
            ItemStack item;
            if (rawSlots.size() == 1
                    && rawSlot == vehicle.getFuelDepositSlot()
                    && (removed = handleFuel(vehicle, item = event.getNewItems().get(rawSlot))) != -1) {
                runTask(() -> handleRemainingItemAmount(item, player::setItemOnCursor, removed));
            }

            event.setCancelled(true);
            break;
        }
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

        if (inventory instanceof LlamaInventory llamaInventory) {
            if (llamaInventory.getHolder() instanceof Llama llama) {
                handleVehicleGUI(player, llama, current, event);
            }
        }

        // Prevent moving items from player inventory to custom inventories by shift-clicking (except for storage GUI).
        Inventory topInventory;
        if (event.getClick().isShiftClick() && ((topInventory = event.getView().getTopInventory()).getHolder() instanceof CustomizationGUI
                || (topInventory instanceof LlamaInventory llamaInventory
                && llamaInventory.getHolder() instanceof Llama llama
                && plugin.getVehicleManager().getVehicleFromLlama(llama) != null))) {
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof ConfirmShopGUI confirm) {
            event.setCancelled(true);

            if (current == null) return;

            if (current.isSimilar(confirm.getConfirm())) {
                confirm.setOpenBack(false);
                completePurchase(player, confirm.getMoney(), confirm.getData(), confirm.getShopDisplayName());
            } else if (current.isSimilar(confirm.getCancel())) {
                closeInventory(player);
            }

            return;
        }

        Messages messages = plugin.getMessages();

        if (holder instanceof ShopGUI shop) {
            event.setCancelled(true);

            if (current == null) return;

            if (current.isSimilar(shop.getClose())) {
                closeInventory(player);
            } else if (current.isSimilar(shop.getPrevious())) {
                shop.previousPage(event.isShiftClick());
            } else if (current.isSimilar(shop.getNext())) {
                shop.nextPage(event.isShiftClick());
            } else if (current.isSimilar(shop.getPreviousType())) {
                int currentOrdinal = shop.getCurrentType().ordinal();

                VehicleType[] values = VehicleType.values();
                VehicleType newType = values[currentOrdinal - 1 < 0 ? values.length - 1 : currentOrdinal - 1];

                shop.setCurrentType(newType);
            } else if (current.isSimilar(shop.getNextType())) {
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
                    messages.send(player, Messages.Message.SHOP_NOT_ENOUGH_MONEY, string -> string.replace("%money%", String.valueOf(money)));
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

        if (current.isSimilar(customization.getClose())) {
            closeInventory(player);
        } else if (current.isSimilar(customization.getPrevious())) {
            customization.previousPage(isShiftClick);
        } else if (current.isSimilar(customization.getNext())) {
            customization.nextPage(isShiftClick);
        } else if (current.isSimilar(customization.getSearch())) {
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
        } else if (current.isSimilar(customization.getClearSearch())) {
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

        messages.send(player, Messages.Message.CUSTOMIZATION_SUCCESSFUL_PURCHASE, string -> string.replace("%money%", String.valueOf(money)));

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
        PreviewTick currentPreview = plugin.getVehicleManager().getPreviews().get(player.getUniqueId());
        if (currentPreview != null && !currentPreview.isCancelled()) {
            currentPreview.cancel();
        }

        new PreviewTick(plugin, player, data).runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    @SuppressWarnings("deprecation")
    private void handleVehicleGUI(Player player, @NotNull Llama llama, ItemStack current, InventoryClickEvent event) {
        Vehicle vehicle = plugin.getVehicleManager().getVehicleFromLlama(llama);
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

        LlamaInventory inventory = llama.getInventory();
        UUID playerUUID = player.getUniqueId();
        boolean isOwner = playerUUID.equals(vehicle.getOwner());

        int index = index(slot, vehicle.is(VehicleType.HELICOPTER));
        if (index == 2) { // Lock / unlock
            if (!isOwner) return;

            if (vehicle.isLocked()) {
                vehicle.setLocked(false);
                inventory.setItem(index, plugin.getItem("gui.vehicle.items.lock").build());
            } else {
                vehicle.setLocked(true);
                inventory.setItem(2, plugin.getItem("gui.vehicle.items.unlock").build());

                UUID driver = vehicle.getDriver();
                if (driver != null && !driver.equals(vehicle.getOwner())) {
                    Pair<LivingEntity, StandSettings> primaryChair = vehicle.getChair(0);
                    if (primaryChair != null) primaryChair.getKey().eject();
                }
            }
        } else if (index == 1) { // Storage
            if (!isOwner) return;

            if (vehicle.getStorageRows() > 0) {
                runTask(() -> player.openInventory(vehicle.getInventory()));
            }
        } else if (index == 4) { // Customizations
            if (!isOwner) return;

            if (!vehicle.getCustomizations().isEmpty()) {
                runTask(() -> new CustomizationGUI(plugin, vehicle, player, null));
            }
        } else if (index == 6) { // Transfer ownership
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
                                for (Pair<LivingEntity, StandSettings> chair : vehicle.getChairs()) {
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
        if (!HELICOPTER_CHAIR_SLOTS.contains(slot)) return;

        int chair = HELICOPTER_CHAIR_SLOTS.indexOf(slot) + 1;

        Pair<LivingEntity, StandSettings> chairPair = vehicle.getChair(chair);
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
            Pair<LivingEntity, StandSettings> driverPair = vehicle.getChair(0);
            if (driverPair != null) driverPair.getKey().addPassenger(player);

            vehicle.getPassengers().remove(playerUUID);
            vehicle.setDriverRaw(playerUUID);
        }

        closeInventory(player);
    }

    private int index(int index, boolean isHelicopter) {
        if (!isHelicopter) return index;

        if (PluginUtils.is(index, 1, 2, 3)) return index;
        if (PluginUtils.is(index, 7, 8)) return index - 3;
        if (PluginUtils.is(index, 12, 13)) return index - 6;

        return Integer.MIN_VALUE;
    }

    private void sitOnChair(@NotNull Helicopter helicopter, @NotNull Player player, int chair) {
        if (!player.isInsideVehicle()) {
            closeInventory(player);
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // The driver is moving to outside (or was outside, and it's moving to another outside chair).
        if (helicopter.isDriver(playerUUID)) {
            helicopter.setOutsideDriver(playerUUID);
            helicopter.setDriverRaw(null);
        }

        // Mark as transfer.
        helicopter.getTransfers().add(playerUUID);

        Pair<LivingEntity, StandSettings> pair = helicopter.getChairs().get(chair);
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