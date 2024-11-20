package me.matsubara.vehicles.vehicle.task;

import lombok.Getter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.files.Config;
import me.matsubara.vehicles.manager.VehicleManager;
import me.matsubara.vehicles.model.Model;
import me.matsubara.vehicles.model.stand.PacketStand;
import me.matsubara.vehicles.model.stand.StandSettings;
import me.matsubara.vehicles.util.BlockUtils;
import me.matsubara.vehicles.util.PluginUtils;
import me.matsubara.vehicles.vehicle.Customization;
import me.matsubara.vehicles.vehicle.Vehicle;
import me.matsubara.vehicles.vehicle.VehicleData;
import me.matsubara.vehicles.vehicle.VehicleType;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PreviewTick extends BukkitRunnable {

    private final VehiclesPlugin plugin;
    private final Player player;
    private final @Getter Model model;
    private final VehicleData data;
    private final PacketStand center;
    private final List<Customization> customizations = new ArrayList<>();

    private final int seconds = (int) (Config.SHOP_PREVIEW_SECONDS.asDouble() * 20);
    private final boolean rainbow = Config.SHOP_PREVIEW_RAINBOW_MESSAGE.asBool();
    private final String shopPreviewMessage = Config.SHOP_PREVIEW_MESSAGE.asStringTranslated();

    private int ticks;
    private int hue;
    private float yaw;

    private static final float ROTATION_SPEED = 5.0f;

    public PreviewTick(VehiclesPlugin plugin, Player player, @NotNull VehicleData data) {
        this.plugin = plugin;
        this.player = player;

        VehicleType type = data.type();

        Location targetLocation = getPlayerTargetLocation(player);
        this.model = new Model(plugin, type, null, targetLocation);
        this.data = data;
        this.center = model.getStandByName("CENTER");

        VehicleManager manager = plugin.getVehicleManager();
        manager.initCustomizations(model, customizations, type);

        Map<String, Material> changes = data.customizationChanges();
        if (changes != null) {
            for (Map.Entry<String, Material> entry : changes.entrySet()) {
                manager.applyCustomization(model, customizations, entry.getKey(), entry.getValue());
            }
        }

        if (center != null) {
            centerStands(targetLocation, center.getSettings().getOffset());
        }

        // Only show to this player!
        for (PacketStand stand : model.getStands()) {
            stand.spawn(player);
        }

        this.yaw = targetLocation.getYaw();

        runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    private void centerStands(Location location, Vector offset) {
        for (PacketStand stand : model.getStands()) {
            StandSettings settings = stand.getSettings();
            Vector temp = settings.getOffset().subtract(offset);

            Location modified = location.clone().add(PluginUtils.offsetVector(temp, location.getYaw(), location.getPitch()));
            modified.setYaw(BlockUtils.yaw(modified.getYaw() + settings.getExtraYaw()));

            stand.setLocation(modified);
        }
    }

    @Override
    public void run() {
        if (++ticks == Integer.MAX_VALUE) ticks = 0;

        if (ticks == seconds
                || !player.isValid()
                || !player.getWorld().equals(model.getLocation().getWorld())) {
            cancel();
            return;
        }

        Location location = getPlayerTargetLocation(player);
        location.setYaw(yaw = BlockUtils.yaw(yaw + ROTATION_SPEED));

        model.setLocation(location);

        if (rainbow || ticks % 20 == 0) {
            int remaining = (int) Math.ceil((double) (seconds - ticks) / 20);
            String message = shopPreviewMessage.replace("%remaining%", String.valueOf(remaining));

            BaseComponent[] components = rainbow ?
                    new BaseComponent[]{new TextComponent(ChatColor.stripColor(message))} :
                    TextComponent.fromLegacyText(message);

            if (rainbow) components[0].setColor(ChatColor.of(Color.getHSBColor(hue / 360.0f, 1.0f, 1.0f)));

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
            hue += 6;
        }

        if (ticks % 20 == 0) spawnParticles();

        for (PacketStand stand : model.getStands()) {
            Location correctLocation = BlockUtils.getCorrectLocation(null, data.type(), location, stand.getSettings());
            stand.teleport(player, correctLocation);
        }
    }

    @Override
    public void cancel() throws IllegalStateException {
        super.cancel();
        model.kill();
        customizations.clear();
        plugin.getVehicleManager().getPreviews().remove(player.getUniqueId());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR); // Clear message.
    }

    private void spawnParticles() {
        Pair<Location, BoundingBox> pair = getBox();

        Location at = pair.getKey();
        BoundingBox box = pair.getValue();

        double y = box.getHeight() / 2;
        player.getWorld().spawnParticle(
                Particle.FIREWORKS_SPARK,
                at,
                1,
                box.getWidthX() / 4,
                y,
                box.getWidthZ() / 4,
                0.001d);
    }

    private @NotNull Pair<Location, BoundingBox> getBox() {
        Vector box = Vehicle.VEHICLE_BOX.get(data.type());
        double x = box.getX();
        double y = box.getY();
        double z = box.getZ();

        Location at = (center != null ? center.getLocation() : model.getLocation()).clone().add(0.0d, y, 0.0d);
        return Pair.of(at, BoundingBox.of(at, x, y, z));
    }

    private @NotNull Location getPlayerTargetLocation(@NotNull Player player) {
        Location eyeLocation = player.getEyeLocation();

        Vector direction = eyeLocation.getDirection().multiply(3.75d);
        Location targetLocation = eyeLocation.clone().add(direction);

        BlockFace face = PluginUtils.getFace(targetLocation.getYaw(), false);
        targetLocation.setDirection(PluginUtils.getDirection(face.getOppositeFace()));

        Block block = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (block != null) {
            targetLocation.setY(block.getY() + 1);
            return targetLocation;
        }

        return targetLocation.subtract(0.0d, 0.5d, 0.0d);
    }
}