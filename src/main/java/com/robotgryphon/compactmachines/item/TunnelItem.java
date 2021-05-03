package com.robotgryphon.compactmachines.item;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.core.Tooltips;
import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.block.walls.TunnelWallBlock;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.api.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.api.tunnels.redstone.IRedstoneReaderTunnel;
import com.robotgryphon.compactmachines.util.TranslationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

import net.minecraft.item.Item.Properties;

public class TunnelItem extends Item {
    public TunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public ITextComponent getName(ItemStack stack) {
        String key = getDefinition(stack)
                .map(def -> {
                    ResourceLocation id = def.getRegistryName();
                    return "item." + id.getNamespace() + ".tunnels." + id.getPath().replace('/', '.');
                })
                .orElse("item." + CompactMachines.MOD_ID + ".tunnels.unnamed");

        return new TranslationTextComponent(key);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        getDefinition(stack).ifPresent(tunnelDef -> {
            if (Screen.hasShiftDown()) {
                IFormattableTextComponent type = new TranslationTextComponent("tooltip." + CompactMachines.MOD_ID + ".tunnel_type", tunnelDef.getRegistryName())
                        .withStyle(TextFormatting.GRAY)
                        .withStyle(TextFormatting.ITALIC);

                tooltip.add(type);
            } else {
                tooltip.add(TranslationUtil.tooltip(Tooltips.HINT_HOLD_SHIFT)
                        .withStyle(TextFormatting.DARK_GRAY)
                        .withStyle(TextFormatting.ITALIC));
            }
        });
    }

    @Override
    public void fillItemCategory(ItemGroup group, NonNullList<ItemStack> items) {
        if (this.allowdedIn(group)) {
            IForgeRegistry<TunnelDefinition> definitions = GameRegistry.findRegistry(TunnelDefinition.class);
            definitions.getValues().forEach(def -> {
                ItemStack withDef = new ItemStack(this, 1);
                CompoundNBT defTag = withDef.getOrCreateTagElement("definition");
                defTag.putString("id", def.getRegistryName().toString());

                items.add(withDef);
            });
        }
    }

    public static Optional<TunnelDefinition> getDefinition(ItemStack stack) {
        CompoundNBT defTag = stack.getOrCreateTagElement("definition");
        if (defTag.isEmpty() || !defTag.contains("id"))
            return Optional.empty();

        ResourceLocation defId = new ResourceLocation(defTag.getString("id"));
        IForgeRegistry<TunnelDefinition> tunnelReg = GameRegistry.findRegistry(TunnelDefinition.class);

        if (!tunnelReg.containsKey(defId))
            return Optional.empty();

        return Optional.ofNullable(tunnelReg.getValue(defId));
    }

    @Override
    public ActionResultType useOn(ItemUseContext context) {
        World w = context.getLevel();
        if (w.isClientSide())
            return ActionResultType.SUCCESS;

        BlockPos pos = context.getClickedPos();
        BlockState blockState = w.getBlockState(pos);

        if (blockState.getBlock() != Registration.BLOCK_SOLID_WALL.get())
            return ActionResultType.FAIL;

        if (context.getPlayer() instanceof ServerPlayerEntity) {
            ItemStack is = context.getItemInHand();
            Item i = is.getItem();

            TunnelItem ti = ((TunnelItem) i);
            Optional<TunnelDefinition> definition = ti.getDefinition(context.getItemInHand());

            definition.ifPresent(def -> {
                BlockState tunnelState = Registration.BLOCK_TUNNEL_WALL.get()
                        .defaultBlockState()
                        .setValue(TunnelWallBlock.TUNNEL_SIDE, context.getClickedFace());

                // Redstone Support
                boolean redstone = (def instanceof IRedstoneReaderTunnel);
                tunnelState = tunnelState.setValue(TunnelWallBlock.REDSTONE, redstone);
                w.setBlock(pos, tunnelState, 3);

                // Get the server and add a deferred task - allows the tile to be created on the client first
                MinecraftServer server = ((ServerWorld) context.getLevel()).getServer();
                server.submitAsync(() -> {
                    TunnelWallTile tile = (TunnelWallTile) context.getLevel().getBlockEntity(context.getClickedPos());
                    tile.setTunnelType(def.getRegistryName());
                });

                is.shrink(1);
            });

            return ActionResultType.CONSUME;
        }

        return ActionResultType.FAIL;
    }

    /**
     * Implementation for easily swapping a tunnel type for another.
     *
     * @param type
     * @param player
     * @param hand
     * @return
     */
    protected ActionResult<ItemStack> swapTunnelType(Item type, PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity) {
            if (player.isShiftKeyDown()) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                ItemStack stack = serverPlayer.getItemInHand(hand);
                ItemStack newStack = new ItemStack(type, stack.getCount());

                serverPlayer.setItemInHand(hand, newStack);

                IFormattableTextComponent msg = new StringTextComponent("switch state")
                        .withStyle(TextFormatting.GOLD);

                serverPlayer.displayClientMessage(msg, true);
                return ActionResult.consume(newStack);
            }
        }

        return ActionResult.pass(player.getItemInHand(hand));
    }


}
