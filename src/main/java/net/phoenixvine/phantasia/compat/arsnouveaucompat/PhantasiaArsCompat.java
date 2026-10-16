package net.phoenixvine.phantasia.compat.arsnouveaucompat;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.PhantasiaMultiblockRegistry;
import net.phoenixvine.phantasia.common.multisetup.PhantasiaMultiSetupRegistry;

public final class PhantasiaArsCompat {

    private static ArsNouveauSetupProvider provider;

    private PhantasiaArsCompat() {}

    public static void init() {
        provider = new ArsNouveauSetupProvider();
        PhantasiaMultiSetupRegistry.register(provider);
        PhantasiaMultiblockRegistry.register(new ArsNouveauMultiblockProvider());

        NeoForge.EVENT_BUS.addListener(PhantasiaArsCompat::onReload);

        Phantasia.LOGGER.info("[Phantasia] Ars Nouveau compat initialized.");
    }

    private static void onReload(AddReloadListenerEvent event) {
        if (provider != null) provider.invalidateAll();
    }

    /** Whether {@code be} is an Ars Nouveau source jar. */
    public static boolean isSourceJar(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
    }

    /** Fills an Ars Nouveau source jar; does nothing for any other block entity. */
    public static void setSource(net.minecraft.world.level.block.entity.BlockEntity be, int amount) {
        if (be instanceof com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile jar) jar.setSource(amount);
    }
}
