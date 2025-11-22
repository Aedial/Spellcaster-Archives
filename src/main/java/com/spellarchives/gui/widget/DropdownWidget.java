package com.spellarchives.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;
import com.spellarchives.util.TextUtils;

public class DropdownWidget<T> extends BaseWidget {
    private final FontRenderer fontRenderer;
    private String label;
    private List<T> options = new ArrayList<>();
    private T selectedOption;
    private List<T> selectedOptions = new ArrayList<>();
    private Consumer<T> onSelect;
    private Consumer<List<T>> onMultiSelect;
    private boolean multiSelect = false;
    private boolean isOpen = false;
    private Function<T, String> optionProvider = Object::toString;
    private float headerScale = 1.0f;
    private float optionScale = 1.0f;


    final private int headerVerticalPadding = 2;
    final private int optionVerticalPadding = 2;
    final private int indicatorSize = 4;
    final private int optionHorizontalOffset = 12;
    final private int panelRadius = 3;
    final private int textColor = 0xFFFFFF;
    final private int headerFill = 0x66000000;
    final private int headerHoverFill = 0xAA222222;
    final private int panelBorder = 0xFF444444;
    final private int hoverBorder = 0xFFAAAAAA;
    final private int panelFill = 0xCC111111;
    final private int panelHoverFill = 0xAA222222;
    final private int optionFill = 0x22000000;
    final private int optionHoverFill = 0x44FFFFFF;
    final private int indicatorColor = 0xFF2A2A2A;
    final private int indicatorColorMulti = 0xFFFFFFFF;
    final private int indicatorColorRadio = 0x66333333;

    public DropdownWidget(Minecraft mc, FontRenderer fontRenderer, String label) {
        super(mc);

        this.fontRenderer = fontRenderer;
        this.label = label;
    }

    public T getSelected() { return selectedOption; }
    public List<T> getSelectedOptions() { return selectedOptions; }
    public boolean isOpen() { return isOpen; }

    public void setOptions(List<T> options) { this.options = options; }
    public void setOptionProvider(Function<T, String> optionProvider) { this.optionProvider = optionProvider; }
    public void setMultiSelect(boolean multiSelect) { this.multiSelect = multiSelect; }
    public void setSelected(T option) { this.selectedOption = option; }
    public void setSelectedOptions(List<T> options) { this.selectedOptions = new ArrayList<>(options); }

    public void setHeaderScale(float s) { this.headerScale = s; }
    public void setOptionScale(float s) { this.optionScale = s; }
    public void setOpen(boolean open) { this.isOpen = open; }
    public void setLabel(String label) { this.label = label; }

    public void setOnSelect(Consumer<T> onSelect) { this.onSelect = onSelect; }
    public void setOnMultiSelect(Consumer<List<T>> onMultiSelect) { this.onMultiSelect = onMultiSelect; }

    public int getHeaderHeight() {
        return Math.round((fontRenderer.FONT_HEIGHT + headerVerticalPadding * 2) * headerScale);
    }

    public int getOptionHeight() {
        return Math.round((fontRenderer.FONT_HEIGHT + optionVerticalPadding * 2) * optionScale);
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        int headerHeight = getHeaderHeight();
        boolean hoverHeader = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + headerHeight;

        if (hoverHeader) isOpen = true;
        else if (isOpen) {
            int contentHeight = options.size() * getOptionHeight();
            boolean hoverContent = mouseX >= x && mouseX < x + width && mouseY >= y + headerHeight && mouseY < y + headerHeight + contentHeight;
            if (!hoverContent) isOpen = false;
        }

        drawHeader(hoverHeader || isOpen, headerHeight);

        if (isOpen && !options.isEmpty()) drawContent(mouseX, mouseY);
    }

