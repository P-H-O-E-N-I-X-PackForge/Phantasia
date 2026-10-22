package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;

import java.util.function.Supplier;

public final class PhantasiaGTCompat {

    private static final DeferredRegister<DimensionMarker> DIMENSION_MARKERS = DeferredRegister
            .create(GTRegistries.DIMENSION_MARKERS, Phantasia.MOD_ID);

    static {

        DIMENSION_MARKERS.register("scene", (Supplier<DimensionMarker>) () -> new DimensionMarker(3,
                () -> Items.DIAMOND_BLOCK, Component.translatable("phantasia.dimension.scene.name")));
    }

    private PhantasiaGTCompat() {}

    public static void init(IEventBus modEventBus) {
        DIMENSION_MARKERS.register(modEventBus);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            GTCEuBlockInspectHelper.register();
            PhantasiaMultiblockRegistry.register(new GTCEuMultiblockProvider());
        }
    }
}
