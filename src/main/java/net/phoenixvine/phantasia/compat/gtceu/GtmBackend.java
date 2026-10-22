package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Set;

/**
 * Everything Phantasia needs from GregTech whose API differs between the 7.x line (and the 8.0.0 release) and the
 * reworked 8.x development API. Implementations are picked by {@link GtmBackends}; the rest of Phantasia only talks
 * to this interface and never names a GregTech type that changed.
 */
public interface GtmBackend {

    /** Registers Phantasia's registrate and GregTech event hooks. */
    void bootstrap(IEventBus modEventBus);

    IPhantasiaMultiblockDefinition createDefinition(MultiblockMachineDefinition definition);

    /** The machine's default fully-built shape as {@code [x][y][z]}, or null when it has none. */
    PhantasiaBlockInfo[][][] previewBlocks(MultiblockMachineDefinition definition);

    /** Whether this block entity is a GregTech machine that needs its level set before use. */
    boolean isMachineEntity(BlockEntity be);

    void drawDynamicRenderers(PhantasiaTrackedDummyWorld world, Set<BlockPos> frontTileEntities, BlockPos slotOrigin,
                              PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                              float camX, float camY, float camZ);
}
