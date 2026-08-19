package net.yut.exsanguinate.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.yut.exsanguinate.berserk.BerserkMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two jobs:
 *  1. Stamps "combat happened" whenever the player attacks something
 *     (BerserkMode uses this together with LivingEntityMixin).
 *  2. Applies the berserk effects every tick (health cap, saturation...).
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
}