package com.robotgryphon.compactmachines;

import com.robotgryphon.compactmachines.compat.theoneprobe.TheOneProbeCompat;
import com.robotgryphon.compactmachines.config.CommonConfig;
import com.robotgryphon.compactmachines.config.EnableVanillaRecipesConfigCondition;
import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CompactMachines.MOD_ID)
@Mod.EventBusSubscriber(modid = CompactMachines.MOD_ID)
public class CompactMachines {
    public static final String MOD_ID = "compactmachines";

    public static final Logger LOGGER = LogManager.getLogger();

    public static ItemGroup COMPACT_MACHINES_ITEMS = new ItemGroup(MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(Registration.MACHINE_BLOCK_ITEM_NORMAL.get());
        }
    };

    public CompactMachines() {
        // Register blocks and items
        Registration.init();

        ModLoadingContext mlCtx = ModLoadingContext.get();
        mlCtx.registerConfig(ModConfig.Type.COMMON, CommonConfig.CONFIG);
        mlCtx.registerConfig(ModConfig.Type.SERVER, ServerConfig.CONFIG);

        CraftingHelper.register(EnableVanillaRecipesConfigCondition.Serializer.INSTANCE);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.RenderTickEvent e) {
        // lol frames r good
//        ObfuscationReflectionHelper.setPrivateValue(
//                Minecraft.class,
//                Minecraft.getInstance(),
//                -100,
//                "field_71420_M"
//        );
    }
}
