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

public interface GtmBackend {

    void bootstrap(IEventBus modEventBus);

    IPhantasiaMultiblockDefinition createDefinition(MultiblockMachineDefinition definition);

    PhantasiaBlockInfo[][][] previewBlocks(MultiblockMachineDefinition definition);

    boolean isMachineEntity(BlockEntity be);

    void drawDynamicRenderers(PhantasiaTrackedDummyWorld world, Set<BlockPos> frontTileEntities, BlockPos slotOrigin,
                              PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partial,
                              float camX, float camY, float camZ);
}
