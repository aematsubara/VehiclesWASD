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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.google.common.base.Strings;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.vehicles.manager.StandManager;
import me.matsubara.vehicles.model.Model;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Getter
public final class PacketStand {

    private final ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
    private final Model model;
    private final int id;
    private final UUID uniqueId;
    private final StandSettings settings;
    private @Setter Location location;
    private List<EntityData> data;
    private boolean destroyed;

    private static final float RADIANS_TO_DEGREES = 57.29577951308232f;

    public PacketStand(@NotNull Model model, @NotNull Location location, StandSettings settings) {
        this.model = model;
        this.id = SpigotReflectionUtil.generateEntityId();
        this.uniqueId = UUID.randomUUID();
        this.settings = settings;
        this.location = location;
    }

    public void spawn() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawn(player);
        }
    }

    public void spawn(@NotNull Player player) {
        spawn(player, false);
    }

    public void spawn(@NotNull Player player, boolean ignore) {
        if (destroyed) return;

        StandManager manager = model.getPlugin().getStandManager();
        if (!ignore && !manager.isInRange(location, player.getLocation())) return;

        model.getIgnored().remove(player.getUniqueId());


        getModel().getPlugin().getThreadPool().execute(() -> {
            sendSpawn(player);
            sendLocation(player);
            sendMetadata(player);
            sendEquipment(player);
        });
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
        this.location = location;
        sendLocation();
    }

    public void teleport(Player player, Location location) {
        this.location = location;
        sendLocation(player);
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

    private WrapperPlayServerDestroyEntities destroyEntityPacket = null;
    private @NotNull WrapperPlayServerDestroyEntities createDestroyEntitiesPacket() {
        if (this.destroyEntityPacket == null) {
            this.destroyEntityPacket = new WrapperPlayServerDestroyEntities(id);
        }
        return this.destroyEntityPacket;
    }

    // This method should only be called when removing the vehicle.
    public void destroy() {
        destroyed = true;
        sendPacket(createDestroyEntitiesPacket());
    }

    public void destroy(Player player) {
        sendPacket(player, createDestroyEntitiesPacket());
        model.getIgnored().add(player.getUniqueId());
    }

    private void sendPacket(PacketWrapper<?> wrapper) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendPacket(player, wrapper);
        }
    }

    private void sendPacket(Player player, PacketWrapper<?> wrapper) {
        // We only need to know if the player is ignored when we send a spawn packet,
        // but the checking is done in StandManager.

        Object channel = SpigotReflectionUtil.getChannel(player);
        manager.sendPacketSilently(channel, wrapper);
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
