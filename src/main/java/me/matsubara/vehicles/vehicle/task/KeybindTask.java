package me.matsubara.vehicles.vehicle.task;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.ActionKeybind;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.util.ComponentUtil;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KeybindTask extends BukkitRunnable {

    private final Player player;
    private final Vehicle vehicle;
    private final VehiclesPlugin plugin;
    private final VehicleManager manager;
    private final Component component;

    private int ticks;

    public KeybindTask(Player player, @NotNull Vehicle vehicle, int ticks) {
        this.player = player;
        this.vehicle = vehicle;
        this.plugin = vehicle.getPlugin();
        this.manager = plugin.getVehicleManager();
        this.ticks = ticks;
        this.component = createKeyControls();
        runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        // Stop sending keybinds if the player is invalid.
        if (!player.isValid() || !player.isOnline()) {
            cancel();
            return;
        }

        // Re-sends the messages every 2 seconds, so it doesn't go away from the player's screen.
        if (ticks % 20 == 0) {
            player.sendActionBar(component);
        }

        if (--ticks <= 0) cancel();
    }

    @Override
    public void cancel() throws IllegalStateException {
        manager.getKeybindTasks().remove(player.getUniqueId());
        super.cancel();
    }

    private Component createKeyControls() {
        ActionKeybind openKeybind = plugin.getOpenMenuKeybind();
        ActionKeybind shootKeybind = plugin.getShootWeaponKeybind();

        FileConfiguration config = plugin.getConfig();
        Component builder = Component.empty();

        String path = "vehicle-keybinds.action-bar-message.type.";

        boolean plane = vehicle.is(VehicleType.PLANE);
        if (plane || vehicle.is(VehicleType.TANK)) {
            String shoot = config.getString(path + "shoot");
            builder = addKeybind(builder, shoot, Component.keybind(shootKeybind.getKeybind()));
        }

        boolean helicopter = vehicle.is(VehicleType.HELICOPTER);
        if (helicopter) {
            String up = config.getString(path + "up.helicopter");
            String down = config.getString(path + "down.helicopter");
            builder = addKeybind(builder, up, Component.keybind("key.jump"));
            builder = addKeybind(builder, down, Component.keybind("key.back"));
        }

        if (plane) {
            String up = config.getString(path + "up.plane");
            String down = config.getString(path + "down.plane");
            builder = addKeybind(builder, up, null);
            builder = addKeybind(builder, down, null);
        }

        String forward = config.getString(path + "forward");
        builder = addKeybind(builder, forward, Component.keybind("key.forward"));

        if (!helicopter) {
            String back = config.getString(path + "back");
            builder = addKeybind(builder, back, Component.keybind("key.back"));
        }

        String left = config.getString(path + "left");
        builder = addKeybind(builder, left, Component.keybind("key.left"));

        String right = config.getString(path + "right");
        builder = addKeybind(builder, right, Component.keybind("key.right"));

        String leave = config.getString(path + "leave");
        builder = addKeybind(builder, leave, Component.keybind("key.sneak"));

        String menu = config.getString(path + "menu");
        builder = addKeybind(builder, menu, Component.keybind(openKeybind.getKeybind()), false);

        return builder;
    }

    private Component addKeybind(@NotNull Component builder, String text, @Nullable Component keybind) {
        return addKeybind(builder, text, keybind, true);
    }

    private Component addKeybind(@NotNull Component builder, String text, @Nullable Component keybind, boolean limiter) {
        if (text == null || text.isBlank()) return builder;

        builder = builder.append(ComponentUtil.deserialize(text));

        if (keybind != null) {
            builder = builder.append(keybind);
        }

        if (!limiter) return builder;

        Component separator = Config.KEYBINDS_ACTION_BAR_MESSAGE_SEPARATOR.asComponentTranslated();
        return builder.append(separator);
    }
}