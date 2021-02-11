package org.dave.compactmachines3.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class MessageRequestMachineAction implements IMessage {
    int id;
    Action action;

    public MessageRequestMachineAction(int id, Action action) {
        this.id = id;
        this.action = action;
    }

    public MessageRequestMachineAction() {
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.id = buf.readInt();
        this.action = Action.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.id);
        buf.writeInt(this.action.ordinal());
    }

    public enum Action {
        REFRESH,
        GIVE_ITEM,
        TELEPORT_INSIDE,
        TELEPORT_OUTSIDE,
        TOGGLE_LOCKED,
        TRY_TO_ENTER,
        RESET_SPAWNPOINT
    }
}
