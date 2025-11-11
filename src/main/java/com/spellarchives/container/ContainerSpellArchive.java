package com.spellarchives.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;

import com.spellarchives.tile.TileSpellArchive;


/**
 * Server-side container for the Spell Archive GUI. No slots are exposed since the
 * backing storage is aggregated and accessed via capability calls and network messages.
 */
public class ContainerSpellArchive extends Container {
    private final TileSpellArchive tile;

    /**
     * Binds the container to a specific tile instance.
     *
     * @param playerInv The player's inventory (unused, kept for symmetry).
     * @param tile The Spell Archive tile entity.
     */
    public ContainerSpellArchive(InventoryPlayer playerInv, TileSpellArchive tile) {
        this.tile = tile;
    }

    /**
     * Allows interaction while the correct tile remains at the expected position and the
     * player is within 8 blocks (64 distance squared).
     *
     * @param playerIn The player attempting interaction.
     * @return True if the container can still be interacted with.
     */
    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tile != null && tile.getWorld() != null && tile.getWorld().getTileEntity(tile.getPos()) == tile && playerIn.getDistanceSq(tile.getPos()) <= 64;
    }
}
