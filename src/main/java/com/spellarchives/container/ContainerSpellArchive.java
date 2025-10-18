package com.spellarchives.container;

import com.spellarchives.tile.TileSpellArchive;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;

public class ContainerSpellArchive extends Container {
    private final TileSpellArchive tile;

    public ContainerSpellArchive(InventoryPlayer playerInv, TileSpellArchive tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tile != null && tile.getWorld() != null && tile.getWorld().getTileEntity(tile.getPos()) == tile && playerIn.getDistanceSq(tile.getPos()) <= 64;
    }

    // No special button handling anymore
    @Override
    public boolean enchantItem(EntityPlayer playerIn, int id) {
        return super.enchantItem(playerIn, id);
    }
}
