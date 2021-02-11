package org.dave.compactmachines3.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.dave.compactmachines3.CompactMachines3;
import org.dave.compactmachines3.misc.ConfigurationHandler;
import org.dave.compactmachines3.misc.Vec3d2f;
import org.dave.compactmachines3.reference.EnumMachineSize;
import org.dave.compactmachines3.tile.TileEntityMachine;
import org.dave.compactmachines3.utility.DimensionBlockPos;
import org.dave.compactmachines3.world.data.RedstoneTunnelData;
import org.dave.compactmachines3.world.tools.DimensionTools;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldSavedDataMachines extends WorldSavedData {

    public int nextId = 0;
    public Map<Integer, Vec3d2f> spawnPoints = new HashMap<>();
    public Map<Integer, Map<EnumFacing, BlockPos>> tunnels = new HashMap<>();
    public Map<Integer, Map<EnumFacing, RedstoneTunnelData>> redstoneTunnels = new HashMap<>();
    public Map<Integer, DimensionBlockPos> machinePositions = new HashMap<>();
    public Map<Integer, BlockPos> machineGrid = new HashMap<>();
    public Map<Integer, EnumMachineSize> machineSizes = new HashMap<>();
    public Map<UUID, Integer> bedLocations = new HashMap<>();
    public BlockPos lastGrid = null;

    private static WorldSavedDataMachines instance;
    private static Map<Integer, BlockPos> clientMachineGrid;
    private static Map<Integer, EnumMachineSize> clientMachineSizes;

    public static WorldSavedDataMachines getInstance() {
        // In some rare cases, like when loading the world and restoring chunk tickets, this could still be null!
        if (instance == null)
            loadSaveData();
        return instance;
    }

    public void setBedLocation(EntityPlayer player) {
        int id = getMachineIdFromEntityPos(player);
        bedLocations.put(player.getUniqueID(), id);
        this.markDirty();
    }

    public int getBedLocation(EntityPlayer player) {
        return bedLocations.getOrDefault(player.getUniqueID(), -1);
    }

    // TODO: Cleanup this class. It's all over the place.
    public DimensionBlockPos getMachineBlockPosition(int id) {
        return machinePositions.get(id);
    }

    public TileEntityMachine getMachine(int id) {
        if(!machinePositions.containsKey(id)) {
            return null;
        }

        DimensionBlockPos dimPos = getMachineBlockPosition(id);
        WorldServer world = DimensionTools.getWorldServerForDimension(dimPos.getDimension());
        TileEntity result = world.getTileEntity(dimPos.getBlockPos());

        return result instanceof TileEntityMachine ? (TileEntityMachine) result : null;
    }

    public void setMachineRoomPosition(int id, BlockPos roomPos, boolean updateLastGrid) {
        machineGrid.put(id, roomPos);
        if (updateLastGrid && roomPos != null)
            this.lastGrid = roomPos;
    }

    public BlockPos getMachineRoomPosition(int id) {
        return machineGrid.get(id);
    }

    public int getMachineIdFromEntityPos(Entity entity) {
        return getMachineIdFromBoxPos(new BlockPos(entity.posX, entity.posY, entity.posZ));
    }

    public int getMachineIdFromBoxPos(BlockPos pos) {
        return getMachineIdFromBoxPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public int getMachineIdFromBoxPos(int x, int y, int z) {
        return getMachineIdFromBoxPos(x, y, z, machineGrid, machineSizes);
    }

    public static int getClientMachineIdFromBoxPos(BlockPos pos) {
        if (clientMachineGrid == null || clientMachineSizes == null)
            return -1;
        return getMachineIdFromBoxPos(pos.getX(), pos.getY(), pos.getZ(), clientMachineGrid, clientMachineSizes);
    }

    private static int getMachineIdFromBoxPos(int x, int y, int z, Map<Integer, BlockPos> machineGrid, Map<Integer, EnumMachineSize> machineSizes) {
        for (Map.Entry<Integer, BlockPos> entry : machineGrid.entrySet()) {
            int roomPosX = entry.getValue().getX();
            int roomPosZ = entry.getValue().getZ();
            EnumMachineSize sizeEnum = machineSizes.get(entry.getKey());
            if (sizeEnum == null) {
                CompactMachines3.logger.error("Machine size was null with key {}", entry.getKey());
                continue;
            }
            int size = sizeEnum.getDimension();
            boolean insideRoom = roomPosX <= x && x <= roomPosX + size && roomPosZ <= z && z <= roomPosZ + size && 40 <= y && y <= 40 + size;

            if (insideRoom) {
                return entry.getKey();
            }
        }

        return -1;
    }

    public void addMachineSize(int id, EnumMachineSize size) {
        machineSizes.put(id, size);
        CompactMachines3.logger.debug("Adding machine size: id={}, size={}", id, size.getName());
        this.markDirty();
    }

    public void addMachinePosition(int id, BlockPos pos, int dimension) {
        machinePositions.put(id, new DimensionBlockPos(pos, dimension));
        CompactMachines3.logger.debug("Adding machine position: id={}, pos={}, dimension={}", id, pos, dimension);
        this.markDirty();
    }

    public void addSpawnPoint(int id, @Nonnull Vec3d2f destination) {
        spawnPoints.put(id, destination);
        CompactMachines3.logger.debug(String.format("Setting spawn point: id=%s, x=%.2f, y=%.2f, z=%.2f, yaw=%.2f, pitch=%.2f",
                id, destination.getX(), destination.getY(), destination.getY(), destination.yaw, destination.pitch));
        this.markDirty();
    }

    public void removeMachinePosition(int id) {
        machinePositions.remove(id);
        CompactMachines3.logger.debug("Removing machine position by id: id={}", id);
        this.markDirty();
    }

    // TODO: We might want to move this to a different class?
    public static int reserveMachineId() {
        WorldSavedDataMachines wsd = WorldSavedDataMachines.getInstance();
        int val = wsd.nextId++; // Returns the value, then adds 1
        wsd.markDirty();
        return val;
    }

    public WorldSavedDataMachines(String name) {
        super(name);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void loadWorld(WorldEvent.Load event) {
        if(event.getWorld().isRemote || event.getWorld().provider.getDimension() != ConfigurationHandler.Settings.dimensionId) {
            return;
        }

        loadSaveData();
    }

    public static synchronized void loadSaveData() {
        if (instance != null)
            return;

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        instance = (WorldSavedDataMachines) machineWorld.getMapStorage().getOrLoadData(WorldSavedDataMachines.class, "WorldSavedDataMachines");
        if (instance == null) {
            instance = new WorldSavedDataMachines("WorldSavedDataMachines");
            instance.markDirty();
        }

        CompactMachines3.logger.info("Loaded data for compact machine world: {} spawn points, next machine id is {}, players with beds: {}", instance.spawnPoints.size(),
                instance.nextId, instance.bedLocations.size());

        machineWorld.getMapStorage().setData("WorldSavedDataMachines", instance);
    }

    public void removeTunnel(BlockPos pos) {
        int id = getMachineIdFromBoxPos(pos);
        Map<EnumFacing, BlockPos> sideMapping = tunnels.get(id);
        if(sideMapping == null) {
            return;
        }

        EnumFacing sideToRemove = null;
        for(EnumFacing side : sideMapping.keySet()) {
            if(sideMapping.get(side).equals(pos)) {
                sideToRemove = side;
                break;
            }
        }

        if(sideToRemove != null) {
            CompactMachines3.logger.debug("Removing tunnel mapping by blockpos: pos={} --> id={}, side={}", pos, id, sideToRemove);
            sideMapping.remove(sideToRemove);
        }
        this.markDirty();
    }

    public void removeTunnel(BlockPos position, EnumFacing side) {
        int id = getMachineIdFromBoxPos(position);
        Map<EnumFacing, BlockPos> sideMapping = tunnels.get(id);
        if(sideMapping == null) {
            return;
        }

        CompactMachines3.logger.debug("Removing tunnel mapping by pos+side: id={}, side={}", id, side);
        sideMapping.remove(side);
        this.markDirty();
    }

    public void addTunnel(BlockPos position, EnumFacing side) {
        this.addTunnel(position, side, false);
    }

    private void addTunnel(BlockPos position, EnumFacing side, boolean isLoading) {
        int id = instance.getMachineIdFromBoxPos(position);

        addTunnel(position, side, id, isLoading);
    }

    private void addTunnel(BlockPos position, EnumFacing side, int id, boolean isLoading) {
        Map<EnumFacing, BlockPos> sideMapping = tunnels.computeIfAbsent(id, k -> new HashMap<>());

        sideMapping.put(side, position);
        CompactMachines3.logger.debug("Adding tunnel mapping: side={}, pos={} --> id={}", side, position, id);

        if(!isLoading) {
            this.markDirty();
        }
    }

    public void toggleRedstoneTunnelOutput(BlockPos pos) {
        int id = getMachineIdFromBoxPos(pos);
        Map<EnumFacing, RedstoneTunnelData> sideMapping = redstoneTunnels.get(id);
        if(sideMapping == null) {
            return;
        }

        EnumFacing sideToRemove = null;
        for(EnumFacing side : sideMapping.keySet()) {
            if(sideMapping.get(side).pos.equals(pos)) {
                sideToRemove = side;
                break;
            }
        }

        if(sideToRemove != null) {
            sideMapping.get(sideToRemove).isOutput = !sideMapping.get(sideToRemove).isOutput;
            CompactMachines3.logger.debug("Toggle tunnel output by blockpos: pos={} --> id={}, side={}, output={}", pos, id, sideToRemove, sideMapping.get(sideToRemove).isOutput);
        }
        this.markDirty();
    }

    public void removeRedstoneTunnel(BlockPos pos) {
        int id = getMachineIdFromBoxPos(pos);
        Map<EnumFacing, RedstoneTunnelData> sideMapping = redstoneTunnels.get(id);
        if(sideMapping == null) {
            return;
        }

        EnumFacing sideToRemove = null;
        for(EnumFacing side : sideMapping.keySet()) {
            if(sideMapping.get(side).pos.equals(pos)) {
                sideToRemove = side;
                break;
            }
        }

        if(sideToRemove != null) {
            CompactMachines3.logger.debug("Removing tunnel mapping by blockpos: pos={} --> id={}, side={}", pos, id, sideToRemove);
            sideMapping.remove(sideToRemove);
        }
        this.markDirty();
    }

    public void removeRedstoneTunnel(BlockPos position, EnumFacing side) {
        int id = getMachineIdFromBoxPos(position);
        Map<EnumFacing, RedstoneTunnelData> sideMapping = redstoneTunnels.get(id);
        if(sideMapping == null) {
            return;
        }

        CompactMachines3.logger.debug("Removing tunnel mapping by pos+side: id={}, side={}", id, side);
        sideMapping.remove(side);
        this.markDirty();
    }

    public void addRedstoneTunnel(BlockPos position, EnumFacing side, boolean isOutput) {
        this.addRedstoneTunnel(position, side, isOutput, false);
    }

    private void addRedstoneTunnel(BlockPos position, EnumFacing side, boolean isOutput, boolean isLoading) {
        int id = getMachineIdFromBoxPos(position);

        Map<EnumFacing, RedstoneTunnelData> sideMapping = redstoneTunnels.get(id);
        if(sideMapping == null) {
            sideMapping = new HashMap<>();
            redstoneTunnels.put(id, sideMapping);
        }

        sideMapping.put(side, new RedstoneTunnelData(position, isOutput));
        CompactMachines3.logger.debug("Adding redstone tunnel mapping: side={}, pos={}, isOutput={} --> id={}", side, position, isOutput, id);

        if(!isLoading) {
            this.markDirty();
        }
    }

    public static Map<Integer, BlockPos> getClientMachineGrid() {
        return clientMachineGrid;
    }

    public static void setClientMachineGrid(Map<Integer, BlockPos> clientMachineGrid) {
        WorldSavedDataMachines.clientMachineGrid = clientMachineGrid;
    }

    public static Map<Integer, EnumMachineSize> getClientMachineSizes() {
        return clientMachineSizes;
    }

    public static void setClientMachineSizes(Map<Integer, EnumMachineSize> clientMachineSizes) {
        WorldSavedDataMachines.clientMachineSizes = clientMachineSizes;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("nextMachineId", nextId);

        NBTTagCompound bedLocationsTag = new NBTTagCompound();
        for (UUID playerId : bedLocations.keySet()) {
            int id = bedLocations.get(playerId);
            bedLocationsTag.setInteger(playerId.toString(), id);
        }

        NBTTagCompound machineSizesTag = new NBTTagCompound();
        for (int id : machineSizes.keySet()) {
            int size = machineSizes.get(id).getMeta();
            machineSizesTag.setInteger("" + id, size);
        }

        NBTTagList spawnPointList = new NBTTagList();
        for (int id : spawnPoints.keySet()) {
            Vec3d2f pos = spawnPoints.get(id);

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("id", id);
            tag.setTag("pos", pos.toTag());
            spawnPointList.appendTag(tag);
        }

        NBTTagList tunnelList = new NBTTagList();
        for (Map.Entry<Integer, Map<EnumFacing, BlockPos>> entry : tunnels.entrySet()) {
            int id = entry.getKey();
            Map<EnumFacing, BlockPos> sideMappings = entry.getValue();

            for (Map.Entry<EnumFacing, BlockPos> sideEntry : sideMappings.entrySet()) {
                EnumFacing side = sideEntry.getKey();
                BlockPos position = sideEntry.getValue();

                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("id", id);
                tag.setInteger("side", side.getIndex());
                tag.setInteger("x", position.getX());
                tag.setInteger("y", position.getY());
                tag.setInteger("z", position.getZ());
                tunnelList.appendTag(tag);
            }
        }

        NBTTagList redstoneTunnelList = new NBTTagList();
        for (Map.Entry<Integer, Map<EnumFacing, RedstoneTunnelData>>  entry : redstoneTunnels.entrySet()) {
            Map<EnumFacing, RedstoneTunnelData> sideMappings = entry.getValue();

            for (Map.Entry<EnumFacing, RedstoneTunnelData> sideEntry : sideMappings.entrySet()) {
                EnumFacing side = sideEntry.getKey();
                RedstoneTunnelData info = sideEntry.getValue();

                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("side", side.getIndex());
                tag.setInteger("x", info.pos.getX());
                tag.setInteger("y", info.pos.getY());
                tag.setInteger("z", info.pos.getZ());
                tag.setBoolean("output", info.isOutput);
                redstoneTunnelList.appendTag(tag);
            }
        }


        NBTTagList machineList = new NBTTagList();
        for (Map.Entry<Integer, DimensionBlockPos> entry : machinePositions.entrySet()) {
            int id = entry.getKey();
            DimensionBlockPos dimpos = entry.getValue();
            BlockPos roomPos = machineGrid.get(id);
            NBTTagCompound tag = dimpos.getAsNBT();
            tag.setInteger("id", id);
            if (roomPos != null)
                tag.setTag("roomPos", NBTUtil.createPosTag(roomPos));
            machineList.appendTag(tag);
        }

        compound.setTag("spawnpoints", spawnPointList);
        compound.setTag("tunnels", tunnelList);
        compound.setTag("machines", machineList);
        compound.setTag("bedLocations", bedLocationsTag);
        compound.setTag("sizes", machineSizesTag);
        compound.setTag("redstoneTunnels", redstoneTunnelList);
        if (lastGrid != null)
            compound.setTag("lastGrid", NBTUtil.createPosTag(lastGrid));
        return compound;
    }


    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nextId = nbt.hasKey("nextMachineId") ? nbt.getInteger("nextMachineId") : /* Legacy */ nbt.getInteger("nextMachineCoord");

        NBTTagCompound bedLocationsTag = null;
        if (nbt.hasKey("bedLocations")) {
            bedLocationsTag = nbt.getCompoundTag("bedLocations");
        } else if (nbt.hasKey("bedcoords")) {
            bedLocationsTag = nbt.getCompoundTag("bedcoords"); // Legacy
        }

        if (bedLocationsTag != null) {
            bedLocations.clear();
            for (String uuidString : bedLocationsTag.getKeySet()) {
                UUID uuid = UUID.fromString(uuidString);
                int machineId = bedLocationsTag.getInteger(uuidString);
                bedLocations.put(uuid, machineId);
            }
        }

        if (nbt.hasKey("sizes")) {
            machineSizes.clear();
            NBTTagCompound machineSizesTag = nbt.getCompoundTag("sizes");
            for (String idString : machineSizesTag.getKeySet()) {
                int id = Integer.parseInt(idString);
                int size = machineSizesTag.getInteger(idString);
                machineSizes.put(id, EnumMachineSize.getFromMeta(size));
            }
        }

        if (nbt.hasKey("spawnpoints")) {
            spawnPoints.clear();
            NBTTagList tagList = nbt.getTagList("spawnpoints", 10);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                int id = tag.hasKey("id", 3) ? tag.getInteger("id") : /* Legacy */ tag.getInteger("coords");
                Vec3d2f pos;
                if (tag.hasKey("pos")) {
                    pos = Vec3d2f.fromTag(tag.getCompoundTag("pos"));
                } else {
                    pos = new Vec3d2f(new Vec3d(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), 0, 0);
                }

                spawnPoints.put(id, pos);
            }
        }

        if (nbt.hasKey("machines")) {
            machinePositions.clear();
            machineGrid.clear();
            NBTTagList tagList = nbt.getTagList("machines", 10);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                int id;
                BlockPos roomPos = null;
                if (tag.hasKey("coords")) {
                    id = tag.getInteger("coords"); // Legacy
                    roomPos = new BlockPos(id * 1024, 0, 0);
                } else {
                    id = tag.getInteger("id");
                    if (tag.hasKey("roomPos"))
                        roomPos = NBTUtil.getPosFromTag(tag.getCompoundTag("roomPos"));
                }
                machinePositions.put(id, new DimensionBlockPos(tag));
                if (roomPos != null)
                    machineGrid.put(id, roomPos);
            }
        }

        // Have to do tunnels after machines so that #getMachineIdFromBoxPos works (needs machineGrid AND machineSizes filled with data)
        if (nbt.hasKey("redstoneTunnels")) {
            redstoneTunnels.clear();
            NBTTagList tagList = nbt.getTagList("redstoneTunnels", 10);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);

                BlockPos position = new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
                EnumFacing side = EnumFacing.byIndex(tag.getInteger("side"));
                boolean isOutput = tag.getBoolean("output");

                this.addRedstoneTunnel(position, side, isOutput, true);
            }
        }

        if (nbt.hasKey("tunnels")) {
            tunnels.clear();
            NBTTagList tagList = nbt.getTagList("tunnels", 10);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);

                BlockPos position = new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
                int id = tag.hasKey("id") ? tag.getInteger("id") : getMachineIdFromBoxPos(position);
                EnumFacing side = EnumFacing.byIndex(tag.getInteger("side"));

                this.addTunnel(position, side, id, true);
            }
        }

        if (nbt.hasKey("lastGrid"))
            this.lastGrid = NBTUtil.getPosFromTag(nbt.getCompoundTag("lastGrid"));
    }
}
