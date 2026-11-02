package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.yut.exsanguinate.mixin.GameRendererAccessor;

/**
 * BLACK AND WHITE FILTER - a short "blood drained" flash after the infusion
 * completes. The whole screen goes desaturated for 1 second, then snaps back
 * to color (a smooth fade would need a custom shader - see README).
 *
 * It works through the vanilla post-processing pipeline: the same one used by
 * the "Super Secret Settings" button. loadPostProcessor() loads a shader that
 * is applied to the whole screen, and disablePostProcessor() removes it.
 * NOTE: post-processing requires "Fancy" graphics.
 */
@Environment(EnvType.CLIENT)
public class ColorFilter {

    // The vanilla desaturation shader (packaged with the game).
    private static final Identifier DESATURATE = new Identifier("shaders/post/desaturate.json");

    // Wall-clock time (ms) the flash ends. 0 = no filter active.
    private static long endTimeMs = 0L;

    // True only when this class loaded the shader, so we never disable one
    // that was already running (another mod, the spectator shader, ...).
    private static boolean loadedByUs = false;

    private ColorFilter() {
    }

    /** Called (via packet) when the infusion completes. Starts the 1 s flash. */
    public static void start() {
        endTimeMs = Util.getMeasuringTimeMs() + 1000L;
    }

    /**
     * Called every rendered frame (from GameRendererMixin).
     * Keeps the filter loaded for the remaining time, then disables it.
     */
    public static void tick() {
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        if (endTimeMs == 0L) {
            return;
        }

        if (Util.getMeasuringTimeMs() >= endTimeMs) {
            endTimeMs = 0L;
            if (loadedByUs) {
                loadedByUs = false;
                gameRenderer.disablePostProcessor();
            }
            return;
        }

        if (gameRenderer.getPostProcessor() == null) {
            ((GameRendererAccessor) gameRenderer).callLoadPostProcessor(DESATURATE);
            loadedByUs = true;
        }
    }
}
