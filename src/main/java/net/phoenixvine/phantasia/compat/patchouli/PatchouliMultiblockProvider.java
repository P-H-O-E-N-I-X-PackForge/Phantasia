package net.phoenixvine.phantasia.compat.patchouli;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockShape;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.common.multiblock.MultiblockRegistry;

import java.util.*;

/**
 * Every multiblock a mod has registered with Patchouli, shown without any code for that mod. Patchouli keeps them in
 * one registry, and each can be simulated into a list of positions and displayed states, which is all a scene needs.
 */
public class PatchouliMultiblockProvider implements IPhantasiaMultiblockProvider {

    private final Map<ResourceLocation, Definition> cache = new HashMap<>();

    @Override
    public String getModId() {
        return "patchouli";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private Definition define(ResourceLocation id, IMultiblock mb) {
        return cache.computeIfAbsent(id, k -> new Definition(id, mb));
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        try {
            ResourceLocation rl = new ResourceLocation(machineId);
            IMultiblock mb = MultiblockRegistry.MULTIBLOCKS.get(rl);
            return mb == null ? Optional.empty() : Optional.of(define(rl, mb));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PhantasiaBlockInfo> resolveBlock(String id) {
        return Optional.empty();
    }

    @Override
    public List<IPhantasiaMultiblockDefinition> getAllDefinitions() {
        List<IPhantasiaMultiblockDefinition> out = new ArrayList<>();
        for (var e : new ArrayList<>(MultiblockRegistry.MULTIBLOCKS.entrySet())) {
            if (e.getKey() != null && e.getValue() != null) out.add(define(e.getKey(), e.getValue()));
        }
        return out;
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        return false;
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        return false;
    }

    static final class Definition implements IPhantasiaMultiblockDefinition {

        private final ResourceLocation id;
        private final IMultiblock multiblock;
        private final Shape shape = new Shape();

        Definition(ResourceLocation id, IMultiblock multiblock) {
            this.id = id;
            this.multiblock = multiblock;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            String path = id.getPath();
            String last = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            String spaced = last.replace('_', ' ');
            return spaced.isEmpty() ? id.toString() : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
        }

        @Override
        public ItemStack getIcon() {
            for (var plane : shape.getBlocks()) for (var row : plane) for (var b : row) {
                if (b != null && b.getBlockState() != null && !b.getBlockState().isAir()) {
                    return new ItemStack(b.getBlockState().getBlock());
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public List<IPhantasiaMultiblockShape> getMatchingShapes() {
            return List.of(shape);
        }

        @Override
        public List<IPhantasiaMultiblockShape> getAllShapes() {
            return List.of(shape);
        }

        private final class Shape implements IPhantasiaMultiblockShape {

            private PhantasiaBlockInfo[][][] cached;

            @Override
            public PhantasiaBlockInfo[][][] getBlocks() {
                if (cached != null) return cached;
                PhantasiaBlockInfo[][][] built = build();
                if (built != null) cached = built;
                return built != null ? built : empty();
            }

            private PhantasiaBlockInfo[][][] empty() {
                PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[1][1][1];
                g[0][0][0] = PhantasiaBlockInfo.EMPTY;
                return g;
            }

            /** Null while there is no level to simulate in (the list of scenes can be built from the title screen). */
            private PhantasiaBlockInfo[][][] build() {
                var level = Minecraft.getInstance().level;
                if (level == null) return null;
                try {
                    var sim = multiblock.simulate(level, BlockPos.ZERO, Rotation.NONE, false);
                    Map<BlockPos, BlockState> states = new HashMap<>();
                    for (var r : sim.getSecond()) {
                        BlockState s = r.getStateMatcher().getDisplayedState(0L);
                        if (s == null || s.isAir() || s.is(Blocks.STRUCTURE_VOID)) continue;
                        states.put(r.getWorldPosition(), s);
                    }
                    if (states.isEmpty()) return null;
                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                    for (BlockPos p : states.keySet()) {
                        minX = Math.min(minX, p.getX());
                        minY = Math.min(minY, p.getY());
                        minZ = Math.min(minZ, p.getZ());
                        maxX = Math.max(maxX, p.getX());
                        maxY = Math.max(maxY, p.getY());
                        maxZ = Math.max(maxZ, p.getZ());
                    }
                    PhantasiaBlockInfo[][][] g = new PhantasiaBlockInfo[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
                    for (var plane : g) for (var row : plane) Arrays.fill(row, PhantasiaBlockInfo.EMPTY);
                    for (var e : states.entrySet()) {
                        BlockPos p = e.getKey();
                        g[p.getX() - minX][p.getY() - minY][p.getZ() - minZ] = PhantasiaBlockInfo
                                .fromBlockState(e.getValue());
                    }
                    return g;
                } catch (Exception e) {
                    return null;
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            return this == o || (o instanceof Definition d && d.id.equals(id));
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
