package me.matsubara.vehicles.listener.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAdvancementTab;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.matsubara.vehicles.VehiclesPlugin;
import me.matsubara.vehicles.gui.VehicleGUI;
import me.matsubara.vehicles.vehicle.Vehicle;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdvancementTab extends SimplePacketListenerAbstract {

    private final VehiclesPlugin plugin;
    private final ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();

    private static final Component EMPTY = Component.text().build();

    public AdvancementTab(VehiclesPlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ADVANCEMENT_TAB) return;

        WrapperPlayClientAdvancementTab wrapper = new WrapperPlayClientAdvancementTab(event);
        if (wrapper.getAction() != WrapperPlayClientAdvancementTab.Action.OPENED_TAB) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Vehicle vehicle = plugin.getVehicleManager().getVehicleByEntity(player);
        if (vehicle == null || !vehicle.isOwner(player)) return;

        Object channel = SpigotReflectionUtil.getChannel(player);

        // Close the advancement menu.
        manager.sendPacket(channel, new WrapperPlayServerOpenWindow(0, 0, EMPTY));
        manager.sendPacket(channel, new WrapperPlayServerCloseWindow(0));

        plugin.getServer().getScheduler().runTask(plugin, () -> new VehicleGUI(plugin, player, vehicle));
    }
}
