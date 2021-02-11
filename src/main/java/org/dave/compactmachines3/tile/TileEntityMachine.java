package org.dave.compactmachines3.tile;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.dave.compactmachines3.CompactMachines3;
import org.dave.compactmachines3.api.IRemoteBlockProvider;
import org.dave.compactmachines3.block.BlockMachine;
import org.dave.compactmachines3.integration.CapabilityNullHandlerRegistry;
import org.dave.compactmachines3.misc.ConfigurationHandler;
import org.dave.compactmachines3.misc.Vec3d2f;
import org.dave.compactmachines3.reference.EnumMachineSize;
import org.dave.compactmachines3.world.ChunkLoadingMachines;
import org.dave.compactmachines3.world.WorldSavedDataMachines;
import org.dave.compactmachines3.world.data.RedstoneTunnelData;
import org.dave.compactmachines3.world.tools.DimensionTools;
import org.dave.compactmachines3.world.tools.SpawnTools;
import org.dave.compactmachines3.world.tools.StructureTools;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TileEntityMachine extends TileEntity implements ICapabilityProvider, ITickable, IRemoteBlockProvider {
    public int id = -1;
    private BlockPos roomPos;
    private boolean initialized = false;
    public boolean alreadyNotifiedOnTick = false;
    public long nextSpawnTick = 0;

    protected String customName = "";
    protected UUID owner;
    protected String schema;
    protected boolean locked = false;
    protected Set<String> playerWhiteList;

    public TileEntityMachine() {
        super();
        playerWhiteList = new HashSet<>();
    }

    public EnumMachineSize getSize() {
        return this.getWorld().getBlockState(this.getPos()).getValue(BlockMachine.SIZE);
    }

    /*
     * NBT reading + writing
     */
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        id = compound.getInteger("machineId");
        if (compound.hasKey("coords")) {
            id = compound.getInteger("coords"); // Legacy
            roomPos = new BlockPos(1024 * id, 40, 0);
        } else {
            NBTTagCompound roomPosTag = compound.getCompoundTag("roomPos");
            if (!roomPosTag.isEmpty()) {
                roomPos = NBTUtil.getPosFromTag(roomPosTag);
            }
        }
        customName = compound.getString("CustomName");
        if(compound.hasKey("ownerLeast") && compound.hasKey("ownerMost")) {
            owner = compound.getUniqueId("owner");
        } else {
            owner = null;
        }

        nextSpawnTick = compound.getLong("spawntick");
        if(compound.hasKey("schema")) {
            schema = compound.getString("schema");
        } else {
            schema = null;
        }

        if(compound.hasKey("locked")) {
            locked = compound.getBoolean("locked");
        } else {
            locked = false;
        }

        playerWhiteList = new HashSet<>();
        if(compound.hasKey("playerWhiteList")) {
            NBTTagList list = compound.getTagList("playerWhiteList", Constants.NBT.TAG_STRING);
            for(NBTBase nameTagBase : list) {
                NBTTagString nameTag = (NBTTagString) nameTagBase;
                playerWhiteList.add(nameTag.getString());
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("machineId", id); // Can't use key "id" because it is used by tile entities
        if (roomPos != null)
            compound.setTag("roomPos", NBTUtil.createPosTag(roomPos));
        compound.setString("CustomName", customName);

        if(hasOwner()) {
            compound.setUniqueId("owner", this.owner);
        }

        compound.setLong("spawntick", nextSpawnTick);
        if(schema != null) {
            compound.setString("schema", schema);
        }

        compound.setBoolean("locked", locked);

        if(playerWhiteList.size() > 0) {
            NBTTagList list = new NBTTagList();
            for(String playerName : playerWhiteList) {
                NBTTagString nameTag = new NBTTagString(playerName);
                list.appendTag(nameTag);
            }

            compound.setTag("playerWhiteList", list);
        }

        return compound;
    }

    public void initStructure() {
        if(this.id != -1) {
            return;
        }

        // "roomPos" is set to the northwest corner of the machine at y=40
        StructureTools.generateCubeForMachine(this);

        Vec3d2f destination = getDefaultSpawnPoint();

        WorldSavedDataMachines.getInstance().addSpawnPoint(this.id, destination);
    }

    public Vec3d2f getDefaultSpawnPoint() {
        return new Vec3d2f(new Vec3d(this.getCenterRoomPos()).add(0.5, 2, 0.5), 0, 0);
    }

    public boolean isAllowedToEnter(EntityPlayer player) {
        return !isLocked()
                || !hasOwner()
                || player.getUniqueID().equals(owner)
                || isOnWhiteList(player)
                || (player.getServer() != null && player.getServer().getPlayerList().canSendCommands(player.getGameProfile()));
    }

    public void syncRoomPos() {
        BlockPos roomPos = WorldSavedDataMachines.getInstance().getMachineRoomPosition(this.id);
        if (this.roomPos == null)
            this.roomPos = roomPos;
    }

    public void setRoomPos(BlockPos roomPos) {
        this.roomPos = roomPos;
        WorldSavedDataMachines.getInstance().setMachineRoomPosition(this.id, roomPos, true);
    }

    /**
     *
     * @return North-west corner of machine at y=40
     */
    public BlockPos getRoomPos() {
        return roomPos;
    }

    /**
     *
     * @return Center floor block of the room based on {@link #getRoomPos}. Calculated by using an offset of (room size/2) on x and z axis at y=40.
     */
    public BlockPos getCenterRoomPos() {
        int centerOffset = getSize().getDimension() / 2;
        return this.roomPos.add(centerOffset, 0, centerOffset);
    }

    public boolean isOnWhiteList(EntityPlayer player) {
        return playerWhiteList.contains(player.getName());
    }

    public boolean isOnWhiteList(String name) {
        return playerWhiteList.contains(name);
    }

    public void addToWhiteList(EntityPlayer player) {
        playerWhiteList.add(player.getName());
    }

    public void addToWhiteList(String playerName) {
        playerWhiteList.add(playerName);
    }

    public void removeFromWhiteList(EntityPlayer player) {
        playerWhiteList.remove(player.getName());
    }

    public void removeFromWhiteList(String playerName) {
        playerWhiteList.remove(playerName);
    }

    public Set<String> getWhiteList() {
        if (world.isRemote) {
            CompactMachines3.logger.warn("The TileEntityMachine#getWhiteList method should not be called on the client. Please report this to the mod author here: https://github.com/CompactMods/CompactMachines/issues/ Thanks!");
            return new HashSet<>();
        }

        return new HashSet<>(playerWhiteList);
    }

    public boolean isLocked() {
        return locked;
    }

    public void toggleLocked() {
        this.locked = !this.locked;
    }

    public void setLocked(boolean state) {
        this.locked = state;
    }

    public boolean hasNewSchema() {
        return schema != null && schema.length() > 0;
    }

    public String getSchemaName() {
        return schema;
    }

    public void setSchema(String schemaName) {
        this.schema = schemaName;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        GameProfile profile = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getProfileByUUID(getOwner());
        if(profile == null) {
            CompactMachines3.logger.warn("Profile not found for owner: {}", getOwner());
            return "Unknown";
        }

        return profile.getName();
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setOwner(EntityPlayer player) {
        if(player == null) {
            return;
        }

        setOwner(player.getUniqueID());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }


    /*
     * Chunk-Loading triggers
     */
    private void initialize() {
        if (this.initialized || this.getWorld().isRemote || this.isInvalid() || this.id == -1 || this.roomPos == null) {
            return;
        }

        if (ConfigurationHandler.Settings.forceLoadChunks && !ChunkLoadingMachines.isMachineChunkLoaded(this.id, this.roomPos)) {
            ChunkLoadingMachines.forceChunk(this.id, this.roomPos);
        }

        BlockPos storedPos = WorldSavedDataMachines.getInstance().getMachineRoomPosition(this.id);
        if (storedPos != this.roomPos) { // Make sure the two values have not de-synced
            WorldSavedDataMachines.getInstance().setMachineRoomPosition(this.id, this.roomPos, false);
        }

        this.initialized = true;
    }

    @Override
    public void update() {
        if (!this.initialized) {
            initialize();
        }

        this.alreadyNotifiedOnTick = false;

        if(nextSpawnTick == 0) {
            nextSpawnTick = this.getWorld().getTotalWorldTime() + ConfigurationHandler.MachineSettings.spawnRate;
        }

        if(!this.getWorld().isRemote && this.id != -1 && isInsideItself()) {
            if (this.getWorld().getTotalWorldTime() % 20 == 0) {
                world.playSound(null, getPos(),
                        SoundEvent.REGISTRY.getObject(new ResourceLocation("entity.wither.spawn")),
                        SoundCategory.MASTER,
                        1.0f,
                        1.0f
                );
            }
        }

        if(!this.getWorld().isRemote && this.id != -1 && this.getWorld().getTotalWorldTime() > nextSpawnTick) {
            if(ConfigurationHandler.MachineSettings.allowPeacefulSpawns || ConfigurationHandler.MachineSettings.allowHostileSpawns) {
                SpawnTools.spawnEntitiesInMachine(id);
            }

            nextSpawnTick = this.getWorld().getTotalWorldTime() + ConfigurationHandler.MachineSettings.spawnRate;
            this.markDirty();
        }

        /*
        // Use this once we render in world or use the proxy world to determine client side capabilities.
        if(!this.getWorld().isRemote && this.getWorld().getTotalWorldTime() % 20 == 0 && this.id != -1) {
            PackageHandler.instance.sendToAllAround(new MessageMachineChunk(this.id), new NetworkRegistry.TargetPoint(this.world.provider.getDimension(), this.getPos().getX(), this.getPos().getY(), this.getPos().getZ(), 32.0f));
        }
        */
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if(this.getWorld().isRemote) {
            return;
        }

        if(ConfigurationHandler.Settings.forceLoadChunks) { // Only unload the chunk if we have forceLoadChunks disabled
            return;
        }

        ChunkLoadingMachines.unforceChunk(this.id, this.roomPos);
    }

    /*
     * Capabilities
     */
    public RedstoneTunnelData getRedstoneTunnelForSide(EnumFacing side) {
        if(!WorldSavedDataMachines.getInstance().redstoneTunnels.containsKey(this.id)) {
            return null;
        }

        return WorldSavedDataMachines.getInstance().redstoneTunnels.get(this.id).get(side);
    }

    @Override
    public int getConnectedDimensionId(EnumFacing side) {
        return ConfigurationHandler.Settings.dimensionId;
    }

    @Override
    public BlockPos getConnectedBlockPosition(EnumFacing side) {
        if(WorldSavedDataMachines.getInstance() == null || WorldSavedDataMachines.getInstance().tunnels == null) {
            return null;
        }

        if(!WorldSavedDataMachines.getInstance().tunnels.containsKey(this.id)) {
            return null;
        }

        return WorldSavedDataMachines.getInstance().tunnels.get(this.id).get(side);
    }

    public BlockPos getMachineWorldInsetPos(EnumFacing facing) {
        BlockPos tunnelPos = this.getConnectedBlockPosition(facing);
        if(tunnelPos == null) {
            return null;
        }

        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelPos, this.roomPos, this.getSize());
        return tunnelPos.offset(insetDirection);
    }

    public IBlockState getConnectedBlockState(EnumFacing facing) {
        BlockPos insetPos = getMachineWorldInsetPos(facing);
        if(insetPos == null) {
            return null;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        return machineWorld.getBlockState(insetPos);
    }

    public TileEntity getConnectedTileEntity(EnumFacing facing) {
        BlockPos insetPos = getMachineWorldInsetPos(facing);
        if(insetPos == null) {
            return null;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        return machineWorld.getTileEntity(insetPos);
    }

    public boolean isInsideItself() {
        if(this.getWorld().provider.getDimension() != ConfigurationHandler.Settings.dimensionId) {
            return false;
        }

        if (this.id == -1)
            return false;

        if (!this.getWorld().isRemote) {
            // Server
            return StructureTools.getIdForPos(this.getPos()) == this.id;
        } else {
            // Client
            return WorldSavedDataMachines.getClientMachineIdFromBoxPos(this.getPos()) == this.id;
        }
    }

    public ItemStack getConnectedPickBlock(EnumFacing facing) {
        BlockPos insetPos = getMachineWorldInsetPos(facing);
        if(insetPos == null) {
            return ItemStack.EMPTY;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        IBlockState state = machineWorld.getBlockState(insetPos);
        //noinspection deprecation
        return state.getBlock().getItem(machineWorld, insetPos, state);
    }

    public int getRedstonePowerOutput(EnumFacing facing) {
        if(this.id == -1) {
            return 0;
        }

        // We don't know the actual power on the client-side, which does not have the worldsaveddatamachines instance
        if(this.getWorld().isRemote) {
            return 0;
        }

        Map<EnumFacing, RedstoneTunnelData> tunnelMapping = WorldSavedDataMachines.getInstance().redstoneTunnels.get(this.id);
        if(tunnelMapping == null) {
            return 0;
        }

        RedstoneTunnelData tunnelData = tunnelMapping.get(facing);
        if(tunnelData == null) {
            return 0;
        }


        if(!tunnelData.isOutput) {
            return 0;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        if(!(machineWorld.getTileEntity(tunnelData.pos) instanceof TileEntityRedstoneTunnel)) {
            return 0;
        }

        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelData.pos, this.roomPos, this.getSize());
        BlockPos insetPos = tunnelData.pos.offset(insetDirection);
        IBlockState insetBlockState = machineWorld.getBlockState(insetPos);

        int power = 0;
        if(insetBlockState.getBlock() instanceof BlockRedstoneWire) {
            power = insetBlockState.getValue(BlockRedstoneWire.POWER);
        } else {
            power = machineWorld.getRedstonePower(insetPos, insetDirection);
        }

        return power;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (isInsideItself() || this.roomPos == null) {
            return false;
        }

        if(world.isRemote || facing == null) {
            if(CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
                return true;
            }

            return super.hasCapability(capability, facing);
        }

        BlockPos tunnelPos = this.getConnectedBlockPosition(facing);
        if(tunnelPos == null) {
            return false;
        }

        World machineWorld = DimensionTools.getServerMachineWorld();
        if(!(machineWorld.getTileEntity(tunnelPos) instanceof TileEntityTunnel)) {
            return false;
        }

        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelPos, this.roomPos, this.getSize());
        BlockPos insetPos = tunnelPos.offset(insetDirection);

        TileEntity te = machineWorld.getTileEntity(insetPos);
        if (te != null && te.hasCapability(capability, insetDirection.getOpposite())) {
            return true;
        }

        return CapabilityNullHandlerRegistry.hasNullHandler(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (isInsideItself() || this.roomPos == null) {
            return null;
        }

        if(this.getWorld().isRemote || facing == null) {
            if(CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
                return CapabilityNullHandlerRegistry.getNullHandler(capability);
            }

            return super.getCapability(capability, facing);
        }

        BlockPos tunnelPos = this.getConnectedBlockPosition(facing);
        if(tunnelPos == null) {
            return null;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        if(!(machineWorld.getTileEntity(tunnelPos) instanceof TileEntityTunnel)) {
            return null;
        }

        EnumFacing insetDirection = StructureTools.getInsetWallFacing(tunnelPos, this.roomPos, this.getSize());
        BlockPos insetPos = tunnelPos.offset(insetDirection);

        TileEntity te = machineWorld.getTileEntity(insetPos);
        if (te != null && te.hasCapability(capability, insetDirection.getOpposite())) {
            return te.getCapability(capability, insetDirection.getOpposite());
        }

        if(CapabilityNullHandlerRegistry.hasNullHandler(capability)) {
            return CapabilityNullHandlerRegistry.getNullHandler(capability);
        }

        return null;
    }
}