    private void drawHeader(boolean active, int headerHeight) {
        int fill = active ? headerHoverFill : headerFill;
        int border = active ? hoverBorder : panelBorder;
        drawRoundedPanel(x, y, width, headerHeight, panelRadius, fill, border);

        String text = label;

        int hPadding = 3;
        int maxWidthUnscaled = (int) ((width - hPadding * 2) / headerScale);
        String fitted = TextUtils.trimToWidth(fontRenderer, text, maxWidthUnscaled);

        GL11.glPushMatrix();
        if (headerScale != 1.0f) GL11.glScalef(headerScale, headerScale, 1f);
        float textX = (x + hPadding) / headerScale;
        float textY = (y + headerVerticalPadding + 1) / headerScale;
        fontRenderer.drawString(fitted, textX, textY, textColor, false);
        GL11.glPopMatrix();
    }

    private void drawContent(int mouseX, int mouseY) {
        int headerHeight = getHeaderHeight();
        int optionHeight = getOptionHeight();
        int contentHeight = options.size() * optionHeight;
        int contentY = y + headerHeight;

        drawRoundedPanel(x, contentY, width, contentHeight + 2, panelRadius, panelFill, panelBorder);

        for (int i = 0; i < options.size(); i++) {
            T opt = options.get(i);
            int rowY = contentY + i * optionHeight + 1;  // Adjust for outer border
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + optionHeight;

            int fill = hover ? optionHoverFill : optionFill;
            drawRect(x + 1, rowY, x + width - 1, rowY + optionHeight, fill);

            boolean selected = multiSelect ? selectedOptions.contains(opt) : (selectedOption == opt);
            int indicatorX = x + (optionHorizontalOffset - indicatorSize) / 2;
            int indicatorY = rowY + (optionHeight - indicatorSize) / 2 + 1;
            drawIndicator(indicatorX, indicatorY, !multiSelect, selected);

            String optLabel = optionProvider.apply(opt);
            int maxWidthUnscaled = (int) ((width - optionHorizontalOffset) / optionScale);
            String trimmed = TextUtils.trimToWidth(fontRenderer, optLabel, maxWidthUnscaled);

            GL11.glPushMatrix();
            if (optionScale != 1.0f) GL11.glScalef(optionScale, optionScale, 1f);
            fontRenderer.drawString(trimmed, (x + optionHorizontalOffset) / optionScale, (rowY + optionVerticalPadding) / optionScale, textColor, false);
            GL11.glPopMatrix();
        }
    }

    private void drawIndicator(int x, int y, boolean radio, boolean selected) {
        drawRect(x, y, x + indicatorSize, y + indicatorSize, indicatorColor);
        drawRect(x, y, x + indicatorSize, y + 1, panelBorder);
        drawRect(x, y + indicatorSize - 1, x + indicatorSize, y + indicatorSize, panelBorder);
        drawRect(x, y, x + 1, y + indicatorSize, panelBorder);
        drawRect(x + indicatorSize - 1, y, x + indicatorSize, y + indicatorSize, panelBorder);

        if (selected) {
            drawRect(x + 1, y + 1, x + indicatorSize - 1, y + indicatorSize - 1, indicatorColorMulti);
        } else if (radio) {
            drawRect(x + 1, y + 1, x + indicatorSize - 1, y + indicatorSize - 1, indicatorColorRadio);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!visible || !isOpen) return false;

        int optionHeight = getOptionHeight();
        int contentHeight = options.size() * optionHeight;
        int contentY = y + getHeaderHeight();

        if (mouseX >= x && mouseX < x + width && mouseY >= contentY && mouseY < contentY + contentHeight) {
            int relY = mouseY - contentY;
            int index = relY / optionHeight;

            if (index >= 0 && index < options.size()) {
                T opt = options.get(index);
                if (multiSelect) {
                    if (selectedOptions.contains(opt)) selectedOptions.remove(opt);
                    else selectedOptions.add(opt);
                    if (onMultiSelect != null) onMultiSelect.accept(selectedOptions);
                } else {
                    selectedOption = opt;
                    isOpen = false;
                    if (onSelect != null) onSelect.accept(selectedOption);
                }

                return true;
            }
        }

        return false;
    }
}
