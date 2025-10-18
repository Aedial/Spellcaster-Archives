package com.spellarchives;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = SpellArchives.MODID, name = SpellArchives.NAME, version = SpellArchives.VERSION, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class SpellArchives {
    public static final String MODID = "spellarchives";
    public static final String NAME = "Spellcaster's Archives";
    public static final String VERSION = "0.1.0";

    @SidedProxy(clientSide = "com.spellarchives.client.ClientProxy", serverSide = "com.spellarchives.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance
    public static SpellArchives instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        com.spellarchives.network.NetworkHandler.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(SpellArchives.instance, new com.spellarchives.client.GuiHandler());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spellarchives.command.CommandArchives());
    }
}
