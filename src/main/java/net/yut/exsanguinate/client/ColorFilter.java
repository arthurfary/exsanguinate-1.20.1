package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
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

    // Seconds of black & white left. 0 = no filter active.
    private static float secondsLeft = 0.0f;

    private ColorFilter() {
    }

    /** Called (via packet) when the infusion completes. Starts the 1 s flash. */
    public static void start() {
        secondsLeft = 1.0f;
    }

    /**
     * Called every rendered frame (from GameRendererMixin).
     * Keeps the filter loaded for the remaining time, then disables it.
     */
    public static void tick(float tickDelta) {
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        if (secondsLeft <= 0.0f) {
            return;
        }

        if (gameRenderer.getPostProcessor() == null) {
            ((GameRendererAccessor) gameRenderer).callLoadPostProcessor(DESATURATE);
        }

        secondsLeft -= tickDelta;
        if (secondsLeft <= 0.0f) {
            secondsLeft = 0.0f;
            gameRenderer.disablePostProcessor();
        }
    }
}