package net.yut.exsanguinate.block.entity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.yut.exsanguinate.network.RitualCompletePayload;
import net.yut.exsanguinate.ritual.AltarRitual;
import net.yut.exsanguinate.ritual.Rituals;
import net.yut.exsanguinate.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the Blood Altar remembers: the items lying on it, and how far along
 * its ritual is.
 *
 * THE RITUAL (RITUAL_TICKS = 4 seconds), started by Rituals when a player
 * kills a creature nearby:
 *
 *   0.0 s  the victim's blood leaps from the corpse to the altar in an arc,
 *          a deep hum and a heartbeat sound
 *   0-3 s  the offering rises, spins faster and faster and draws together,
 *          a spiral of blood closes in around it, the heartbeat quickens
 *   3-4 s  the items tremble, shrink into each other and vanish
 *   4.0 s  no flash: black ink and a thick cloud of smoke spill from the
 *          altar, a ring of blood stains the floor, a deep thud shakes the
 *          camera and the light dies for a moment (Darkness) - for those
 *          standing close.
 *          The forged item floats above the altar, ready to be taken.
 *
 * Before and during the ritual a primed altar also drones and, now and
 * then, whispers - only within the sacrifice radius (client side:
 * client/AltarHumSound).
 *
 * WHO DOES WHAT: the SERVER owns the truth (items, ritual timer, result,
 * sounds, the big burst). Whenever that changes it SYNCS the block entity
 * to clients (sync -> toUpdatePacket -> readNbt on the client). The CLIENT
 * then runs its own copy of the timer every tick, only to animate: the
 * floating items (BloodAltarRenderer) and the particles (clientTick). That
 * way the server sends two packets per ritual instead of one per tick.
 */
public class BloodAltarBlockEntity extends BlockEntity {

    // How many items fit on the altar.
    public static final int SLOTS = 4;

    // Length of the ritual (80 ticks = 4 s).
    public static final int RITUAL_TICKS = 80;

    // How long the blood takes to fly from the corpse to the altar.
    private static final int STREAM_TICKS = 24;

    // Only players this close witness the end: the thud and the darkness.
    public static final double WITNESS_RADIUS = 12.0;

    // How long the light dies for the witnesses (60 ticks = 3 s). 0 = never.
    private static final int DARKNESS_TICKS = 60;

    // A primed altar whispers on average once every this many ticks (~25 s).
    private static final int WHISPER_CHANCE = 500;

    // Where the offering floats: the top of the altar (14 px) and a bit above.
    private static final double OFFERING_HEIGHT = 1.15;

