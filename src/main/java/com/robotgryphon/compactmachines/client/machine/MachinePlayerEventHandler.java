package com.robotgryphon.compactmachines.client.machine;

import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.network.MachinePlayersChangedPacket;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;

import java.util.UUID;

public class MachinePlayerEventHandler {

    public static void handlePlayerMachineChanged(UUID playerID,
                                                  MachinePlayersChangedPacket.EnumPlayerChangeType changeType,
                                                  DimensionalPosition pos) {
        ClientWorld w = Minecraft.getInstance().level;
        if(w == null)
            return;

        if (w.dimension() != pos.getDimension())
            return;

        if(!w.isLoaded(pos.getBlockPosition()))
            return;

        CompactMachineTile tile = (CompactMachineTile) w.getBlockEntity(pos.getBlockPosition());
        if (tile == null)
            return;

        switch (changeType) {
            case EXITED:
                tile.handlePlayerLeft(playerID);
                break;

            case ENTERED:
                tile.handlePlayerEntered(playerID);
                break;
        }
    }
}
