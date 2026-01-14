package net.yut.exsanguinate.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class BloodCurseEffect extends StatusEffect {
    public BloodCurseEffect() {
        super(
                StatusEffectCategory.HARMFUL, // Category
                0xFF0000 // Color in hex (this is red - change to whatever you want)
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // We don't want this effect to do anything on its own
        return false;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Leave empty - we just want the visual effect
    }
}