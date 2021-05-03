package com.robotgryphon.compactmachines.data.persistent;

import com.mojang.serialization.DataResult;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.graph.CompactMachineConnectionGraph;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

public class MachineConnections extends WorldSavedData {
    public static final String DATA_NAME = CompactMachines.MOD_ID + "_connections";

    public CompactMachineConnectionGraph graph;

    public MachineConnections() {
        super(DATA_NAME);
        graph = new CompactMachineConnectionGraph();
    }

    public static MachineConnections get(MinecraftServer server) {
        ServerWorld compactWorld = server.getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.error("No compact dimension found. Report this.");
            return null;
        }

        DimensionSavedDataManager sd = compactWorld.getDataStorage();
        return sd.computeIfAbsent(MachineConnections::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        if (nbt.contains("graph")) {
            CompoundNBT graphNbt = nbt.getCompound("graph");
            DataResult<CompactMachineConnectionGraph> graphParseResult = CompactMachineConnectionGraph.CODEC.parse(NBTDynamicOps.INSTANCE, graphNbt);

            graphParseResult
                    .resultOrPartial(CompactMachines.LOGGER::error)
                    .ifPresent(g -> this.graph = g);
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT nbt) {
        if(graph != null) {
            DataResult<INBT> dataResult = CompactMachineConnectionGraph.CODEC.encodeStart(NBTDynamicOps.INSTANCE, graph);
            dataResult
                    .resultOrPartial(CompactMachines.LOGGER::error)
                    .ifPresent(gNbt -> {
                        nbt.put("graph", gNbt);
                    });
        }

        return nbt;
    }
}
