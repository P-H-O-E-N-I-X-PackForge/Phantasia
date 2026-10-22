package net.phoenixvine.phantasia.integration.emi;

import net.neoforged.fml.ModList;

public final class EmiCompat {

    public static final boolean EMI_PRESENT = ModList.get().isLoaded("emi");

    private EmiCompat() {}
}
