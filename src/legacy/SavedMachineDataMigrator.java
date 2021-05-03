package com.robotgryphon.compactmachines.data.legacy;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.legacy.CompactMachineServerData;
import com.robotgryphon.compactmachines.data.legacy.SavedMachineData;
import com.robotgryphon.compactmachines.data.machine.CompactMachineInternalData;
import com.robotgryphon.compactmachines.data.world.ExternalMachineData;
import com.robotgryphon.compactmachines.data.world.InternalMachineData;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class SavedMachineDataMigrator {

    public static void migrate(MinecraftServer server) {
        DimensionSavedDataManager ds = server
                .getLevel(Registration.COMPACT_DIMENSION)
                .getDataStorage();

        SavedMachineData found = ds.get(SavedMachineData::new, SavedMachineData.DATA_NAME);
        if (found == null) {
            CompactMachines.LOGGER.debug("Migration complete; nothing available to migrate.");
            return;
        }

        ExternalMachineData emd = ExternalMachineData.get(server);
        if (emd == null) {
            CompactMachines.LOGGER.error("Could not perform migration; couldn't create the external machine file.");
            return;
        }

        InternalMachineData imd = InternalMachineData.get(server);
        if (imd == null) {
            CompactMachines.LOGGER.error("Could not perform migration; couldn't create the internal machine file.");
            return;
        }

        CompactMachineServerData data = found.getData();
        data.getMachines().forEach(mach -> {
            DimensionalPosition outside = mach.getOutsidePosition(server);
            BlockPos internalCenter = mach.getCenter();
            ChunkPos machineChunk = new ChunkPos(internalCenter);

            UUID owner = mach.getOwner();
            EnumMachineSize size = mach.getSize();
            BlockPos center = mach.getCenter();
            BlockPos spawn = mach.getSpawnPoint().orElse(center);

            int id = mach.getId();
            if (!emd.machineLocations.containsKey(id))
                emd.machineLocations.put(id, outside);

            if (!emd.machineMapping.containsKey(id))
                emd.machineMapping.put(id, machineChunk);

            if (!imd.isRegistered(machineChunk)) {
                try {
                    CompactMachineInternalData d = new CompactMachineInternalData(owner, center, spawn, size);
                    imd.register(machineChunk, d);
                } catch (OperationNotSupportedException e) {
                    e.printStackTrace();
                }
            }
        });

        imd.setDirty();
        emd.setDirty();
    }

    private static boolean doFileDelete(DimensionSavedDataManager ds, SavedMachineData found) {
        Method getDataFile = ObfuscationReflectionHelper.findMethod(
                DimensionSavedDataManager.class,
                "func_215754_a",
                String.class);

        try {
            File i = (File) getDataFile.invoke(ds, found.getId());
            System.out.println(i.getAbsolutePath());

            return i.delete();
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return false;
    }
}
