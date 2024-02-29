package me.matsubara.vehicles.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
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

public class InputManager extends PacketAdapter implements Listener {

    private final Map<UUID, PlayerInput> inputs = new ConcurrentHashMap<>();

    public InputManager(VehiclesPlugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.STEER_VEHICLE);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    public PlayerInput getInput(UUID uuid) {
        return uuid != null ? inputs.getOrDefault(uuid, PlayerInput.ZERO) : PlayerInput.ZERO;
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent event) {
        Player player = event.getPlayer();

        StructureModifier<Float> floats = event.getPacket().getFloat();
        float sideways = floats.readSafely(0);
        float forward = floats.readSafely(1);

        StructureModifier<Boolean> booleans = event.getPacket().getBooleans();
        boolean jump = booleans.readSafely(0);
        boolean dismount = booleans.readSafely(1);

        PlayerInput input = new PlayerInput(sideways, forward, jump, dismount);
        inputs.put(player.getUniqueId(), input);
    }

    @EventHandler
    public void onEntityDismount(@NotNull EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) inputs.remove(player.getUniqueId());
    }
}