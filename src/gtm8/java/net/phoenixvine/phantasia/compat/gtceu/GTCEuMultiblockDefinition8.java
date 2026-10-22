package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link IPhantasiaMultiblockDefinition} for the reworked GregTech 8.x API. */
public class GTCEuMultiblockDefinition8 implements IPhantasiaMultiblockDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(GTCEuMultiblockDefinition8.class);

    private final MultiblockMachineDefinition definition;
    private List<IPhantasiaMultiblockShape> cachedShapes;

    public GTCEuMultiblockDefinition8(MultiblockMachineDefinition definition) {
        this.definition = definition;
    }

    @Override
    public ResourceLocation getId() {
        return definition.getId();
    }

    @Override
    public List<IPhantasiaMultiblockShape> getMatchingShapes() {
        if (cachedShapes == null) {
            PhantasiaBlockInfo[][][] blocks = null;
            try {
                blocks = GtmBackend8.buildPreview(definition);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] Could not build the preview shape of {}: {}", definition.getId(),
                        e.toString());
            }
            final PhantasiaBlockInfo[][][] shape = blocks;
            cachedShapes = shape == null ? List.of() : List.of((IPhantasiaMultiblockShape) () -> shape);
        }
        return cachedShapes;
    }

    @Override
    public List<IPhantasiaMultiblockShape> getAllShapes() {
        return getMatchingShapes();
    }

    @Override
    public String getDisplayName() {
        try {
            return definition.getBlock().getName().getString();
        } catch (Exception e) {
            return definition.getId().getPath();
        }
    }

    @Override
    public ItemStack getIcon() {
        try {
            return new ItemStack(definition.getBlock());
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void onShapeLoaded(PhantasiaTrackedDummyWorld level, BlockPos origin,
                              Map<BlockPos, PhantasiaBlockInfo> blockMap, Map<BlockPos, BlockPos> localToWorld) {
        MultiblockControllerMachine controller = null;
        for (BlockPos wp : localToWorld.values()) {
            try {
                if (level.getBlockEntity(wp) instanceof MultiblockControllerMachine ctrl) {
                    controller = ctrl;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (controller == null) return;

        final MultiblockControllerMachine ctrl = controller;
        RenderSystem.recordRenderCall(() -> {
            try {
                ctrl.checkAndFormStructure();
                LOGGER.debug("[Phantasia] Structure check run for {}", definition.getId());
            } catch (Exception e) {
                LOGGER.error("[Phantasia] Forming the structure failed for {}: {}", definition.getId(),
                        e.getMessage(), e);
            }
        });
    }

    @Override
    public void setMachineWorking(PhantasiaTrackedDummyWorld level, boolean working) {
        if (level == null) return;
        try {
            for (var be : level.blockEntities.values()) {
                if (!(be instanceof WorkableMultiblockMachine workable)) continue;
                RecipeLogic logic = workable.getRecipeLogic();
                if (logic != null) logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
                return;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void applyWorkingState(PhantasiaTrackedDummyWorld level, Set<BlockPos> positions,
                                  Map<BlockPos, PhantasiaBlockInfo> blockMap, boolean working) {
        if (level == null) return;

        try {
            for (var entry : level.blockEntities.entrySet()) {
                if (!positions.contains(entry.getKey())) continue;
                RecipeLogic logic = null;
                if (entry.getValue() instanceof WorkableMultiblockMachine wm) {
                    logic = wm.getRecipeLogic();
                } else if (entry.getValue() instanceof WorkableTieredMachine wt) {
                    logic = wt.getRecipeLogic();
                }
                if (logic != null) logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
            }
        } catch (Throwable ignored) {}

        for (var e : blockMap.entrySet()) {
            BlockPos worldPos = e.getKey();
            if (!positions.contains(worldPos)) continue;
            BlockState original = e.getValue().getBlockState();
            if (original == null || original.isAir()) continue;
            try {
                BlockState current = level.getBlockState(worldPos);
                if (current == null || current.isAir()) continue;
                BlockState next = null;
                if (current.getBlock() instanceof ActiveBlock ab) {
                    next = ab.changeActive(current, working);
                } else if (original.getBlock() instanceof ActiveBlock ab) {
                    next = ab.changeActive(current, working);
                }
                if (next != null && next != current)
                    level.renderedBlocks.put(worldPos, PhantasiaBlockInfo.fromBlockState(next));
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Variant detection reads the 7.x predicate grid, which 8.x replaced with character-keyed slices; it is not
     * ported yet, so 8.x machines show no variant groups.
     */
    @Override
    public List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data, PhantasiaLoadedPattern pattern,
                                                              String machinePrefix, Set<String> explicitIds) {
        return List.of();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof GTCEuMultiblockDefinition8 other && definition.equals(other.definition));
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public String toString() {
        return "GTCEuMultiblockDefinition8[" + definition.getId() + "]";
    }
}
