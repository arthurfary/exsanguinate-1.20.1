package net.yut.exsanguinate.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives us write access to LivingEntity.itemUseTimeLeft (the ticks remaining
 * of the current "use", e.g. holding right-click). Vanilla only exposes a
 * getter, but BerserkMode needs to shorten the infusion charge time.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("itemUseTimeLeft")
    void setItemUseTimeLeft(int ticks);
}