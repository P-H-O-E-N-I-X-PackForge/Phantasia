package net.phoenixvine.phantasia.compat.gtceu;

import net.neoforged.bus.api.IEventBus;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

public final class PhantasiaGTCompat {

    private PhantasiaGTCompat() {}

    public static void init(IEventBus modEventBus) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            GTCEuBlockInspectHelper.register();
            PhantasiaMultiblockRegistry.register(new GTCEuMultiblockProvider());
        }
    }
}
