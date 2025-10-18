package com.spellarchives.block;

import com.spellarchives.tile.TileSpellArchive;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockSpellArchive extends BlockContainer {

    public BlockSpellArchive() {
        super(Material.WOOD);
        setHardness(2.0F);
        setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileSpellArchive();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof TileSpellArchive)) return true;

        ItemStack held = playerIn.getHeldItem(hand);

        if (!worldIn.isRemote) {
            TileSpellArchive archive = (TileSpellArchive) te;
            if (!held.isEmpty() && archive.isSpellBook(held)) {
                int added = archive.addBooks(held, held.getCount());
                if (added > 0) held.shrink(added);
                return true;
            }
            playerIn.openGui(com.spellarchives.SpellArchives.instance, com.spellarchives.client.GuiHandler.GUI_SPELL_ARCHIVE, worldIn, pos.getX(), pos.getY(), pos.getZ());
        }

        return true;
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity te, ItemStack tool) {
        if (te instanceof TileSpellArchive) {
            ItemStack stack = new ItemStack(net.minecraft.item.Item.getItemFromBlock(this));

            NBTTagCompound tag = new NBTTagCompound();
            te.writeToNBT(tag);

            NBTTagCompound blockTag = new NBTTagCompound();
            blockTag.setTag("BlockEntityTag", tag);
            stack.setTagCompound(blockTag);

            spawnAsEntity(world, pos, stack);
        } else {
            super.harvestBlock(world, player, pos, state, te, tool);
        }
    }
}
