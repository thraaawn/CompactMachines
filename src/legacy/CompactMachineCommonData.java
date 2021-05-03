package com.robotgryphon.compactmachines.data.legacy;

import com.robotgryphon.compactmachines.data.codec.NbtListCollector;
import com.robotgryphon.compactmachines.data.player.CompactMachinePlayerData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Deprecated
public class CompactMachineCommonData {
    protected Map<Integer, CompactMachinePlayerData> playerData;

    private static CompactMachineCommonData INSTANCE = new CompactMachineCommonData();

    protected CompactMachineCommonData() {
        this.playerData = new HashMap<>();
    }

    @Deprecated
    public static CompactMachineCommonData getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CompactMachineCommonData();
        }

        return INSTANCE;
    }

    public static CompoundNBT serializePlayerData(CompactMachineCommonData shared, CompoundNBT nbt) {
        ListNBT playerList = shared.playerData.values()
                .stream()
                .filter(CompactMachinePlayerData::hasPlayers)
                .map(CompactMachinePlayerData::serializeNBT)
                .collect(NbtListCollector.toNbtList());

        if (!playerList.isEmpty())
            nbt.put("players", playerList);

        return nbt;
    }

    public CompoundNBT serializeNBT(CompoundNBT compound) {
        compound = serializePlayerData(this, compound);
        return compound;
    }

    public void deserializeNBT(CompoundNBT nbt) {

    }

}
