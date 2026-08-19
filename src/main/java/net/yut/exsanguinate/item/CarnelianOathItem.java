package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.yut.exsanguinate.berserk.BerserkMode;
import net.yut.exsanguinate.mixin.LivingEntityAccessor;
import net.yut.exsanguinate.network.InfusionCompletePayload;
import net.yut.exsanguinate.sound.ModSounds;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * The Carnelian Oath - the mod's only sword. It has two mechanics:
 *
 * 1. LIFE DRAIN: every hit costs the player health. "Exsanguinate" means
 *    "to drain of blood" - the sword feeds on its own wielder.
 *
 * 2. INFUSION: hold right click for 2 seconds to "infuse" the sword with
 *    blood (costs 3 hearts). While infused the sword glows like an enchanted
 *    item, and the next hit deals massive bonus damage, then the infusion
 *    is consumed.
 */
public class CarnelianOathItem extends SwordItem {

    // ---- Life drain ----
    // Health is in half-hearts, so 2.0 = 1 heart.
    private static final float LIFE_DRAIN_AMOUNT = 2.0f;

    // ---- Infusion ----
    // 3 hearts = 6.0 health. The player must have MORE than this to infuse,
    // otherwise the ritual would kill them.
    private static final float INFUSION_HEALTH_COST = 6.0f;

    // While berserk the ritual is cheaper: 1 heart, and it only takes half
    // the time (see usageTick, which shortens the use).
    private static final float BERSERK_HEALTH_COST = 2.0f;
    private static final int BERSERK_INFUSION_TIME_TICKS = 20;

    // Bonus damage dealt by an infused hit (8 extra = double the sword's damage).
    private static final float INFUSED_EXTRA_DAMAGE = 8.0f;

    // 2 seconds = 40 ticks (Minecraft runs 20 ticks per second).
    private static final int INFUSION_TIME_TICKS = 40;

    // NBT key that remembers "this sword is currently infused".
    // NBT travels with the item stack, so both client and server see it.
    private static final String INFUSED_KEY = "Infused";

    // ---- Slash particles ----
    private static final double SLASH_RANGE = 8.0;
    private static final int SLASH_LINES = 8;
    private static final int PARTICLES_PER_LINE = 15;
    private static final double LINE_WIDTH = 1.6;
    private static final double TILT_ANGLE = 25.0;

    /**
     * Constructor parameters, explained for learners:
     *
     * - toolMaterial : decides base durability, mining speed, and a base attack
     *                  damage bonus. ToolMaterials.NETHERITE = 2031 durability.
     * - attackDamage : ADDED to the material's base attack damage. Netherite
     *                  gives +3, so this sword deals 3 + 5 = 8 damage per hit
     *                  (the same as a vanilla netherite sword).
     * - attackSpeed  : ADDED to the base value of 4.0. 4.0 + (-3.2) = 0.8
     *                  attacks per second, so it swings very slowly - it is
     *                  a heavy blood ritual blade.
     */
    public CarnelianOathItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    // ======================== INFUSION ========================

    /**
     * Called when the player right-clicks with the sword.
     * We start "using" the item: the player's arm goes into the blocking pose
     * (UseAction.BLOCK, the old sword-blocking animation) and a 2 second
     * timer starts. The blood price is paid when the ritual COMPLETES
     * (finishUsing), so releasing early costs nothing.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Already infused? Refuse - re-infusing would waste blood for nothing.
        if (isInfused(stack)) {
            return TypedActionResult.fail(stack);
        }

        // The price depends on the wielder's state (berserk = cheaper).
        float cost = isBerserk(user, world) ? BERSERK_HEALTH_COST : INFUSION_HEALTH_COST;

        // Refuse if the drain would kill the player - the sword is greedy,
        // but not suicidal. Plays a dull "refusal" sound.
        if (user.getHealth() <= cost) {
            if (!world.isClient) {
                world.playSound(null, user.getBlockPos(), ModSounds.BLOOD_FAIL,
                        SoundCategory.PLAYERS, 0.8f, 1.0f);
            }
            return TypedActionResult.fail(stack);
        }

        // Start the drain: low heartbeat-like thump as the blood flows.
        if (!world.isClient) {
            world.playSound(null, user.getBlockPos(), ModSounds.BLOOD_DRAIN,
                    SoundCategory.PLAYERS, 0.7f, 0.9f);
        }

        // Start using the item (like drawing a bow or raising a shield).
        user.setCurrentHand(hand);
        return TypedActionResult.pass(stack);
    }

    /**
     * How long (in ticks) the use lasts before finishUsing() is called.
     */
    @Override
    public int getMaxUseTime(ItemStack stack) {
        return INFUSION_TIME_TICKS;
    }

