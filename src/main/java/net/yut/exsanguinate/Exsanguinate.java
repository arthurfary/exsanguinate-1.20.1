package net.yut.exsanguinate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.yut.exsanguinate.berserk.BerserkMode;
import net.yut.exsanguinate.block.ModBlocks;
import net.yut.exsanguinate.block.entity.ModBlockEntities;
import net.yut.exsanguinate.effect.ModEffects;
import net.yut.exsanguinate.item.BladeKills;
import net.yut.exsanguinate.item.ModItemGroups;
import net.yut.exsanguinate.item.ModItems;
import net.yut.exsanguinate.particle.ModParticles;
import net.yut.exsanguinate.ritual.Rituals;
import net.yut.exsanguinate.sound.ModSounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MAIN ENTRYPOINT of the mod.
 *
 * Fabric calls onInitialize() exactly once when the game starts. This class
 * runs on BOTH sides: the client (when you play Minecraft) and the server
 * (a single player world hosts a built-in server). So this is where we
 * register everything that both sides need: items, effects, sounds...
 *
 * Client-only features (keybinds, HUD, screens, ...) go in ExsanguinateClient.
 */
public class Exsanguinate implements ModInitializer {

    // The mod's id, also called "namespace". Every resource the mod adds uses
    // this prefix, e.g. the sword is "exsanguinate:carnelian_oath".
    public static final String MOD_ID = "exsanguinate";

    // Logger used for console messages, printed as [exsanguinate/INFO] ...
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Each register* method registers its own content (see the classes).
        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        ModBlockEntities.registerModBlockEntities();
        ModItemGroups.registerItemGroups();
        ModEffects.registerModEffects();
        ModSounds.registerModSounds();
        ModParticles.registerModParticles();
        // Kills near a Blood Altar start its ritual.
        Rituals.register();
        // Player kills with the blades: head drops (death messages: DamageSourceMixin).
        BladeKills.register();

        // Berserk state is per player and in memory: drop it when they leave.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BerserkMode.forget(handler.player));
    }
}