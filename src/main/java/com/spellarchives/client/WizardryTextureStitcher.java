package com.spellarchives.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.spellarchives.SpellArchives;
import com.spellarchives.render.DynamicTextureFactory;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;


/**
 * Client-side utility responsible for stitching all Wizardry-derived resources the Spell Archive
 * model depends on into the blocks texture atlas during the {@link TextureStitchEvent.Pre} phase.
 *
 * Responsibilities:
 * - Registers the dark oak Wizardry bookshelf base textures (side/top/inside).
 * - Generates a one-time random assignment of elements and runes for each of the
 *   8 archive corners to give subtle visual variety across game launches.
 * - Selects 3 random spell icons used on the archive core side faces (left, right, back).
 * - Optionally (when the JVM flag {@code -Dspellarchives.dumpInsideTextures=true} is set) dumps
 *   the dynamically generated inside textures (variants 1..14) after stitching for debugging.
 *
 * Lifecyle & Usage:
 * - Called once per resource reload (normally once at client start). The random choices are not
 *   persisted; a full client restart will re-roll the selections.
 * - Populates internal static arrays ({@code cornerElements}, {@code cornerRunes},
 *   {@code coreSideSpellIcons}) that the model retexture step queries later.
 * - Only operates when the event's atlas is the blocks atlas; other atlases are ignored.
 *
 * Error Handling & Guarantees:
 * - If Wizardry provides fewer than 3 spell icons, bookshelf side texture is used as a safe fallback.
 * - All registered ResourceLocations use the form {@code modid:path_without_textures_prefix_and_png} required by
 *   {@link TextureMap#registerSprite(ResourceLocation)}.
 * - Corner rune indices are guaranteed unique per corner (sampled without replacement from 1..4).
 */
@Mod.EventBusSubscriber(modid = SpellArchives.MODID, value = Side.CLIENT)
public final class WizardryTextureStitcher {

    // Wizardry dark oak bookshelf textures we need to register
    private static final ResourceLocation BOOKSHELF_SIDE = new ResourceLocation("ebwizardry", "blocks/dark_oak_bookshelf_side");
    private static final ResourceLocation BOOKSHELF_TOP = new ResourceLocation("ebwizardry", "blocks/dark_oak_bookshelf_top");
    private static final ResourceLocation BOOKSHELF_INSIDE = new ResourceLocation("ebwizardry", "blocks/dark_oak_bookshelf_inside");

    // All available elements for runestones (excluding MAGIC which doesn't have runestones)
    private static final String[] ELEMENTS = {"fire", "ice", "lightning", "necromancy", "earth", "sorcery", "healing"};

    // Randomized corner element assignments and rune selections (computed once on stitch)
    private static String[] cornerElements = new String[8];
    private static int[][] cornerRunes = new int[8][3]; // 3 runes per corner (from 1-4)

    // Randomized spell icons for the 3 core sides (left, right, back)
    private static ResourceLocation[] coreSideSpellIcons = new ResourceLocation[3];

    // Dump flag: enable with JVM property -Dspellarchives.dumpInsideTextures=true
    private static final boolean DUMP_INSIDE_TEXTURES = Boolean.getBoolean("spellarchives.dumpInsideTextures");

    // Scale factor for inside books texture variants (from 16 x 16 to scale*16 x scale*16)
    private static final int INSIDE_BOOKS_TEXTURE_SCALE = 4;
    // Width and height of inside books texture variants (in pixels of base 16x16)
    private static final int INSIDE_BOOKS_WIDTH = 2;
    private static final int INSIDE_BOOKS_HEIGHT = 5;
    // Number of inside books texture variants per row in the source image
    private static final int INSIDE_BOOKS_SPINES_PER_ROW = 7;
    // Offset of the books area within the inside texture (from the bottom left, in pixels of base 16x16)
    private static final int INSIDE_BOOKS_XOFFSET = 1;
    private static final int INSIDE_BOOKS_YOFFSET = 1;
    // Size of the spell icon area within the inside texture (in pixels of the scaled texture)
    private static final int INSIDE_BOOKS_ICON_SIZE = 8;

    private WizardryTextureStitcher() {}

