package com.spellarchives.command;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.spellarchives.SpellArchives;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.spell.Spell;



public class CommandArchives extends CommandBase {
    @Override
    public String getName() { return "archives"; }

    @Override
    public String getUsage(ICommandSender sender) { return "/archives fill <count|\"max\"> [typesCount]"; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2 || !"fill".equalsIgnoreCase(args[0])) {
            SpellArchives.LOGGER.chatWarnTrans(sender, "chat.spellarchives.usage", getUsage(sender));
            return;
        }

        // Parse count per spell
        int count;
        if ("max".equalsIgnoreCase(args[1])) {
            count = Integer.MAX_VALUE;
        } else {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                SpellArchives.LOGGER.chatErrorTrans(sender, "chat.spellarchives.invalid_number", args[1]);
                return;
            }
        }

        // Parse optional types count
        int typesCount = Spell.getAllSpells().size();
        if (args.length >= 3) {
            try {
                typesCount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                SpellArchives.LOGGER.chatErrorTrans(sender, "chat.spellarchives.invalid_number", args[2]);
                return;
            }
        }

        // Sender must be a player to ray trace what they're looking at
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            SpellArchives.LOGGER.chatErrorTrans(sender, "chat.spellarchives.player_only");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;

        // Ray trace to find the block the player is looking at
        RayTraceResult hit = rayTrace(player, 6.0D);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            SpellArchives.LOGGER.chatWarnTrans(sender, "chat.spellarchives.look_at_archive");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        World world = player.getEntityWorld();
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileSpellArchive)) {
            SpellArchives.LOGGER.chatErrorTrans(sender, "chat.spellarchives.not_archive");
            return;
        }
        TileSpellArchive tile = (TileSpellArchive) te;

        int addedTypes = 0;
        long totalBooksRequested = 0;
        long totalBooksAdded = 0;

        // Add typesCount random spells
        List<Spell> allSpells = Spell.getAllSpells();
        if (typesCount < allSpells.size()) {
            Collections.shuffle(allSpells);
            allSpells = allSpells.subList(0, typesCount);
        }

        for (Spell spell : allSpells) {
            if (spell == null) continue;

            Integer meta = spell.metadata();
            if (meta == -1) continue; // Not registered

            // Choose the correct spell book item for the spell's owning mod
            Item spellBookItem = tile.getSpellBookForModPublic(spell);
            if (spellBookItem == null) continue;

            ItemStack book = new ItemStack(spellBookItem, 1, meta.intValue());
            totalBooksRequested += count;
            ItemStack remaining = tile.addBooks(book, count);
            if (remaining != book) {
                addedTypes++;
                totalBooksAdded += (count - remaining.getCount());
            }
        }

        SpellArchives.LOGGER.chatSuccessTrans(sender, "chat.spellarchives.filled_summary", addedTypes, totalBooksAdded, totalBooksRequested);
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
            if (current == null || getPathSafe(rl).contains("spell_book")) {
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
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, Collections.singletonList("fill"));

        // Suggest arguments for "fill" subcommand
        if (args.length > 0 && "fill".equalsIgnoreCase(args[0])) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, Arrays.asList("max", "1", "64"));
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, Arrays.asList("196", "150", "98", "50", "1"));
        }

        return Collections.emptyList();
    }

    // Simple ray trace helper: mirrors client reach logic on server
    private static RayTraceResult rayTrace(EntityPlayerMP player, double range) {
        Vec3d eye = player.getPositionEyes(1.0f);
        Vec3d look = player.getLook(1.0f);
        Vec3d end = eye.add(look.x * range, look.y * range, look.z * range);

        return player.world.rayTraceBlocks(eye, end, false, false, false);
    }
}
