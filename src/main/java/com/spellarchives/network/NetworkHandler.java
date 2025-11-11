package com.spellarchives.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.spellarchives.SpellArchives;


/**
 * Central registration point for SimpleNetworkWrapper messages.
 */
public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SpellArchives.MODID);

    private static boolean initialized = false;

    /**
     * Registers all network messages. Safe to call multiple times; guarded by an
     * initialization flag.
     */
    public static void init() {
        if (initialized) return;

        int id = 0;
        CHANNEL.registerMessage(MessageExtractBook.Handler.class, MessageExtractBook.class, id++, Side.SERVER);
        initialized = true;
    }
}
