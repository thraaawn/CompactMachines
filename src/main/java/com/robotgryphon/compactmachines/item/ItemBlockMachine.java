package com.robotgryphon.compactmachines.item;

import com.mojang.authlib.GameProfile;
import com.robotgryphon.compactmachines.api.core.Tooltips;
import com.robotgryphon.compactmachines.block.BlockCompactMachine;
import com.robotgryphon.compactmachines.data.persistent.CompactMachineData;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.reference.Reference;
import com.robotgryphon.compactmachines.util.PlayerUtil;
import com.robotgryphon.compactmachines.util.TranslationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.*;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ItemBlockMachine extends BlockItem {

    public ItemBlockMachine(Block blockIn, EnumMachineSize size, Properties builder) {
        super(blockIn, builder);
    }

    @Override
    public void fillItemCategory(ItemGroup group, NonNullList<ItemStack> items) {
        super.fillItemCategory(group, items);
    }

    public static Optional<Integer> getMachineId(ItemStack stack) {
        if (!stack.hasTag())
            return Optional.empty();

        CompoundNBT machineData = stack.getTagElement("cm");
        if (machineData == null)
            return Optional.empty();

        if (machineData.contains("coords")) {
            int c = machineData.getInt("coords");
            return c > -1 ? Optional.of(c) : Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    protected boolean canPlace(BlockItemUseContext ctx, BlockState state) {
        boolean s = super.canPlace(ctx, state);
        if (!s) return false;

        ItemStack stack = ctx.getItemInHand();
        World level = ctx.getLevel();

        if(level.isClientSide)
            return true;

        MinecraftServer serv = level.getServer();
        return getMachineId(stack)
                .map(id -> {
                    // Need to determine if another machine with this ID already on server
                    CompactMachineData extern = CompactMachineData.get(serv);
                    return extern.isPlaced(id);
                })
                .orElse(true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn);



        // We need NBT data for the rest of this
        if (stack.hasTag()) {

            CompoundNBT nbt = stack.getTag();

            getMachineId(stack).ifPresent(id -> {
                tooltip.add(TranslationUtil.tooltip(Tooltips.Machines.ID, id));
            });

            if (nbt.contains(Reference.CompactMachines.OWNER_NBT)) {
                UUID owner = nbt.getUUID(Reference.CompactMachines.OWNER_NBT);
                Optional<GameProfile> playerProfile = PlayerUtil.getProfileByUUID(worldIn, owner);

                IFormattableTextComponent player = playerProfile
                        .map(p -> (IFormattableTextComponent) new StringTextComponent(p.getName()))
                        .orElse(TranslationUtil.tooltip(Tooltips.UNKNOWN_PLAYER_NAME));

                IFormattableTextComponent ownerText = TranslationUtil.tooltip(Tooltips.Machines.OWNER)
                        .append(player);

                tooltip.add(ownerText);
            }

        }

        if (Screen.hasShiftDown()) {
            Block b = Block.byItem(stack.getItem());
            if (b instanceof BlockCompactMachine) {
                EnumMachineSize size = ((BlockCompactMachine) b).getSize();
                int internalSize = size.getInternalSize();

                IFormattableTextComponent text = TranslationUtil.tooltip(Tooltips.Machines.SIZE, internalSize)
                        .withStyle(TextFormatting.YELLOW);

                tooltip.add(text);
            }
        } else {
            IFormattableTextComponent text = TranslationUtil.tooltip(Tooltips.HINT_HOLD_SHIFT)
                    .withStyle(TextFormatting.DARK_GRAY)
                    .withStyle(TextFormatting.ITALIC);

            tooltip.add(text);
        }
    }
}
