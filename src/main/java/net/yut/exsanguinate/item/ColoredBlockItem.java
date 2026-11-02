package net.yut.exsanguinate.item;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A block's item form with a coloured name (the Altar of the Lesser Zenith is gold).
 *
 * Item names are white unless the item says otherwise: getName() builds the
 * name text, so returning it with a colour colours it everywhere the item
 * is named - tooltips, the name above the hotbar, chat.
 */
public class ColoredBlockItem extends BlockItem {

    private final Formatting color;

    public ColoredBlockItem(Block block, Settings settings, Formatting color) {
        super(block, settings);
        this.color = color;
    }

    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(color);
    }
}
