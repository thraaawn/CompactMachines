package com.robotgryphon.compactmachines.tunnels.definitions;

import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.data.machines.CompactMachineData;
import com.robotgryphon.compactmachines.reference.EnumTunnelType;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.tunnels.api.IRedstoneTunnel;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.server.ServerWorld;

import java.util.Optional;

public class RedstoneTunnelDefinition extends TunnelDefinition implements IRedstoneTunnel {
    public RedstoneTunnelDefinition(EnumTunnelType type, Item item) {
        super(type, item);
    }

    @Override
    public boolean canConnectRedstone(IBlockReader world, BlockState state, BlockPos pos, Direction side) {
        return side.getAxis().isHorizontal();
    }

    @Override
    public boolean canProvidePower(BlockState state) {
        return true;
    }

    @Override
    public int getStrongPower(IBlockReader world, BlockState state, BlockPos pos, Direction side) {
        return 0;
    }

    @Override
    public int getWeakPower(IBlockReader world, BlockState state, BlockPos pos, Direction side) {
        // Read info from tile entity data
        TileEntity tileEntity = world.getTileEntity(pos);
        if(tileEntity instanceof TunnelWallTile) {
            TunnelWallTile tile = (TunnelWallTile) tileEntity;
            Optional<CompactMachineData> machineInfo = tile.getMachineInfo();

            // No tile entity data for machine?
            if(!machineInfo.isPresent())
                return 0;

            CompactMachineData compactMachineData = machineInfo.get();
            if(!compactMachineData.isPlacedInWorld())
                return 0;

            if(world instanceof ServerWorld) {
                DimensionalPosition realPosition = compactMachineData.getOutsidePosition((ServerWorld) world);
            }
        }

        return 0;
    }
}
