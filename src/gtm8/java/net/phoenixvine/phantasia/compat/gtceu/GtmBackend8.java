package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.mui.MultiblockSchemaInfo;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The reworked GregTech 8.x development API. Compiled in the {@code gtm8} source set and created by
 * {@link GtmBackends} only when that API is installed.
 */
public class GtmBackend8 implements GtmBackend {

    @Override
    public void bootstrap(IEventBus modEventBus) {
        // GTRegistrate.create(...) already hooks itself up in 8.x, and there is no per-mod material registry to add.
    }

    @Override
    public IPhantasiaMultiblockDefinition createDefinition(MultiblockMachineDefinition definition) {
        return new GTCEuMultiblockDefinition8(definition);
    }

    @Override
    public PhantasiaBlockInfo[][][] previewBlocks(MultiblockMachineDefinition definition) {
        return buildPreview(definition);
    }

    /**
     * Lays all of the machine's patterns (default + substructures) out and merges them into one preview: minimum
     * repetitions for repeatable slices, smallest bounds for expandable patterns, controller facing its default
     * direction.
     */
    static PhantasiaBlockInfo[][][] buildPreview(MultiblockMachineDefinition definition) {
        Map<String, Supplier<IBlockPattern>> patterns = definition.getStructurePatterns();
        if (patterns.isEmpty()) return null;

        RotationState rotation = definition.getRotationState();
        Direction front = rotation.defaultDirection;
        Direction up = rotation == RotationState.Y_AXIS ? Direction.NORTH : Direction.UP;
        if (front.getAxis().isVertical() && up.getAxis().isVertical()) up = Direction.NORTH;

        MultiblockSchemaInfo schema = new MultiblockSchemaInfo(definition);

        // Merge blocks from all patterns (default + all substructures)
        Map<BlockPos, BlockInfo> allBlocks = new HashMap<>();
        for (Supplier<IBlockPattern> supplier : patterns.values()) {
            IBlockPattern pattern = supplier.get();
            if (pattern == null) continue;
            AbstractStructureHelper helper = makeHelper(pattern);
            if (helper == null) continue;
            helper.populate(schema, allBlocks, pattern, null, front, up, false);
        }
        if (allBlocks.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : allBlocks.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        PhantasiaBlockInfo[][][] out = new PhantasiaBlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        for (var column : out) for (var row : column) java.util.Arrays.fill(row, PhantasiaBlockInfo.EMPTY);
        for (var entry : allBlocks.entrySet()) {
            BlockInfo info = entry.getValue();
            if (info == null || info.isAir()) continue;
            BlockPos p = entry.getKey();
            out[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = PhantasiaBlockInfo
                    .fromBlockState(info.getBlockState());
        }
        return out;
    }

    private static AbstractStructureHelper makeHelper(IBlockPattern pattern) {
        if (pattern instanceof BlockPattern blockPattern) {
            Int2IntMap repeats = new Int2IntArrayMap();
            var slices = blockPattern.getSlices();
            for (int i = 0; i < slices.length; i++) repeats.put(i, slices[i].getMinRepeats());
            return AbstractStructureHelper.blockPattern(repeats);
        } else if (pattern instanceof ExpandablePattern expandable) {
            IntList dims = new IntArrayList();
            expandable.getBoundsConstraints().apply().stream().mapToInt(c -> c.leftInt()).forEach(dims::add);
            return AbstractStructureHelper.expandable(dims);
        }
        return null;
    }

    @Override
    public boolean isMachineEntity(BlockEntity be) {
        return be instanceof MetaMachine;
    }

    @Override
    public void drawDynamicRenderers(PhantasiaTrackedDummyWorld world, Set<BlockPos> frontTileEntities,
                                     BlockPos slotOrigin, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                     float partial, float camX, float camY, float camZ) {
        // 8.x machines render through their block entities, which Phantasia already draws, so nothing extra here.
    }

    static boolean isController(BlockEntity be) {
        return be instanceof MultiblockControllerMachine;
    }
}
