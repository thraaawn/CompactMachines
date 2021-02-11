package org.dave.compactmachines3.world.tools;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.dave.compactmachines3.CompactMachines3;
import org.dave.compactmachines3.misc.ConfigurationHandler;
import org.dave.compactmachines3.misc.Vec3d2f;
import org.dave.compactmachines3.network.MessageMachinePositions;
import org.dave.compactmachines3.reference.EnumMachineSize;
import org.dave.compactmachines3.schema.Schema;
import org.dave.compactmachines3.schema.SchemaRegistry;
import org.dave.compactmachines3.skyworld.SkyWorldSavedData;
import org.dave.compactmachines3.skyworld.SkyWorldType;
import org.dave.compactmachines3.tile.TileEntityMachine;
import org.dave.compactmachines3.utility.DimensionBlockPos;
import org.dave.compactmachines3.world.TeleporterMachines;
import org.dave.compactmachines3.world.WorldSavedDataMachines;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportationTools {
    private static final Map<UUID, ExitData> machineExitData = new HashMap<>();

    public static boolean tryToEnterMachine(EntityPlayerMP player, TileEntityMachine machine) {
        if (machineExitData.containsKey(player.getUniqueID())) {
            ExitData exitData = machineExitData.get(player.getUniqueID());
            if (exitData.machineId == machine.id) {
                WorldServer machineWorld = DimensionTools.getServerMachineWorld();
                int waitTimeSeconds = ConfigurationHandler.MachineSettings.waitTime;

                if (machineWorld.getTotalWorldTime() - exitData.exitTimestamp <= (waitTimeSeconds * 20L)) {
                    ITextComponent message = new TextComponentTranslation("hint.compactmachines3.entered_too_soon", waitTimeSeconds, waitTimeSeconds == 1 ? "" : "s");
                    message.getStyle().setColor(TextFormatting.RED);
                    player.sendStatusMessage(message, true);
                    return false;
                }
            }
        }

        BlockPos pos = machine.getPos();
        World world = machine.getWorld();

        if (machine.isInsideItself()) {
            return false;
        }

        if (!machine.isAllowedToEnter(player)) {
            player.sendStatusMessage(new TextComponentTranslation("hint.compactmachines3.not_permitted_to_enter"), true);
            return false;
        }

        // Prevent players from claiming more than one machine in the SkyWorld Machine Hub
        if (!machine.hasOwner() && world.getWorldType() instanceof SkyWorldType) {
            boolean playerHasHubMachine = SkyWorldSavedData.instance.isHubMachineOwner(player);
            if (playerHasHubMachine) {
                player.sendStatusMessage(new TextComponentTranslation("hint.compactmachines3.skyworld.only_one_machine_claim"), true);
                return false;
            }
        }

        boolean isNew = machine.id == -1;
        machine.initStructure();

        WorldSavedDataMachines.getInstance().addMachinePosition(machine.id, pos, world.provider.getDimension());

        if (isNew) // A new grid position was chosen if the id had not yet been set before the structure init, have to update for clients
            MessageMachinePositions.updateClientMachinePositions();

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        BlockPos destination = new BlockPos(WorldSavedDataMachines.getInstance().spawnPoints.get(machine.id).getPosition());

        BlockPos safe = null;
        if (!ConfigurationHandler.MachineSettings.allowUnsafeTeleport && (!machineWorld.isAirBlock(destination) || !machineWorld.isAirBlock(destination.add(0, 1, 0)))) {
            safe = TeleportationTools.findSafeSpawnpoint(machine);
            if (safe == null) {
                ITextComponent message = new TextComponentTranslation("hint.compactmachines3.no_spawnpoint_found")
                        .setStyle(new Style().setColor(TextFormatting.RED));
                player.sendStatusMessage(message, true);
                return false;
            }
        }

        machineExitData.remove(player.getUniqueID()); // Remove old exit data
        TeleportationTools.teleportPlayerToMachine(player, machine.id, false, safe);
        if (safe != null) {
            ITextComponent message = new TextComponentTranslation("hint.compactmachines3.used_safe_spawnpoint")
                    .setStyle(new Style().setColor(TextFormatting.GREEN));
            player.sendStatusMessage(message, true);
        }
        StructureTools.setBiomeForMachineId(machine.id, world.getBiome(pos));

        if (!machine.hasOwner() || "Unknown".equals(machine.getOwnerName())) {
            machine.setOwner(player);
            machine.markDirty();

            if (world.getWorldType() instanceof SkyWorldType) {
                SkyWorldSavedData.instance.setHomeOwner(player, machine.id);
            }
        }

        return true;
    }

    public static BlockPos findSafeSpawnpoint(TileEntityMachine machine) {
        BlockPos start = machine.getCenterRoomPos();
        EnumMachineSize size = machine.getSize();
        WorldServer world = DimensionTools.getServerMachineWorld();

        BlockPos found = TeleportationTools.getValidSpawnLocation(world, start, (size.getDimension() - 2) / 2, size.getDimension() - 2, 1);
        if (found == start) {
            // The start should always be in the ground of the room
            return null;
        }

        return found;
    }

    public static void teleportToSkyworldHome(EntityPlayer player) {
        if (!SkyWorldSavedData.instance.hasSkyworldHome(player)) {
            return;
        }

        int id = SkyWorldSavedData.instance.getSkyworldHome(player);
        DimensionBlockPos pos = WorldSavedDataMachines.getInstance().machinePositions.get(id);
        if (pos == null) {
            return;
        }

        BlockPos spawnPoint = pos.getBlockPos();
        player.setPositionAndUpdate(spawnPoint.getX() + 0.5d, spawnPoint.getY() + 1.2d, spawnPoint.getZ() + 0.5d);
    }

    public static int getLastKnownRoomId(EntityPlayer player, boolean getFirst) {
        NBTTagCompound playerNBT = player.getEntityData();
        if (!playerNBT.hasKey("compactmachines3-idHistory") && /* Legacy */ !playerNBT.hasKey("compactmachines3-coordHistory")) {
            return -1;
        }

        NBTTagList idHistory = playerNBT.getTagList("compactmachines3-idHistory", 10);
        if (idHistory.isEmpty()) {
            idHistory = playerNBT.getTagList("compactmachines3-coordHistory", 10); // Legacy
        }
        if (idHistory.isEmpty()) {
            return -1;
        }

        NBTTagCompound nbt = idHistory.getCompoundTagAt(getFirst ? 0 : idHistory.tagCount() - 1);
        return nbt.hasKey("id", 3) ? nbt.getInteger("id") : /* Legacy */ nbt.getInteger("coords");
    }

    public static void teleportPlayerToMachine(EntityPlayerMP player, int id) {
        teleportPlayerToMachine(player, id, false, null);
    }

    private static void teleportPlayerToMachine(EntityPlayerMP player, int id, boolean isReturning, BlockPos safe) {
        if (id == -1)
            return;

        NBTTagCompound playerNBT = player.getEntityData();
        if (player.dimension != ConfigurationHandler.Settings.dimensionId) {
            playerNBT.setInteger("compactmachines3-oldDimension", player.dimension);
            playerNBT.setTag("compactmachines3-oldPos", new Vec3d2f(player).toTag());

            WorldServer machineWorld = DimensionTools.getServerMachineWorld();
            player.addExperienceLevel(0);
            PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
            playerList.transferPlayerToDimension(player, ConfigurationHandler.Settings.dimensionId, new TeleporterMachines(machineWorld));

            playerNBT.removeTag("compactmachines3-idHistory");
            playerNBT.removeTag("compactmachines3-coordHistory"); // Legacy
        }

        if (!isReturning) {
            addMachineIdToHistory(id, player, true);
        }

        TileEntityMachine machine = WorldSavedDataMachines.getInstance().getMachine(id);
        if (machine == null)
            return;

        if (machine.hasNewSchema()) {
            Schema schema = SchemaRegistry.instance.getSchema(machine.getSchemaName());
            if (schema == null) {
                CompactMachines3.logger.warn("Unknown schema used by Compact Machine @ {}", WorldSavedDataMachines.getInstance().getMachineBlockPosition(id));
            } else {
                StructureTools.restoreSchema(schema, id);

                Vec3d adjustedSpawnPosition = schema.getSpawnPosition().add(new Vec3d(WorldSavedDataMachines.getInstance().getMachineRoomPosition(id)));
                WorldSavedDataMachines.getInstance().addSpawnPoint(machine.id, new Vec3d2f(adjustedSpawnPosition, 0, 0));
                machine.setSchema(null);
                machine.markDirty();
            }
        }

        if (safe == null) {
            Vec3d2f destination = WorldSavedDataMachines.getInstance().spawnPoints.get(id);
            destination.setPlayerLocation(player);
        } else {
            Vec3d destination = new Vec3d(safe).add(0.5, 0, 0.5);
            player.setPositionAndUpdate(destination.x, destination.y, destination.z);
        }
    }

    public static void addMachineIdToHistory(int id, EntityPlayer player, boolean isBeforeTeleport) {
        NBTTagCompound playerNBT = player.getEntityData();
        NBTTagList idHistory = playerNBT.getTagList("compactmachines3-idHistory", 10);
        if (idHistory.isEmpty()) {
            idHistory = playerNBT.getTagList("compactmachines3-coordHistory", 10); // Legacy
        }

        NBTTagCompound toAppend = new NBTTagCompound();
        toAppend.setInteger("id", id);
        if (!idHistory.isEmpty() && isBeforeTeleport) // Only add positional data if we have already been in at least 1 machine, otherwise oldPos is used
            toAppend.setTag("pos", new Vec3d2f(player).toTag());

        idHistory.appendTag(toAppend);
        playerNBT.setTag("compactmachines3-idHistory", idHistory);
    }

    public static void teleportPlayerOutOfMachineDimension(EntityPlayerMP player) {
        PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();

        NBTTagCompound playerNBT = player.getEntityData();
        if (playerNBT.hasKey("compactmachines3-oldDimension")) {
            int oldDimension = playerNBT.getInteger("compactmachines3-oldDimension");
            Vec3d2f pos;
            if (playerNBT.hasKey("compactmachines3-oldPos")) {
                pos = Vec3d2f.fromTag(playerNBT.getCompoundTag("compactmachines3-oldPos"));
            } else {
                double oldPosX = playerNBT.getDouble("compactmachines3-oldPosX");
                double oldPosY = playerNBT.getDouble("compactmachines3-oldPosY");
                double oldPosZ = playerNBT.getDouble("compactmachines3-oldPosZ");

                pos = new Vec3d2f(new Vec3d(oldPosX, oldPosY, oldPosZ), player);
            }

            player.addExperienceLevel(0);
            playerList.transferPlayerToDimension(player, oldDimension, new TeleporterMachines(DimensionTools.getWorldServerForDimension(oldDimension)));
            // Should only be null if the compound tag is empty, which it shouldn't be if it exists
            if (pos != null)
                pos.setPlayerLocation(player);
        } else {
            int firstId = getLastKnownRoomId(player, true);
            playerNBT.removeTag("compactmachines3-idHistory");
            playerNBT.removeTag("compactmachines3-coordHistory");
            if (firstId == -1) {
                firstId = StructureTools.getIdForPos(new BlockPos(player.posX, player.posY, player.posZ));
            }

            DimensionBlockPos pos = WorldSavedDataMachines.getInstance().machinePositions.get(firstId);

            BlockPos startPoint;
            int dimension;

            if (pos != null) {
                // The machine exists and we know its position
                dimension = pos.getDimension();
                startPoint = pos.getBlockPos();
            } else {
                // We have no idea -> use the world spawn instead
                dimension = 0;
                startPoint = DimensionTools.getWorldServerForDimension(0).provider.getRandomizedSpawnPoint();
            }

            WorldServer world = DimensionTools.getWorldServerForDimension(dimension);
            BlockPos spawnPoint = pos != null ? getValidSpawnLocation(world, startPoint) : startPoint;

            playerList.transferPlayerToDimension(player, dimension, new TeleporterMachines(world));
            player.setPositionAndUpdate(spawnPoint.getX() + 0.5d, spawnPoint.getY() + 0.2d, spawnPoint.getZ() + 0.5d);

        }
    }

    public static BlockPos getValidSpawnLocation(WorldServer world, BlockPos start) {
        return getValidSpawnLocation(world, start, 5, 3, -1);
    }

    public static BlockPos getValidSpawnLocation(WorldServer world, BlockPos start, int radius, int height, int startHeight) {
        // Spiral outwards
        for (int y = startHeight; y < height + startHeight; y++) {
            for (int r = 0; r <= radius; r++) {
                if (r == 0) {
                    BlockPos check = start.add(0, y, 0);
                    if (isValidSpawnpoint(world, check)) {
                        return check;
                    }
                    continue;
                }
                for (int q = -r + 1; q <= r; q++) {
                    BlockPos check = start.add(r, y, q);
                    if (isValidSpawnpoint(world, check)) {
                        return check;
                    }
                }
                for (int q = r - 1; q >= -r; q--) {
                    BlockPos check = start.add(q, y, r);
                    if (isValidSpawnpoint(world, check)) {
                        return check;
                    }
                }
                for (int q = r - 1; q >= -r; q--) {
                    BlockPos check = start.add(-r, y, q);
                    if (isValidSpawnpoint(world, check)) {
                        return check;
                    }
                }
                for (int q = -r + 1; q <= r; q++) {
                    BlockPos check = start.add(q, y, -r);
                    if (isValidSpawnpoint(world, check)) {
                        return check;
                    }
                }
            }
        }

        return start;
    }

    public static boolean isValidSpawnpoint(WorldServer world, BlockPos pos) {
        return world.isAirBlock(pos) && world.isAirBlock(pos.up()) && !world.isAirBlock(pos.down());
    }

    public static void teleportPlayerOutOfMachine(EntityPlayerMP player) {
        NBTTagCompound playerNBT = player.getEntityData();
        if (!playerNBT.hasKey("compactmachines3-idHistory") && /* Legacy */ !playerNBT.hasKey("compactmachines3-coordHistory")) {
            teleportPlayerOutOfMachineDimension(player);
            return;
        }

        NBTTagList idHistory = playerNBT.getTagList("compactmachines3-idHistory", 10);
        if (idHistory.isEmpty()) {
            idHistory = playerNBT.getTagList("compactmachines3-coordHistory", 10); // Legacy
        }
        if (idHistory.isEmpty()) {
            // No id history, teleport back to overworld
            teleportPlayerOutOfMachineDimension(player);
            return;
        }

        // Remove the last tag (our entered data), but not before storing it and getting the pos we teleported into the machine with, THEN teleport to the new last
        NBTTagCompound enteredTag = idHistory.getCompoundTagAt(idHistory.tagCount() - 1);
        Vec3d2f pos = Vec3d2f.fromTag(enteredTag.getCompoundTag("pos"));
        idHistory.removeTag(idHistory.tagCount() - 1);

        // No id history -> Back to the overworld
        if (idHistory.tagCount() == 0) {
            teleportPlayerOutOfMachineDimension(player);
            return;
        }

        NBTTagCompound lastTag = idHistory.getCompoundTagAt(idHistory.tagCount() - 1);
        int id = lastTag.hasKey("id", 3) ? lastTag.getInteger("id") : /* Legacy */ lastTag.getInteger("coords");
        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        machineExitData.put(player.getUniqueID(), new ExitData(enteredTag.getInteger("id"), machineWorld.getTotalWorldTime()));
        if (pos == null) {
            teleportPlayerToMachine(player, id, true, null);
        } else {
            pos.setPlayerLocation(player);
        }
    }

    private static final class ExitData {
        private final int machineId;
        private final long exitTimestamp;

        private ExitData(int machineId, long exitTimestamp) {
            this.machineId = machineId;
            this.exitTimestamp = exitTimestamp;
        }
    }
}
