package net.yut.exsanguinate.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.yut.exsanguinate.berserk.BerserkMode;
import org.lwjgl.glfw.GLFW;

/**
 * DEV TOOL - press F9 to toggle the berserk visuals (blood vignette, colour
 * drain, heartbeat/drone) without having to build up 55 rage first.
 *
 * Only registered in a development environment (a normal install never gets
 * the key), and it only changes what THIS client sees: the server does not
 * know about it, so it cannot be used to cheat.
 *
 * Handy while tuning the colour filter: press F9 twice (on, then off) to
 * watch the 1 s fade in and 2 s fade out again and again.
 */
@Environment(EnvType.CLIENT)
public final class BerserkPreview {

    private BerserkPreview() {
    }

    /** Called once from the client entrypoint. No-op outside dev. */
    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return;
        }

        KeyBinding toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.exsanguinate.dev_berserk",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "category.exsanguinate.dev"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.wasPressed()) {
                boolean active = !BerserkMode.isClientActive();
                // Same entry point the server packet uses, so every berserk
                // visual (vignette, sounds, colour filter) follows along.
                BerserkVignette.setActive(active);
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable(active
                            ? "exsanguinate.dev.berserk.on"
                            : "exsanguinate.dev.berserk.off"), true);
                }
            }
        });
    }
}
