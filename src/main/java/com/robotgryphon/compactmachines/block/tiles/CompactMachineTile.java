package com.robotgryphon.compactmachines.block.tiles;

import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.CompactMachineCommonData;
import com.robotgryphon.compactmachines.data.CompactMachineServerData;
import com.robotgryphon.compactmachines.data.machines.CompactMachinePlayerData;
import com.robotgryphon.compactmachines.data.machines.CompactMachineRegistrationData;
import com.robotgryphon.compactmachines.reference.Reference;
import com.robotgryphon.compactmachines.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.tunnels.api.ICapableTunnel;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CompactMachineTile extends TileEntity implements ICapabilityProvider, ITickableTileEntity {
    public int machineId = -1;
    private boolean initialized = false;
    public boolean alreadyNotifiedOnTick = false;
    public long nextSpawnTick = 0;

    protected UUID owner;
    protected String schema;
    protected boolean locked = false;
    protected Set<String> playerWhiteList;

    public CompactMachineTile() {
        super(Registration.MACHINE_TILE_ENTITY.get());

        playerWhiteList = new HashSet<>();
    }

    @Override
    public void validate() {
        super.validate();

        if(ServerConfig.MACHINE_CHUNKLOADING.get())
            doChunkload(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();

        if(ServerConfig.MACHINE_CHUNKLOADING.get())
            doChunkload(false);
    }

    @Override
    public void remove() {
        super.remove();

        if(ServerConfig.MACHINE_CHUNKLOADING.get())
            doChunkload(false);
    }

    @Override
    public void read(BlockState state, CompoundNBT nbt) {
        super.read(state, nbt);

        machineId = nbt.getInt("coords");
        // TODO customName = nbt.getString("CustomName");
        if (nbt.contains(Reference.CompactMachines.OWNER_NBT)) {
            owner = nbt.getUniqueId(Reference.CompactMachines.OWNER_NBT);
        } else {
            owner = null;
        }

        nextSpawnTick = nbt.getLong("spawntick");
        if (nbt.contains("schema")) {
            schema = nbt.getString("schema");
        } else {
            schema = null;
        }

        if (nbt.contains("locked")) {
            locked = nbt.getBoolean("locked");
        } else {
            locked = false;
        }

        playerWhiteList = new HashSet<>();
        if (nbt.contains("playerWhiteList")) {
            ListNBT list = nbt.getList("playerWhiteList", Constants.NBT.TAG_STRING);
            list.forEach(nametag -> playerWhiteList.add(nametag.getString()));
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        nbt = super.write(nbt);

        nbt.putInt("coords", machineId);
        // nbt.putString("CustomName", customName.getString());

        if (owner != null) {
            nbt.putUniqueId(Reference.CompactMachines.OWNER_NBT, this.owner);
        }

        nbt.putLong("spawntick", nextSpawnTick);
        if (schema != null) {
            nbt.putString("schema", schema);
        }

        nbt.putBoolean("locked", locked);

        if (playerWhiteList.size() > 0) {
            ListNBT list = new ListNBT();
            playerWhiteList.forEach(player -> {
                StringNBT nameTag = StringNBT.valueOf(player);
                list.add(nameTag);
            });

            nbt.put("playerWhiteList", list);
        }

        return nbt;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap) {
        return super.getCapability(cap);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if(world.isRemote)
            return super.getCapability(cap, side);

        ServerWorld serverWorld = (ServerWorld) world;
        ServerWorld compactWorld = serverWorld.getServer().getWorld(Registration.COMPACT_DIMENSION);
        if(compactWorld == null)
            return LazyOptional.empty();

        Set<BlockPos> tunnelPositions = TunnelHelper.getTunnelsForMachineSide(this.machineId, serverWorld, side);
        if(tunnelPositions.isEmpty())
            return LazyOptional.empty();

        for(BlockPos possibleTunnel : tunnelPositions) {
            TunnelWallTile tile = (TunnelWallTile) compactWorld.getTileEntity(possibleTunnel);
            if(tile == null)
                continue;

            Optional<TunnelDefinition> tunnel = tile.getTunnelDefinition();
            if(!tunnel.isPresent())
                continue;

            TunnelDefinition definition = tunnel.get();
            if(definition instanceof ICapableTunnel) {
                LazyOptional<T> capPoss = ((ICapableTunnel) definition).getInternalCapability(compactWorld, possibleTunnel, cap, side);
                if(capPoss.isPresent())
                    return capPoss;
            }
        }

        return LazyOptional.empty();
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(pos, 1, getUpdateTag());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT base = super.getUpdateTag();
        base.putInt("machine", this.machineId);

        if(world instanceof ServerWorld) {
            Optional<CompactMachinePlayerData> playerData = Optional.empty();
            try {
                playerData = CompactMachineServerData
                        .getInstance(world.getServer())
                        .getPlayerData(machineId);
            } catch (Exception e) {
                e.printStackTrace();
            }

            playerData.ifPresent(data -> {
                CompoundNBT playerNbt = data.serializeNBT();
                base.put("players", playerNbt);
            });

            if(this.owner != null)
                base.putUniqueId("owner", this.owner);
        }

        return base;
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        super.handleUpdateTag(state, tag);

        this.machineId = tag.getInt("machine");
        if(tag.contains("players")) {
            CompoundNBT players = tag.getCompound("players");
            CompactMachinePlayerData playerData = CompactMachinePlayerData.fromNBT(players);

            CompactMachineCommonData.getInstance().updatePlayerData(playerData);
        }

        if(tag.contains("owner"))
            owner = tag.getUniqueId("owner");
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        BlockState state = null;
        if (this.world != null) {
            state = this.world.getBlockState(pos);
        }

        read(state, pkt.getNbtCompound());
    }

    @Override
    public void tick() {

    }

    public Optional<UUID> getOwnerUUID() {
        return Optional.ofNullable(this.owner);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setMachineId(int id) {
        this.machineId = id;
        this.markDirty();
    }

    public Optional<CompactMachineRegistrationData> getMachineData() {
        if(this.machineId == 0)
            return Optional.empty();

        if(world instanceof ServerWorld) {
            return CompactMachineServerData
                    .getInstance(world.getServer())
                    .getMachineData(this.machineId);
        } else {
            return Optional.empty();
        }
    }

    public boolean hasPlayersInside() {
        return CompactMachineCommonData
                .getInstance()
                .getPlayerData(machineId)
                .map(CompactMachinePlayerData::hasPlayers)
                .orElse(false);
    }

    protected void doChunkload(boolean force) {
        if(world.isRemote)
            return;

        getMachineData().ifPresent(data -> {
            ServerWorld compact = this.world.getServer().getWorld(Registration.COMPACT_DIMENSION);
            IChunk machineChunk = compact.getChunk(data.getCenter());
            ChunkPos chunkPos = machineChunk.getPos();
            compact.forceChunk(chunkPos.x, chunkPos.z, force);
        });
    }

    public void doPostPlaced() {
        doChunkload(true);
    }

    /*
     * Chunk-Loading triggers
     */

//    private void initialize() {
//        if (this.getWorld().isRemote) {
//            return;
//        }
//
//        if (!ChunkLoadingMachines.isMachineChunkLoaded(this.coords)) {
//            ChunkLoadingMachines.forceChunk(this.coords);
//        }
//
//    }

//    @Override
//    public void update() {
//        if (!this.initialized && !this.isInvalid() && this.coords != -1) {
//            initialize();
//            this.initialized = true;
//        }
//
//        this.alreadyNotifiedOnTick = false;
//
//        if (nextSpawnTick == 0) {
//            nextSpawnTick = this.getWorld().getTotalWorldTime() + ConfigurationHandler.MachineSettings.spawnRate;
//        }
//
//        if (!this.getWorld().isRemote && this.coords != -1 && isInsideItself()) {
//            if (this.getWorld().getTotalWorldTime() % 20 == 0) {
//                world.playSound(null, getPos(),
//                        SoundEvent.REGISTRY.getObject(new ResourceLocation("entity.wither.spawn")),
//                        SoundCategory.MASTER,
//                        1.0f,
//                        1.0f
//                );
//            }
//        }
//
//        if (!this.getWorld().isRemote && this.coords != -1 && this.getWorld().getTotalWorldTime() > nextSpawnTick) {
//            if (ConfigurationHandler.MachineSettings.allowPeacefulSpawns || ConfigurationHandler.MachineSettings.allowHostileSpawns) {
//                SpawnTools.spawnEntitiesInMachine(coords);
//            }
//
//            nextSpawnTick = this.getWorld().getTotalWorldTime() + ConfigurationHandler.MachineSettings.spawnRate;
//            this.markDirty();
//        }
//
//        /*
//        // Use this once we render in world or use the proxy world to determine client side capabilities.
//        if(!this.getWorld().isRemote && this.getWorld().getTotalWorldTime() % 20 == 0 && this.coords != -1) {
//            PackageHandler.instance.sendToAllAround(new MessageMachineChunk(this.coords), new NetworkRegistry.TargetPoint(this.world.provider.getDimension(), this.getPos().getX(), this.getPos().getY(), this.getPos().getZ(), 32.0f));
//        }
//        */
//    }

//    @Override
//    public void onChunkUnload() {
//        super.onChunkUnload();
//        if (this.getWorld().isRemote) {
//            return;
//        }
//
//        if (ConfigurationHandler.Settings.forceLoadChunks) {
//            return;
//        }
//
//        ChunkLoadingMachines.unforceChunk(this.coords);
//    }
//
//    public boolean isInsideItself() {
//        if (this.getWorld().provider.getDimension() != ConfigurationHandler.Settings.dimensionId) {
//            return false;
//        }
//
//        return StructureTools.getCoordsForPos(this.getPos()) == this.coords;
//    }
//
//    public ItemStack getConnectedPickBlock(EnumFacing facing) {
//        BlockPos insetPos = getMachineWorldInsetPos(facing);
//        if (insetPos == null) {
//            return ItemStack.EMPTY;
//        }
//
//        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
//        IBlockState state = machineWorld.getBlockState(insetPos);
//        return state.getBlock().getItem(machineWorld, insetPos, state);
//    }
//
//    public int getRedstonePowerOutput(EnumFacing facing) {
//        if (this.coords == -1) {
//            return 0;
//        }
//
//        // We don't know the actual power on the client-side, which does not have the worldsaveddatamachines instance
//        if (WorldSavedDataMachines.INSTANCE == null || WorldSavedDataMachines.INSTANCE.redstoneTunnels == null) {
//            return 0;
//        }
//
//        HashMap<EnumFacing, RedstoneTunnelData> tunnelMapping = WorldSavedDataMachines.INSTANCE.redstoneTunnels.get(this.coords);
//        if (tunnelMapping == null) {
//            return 0;
//        }
//
//        RedstoneTunnelData tunnelData = tunnelMapping.get(facing);
//        if (tunnelData == null) {
//            return 0;
//        }
//
//
//        if (!tunnelData.isOutput) {
//            return 0;
//        }
//
//        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
//        if (!(machineWorld.getTileEntity(tunnelData.pos) instanceof TileEntityRedstoneTunnel)) {
//            return 0;
//        }
//
//        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelData.pos, this.getSize().getDimension());
//        BlockPos insetPos = tunnelData.pos.offset(insetDirection);
//        IBlockState insetBlockState = machineWorld.getBlockState(insetPos);
//
//        int power = 0;
//        if (insetBlockState.getBlock() instanceof BlockRedstoneWire) {
//            power = insetBlockState.getValue(BlockRedstoneWire.POWER);
//        } else {
//            power = machineWorld.getRedstonePower(insetPos, insetDirection);
//        }
//
//        return power;
//    }
//
//    @Override
//    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
//        if (isInsideItself()) {
//            return false;
//        }
//
//        if (world.isRemote || facing == null) {
//            if (CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
//                return true;
//            }
//
//            return super.hasCapability(capability, facing);
//        }
//
//        BlockPos tunnelPos = this.getConnectedBlockPosition(facing);
//        if (tunnelPos == null) {
//            return false;
//        }
//
//        World machineWorld = DimensionTools.getServerMachineWorld();
//        if (!(machineWorld.getTileEntity(tunnelPos) instanceof TileEntityTunnel)) {
//            return false;
//        }
//
//        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelPos, this.getSize().getDimension());
//        BlockPos insetPos = tunnelPos.offset(insetDirection);
//
//        TileEntity te = machineWorld.getTileEntity(insetPos);
//        if (te != null && te instanceof ICapabilityProvider && te.hasCapability(capability, insetDirection.getOpposite())) {
//            return true;
//        }
//
//        if (CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
//            return true;
//        }
//
//        return false;
//    }
//
//    @Override
//    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
//        if (isInsideItself()) {
//            return null;
//        }
//
//        if (this.getWorld().isRemote || facing == null) {
//            if (CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
//                return CapabilityNullHandlerRegistry.getNullHandler(capability);
//            }
//
//            return super.getCapability(capability, facing);
//        }
//
//        BlockPos tunnelPos = this.getConnectedBlockPosition(facing);
//        if (tunnelPos == null) {
//            return null;
//        }
//
//        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
//        if (!(machineWorld.getTileEntity(tunnelPos) instanceof TileEntityTunnel)) {
//            return null;
//        }
//
//        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelPos, this.getSize().getDimension());
//        BlockPos insetPos = tunnelPos.offset(insetDirection);
//
//        TileEntity te = machineWorld.getTileEntity(insetPos);
//        if (te instanceof ICapabilityProvider && te.hasCapability(capability, insetDirection.getOpposite())) {
//            return machineWorld.getTileEntity(insetPos).getCapability(capability, insetDirection.getOpposite());
//        }
//
//        if (CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
//            return CapabilityNullHandlerRegistry.getNullHandler(capability);
//        }
//
//        return null;
//    }
}
