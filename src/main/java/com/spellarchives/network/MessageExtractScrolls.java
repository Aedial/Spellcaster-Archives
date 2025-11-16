package com.spellarchives.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.spellarchives.tile.TileSpellArchive;

/**
 * Server message to extract identification scrolls from the archive's reserve into the player's carried item.
 */
public class MessageExtractScrolls implements IMessage {
    private BlockPos pos;
    private boolean half; // if true, take half; else take all

    public MessageExtractScrolls() {}

    public MessageExtractScrolls(BlockPos pos, boolean half) {
        this.pos = pos;
        this.half = half;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeBoolean(this.half);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.half = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<MessageExtractScrolls, IMessage> {
        @Override
        public IMessage onMessage(MessageExtractScrolls message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player == null || player.world == null) return;
                if (!(player.world.getTileEntity(message.pos) instanceof TileSpellArchive)) return;

                TileSpellArchive tile = (TileSpellArchive) player.world.getTileEntity(message.pos);
                int available = tile.getIdentificationScrollCountPublic();
                if (available <= 0) return;

                int toTake = (message.half ? (int) Math.max(1, (((long) available) + 1) / 2) : available);

                ItemStack carried = player.inventory.getItemStack(); // carried (cursor) stack
                ItemStack scrollItem = new ItemStack(Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation("ebwizardry", "identification_scroll")));
                if (scrollItem.isEmpty()) return;

                if (carried.isEmpty()) {
                    // Create new carried stack of up to toTake
                    int taken = tile.consumeIdentificationScrolls(toTake);
                    if (taken > 0) {
                        ItemStack out = scrollItem.copy();
                        out.setCount(taken);
                        player.inventory.setItemStack(out);
                        player.updateHeldItem();
                    }
                } else if (carried.getItem() == scrollItem.getItem()) {
                    // Merge into existing carried stack without artificial limit
                    int taken = tile.consumeIdentificationScrolls(toTake);
                    if (taken > 0) {
                        carried.grow(taken);
                        player.inventory.setItemStack(carried);
                        player.updateHeldItem();
                    }
                } else {
                    // Different carried item; do nothing
                }
            });
            return null;
        }
    }
}
