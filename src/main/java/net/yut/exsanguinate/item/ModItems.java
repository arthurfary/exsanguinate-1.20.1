package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
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
    // Making it public and static lets any other class reference it
    // (e.g. ModItemTagProvider adds it to a tag).
    public static final Item CARNELIAN_OATH = registerItem("carnelian_oath",
            new CarnelianOathItem(ToolMaterials.NETHERITE, 0, -3.2f, new Item.Settings()));

    // Crafting ingredient for the sword: iron "soaked" in blood (see recipes).
    public static final Item BLOOD_INFUSED_IRON = registerItem("blood_infused_iron", new Item(new Item.Settings()));

    /**
     * Helper that registers any item.
     * Registries.ITEM is the big list of every item in the game.
     */
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Exsanguinate.MOD_ID, name), item);
    }

    /**
     * Called from Exsanguinate.onInitialize().
     * The item itself was already registered by the static field above, so this
     * hooks up the sword's blood strike and adds it to the vanilla "Combat"
     * creative tab.
     */
    public static void registerModItems() {
        Exsanguinate.LOGGER.info("Registering mod items for " + Exsanguinate.MOD_ID);

        CarnelianOathItem.register();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(fabricItemGroupEntries -> {
            fabricItemGroupEntries.add(CARNELIAN_OATH);
            fabricItemGroupEntries.add(BLOOD_INFUSED_IRON);
        });
    }
}