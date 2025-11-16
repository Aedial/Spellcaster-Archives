package com.spellarchives.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.spell.Spell;

import com.spellarchives.SpellArchives;
import com.spellarchives.config.SpellArchivesConfig;
import com.spellarchives.tile.TileSpellArchive;

/**
 * Listens for player item pickups and, when enabled in config and a Spellcaster's Archives
 * item is present in the player's inventory, diverts Wizardry spell books directly into the
 * carried Archives item NBT instead of the player's inventory. To avoid scanning the full
 * inventory each pickup, the matching slot index is cached per player and revalidated on use.
 */
@Mod.EventBusSubscriber(modid = SpellArchives.MODID)
public final class AutoPickupHandler {
    private static final Map<UUID, Integer> cachedArchiveSlotByPlayer = new HashMap<>();

    private AutoPickupHandler() {}

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!SpellArchivesConfig.isAutoPickupEnabled()) return;

        EntityPlayer player = event.getEntityPlayer();
        if (player == null || player.world == null || player.world.isRemote) return;

        EntityItem entityItem = event.getItem();
        if (entityItem == null) return;

        ItemStack picked = entityItem.getItem();
        if (picked == null || picked.isEmpty()) return;

        if (!isSpellBook(picked)) return;

        int slot = getArchiveSlotCached(player);
        if (slot < 0) return;  // no carried Archives item present

        ItemStack archive = player.inventory.getStackInSlot(slot);
        if (archive == null || archive.isEmpty() || !isArchiveItem(archive)) {
            // Invalidate and rescan once in case the cached slot became stale
            invalidateCache(player);
            slot = getArchiveSlotCached(player);
            if (slot < 0) return;
            archive = player.inventory.getStackInSlot(slot);
            if (archive == null || archive.isEmpty() || !isArchiveItem(archive)) return;
        }

        // Add the picked books to the archive
        int accepted = addToArchiveItem(archive, picked);
        if (accepted <= 0) return;  // couldn't add any (should be extremely rare)

        if (accepted >= picked.getCount()) {
            // Fully handled: remove the world item and cancel the default pickup handling
            entityItem.setDead();
            event.setCanceled(true);
        } else {
            // Partially handled: shrink the entity item; let vanilla handle the remainder normally
            picked.shrink(accepted);
            entityItem.setItem(picked);
        }
    }

    private static boolean isSpellBook(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemSpellBook;
    }

    private static boolean isArchiveItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item archiveItem = Item.getItemFromBlock(ModBlocks.SPELL_ARCHIVE);
        return archiveItem != null && stack.getItem() == archiveItem;
    }

    private static String getSpellRegistryName(ItemStack spellBook) {
        try {
            Spell s = Spell.byMetadata(spellBook.getItemDamage());
            ResourceLocation rl = s != null ? s.getRegistryName() : null;
            return rl != null ? rl.toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getArchiveSlotCached(EntityPlayer player) {
        UUID id = player.getUniqueID();
        Integer cached = cachedArchiveSlotByPlayer.get(id);

        if (cached != null && cached >= 0) {
            ItemStack stack = player.inventory.getStackInSlot(cached);
            if (isArchiveItem(stack)) return cached;
        }

        int slot = findArchiveSlot(player.inventory);
        cachedArchiveSlotByPlayer.put(id, slot);
        return slot;
    }

    private static void invalidateCache(EntityPlayer player) {
        cachedArchiveSlotByPlayer.remove(player.getUniqueID());
    }

    private static int findArchiveSlot(InventoryPlayer inv) {
        if (inv == null) return -1;

        Item archiveItem = Item.getItemFromBlock(ModBlocks.SPELL_ARCHIVE);
        if (archiveItem == null) return -1;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s != null && !s.isEmpty() && s.getItem() == archiveItem) return i;
        }

        return -1;
    }

    /**
     * Adds the specified number of spell books of a given spell to the carried Archives item stack.
     *
     * @param archiveStack The Archives item stack (must be ItemBlock of SPELL_ARCHIVE).
     * @param spellBook The spell book item stack to add.
     * @return The remaining spell book item stack that could not be added.
     */
    private static int addToArchiveItem(ItemStack archiveStack, ItemStack spellBook) {
        if (archiveStack == null || archiveStack.isEmpty() || spellBook == null || spellBook.isEmpty()) return 0;

        // Reconstruct a TileSpellArchive from the item's BlockEntityTag NBT (if present)
        TileSpellArchive archiveTile = new TileSpellArchive();

        NBTTagCompound root = archiveStack.getTagCompound();
        NBTTagCompound blockTag = (root != null && root.hasKey("BlockEntityTag"))
                ? root.getCompoundTag("BlockEntityTag")
                : new NBTTagCompound();

        archiveTile.readFromNBT(blockTag);

        // Attempt to insert the books into the reconstructed tile, for consistency of insertion logic
        int before = spellBook.getCount();
        ItemStack remainder = archiveTile.addBooks(spellBook);
        int after = remainder == null || remainder.isEmpty() ? 0 : remainder.getCount();
        int accepted = before - after;

        if (accepted > 0) {
            // Save the modified tile back into the item's NBT, preserving other root tags
            NBTTagCompound newRoot = root != null ? root.copy() : new NBTTagCompound();
            NBTTagCompound newBlockTag = archiveTile.writeToNBT(new NBTTagCompound());

            newRoot.setTag("BlockEntityTag", newBlockTag);
            archiveStack.setTagCompound(newRoot);
        }

        return accepted;
    }
}
