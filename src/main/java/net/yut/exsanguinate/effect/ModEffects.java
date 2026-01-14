package net.yut.exsanguinate.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

public class ModEffects {
    public static final StatusEffect BLOOD_CURSE = registerEffect("blood_curse", new BloodCurseEffect());

    private static StatusEffect registerEffect(String name, StatusEffect effect) {
        return Registry.register(Registries.STATUS_EFFECT, new Identifier(Exsanguinate.MOD_ID, name), effect);
    }

    public static void registerModEffects() {
        Exsanguinate.LOGGER.info("Registering mod effects for " + Exsanguinate.MOD_ID);
    }
}