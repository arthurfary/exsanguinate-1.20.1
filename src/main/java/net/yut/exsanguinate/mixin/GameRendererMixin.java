package net.yut.exsanguinate.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.yut.exsanguinate.client.ColorFilter;
import net.yut.exsanguinate.client.ScreenShake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixes into GameRenderer.renderWorld(), the method that renders the world
 * each frame. Right after the camera rotation is applied to the view matrix
 * (and just before the world is drawn), we apply the screen shake offset.
 *
 * This is the classic "screen shake" spot: translating the view matrix
 * shifts everything the camera sees, while the HUD stays in place.
 * The black & white filter is also ticked from here, once per frame.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void exsanguinate$applyScreenShake(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        ScreenShake.apply(matrices, tickDelta);
        ColorFilter.tick();
    }
}