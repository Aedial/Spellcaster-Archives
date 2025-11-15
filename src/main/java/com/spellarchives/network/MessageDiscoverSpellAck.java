package com.spellarchives.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.spellarchives.gui.GuiSpellArchive;

import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.spell.Spell;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server->client acknowledgement that a spell has been discovered. Ensures client-side
 * WizardData is updated immediately so GUIs reflect the change, and shows the status message.
 */
public class MessageDiscoverSpellAck implements IMessage {
    private String spellName;

    public MessageDiscoverSpellAck() {}

    public MessageDiscoverSpellAck(String spellName) {
        this.spellName = spellName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = spellName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        this.spellName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static class Handler implements IMessageHandler<MessageDiscoverSpellAck, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(MessageDiscoverSpellAck message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().player == null) return;

                Spell spell = Spell.get(message.spellName);
                if (spell == null) return;

                WizardData data = WizardData.get(Minecraft.getMinecraft().player);
                if (data != null && !data.hasSpellBeenDiscovered(spell)) {
                    data.discoverSpell(spell);
                }

                // Show message in chat if a GUI is open to avoid being hidden; otherwise use status bar
                if (Minecraft.getMinecraft().currentScreen != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentTranslation("spell.discover", spell.getNameForTranslationFormatted()));
                } else {
                    Minecraft.getMinecraft().player.sendStatusMessage(new TextComponentTranslation("spell.discover", spell.getNameForTranslationFormatted()), true);
                }

                // Play the discovery sound client-side as well (covers cases where server-side is inaudible)
                Minecraft.getMinecraft().player.playSound(WizardrySounds.MISC_DISCOVER_SPELL, 1.25f, 1f);

                // If the Archives GUI is open, ask it to refresh cached presentation
                if (Minecraft.getMinecraft().currentScreen instanceof GuiSpellArchive) {
                    ((GuiSpellArchive) Minecraft.getMinecraft().currentScreen).onExternalStateChanged();
                }
            });

            return null;
        }
    }
}
