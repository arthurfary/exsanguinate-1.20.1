package net.yut.exsanguinate.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MIXINS - what and why:
 *
 * A mixin lets a mod modify Minecraft's compiled code at runtime, without
 * touching the game files. You "inject" your own code into an existing
 * method of an existing class.
 *
 * This specific mixin injects an empty callback at the start (HEAD) of
 * MinecraftServer.loadWorld() and does nothing with it - it is just a
 * template showing the syntax. Anything you put in init() would run every
 * time a world is loaded.
 *
 * Mixins must be listed in src/main/resources/exsanguinate.mixins.json,
 * which is referenced from fabric.mod.json.
 */
@Mixin(MinecraftServer.class)
public class ExampleMixin {
	@Inject(at = @At("HEAD"), method = "loadWorld")
	private void init(CallbackInfo info) {
		// This code is injected into the start of MinecraftServer.loadLevel()
	}
}