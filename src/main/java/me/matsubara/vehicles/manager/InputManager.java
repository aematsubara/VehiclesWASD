package me.matsubara.vehicles.manager;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InputManager extends SimplePacketListenerAbstract implements Listener {

    private final Map<UUID, PlayerInput> inputs = new ConcurrentHashMap<>();

    public InputManager(@NotNull VehiclesPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public PlayerInput getInput(@Nullable Player player) {
        return player != null ? inputs.getOrDefault(player.getUniqueId(), PlayerInput.ZERO) : PlayerInput.ZERO;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        PacketType.Play.Client type = event.getPacketType();

        if (type != PacketType.Play.Client.STEER_VEHICLE
                && (!XReflection.supports(21, 2) || type != PacketType.Play.Client.PLAYER_INPUT)) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        PlayerInput input;
        if (type == PacketType.Play.Client.STEER_VEHICLE) {
            WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);
            input = new PlayerInput(
                    wrapper.getSideways(),
                    wrapper.getForward(),
                    wrapper.isJump(),
                    wrapper.isUnmount(),
                    false);
        } else {
            WrapperPlayClientPlayerInput wrapper = new WrapperPlayClientPlayerInput(event);
            input = new PlayerInput(
                    wrapper.isLeft() ? 0.98f : wrapper.isRight() ? -0.98f : 0.0f,
                    wrapper.isForward() ? 0.98f : wrapper.isBackward() ? -0.98f : 0.0f,
                    wrapper.isJump(),
                    wrapper.isShift(),
                    wrapper.isSprint());
        }

        inputs.put(player.getUniqueId(), input);
    }

    @EventHandler // We can keep using this event if we stay in 1.20.1.
    public void onEntityDismount(@NotNull EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) inputs.remove(player.getUniqueId());
    }
}