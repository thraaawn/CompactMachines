package com.robotgryphon.compactmachines.network;

import com.robotgryphon.compactmachines.data.persistent.CompactMachineData;
import com.robotgryphon.compactmachines.data.persistent.MachineConnections;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Dimension;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CMPacketTargets {

    public static final PacketDistributor<Chunk> TRACKING_ROOM = new PacketDistributor<>(
            CMPacketTargets::trackingRoom, NetworkDirection.PLAY_TO_CLIENT);

    private static Consumer<IPacket<?>> trackingRoom(PacketDistributor<Chunk> dist, Supplier<Chunk> supplier) {
        Chunk roomChunk = supplier.get();
        World level = roomChunk.getLevel();

        HashMap<UUID, ServerPlayNetHandler> trackingPlayersGlobal = new HashMap<>();

        if (level instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) level;
            MinecraftServer server = serverWorld.getServer();

            MachineConnections connections = MachineConnections.get(server);
            CompactMachineData machines = CompactMachineData.get(server);

            Collection<Integer> linked = connections.graph.getMachinesFor(roomChunk.getPos());

            for (int machine : linked) {
                machines.getMachineLocation(machine).ifPresent(loc -> {
                    Optional<ServerWorld> machineWorld = loc.getWorld(server);
                    BlockPos machineWorldLocation = loc.getBlockPosition();
                    ChunkPos machineWorldChunk = new ChunkPos(machineWorldLocation);

                    machineWorld.ifPresent(mw -> {
                        mw.getChunkSource().chunkMap.getPlayers(machineWorldChunk, false).forEach(player -> {
                            if (!trackingPlayersGlobal.containsKey(player.getUUID()))
                                trackingPlayersGlobal.put(player.getUUID(), player.connection);
                        });
                    });
                });
            }
        }

        return pack -> trackingPlayersGlobal.values().forEach(conn -> conn.send(pack));
    }
}
