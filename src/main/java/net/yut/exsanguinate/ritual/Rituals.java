package net.yut.exsanguinate.ritual;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.yut.exsanguinate.Exsanguinate;
import net.yut.exsanguinate.block.entity.BloodAltarBlockEntity;
import net.yut.exsanguinate.item.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every blood ritual the altar knows, and the "sacrifice" that starts them.
 *
 * A ritual is never started by the player directly. The offering waits on
 * the altar until a player KILLS a creature within SACRIFICE_RADIUS blocks of
 * it; the blood of that kill is what powers the ritual (see
 * BloodAltarBlockEntity for the 4 second ceremony itself).
 *
 * To add a ritual, add one line below: the items to lay on the altar and the
 * result. (A data-driven version would be a custom recipe type read from
 * JSON - more flexible, but a lot more code. Java is enough for now.)
 */
public final class Rituals {

    // How close to the altar the victim must die, in blocks.
    public static final double SACRIFICE_RADIUS = 6.0;

    private static final List<AltarRitual> ALL = new ArrayList<>();

    // Two blood-soaked ingots for the blade, a stick for the grip.
    public static final AltarRitual CARNELIAN_OATH = add(new AltarRitual(
            List.of(ModItems.BLOOD_INFUSED_IRON, ModItems.BLOOD_INFUSED_IRON, Items.STICK),
            ModItems.CARNELIAN_OATH));

    private Rituals() {
    }

    private static AltarRitual add(AltarRitual ritual) {
        ALL.add(ritual);
        return ritual;
    }

    /** The ritual this altar content would perform, or null if none matches. */
    @Nullable
    public static AltarRitual find(List<ItemStack> onAltar) {
        if (onAltar.isEmpty()) {
            return null;
        }
        for (AltarRitual ritual : ALL) {
            if (ritual.matches(onAltar)) {
                return ritual;
            }
        }
        return null;
    }

    /**
     * Called once from Exsanguinate.onInitialize(). Listens for kills and
     * offers the victim's blood to the nearest altar that is ready.
     */
    public static void register() {
        Exsanguinate.LOGGER.info("Registering blood rituals for " + Exsanguinate.MOD_ID);

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, victim) -> {
            if (killer instanceof PlayerEntity && !(victim instanceof ArmorStandEntity)) {
                sacrifice(world, victim);
            }
        });
    }

    /**
     * Finds the closest idle altar with a valid offering around the victim
     * and starts its ritual. Only one altar drinks from each death.
     *
     * Instead of checking every block in a 13x13x13 cube, we only look at the
     * block entities of the (at most 4) chunks the radius touches.
     * getWorldChunk returns null for chunks that are not loaded, so a kill
     * never loads or generates chunks.
     */
    private static void sacrifice(ServerWorld world, LivingEntity victim) {
        Vec3d blood = victim.getPos().add(0.0, victim.getHeight() * 0.5, 0.0);
        int minChunkX = ChunkSectionPos.getSectionCoord(blood.x - SACRIFICE_RADIUS);
        int maxChunkX = ChunkSectionPos.getSectionCoord(blood.x + SACRIFICE_RADIUS);
        int minChunkZ = ChunkSectionPos.getSectionCoord(blood.z - SACRIFICE_RADIUS);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(blood.z + SACRIFICE_RADIUS);

        BloodAltarBlockEntity closest = null;
        double closestDistance = SACRIFICE_RADIUS * SACRIFICE_RADIUS;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof BloodAltarBlockEntity altar && altar.canStartRitual()) {
                        double distance = Vec3d.ofCenter(altar.getPos()).squaredDistanceTo(blood);
                        if (distance <= closestDistance) {
                            closest = altar;
                            closestDistance = distance;
                        }
                    }
                }
            }
        }

        if (closest != null) {
            closest.startRitual(world, blood);
        }
    }
}
