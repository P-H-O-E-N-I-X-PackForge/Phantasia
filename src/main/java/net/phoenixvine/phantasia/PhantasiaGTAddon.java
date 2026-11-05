package net.phoenixvine.phantasia;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@SuppressWarnings("unused")
@GTAddon(Phantasia.MOD_ID)
public class PhantasiaGTAddon implements IGTAddon {

    private static GTRegistrate registrate;

    @Override
    public GTRegistrate getRegistrate() {
        if (registrate == null) registrate = GTRegistrate.create(Phantasia.MOD_ID);
        return registrate;
    }
}
