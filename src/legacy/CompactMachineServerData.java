package com.robotgryphon.compactmachines.data.legacy;

import com.robotgryphon.compactmachines.data.codec.NbtListCollector;
import com.robotgryphon.compactmachines.data.player.CompactMachinePlayerData;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Deprecated
public class CompactMachineServerData extends CompactMachineCommonData {

    private Map<Integer, CompactMachineRegistrationData> machineData;

    CompactMachineServerData() {
        super();
        this.machineData = new HashMap<>();
    }

    public static CompactMachineServerData fromNbt(CompoundNBT nbt) {
        CompactMachineServerData d = new CompactMachineServerData();
        d.deserializeNBT(nbt);
        return d;
    }

    @Override
    public CompoundNBT serializeNBT(CompoundNBT nbt) {
        nbt = super.serializeNBT(nbt);

        ListNBT machineList = machineData.values()
                .stream()
                .map(CompactMachineRegistrationData::serializeNBT)
                .collect(NbtListCollector.toNbtList());

        nbt.put("machines", machineList);

        return nbt;
    }


    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        super.deserializeNBT(nbt);

        if (nbt.contains("machines")) {
            ListNBT machines = nbt.getList("machines", Constants.NBT.TAG_COMPOUND);
            machines.forEach(data -> {
                CompactMachineRegistrationData md = CompactMachineRegistrationData.fromNBT(data);
                machineData.put(md.getId(), md);
            });
        }
    }

    public int getNextMachineId() {
        return machineData.size() + 1;
    }

    public boolean registerMachine(int newID, CompactMachineRegistrationData compactMachineData) {
        if (machineData.containsKey(newID))
            return false;

        this.machineData.put(newID, compactMachineData);
        //  TODO this.playerData.put(newID, new CompactMachinePlayerData(newID));
        return true;
    }


    public Stream<AxisAlignedBB> getAllMachineBounds() {
        return machineData.values().stream()
                .map(mach -> new AxisAlignedBB(mach.getCenter(), mach.getCenter())
                        .inflate(mach.getSize().getInternalSize()));
    }

    public Stream<CompactMachineRegistrationData> getMachines() {
        return machineData.values().stream();
    }

    public Optional<CompactMachineRegistrationData> getMachineContainingPosition(Vector3d position) {
        return getMachines()
                .filter(machine -> {
                    BlockPos center = machine.getCenter();
                    AxisAlignedBB bounds = new AxisAlignedBB(center, center)
                            .inflate(machine.getSize().getInternalSize());

                    return bounds.contains(position);
                })
                .findFirst();
    }

    public Optional<CompactMachineRegistrationData> getMachineContainingPosition(BlockPos position) {
        AxisAlignedBB possibleCenters = new AxisAlignedBB(position, position).inflate(EnumMachineSize.maximum().getInternalSize());

        return getMachines()
                .filter(machine -> {
                    BlockPos center = machine.getCenter();
                    Vector3d center3d = new Vector3d(center.getX(), center.getY(), center.getZ());
                    return possibleCenters.contains(center3d);
                })
                .findFirst();
    }

    public void updateMachineData(CompactMachineRegistrationData d) {
        int id = d.getId();
        if (!machineData.containsKey(id))
            return;

        machineData.replace(id, d);
    }

    public Optional<CompactMachineRegistrationData> getMachineData(int machineId) {
        if (!machineData.containsKey(machineId))
            return Optional.empty();

        return Optional.ofNullable(machineData.get(machineId));
    }

    public void removeMachine(int machineId) {
        if(!machineData.containsKey(machineId))
            return;

        machineData.remove(machineId);
    }

    public void removeMachine(CompactMachineRegistrationData mach) {
        removeMachine(mach.getId());
    }
}
