package net.yut.exsanguinate.item;


import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;



public class ModItems {
    private static Item registerItem(String name, Item item){
        return Registry.register(Registries.ITEM, new Identifier(Exsanguinate.MOD_ID, name), item);
    }

    public static void registerModItems(){
        Exsanguinate.LOGGER.info("Registering mod items for " + Exsanguinate.MOD_ID);
    }
}
