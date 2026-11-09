package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.yut.exsanguinate.Exsanguinate;
import net.yut.exsanguinate.berserk.BerserkMode;
import net.yut.exsanguinate.mixin.GameRendererAccessor;
import net.yut.exsanguinate.mixin.PostEffectProcessorAccessor;

/**
 * BERSERK FILTER - the screen drains of colour while the player is berserk.
 *
 * It is a custom post shader (see assets/exsanguinate/shaders/): the screen
 * goes fully black and white, except red pixels, which keep their colour, so
 * blood and the blood vignette stay red in a grey world.
 *
 * Because it is our own shader, the strength can be animated: the shader has
 * an "Intensity" uniform (0 = untouched screen, 1 = fully desaturated) which
 * this class sets every rendered frame. The fade is driven by the same
 * client-side berserk flag as the blood vignette, so it starts and ends
 * exactly with the mode:
 *
 *  - fades IN over 1 s
 *  - fades OUT over 2 s (slower still: the colour slowly comes back)
 *
 * The raw 0 -> 1 progress is eased (smoothstep) before it reaches the shader,
 * so both ends of each fade are gentle instead of a constant-speed slide.
 *
 * Like every post shader this needs "Fancy" graphics, and it works through
 * the vanilla pipeline: loadPostProcessor() loads the shader, and
 * disablePostProcessor() removes it. We only ever disable a shader we
 * loaded ourselves.
 */
@Environment(EnvType.CLIENT)
public class ColorFilter {

    // Our post shader: assets/exsanguinate/shaders/post/berserk.json
    private static final Identifier BERSERK_FILTER =
            new Identifier(Exsanguinate.MOD_ID, "shaders/post/berserk.json");

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to adjust the filter, then restart the game.
    // ------------------------------------------------------------------
    /** How long the fade in takes, in seconds (berserk starting). */
    public static final float FADE_IN_SECONDS = 1.0f;

    /** How long the fade out takes, in seconds (berserk ending). */
    public static final float FADE_OUT_SECONDS = 2.0f;

    // 0.0 = no filter at all, 1.0 = fully desaturated.
    private static float progress = 0.0f;

    // Wall-clock time of the last frame, so the fade follows real seconds
    // rather than frame count. 0 = none yet.
    private static long lastFrameMs = 0L;

    // True only when this class loaded the shader, so we never disable one
    // that was already running (another mod, the spectator shader, ...).
    private static boolean loadedByUs = false;

    // When a load attempt failed (bad json/glsl), wait a bit before trying
    // again instead of retrying every frame and flooding the log.
    private static long nextLoadAttemptMs = 0L;

    // Only warn once about a shader without the Intensity uniform.
    private static boolean warnedMissingUniform = false;

    private ColorFilter() {
    }

    /**
     * Called when leaving a world: drop the filter immediately instead of
     * letting it fade out over the next screen.
     */
    public static void reset() {
        progress = 0.0f;
        lastFrameMs = 0L;
        nextLoadAttemptMs = 0L;
        if (loadedByUs) {
            loadedByUs = false;
            disable(gameRenderer());
        }
    }

    /**
     * Called every rendered frame (from GameRendererMixin). Moves the fade
     * along with real elapsed time and feeds the eased strength to the
     * shader's "Intensity" uniform.
     *
     * Loading or removing the shader does NOT happen here: doing that in the
     * middle of renderWorld leaves the wrong framebuffer bound for the rest
     * of the frame and smears the screen. updateShader() does it between
     * frames instead.
     */
    public static void tick() {
        // Clamp the step so a stutter (or unpausing) never snaps the fade.
        long now = Util.getMeasuringTimeMs();
        float deltaSeconds = lastFrameMs == 0L ? 0.0f
                : MathHelper.clamp((now - lastFrameMs) / 1000.0f, 0.0f, 0.1f);
        lastFrameMs = now;

        if (BerserkMode.isClientActive()) {
            progress = Math.min(1.0f, progress + deltaSeconds / FADE_IN_SECONDS);
        } else {
            progress = Math.max(0.0f, progress - deltaSeconds / FADE_OUT_SECONDS);
        }

        if (progress > 0.0f) {
            setIntensity(gameRenderer().getPostProcessor(), eased(progress));
        }
    }

    /**
     * Called once per client tick (from ExsanguinateClient), between frames.
     * This is where the shader is loaded and removed, so it never happens in
     * the middle of the world render.
     */
    public static void updateShader() {
        GameRenderer gameRenderer = gameRenderer();

        // Fully faded out (or never faded in): the shader is not needed.
        if (progress <= 0.0f) {
            if (loadedByUs) {
                loadedByUs = false;
                disable(gameRenderer);
            }
            return;
        }

        // Already loaded (ours) or in use by someone else: never steal it.
        if (gameRenderer.getPostProcessor() != null) {
            return;
        }

        long now = Util.getMeasuringTimeMs();
        if (now < nextLoadAttemptMs) {
            return;
        }

        ((GameRendererAccessor) gameRenderer).callLoadPostProcessor(BERSERK_FILTER);
        if (gameRenderer.getPostProcessor() == null) {
            // The load failed (vanilla already logged why: bad json or glsl).
            // Do not hammer it every frame; try again in a while.
            nextLoadAttemptMs = now + 5000L;
            loadedByUs = false;
            return;
        }

        loadedByUs = true;
        setIntensity(gameRenderer.getPostProcessor(), eased(progress));
    }

    private static GameRenderer gameRenderer() {
        return MinecraftClient.getInstance().gameRenderer;
    }

    /**
     * Removes the filter, but never someone else's shader: if vanilla (or
     * another mod) swapped in its own post processor meanwhile, leave it.
     */
    private static void disable(GameRenderer gameRenderer) {
        PostEffectProcessor current = gameRenderer.getPostProcessor();
        if (current != null && BERSERK_FILTER.toString().equals(current.getName())) {
            gameRenderer.disablePostProcessor();
        }
    }

    /** Smoothstep: starts and ends gently instead of at constant speed. */
    private static float eased(float linear) {
        return linear * linear * (3.0f - 2.0f * linear);
    }

    /** Pushes the fade strength into the shader's Intensity uniform. */
    private static void setIntensity(PostEffectProcessor effect, float intensity) {
        if (effect == null || !BERSERK_FILTER.toString().equals(effect.getName())) {
            return;
        }
        boolean found = false;
        for (PostEffectPass pass : ((PostEffectProcessorAccessor) effect).getPasses()) {
            if (pass.getProgram() == null) {
                continue;
            }
            GlUniform uniform = pass.getProgram().getUniformByName("Intensity");
            if (uniform != null) {
                uniform.set(intensity);
                found = true;
            }
        }
        if (!found && !warnedMissingUniform) {
            warnedMissingUniform = true;
            Exsanguinate.LOGGER.warn("Berserk shader has no 'Intensity' uniform; the screen will not fade.");
        }
    }
}
