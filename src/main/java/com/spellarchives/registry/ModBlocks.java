package com.spellarchives.registry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import com.spellarchives.SpellArchives;
import com.spellarchives.block.BlockSpellArchive;
import com.spellarchives.tile.TileSpellArchive;


/**
 * Registers the Spell Archives mod's blocks and their corresponding item forms with Forge's registries.
 *
 * Notes and ordering:
 * - Blocks are registered first, along with their {@link TileEntity} types.
 * - ItemBlocks are then registered during the item registry event to ensure their {@code registryName}
 *   matches the registered block.
 * - The custom {@code ItemBlock} implementation restores any serialized TileEntity data from the
 *   item's {@code BlockEntityTag} on placement, so moving a Spell Archive preserves its contents and state.
 */
@Mod.EventBusSubscriber(modid = SpellArchives.MODID)
public class ModBlocks {
    public static Block SPELL_ARCHIVE;

    /**
     * Block registry callback. Creates and registers the Spell Archive block and associates the
     * archive's {@link TileSpellArchive} {@link TileEntity} type with a stable registry key.
     *
     * @param event Forge block registry event invoked during startup.
     */
    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        SPELL_ARCHIVE = new BlockSpellArchive();
        SPELL_ARCHIVE.setRegistryName(new ResourceLocation(SpellArchives.MODID, "spell_archive"));
        SPELL_ARCHIVE.setTranslationKey(SpellArchives.MODID + ".spell_archive");
        event.getRegistry().register(SPELL_ARCHIVE);

        GameRegistry.registerTileEntity(TileSpellArchive.class, new ResourceLocation(SpellArchives.MODID, "spell_archive"));
    }

    /**
     * Item registry callback. Registers the {@link ItemBlock} for {@link #SPELL_ARCHIVE}. The custom
     * {@code placeBlockAt} implementation below restores the archive's serialized TileEntity NBT so the
     * block can be broken, carried, and placed without losing aggregated book data.
     *
     * @param event Forge item registry event invoked after block registration.
     */
    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        if (SPELL_ARCHIVE != null) {
            Item itemBlock = new ItemBlock(SPELL_ARCHIVE) {
                /**
                 * Places {@link #block} and, if present, merges the item's {@code BlockEntityTag} into the freshly
                 * created {@link TileSpellArchive}. This preserves stored spell counts and any other TE state.
                 *
                 * @param stack the item stack being placed.
                 * @param player the player placing the block.
                 * @param world the world in which the block is being placed.
                 * @param pos the target position.
                 * @param side the side of the block being targeted.
                 * @param hitX hit X coordinate on the target block's side.
                 * @param hitY hit Y coordinate on the target block's side.
                 * @param hitZ hit Z coordinate on the target block's side.
                 * @param newState the block state to set.
                 * @return true if placement succeeded (block set in world); false otherwise.
                 */
                @Override
                public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, IBlockState newState) {
                    boolean placed = super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);

                    if (placed && stack.hasTagCompound() && stack.getTagCompound().hasKey("BlockEntityTag")) {
                        // Restore TE state from item NBT
                        TileEntity te = world.getTileEntity(pos);
                        if (te instanceof TileSpellArchive) {
                            te.readFromNBT(stack.getTagCompound().getCompoundTag("BlockEntityTag"));
                            te.markDirty();
                        }
                    }

                    return placed;
                }
            };

            itemBlock.setRegistryName(SPELL_ARCHIVE.getRegistryName());
            itemBlock.setTranslationKey(SPELL_ARCHIVE.getTranslationKey());
            event.getRegistry().register(itemBlock);
        }
    }
}
