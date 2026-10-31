package net.yut.exsanguinate.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.yut.exsanguinate.client.BerserkVignette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the berserk blood vignette as part of the HUD.
 *
 * Fabric's HudRenderCallback would draw it on top of everything, darkening
 * the hotbar and hearts. Instead we slot it in right after vanilla's own
 * vignette, before the hotbar is drawn. The anchor is the first thing
 * render() does after the vignette (getLastFrameDuration), which runs on
 * both Fancy and Fast graphics.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;getLastFrameDuration()F"
            )
    )
    private void exsanguinate$renderBerserkVignette(DrawContext context, float tickDelta, CallbackInfo ci) {
        BerserkVignette.render(context, tickDelta);
    }
}
