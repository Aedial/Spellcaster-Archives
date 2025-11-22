package com.spellarchives.gui;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.client.ClientProxy;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.data.SpellGlyphData;
import electroblob.wizardry.data.WizardData;
import electroblob.wizardry.spell.Spell;

import com.spellarchives.config.ClientConfig;
import com.spellarchives.config.SpellArchivesConfig;
import com.spellarchives.gui.GuiSpellArchive.BookEntry;
import com.spellarchives.gui.SpellPresentation;
import com.spellarchives.gui.widget.InstructionWidget;
import com.spellarchives.tile.TileSpellArchive;
import com.spellarchives.util.TextUtils;


public class RightPanelRenderer {
    private final GuiSpellArchive gui;
    private final Minecraft mc;
    private final FontRenderer fontRenderer;
    private final InstructionWidget instructionWidget;

    public RightPanelRenderer(GuiSpellArchive gui) {
        this.gui = gui;
        this.mc = Minecraft.getMinecraft();
        this.fontRenderer = mc.fontRenderer;
        this.instructionWidget = new InstructionWidget(mc, fontRenderer);
        this.instructionWidget.setLineHeight(ClientConfig.RIGHT_DESC_LINE_HEIGHT);
    }

    public void render(BookEntry hovered, int rightPanelX, int rightPanelY, int rightPanelW, int rightPanelH) {
        int rightX = rightPanelX + ClientConfig.RIGHT_PANEL_INNER_MARGIN;
        int rightY = rightPanelY + ClientConfig.RIGHT_PANEL_INNER_MARGIN;

        if (hovered == null) {
            gui.getCacheManager().clearCachedPresentation();
            return;
        }

        String key = gui.getTile().keyOfPublic(hovered.stack);
        Integer live = gui.getTile().getSnapshot().get(key);
        int liveCount = live == null ? 0 : live.intValue();

        SpellPresentation p = gui.getCacheManager().getCachedPresentation(key, liveCount);
        if (p == null) {
            p = buildSpellPresentation(hovered, liveCount);
            gui.getCacheManager().putCachedPresentation(key, p);
        }

        if (p != null) drawBookInfoFromPresentation(p, rightX, rightY, 0xFFFFFF, rightPanelX, rightPanelY, rightPanelW, rightPanelH);
    }

    private void drawBookInfoFromPresentation(SpellPresentation p, int x, int y, int color, int rightPanelX, int rightPanelY, int rightPanelW, int rightPanelH) {
        drawHeader(p, x, y, color, rightPanelX, rightPanelW);

        int rowY = y + ClientConfig.RIGHT_AFTER_HEADER_GAP;
        rowY = drawElementLine(p, x, rowY, color);
        rowY = drawPropertiesLines(p, x, rowY);
        rowY += ClientConfig.RIGHT_SECTION_GAP;

        drawDescriptionAndIcon(p, x, rowY, color, rightPanelX, rightPanelY, rightPanelW, rightPanelH);
    }

    private SpellPresentation buildSpellPresentation(BookEntry b, int effectiveCount) {
        ItemStack toShow = b.stack;
        Spell spell = b.spell != null ? b.spell : gui.getTile().getSpellPublic(toShow);
        if (spell == null) return null;

        String headerName;
        ResourceLocation spellIcon = null;
        String description;
        if (b.discovered) {
            headerName = "\u00A77" + spell.getDisplayNameWithFormatting();
            spellIcon = spell.getIcon();
            description = spell.getDescription();
        } else {
            World world = Wizardry.proxy.getTheWorld();
            headerName = "\u00A79" + SpellGlyphData.getGlyphName(spell, world);
            description = SpellGlyphData.getGlyphDescription(spell, world);
        }

        String tierName = spell.getTier().getDisplayNameWithFormatting();

        Element element = spell.getElement();
        String elementName = element.getFormattingCode() + element.getDisplayName();
        ResourceLocation elementIcon = element.getIcon();

        int cost = spell.getCost();
        boolean isContinuous = spell.isContinuous;
        int chargeUpTime = spell.getChargeup();
        int cooldown = spell.getCooldown();

        return new SpellPresentation(toShow, spell, b.discovered, headerName, spellIcon, description, tierName,
                     elementName, elementIcon, effectiveCount, cost, isContinuous, chargeUpTime, cooldown);
    }

    private void drawHeader(SpellPresentation p, int x, int y, int color, int rightPanelX, int rightPanelW) {
        String countStr = TextUtils.formatCompactCount(p.count) + "x ";
        int textStartX = x + ClientConfig.RIGHT_TITLE_TEXT_GAP;
        int rightContentRight = rightPanelX + rightPanelW - ClientConfig.RIGHT_PANEL_INNER_MARGIN;
        int maxHeaderW = Math.max(0, rightContentRight - textStartX);

        String header = p.discovered ? p.headerName : "#" + p.headerName;
        FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;
        int spaceLeft = Math.max(0, maxHeaderW - 2);
        String headerFitted = TextUtils.trimToWidth(fontRendererInstance, countStr + header, spaceLeft);

        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(p.stack, x, y);
        RenderHelper.disableStandardItemLighting();

        fontRendererInstance.drawString(headerFitted, textStartX + 2, y + 4, color);
    }

