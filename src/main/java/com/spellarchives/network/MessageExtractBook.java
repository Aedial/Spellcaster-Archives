package com.spellarchives.network;

import java.nio.charset.StandardCharsets;
import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.spellarchives.tile.TileSpellArchive;


/**
 * Client->server request to extract a specific number of spell books of a given key from
 * a Spell Archive tile. The server validates the tile and key, removes the books, and
 * transfers them to the player's inventory (dropping if full).
 */
public class MessageExtractBook implements IMessage {
    private BlockPos pos;
    private String key;
    private int amount;

    public MessageExtractBook() {}

    public MessageExtractBook(BlockPos pos, String key, int amount) {
        this.pos = pos;
        this.key = key;
        this.amount = amount;
    }

    /**
     * Decodes the message from the network buffer.
     *
     * @param buf The input byte buffer.
     */
    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int len = buf.readInt();
        byte[] arr = new byte[len];
        buf.readBytes(arr);
        this.key = new String(arr, StandardCharsets.UTF_8);
        this.amount = buf.readInt();
    }

    /**
     * Encodes the message into the network buffer.
     *
     * @param buf The output byte buffer.
     */
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());

        byte[] arr = key.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(arr.length);
        buf.writeBytes(arr);
        buf.writeInt(amount);
    }

    /**
     * Stateless handler processing extraction requests on the server thread.
     */
    public static class Handler implements IMessageHandler<MessageExtractBook, IMessage> {
        /**
         * Schedules work on the main server thread to perform validation and extraction.
         *
         * @param message The received extraction request.
         * @param ctx Message context.
         * @return Null response (fire-and-forget).
         */
        @Override
        public IMessage onMessage(MessageExtractBook message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            WorldServer world = player.getServerWorld();

            MinecraftServer server = player.getServer();
            if (server == null) return null;

            server.addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (!(te instanceof TileSpellArchive)) return;

                TileSpellArchive tile = (TileSpellArchive) te;
                ItemStack template = tile.stackFromKeyPublic(message.key);
                if (template.isEmpty()) return;

                ItemStack extracted = tile.removeBooks(template, message.amount);
                if (!extracted.isEmpty()) {
                    if (!player.inventory.addItemStackToInventory(extracted)) {
                        player.dropItem(extracted, false);
                    }

                    // Ensure inventory updates are pushed while a custom container is open
                    player.inventoryContainer.detectAndSendChanges();
                }
            });

            return null;
        }
    }
}
