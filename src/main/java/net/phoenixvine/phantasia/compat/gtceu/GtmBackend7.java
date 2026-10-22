package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;
import java.util.Set;

/** GregTech 7.x and the 8.0.0 release. Created by {@link GtmBackends}. */
public class GtmBackend7 implements GtmBackend {

    @Override
    public void bootstrap(IEventBus modEventBus) {
        PhantasiaGTCompat.PHANTASIA_REGISTRATE.registerRegistrate();
        modEventBus.addListener(GtmBackend7::addMaterialRegistries);
    }

    private static void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(Phantasia.MOD_ID);
    }

    @Override
    public IPhantasiaMultiblockDefinition createDefinition(MultiblockMachineDefinition definition) {
        return new GTCEuMultiblockDefinition(definition);
    }

    @Override
    public PhantasiaBlockInfo[][][] previewBlocks(MultiblockMachineDefinition definition) {
        List<?> shapes = definition.getMatchingShapes();
        if (shapes == null || shapes.isEmpty()) return null;
        return new GTCEuMultiblockShape(definition.getMatchingShapes().get(0)).getBlocks();
    }

    @Override
    public boolean isMachineEntity(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity;
    }

    @Override
    public void drawDynamicRenderers(PhantasiaTrackedDummyWorld world, Set<BlockPos> frontTileEntities,
                                     BlockPos slotOrigin, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                     float partial, float camX, float camY, float camZ) {
        GTCEuDynamicRenderHelper.drawDynamicRenderers(world, frontTileEntities, slotOrigin, poseStack, buffers,
                partial, camX, camY, camZ);
    }
}
