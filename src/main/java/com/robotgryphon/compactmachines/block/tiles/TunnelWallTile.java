package com.robotgryphon.compactmachines.block.tiles;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.tunnels.ICapableTunnel;
import com.robotgryphon.compactmachines.api.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.block.walls.TunnelWallBlock;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.persistent.CompactMachineData;
import com.robotgryphon.compactmachines.data.persistent.MachineConnections;
import com.robotgryphon.compactmachines.network.NetworkHandler;
import com.robotgryphon.compactmachines.network.TunnelAddedPacket;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Optional;

public class TunnelWallTile extends TileEntity {

    private int connectedMachine;
    private ResourceLocation tunnelType;

    public TunnelWallTile() {
        super(Registration.TUNNEL_WALL_TILE.get());
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);

        if (nbt.contains("tunnel_type")) {
            ResourceLocation type = new ResourceLocation(nbt.getString("tunnel_type"));
            this.tunnelType = type;
        }

        if (nbt.contains("machine")) {
            this.connectedMachine = nbt.getInt("machine");
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        compound = super.save(compound);
        compound.putString("tunnel_type", tunnelType.toString());
        compound.putInt("machine", connectedMachine);
        return compound;
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT nbt = super.getUpdateTag();
        nbt.putString("tunnel_type", tunnelType.toString());
        nbt.putInt("machine", connectedMachine);
        return nbt;
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        super.handleUpdateTag(state, tag);

        if (tag.contains("tunnel_type")) {
            this.tunnelType = new ResourceLocation(tag.getString("tunnel_type"));
        }

        if (tag.contains("machine")) {
            this.connectedMachine = tag.getInt("machine");
        }
    }

    public Optional<DimensionalPosition> getConnectedPosition() {
        if (level == null || level.isClientSide())
            return Optional.empty();

        ServerWorld serverWorld = (ServerWorld) level;
        MinecraftServer serv = serverWorld.getServer();

        MachineConnections connections = MachineConnections.get(serv);
        CompactMachineData extern = CompactMachineData.get(serv);
        if (connections == null || extern == null)
            return Optional.empty();

        if (this.connectedMachine <= 0) {
            Optional<Integer> mid = tryFindExternalMachineByChunkPos(connections);

            // Map the results - either it found an ID and we can map, or it found nothing
            return mid.map(i -> {
                this.connectedMachine = i;
                Optional<DimensionalPosition> pos = extern.getMachineLocation(i);
                return pos.map(p -> {
                    BlockPos bumped = p.getBlockPosition().relative(getConnectedSide(), 1);
                    return new DimensionalPosition(p.getDimension(), bumped);
                }).orElse(null);
            });
        }

        Optional<DimensionalPosition> pos = extern.getMachineLocation(this.connectedMachine);
        return pos.map(p -> {
            BlockPos bumped = p.getBlockPosition().relative(getConnectedSide(), 1);
            DimensionalPosition bdp = new DimensionalPosition(p.getDimension(), bumped);
            return bdp;
        });
    }

    private Optional<Integer> tryFindExternalMachineByChunkPos(MachineConnections connections) {
        ChunkPos thisMachineChunk = new ChunkPos(worldPosition);
        Collection<Integer> externalMachineIDs = connections.graph.getMachinesFor(thisMachineChunk);

        // This shouldn't happen - there should always be at least one machine attached externally
        // If this DOES happen, it's probably a migration failure or the block was destroyed without notification
        if (externalMachineIDs.isEmpty()) {
            CompactMachines.LOGGER.warn("Warning: Tunnel applied to a machine but no external machine data found.");
            CompactMachines.LOGGER.warn("Please validate the tunnel at: " + worldPosition.toShortString());
            return Optional.empty();
        }

        int first = externalMachineIDs.stream().findFirst().orElse(-1);
        // sanity - makes compiler happier, we already did a check above for empty state
        if (first == -1) return Optional.empty();

        // In theory, we can re-attach the tunnel to the first found external machine, if the saved data
        // does not actually contain an attached external id
        return Optional.of(first);
    }

    /**
     * Gets the side the tunnel is placed on (the wall inside the machine)
     *
     * @return
     */
    public Direction getTunnelSide() {
        BlockState state = getBlockState();
        return state.getValue(TunnelWallBlock.TUNNEL_SIDE);
    }

    /**
     * Gets the side the tunnel connects to externally (the machine side)
     *
     * @return
     */
    public Direction getConnectedSide() {
        BlockState blockState = getBlockState();
        return blockState.getValue(TunnelWallBlock.CONNECTED_SIDE);
    }

    public Optional<ResourceLocation> getTunnelDefinitionId() {
        return Optional.ofNullable(this.tunnelType);
    }

    public Optional<TunnelDefinition> getTunnelDefinition() {
        if (tunnelType == null)
            return Optional.empty();

        TunnelDefinition definition = GameRegistry
                .findRegistry(TunnelDefinition.class)
                .getValue(tunnelType);

        return Optional.ofNullable(definition);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap) {
        return LazyOptional.empty();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        Optional<TunnelDefinition> tunnelDef = getTunnelDefinition();

        // If we don't have a definition for the tunnel, skip
        if (!tunnelDef.isPresent())
            return super.getCapability(cap, side);

        // loop through tunnel definition for capabilities
        TunnelDefinition definition = tunnelDef.get();
        if (definition instanceof ICapableTunnel) {
            if (!level.isClientSide) {
                ServerWorld sw = (ServerWorld) level;
                return ((ICapableTunnel) definition).getExternalCapability(sw, worldPosition, cap, side);
            }
        }

        return super.getCapability(cap, side);
    }

    public void setTunnelType(ResourceLocation registryName) {
        this.tunnelType = registryName;

        if (level != null && !level.isClientSide()) {
            setChanged();

            TunnelAddedPacket pkt = new TunnelAddedPacket(worldPosition, registryName);

            Chunk chunkAt = level.getChunkAt(worldPosition);
            NetworkHandler.MAIN_CHANNEL
                    .send(PacketDistributor.TRACKING_CHUNK.with(() -> chunkAt), pkt);
        }
    }
}
