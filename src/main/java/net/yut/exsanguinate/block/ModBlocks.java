package net.yut.exsanguinate.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;
import net.yut.exsanguinate.item.ColoredBlockItem;

/**
 * Registers the mod's blocks. Same pattern as ModItems, with one extra step:
 * a block only exists in the WORLD. To hold it in your hand or inventory it
 * also needs an item, a BlockItem, registered under the same id.
 */
public class ModBlocks {

    // The ritual altar the Carnelian Oath is forged on (see BloodAltarBlock).
    // Settings copied from polished deepslate: same hardness, sounds and
    // "needs a pickaxe". nonOpaque() because it isn't a full cube - without
    // it the blocks next to it would hide their faces and look like holes.
    // Its item's name is gold.
    public static final Block BLOOD_ALTAR = registerBlock("blood_altar",
            new BloodAltarBlock(AbstractBlock.Settings.copy(Blocks.POLISHED_DEEPSLATE).requiresTool().nonOpaque()),
            Formatting.GOLD);

    /** Registers the block and its BlockItem (with a coloured name) under the same id. */
    private static Block registerBlock(String name, Block block, Formatting nameColor) {
        Identifier id = new Identifier(Exsanguinate.MOD_ID, name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new ColoredBlockItem(block, new Item.Settings(), nameColor));
        return block;
    }

    /**
     * Called from Exsanguinate.onInitialize(). The blocks are registered by
     * the static fields above, so this only logs a message. (They are listed
     * in the mod's creative tab, see ModItemGroups.)
     */
    public static void registerModBlocks() {
        Exsanguinate.LOGGER.info("Registering mod blocks for " + Exsanguinate.MOD_ID);
    }
}
