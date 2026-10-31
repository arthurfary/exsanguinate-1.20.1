package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.yut.exsanguinate.item.ModItems;

/**
 * Camera shake for the Carnelian Oath. Two sources add up:
 *
 * 1. CHARGE: while the player charges the infusion, a rumble that starts at 0
 *    and ramps up (squared, so it starts slow and accelerates). It stops when
 *    right click is released or the infusion finishes.
 *
 * 2. IMPACT: a "kick" when a fed swing lands (kick() is called from the
 *    client's attack event, see ExsanguinateClient). The kick is stored as
 *    "trauma" that decays every tick; the shake grows with trauma squared, so
 *    a hit slams the camera hard and then settles quickly.
 *
 * How it works: the GameRendererMixin calls apply() while the world is being
 * rendered, right after the camera rotation is applied to the view matrix.
 * Translating and rolling that matrix slightly offsets everything the camera
 * sees, which reads as "screen shake". The HUD is not affected.
 */
@Environment(EnvType.CLIENT)
public class ScreenShake {

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to adjust the shake, then restart the game.
    // ------------------------------------------------------------------
    /** Master switch: set to false to disable the shake entirely. */
    public static final boolean ENABLED = true;

    /** Strongest charge rumble offset, in blocks (0.05 = subtle, 0.2 = violent). */
    public static final float MAX_INTENSITY = 0.1f;

    /** How fast the shake oscillates (higher = faster/jitterier). */
    public static final float SPEED = 0.5f;

    /** Strongest impact offset, in blocks, at full trauma. */
    public static final float IMPACT_INTENSITY = 0.25f;

    /** Strongest impact camera roll, in degrees, at full trauma. */
    public static final float IMPACT_ROLL = 5.0f;

    /** Trauma lost per tick (0.08 = a full kick settles in ~0.6 s). */
    public static final float TRAUMA_DECAY = 0.08f;

    // Current impact trauma, 0.0 (calm) to 1.0 (maximum).
    private static float trauma = 0.0f;

    /** Slams the camera. Kicks stack, capped at 1.0. */
    public static void kick(float amount) {
        trauma = Math.min(1.0f, trauma + amount);
    }

    /** Called once per client tick: settles the impact shake. */
    public static void tick() {
        trauma = Math.max(0.0f, trauma - TRAUMA_DECAY);
    }

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

        float impact = trauma * trauma;
        float charge = chargeRamp(player);
        if (impact <= 0.0f && charge <= 0.0f) {
            return;
        }

        // Oscillation time - speeds up the whole shake when configured so.
        float time = (client.world.getTime() + tickDelta) * SPEED;

        // A sine + cosine for smooth oscillation, plus a bit of randomness
        // so the shake doesn't look mechanical.
        float amplitude = charge * MAX_INTENSITY + impact * IMPACT_INTENSITY;
        float x = (MathHelper.sin(time * 2.0f) + (float) (client.world.random.nextDouble() - 0.5)) * amplitude;
        float y = (MathHelper.cos(time * 1.7f) + (float) (client.world.random.nextDouble() - 0.5)) * amplitude;
        matrices.translate(x, y, 0.0f);

        // Roll the camera too: a tilt sells an impact far better than sliding.
        if (impact > 0.0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(time * 5.0f) * impact * IMPACT_ROLL));
        }
    }

    /**
     * Charge progress squared while the player is charging the sword, else 0.
     * Squaring makes the rumble start slow and accelerate as the infusion
     * nears completion.
     */
    private static float chargeRamp(PlayerEntity player) {
        if (!player.isUsingItem()) {
            return 0.0f;
        }
        ItemStack activeStack = player.getActiveItem();
        if (activeStack.getItem() != ModItems.CARNELIAN_OATH) {
            return 0.0f;
        }

        // Charge progress: 0.0 just started, 1.0 fully charged.
        float progress = MathHelper.clamp(1.0f - (float) player.getItemUseTimeLeft() / activeStack.getMaxUseTime(), 0.0f, 1.0f);
        return progress * progress;
    }
}
