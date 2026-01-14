package net.yut.exsanguinate.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class CarnelianOathItem extends SwordItem {
    public CarnelianOathItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player) {
            float damageAmount = 2.0f;
            float newHealth = player.getHealth() - damageAmount;

            if (newHealth < 0) {
                newHealth = 0;
            }

            player.setHealth(newHealth);
        }

        // Spawn drain particles over time
        if (!target.getWorld().isClient && target.getWorld() instanceof ServerWorld serverWorld) {
            spawnDrainParticles(serverWorld, target, attacker);
        }

        return super.postHit(stack, target, attacker);
    }

    private void spawnDrainParticles(ServerWorld world, LivingEntity target, LivingEntity attacker) {
        Vec3d lookDirection = attacker.getRotationVec(1.0f).normalize();
        Vec3d eyePosition = attacker.getPos().add(0, attacker.getEyeHeight(attacker.getPose()), 0);
        Vec3d horizontalPerpendicular = lookDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();

        double randomTiltDirection = Math.random() > 0.5 ? 1.0 : -1.0;
        double tiltAngleRadians = Math.toRadians(25) * randomTiltDirection;
        double slashTravelDistance = 8.0;
        int numberOfLines = 8;
        double spacingBetweenLines = slashTravelDistance / numberOfLines;

        double cosTilt = Math.cos(tiltAngleRadians);
        double sinTilt = Math.sin(tiltAngleRadians);
        Vec3d tiltedPerpendicular = new Vec3d(
                horizontalPerpendicular.x * cosTilt - horizontalPerpendicular.z * sinTilt,
                horizontalPerpendicular.y,
                horizontalPerpendicular.x * sinTilt + horizontalPerpendicular.z * cosTilt
        );

        for (int lineIndex = 0; lineIndex < numberOfLines; lineIndex++) {
            final int currentLineIndex = lineIndex;

            world.getServer().execute(() -> {
                if (!attacker.isRemoved()) {
                    double distanceFromAttacker = currentLineIndex * spacingBetweenLines;
                    Vec3d lineCenter = eyePosition.add(lookDirection.multiply(distanceFromAttacker));

                    int particlesPerLine = 15;
                    double lineWidth = 1.6;
                    for (int i = 0; i < particlesPerLine; i++) {
                        double offsetAlongLine = (Math.random() - 0.5) * lineWidth;

                        double particleX = lineCenter.x + tiltedPerpendicular.x * offsetAlongLine;
                        double particleY = lineCenter.y;
                        double particleZ = lineCenter.z + tiltedPerpendicular.z * offsetAlongLine;

                        world.spawnParticles(ParticleTypes.FIREWORK, particleX, particleY, particleZ, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            });
        }

    }


}