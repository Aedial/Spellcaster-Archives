package com.spellarchives.command;

import com.spellarchives.Log;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.spell.Spell;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

public class CommandArchives extends CommandBase {
    @Override
    public String getName() { return "archives"; }

    @Override
    public String getUsage(ICommandSender sender) { return "/archives fill <count|\"max\">"; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2 || !"fill".equalsIgnoreCase(args[0])) {
            Log.chatWarn(sender, "Usage: " + getUsage(sender));
            return;
        }

        int count;
        if ("max".equalsIgnoreCase(args[1])) {
            count = Integer.MAX_VALUE;
        } else {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Log.chatError(sender, "Invalid number for count: " + args[1]);
                return;
            }
        }

        // Sender must be a player to ray trace what they're looking at
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            Log.chatError(sender, "This command must be run by a player (to target an archive).");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;

        // Ray trace to find the block the player is looking at
        RayTraceResult hit = rayTrace(player, 6.0D);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            Log.chatWarn(sender, "Look at an Archive block and run the command again.");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        World world = player.getEntityWorld();
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSpellArchive)) {
            Log.chatError(sender, "That block is not a Spell Archive.");
            return;
        }
        TileSpellArchive tile = (TileSpellArchive) te;

        int addedTypes = 0;
        long totalBooksRequested = 0;
        long totalBooksAdded = 0;

        // Use the global Spell registry; for each Spell, find its metadata id and build the base spell book stack.
        Item spellBookItem = Item.REGISTRY.getObject(new ResourceLocation("ebwizardry", "spell_book"));
        if (spellBookItem == null) {
            Log.chatError(sender, "Wizardry spell book item not found (ebwizardry:spell_book).");
            return;
        }

        // Build a one-time map from Spell to metadata by scanning a reasonable range.
        Map<Spell, Integer> metaBySpell = new HashMap<>();
        for (int m = 0; m < 4096; m++) {
            Spell s = Spell.byMetadata(m);
            if (s != null) {
                metaBySpell.putIfAbsent(s, m);
            } else if (m > 256 && m % 64 == 0 && metaBySpell.size() > 0) {
                // Heuristic early-out: after some gaps and having found entries, assume sparse end
                if (m > 1024) break;
            }
        }

        for (Spell spell : Spell.getAllSpells()) {
            if (spell == null) continue;
            Integer meta = metaBySpell.get(spell);
            if (meta == null) continue; // Unknown mapping

            ItemStack book = new ItemStack(spellBookItem, 1, meta.intValue());
            totalBooksRequested += count;
            int added = tile.addBooks(book, count);
            if (added > 0) {
                addedTypes++;
                totalBooksAdded += added;
            }
        }

        Log.chatSuccess(sender, "Filled " + addedTypes + " spell types (" + totalBooksAdded + "/" + totalBooksRequested + ") into the archive.");
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Collections.singletonList("fill"));
        }

        if (args.length == 2 && "fill".equalsIgnoreCase(args[0])) {
            // Suggest a few common counts and 'max'
            return getListOfStringsMatchingLastWord(args, Arrays.asList("16", "64", "256", "1024", "max"));
        }

        return Collections.emptyList();
    }

    // Simple ray trace helper: mirrors client reach logic on server
    private static RayTraceResult rayTrace(EntityPlayerMP player, double range) {
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1.0f);
        net.minecraft.util.math.Vec3d look = player.getLook(1.0f);
        net.minecraft.util.math.Vec3d end = eye.add(look.x * range, look.y * range, look.z * range);
        return player.world.rayTraceBlocks(eye, end, false, false, false);
    }
}
