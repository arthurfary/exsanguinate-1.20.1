package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * What happens when the Carnelian Oath or the Heavy Cleaver takes a life:
 *
 *  1. A slain PLAYER drops their own head, wearing their skin.
 *  2. The death message is the mod's own, written in the language file:
 *       "death.attack.exsanguinate.carnelian_oath"
 *       "death.attack.exsanguinate.cleaver"
 *     with %1$s = who died, %2$s = the killer, %3$s = the weapon's name.
 *     (The message is built by DamageSourceMixin, which asks killingBlade().)
 *
 * Both only count a blow struck BY HAND with the blade in the main hand -
 * not an arrow shot while holding it.
 */
public final class BladeKills {

    private BladeKills() {
    }

    /** Called once from Exsanguinate.onInitialize(). */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (victim instanceof PlayerEntity player && killingBlade(source) != null) {
                dropHead(player);
            }
        });
    }

    /**
     * The mod's blade that dealt this blow, or null if it wasn't one.
     * "Direct" = the attacker itself hit (getSource() is the attacker), not a
     * projectile or anything else it set off.
     */
    @Nullable
    public static Item killingBlade(DamageSource source) {
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof PlayerEntity killer) || source.getSource() != attacker) {
            return null;
        }
        ItemStack held = killer.getMainHandStack();
        if (held.isOf(ModItems.CARNELIAN_OATH) || held.isOf(ModItems.CLEAVER)) {
            return held.getItem();
        }
        return null;
    }

    /**
     * The death message for a kill by one of the blades (see the lang keys
     * above), or null to let vanilla write its usual one.
     */
    @Nullable
    public static Text deathMessage(DamageSource source, LivingEntity victim) {
        Item blade = killingBlade(source);
        if (blade == null) {
            return null;
        }
        PlayerEntity killer = (PlayerEntity) source.getAttacker();
        String key = blade == ModItems.CARNELIAN_OATH
                ? "death.attack.exsanguinate.carnelian_oath"
                : "death.attack.exsanguinate.cleaver";
        return Text.translatable(key, victim.getDisplayName(), killer.getDisplayName(), killer.getMainHandStack().toHoverableText());
    }

    /**
     * A player head is just the vanilla item with the owner's profile in its
     * "SkullOwner" tag. The profile from a live player already carries their
     * skin, so the head shows it right away (no lookup needed).
     */
    private static void dropHead(PlayerEntity player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateNbt().put("SkullOwner", NbtHelper.writeGameProfile(new NbtCompound(), player.getGameProfile()));
        player.dropStack(head);
    }
}
