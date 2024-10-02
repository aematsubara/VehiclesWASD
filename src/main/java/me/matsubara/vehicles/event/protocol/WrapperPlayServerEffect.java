package me.matsubara.vehicles.event.protocol;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class WrapperPlayServerEffect extends PacketWrapper<WrapperPlayServerEffect> {

    private int type;
    private Vector3i pos;
    private int data;
    private boolean globalEvent;

    public WrapperPlayServerEffect(int type, Vector3i pos, int data, boolean globalEvent) {
        super(PacketType.Play.Server.EFFECT);
        this.type = type;
        this.pos = pos;
        this.data = data;
        this.globalEvent = globalEvent;
    }

    @Override
    public void read() {
        type = readInt();
        pos = readBlockPosition();
        data = readInt();
        globalEvent = readBoolean();
    }

    @Override
    public void write() {
        writeInt(type);
        writeBlockPosition(pos);
        writeInt(data);
        writeBoolean(globalEvent);
    }

    @Override
    public void copy(@NotNull WrapperPlayServerEffect wrapper) {
        type = wrapper.type;
        pos = wrapper.pos;
        data = wrapper.data;
        globalEvent = wrapper.globalEvent;
    }
}