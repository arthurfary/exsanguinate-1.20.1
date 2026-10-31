package net.yut.exsanguinate.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * Registers the mod's sound events.
 *
 * A SoundEvent is just a name ("exsanguinate:sword_swing"). The actual sound
 * FILE (assets/exsanguinate/sounds/sword_swing.ogg) is linked to that name in
 * assets/exsanguinate/sounds.json.
 *
 * The chain is: SoundEvent id  ->  sounds.json  ->  .ogg file
 */
public class ModSounds {

    // The sound event is registered here, as a static field, when the class
    // loads - the same pattern used for items and effects.
    public static final SoundEvent SWORD_SWING = register("sword_swing");

    // Bloody low thump played while/after the infusion drains the player.
    public static final SoundEvent BLOOD_DRAIN = register("blood_drain");

    // Dull "refusal" thud when the sword cannot take blood (player too weak).
    public static final SoundEvent BLOOD_FAIL = register("blood_fail");

    // Low heartbeat thump, looped by the client while berserk (BerserkSounds).
    public static final SoundEvent BERSERK_HEARTBEAT = register("berserk_heartbeat");

    // Eerie drone that plays (looping) the whole time the player is berserk.
    public static final SoundEvent BERSERK_AMBIENCE = register("berserk_ambience");

    // Short cue when the infusion ritual is fully charged.
    public static final SoundEvent INFUSION_CHARGED = register("infusion_charged");

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(Exsanguinate.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /**
     * Called from Exsanguinate.onInitialize().
     * The sound events are already registered by the static fields above,
     * so this only logs a message.
     */
    public static void registerModSounds() {
        Exsanguinate.LOGGER.info("Registering mod sounds for " + Exsanguinate.MOD_ID);
    }
}