package net.yut.exsanguinate.ritual;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One blood ritual: the exact items that must lie on the Blood Altar, and
 * what they become once a creature is sacrificed next to it.
 *
 * The offering is a bag, not a pattern: order doesn't matter, but the altar
 * must hold exactly these items and nothing else.
 *
 * @param offering the items that must be on the altar (repeat an item to need it twice)
 * @param result   the item the ritual forges
 */
public record AltarRitual(List<Item> offering, Item result) {

    /** Does this altar content (one item per stack) match the offering exactly? */
    public boolean matches(List<ItemStack> onAltar) {
        if (onAltar.size() != offering.size()) {
            return false;
        }
        // Cross off each item on the altar from a copy of the offering list.
        List<Item> missing = new ArrayList<>(offering);
        for (ItemStack stack : onAltar) {
            if (!missing.remove(stack.getItem())) {
                return false;
            }
        }
        return missing.isEmpty();
    }
}
