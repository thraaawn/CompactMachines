package com.robotgryphon.compactmachines.tests.codec;

import com.mojang.serialization.DataResult;
import com.robotgryphon.compactmachines.data.codec.CodecExtensions;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.math.vector.Vector3d;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CodecTests {

    @Test
    void canSerializeVector3d() {
        Vector3d expected = new Vector3d(1.25d, 2.50d, 3.75d);

        DataResult<INBT> nbtResult = CodecExtensions.VECTOR3D.encodeStart(NBTDynamicOps.INSTANCE, expected);
        nbtResult.resultOrPartial(Assertions::fail)
                .ifPresent(nbt -> {
                    ListNBT list = (ListNBT) nbt;

                    Assertions.assertEquals(expected.x, list.getDouble(0));
                    Assertions.assertEquals(expected.y, list.getDouble(1));
                    Assertions.assertEquals(expected.z, list.getDouble(2));
                });
    }

    @Test
    void canSerializeMachineSize() {
        DataResult<INBT> result = EnumMachineSize.CODEC.encodeStart(NBTDynamicOps.INSTANCE, EnumMachineSize.LARGE);

        result.resultOrPartial(Assertions::fail)
                .ifPresent(nbt -> {
                    Assertions.assertEquals(StringNBT.TYPE, nbt.getType());

                    StringNBT string = (StringNBT) nbt;
                    Assertions.assertNotNull(string);
                    Assertions.assertEquals(EnumMachineSize.LARGE.getSerializedName(), string.getAsString());
                });
    }
}
