package com.robotgryphon.compactmachines.block;

import com.robotgryphon.compactmachines.CompactMachines;
import com.robotgryphon.compactmachines.api.tunnels.ITunnelConnectionInfo;
import com.robotgryphon.compactmachines.api.tunnels.redstone.IRedstoneReaderTunnel;
import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeData;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeDataProvider;
import com.robotgryphon.compactmachines.compat.theoneprobe.providers.CompactMachineProvider;
import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.EnumMachinePlayersBreakHandling;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.reference.Reference;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.util.PlayerUtil;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BlockCompactMachine extends Block implements IProbeDataProvider {

    private final EnumMachineSize size;

    public BlockCompactMachine(EnumMachineSize size, AbstractBlock.Properties props) {
        super(props);
        this.size = size;
    }

    @Override
    public float getDestroyProgress(BlockState state, PlayerEntity player, IBlockReader worldIn, BlockPos pos) {
        CompactMachineTile tile = (CompactMachineTile) worldIn.getBlockEntity(pos);
        float normalHardness = super.getDestroyProgress(state, player, worldIn, pos);

        if (tile == null)
            return normalHardness;

        boolean hasPlayers = tile.hasPlayersInside();


        // If there are players inside, check config for break handling
        if (hasPlayers) {
            EnumMachinePlayersBreakHandling hand = ServerConfig.MACHINE_PLAYER_BREAK_HANDLING.get();
            switch (hand) {
                case UNBREAKABLE:
                    return 0;

                case OWNER:
                    Optional<UUID> ownerUUID = tile.getOwnerUUID();
                    return ownerUUID
                            .map(uuid -> player.getUUID() == uuid ? normalHardness : 0)
                            .orElse(normalHardness);

                case ANYONE:
                    return normalHardness;
            }
        }

        // No players inside - let anyone break it
        return normalHardness;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side) {
        return false;
    }

    @Override
    public int getSignal(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
//        TODO Redstone out tunnels
//        if(!(blockAccess.getTileEntity(pos) instanceof TileEntityMachine)) {
//            return 0;
//        }
//
//        TileEntityMachine machine = (TileEntityMachine) blockAccess.getTileEntity(pos);
//        if(machine.isInsideItself()) {
//            return 0;
//        }
//
//        return machine.getRedstonePowerOutput(side.getOpposite());
        return 0;
    }

    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block changedBlock, BlockPos changedPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, changedBlock, changedPos, isMoving);

        if (world.isClientSide)
            return;

        ServerWorld serverWorld = (ServerWorld) world;

        BlockState changedState = serverWorld.getBlockState(changedPos);

        CompactMachineTile machine = (CompactMachineTile) serverWorld.getBlockEntity(pos);
        if (machine == null)
            return;

        ServerWorld compactWorld = serverWorld.getServer().getLevel(Registration.COMPACT_DIMENSION);
        if (compactWorld == null) {
            CompactMachines.LOGGER.warn("Warning: Compact Dimension was null! Cannot fetch internal state for machine neighbor change listener.");
            return;
        }

        // Determine whether it's an immediate neighbor; if so, execute ...
        Arrays.stream(Direction.values())
                .filter(hd -> pos.relative(hd).equals(changedPos))
                .findFirst()
                .ifPresent(facing -> {
                    Set<BlockPos> tunnelsForMachineSide = TunnelHelper.getTunnelsForMachineSide(machine.machineId, serverWorld, facing);
                    for (BlockPos tunnelPos : tunnelsForMachineSide) {
                        TunnelWallTile tunnelTile = (TunnelWallTile) compactWorld.getBlockEntity(tunnelPos);
                        if (tunnelTile == null) continue;

                        compactWorld.updateNeighborsAt(tunnelPos, Registration.BLOCK_TUNNEL_WALL.get());

                        ITunnelConnectionInfo connInfo = TunnelHelper.generateConnectionInfo(tunnelTile);

                        tunnelTile.getTunnelDefinition().ifPresent(tunnelDefinition -> {
                            // TODO: world notification interface for tunnels
                            if (tunnelDefinition instanceof IRedstoneReaderTunnel) {
                                // Send redstone changes into machine
                                IRedstoneReaderTunnel rrt = (IRedstoneReaderTunnel) tunnelDefinition;
                                int latestPower = world.getSignal(changedPos, facing);
                                rrt.onPowerChanged(connInfo, latestPower);
                            }
                        });
                    }
                });
    }


    public static EnumMachineSize getMachineSizeFromNBT(@Nullable CompoundNBT tag) {
        try {
            if (tag == null)
                return EnumMachineSize.TINY;

            if (!tag.contains("size"))
                return EnumMachineSize.TINY;

            String sizeFromTag = tag.getString("size");
            return EnumMachineSize.getFromSize(sizeFromTag);
        } catch (Exception ex) {
            return EnumMachineSize.TINY;
        }
    }

    public static Block getBySize(EnumMachineSize size) {
        switch (size) {
            case TINY:
                return Registration.MACHINE_BLOCK_TINY.get();

            case SMALL:
                return Registration.MACHINE_BLOCK_SMALL.get();

            case NORMAL:
                return Registration.MACHINE_BLOCK_NORMAL.get();

            case LARGE:
                return Registration.MACHINE_BLOCK_LARGE.get();

            case GIANT:
                return Registration.MACHINE_BLOCK_GIANT.get();

            case MAXIMUM:
                return Registration.MACHINE_BLOCK_MAXIMUM.get();
        }

        return Registration.MACHINE_BLOCK_NORMAL.get();
    }

    public static Item getItemBySize(EnumMachineSize size) {
        switch (size) {
            case TINY:
                return Registration.MACHINE_BLOCK_ITEM_TINY.get();

            case SMALL:
                return Registration.MACHINE_BLOCK_ITEM_SMALL.get();

            case NORMAL:
                return Registration.MACHINE_BLOCK_ITEM_NORMAL.get();

            case LARGE:
                return Registration.MACHINE_BLOCK_ITEM_LARGE.get();

            case GIANT:
                return Registration.MACHINE_BLOCK_ITEM_GIANT.get();

            case MAXIMUM:
                return Registration.MACHINE_BLOCK_ITEM_MAXIMUM.get();
        }

        return Registration.MACHINE_BLOCK_ITEM_NORMAL.get();
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player) {
        Block given = getBySize(this.size);
        ItemStack stack = new ItemStack(given, 1);

        CompoundNBT nbt = stack.getOrCreateTagElement("cm");
        nbt.putString("size", this.size.getName());

        CompactMachineTile tileEntity = (CompactMachineTile) world.getBlockEntity(pos);
        if (tileEntity != null && tileEntity.mapped()) {
            nbt.putInt("coords", tileEntity.machineId);
        }

        return stack;
    }


