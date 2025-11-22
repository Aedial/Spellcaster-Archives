package com.spellarchives.gui;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;

import com.spellarchives.client.DynamicTextureFactory;
import com.spellarchives.config.ClientConfig;
import com.spellarchives.config.SpellArchivesConfig;
import com.spellarchives.gui.GuiSpellArchive.BookEntry;
import com.spellarchives.gui.GuiSpellArchive.DisplayRows;
import com.spellarchives.gui.GuiSpellArchive.GridGeometry;
import com.spellarchives.gui.GuiSpellArchive.GrooveRow;
import com.spellarchives.gui.GuiSpellArchive.PageInfo;
import com.spellarchives.util.TextUtils;


public class LeftPanelRenderer {
    private final GuiSpellArchive gui;
    private final Minecraft mc;
    private final FontRenderer fontRenderer;

    private int scrollSlotX, scrollSlotY, scrollSlotW, scrollSlotH;
    private boolean scrollSlotEnabled = true;

    public LeftPanelRenderer(GuiSpellArchive gui) {
        this.gui = gui;
        this.mc = Minecraft.getMinecraft();
        this.fontRenderer = mc.fontRenderer;
    }

    public BookEntry renderPage(DisplayRows dr, PageInfo pi, GridGeometry gg, int mouseX, int mouseY, int cellW, int cellH, int gridCols) {
        BookEntry hoveredEntry = null;

        for (GrooveRow gr : pi.layout) {
            int idx = gr.rowIndex;
            int baseY = gr.baseY;
            List<BookEntry> slice = dr.rows.get(idx);

            int grooveW = Math.min(gg.gridW, 1 + gridCols * (cellW + ClientConfig.SPINE_LEFT_BORDER));
            drawRowGroove(gg.gridX, baseY, grooveW, cellH);

            if (gr.showHeader) {
                renderTierHeader(gr, slice, gg, baseY);
            }

            for (int i = 0; i < slice.size(); i++) {
                BookEntry b = slice.get(i);
                int x = gg.gridX + i * (cellW + ClientConfig.SPINE_LEFT_BORDER);
                int y = baseY;

                renderBookSpine(b, x, y, cellW, cellH);

                if (mouseX >= x && mouseX < x + cellW + ClientConfig.SPINE_LEFT_BORDER && mouseY >= y && mouseY < y + cellH) {
                    hoveredEntry = b;
                    int hoverBorder = ClientConfig.HOVER_BORDER;
                    Gui.drawRect(x, y, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + 1, hoverBorder);
                    Gui.drawRect(x, y + cellH - 1, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + cellH, hoverBorder);
                    Gui.drawRect(x, y, x + 1, y + cellH, hoverBorder);
                    Gui.drawRect(x + cellW + ClientConfig.SPINE_LEFT_BORDER, y, x + cellW + 1 + ClientConfig.SPINE_LEFT_BORDER, y + cellH, hoverBorder);
                }
            }
        }

        return hoveredEntry;
    }

    private void renderTierHeader(GrooveRow gr, List<BookEntry> slice, GridGeometry gg, int baseY) {
        String tierText;
        int rarityRGB = 0x777777;

        if (!slice.isEmpty()) {
            Spell rep = slice.get(0).spell;
            if (rep == null) rep = gui.getTile().getSpellPublic(slice.get(0).stack);
            tierText = rep != null ? rep.getTier().getDisplayNameWithFormatting() : (I18n.format("gui.spellarchives.tier_fallback", gr.tier));
            rarityRGB = slice.get(0).rarityColor;
        } else {
            tierText = I18n.format("gui.spellarchives.tier_fallback", gr.tier);
        }

        String tierPlain = TextFormatting.getTextWithoutFormattingCodes(tierText);
        int tabPadX = ClientConfig.TAB_PADDING_X;
        int tabW = fontRenderer.getStringWidth(tierPlain) + tabPadX * 2;
        int tabH = gg.headerH;
        int headerY = baseY - gg.headerH + 1;
        int tabX = gg.gridX + ClientConfig.TAB_OFFSET_X;

        int fill = (0xCC << 24) | (rarityRGB & 0xFFFFFF);
        int border = 0xFF000000 | TextUtils.darkenColor(rarityRGB, 0.6f);
        GuiUtils.drawRoundedPanel(tabX, headerY, tabW, tabH, ClientConfig.TAB_RADIUS, fill, border);
        fontRenderer.drawString(tierPlain, tabX + tabPadX, headerY + ClientConfig.HEADER_TEXT_OFFSET_Y, 0x000000);
    }