    private int drawElementLine(SpellPresentation p, int x, int rowY, int color) {
        int colLeft = x;
        int textLeft = x + ClientConfig.RIGHT_ELEMENT_ICON_SIZE + 4;

        if (p.elementIcon != null) {
            mc.getTextureManager().bindTexture(p.elementIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawScaledCustomSizeModalRect(colLeft, rowY, 0, 0, 16, 16, ClientConfig.RIGHT_ELEMENT_ICON_SIZE, ClientConfig.RIGHT_ELEMENT_ICON_SIZE, 16, 16);
        }

        fontRenderer.drawString(p.elementName, textLeft, rowY, color);

        return rowY + ClientConfig.RIGHT_LINE_GAP_MEDIUM;
    }

    private int drawPropertiesLines(SpellPresentation p, int x, int rowY) {
        String costStr = I18n.format("gui.spellarchives.cost_unknown");
        String cooldownStr = I18n.format("gui.spellarchives.cooldown_unknown");
        String chargeStr = I18n.format("gui.spellarchives.charge_unknown");

        if (p.discovered) {
            costStr = I18n.format("gui.spellarchives.cost_fmt", p.cost, p.isContinuous ? "/" + I18n.format("timeunit.s") : "");
            cooldownStr = I18n.format("gui.spellarchives.cooldown_fmt", TextUtils.formatTimeTicks(p.cooldown));
            chargeStr = I18n.format("gui.spellarchives.charge_fmt", TextUtils.formatTimeTicks(p.chargeUpTime));
        }

        fontRenderer.drawString(costStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(cooldownStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

        fontRenderer.drawString(chargeStr, x, rowY, ClientConfig.DETAIL_TEXT);
        rowY += ClientConfig.RIGHT_LINE_GAP_SMALL;

        return rowY;
    }

    private void drawDescriptionAndIcon(SpellPresentation p, int x, int rowY, int color, int rightPanelX, int rightPanelY, int rightPanelW, int rightPanelH) {
        String desc = p.description != null ? p.description : "";
        int maxW = rightPanelW - ClientConfig.RIGHT_PANEL_TEXT_SIDE_PAD * 2;
        int safeMaxW = Math.max(0, maxW);
        boolean showIcon = p.discovered && p.spellIcon != null;
        int contentWidth = rightPanelW - ClientConfig.RIGHT_PANEL_INNER_MARGIN * 2;
        int iconW = showIcon ? contentWidth : 0;
        int iconH = iconW;
        int bottomClamp = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH - (showIcon ? 4 : 0);

        if (safeMaxW > 0 && desc.length() > 0) {
            FontRenderer fontRendererInstance = p.discovered ? fontRenderer : ClientProxy.mixedFontRenderer;
            String safeDesc = TextUtils.sanitizeForBreakIterator(desc);

            for (String line : TextUtils.wrapTextToWidth(fontRendererInstance, safeDesc, safeMaxW)) {
                if (rowY + ClientConfig.RIGHT_DESC_LINE_HEIGHT > bottomClamp) break;
                if (!p.discovered) line = "#" + line;

                fontRendererInstance.drawString(line, x, rowY, color);
                rowY += ClientConfig.RIGHT_DESC_LINE_HEIGHT;
            }
        }

        if (showIcon) {
            int iconX = rightPanelX + ClientConfig.RIGHT_PANEL_INNER_MARGIN;
            int iconY = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH;
            mc.getTextureManager().bindTexture(p.spellIcon);
            GlStateManager.color(1f, 1f, 1f, 1f);
            Gui.drawScaledCustomSizeModalRect(iconX, iconY, 0, 0, 16, 16, iconW, iconH, 16, 16);
        }

        int instColor = ClientConfig.DETAIL_TEXT;
        String inst1 = I18n.format("gui.spellarchives.inst.left_extract");
        String inst2 = I18n.format("gui.spellarchives.inst.shift_left_stack", p.stack.getMaxStackSize());
        String inst3;
        if (!p.discovered) {
            if (!SpellArchivesConfig.isScrollReserveEnabled()) inst3 = I18n.format("gui.spellarchives.inst.right_discover_disabled");
            else if (gui.getTile().getIdentificationScrollCountPublic() <= 0) inst3 = I18n.format("gui.spellarchives.inst.right_discover_add");
            else inst3 = I18n.format("gui.spellarchives.inst.right_discover");
        } else {
            inst3 = I18n.format("gui.spellarchives.inst.right_discover_known");
        }

        instructionWidget.clear();
        instructionWidget.addInstruction(inst1);
        instructionWidget.addInstruction(inst2);
        if (inst3 != null && !inst3.isEmpty()) instructionWidget.addInstruction(inst3);

        instructionWidget.setColor(instColor);
        instructionWidget.setAlignBottom(true);

        int iconTopY = rightPanelY + rightPanelH - ClientConfig.RIGHT_BOTTOM_CLAMP_MARGIN - iconH - (showIcon ? 4 : 0);
        int minAllowed = rowY + ClientConfig.RIGHT_SECTION_GAP / 2;
        int availableHeight = iconTopY - minAllowed;

        if (availableHeight > 0) {
            instructionWidget.setBounds(x, minAllowed, safeMaxW, availableHeight);
            instructionWidget.draw(0, 0, 0);
        }
    }
}