//    @Override
//    public String getSpecialName(ItemStack stack) {
//        return this.getStateFromMeta(stack.getItemDamage()).getValue(SIZE).getName();
//    }


    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new CompactMachineTile();
    }

    @Override
    public void destroy(IWorld world, BlockPos pos, BlockState state) {
        if (world.isClientSide()) {
            super.destroy(world, pos, state);
            return;
        }

        if (!(world.getBlockEntity(pos) instanceof CompactMachineTile)) {
            return;
        }

        CompactMachineTile te = (CompactMachineTile) world.getBlockEntity(pos);
//        WorldSavedDataMachines.INSTANCE.removeMachinePosition(te.coords);
//
//        BlockMachine.spawnItemWithNBT(world, pos, state.get(BlockMachine.SIZE), te);
//
//        ChunkLoadingMachines.unforceChunk(te.coords);

        super.destroy(world, pos, state);
    }

    @Override
    public void setPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {

        if (worldIn.isClientSide())
            return;

        ServerWorld serverWorld = (ServerWorld) worldIn;

        boolean hasProperTile = worldIn.getBlockEntity(pos) instanceof CompactMachineTile;
        if (!hasProperTile)
            return;

        CompactMachineTile tile = (CompactMachineTile) worldIn.getBlockEntity(pos);

        // The machine already has data for some reason
        if (tile.machineId != -1)
            return;

//        if (stack.hasDisplayName()) {
//            tile.setCustomName(stack.getDisplayName());
//        }

        if(!stack.hasTag())
            return;

        CompoundNBT nbt = stack.getTag();
        if(nbt == null)
            return;

        if (nbt.contains(Reference.CompactMachines.OWNER_NBT)) {
            tile.setOwner(nbt.getUUID(Reference.CompactMachines.OWNER_NBT));
        }

        if (!tile.getOwnerUUID().isPresent() && placer instanceof PlayerEntity) {
            tile.setOwner(placer.getUUID());
        }

        if (nbt.contains("cm")) {
            CompoundNBT machineData = nbt.getCompound("cm");
            if (machineData.contains("coords")) {
                int machineID = machineData.getInt("coords");
                tile.setMachineId(machineID);
            }
        }

        tile.doPostPlaced();
        tile.setChanged();
    }

    @Override
    public ActionResultType use(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        if (worldIn.isClientSide())
            return ActionResultType.SUCCESS;

        // TODO - Open GUI with machine preview
        if (player instanceof ServerPlayerEntity) {

            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            TileEntity te = worldIn.getBlockEntity(pos);
            CompactMachineTile tile = (CompactMachineTile) te;

            ItemStack mainItem = player.getMainHandItem();
            if (mainItem.isEmpty())
                return ActionResultType.PASS;

            // TODO - Item tags instead of direct item reference here
            if (mainItem.getItem() == Registration.PERSONAL_SHRINKING_DEVICE.get()) {
                // Try teleport to compact machine dimension
                PlayerUtil.teleportPlayerIntoMachine(serverPlayer, pos, size);
            }
        }

        return ActionResultType.SUCCESS;
    }

    public EnumMachineSize getSize() {
        return this.size;
    }

    @Override
    public void addProbeData(IProbeData data, PlayerEntity player, World world, BlockState state) {
        CompactMachineProvider.exec(data, world);
    }

    // 1.12.1 code
//    @Override
//    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
//        if(player.isSneaking()) {
//            return false;
//        }
//
//        if(world.isRemote || !(player instanceof EntityPlayerMP)) {
//            return true;
//        }
//
//        if(!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
//            return false;
//        }
//
//        TileEntityMachine machine = (TileEntityMachine)world.getTileEntity(pos);
//        ItemStack playerStack = player.getHeldItemMainhand();
//        if(ShrinkingDeviceUtils.isShrinkingDevice(playerStack)) {
//            TeleportationTools.tryToEnterMachine(player, machine);
//            return true;
//        }
//
//        player.openGui(compactmachines.instance, GuiIds.MACHINE_VIEW.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
//        PackageHandler.instance.sendTo(new MessageMachineContent(machine.coords), (EntityPlayerMP)player);
//        PackageHandler.instance.sendTo(new MessageMachineChunk(machine.coords), (EntityPlayerMP)player);
//
//        return true;
//    }

// TOP code
//    @Override
//    public String getID() {
//        return CompactMachines.MODID + ":" + "machine";
//    }
//
//    @Override
//    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, PlayerEntity player, World world, BlockState blockState, IProbeHitData data) {
//        String size = this.size.getName();
//        probeInfo.text(new TranslationTextComponent("machines.sizes." + size));
//
    // }
}
