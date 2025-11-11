package com.spellarchives.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.network.IGuiHandler;

import com.spellarchives.container.ContainerSpellArchive;
import com.spellarchives.gui.GuiSpellArchive;
import com.spellarchives.tile.TileSpellArchive;


/**
 * Central Forge GUI handler for the Spell Archive. Bridges the server-side {@link ContainerSpellArchive}
 * with the client-side {@link GuiSpellArchive} when a player opens the interface.
 *
 * Contract & Behavior:
 * - Only GUI id {@link #GUI_SPELL_ARCHIVE} (value {@code 1}) is recognized.
 * - Both server and client queries validate that the TileEntity at the supplied coordinates is a
 *   {@link TileSpellArchive}; otherwise {@code null} is returned (Forge interprets null as failure).
 * - Coordinates are passed as raw ints (not a {@link BlockPos}); they must reference a loaded chunk.
 * - No side effects beyond constructing the container or GUI; all state lives in the tile entity.
 *
 * Error Handling:
 * - Returning {@code null} gracefully aborts GUI opening without crashing, e.g. if the TE was removed.
 * - No network packets are sent manually; Forge handles synchronization when the container is returned.
 */
public class GuiHandler implements IGuiHandler {
    public static final int GUI_SPELL_ARCHIVE = 1;

    /**
     * Forge callback requesting the server-side GUI element. Validates GUI id and tile entity type, then constructs
     * a {@link ContainerSpellArchive} bound to the player's inventory and the target {@link TileSpellArchive}.
     *
     * @param ID requested GUI id (must equal {@link #GUI_SPELL_ARCHIVE}).
     * @param player player initiating the open action.
     * @param world server world containing the tile entity.
     * @param x tile X coordinate.
     * @param y tile Y coordinate.
     * @param z tile Z coordinate.
     * @return a new {@link ContainerSpellArchive} if validation succeeds; {@code null} otherwise.
     */
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SPELL_ARCHIVE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileSpellArchive) {
                return new ContainerSpellArchive(player.inventory, (TileSpellArchive) te);
            }
        }

        return null;
    }

    /**
     * Forge callback requesting the client-side GUI. Mirrors {@link #getServerGuiElement} validation and wraps the
     * server container instance inside a {@link GuiSpellArchive} for rendering and interaction.
     *
     * @param ID requested GUI id (must equal {@link #GUI_SPELL_ARCHIVE}).
     * @param player client-side player.
     * @param world client world.
     * @param x tile X coordinate.
     * @param y tile Y coordinate.
     * @param z tile Z coordinate.
     * @return a {@link GuiSpellArchive} if successful; {@code null} if id/TE mismatch.
     */
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_SPELL_ARCHIVE) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileSpellArchive) {
                return new GuiSpellArchive(new ContainerSpellArchive(player.inventory, (TileSpellArchive) te), (TileSpellArchive) te, player);
            }
        }

        return null;
    }
}
