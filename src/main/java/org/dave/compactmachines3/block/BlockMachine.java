package org.dave.compactmachines3.block;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.dave.compactmachines3.CompactMachines3;
import org.dave.compactmachines3.compat.ITopInfoProvider;
import org.dave.compactmachines3.init.Blockss;
import org.dave.compactmachines3.misc.ConfigurationHandler;
import org.dave.compactmachines3.network.MessageMachineChunk;
import org.dave.compactmachines3.network.MessageMachineContent;
import org.dave.compactmachines3.network.PackageHandler;
import org.dave.compactmachines3.reference.EnumMachineSize;
import org.dave.compactmachines3.reference.GuiIds;
import org.dave.compactmachines3.tile.TileEntityMachine;
import org.dave.compactmachines3.tile.TileEntityRedstoneTunnel;
import org.dave.compactmachines3.tile.TileEntityTunnel;
import org.dave.compactmachines3.utility.ShrinkingDeviceUtils;
import org.dave.compactmachines3.world.ChunkLoadingMachines;
import org.dave.compactmachines3.world.WorldSavedDataMachines;
import org.dave.compactmachines3.world.data.RedstoneTunnelData;
import org.dave.compactmachines3.world.tools.DimensionTools;
import org.dave.compactmachines3.world.tools.TeleportationTools;

import java.util.ArrayList;
import java.util.List;

public class BlockMachine extends BlockBase implements IMetaBlockName, ITileEntityProvider, ITopInfoProvider {

    public static final PropertyEnum<EnumMachineSize> SIZE = PropertyEnum.create("size", EnumMachineSize.class);

    public BlockMachine(Material material) {
        super(material);

        this.setHardness(8.0F);
        this.setResistance(20.0F);

        this.setCreativeTab(CompactMachines3.CREATIVE_TAB);

        setDefaultState(blockState.getBaseState().withProperty(SIZE, EnumMachineSize.TINY));
    }