    /**
     * Texture stitch pre-hook: registers bookshelf textures and performs the random selection logic
     * for corner elements/runes and core side spell icons. Populates internal static arrays queried
     * by model baking code.
     *
     * Selection Details:
     * - Elements: 7 distinct Wizardry elements (excluding MAGIC) are shuffled; one element is duplicated
     *             to fill the 8th corner producing mild asymmetry.
     * - Runes: For each corner, 3 distinct rune indices (1..4) are chosen and all 4 rune textures (0 + 3 choices)
     *           registered for that corner's element.
     * - Spell Icons: All available Wizardry spell icons are collected and shuffled; up to 3 are chosen for
     *                left/right/back core faces. Missing icons fall back to the bookshelf side texture.
     *
     * @param event the PRE stitch event for a texture atlas; only processed if it targets the blocks atlas.
     */
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        // Only operate on the main blocks texture atlas
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;
        TextureMap map = event.getMap();

        // Register the Wizardry bookshelf textures so they're available in our model
        map.registerSprite(BOOKSHELF_SIDE);
        map.registerSprite(BOOKSHELF_TOP);
        map.registerSprite(BOOKSHELF_INSIDE);

        // Randomly assign elements to corners and select runes
        Random rand = new Random();
        
        // Shuffle elements for corner assignment (7 elements, 1 will repeat)
        List<String> elementList = new ArrayList<>(Arrays.asList(ELEMENTS));
        Collections.shuffle(elementList, rand);

        // Repeat element for 8th corner
        elementList.add(6, elementList.get(2)); // repeat 3rd element at corner 7

        for (int corner = 0; corner < 8; corner++) {
            String element = elementList.get(corner);
            cornerElements[corner] = element;

            // Select 3 random runes from 1-4 (no duplicates)
            List<Integer> runeIndices = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
            Collections.shuffle(runeIndices, rand);
            cornerRunes[corner][0] = runeIndices.get(0);
            cornerRunes[corner][1] = runeIndices.get(1);
            cornerRunes[corner][2] = runeIndices.get(2);

            // Register runestone_0 for this element (used on faces touching core)
            map.registerSprite(new ResourceLocation("ebwizardry", "blocks/runestone_" + element + "_0"));

            // Register the 3 selected runes for outer faces
            for (int i = 0; i < 3; i++) {
                map.registerSprite(new ResourceLocation("ebwizardry", "blocks/runestone_" + element + "_" + cornerRunes[corner][i]));
            }
        }


        // Fallback: if we don't have enough spell icons, use the bookshelf texture
        for (int i = 0; i < 3; i++) coreSideSpellIcons[i] = BOOKSHELF_SIDE;

        // Collect all spell icons and choose 3 randomly for the core sides
        List<ResourceLocation> allSpellIcons = new ArrayList<>();
        for (Spell spell : Spell.getAllSpells()) {
            if (spell != null) {
                ResourceLocation icon = spell.getIcon();
                if (icon != null) {
                    allSpellIcons.add(icon);
                }
            }
        }

        // Select 3 random spell icons (or fewer if not enough spells exist)
        Collections.shuffle(allSpellIcons, rand);
        for (int i = 0; i < 3 && i < allSpellIcons.size(); i++) {
            coreSideSpellIcons[i] = allSpellIcons.get(i);
            
            // Convert spell icon path for registration: 
            // Icon is "modid:textures/spells/name.png", registerSprite needs "modid:spells/name"
            String path = coreSideSpellIcons[i].getPath();
            if (path.startsWith("textures/")) path = path.substring("textures/".length());
            if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);

            ResourceLocation spriteLocation = new ResourceLocation(coreSideSpellIcons[i].getNamespace(), path);
            
