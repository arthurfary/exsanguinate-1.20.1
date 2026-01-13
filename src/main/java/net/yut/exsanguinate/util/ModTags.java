package net.yut.exsanguinate.util;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;

public class ModTags {
    public static class Items {
        //public static final TagKey<Item> BLADES = createTag("blades")
        private static TagKey<Item> createTag(String name){
            return TagKey.of(RegistryKeys.ITEM, Identifier.of(Exsanguinate.MOD_ID, name));
        }
    }
}
