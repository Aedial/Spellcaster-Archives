package com.spellarchives.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.google.common.collect.ImmutableMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.spellarchives.SpellArchives;


/**
 * Retextures the spell archive model at bake time with randomized runestones and spell icons
 * per-variant. Ensures baked models are registered for all books/facing combinations.
 */
@Mod.EventBusSubscriber(modid = SpellArchives.MODID, value = Side.CLIENT)
public final class SpellArchiveModelRetexture {

    private SpellArchiveModelRetexture() {}

    /**
     * Convert a ResourceLocation to the format expected by model textures.
     * They are stored as "modid:textures/<name>.png" but models need "modid:<name>".
     */
    private static String convertResourcePath(ResourceLocation icon) {
        String path = icon.getPath();
        // Remove "textures/" prefix if present
        if (path.startsWith("textures/")) path = path.substring("textures/".length());

        // Remove .png extension if present
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);

        return icon.getNamespace() + ":" + path;
    }

    /**
     * Intercepts model baking to provide retextured models for all variants and inventory.
     *
     * @param event The model bake event.
     */
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        // Retexture all world variants (to inject spell icons and corner runestones) plus inventory
        List<String> varList = new ArrayList<>();
        for (int books = 0; books <= 14; books++) {
            for (String facing : new String[]{"north", "south", "west", "east"}) {
                varList.add("books=" + books + ",facing=" + facing);
            }
        }

        ResourceLocation baseLoc = new ResourceLocation(SpellArchives.MODID, "spell_archive");

        varList.add("inventory");
        String[] variants = varList.toArray(new String[0]);
        for (String variant : variants) {
            // Determine books value to select proper model path so blockstates resolve directly
            String basePath = "spell_archive";
            if (variant.startsWith("books=")) {
                int comma = variant.indexOf(',');
                String booksPart = (comma >= 0 ? variant.substring(0, comma) : variant);
                String num = booksPart.substring("books=".length());

                try {
                    int b = Integer.parseInt(num);
                    if (b > 0) basePath = "spell_archive_" + b; // books=0 uses original base
                } catch (NumberFormatException e) {
                    SpellArchives.LOGGER.error("Invalid books value in variant: " + variant, e);
                }
            }

            ModelResourceLocation modelLocation = new ModelResourceLocation(new ResourceLocation(SpellArchives.MODID, basePath), variant);

            IBakedModel existingModel = event.getModelRegistry().getObject(modelLocation);
            if (existingModel == null) {
                // Some variants (books>0) share same underlying model; proceed with base model load
                existingModel = event.getModelRegistry().getObject(new ModelResourceLocation(baseLoc, "inventory"));
                if (existingModel == null) {
                    SpellArchives.LOGGER.warn("Model not found for base spell archive: " + modelLocation);
                    continue;
                }
            }

            try {
                // Build texture map with randomized corner textures and spell icons
                Map<String, String> textures = new HashMap<>();

                // Keep base textures
                textures.put("particle", "ebwizardry:blocks/dark_oak_bookshelf_top");
                textures.put("side", "ebwizardry:blocks/dark_oak_bookshelf_side");
                textures.put("top", "ebwizardry:blocks/dark_oak_bookshelf_top");

                // Add randomized spell icons for core sides
                ResourceLocation leftIcon = WizardryTextureStitcher.getCoreSideSpellIcon(0);
                ResourceLocation rightIcon = WizardryTextureStitcher.getCoreSideSpellIcon(1);
                ResourceLocation backIcon = WizardryTextureStitcher.getCoreSideSpellIcon(2);

                // Convert spell icon paths: remove .png extension and adjust path
                String leftPath = convertResourcePath(leftIcon);
                String rightPath = convertResourcePath(rightIcon);
                String backPath = convertResourcePath(backIcon);

                textures.put("spell_left", leftPath);
                textures.put("spell_right", rightPath);
                textures.put("spell_back", backPath);

                // Randomized corner textures
                for (int corner = 0; corner < 8; corner++) {
                    String element = WizardryTextureStitcher.getCornerElement(corner);
                    int[] runes = WizardryTextureStitcher.getCornerRunes(corner);
                    textures.put("c" + corner + "_0", "ebwizardry:blocks/runestone_" + element + "_0");
                    textures.put("c" + corner + "_1", "ebwizardry:blocks/runestone_" + element + "_" + runes[0]);
                    textures.put("c" + corner + "_2", "ebwizardry:blocks/runestone_" + element + "_" + runes[1]);
                    textures.put("c" + corner + "_3", "ebwizardry:blocks/runestone_" + element + "_" + runes[2]);
                }

                // Load the specific base model file; books=0 keeps original spell_archive
                ResourceLocation modelRes = new ResourceLocation(SpellArchives.MODID, "block/" + basePath);
                IModel model = ModelLoaderRegistry.getModel(modelRes);
                IModel retextured = model.retexture(ImmutableMap.copyOf(textures));

                // Determine Y rotation from variant (blockstates apply this normally; we replicate here)
                int rotY = 0;
                String facingPart = null;
                if (variant.contains("facing=")) {
                    int idx = variant.indexOf("facing=");
                    facingPart = variant.substring(idx + 7);
                    int comma = facingPart.indexOf(',');
                    if (comma >= 0) facingPart = facingPart.substring(0, comma);
                }
                if (facingPart != null) {
                    String dir = facingPart;
                    if ("south".equals(dir)) rotY = 0;
                    else if ("west".equals(dir)) rotY = 90;
                    else if ("north".equals(dir)) rotY = 180;
                    else if ("east".equals(dir)) rotY = 270;
                    else SpellArchives.LOGGER.warn("Invalid facing direction in variant: " + variant);
                }

                // Bake and register with rotation so faces line up with blockstate
                // Ensure inside sprite resolves even if resource scanning complains; prefer atlas sprite directly
                final TextureMap blockAtlas = Minecraft.getMinecraft().getTextureMapBlocks();
                Function<ResourceLocation, TextureAtlasSprite> getter = loc -> blockAtlas.getAtlasSprite(loc.toString());

                IBakedModel bakedModel = retextured.bake(
                    ModelRotation.getModelRotation(0, rotY),
                    DefaultVertexFormats.BLOCK,
                    getter
                );

                // Register under the actual per-books path so blockstate resolves without reload
                event.getModelRegistry().putObject(modelLocation, bakedModel);
                // Also register under the canonical 'spell_archive' path for safety (books property included in variant)
                ModelResourceLocation canonicalLoc = new ModelResourceLocation(baseLoc, variant);
                event.getModelRegistry().putObject(canonicalLoc, bakedModel);

            } catch (Exception e) {
                SpellArchives.LOGGER.error("Failed to retexture spell archive model for " + variant, e);
            }
        }
    }
}
