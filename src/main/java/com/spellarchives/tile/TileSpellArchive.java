package com.spellarchives.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.spell.Spell;
import io.github.kurrycat2004.enchlib.common.ISlotlessItemHandler;
import net.minecraftforge.common.capabilities.CapabilityInject;

import com.spellarchives.SpellArchives;


/**
 * Tile entity for the Spellcaster's Archives block. This tile stores an effectively-unbounded
 * collection of Electroblob's Wizardry spell books, collapsing identical books into
 * a single logical entry keyed by the book's registry name and metadata. The
 * inventory is exposed through capabilities:
 *
 * - IItemHandler: dynamic slot view with a virtual insertion slot to accept new spell types
 * - IItemRepository (Storage Drawers API): slotless, aggregated view used by external systems
 * - ISlotlessItemHandler (enchlib): slotless insertion/extraction fixes via mixins (requires enchlib to apply)
 *
 * Persistence intentionally stores spell identifiers by spell registry name rather than
 * raw item metadata to remain stable across Wizardry metadata changes. Unknown/removed
 * spells are reported in the log and ignored on load.
 */
public class TileSpellArchive extends TileEntity {
    // Injected capability from Storage Drawers API
    @CapabilityInject(IItemRepository.class)
    private static Capability<IItemRepository> SD_REPO_CAP = null;

    // Mapping of slot -> runtime key (rl|meta) for slot-based extraction
    private final List<String> slotKeys = new ArrayList<>();
    // prototypesByKey: runtime key -> 1-count ItemStack prototype (should not be EMPTY)
    private final Map<String, ItemStack> prototypesByKey = new HashMap<>();
    // Map key: rl|meta (NBT intentionally ignored), value: total count
    // Use LinkedHashMap to keep slot order stable for external handlers (e.g., hoppers)
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    // Incremented on content changes; synced to client for GUI refresh
    private int changeCounter = 0;

    // Internal reserve of identification scrolls (manual-only extraction; not exposed via capabilities)
    private int identificationScrolls = 0;

    // During block harvest we temporarily suppress exposing capability to avoid AE2 UEL
    // NPEs when neighbors are updated mid-removal.
    // This flag is only set right before scheduling removal at end-of-tick.
    private boolean suppressCap = false;

    // Capability: dynamic view, present at least 1 slot for insertion when empty
    private final IItemHandler itemHandler = new RepoHandler();

    // Static metadata -> spell name mapping, built once at first use
    private static Map<Integer, String> metadataToSpellName = null;
    private static Map<String, Integer> spellNameToMetadata = null;

    // Cached index of modid -> spell book item for that mod (prefers items named "spell_book")
    private static Map<String, Item> spellBookByMod = null;
    private static Item defaultSpellBook = null;

    /**
     * Builds static mappings between Wizardry spell metadata and their registry names.
     * Safe to call repeatedly; initialization happens once on first use.
     */
    private static void buildSpellMappings() {
        if (metadataToSpellName != null) return;

        metadataToSpellName = new LinkedHashMap<>();
        spellNameToMetadata = new LinkedHashMap<>();

        for (Spell spell : Spell.registry) {
            if (spell != null && spell.getRegistryName() != null) {
                int meta = spell.metadata();
                String name = spell.getRegistryName().toString();
                metadataToSpellName.put(meta, name);
                spellNameToMetadata.put(name, meta);
            }
        }
    }

    /**
     * Item/record repository facade over the internal aggregated counts map.
     * Provides both slot-based and slotless insertion/extraction semantics.
     */
    private class RepoHandler implements IItemHandler, IItemRepository, ISlotlessItemHandler {

        /**
         * Returns the number of slots presented to external handlers. We always expose
         * at least one slot so external inserters can interact with an otherwise empty archive.
         *
         * @return The number of slots.
         * @return Total visible slots.
         */
        @Override
        public int getSlots() {
            return Math.max(1, slotKeys.size());
        }

