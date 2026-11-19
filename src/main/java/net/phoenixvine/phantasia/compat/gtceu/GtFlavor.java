package net.phoenixvine.phantasia.compat.gtceu;

import net.phoenixvine.phantasia.Phantasia;

/**
 * Which GregTech this is running on: real GTM, or Firebird (our fork), which has everything GTM has and more. Firebird
 * keeps GTM's mod id and version, so it is told apart by the classes it adds, one feature at a time, not by a version.
 */
public final class GtFlavor {

    private static final String PREVIEW_EXTENSIONS = "com.gregtechceu.gtceu.integration.recipeviewer.widgets.MultiblockPreviewExtensions";

    private GtFlavor() {}

    /** Whether the multiblock preview can take widgets from other mods. */
    public static boolean hasPreviewExtensions() {
        return present(PREVIEW_EXTENSIONS);
    }

    /** Starts the part of Phantasia that needs Firebird's GregTech, if this is it. Safe on real GTM. */
    public static void initFirebirdSupport() {
        if (!hasPreviewExtensions()) {
            Phantasia.LOGGER.info("[Phantasia] GregTech without Firebird's extras, using the shared features only");
            return;
        }
        try {
            ((FirebirdBridge) Class.forName("net.phoenixvine.phantasia.firebird.FirebirdSupport")
                    .getDeclaredConstructor().newInstance()).init();
            Phantasia.LOGGER.info("[Phantasia] Firebird's GregTech features are active");
        } catch (ClassNotFoundException e) {
            Phantasia.LOGGER.warn("[Phantasia] This build has no Firebird support, using the shared features only");
        } catch (Throwable t) {
            Phantasia.LOGGER.error("[Phantasia] Could not start the Firebird support", t);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, GtFlavor.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
