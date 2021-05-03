package com.robotgryphon.compactmachines.compat.theoneprobe.providers;

import com.mojang.authlib.GameProfile;
import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.core.Tooltips;
import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeData;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.util.TranslationUtil;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ITextStyle;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.Set;

public class CompactMachineProvider {

    public static void exec(IProbeData data, World world) {
        IProbeInfo info = data.getInfo();
        IProbeHitData hitData = data.getHitData();

        addProbeInfo(info, world, hitData);
    }

    private static void addProbeInfo(IProbeInfo info, World world, IProbeHitData hitData) {
        TileEntity te = world.getBlockEntity(hitData.getPos());
        if (te instanceof CompactMachineTile) {
            CompactMachineTile machine = (CompactMachineTile) te;

            if(machine.mapped()) {
                IFormattableTextComponent id = TranslationUtil
                        .tooltip(Tooltips.Machines.ID, machine.machineId)
                        .withStyle(TextFormatting.GREEN);

                info.text(id);
            } else {
                IFormattableTextComponent newMachine = TranslationUtil
                        .message(new ResourceLocation(CompactMachines.MOD_ID, "new_machine"))
                        .withStyle(TextFormatting.GREEN);

                info.text(newMachine);
            }

            machine.getOwnerUUID().ifPresent(ownerID -> {
                // Owner Name
                PlayerEntity owner = world.getPlayerByUUID(ownerID);
                if (owner != null) {
                    GameProfile ownerProfile = owner.getGameProfile();
                    IFormattableTextComponent ownerText = TranslationUtil
                            .tooltip(Tooltips.Machines.OWNER, ownerProfile.getName())
                            .withStyle(TextFormatting.GRAY);

                    info.text(ownerText);
                }
            });

            Set<BlockPos> tunnelsForMachineSide = TunnelHelper.getTunnelsForMachineSide(machine.machineId,
                    (ServerWorld) world, hitData.getSideHit());

            IProbeInfo vertical = info.vertical(info.defaultLayoutStyle().spacing(0));

            ServerWorld cm = world.getServer().getLevel(Registration.COMPACT_DIMENSION);
            tunnelsForMachineSide.forEach(pos -> {
                TunnelWallTile tile = (TunnelWallTile) cm.getBlockEntity(pos);
                if (tile == null)
                    return;

                tile.getTunnelDefinition().ifPresent(tunnelDef -> {
                    vertical.text(
                            new StringTextComponent(pos.toString() + ": " + tunnelDef.getRegistryName().toString())
                    );
                });
            });
        }
    }
}
