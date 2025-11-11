package com.spellarchives.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.GuiConfigEntries;
import net.minecraftforge.fml.client.config.IConfigElement;


/**
 * Custom config list entry that renders a single non-editable button-like row
 * which opens the Theme Picker when clicked.
 */
public class ThemePickerEntry implements GuiConfigEntries.IConfigEntry {
    private final GuiConfig owningScreen;
    private final GuiConfigEntries owningList;
    private final IConfigElement element;

    // button geometry relative to the entry area
    private int btnX, btnY, btnW, btnH;

    public ThemePickerEntry(GuiConfig owningScreen, GuiConfigEntries owningList, IConfigElement element) {
        this.owningScreen = owningScreen;
        this.owningList = owningList;
        this.element = element;
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
        String label = I18n.format(element.getLanguageKey());

        // Use the owning list's computed columns so our entry lines up with other controls
        int labelX = owningList.labelX;
        int controlX = owningList.controlX;
        int controlW = owningList.controlWidth;

        // draw label at the standard label X
        owningScreen.mc.fontRenderer.drawString(label, labelX, y + (slotHeight - 8) / 2, 0xFFFFFF);

        // Create a local GuiButtonExt and draw it in the control area so styling matches Forge's native buttons
        GuiButtonExt btn = new GuiButtonExt(0, controlX, y, controlW, slotHeight, I18n.format("config.spellarchives.theme_picker"));
        // Draw using the global Minecraft instance
        btn.drawButton(Minecraft.getMinecraft(), mouseX, mouseY, partialTicks);

        // Record for hit testing
        btnX = controlX;
        btnY = y;
        btnW = controlW;
        btnH = slotHeight;
    }

    @Override
    public boolean mousePressed(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        // x,y are the absolute mouse coordinates as passed by the list; test against our button rect
        if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
            Minecraft.getMinecraft().displayGuiScreen(new ThemePickerGui(owningScreen));
            return true;
        }

        return false;
    }

    @Override
    public void mouseReleased(int index, int x, int y, int mouseEvent, int relativeX, int relativeY) {}

    // IConfigEntry methods
    @Override
    public IConfigElement getConfigElement() { return element; }

    @Override
    public String getName() { return element.getName(); }

    @Override
    public Object getCurrentValue() { return element.get(); }

    @Override
    public boolean isDefault() { return element.isDefault(); }

    @Override
    public Object[] getCurrentValues() {
        return new Object[] { getCurrentValue() };
    }

    @Override
    public boolean enabled() { return true; }

    @Override
    public void keyTyped(char eventChar, int eventKey) {}

    @Override
    public void setToDefault() {}

    @Override
    public void undoChanges() {}

    @Override
    public boolean isChanged() { return false; }

    @Override
    public boolean saveConfigElement() { return false; }

    @Override
    public void drawToolTip(int mouseX, int mouseY) {}

    @Override
    public void updateCursorCounter() {}

    @Override
    public void mouseClicked(int x, int y, int mouseEvent) {
        // If the mouse clicked inside our button rect, open the Theme Picker
        if (x >= btnX && x <= btnX + btnW && y >= btnY && y <= btnY + btnH) {
            Minecraft.getMinecraft().displayGuiScreen(new ThemePickerGui(owningScreen));
        }
    }

    @Override
    public int getEntryRightBound() {
        // Provide a reasonable right bound so the list can layout controls.
        return this.owningScreen.width - 10;
    }

    @Override
    public int getLabelWidth() {
        String label = I18n.format(element.getLanguageKey());
        return owningScreen.mc.fontRenderer.getStringWidth(label);
    }

    @Override
    public void onGuiClosed() {}

    @Override
    public void updatePosition(int slotIndex, int insideLeft, int y, float partialTicks) {}
}
