package net.yut.exsanguinate.berserk;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
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

    private BerserkMode() {
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
     * Low health AND in combat within the last 10 seconds.
     */
    public static boolean isActive(PlayerEntity player) {
        if (player.getHealth() > TRIGGER_HEALTH) {
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

        if (isActive(player)) {
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
        } else if (player.getMaxHealth() != DEFAULT_MAX_HEALTH) {
            // Berserk wore off - give the player their full health bar back.
            var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(DEFAULT_MAX_HEALTH);
            }
        }
    }
}