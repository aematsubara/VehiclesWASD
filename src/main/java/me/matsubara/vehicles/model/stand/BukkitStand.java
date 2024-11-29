package me.matsubara.vehicles.model.stand;

import io.papermc.lib.PaperLib;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.model.stand.data.ItemSlot;
import me.matsubara.vehicles.vehicle.Vehicle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

@Getter
public class BukkitStand implements IStand {

    private final VehiclesPlugin plugin;
    private final StandSettings settings;
    private @Getter(AccessLevel.NONE) Location spawnLocation;
    private @Setter ArmorStand stand;

    public BukkitStand(VehiclesPlugin plugin, @NotNull Location location, StandSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.spawnLocation = location;
    }

    @Override
    public void spawn(Player player) {
        if (stand != null && stand.isValid()) return;

        World world = spawnLocation.getWorld();
        if (world == null) return;

        // Copy the location from the previous stand.
        if (stand != null) {
            spawnLocation = stand.getLocation();
            destroy();
        }

        this.stand = world.spawn(spawnLocation, ArmorStand.class, this::initStand);
    }

    @Override
    public void destroy() {
        stand.remove();
    }

    @Override
    public void destroy(Player player) {
        //This method is only used in previews, but for that we use packet stands.
    }

    @Override
    public void teleport(Collection<Player> players, Location to) {
        PaperLib.teleportAsync(stand, to);
    }

    @Override
    public void teleport(Player player, Location to) {
        //This method is only used in previews, but for that we use packet stands.
    }

    @Override
    public void sendMetadata(Collection<Player> players) {
        setSettings(stand);
    }

    @Override
    public void sendEquipment(Collection<Player> players) {
        setEquipment(stand);
    }

    public void setLocation(Location location) {
        //This method is only used in previews, but for that we use packet stands.
    }

    public Location getLocation() {
        return stand.getLocation();
    }

    @Override
    public int getId() {
        return stand.getEntityId();
    }

    private void initStand(@NotNull ArmorStand stand) {
        setSettings(stand);

        // For these armor stands, we don't want the tick (if possible).
        Vehicle.setTick(stand, false);

        Vehicle.lockSlots(stand);
        Vehicle.LISTEN_MODE_IGNORE.accept(plugin, stand);

        setEquipment(stand);
    }

    private void setSettings(@NotNull ArmorStand stand) {
        // Entity settings.
        stand.setVisible(!settings.isInvisible());
        stand.setSmall(settings.isSmall());
        stand.setBasePlate(settings.isBasePlate());
        stand.setArms(settings.isArms());
        stand.setVisualFire(settings.isFire());
        stand.setMarker(settings.isMarker());
        stand.setGlowing(settings.isGlow());
        stand.setCustomName(settings.getCustomName());
        stand.setCustomNameVisible(settings.isCustomNameVisible());
        stand.setGravity(false);
        stand.setCollidable(false);
        stand.setPersistent(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);

        // Entity poses.
        stand.setHeadPose(settings.getHeadPose());
        stand.setBodyPose(settings.getBodyPose());
        stand.setLeftArmPose(settings.getLeftArmPose());
        stand.setRightArmPose(settings.getRightArmPose());
        stand.setLeftLegPose(settings.getLeftLegPose());
        stand.setRightLegPose(settings.getRightLegPose());
    }

    private void setEquipment(@NotNull ArmorStand stand) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) return;

        // Entity equipment.
        for (Map.Entry<ItemSlot, ItemStack> entry : settings.getEquipment().entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null || item.getType().isAir()) continue;

            EquipmentSlot slot = entry.getKey().getSlot();
            equipment.setItem(slot, item);
        }
    }
}