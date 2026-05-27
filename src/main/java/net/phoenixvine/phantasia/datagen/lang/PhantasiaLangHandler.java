package net.phoenixvine.phantasia.datagen.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class PhantasiaLangHandler {

    public static void init(RegistrateLangProvider provider) {
        // Keybinds

        provider.add("key.categories.phantasia", "Phantasia");
        provider.add("key.phantasia.phantasia_menu", "Open Phantasia Menu");
    }

    public static void multiLang(RegistrateLangProvider provider, String key, String... values) {
        for (var i = 0; i < values.length; i++) {
            provider.add(getSubKey(key, i), values[i]);
        }
    }

    protected static void multilineLang(RegistrateLangProvider provider, String key, String multiline) {
        var lines = multiline.split("\n");
        multiLang(provider, key, lines);
    }

    protected static String getSubKey(String key, int index) {
        return key + "." + index;
    }
}
