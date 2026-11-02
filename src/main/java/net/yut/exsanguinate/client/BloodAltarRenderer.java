package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import net.yut.exsanguinate.block.entity.BloodAltarBlockEntity;

import java.util.List;

/**
 * Draws the offering floating above the Blood Altar.
 *
 * The block model (JSON) is drawn by Minecraft like any block; a block
 * entity RENDERER is extra drawing done every frame, used for things that
 * move - here the items. Since it runs every frame, everything is computed
 * from time, so it animates smoothly with no extra data from the server.
 *
 * Idle: the items bob and orbit slowly in a little ring.
 * Ritual: they rise, spin faster, the ring closes until they overlap, they
 * tremble and shrink into nothing - and the server swaps in the result.
 */
@Environment(EnvType.CLIENT)
public class BloodAltarRenderer implements BlockEntityRenderer<BloodAltarBlockEntity> {

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to change the look.
    // ------------------------------------------------------------------
    /** Height of the items' center above the bottom of the block (the top is at 0.875). */
    private static final float FLOAT_HEIGHT = 1.25f;
    /** How far the items rise during the ritual, in blocks. */
    private static final float RITUAL_RISE = 0.6f;
    /** Radius of the ring the items sit in (when there is more than one). */
    private static final float RING_RADIUS = 0.3f;
    /**
     * Size of each item, and of a lone item (a lone item is usually the
     * result: show it off). These multiply the item's true model size: a
     * normal item is 1 block wide, the Carnelian Oath about 2.
     */
    private static final float ITEM_SCALE = 0.4f;
    private static final float SINGLE_SCALE = 0.75f;
    /** Idle orbit and self-spin speeds, in degrees per tick. */
    private static final float ORBIT_SPEED = 1.0f;
    private static final float SPIN_SPEED = 2.0f;
    /** Ritual progress (0..1) at which the ring has fully closed, and at which the items start to vanish. */
    private static final float GATHER_END = 0.75f;
    private static final float VANISH_START = 0.85f;

    private final ItemRenderer itemRenderer;

    public BloodAltarRenderer(BlockEntityRendererFactory.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BloodAltarBlockEntity altar, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = altar.getWorld();
        List<ItemStack> offering = altar.getOffering();
        if (world == null || offering.isEmpty()) {
            return;
        }

        float time = world.getTime() + tickDelta;
        float progress = altar.getRitualProgress(tickDelta);

        // gather: 0 -> 1 while the ring closes. vanish: 0 -> 1 as they melt away.
        float gather = smoothstep(MathHelper.clamp(progress / GATHER_END, 0.0f, 1.0f));
        float vanish = MathHelper.clamp((progress - VANISH_START) / (1.0f - VANISH_START), 0.0f, 1.0f);

        int count = offering.size();
        float scale = (count == 1 ? SINGLE_SCALE : ITEM_SCALE) * (1.0f - vanish);
        if (scale <= 0.001f) {
            return;
        }
        float radius = count == 1 ? 0.0f : RING_RADIUS * (1.0f - gather);
        float height = FLOAT_HEIGHT + MathHelper.sin(time * 0.08f) * 0.04f + RITUAL_RISE * gather;

        // The orbit and the spin accelerate with progress squared: slow at
        // first, frantic at the end.
        float orbit = time * ORBIT_SPEED + 900.0f * progress * progress;
        float spin = time * SPIN_SPEED + 1440.0f * progress * progress;

        // Trembling in the last part of the ritual.
        float tremble = MathHelper.clamp((progress - 0.6f) / 0.4f, 0.0f, 1.0f) * 0.035f;

        // Light: the light above the altar normally (the altar block itself is
        // solid, so its own light value would be dark). During the ritual the
        // offering glows at full brightness.
        int itemLight = altar.isRitualActive()
                ? LightmapTextureManager.MAX_LIGHT_COORDINATE
                : WorldRenderer.getLightmapCoordinates(world, altar.getPos().up());

        for (int i = 0; i < count; i++) {
            float angle = (orbit + i * 360.0f / count) * MathHelper.RADIANS_PER_DEGREE;

            matrices.push();
            matrices.translate(
                    0.5f + MathHelper.cos(angle) * radius + MathHelper.sin(time * 2.7f + i) * tremble,
                    height + MathHelper.cos(time * 3.1f + i) * tremble,
                    0.5f + MathHelper.sin(angle) * radius + MathHelper.sin(time * 3.7f + i) * tremble);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin + i * 90.0f));
            matrices.scale(scale, scale, scale);
            // NONE = the model exactly as it was made, with none of the
            // per-view transforms. Item frames (FIXED) shrink the huge
            // Carnelian Oath down to one block; NONE keeps every item at its
            // real size relative to the others, so the oath stays huge.
            itemRenderer.renderItem(offering.get(i), ModelTransformationMode.NONE, itemLight,
                    OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, world, (int) altar.getPos().asLong() + i);
            matrices.pop();
        }
    }

    /** Eases 0..1 in and out (slow start, slow end). */
    private static float smoothstep(float x) {
        return x * x * (3.0f - 2.0f * x);
    }
}
