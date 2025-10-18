package com.spellarchives.client;

import com.spellarchives.tile.TileSpellArchive;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {
    public static final int GUI_SPELL_ARCHIVE = 1;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SPELL_ARCHIVE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileSpellArchive) {
                return new com.spellarchives.container.ContainerSpellArchive(player.inventory, (TileSpellArchive) te);
            }
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SPELL_ARCHIVE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileSpellArchive) {
                return new com.spellarchives.gui.GuiSpellArchive(new com.spellarchives.container.ContainerSpellArchive(player.inventory, (TileSpellArchive) te), (TileSpellArchive) te, player);
            }
        }

        return null;
    }
}