    @SideOnly(Side.CLIENT)
    public void initModel() {
        Item itemBlockMachine = Item.getItemFromBlock(Blockss.machine);
        for(EnumMachineSize size : EnumMachineSize.values()) {
            ModelLoader.setCustomModelResourceLocation(itemBlockMachine, size.getMeta(), new ModelResourceLocation(itemBlockMachine.getRegistryName(), "size=" + size.getName()));
        }
    }

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
        return 0;
    }

    @Override
    public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, EnumFacing side) {
        if(!(blockAccess.getTileEntity(pos) instanceof TileEntityMachine)) {
            return 0;
        }

        TileEntityMachine machine = (TileEntityMachine) blockAccess.getTileEntity(pos);
        // If we are on the client, this should not be called
        if(machine.getWorld().isRemote || machine.isInsideItself()) {
            return 0;
        }

        return machine.getRedstonePowerOutput(side.getOpposite());
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos what) {
        super.neighborChanged(state, world, pos, blockIn, what);

        if(world.isRemote) {
            return;
        }

        if(!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
            return;
        }

        // Determine whether it's an immediate neighbor ...
        EnumFacing facing = null;
        for(EnumFacing dir : EnumFacing.values()) {
            if(pos.offset(dir).equals(what)) {
                facing = dir;
                break;
            }
        }

        // And do nothing if it isnt, e.g. diagonal
        if(facing == null) {
            return;
        }

        // Make sure we don't stack overflow when we get in a notifyBlockChange loop.
        // Just ensure only a single notification happens per tick.
        TileEntityMachine te = (TileEntityMachine) world.getTileEntity(pos);
        if(te.isInsideItself() || te.alreadyNotifiedOnTick) {
            return;
        }

        WorldServer machineWorld = DimensionTools.getServerMachineWorld();
        BlockPos neighborPos = te.getConnectedBlockPosition(facing);
        if(neighborPos != null && machineWorld.getTileEntity(neighborPos) instanceof TileEntityTunnel) {
            machineWorld.notifyNeighborsOfStateChange(neighborPos, Blockss.tunnel, false);
            te.alreadyNotifiedOnTick = true;
        }


        RedstoneTunnelData tunnelData = te.getRedstoneTunnelForSide(facing);
        if(tunnelData != null && !tunnelData.isOutput) {
            BlockPos redstoneNeighborPos = tunnelData.pos;
            if(redstoneNeighborPos != null && machineWorld.getTileEntity(redstoneNeighborPos) instanceof TileEntityRedstoneTunnel) {
                machineWorld.notifyNeighborsOfStateChange(redstoneNeighborPos, Blockss.redstoneTunnel, false);
            }
        }
    }

    @Override
    public void getSubBlocks(CreativeTabs itemIn, NonNullList<ItemStack> items) {
        for(EnumMachineSize size : EnumMachineSize.values()) {
            items.add(new ItemStack(this, 1, size.getMeta()));
        }

    }

    @Override
    public int damageDropped(IBlockState state) {
        return this.getMetaFromState(state);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(Item.getItemFromBlock(this), 1, this.getMetaFromState(state));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, SIZE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(SIZE).getMeta();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(SIZE, EnumMachineSize.getFromMeta(meta));
    }

    @Override
    public String getSpecialName(ItemStack stack) {
        return this.getStateFromMeta(stack.getItemDamage()).getValue(SIZE).getName();
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityMachine();
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        return new ArrayList<>();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if(world.isRemote) {
            super.breakBlock(world, pos, state);
            return;
        }

        if(!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
            return;
        }

        TileEntityMachine te = (TileEntityMachine) world.getTileEntity(pos);
        WorldSavedDataMachines.getInstance().removeMachinePosition(te.id);

        BlockMachine.spawnItemWithNBT(world, pos, state.getValue(BlockMachine.SIZE), te);

        ChunkLoadingMachines.unforceChunk(te.id, te.getRoomPos());
        world.removeTileEntity(pos);

        super.breakBlock(world, pos, state);
    }

    public static void spawnItemWithNBT(World world, BlockPos pos, EnumMachineSize size, TileEntityMachine te) {
        if(world.isRemote) {
           return;
        }

        ItemStack stack = new ItemStack(Blockss.machine, 1, size.getMeta());
        // If a player places a machine, doesn't use it, and picks it back up, they can't stack it as it has NBT data.
        if (ConfigurationHandler.MachineSettings.allowPickupEmptyMachines && te.id == -1 && !te.hasNewSchema()) {
            // Don't add NBT data if the config value is set to true and the machine id is -1 and it doesn't have a schema
        } else {
            NBTTagCompound compound = new NBTTagCompound();
            compound.setInteger("machineId", te.id);
            if (te.hasOwner()) {
                compound.setUniqueId("owner", te.getOwner());
            }
            if (te.hasNewSchema()) {
                compound.setString("schema", te.getSchemaName());
            }
            stack.setTagCompound(compound);
        }

        if(te.getCustomName().length() > 0) {
            stack.setStackDisplayName(te.getCustomName());
        }



        EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, stack);
        entityItem.lifespan = 1200;
        entityItem.setPickupDelay(10);

        float motionMultiplier = 0.02F;
        entityItem.motionX = 0.0f;
        entityItem.motionY = (float) world.rand.nextGaussian() * motionMultiplier + 0.1F;
        entityItem.motionZ = 0.0f;

        world.spawnEntity(entityItem);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);

        if (!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
            // Not a tile entity machine. Should not happen.
            return;
        }

        TileEntityMachine tileEntityMachine = (TileEntityMachine) world.getTileEntity(pos);
        if (tileEntityMachine.id != -1) {
            // The machine already has data for some reason
            return;
        }

        // TODO: Allow storing of schemas in machines
        if(stack.hasTagCompound()) {
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound.hasKey("machineId") || tagCompound.hasKey("coords")) {
                int id = tagCompound.hasKey("machineId") ? tagCompound.getInteger("machineId") : tagCompound.getInteger("coords");
                if (id != -1) {
                    tileEntityMachine.id = id;
                    if(!world.isRemote) {
                        WorldSavedDataMachines.getInstance().addMachinePosition(tileEntityMachine.id, pos, world.provider.getDimension());
                        WorldSavedDataMachines.getInstance().addMachineSize(tileEntityMachine.id, tileEntityMachine.getSize());
                        tileEntityMachine.syncRoomPos(); // Sync the roomPos if it already exists

                        // This should be set when a player enters the machine for the first time anyways, and this breaks with the grid system as the position
                        // of the room has not yet been determined/generated.
                        // StructureTools.setBiomeForMachineId(id, world.getBiome(pos));
                    }
                }
            }

            if (tagCompound.hasKey("schema")) {
                tileEntityMachine.setSchema(tagCompound.getString("schema"));
            }

            if(stack.hasDisplayName()) {
                tileEntityMachine.setCustomName(stack.getDisplayName());
            }

            if(tagCompound.hasKey("ownerLeast") && tagCompound.hasKey("ownerMost")) {
                tileEntityMachine.setOwner(tagCompound.getUniqueId("owner"));
            }
        }

        if(!tileEntityMachine.hasOwner() && placer instanceof EntityPlayer) {
            tileEntityMachine.setOwner((EntityPlayer)placer);
        }

        tileEntityMachine.markDirty();
    }



    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if(player.isSneaking()) {
            return false;
        }

        if(world.isRemote || !(player instanceof EntityPlayerMP)) {
            return true;
        }

        if(!(world.getTileEntity(pos) instanceof TileEntityMachine)) {
            return false;
        }

        TileEntityMachine machine = (TileEntityMachine)world.getTileEntity(pos);
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        ItemStack playerStack = serverPlayer.getHeldItemMainhand();
        if(ShrinkingDeviceUtils.isShrinkingDevice(playerStack)) {
            TeleportationTools.tryToEnterMachine(serverPlayer, machine);
            return true;
        }

        player.openGui(CompactMachines3.instance, GuiIds.MACHINE_VIEW.ordinal(), world, pos.getX(), pos.getY(), pos.getZ());
        PackageHandler.instance.sendTo(new MessageMachineContent(machine.id), serverPlayer);
        PackageHandler.instance.sendTo(new MessageMachineChunk(machine.id), serverPlayer);

        return true;
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        TileEntity te = world.getTileEntity(data.getPos());
        if(te instanceof TileEntityMachine) {
            TileEntityMachine machine = (TileEntityMachine) te;
            if(machine.isInsideItself()) {
                probeInfo.horizontal().text(TextFormatting.DARK_RED + "{*tooltip.compactmachines3.machine.stopitsoaryn*}" + TextFormatting.RESET);
                return;
            }

            String nameOrId = "";
            if(machine.id < 0 && machine.getCustomName().length() == 0) {
                nameOrId = "{*tooltip.compactmachines3.machine.id.unused*}";
            } else if(machine.getCustomName().length() > 0) {
                nameOrId = machine.getCustomName();
            } else {
                nameOrId = "#" + machine.id;
            }

            probeInfo.horizontal().text(TextFormatting.GREEN + "{*tooltip.compactmachines3.machine.id*} " + TextFormatting.YELLOW + nameOrId + TextFormatting.RESET);
            if(player.isCreative() && mode == ProbeMode.EXTENDED) {
                if(machine.hasNewSchema()) {
                    String schemaName = machine.getSchemaName();
                    probeInfo.horizontal().text(TextFormatting.RED + "{*tooltip.compactmachines3.machine.schema*} " + TextFormatting.YELLOW + schemaName + TextFormatting.RESET);
                }
            }

            String translate = "enumfacing." + data.getSideHit().getName();
            probeInfo.horizontal()
                    .item(new ItemStack(Items.COMPASS))
                    .text(TextFormatting.YELLOW + "{*" + translate + "*}" + TextFormatting.RESET);

            ItemStack connectedStack = machine.getConnectedPickBlock(data.getSideHit());
            if(connectedStack != null && !connectedStack.isEmpty()) {
                probeInfo.horizontal().item(connectedStack).itemLabel(connectedStack);
            }

        }
    }
}
