package net.yut.exsanguinate.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * Registers the mod's status effects (the things potions apply, like poison
 * or strength). Same pattern as ModItems: static fields register immediately
 * when the class loads.
 */
public class ModEffects {

    // HARMFUL (red potion icon), red color. Does nothing on its own -
    // see BloodCurseEffect for why.
    public static final StatusEffect BLOOD_CURSE = registerEffect("blood_curse", new BloodCurseEffect());

    private static StatusEffect registerEffect(String name, StatusEffect effect) {
        return Registry.register(Registries.STATUS_EFFECT, new Identifier(Exsanguinate.MOD_ID, name), effect);
    }

    /**
     * Called from Exsanguinate.onInitialize().
     * The effect itself was already registered by the static field above,
     * so this only logs a message.
     */
    public static void registerModEffects() {
        Exsanguinate.LOGGER.info("Registering mod effects for " + Exsanguinate.MOD_ID);
    }
}