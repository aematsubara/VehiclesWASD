package me.matsubara.vehicles.gui;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Messages;
import me.matsubara.vehicles.util.InventoryUpdate;
import me.matsubara.vehicles.util.ItemBuilder;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Getter
public final class MyVehiclesGUI implements InventoryHolder {

    private final VehiclesPlugin plugin;
    private final Inventory inventory;
    private final Player player;
    private final ArrayList<VehicleData> vehicles = new ArrayList<>();

    private int current;
    private int pages;

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    public MyVehiclesGUI(@NotNull VehiclesPlugin plugin, @NotNull Player player) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 36);
        this.player = player;
        initVehicles();

        if (vehicles.isEmpty()) {
            plugin.getMessages().send(player, Messages.Message.VEHICLES_EMPTY);
            return;
        }

        player.openInventory(inventory);
        updateInventory();
    }

    private void initVehicles() {
        // Get data from existing vehicles.
        for (Vehicle vehicle : plugin.getVehicleManager().getVehicles()) {
            if (vehicle.isOwner(player)) {
                // There's no need to save inventory since we don't use it here.
                vehicles.add(vehicle.createSaveData(false));
            }
        }

        for (World world : Bukkit.getWorlds()) {
            // Load vehicles saved in the world.
            if (Vehicle.MY_VEHICLES_FEATURE_MODERN) {
                handleWorld(world, world.getPersistentDataContainer());
                continue;
            }

            // Load vehicles in the loaded chunks.
            for (Chunk chunk : world.getLoadedChunks()) {
                handleWorld(world, chunk.getPersistentDataContainer());
            }
        }

        // Sort by type, alphabetically.
        vehicles.sort(Comparator.comparing(data -> data.type().name()));
    }

    private void handleWorld(World world, @NotNull PersistentDataContainer container) {
        ArrayList<VehicleData> datas = container.get(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA_LIST);
        if (datas == null) return;

        for (VehicleData data : datas) {
            // Ignore spawned vehicles.
            if (vehicles.stream().anyMatch(temp ->
                    temp.modelUniqueId().equals(data.modelUniqueId()))) continue;

            if (player.getUniqueId().equals(data.owner())) {
                data.location().setWorld(world); // Update world.
                vehicles.add(data);
            }
        }
    }

    public void updateInventory() {
        inventory.clear();

        pages = (int) (Math.ceil((double) vehicles.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 36; i++) {
            if (ArrayUtils.contains(SLOTS, i) || ArrayUtils.contains(HOTBAR, i)) continue;
            inventory.setItem(i, background);
        }

        String path = "gui.vehicles.items.";

        if (current > 0) inventory.setItem(19, plugin.getItem(path + "previous-page").build());
        inventory.setItem(27, plugin.getItem(path + "information").build());
        inventory.setItem(35, plugin.getItem(path + "close").build());
        if (current < pages - 1) inventory.setItem(25, plugin.getItem(path + "next-page").build());

        if (vehicles.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = current * SLOTS.length;
        boolean isLastPage = current == pages - 1;

        FileConfiguration config = plugin.getConfig();
        for (int index = 0, aux = startFrom; isLastPage ? (index < vehicles.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            VehicleData data = vehicles.get(aux);
            data.keepWorld().set(true);

            Location location = data.location();

            World world = location.getWorld();
            if (world == null) continue;

            VehicleType type = data.type();

            String lock = config.getString("translations.lock." + (data.locked() ? "locked" : "unlocked"));

            //noinspection DataFlowIssue
            inventory.setItem(slotIndex.get(index), new ItemBuilder(plugin.getTypeCategoryItem().get(type))
                    .setLore(config.getStringList(path + "vehicle.lore"))
                    .replace("%type%", plugin.getVehicleTypeFormatted(type))
                    .replace("%world%", world.getName())
                    .replace("%x%", PluginUtils.fixedDouble(location.getX()))
                    .replace("%y%", PluginUtils.fixedDouble(location.getY()))
                    .replace("%z%", PluginUtils.fixedDouble(location.getZ()))
                    .replace("%fuel%", PluginUtils.fixedDouble(data.fuel()))
                    .replace("%max-fuel%", PluginUtils.fixedDouble(plugin.getMaxFuel(type)))
                    .replace("%lock%", lock)
                    .setData(plugin.getSaveDataKey(), Vehicle.VEHICLE_DATA, data)
                    .build());
        }

        InventoryUpdate.updateInventory(player, getTitle());
    }

    private @NotNull String getTitle() {
        String title = plugin.getConfig().getString("gui.vehicles.title");
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