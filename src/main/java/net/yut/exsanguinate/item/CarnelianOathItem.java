package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.yut.exsanguinate.berserk.BerserkMode;
import net.yut.exsanguinate.effect.ModEffects;
import net.yut.exsanguinate.mixin.LivingEntityAccessor;
import net.yut.exsanguinate.network.InfusionCompletePayload;
import net.yut.exsanguinate.sound.ModSounds;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * The Carnelian Oath - a heavy blade that is only sharp when it is fed.
 *
 * 1. BLOOD STRIKE: the blade itself is dull (see ModItems). A full-power
 *    swing pays 1 heart and, in exchange, bites for a lot of extra damage.
 *    Too weak to pay (1 heart or less)? The blade refuses and stays dull.
 *
 * 2. INFUSION: hold right click for 2 seconds to pay 3 hearts up front.
 *    The next full swing is a crushing blow: far more damage, a huge shove
 *    and a violent camera kick. The infusion is consumed by that swing.
 *
 * Every fed hit is made to feel heavy: knockback, a low thud, a spray of
 * blood, a camera kick (client side, see ScreenShake) and a short recovery
 * slowdown for the wielder, who is committed to the swing.
 */
public class CarnelianOathItem extends SwordItem {

    // ---- Blood strike ----
    // Health is in half-hearts, so 2.0 = 1 heart.
    private static final float BLOOD_COST = 2.0f;

    // Extra damage of a fed swing (the blade alone deals ~5, so 15 fed).
    private static final float BLOOD_BONUS = 10.0f;

    // A swing only counts as a committed "full swing" (and only then feeds
    // the blade) once the attack cooldown is this charged. Spam-clicking
    // gets you the dull, weak hit and costs nothing.
    public static final float READY = 0.9f;

    // ---- Infusion ----
    // 3 hearts = 6.0 health. The player must have MORE than this to infuse,
    // otherwise the ritual would kill them.
    private static final float INFUSION_HEALTH_COST = 6.0f;

    // While berserk the ritual is cheaper: 1 heart, and it only takes half
    // the time (see usageTick, which shortens the use).
    private static final float BERSERK_HEALTH_COST = 2.0f;
    private static final int BERSERK_INFUSION_TIME_TICKS = 20;

    // Extra damage of the infused swing. It replaces the blood strike bonus
    // (the blood was already paid by the ritual, so the swing is free).
    private static final float INFUSED_BONUS = 24.0f;

    // 2 seconds = 40 ticks (Minecraft runs 20 ticks per second).
    private static final int INFUSION_TIME_TICKS = 40;

    // After paying blood, natural healing stops for this long (100 = 5 s).
    // Every payment restarts it, so it stays open for a whole fight.
    private static final int WOUND_TICKS = 100;

    // NBT key that remembers "this sword is currently infused".
    // NBT travels with the item stack, so both client and server see it.
    private static final String INFUSED_KEY = "Infused";

    // ---- Weight (tweak these to change how the hits feel) ----
    // Loudness of the whole impact sound stack (1.0 = full, 0.5 = half).
    private static final float IMPACT_VOLUME = 0.6f;

    // Extra shove on a fed hit, on top of vanilla knockback.
    private static final double KNOCKBACK = 0.9;
    private static final double INFUSED_KNOCKBACK = 2.0;

    // The wielder is slowed briefly after a fed hit: heavy blades have
    // recovery. Set to 0 to disable.
    private static final int RECOVERY_TICKS = 10;
    private static final int INFUSED_RECOVERY_TICKS = 20;

    // Id of the temporary attack-damage modifier that carries the bonus into
    // the vanilla hit (see register). Only exists for the length of one swing.
    private static final UUID STRIKE_ID = UUID.fromString("5c1f6a3e-8b0d-4e47-9a52-3d7b1c0e9f24");

    /**
     * Constructor parameters, explained for learners:
     *
     * - toolMaterial : decides base durability, mining speed, and a base attack
     *                  damage bonus. ToolMaterials.NETHERITE = 2031 durability
     *                  and +4 damage.
     * - attackDamage : ADDED to the material's base attack damage. Netherite
     *                  gives +4, so the blade alone deals 4 + 0 = 4 (5 with the
     *                  player's own 1). Everything above that is paid in blood.
     * - attackSpeed  : ADDED to the base value of 4.0. 4.0 + (-3.2) = 0.8
     *                  attacks per second, so it swings very slowly - it is
     *                  a heavy blood ritual blade.
     */
    public CarnelianOathItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    // ======================== BLOOD STRIKE ========================

    /**
     * Called once from ModItems.registerModItems().
     *
     * Why an event + a temporary attribute? Vanilla works out the hit's damage
     * from the attacker's attack-damage attribute BEFORE it hits. If we tried to
     * add damage afterwards (in postHit), the target's invulnerability frames
     * would swallow it. So just before the swing resolves we hand the player a
     * temporary damage bonus; vanilla then does all the rest (cooldown scaling,
     * critical hits, armor...). postHit removes it again, and so does
     * inventoryTick as a safety net for swings that never landed.
     */
    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getMainHandStack();
            if (world.isClient || !(entity instanceof LivingEntity) || !stack.isOf(ModItems.CARNELIAN_OATH)
                    || player.getAttackCooldownProgress(0.5f) < READY) {
                return ActionResult.PASS;
            }

