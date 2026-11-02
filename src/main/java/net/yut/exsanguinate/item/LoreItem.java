package net.yut.exsanguinate.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A plain item whose tooltip is a few lines of lore - and the helper every
 * other item and block of the mod uses for its own lore.
 *
 * The lines live only in the language file, as "<translation key>.lore.1",
 * ".lore.2" and so on: add or remove lines there and the tooltip follows,
 * no code change needed. Lore never explains anything; it only hints.
 */
public class LoreItem extends Item {

    // Lore lines are capped so a typo can't loop forever.
    private static final int MAX_LINES = 8;

    public LoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        appendLore(tooltip, getTranslationKey());
    }

    /**
     * Adds every "<key>.lore.N" line that exists in the current language,
     * in dark grey italics like an old inscription.
     * Tooltips are only built on the client, where the language is loaded.
     */
    public static void appendLore(List<Text> tooltip, String translationKey) {
        Language language = Language.getInstance();
        for (int line = 1; line <= MAX_LINES; line++) {
            String key = translationKey + ".lore." + line;
            if (!language.hasTranslation(key)) {
                return;
            }
            tooltip.add(Text.translatable(key).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        }
    }
}
