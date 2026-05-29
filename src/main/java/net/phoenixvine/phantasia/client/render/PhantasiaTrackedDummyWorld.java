package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends TrackedDummyWorld {

    /**
     * Drives one ambient animation tick for the block at {@code pos}.
     *
     * <p>
     * Particles emitted via {@code level.addParticle()} route to this world's
     * LDLib {@code ParticleManager} (set in PhantasiaSceneScreen via
     * {@code setParticleManager}). The renderer calls {@code pm.tick()} then
     * {@code pm.render(camera, partial)} each frame so they are drawn correctly.
     *
     * <p>
     * <b>The hasTicker guard — solved without a TFG dep:</b>
     * Some TFG blocks (ActiveParticleBlock with hasTicker=true) contain:
     *
     * <pre>
     * if (hasTicker &amp;&amp; level.getBlockEntity(pos) != null) return;
     * </pre>
     *
     * in their {@code animateTick}. The dummy world has real BEs registered so
     * that guard always fires. We temporarily remove the BE from the world's
     * internal map during the {@code animateTick} call, then restore it in a
     * {@code finally} block — no compile-time dep on TFG needed.
     */
    public void tickAnimateForPos(BlockPos pos, RandomSource random) {
        BlockState state = getBlockState(pos);
        if (state.isAir()) return;

        BlockEntity hidden = getBlockEntity(pos);
        if (hidden != null) removeBlockEntityForTick(pos);

        try {
            state.getBlock().animateTick(state, this, pos, random);
        } finally {
            if (hidden != null) restoreBlockEntityAfterTick(pos, hidden);
        }
    }

    // ── BE hide/restore via reflection ───────────────────────────────────────

    private static java.lang.reflect.Field beMapField = null;

    private void removeBlockEntityForTick(BlockPos pos) {
        try {
            java.util.Map<?, ?> map = getBlockEntityMap();
            if (map != null) map.remove(pos);
        } catch (Exception ignored) {}
    }

    private void restoreBlockEntityAfterTick(BlockPos pos, BlockEntity be) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<BlockPos, BlockEntity> map = (java.util.Map<BlockPos, BlockEntity>) getBlockEntityMap();
            if (map != null) map.put(pos, be);
        } catch (Exception ignored) {}
    }

    /**
     * Walks the class hierarchy to find the field holding the BE map.
     * Identified as: a Map whose values are BlockEntity instances.
     * Cached after first successful lookup.
     */
    private java.util.Map<?, ?> getBlockEntityMap() {
        if (beMapField == null) {
            for (Class<?> c = this.getClass(); c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (!java.util.Map.class.isAssignableFrom(f.getType())) continue;
                    try {
                        f.setAccessible(true);
                        Object val = f.get(this);
                        if (val instanceof java.util.Map<?, ?> m && !m.isEmpty()) {
                            Object first = m.values().iterator().next();
                            if (first instanceof BlockEntity) {
                                beMapField = f;
                                return m;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        if (beMapField != null) {
            try {
                return (java.util.Map<?, ?>) beMapField.get(this);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