            // Register the icon as a sprite in the block atlas
            map.registerSprite(spriteLocation);
        }
    }

    /**
     * Texture stitch POST hook: when the JVM flag {@code spellarchives.dumpInsideTextures} is true, recreates the
     * dynamically generated inside textures (variants 1..14) dumps them into {@code ./spellarchives_dump/textures/dynamic/}.
     * This is intended as a fallback for texture generation, as dynamic textures have failed to work, so far.
     *
     * Notes:
     * - Reflection is used to obtain the Minecraft game directory; if that fails, {@code user.dir} is used.
     * - Variant 0 is the unchanged base texture so it is skipped.
     * - Transparent pixels in generated spines are preserved; only non-transparent pixels overwrite output.
     *
     * @param event the POST stitch event; ignored if not for blocks atlas or dump flag disabled.
     */
    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (!DUMP_INSIDE_TEXTURES) return; // only active when flag enabled
        if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) {
            SpellArchives.LOGGER.warn("Texture stitch post event on non-block atlas; skipping inside texture dump");
            return;
        }

        try {
            final int outW = 16 * INSIDE_BOOKS_TEXTURE_SCALE;
            final int outH = 16 * INSIDE_BOOKS_TEXTURE_SCALE;
            final int rowHeight = outH / 2;
            final int spinesPerRow = INSIDE_BOOKS_SPINES_PER_ROW;
            final int spineW = INSIDE_BOOKS_WIDTH * INSIDE_BOOKS_TEXTURE_SCALE;
            final int spineH = INSIDE_BOOKS_HEIGHT * INSIDE_BOOKS_TEXTURE_SCALE;
            final int offsetX = INSIDE_BOOKS_XOFFSET * INSIDE_BOOKS_TEXTURE_SCALE;
            final int offsetY = rowHeight - spineH - INSIDE_BOOKS_YOFFSET * INSIDE_BOOKS_TEXTURE_SCALE;
            final Element[] order = new Element[]{
                Element.MAGIC, Element.FIRE, Element.ICE, Element.LIGHTNING, Element.NECROMANCY, Element.EARTH, Element.SORCERY
            };

            // Load base inside texture
            BufferedImage baseInside = loadTextureImage(new ResourceLocation("ebwizardry", "textures/blocks/dark_oak_bookshelf_inside.png"));
            if (baseInside == null) {
                SpellArchives.LOGGER.warn("Inside dump: could not load base inside texture");
                return;
            }
            BufferedImage scaledBase = scaleTo(baseInside, outW, outH);

            // Prepare output directory
            File gameDir;
            try {
                gameDir = (File) Minecraft.class.getMethod("getMinecraftDir").invoke(null);
            } catch (Throwable reflection) {
                // Fallback: user.dir
                gameDir = new File(System.getProperty("user.dir", "."));
            }
            File root = new File(gameDir, "spellarchives_dump/textures/dynamic");
            if (!root.exists()) root.mkdirs();

            // Generate and dump inside textures 1..14
            for (int variant = 1; variant <= 14; variant++) { // skip 0 (base inside texture)
                BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
                out.getGraphics().drawImage(scaledBase, 0, 0, null);

                for (int i = 0; i < variant; i++) {
                    int row = (i < spinesPerRow) ? 1 : 0;
                    int col = i % spinesPerRow;
                    Element elem = order[col];
                    int baseRgb = elementBaseColor(elem);

                    // Generate spine texture for this element
                    ResourceLocation icon = elem.getIcon();
                    ResourceLocation spineRl = DynamicTextureFactory.getOrCreateSpineTexture(baseRgb, spineW, spineH, icon, INSIDE_BOOKS_ICON_SIZE);
                    ITextureObject texObj = Minecraft.getMinecraft().getTextureManager().getTexture(spineRl);

                    int[] spinePixels = null;
                    if (texObj instanceof DynamicTexture) spinePixels = ((DynamicTexture)texObj).getTextureData();
                    if (spinePixels == null) {
                        SpellArchives.LOGGER.warn("Inside dump: could not get spine texture data for " + spineRl);
                        continue;
                    }

                    // Apply spine pixels to output image for this spine
                    int x = offsetX + col * spineW;
                    int yBase = row == 1 ? rowHeight : 0;
                    int y = yBase + offsetY;

                    for (int sy = 0; sy < spineH; sy++) {
                        for (int sx = 0; sx < spineW; sx++) {
                            int argb = spinePixels[sy * spineW + sx];
                            if (((argb >>> 24) & 0xFF) == 0) continue;
                            out.setRGB(x + sx, y + sy, argb);
                        }
                    }
                }

                File outFile = new File(root, "spell_archive_inside_" + variant + ".png");
                javax.imageio.ImageIO.write(out, "PNG", outFile);
            }

            SpellArchives.LOGGER.info("Dumped inside textures 1..14 to " + root.getAbsolutePath());
        } catch (Throwable t) {
            SpellArchives.LOGGER.error("Failed dumping inside textures", t);
        }
    }

    /**
     * Provides the randomly assigned element name for a corner index (0-7). Values are determined once per resource
     * reload during {@link #onTextureStitch(TextureStitchEvent.Pre)}. One element is duplicated to fill all 8 corners.
     *
     * @param corner corner index in [0,7]; each corresponds to a unique spatial corner of the archive model.
     * @return lowercase element identifier (e.g. "fire", "ice").
     * @throws IllegalArgumentException if {@code corner} is out of range.
     */
    public static String getCornerElement(int corner) {
        if (corner < 0 || corner >= 8) throw new IllegalArgumentException("Corner must be 0-7");
        return cornerElements[corner];
    }

    /**
     * Returns the three distinct rune indices (each 1..4) chosen for a corner. Order matches the registration sequence
     * and is used for selecting the appropriate sprite in retexturing.
     *
     * @param corner corner index in [0,7].
     * @return array of length 3 containing rune indices (values 1..4).
     * @throws IllegalArgumentException if {@code corner} is out of range.
     */
    public static int[] getCornerRunes(int corner) {
        if (corner < 0 || corner >= 8) throw new IllegalArgumentException("Corner must be 0-7");
        return cornerRunes[corner];
    }

    /**
     * Provides the selected spell icon for a core side: indices 0=left, 1=right, 2=back. Icons may fall back to the
     * bookshelf texture if Wizardry exposes fewer than three spells.
     *
     * @param side core side index (0 left, 1 right, 2 back).
     * @return resource location of the icon sprite registered in the block atlas.
     * @throws IllegalArgumentException if {@code side} is outside [0,2].
     */
    public static ResourceLocation getCoreSideSpellIcon(int side) {
        if (side < 0 || side >= 3) throw new IllegalArgumentException("Core side must be 0 (left), 1 (right), or 2 (back)");
        return coreSideSpellIcons[side];
    }

    /**
     * Maps Wizardry {@link Element} values to a packed RGB base color used in dynamic spine generation. The chosen
     * color influences tinting before icons are composited.
     *
     * @param e target element (must not be null).
     * @return packed 24-bit RGB (alpha omitted) providing a neutral base for that element.
     */
    private static int elementBaseColor(Element e) {
        switch (e) {
            case MAGIC: return 0x6E6E6E;
            case FIRE: return 0x7A1E1E;
            case ICE: return 0x1E6C7A;
            case LIGHTNING: return 0x1E4B7A;
            case NECROMANCY: return 0x4B1E7A;
            case EARTH: return 0x2E5B2E;
            case SORCERY: return 0x2E7A5B;
            case HEALING: return 0x8A7A2E; // unused in current 7-per-row layout
            default: return 0x555555;
        }
    }

    /**
     * Produces a nearest-neighbor scaled copy of {@code src}. Chosen to preserve pixel art crispness when enlarging
     * 16x16 textures to higher resolutions for debug dumping.
     *
     * @param src source image (unmodified).
     * @param w destination width in pixels.
     * @param h destination height in pixels.
     * @return newly allocated scaled image of size {@code w}x{@code h}.
     */
    private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        return dst;
    }

    /**
     * Attempts to load a texture image using Minecraft's resource manager. Only PNG decoding errors or missing
     * resources cause a {@code null} return; other IO exceptions are swallowed as a silent failure since callers
     * treat {@code null} as a non-fatal diagnostic case.
     *
     * @param rl resource location pointing to a PNG texture.
     * @return decoded {@link BufferedImage} or {@code null} if unavailable or unreadable.
     */
    private static BufferedImage loadTextureImage(ResourceLocation rl) {
        try {
            IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
            try (IResource res = rm.getResource(rl)) {
                return ImageIO.read(res.getInputStream());
            }
        } catch (IOException e) {
            return null;
        }
    }
}
