package net.yut.exsanguinate.mixin;

import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Gives us the list of passes of a loaded post shader. ColorFilter walks it
 * to find its "Intensity" uniform, which it updates every frame to fade the
 * berserk desaturation in and out. Vanilla has no public getter for it.
 */
@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {

    @Accessor("passes")
    List<PostEffectPass> getPasses();
}
