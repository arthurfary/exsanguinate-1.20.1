package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.yut.exsanguinate.item.ModItems;

/**
 * Applies the screen shake while the player charges the Carnelian Oath.
 *
 * The strength starts at 0 and ramps up as the infusion charges (the shake
 * is proportional to the charge progress, squared, so it starts slow and
 * accelerates). It only shakes while holding right click - releasing or
 * finishing the infusion stops it.
 *
 * How it works: the GameRendererMixin calls apply() while the world is being
 * rendered, right after the camera rotation is applied to the view matrix.
 * Translating that matrix slightly offsets everything the camera sees,
 * which reads as "screen shake". The HUD is not affected.
 */
@Environment(EnvType.CLIENT)
public class ScreenShake {

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to adjust the shake, then restart the game.
    // ------------------------------------------------------------------
    /** Master switch: set to false to disable the shake entirely. */
    public static final boolean ENABLED = true;

    /** Strongest camera offset, in blocks (0.05 = subtle, 0.2 = violent). */
    public static final float MAX_INTENSITY = 0.1f;

    /** How fast the shake oscillates (higher = faster/jitterier). */
    public static final float SPEED = 0.5f;

    /**
     * Offsets the given view matrix by a small, oscillating amount.
     * Called once per rendered frame.
     */
    public static void apply(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.world == null || !ENABLED) {
            return;
        }

        // Only shake while the player is actually charging the sword.
        if (!player.isUsingItem()) {
            return;
        }
        ItemStack activeStack = player.getActiveItem();
        if (activeStack.getItem() != ModItems.CARNELIAN_OATH) {
            return;
        }

        // Charge progress: 0.0 just started, 1.0 fully charged.
        int maxUseTime = activeStack.getMaxUseTime();
        int timeLeft = player.getItemUseTimeLeft();
        float progress = MathHelper.clamp(1.0f - (float) timeLeft / maxUseTime, 0.0f, 1.0f);
        if (progress <= 0.0f) {
            return;
        }

        // Squaring the progress makes the shake start slow and accelerate
        // ("ramps up") as the infusion nears completion.
        float ramp = progress * progress;

        // Oscillation time - speeds up the whole shake when configured so.
        float time = (client.world.getTime() + tickDelta) * SPEED;

        // A sine + cosine for smooth oscillation, plus a bit of randomness
        // so the shake doesn't look mechanical. Scaled by max intensity.
        float amplitude = ramp * MAX_INTENSITY;
        float x = (MathHelper.sin(time * 2.0f) + (float) (client.world.random.nextDouble() - 0.5)) * amplitude;
        float y = (MathHelper.cos(time * 1.7f) + (float) (client.world.random.nextDouble() - 0.5)) * amplitude;

        matrices.translate(x, y, 0.0f);
    }
}