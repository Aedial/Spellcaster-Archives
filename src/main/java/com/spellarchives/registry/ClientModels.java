package com.spellarchives.registry;

import com.spellarchives.SpellArchives;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = SpellArchives.MODID)
public class ClientModels {
    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        Item item = Item.getItemFromBlock(ModBlocks.SPELL_ARCHIVE);
        if (item != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(SpellArchives.MODID + ":spell_archive", "inventory"));
        }
    }
}
