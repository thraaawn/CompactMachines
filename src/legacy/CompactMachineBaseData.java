package com.robotgryphon.compactmachines.data.legacy;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Optional;

/**
 * Holds basic information about a compact machine.
 */
@Deprecated
public abstract class CompactMachineBaseData implements INBTSerializable<CompoundNBT> {

    private int id;

    protected CompactMachineBaseData() {
        this.id = -1;
    }

    protected CompactMachineBaseData(int id) {
        this.id = id;
    }

    protected static Optional<Integer> getIdFromNbt(INBT nbt) {
        if(nbt instanceof CompoundNBT) {
            CompoundNBT cnbt = (CompoundNBT) nbt;
            return cnbt.contains("id") ?
                    Optional.of(cnbt.getInt("id")) :
                    Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();

        nbt.putInt("id", this.id);

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        Optional<Integer> idFromNbt = getIdFromNbt(nbt);
        idFromNbt.ifPresent(i -> this.id = i);
    }

    public int getId() {
        return id;
    }
}
