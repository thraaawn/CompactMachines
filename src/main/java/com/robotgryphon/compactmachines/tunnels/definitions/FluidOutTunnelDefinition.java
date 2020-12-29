package com.robotgryphon.compactmachines.tunnels.definitions;

import com.robotgryphon.compactmachines.block.tiles.TunnelWallTile;
import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.teleportation.DimensionalPosition;
import com.robotgryphon.compactmachines.tunnels.EnumTunnelSide;
import com.robotgryphon.compactmachines.tunnels.TunnelDefinition;
import com.robotgryphon.compactmachines.tunnels.TunnelHelper;
import com.robotgryphon.compactmachines.tunnels.api.ICapableTunnel;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.Optional;

public class FluidOutTunnelDefinition extends TunnelDefinition implements ICapableTunnel {
    public FluidOutTunnelDefinition() {
        super(Registration.ITEM_FLUID_TUNNEL_OUT.get());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getInternalCapability(ServerWorld compactWorld, BlockPos tunnelPos, @Nonnull Capability<T> cap, Direction side) {
        return LazyOptional.empty();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getExternalCapability(ServerWorld world, BlockPos tunnelPos, @Nonnull Capability<T> cap, Direction side) {
        TileEntity te = world.getTileEntity(tunnelPos);

        // Rough implementation here; should filter by the forge fluid caps though
        // Also, some pipes are caching; should definitely be sending packets to update internal connections
        if (te instanceof TunnelWallTile) {
            TunnelWallTile twt = (TunnelWallTile) te;

            Optional<BlockState> connectedState = TunnelHelper.getConnectedState(world, twt, EnumTunnelSide.OUTSIDE);
            if (!connectedState.isPresent())
                return LazyOptional.empty();

            // link to external block capability
            Optional<DimensionalPosition> connectedPosition = twt.getConnectedPosition();
            if (!connectedPosition.isPresent())
                return LazyOptional.empty();

            DimensionalPosition dimensionalPosition = connectedPosition.get();
            // CompactMachines.LOGGER.debug(String.format("[%s] %s %s", 0, dimensionalPosition.getDimension(), dimensionalPosition.getPosition()));

            Optional<ServerWorld> connectedWorld = dimensionalPosition.getWorld(world);
            if (!connectedWorld.isPresent())
                return LazyOptional.empty();

            ServerWorld csw = connectedWorld.get();

            BlockPos connectedPos = dimensionalPosition.getBlockPosition();
            if (connectedState.get().hasTileEntity()) {
                TileEntity connectedTile = csw.getTileEntity(connectedPos);
                if (connectedTile != null) {
                    LazyOptional<T> sidedCap = connectedTile.getCapability(cap, twt.getTunnelSide().getOpposite());
                    if(sidedCap.isPresent())
                        return sidedCap;

                    LazyOptional<T> capability = connectedTile.getCapability(cap, null);
                    return capability;
                }
            }

            return LazyOptional.empty();
        }

        return LazyOptional.empty();
    }

    @Override
    public int getTunnelRingColor() {
        return 0xFF0000F0;
    }

    /**
     * Gets the color for the indicator at the top-right of the block texture.
     *
     * @return
     */
    @Override
    public int getTunnelIndicatorColor() {
        return TunnelDefinition.NO_INDICATOR_COLOR;
    }
}
