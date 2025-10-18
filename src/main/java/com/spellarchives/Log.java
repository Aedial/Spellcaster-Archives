package com.spellarchives;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public final class Log {
    private static final Logger LOGGER = LogManager.getLogger(SpellArchives.MODID);

    private Log() {}

    public static void info(String msg) {
        LOGGER.info("[SpellArchives] " + msg);
    }

    public static void warn(String msg) {
        LOGGER.warn("[SpellArchives] " + msg);
    }

    public static void debug(String msg) {
        LOGGER.debug("[SpellArchives] " + msg);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.error("[SpellArchives] " + msg, t);
    }

    // --- Colored chat helpers for player feedback ---
    public static void chat(ICommandSender to, String msg, TextFormatting color) {
        if (to == null) return;

        TextComponentString tc = new TextComponentString("[SpellArchives] " + msg);
        tc.setStyle(new Style().setColor(color));
        to.sendMessage(tc);
    }

    public static void chatInfo(ICommandSender to, String msg) { chat(to, msg, TextFormatting.AQUA); }
    public static void chatWarn(ICommandSender to, String msg) { chat(to, msg, TextFormatting.YELLOW); }
    public static void chatError(ICommandSender to, String msg) { chat(to, msg, TextFormatting.RED); }
    public static void chatSuccess(ICommandSender to, String msg) { chat(to, msg, TextFormatting.GREEN); }
}
