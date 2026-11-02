package net.yut.exsanguinate.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;
import net.yut.exsanguinate.block.ModBlocks;

/**
 * Registers the mod's block entity types.
 *
 * A BLOCK ENTITY is extra data (and logic) attached to one block in the
 * world - chests keep their items in one, furnaces their fuel and progress.
 * The altar needs one to remember what lies on it and how far its ritual is.
 * The TYPE ties the block entity class to the block(s) that may own it.
 */
public class ModBlockEntities {

    public static final BlockEntityType<BloodAltarBlockEntity> BLOOD_ALTAR = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Exsanguinate.MOD_ID, "blood_altar"),
            FabricBlockEntityTypeBuilder.create(BloodAltarBlockEntity::new, ModBlocks.BLOOD_ALTAR).build());

    /** Called from Exsanguinate.onInitialize(), after ModBlocks. */
    public static void registerModBlockEntities() {
        Exsanguinate.LOGGER.info("Registering mod block entities for " + Exsanguinate.MOD_ID);
    }
}
