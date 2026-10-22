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
     * Lays the machine's default pattern out the way GregTech's own preview does: minimum repetitions for repeatable
     * slices, smallest bounds for expandable patterns, controller facing its default direction.
     */
    static PhantasiaBlockInfo[][][] buildPreview(MultiblockMachineDefinition definition) {
        Supplier<IBlockPattern> supplier = definition.getStructurePatterns().get("default");
        if (supplier == null) {
            if (definition.getStructurePatterns().isEmpty()) return null;
            supplier = definition.getStructurePatterns().values().iterator().next();
        }
        IBlockPattern pattern = supplier.get();
        if (pattern == null) return null;

        AbstractStructureHelper helper;
        if (pattern instanceof BlockPattern blockPattern) {
            Int2IntMap repeats = new Int2IntArrayMap();
            var slices = blockPattern.getSlices();
            for (int i = 0; i < slices.length; i++) repeats.put(i, slices[i].getMinRepeats());
            helper = AbstractStructureHelper.blockPattern(repeats);
        } else if (pattern instanceof ExpandablePattern expandable) {
            IntList dims = new IntArrayList();
            expandable.getBoundsConstraints().apply().stream().mapToInt(c -> c.leftInt()).forEach(dims::add);
            helper = AbstractStructureHelper.expandable(dims);
        } else {
            return null;
        }

        RotationState rotation = definition.getRotationState();
        Direction front = rotation.defaultDirection;
        Direction up = rotation == RotationState.Y_AXIS ? Direction.NORTH : Direction.UP;
        if (front.getAxis().isVertical() && up.getAxis().isVertical()) up = Direction.NORTH;

        Map<BlockPos, BlockInfo> blocks = new HashMap<>();
        helper.populate(new MultiblockSchemaInfo(definition), blocks, pattern, null, front, up, false);
        if (blocks.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        PhantasiaBlockInfo[][][] out = new PhantasiaBlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        for (var column : out) for (var row : column) java.util.Arrays.fill(row, PhantasiaBlockInfo.EMPTY);
        for (var entry : blocks.entrySet()) {
            BlockInfo info = entry.getValue();
            if (info == null || info.isAir()) continue;
            BlockPos p = entry.getKey();
            out[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = PhantasiaBlockInfo
                    .fromBlockState(info.getBlockState());
        }
        return out;
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
