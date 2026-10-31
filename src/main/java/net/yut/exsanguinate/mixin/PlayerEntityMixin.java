package net.yut.exsanguinate.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.yut.exsanguinate.berserk.BerserkMode;
import net.yut.exsanguinate.effect.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Three jobs:
 *  1. Stamps "combat happened" whenever the player attacks something
 *     (BerserkMode uses this together with LivingEntityMixin).
 *  2. Applies the berserk effects every tick (health cap, saturation...).
 *  3. Blocks natural healing while the Blood Curse is on.
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void exsanguinate$onAttack(Entity target, CallbackInfo ci) {
        BerserkMode.onCombat((PlayerEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void exsanguinate$berserkTick(CallbackInfo ci) {
        BerserkMode.tick((PlayerEntity) (Object) this);
    }

    // canFoodHeal() is what the hunger bar asks before healing the player.
    // While the Blood Curse is on, the answer is no: blood paid to the sword
    // stays paid. Berserk keeps its own regen (its saturation is the point).
    @Inject(method = "canFoodHeal", at = @At("HEAD"), cancellable = true)
    private void exsanguinate$openWound(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.hasStatusEffect(ModEffects.BLOOD_CURSE) && !BerserkMode.isActive(player)) {
            cir.setReturnValue(false);
        }
    }
}