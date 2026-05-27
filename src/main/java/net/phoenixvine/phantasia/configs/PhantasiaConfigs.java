package net.phoenixvine.phantasia.configs;

import net.phoenixvine.phantasia.Phantasia;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = Phantasia.MOD_ID)
public class PhantasiaConfigs {

    public static PhantasiaConfigs INSTANCE;
    public static ConfigHolder<PhantasiaConfigs> CONFIG_HOLDER;

    public static void init() {
        CONFIG_HOLDER = Configuration.registerConfig(PhantasiaConfigs.class, ConfigFormats.yaml());
        INSTANCE = CONFIG_HOLDER.getConfigInstance();
    }

    @Configurable
    public PhantasiaUIConfig phantasiaUI = new PhantasiaUIConfig();

    public static class PhantasiaUIConfig {

        @Configurable
        @Configurable.Comment({
                "LOOK_AT: Bar appears when looking at a placed controller.",
                "HELD_ITEM: Bar appears when holding a controller in your hand.",
                "PERSISTENT: Bar stays on screen for the last accessed machine."
        })
        public DisplayMode displayMode = DisplayMode.TOOLTIP_HOTBAR;

        @Configurable
        @Configurable.Comment("Ticks required to hold the key to open the menu (20 ticks = 1 second).")
        public int activationTicks = 20;

        @Configurable
        @Configurable.Comment({
                "Visual theme for the Phantasia scene viewer.",
                "COBALT   - Default. Deep navy with sky-blue accents.",
                "RAINBOW  - Hue-cycling accent on a neutral dark base.",
                "AMETHYST - Soft purple with gold progress bars.",
                "MINECRAFT - Classic vanilla inventory look (stone grey, gold accent)."
        })
        public String theme = "COBALT";

        public enum DisplayMode {
            TOOLTIP_ONLY,
            JADE_ONLY,
            HOTBAR_ONLY,
            TOOLTIP_JADE,
            TOOLTIP_HOTBAR
        }
    }
}
