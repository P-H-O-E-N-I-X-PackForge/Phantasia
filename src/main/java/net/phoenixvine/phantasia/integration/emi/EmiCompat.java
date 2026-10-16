package net.phoenixvine.phantasia.integration.emi;

import net.neoforged.fml.ModList;

/**
 * Whether EMI is loaded, checked without referencing any {@code dev.emi.emi} class.
 *
 * <p>
 * {@link PhantasiaEmiPlugin} implements EMI's own {@code EmiPlugin} interface, so merely
 * *loading* that class (e.g. to read a static field on it) forces the JVM to resolve
 * {@code dev.emi.emi.api.EmiPlugin}, which doesn't exist when EMI isn't installed -
 * {@code NoClassDefFoundError}, even behind an {@code if}. Callers that just need to know
 * whether EMI is present (to decide whether to show a button, etc.) must check here instead of
 * touching {@link PhantasiaEmiPlugin} at all.
 */
public final class EmiCompat {

    public static final boolean EMI_PRESENT = ModList.get().isLoaded("emi");

    private EmiCompat() {}
}
