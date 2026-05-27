package net.phoenixvine.phantasia.datagen;

import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.datagen.lang.PhantasiaLangHandler;

import com.tterrag.registrate.providers.ProviderType;

public class PhantasiaDatagen {

    public static void init() {
        Phantasia.PHANTASIA_REGISTRATE.addDataGenerator(ProviderType.LANG, PhantasiaLangHandler::init);
    }
}
