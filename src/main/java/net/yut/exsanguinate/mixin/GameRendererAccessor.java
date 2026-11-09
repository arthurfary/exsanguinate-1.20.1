package net.yut.exsanguinate.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Gives us access to GameRenderer.loadPostProcessor(), which is package
 * private in vanilla. ColorFilter uses it to load the berserk desaturation
 * shader (and only that shader).
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Invoker("loadPostProcessor")
    void callLoadPostProcessor(Identifier id);
}