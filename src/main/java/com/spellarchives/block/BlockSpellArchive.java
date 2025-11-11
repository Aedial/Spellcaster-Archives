package com.spellarchives.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.spellarchives.SpellArchives;
import com.spellarchives.client.GuiHandler;
import com.spellarchives.tile.TileSpellArchive;


/**
 * Block for the Spell Archive. Holds a {@link TileSpellArchive} that aggregates spell
 * books. Presents a model with variable stripes based on the number of distinct spell
 * types stored. Right-click inserts held spell books or opens the GUI when an invalid
 * item or empty hand is used.
 */
@SuppressWarnings("deprecation")
public class BlockSpellArchive extends BlockContainer {

    // Facing direction of the block
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    // Number of book stripes to render: 0 (empty) .. 14 (full)
    public static final PropertyInteger BOOKS = PropertyInteger.create("books", 0, 14);

    // Not a full 16x16x16 cube; model uses 1..15 bounds
    private static final AxisAlignedBB AABB = new AxisAlignedBB(1 / 16.0D, 1 / 16.0D, 1 / 16.0D, 15 / 16.0D, 15 / 16.0D, 15 / 16.0D);

    public BlockSpellArchive() {
        super(Material.WOOD);
        setHardness(2.0F);
        setCreativeTab(CreativeTabs.MISC);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(BOOKS, 0));
    }

    /**
     * Creates the Spell Archive tile for this block. The world and metadata are part of the
     * vanilla signature but aren't used here since the tile has no variant state at creation.
     *
     * @param worldIn The world that will contain the tile (not used).
     * @param meta Block metadata at placement (ignored).
     * @return A fresh {@link TileSpellArchive} with empty storage.
     */
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileSpellArchive();
    }

    /**
     * Declares that the block uses a baked model for rendering. The state parameter is ignored
     * because the render type is constant for this block.
     *
     * @param state The current block state (not used).
     * @return {@link EnumBlockRenderType#MODEL} for normal model rendering.
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    /**
     * Returns the constant selection/collision box matching the shelf model (smaller than a
     * full cube). The parameters are provided by vanilla but don't affect the returned AABB.
     *
     * @param state The queried state (not used).
     * @param source Accessor for world data (not used).
     * @param pos Block position (not used).
     * @return The predefined bounding box for this block.
     */
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return AABB;
    }

    /**
     * Indicates the model isn't a full 16x16x16 cube to adjust lighting and culling.
     *
     * @param state The state being queried (unused).
     * @return Always false to match the non-cubic shape.
     */
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /**
     * Reports non-opaque so adjacent block faces can render where appropriate.
     *
     * @param state The state being queried (unused).
     * @return Always false to avoid fully occluding neighbors.
     */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /**
     * Returns UNDEFINED for all faces so other blocks don't treat this as a solid surface for
     * special connections (e.g., fences). Parameters are part of the vanilla query.
     *
     * @param worldIn Read-only world access for context (unused).
     * @param state Current block state (unused).
     * @param pos Position of the block (unused).
     * @param face Which face is being queried (unused).
     * @return {@link BlockFaceShape#UNDEFINED} for every face.
     */
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    /**
     * Handles right-click activation. If the player is holding a spell book, the server inserts
     * it into the archive; otherwise the GUI is opened. Client side returns early and lets the
     * server perform the action.
     *
     * @param worldIn Interaction world; used to check side and open the GUI on the server.
     * @param pos Position of the archive; used to locate the tile and open the GUI.
     * @param state Current block state (unused for logic).
     * @param playerIn Interacting player; used to read the held item and open the GUI.
     * @param hand Which hand was used; used to get the held item stack.
     * @param facing Block face that was clicked (ignored).
     * @param hitX Local X hit coordinate (ignored).
     * @param hitY Local Y hit coordinate (ignored).
     * @param hitZ Local Z hit coordinate (ignored).
     * @return True to indicate the interaction was handled.
     */
    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (!(te instanceof TileSpellArchive)) return true;

        if (!worldIn.isRemote) {
            TileSpellArchive archive = (TileSpellArchive) te;

            ItemStack held = playerIn.getHeldItem(hand);
            if (!held.isEmpty() && archive.isSpellBook(held)) {
                int added = archive.addBooks(held, held.getCount());
                if (added > 0) held.shrink(added);
            } else {
                playerIn.openGui(SpellArchives.instance, GuiHandler.GUI_SPELL_ARCHIVE, worldIn, pos.getX(), pos.getY(), pos.getZ());
            }
        }

        return true;
    }

    /**
     * Replaces normal harvest drops by spawning the block as an item that contains the tile's
     * NBT data. This preserves stored spells when the block is broken.
     *
     * @param world The world the block is being harvested in; used to spawn the dropped item.
     * @param player Player who harvested the block (unused).
     * @param pos Position of the block; used for spawning the item.
     * @param state Current block state (unused).
     * @param te Tile entity to serialize into the dropped item.
     * @param tool Player's tool (unused).
     */
    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, TileEntity te, ItemStack tool) {
        if (!(te instanceof TileSpellArchive)) {
            super.harvestBlock(world, player, pos, state, te, tool);
            return;
        }

        NBTTagCompound tag = new NBTTagCompound();
        te.writeToNBT(tag);

        ItemStack stack = new ItemStack(Item.getItemFromBlock(this));
        NBTTagCompound blockTag = new NBTTagCompound();
        blockTag.setTag("BlockEntityTag", tag);
        stack.setTagCompound(blockTag);

        spawnAsEntity(world, pos, stack);
    }

    /**
     * Chooses the initial facing at placement time based on the placing entity's orientation.
     * All click coordinates and metadata are ignored.
     *
     * @param world World the block is placed in (unused).
     * @param pos Target position (unused).
     * @param facing Placement face (ignored).
     * @param hitX Local hit x (ignored).
     * @param hitY Local hit y (ignored).
     * @param hitZ Local hit z (ignored).
     * @param meta Original item metadata (ignored).
     * @param placer Entity placing the block; used to get horizontal facing.
     * @param hand Hand used (ignored).
     * @return New state with FACING set to the placer orientation.
     */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }

    /**
     * Ensures the block state matches the placing entity's orientation post-placement.
     * Used to keep FACING consistent with the placer.
     *
     * @param world World the block is in; used to set the updated state.
     * @param pos Position of the block to update.
     * @param state Current state before adjustment.
     * @param placer Entity that placed the block; queried for orientation.
     * @param stack Item stack used to place the block (unused).
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos, state.withProperty(FACING, placer.getHorizontalFacing()), 2);
    }

    /**
     * Declares the two properties used by this block's state: FACING (orientation) and BOOKS
     * (derived stripe count for model variants).
     *
     * @return A container describing this block's properties.
     */
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, BOOKS);
    }

    /**
     * Converts the saved block metadata (0..3) to a full state. Only FACING is encoded in meta;
     * BOOKS is computed dynamically via {@link #getActualState}.
     *
     * @param meta Lower two bits representing horizontal facing.
     * @return Default state with FACING set and BOOKS cleared to 0.
     */
    @Override
    public IBlockState getStateFromMeta(int meta) {
        // Only FACING is stored in metadata; BOOKS is computed dynamically via getActualState
        return this.getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(BOOKS, 0);
    }

    /**
     * Encodes the state's FACING into metadata for chunk storage. BOOKS is not persisted and
     * is recalculated client-side when rendering.
     *
     * @param state The full block state.
     * @return Metadata value 0..3 corresponding to horizontal facing.
     */
    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    /**
     * Derives the BOOKS property from the tile's number of distinct stored spell types to
     * select a visual model variant (0..14 stripes). This doesn't mutate the tile; it only
     * returns a state reflecting the current content.
     *
     * @param state The incoming state prior to applying BOOKS.
     * @param world Read-only world access used to look up the tile at pos.
     * @param pos Position of the Spell Archive tile to query.
     * @return Either the original state (if unchanged) or a copy with BOOKS updated.
     */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        int stripes = 0;

        if (te instanceof TileSpellArchive) {
            // Number of distinct spell types in the archive, scaled to 0..14 with cap 196
            int types = ((TileSpellArchive) te).getDistinctSpellTypeCount();
            if (types > 0) stripes = Math.min(14, (int) Math.ceil(types * 14.0 / 196.0));
        }

        // If already set correctly, keep original instance to avoid redundant model cache misses
        if (state.getValue(BOOKS) == stripes) return state;

        return state.withProperty(BOOKS, stripes);
    }
}
