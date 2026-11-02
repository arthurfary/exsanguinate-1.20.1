package net.yut.exsanguinate.item;

import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

/**
 * Registers all of the mod's items.
 *
 * "Registering" an item means telling Minecraft it exists. Every registered
 * thing gets a unique id in the form "mod_id:name", e.g.
 * "exsanguinate:carnelian_oath". That id is what links the item to its model,
 * language entry, recipe, tags, etc.
 */
public class ModItems {

    // The item is registered right here, as a static field, when the class loads.
    // Making it public and static lets any other class reference it.
    public static final Item CARNELIAN_OATH = registerItem("carnelian_oath",
            new CarnelianOathItem(ToolMaterials.NETHERITE, 0, -3.2f, new Item.Settings()));

    // Ritual ingredient for the sword: iron "soaked" in blood. Not crafted -
    // made by killing with the cleaver while holding iron in the off hand.
    // LoreItem = a plain item with a few lines of lore in its tooltip.
    public static final Item BLOOD_INFUSED_IRON = registerItem("blood_infused_iron", new LoreItem(new Item.Settings()));

    // The butcher's tool that soaks iron in blood (see CleaverItem).
    public static final Item CLEAVER = registerItem("cleaver",
            new CleaverItem(ToolMaterials.IRON, 3, -2.8f, new Item.Settings()));

    /**
     * Helper that registers any item.
     * Registries.ITEM is the big list of every item in the game.
     */
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Exsanguinate.MOD_ID, name), item);
    }

    /**
     * Called from Exsanguinate.onInitialize().
     * The items themselves were already registered by the static fields above,
     * so this hooks up their events (the sword's blood strike, the cleaver's
     * infusion on kill). They are listed in the mod's own creative tab
     * (ModItemGroups).
     */
    public static void registerModItems() {
        Exsanguinate.LOGGER.info("Registering mod items for " + Exsanguinate.MOD_ID);

        CarnelianOathItem.register();
        CleaverItem.register();

    }
}