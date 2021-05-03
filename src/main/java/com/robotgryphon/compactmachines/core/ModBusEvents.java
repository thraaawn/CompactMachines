package com.robotgryphon.compactmachines.core;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.compat.theoneprobe.TheOneProbeCompat;
import com.robotgryphon.compactmachines.network.NetworkHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;

@Mod.EventBusSubscriber(modid = CompactMachines.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBusEvents {

    @SubscribeEvent
    public static void setup(final FMLCommonSetupEvent event) {
        CompactMachines.LOGGER.trace("Initializing network handler.");
        NetworkHandler.initialize();
    }

    @SubscribeEvent
    public static void enqueueIMC(final InterModEnqueueEvent event) {
        CompactMachines.LOGGER.trace("Sending IMC setup to TOP and other mods.");
        if (ModList.get().isLoaded("theoneprobe"))
            TheOneProbeCompat.sendIMC();
    }
}
