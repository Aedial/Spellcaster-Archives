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
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.spellarchives.SpellArchives;
import com.spellarchives.config.ClientConfig;
import com.spellarchives.config.SpellArchivesConfig;


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
        List<IConfigElement> out = new ArrayList<>();

        // Ensure both configs are initialized
        if (SpellArchivesConfig.getConfiguration() == null) SpellArchivesConfig.init();
        Configuration guiCfg = ClientConfig.getConfiguration();
        Configuration gameplayCfg = SpellArchivesConfig.getConfiguration();

        // Top-level category: Gameplay (server/global). Wrap to ensure proper localized title.
        if (gameplayCfg != null && gameplayCfg.hasCategory("gameplay")) {
            List<IConfigElement> gpChildren = new ConfigElement(gameplayCfg.getCategory("gameplay")).getChildElements();
            out.add(new DummyCategoryElement("gameplay", "config.spellarchives.category.gameplay", gpChildren));
        }

        // Top-level category: GUI (client styling)
        if (guiCfg != null && guiCfg.hasCategory("gui")) {
            List<IConfigElement> guiChildren = new ConfigElement(guiCfg.getCategory("gui")).getChildElements();
            // Insert theme picker at the top of the GUI category
            guiChildren.add(0, new ThemePickerConfigElement("theme_picker", I18n.format("config.spellarchives.theme_picker")));

            out.add(new DummyCategoryElement("gui", "config.spellarchives.category.gui", guiChildren));
        }

        return out;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        // Ensure both configurations are saved on Done
        Configuration guiCfg = ClientConfig.getConfiguration();
        if (guiCfg != null && guiCfg.hasChanged()) guiCfg.save();

        Configuration gameplayCfg = SpellArchivesConfig.getConfiguration();
        if (gameplayCfg != null && gameplayCfg.hasChanged()) gameplayCfg.save();
    }
}
