package net.yut.exsanguinate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.yut.exsanguinate.Exsanguinate;

/**
 * BLOOD VIGNETTE - red, grainy screen edges while the player is berserk.
 *
 * When berserk starts it fades in while zooming OUT from the center: the
 * image starts blown up (its red edges off screen) and shrinks into place,
 * so the blood closes in on you. When berserk ends it does the reverse:
 * fades out while zooming IN, the edges sliding back off screen.
 *
 * Both are the same animation run forwards or backwards: a single
 * "progress" value goes 0 -> 1 (fade in) or 1 -> 0 (fade out), and both
 * the opacity and the zoom are worked out from it.
 *
 * The server says when berserk starts/ends (BerserkStatePayload); the
 * InGameHudMixin calls render() every frame, under the hotbar and hearts.
 */
@Environment(EnvType.CLIENT)
public class BerserkVignette {

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to adjust the vignette, then restart the game.
    // ------------------------------------------------------------------
    /** How long the fade in takes, in ticks (20 = 1 second). */
    public static final int FADE_IN_TICKS = 15;

    /** How long the fade out takes, in ticks. */
    public static final int FADE_OUT_TICKS = 25;

    /** Extra zoom when fully faded out (0.5 = 50% bigger than the screen). */
    public static final float ZOOM = 0.5f;

    private static final Identifier TEXTURE = new Identifier(Exsanguinate.MOD_ID, "textures/misc/blood_vignette.png");

    // Is the player berserk right now (as last told by the server)?
    private static boolean active = false;

    // 0.0 = invisible, 1.0 = fully shown. "prev" is last tick's value, so the
    // animation can be smoothed between ticks (every frame, not every tick).
    private static float progress = 0.0f;
    private static float prevProgress = 0.0f;

    private BerserkVignette() {
    }

    /** Called (via packet) when berserk starts or ends. */
    public static void setActive(boolean berserk) {
        active = berserk;
    }

    /** Is the player berserk right now? (BerserkSounds asks.) */
    public static boolean isActive() {
        return active;
    }

    /**
     * How far the fade has gone, 0.0 (gone) to 1.0 (fully shown). The
     * berserk sounds use it too, so they fade in and out with the vignette.
     */
    public static float progress() {
        return progress;
    }

    /** Called on disconnect: never carry a vignette into the next world. */
    public static void reset() {
        active = false;
        progress = 0.0f;
        prevProgress = 0.0f;
    }

    /** Called once per client tick: moves the animation along. */
    public static void tick() {
        prevProgress = progress;
        progress = active
                ? Math.min(1.0f, progress + 1.0f / FADE_IN_TICKS)
                : Math.max(0.0f, progress - 1.0f / FADE_OUT_TICKS);
    }

    /** Called every rendered frame (from InGameHudMixin). */
    public static void render(DrawContext context, float tickDelta) {
        float linear = MathHelper.lerp(tickDelta, prevProgress, progress);
        if (linear <= 0.0f) {
            return;
        }
        // Smoothstep: starts and ends gently instead of at constant speed.
        float eased = linear * linear * (3.0f - 2.0f * linear);
        float scale = 1.0f + (1.0f - eased) * ZOOM;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        // Scale around the screen center: move the center to (0, 0), scale,
        // then move it back.
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(width / 2.0f, height / 2.0f, 0.0f);
        matrices.scale(scale, scale, 1.0f);
        matrices.translate(-width / 2.0f, -height / 2.0f, 0.0f);

        // Same setup vanilla uses for full-screen overlays (pumpkin, powder
        // snow), plus normal alpha blending so the image's transparent
        // center lets the world show through.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1.0f, 1.0f, 1.0f, eased);
        // Texture size = drawn size, so the whole image stretches over the screen.
        context.drawTexture(TEXTURE, 0, 0, -90, 0.0f, 0.0f, width, height, width, height);
        context.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        matrices.pop();
    }
}
