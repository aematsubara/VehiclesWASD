package me.matsubara.vehicles.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.data.PlayerInput;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
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

    public PlayerInput getInput(UUID uuid) {
        return uuid != null ? inputs.getOrDefault(uuid, PlayerInput.ZERO) : PlayerInput.ZERO;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.STEER_VEHICLE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);

        float sideways = wrapper.getSideways();
        float forward = wrapper.getForward();

        boolean jump = wrapper.isJump();
        boolean dismount = wrapper.isUnmount();

        PlayerInput input = new PlayerInput(sideways, forward, jump, dismount);
        inputs.put(player.getUniqueId(), input);
    }

    @SuppressWarnings("UnstableApiUsage")  // We can keep using this event if we stay in 1.20.4.
    @EventHandler
    public void onEntityDismount(@SuppressWarnings("deprecation") @NotNull EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) inputs.remove(player.getUniqueId());
    }
}