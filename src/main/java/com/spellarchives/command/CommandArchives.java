package com.spellarchives.command;

import com.spellarchives.Log;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.item.ItemSpellBook;
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

        // Build a cache of modid -> ItemSpellBook, preferring each mod's own spell book item when available.
        Map<String, Item> spellBookByMod = buildSpellBookItemIndex();
        Item defaultBook = spellBookByMod.get("ebwizardry");
        if (defaultBook == null) {
            Log.chatError(sender, "Wizardry spell book item not found (ebwizardry:spell_book).");
            return;
        }

        for (Spell spell : Spell.getAllSpells()) {
            if (spell == null) continue;

            Integer meta = spell.metadata();
            if (meta == -1) continue; // Not registered

            // Choose the correct spell book item for the spell's owning mod, fallback to ebwizardry's
            ResourceLocation spellId = spell.getRegistryName();
            String modid = spellId != null ? getNamespaceSafe(spellId) : "ebwizardry";
            Item spellBookItem = spellBookByMod.getOrDefault(modid, defaultBook);

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

    // Scans the item registry and builds a map from modid -> ItemSpellBook instance for that mod.
    // Prefers items whose path contains "spell_book" when multiple exist.
    private Map<String, Item> buildSpellBookItemIndex() {
        Map<String, Item> map = new HashMap<>();

        // First pass: record any ItemSpellBook per modid
        for (ResourceLocation rl : Item.REGISTRY.getKeys()) {
            Item item = Item.REGISTRY.getObject(rl);
            if (!(item instanceof ItemSpellBook)) continue;

            String modid = getNamespaceSafe(rl);
            Item current = map.get(modid);

            // Prefer paths that explicitly contain "spell_book"
            if (current == null) {
                map.put(modid, item);
            } else if (getPathSafe(rl).contains("spell_book")) {
                map.put(modid, item);
            }
        }

        // Ensure default ebwizardry book is present if available
        Item eb = Item.REGISTRY.getObject(new ResourceLocation("ebwizardry", "spell_book"));
        if (eb instanceof ItemSpellBook) map.put("ebwizardry", eb);

        return map;
    }

    // Fallback-safe namespace extraction that works across mappings
    private static String getNamespaceSafe(ResourceLocation rl) {
        if (rl == null) return "";
        String s = rl.toString(); // format: namespace:path
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(0, i) : "";
    }

    private static String getPathSafe(ResourceLocation rl) {
        if (rl == null) return "";
        String s = rl.toString();
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
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
