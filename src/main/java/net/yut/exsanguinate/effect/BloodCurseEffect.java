package net.yut.exsanguinate.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * A status effect that is purely visual: it shows up as a red debuff icon,
 * but does nothing while active.
 *
 * It "ticks" every few seconds while applied. We return false from
 * canApplyUpdateEffect so the effect never ticks - it just sits in the
 * effects bar until it expires.
 */
public class BloodCurseEffect extends StatusEffect {
    public BloodCurseEffect() {
        super(
                StatusEffectCategory.HARMFUL, // HARMFUL = red icon (like poison), BENEFICIAL = blue (like regen)
                0xFF0000 // The color of the particles/icon, as hex (this is red)
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // No tick logic - the effect does nothing on its own.
        return false;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Leave empty - we just want the visual effect.
    }
}