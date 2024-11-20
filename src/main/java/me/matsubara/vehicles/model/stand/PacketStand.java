package me.matsubara.vehicles.model.stand;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.google.common.base.Strings;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.VehiclesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public final class PacketStand {

    private final ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
    private final VehiclesPlugin plugin;
    private final int id;
    private final UUID uniqueId;
    private final StandSettings settings;
    private @Setter Location location;
    private World world;
    private List<EntityData> data;
    private WrapperPlayServerDestroyEntities destroyEntities;
    private boolean destroyed;

    private static final float RADIANS_TO_DEGREES = 57.29577951308232f;

    public PacketStand(VehiclesPlugin plugin, @NotNull Location location, StandSettings settings) {
        this.plugin = plugin;
        this.id = SpigotReflectionUtil.generateEntityId();
        this.uniqueId = UUID.randomUUID();
        this.settings = settings;
        this.location = location;
        this.world = location.getWorld();
    }

    public void spawn(Location location) {
        // Prevent errors when trying to send packets when the plugin is being disabled.
        if (destroyed || !plugin.isEnabled()) return;

        // Create the packets once and send them to the players.
        PacketWrapper<?> spawn = createSpawnPacket();
        WrapperPlayServerEntityTeleport teleport = createTeleport();
        WrapperPlayServerEntityHeadLook rotation = createRotation();
        WrapperPlayServerEntityMetadata metadata = createMetadataPacket();
        List<WrapperPlayServerEntityEquipment> equipment = createEquipment();

        for (Player player : world.getPlayers()) {
            if (!plugin.getStandManager().isInRange(location, player.getLocation())) continue;

            Object channel = SpigotReflectionUtil.getChannel(player);

            manager.sendPacket(channel, spawn);
            manager.sendPacket(channel, teleport);
            manager.sendPacket(channel, rotation);
            manager.sendPacket(channel, metadata);

            for (WrapperPlayServerEntityEquipment wrapper : equipment) {
                manager.sendPacket(channel, wrapper);
            }
        }
    }

    public void spawn(@NotNull Player player) {
        if (destroyed) return;

        sendSpawn(player);
        sendLocation(player);
        sendMetadata(player);
        sendEquipment(player);
    }

    private @NotNull PacketWrapper<?> createSpawnPacket() {
        PacketWrapper<?> wrapper;
        if (XReflection.MINOR_NUMBER > 18) {
            wrapper = new WrapperPlayServerSpawnEntity(
                    id,
                    uniqueId,
                    EntityTypes.ARMOR_STAND,
                    SpigotConversionUtil.fromBukkitLocation(location),
                    location.getYaw(),
                    0,
                    Vector3d.zero());
        } else {
            wrapper = new WrapperPlayServerSpawnLivingEntity(
                    id,
                    uniqueId,
                    EntityTypes.ARMOR_STAND,
                    SpigotConversionUtil.fromBukkitLocation(location),
                    location.getYaw(),
                    Vector3d.zero(),
                    Collections.emptyList());
        }
        return wrapper;
    }

    private void sendSpawn(Player player) {
        sendPacket(player, createSpawnPacket());
    }

    private @NotNull WrapperPlayServerEntityTeleport createTeleport() {
        return new WrapperPlayServerEntityTeleport(
                id,
                SpigotConversionUtil.fromBukkitLocation(location),
                false);
    }

    private @NotNull WrapperPlayServerEntityHeadLook createRotation() {
        return new WrapperPlayServerEntityHeadLook(id, location.getYaw());
    }

    private void sendLocation() {
        sendPacket(createTeleport());
        sendPacket(createRotation());
    }

    private void sendLocation(Player player) {
        sendPacket(player, createTeleport());
        sendPacket(player, createRotation());
    }

    public void teleport(Location location) {
        if (invalidTeleport(location)) return;

        this.location = location;
        this.world = location.getWorld();

        sendLocation();
    }

    public void teleport(Player player, Location location) {
        if (invalidTeleport(location)) return;

        this.location = location;
        this.world = location.getWorld();

        sendLocation(player);
    }

    private boolean invalidTeleport(@NotNull Location location) {
        World world = location.getWorld();
        return world == null || !Objects.equals(world, this.world);
    }

    private @NotNull List<WrapperPlayServerEntityEquipment> createEquipment() {
        List<WrapperPlayServerEntityEquipment> wrappers = new ArrayList<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.BODY) continue;
            Equipment equipment = new Equipment(slot, settings.getEquipment().get(slot));
            wrappers.add(new WrapperPlayServerEntityEquipment(id, List.of(equipment)));
        }

        return wrappers;
    }

    public void sendEquipment() {
        createEquipment().forEach(this::sendPacket);
    }

    public void sendEquipment(Player player) {
        for (WrapperPlayServerEntityEquipment wrapper : createEquipment()) {
            sendPacket(player, wrapper);
        }
    }

    public @NotNull WrapperPlayServerEntityMetadata createMetadataPacket() {
        List<EntityData> temp = data != null ? data : (data = createEntityData());
        return new WrapperPlayServerEntityMetadata(id, temp);
    }

    public void sendMetadata() {
        data = null;
        sendPacket(createMetadataPacket());
    }

    public void sendMetadata(Player player) {
        sendPacket(player, createMetadataPacket());
    }

    private @NotNull List<EntityData> createEntityData() {
        List<EntityData> data = new ArrayList<>();

        data.add(new EntityData(0, EntityDataTypes.BYTE, (byte)
                ((settings.isFire() ? 0x01 : 0)
                        | (settings.isInvisible() ? 0x20 : 0)
                        | (settings.isGlow() ? 0x40 : 0))));

        String name = Strings.emptyToNull(settings.getCustomName());
        data.add(new EntityData(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT,
                Optional.ofNullable(name != null ? Component.text(name) : null)));

        data.add(new EntityData(3, EntityDataTypes.BOOLEAN, settings.isCustomNameVisible()));

        data.add(new EntityData(15, EntityDataTypes.BYTE, (byte)
                ((settings.isSmall() ? 0x01 : 0)
                        | (settings.isArms() ? 0x04 : 0)
                        | (settings.isBasePlate() ? 0 : 0x08)
                        | (settings.isMarker() ? 0x10 : 0))));

        for (StandSettings.Pose pose : StandSettings.Pose.values()) {
            Vector3f rotation = pose.get(settings).multiply(RADIANS_TO_DEGREES);
            data.add(new EntityData(pose.getIndex(), EntityDataTypes.ROTATION, rotation));
        }

        return data;
    }

    private @NotNull WrapperPlayServerDestroyEntities createDestroyEntitiesPacket() {
        return destroyEntities != null ? destroyEntities : (destroyEntities = new WrapperPlayServerDestroyEntities(id));
    }

    // This method should only be called when removing the vehicle.
    public void destroy() {
        destroyed = true;
        sendPacket(createDestroyEntitiesPacket());
    }

    public void destroy(Player player) {
        sendPacket(player, createDestroyEntitiesPacket());
    }

    private void sendPacket(PacketWrapper<?> wrapper) {
        for (Player player : world.getPlayers()) {
            sendPacket(player, wrapper);
        }
    }

    private void sendPacket(Player player, PacketWrapper<?> wrapper) {
        // Prevent errors when trying to send packets when the plugin is being disabled.
        if (!plugin.isEnabled()) return;

        // We only need to know if the player is ignored when we send a spawn packet,
        // but the checking is done in StandManager.
        Object channel = SpigotReflectionUtil.getChannel(player);
        manager.sendPacket(channel, wrapper);
    }

    @Getter
    public enum ItemSlot {
        MAINHAND(EquipmentSlot.MAIN_HAND, "main-hand"),
        OFFHAND(EquipmentSlot.OFF_HAND, "off-hand"),
        FEET(EquipmentSlot.BOOTS, "boots"),
        LEGS(EquipmentSlot.LEGGINGS, "leggings"),
        CHEST(EquipmentSlot.CHEST_PLATE, "chestplate"),
        HEAD(EquipmentSlot.HELMET, "helmet");

        private final EquipmentSlot slot;
        private final String path;

        ItemSlot(EquipmentSlot slot, String path) {
            this.slot = slot;
            this.path = path;
        }
    }
}