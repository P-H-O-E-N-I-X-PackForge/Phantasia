package net.phoenixvine.phantasia;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.phoenixvine.phantasia.client.PhantasiaClient;
import net.phoenixvine.phantasia.client.keybind.PhoenixKeybinds;
import net.phoenixvine.phantasia.compat.arsnouveaucompat.PhantasiaArsCompat;
import net.phoenixvine.phantasia.configs.PhantasiaConfigs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Phantasia.MOD_ID)
public class Phantasia {

    public static final String MOD_ID = "phantasia";
    public static final Logger LOGGER = LogManager.getLogger();

    public static net.phoenixvine.phantasia.compat.custom.PhantasiaCustomProvider CUSTOM_PROVIDER;

    public Phantasia(IEventBus modEventBus, ModContainer container) {
        PhantasiaConfigs.init();

        net.phoenixvine.phantasia.common.PhantasiaItems.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(PhoenixKeybinds::register);
            PhantasiaClient.init(modEventBus);

            net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry.register(
                    new net.phoenixvine.phantasia.compat.vanilla.VanillaMultiblockProvider());

            CUSTOM_PROVIDER = new net.phoenixvine.phantasia.compat.custom.PhantasiaCustomProvider();
            net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry.register(CUSTOM_PROVIDER);

            if (ModList.get().isLoaded("ars_nouveau")) {
                PhantasiaArsCompat.init();
            }

            if (ModList.get().isLoaded("ae2")) {
                net.phoenixvine.phantasia.compat.ae2.PhantasiaAE2Compat.init();
            }

            if (ModList.get().isLoaded("immersiveengineering")) {
                net.phoenixvine.phantasia.compat.ie.PhantasiaIECompat.init();
            }

            boolean ipLoaded = false;
            try {
                Class.forName("flaxbeard.immersivepetroleum.ImmersivePetroleum");
                ipLoaded = true;
            } catch (ClassNotFoundException ignored) {}
            if (ModList.get().isLoaded("immersiveengineering") && ipLoaded) {
                net.phoenixvine.phantasia.compat.ip.PhantasiaIPCompat.init();
            }

            if (ModList.get().isLoaded("tfc")) {
                net.phoenixvine.phantasia.compat.tfc.PhantasiaTFCCompat.init();
            }

            if (ModList.get().isLoaded("create")) {
                net.phoenixvine.phantasia.compat.create.PhantasiaCreateCompat.init();
            }

            boolean eaeLoaded = false;
            try {
                Class.forName("com.glodblock.github.extendedae.ExtendedAE");
                eaeLoaded = true;
            } catch (ClassNotFoundException ignored) {}
            if (ModList.get().isLoaded("ae2") && eaeLoaded) {
                net.phoenixvine.phantasia.compat.eae.PhantasiaEAECompat.init();
            }

            if (ModList.get().isLoaded("advanced_ae")) {
                net.phoenixvine.phantasia.compat.aae.PhantasiaAAECompat.init();
            }
        }
        modEventBus.addListener(this::clientSetup);

        if (ModList.get().isLoaded("gtceu")) {
            net.phoenixvine.phantasia.compat.gtceu.PhantasiaGTCompat.init(modEventBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Phantasia] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[Phantasia] Client setup on Minecraft {}", Minecraft.getInstance().getLaunchedVersion());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
