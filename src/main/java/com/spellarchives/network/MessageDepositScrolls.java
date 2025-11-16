package com.spellarchives.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.spellarchives.tile.TileSpellArchive;

/**
 * Server message to deposit identification scrolls into the archive's internal reserve.
 */
public class MessageDepositScrolls implements IMessage {
    private BlockPos pos;
    private int amount;

    public MessageDepositScrolls() {}

    public MessageDepositScrolls(BlockPos pos, int amount) {
        this.pos = pos;
        this.amount = amount;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeInt(this.amount);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.amount = buf.readInt();
    }

    public static class Handler implements IMessageHandler<MessageDepositScrolls, IMessage> {
        @Override
        public IMessage onMessage(MessageDepositScrolls message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player == null || player.world == null) return;

                if (player.world.getTileEntity(message.pos) instanceof TileSpellArchive) {
                    TileSpellArchive tile = (TileSpellArchive) player.world.getTileEntity(message.pos);

                    // Use carried (cursor) stack for GUI drag/drop semantics
                    ItemStack carried = player.inventory.getItemStack();
                    if (!tile.isIdentificationScroll(carried)) return;

                    int req = Math.max(1, Math.min(message.amount, carried.getCount()));
                    if (req <= 0) return;

                    int accepted = tile.addIdentificationScrolls(req);
                    if (accepted <= 0) return;

                    if (!player.isCreative()) {
                        carried.shrink(accepted);
                        if (carried.getCount() <= 0) player.inventory.setItemStack(ItemStack.EMPTY);
                        else player.inventory.setItemStack(carried);

                        player.updateHeldItem();
                    }
                }
            });

            return null;
        }
    }
}
