package net.yut.exsanguinate.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.registry.RegistryWrapper;
import net.yut.exsanguinate.item.ModItems;
import net.yut.exsanguinate.util.ModTags;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Generates the mod's item tag JSON files
 * (data/exsanguinate/tags/items/<tag>.json).
 *
 * This provider generates the "blades" tag with the Carnelian Oath inside,
 * so other code can check "is this item a blade?" via ModTags.Items.BLADES.
 *
 * It is registered in ExsanguinateDataGenerator and run with: ./gradlew runDatagen
 */
public class ModItemTagProvider extends FabricTagProvider.ItemTagProvider {
    public ModItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> completableFuture, @Nullable BlockTagProvider blockTagProvider) {
        super(output, completableFuture, blockTagProvider);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // Add the sword to our own "blades" tag.
        getOrCreateTagBuilder(ModTags.Items.BLADES)
                .add(ModItems.CARNELIAN_OATH);
    }
}