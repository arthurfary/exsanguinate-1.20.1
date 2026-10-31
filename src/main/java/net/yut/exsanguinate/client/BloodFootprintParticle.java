package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A bloody footprint lying flat on the ground. It stays still for a few
 * seconds, then fades away.
 *
 * Normal particles are "billboards": flat squares that always turn to face
 * the camera. A footprint must lie on the floor instead and point the way
 * the wielder walked, so buildGeometry() is overridden to draw the square
 * flat and rotated.
 *
 * The walking direction travels in the particle's "velocity X" slot (see
 * BerserkMode.leaveFootprint): a footprint never moves, so that slot is free.
 */
@Environment(EnvType.CLIENT)
public class BloodFootprintParticle extends SpriteBillboardParticle {

    // How long a footprint lasts (100 ticks = 5 s) and when it starts fading.
    private static final int LIFETIME_TICKS = 100;
    private static final float FADE_START = 0.6f;

    // Half the footprint's width/length in blocks (0.27 = a 0.54 block print).
    private static final float SIZE = 0.27f;

    // Lays the square flat, toe pointing the way the wielder walked.
    private final Quaternionf rotation;

    protected BloodFootprintParticle(ClientWorld world, double x, double y, double z, float angle) {
        super(world, x, y, z);
        this.maxAge = LIFETIME_TICKS;
        this.scale = SIZE;
        this.gravityStrength = 0.0f;
        this.collidesWithWorld = false;
        this.rotation = new Quaternionf().rotationY(angle).rotateX((float) (-Math.PI / 2));
    }

    @Override
    public void tick() {
        // No super.tick(): footprints never move, they only age and fade.
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }
        float life = (float) this.age / this.maxAge;
        this.alpha = life < FADE_START ? 1.0f : 1.0f - (life - FADE_START) / (1.0f - FADE_START);
    }

    @Override
    public ParticleTextureSheet getType() {
        // Translucent so the fade-out (alpha) actually shows.
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Same as the vanilla billboard version, except the corners are turned by
     * our fixed "flat on the ground" rotation instead of the camera's.
     */
    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3d cameraPos = camera.getPos();
        float x = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cameraPos.getX());
        float y = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cameraPos.getY());
        float z = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cameraPos.getZ());

        Vector3f[] corners = {
                new Vector3f(-1.0f, -1.0f, 0.0f), new Vector3f(-1.0f, 1.0f, 0.0f),
                new Vector3f(1.0f, 1.0f, 0.0f), new Vector3f(1.0f, -1.0f, 0.0f)
        };
        float size = this.getSize(tickDelta);
        for (Vector3f corner : corners) {
            corner.rotate(this.rotation).mul(size).add(x, y, z);
        }

        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        float[][] uvs = {{maxU, maxV}, {maxU, minV}, {minU, minV}, {minU, maxV}};
        int light = this.getBrightness(tickDelta);

        // Drawn twice (front and back) so it shows no matter which way the
        // square ended up facing.
        for (int i = 0; i < 4; i++) {
            vertex(vertexConsumer, corners[i], uvs[i], light);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(vertexConsumer, corners[i], uvs[i], light);
        }
    }

    private void vertex(VertexConsumer vertexConsumer, Vector3f pos, float[] uv, int light) {
        vertexConsumer.vertex(pos.x(), pos.y(), pos.z()).texture(uv[0], uv[1])
                .color(this.red, this.green, this.blue, this.alpha).light(light).next();
    }

    /**
     * Creates footprints when the server says to spawn one. Registered in
     * ExsanguinateClient. The SpriteProvider holds the texture(s) listed in
     * particles/blood_footprint.json.
     */
    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z,
                                       double angle, double unusedY, double unusedZ) {
            BloodFootprintParticle footprint = new BloodFootprintParticle(world, x, y, z, (float) angle);
            footprint.setSprite(this.sprites);
            return footprint;
        }
    }
}
