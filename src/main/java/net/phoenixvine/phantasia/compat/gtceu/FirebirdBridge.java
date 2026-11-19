package net.phoenixvine.phantasia.compat.gtceu;

/**
 * What the {@code firebird} source set provides: the parts of Phantasia that use features only Firebird's GregTech
 * has, such as the multiblock preview extension slots. Nothing in the main source set names a class from that source
 * set; {@link GtFlavor} starts it by name, and only when those features are there.
 */
public interface FirebirdBridge {

    /** Registers what Phantasia adds to the Firebird GregTech's recipe viewer. Called once, on the client. */
    void init();
}
