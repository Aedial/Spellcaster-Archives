package com.spellarchives.util;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.spellarchives.SpellArchives;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;



public final class Log {
    private final Logger logger = LogManager.getLogger(SpellArchives.MODID);

    public Log() {}

    public void info(String msg) {
        logger.info("[SpellArchives] " + msg);
    }

    public void warn(String msg) {
        logger.warn("[SpellArchives] " + msg);
    }

    public void debug(String msg) {
        logger.debug("[SpellArchives] " + msg);
    }

    public void error(String msg, Throwable t) {
        logger.error("[SpellArchives] " + msg, t);
    }

    // --- Colored chat helpers for player feedback ---
    public void chat(ICommandSender to, String msg, TextFormatting color) {
        if (to == null) return;

        TextComponentString tc = new TextComponentString("[SpellArchives] " + msg);
        tc.setStyle(new Style().setColor(color));
        to.sendMessage(tc);
    }

    /**
     * Sends a localized translation component to the given ICommandSender.
     * key is a translation key in the mod's lang files; args are format parameters.
     */
    public void chatTrans(ICommandSender to, String key, TextFormatting color, Object... args) {
        if (to == null) return;

        TextComponentTranslation tc = new TextComponentTranslation(key, args);

        TextComponentString prefix = new TextComponentString("[SpellArchives] ");
        prefix.setStyle(new Style().setColor(color));
        tc.setStyle(new Style().setColor(color));
        prefix.appendSibling(tc);
        to.sendMessage(prefix);
    }

    public void chatInfo(ICommandSender to, String msg) { chat(to, msg, TextFormatting.AQUA); }
    public void chatWarn(ICommandSender to, String msg) { chat(to, msg, TextFormatting.YELLOW); }
    public void chatError(ICommandSender to, String msg) { chat(to, msg, TextFormatting.RED); }
    public void chatSuccess(ICommandSender to, String msg) { chat(to, msg, TextFormatting.GREEN); }

    // Translated variants
    public void chatInfoTrans(ICommandSender to, String key, Object... args) { chatTrans(to, key, TextFormatting.AQUA, args); }
    public void chatWarnTrans(ICommandSender to, String key, Object... args) { chatTrans(to, key, TextFormatting.YELLOW, args); }
    public void chatErrorTrans(ICommandSender to, String key, Object... args) { chatTrans(to, key, TextFormatting.RED, args); }
    public void chatSuccessTrans(ICommandSender to, String key, Object... args) { chatTrans(to, key, TextFormatting.GREEN, args); }
}
