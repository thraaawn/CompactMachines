package com.robotgryphon.compactmachines.data.legacy;

import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds information that can be used to uniquely identify a compact machine.
 * @deprecated Prefer InternalMachineData and CompactMachineInternalData instead.
 */
@Deprecated
public class CompactMachineRegistrationData extends CompactMachineBaseData {

    private BlockPos center;
    private BlockPos spawnPoint;
    private UUID owner;
    private EnumMachineSize size;

    /**
     * Whether or not a player has picked up the machine.
     */
    private boolean inPlayerInventory;
    private UUID playerUUID;
    private DimensionalPosition outsidePosition;

    private CompactMachineRegistrationData() {
        super(0);
    }

    public CompactMachineRegistrationData(int id, BlockPos center, UUID owner, EnumMachineSize size) {
        super(id);
        this.center = center;
        this.owner = owner;
        this.size = size;
    }

    public static CompactMachineRegistrationData fromNBT(INBT machine) {
        if (machine instanceof CompoundNBT) {
            CompactMachineRegistrationData d = new CompactMachineRegistrationData();
            d.deserializeNBT((CompoundNBT) machine);

            return d;
        }

        return null;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = super.serializeNBT();

        CompoundNBT centerNbt = NBTUtil.writeBlockPos(this.center);
        nbt.put("center", centerNbt);

        if(this.spawnPoint != null) {
            CompoundNBT spawnNbt = NBTUtil.writeBlockPos(this.spawnPoint);
            nbt.put("spawn", spawnNbt);
        }

        IntArrayNBT ownerNbt = NBTUtil.createUUID(this.owner);
        nbt.put("owner", ownerNbt);

        if(this.outsidePosition != null) {
            CompoundNBT pos = outsidePosition.serializeNBT();
            nbt.put("outside", pos);
        }

        nbt.putString("size", size.getName());

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        super.deserializeNBT(nbt);

        if (nbt.contains("size")) {
            this.size = EnumMachineSize.getFromSize(nbt.getString("size"));
        }

        if (nbt.contains("owner")) {
            INBT ownerNbt = nbt.get("owner");
            if (ownerNbt != null)
                this.owner = NBTUtil.loadUUID(ownerNbt);
        }

        if (nbt.contains("center")) {
            this.center = NBTUtil.readBlockPos(nbt.getCompound("center"));
        }

        if(nbt.contains("spawn")) {
            this.spawnPoint = NBTUtil.readBlockPos(nbt.getCompound("spawn"));
        }

        if(nbt.contains("outside")) {
            this.outsidePosition = DimensionalPosition.fromNBT(nbt.getCompound("outside"));
        }
    }

    public BlockPos getCenter() {
        return center;
    }

    public Optional<BlockPos> getSpawnPoint() {
        return Optional.ofNullable(this.spawnPoint);
    }

    public UUID getOwner() {
        return owner;
    }

    public EnumMachineSize getSize() {
        return size;
    }

    public void setSpawnPoint(BlockPos position) {
        this.spawnPoint = position;
    }

    /**
     * Gets the position of the machine in-world. (Dimension and Position info)
     * @return
     */
    public DimensionalPosition getOutsidePosition(MinecraftServer server) {
        if(this.inPlayerInventory) {
            ServerPlayerEntity player = server.getPlayerList().getPlayer(playerUUID);

            Vector3d positionVec = player.position();
            RegistryKey<World> dimensionKey = player.level.dimension();

            // Player location in-world
            return new DimensionalPosition(dimensionKey, positionVec);
        } else {
            return this.outsidePosition;
        }
    }

    public boolean isPlacedInWorld() {
        return !this.inPlayerInventory;
    }

    public void setWorldPosition(ServerWorld world, BlockPos pos) {
        this.outsidePosition = new DimensionalPosition(world.dimension(),
                new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * Starts tracking the outside position as a player instead of a block position.
     * @param player
     */
    public void addToPlayerInventory(UUID player) {
        this.playerUUID = player;
        this.inPlayerInventory = true;
        this.outsidePosition = null;
    }

    /**
     * Stops tracking a player as the outside position.
     */
    public void removeFromPlayerInventory() {
        this.playerUUID = null;
        this.inPlayerInventory = false;
    }
}
