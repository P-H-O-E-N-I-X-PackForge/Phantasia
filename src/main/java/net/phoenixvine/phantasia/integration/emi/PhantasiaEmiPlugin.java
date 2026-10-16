package net.phoenixvine.phantasia.integration.emi;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@dev.emi.emi.api.EmiEntrypoint
@OnlyIn(Dist.CLIENT)
public class PhantasiaEmiPlugin implements EmiPlugin {

    public static final boolean EMI_PRESENT = ModList.get().isLoaded("emi");

    @Override
    public void register(EmiRegistry registry) {}
}
