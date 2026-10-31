package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.yut.exsanguinate.sound.ModSounds;

/**
 * What a berserk player HEARS (only them - the client plays these locally):
 *
 *  1. A low heartbeat: "lub-dub", once a second.
 *  2. An eerie drone, looping the whole time.
 *
 * Both follow the vignette's fade (BerserkVignette.progress()), so they swell
 * in when berserk starts and die away when it ends, in step with the red
 * screen edges.
 *
 * The actual sounds are picked in sounds.json (berserk_heartbeat and
 * berserk_ambience) - swap those entries for custom .ogg files any time.
 */
@Environment(EnvType.CLIENT)
public class BerserkSounds {

    // ------------------------------------------------------------------
    // SETTINGS - tweak these to adjust the sounds, then restart the game.
    // ------------------------------------------------------------------
    /** Ticks between heartbeats (20 = 60 beats per minute). */
    public static final int HEARTBEAT_TICKS = 20;

    /** Ticks between the "lub" and the "dub" of one heartbeat. */
    public static final int DUB_DELAY_TICKS = 6;

    /** Loudness of the heartbeat (the "dub" is a bit softer). */
    public static final float HEARTBEAT_VOLUME = 0.45f;

    /** Pitch of the heartbeat. Lower = deeper (0.5 is the lowest). */
    public static final float HEARTBEAT_PITCH = 0.6f;

    /** Loudness of the eerie drone. */
    public static final float DRONE_VOLUME = 0.5f;

    // The drone currently playing, or null.
    private static Drone drone = null;

    // Counts ticks while berserk, to time the heartbeat.
    private static int beatTicks = 0;

    private BerserkSounds() {
    }

    /** Called once per client tick (after BerserkVignette.tick). */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.isPaused()) {
            return;
        }

        // Start the drone when berserk starts (the drone fades itself in/out).
        if (BerserkVignette.isActive() && (drone == null || drone.isDone())) {
            drone = new Drone();
            client.getSoundManager().play(drone);
        }

        float intensity = BerserkVignette.progress();
        if (intensity <= 0.0f) {
            beatTicks = 0;
            return;
        }

        int beat = beatTicks++ % HEARTBEAT_TICKS;
        if (beat == 0) {
            thump(client, HEARTBEAT_VOLUME * intensity, HEARTBEAT_PITCH);
        } else if (beat == DUB_DELAY_TICKS) {
            thump(client, HEARTBEAT_VOLUME * 0.7f * intensity, HEARTBEAT_PITCH * 0.9f);
        }
    }

    /** Called on disconnect (the game stops all sounds then anyway). */
    public static void reset() {
        drone = null;
        beatTicks = 0;
    }

    private static void thump(MinecraftClient client, float volume, float pitch) {
        // Not positioned in the world: it plays "inside your head", the same
        // in both ears, wherever you look.
        client.getSoundManager().play(new PositionedSoundInstance(
                ModSounds.BERSERK_HEARTBEAT.getId(), SoundCategory.PLAYERS, volume, pitch,
                SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE,
                0.0, 0.0, 0.0, true));
    }

    /**
     * The looping eerie drone. A "moving" sound instance gets tick() called
     * every tick while it plays, which is where its volume follows the fade.
     */
    private static class Drone extends MovingSoundInstance {

        Drone() {
            super(ModSounds.BERSERK_AMBIENCE, SoundCategory.PLAYERS, SoundInstance.createRandom());
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.0f;
            // Inside your head, like the heartbeat.
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
        }

        @Override
        public void tick() {
            float intensity = BerserkVignette.progress();
            this.volume = DRONE_VOLUME * intensity;
            // Fully faded out and berserk is over: stop for good.
            if (intensity <= 0.0f && !BerserkVignette.isActive()) {
                this.setDone();
            }
        }

        @Override
        public boolean shouldAlwaysPlay() {
            // It starts at volume 0 (to fade in), and Minecraft skips
            // sounds that start silent unless they ask not to be.
            return true;
        }
    }
}
