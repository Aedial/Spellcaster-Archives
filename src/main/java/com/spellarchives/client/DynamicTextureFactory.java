package com.spellarchives.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import com.spellarchives.config.ClientConfig;
import com.spellarchives.util.TextUtils;


/**
 * Centralized factory for dynamic textures used by the GUI. Caches textures and invalidates
 * them automatically when relevant configuration changes (tracked via ClientConfig.CONFIG_REVISION).
 */
public final class DynamicTextureFactory {
    private DynamicTextureFactory() {}

    private static final Map<String, ResourceLocation> spineCache = new LinkedHashMap<>();
    private static final Map<String, ResourceLocation> bgCache = new LinkedHashMap<>();
    private static int cacheRevision = -1;

    private static void checkRevision() {
        if (cacheRevision != ClientConfig.CONFIG_REVISION) {
            // Attempt to delete previously registered dynamic textures from the texture manager
            TextureManager tm = Minecraft.getMinecraft().getTextureManager();
            for (ResourceLocation rl : spineCache.values()) tm.deleteTexture(rl);
            for (ResourceLocation rl : bgCache.values()) tm.deleteTexture(rl);

            spineCache.clear();
            bgCache.clear();
            cacheRevision = ClientConfig.CONFIG_REVISION;
        }
    }

