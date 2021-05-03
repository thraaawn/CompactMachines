package com.robotgryphon.compactmachines.data.persistent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.codec.CodecExtensions;
import com.robotgryphon.compactmachines.data.codec.NbtListCollector;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Holds information on the external points of a machine, ie the actual machine blocks.
 */
public class CompactMachineData extends WorldSavedData {

    /**
     * File storage name.
     */
    public final static String DATA_NAME = CompactMachines.MOD_ID + "_machines";

    /**
     * Specifies locations of machines in-world, outside of the compact world.
     * This is used for things like spawn lookups, tunnel handling, and forced ejections.
     */
    public Map<Integer, MachineData> data;

    public CompactMachineData() {
        super(DATA_NAME);
        data = new HashMap<>();
    }

    @Nullable
    public static CompactMachineData get(MinecraftServer server) {
        ServerWorld compactWorld = server.getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.error("No compact dimension found. Report this.");
            return null;
        }

        DimensionSavedDataManager sd = compactWorld.getDataStorage();
        return sd.computeIfAbsent(CompactMachineData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        if(nbt.contains("locations")) {
            ListNBT nbtLocations = nbt.getList("locations", Constants.NBT.TAG_COMPOUND);
            nbtLocations.forEach(nbtLoc -> {
                DataResult<MachineData> res = MachineData.CODEC.parse(NBTDynamicOps.INSTANCE, nbtLoc);
                res.resultOrPartial(err -> {
                    CompactMachines.LOGGER.error("Error while processing machine data: " + err);
                }).ifPresent(machineInfo -> this.data.put(machineInfo.machineId, machineInfo));
            });
        }
    }

    @Override
    @Nonnull
    public CompoundNBT save(@Nonnull CompoundNBT nbt) {
        if(!data.isEmpty()) {
            ListNBT nbtLocations = data.values()
                    .stream()
                    .map(entry -> {
                        DataResult<INBT> nbtRes = MachineData.CODEC.encodeStart(NBTDynamicOps.INSTANCE, entry);
                        return nbtRes
                                .resultOrPartial(err -> CompactMachines.LOGGER.error("Error serializing machine data: " + err));

                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(NbtListCollector.toNbtList());

            nbt.put("locations", nbtLocations);
        }

        return nbt;
    }

    public boolean isPlaced(Integer machineId) {
        return data.containsKey(machineId);
    }

    public void setMachineLocation(int machineId, DimensionalPosition position) {
        // TODO - Packet/Event for machine changing external location (tunnels)
        if(data.containsKey(machineId)) {
            data.get(machineId).setLocation(position);
        } else {
            data.put(machineId, new MachineData(machineId, position));
        }

        this.setDirty();
    }

    public Optional<DimensionalPosition> getMachineLocation(int machineId) {
        if(!data.containsKey(machineId))
            return Optional.empty();

        MachineData machineData = this.data.get(machineId);
        return Optional.ofNullable(machineData.location);
    }

    public static class MachineData {
        private final int machineId;
        public DimensionalPosition location;

        public static final Codec<MachineData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("machine").forGetter(MachineData::getMachineId),
                DimensionalPosition.CODEC.fieldOf("location").forGetter(MachineData::getLocation)
        ).apply(i, MachineData::new));

        public MachineData(int machineId, DimensionalPosition location) {
            this.machineId = machineId;
            this.location = location;
        }

        public int getMachineId() {
            return this.machineId;
        }

        public DimensionalPosition getLocation() {
            return this.location;
        }

        public MachineData setLocation(DimensionalPosition position) {
            this.location = position;
            return this;
        }
    }
}