    // Particle colors: fresh blood for the stream, a darker clot for the spiral.
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.6f, 0.02f, 0.02f), 1.4f);
    private static final DustParticleEffect DARK_BLOOD = new DustParticleEffect(new Vector3f(0.3f, 0.0f, 0.02f), 1.0f);

    // NBT keys.
    private static final String RITUAL_KEY = "RitualTicks";
    private static final String BLOOD_KEY = "BloodSource";

    // The offering, one item per slot. Slots fill from the front.
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SLOTS, ItemStack.EMPTY);

    // 0 = idle, 1..RITUAL_TICKS = the ritual is running.
    private int ritualTicks = 0;

    // Where the sacrifice died (the blood stream starts there). Only set
    // while a ritual runs.
    @Nullable
    private Vec3d bloodSource = null;

    /**
     * Client only: called every tick while this altar is primed. The client
     * entrypoint plugs in the drone (client/AltarHumSound::keepAlive). It is
     * a hook instead of a direct call because this class runs on servers
     * too, where client-only classes like sound instances don't exist.
     */
    public static Consumer<BloodAltarBlockEntity> clientHum = altar -> {
    };

    // Client only, same idea: the rare whisper (plugged in by ExsanguinateClient,
    // so it can fade with distance exactly like the drone).
    public static Consumer<BloodAltarBlockEntity> clientWhisper = altar -> {
    };

    public BloodAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLOOD_ALTAR, pos, state);
    }

    // ======================== THE OFFERING ========================

    /** The items on the altar, in the order they were placed (no empty slots). */
    public List<ItemStack> getOffering() {
        List<ItemStack> offering = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                offering.add(stack);
            }
        }
        return offering;
    }

    /** All slots, empty ones included - used to drop everything when broken. */
    public DefaultedList<ItemStack> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    public boolean isFull() {
        return items.stream().noneMatch(ItemStack::isEmpty);
    }

    /** Lays one item on the altar (server side). */
    public void addItem(ItemStack stack) {
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack);
                sync();
                return;
            }
        }
    }

    /** Takes back the last item laid on the altar (server side). */
    public ItemStack takeLastItem() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
                sync();
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // ======================== THE RITUAL ========================

    public boolean isRitualActive() {
        return ritualTicks > 0;
    }

    /** The ritual the current offering would perform, or null. */
    @Nullable
    public AltarRitual findRitual() {
        return Rituals.find(getOffering());
    }

    /** Idle with a valid offering: a death nearby would start the ritual. */
    public boolean canStartRitual() {
        return !isRitualActive() && findRitual() != null;
    }

    /** Waiting for blood, or drinking it: the altar drones. */
    public boolean isPrimed() {
        return isRitualActive() || findRitual() != null;
    }

    /**
     * 0.0 at the start of the ritual, 1.0 at the end (0 when idle).
     * tickDelta (0..1, how far we are into the current tick) makes the
     * animation smooth between ticks.
     */
    public float getRitualProgress(float tickDelta) {
        if (ritualTicks <= 0) {
            return 0.0f;
        }
        return MathHelper.clamp((ritualTicks + tickDelta) / RITUAL_TICKS, 0.0f, 1.0f);
    }

    /**
     * Called by Rituals when a creature dies near this altar.
     * {@code blood} is where it died: the blood stream flies from there.
     */
    public void startRitual(ServerWorld world, Vec3d blood) {
        ritualTicks = 1;
        bloodSource = blood;

        // A burst of gore where the victim fell, and the opening chord:
        // the soul leaving, a deep hum, one slow heartbeat. Volumes stay at
        // or below 1.0 so nothing carries past ~16 blocks.
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                blood.x, blood.y, blood.z, 30, 0.3, 0.3, 0.3, 0.15);
        world.playSound(null, blood.x, blood.y, blood.z, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 0.5f, 0.5f);
        world.playSound(null, pos, ModSounds.BLOOD_DRAIN, SoundCategory.BLOCKS, 0.8f, 0.5f);

        sync();
    }

    /** Runs every tick on both sides (see BloodAltarBlock.getTicker). */
    public static void tick(World world, BlockPos pos, BlockState state, BloodAltarBlockEntity altar) {
        if (world instanceof ServerWorld serverWorld) {
            altar.serverTick(serverWorld);
        } else {
            altar.clientTick(world);
        }
    }

    private void serverTick(ServerWorld world) {
        if (ritualTicks <= 0) {
            return;
        }
        ritualTicks++;
        float progress = (float) ritualTicks / RITUAL_TICKS;

        // The heartbeat quickens: every 14 ticks, then 8, then 4.
        int beat = ritualTicks < 30 ? 14 : ritualTicks < 55 ? 8 : 4;
        if (ritualTicks % beat == 0) {
            world.playSound(null, pos, ModSounds.BLOOD_DRAIN, SoundCategory.BLOCKS,
                    0.5f + progress * 0.4f, 0.5f + progress * 0.6f);
        }
        // One second before the end the altar "inhales".
        if (ritualTicks == RITUAL_TICKS - 20) {
            world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.6f, 0.5f);
        }

        if (ritualTicks >= RITUAL_TICKS) {
            completeRitual(world);
        }
    }

    /** The end of the ritual: the offering becomes the result. */
    private void completeRitual(ServerWorld world) {
        // Check again, in case the offering changed somehow (commands...).
        AltarRitual ritual = findRitual();
        ritualTicks = 0;
        bloodSource = null;

        Vec3d top = Vec3d.ofBottomCenter(pos).add(0.0, OFFERING_HEIGHT, 0.0);
        if (ritual == null) {
            // Nothing to forge: the blood just fizzles out.
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 12, 0.2, 0.2, 0.2, 0.02);
            world.playSound(null, pos, ModSounds.BLOOD_FAIL, SoundCategory.BLOCKS, 1.0f, 0.8f);
            sync();
            return;
        }

        items.clear();
        items.set(0, new ItemStack(ritual.result()));

        // The finale is dark, not bright: black ink spills out of the
        // forged item, a thick cloud of smoke rolls off it, spores drift and
        // a ring of blood stains the floor around the altar.
        world.spawnParticles(ParticleTypes.SQUID_INK, top.x, top.y, top.z, 30, 0.25, 0.25, 0.25, 0.03);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 32, 0.4, 0.35, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 0.4, top.z, 8, 0.15, 0.1, 0.15, 0.06);
        world.spawnParticles(ParticleTypes.CRIMSON_SPORE, top.x, top.y, top.z, 40, 0.6, 0.5, 0.6, 0.02);
        bloodRing(world);

        // One deep, heavy thud: the Warden's punch and a muffled boom, both
        // at the lowest pitch, under the wet bloom and the heartbeat.
        world.playSound(null, pos, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.35f, 0.5f);
        world.playSound(null, pos, ModSounds.BLOOD_DRAIN, SoundCategory.BLOCKS, 0.9f, 0.5f);
        world.playSound(null, pos, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.BLOCKS, 1.0f, 0.5f);
        world.playSound(null, pos, SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.BLOCKS, 0.8f, 0.5f);

        // Only those standing close feel it: a camera thud, and the light
        // dies for a few seconds (the Warden's Darkness, without an icon).
        for (ServerPlayerEntity player : PlayerLookup.around(world, Vec3d.ofCenter(pos), WITNESS_RADIUS)) {
            if (DARKNESS_TICKS > 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, DARKNESS_TICKS, 0, true, false, false));
            }
            ServerPlayNetworking.send(player, new RitualCompletePayload(pos));
        }

        sync();
    }

    /** A ring of dark blood on the floor around the altar. */
    private void bloodRing(ServerWorld world) {
        int points = 28;
        double radius = 1.4;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points;
            world.spawnParticles(DARK_BLOOD,
                    pos.getX() + 0.5 + Math.cos(angle) * radius, pos.getY() + 0.1, pos.getZ() + 0.5 + Math.sin(angle) * radius,
                    1, 0.05, 0.0, 0.05, 0.0);
        }
    }

    /**
     * Client side: advance our copy of the timer and draw the particles.
     * The client never ends the ritual itself - it waits for the server's
     * sync, which also brings the forged item.
     */
    private void clientTick(World world) {
        Random random = world.random;
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double top = pos.getY() + OFFERING_HEIGHT;

        if (!isPrimed()) {
            return;
        }
        // Primed: drone (and keep droning through the ritual).
        clientHum.accept(this);

        if (ritualTicks <= 0) {
            // Waiting for blood: spores seep from the basin, and now and
            // then the stone whispers - quietly, only for those close by.
            if (world.getTime() % 6 == 0) {
                world.addParticle(ParticleTypes.CRIMSON_SPORE,
                        cx + (random.nextDouble() - 0.5) * 0.7, pos.getY() + 0.95, cz + (random.nextDouble() - 0.5) * 0.7,
                        0.0, 0.02, 0.0);
            }
            if (random.nextInt(WHISPER_CHANCE) == 0) {
                clientWhisper.accept(this);
            }
            return;
        }

        if (ritualTicks < RITUAL_TICKS) {
            ritualTicks++;
        }
        float progress = (float) ritualTicks / RITUAL_TICKS;

        // 1. The blood stream: a short ribbon racing along an arc from the
        //    corpse to the altar. Each tick we drop a few droplets at the head
        //    of the ribbon and just behind it; they linger and fade by themselves.
        if (bloodSource != null && ritualTicks <= STREAM_TICKS) {
            Vec3d target = new Vec3d(cx, top, cz);
            float head = (float) ritualTicks / STREAM_TICKS;
            for (int i = 0; i < 6; i++) {
                float t = head - i * 0.03f;
                if (t < 0.0f) {
                    break;
                }
                Vec3d at = arc(bloodSource, target, t);
                world.addParticle(BLOOD,
                        at.x + (random.nextDouble() - 0.5) * 0.1,
                        at.y + (random.nextDouble() - 0.5) * 0.1,
                        at.z + (random.nextDouble() - 0.5) * 0.1,
                        0.0, 0.0, 0.0);
            }
        }

        // 2. Three arms of a blood spiral, closing in and rising as the
        //    ritual builds, and spinning faster.
        double radius = 1.5 * (1.0 - progress) + 0.15;
        double spin = ritualTicks * (0.25 + progress * 0.45);
        for (int arm = 0; arm < 3; arm++) {
            double angle = spin + arm * (Math.PI * 2.0 / 3.0);
            world.addParticle(DARK_BLOOD,
                    cx + Math.cos(angle) * radius, pos.getY() + 0.9 + progress * 0.9, cz + Math.sin(angle) * radius,
                    0.0, 0.0, 0.0);
        }

        // 3. Spores rising off the stone, more and more of them.
        if (random.nextFloat() < 0.3f + progress) {
            world.addParticle(ParticleTypes.CRIMSON_SPORE,
                    cx + (random.nextDouble() - 0.5) * 1.2, pos.getY() + 0.9, cz + (random.nextDouble() - 0.5) * 1.2,
                    0.0, 0.05 + progress * 0.1, 0.0);
        }

        // 4. The last second: the offering smokes as it melts together.
        if (progress > 0.75f && random.nextFloat() < 0.5f) {
            world.addParticle(ParticleTypes.SMOKE,
                    cx + (random.nextDouble() - 0.5) * 0.3, top + 0.6, cz + (random.nextDouble() - 0.5) * 0.3,
                    0.0, 0.03, 0.0);
        }
    }

    /**
     * A point along a curved arc from {@code from} to {@code to}: t = 0 is the
     * start, t = 1 the end. It's a quadratic Bezier curve whose middle control
     * point is lifted above the halfway point, so the blood leaps up and over
     * instead of sliding along the ground.
     */
    private static Vec3d arc(Vec3d from, Vec3d to, float t) {
        Vec3d mid = from.lerp(to, 0.5).add(0.0, 1.0 + from.distanceTo(to) * 0.25, 0.0);
        Vec3d a = from.lerp(mid, t);
        Vec3d b = mid.lerp(to, t);
        return a.lerp(b, t);
    }

    // ======================== SAVING & SYNCING ========================

    /** Saves the altar into the world file (and into sync packets). */
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putInt(RITUAL_KEY, ritualTicks);
        if (bloodSource != null) {
            NbtCompound blood = new NbtCompound();
            blood.putDouble("X", bloodSource.x);
            blood.putDouble("Y", bloodSource.y);
            blood.putDouble("Z", bloodSource.z);
            nbt.put(BLOOD_KEY, blood);
        }
    }

    /** Loads the altar from the world file, or (client) from a sync packet. */
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        // readNbt only fills the slots it finds, so clear first: otherwise an
        // item taken off the altar would stay visible on the client.
        items.clear();
        Inventories.readNbt(nbt, items);
        ritualTicks = nbt.getInt(RITUAL_KEY);
        if (nbt.contains(BLOOD_KEY)) {
            NbtCompound blood = nbt.getCompound(BLOOD_KEY);
            bloodSource = new Vec3d(blood.getDouble("X"), blood.getDouble("Y"), blood.getDouble("Z"));
        } else {
            bloodSource = null;
        }
    }

    /** The packet sent to clients when we call sync(): our whole NBT. */
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** What clients get when the chunk first loads for them. */
    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    /** Marks the altar for saving and pushes its new state to every nearby client. */
    private void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }
}
