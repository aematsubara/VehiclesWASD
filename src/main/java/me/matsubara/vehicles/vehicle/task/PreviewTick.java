package me.matsubara.vehicles.vehicle.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.md_5.bungee.api.ChatMessageType;
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

public class PreviewTick extends BukkitRunnable {

    private final VehiclesPlugin plugin;
    private final Player player;
    private final int seconds;
    private final @Getter Model model;
    private final VehicleData data;
    private final PacketStand center;
    private final List<Customization> customizations = new ArrayList<>();

    private int tick;
    private int hue;
    private float yaw;

    private static final float ROTATION_SPEED = 5.0f;

    public PreviewTick(VehiclesPlugin plugin, Player player, @NotNull VehicleData data) {
        this.plugin = plugin;
        this.player = player;
        this.seconds = Config.SHOP_PREVIEW_SECONDS.getValue(int.class);

        VehicleType type = data.type();

        Location targetLocation = getPlayerTargetLocation(player);
        this.model = new Model(plugin, type.toPath(), null, targetLocation);
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

        plugin.getVehicleManager().getPreviews().put(player.getUniqueId(), this);
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
        if (tick == seconds * 20 || !player.isValid()) {
            cancel();
            return;
        }

        Location location = getPlayerTargetLocation(player);
        location.setYaw(yaw = BlockUtils.yaw(yaw + ROTATION_SPEED));

        model.setLocation(location);

        boolean rainbow = Config.SHOP_PREVIEW_RAINBOW_MESSAGE.getValue(Boolean.class);
        if (rainbow || tick % 20 == 0) {
            Component message = Config.SHOP_PREVIEW_MESSAGE.asComponentTranslated().replaceText(builder -> builder.matchLiteral("%remaining%").replacement(String.valueOf(seconds - tick / 20)));

            if (rainbow) message = message.color(TextColor.color(hue / 360.0f, 1.0f, 1.0f));

            player.sendActionBar(message);
            hue += 6;
        }

        if (tick % 20 == 0) spawnParticles();

        for (PacketStand stand : model.getStands()) {
            Location correctLocation = BlockUtils.getCorrectLocation(null, data.type(), location, stand.getSettings());
            stand.teleport(player, correctLocation);
        }

        tick++;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
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