package org.dave.compactmachines3.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.items.ItemHandlerHelper;
import org.dave.compactmachines3.init.Blockss;
import org.dave.compactmachines3.misc.Vec3d2f;
import org.dave.compactmachines3.reference.EnumMachineSize;
import org.dave.compactmachines3.tile.TileEntityMachine;
import org.dave.compactmachines3.utility.DimensionBlockPos;
import org.dave.compactmachines3.world.TeleporterMachines;
import org.dave.compactmachines3.world.WorldSavedDataMachines;
import org.dave.compactmachines3.world.tools.DimensionTools;
import org.dave.compactmachines3.world.tools.TeleportationTools;

public class MessageRequestMachineActionHandler implements IMessageHandler<MessageRequestMachineAction, IMessage> {
    @Override
    public IMessage onMessage(MessageRequestMachineAction message, MessageContext ctx) {
        int id = message.id;
        EntityPlayerMP serverPlayer = ctx.getServerHandler().player;

        if (message.action == MessageRequestMachineAction.Action.REFRESH) {
            serverPlayer.getServerWorld().addScheduledTask(() -> {
                if (message.id < 0) {
                    updateMachineContent(WorldSavedDataMachines.getInstance().nextId - 1, serverPlayer);
                } else if (message.id >= WorldSavedDataMachines.getInstance().nextId) {
                    updateMachineContent(0, serverPlayer);
                } else {
                    updateMachineContent(id, serverPlayer);
                }
            });
        } else if (message.action == MessageRequestMachineAction.Action.GIVE_ITEM) {
            if (!serverPlayer.canUseCommand(4, "compactmachines3 machines give"))
                return null;

            EnumMachineSize size = WorldSavedDataMachines.getInstance().machineSizes.get(id);
            ItemStack stack = new ItemStack(Blockss.machine, 1, size.getMeta());
            NBTTagCompound compound = new NBTTagCompound();
            compound.setInteger("machineId", id);
            stack.setTagCompound(compound);

            serverPlayer.getServerWorld().addScheduledTask(() -> {
                ItemHandlerHelper.giveItemToPlayer(serverPlayer, stack);
                WorldSavedDataMachines.getInstance().removeMachinePosition(id);
            });
        } else if (message.action == MessageRequestMachineAction.Action.TELEPORT_INSIDE) {
            if (!serverPlayer.canUseCommand(4, "compactmachines3 machines view"))
                return null;

            serverPlayer.getServerWorld().addScheduledTask(() -> TeleportationTools.teleportPlayerToMachine(serverPlayer, id));
        } else if (message.action == MessageRequestMachineAction.Action.TELEPORT_OUTSIDE) {
            if (!serverPlayer.canUseCommand(4, "compactmachines3 machines view"))
                return null;

            serverPlayer.getServerWorld().addScheduledTask(() -> {
                DimensionBlockPos pos = WorldSavedDataMachines.getInstance().machinePositions.get(id);
                WorldServer world = DimensionTools.getWorldServerForDimension(pos.getDimension());
                BlockPos spawnPos = TeleportationTools.getValidSpawnLocation(world, pos.getBlockPos());

                PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
                playerList.transferPlayerToDimension(serverPlayer, pos.getDimension(), new TeleporterMachines(world));
                serverPlayer.setPositionAndUpdate(spawnPos.getX() + 0.5f, spawnPos.getY() + 0.5f, spawnPos.getZ() + 0.5f);
            });
        } else if (message.action == MessageRequestMachineAction.Action.TOGGLE_LOCKED) {
            serverPlayer.getServerWorld().addScheduledTask(() -> {
                TileEntityMachine machine = WorldSavedDataMachines.getInstance().getMachine(id);
                if (machine == null)
                    return;

                if (!machine.hasOwner()) {
                    machine.setOwner(serverPlayer);
                }
                if (serverPlayer.getUniqueID().equals(machine.getOwner()) || isOp(serverPlayer)) {
                    machine.toggleLocked();
                    machine.markDirty();
                }

                updateMachineContent(id, serverPlayer);
            });
        } else if (message.action == MessageRequestMachineAction.Action.TRY_TO_ENTER) {
            serverPlayer.getServerWorld().addScheduledTask(() -> {
                TileEntityMachine machine = WorldSavedDataMachines.getInstance().getMachine(id);
                if (machine != null)
                    TeleportationTools.tryToEnterMachine(serverPlayer, machine);
            });
        } else if (message.action == MessageRequestMachineAction.Action.RESET_SPAWNPOINT) {
            serverPlayer.getServerWorld().addScheduledTask(() -> {
                TileEntityMachine machine = WorldSavedDataMachines.getInstance().getMachine(id);
                if (machine == null)
                    return;

                if (!machine.hasOwner()) {
                    machine.setOwner(serverPlayer);
                }
                if (serverPlayer.getUniqueID().equals(machine.getOwner()) || isOp(serverPlayer)) {
                   BlockPos found = TeleportationTools.findSafeSpawnpoint(machine);

                    ITextComponent tc;
                    if (found == null) {
                        tc = new TextComponentTranslation("hint.compactmachines3.no_spawnpoint_found")
                                .setStyle(new Style().setColor(TextFormatting.RED));
                    } else {
                        Vec3d spawnpoint = new Vec3d(found).add(0.5, 0, 0.5);
                        WorldSavedDataMachines.getInstance().addSpawnPoint(id, new Vec3d2f(spawnpoint, 0, 0));

                        tc = new TextComponentTranslation("hint.compactmachines3.reset_spawnpoint", spawnpoint.x, spawnpoint.y, spawnpoint.z)
                                .setStyle(new Style().setColor(TextFormatting.GREEN));
                    }

                    serverPlayer.sendStatusMessage(tc, true);
                }
            });
        }

        return null;
    }

    private static boolean isOp(EntityPlayerMP serverPlayer) {
        return serverPlayer.getServer() != null && serverPlayer.getServer().getPlayerList().canSendCommands(serverPlayer.getGameProfile());
    }

    private static void updateMachineContent(int id, EntityPlayerMP serverPlayer) {
        PackageHandler.instance.sendTo(new MessageMachineChunk(id), serverPlayer);
        PackageHandler.instance.sendTo(new MessageMachineContent(id), serverPlayer);
    }
}
