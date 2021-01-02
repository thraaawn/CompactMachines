package com.robotgryphon.compactmachines.block;

import com.robotgryphon.compactmachines.block.tiles.CompactMachineTile;
import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.compat.theoneprobe.providers.CompactMachineProvider;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeData;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeDataProvider;
import com.robotgryphon.compactmachines.config.CommonConfig;
import com.robotgryphon.compactmachines.config.ServerConfig;
import com.robotgryphon.compactmachines.core.EnumMachinePlayersBreakHandling;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.machines.CompactMachineRegistrationData;
import com.robotgryphon.compactmachines.reference.EnumMachineSize;
import com.robotgryphon.compactmachines.reference.Reference;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.tunnels.EnumTunnelSide;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.util.CompactMachineUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
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
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BlockCompactMachine extends Block implements IProbeDataProvider {

    private final EnumMachineSize size;

    public BlockCompactMachine(EnumMachineSize size, Block.Properties props) {
        super(props);
        this.size = size;
    }

    @Override
    public float getPlayerRelativeBlockHardness(BlockState state, PlayerEntity player, IBlockReader worldIn, BlockPos pos) {
        CompactMachineTile tile = (CompactMachineTile) worldIn.getTileEntity(pos);
        float normalHardness = super.getPlayerRelativeBlockHardness(state, player, worldIn, pos);

        if (tile == null)
            return normalHardness;

        boolean hasPlayers = tile.hasPlayersInside();


        // If there are players inside, check config for break handling
        if(hasPlayers) {
            EnumMachinePlayersBreakHandling hand = ServerConfig.MACHINE_PLAYER_BREAK_HANDLING.get();
            switch (hand) {
                case UNBREAKABLE:
                    return 0;

                case OWNER:
                    Optional<UUID> ownerUUID = tile.getOwnerUUID();
                    return ownerUUID
                            .map(uuid -> player.getUniqueID() == uuid ? normalHardness : 0)
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
    public int getStrongPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
        return 0;
    }

    @Override
    public int getWeakPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
//        TODO Tile Entity
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
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block blockIn, BlockPos neighbor, boolean isMoving) {
        super.neighborChanged(state, world, pos, blockIn, neighbor, isMoving);

        if (world.isRemote()) {
            return;
        }

//        TODO Tile Entity
//        if(!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
//            return;
//        }

        // Determine whether it's an immediate neighbor ...
        Direction facing = null;
        for (Direction dir : Direction.values()) {
            if (pos.offset(dir).equals(neighbor)) {
                facing = dir;
                break;
            }
        }

        // And do nothing if it isnt, e.g. diagonal
        if (facing == null) {
            return;
        }

//        TODO Tile Entity and Server Stuff
        CompactMachineTile tile = (CompactMachineTile) world.getTileEntity(pos);
        if(tile == null)
            return;

        Optional<CompactMachineRegistrationData> machineData = tile.getMachineData();
        Direction finalFacing = facing;
        ServerWorld sWorld = (ServerWorld) world;
        ServerWorld compactWorld = sWorld.getServer().getWorld(Registration.COMPACT_DIMENSION);

        if(compactWorld == null)
            return;

        machineData.ifPresent(data -> {
            Set<BlockPos> tunnels = TunnelHelper.getTunnelsForMachineSide(data.getId(), sWorld, finalFacing);
            tunnels.forEach(tun -> {
                TileEntity tTunnel = compactWorld.getTileEntity(tun);
                if(tTunnel instanceof TunnelWallTile) {
                    TunnelWallTile tTunnelReal = (TunnelWallTile) tTunnel;
                    Optional<DimensionalPosition> tunnelConnectedPosition = TunnelHelper.getTunnelConnectedPosition(tTunnelReal, EnumTunnelSide.INSIDE);
                    tunnelConnectedPosition.ifPresent(dimPos -> {
                        BlockPos bp = dimPos.getBlockPosition();
                        Optional<BlockState> connectedState = TunnelHelper.getConnectedState(compactWorld, tTunnelReal, EnumTunnelSide.INSIDE);

                        compactWorld.notifyNeighborsOfStateChange(bp, Registration.BLOCK_TUNNEL_WALL.get());
                    });
                }
            });
        });

//
//        RedstoneTunnelData tunnelData = te.getRedstoneTunnelForSide(facing);
//        if(tunnelData != null && !tunnelData.isOutput) {
//            BlockPos redstoneNeighborPos = tunnelData.pos;
//            if(redstoneNeighborPos != null && machineWorld.getTileEntity(redstoneNeighborPos) instanceof TileEntityRedstoneTunnel) {
//                machineWorld.notifyNeighborsOfStateChange(redstoneNeighborPos, Blockss.redstoneTunnel, false);
//            }
//        }
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player) {
        Block given = CompactMachineUtil.getMachineBlockBySize(this.size);
        ItemStack stack = new ItemStack(given, 1);

        CompoundNBT nbt = stack.getOrCreateTag();
        nbt.putString("size", this.size.getName());

        CompactMachineTile tileEntity = (CompactMachineTile) world.getTileEntity(pos);
        if (tileEntity != null) {
            nbt.putInt("coords", tileEntity.machineId);
        }

        stack.setTag(nbt);

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
    public void onPlayerDestroy(IWorld world, BlockPos pos, BlockState state) {
        if (world.isRemote()) {
            super.onPlayerDestroy(world, pos, state);
            return;
        }

        if (!(world.getTileEntity(pos) instanceof CompactMachineTile)) {
            return;
        }

        CompactMachineTile te = (CompactMachineTile) world.getTileEntity(pos);
//        WorldSavedDataMachines.INSTANCE.removeMachinePosition(te.coords);
//
//        BlockMachine.spawnItemWithNBT(world, pos, state.get(BlockMachine.SIZE), te);
//
//        ChunkLoadingMachines.unforceChunk(te.coords);

        super.onPlayerDestroy(world, pos, state);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {

        if (worldIn.isRemote())
            return;

        ServerWorld serverWorld = (ServerWorld) worldIn;

        boolean hasProperTile = worldIn.getTileEntity(pos) instanceof CompactMachineTile;
        if (!hasProperTile)
            return;

        CompactMachineTile tile = (CompactMachineTile) worldIn.getTileEntity(pos);

        // The machine already has data for some reason
        if (tile.machineId != -1)
            return;

//        if (stack.hasDisplayName()) {
//            tile.setCustomName(stack.getDisplayName());
//        }

        CompoundNBT nbt = stack.getOrCreateTag();

        if (nbt.contains(Reference.CompactMachines.OWNER_NBT)) {
            tile.setOwner(nbt.getUniqueId(Reference.CompactMachines.OWNER_NBT));
        }

        if (!tile.getOwnerUUID().isPresent() && placer instanceof PlayerEntity) {
            tile.setOwner(placer.getUniqueID());
        }

        if (nbt.contains("cm")) {
            CompoundNBT machineData = nbt.getCompound("cm");
            if (machineData.contains("coords")) {
                int machineID = machineData.getInt("coords");
                tile.setMachineId(machineID);

                CompactMachineUtil.updateMachineInWorldPosition(serverWorld, machineID, pos);
            }
        }

        tile.doPostPlaced();
        tile.markDirty();
    }

//        // TODO: Allow storing of schemas in machines
//        if(stack.hasTag()) {
//            if(stack.getTag().contains("coords")) {
//                int coords = stack.getTag().getInt("coords");
//                if (coords != -1) {
//                    tileEntityMachine.coords = coords;
//                    if(!world.isRemote) {
//                        WorldSavedDataMachines.INSTANCE.addMachinePosition(tileEntityMachine.coords, pos, world.provider.getDimension(), tileEntityMachine.getSize());
//                        StructureTools.setBiomeForCoords(coords, world.getBiome(pos));
//                    }
//                }
//            }
//
//            if(stack.getTag().contains("schema")) {
//                tileEntityMachine.setSchema(stack.getTag().getString("schema"));
//            }
//

//        }
//

//
//        tileEntityMachine.markDirty();
//    }


    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        if (worldIn.isRemote())
            return ActionResultType.SUCCESS;

        // TODO - Open GUI with machine preview
        if (player instanceof ServerPlayerEntity) {

            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            TileEntity te = worldIn.getTileEntity(pos);
            CompactMachineTile tile = (CompactMachineTile) te;

            ItemStack mainItem = player.getHeldItemMainhand();
            if (mainItem.isEmpty())
                return ActionResultType.PASS;

            if (mainItem.getItem() == Registration.PERSONAL_SHRINKING_DEVICE.get()) {
                // Try teleport to compact machine dimension
                CompactMachineUtil.teleportInto(serverPlayer, pos, size);
            }
        }

        return ActionResultType.SUCCESS;
    }

    public EnumMachineSize getSize() {
        return this.size;
    }

    @Override
    public void addProbeData(IProbeData data, PlayerEntity player, World world, BlockState state) {
        CompactMachineProvider.exec(data, player, world, state);
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
