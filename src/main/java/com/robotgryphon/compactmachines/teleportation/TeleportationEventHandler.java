package com.robotgryphon.compactmachines.teleportation;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.core.Messages;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.persistent.CompactRoomData;
import com.robotgryphon.compactmachines.util.TranslationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CompactMachines.MOD_ID)
public class TeleportationEventHandler {

    @SubscribeEvent
    public static void onEnderTeleport(final EnderTeleportEvent evt) {
        Vector3d target = new Vector3d(
                evt.getTargetX(),
                evt.getTargetY(),
                evt.getTargetZ()
        );

        Entity ent = evt.getEntity();
        doEntityTeleportHandle(evt, target, ent);
    }

    @SubscribeEvent
    public static void onEntityTeleport(final EntityTeleportEvent evt) {
        // Allow teleport commands, we don't want to trap people anywhere
        if (evt instanceof EntityTeleportEvent.TeleportCommand)
            return;

        Entity ent = evt.getEntity();
        doEntityTeleportHandle(evt, evt.getTarget(), ent);
    }


    /**
     * Helper to determine if an event should be canceled, by determining if a target is outside
     * a machine's bounds.
     *
     * @param entity Entity trying to teleport.
     * @param target Teleportation target location.
     * @return True if teleportation should be cancelled; false otherwise.
     */
    private static boolean cancelOutOfBoxTeleport(Entity entity, Vector3d target) {
        MinecraftServer serv = entity.getServer();
        if (serv == null)
            return false;

        ChunkPos machineChunk = new ChunkPos(entity.xChunk, entity.zChunk);

        CompactRoomData intern = CompactRoomData.get(serv);
        if (intern == null)
            return false;

        return intern.getInnerBounds(machineChunk)
                .map(bounds -> !bounds.contains(target))
                .orElse(false);
    }

    private static void doEntityTeleportHandle(EntityEvent evt, Vector3d target, Entity ent) {
        if (ent.level.dimension() == Registration.COMPACT_DIMENSION) {
            if (cancelOutOfBoxTeleport(ent, target) && evt.isCancelable()) {
                if (ent instanceof ServerPlayerEntity) {
                    ((ServerPlayerEntity) ent).displayClientMessage(
                            TranslationUtil.message(Messages.TELEPORT_OUT_OF_BOUNDS, ent.getName())
                                    .withStyle(TextFormatting.RED)
                                    .withStyle(TextFormatting.ITALIC),
                            true
                    );
                }
                evt.setCanceled(true);
            }
        }
    }
}
