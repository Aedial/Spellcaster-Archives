package com.spellarchives.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.client.config.ConfigGuiType;
import net.minecraftforge.fml.client.config.IConfigElement;


/**
 * A lightweight IConfigElement that appears in the GuiConfig list as a normal entry
 * but doesn't represent an editable property. Clicking its label is handled by the
 * parent config GUI to open the Theme Picker.
 */
@SuppressWarnings("unchecked")
public class ThemePickerConfigElement implements IConfigElement {
    private final String name;
    private final String languageKey;

    public ThemePickerConfigElement(String name, String languageKey) {
        this.name = name;
        this.languageKey = languageKey;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getQualifiedName() {
        return name;
    }

    @Override
    public String getComment() {
        return null;
    }

    @Override
    public String getLanguageKey() {
        return languageKey;
    }

    @Override
    public boolean showInGui() {
        return true;
    }

    @Override
    public boolean isList() {
        return false;
    }

    @Override
    public boolean isListLengthFixed() {
        return true;
    }

    @Override
    public Class getArrayEntryClass() {
        return null;
    }

    @Override
    public Class getConfigEntryClass() {
        return ThemePickerEntry.class;
    }

    @Override
    public boolean isProperty() {
        return true;
    }

    @Override
    public ConfigGuiType getType() {
        return ConfigGuiType.STRING;
    }

    @Override
    public Pattern getValidationPattern() {
        return null;
    }

    @Override
    public void set(Object[] values) {}

    @Override
    public void set(Object value) {}

    @Override
    public Object[] getList() {
        return new Object[0];
    }

    @Override
    public Object get() {
        try {
            return I18n.format(languageKey);
        } catch (Throwable t) {
            return languageKey;
        }
    }

    @Override
    public boolean requiresMcRestart() {
        return false;
    }

    @Override
    public boolean requiresWorldRestart() {
        return false;
    }

    @Override
    public void setToDefault() {
        // No-op
    }

    @Override
    public Object[] getDefaults() {
        return new Object[0];
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public int getMaxListLength() {
        return 0;
    }

    @Override
    public Object getDefault() {
        try {
            return I18n.format(languageKey);
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public Object getMinValue() {
        return null;
    }

    @Override
    public Object getMaxValue() {
        return null;
    }

    @Override
    public String[] getValidValues() {
        return null;
    }

    @Override
    public List<IConfigElement> getChildElements() {
        return new ArrayList<>();
    }
}
