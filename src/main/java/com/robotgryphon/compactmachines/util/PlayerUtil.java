package com.robotgryphon.compactmachines.util;

import com.mojang.authlib.GameProfile;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.core.Messages;
import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.persistent.MachineConnections;
import com.robotgryphon.compactmachines.data.player.CompactMachinePlayerData;
import com.robotgryphon.compactmachines.data.persistent.CompactMachineData;
import com.robotgryphon.compactmachines.data.persistent.CompactRoomData;
import com.robotgryphon.compactmachines.network.CMPacketTargets;
import com.robotgryphon.compactmachines.network.MachinePlayersChangedPacket;
import com.robotgryphon.compactmachines.network.NetworkHandler;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.naming.OperationNotSupportedException;
import java.util.Optional;
import java.util.UUID;

public abstract class PlayerUtil {
    public static Optional<GameProfile> getProfileByUUID(IWorld world, UUID uuid) {
        PlayerEntity player = world.getPlayerByUUID(uuid);
        if (player == null)
            return Optional.empty();

        GameProfile profile = player.getGameProfile();
        return Optional.of(profile);
    }

    public static DimensionalPosition getPlayerDimensionalPosition(PlayerEntity player) {
        Vector3d pos = player.position();
        RegistryKey<World> dim = player.level.dimension();

        return new DimensionalPosition(dim, pos);
    }

    public static void teleportPlayerIntoMachine(ServerPlayerEntity serverPlayer, BlockPos machinePos, EnumMachineSize size) {
        ServerWorld serverWorld = serverPlayer.getLevel();

        MinecraftServer serv = serverWorld.getServer();
        if (serverWorld.dimension() == Registration.COMPACT_DIMENSION) {
            IFormattableTextComponent msg = TranslationUtil
                    .message(Messages.CANNOT_ENTER_MACHINE)
                    .withStyle(TextFormatting.RED);

            serverPlayer.displayClientMessage(msg, true);
            return;
        }

        ServerWorld compactWorld = serv.getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.warn("Compact dimension not found; player attempted to enter machine.");
            return;
        }

        CompactMachineTile tile = (CompactMachineTile) serverWorld.getBlockEntity(machinePos);
        if (tile == null)
            return;

        if (!tile.mapped()) {
            CompactMachineData machines = CompactMachineData.get(serv);
            CompactRoomData rooms = CompactRoomData.get(serv);
            MachineConnections connections = MachineConnections.get(serv);

            if (machines == null || rooms == null || connections == null) {
                CompactMachines.LOGGER.error("Could not load world saved data while creating new machine and room.");
                return;
            }

            int nextId = rooms.getNextId();
            Vector3i location = MathUtil.getRegionPositionByIndex(nextId);

            int centerY = ServerConfig.MACHINE_FLOOR_Y.get() + (size.getInternalSize() / 2);
            BlockPos newCenter = MathUtil.getCenterWithY(location, centerY);

            // Generate a new machine inside and update the tile
            CompactStructureGenerator.generateCompactStructure(compactWorld, size, newCenter);
            ChunkPos machineChunk = new ChunkPos(newCenter);
            tile.setMachineId(nextId);

            connections.graph.addMachine(nextId);
            connections.graph.addRoom(machineChunk);
            connections.graph.connectMachineToRoom(nextId, machineChunk);
            connections.setDirty();

            machines.setMachineLocation(nextId, new DimensionalPosition(serverWorld.dimension(), machinePos));

            try {
                rooms.createNew()
                        .owner(serverPlayer.getUUID())
                        .size(size)
                        .chunk(machineChunk)
                        .register();
            } catch (OperationNotSupportedException e) {
                CompactMachines.LOGGER.warn(e);
            }
        }

