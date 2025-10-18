package com.spellarchives.registry;

import com.spellarchives.SpellArchives;
import com.spellarchives.block.BlockSpellArchive;
import com.spellarchives.tile.TileSpellArchive;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = SpellArchives.MODID)
public class ModBlocks {
    public static Block SPELL_ARCHIVE;

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        SPELL_ARCHIVE = new BlockSpellArchive();
        SPELL_ARCHIVE.setRegistryName(new ResourceLocation(SpellArchives.MODID, "spell_archive"));
        SPELL_ARCHIVE.setTranslationKey(SpellArchives.MODID + ".spell_archive");
        event.getRegistry().register(SPELL_ARCHIVE);

        GameRegistry.registerTileEntity(TileSpellArchive.class, new ResourceLocation(SpellArchives.MODID, "spell_archive"));
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        if (SPELL_ARCHIVE != null) {
            Item itemBlock = new ItemBlock(SPELL_ARCHIVE) {
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
