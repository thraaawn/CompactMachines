package com.robotgryphon.compactmachines.data.player;

import com.mojang.serialization.DataResult;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.codec.CodecExtensions;
import com.robotgryphon.compactmachines.data.codec.NbtListCollector;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.util.PlayerUtil;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Holds basic information about players inside a compact machine.
 */
public class CompactMachinePlayerData extends WorldSavedData {

    public static final String DATA_NAME = "players";
    private HashMap<UUID, ChunkPos> internalPlayerLocations;

    /**
     * Holds a mapping of where players entered a machine.
     */
    private HashMap<UUID, DimensionalPosition> externalSpawns;

    protected CompactMachinePlayerData() {
        super(DATA_NAME);
        this.internalPlayerLocations = new HashMap<>(0);
        this.externalSpawns = new HashMap<>(0);
    }

    @Nullable
    public static CompactMachinePlayerData get(MinecraftServer server) {
        ServerWorld compactWorld = server.getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.error("No compact dimension found. Report this.");
            return null;
        }

        DimensionSavedDataManager sd = compactWorld.getDataStorage();
        return sd.computeIfAbsent(CompactMachinePlayerData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        if (nbt.contains("players")) {
            ListNBT players = nbt.getList("players", Constants.NBT.TAG_COMPOUND);
            players.forEach(playerData -> {
                CompoundNBT pd = (CompoundNBT) playerData;
                UUID id = pd.getUUID("id");

                Optional<ChunkPos> chunk = CodecExtensions.CHUNKPOS
                        .parse(NBTDynamicOps.INSTANCE, pd.get("chunk"))
                        .resultOrPartial(CompactMachines.LOGGER::error);

                // If the chunk position was found, load it into the map
                chunk.ifPresent(c -> {
                    this.internalPlayerLocations.put(id, c);
                });
            });
        }

        if (nbt.contains("spawns")) {
            ListNBT spawns = nbt.getList("spawns", Constants.NBT.TAG_COMPOUND);
            spawns.forEach(spawnData -> {

                CompoundNBT spawn = (CompoundNBT) spawnData;
                UUID playerId = spawn.getUUID("id");
                DimensionalPosition pos = DimensionalPosition.fromNBT(spawn.getCompound("spawn"));

                externalSpawns.put(playerId, pos);
            });
        }
    }

    @Override
    @Nonnull
    public CompoundNBT save(CompoundNBT nbt) {
        ListNBT ids = internalPlayerLocations.entrySet()
                .stream()
                .map(u -> {
                    IntArrayNBT intNBTS = NBTUtil.createUUID(u.getKey());
                    CompoundNBT pnbt = new CompoundNBT();
                    pnbt.put("id", intNBTS);

                    DataResult<INBT> r = CodecExtensions.CHUNKPOS.encodeStart(NBTDynamicOps.INSTANCE, u.getValue());
                    r.result().ifPresent(a -> pnbt.put("chunk", a));

                    return pnbt;
                })
                .collect(NbtListCollector.toNbtList());
        nbt.put("players", ids);


        ListNBT spawns = externalSpawns.entrySet()
                .stream()
                .map((pSpawn) -> {
                    CompoundNBT spawn = new CompoundNBT();

                    UUID playerId = pSpawn.getKey();
                    DimensionalPosition pSpawnPos = pSpawn.getValue();
                    spawn.put("id", NBTUtil.createUUID(playerId));
                    spawn.put("spawn", pSpawnPos.serializeNBT());

                    return spawn;
                })
                .collect(NbtListCollector.toNbtList());

        nbt.put("spawns", spawns);

        return nbt;
    }

    public boolean hasPlayers() {
        return !internalPlayerLocations.isEmpty();
    }

    public void addPlayer(UUID playerID, ChunkPos innerChunk) {
        if (!internalPlayerLocations.containsKey(playerID)) {
            internalPlayerLocations.put(playerID, innerChunk);
        }
    }

    public void addPlayer(ServerPlayerEntity serverPlayer, ChunkPos innerChunk) {
        UUID playerUUID = serverPlayer.getGameProfile().getId();
        addPlayer(playerUUID, innerChunk);

        // server support - add spawn to data
        if (!externalSpawns.containsKey(playerUUID)) {
            DimensionalPosition pos = PlayerUtil.getPlayerDimensionalPosition(serverPlayer);
            externalSpawns.put(playerUUID, pos);
        }
    }

    public void removePlayer(UUID playerID) {
        if (internalPlayerLocations.containsKey(playerID)) {
            internalPlayerLocations.remove(playerID);
            externalSpawns.remove(playerID);
        }
    }

    public void removePlayer(ServerPlayerEntity serverPlayer) {
        UUID playerUUID = serverPlayer.getGameProfile().getId();
        removePlayer(playerUUID);
    }

    public Optional<DimensionalPosition> getExternalSpawn(ServerPlayerEntity serverPlayer) {
        UUID playerUUID = serverPlayer.getGameProfile().getId();
        if (!internalPlayerLocations.containsKey(playerUUID))
            return Optional.empty();

        if (!externalSpawns.containsKey(playerUUID))
            return Optional.empty();

        return Optional.of(externalSpawns.get(playerUUID));
    }

    public Set<UUID> getPlayersInside(ChunkPos machinePos) {
        return Collections.emptySet();
    }
}
