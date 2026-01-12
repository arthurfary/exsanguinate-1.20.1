package net.yut.exsanguinate;

import net.fabricmc.api.ModInitializer;

import net.yut.exsanguinate.item.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Exsanguinate implements ModInitializer {
	public static final String MOD_ID = "exsanguinate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        ModItems.registerModItems();
	}
}