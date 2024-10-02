package me.matsubara.vehicles.vehicle.task;

import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.ActionKeybind;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
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
    private final BaseComponent[] components;

    private int ticks;

    public KeybindTask(Player player, @NotNull Vehicle vehicle, int ticks) {
        this.player = player;
        this.vehicle = vehicle;
        this.plugin = vehicle.getPlugin();
        this.manager = plugin.getVehicleManager();
        this.ticks = ticks;
        this.components = createKeyControls();
        runTaskTimer(plugin, 1L, 1L);
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
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
        }

        if (--ticks <= 0) cancel();
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        manager.getKeybindTasks().remove(player.getUniqueId());
        super.cancel();
    }

    private BaseComponent[] createKeyControls() {
        ActionKeybind openKeybind = plugin.getOpenMenuKeybind();
        ActionKeybind shootKeybind = plugin.getShootWeaponKeybind();

        FileConfiguration config = plugin.getConfig();
        ComponentBuilder builder = new ComponentBuilder();

        String path = "vehicle-keybinds.action-bar-message.type.";

        boolean plane = vehicle.is(VehicleType.PLANE);
        if (plane || vehicle.is(VehicleType.TANK)) {
            String shoot = config.getString(path + "shoot");
            addKeybind(builder, shoot, new KeybindComponent(shootKeybind.getKeybind()));
        }

        boolean helicopter = vehicle.is(VehicleType.HELICOPTER);
        if (helicopter) {
            String up = config.getString(path + "up.helicopter");
            String down = config.getString(path + "down.helicopter");
            addKeybind(builder, up, new KeybindComponent(Keybinds.JUMP));
            addKeybind(builder, down, new KeybindComponent(Keybinds.BACK));
        }

        if (plane) {
            String up = config.getString(path + "up.plane");
            String down = config.getString(path + "down.plane");
            addKeybind(builder, up, null);
            addKeybind(builder, down, null);
        }

        String forward = config.getString(path + "forward");
        addKeybind(builder, forward, new KeybindComponent(Keybinds.FORWARD));

        if (!helicopter) {
            String back = config.getString(path + "back");
            addKeybind(builder, back, new KeybindComponent(Keybinds.BACK));
        }

        String left = config.getString(path + "left");
        addKeybind(builder, left, new KeybindComponent(Keybinds.LEFT));

        String right = config.getString(path + "right");
        addKeybind(builder, right, new KeybindComponent(Keybinds.RIGHT));

        String leave = config.getString(path + "leave");
        addKeybind(builder, leave, new KeybindComponent(Keybinds.SNEAK));

        String menu = config.getString(path + "menu");
        addKeybind(builder, menu, new KeybindComponent(openKeybind.getKeybind()), false);

        return builder.create();
    }

    private void addKeybind(@NotNull ComponentBuilder builder, String text, @Nullable BaseComponent keybind) {
        addKeybind(builder, text, keybind, true);
    }

    private void addKeybind(@NotNull ComponentBuilder builder, String text, @Nullable BaseComponent keybind, boolean limiter) {
        if (text == null || text.isBlank()) return;

        builder.append(TextComponent.fromLegacyText(PluginUtils.translate(text)));

        if (keybind != null) {
            builder.append(keybind);
        }

        if (!limiter) return;

        String separator = Config.KEYBINDS_ACTION_BAR_MESSAGE_SEPARATOR.asStringTranslated();
        builder.append(TextComponent.fromLegacyText(separator), ComponentBuilder.FormatRetention.NONE);
    }
}