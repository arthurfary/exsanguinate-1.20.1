package net.yut.exsanguinate;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.yut.exsanguinate.datagen.ModItemTagProvider;

/**
 * DATA GENERATION entrypoint.
 *
 * "Datagen" generates JSON data files (tags, recipes, loot tables, models...)
 * from Java code, so you don't have to write JSON by hand.
 *
 * Run it with:  ./gradlew runDatagen
 * Generated files are written to src/main/generated and packed into the jar.
 */
public class ExsanguinateDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        // A "pack" is a group of providers. Each provider generates one type of file.
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
        // Tag providers need the game's registry data, so they are added via the
        // registry-dependent factory form (output + registries lookup).
        pack.addProvider((output, registriesFuture) -> new ModItemTagProvider(output, registriesFuture, null));
    }
}