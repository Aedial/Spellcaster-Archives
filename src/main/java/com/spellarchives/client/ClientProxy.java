package com.spellarchives.client;

import com.spellarchives.CommonProxy;

public class ClientProxy extends CommonProxy {
	@Override
	public void preInit() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.spellarchives.registry.ClientModels());
	}
}
