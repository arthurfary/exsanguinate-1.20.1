package net.yut.exsanguinate.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.yut.exsanguinate.Exsanguinate;
import net.yut.exsanguinate.block.ModBlocks;

/**
 * The mod's own creative inventory tab, with the Heavy Cleaver as its icon.
 *
 * An item group is registered like an item. Its name comes from the
 * language file ("itemGroup.exsanguinate"); entries() lists what goes in
 * it, in order. Add every new item or block here.
 */
public class ModItemGroups {

    public static final RegistryKey<ItemGroup> EXSANGUINATE = RegistryKey.of(RegistryKeys.ITEM_GROUP,
            new Identifier(Exsanguinate.MOD_ID, "exsanguinate"));

    /** Called from Exsanguinate.onInitialize(), after items and blocks exist. */
    public static void registerItemGroups() {
        Registry.register(Registries.ITEM_GROUP, EXSANGUINATE, FabricItemGroup.builder()
                .icon(() -> new ItemStack(ModItems.CLEAVER))
                .displayName(Text.translatable("itemGroup.exsanguinate"))
                .entries((context, entries) -> {
                    entries.add(ModItems.CARNELIAN_OATH);
                    entries.add(ModItems.CLEAVER);
                    entries.add(ModItems.BLOOD_INFUSED_IRON);
                    entries.add(ModBlocks.BLOOD_ALTAR);
                })
                .build());
    }
}
