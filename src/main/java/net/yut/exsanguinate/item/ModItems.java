package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

public class ModItems {
    // Using IRON material for now - you can change this to DIAMOND, NETHERITE, etc.
    public static final Item CARNELIAN_OATH = registerItem("carnelian_oath",
            new SwordItem(ToolMaterials.IRON, 3, -2.4f, new Item.Settings()));

    private static Item registerItem(String name, Item item){
        return Registry.register(Registries.ITEM, new Identifier(Exsanguinate.MOD_ID, name), item);
    }

    public static void registerModItems(){
        Exsanguinate.LOGGER.info("Registering mod items for " + Exsanguinate.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(fabricItemGroupEntries -> {
            fabricItemGroupEntries.add(CARNELIAN_OATH);
        });
    }
}