    private void renderBookSpine(BookEntry b, int x, int y, int cellW, int cellH) {
        int elemColor;
        Spell repSpell = b.spell;
        if (repSpell == null) repSpell = gui.getTile().getSpellPublic(b.stack);
        Element repElem = repSpell != null ? repSpell.getElement() : null;
        if (repElem != null) {
            Style st = repElem.getColour();
            elemColor = TextUtils.rgbFromStyle(st, b.elementColor);
        } else {
            elemColor = b.elementColor;
        }

        int spineW = cellW;
        int spineH = cellH - (ClientConfig.SPINE_TOP_BORDER + ClientConfig.SPINE_BOTTOM_BORDER);
        if (spineW > 0 && spineH > 0) {
            ResourceLocation eIcon = (repElem != null) ? repElem.getIcon() : null;
            ResourceLocation spineTex = DynamicTextureFactory.getOrCreateSpineTexture(elemColor & 0xFFFFFF, spineW, spineH, ClientConfig.SPINE_EMBED_ICON ? eIcon : null, ClientConfig.SPINE_ICON_SIZE);

            if (spineTex != null) {
                mc.getTextureManager().bindTexture(spineTex);
                GlStateManager.color(1f, 1f, 1f, 1f);
                Gui.drawScaledCustomSizeModalRect(
                        x + ClientConfig.SPINE_LEFT_BORDER,
                        y + ClientConfig.SPINE_TOP_BORDER,
                        0, 0,
                        spineW, spineH,
                        spineW, spineH,
                        spineW, spineH);
            } else {
                int lx = x + ClientConfig.SPINE_LEFT_BORDER;
                int ty = y + ClientConfig.SPINE_TOP_BORDER;
                int rx = lx + spineW;
                int by = ty + spineH;
                Gui.drawRect(lx, ty, rx, by, 0xFF000000 | elemColor);
            }

            if (ClientConfig.isPanelThemingEnabled()) {
                int shadow = (0x22 << 24) | (ClientConfig.GROOVE_SH & 0x00FFFFFF);
                Gui.drawRect(x + ClientConfig.SPINE_LEFT_BORDER, y + ClientConfig.SPINE_TOP_BORDER + spineH, x + ClientConfig.SPINE_LEFT_BORDER + spineW, y + ClientConfig.SPINE_TOP_BORDER + spineH + 1, shadow);
            }
        }
    }

    private void drawRowGroove(int x, int y, int w, int h) {
        int base = ClientConfig.GROOVE_BASE;
        int hl = ClientConfig.GROOVE_HL;
        int sh = ClientConfig.GROOVE_SH;

        Gui.drawRect(x, y, x + w, y + h, base);
        Gui.drawRect(x, y, x + w, y + 1, hl);
        Gui.drawRect(x, y + h - 1, x + w, y + h, sh);

        int shadowAlpha = 0x30;
        int shadowRgb = TextUtils.darkenColor(sh & 0xFFFFFF, 0.6f);
        int shadowCol = (shadowAlpha << 24) | (shadowRgb & 0x00FFFFFF);
        Gui.drawRect(x, y + h, x + w, y + h + 2, shadowCol);
    }

    public void placePaginationButtons(GuiButton prevButton, GuiButton nextButton, int leftPanelX, int leftPanelY, int leftPanelW, int leftPanelH, int page, boolean anyHasNext) {
        int arrowsY = leftPanelY + leftPanelH - ClientConfig.BOTTOM_BAR_HEIGHT / 2 - ClientConfig.ARROWS_Y_OFFSET;
        int prevX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int nextX = leftPanelX + leftPanelW - ClientConfig.GRID_INNER_PADDING - ClientConfig.NAV_BUTTON_SIZE;

        if (prevButton != null && nextButton != null) {
            prevButton.x = prevX;
            prevButton.y = arrowsY;

            nextButton.x = nextX;
            nextButton.y = arrowsY;

            prevButton.visible = page > 0;
            nextButton.visible = anyHasNext;
        }
    }

