package com.spellarchives.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.IModGuiFactory.RuntimeOptionCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.spellarchives.SpellArchives;
import com.spellarchives.gui.GuiStyle;


/**
 * Minimal GUI factory so the Mods -> Config button opens the standard Forge config GUI
 */
public class ClientConfigGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new ClientConfigGui(parent);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return new HashSet<>();
    }

    @Override
    public boolean hasConfigGui() { return true; }
}

class ClientConfigGui extends GuiConfig {
    private static final int BTN_THEME_PICKER_ID = 9000;

    public ClientConfigGui(GuiScreen parent) {
        super(parent,
              buildElements(),
              SpellArchives.MODID,
              false,
              false,
              I18n.format("gui.spellarchives.config_title"));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        Configuration cfg = ClientConfig.getConfiguration();
    }

    private static List<IConfigElement> buildElements() {
        Configuration cfg = ClientConfig.getConfiguration();
        if (cfg == null) return new ArrayList<>();

        List<IConfigElement> elements = new ConfigElement(cfg.getCategory("gui")).getChildElements();
        elements.add(0, new ThemePickerConfigElement("theme_picker", I18n.format("config.spellarchives.theme_picker")));

        return elements;
    }
}
