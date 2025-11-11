package com.spellarchives;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import com.spellarchives.client.GuiHandler;
import com.spellarchives.command.CommandArchives;
import com.spellarchives.network.NetworkHandler;
import com.spellarchives.util.Log;


/**
 * Primary mod class for Spellcaster's Archives. Wires up sided proxies, GUI handler,
 * and network channel registration across Forge lifecycle events.
 */
@Mod(modid = SpellArchives.MODID, name = SpellArchives.NAME, version = SpellArchives.VERSION, acceptedMinecraftVersions = "[1.12,1.12.2]", guiFactory = "com.spellarchives.client.ClientConfigGuiFactory")
public class SpellArchives {
    public static final String MODID = "spellarchives";
    public static final String NAME = "Spellcaster's Archives";
    public static final String VERSION = "0.2.0";
    public static final Log LOGGER = new Log();

    @SidedProxy(clientSide = "com.spellarchives.client.ClientProxy", serverSide = "com.spellarchives.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance
    public static SpellArchives instance;

    /**
     * Forge pre-initialization: delegate to the sided proxy for registrations requiring
     * this phase.
     *
     * @param event The pre-initialization event.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    /**
     * Forge initialization: sets up proxies, network wrapper, and GUI handler routing.
     *
     * @param event The initialization event.
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        NetworkHandler.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(SpellArchives.instance, new GuiHandler());
    }

    /**
     * Forge post-initialization: finalizes client/server-specific setup.
     *
     * @param event The post-initialization event.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }

    /**
     * Registers server-side commands on startup.
     *
     * @param event The server starting event.
     */
    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandArchives());
    }
}
