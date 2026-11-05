package net.phoenixvine.phantasia.compat.gtceu;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.common.data.pattern.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.data.script.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.data.variant.PhantasiaVariantGroup;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class GTCEuMultiblockDefinition implements IPhantasiaMultiblockDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(GTCEuMultiblockDefinition.class);

    private final MultiblockMachineDefinition definition;
    private List<IPhantasiaMultiblockShape> cachedShapes;

    public GTCEuMultiblockDefinition(MultiblockMachineDefinition definition) {
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
                blocks = previewBlocks(definition);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] Could not build the preview shape of {}: {}", definition.getId(),
                        e.toString());
            }
            final PhantasiaBlockInfo[][][] shape = blocks;
            cachedShapes = shape == null ? List.of() : List.of((IPhantasiaMultiblockShape) () -> shape);
            if (shape != null) {
                int solid = 0;
                for (var a : shape) for (var b : a) for (var c : b) {
                    if (c != null && c.getBlockState() != null && !c.getBlockState().isAir()) solid++;
                }
                LOGGER.info("[Phantasia] Preview of {}: {}x{}x{} with {} blocks", definition.getId(), shape.length,
                        shape.length > 0 ? shape[0].length : 0,
                        shape.length > 0 && shape[0].length > 0 ? shape[0][0].length : 0, solid);
            }
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
                formStructure(ctrl);
                LOGGER.debug("[Phantasia] Structure check run for {}", definition.getId());
            } catch (Exception e) {
                LOGGER.error("[Phantasia] Forming the structure failed for {}: {}", definition.getId(),
                        e.getMessage(), e);
            }
        });
    }

    /**
     * Forms the controller in the scene. GregTech 8's own {@code checkAndFormStructure()} returns at once unless the
     * level is a server level, and the scene's level is a client one, so this runs the same check and form steps it
     * is made of (minus the saved-data bookkeeping, which a scene has no use for).
     */
    private static void formStructure(MultiblockControllerMachine ctrl) {
        for (String name : ctrl.getStructureNames()) {
            var state = ctrl.checkStructurePattern(name);
            if (state != null && state.getState().isValid()) {
                ctrl.formStructure(name);
            } else {
                StringBuilder why = new StringBuilder();
                if (state != null) {
                    for (var err : state.getErrors()) {
                        why.append(" | ").append(err.getClass().getSimpleName()).append(" at ")
                                .append(err.getPos());
                        if (err instanceof com.gregtechceu.gtceu.api.multiblock.error.SinglePredicateError single) {
                            why.append(" [").append(single.type).append(", found ").append(single.actualCount)
                                    .append(", wants ").append(single.predMinCount).append("..")
                                    .append(single.predMaxCount).append(", layer ").append(single.predMinLayerCount)
                                    .append("..").append(single.predMaxLayerCount).append(", ")
                                    .append(single.debugName).append("]");
                        }
                    }
                }
                if (state != null && ctrl.getLevel() != null) {
                    Map<String, Integer> census = new java.util.TreeMap<>();
                    Map<String, List<BlockPos>> where = new java.util.TreeMap<>();
                    BlockPos cp = ctrl.getBlockPos();
                    for (BlockPos q : BlockPos.betweenClosed(cp.offset(-12, -12, -12), cp.offset(12, 12, 12))) {
                        BlockState bs = ctrl.getLevel().getBlockState(q);
                        if (bs.isAir()) continue;
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock())
                                .toString();
                        census.merge(id, 1, Integer::sum);
                        if (bs.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock) {
                            where.computeIfAbsent(id, k -> new ArrayList<>()).add(q.immutable());
                        }
                    }
                    LOGGER.warn("[Phantasia] {} scene census around controller {} (facing {}): {} | machines at {}",
                            ctrl.getDefinition().getId(), cp, ctrl.getFrontFacing(), census, where);
                }
                LOGGER.warn(
                        "[Phantasia] {} did not pass its structure check ({}) in the scene: it will render unformed{}",
                        ctrl.getDefinition().getId(), state == null ? "no pattern" : state.getState(), why);
            }
        }
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

    @Override
    public List<PhantasiaVariantGroup> detectProviderVariants(PhantasiaScriptData data, PhantasiaLoadedPattern pattern,
                                                              String machinePrefix, Set<String> explicitIds) {
        List<PhantasiaVariantGroup> result = new ArrayList<>(substructureToggles(machinePrefix, pattern));
        result.addAll(detectPredicateVariants(machinePrefix, pattern, explicitIds));
        return result;
    }

    @Override
    public Map<String, Set<BlockPos>> getSubstructures() {
        Preview preview = buildPreview(definition);
        return preview == null ? Map.of() : preview.substructures();
    }

    /** One on/off switch per named structure pattern, when the machine has more than one. */
    private List<PhantasiaVariantGroup> substructureToggles(String machinePrefix, PhantasiaLoadedPattern pattern) {
        Map<String, Set<BlockPos>> subs = getSubstructures();
        if (subs.size() < 2) return List.of();
        List<PhantasiaVariantGroup> out = new ArrayList<>();
        for (var entry : subs.entrySet()) {
            Set<BlockPos> world = new java.util.HashSet<>();
            for (BlockPos local : entry.getValue()) {
                BlockPos w = pattern.localToWorld.get(local);
                if (w != null) world.add(w);
            }
            if (world.isEmpty()) continue;
            out.add(PhantasiaVariantGroup.createToggle(machinePrefix + "::sub_" + entry.getKey(),
                    prettySubstructure(entry.getKey()) + " (" + world.size() + " blocks)", world));
        }
        return out.size() < 2 ? List.of() : out;
    }

    private static String prettySubstructure(String name) {
        if (name == null || name.isEmpty()) return "Structure";
        String spaced = name.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private List<PhantasiaVariantGroup> detectPredicateVariants(String machinePrefix, PhantasiaLoadedPattern pattern,
                                                                Set<String> excludeIds) {
        Preview preview = buildPreview(definition);
        if (preview == null) return List.of();

        Map<String, List<BlockPos>> sigToLocal = new LinkedHashMap<>();
        Map<String, List<PhantasiaBlockInfo[]>> sigToCandidates = new LinkedHashMap<>();

        for (Map.Entry<BlockPos, BlockInfo> entry : preview.raw().entrySet()) {
            BlockInfo placed = entry.getValue();
            if (placed == null || placed.isAir()) continue;
            BlockPos at = entry.getKey();

            List<List<BlockInfo>> candidates;
            try {
                var predicate = preview.helper().getPredicateFromPos(preview.pattern(), at, preview.front(),
                        preview.up(), false);
                if (predicate == null) continue;
                candidates = predicate.getCandidates();
            } catch (Exception e) {
                continue;
            }
            if (candidates == null || candidates.size() < 2) continue;

            List<PhantasiaBlockInfo[]> componentCandidates = new ArrayList<>();
            boolean valid = true;
            for (List<BlockInfo> component : candidates) {
                if (component == null || component.isEmpty()) {
                    valid = false;
                    break;
                }
                PhantasiaBlockInfo[] cands = new PhantasiaBlockInfo[component.size()];
                for (int ci = 0; ci < cands.length; ci++) {
                    BlockInfo bi = component.get(ci);
                    cands[ci] = bi != null ? PhantasiaBlockInfo.fromBlockState(bi.getBlockState()) :
                            PhantasiaBlockInfo.EMPTY;
                }
                componentCandidates.add(cands);
            }
            if (!valid) continue;

            List<String> sigParts = new ArrayList<>();
            for (PhantasiaBlockInfo[] cands : componentCandidates) {
                List<String> ids = new ArrayList<>();
                for (PhantasiaBlockInfo bi : cands) {
                    if (bi == null || bi.getBlockState() == null) continue;
                    var rl = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(bi.getBlockState().getBlock());
                    if (rl != null) ids.add(rl.toString());
                }
                Collections.sort(ids);
                sigParts.add(String.join(",", ids));
            }
            Collections.sort(sigParts);
            String sig = String.join("|", sigParts);

            BlockPos local = at.subtract(preview.min());
            sigToLocal.computeIfAbsent(sig, k -> new ArrayList<>()).add(local);
            sigToCandidates.putIfAbsent(sig, componentCandidates);
        }

        List<PhantasiaVariantGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<BlockPos>> entry : sigToLocal.entrySet()) {
            String sig = entry.getKey();

            String groupId = machinePrefix + "::predicate_" + Integer.toUnsignedString(sig.hashCode(), 16);
            if (excludeIds.contains(groupId)) continue;

            List<PhantasiaBlockInfo[]> componentCandidates = sigToCandidates.get(sig);
            List<BlockState> options = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (PhantasiaBlockInfo[] cands : componentCandidates) {
                BlockState rep = null;
                for (PhantasiaBlockInfo bi : cands) {
                    if (bi != null && bi.getBlockState() != null && !bi.getBlockState().isAir()) {
                        rep = bi.getBlockState();
                        break;
                    }
                }
                if (rep == null) continue;
                options.add(rep);
                labels.add(PhantasiaVariantGroup.blockDisplayName(rep));
            }
            if (options.size() < 2) continue;

            boolean allParts = options.stream().allMatch(
                    s -> s.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock);
            if (allParts) continue;

            Map<BlockPos, Integer> posMap = new HashMap<>();
            int defaultIdx = 0;
            boolean foundDefault = false;

            for (BlockPos local : entry.getValue()) {
                BlockPos world = pattern.localToWorld.get(local);
                if (world == null) continue;
                PhantasiaBlockInfo info = pattern.blockMap.get(world);
                if (info == null || info.getBlockState() == null) {
                    posMap.put(world, 0);
                    continue;
                }
                Block loaded = info.getBlockState().getBlock();

                int idx = 0;
                outer:
                for (int ci = 0; ci < componentCandidates.size(); ci++) {
                    for (PhantasiaBlockInfo bi : componentCandidates.get(ci)) {
                        if (bi != null && bi.getBlockState() != null && bi.getBlockState().getBlock() == loaded) {
                            idx = ci;
                            break outer;
                        }
                    }
                }
                posMap.put(world, idx);
                if (!foundDefault) {
                    defaultIdx = idx;
                    foundDefault = true;
                }
            }
            if (posMap.isEmpty()) continue;

            result.add(PhantasiaVariantGroup.create(
                    groupId,
                    labels.get(0) + " / " + labels.get(1) + (options.size() > 2 ? " …" : ""),
                    PhantasiaVariantGroup.Category.OPTIONAL,
                    true, options, labels, posMap, defaultIdx));
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof GTCEuMultiblockDefinition other && definition.equals(other.definition));
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    @Override
    public String toString() {
        return "GTCEuMultiblockDefinition[" + definition.getId() + "]";
    }

    private record Preview(PhantasiaBlockInfo[][][] blocks, Map<BlockPos, BlockInfo> raw, BlockPos min,
                           AbstractStructureHelper helper, IBlockPattern pattern, Direction front, Direction up,
                           Map<String, Set<BlockPos>> substructures) {}

    public static PhantasiaBlockInfo[][][] previewBlocks(MultiblockMachineDefinition definition) {
        Preview preview = buildPreview(definition);
        return preview == null ? null : preview.blocks();
    }

    private static Preview buildPreview(MultiblockMachineDefinition definition) {
        Map<String, Supplier<IBlockPattern>> patterns = definition.getStructurePatterns();
        if (patterns.isEmpty()) {
            LOGGER.warn("[Phantasia] {} has no structure patterns: nothing to preview", definition.getId());
            return null;
        }

        RotationState rotation = definition.getRotationState();
        Direction front = rotation.defaultDirection;
        Direction up = rotation == RotationState.Y_AXIS ? Direction.NORTH : Direction.UP;
        if (front.getAxis().isVertical() && up.getAxis().isVertical()) up = Direction.NORTH;

        // Use "default" as the primary pattern for the Preview record's metadata fields
        Supplier<IBlockPattern> primarySupplier = patterns.getOrDefault("default",
                patterns.values().iterator().next());
        IBlockPattern primaryPattern = primarySupplier.get();
        if (primaryPattern == null) {
            LOGGER.warn("[Phantasia] {}: the primary pattern supplier returned null", definition.getId());
            return null;
        }

        AbstractStructureHelper primaryHelper = makeHelper(primaryPattern);
        if (primaryHelper == null) {
            LOGGER.warn("[Phantasia] {}: pattern type {} is not supported by the preview", definition.getId(),
                    primaryPattern.getClass().getName());
            return null;
        }

        // Merge blocks from all patterns (default + all substructures)
        Map<BlockPos, BlockInfo> allBlocks = new HashMap<>();
        Map<String, Set<BlockPos>> placedBy = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<IBlockPattern>> named : patterns.entrySet()) {
            IBlockPattern pat = named.getValue().get();
            if (pat == null) continue;
            AbstractStructureHelper h = makeHelper(pat);
            if (h == null) continue;
            Map<BlockPos, BlockInfo> own = new HashMap<>();
            h.populate(own, pat, new Long2ObjectOpenHashMap<>(), front, up, false);
            try {
                enforceLimits(h, pat, own, front, up);
            } catch (Exception e) {
                LOGGER.warn("[Phantasia] {}: could not enforce the hatch limits of the preview: {}",
                        definition.getId(), e.toString());
            }
            Set<BlockPos> ownPositions = new java.util.HashSet<>();
            for (var e : own.entrySet())
                if (e.getValue() != null && !e.getValue().isAir()) ownPositions.add(e.getKey());
            placedBy.put(named.getKey(), ownPositions);
            allBlocks.putAll(own);
            if (own.isEmpty()) {
                LOGGER.warn("[Phantasia] {}: a {} pattern placed no blocks (front {}, up {})", definition.getId(),
                        pat.getClass().getSimpleName(), front, up);
            }
        }
        if (allBlocks.isEmpty()) {
            LOGGER.warn("[Phantasia] {}: no pattern placed any block, the preview is empty", definition.getId());
            return null;
        }

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

        BlockPos min = new BlockPos(minX, minY, minZ);
        PhantasiaBlockInfo[][][] out = new PhantasiaBlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        for (var column : out) for (var row : column) java.util.Arrays.fill(row, PhantasiaBlockInfo.EMPTY);
        for (var entry : allBlocks.entrySet()) {
            BlockInfo info = entry.getValue();
            if (info == null || info.isAir()) continue;
            BlockPos p = entry.getKey();
            out[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = PhantasiaBlockInfo
                    .fromBlockState(info.getBlockState());
        }
        Map<String, Set<BlockPos>> localBySub = new LinkedHashMap<>();
        for (var e : placedBy.entrySet()) {
            Set<BlockPos> local = new java.util.HashSet<>();
            for (BlockPos p : e.getValue()) local.add(p.subtract(min));
            localBySub.put(e.getKey(), local);
        }
        return new Preview(out, allBlocks, min, primaryHelper, primaryPattern, front, up, localBySub);
    }

    /**
     * GregTech's preview filler can place a limited hatch (one with a maximum count) far more often than its limit
     * allows, which makes the scene fail its own structure check and render unformed. This walks the placed blocks
     * in order and swaps any that are over their predicate's maximum for the first unlimited option of the same
     * position, normally the casing.
     */
    private static void enforceLimits(AbstractStructureHelper helper, IBlockPattern pattern,
                                      Map<BlockPos, BlockInfo> blocks, Direction front, Direction up) {
        Map<Object, Integer> counts = new java.util.IdentityHashMap<>();
        List<BlockPos> order = new ArrayList<>(blocks.keySet());
        order.sort(java.util.Comparator.<BlockPos>comparingInt(q -> q.getX()).thenComparingInt(q -> q.getY())
                .thenComparingInt(q -> q.getZ()));
        for (BlockPos at : order) {
            BlockInfo placed = blocks.get(at);
            if (placed == null || placed.isAir()) continue;
            var predicate = helper.getPredicateFromPos(pattern, at, front, up, false);
            if (predicate == null) continue;
            List<com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate> flat = new ArrayList<>();
            collect(predicate, flat);

            com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate owner = null;
            for (var base : flat) {
                if (base.getCandidates().stream().anyMatch(ci -> ci != null && ci.getBlockState() != null &&
                        ci.getBlockState().getBlock() == placed.getBlockState().getBlock())) {
                    owner = base;
                    break;
                }
            }
            if (placed.getBlockState().getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock) {
                StringBuilder dbg = new StringBuilder();
                for (var base : flat) {
                    dbg.append(base.getClass().getSimpleName()).append("(max ").append(base.getMaxCount())
                            .append(", min ").append(base.getMinCount()).append(", cands ")
                            .append(base.getCandidates().size()).append(base == owner ? ", OWNER" : "").append(") ");
                }
                LOGGER.info("[Phantasia] limits at {} holding {}: {}", at,
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlockState().getBlock()),
                        dbg);
            }
            if (owner == null || owner.getMaxCount() < 0) continue;
            int n = counts.merge(owner, 1, Integer::sum);
            if (n <= owner.getMaxCount()) continue;

            for (var base : flat) {
                if (base == owner || base.getMaxCount() >= 0) continue;
                var first = base.getFirstCandidate();
                if (first.isPresent() && !first.get().isAir()) {
                    blocks.put(at, first.get());
                    break;
                }
            }
        }
    }

    private static void collect(com.gregtechceu.gtceu.api.multiblock.MultiPredicate predicate,
                                List<com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate> out) {
        out.addAll(predicate.predicates());
        for (var child : predicate.children()) collect(child, out);
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
}
