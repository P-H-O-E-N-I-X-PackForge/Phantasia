package net.phoenixvine.phantasia.client.screens;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaTrackedDummyWorld;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.PhantasiaScript;
import net.phoenixvine.phantasia.common.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.PhantasiaScripts;
import net.phoenixvine.phantasia.utils.PhantasiaThemeUtils;
import net.phoenixvine.phantasia.utils.PhantasiaUIUtils;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;

import static net.phoenixvine.phantasia.utils.PhantasiaThemeUtils.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaSceneScreen extends Screen {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared dummy world
    // ─────────────────────────────────────────────────────────────────────────

    public static PhantasiaTrackedDummyWorld SHARED_LEVEL;
    private static int NEXT_REGION = 0;
    private static final int REGION_SIZE = 512;

    public static void invalidateSharedLevel() {
        SHARED_LEVEL = null;
        NEXT_REGION = 0;
    }

    public static BlockPos getOriginForCurrentPattern() {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof PhantasiaSceneScreen pss && pss.pattern != null)
            return pss.pattern.origin;
        if (mc.screen instanceof PhantasiaFootprintScreen pfs && pfs.getPattern() != null)
            return pfs.getPattern().origin;
        return BlockPos.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final int FULL_PANEL_W = 168;
    private static final int COLLAPSED_PANEL_W = 18;
    private static final int TIMELINE_H = 26;
    private static final int CAPTION_STRIP_H = 22;

    // ─────────────────────────────────────────────────────────────────────────
    // Camera defaults
    // ─────────────────────────────────────────────────────────────────────────

    private static final float CAM_TARGET_Y_BIAS = 0.0f;
    private static final float CAM_DEFAULT_PITCH = 5.0f;
    private static final float CAM_DEFAULT_ZOOM = 40.0f;
    private static final float CAM_ZOOM_IN_FACTOR = 0.9f;
    private static final float CAM_ZOOM_OUT_FACTOR = 1.1f;
    private static final float CAM_ZOOM_MIN = 2.0f;
    private static final float CAM_ZOOM_MAX = 300.0f;
    private static final float CAM_ORBIT_SENSITIVITY = 0.5f;
    private static final float CAM_PAN_SPEED = 0.02f;

    // ─────────────────────────────────────────────────────────────────────────
    // Core state
    // ─────────────────────────────────────────────────────────────────────────

    private final Screen parent;
    public final MultiblockMachineDefinition definition;
    private PhantasiaScript script;

    private PhantasiaLoadedPattern pattern;

    /**
     * Our custom renderer. Created once on first init(), survives re-inits
     * (window resize, sub-screen returns) so VBOs are preserved.
     * Explicitly closed in onClose() and on shape changes.
     */
    private PhantasiaWorldRenderer renderer;

    private int shapeIndex = 0;
    private List<MultiblockShapeInfo> availableShapes = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaCamera camera;
    private boolean isPanning = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Playback
    // ─────────────────────────────────────────────────────────────────────────

    private boolean playing = true;
    private int playbackTick = 0;
    private float tickAccum = 0f;
    private float playbackSpeed = 1.0f;
    private boolean scrubbing = false;
    private PhantasiaScript.Step lastAppliedStep = null;

    // ─────────────────────────────────────────────────────────────────────────
    // View / filter
    // ─────────────────────────────────────────────────────────────────────────

    public enum ViewFilter {
        ALL,
        HATCHES_BUSES,
        ENERGY_IO,
        BLOCK_ENTITIES,
        CONTROLLER
    }

    private ViewFilter viewFilter = ViewFilter.ALL;
    private boolean wasPlayingBeforeFilter = false;
    private int manualLayer = -1;

    private Set<BlockPos> filteredHatchBus = null;
    private Set<BlockPos> filteredEnergyIO = null;
    private Set<BlockPos> filteredHasBE = null;
    private Set<BlockPos> filteredController = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Build-order mode
    // ─────────────────────────────────────────────────────────────────────────

    private boolean buildOrderMode = false;
    private int buildOrderGroup = 0;
    private float buildPulse = 0f;
    private int savedManualLayer = -1;
    private boolean buildPulseUp = true;

    private int coilIndex = 1;

    // Dynamically retrieve all registered coil blocks sorted by their temperature/tier
    private static final List<BlockInfo> COIL_TIERS = java.util.stream.Stream.of(
            com.gregtechceu.gtceu.api.block.ICoilType.ALL_COILS_TEMPERATURE_SORTED.get())
            .map(coil -> {
                // Fetch the block associated with the coil material from the registry
                var block = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.get(coil);
                return new BlockInfo(block.get().defaultBlockState());
            })
            .toList();

    // ─────────────────────────────────────────────────────────────────────────
    // Caption
    // ─────────────────────────────────────────────────────────────────────────

    private float captionAlpha = 0f;
    private String captionCurrent = null;
    private String captionOutgoing = null;
    private float captionOutAlpha = 0f;

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    private final List<PhantasiaUIUtils.ButtonAction> activeButtons = new ArrayList<>();
    private boolean sidePanelCollapsed = false;
    private BlockPos hoveredPos = null;
    private Component pendingTooltip = null;

    public boolean showMistakes = false;
    public int selectedTierIndex = -1;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaSceneScreen(MultiblockMachineDefinition definition, Screen parent) {
        super(Component.literal(definition.getLangValue()));
        this.parent = parent;
        this.definition = definition;
        this.script = PhantasiaScripts.get(definition);
    }

    public void reloadScript() {
        this.script = PhantasiaScripts.get(definition);
        this.lastAppliedStep = null;
        applyVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // init()
    // ─────────────────────────────────────────────────────────────────────────

    private List<BlockInfo> coilTiers = new ArrayList<>();

    @Override
    protected void init() {
        super.init();

        this.coilTiers = java.util.stream.Stream.of(
                com.gregtechceu.gtceu.api.block.ICoilType.ALL_COILS_TEMPERATURE_SORTED.get())
                .map(coil -> {
                    var block = com.gregtechceu.gtceu.api.GTCEuAPI.HEATING_COILS.get(coil);
                    return new BlockInfo(block.get().defaultBlockState());
                })
                .toList();

        if (SHARED_LEVEL == null) {
            if (Minecraft.getInstance().level == null) {
                onClose();
                return;
            }
            SHARED_LEVEL = new PhantasiaTrackedDummyWorld();

            // Fix: Instantiate with no arguments
            SHARED_LEVEL.setParticleManager(new com.lowdragmc.lowdraglib.client.scene.ParticleManager());
        }

        availableShapes = definition.getMatchingShapes();
        if (pattern == null && !availableShapes.isEmpty()) {
            if (shapeIndex >= availableShapes.size()) shapeIndex = 0;
            pattern = loadPattern(availableShapes.get(shapeIndex));
            invalidateFilterSets();
        }

        // ── Renderer ──────────────────────────────────────────────────────────
        if (renderer == null) {
            renderer = new PhantasiaWorldRenderer(SHARED_LEVEL);
            if (pattern != null) {
                renderer.setBaseplatePositions(pattern.baseplatePositions);
                renderer.setControllerWorldPos(pattern.controllerWorldPos);
            }
            // GT's DynamicRenderManager is just a type registry — it has no
            // render hook. GT dynamic renders (fusion rings, etc.) are driven
            // by the machine's MetaMachineBlockEntity BER, which is already
            // handled by drawTileEntities once the BEs are registered.
        }

        // ── Camera ────────────────────────────────────────────────────────────
        if (camera == null) {
            camera = buildFreshCamera();
        } else if (camera.hasSavedSnapshot()) {
            camera.restore();
        } else if (!camera.isPlayerOwned()) {
            resetCameraToDefault(LerpType.SNAP, 0);
        }

        // ── Synchronize Structure and Text ────────────────────────────────────
        if (pattern != null) {
            // Force the structure block states to match coilIndex (0) immediately on startup
            updateCoilType();
            applyVisibility();

            // ── PERFORMANCE OPTIMIZATION: Populate Ambient Ticking Cache ──
            // Instead of querying pattern maps every tick, cache the ticking positions once here.
            this.ambientTickingPositions.clear();
            for (net.minecraft.core.BlockPos wp : pattern.blockMap.keySet()) {
                net.minecraft.world.level.block.state.BlockState state = SHARED_LEVEL.getBlockState(wp);
                // Ignore air entirely, map only real positions
                if (!state.isAir()) {
                    this.ambientTickingPositions.add(wp);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera helpers
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaCamera buildFreshCamera() {
        float[] yp = resolveStartingYawPitch();
        float zoom = resolveStartingZoom();
        float[] target = resolveTarget();
        PhantasiaCamera cam = new PhantasiaCamera(yp[0], yp[1], zoom,
                target[0], target[1], target[2]);
        if (pattern != null) cam.setFloorY(pattern.origin.getY() + 0.5f);
        return cam;
    }

    private void resetCameraToDefault(LerpType type, int ticks) {
        float[] yp = resolveStartingYawPitch();
        float zoom = resolveStartingZoom();
        float[] target = resolveTarget();
        camera.setTarget(target[0], target[1], target[2]);
        camera.hardReset(yp[0], yp[1], zoom, target[0], target[1], target[2], type, ticks);
        if (pattern != null) camera.setFloorY(pattern.origin.getY() + 0.5f);
    }

    private float[] resolveStartingYawPitch() {
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null) {
                float yaw = sc.hasYaw() ? sc.getYaw() : getFacingYaw();
                float pitch = sc.hasPitch() ? sc.getPitch() : CAM_DEFAULT_PITCH;
                return new float[] { yaw, pitch };
            }
        }
        if (script != null && !script.getSteps().isEmpty()) {
            PhantasiaScript.Step s0 = script.getSteps().get(0);
            if (s0.hasCamera()) return new float[] { s0.yaw(), s0.pitch() };
        }
        return new float[] { getFacingYaw(), CAM_DEFAULT_PITCH };
    }

    private float resolveStartingZoom() {
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null && sc.hasZoom()) return sc.getZoom();
        }
        if (script != null && !script.getSteps().isEmpty()) {
            PhantasiaScript.Step s0 = script.getSteps().get(0);
            if (s0.hasCamera() && s0.zoom() > 0) return s0.zoom();
        }
        if (pattern == null) return CAM_DEFAULT_ZOOM;
        // Compute the bounding box of THIS pattern only — do NOT use
        // SHARED_LEVEL.getSize(), which spans every region ever added to the
        // shared world and grows without bound as the player switches shapes.
        int sxLen = 0, syLen = 0, szLen = 0;
        for (BlockPos lp : pattern.localToWorld.keySet()) {
            sxLen = Math.max(sxLen, lp.getX() + 1);
            syLen = Math.max(syLen, lp.getY() + 1);
            szLen = Math.max(szLen, lp.getZ() + 1);
        }
        float maxDim = Math.max(sxLen, Math.max(syLen, szLen));
        return Math.max(CAM_DEFAULT_ZOOM, maxDim * 3.0f);
    }

    private float[] resolveTarget() {
        if (pattern == null) return new float[] { 0, 0, 0 };
        float cx, cy, cz;
        // Compute the centroid from this pattern's own world positions only.
        // SHARED_LEVEL.getMinPos()/getMaxPos() spans ALL ever-added regions
        // and drifts far from the current pattern after structure-size switches.
        int wxMin = Integer.MAX_VALUE, wxMax = Integer.MIN_VALUE;
        int wzMin = Integer.MAX_VALUE, wzMax = Integer.MIN_VALUE;
        for (BlockPos wp : pattern.blockMap.keySet()) {
            wxMin = Math.min(wxMin, wp.getX());
            wxMax = Math.max(wxMax, wp.getX());
            wzMin = Math.min(wzMin, wp.getZ());
            wzMax = Math.max(wzMax, wp.getZ());
        }
        if (wxMin > wxMax) {
            cx = pattern.origin.getX() + 0.5f;
            cz = pattern.origin.getZ() + 0.5f;
        } else {
            cx = (wxMin + wxMax) * 0.5f + 0.5f;
            cz = (wzMin + wzMax) * 0.5f + 0.5f;
        }
        cy = pattern.origin.getY() + (pattern.minY + pattern.maxY) * 0.5f + CAM_TARGET_Y_BIAS;
        if (script != null) {
            var sc = script.getStartCamera();
            if (sc != null && sc.hasTargetOffset()) {
                cx += sc.getTargetOffsetX();
                cy += sc.getTargetOffsetY();
                cz += sc.getTargetOffsetZ();
            }
        }
        return new float[] { cx, cy, cz };
    }

    private float getFacingYaw() {
        if (pattern == null || pattern.controllerWorldPos == null || SHARED_LEVEL == null)
            return -135f;
        try {
            BlockState ctrl = SHARED_LEVEL.getBlockState(pattern.controllerWorldPos);
            if (ctrl.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return switch (ctrl.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST -> 270f;
                    case EAST -> 90f;
                    default -> -135f;
                };
            }
        } catch (Exception ignored) {}
        return -135f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pattern loading
    // ─────────────────────────────────────────────────────────────────────────

    private PhantasiaLoadedPattern loadPattern(MultiblockShapeInfo shape) {
        int regionIndex = NEXT_REGION++;
        BlockPos origin = new BlockPos(regionIndex * REGION_SIZE, 50, 0);

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Set<BlockPos> baseplatePos = new HashSet<>();
        Set<BlockPos> bePos = new HashSet<>();
        BlockPos controllerWP = null;
        MultiblockControllerMachine controller = null;

        BlockInfo floor = getBaseplateBlockFromConfig();
        BlockInfo[][][] raw = shape.getBlocks();
        int sxLen = raw.length;
        int szLen = sxLen > 0 && raw[0].length > 0 ? raw[0][0].length : 0;
        int padX = Math.max(2, sxLen / 2 + 1);
        int padZ = Math.max(2, szLen / 2 + 1);

        for (int bx = -padX; bx <= sxLen + padX; bx++)
            for (int bz = -padZ; bz <= szLen + padZ; bz++) {
                BlockPos wp = origin.offset(bx, -1, bz);
                blockMap.put(wp, floor);
                baseplatePos.add(wp);
            }

        for (int x = 0; x < raw.length; x++)
            for (int y = 0; y < raw[x].length; y++)
                for (int z = 0; z < raw[x][y].length; z++) {
                    BlockInfo info = raw[x][y][z];
                    if (info == null) continue;
                    BlockPos lp = new BlockPos(x, y, z);
                    BlockPos wp = origin.offset(x, y, z);
                    try {
                        var be = info.getBlockEntity(wp);
                        if (be instanceof MetaMachineBlockEntity mbe) {
                            mbe.setLevel(SHARED_LEVEL);
                            var machine = mbe.getMetaMachine();
                            if (machine instanceof MultiblockControllerMachine ctrl && controllerWP == null) {
                                controller = ctrl;
                                controllerWP = wp;
                            }
                            bePos.add(wp);
                        }
                    } catch (Exception ignored) {}
                    blockMap.put(wp, info);
                    localToWorld.put(lp, wp);
                }

        SHARED_LEVEL.addBlocks(blockMap);

        // Register every MetaMachineBlockEntity with the dummy world so that
        // TrackedDummyWorld.getBlockEntity(pos) returns them. Without this,
        // the bake thread's BE scan finds nothing and frontTileEntities stays
        // empty — meaning drawTileEntities never renders any machine overlays.
        // We must do this BEFORE onStructureFormed so the controller's own BE
        // is already in the world when formation logic queries it.
        for (BlockPos bp : bePos) {
            try {
                BlockInfo info = blockMap.get(bp);
                if (info == null) continue;
                var be = info.getBlockEntity(bp);
                if (be != null) {
                    be.setLevel(SHARED_LEVEL); // ensure hasLevel() returns true
                    SHARED_LEVEL.setInnerBlockEntity(be);
                }
            } catch (Exception ignored) {}
        }
        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                "[Phantasia] Registered {} block entities with SHARED_LEVEL", bePos.size());
        if (controller != null) {
            try {
                BlockPattern pat = controller.getPattern();
                net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                        "[Phantasia] loadPattern: controller={}, pattern={}, multiblockState={}",
                        controller.getClass().getSimpleName(),
                        pat != null ? "present" : "null",
                        controller.getMultiblockState());
                if (pat != null) {
                    boolean matched = pat.checkPatternAt(controller.getMultiblockState(), true);
                    net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                            "[Phantasia] checkPatternAt result: {}", matched);
                    if (matched) {
                        controller.onStructureFormed();
                        net.phoenixvine.phantasia.Phantasia.LOGGER.info(
                                "[Phantasia] onStructureFormed completed, entities in world: {}",
                                SHARED_LEVEL.getAllEntities().spliterator().estimateSize());
                    }
                }
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.error(
                        "[Phantasia] onStructureFormed failed: {}", e.getMessage(), e);
            }
        }

        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos lp : localToWorld.keySet()) {
            minY = Math.min(minY, lp.getY());
            maxY = Math.max(maxY, lp.getY());
        }
        if (minY > maxY) {
            minY = 0;
            maxY = 0;
        }

        return new PhantasiaLoadedPattern(blockMap, localToWorld, baseplatePos,
                controllerWP, bePos, origin, minY, maxY, controller, script);
    }


    private BlockInfo getBaseplateBlockFromConfig() {
        try {
            String blockId = net.phoenixvine.phantasia.configs.PhantasiaConfigs.INSTANCE.phantasiaUI.baseplateBlock;
            var rl = new ResourceLocation(blockId);
            var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);

            if (block != null && block != Blocks.AIR) {
                return BlockInfo.fromBlockState(block.defaultBlockState());
            }
        } catch (Exception ignored) {
            // Fallback if ResourceLocation parsing fails
        }
        return BlockInfo.fromBlockState(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibility
    // ─────────────────────────────────────────────────────────────────────────

    public void applyVisibility() {
        if (renderer == null || pattern == null || SHARED_LEVEL == null) return;
        PhantasiaScript.Step step = script.getActiveStep(playbackTick);

        Set<BlockPos> next = new HashSet<>();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            if (isBlockVisible(e.getKey(), e.getValue(), step))
                next.add(e.getValue());
        }
        renderer.setVisible(next);
    }

    public boolean isBlockVisible(BlockPos local, BlockPos world, PhantasiaScript.Step step) {
        // ViewFilter always takes priority — it is an explicit "show only X" selection.
        if (viewFilter != ViewFilter.ALL) {
            Set<BlockPos> fs = getFilterSet(viewFilter);
            return fs == null || fs.contains(world);
        }
        // Build-order mode is checked BEFORE manualLayer: build mode drives its own
        // layer visibility and must never be overridden by whatever layer was set in
        // the normal view. When build mode is active, manualLayer is ignored entirely.
        if (buildOrderMode) {
            int g = pattern.getGroupIndex(local);
            return g != -1 && g <= buildOrderGroup;
        }
        // Manual layer filter (◀/▶ buttons in normal view).
        // manualLayer is in local (pattern-space) Y — same coordinate space as local.getY().
        if (manualLayer >= 0) return local.getY() == manualLayer;
        if (step != null) return step.filter().test(local);
        return true;
    }

    public void applyViewFilter(ViewFilter vf) {
        if (viewFilter == vf) return;
        viewFilter = vf;
        applyVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter sets (lazy build)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean computeHasRealSizeVariants() {
        if (availableShapes == null || availableShapes.size() <= 1) return false;
        int firstCount = countBlocks(availableShapes.get(0));
        for (int i = 1; i < availableShapes.size(); i++) {
            if (countBlocks(availableShapes.get(i)) != firstCount) return true;
        }
        return false;
    }

    private static int countBlocks(MultiblockShapeInfo shape) {
        int count = 0;
        for (BlockInfo[][] layer : shape.getBlocks())
            for (BlockInfo[] row : layer)
                for (BlockInfo b : row)
                    if (b != null && b.getBlockState() != null && !b.getBlockState().isAir())
                        count++;
        return count;
    }

    private void buildFilterSets() {
        if (pattern == null || SHARED_LEVEL == null) return;
        filteredHatchBus = new HashSet<>();
        filteredEnergyIO = new HashSet<>();
        filteredHasBE = pattern.blockEntityWorldPos;
        filteredController = pattern.controllerWorldPos != null ? Set.of(pattern.controllerWorldPos) : Set.of();

        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            BlockPos wp = e.getValue();
            if (wp.equals(pattern.controllerWorldPos)) continue;
            BlockState state = SHARED_LEVEL.getBlockState(wp);
            if (!(state.getBlock() instanceof MetaMachineBlock)) continue;
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (rl == null) continue;
            String p = rl.getPath();
            if (p.contains("hatch") || p.contains("bus") || p.contains("muffler") || p.contains("maintenance"))
                filteredHatchBus.add(wp);
            if (p.contains("energy") || p.contains("dynamo") || p.contains("laser") || p.contains("power"))
                filteredEnergyIO.add(wp);
        }
    }

    /**
     * Nulls all filter-set caches so the next getFilterSet() call rebuilds them
     * against the current pattern. Must be called whenever pattern changes.
     */
    private void invalidateFilterSets() {
        filteredHatchBus = null;
        filteredEnergyIO = null;
        filteredHasBE = null;
        filteredController = null;
    }

    private Set<BlockPos> getFilterSet(ViewFilter vf) {
        if (filteredHatchBus == null) buildFilterSets();
        return switch (vf) {
            case HATCHES_BUSES -> filteredHatchBus;
            case ENERGY_IO -> filteredEnergyIO;
            case BLOCK_ENTITIES -> filteredHasBE;
            case CONTROLLER -> filteredController;
            default -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tick()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (camera != null) camera.tick();

        if (captionCurrent != null && captionAlpha < 1f)
            captionAlpha = Math.min(1f, captionAlpha + 0.1f);
        if (captionOutgoing != null) {
            captionOutAlpha -= 0.1f;
            if (captionOutAlpha <= 0f) {
                captionOutgoing = null;
                captionOutAlpha = 0f;
            }
        }

        if (buildOrderMode) {
            buildPulse += buildPulseUp ? 0.05f : -0.05f;
            if (buildPulse >= 1f) {
                buildPulse = 1f;
                buildPulseUp = false;
            }
            if (buildPulse <= 0f) {
                buildPulse = 0f;
                buildPulseUp = true;
            }
        }

        // Inside PhantasiaSceneScreen.java -> tick()
        if (playing && !scrubbing && SHARED_LEVEL != null && renderer != null) {
            net.minecraft.util.RandomSource random = SHARED_LEVEL.getRandom();
            tickAmbientEffects(random);
        }

        if (!playing || scrubbing || buildOrderMode || script == null || viewFilter != ViewFilter.ALL) return;

        int prevTick = playbackTick;
        tickAccum += playbackSpeed;
        while (tickAccum >= 1f) {
            tickAccum -= 1f;
            playbackTick++;
        }
        if (playbackTick >= script.getTotalTicks()) {
            playbackTick = (int) script.getTotalTicks();
            playing = false;
        }

        PhantasiaScript.Step step = script.getActiveStep(playbackTick);

        if (step != null && step.forceCoil() != -1 && step.forceCoil() != coilIndex) {
            coilIndex = step.forceCoil();
            updateCoilType();
        }

        if (playbackTick != prevTick && step != lastAppliedStep) {
            lastAppliedStep = step;

            if (step != null && step.hasCamera() && camera != null) {
                float zoom = step.zoom() > 0 ? step.zoom() : camera.getZoom();
                camera.scriptDrive(step.yaw(), step.pitch(), zoom,
                        step.lerpType(), step.lerpTicks());
            }

            if (step != null && step.forceShape() != -1 && step.forceShape() != shapeIndex && availableShapes != null &&
                    step.forceShape() < availableShapes.size()) {

                shapeIndex = step.forceShape();

                camera.hardReset(
                        camera.getYaw(), camera.getPitch(), camera.getZoom(),
                        camera.getTargetX(), camera.getTargetY(), camera.getTargetZ(),
                        LerpType.SNAP, 0);

                if (renderer != null) {
                    renderer.close();
                    renderer = null;
                }
                pattern = null;
                invalidateFilterSets();
                init();
                return; // init() already calls applyVisibility
            }

            applyVisibility();
            updateMachineState(step);
            updateCaptionForStep(step);
        }
    }

    // Add this as a field in PhantasiaSceneScreen
    private final java.util.List<BlockPos> ambientTickingPositions = new java.util.ArrayList<>();

    private void tickAmbientEffects(net.minecraft.util.RandomSource random) {
        for (BlockPos wp : ambientTickingPositions) {
            if (renderer.isVisible(wp)) {
                SHARED_LEVEL.tickAnimateForPos(wp, random);
            }
        }
    }

    private void updateCaptionForStep(PhantasiaScript.Step step) {
        String next = step != null ? step.caption() : null;
        if (!Objects.equals(next, captionCurrent)) {
            captionOutgoing = captionCurrent;
            captionOutAlpha = captionAlpha;
            captionCurrent = next;
            captionAlpha = 0f;
        }
    }

    private void updateMachineState(PhantasiaScript.Step step) {
        if (pattern == null || pattern.controller == null) return;
        boolean working = step != null && step.working() && playbackTick < script.getTotalTicks();
        if (pattern.controller instanceof WorkableMultiblockMachine w) {
            RecipeLogic logic = w.getRecipeLogic();
            if ((logic.getStatus() == RecipeLogic.Status.WORKING) != working)
                logic.setStatus(working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);

            // Inject a fake recipe so recipe-dependent renders (fusion plasma colour,
            // laser colour, etc.) have something to read.
            String rid = (step != null && working) ? step.fakeRecipeId() : null;
            if (rid != null && !rid.isBlank()) {
                injectFakeRecipe(logic, rid.trim());
            } else if (!working && logic.getLastRecipe() != null) {
                // Using the native cleanup method ensures everything handles cleanly
                logic.resetRecipeLogic();
            }
        }
        var rs = pattern.controller.getRenderState();
        var ap = com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_ACTIVE;
        if (rs.hasProperty(ap) && rs.getValue(ap) != working)
            pattern.controller.setRenderState(rs.setValue(ap, working));
    }

    private void injectFakeRecipe(RecipeLogic logic, String rid) {
        try {
            var rl = new ResourceLocation(rid);

            // Safely access the client-side global Minecraft recipe manager
            if (Minecraft.getInstance().getConnection() == null) return;

            var optionalRecipe = Minecraft.getInstance()
                    .getConnection()
                    .getRecipeManager()
                    .byKey(rl);

            // Check if the recipe exists and belongs to GregTech (GTRecipe)
            if (optionalRecipe.isPresent() &&
                    optionalRecipe.get() instanceof com.gregtechceu.gtceu.api.recipe.GTRecipe gtRecipe) {
                if (logic.getLastRecipe() != gtRecipe) {
                    // Initialize the recipe inside the machine logic loop safely
                    logic.setupRecipe(gtRecipe);

                    // Set progress to mid-recipe so duration-dependent renders show a stable state.
                    logic.setProgress(gtRecipe.duration / 2);
                }
            }
        } catch (Exception ignored) {
            // Bad resource location or missing recipe — leave last-recipe unchanged.
        }
    }

    private void updateCoilType() {
        if (pattern == null || pattern.blockMap == null || coilTiers.isEmpty()) return;

        // Wrap the index safely just in case the tier size shifted
        if (coilIndex >= coilTiers.size()) coilIndex = 0;
        BlockInfo newCoil = coilTiers.get(coilIndex);

        for (Map.Entry<BlockPos, BlockInfo> e : pattern.blockMap.entrySet()) {
            if (e.getValue().getBlockState().getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock) {
                e.setValue(newCoil);
                if (SHARED_LEVEL != null)
                    SHARED_LEVEL.setBlock(e.getKey(), newCoil.getBlockState(), 3);
            }
        }

        // Order matters here:
        // 1. applyVisibility() first — updates targetVisible so setVisible() knows the
        // correct set. Because no block *positions* changed (only their states),
        // setVisible sees zero appearing/disappearing blocks and does NOT call
        // scheduleBake() — it only calls scheduleBake when hasTransitions is false,
        // which it is, but the call path is: no transitions → scheduleBake(). So we
        // call invalidate() AFTER to cancel that bake and replace it with a fresh one
        // that will read the already-written new block states safely.
        // 2. invalidate() cancels any in-flight bake (avoiding a race where the bake
        // thread's save/restore pass reads renderedBlocks while we're still writing
        // new coil states above), clears transition state, and sets rebakeNeeded.
        // The renderer fires the fresh bake on the NEXT render frame — after this
        // method returns and the main thread is done mutating world state.
        applyVisibility();
        if (renderer != null) renderer.invalidate();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // render()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        activeButtons.clear();
        pendingTooltip = null;

        int pw = getCurrentPanelWidth();
        int sw = this.width - pw;

        // Dynamically compute layout boundaries instead of using hardcoded limits
        int totalTextLines = 1;
        if (captionCurrent != null) {
            int maximumAvailableWidth = sw - 20;
            totalTextLines = Math.min(font.split(Component.literal(captionCurrent), maximumAvailableWidth).size(), 3);
        }

        // Increase padding per line to utilize more vertical screen area
        int dynamicCaptionH = Math.max(22, (totalTextLines * (font.lineHeight + 3)) + 12);
        int sh = this.height - TIMELINE_H - dynamicCaptionH;

        g.fill(0, 0, this.width, this.height, C_BG());

        if (renderer != null && camera != null) {
            CameraView view = camera.getView(partial);
            renderer.setMousePos(mx, my);
            renderer.render(view, 0, dynamicCaptionH, sw, sh);
            BlockHitResult hit = renderer.getLastHitResult();
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hp = hit.getBlockPos();
                hoveredPos = renderer.isVisible(hp) ? hp : null;
            } else {
                hoveredPos = null;
            }
        }

        // Render caption background and text layout
        renderCaption(g, dynamicCaptionH);

        if (buildOrderMode && pattern != null) renderBuildPulseBanner(g);
        if (showMistakes && script != null && script.hasCommonMistakes())
            renderMistakesOverlay(g);

        renderTimeline(g, mx, my);
        renderSidePanel(g, mx, my);
        regBtn(g, mx, my, 10, 10, 50, 18, "Back", Component.literal("Return to previous screen"), this::onClose);

        super.render(g, mx, my, partial);

        if (pendingTooltip != null) {
            g.renderTooltip(font, pendingTooltip, mx, my);
        }

        int px = this.width - pw;
        if (hoveredPos != null && SHARED_LEVEL != null) {
            try {
                BlockState st = SHARED_LEVEL.getBlockState(hoveredPos);
                if (!st.isAir()) {
                    g.drawString(font, trunc(st.getBlock().getName().getString(), pw - 20),
                            px + 10, this.height - 20, C_DIM(), false);
                    if (mx < px) g.renderTooltip(font, st.getBlock().getName(), mx, my);
                }
            } catch (Exception ignored) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int px = this.width - getCurrentPanelWidth();
        int barY = this.height - TIMELINE_H;

        // Draws the timeline tracking layer behind buttons
        g.fill(0, barY, px, this.height, C_TL_BG());
        g.fill(0, barY, px, barY + 1, C_ACCENT());

        int x = 6;
        regBtn(g, mx, my, x, barY + 4, 18, 17, playing ? "⏸" : "▶", Component.literal("Play / Pause"), () -> {
            if (!playing && playbackTick >= script.getTotalTicks()) {
                playbackTick = 0;
                tickAccum = 0f;
                lastAppliedStep = null;
                applyVisibility();
            }
            playing = !playing;
        });
        x += 22;
        regBtn(g, mx, my, x, barY + 4, 18, 17,
                camera != null && camera.isLocked() ? "🔒" : "🔓",
                Component.literal("Toggle Camera Lock"),
                () -> {
                    if (camera != null) camera.toggleLocked();
                });
        x += 22;
        String spd = playbackSpeed == 0.5f ? "½x" : playbackSpeed == 2f ? "2x" : "1x";
        regBtn(g, mx, my, x, barY + 4, 24, 17, spd, Component.literal("Cycle Playback Speed"),
                () -> playbackSpeed = playbackSpeed == 1f ? 2f : playbackSpeed == 2f ? 0.5f : 1f);

        int tx = 80, tw = px - tx - 65, midY = barY + TIMELINE_H / 2;

        g.fill(tx, midY - 1, tx + tw, midY + 1, C_BTN());

        float total = script.getTotalTicks();
        for (PhantasiaScript.Step s : script.getSteps()) {
            int mx2 = tx + (int) (tw * s.tickOffset() / total);
            g.fill(mx2 - 1, midY - 4, mx2 + 1, midY + 4, C_DIM() | 0xAA000000);
        }
        float prog = total > 0 ? playbackTick / total : 0f;
        g.fill(tx, midY - 1, tx + (int) (tw * prog), midY + 1, C_PROG());
        g.fill(tx + (int) (tw * prog) - 2, midY - 4,
                tx + (int) (tw * prog) + 2, midY + 4, C_ACCENT());
        g.drawString(font, formatTicks(playbackTick), tx + tw + 8, barY + 9, C_DIM(), false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caption Strip (Dynamic Vertical Space + Full Background Block Layering)
    // ─────────────────────────────────────────────────────────────────────────

    private void renderCaption(GuiGraphics g, int dynamicCaptionH) {
        if (captionCurrent == null && captionOutgoing == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        int sw = this.width - getCurrentPanelWidth();
        int stripY = this.height - TIMELINE_H - dynamicCaptionH;

        // Thicker plate that frames all text behind the layout cleanly
        g.fill(0, stripY, sw, this.height - TIMELINE_H, C_TL_BG());
        g.fill(0, stripY, sw, stripY + 1, C_ACCENT());

        if (captionOutgoing != null && captionOutAlpha > 0.05f) {
            int alphaBits = ((int) (captionOutAlpha * 255) << 24);
            int blendOutColor = (alphaBits | (C_DIM() & 0x00FFFFFF));
            drawWrappedCaptionText(g, captionOutgoing, sw, stripY, dynamicCaptionH, blendOutColor);
        }
        if (captionCurrent != null && captionAlpha > 0.05f) {
            int alphaBits = ((int) (captionAlpha * 255) << 24);
            int blendInColor = (alphaBits | (C_TEXT() & 0x00FFFFFF));
            drawWrappedCaptionText(g, captionCurrent, sw, stripY, dynamicCaptionH, blendInColor);
        }
        g.pose().popPose();
    }

    private void drawWrappedCaptionText(GuiGraphics g, String rawText, int totalWidth, int baseStripY,
                                        int dynamicCaptionH, int mixedColor) {
        int maximumAvailableWidth = totalWidth - 20;
        Component textComp = Component.literal(rawText);
        List<net.minecraft.util.FormattedCharSequence> textLines = font.split(textComp, maximumAvailableWidth);

        int maxRenderLines = 3;
        boolean demandsEllipsis = textLines.size() > maxRenderLines;
        int activeLineCount = Math.min(textLines.size(), maxRenderLines);

        int lineSpacing = 3; // Added extra line spacing breathing room
        int totalBlockHeight = (activeLineCount * font.lineHeight) + ((activeLineCount - 1) * lineSpacing);
        int renderingStartY = baseStripY + (dynamicCaptionH - totalBlockHeight) / 2;

        for (int i = 0; i < activeLineCount; i++) {
            int lineY = renderingStartY + (i * (font.lineHeight + lineSpacing));

            if (i == 2 && demandsEllipsis) {
                int safetyLimitWidth = maximumAvailableWidth - font.width("...");
                List<net.minecraft.util.FormattedCharSequence> dynamicTrimmed = font.split(textComp, safetyLimitWidth);

                if (dynamicTrimmed.size() >= 3) {
                    int lineLeftX = (totalWidth - font.width(dynamicTrimmed.get(2)) - font.width("...")) / 2;
                    g.drawString(font, dynamicTrimmed.get(2), lineLeftX, lineY, mixedColor, false);

                    int dimAlphaOnly = (mixedColor & 0xFF000000);
                    int customDimmedDot = dimAlphaOnly | (C_DIM() & 0x00FFFFFF);
                    g.drawString(font, "...", lineLeftX + font.width(dynamicTrimmed.get(2)), lineY, customDimmedDot,
                            false);
                } else {
                    g.drawCenteredString(font, textLines.get(i), totalWidth / 2, lineY, mixedColor);
                }
            } else {
                g.drawCenteredString(font, textLines.get(i), totalWidth / 2, lineY, mixedColor);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build-order banner
    // ─────────────────────────────────────────────────────────────────────────

    private void renderBuildPulseBanner(GuiGraphics g) {
        if (buildOrderGroup >= pattern.buildOrder.size()) return;
        int sceneW = this.width - getCurrentPanelWidth();
        int alpha = (int) (buildPulse * 0xBB);
        int col = (alpha << 24) | (C_ACCENT() & 0x00FFFFFF);
        int by = TIMELINE_H;

        g.fill(0, by, sceneW, by + 18, ((alpha / 4) << 24) | (C_PANEL() & 0x00FFFFFF));
        g.fill(0, by + 17, sceneW, by + 18, col);
        List<BlockPos> grp = pattern.buildOrder.get(buildOrderGroup);

        int textWithAlpha = (alpha << 24) | (C_TEXT() & 0x00FFFFFF);
        g.drawCenteredString(font,
                "Next: Layer Y=" + grp.get(0).getY() + " — " + grp.size() + " block(s)",
                sceneW / 2, by + 5, textWithAlpha);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mistakes overlay
    // ─────────────────────────────────────────────────────────────────────────

    private void renderMistakesOverlay(GuiGraphics g) {
        List<PhantasiaScript.LocalWarning> local = script.getCommonMistakes();
        List<String> global = script.getGlobalMistakes();
        int x = 8, y = TIMELINE_H + 26;
        int ph = (local.size() + global.size()) * 12 + 10;

        g.fill(x - 2, y - 2, x + 240, y + ph, C_PANEL() | 0xEA000000);
        g.fill(x - 2, y - 2, x + 240, y - 1, PhantasiaThemeUtils.C_WARN());

        for (var w : local) {
            g.drawString(font, "⚠ " + w.label(), x, y, w.color(), false);
            BlockPos lp = w.localPos();
            g.drawString(font,
                    " [" + lp.getX() + "," + lp.getY() + "," + lp.getZ() + "]",
                    x + font.width("⚠ " + w.label()), y, C_DIM(), false);
            y += 12;
        }
        for (String m : global) {
            g.drawString(font, "• " + m, x, y, C_TEXT(), false);
            y += 12;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Side panel
    // ─────────────────────────────────────────────────────────────────────────

    private void renderSidePanel(GuiGraphics g, int mx, int my) {
        int pw = getCurrentPanelWidth();
        int px = this.width - pw;
        activeButtons.removeIf(b -> b.x() >= px);

        g.fill(px, 0, this.width, this.height, C_PANEL());
        g.fill(px, 0, px + 1, this.height, C_ACCENT());

        // Collapse/Expand button
        int collapseBtnX = this.width - COLLAPSED_PANEL_W;
        String collapseBtnLabel = sidePanelCollapsed ? "▶" : "◀";
        regBtn(g, mx, my, collapseBtnX, 0, COLLAPSED_PANEL_W, 18, collapseBtnLabel, Component.literal("Toggle Side Panel"), () -> {
            sidePanelCollapsed = !sidePanelCollapsed;
        });

        int y = 10;
        if (sidePanelCollapsed) return; // Only show context data if expanded

        g.drawString(font, trunc(definition.getLangValue(), pw - 20),
                px + 10, y, C_ACCENT(), false);
        y += 20;

        boolean hasCoilBlocks = pattern != null && pattern.blockMap.values().stream()
                .anyMatch(i -> i.getBlockState().getBlock() instanceof com.gregtechceu.gtceu.common.block.CoilBlock);
        boolean hasRealSizeVariants = computeHasRealSizeVariants();
        boolean isCoilTierMachine = hasCoilBlocks && !hasRealSizeVariants;

        if (isCoilTierMachine) {
            String cn = COIL_TIERS.get(coilIndex).getBlockState().getBlock()
                    .getName().getString();
            regBtn(g, mx, my, px + 10, y, pw - 20, 16, "Coil: " + cn, Component.literal("Change heating coil material"), () -> {
                coilIndex = (coilIndex + 1) % COIL_TIERS.size();
                updateCoilType();
            });
            y += 20;
        }

        if (hasRealSizeVariants) {
            regBtn(g, mx, my, px + 10, y, pw - 20, 16, "Structure Size: " + (shapeIndex + 1),
                    Component.literal("Switch between available structure variants"), () -> {
                        shapeIndex = (shapeIndex + 1) % availableShapes.size();
                        if (renderer != null) {
                            renderer.close();
                            renderer = null;
                        }
                        pattern = null;
                        invalidateFilterSets();
                        if (camera != null) camera.clearSnapshot();
                        if (camera != null) camera.clearPlayerOwned();
                        init();
                    });
            y += 20;
        }
        y += 5;

        int bW = 20, lW = pw - 60, lX = px + 30;

        g.drawString(font, "Show:", px + 10, y, C_DIM(), false);
        y += 12;
        ViewFilter[] vfs = ViewFilter.values();
        int fw = (pw - 25) / 2;
        for (int i = 0; i < vfs.length; i++) {
            final ViewFilter vf = vfs[i];
            int bx = (i % 2 == 0) ? px + 10 : px + 15 + fw;
            regBtn(g, mx, my, bx, y, fw, 14, vf.name(), viewFilter == vf,
                    Component.literal("Filter view to: " + vf.name()),
                    () -> toggleViewFilter(vf));
            if (i % 2 != 0 || i == vfs.length - 1) y += 17;
        }

        y += 8;
        if (script != null && script.hasCommonMistakes()) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⚠", "Common Mistakes",
                    showMistakes, Component.literal("Show or hide potential build errors"),
                    () -> showMistakes = !showMistakes);
            y += 20;
        }

        // ── Layer navigation (only shown when NOT in build mode) ──────────────
        if (!buildOrderMode && pattern != null) {
            g.drawString(font, "Layer:", px + 10, y + 4, C_DIM(), false);
            String layerLabel = manualLayer >= 0 ? "Y=" + manualLayer : "All";
            regBtn(g, mx, my, px + 10, y + 14, bW, 14, "◀", Component.literal("Previous Layer"),
                    () -> nudgeLayer(-1));
            g.drawCenteredString(font, layerLabel, lX + lW / 2, y + 17, C_ACCENT());
            regBtn(g, mx, my, lX + lW + 2, y + 14, bW, 14, "▶", Component.literal("Next Layer"),
                    () -> nudgeLayer(1));
            if (manualLayer >= 0) {
                regBtn(g, mx, my, px + 10, y + 31, pw - 20, 12, "Show All Layers", Component.literal("Reset layer filter"),
                        () -> {
                            manualLayer = -1;
                            applyVisibility();
                        });
                y += 46;
            } else {
                y += 32;
            }
        }

        // ── Build-order step buttons (only shown when IN build mode) ──────────
        if (buildOrderMode && pattern != null) {
            int totalGroups = pattern.buildOrder.size();
            g.drawString(font, "Build Step:", px + 10, y + 4, C_DIM(), false);
            String stepLabel = (buildOrderGroup + 1) + " / " + totalGroups;
            regBtn(g, mx, my, px + 10, y + 14, bW, 14, "◀", Component.literal("Previous Build Step"),
                    () -> buildOrderStep(-1));
            g.drawCenteredString(font, stepLabel, lX + lW / 2, y + 17, C_ACCENT());
            regBtn(g, mx, my, lX + lW + 2, y + 14, bW, 14, "▶", Component.literal("Next Build Step"),
                    () -> buildOrderStep(1));
            y += 32;
        }
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🧱", "Build Mode", buildOrderMode,
                Component.literal("Toggle build-order visualization"),
                () -> {
                    buildOrderMode = !buildOrderMode;
                    if (buildOrderMode) {
                        savedManualLayer = manualLayer;
                        manualLayer = -1;
                        buildOrderGroup = 0;
                    } else {
                        manualLayer = savedManualLayer;
                    }
                    applyVisibility();
                });
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🗺", "Footprint", false,
                Component.literal("View structure footprint on the ground"),
                this::openFootprintScreen);
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "⊕", "Center Camera", false,
                Component.literal("Reset camera to center of structure"),
                this::centerCamera);
        y += 20;
        regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "🔍", "Block List", false,
                Component.literal("View and filter required blocks"),
                this::openBlockFilterScreen);
        y += 20;

        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getAbilities().instabuild) {
            regIconBtn(g, mx, my, px + 10, y, pw - 20, 16, "✏", "Edit Script", false,
                    Component.literal("Open the visual script editor"),
                    this::openScriptEditor);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (PhantasiaUIUtils.ButtonAction b : activeButtons) {
            if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
        }

        int px = this.width - getCurrentPanelWidth();
        int tlY = this.height - TIMELINE_H;

        if (btn == 0 && my >= tlY && mx < px && !buildOrderMode) {
            int tx = 80, tw = px - tx - 65;
            if (mx >= tx && mx <= tx + tw) {
                playing = false;
                scrubbing = true;
                scrubTo((float) (mx - tx) / tw);
                return true;
            }
        }

        if (mx < px && my > CAPTION_STRIP_H && my < tlY) {
            if (btn == 1 && hoveredPos != null && SHARED_LEVEL != null) {
                try {
                    if (!SHARED_LEVEL.getBlockState(hoveredPos).isAir()) {
                        if (camera != null) camera.save();
                        Minecraft.getInstance().setScreen(
                                new PhantasiaBlockInspectScreen(hoveredPos, pattern, this));
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            if (btn == 2 && camera != null && !camera.isLocked()) {
                isPanning = true;
                return true;
            }
            if (btn == 0) return true;
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        int px = this.width - getCurrentPanelWidth();
        int tlY = this.height - TIMELINE_H;
        if (mx >= px || my >= tlY || camera == null || camera.isLocked())
            return super.mouseDragged(mx, my, btn, dx, dy);

        if (scrubbing && btn == 0) {
            scrubTo(Mth.clamp((float) (mx - 80) / (px - 80 - 65), 0f, 1f));
            return true;
        }

        if (btn == 2 && isPanning) {
            Vector3f right = new Vector3f(), up = new Vector3f();
            camera.getRightAndUp(right, up);
            float s = CAM_PAN_SPEED;
            camera.pan(
                    (right.x * (float) -dx + up.x * (float) dy) * s,
                    (right.y * (float) -dx + up.y * (float) dy) * s,
                    (right.z * (float) -dx + up.z * (float) dy) * s);
            return true;
        }

        if (btn == 0) {
            // Cache current focal values to freeze them during rotation
            float originalZoom = camera.getZoom();
            float targetX = camera.getTargetX();
            float targetY = camera.getTargetY();
            float targetZ = camera.getTargetZ();

            // Perform pure rotation
            camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY,
                    (float) dy * CAM_ORBIT_SENSITIVITY);

            // Re-bind to the fixed pivot point and distance
            camera.setTarget(targetX, targetY, targetZ);
            camera.setPosition(camera.getYaw(), camera.getPitch(), originalZoom);
            return true;
        }

        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= this.width - getCurrentPanelWidth()) return false;
        if (camera == null || camera.isLocked()) return false;
        camera.zoom(delta > 0 ? CAM_ZOOM_IN_FACTOR : CAM_ZOOM_OUT_FACTOR,
                CAM_ZOOM_MIN, CAM_ZOOM_MAX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 2 || btn == 0) isPanning = false;
        if (scrubbing) {
            int px = this.width - getCurrentPanelWidth();
            scrubTo(Mth.clamp((float) (mx - 80) / (px - 80 - 65), 0f, 1f));
            scrubbing = false;
            applyVisibility();
        }
        return super.mouseReleased(mx, my, btn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void toggleViewFilter(ViewFilter vf) {
        if (viewFilter == vf) {
            viewFilter = ViewFilter.ALL;
            if (wasPlayingBeforeFilter) playing = true;
            wasPlayingBeforeFilter = false;
        } else {
            if (viewFilter == ViewFilter.ALL) {
                wasPlayingBeforeFilter = playing;
                playing = false;
            }
            viewFilter = vf;
        }
        applyVisibility();
    }

    private void centerCamera() {
        if (camera == null || pattern == null) return;

        // 1. Resolve where the camera is *going* to go
        float[] defaultTarget = resolveTarget();

        // 2. Calculate the Euclidean distance from where the camera is *currently* looking
        float dx = camera.getTargetX() - defaultTarget[0];
        float dy = camera.getTargetY() - defaultTarget[1];
        float dz = camera.getTargetZ() - defaultTarget[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 3. Scale the ticks dynamically using Minecraft's Mth utility.
        // Small jump (e.g., distance ~0) = 12 ticks. Max jump caps out at 26 ticks.
        int dynamicTicks = (int) Mth.clamp(12 + (distance * 0.4), 12, 26);

        resetCameraToDefault(LerpType.EASE_OUT, dynamicTicks);
    }

    private void nudgeLayer(int delta) {
        if (pattern == null) return;
        if (manualLayer < 0) {
            // FIX (bug 3): initialise to the boundary layer on first nudge,
            // then always clamp to [minY, maxY] so the set is never empty.
            manualLayer = (delta < 0) ? pattern.maxY : pattern.minY;
        } else {
            manualLayer = Mth.clamp(manualLayer + delta, pattern.minY, pattern.maxY);
        }
        applyVisibility();
    }

    private void buildOrderStep(int delta) {
        if (pattern == null) return;
        buildOrderGroup = Mth.clamp(buildOrderGroup + delta, 0,
                pattern.buildOrder.size() - 1);
        applyVisibility();
    }

    private void scrubTo(float t) {
        scrubbing = true;
        playing = false;
        playbackTick = (int) (Mth.clamp(t, 0f, 1f) * script.getTotalTicks());
        PhantasiaScript.Step step = script.getActiveStep(playbackTick);
        if (step != lastAppliedStep) {
            lastAppliedStep = step;
            updateCaptionForStep(step);
            applyVisibility();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sub-screen navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void openScriptEditor() {
        if (camera != null) camera.save();
        String machineId = definition.getId().toString();
        PhantasiaScriptData current = script.getSourceData();
        if (current == null) current = PhantasiaScriptData.defaultFor(machineId);
        Minecraft.getInstance().setScreen(
                new PhantasiaScriptEditorScreen(this, machineId, current));
    }

    private void openFootprintScreen() {
        if (pattern == null) return;
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaFootprintScreen(pattern, this, script));
    }

    private void openBlockFilterScreen() {
        if (pattern == null) return;
        if (camera != null) camera.save();
        Minecraft.getInstance().setScreen(
                new PhantasiaBlockFilterScreen(pattern, script, viewFilter, this));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public float getRotationYaw() {
        return camera != null ? camera.getYaw() : 0f;
    }

    public float getRotationPitch() {
        return camera != null ? camera.getPitch() : 0f;
    }

    public PhantasiaLoadedPattern getLoadedPattern() {
        return pattern;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h, String label, Runnable action) {
        regBtn(g, mx, my, x, y, w, h, label, null, action);
    }

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h, String label, Component tooltip, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov, C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
        if (hov && tooltip != null) pendingTooltip = tooltip;
    }

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h,
                        String label, boolean active, Runnable action) {
        regBtn(g, mx, my, x, y, w, h, label, active, null, action);
    }

    private void regBtn(GuiGraphics g, int mx, int my,
                        int x, int y, int w, int h,
                        String label, boolean active, Component tooltip, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawThemedBtn(g, font, x, y, w, h, label, hov,
                active ? C_BTN_ACT() : C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
        if (hov && tooltip != null) pendingTooltip = tooltip;
    }

    private void regIconBtn(GuiGraphics g, int mx, int my,
                            int x, int y, int w, int h,
                            String icon, String label, boolean active, Runnable action) {
        regIconBtn(g, mx, my, x, y, w, h, icon, label, active, null, action);
    }

    private void regIconBtn(GuiGraphics g, int mx, int my,
                            int x, int y, int w, int h,
                            String icon, String label, boolean active, Component tooltip, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        PhantasiaThemeUtils.drawIconBtn(g, font, x, y, w, h, icon, label, hov,
                active ? C_BTN_ACT() : C_BTN());
        activeButtons.add(new PhantasiaUIUtils.ButtonAction(x, y, w, h, action));
        if (hov && tooltip != null) pendingTooltip = tooltip;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Misc
    // ─────────────────────────────────────────────────────────────────────────

    private int getCurrentPanelWidth() {
        return sidePanelCollapsed ? COLLAPSED_PANEL_W : FULL_PANEL_W;
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2)
            s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    private static String formatTicks(int t) {
        return String.format("%d.%02ds", t / 20, (t % 20) * 5);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        invalidateSharedLevel();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
