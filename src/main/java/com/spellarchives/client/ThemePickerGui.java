package com.spellarchives.client;

import java.io.IOException;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.spellarchives.gui.GuiStyle;


public class ThemePickerGui extends GuiScreen {
    private final GuiScreen parent;
    private static final String[] THEMES = new String[] {"bookshelf", "parchment", "dark"};

    public ThemePickerGui(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;

        // Left panel theme buttons (compact stacked) — headings are drawn in drawScreen (not buttons)
        int leftX = centerX - 160;
        int rightX = centerX + 40;
        int y = 60; // add extra spacing from title

        for (int i = 0; i < THEMES.length; i++) {
            final int id = 100 + i;
            String label = I18n.format("config.spellarchives.theme." + THEMES[i]);
            // Open theme details for left panel
            this.buttonList.add(new GuiButton(id, leftX, y + (i * 22), 120, 18, label));
        }

        // Right panel theme buttons
        for (int i = 0; i < THEMES.length; i++) {
            final int id = 200 + i;
            String label = I18n.format("config.spellarchives.theme." + THEMES[i]);
            // Open theme details for right panel
            this.buttonList.add(new GuiButton(id, rightX, y + (i * 22), 120, 18, label));
        }

        // Done
        this.buttonList.add(new GuiButton(900, this.width / 2 - 50, this.height - 30, 100, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        Configuration cfg = ClientConfig.getConfiguration();
        if (cfg == null) return;

        // Instead of directly applying, open a details submenu for the chosen theme
        if (button.id >= 100 && button.id < 200) {
            String theme = THEMES[button.id - 100];
            this.mc.displayGuiScreen(new ThemeDetailsGui(this, theme, true));
        } else if (button.id >= 200 && button.id < 300) {
            String theme = THEMES[button.id - 200];
            this.mc.displayGuiScreen(new ThemeDetailsGui(this, theme, false));
        } else if (button.id == 900) {
            this.mc.displayGuiScreen(parent);
        }
    }

    private void applyThemeInMemory(String theme, boolean left) {
        if (theme == null) {
            // reload explicit colors (do not write presets)
            GuiStyle.reloadFromConfig();
            return;
        }

        Map<String, Integer> m = GuiStyle.computeThemePresetColors(theme, left);
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            // write into configuration in-memory so the GUI reflects it immediately
            String key = e.getKey();
            int val = e.getValue();
            Property prop = ClientConfig.getConfiguration().getCategory("gui").get(key);
            if (prop != null && prop.getType() == Property.Type.STRING) {
                String hex = String.format("0x%08X", GuiStyle.clampColor(val));
                ClientConfig.getConfiguration().get("gui", key, hex).set(hex);
            } else {
                ClientConfig.getConfiguration().get("gui", key, val).set(val);
            }
        }

        Map<String, Float> pm = GuiStyle.computePanelThemeParams(theme);
        for (Map.Entry<String, Float> p : pm.entrySet()) {
            ClientConfig.getConfiguration().get("gui", p.getKey(), p.getValue()).set(p.getValue());
        }

        GuiStyle.reloadFromConfig();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRenderer, I18n.format("config.spellarchives.theme_picker"), this.width / 2, 12, 0xFFFFFF);

        // Draw static headings for the left/right columns
        int centerX = this.width / 2;
        int leftX = centerX - 160;
        int rightX = centerX + 40;
        drawString(this.fontRenderer, I18n.format("config.spellarchives.left_panel_theme"), leftX, 44, 0xFFFFFF);
        drawString(this.fontRenderer, I18n.format("config.spellarchives.right_panel_theme"), rightX, 44, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
