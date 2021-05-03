package com.robotgryphon.compactmachines.data.persistent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.codec.CodecExtensions;
import com.robotgryphon.compactmachines.data.codec.NbtListCollector;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.util.MathUtil;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.naming.OperationNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CompactRoomData extends WorldSavedData {
    public static final String DATA_NAME = CompactMachines.MOD_ID + "_rooms";

    private Map<ChunkPos, RoomData> machineData;

    public CompactRoomData() {
        super(DATA_NAME);
        machineData = new HashMap<>();
    }

    @Nullable
    public static CompactRoomData get(MinecraftServer server) {
        ServerWorld compactWorld = server.getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.error("No compact dimension found. Report this.");
            return null;
        }

        DimensionSavedDataManager sd = compactWorld.getDataStorage();
        return sd.computeIfAbsent(CompactRoomData::new, DATA_NAME);
    }

    @Override
    public void load(CompoundNBT nbt) {
        if (nbt.contains("machines")) {
            ListNBT machines = nbt.getList("machines", Constants.NBT.TAG_COMPOUND);
            machines.forEach(machNbt -> {
                DataResult<RoomData> result =
                        RoomData.CODEC.parse(NBTDynamicOps.INSTANCE, machNbt);

                result
                        .resultOrPartial((err) -> CompactMachines.LOGGER.error("Error loading machine data from file: {}", err))
                        .ifPresent(imd -> {
                            ChunkPos chunk = new ChunkPos(imd.getCenter());
                            this.machineData.put(chunk, imd);
                        });
            });
        }
    }

    @Override
    @Nonnull
    public CompoundNBT save(@Nonnull CompoundNBT nbt) {
        if (!machineData.isEmpty()) {
            ListNBT collect = machineData.values()
                    .stream()
                    .map(data -> {
                        DataResult<INBT> n = RoomData.CODEC.encodeStart(NBTDynamicOps.INSTANCE, data);
                        return n.result();
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(NbtListCollector.toNbtList());

            nbt.put("machines", collect);
        }

        return nbt;
    }

    public boolean isRegistered(ChunkPos chunkPos) {
        return machineData.containsKey(chunkPos);
    }

    private void register(ChunkPos pos, RoomData data) throws OperationNotSupportedException {
        if (isRegistered(pos))
            throw new OperationNotSupportedException("Machine already registered.");

        machineData.put(pos, data);
        setDirty();
    }

    @Nullable
    public DimensionalPosition getSpawn(ChunkPos roomChunk) {
        RoomData roomData = machineData.get(roomChunk);
        if (roomData == null)
            return null;

        return new DimensionalPosition(
                Registration.COMPACT_DIMENSION,
                roomData.getSpawn()
        );
    }

    public int getNextId() {
        return this.machineData.size() + 1;
    }

    public void setSpawn(ChunkPos roomChunk, Vector3d position) {
        if (!machineData.containsKey(roomChunk))
            return;

        RoomData roomData = machineData.get(roomChunk);
        roomData.setSpawn(position);

        setDirty();
    }

    public Optional<AxisAlignedBB> getInnerBounds(ChunkPos roomChunk) {
        if (!machineData.containsKey(roomChunk))
            return Optional.empty();

        AxisAlignedBB bounds = machineData.get(roomChunk).getMachineBounds();
        return Optional.of(bounds);
    }

    public NewRoomRegistration createNew() {
        return new NewRoomRegistration(this);
    }

    public static class NewRoomRegistration {

        private final CompactRoomData storage;
        private Vector3d spawn;
        private ChunkPos chunk = new ChunkPos(0, 0);
        private EnumMachineSize size = EnumMachineSize.TINY;
        private BlockPos center = BlockPos.ZERO;
        private UUID owner;

        public NewRoomRegistration(CompactRoomData storage) {
            this.storage = storage;
        }

        private void recalculateSize() {
            BlockPos centerAtFloor = MathUtil.getCenterWithY(chunk, ServerConfig.MACHINE_FLOOR_Y.get());
            BlockPos centerSized = centerAtFloor.above(size.getInternalSize() / 2);

            this.spawn = new Vector3d(centerAtFloor.getX(), centerAtFloor.getY(), centerAtFloor.getZ());
            this.center = centerSized;
        }

        public NewRoomRegistration owner(UUID owner) {
            this.owner = owner;
            return this;
        }

        public NewRoomRegistration size(EnumMachineSize size) {
            this.size = size;
            recalculateSize();
            return this;
        }

        public NewRoomRegistration spawn(BlockPos spawn) {
            Vector3d spawnTest = new Vector3d(spawn.getX(), spawn.getY(), spawn.getZ());

            // Make sure the spawn is inside the new room bounds
            if(size.getBounds(this.center).contains(spawnTest))
                this.spawn = spawnTest;

            return this;
        }

        public NewRoomRegistration chunk(ChunkPos chunk) {
            this.chunk = chunk;
            recalculateSize();
            return this;
        }

        public void register() throws OperationNotSupportedException {
            RoomData data = new RoomData(owner, center, spawn, size);
            storage.register(chunk, data);
        }
    }

    private static class RoomData {

        public static final Codec<RoomData> CODEC = RecordCodecBuilder.create(i -> i.group(
                CodecExtensions.UUID_CODEC.fieldOf("owner").forGetter(RoomData::getOwner),
                BlockPos.CODEC.fieldOf("center").forGetter(RoomData::getCenter),
                CodecExtensions.VECTOR3D.fieldOf("spawn").forGetter(RoomData::getSpawn),
                EnumMachineSize.CODEC.fieldOf("size").forGetter(RoomData::getSize)
        ).apply(i, RoomData::new));

        private final UUID owner;
        private final BlockPos center;
        private Vector3d spawn;
        private final EnumMachineSize size;

        public RoomData(UUID owner, BlockPos center, Vector3d spawn, EnumMachineSize size) {
            this.owner = owner;
            this.center = center;
            this.spawn = spawn;
            this.size = size;
        }

        private EnumMachineSize getSize() {
            return this.size;
        }

        public UUID getOwner() {
            return this.owner;
        }

        public Vector3d getSpawn() {
            if (this.spawn != null)
                return this.spawn;

            Vector3d newSpawn = new Vector3d(
                    center.getX(),
                    center.getY(),
                    center.getZ()
            );

            double offset = size.getInternalSize() / 2.0d;

            this.spawn = newSpawn.subtract(0, offset, 0);
            return this.spawn;
        }

        public BlockPos getCenter() {
            return this.center;
        }

        public void setSpawn(Vector3d newSpawn) {
            this.spawn = newSpawn;
        }

        public AxisAlignedBB getMachineBounds() {
            return size.getBounds(this.center);
        }
    }
}
