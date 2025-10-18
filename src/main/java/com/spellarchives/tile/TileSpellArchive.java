package com.spellarchives.tile;

import electroblob.wizardry.item.ItemSpellBook;
import electroblob.wizardry.spell.Spell;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.Map;

public class TileSpellArchive extends TileEntity implements ITickable {

    // Map key: rl|meta (NBT intentionally ignored), value: total count
    // Use LinkedHashMap to keep slot order stable for external handlers (e.g., hoppers)
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    // Overflow is always voided (no toggle)

    // Incremented on content changes; synced to client for GUI refresh
    private int changeCounter = 0;

    // Capability: dynamic view, at least 1 slot for insertion when empty
    private final IItemHandler itemHandler = new IItemHandler() {

        @Override
        public int getSlots() {
            // Expose one extra empty slot to allow inserting new book types
            return counts.size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // The last slot is the virtual insertion slot; always empty
            if (slot == counts.size()) return ItemStack.EMPTY;

            String key = keyAt(slot);
            if (key == null) return ItemStack.EMPTY;

            ItemStack template = stackFromKey(key);
            if (template.isEmpty()) return ItemStack.EMPTY;

            int available = counts.getOrDefault(key, 0);
            template.setCount(Math.min(available, template.getMaxStackSize()));
            return template;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !isSpellBook(stack)) return stack;

            String key = keyOf(stack);
            // If inserting into a concrete slot, only accept the matching key; otherwise reject
            if (slot != counts.size()) {
                String slotKey = keyAt(slot);
                if (slotKey == null || !slotKey.equals(key)) return stack;
            }
            int current = counts.getOrDefault(key, 0);
            int space = Integer.MAX_VALUE - current;

            if (space <= 0) {
                // Already at cap; consume and void
                return ItemStack.EMPTY;
            }

            int toInsert = stack.getCount();

            if (toInsert > space) {
                // Overflow case: insert up to cap and void the rest
                if (!simulate) {
                    counts.put(key, Integer.MAX_VALUE);
                    onContentsChanged();
                }
                return ItemStack.EMPTY;
            }

            // Normal insert within capacity
            if (!simulate) {
                counts.put(key, current + toInsert);
                onContentsChanged();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) return ItemStack.EMPTY;
            // Nothing to extract from the virtual insertion slot
            if (slot == counts.size()) return ItemStack.EMPTY;

            String key = keyAt(slot);
            if (key == null) return ItemStack.EMPTY;

            ItemStack base = stackFromKey(key);
            if (base.isEmpty()) return ItemStack.EMPTY;

            int available = counts.getOrDefault(key, 0);
            int toExtract = Math.min(amount, Math.min(available, base.getMaxStackSize()));
            if (toExtract <= 0) return ItemStack.EMPTY;

            ItemStack out = base.copy();
            out.setCount(toExtract);

            if (!simulate) removeBooks(out, toExtract);
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty() || !isSpellBook(stack)) return false;
            // Virtual insertion slot accepts any valid book
            if (slot == counts.size()) return true;
            // Concrete slots accept only their own book key for merging
            String key = keyAt(slot);
            return key != null && key.equals(keyOf(stack));
        }
    };

    public boolean isSpellBook(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ItemSpellBook;
    }

    private String keyOf(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation rl = item.getRegistryName();
        int meta = stack.getMetadata();
        return rl + "|" + meta;
    }

    private ItemStack stackFromKey(String key) {
        String[] parts = key.split("\\|", 2);
        if (parts.length < 2) return ItemStack.EMPTY;

        ResourceLocation rl = new ResourceLocation(parts[0]);
        int meta = Integer.parseInt(parts[1]);

        Item item = Item.REGISTRY.getObject(rl);
        if (item == null) return ItemStack.EMPTY;

        return new ItemStack(item, 1, meta);
    }

    private String keyAt(int slot) {
        if (counts.isEmpty() || slot < 0 || slot >= counts.size()) return null;

        int i = 0;
        for (String k : counts.keySet()) {
            if (i++ == slot) return k;
        }
        return null;
    }

    public int addBooks(ItemStack stack, int count) {
        String key = keyOf(stack);
        int current = counts.getOrDefault(key, 0);

        if (count <= 0) return 0;

        int space = Integer.MAX_VALUE - current;
        if (space <= 0) {
            // Already full; treat as accepted (voided)
            return count;
        }

        if (count > space) {
            // Fill to cap and void the remainder; report all handled
            counts.put(key, Integer.MAX_VALUE);
            onContentsChanged();
            return count;
        } else {
            int newVal = current + count;
            counts.put(key, newVal);
            onContentsChanged();
            return count;
        }
    }

    public int removeBooks(ItemStack stack, int count) {
        String key = keyOf(stack);
        int current = counts.getOrDefault(key, 0);

        int removed = Math.min(current, count);
        if (removed > 0) {
            int remaining = current - removed;
            if (remaining == 0) {
                counts.remove(key);
            } else {
                counts.put(key, remaining);
            }
            onContentsChanged();
        }
        return removed;
    }

    public int getCountFor(ItemStack stack) {
        return counts.getOrDefault(keyOf(stack), 0);
    }

    // Expose a stable snapshot for client GUI rendering
    public Map<String, Integer> getSnapshot() {
        return new LinkedHashMap<>(counts);
    }

    // Public adapter for GUI to reconstruct stacks
    public ItemStack stackFromKeyPublic(String key) {
        return stackFromKey(key);
    }

    // Public adapter for GUI/network to encode a stack key
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

    public int getTierOf(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return 0;

        return spell.getTier().ordinal();
    }

    public int getElementOf(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return 0;

        return spell.getElement().ordinal();
    }

    public int getRarityColor(ItemStack stack) {
        int idx = Math.max(0, Math.min(getTierOf(stack), TIER_COLORS.length - 1));

        return TIER_COLORS[idx];
    }

    public int getElementColor(ItemStack stack) {
        int idx = Math.max(0, getElementOf(stack)) % ELEMENT_COLORS.length;

        return ELEMENT_COLORS[idx];
    }

    public String getSpellNameLocalized(ItemStack stack) {
        Spell spell = getSpell(stack);
        if (spell == null) return stack.getDisplayName();
        // Use Wizardry's provided formatted display name; GUI applies extra formatting as needed
        return spell.getDisplayNameWithFormatting();
    }

    private Spell getSpell(ItemStack stack) {
        if (!isSpellBook(stack)) return null;
        return Spell.byMetadata(stack.getItemDamage());
    }

    // Discovery handled on the client using Wizardry API

    // Expose spell for client GUI rendering
    public Spell getSpellPublic(ItemStack stack) {
        return getSpell(stack);
    }

    public java.util.List<String> getDescriptionLines(ItemStack stack, net.minecraft.entity.player.EntityPlayer player) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Count: " + getCountFor(stack));

        return lines;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("k", e.getKey());
            tag.setInteger("v", e.getValue());
            list.appendTag(tag);
        }
        compound.setTag("counts", list);

        compound.setInteger("rev", this.changeCounter);

        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        counts.clear();
        NBTTagList list = compound.getTagList("counts", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            counts.put(tag.getString("k"), tag.getInteger("v"));
        }

        this.changeCounter = compound.getInteger("rev");
    }

    public int getChangeCounterPublic() {
        return changeCounter;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, net.minecraft.util.EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) itemHandler;

        return super.getCapability(capability, facing);
    }

    @Override
    public void update() {
        // No ticking behavior needed for now
    }

    // ---- Client sync ----
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(getPos(), 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    // Notify world/clients and save when contents change
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
