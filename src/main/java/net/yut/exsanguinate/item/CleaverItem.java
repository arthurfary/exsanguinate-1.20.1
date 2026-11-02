package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.yut.exsanguinate.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * The Heavy Cleaver ("cleaver" in code) - the tool that makes Blood
 * Infused Iron (shown in game as Bloodquenched Iron). It has no lore lines
 * in the language file, so its tooltip is just its name.
 *
 * On its own it is just a heavy iron chopper. The trick is the OFF HAND:
 * kill something with the cleaver while holding iron ingots in your off
 * hand, and the spilled blood has a 50% chance to soak into one ingot,
 * turning it into Blood Infused Iron (the ingredient of the Carnelian Oath
 * ritual, see BloodAltarBlockEntity).
 *
 * Why a kill event instead of postHit? postHit runs on every hit, and we
 * only care about the killing blow. Fabric's AFTER_KILLED_OTHER_ENTITY
 * fires exactly once, on the server, when an entity kills another one, and
 * hands us both the killer and the victim.
 */
public class CleaverItem extends SwordItem {

    // Chance that a kill soaks one ingot (0.5 = 50%).
    public static final float INFUSE_CHANCE = 0.5f;

    // The dark red of the blood trail that flies from the victim to the ingot.
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55f, 0.02f, 0.02f), 1.2f);

    /**
     * Same parameters as the sword (see CarnelianOathItem for the long
     * explanation). With ToolMaterials.IRON (+2) and attackDamage 3 the
     * cleaver hits for 1 + 2 + 3 = 6, and 4.0 - 2.8 = 1.2 swings per second:
     * slower than a sword, faster than an axe.
     */
    public CleaverItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    /** Called once from ModItems.registerModItems(). */
    public static void register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim) -> {
            if (killer instanceof PlayerEntity player) {
                tryInfuse(world, player, victim);
            }
        });
    }

    /**
     * The kill happened: if the killer swung the cleaver with iron in the
     * other hand, roll the dice and maybe turn one ingot into infused iron.
     */
    private static void tryInfuse(ServerWorld world, PlayerEntity player, LivingEntity victim) {
        if (!player.getMainHandStack().isOf(ModItems.CLEAVER) || victim instanceof ArmorStandEntity) {
            return;
        }
        ItemStack offHand = player.getOffHandStack();
        if (!offHand.isOf(Items.IRON_INGOT)) {
            return;
        }

        Vec3d wound = victim.getPos().add(0.0, victim.getHeight() * 0.5, 0.0);
        Vec3d ingot = offHandPos(player);

        if (world.random.nextFloat() >= INFUSE_CHANCE) {
            // The blood didn't take: a wisp of smoke off the ingot, nothing more.
            world.spawnParticles(ParticleTypes.SMOKE, ingot.x, ingot.y, ingot.z, 3, 0.05, 0.05, 0.05, 0.01);
            return;
        }

        // Take one plain ingot, give one infused ingot. When it was the last
        // ingot the infused one goes straight into the off hand (feels like the
        // ingot itself changed); otherwise into the inventory, or dropped if full.
        offHand.decrement(1);
        ItemStack infused = new ItemStack(ModItems.BLOOD_INFUSED_IRON);
        if (offHand.isEmpty()) {
            player.setStackInHand(Hand.OFF_HAND, infused);
        } else {
            player.getInventory().offerOrDrop(infused);
        }

        // A spray at the wound, a trail of blood flying into the ingot, and a
        // hiss like hot iron quenched in blood.
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                wound.x, wound.y, wound.z, 20, 0.25, 0.3, 0.25, 0.1);
        bloodTrail(world, wound, ingot);
        world.spawnParticles(ParticleTypes.CRIMSON_SPORE, ingot.x, ingot.y, ingot.z, 8, 0.1, 0.1, 0.1, 0.02);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_LAVA_EXTINGUISH,
                SoundCategory.PLAYERS, 0.4f, 1.4f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLOOD_DRAIN,
                SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    /** A line of blood droplets from the victim to the ingot. */
    private static void bloodTrail(ServerWorld world, Vec3d from, Vec3d to) {
        int steps = 12;
        for (int i = 0; i <= steps; i++) {
            Vec3d at = from.lerp(to, (double) i / steps);
            world.spawnParticles(BLOOD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Roughly where the off hand is in the world: chest height, a little in
     * front of the player and to the off-hand side. Yaw 0 faces +Z (south),
     * so the player's right side is (-cos yaw, -sin yaw).
     */
    private static Vec3d offHandPos(PlayerEntity player) {
        float yaw = player.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        double forwardX = -MathHelper.sin(yaw);
        double forwardZ = MathHelper.cos(yaw);
        // The off hand is on the left for right-handed players and vice versa.
        double side = player.getMainArm() == Arm.RIGHT ? -1.0 : 1.0;
        double rightX = -MathHelper.cos(yaw);
        double rightZ = -MathHelper.sin(yaw);
        return player.getPos().add(
                forwardX * 0.35 + rightX * side * 0.35,
                player.getHeight() * 0.55,
                forwardZ * 0.35 + rightZ * side * 0.35);
    }

    /** The name is gold, wherever the cleaver is named (tooltip, hotbar, chat). */
    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.GOLD);
    }

    /** Lore only (see LoreItem). */
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        LoreItem.appendLore(tooltip, getTranslationKey());
    }
}