    /**
     * Generates (or retrieves from cache) a dynamic spine texture used for spell books, applying
     * curvature, shading, optional bands, noise, and optional embedded element icon.
     *
     * @param baseRgb Base color for the spine (RGB only).
     * @param w Width in pixels.
     * @param h Height in pixels.
     * @param iconRl Optional icon to embed.
     * @param iconSize Icon pixel size.
     * @return Resource location of the generated or cached texture.
     */
    public static ResourceLocation getOrCreateSpineTexture(int baseRgb, int w, int h, ResourceLocation iconRl, int iconSize) {
        checkRevision();
        String key = baseRgb + "_" + w + "x" + h + (iconRl != null && ClientConfig.SPINE_EMBED_ICON ? ("|icon=" + iconRl.toString() + "|s=" + iconSize) : "");
        ResourceLocation existing = spineCache.get(key);
        if (existing != null) return existing;
        // Full generator: curvature, vertical shading, deterministic noise, optional bands and icon embedding
        int[] pixels = new int[w * h];

        // Create a deterministic seed from (color, w, h) for asymmetric details
        int seed = 0x9E3779B9;
        seed ^= baseRgb * 0x45d9f3b;
        seed ^= (w << 16) ^ (h << 1);
        seed = (seed ^ (seed >>> 16)) * 0x7feb352d;
        seed = (seed ^ (seed >>> 15)) * 0x846ca68b;
        seed = seed ^ (seed >>> 16);

        // Pseudo-random helpers
        IntUnaryOperator next = s -> {
            int z = s + 0x6D2B79F5;
            z = (z ^ (z >>> 15)) * 0x2C1B3C6D;
            z = (z ^ (z >>> 12)) * 0x297A2D39;

            return z ^ (z >>> 15);
        };
        IntFunction<Float> rf = s -> ((s & 0x7FFFFFFF) / (float)0x7FFFFFFF);

        int s1 = next.applyAsInt(seed);
        int s2 = next.applyAsInt(s1);
        int s3 = next.applyAsInt(s2);
        int s4 = next.applyAsInt(s3);
        int s5 = next.applyAsInt(s4);

        // Off-center bias for spine curvature
        float centerBias = 0.5f + (rf.apply(s1) - 0.5f) * 0.18f; // 0.32..0.68
        centerBias = Math.max(0.3f, Math.min(0.7f, centerBias));
        float asymTilt = ClientConfig.SPINE_ENABLE_TILT ? (rf.apply(s2) - 0.5f) * 0.12f : 0f; // -0.06..0.06
        float noiseAmp = ClientConfig.SPINE_ENABLE_NOISE ? ClientConfig.SPINE_NOISE_AMPLITUDE : 0f; // +/- percentage noise

        // Two horizontal bands near the top of available area above icon reserve
        int iconReserve = ClientConfig.SPINE_ICON_SIZE + ClientConfig.SPINE_ICON_BOTTOM_MARGIN; // reserve for bottom icon area
        int available = Math.max(0, h - iconReserve);
        int bandThickness = ClientConfig.SPINE_BAND_THICKNESS;
        int bandGap = ClientConfig.SPINE_BAND_GAP;

        // Place bands starting at top with a small top space
        int band1 = available > 0 ? Math.min(available - bandThickness, ClientConfig.SPINE_BAND_TOP_SPACE) : -1;
        int band2 = band1 >= 0 ? Math.min(available - bandThickness, band1 + bandThickness + bandGap) : -1;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Curved vertical shading with biased center and tilt
                float nx = (x + 0.5f) / w; // 0..1
                float dist = Math.abs(nx - centerBias) * 2f; // 0 center -> ~1 edges
                float side = Math.signum(nx - centerBias);
                dist *= (1f + asymTilt * side);
                float shade = ClientConfig.SPINE_ENABLE_CURVATURE
                    ? (ClientConfig.SPINE_CENTER_BRIGHTEN - ClientConfig.SPINE_EDGE_FACTOR * dist)
                    : 1.0f;
                int rgb = TextUtils.darkenColor(baseRgb, shade);

                // Subtle horizontal roll-off towards top/bottom
                float ny = (y + 0.5f) / h;
                float edgeY = Math.min(ny, 1f - ny) * 2f; // 0 at edges, 1 at center
                float vshade = ClientConfig.SPINE_VSHADE_BASE + ClientConfig.SPINE_VSHADE_RANGE * edgeY; // darker near top/bottom
                rgb = TextUtils.darkenColor(rgb, vshade);

                // Per-pixel noise to break uniformity (deterministic)
                int ns = s5 + x * 7349 + y * 1931;
                ns = next.applyAsInt(ns);
                float n = (rf.apply(ns) - 0.5f) * 2f; // -1..1
                if (noiseAmp != 0f) rgb = TextUtils.darkenColor(rgb, 1f + n * noiseAmp);

                pixels[y * w + x] = 0xFF000000 | (rgb & 0xFFFFFF);
            }
        }

        // Apply horizontal bands post-pass for clean thin lines (darken slightly)
        if (ClientConfig.SPINE_ENABLE_BANDS && band1 >= 0) {
            for (int x = 0; x < w; x++) {
                for (int dy = 0; dy < bandThickness; dy++) { // 2px thickness
                    int yy = band1 + dy;
                    if (yy >= 0 && yy < h) {
                        int idx = yy * w + x;
                        int rgb = pixels[idx] & 0xFFFFFF;
                        pixels[idx] = 0xFF000000 | (TextUtils.darkenColor(rgb, ClientConfig.SPINE_BAND1_DARKEN) & 0xFFFFFF);
                    }
                }
            }
        }

        if (ClientConfig.SPINE_ENABLE_BANDS && band2 >= 0) {
            for (int x = 0; x < w; x++) {
                for (int dy = 0; dy < bandThickness; dy++) {
                    int yy = band2 + dy;
                    if (yy >= 0 && yy < h) {
                        int idx = yy * w + x;
                        int rgb = pixels[idx] & 0xFFFFFF;
                        pixels[idx] = 0xFF000000 | (TextUtils.darkenColor(rgb, ClientConfig.SPINE_BAND2_DARKEN) & 0xFFFFFF);
                    }
                }
            }
        }

        // Optionally embed the element icon into the spine texture (bottom-centered)
        if (ClientConfig.SPINE_EMBED_ICON && iconRl != null && iconSize > 0) {
            int ix = (w - iconSize) / 2;
            int iy = (h - iconSize - ClientConfig.SPINE_ICON_BOTTOM_MARGIN) + ClientConfig.SPINE_ICON_Y_OFFSET;

            if (ix >= 0 && iy >= 0 && ix + iconSize <= w && iy + iconSize <= h) {
                try {
                    IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
                    try (IResource res = rm.getResource(iconRl)) {
                        BufferedImage img = ImageIO.read(res.getInputStream());
                        if (img != null) {
                            int srcW = img.getWidth();
                            int srcH = img.getHeight();
                            int[] iconBuf = img.getRGB(0, 0, srcW, srcH, null, 0, srcW);

                            for (int ty = 0; ty < iconSize; ty++) {
                                int sy = ty * srcH / iconSize;
                                for (int tx = 0; tx < iconSize; tx++) {
                                    int sx = tx * srcW / iconSize;
                                    int argb = iconBuf[sy * srcW + sx];
                                    int a = (argb >>> 24) & 0xFF;
                                    if (a == 0) continue;

                                    int dstIndex = (iy + ty) * w + (ix + tx);
                                    int dst = pixels[dstIndex];

                                    int sr = (argb >>> 16) & 0xFF;
                                    int sg = (argb >>> 8) & 0xFF;
                                    int sb = (argb) & 0xFF;

                                    int dr = (dst >>> 16) & 0xFF;
                                    int dg = (dst >>> 8) & 0xFF;
                                    int db = (dst) & 0xFF;

                                    float af = a / 255.0f;
                                    int rr = (int)(sr * af + dr * (1 - af));
                                    int rg = (int)(sg * af + dg * (1 - af));
                                    int rb = (int)(sb * af + db * (1 - af));

                                    pixels[dstIndex] = 0xFF000000 | ((rr & 0xFF) << 16) | ((rg & 0xFF) << 8) | (rb & 0xFF);
                                }
                            }
                        }
                    }
                } catch (IOException ioe) {
                    // Ignore icon embedding on IO issues; spine will render without embedded icon
                }
            }
        }

        DynamicTexture dyn = new DynamicTexture(w, h);
        int[] data = dyn.getTextureData();
        System.arraycopy(pixels, 0, data, 0, pixels.length);

        dyn.updateDynamicTexture();
        ResourceLocation rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("sa_spine_" + key, dyn);
        spineCache.put(key, rl);

        return rl;
    }

    /**
     * Generates (or retrieves from cache) a simple vertical gradient background texture.
     *
     * @param w Width in pixels.
     * @param h Height in pixels.
     * @return Resource location of the generated or cached texture.
     */
    public static ResourceLocation getOrCreatePanelBg(int w, int h) {
        checkRevision();

        String key = "bg_" + w + "x" + h + "_rev" + ClientConfig.CONFIG_REVISION;
        ResourceLocation existing = bgCache.get(key);
        if (existing != null) return existing;

        int topColor = ClientConfig.BACKGROUND_FILL;
        int bottomColor = ClientConfig.BACKGROUND_BORDER;
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            float t = y / (float)Math.max(1, h - 1);
            int r = (int)((((topColor >> 16) & 0xFF) * (1-t)) + (((bottomColor >> 16) & 0xFF) * t));
            int g = (int)((((topColor >> 8) & 0xFF) * (1-t)) + (((bottomColor >> 8) & 0xFF) * t));
            int b = (int)((((topColor) & 0xFF) * (1-t)) + (((bottomColor) & 0xFF) * t));
            int col = (0xFF << 24) | (r << 16) | (g << 8) | b;

            for (int x = 0; x < w; x++) pixels[y * w + x] = col;
        }

        DynamicTexture dyn = new DynamicTexture(w, h);
        int[] data = dyn.getTextureData();
        System.arraycopy(pixels, 0, data, 0, pixels.length);
        dyn.updateDynamicTexture();

        ResourceLocation rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("sa_panel_bg_" + w + "x" + h, dyn);
        bgCache.put(key, rl);
        return rl;
    }
}
