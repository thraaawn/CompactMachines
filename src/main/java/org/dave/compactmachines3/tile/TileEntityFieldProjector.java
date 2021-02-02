package org.dave.compactmachines3.tile;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.dave.compactmachines3.block.BlockFieldProjector;
import org.dave.compactmachines3.init.Blockss;
import org.dave.compactmachines3.miniaturization.MultiblockRecipe;
import org.dave.compactmachines3.miniaturization.MultiblockRecipes;
import org.dave.compactmachines3.misc.ConfigurationHandler;
import org.dave.compactmachines3.world.tools.StructureTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TileEntityFieldProjector extends TileEntity implements ITickable {
    private int activeMagnitude = 0;

    protected UUID owner;

    public TileEntityFieldProjector() {
        super();
    }

    public EnumFacing getDirection() {
        if (this.getWorld().getBlockState(this.pos).getBlock() == Blockss.fieldProjector) {
            return this.getWorld().getBlockState(this.pos).getValue(BlockFieldProjector.FACING);
        }

        return EnumFacing.NORTH;
    }

    public TileEntityCraftingHologram getCraftingHologram() {
        if (activeMagnitude == 0) {
            return null;
        }

        BlockPos center = this.getPos().offset(this.getDirection(), activeMagnitude * 2);
        if (getWorld().getTileEntity(center) instanceof TileEntityCraftingHologram) {
            return (TileEntityCraftingHologram) getWorld().getTileEntity(center);
        }

        return null;
    }

    public int getActiveMagnitude() {
        return activeMagnitude;
    }

    public int getCraftingProgress() {
        TileEntityCraftingHologram te = getCraftingHologram();
        if (te == null) {
            return 0;
        }

        return te.getProgress();
    }

    public float getCraftingProgressPercent() {
        TileEntityCraftingHologram te = getCraftingHologram();
        if (te == null) {
            return 0.0f;
        }

        if (te.getRecipe() == null) {
            return 0.0f;
        }

        return (float) te.getProgress() / (float) te.getRecipe().getTicks();
    }

    public MultiblockRecipe getActiveRecipe() {
        TileEntityCraftingHologram te = getCraftingHologram();
        if (te == null) {
            return null;
        }

        return te.getRecipe();
    }

    public ItemStack getActiveCraftingResult() {
        if (getActiveRecipe() == null) {
            return ItemStack.EMPTY;
        }

        return getActiveRecipe().getTargetStack();
    }

    public List<BlockPos> getInsideBlocks() {
        if (activeMagnitude == 0) {
            return Collections.emptyList();
        }

        int fieldDimension = activeMagnitude * 2 - 1;
        int fieldRadius = (fieldDimension + 1) / 2;

        BlockPos frontTopLeft = this.getPos()
                .offset(this.getDirection(), activeMagnitude + 1)
                .offset(EnumFacing.UP, fieldRadius - 1)
                .offset(this.getDirection().rotateYCCW(), fieldRadius - 1);

        List<BlockPos> insideBlocks = new ArrayList<>();
        for (int x = 0; x < fieldDimension; x++) {
            for (int y = 0; y < fieldDimension; y++) {
                for (int z = 0; z < fieldDimension; z++) {
                    BlockPos blockToCheck = frontTopLeft
                            .offset(this.getDirection(), x)
                            .offset(EnumFacing.DOWN, y)
                            .offset(this.getDirection().rotateY(), z);

                    // ((WorldServer)world).spawnParticle(EnumParticleTypes.REDSTONE, true, blockToCheck.getX() + 0.5d, blockToCheck.getY() + 0.5d, blockToCheck.getZ() + 0.5d, 10, 0.0d, 0.0d, 0.0d, 0);

                    if (getWorld().isAirBlock(blockToCheck)) {
                        continue;
                    }

                    insideBlocks.add(blockToCheck);
                }
            }
        }

        return insideBlocks;
    }

    private TileEntityFieldProjector getMasterByAddingDirections(EnumFacing A, EnumFacing B) {
        for (int size = 1; size <= ConfigurationHandler.Settings.getMaximumMagnitude(); size++) {
            BlockPos pos = this.getPos().offset(A, size).offset(B, size);

            if (!(getWorld().getTileEntity(pos) instanceof TileEntityFieldProjector)) {
                continue;
            }
            TileEntityFieldProjector te = (TileEntityFieldProjector) getWorld().getTileEntity(pos);

            // It might be facing the wrong direction actually. If so, skip it
            if (!te.isMaster()) {
                continue;
            }

            return te;
        }

        return null;
    }

    public TileEntityFieldProjector getMasterProjector() {
        if (isMaster()) {
            return this;
        }

        if (this.getDirection() == EnumFacing.EAST) {
            // Master must be opposite, i.e. WEST
            return getMasterByAddingDirections(EnumFacing.EAST, EnumFacing.EAST);
        } else if (this.getDirection() == EnumFacing.NORTH) {
            // Master is to the north and west
            return getMasterByAddingDirections(EnumFacing.NORTH, EnumFacing.EAST);
        } else if (this.getDirection() == EnumFacing.SOUTH) {
            // Master is to the south and west
            return getMasterByAddingDirections(EnumFacing.SOUTH, EnumFacing.EAST);
        }

        return null;
    }

    public boolean isMaster() {
        return this.getDirection() == EnumFacing.WEST;
    }

    public List<BlockPos> getMissingProjectors(int magnitude) {
        int radius = magnitude * 2;

        BlockPos center = this.getPos().offset(this.getDirection(), radius);

        BlockPos opposite = center.offset(this.getDirection(), radius);
        BlockPos cw = center.offset(this.getDirection().rotateY(), radius);
        BlockPos ccw = center.offset(this.getDirection().rotateYCCW(), radius);

        List<BlockPos> missingBlocks = new ArrayList<>();
        IBlockState oppositeState = world.getBlockState(opposite);

        if (oppositeState.getBlock() != Blockss.fieldProjector || oppositeState.getValue(BlockFieldProjector.FACING) != this.getDirection().getOpposite() || world.isBlockPowered(opposite)) {
            missingBlocks.add(opposite);
        }

        IBlockState cwState = world.getBlockState(cw);
        if (cwState.getBlock() != Blockss.fieldProjector || cwState.getValue(BlockFieldProjector.FACING) != this.getDirection().rotateY().getOpposite() || world.isBlockPowered(cw)) {
            missingBlocks.add(cw);
        }

        IBlockState ccwState = world.getBlockState(ccw);
        if (ccwState.getBlock() != Blockss.fieldProjector || ccwState.getValue(BlockFieldProjector.FACING) != this.getDirection().rotateYCCW().getOpposite() || world.isBlockPowered(ccw)) {
            missingBlocks.add(ccw);
        }

        return missingBlocks;
    }

    public int getCraftingAreaMagnitude() {
        for (int magnitude = 1; magnitude <= ConfigurationHandler.Settings.getMaximumMagnitude(); magnitude++) {
            BlockPos opposite = getPos().offset(getDirection(), magnitude * 4);
            if (!(world.getTileEntity(opposite) instanceof TileEntityFieldProjector)) {
                continue;
            }

            return magnitude;
        }

        return 0;
    }

    public boolean canGenerateFieldAtMagnitude(int magnitude) {
        return getInvalidBlockInField(magnitude) == null;
    }

    public BlockPos getInvalidBlockInField(int magnitude) {
        int fieldDimension = (2 * magnitude) + 1; // (x + 1) * 2 - 1 -> 2x + 2 - 1 -> 2x + 1, which is simpler

        BlockPos center = this.getPos().offset(this.getDirection(), magnitude);
        BlockPos topLeft = center.offset(EnumFacing.UP, magnitude).offset(this.getDirection().rotateYCCW(), magnitude);

        List<BlockPos> positionsToCheck = StructureTools.getCubePositionsLegacy(topLeft, fieldDimension, fieldDimension, fieldDimension, false);
        for (BlockPos pos : positionsToCheck) {
            if (!getWorld().isAirBlock(pos)) {
                return pos;
            }
        }

        return null;
    }

    public boolean shouldRenderField() {
        // This method is only ever called on the master projector
        if (world.isBlockPowered(getPos())) {
            return false;
        }

        int magnitude = getCraftingAreaMagnitude();
        if (magnitude <= 1) {
            return false;
        }

        if (!getMissingProjectors(magnitude).isEmpty()) {
            return false;
        }

        // Then check the crafting area is free
        if (!this.canGenerateFieldAtMagnitude(magnitude)) {
            return false;
        }

        return true;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (this.getWorld().getBlockState(this.getPos()).getBlock() == Blockss.fieldProjector) {
            BlockPos centerOfField = this.getPos().offset(this.getDirection(), this.getActiveMagnitude() * 2);
            return new AxisAlignedBB(centerOfField).grow(this.getActiveMagnitude() * 3);
        } else {
            return super.getRenderBoundingBox();
        }
    }

    @Override
    public void update() {
        if (!world.isRemote)
            world.updateComparatorOutputLevel(getPos(), Blockss.fieldProjector);

        if (world.isBlockPowered(getPos())) {
            return;
        }

        int magnitude = getCraftingAreaMagnitude();
        if (magnitude <= 1) {
            return;
        }

        // There is nothing to do when we are missing one of the other field projectors
        if (!this.getMissingProjectors(magnitude).isEmpty()) {
            activeMagnitude = 0;
            return;
        }

        if (!this.canGenerateFieldAtMagnitude(magnitude)) {
            return;
        }

        activeMagnitude = magnitude;
        if (world.isRemote) {
            // The client is already done
            return;
        }

        if (!this.isMaster()) // Only run the rest of the recipe code on the master
            return;

        BlockPos center = this.getPos().offset(this.getDirection(), magnitude * 2);

        // Determine whether there is a catalyst item inside the entire field
        double growWD = magnitude + 1.5d;
        AxisAlignedBB fieldBB = new AxisAlignedBB(center).grow(growWD);
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, fieldBB);

        for (EntityItem item : items) {
            if (item.ticksExisted > ConfigurationHandler.Settings.maximumCraftingCatalystAge) {
                continue;
            }

            MultiblockRecipe multiblockRecipe = MultiblockRecipes.tryCrafting(world, getPos(), item.getItem());
            if (multiblockRecipe == null) {
                continue;
            }

            // Remove blocks from the world
            for (BlockPos pos : getInsideBlocks()) {
                world.setBlockToAir(pos);
                ((WorldServer) world).spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX(), pos.getY(), pos.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.01D);
            }

            // Reduce the number of items in the stack
            ItemStack stack = item.getItem();
            if (stack.getCount() > 1) {
                stack.setCount(stack.getCount() - 1);
                item.setItem(stack.copy());
            } else {
                item.setDead();
            }

            // Create recipe hologram
            world.setBlockState(center, Blockss.craftingHologram.getDefaultState());
            TileEntityCraftingHologram teHologram = (TileEntityCraftingHologram) world.getTileEntity(center);
            if (teHologram != null) {
                teHologram.setRecipe(multiblockRecipe);
            }
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(getPos());
            world.notifyBlockUpdate(getPos(), state, state, 3);
        }
    }


    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        owner = compound.getUniqueId("owner");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        if (hasOwner()) {
            compound.setUniqueId("owner", this.owner);
        }

        return compound;
    }

    public UUID getOwner() {
        return owner;
    }

    @SideOnly(Side.SERVER)
    public String getOwnerName() {
        GameProfile profile = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getProfileByUUID(getOwner());
        return profile == null ? null : profile.getName();
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setOwner(EntityPlayer player) {
        if (player == null) {
            return;
        }

        setOwner(player.getUniqueID());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
