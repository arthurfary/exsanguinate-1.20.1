package net.yut.exsanguinate.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * Registers the mod's particle types. Same pattern as ModItems: static fields
 * register immediately when the class loads.
 *
 * A particle TYPE is only a name both sides agree on, so the server can tell
 * clients "spawn a blood_footprint here". What it looks like lives on the
 * client: BloodFootprintParticle (behavior) + particles/blood_footprint.json
 * (which texture to use).
 */
public class ModParticles {

    // A bloody footprint left on the ground by a berserk wielder (see BerserkMode).
    // true = "always spawn": shown even past 32 blocks and on Minimal particle
    // settings, because it is a gameplay warning, not decoration.
    public static final DefaultParticleType BLOOD_FOOTPRINT = register("blood_footprint", FabricParticleTypes.simple(true));

    private static DefaultParticleType register(String name, DefaultParticleType type) {
        return Registry.register(Registries.PARTICLE_TYPE, new Identifier(Exsanguinate.MOD_ID, name), type);
    }

    /**
     * Called from Exsanguinate.onInitialize().
     * The particle types are already registered by the static fields above,
     * so this only logs a message.
     */
    public static void registerModParticles() {
        Exsanguinate.LOGGER.info("Registering mod particles for " + Exsanguinate.MOD_ID);
    }
}
