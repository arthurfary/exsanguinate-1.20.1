package net.yut.exsanguinate.berserk;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.yut.exsanguinate.item.ModItems;
import net.yut.exsanguinate.network.BerserkStatePayload;
import net.yut.exsanguinate.particle.ModParticles;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BERSERK MODE - the "rage" state the sword puts its wielder in at low health.
 *
 * While the player is at 4 hearts or less AND was in combat recently
 * (dealt or took damage within the last 10 seconds), they are berserk:
 *
 *  - max health is capped at 4 hearts (with saturation, so they regen up to 4)
 *  - every hit deals bonus damage
 *  - the infusion ritual only costs 1 heart and takes half the time
 *  - they leave bloody footprints, so other players can tell
 *  - their own screen gets a blood vignette (client side, BerserkVignette)
 *
 * The state is computed from two things:
 *  1. the player's health (easy, just read it)
 *  2. "recently in combat" - a timestamp stamped by the LivingEntityMixin
 *     (damage taken) and PlayerEntityMixin (damage dealt) mixins.
 *
 * This class is pure server-side logic (the mixins only stamp on the server),
 * but the checks themselves are harmless to call from anywhere.
 */
public final class BerserkMode {

    // Health at or below which berserk turns on (8.0 = 4 hearts).
    public static final float TRIGGER_HEALTH = 8.0f;

    // Cap applied to the max-health attribute while berserk (4 hearts).
    private static final float CAP_HEALTH = 8.0f;

    // How long berserk lasts after the last combat event (200 ticks = 10 s).
    private static final int AWAY_FROM_COMBAT_TICKS = 200;

    // The vanilla default max health, restored when berserk ends.
    private static final double DEFAULT_MAX_HEALTH = 20.0;

    // Extra damage per hit while berserk.
    public static final float BONUS_DAMAGE = 4.0f;

    // Player uuid -> entity age of their last combat event (dealt or took damage).
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

    // ---- Blood footprints (so everyone can see who is berserk) ----
    // Distance walked between two prints, in blocks.
    private static final double STRIDE = 0.8;

    // How far each foot lands from the walking line (left/right), in blocks.
    private static final double FOOT_OFFSET = 0.15;

    // Where a berserk player left their last print, and with which foot.
    private record Footstep(Vec3d pos, boolean left) {
    }

    private static final Map<UUID, Footstep> LAST_FOOTSTEP = new HashMap<>();

    // Players whose client was last told "you are berserk" (see tick).
    private static final Set<UUID> BERSERK_PLAYERS = new HashSet<>();

    private BerserkMode() {
    }

    /**
     * Called when a player disconnects. Drops everything we remember about
     * them, so a rejoin starts clean: their client starts with no vignette,
     * and their combat timestamp (an entity age, which restarts at 0 on
     * rejoin) can't keep them berserk by mistake.
     */
    public static void forget(PlayerEntity player) {
        LAST_COMBAT.remove(player.getUuid());
        LAST_FOOTSTEP.remove(player.getUuid());
        BERSERK_PLAYERS.remove(player.getUuid());
    }

    /**
     * Called by the mixins whenever the player deals or takes damage.
     * Stamps the current entity age so isActive() knows combat just happened.
     */
    public static void onCombat(PlayerEntity player) {
        LAST_COMBAT.put(player.getUuid(), (long) player.age);
    }

    /**
     * Is this player currently berserk?
     * Wielding the sword AND low health AND in combat within the last 10 seconds.
     * (Without the sword check, any player at low health would get their max
     * health capped, sword or not.)
     */
    public static boolean isActive(PlayerEntity player) {
        if (!player.getMainHandStack().isOf(ModItems.CARNELIAN_OATH) || player.getHealth() > TRIGGER_HEALTH) {
            return false;
        }
        Long lastCombat = LAST_COMBAT.get(player.getUuid());
        return lastCombat != null && player.age - lastCombat < AWAY_FROM_COMBAT_TICKS;
    }

    /**
     * Called every tick for every player (from PlayerEntityMixin).
     * Applies the berserk effects while active, and restores the player
     * when it wears off.
     */
    public static void tick(PlayerEntity player) {
        // Only the server may change attributes, effects and health.
        if (player.getWorld().isClient) {
            return;
        }

        boolean active = isActive(player);

        // Tell the player's client when berserk starts or ends, so it can fade
        // the blood vignette. add/remove return true only when the set actually
        // changed, i.e. exactly on the tick the state flips.
        if (player instanceof ServerPlayerEntity serverPlayer
                && (active ? BERSERK_PLAYERS.add(player.getUuid()) : BERSERK_PLAYERS.remove(player.getUuid()))) {
            ServerPlayNetworking.send(serverPlayer, new BerserkStatePayload(active));
        }

        if (active) {
            // Cap max health at 4 hearts so healing (and the saturation below)
            // can never take the player above 4 hearts while berserk.
            if (player.getMaxHealth() != CAP_HEALTH) {
                var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (attribute != null) {
                    attribute.setBaseValue(CAP_HEALTH);
                }
            }

            // Never above the cap, even if health was higher when berserk kicked in.
            player.setHealth(Math.min(player.getHealth(), CAP_HEALTH));

            // Saturation regenerates the player back up to the 4-heart cap.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 20, 0));

            leaveFootprint(player);
        } else {
            // Not berserk: forget the trail, so the next rage starts a fresh one.
            LAST_FOOTSTEP.remove(player.getUuid());

            if (player.getMaxHealth() != DEFAULT_MAX_HEALTH) {
                // Berserk wore off - give the player their full health bar back.
                var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (attribute != null) {
                    attribute.setBaseValue(DEFAULT_MAX_HEALTH);
                }
            }
        }
    }

    /**
     * Drops a bloody footprint every STRIDE blocks a berserk player walks.
     * The server spawns them, so every nearby player sees them: they are the
     * "this one is berserk" warning. Prints alternate left/right feet and
     * point the way the player walked. Jumping and swimming leave none.
     */
    private static void leaveFootprint(PlayerEntity player) {
        Vec3d pos = player.getPos();
        Footstep last = LAST_FOOTSTEP.get(player.getUuid());
        if (last == null) {
            LAST_FOOTSTEP.put(player.getUuid(), new Footstep(pos, false));
            return;
        }

        Vec3d walked = new Vec3d(pos.x - last.pos().x, 0.0, pos.z - last.pos().z);
        if (walked.lengthSquared() < STRIDE * STRIDE) {
            return;
        }

        if (player.isOnGround() && !player.isTouchingWater() && player.getWorld() instanceof ServerWorld world) {
            Vec3d direction = walked.normalize();
            // (-z, x) is the walking direction turned 90 degrees: sideways.
            double side = last.left() ? -FOOT_OFFSET : FOOT_OFFSET;
            // count 0 = the three "velocity" numbers reach the client as-is.
            // The footprint reads the first one as its rotation (see
            // BloodFootprintParticle). 0.02 up stops it flickering into the floor.
            world.spawnParticles(ModParticles.BLOOD_FOOTPRINT,
                    pos.x - direction.z * side, pos.y + 0.02, pos.z + direction.x * side,
                    0, Math.atan2(-direction.x, -direction.z), 0.0, 0.0, 1.0);
        }
        LAST_FOOTSTEP.put(player.getUuid(), new Footstep(pos, !last.left()));
    }
}