    public void computeAndRenderScrollSlot(int mouseX, int mouseY, int leftPanelX, int leftPanelY, int leftPanelW, int leftPanelH) {
        int barY = leftPanelY + leftPanelH - ClientConfig.BOTTOM_BAR_HEIGHT / 2 - ClientConfig.ARROWS_Y_OFFSET;
        int prevX = leftPanelX + ClientConfig.GRID_INNER_PADDING;
        int nextX = leftPanelX + leftPanelW - ClientConfig.GRID_INNER_PADDING - ClientConfig.NAV_BUTTON_SIZE;

        int prevRight = prevX + ClientConfig.NAV_BUTTON_SIZE;
        int gapLeft = prevRight + ClientConfig.SCROLL_SLOT_SIDE_GAP;
        int gapRight = nextX - ClientConfig.SCROLL_SLOT_SIDE_GAP;
        int slotSize = Math.min(Math.min(ClientConfig.NAV_BUTTON_SIZE, gapRight - gapLeft), Math.max(8, ClientConfig.SCROLL_SLOT_MAX_SIZE));

        scrollSlotX = gapLeft + (gapRight - gapLeft - slotSize) / 2;
        scrollSlotY = barY + (ClientConfig.NAV_BUTTON_SIZE - slotSize) / 2;
        scrollSlotW = slotSize;
        scrollSlotH = slotSize;

        scrollSlotEnabled = SpellArchivesConfig.isScrollReserveEnabled();
        int fill = scrollSlotEnabled ? ClientConfig.SCROLL_SLOT_BG : ClientConfig.SCROLL_SLOT_BG_DISABLED;
        int border = scrollSlotEnabled ? ClientConfig.SCROLL_SLOT_BORDER : ClientConfig.SCROLL_SLOT_BORDER_DISABLED;
        GuiUtils.drawRoundedPanel(scrollSlotX, scrollSlotY, scrollSlotW, scrollSlotH, ClientConfig.SCROLL_SLOT_RADIUS, fill, border);

        int count = gui.getTile().getIdentificationScrollCountPublic();
        int maxCount = SpellArchivesConfig.getScrollReserveMax();
        if (count > 0) {
            ItemStack example = new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("ebwizardry", "identification_scroll")));
            if (!example.isEmpty()) {
                RenderHelper.enableGUIStandardItemLighting();
                int iconX = scrollSlotX + 2;
                int iconY = scrollSlotY + (scrollSlotH - 16) / 2;
                mc.getRenderItem().renderItemIntoGUI(example, iconX, iconY);
                RenderHelper.disableStandardItemLighting();

                String ct = TextUtils.formatCompactCount(count);
                int textW = fontRenderer.getStringWidth(ct);
                int textX = scrollSlotX + (scrollSlotW - textW / 2) / 2;
                int textY = scrollSlotY + scrollSlotH + fontRenderer.FONT_HEIGHT / 4;

                GL11.glPushMatrix();
                GL11.glScalef(0.5f, 0.5f, 1f);
                int color = scrollSlotEnabled ? 0xFFFFFF : 0x888888;
                fontRenderer.drawString(ct, textX * 2, textY * 2, color);
                GL11.glPopMatrix();
            }
        }

        if (!scrollSlotEnabled) {
            int overlay = 0x77000000;
            Gui.drawRect(scrollSlotX, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, overlay);
        }

        boolean hover = mouseX >= scrollSlotX && mouseX < scrollSlotX + scrollSlotW && mouseY >= scrollSlotY && mouseY < scrollSlotY + scrollSlotH;
        if (hover) {
            if (scrollSlotEnabled) {
                Gui.drawRect(scrollSlotX, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + 1, ClientConfig.HOVER_BORDER);
                Gui.drawRect(scrollSlotX, scrollSlotY + scrollSlotH - 1, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
                Gui.drawRect(scrollSlotX, scrollSlotY, scrollSlotX + 1, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
                Gui.drawRect(scrollSlotX + scrollSlotW - 1, scrollSlotY, scrollSlotX + scrollSlotW, scrollSlotY + scrollSlotH, ClientConfig.HOVER_BORDER);
            }

            List<String> tip = new ArrayList<>();
            if (!scrollSlotEnabled) {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.disabled.title"));
                tip.add(I18n.format("gui.spellarchives.scroll_slot.disabled.toggle"));
            } else if (count <= 0) {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.empty"));
            } else {
                tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.count", count));
                if (maxCount > 0 && count >= maxCount) tip.add(I18n.format("gui.spellarchives.scroll_slot.tooltip.full"));
            }

            gui.setPendingTooltip(tip, mouseX, mouseY);
        }
    }

    public boolean isMouseInScrollSlot(int mouseX, int mouseY) {
        return mouseX >= scrollSlotX && mouseX < scrollSlotX + scrollSlotW && mouseY >= scrollSlotY && mouseY < scrollSlotY + scrollSlotH;
    }
}
