package com.spellarchives.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import electroblob.wizardry.spell.Spell;

public class SpellPresentation {
    public final ItemStack stack;
    public final Spell spell;
    public final boolean discovered;
    public final String headerName;
    public final ResourceLocation spellIcon;
    public final String description;
    public final String tierName;
    public final String elementName;
    public final ResourceLocation elementIcon;
    public final int count;
    public final int cost;
    public final boolean isContinuous;
    public final int chargeUpTime;
    public final int cooldown;

    public SpellPresentation(ItemStack stack, Spell spell, boolean discovered, String headerName, ResourceLocation spellIcon, String description,
                      String tierName, String elementName, ResourceLocation elementIcon, int count, int cost, boolean isContinuous, int chargeUpTime, int cooldown) {
        this.stack = stack;
        this.spell = spell;
        this.discovered = discovered;
        this.headerName = headerName;
        this.spellIcon = spellIcon;
        this.description = description;
        this.tierName = tierName;
        this.elementName = elementName;
        this.elementIcon = elementIcon;
        this.count = count;
        this.cost = cost;
        this.isContinuous = isContinuous;
        this.chargeUpTime = chargeUpTime;
        this.cooldown = cooldown;
    }
}
