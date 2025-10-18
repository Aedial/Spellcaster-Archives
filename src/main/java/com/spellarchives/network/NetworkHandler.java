package com.spellarchives.network;

import com.spellarchives.SpellArchives;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SpellArchives.MODID);

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        int id = 0;
        CHANNEL.registerMessage(MessageExtractBook.Handler.class, MessageExtractBook.class, id++, Side.SERVER);
        initialized = true;
    }
}
