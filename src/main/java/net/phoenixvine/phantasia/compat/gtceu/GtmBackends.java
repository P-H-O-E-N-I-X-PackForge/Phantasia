package net.phoenixvine.phantasia.compat.gtceu;

/**
 * Picks the {@link GtmBackend} for the installed GregTech. Decided by a class only the reworked 8.x development API
 * has, not by the version number - the 8.0.0 release still has the classic API. The implementations are created by
 * name so the unused one is never loaded.
 */
public final class GtmBackends {

    private static final String MARKER = "com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData";
    private static final String PKG = "net.phoenixvine.phantasia.compat.gtceu.";

    private static GtmBackend instance;

    private GtmBackends() {}

    public static boolean usesNewApi() {
        try {
            Class.forName(MARKER, false, GtmBackends.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static synchronized GtmBackend get() {
        if (instance == null) {
            String name = PKG + (usesNewApi() ? "GtmBackend8" : "GtmBackend7");
            try {
                instance = (GtmBackend) Class.forName(name).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not load Phantasia's GregTech backend " + name, e);
            }
        }
        return instance;
    }
}
