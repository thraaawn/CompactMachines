package com.robotgryphon.compactmachines.tests.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.robotgryphon.compactmachines.data.persistent.CompactMachineData;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.tests.util.FileHelper;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@DisplayName("External Machine Data")
public class MachineExternalDataTests {
    private final Path EXTERNAL = Paths.get("scenario", "single-machine-player-inside", "machines_external.dat");

    Codec<List<CompactMachineData.MachineData>> c = CompactMachineData.MachineData.CODEC.listOf()
            .fieldOf("locations")
            .stable()
            .codec();

    @Test
    @DisplayName("Loads Single Machine Data")
    void canLoadSingleMachineData() throws IOException {
        // The external point is overworld @ 8x4x8 (it was made in a default void superflat)
        DimensionalPosition OUTSIDE = new DimensionalPosition(
                RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("overworld")),
                new BlockPos(8,4,8)
        );

        CompoundNBT nbt = FileHelper.INSTANCE.getNbtFromFile(EXTERNAL.toString());
        CompoundNBT data = nbt.getCompound("data");
        DataResult<List<CompactMachineData.MachineData>> result = c.parse(NBTDynamicOps.INSTANCE, data);

        Assertions.assertFalse(data.isEmpty());

        Optional<List<CompactMachineData.MachineData>> res = result.result();
        Assertions.assertTrue(res.isPresent());

        res.ifPresent(list -> {
            Assertions.assertEquals(1, list.size());

            CompactMachineData.MachineData extern = list.get(0);
            Assertions.assertEquals(OUTSIDE, extern.location);
        });
    }
}