            float bonus = bloodBonus(player, stack) + (isBerserk(player, world) ? BerserkMode.BONUS_DAMAGE : 0.0f);
            if (bonus > 0.0f) {
                clearStrike(player);
                player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).addTemporaryModifier(
                        new EntityAttributeModifier(STRIKE_ID, "Blood strike", bonus, EntityAttributeModifier.Operation.ADDITION));
            }
            return ActionResult.PASS;
        });
    }

    /**
     * How much extra damage the blood buys right now: the infused bonus,
     * the normal bonus if the wielder can afford the heart, or nothing.
     * Public because the client uses it too (to decide on the camera kick).
     */
    public static float bloodBonus(PlayerEntity player, ItemStack stack) {
        if (isInfused(stack)) {
            return INFUSED_BONUS;
        }
        return player.getHealth() > BLOOD_COST ? BLOOD_BONUS : 0.0f;
    }

    /** Removes the temporary damage bonus. Returns true if it was there. */
    private static boolean clearStrike(PlayerEntity player) {
        // Not tryRemoveModifier: that one only removes PERSISTENT modifiers.
        EntityAttributeInstance attackDamage = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attackDamage.getModifier(STRIKE_ID) == null) {
            return false;
        }
        attackDamage.removeModifier(STRIKE_ID);
        return true;
    }

    /**
     * Takes the blood. The wound stays open for a while (Blood Curse): no
     * natural healing until it closes, so the price actually sticks instead
     * of being healed back by a full food bar a second later.
     */
    private static void payBlood(PlayerEntity player, float cost) {
        player.setHealth(player.getHealth() - cost);
        player.addStatusEffect(new StatusEffectInstance(ModEffects.BLOOD_CURSE, WOUND_TICKS, 0));
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

        // Start using the item (like drawing a bow or raising a shield).
        // consume() claims the click so the off hand cannot start its own use
        // and cancel the charge. The heartbeat starts on the first usageTick.
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
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
     * It also slows the wielder to a crawl while charging.
     */
    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    /**
     * Called every tick while the player holds right click.
     *
     * Server side: a heartbeat thump that gets louder, higher and faster as the
     * charge builds, so the ritual feels like the blood is being pulled out.
     * Client side: red particles around the player (rendered locally, so they
     * never get doubled by the server broadcasting them too).
     *
     * While berserk, the charge is also shortened to 1 second. We simply pull
     * the use timer down to the berserk maximum - the ticking loop counts it
     * down from there.
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

        // progress goes from 0.0 (just started) to 1.0 (about to finish).
        float progress = 1.0f - (float) remainingUseTicks / INFUSION_TIME_TICKS;

        if (!world.isClient) {
            // One thump every 10 ticks, every 5 once the charge is half done.
            int elapsed = INFUSION_TIME_TICKS - remainingUseTicks;
            if (elapsed % (elapsed < 20 ? 10 : 5) == 0) {
                world.playSound(null, player.getBlockPos(), ModSounds.BLOOD_DRAIN,
                        SoundCategory.PLAYERS, 0.5f + progress * 0.4f, 0.7f + progress * 0.6f);
            }
        } else {
            // More particles spawn as the charge completes, so you can SEE the progress.
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
                payBlood(player, cost);
                setInfused(stack, true);

                // The drain completes with a higher, bloodier thump + burst,
                // and a short "charged" cue so you know the blade is ready.
                serverWorld.playSound(null, player.getBlockPos(), ModSounds.BLOOD_DRAIN,
                        SoundCategory.PLAYERS, 0.9f, 1.2f);
                serverWorld.playSound(null, player.getBlockPos(), ModSounds.INFUSION_CHARGED,
                        SoundCategory.PLAYERS, 0.8f, 1.0f);
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

    /** The name is bold dark red, wherever the sword is named (tooltip, hotbar, chat). */
    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.DARK_RED, Formatting.BOLD);
    }

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
     *
     * Server: drops a leftover blood strike bonus, in case a swing never
     * landed (the target was invulnerable) and postHit never ran.
     * Client: while the infused sword is selected, ambient red particles drift
     * around the player so the charged state is always visible.
     */
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient) {
            if (entity instanceof PlayerEntity player) {
                clearStrike(player);
            }
            return;
        }
        if (!selected || !isInfused(stack)) {
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
     * Called by vanilla AFTER a hit landed on the main target (server only in
     * practice - the client never gets a successful damage() back).
     *
     * The extra damage was already dealt (see register); this is where the
     * wielder pays for it and where the hit gets its weight.
     */
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient && attacker instanceof PlayerEntity player) {
            ServerWorld serverWorld = (ServerWorld) target.getWorld();

            // The full force of this swing (player + blade + blood bonus),
            // read before the bonus is removed. Before armor, on purpose:
            // hitting an armored player builds rage as fast as hitting a mob.
            float swingDamage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);

            // The bonus is only on the player if register() judged this swing
            // a committed one - spam clicks are plain, dull hits.
            boolean struck = clearStrike(player);
            boolean infused = struck && isInfused(stack);
            // Re-check the price: thorns & co. may have hurt the wielder since.
            boolean fed = struck && (infused || player.getHealth() > BLOOD_COST);

            if (infused) {
                // The blood was paid by the ritual; the swing only consumes it.
                setInfused(stack, false);
            } else if (fed) {
                payBlood(player, BLOOD_COST);
            }

            if (fed) {
                impact(serverWorld, target, player, infused);
                // Fed hits build rage; enough of it and the wielder goes berserk.
                BerserkMode.addRage(player, swingDamage);
            } else if (player.getHealth() <= BLOOD_COST) {
                // Too weak to pay: the swing connects, but the blade refuses.
                serverWorld.playSound(null, player.getBlockPos(), ModSounds.BLOOD_FAIL,
                        SoundCategory.PLAYERS, 0.8f, 1.2f);
                spawnParticleBurst(serverWorld, player.getPos().add(0, 1.5, 0), 4);
            }
        }

        // Let the vanilla sword logic run too (durability damage...).
        return super.postHit(stack, target, attacker);
    }

    /**
     * Everything that makes a fed hit feel heavy: a shove, layered low sounds,
     * a spray of blood and a recovery slowdown. The camera kick is client
     * side (ScreenShake), triggered by the client's own attack event.
     */
    private static void impact(ServerWorld world, LivingEntity target, PlayerEntity player, boolean infused) {
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.5, 0);

        // Shove the target away from the wielder (same formula vanilla uses).
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        target.takeKnockback(infused ? INFUSED_KNOCKBACK : KNOCKBACK, MathHelper.sin(yaw), -MathHelper.cos(yaw));
        // Vanilla already synced player targets before postHit ran, so
        // push the extra velocity out ourselves.
        if (target instanceof ServerPlayerEntity hit) {
            hit.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(hit));
        }

        // Three low layers: the heartbeat thump, a deep body-blow and the cleave.
        world.playSound(null, at.x, at.y, at.z, ModSounds.BLOOD_DRAIN, SoundCategory.PLAYERS,
                IMPACT_VOLUME, infused ? 0.5f : 0.75f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS,
                IMPACT_VOLUME * (infused ? 1.0f : 0.7f), infused ? 0.8f : 1.1f);
        world.playSound(null, at.x, at.y, at.z, ModSounds.SWORD_SWING, SoundCategory.PLAYERS,
                IMPACT_VOLUME * 0.7f, infused ? 0.6f : 0.9f + world.random.nextFloat() * 0.2f);
        if (infused) {
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS,
                    IMPACT_VOLUME * 0.8f, 0.5f);
        }

        // Block-break particles of a redstone block look like flying gore.
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                at.x, at.y, at.z, infused ? 70 : 28, 0.3, 0.4, 0.3, 0.15);
        if (infused) {
            spawnParticleBurst(world, at, 24);
        }

        // Recovery: you put your whole body into that swing.
        int recovery = infused ? INFUSED_RECOVERY_TICKS : RECOVERY_TICKS;
        if (recovery > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, recovery, 2, false, false, false));
        }
    }

    // ======================== TOOLTIP ========================

    /**
     * The description under the item name in the inventory: only lore (see
     * LoreItem), the same whether or not the blade is charged. (Charged
     * shows through the glint and the red spores instead.)
     */
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        LoreItem.appendLore(tooltip, getTranslationKey());
    }

    // ======================== HELPERS ========================

    /**
     * Berserk check that also works from the client side. Rage and combat
     * timers only exist on the server, so the client uses what the server
     * last told it about its OWN player (BerserkStatePayload). That keeps
     * client and server in agreement about infusion costs and charge time.
     */
    private static boolean isBerserk(LivingEntity user, World world) {
        if (!(user instanceof PlayerEntity player)) {
            return false;
        }
        if (world.isClient) {
            return player.isMainPlayer() && BerserkMode.isClientActive();
        }
        return BerserkMode.isActive(player);
    }

    /**
     * Reads the "infused" flag from the item's NBT.
     * hasNbt() check avoids creating an empty NBT compound on every read.
     */
    public static boolean isInfused(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean(INFUSED_KEY);
    }

    /**
     * Writes the "infused" flag into the item's NBT.
     */
    private static void setInfused(ItemStack stack, boolean infused) {
        stack.getOrCreateNbt().putBoolean(INFUSED_KEY, infused);
    }

    /**
     * Spawns a burst of red crimson spores at a position - used for the
     * infusion ritual, the refusal and the infused hit.
     */
    private static void spawnParticleBurst(ServerWorld world, Vec3d pos, int count) {
        for (int i = 0; i < count; i++) {
            world.spawnParticles(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y, pos.z, 1,
                    (Math.random() - 0.5) * 0.3,
                    Math.random() * 0.4 + 0.1,
                    (Math.random() - 0.5) * 0.3,
                    0.1);
        }
    }
}
