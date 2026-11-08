package net.yut.exsanguinate.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.text.Text;
import net.yut.exsanguinate.item.BladeKills;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps in the mod's own death message when the Carnelian Oath or the Heavy
 * Cleaver lands the killing blow (see BladeKills). Every other death keeps
 * vanilla's message.
 */
@Mixin(DamageSource.class)
public class DamageSourceMixin {

    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void exsanguinate$bladeDeathMessage(LivingEntity killed, CallbackInfoReturnable<Text> cir) {
        Text message = BladeKills.deathMessage((DamageSource) (Object) this, killed);
        if (message != null) {
            cir.setReturnValue(message);
        }
    }
}
