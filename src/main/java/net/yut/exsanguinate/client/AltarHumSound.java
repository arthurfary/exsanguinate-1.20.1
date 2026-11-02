package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.yut.exsanguinate.block.entity.BloodAltarBlockEntity;
import net.yut.exsanguinate.ritual.Rituals;
import net.yut.exsanguinate.sound.ModSounds;

import java.util.HashMap;
import java.util.Map;

/**
 * The drone of a primed Blood Altar: the berserk drone's darker sibling,
 * an octave lower and barely there, with a wet, fleshy loop underneath it.
 * Only people standing near the altar hear it: it fades to silence at the
 * same distance a kill still feeds the altar (Rituals.SACRIFICE_RADIUS).
 *
 * Each primed altar plays two looping layers (see sounds.json: altar_hum is
 * the soul sand valley drone at half pitch, altar_hum_flesh the crimson
 * forest loop at half pitch). They fade in when the offering is complete,
 * swell a little while the ritual runs, and fade out when it's over or the
 * offering is taken away.
 *
 * BloodAltarBlockEntity calls keepAlive() every client tick while it is
 * primed; this class makes sure each layer is playing exactly once.
 */
@Environment(EnvType.CLIENT)
public class AltarHumSound extends MovingSoundInstance {

    // ------------------------------------------------------------------
    // SETTINGS - keep these low: it should be felt more than heard.
    // ------------------------------------------------------------------
    /** Loudness of the drone and the flesh layer while primed. */
    private static final float DRONE_VOLUME = 0.22f;
    private static final float FLESH_VOLUME = 0.12f;
    /** Loudness of the rare whisper (at the altar; it fades out by RANGE too). */
    private static final float WHISPER_VOLUME = 0.3f;
    /** Both layers are multiplied by this while the ritual runs. */
    private static final float RITUAL_SWELL = 1.6f;
    /** Fade speed per tick (0.04 = 1.25 s to fade fully in or out). */
    private static final float FADE_PER_TICK = 0.04f;
    /** Silent past this many blocks: the reach of a sacrifice. */
    public static final double RANGE = Rituals.SACRIFICE_RADIUS;

    // The layers currently playing, by altar position (two keys per altar).
    private static final Map<Long, AltarHumSound> PLAYING = new HashMap<>();

    private final BloodAltarBlockEntity altar;
    private final long key;
    private final float maxVolume;
    private float fade = 0.0f;

    private AltarHumSound(BloodAltarBlockEntity altar, SoundEvent sound, long key, float maxVolume) {
        super(sound, SoundCategory.BLOCKS, SoundInstance.createRandom());
        this.altar = altar;
        this.key = key;
        this.maxVolume = maxVolume;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 0.0f;
        BlockPos pos = altar.getPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.8;
        this.z = pos.getZ() + 0.5;
        // Positional (you can tell where it comes from). Minecraft's own
        // falloff only reaches silence at 16 blocks, so tick() fades it out
        // much sooner, at RANGE.
        this.attenuationType = AttenuationType.LINEAR;
    }

    /** Called every client tick while an altar is primed: (re)starts its layers. */
    public static void keepAlive(BloodAltarBlockEntity altar) {
        long base = altar.getPos().asLong() * 2;
        ensurePlaying(altar, ModSounds.ALTAR_HUM, base, DRONE_VOLUME);
        ensurePlaying(altar, ModSounds.ALTAR_HUM_FLESH, base + 1, FLESH_VOLUME);
    }

    private static void ensurePlaying(BloodAltarBlockEntity altar, SoundEvent sound, long key, float volume) {
        SoundManager sounds = MinecraftClient.getInstance().getSoundManager();
        AltarHumSound playing = PLAYING.get(key);
        // Still the same altar and still playing? Nothing to do. (A new world
        // or a resource reload stops sounds or replaces the block entity.)
        if (playing != null && playing.altar == altar && !playing.isDone() && sounds.isPlaying(playing)) {
            return;
        }
        AltarHumSound hum = new AltarHumSound(altar, sound, key, volume);
        PLAYING.put(key, hum);
        sounds.play(hum);
    }

    /** The rare whisper of a primed altar, as quiet and as short-reaching as the drone. */
    public static void whisper(BloodAltarBlockEntity altar) {
        BlockPos pos = altar.getPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        float volume = WHISPER_VOLUME * closeness(x, y, z);
        if (volume > 0.0f) {
            MinecraftClient.getInstance().getSoundManager().play(new PositionedSoundInstance(
                    ModSounds.ALTAR_WHISPER, SoundCategory.BLOCKS, volume,
                    0.9f + altar.getWorld().random.nextFloat() * 0.2f, SoundInstance.createRandom(), x, y, z));
        }
    }

    /** Called on disconnect (the game stops all sounds then anyway). */
    public static void reset() {
        PLAYING.clear();
    }

    @Override
    public void tick() {
        boolean wanted = !altar.isRemoved()
                && altar.getWorld() == MinecraftClient.getInstance().world
                && altar.isPrimed();
        fade = MathHelper.clamp(fade + (wanted ? FADE_PER_TICK : -FADE_PER_TICK), 0.0f, 1.0f);
        volume = maxVolume * fade * (altar.isRitualActive() ? RITUAL_SWELL : 1.0f) * closeness(x, y, z);
        if (!wanted && fade <= 0.0f) {
            setDone();
            PLAYING.remove(key, this);
        }
    }

    /**
     * 1 at the sound's position, 0 at RANGE blocks away or further. Shared
     * with the altar's whisper so both reach exactly as far.
     */
    public static float closeness(double x, double y, double z) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return 0.0f;
        }
        double distance = Math.sqrt(player.squaredDistanceTo(x, y, z));
        return (float) MathHelper.clamp(1.0 - distance / RANGE, 0.0, 1.0);
    }

    @Override
    public boolean shouldAlwaysPlay() {
        // It starts at volume 0 to fade in; Minecraft skips silent sounds
        // unless they ask to be played anyway.
        return true;
    }
}
