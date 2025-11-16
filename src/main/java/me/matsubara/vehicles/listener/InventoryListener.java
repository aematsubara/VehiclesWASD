package me.matsubara.vehicles.listener;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.gui.*;
import me.matsubara.vehicles.hook.economy.EconomyExtension;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.manager.targets.TypeTarget;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.IStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.*;
import me.matsubara.vehicles.vehicle.type.Generic;
import me.matsubara.vehicles.vehicle.type.UpAndDown;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
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

        // Save inventory when closing.
        if (holder instanceof Vehicle vehicle) vehicle.saveInventory();
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
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(plugin.getFuelItemKey(), PersistentDataType.INTEGER)) {
                return (int) vehicle.getMaxFuel() + 1;
            }
        }

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
        VehicleManager manager = plugin.getVehicleManager();

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
                VehicleType type = PluginUtils.getOrNull(VehicleType.class, typeCategory.toUpperCase(Locale.ROOT));
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

                EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
                if (!economyExtension.has(player, money)) {
                    messages.send(player, Messages.Message.SHOP_NOT_ENOUGH_MONEY, string -> string.replace("%money%", economyExtension.format(money)));
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

            manager.startPreview(player, data);
            closeInventory(player);
            return;
        }

        if (holder instanceof MyVehiclesGUI vehicles) {
            event.setCancelled(true);

            if (current == null) return;

            boolean isShiftClick = event.isShiftClick();

            if (isCustomItem(current, "close")) {
                closeInventory(player);
            } else if (isCustomItem(current, "previous-page")) {
                vehicles.previousPage(isShiftClick);
            } else if (isCustomItem(current, "next-page")) {
                vehicles.nextPage(isShiftClick);
            }

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            PersistentDataContainer container = meta.getPersistentDataContainer();

            VehicleData data = container.get(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA);
            if (data == null) return;

            Location location = data.location();
            UUID modelUniqueId = data.modelUniqueId();

            Vehicle temp = manager.getVehicleByModelId(modelUniqueId);
            if (temp != null) temp.getVelocityStand().getLocation(location);

            // Load the chunk before looking for the vehicle.
            Chunk chunk = location.getChunk();
            chunk.load();
            chunk.getEntities();

            // After loading the chunk, the vehicle SHOULD be present.
            Vehicle vehicle = manager.getVehicleByModelId(modelUniqueId);
            if (vehicle != null) {
                manager.saveVehicleOnInventory(player, vehicle);
            } else {
                messages.send(player, Messages.Message.PICK_NOT_FOUND);
            }

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

        Model model = vehicle.getModel();
        Set<Player> seeing = vehicle.getSeeingPlayers();

        if (event.getClick().isRightClick()) {
            List<IStand> stands = customizationByName.getStandList(model);

            Set<String> changed = new HashSet<>();

            for (IStand stand : stands) {
                StandSettings settings = stand.getSettings();
                if (settings.isGlow()) continue;

                settings.setGlow(true);
                settings.setMarker(true);
                stand.sendMetadata(seeing);

                changed.add(settings.getPartName());
            }

            // Glow for 3 seconds.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (IStand stand : stands) {
                    StandSettings settings = stand.getSettings();
                    if (!settings.isGlow()
                            || !changed.contains(settings.getPartName())) continue;

                    settings.setGlow(false);
                    settings.setMarker(false);
                    stand.sendMetadata(seeing);
                }
            }, 60L);

            closeInventory(player);
            return;
        }

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

        plugin.getVehicleManager().applyCustomization(model, customizationByName, newType, seeing);
    }

    private void completePurchase(Player player, double money, @NotNull VehicleData data, String shopDisplayName) {
        Messages messages = plugin.getMessages();

        EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
        if (!economyExtension.takeMoney(player, money)) {
            messages.send(player, Messages.Message.AN_ERROR_OCURRED);
            closeInventory(player);
            return;
        }

        messages.send(player, Messages.Message.SHOP_SUCCESSFUL_PURCHASE, string -> string.replace("%money%", economyExtension.format(money)));

        VehicleData temp = new VehicleData(
                player.getUniqueId(), // We need to assign the owner here, otherwise we won't be able to use the vehicle.
                data.fuel(),
                data.locked(),
                data.modelUniqueId(),
                data.location(),
                data.type(),
                data.base64Storage(),
                shopDisplayName,
                data.customizationChanges(),
                null);

        player.getInventory().addItem(plugin.createVehicleItem(temp.type(), temp));

        closeInventory(player);
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

        Messages messages = plugin.getMessages();

        if (isCustomItem(current, "lock") || isCustomItem(current, "unlock")) {
            if (!isOwner) return;

            if (vehicle.isLocked()) {
                vehicle.setLocked(false);
                inventory.setItem(slot, gui.getItem(player, "lock"));
            } else {
                vehicle.setLocked(true);
                inventory.setItem(slot, gui.getItem(player, "unlock"));

                // After locking the vehicle, we want to eject the passengers (except the driver and owner).
                for (Pair<ArmorStand, StandSettings> pair : vehicle.getChairs()) {
                    for (Entity entity : pair.getKey().getPassengers()) {
                        if (vehicle.isOwner(entity)) continue;
                        entity.leaveVehicle();
                    }
                }
            }
        } else if (isCustomItem(current, "storage")) {
            if (!isOwner) return;

            if (vehicle.getStorageRows() > 0) {
                runTask(() -> vehicle.openInventory(player));
            }
        } else if (isCustomItem(current, "customization")) {
            if (!isOwner) return;

            if (!vehicle.getCustomizations().isEmpty()) {
                if (Config.CUSTOMIZATIONS_REQUIRE_PERMISSION.asBool() && !player.hasPermission("vehicleswasd.customization")) {
                    messages.send(player, Messages.Message.CUSTOMIZATION_NO_PERMISSION);
                    return;
                }
                runTask(() -> new CustomizationGUI(plugin, vehicle, player, null));
            }
        } else if (isCustomItem(current, "transfer-ownership")) {
            if (!isOwner) return;

            new AnvilGUI.Builder()
                    .onClick((at, snapshot) -> {
                        if (at != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                        String text = snapshot.getText();
                        Player clicker = snapshot.getPlayer();

                        if (text.isEmpty()) {
                            messages.send(clicker, Messages.Message.TRANSFER_SPECIFY_PLAYER);
                            return CLOSE_RESPONSE;
                        }

                        Player newOwner = Bukkit.getPlayerExact(text);
                        if (newOwner != null && newOwner.isOnline()) {
                            if (vehicle.isOwner(newOwner)) {
                                messages.send(clicker, Messages.Message.TRANSFER_SAME_OWNER);
                            } else {
                                UUID ownerUUID = newOwner.getUniqueId();

                                // Eject every passenger (except new owner).
                                for (Pair<ArmorStand, StandSettings> chair : vehicle.getChairs()) {
                                    String partName = chair.getValue().getPartName();

                                    String newOwnerChair = vehicle.getPassengers().get(newOwner);
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

        if (vehicle.is(VehicleType.TRACTOR)) {
            if (isCustomItem(current, "enabled") || isCustomItem(current, "disabled")) {
                TractorMode mode = TractorMode.values()[slot - VehicleGUI.STATE_START];
                handleTractorMode(player, gui, mode);
            } else {
                for (TractorMode mode : TractorMode.values()) {
                    if (!isCustomItem(current, mode.toPath())) continue;
                    handleTractorMode(player, gui, mode);
                    break;
                }
            }
        }

        if (!(vehicle instanceof UpAndDown upAndDown)
                || !vehicle.is(VehicleType.HELICOPTER)) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        int chair = meta.getPersistentDataContainer().getOrDefault(plugin.getChairNumbeKey(), PersistentDataType.INTEGER, -1);
        if (chair == -1) return;

        Pair<ArmorStand, StandSettings> chairPair = vehicle.getChair(chair);
        if (chairPair == null) return;

        List<Entity> passengers = chairPair.getKey().getPassengers();
        if (passengers.isEmpty()) {
            sitOnChair(upAndDown, player, chair);
            return;
        }

        Entity passenger;
        if ((passenger = passengers.get(0)).equals(upAndDown.getOutsideDriver())
                && passenger.equals(player)) { // Go back to the main chair if the player that clicked is the driver.
            upAndDown.setOutsideDriver(null);
            upAndDown.getTransfers().add(playerUUID);

            // Sit player on the driver sit.
            Pair<ArmorStand, StandSettings> driverPair = vehicle.getChair(0);
            if (driverPair != null) driverPair.getKey().addPassenger(player);

            vehicle.getPassengers().remove(player);
            vehicle.setDriverRaw(player);
        }

        closeInventory(player);
    }

    private void handleTractorMode(Player player, @NotNull VehicleGUI gui, TractorMode newMode) {
        if (!(gui.getVehicle() instanceof Generic generic)) return;

        Inventory inventory = gui.getInventory();

        int start = VehicleGUI.STATE_START;

        TractorMode previousMode = generic.getTractorMode();
        if (previousMode != null) inventory.setItem(
                start + previousMode.ordinal(),
                gui.getItem(player, "disabled"));

        // We don't want to enable the same mode again.
        generic.setTractorMode(previousMode == newMode ? null : newMode);
        if (generic.getTractorMode() == null) return;

        inventory.setItem(
                start + newMode.ordinal(),
                gui.getItem(player, "enabled"));
    }

    private void sitOnChair(@NotNull UpAndDown upAndDown, @NotNull Player player, int chair) {
        // The driver is moving to outside (or was outside, and it's moving to another outside chair).
        if (upAndDown.isDriver(player)) {
            upAndDown.setOutsideDriver(player);
            upAndDown.setDriverRaw(null);
        }

        // Mark as transfer.
        upAndDown.getTransfers().add(player.getUniqueId());

        Pair<ArmorStand, StandSettings> pair = upAndDown.getChairs().get(chair);
        if (pair != null) {
            pair.getKey().addPassenger(player);
            upAndDown.getPassengers().put(player, pair.getValue().getPartName());
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