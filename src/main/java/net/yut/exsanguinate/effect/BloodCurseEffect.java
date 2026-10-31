package net.yut.exsanguinate.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * The "open wound" the Carnelian Oath leaves after it takes your blood.
 *
 * While it is on, the player cannot heal from food (natural regeneration).
 * The effect class itself does nothing: the blocking lives in
 * PlayerEntityMixin, which checks for this effect. That keeps the effect a
 * plain marker - with a red icon and red swirl particles, so the player can
 * SEE they are bleeding.
 *
 * It "ticks" every few seconds while applied. We return false from
 * canApplyUpdateEffect so the effect never ticks - it just sits in the
 * effects bar until it expires.
 */
public class BloodCurseEffect extends StatusEffect {
    public BloodCurseEffect() {
        super(
                StatusEffectCategory.HARMFUL, // HARMFUL = red icon (like poison), BENEFICIAL = blue (like regen)
                0x8A0303 // The color of the particles, as hex (a dark blood red)
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        // No tick logic - the effect does nothing on its own.
        return false;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // Leave empty - PlayerEntityMixin does the real work.
    }
}