    /**
     * BLOCK = the old "blocking" pose: the sword is held in front of the face,
     * exactly like the pre-1.9 blocking animation (now used by shields).
     * It also gives knockback resistance while charging.
     */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    /**
     * Called every tick while the player holds right click.
     * We spawn red particles around the player so it's obvious the infusion
     * is charging. Client-side only, so the effect renders locally and never
     * gets doubled by the server broadcasting it too.
     *
     * On the SERVER side there is a second job: while berserk, the charge is
     * shortened to 1 second. We simply pull the use timer down to the berserk
     * maximum - the ticking loop counts it down from there.
     */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) {
            return;
        }

        // While berserk the infusion only takes half the time: pull the use
        // timer down to 20 ticks (it counts down from there naturally).
        if (isBerserk(user, world)) {
            LivingEntityAccessor accessor = (LivingEntityAccessor) user;
            if (remainingUseTicks > BERSERK_INFUSION_TIME_TICKS) {
                accessor.setItemUseTimeLeft(BERSERK_INFUSION_TIME_TICKS);
            }
        }

        // Particles: client-only, so the effect renders locally and never gets
        // doubled by the server broadcasting it too.
        if (world.isClient) {
            // progress goes from 0.0 (just started) to 1.0 (about to finish).
            // More particles spawn as the charge completes, so you can SEE the progress.
            float progress = 1.0f - (float) remainingUseTicks / INFUSION_TIME_TICKS;
            int particleCount = 1 + (int) (progress * 3);

            for (int i = 0; i < particleCount; i++) {
                Vec3d pos = player.getPos().add(
                        (Math.random() - 0.5) * 1.2,
                        0.3 + Math.random() * 1.6,
                        (Math.random() - 0.5) * 1.2
                );
                // CRIMSON_SPORE is a soft red floating dust - reads as "blood".
                world.addParticle(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 0.0, 0.03, 0.0);
            }
        }
    }

    /**
     * Called when the full 2 seconds are up (only once - and importantly,
     * this method runs on both sides, so all state changes must be guarded
     * with the server check below).
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof PlayerEntity player) {
            ServerWorld serverWorld = (ServerWorld) world;

            // Berserk wielders pay only 1 heart for the ritual.
            float cost = BerserkMode.isActive(player) ? BERSERK_HEALTH_COST : INFUSION_HEALTH_COST;

            if (player.getHealth() > cost) {
                // Pay the blood price and mark the sword as infused.
                player.setHealth(player.getHealth() - cost);
                setInfused(stack, true);

                // The drain completes with a higher, bloodier thump + burst.
                serverWorld.playSound(null, player.getBlockPos(), ModSounds.BLOOD_DRAIN,
                        SoundCategory.PLAYERS, 0.9f, 1.2f);
                spawnParticleBurst(serverWorld, player.getPos().add(0, 1.5, 0), 30);

                // Tell the client "infusion done" so it can flash the screen
                // black & white for a second (ColorFilter).
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    ServerPlayNetworking.send(serverPlayer, new InfusionCompletePayload());
                }
            } else {
                // Not enough blood - the ritual fails with a dull thud.
                spawnParticleBurst(serverWorld, player.getPos().add(0, 1.5, 0), 6);
                serverWorld.playSound(null, player.getBlockPos(), ModSounds.BLOOD_FAIL,
                        SoundCategory.PLAYERS, 0.4f, 1.4f);
            }
        }
        return stack;
    }

    /**
     * Called when the player releases right click BEFORE the 2 seconds are up.
     * Nothing happens - the infusion is only paid for when fully charged.
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        // Released early = cancelled. No blood taken, no infusion.
    }

    // ======================== INFUSED STATE ========================

    /**
     * While infused, the sword glows like an enchanted item (hasGlint controls
     * the sparkle effect). Very easy for the player to see.
     */
    @Override
    public boolean hasGlint(ItemStack stack) {
        return super.hasGlint(stack) || isInfused(stack);
    }

    /**
     * Called every tick while the item is anywhere in the player's inventory.
     * While the infused sword is selected, ambient red particles drift around
     * the player so the charged state is always visible.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient || !selected || !isInfused(stack)) {
            return;
        }

        // Every 20 ticks (1 second), spawn a couple of blood particles.
        if (world.getTime() % 20 == 0) {
            Vec3d pos = entity.getPos().add(
                    (Math.random() - 0.5) * 1.5,
                    0.2 + Math.random() * 1.8,
                    (Math.random() - 0.5) * 1.5
            );
            world.addParticle(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 0.0, 0.02, 0.0);
            world.addParticle(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 0.0, 0.02, 0.0);
        }
    }

    // ======================== HITTING THINGS ========================

    /**
     * Called whenever this item is used to hit an entity (on both sides).
     */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {

        // ---- 0. Infused hit: a charged sword delivers a massive strike ----
        // The infusion is consumed by the first hit.
        boolean infusedHit = !target.getWorld().isClient && isInfused(stack);
        if (infusedHit) {
            setInfused(stack, false);

            ServerWorld serverWorld = (ServerWorld) target.getWorld();

            // Deal the extra damage on top of the normal hit.
            if (attacker instanceof PlayerEntity player) {
                target.damage(serverWorld.getDamageSources().playerAttack(player), INFUSED_EXTRA_DAMAGE);
            }

            // Big blood burst at the target so the hit clearly reads as special.
            spawnParticleBurst(serverWorld, target.getPos().add(0, target.getHeight() * 0.5, 0), 24);
        }

        // ---- 1. Life drain + berserk: these only run on the SERVER ----
        // The server is the "authority" of the world. When the server spawns a
        // particle or plays a sound, Minecraft automatically broadcasts it to
        // all nearby clients. Doing this on the client too would double it.
        if (!target.getWorld().isClient && attacker instanceof PlayerEntity player) {
            ServerWorld serverWorld = (ServerWorld) target.getWorld();

            // Berserk rage: every hit deals bonus damage on top of the normal hit.
            if (BerserkMode.isActive(player)) {
                target.damage(serverWorld.getDamageSources().playerAttack(player), BerserkMode.BONUS_DAMAGE);
            }

            // The sword feeds on the wielder's blood with every hit...
            boolean drained = player.getHealth() > LIFE_DRAIN_AMOUNT;
            if (drained) {
                player.setHealth(player.getHealth() - LIFE_DRAIN_AMOUNT);

                // Infused hits get a deeper, heavier sound.
                float pitch = infusedHit ? 0.6f : 0.9f + serverWorld.random.nextFloat() * 0.2f;
                serverWorld.playSound(null, attacker.getBlockPos(), ModSounds.SWORD_SWING,
                        SoundCategory.PLAYERS, 0.7f, pitch);

                // Spawn the red slash arc in the direction the attacker is looking.
                spawnSlashParticles(serverWorld, attacker);
            } else {
                // ...but if the wielder is too weak, the syphon MISSES:
                // the swing still connects normally, it just takes no blood,
                // and the sword gives a dull "refusal" thud instead.
                serverWorld.playSound(null, attacker.getBlockPos(), ModSounds.BLOOD_FAIL,
                        SoundCategory.PLAYERS, 0.8f, 1.2f);
                spawnParticleBurst(serverWorld, player.getPos().add(0, 1.5, 0), 4);
            }
        }

        // Let the vanilla sword logic run too (durability damage, sweep attack...).
        return super.postHit(stack, target, attacker);
    }

    // ======================== TOOLTIP ========================

    /**
     * The description under the item name in the inventory.
     * While infused, it shows exactly how much damage the next hit will deal.
     */
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (isInfused(stack)) {
            // Example of the damage the infused blade will yield:
            // normal sword damage + the infused bonus.
            float damage = getAttackDamage() + INFUSED_EXTRA_DAMAGE;
            tooltip.add(Text.translatable("item.exsanguinate.carnelian_oath.tooltip.infused",
                    String.format("%.1f", damage)).formatted(Formatting.RED));
        } else {
            tooltip.add(Text.translatable("item.exsanguinate.carnelian_oath.tooltip.hint").formatted(Formatting.GRAY));
        }
    }

    // ======================== HELPERS ========================

    /**
     * Berserk check that also works from the client side. The combat
     * timestamps only exist on the server, so the client approximates the
     * state with the low-health check alone. That keeps client and server in
     * agreement about costs and timers (minor cosmetic differences are fine).
     */
    private static boolean isBerserk(LivingEntity user, World world) {
        if (!(user instanceof PlayerEntity player)) {
            return false;
        }
        return BerserkMode.isActive(player)
                || (world.isClient && player.getHealth() <= BerserkMode.TRIGGER_HEALTH);
    }

    /**
     * Reads the "infused" flag from the item's NBT.
     * hasNbt() check avoids creating an empty NBT compound on every read.
     */
    private static boolean isInfused(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean(INFUSED_KEY);
    }

    /**
     * Writes the "infused" flag into the item's NBT.
     */
    private static void setInfused(ItemStack stack, boolean infused) {
        stack.getOrCreateNbt().putBoolean(INFUSED_KEY, infused);
    }

    /**
     * Spawns an explosion of red particles (crimson spores + bright sparks)
     * at a position - used for the infusion ritual and the infused hit.
     */
    private void spawnParticleBurst(ServerWorld world, Vec3d pos, int count) {
        for (int i = 0; i < count; i++) {
            world.spawnParticles(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 1,
                    (Math.random() - 0.5) * 0.3,
                    Math.random() * 0.4 + 0.1,
                    (Math.random() - 0.5) * 0.3,
                    0.1);
        }
        for (int i = 0; i < count / 2; i++) {
            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 1,
                    (Math.random() - 0.5) * 0.5,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * 0.5,
                    0.05);
        }
    }

    /**
     * Spawns a tilted arc of particles in front of the attacker, like a slash.
     *
     * How it works:
     *  1. Find where the attacker is looking (direction + eye position).
     *  2. Calculate a direction perpendicular to the look direction (to the side).
     *  3. Tilt that perpendicular 25 degrees up or down, so the arc is slanted.
     *  4. Draw SLASH_LINES particle rows, each one further away, forming a
     *     slanted "wall" of particles that reads as a sword slash.
     */
    private void spawnSlashParticles(ServerWorld world, LivingEntity attacker) {
        Vec3d lookDirection = attacker.getRotationVec(1.0f).normalize();
        Vec3d eyePosition = attacker.getPos().add(0, attacker.getEyeHeight(attacker.getPose()), 0);

        // Sideways direction, perpendicular to where the attacker is looking.
        Vec3d horizontalPerpendicular = lookDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();

        // Randomly tilt the arc up or down so every hit looks slightly different.
        double randomTiltDirection = Math.random() > 0.5 ? 1.0 : -1.0;
        double tiltAngleRadians = Math.toRadians(TILT_ANGLE) * randomTiltDirection;

        double cosTilt = Math.cos(tiltAngleRadians);
        double sinTilt = Math.sin(tiltAngleRadians);
        Vec3d tiltedPerpendicular = new Vec3d(
                horizontalPerpendicular.x * cosTilt - horizontalPerpendicular.z * sinTilt,
                horizontalPerpendicular.y,
                horizontalPerpendicular.x * sinTilt + horizontalPerpendicular.z * cosTilt
        );

        double spacingBetweenLines = SLASH_RANGE / SLASH_LINES;

        for (int lineIndex = 0; lineIndex < SLASH_LINES; lineIndex++) {
            // Center of this row: start at the eye, then move forward in the look direction.
            double distanceFromAttacker = lineIndex * spacingBetweenLines;
            Vec3d lineCenter = eyePosition.add(lookDirection.multiply(distanceFromAttacker));

            // Spread PARTICLES_PER_LINE particles from side to side across the row.
            for (int i = 0; i < PARTICLES_PER_LINE; i++) {
                double offsetAlongLine = (Math.random() - 0.5) * LINE_WIDTH;

                double particleX = lineCenter.x + tiltedPerpendicular.x * offsetAlongLine;
                double particleY = lineCenter.y;
                double particleZ = lineCenter.z + tiltedPerpendicular.z * offsetAlongLine;

                // spawnParticles(x, y, z, count, velocityX, velocityY, velocityZ, speed).
                // FIREWORK is a bright spark that fades quickly - a nice "blood magic" slash.
                world.spawnParticles(ParticleTypes.FIREWORK, particleX, particleY, particleZ, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}