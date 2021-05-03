package com.robotgryphon.compactmachines.tests.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ExampleTest {

    @Test
    @Tag("minecraft")
    void CanDoBasicTest() {
        MinecraftServer serv = ServerLifecycleHooks.getCurrentServer();
        // SavedMachineData sd = SavedMachineData.getInstance(serv);
        // SavedMachineDataMigrator.migrate(serv);
    }


}