        serv.submitAsync(() -> {
            DimensionalPosition spawn = tile.getSpawn().orElse(null);
            if (spawn == null) {
                CompactMachines.LOGGER.error("Machine " + tile.machineId + " could not load spawn info.");
                return;
            }

            try {
                // Mark the player as inside the machine, set external spawn, and yeet
                addPlayerToMachine(serverPlayer, machinePos);
            } catch (Exception ex) {
                CompactMachines.LOGGER.error(ex);
            }

            Vector3d sp = spawn.getPosition();
            Vector3d sr = spawn.getRotation() != Vector3d.ZERO ?
                    spawn.getRotation() : new Vector3d(serverPlayer.xRot, serverPlayer.yRot, 0);

            serverPlayer.teleportTo(
                    compactWorld,
                    sp.x,
                    sp.y,
                    sp.z,
                    (float) sr.y,
                    (float) sr.x);
        });

        // TODO - Move machine generation to new method
    }

    // TODO - Overhaul this to handle nested machines
    public static void teleportPlayerOutOfMachine(ServerWorld world, ServerPlayerEntity serverPlayer) {

        MinecraftServer serv = world.getServer();
        CompactMachinePlayerData playerData = CompactMachinePlayerData.get(serv);
        if (playerData == null)
            return;

        Optional<DimensionalPosition> externalSpawn = playerData.getExternalSpawn(serverPlayer);
        if (!externalSpawn.isPresent()) {
            serverPlayer.displayClientMessage(
                    new TranslationTextComponent("messages.%s.how_did_you_get_here", CompactMachines.MOD_ID),
                    true
            );

            return;
        }

        ChunkPos currentMachine = new ChunkPos(serverPlayer.blockPosition());

        MachineConnections connections = MachineConnections.get(serv);
        if (connections == null)
            return;

        DimensionalPosition spawnPoint = externalSpawn.get();

        Optional<ServerWorld> outsideWorld = spawnPoint.getWorld(serv);
        outsideWorld.ifPresent(w -> {
            Vector3d worldPos = spawnPoint.getPosition();
            Vector3d entryRot = spawnPoint.getRotation();
            serverPlayer.teleportTo(w, worldPos.x(), worldPos.y(), worldPos.z(), (float) entryRot.y, (float) entryRot.x);

            removePlayerFromMachine(serverPlayer, currentMachine);
        });
    }

    public static void addPlayerToMachine(ServerPlayerEntity serverPlayer, BlockPos machinePos) {
        MinecraftServer serv = serverPlayer.getServer();
        if (serv == null)
            return;

        CompactMachinePlayerData playerData = CompactMachinePlayerData.get(serv);
        if (playerData == null)
            return;

        CompactMachineTile tile = (CompactMachineTile) serverPlayer.getLevel().getBlockEntity(machinePos);
        if (tile == null)
            return;


        tile.getInternalChunkPos().ifPresent(mChunk -> {
            final Chunk chunk = serv.getLevel(Registration.COMPACT_DIMENSION)
                    .getChunk(mChunk.x, mChunk.z);

            playerData.addPlayer(serverPlayer, mChunk);
            playerData.setDirty();

            MachinePlayersChangedPacket p = MachinePlayersChangedPacket.Builder.create(serv)
                    .forMachine(mChunk)
                    .forPlayer(serverPlayer)
                    .enteredFrom(tile.machineId)
                    .build();

            NetworkHandler.MAIN_CHANNEL.send(CMPacketTargets.TRACKING_ROOM.with(() -> chunk), p);
        });
    }

    public static void removePlayerFromMachine(ServerPlayerEntity serverPlayer, ChunkPos roomChunk) {
        MinecraftServer serv = serverPlayer.getServer();

        CompactMachinePlayerData playerData = CompactMachinePlayerData.get(serv);
        if (playerData == null)
            return;

        playerData.removePlayer(serverPlayer);
        playerData.setDirty();

        final Chunk chunk = serv.getLevel(Registration.COMPACT_DIMENSION)
                .getChunk(roomChunk.x, roomChunk.z);

        MachinePlayersChangedPacket p = MachinePlayersChangedPacket.Builder.create(serv)
                .forMachine(roomChunk)
                .forPlayer(serverPlayer)
                .build();

        NetworkHandler.MAIN_CHANNEL.send(CMPacketTargets.TRACKING_ROOM.with(() -> chunk), p);
    }
}
