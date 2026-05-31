package net.phoenixvine.phantasia.client.render;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantasiaTrackedDummyWorld extends TrackedDummyWorld {

    // ── Particle routing ──────────────────────────────────────────────────────

    /**
     * Routes animateTick particle spawns directly to mc.particleEngine.
     *
     * LDLib DummyWorld.addParticle() cannot construct Particle objects from
     * ParticleOptions — that requires ParticleProvider lookups only present in
     * mc.particleEngine. Routing here ensures particles from animateTick reach
     * the same engine GT BER particles use (via the particleProxyLevel swap in
     * drawTileEntities), so mc.particleEngine.render() draws them each frame.
     */
    @Override
    public void addParticle(ParticleOptions particleData,
                            double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.particleEngine != null) {
            try {
                mc.particleEngine.createParticle(particleData, x, y, z, xSpeed, ySpeed, zSpeed);
            } catch (Exception ignored) {
                // Provider not registered for this particle type — skip silently.
            }
        }
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions particleData,
                                         double x, double y, double z,
                                         double xSpeed, double ySpeed, double zSpeed) {
        addParticle(particleData, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    // ── animateTick with hasTicker guard bypass ───────────────────────────────

    /**
     * Drives one ambient animation tick for the block at {@code pos}.
     *
     * The hasTicker guard bypass: TFG's ActiveParticleBlock with hasTicker=true
     * checks level.getBlockEntity(pos) != null and returns early if so. The
     * dummy world has real BEs registered, so we temporarily remove the BE
     * during the animateTick call and restore it in a finally block.
     *
     * Exceptions from individual blocks (e.g. failed particle type lazy
     * resolution) are allowed to propagate — the caller (tickAmbientEffects)
     * wraps each call in its own try/catch so one bad block doesn't kill others.
     */
    public void tickAnimateForPos(BlockPos pos, RandomSource random) {
        BlockState state = getBlockState(pos);
        if (state.isAir()) return;

        // Guard: spawnClient() in TFG blocks bails if level.isClientSide is false.
        // TrackedDummyWorld inherits isClientSide=true from LDLib DummyWorld, but
        // guard here explicitly in case a subclass or mixin changes it.
        if (!this.isClientSide) return;

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
