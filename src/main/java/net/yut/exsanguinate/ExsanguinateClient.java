package net.yut.exsanguinate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.client.ColorFilter;
import net.yut.exsanguinate.item.ModItems;
import net.yut.exsanguinate.network.InfusionCompletePayload;

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

        // When the server tells us the infusion completed, flash the screen
        // black & white for a second (ColorFilter). Handlers run on the
        // network thread, so hop back to the main thread before touching UI.
        ClientPlayNetworking.registerGlobalReceiver(InfusionCompletePayload.TYPE,
                (payload, player, responseSender) -> MinecraftClient.getInstance().execute(ColorFilter::start));
    }
}