package com.spellarchives.network;

import com.spellarchives.tile.TileSpellArchive;

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

import java.nio.charset.StandardCharsets;

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

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int len = buf.readInt();
        byte[] arr = new byte[len];
        buf.readBytes(arr);
        this.key = new String(arr, StandardCharsets.UTF_8);
        this.amount = buf.readInt();
    }

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

    public static class Handler implements IMessageHandler<MessageExtractBook, IMessage> {
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

                int removed = tile.removeBooks(template, message.amount);
                if (removed > 0) {
                    ItemStack give = template.copy();
                    give.setCount(removed);
                    if (!player.inventory.addItemStackToInventory(give)) {
                        player.dropItem(give, false);
                    }

                    // Ensure inventory updates are pushed while a custom container is open
                    player.inventoryContainer.detectAndSendChanges();
                }
            });

            return null;
        }
    }
}
