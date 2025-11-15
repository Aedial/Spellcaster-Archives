package com.spellarchives.network;

import java.nio.charset.StandardCharsets;
import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.spellarchives.config.SpellArchivesConfig;
import com.spellarchives.tile.TileSpellArchive;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.spell.Spell;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Client->server message to identify (discover) a hovered spell using one identification scroll from the archive.
 */
public class MessageDiscoverSpell implements IMessage {
    private BlockPos pos;
    private String key;

    public MessageDiscoverSpell() {}

    public MessageDiscoverSpell(BlockPos pos, String key) {
        this.pos = pos;
        this.key = key;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());

        byte[] arr = key.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(arr.length);
        buf.writeBytes(arr);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        int len = buf.readInt();
        byte[] arr = new byte[len];
        buf.readBytes(arr);
        this.key = new String(arr, StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<MessageDiscoverSpell, IMessage> {
        @Override
        public IMessage onMessage(MessageDiscoverSpell message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!SpellArchivesConfig.isScrollReserveEnabled() || player == null || player.world == null) return;

                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileSpellArchive)) return;
                TileSpellArchive tile = (TileSpellArchive) te;
                if (tile.getIdentificationScrollCountPublic() <= 0) return;

                ItemStack proto = tile.stackFromKeyPublic(message.key);
                if (proto.isEmpty()) return;

                Spell spell = tile.getSpellPublic(proto);
                if (spell == null) return;

                WizardData data = WizardData.get(player);
                if (data == null || data.hasSpellBeenDiscovered(spell)) return; // already discovered, do nothing

                // consume one scroll and discover
                int taken = tile.consumeIdentificationScrolls(1);
                if (taken <= 0) return;

                data.discoverSpell(spell);

                // play sound and show status message (above hotbar)
                player.playSound(WizardrySounds.MISC_DISCOVER_SPELL, 1.25f, 1f);
                player.sendStatusMessage(new TextComponentTranslation("spell.discover", spell.getNameForTranslationFormatted()), true);

                // Also notify the client immediately to update WizardData and refresh any open GUI
                if (spell.getRegistryName() != null) {
                    NetworkHandler.CHANNEL.sendTo(new MessageDiscoverSpellAck(spell.getRegistryName().toString()), player);
                }
            });

            return null;
        }
    }
}
