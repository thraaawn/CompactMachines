package com.robotgryphon.compactmachines.block.walls;

import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeData;
import com.robotgryphon.compactmachines.compat.theoneprobe.IProbeDataProvider;
import com.robotgryphon.compactmachines.compat.theoneprobe.providers.TunnelProvider;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.data.machines.CompactMachineRegistrationData;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.tunnels.EnumTunnelSide;
import com.robotgryphon.compactmachines.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.tunnels.api.IRedstoneTunnel;
import com.robotgryphon.compactmachines.util.CompactMachineUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class TunnelWallBlock extends WallBlock implements IProbeDataProvider {
    public static DirectionProperty TUNNEL_SIDE = DirectionProperty.create("tunnel_side", Direction.values());
    public static DirectionProperty CONNECTED_SIDE = DirectionProperty.create("connected_side", Direction.values());

    public static BooleanProperty REDSTONE = BooleanProperty.create("redstone");

    public TunnelWallBlock(Properties props) {
        super(props);
        setDefaultState(getStateContainer().getBaseState()
                .with(CONNECTED_SIDE, Direction.UP)
                .with(TUNNEL_SIDE, Direction.UP)
                .with(REDSTONE, false)
        );
    }

    public Optional<TunnelDefinition> getTunnelInfo(IBlockReader world, BlockPos pos) {
        TunnelWallTile tile = (TunnelWallTile) world.getTileEntity(pos);
        if (tile == null)
            return Optional.empty();

        return tile.getTunnelDefinition();
    }

    @Override
    public boolean canConnectRedstone(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction side) {
        Optional<TunnelDefinition> tunnelInfo = getTunnelInfo(world, pos);
        if (!tunnelInfo.isPresent())
            return false;

        TunnelDefinition definition = tunnelInfo.get();
        if (definition instanceof IRedstoneTunnel) {
            return ((IRedstoneTunnel) definition).canConnectRedstone(world, state, pos, side);
        }

        return false;
    }

    @Override
    public boolean canProvidePower(BlockState state) {
        return state.get(REDSTONE);
    }

    @Override
    public int getStrongPower(BlockState state, IBlockReader world, BlockPos pos, Direction side) {
        Optional<TunnelDefinition> tunnelInfo = getTunnelInfo(world, pos);
        if (!tunnelInfo.isPresent())
            return 0;

        TunnelDefinition definition = tunnelInfo.get();
        if (definition instanceof IRedstoneTunnel) {
            return ((IRedstoneTunnel) definition).getStrongPower(world, state, pos, side);
        }

        return 0;
    }

    @Override
    public int getWeakPower(BlockState state, IBlockReader world, BlockPos pos, Direction side) {
        Optional<TunnelDefinition> tunnelInfo = getTunnelInfo(world, pos);
        if (!tunnelInfo.isPresent())
            return 0;

        TunnelDefinition definition = tunnelInfo.get();
        if (definition instanceof IRedstoneTunnel) {
            return ((IRedstoneTunnel) definition).getWeakPower(world, state, pos, side);
        }

        return 0;
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        if (worldIn.isRemote())
            return ActionResultType.SUCCESS;


        if (player.isSneaking()) {
            // TODO Remove tunnelDef and return
            Optional<TunnelDefinition> tunnelDef = getTunnelInfo(worldIn, pos);

            if (!tunnelDef.isPresent())
                return ActionResultType.FAIL;

            BlockState solidWall = Registration.BLOCK_SOLID_WALL.get().getDefaultState();

            worldIn.setBlockState(pos, solidWall);

            TunnelDefinition tunnelRegistration = tunnelDef.get();
            Item item = tunnelRegistration.getItem();
            ItemStack stack = new ItemStack(item, 1);

            ItemEntity ie = new ItemEntity(worldIn, player.getPosX(), player.getPosY(), player.getPosZ(), stack);
            worldIn.addEntity(ie);

//                        IFormattableTextComponent t = new StringTextComponent(tunnelRegistration.getRegistryName().toString())
//                                .mergeStyle(TextFormatting.GRAY);
//
//                        player.sendStatusMessage(t, true);
        } else {
            // Rotate tunnel
            Direction dir = state.get(CONNECTED_SIDE);
            Direction nextDir = TunnelHelper.getNextDirection(dir);

            worldIn.setBlockState(pos, state.with(CONNECTED_SIDE, nextDir));
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(TUNNEL_SIDE).add(CONNECTED_SIDE).add(REDSTONE);
        super.fillStateContainer(builder);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new TunnelWallTile();
    }

    @Override
    public void addProbeData(IProbeData data, PlayerEntity player, World world, BlockState state) {
        TunnelProvider.exec(data, player, world, state);
    }

    @Override
    public void neighborChanged(BlockState state, @Nonnull World world, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, world, pos, blockIn, fromPos, isMoving);

        if(world.isRemote)
            return;

        ServerWorld serverWorld = (ServerWorld) world;
        TunnelWallTile tile = (TunnelWallTile) serverWorld.getTileEntity(pos);
        if(tile == null)
            return;

        Optional<CompactMachineRegistrationData> machineInfo = tile.getMachineInfo();
        if(!machineInfo.isPresent())
            return;

        CompactMachineRegistrationData machineData = machineInfo.get();
        Block block = CompactMachineUtil.getMachineBlockBySize(machineData.getSize());

        Optional<DimensionalPosition> tunnelConnectedPosition = TunnelHelper.getTunnelConnectedPosition(tile, EnumTunnelSide.OUTSIDE);
        tunnelConnectedPosition.ifPresent(connPos -> {
            Optional<ServerWorld> connectedWorld = connPos.getWorld(serverWorld);
            connectedWorld.ifPresent(cw -> {
                BlockPos connPospos = connPos.getBlockPosition();
                cw.notifyNeighborsOfStateChange(connPospos, block);
            });
        });

    }
}
