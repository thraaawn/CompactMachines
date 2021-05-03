package com.robotgryphon.compactmachines.network;

import com.robotgryphon.compactmachines.CompactMachines;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    private static int index = 0;
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel MAIN_CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CompactMachines.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void initialize() {
        MAIN_CHANNEL.messageBuilder(MachinePlayersChangedPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MachinePlayersChangedPacket::encode)
                .decoder(MachinePlayersChangedPacket::new)
                .consumer(MachinePlayersChangedPacket::handle)
                .add();

        MAIN_CHANNEL.messageBuilder(TunnelAddedPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TunnelAddedPacket::encode)
                .decoder(TunnelAddedPacket::new)
                .consumer(TunnelAddedPacket::handle)
                .add();
    }
}
