package net.yut.exsanguinate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.yut.exsanguinate.block.entity.BloodAltarBlockEntity;
import net.yut.exsanguinate.block.entity.ModBlockEntities;
import net.yut.exsanguinate.client.AltarHumSound;
import net.yut.exsanguinate.client.BerserkSounds;
import net.yut.exsanguinate.client.BerserkVignette;
import net.yut.exsanguinate.client.BloodAltarRenderer;
import net.yut.exsanguinate.client.BloodFootprintParticle;
import net.yut.exsanguinate.client.ColorFilter;
import net.yut.exsanguinate.client.ScreenShake;
import net.yut.exsanguinate.item.CarnelianOathItem;
import net.yut.exsanguinate.item.ModItems;
import net.yut.exsanguinate.network.BerserkStatePayload;
import net.yut.exsanguinate.network.InfusionCompletePayload;
import net.yut.exsanguinate.network.RitualCompletePayload;
import net.yut.exsanguinate.particle.ModParticles;

/**
 * CLIENT ENTRYPOINT.
 *
 * Fabric only calls onInitializeClient() when the game runs in a client
 * environment (i.e. when you actually play Minecraft). A dedicated server
 * never runs this class.
 *
 * Client-only features go here: keybinds, HUD rendering, custom screens,
 * client-side particles, shader setup...
 */
public class ExsanguinateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Model predicates are the "switches" used by item model overrides.
        // The vanilla shield uses the "blocking" predicate (1 when the holder
        // is using the item) to swap to its blocking model. We register the
        // same predicate for the Carnelian Oath, so its model can do the same:
        // the sword model switches to carnelian_oath_blocking while you hold
        // right click, which holds the sword up in front of the face in
        // first person (the old blocking animation).
        ModelPredicateProviderRegistry.register(
                ModItems.CARNELIAN_OATH,
                new Identifier("blocking"),
                (stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
        );

        // Camera kick on a fed swing. This is the CLIENT's own attack event, so
        // the kick lands the instant you click (no waiting for the server).
        // Dull swings (spam clicks, a blade that can't be fed) get no kick.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getMainHandStack();
            if (world.isClient && entity instanceof LivingEntity && stack.isOf(ModItems.CARNELIAN_OATH)
                    && player.getAttackCooldownProgress(0.5f) >= CarnelianOathItem.READY
                    && CarnelianOathItem.bloodBonus(player, stack) > 0.0f) {
                ScreenShake.kick(CarnelianOathItem.isInfused(stack) ? 0.9f : 0.5f);
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ScreenShake.tick();
            BerserkVignette.tick();
            BerserkSounds.tick(client);
        });

        // The Blood Altar's floating offering (see BloodAltarRenderer) and the
        // drone of a primed altar (see AltarHumSound).
        BlockEntityRendererFactories.register(ModBlockEntities.BLOOD_ALTAR, BloodAltarRenderer::new);
        BloodAltarBlockEntity.clientHum = AltarHumSound::keepAlive;
        BloodAltarBlockEntity.clientWhisper = AltarHumSound::whisper;

        // A ritual completed close by: one dull thud of the camera, softer
        // with distance (only players within WITNESS_RADIUS get this).
        ClientPlayNetworking.registerGlobalReceiver(RitualCompletePayload.TYPE,
                (payload, player, responseSender) -> MinecraftClient.getInstance().execute(() -> {
                    double distance = Math.sqrt(player.squaredDistanceTo(Vec3d.ofCenter(payload.pos())));
                    float closeness = 1.0f - (float) (distance / BloodAltarBlockEntity.WITNESS_RADIUS);
                    ScreenShake.kick(0.4f * MathHelper.clamp(closeness, 0.0f, 1.0f));
                }));

        // What the berserk blood footprints look like (the server only says where).
        ParticleFactoryRegistry.getInstance().register(ModParticles.BLOOD_FOOTPRINT, BloodFootprintParticle.Factory::new);

        // When the server tells us the infusion completed, flash the screen
        // black & white for a second (ColorFilter). Handlers run on the
        // network thread, so hop back to the main thread before touching UI.
        ClientPlayNetworking.registerGlobalReceiver(InfusionCompletePayload.TYPE,
                (payload, player, responseSender) -> MinecraftClient.getInstance().execute(ColorFilter::start));

        // Berserk started/ended: fade the blood vignette in/out. Cleared on
        // disconnect so it never follows you into another world or server.
        ClientPlayNetworking.registerGlobalReceiver(BerserkStatePayload.TYPE,
                (payload, player, responseSender) -> MinecraftClient.getInstance().execute(() -> BerserkVignette.setActive(payload.active())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BerserkVignette.reset();
            BerserkSounds.reset();
            AltarHumSound.reset();
        });
    }
}