        /**
         * Returns a representative stack for the given slot. The stack's count reflects
         * the total available amount for that spell type.
         *
         * @param slot Logical slot index.
         * @return A template stack with an amount reflecting availability, or empty if none.
         */
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= slotKeys.size()) return ItemStack.EMPTY;

            String key = slotKeys.get(slot);
            int available = counts.getOrDefault(key, 0);
            if (available <= 0) return ItemStack.EMPTY;

            ItemStack proto = prototypesByKey.get(key);
            ItemStack out = proto.copy();
            out.setCount(available);
            return out;
        }

        /**
         * Inserts spell books into the archive. Slot index is ignored (slotless semantics).
         * Capacity is effectively unbounded (Integer.MAX_VALUE per type); overflow is
         * treated as accepted and the remainder is voided to avoid systems overfilling.
         *
         * @param slot The target slot index; ignored because insertion is slotless.
         * @param stack The incoming stack to insert.
         * @param simulate If true, do not modify state; only compute the result.
         * @return An empty stack unless the stack is invalid, in which case the original stack is returned.
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;

            // Accept spell books
            if (isSpellBook(stack)) {
                if (!simulate) {
                    // insert into any slot, capping at Integer.MAX_VALUE per type
                    String key = keyOf(stack);

                    long current = (long) counts.getOrDefault(key, 0);
                    long newCount = Math.min(current + stack.getCount(), Integer.MAX_VALUE);

                    int oldSize = counts.size();
                    counts.put(key, (int) newCount);
                    updateCachedForKey(key, stack, oldSize);
                    onContentsChanged();
                }

                return ItemStack.EMPTY;
            }

            // Accept identification scrolls via capability insertion into the internal reserve with capacity cap
            if (isIdentificationScroll(stack)) {
                int max = com.spellarchives.config.SpellArchivesConfig.getScrollReserveMax();
                int canAccept = Math.max(0, (max < 0 ? Integer.MAX_VALUE : max) - identificationScrolls);
                if (canAccept <= 0) return stack; // full

                int toInsert = Math.min(canAccept, stack.getCount());
                if (!simulate) addIdentificationScrolls(toInsert);

                if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

                ItemStack rem = stack.copy();
                rem.shrink(toInsert);
                return rem;
            }

            return stack;
        }

        /**
         * Extracts spell books from the archive by runtime key.
         *
         * @param key The runtime key indicating the desired spell type.
         * @param amount The maximum amount to extract.
         * @param simulate If true, do not modify state; only compute the result.
         * @return A stack representing the extracted amount, or empty if unavailable.
         */
        public ItemStack extractItemFromKey(String key, int amount, boolean simulate) {
            if (key == null) return ItemStack.EMPTY;

            int available = counts.getOrDefault(key, 0);
            if (available <= 0) return ItemStack.EMPTY;

            ItemStack proto = prototypesByKey.get(key);
            ItemStack out = proto.copy();
            int toExtract = Math.min(available, amount);
            out.setCount(toExtract);

            if (!simulate) {
                counts.put(key, available - toExtract);
                onContentsChanged();
            }

            return out;
        }

        /**
         * Extracts up to the requested amount of books from the specified slot.
         *
         * @param slot Logical slot index to extract from.
         * @param amount Desired amount to extract.
         * @param simulate If true, do not modify state; only compute the result.
         * @return A stack representing the extracted books, or empty if none available in that slot.
         */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || slot < 0 || slot >= slotKeys.size()) return ItemStack.EMPTY;

            String key = slotKeys.get(slot);
            return extractItemFromKey(key, amount, simulate);
        }

        /**
         * Per-type logical capacity. Exposed as Integer.MAX_VALUE to indicate effectively
         * unlimited storage for a single spell type.
         *
         * @param slot Logical slot index.
         * @return The maximum number of items representable in a single slot entry.
         */
        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        /**
         * Only allows valid spell books and identification scrolls.
         *
         * @param slot Logical slot index.
         * @param stack Candidate stack to test.
         * @return True if the stack can be inserted into the Archives.
         */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty() || (!isSpellBook(stack) && !isIdentificationScroll(stack))) return false;

            if (slot < 0 || slot >= slotKeys.size()) return false;

            if (isIdentificationScroll(stack)) return true;

            String key = slotKeys.get(slot);
            return key != null && key.equals(keyOf(stack));
        }

        // ---- IItemRepository implementation (Storage Drawers API) ----
        /**
         * Returns an aggregated, slotless view of all stored spell books. Each record contains a
         * prototype stack with count 1 paired with the total quantity stored for that type.
         *
         * @return A list of item records representing all stored book types and quantities.
         */
        @Override
        public NonNullList<IItemRepository.ItemRecord> getAllItems() {
            NonNullList<IItemRepository.ItemRecord> list = NonNullList.create();
            for (HashMap.Entry<String, ItemStack> entry : prototypesByKey.entrySet()) {
                int total = counts.getOrDefault(entry.getKey(), 0);
                list.add(new IItemRepository.ItemRecord(entry.getValue().copy(), total));
            }

            return list;
        }

        /**
         * Slotless insertion used by external systems. Honors an optional predicate to filter
         * acceptable stacks.
         *
         * @param stack The stack to insert.
         * @param simulate If true, do not modify state; only compute the result.
         * @param predicate Optional filter to accept/reject the item type.
         * @return The remainder if not fully inserted, or empty if completely accepted.
         */
        @Override
        public ItemStack insertItem(ItemStack stack, boolean simulate, Predicate<ItemStack> predicate) {
            if (stack.isEmpty() || !(isSpellBook(stack) || isIdentificationScroll(stack))) return stack;
            if (predicate != null && !predicate.test(stack)) return stack;

            return insertItem(0, stack, simulate);
        }

        /**
         * Slotless extraction used by external systems. Honors an optional predicate to filter
         * acceptable outputs.
         *
         * @param stack Template stack indicating the desired type.
         * @param amount The maximum amount to extract.
         * @param simulate If true, do not modify state; only compute the result.
         * @param predicate Optional filter to accept/reject the item type.
         * @return A stack representing the extracted amount, or empty if unavailable.
         */
        @Override
        public ItemStack extractItem(ItemStack stack, int amount, boolean simulate, Predicate<ItemStack> predicate) {
            if (amount <= 0 || stack.isEmpty() || !isSpellBook(stack)) return ItemStack.EMPTY;
            if (predicate != null && !predicate.test(stack)) return ItemStack.EMPTY;

            String key = keyOf(stack);
            return extractItemFromKey(key, amount, simulate);
        }
    }

    /**
     * Inserts spell books into the archive.
     *
     * @param stack The stack to insert.
     * @return The remainder if not fully inserted, or empty if completely accepted.
     */
    public ItemStack addBooks(ItemStack stack) {
        return itemHandler.insertItem(0, stack, false);
    }

    /**
     * Inserts spell books into the archive.
     *
     * @param stack The stack to insert.
     * @param count The number of books to insert.
     * @return The remainder if not fully inserted, or empty if completely accepted.
     */
    public ItemStack addBooks(ItemStack stack, int count) {
        ItemStack toInsert = stack.copy();
        toInsert.setCount(count);
        return itemHandler.insertItem(0, toInsert, false);
    }

    /**
     * Returns true if the given stack is a Wizardry identification scroll.
     * Uses registry name for loose coupling.
     */
    public boolean isIdentificationScroll(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        ResourceLocation rl = item.getRegistryName();
        return rl != null && "ebwizardry".equals(rl.getNamespace()) && "identification_scroll".equals(rl.getPath());
    }

    /**
     * Adds identification scrolls to the internal reserve. Not exposed to external item handlers.
     *
     * @param amount Number of scrolls to add (<= 0 ignored).
     */
    public int addIdentificationScrolls(int amount) {
        if (amount <= 0) return 0;

        int max = com.spellarchives.config.SpellArchivesConfig.getScrollReserveMax();
        if (max < 0) max = Integer.MAX_VALUE;

        int cur = this.identificationScrolls;
        if (cur >= max) return 0;

        int accepted = Math.min(amount, max - cur);
        if (accepted > 0) {
            this.identificationScrolls = cur + accepted;
            onContentsChanged();
        }

        return accepted;
    }

    /**
     * Consumes up to the requested number of identification scrolls.
     *
     * @param amount Maximum number to consume.
     * @return The number actually consumed.
     */
    public int consumeIdentificationScrolls(int amount) {
        if (amount <= 0) return 0;

        int taken = Math.min(amount, this.identificationScrolls);
        if (taken > 0) {
            this.identificationScrolls -= taken;
            onContentsChanged();
        }

        return taken;
    }

    /**
     * Public getter for client GUI to display the reserve.
     */
    public int getIdentificationScrollCountPublic() {
        return this.identificationScrolls;
    }

    /**
     * Removes spell books from the archive.
     *
     * @param stack The spell book type to remove.
     * @param count The number of books to remove.
     * @return The extracted books, or empty if none were available.
     */
    public ItemStack removeBooks(ItemStack stack, int count) {
        return ((IItemRepository) itemHandler).extractItem(stack, count, false, null);
    }

    /**
     * Checks whether the given stack is a Wizardry spell book.
     *
     * @param stack The stack to test.
     * @return True if the stack is a spell book.
     */
    public boolean isSpellBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemSpellBook;
    }

    /**
     * Encodes a stack into a runtime key of the form "modid:item|meta".
     *
     * @param stack The stack to encode.
     * @return The encoded key.
     */
    private String keyOf(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation rl = item.getRegistryName();
        int meta = stack.getMetadata();

        return rl + "|" + meta;
    }

    /**
     * Decodes a runtime key back into a 1-count ItemStack with the stored metadata.
     * Returns ItemStack.EMPTY if the item cannot be resolved.
     *
     * @param key The encoded key to decode.
     * @return A 1-count stack for the key, or ItemStack.EMPTY if unknown.
     */
    private ItemStack stackFromKey(String key) {
        String[] parts = key.split("\\|", 2);
        if (parts.length < 2) return ItemStack.EMPTY;

        ResourceLocation rl = new ResourceLocation(parts[0]);
        int meta = Integer.parseInt(parts[1]);

        Item item = Item.REGISTRY.getObject(rl);
        if (item == null) return ItemStack.EMPTY;

        return new ItemStack(item, 1, meta);
    }

    /**
     * Converts a runtime key (rl|meta) to a spell registry name for NBT storage.
     * Returns null if the spell cannot be resolved.
     *
     * @param key The runtime key to convert.
     * @return The spell registry name, or null if not found.
     */
    private static String keyToSpellName(String key) {
        buildSpellMappings();

        String[] parts = key.split("\\|", 2);
        if (parts.length < 2) return null;

        try {
            int meta = Integer.parseInt(parts[1]);
            return metadataToSpellName.get(meta);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converts a spell registry name from NBT back to a runtime key (rl|meta).
     * Returns null if the spell no longer exists.
     *
     * @param spellName The spell's registry name.
     * @return The runtime key for the current environment, or null if unmapped.
     */
    private static String spellNameToKey(String spellName) {
        buildSpellMappings();
        buildSpellBookIndex();

        Integer meta = spellNameToMetadata.get(spellName);
        if (meta == null) return null;

        String modid = "ebwizardry";
        int i = spellName.indexOf(':');
        if (i > 0) modid = spellName.substring(0, i);

        Item spellBook = spellBookByMod.get(modid);
        if (spellBook == null) spellBook = defaultSpellBook;
        if (spellBook == null || spellBook.getRegistryName() == null) return null;

        return spellBook.getRegistryName() + "|" + meta;
    }

    /**
     * Builds the cached index of modid -> ItemSpellBook for all registered items.
     * Safe to call repeatedly; initialization happens once on first use.
     */
    private static void buildSpellBookIndex() {
        if (spellBookByMod != null) return;

        Map<String, Item> map = new HashMap<>();

        for (ResourceLocation rl : Item.REGISTRY.getKeys()) {
            Item item = Item.REGISTRY.getObject(rl);
            if (!(item instanceof ItemSpellBook)) continue;

            String modid = getNamespaceSafe(rl);
            Item existing = map.get(modid);

            // Prefer items whose path explicitly contains "spell_book"
            if (existing == null || getPathSafe(rl).contains("spell_book")) {
                map.put(modid, item);
            }
        }

        Item eb = Item.REGISTRY.getObject(new ResourceLocation("ebwizardry", "spell_book"));
        if (eb instanceof ItemSpellBook) {
            map.put("ebwizardry", eb);
            defaultSpellBook = eb;
        }

        spellBookByMod = map;
    }

    // Fallback-safe namespace extraction that works across mappings
    private static String getNamespaceSafe(ResourceLocation rl) {
        if (rl == null) return "";

        String s = rl.toString(); // namespace:path
        int idx = s.indexOf(':');

        return idx >= 0 ? s.substring(0, idx) : "";
    }

    private static String getPathSafe(ResourceLocation rl) {
        if (rl == null) return "";

        String s = rl.toString();
        int idx = s.indexOf(':');

        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    /**
     * Update internal caches to ensure the given key is represented in the slot list
     * and prototype map.
     *
     * @param key The runtime key to update.
     * @param proto The prototype stack to use for the key (should not be null).
     * @param oldSize Previous size of the counts map.
     */
    private void updateCachedForKey(String key, ItemStack proto, int oldSize) {
        // If the counts map grew, we have a new key and must create slot/prototype caches.
        if (counts.size() != oldSize) {
            slotKeys.add(key);

            proto = proto.copy();
            proto.setCount(1);
            prototypesByKey.put(key, proto);
        }
    }

    /**
     * Returns the total number of books stored matching the given stack's type.
     *
     * @param stack The spell book type.
     * @return Stored count for the type (0 if none).
     */
    public int getCountFor(ItemStack stack) {
        String key = keyOf(stack);
        return counts.getOrDefault(key, 0);
    }

    /**
     * Provides a snapshot copy of the internal counts map for safe client-side GUI rendering.
     * Keys with zero counts are omitted.
     *
     * @return A new copy of the counts map.
     */
    public Map<String, Integer> getSnapshot() {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        for (HashMap.Entry<String, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            if (count > 0) out.put(entry.getKey(), count);
        }

        return out;
    }

    /**
     * Returns the number of distinct spell types present. Used by the block model to
     * select a visual progression state.
     *
     * @return The number of distinct types.
     */
    public int getDistinctSpellTypeCount() {
        int ct = 0;
        for (int c : counts.values()) {
            if (c > 0) ct++;
        }

        return ct;
    }

    /**
     * Public adapter for GUI/network layers to reconstruct a stack from a runtime key.
     *
     * @param key The runtime key.
     * @return A 1-count stack or ItemStack.EMPTY if unknown.
     */
    public ItemStack stackFromKeyPublic(String key) {
        return prototypesByKey.getOrDefault(key, ItemStack.EMPTY);
    }

    /**
     * Public adapter for GUI/network layers to encode a stack into a runtime key.
     *
     * @param stack The stack to encode.
     * @return The encoded key.
     */
    public String keyOfPublic(ItemStack stack) {
        return keyOf(stack);
    }

    // ---- GUI helpers (Wizardry-aware with graceful fallbacks) ----
    // Local color palettes (fallback-safe across Wizardry versions)
    private static final int[] TIER_COLORS = new int[]{
        0x55FF55, // Novice
        0x5555FF, // Apprentice
        0xAA00AA, // Advanced
        0xFFD700  // Master
    };

    private static final int[] ELEMENT_COLORS = new int[]{
        0xE25822, // fire
        0x22A1E2, // ice
        0x7CFC00, // earth
        0xAAAAAA, // necromancy
        0x00CED1, // sorcery
        0xFFA500, // lightning
        0xFF69B4, // healing
        0x8A2BE2  // arcane
    };

    /**
     * Returns the Wizardry tier ordinal for the spell contained in the given stack.
     * 0 is used as a safe fallback when the spell cannot be resolved.
     *
     * @param stack The spell book stack.
     * @return The tier ordinal (0..n).
     */
    public int getTierOf(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return 0;

        return spell.getTier().ordinal();
    }

    /**
     * Returns the Wizardry element ordinal for the spell contained in the given stack.
     * 0 is used as a safe fallback when the spell cannot be resolved.
     *
     * @param stack The spell book stack.
     * @return The element ordinal (0..n).
     */
    public int getElementOf(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return 0;

        return spell.getElement().ordinal();
    }

    /**
     * Chooses a color representative of the spell tier for UI rendering.
     *
     * @param stack The spell book stack.
     * @return ARGB color integer.
     */
    public int getRarityColor(ItemStack stack) {
        int idx = Math.max(0, Math.min(getTierOf(stack), TIER_COLORS.length - 1));

        return TIER_COLORS[idx];
    }

    /**
     * Chooses a color representative of the spell element for UI rendering.
     *
     * @param stack The spell book stack.
     * @return ARGB color integer.
     */
    public int getElementColor(ItemStack stack) {
        int idx = Math.max(0, getElementOf(stack)) % ELEMENT_COLORS.length;

        return ELEMENT_COLORS[idx];
    }

    /**
     * Returns a localized, formatted display name for the spell contained in the stack.
     * Falls back to the stack's display name if the spell cannot be resolved.
     *
     * @param stack The spell book stack.
     * @return Localized display name.
     */
    public String getSpellNameLocalized(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return stack.getDisplayName();

        // Use Wizardry's provided formatted display name; GUI applies extra formatting as needed
        return spell.getDisplayNameWithFormatting();
    }

    /**
     * Resolves the Wizardry spell instance from a spell book stack.
     *
     * @param stack The spell book stack.
     * @return The spell, or null if the stack is not a spell book or cannot be mapped.
     */
    private Spell getSpell(ItemStack stack) {
        if (!isSpellBook(stack)) return null;

        return Spell.byMetadata(stack.getItemDamage());
    }

    /**
     * Public adapter to expose the resolved spell to client GUI code.
     *
     * @param stack The spell book stack.
     * @return The resolved spell or null.
     */
    public Spell getSpellPublic(ItemStack stack) {
        return getSpell(stack);
    }

    /**
     * Builds tooltip/description lines for the given stack in the context of a player.
     * Currently shows only the stored count.
     *
     * @param stack The spell book stack.
     * @param player The player viewing the tooltip.
     * @return A list of localized description lines.
     */
    @SideOnly(Side.CLIENT)
    public List<String> getDescriptionLines(ItemStack stack, EntityPlayer player) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.spellarchives.count_fmt", getCountFor(stack)));

        return lines;
    }

    /**
     * Serializes the archive contents to NBT. Spells are persisted by spell registry name to
     * be resilient to item metadata reassignments.
     *
     * @param compound Destination NBT compound to write into.
     * @return The same compound for chaining.
     */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        NBTTagList list = new NBTTagList();
        for (HashMap.Entry<String, Integer> entry : counts.entrySet()) {
            String spellName = keyToSpellName(entry.getKey());

            if (spellName != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("spell", spellName);
                tag.setInteger("count", entry.getValue());
                list.appendTag(tag);
            }
        }
        compound.setTag("spells", list);
        compound.setInteger("rev", this.changeCounter);

        compound.setInteger("id_scrolls", this.identificationScrolls);

        return compound;
    }

    /**
     * Deserializes archive contents from NBT. Attempts to map spell registry names back to
     * the current spell book item/metadata; unknown spells (e.g., from missing mods) are
     * reported and skipped.
     *
     * @param compound Source NBT compound to read from.
     */
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        // Clear all slot-backed structures
        slotKeys.clear();
        prototypesByKey.clear();
        counts.clear();

        if (compound.hasKey("spells")) {
            NBTTagList list = compound.getTagList("spells", 10);
            int unmappedCount = 0;
            Map<String, Long> unmappedByMod = new LinkedHashMap<>();

            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                String spellName = tag.getString("spell");
                int count = tag.getInteger("count");

                // Convert spell name to runtime key
                String runtimeKey = spellNameToKey(spellName);

                ItemStack proto = null;
                if (runtimeKey != null) proto = stackFromKey(runtimeKey);

                if (runtimeKey == null || proto == null || proto.isEmpty()) {
                    // unmapped spell (mod removed or spell deleted)
                    unmappedCount++;
                    String modid = spellName.contains(":") ? spellName.split(":", 2)[0] : "unknown";
                    unmappedByMod.put(modid, unmappedByMod.getOrDefault(modid, 0L) + count);
                } else {
                    // create caches for this runtime key using the decoded prototype
                    counts.put(runtimeKey, count);
                    updateCachedForKey(runtimeKey, proto, 0);  // everything is new at this point, so oldSize=0
                }
            }

            if (unmappedCount > 0) {
                SpellArchives.LOGGER.warn("Spellcaster's Archives at " + pos + " failed to map " + unmappedCount + " spell(s) from removed mods:");
                for (Map.Entry<String, Long> entry : unmappedByMod.entrySet()) {
                    SpellArchives.LOGGER.warn("  - Mod '" + entry.getKey() + "': " + entry.getValue() + " book(s)");
                }
            }
        }

        this.changeCounter = compound.getInteger("rev");

        this.identificationScrolls = compound.getInteger("id_scrolls");
    }

    /**
     * Exposes the current change counter for client polling/GUI cache invalidation.
     *
     * @return The current change counter.
     */
    public int getChangeCounterPublic() {
        return changeCounter;
    }

    /**
     * Advertises supported capabilities (item handler and, if present, the Storage Drawers
     * item repository capability).
     *
     * @param capability The queried capability.
     * @param facing The side (may be null).
     * @return True if supported.
     */
    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (suppressCap) return false;

        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;

        if (SD_REPO_CAP != null && capability == SD_REPO_CAP) return true;

        return super.hasCapability(capability, facing);
    }

    /**
     * Provides the single shared handler instance for supported capabilities.
     *
     * @param capability The requested capability type.
     * @param facing The side (may be null).
     * @return The handler instance cast to the requested type, or null if unsupported.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (suppressCap) return null;

        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) itemHandler;

        if (SD_REPO_CAP != null && capability == SD_REPO_CAP) return (T) itemHandler;

        return super.getCapability(capability, facing);
    }

    /**
     * Called by the block right before scheduling end-of-tick removal to temporarily
     * hide capabilities during neighbor updates.
     */
    public void suppressExternalCaps() {
        this.suppressCap = true;
    }

    /**
     * Ensure the tile entity is NOT refreshed (re-created) on mere blockstate/property changes
     * like rotations; only refresh if the actual block instance changes.
     * 
     * @param world The world.
     * @param pos The block position.
     * @param oldState The old block state.
     * @param newState The new block state.
     * @return True to refresh (remove/recreate), false to keep existing tile.
     */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // ---- Client sync ----
    /**
     * Creates the update NBT payload used for client synchronization.
     *
     * @return The NBT payload representing current state.
     */
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    /**
     * Creates the network packet used to synchronize this tile entity to clients.
     *
     * @return The packet with current update tag.
     */
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    /**
     * Applies an incoming client synchronization packet and requests a re-render so that
     * model variants depending on the archive state are updated.
     *
     * @param net Network manager.
     * @param pkt Incoming update packet.
     */
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());

        // Force a client-side re-render so the block state is re-queried and the correct model variant is selected
        if (world != null) world.markBlockRangeForRenderUpdate(getPos(), getPos());
    }

    /**
     * Marks the tile dirty and notifies the world/clients that the archive contents have
     * changed. Also increments the change counter for GUI cache invalidation.
     */
    private void onContentsChanged() {
        markDirty();
        if (world != null && !world.isRemote) {
            this.changeCounter++;
            IBlockState state = world.getBlockState(getPos());
            world.notifyBlockUpdate(getPos(), state, state, 3);
            world.scheduleBlockUpdate(getPos(), state.getBlock(), 1, 0);
        }
    }
}
