package net.phoenixvine.phantasia.client.screens;

import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.client.camera.CameraView;
import net.phoenixvine.phantasia.client.camera.LerpType;
import net.phoenixvine.phantasia.client.camera.PhantasiaCamera;
import net.phoenixvine.phantasia.client.render.PhantasiaWorldRenderer;
import net.phoenixvine.phantasia.common.PhantasiaLoadedPattern;
import net.phoenixvine.phantasia.common.PhantasiaScript;
import net.phoenixvine.phantasia.common.PhantasiaScriptData;
import net.phoenixvine.phantasia.common.PhantasiaScriptLoader;

import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class PhantasiaScriptEditorScreen extends Screen {

    // ── Theme ─────────────────────────────────────────────────────────────────
    private static final int C_BG = 0xFF080810;
    private static final int C_BAR = 0xEE0A0A14;
    private static final int C_PANEL = 0xDD0C0C1A;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_BTN = 0xBB151528;
    private static final int C_BTN_HOV = 0xBB1A2840;
    private static final int C_BTN_ACT = 0xFF0D3050;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_DIM = 0xFF667788;
    private static final int C_WARN = 0xFFFFB74D;
    private static final int C_GREEN = 0xFF66BB6A;
    private static final int C_RED = 0xFFFF5252;

    // Mistake colour palette (displayed as swatches in ANNOTATE mode)
    private static final int[] MISTAKE_COLORS = {
            0xFFFFB74D, 0xFFFF5252, 0xFF66BB6A, 0xFF4FC3F7, 0xFFCE93D8, 0xFFFFFFFF
    };
    private static final String[] MISTAKE_COLOR_NAMES = {
            "Amber", "Red", "Green", "Cyan", "Purple", "White"
    };

    private static final int TOP_BAR_H = 22;
    private static final int STEP_ROW_H = 50;
    private static final int TIMELINE_H = 22;
    private static final int BOTTOM_H = STEP_ROW_H + TIMELINE_H;

    // Camera sensitivity constants (matching PhantasiaSceneScreen)
    private static final float CAM_ORBIT_SENSITIVITY = 0.35f;
    private static final float CAM_PAN_SENSITIVITY = 0.02f;

    private static final String[] SHOW_MODES = { "all", "layer", "layers", "pos", "parts", "controller", "functional" };
    private static final String[] SHOW_LABELS = { "All", "Layer", "Range", "Pos", "Parts", "Ctrl", "Func" };

    // ── Mode ──────────────────────────────────────────────────────────────────
    private enum Mode {
        SELECT,
        ANNOTATE
    }

    private Mode mode = null; // null = default/camera-only state

    // ── Data ──────────────────────────────────────────────────────────────────
    private final PhantasiaSceneScreen parentScene;
    private final String machineId;
    private PhantasiaScriptData data;
    private boolean dirty = false;
    private int selectedStep = 0;

    // ── Own 3D world ──────────────────────────────────────────────────────────
    private PhantasiaWorldRenderer renderer;
    private TrackedDummyWorld editorLevel;
    private PhantasiaLoadedPattern pattern;

    // ── Camera ────────────────────────────────────────────────────────────────
    private PhantasiaCamera camera;

    // ── SELECT mode ───────────────────────────────────────────────────────────
    private final Set<BlockPos> selectedWorldPos = new LinkedHashSet<>();
    private BlockPos hoveredWorldPos = null;
    private float selectPulse = 0f;
    private boolean pulseUp = true;

    // ── ANNOTATE mode ─────────────────────────────────────────────────────────
    private BlockPos pendingAnnotationLocalPos = null;
    private String pendingAnnotationLabel = "";
    private int selectedMistakeColor = 0;
    private int hoveredMistakeIndex = -1;

    // ── Layer slider ──────────────────────────────────────────────────────────
    private boolean draggingLayer = false;
    private boolean draggingLayerMax = false;

    // ── Timeline dragging ─────────────────────────────────────────────────────
    private int draggingTimelineDot = -1;
    private boolean dotDragMoved = false;
    private double dotDragStartMX = 0;

    // ── Timeline hover ghost ──────────────────────────────────────────────────
    private int timelineGhostX = -1;
    private int timelineGhostTick = -1;

    // ── Step reordering (Alt+drag) ────────────────────────────────────────────
    private int reorderingStep = -1;
    private int reorderInsertAt = -1;

    // ── SELECT mode deferred click (committed on release if not dragged) ──────
    private boolean selectClickPending = false;
    private int selectClickBtn = 0;
    private double selectClickMX = 0;
    private double selectClickMY = 0;

    // ── Undo stack ────────────────────────────────────────────────────────────
    private static final int MAX_UNDO = 20;
    private final ArrayDeque<PhantasiaScriptData> undoStack = new ArrayDeque<>();

    // ── Preview playback ──────────────────────────────────────────────────────
    private boolean previewing = false;
    private int previewTick = 0;
    private float previewAccum = 0f;

    // ── Unsaved-changes confirmation dialog ───────────────────────────────────
    private boolean showingCloseConfirm = false;

    // ── Inputs ────────────────────────────────────────────────────────────────
    private EditBox tickBox;
    private EditBox hideLayerBox;
    private EditBox hidePosBox;
    private EditBox fakeRecipeBox;
    private EditBox lerpTicksBox;
    private EditBox camZoomBox;
    private EditBox scriptDurationBox;
    // startCamera panel fields
    private EditBox scYawBox;
    private EditBox scPitchBox;
    private EditBox scZoomBox;
    private EditBox scOffsetXBox;
    private EditBox scOffsetYBox;
    private EditBox scOffsetZBox;

    // ── Start-camera panel toggle ─────────────────────────────────────────────
    private boolean showStartCamPanel = false;

    // ── Button registry ───────────────────────────────────────────────────────
    private record Btn(int x, int y, int w, int h, Runnable action) {

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> btns = new ArrayList<>(64);

    private int lastMouseX = 0;
    private int lastMouseY = 0;

    private Mode hoveredModeBtnThisFrame = null;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public PhantasiaScriptEditorScreen(PhantasiaSceneScreen parent,
                                       String machineId,
                                       PhantasiaScriptData original) {
        super(Component.literal("Editor"));
        this.parentScene = parent;
        this.machineId = machineId;
        this.data = original.copy();
        ensureOneStep();
    }

    private void ensureOneStep() {
        if (data.getSteps().isEmpty()) {
            PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(0, null);
            s.show = "all";
            data.getSteps().add(s);
        }
        selectedStep = Mth.clamp(selectedStep, 0, data.getSteps().size() - 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        if (editorLevel == null) setupEditorWorld();

        if (renderer == null) {
            renderer = new PhantasiaWorldRenderer(editorLevel);
            if (pattern != null) renderer.setBaseplatePositions(pattern.baseplatePositions);
            initCamera();
        } else {
            // Renderer persists across resizes — nothing to rebuild.
            // Camera also persists; its view is recomputed from current state each frame.
        }

        buildInputWidgets();
        populateInputsFromStep();
        rebuildVisibility();
    }

    private void setupEditorWorld() {
        pattern = parentScene != null ? parentScene.getLoadedPattern() : null;
        if (pattern == null) {
            onClose();
            return;
        }
        editorLevel = new TrackedDummyWorld();
        editorLevel.addBlocks(pattern.blockMap);
    }

    private void initCamera() {
        if (pattern == null) return;
        float midY = pattern.origin.getY() + (pattern.minY + pattern.maxY) * 0.5f + 0.5f;
        BlockPos cp = pattern.controllerWorldPos != null ? pattern.controllerWorldPos : pattern.origin;
        float tgtX = cp.getX() + 0.5f;
        float tgtZ = cp.getZ() + 0.5f;
        int machineH = pattern.maxY - pattern.minY + 1;
        float dist = 15f + Math.max(0, machineH - 8) * 1.5f;

        float yaw = -135f, pitch = -30f;
        if (!data.getSteps().isEmpty() && data.getSteps().get(0).camera != null) {
            yaw = data.getSteps().get(0).camera.yaw;
            pitch = data.getSteps().get(0).camera.pitch;
        }

        camera = new PhantasiaCamera(yaw, pitch, dist, tgtX, midY, tgtZ);
        camera.setFloorY(pattern.origin.getY() + 0.5f);
    }

    private void buildInputWidgets() {
        clearWidgets();

        tickBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        tickBox.setMaxLength(5);
        tickBox.setFilter(s -> s.matches("\\d*"));
        tickBox.setResponder(v -> {
            try {
                step().tick = Integer.parseInt(v);
                dirty = true;
            } catch (NumberFormatException ignored) {}
        });

        hideLayerBox = addW(new EditBox(font, 0, 0, 30, 12, Component.empty()));
        hideLayerBox.setMaxLength(4);
        hideLayerBox.setFilter(s -> s.matches("-?\\d*"));
        hideLayerBox.setHint(Component.literal("-1"));
        hideLayerBox.setResponder(v -> {
            try {
                step().hideLayer = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                step().hideLayer = -1;
            }
            dirty = true;
            rebuildVisibility();
        });

        hidePosBox = addW(new EditBox(font, 0, 0, 120, 12, Component.empty()));
        hidePosBox.setMaxLength(512);
        hidePosBox.setHint(Component.literal("x,y,z; x,y,z ..."));
        hidePosBox.setResponder(v -> {
            step().hidePositions = parsePosList(v);
            dirty = true;
            rebuildVisibility();
        });

        fakeRecipeBox = addW(new EditBox(font, 0, 0, 180, 12, Component.empty()));
        fakeRecipeBox.setMaxLength(256);
        fakeRecipeBox.setHint(Component.literal("gtceu:fusion/recipe_name"));
        fakeRecipeBox.setResponder(v -> {
            step().fakeRecipeId = v.isBlank() ? null : v.trim();
            dirty = true;
        });
        fakeRecipeBox.visible = false;
        fakeRecipeBox.active = false;

        lerpTicksBox = addW(new EditBox(font, 0, 0, 34, 12, Component.empty()));
        lerpTicksBox.setMaxLength(4);
        lerpTicksBox.setFilter(s -> s.matches("\\d*"));
        lerpTicksBox.setHint(Component.literal("20"));
        lerpTicksBox.setResponder(v -> {
            PhantasiaScriptData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.lerpTicks = Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
                s.camera.lerpTicks = 20;
            }
            dirty = true;
        });
        lerpTicksBox.visible = false;
        lerpTicksBox.active = false;

        camZoomBox = addW(new EditBox(font, 0, 0, 40, 12, Component.empty()));
        camZoomBox.setMaxLength(7);
        camZoomBox.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        camZoomBox.setHint(Component.literal("auto"));
        camZoomBox.setResponder(v -> {
            PhantasiaScriptData.StepData s = step();
            if (s.camera == null) return;
            try {
                s.camera.zoom = Float.parseFloat(v);
            } catch (NumberFormatException ignored) {
                s.camera.zoom = -1f;
            }
            dirty = true;
        });
        camZoomBox.visible = false;
        camZoomBox.active = false;

        scriptDurationBox = addW(new EditBox(font, 0, 0, 46, 12, Component.empty()));
        scriptDurationBox.setMaxLength(6);
        scriptDurationBox.setFilter(s -> s.matches("\\d*"));
        scriptDurationBox.setHint(Component.literal("auto"));
        scriptDurationBox.setResponder(v -> {
            try {
                data.setScriptDuration(v.isBlank() ? -1 : Integer.parseInt(v));
            } catch (NumberFormatException ignored) {
                data.setScriptDuration(-1);
            }
            dirty = true;
        });
        scriptDurationBox.visible = false;
        scriptDurationBox.active = false;

        // ── Start-camera panel boxes ──────────────────────────────────────────
        scYawBox = makeFloatBox(v -> {
            ensureStartCam().yaw = v;
            dirty = true;
        }, "−135");
        scPitchBox = makeFloatBox(v -> {
            ensureStartCam().pitch = v;
            dirty = true;
        }, "−35");
        scZoomBox = makeFloatBox(v -> {
            ensureStartCam().zoom = v;
            dirty = true;
        }, "auto");
        scOffsetXBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetX = v;
            dirty = true;
        }, "0");
        scOffsetYBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetY = v;
            dirty = true;
        }, "0");
        scOffsetZBox = makeFloatBox(v -> {
            ensureStartCam().targetOffsetZ = v;
            dirty = true;
        }, "0");
    }

    /** Creates a small float EditBox wired to the given float setter. */
    private EditBox makeFloatBox(java.util.function.Consumer<Float> setter, String hint) {
        EditBox box = addW(new EditBox(font, 0, 0, 44, 12, Component.empty()));
        box.setMaxLength(8);
        box.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> {
            try {
                setter.accept(Float.parseFloat(v));
            } catch (NumberFormatException ignored) {}
        });
        box.visible = false;
        box.active = false;
        return box;
    }

    private PhantasiaScriptData.StartCameraData ensureStartCam() {
        if (data.getStartCamera() == null)
            data.setStartCamera(new PhantasiaScriptData.StartCameraData());
        return data.getStartCamera();
    }

    private void clearStartCam() {
        data.setStartCamera(null);
        populateStartCamBoxes();
        dirty = true;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T addW(T w) {
        return addRenderableWidget(w);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tick
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (camera != null) camera.tick();

        // SELECT pulse
        if (mode == Mode.SELECT) {
            selectPulse += pulseUp ? 0.07f : -0.07f;
            if (selectPulse >= 1f) {
                selectPulse = 1f;
                pulseUp = false;
            }
            if (selectPulse <= 0f) {
                selectPulse = 0f;
                pulseUp = true;
            }
        }

        // Preview playback
        if (previewing) {
            previewAccum += 1f;
            while (previewAccum >= 1f) {
                previewAccum -= 1f;
                previewTick++;
            }
            int total = computeTotalTicks();
            if (previewTick >= total) previewTick = 0;
            for (int i = data.getSteps().size() - 1; i >= 0; i--) {
                if (data.getSteps().get(i).tick <= previewTick) {
                    if (i != selectedStep) {
                        selectedStep = i;
                        rebuildVisibility();
                    }
                    break;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visibility + block highlighting
    // ─────────────────────────────────────────────────────────────────────────

    private void rebuildVisibility() {
        if (renderer == null || pattern == null) return;

        PhantasiaScriptData.StepData s = step();
        Set<BlockPos> visible = new HashSet<>(pattern.baseplatePositions);

        if (mode == Mode.SELECT) {
            // Show every machine block so the user can click them.
            for (BlockPos wp : pattern.localToWorld.values()) visible.add(wp);
        } else {
            PhantasiaScriptData tmp = new PhantasiaScriptData(machineId);
            tmp.getSteps().add(s);
            PhantasiaScript tmpScript = PhantasiaScript.fromData(tmp);
            PhantasiaScript.Step compiled = tmpScript.getActiveStep(0);
            for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
                if (compiled == null || compiled.filter().test(e.getKey()))
                    visible.add(e.getValue());
            }
        }

        renderer.setVisible(visible);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        btns.clear();
        hideAllInputs();
        lastMouseX = mx;
        lastMouseY = my;

        g.fill(0, 0, this.width, this.height, C_BG);

        // 3D scene
        if (renderer != null && camera != null) {
            int sceneH = this.height - TOP_BAR_H - BOTTOM_H;
            renderer.setMousePos(mx, my); // raw screen coords — renderer handles the viewport offset internally
            CameraView view = camera.getView(partial);
            renderer.render(view, 0, TOP_BAR_H, this.width, sceneH);

            BlockHitResult hit = renderer.getLastHitResult();
            hoveredWorldPos = (hit != null && hit.getType() == HitResult.Type.BLOCK) ? hit.getBlockPos() : null;
        }

        // Mode-specific in-scene overlays (screen space)
        renderInSceneOverlays(g, mx, my);

        // UI chrome
        renderTopBar(g, mx, my);
        renderModeTooltipBanner(g);
        renderLayerSlider(g, mx, my);
        renderStepRow(g, mx, my);
        renderTimeline(g, mx, my);
        if (showStartCamPanel) renderStartCamPanel(g, mx, my);

        // EditBoxes on top
        super.render(g, mx, my, partial);

        // Tooltip: hovered block name in SELECT mode
        if (hoveredWorldPos != null && editorLevel != null && mode == Mode.SELECT) {
            try {
                BlockState bs = editorLevel.getBlockState(hoveredWorldPos);
                if (!bs.isAir()) g.renderTooltip(font, bs.getBlock().getName(), mx, my);
            } catch (Exception ignored) {}
        }

        // Unsaved-changes confirmation dialog (rendered last)
        if (showingCloseConfirm) renderCloseConfirmDialog(g, mx, my);
    }

    private void renderModeTooltipBanner(GuiGraphics g) {
        if (hoveredModeBtnThisFrame == null) return;
        if (hoveredModeBtnThisFrame == mode) return;
        String tip;
        if (hoveredModeBtnThisFrame == Mode.SELECT)
            tip = "SELECT — Click blocks to add/remove from this step's position list";
        else
            tip = "ANNOTATE — Click any block to attach a floating mistake label";
        drawBanner(g, tip, TOP_BAR_H + 4, C_DIM);
    }

    private void renderCloseConfirmDialog(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0xBB000000);

        int dw = 280, dh = 70;
        int dx = (this.width - dw) / 2, dy = (this.height - dh) / 2;

        g.fill(dx, dy, dx + dw, dy + dh, C_PANEL);
        g.fill(dx, dy, dx + dw, dy + 1, C_WARN);

        g.drawCenteredString(font, "Unsaved changes — discard and close?", dx + dw / 2, dy + 10, C_WARN);
        g.drawCenteredString(font, "All edits since your last save will be lost.", dx + dw / 2, dy + 22, C_DIM);

        int btnY = dy + dh - 20;
        btn(g, mx, my, dx + dw / 2 - 118, btnY, 110, 14, "✕ Discard & Close", C_RED, this::forceClose);
        btn(g, mx, my, dx + dw / 2 + 8, btnY, 110, 14, "↩ Keep Editing", C_BTN, () -> showingCloseConfirm = false);
    }

    private void renderInSceneOverlays(GuiGraphics g, int mx, int my) {
        renderMistakeMarkers(g);
        if (mode == Mode.SELECT) renderSelectOverlay(g, mx, my);
        if (mode == Mode.ANNOTATE) renderAnnotateOverlay(g, mx, my);
    }

    private void renderMistakeMarkers(GuiGraphics g) {
        if (data.getMistakes().isEmpty() || pattern == null || camera == null) return;
        hoveredMistakeIndex = -1;

        // Get eye/lookat from our camera, not from a renderer field
        CameraView view = camera.getView(0f);
        Vector3f eye = view.eyePos();
        Vector3f lookat = view.lookAt();

        Vector3f fwd = new Vector3f(lookat).sub(eye).normalize();
        Vector3f rgt = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
        float fov = this.height / (2f * (float) Math.tan(Math.toRadians(PhantasiaCamera.FOV)));

        for (int i = 0; i < data.getMistakes().size(); i++) {
            PhantasiaScriptData.MistakeData m = data.getMistakes().get(i);
            BlockPos local = new BlockPos(m.x, m.y, m.z);
            BlockPos world = pattern.localToWorld.get(local);
            if (world == null) continue;

            float wx = world.getX() + 0.5f, wy = world.getY() + 1.4f, wz = world.getZ() + 0.5f;
            Vector3f toP = new Vector3f(wx - eye.x(), wy - eye.y(), wz - eye.z());
            float depth = toP.dot(fwd);
            if (depth < 0.5f) continue;

            float sx = this.width / 2f + (toP.dot(rgt) / depth) * fov;
            float sy = this.height / 2f - (toP.dot(upv) / depth) * fov;
            int isx = (int) sx, isy = (int) sy;

            if (isy < TOP_BAR_H || isy > this.height - BOTTOM_H) continue;

            int col = m.colorArgb();
            String lbl = m.label;
            int lw = font.width(lbl) + 8;

            g.fill(isx - lw / 2 - 1, isy - 6, isx + lw / 2 + 1, isy + 8, 0xCC000000);
            g.fill(isx - lw / 2 - 1, isy - 6, isx + lw / 2 + 1, isy - 5, col);
            g.drawCenteredString(font, lbl, isx, isy - 3, col);
            g.fill(isx - 1, isy + 8, isx + 1, isy + 14, col & 0x88FFFFFF);

            if (mode == Mode.ANNOTATE && isOver(lastMouseX, lastMouseY, isx - lw / 2 - 1, isy - 6, lw + 2, 14))
                hoveredMistakeIndex = i;
        }
    }

    private void renderSelectOverlay(GuiGraphics g, int mx, int my) {
        int hy = TOP_BAR_H + 4;
        String hint = selectedWorldPos.isEmpty() ?
                "Left-click blocks to add to step  |  Ctrl+A: select all  |  Ctrl+D: clear" :
                selectedWorldPos.size() + " block" + (selectedWorldPos.size() == 1 ? "" : "s") +
                        " selected  —  Left-click to toggle  |  Right-click to remove";
        drawBanner(g, hint, hy, C_ACCENT);

        if (hoveredWorldPos != null && pattern != null) {
            BlockPos local = pattern.toLocal(hoveredWorldPos);
            if (local != null && !pattern.baseplatePositions.contains(hoveredWorldPos)) {
                boolean isSel = isInPositionList(local);
                g.drawCenteredString(font, isSel ? "▼ Remove from step" : "▲ Add to step",
                        this.width / 2, hy + 20, isSel ? C_WARN : C_GREEN);
            }
        }

        if (pattern != null && camera != null) {
            CameraView view = camera.getView(0f);
            Vector3f eye = view.eyePos();
            Vector3f lookat = view.lookAt();
            Vector3f fwd = new Vector3f(lookat).sub(eye).normalize();
            Vector3f rgt = new Vector3f(fwd).cross(new Vector3f(0, 1, 0)).normalize();
            Vector3f upv = new Vector3f(rgt).cross(fwd).normalize();
            float fov = this.height / (2f * (float) Math.tan(Math.toRadians(PhantasiaCamera.FOV)));

            for (BlockPos wp : selectedWorldPos) {
                float[] sc = projectToScreen(wp.getX() + 0.5f, wp.getY() + 0.5f, wp.getZ() + 0.5f,
                        eye, fwd, rgt, upv, fov);
                if (sc == null || sc[2] < 0.3f) continue;
                int isx = (int) sc[0], isy = (int) sc[1];
                if (isy < TOP_BAR_H || isy > this.height - BOTTOM_H) continue;

                int alpha = (int) (0.5f + selectPulse * 0.5f) * 0xAA;
                alpha = Mth.clamp(alpha, 0x44, 0xBB);
                int col = (alpha << 24) | (C_ACCENT & 0x00FFFFFF);
                g.fill(isx - 3, isy - 3, isx + 3, isy + 3, col);
                g.fill(isx - 5, isy - 1, isx + 5, isy + 1, col & 0x66FFFFFF);
                g.fill(isx - 1, isy - 5, isx + 1, isy + 5, col & 0x66FFFFFF);
            }

            if (hoveredWorldPos != null && !selectedWorldPos.contains(hoveredWorldPos) &&
                    !pattern.baseplatePositions.contains(hoveredWorldPos)) {
                float[] sc = projectToScreen(hoveredWorldPos.getX() + 0.5f, hoveredWorldPos.getY() + 0.5f,
                        hoveredWorldPos.getZ() + 0.5f, eye, fwd, rgt, upv, fov);
                if (sc != null && sc[2] > 0.3f) {
                    int isx = (int) sc[0], isy = (int) sc[1];
                    g.fill(isx - 4, isy - 4, isx + 4, isy + 4, 0x66FFFFFF);
                }
            }
        }
    }

    private void renderAnnotateOverlay(GuiGraphics g, int mx, int my) {
        if (pendingAnnotationLocalPos != null) {
            int px = this.width / 2 - 160;
            int py = this.height - BOTTOM_H - 52;
            int pw = 320;

            g.fill(px, py, px + pw, py + 50, C_BAR);
            g.fill(px, py, px + pw, py + 1, C_WARN);

            // Label preview / open-subscreen button
            String labelPreview = pendingAnnotationLabel.isBlank() ? "✎  Type label..." :
                    "✎  " + pendingAnnotationLabel;
            int lpW = pw - 14;
            boolean lpHov = isOver(mx, my, px + 6, py + 4, lpW, 14);
            g.fill(px + 6, py + 4, px + 6 + lpW, py + 18,
                    lpHov ? C_BTN_HOV : C_BTN);
            g.fill(px + 6, py + 4, px + 6 + lpW, py + 5, C_WARN);
            g.drawString(font, labelPreview, px + 10, py + 7,
                    pendingAnnotationLabel.isBlank() ? C_DIM : C_TEXT, false);
            btns.add(new Btn(px + 6, py + 4, lpW, 14, this::openAnnotationLabelInput));

            int sx = px + 6, sy = py + 22;
            g.drawString(font, "Colour:", sx, sy + 1, C_DIM, false);
            sx += 44;
            for (int i = 0; i < MISTAKE_COLORS.length; i++) {
                boolean sel = (i == selectedMistakeColor);
                boolean hov = isOver(mx, my, sx, sy, 16, 12);
                g.fill(sx, sy, sx + 16, sy + 12, MISTAKE_COLORS[i]);
                if (sel) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFFFFFFFF);
                    g.fill(sx - 1, sy + 12, sx + 17, sy + 13, 0xFFFFFFFF);
                }
                int fi = i;
                btns.add(new Btn(sx, sy, 16, 12, () -> selectedMistakeColor = fi));
                if (hov) g.drawString(font, MISTAKE_COLOR_NAMES[i], sx, sy + 14, C_DIM, false);
                sx += 20;
            }

            int btnY = py + 36;
            btn(g, mx, my, px + pw - 120, btnY, 54, 12, "✓ Add", C_GREEN, this::confirmAnnotation);
            btn(g, mx, my, px + pw - 62, btnY, 54, 12, "✕ Cancel", C_BTN, this::cancelAnnotation);
            g.drawString(font, "Marking: " + pendingAnnotationLocalPos.toShortString(), px + 6, btnY + 1, C_DIM, false);
            return;
        }

        String hint = hoveredMistakeIndex >= 0 ? "Right-click marker to remove  |  Left-click block to add" :
                "Left-click any block to add a mistake marker";
        drawBanner(g, hint, TOP_BAR_H + 4, C_WARN);

        if (mode == Mode.ANNOTATE) {
            int glx = this.width - 310;
            int gly = this.height - BOTTOM_H + STEP_ROW_H + 4;
            g.drawString(font, "Global note:", glx - 74, gly + 2, C_DIM, false);
            boolean gnHov = isOver(mx, my, glx, gly, 220, 14);
            g.fill(glx, gly, glx + 220, gly + 14, gnHov ? C_BTN_HOV : C_BTN);
            g.fill(glx, gly, glx + 220, gly + 1, C_WARN);
            g.drawString(font, "✎  Add global note...", glx + 4, gly + 3, C_DIM, false);
            btns.add(new Btn(glx, gly, 220, 14, this::openGlobalMistakeInput));
        }
    }

    private void openAnnotationLabelInput() {
        Minecraft.getInstance().setScreen(new PhantasiaTextInputScreen(
                this, "Annotation Label", "e.g. Wrong block here", pendingAnnotationLabel, 128,
                v -> {
                    pendingAnnotationLabel = v;
                }));
    }

    private void openGlobalMistakeInput() {
        Minecraft.getInstance().setScreen(new PhantasiaTextInputScreen(
                this, "Global Mistake Note", "e.g. Controller must face south", "", 256,
                v -> {
                    if (!v.isBlank()) {
                        checkpoint();
                        data.getGlobalMistakes().add(v.trim());
                        dirty = true;
                    }
                }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top bar
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTopBar(GuiGraphics g, int mx, int my) {
        hoveredModeBtnThisFrame = null;
        g.fill(0, 0, this.width, TOP_BAR_H, C_BAR);
        g.fill(0, TOP_BAR_H - 1, this.width, TOP_BAR_H, C_ACCENT);

        int x = 6;
        x = modeBtn(g, mx, my, x, Mode.SELECT, "◈ Select");
        x = modeBtn(g, mx, my, x, Mode.ANNOTATE, "⚠ Annotate");

        x += 6;
        boolean ph = isOver(mx, my, x, 3, 70, TOP_BAR_H - 6);
        g.fill(x, 3, x + 70, TOP_BAR_H - 3, previewing ? C_BTN_ACT : (ph ? C_BTN_HOV : C_BTN));
        if (previewing) g.fill(x, TOP_BAR_H - 3, x + 70, TOP_BAR_H - 2, C_GREEN);
        g.drawString(font, previewing ? "⏹ Stop" : "▶ Preview",
                x + 5, (TOP_BAR_H - 8) / 2, previewing ? C_GREEN : C_DIM, false);
        btns.add(new Btn(x, 3, 70, TOP_BAR_H - 6, this::togglePreview));
        x += 74;

        boolean hasStartCam = data.getStartCamera() != null;
        boolean scHov = isOver(mx, my, x, 3, 80, TOP_BAR_H - 6);
        g.fill(x, 3, x + 80, TOP_BAR_H - 3,
                showStartCamPanel ? C_BTN_ACT : (scHov ? C_BTN_HOV : C_BTN));
        if (hasStartCam) g.fill(x, TOP_BAR_H - 3, x + 80, TOP_BAR_H - 2, C_ACCENT);
        g.drawString(font, "⊙ Start Cam", x + 5, (TOP_BAR_H - 8) / 2,
                hasStartCam ? C_ACCENT : C_DIM, false);
        if (scHov) g.renderTooltip(font,
                Component.literal(
                        "Set the initial camera framing for this machine (yaw, pitch, zoom, target offset). Independent of step cameras."),
                mx, my);
        btns.add(new Btn(x, 3, 80, TOP_BAR_H - 6, () -> showStartCamPanel = !showStartCamPanel));

        int rx = this.width - 4;
        rx = topBtn(g, mx, my, rx, "✕ Back", C_BTN, this::onClose);
        rx = topBtn(g, mx, my, rx, "💾 Save", C_GREEN, this::save);
        if (dirty) {
            String dot = "● unsaved";
            rx -= font.width(dot) + 10;
            g.drawString(font, dot, rx, (TOP_BAR_H - 8) / 2, C_WARN, false);
        }
    }

    private int modeBtn(GuiGraphics g, int mx, int my, int x, Mode m, String label) {
        int w = font.width(label) + 12;
        boolean act = (mode == m);
        boolean hov = isOver(mx, my, x, 3, w, TOP_BAR_H - 6);
        if (hov) hoveredModeBtnThisFrame = m;
        g.fill(x, 3, x + w, TOP_BAR_H - 3, act ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
        if (act) g.fill(x, TOP_BAR_H - 3, x + w, TOP_BAR_H - 2, C_ACCENT);
        g.drawString(font, label, x + 6, (TOP_BAR_H - 8) / 2, act ? C_ACCENT : C_TEXT, false);
        // Clicking an active mode button deactivates it (back to default/camera state)
        btns.add(new Btn(x, 3, w, TOP_BAR_H - 6, () -> setMode(act ? null : m)));
        return x + w + 4;
    }

    private int topBtn(GuiGraphics g, int mx, int my, int rx, String label, int color, Runnable action) {
        int w = font.width(label) + 10;
        int x = rx - w, y = 3, h = TOP_BAR_H - 6;
        boolean hov = isOver(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : color);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawString(font, label, x + 5, (TOP_BAR_H - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, y, w, h, action));
        return x - 4;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer slider
    // ─────────────────────────────────────────────────────────────────────────

    private void renderLayerSlider(GuiGraphics g, int mx, int my) {
        PhantasiaScriptData.StepData s = step();
        if (!"layer".equals(s.show) && !"layers".equals(s.show)) return;
        if (pattern == null) return;

        int sliderX = 10;
        int sceneTop = TOP_BAR_H;
        int sceneBottom = this.height - BOTTOM_H;
        int sliderH = sceneBottom - sceneTop - 24;
        int sliderY = sceneTop + 12;

        g.drawString(font, "Layer", sliderX - 2, sliderY - 20, C_DIM, false);
        g.drawString(font, "filter", sliderX - 2, sliderY - 11, C_DIM, false);
        g.fill(sliderX + 3, sliderY, sliderX + 7, sliderY + sliderH, 0x44FFFFFF);
        g.drawString(font, "Y=" + pattern.maxY, sliderX + 16, sliderY - 1, C_DIM, false);
        g.drawString(font, "Y=" + pattern.minY, sliderX + 16, sliderY + sliderH - 1, C_DIM, false);

        int range = Math.max(1, pattern.maxY - pattern.minY);

        if ("layer".equals(s.show)) {
            float t = 1f - (float) (s.layer - pattern.minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            boolean hov = isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14) || draggingLayer;
            g.fill(sliderX - 2, thumbY - 6, sliderX + 16, thumbY + 6, hov ? C_ACCENT : C_BTN_ACT);
            g.drawString(font, String.valueOf(s.layer), sliderX + 18, thumbY - 4, C_ACCENT, false);
            for (int y = pattern.minY; y <= pattern.maxY; y++) {
                float ft = 1f - (float) (y - pattern.minY) / range;
                int fy = sliderY + (int) (ft * sliderH);
                g.fill(sliderX + 4, fy, sliderX + 6, fy + 1, 0x33FFFFFF);
            }
        } else {
            float tMin = 1f - (float) (s.layerMin - pattern.minY) / range;
            float tMax = 1f - (float) (s.layerMax - pattern.minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH);
            int tyMax = sliderY + (int) (tMax * sliderH);
            g.fill(sliderX + 2, tyMax, sliderX + 8, tyMin, 0x664FC3F7);
            boolean hovMin = isOver(mx, my, sliderX - 2, tyMin - 6, 18, 12) || (draggingLayer && !draggingLayerMax);
            boolean hovMax = isOver(mx, my, sliderX - 2, tyMax - 6, 18, 12) || (draggingLayer && draggingLayerMax);
            g.fill(sliderX - 2, tyMin - 5, sliderX + 16, tyMin + 5, hovMin ? C_ACCENT : C_BTN_ACT);
            g.fill(sliderX - 2, tyMax - 5, sliderX + 16, tyMax + 5, hovMax ? C_ACCENT : C_BTN_ACT);
            g.drawString(font, s.layerMin + "→" + s.layerMax, sliderX + 18, (tyMin + tyMax) / 2 - 4, C_ACCENT, false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step row (two rows of controls — kept verbatim from original)
    // ─────────────────────────────────────────────────────────────────────────

    private void renderStepRow(GuiGraphics g, int mx, int my) {
        int rowY = this.height - BOTTOM_H;
        int row2 = rowY + STEP_ROW_H / 2;

        g.fill(0, rowY, this.width, rowY + STEP_ROW_H, C_BAR);
        g.fill(0, rowY, this.width, rowY + 1, C_ACCENT);

        PhantasiaScriptData.StepData s = step();

        // ── ROW 1: step navigation + caption + tick + working + camera ─────────
        int y1 = rowY + 5;
        int x = 8;

        // ── GROUP: Step nav ──────────────────────────────────────────────────
        g.drawString(font, "STEP", x, y1 - 2, 0xFF334455, false);
        String stepLbl = (selectedStep + 1) + "/" + data.getSteps().size();
        g.drawString(font, stepLbl, x, y1 + 6, C_ACCENT, false);
        x += font.width(stepLbl) + 8;

        btn(g, mx, my, x, y1, 14, 14, "+", C_BTN, this::addStep);
        x += 18;
        btn(g, mx, my, x, y1, 14, 14, "−", C_BTN, this::deleteStep);
        x += 18;
        btn(g, mx, my, x, y1, 24, 14, "Dup", C_BTN, this::duplicateStep);
        x += 28;
        btn(g, mx, my, x, y1, 14, 14, "◄", C_BTN, () -> moveStep(selectedStep, -1));
        x += 18;
        btn(g, mx, my, x, y1, 14, 14, "►", C_BTN, () -> moveStep(selectedStep, +1));
        x += 18;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // ── GROUP: Timing ────────────────────────────────────────────────────
        g.drawString(font, "Tick:", x, y1 + 3, C_DIM, false);
        x += font.width("Tick:") + 3;
        placeBox(tickBox, x, y1, 38, 13);
        if (isOver(mx, my, x - font.width("Tick:") - 3, y1, font.width("Tick:") + 3 + 38, 13))
            safeTooltip(g, "Tick: when this step activates. 20 ticks ≈ 1 sec.", mx, rowY);
        x += 44;

        // Caption — button opens subscreen for easier editing
        String capVal = s.caption != null ? s.caption : "";
        int capW = Math.min(220, this.width / 2 - x - 10);
        boolean capHov = isOver(mx, my, x, y1, capW, 13);
        g.fill(x, y1, x + capW, y1 + 13, capHov ? C_BTN_HOV : C_BTN);
        g.fill(x, y1, x + capW, y1 + 1, 0x33FFFFFF);
        String capDisplay = capVal.isEmpty() ? "✎  Caption..." : trunc(capVal, capW - 16);
        g.drawString(font, capDisplay, x + 4, y1 + 3,
                capVal.isEmpty() ? C_DIM : C_TEXT, false);
        if (!capVal.isEmpty() && font.width(capVal) > capW - 16)
            g.drawString(font, "…", x + capW - 8, y1 + 3, C_DIM, false);
        btns.add(new Btn(x, y1, capW, 13, () -> Minecraft.getInstance().setScreen(
                new PhantasiaTextInputScreen(this, "Step Caption", "What the viewer sees for this step...",
                        s.caption != null ? s.caption : "", 256, v -> {
                            checkpoint();
                            s.caption = v.isBlank() ? null : v;
                            dirty = true;
                        }))));
        x += capW + 8;

        g.fill(x, y1, x + 1, y1 + 14, 0x33FFFFFF);
        x += 8;

        // ── GROUP: State ─────────────────────────────────────────────────────
        boolean wh = isOver(mx, my, x, y1, 82, 14);
        g.fill(x, y1, x + 82, y1 + 14, s.working ? C_BTN_ACT : (wh ? C_BTN_HOV : C_BTN));
        if (s.working) g.fill(x, y1, x + 82, y1 + 1, C_GREEN);
        g.drawString(font, (s.working ? "✓" : "○") + " Running", x + 5, y1 + 3, s.working ? C_GREEN : C_DIM, false);
        if (wh) safeTooltip(g, "Toggle: show machine as running in this step", mx, rowY);
        btns.add(new Btn(x, y1, 82, 14, () -> {
            checkpoint();
            s.working = !s.working;
            dirty = true;
        }));
        x += 88;

        if (s.working) {
            g.drawString(font, "Recipe:", x, y1 + 3, C_DIM, false);
            x += font.width("Recipe:") + 4;
            placeBox(fakeRecipeBox, x, y1, 180, 13);
            if (isOver(mx, my, x - font.width("Recipe:") - 4, y1, font.width("Recipe:") + 4 + 180, 13))
                safeTooltip(g, "Optional GT recipe ID for recipe-dependent renders", mx, rowY);
            x += 186;
        }

        // ── GROUP: Camera ────────────────────────────────────────────────────
        boolean hasCam = s.camera != null;
        boolean cch = isOver(mx, my, x, y1, 110, 14);
        g.fill(x, y1, x + 110, y1 + 14, hasCam ? C_BTN_ACT : (cch ? C_BTN_HOV : C_BTN));
        if (hasCam) g.fill(x, y1, x + 110, y1 + 1, C_ACCENT);
        g.drawString(font, hasCam ? "📷 Update Cam" : "📷 Capture Cam", x + 5, y1 + 3, hasCam ? C_ACCENT : C_DIM,
                false);
        if (cch) safeTooltip(g, "Save current viewport as this step's camera", mx, rowY);
        btns.add(new Btn(x, y1, 110, 14, this::captureCamera));
        x += 116;

        if (hasCam) {
            // Zoom override
            g.drawString(font, "Zoom:", x, y1 + 3, C_DIM, false);
            x += font.width("Zoom:") + 3;
            placeBox(camZoomBox, x, y1, 40, 13);
            if (isOver(mx, my, x - font.width("Zoom:") - 3, y1, font.width("Zoom:") + 3 + 40, 13))
                safeTooltip(g, "Zoom distance (world units). Blank = auto-fit.", mx, rowY);
            x += 46;
            // ✕ clear camera
            boolean rch = isOver(mx, my, x, y1, 48, 14);
            g.fill(x, y1, x + 48, y1 + 14, rch ? C_BTN_HOV : C_BTN);
            g.drawString(font, "✕ Cam", x + 5, y1 + 3, rch ? C_RED : C_DIM, false);
            btns.add(new Btn(x, y1, 48, 14, () -> {
                checkpoint();
                s.camera = null;
                dirty = true;
            }));
            x += 54;

            // ── Lerp type cycle button ────────────────────────────────────────
            LerpType lt = LerpType.fromString(s.camera.lerpType);
            String ltLabel = lt.name().replace("_", " ");
            int ltW = font.width(ltLabel) + 16;
            boolean lth = isOver(mx, my, x, y1, ltW, 14);
            g.fill(x, y1, x + ltW, y1 + 14, lth ? C_BTN_HOV : C_BTN);
            g.fill(x, y1, x + ltW, y1 + 1, C_ACCENT);
            g.drawString(font, ltLabel, x + 8, y1 + 3, C_ACCENT, false);
            if (lth) safeTooltip(g, "Easing for this camera move. Click to cycle. Snap = instant.", mx, rowY);
            btns.add(new Btn(x, y1, ltW, 14, () -> {
                checkpoint();
                LerpType[] vals = LerpType.values();
                s.camera.lerpType = vals[(lt.ordinal() + 1) % vals.length].name();
                dirty = true;
            }));
            x += ltW + 4;

            // ── Lerp ticks field (hidden when SNAP) ───────────────────────────
            if (lt != LerpType.SNAP) {
                g.drawString(font, "in", x, y1 + 3, C_DIM, false);
                x += font.width("in") + 3;
                placeBox(lerpTicksBox, x, y1, 34, 13);
                if (isOver(mx, my, x, y1, 34, 13))
                    safeTooltip(g, "Transition duration in ticks (20 = 1 second).", mx, rowY);
                x += 38;
                g.drawString(font, "t", x, y1 + 3, C_DIM, false);
                x += font.width("t") + 4;
            }
        }

        // ── ROW 2: show mode + hide controls ──────────────────────────────────
        int y2 = rowY + STEP_ROW_H / 2 + 3;
        x = 8;
        g.drawString(font, "Show:", x, y2 + 2, C_DIM, false);
        x += font.width("Show:") + 4;

        for (int i = 0; i < SHOW_MODES.length; i++) {
            String sm = SHOW_MODES[i];
            String sml = SHOW_LABELS[i];
            int mw = font.width(sml) + 10;
            boolean act = sm.equals(s.show);
            boolean hov = isOver(mx, my, x, y2, mw, 14);
            g.fill(x, y2, x + mw, y2 + 14, act ? C_BTN_ACT : (hov ? C_BTN_HOV : C_BTN));
            if (act) g.fill(x, y2, x + mw, y2 + 1, C_ACCENT);
            g.drawString(font, sml, x + 5, y2 + 3, act ? C_ACCENT : C_TEXT, false);
            final String fsm = sm;
            btns.add(new Btn(x, y2, mw, 14, () -> {
                checkpoint();
                s.show = fsm;
                if ("pos".equals(fsm)) {
                    syncSelectedFromStep();
                    setMode(Mode.SELECT);
                } else if (mode == Mode.SELECT && !"pos".equals(fsm)) setMode(null);
                dirty = true;
                rebuildVisibility();
            }));
            x += mw + 3;

            if (act) {
                if ("layer".equals(sm))
                    g.drawString(font, " Y=" + s.layer, x, y2 + 2, C_ACCENT, false);
                else if ("layers".equals(sm))
                    g.drawString(font, " " + s.layerMin + "→" + s.layerMax, x, y2 + 2, C_ACCENT, false);
            }
        }

        // ── GROUP: Hide controls (right side of row 2) ───────────────────────
        int rx2 = this.width - 8;

        int hpW = 130;
        rx2 -= hpW;
        g.drawString(font, "HidePos:", rx2 - 54, y2 + 2, C_DIM, false);
        if (isOver(mx, my, rx2 - 54, y2, 54 + hpW, 13))
            safeTooltip(g, "Local positions to hide: x,y,z; x,y,z", mx, rowY);
        placeBox(hidePosBox, rx2, y2, hpW, 13);
        rx2 -= 58;

        g.drawString(font, "HideY:", rx2 - 40, y2 + 2, C_DIM, false);
        if (isOver(mx, my, rx2 - 40, y2, 40 + 30, 13))
            safeTooltip(g, "Hide all blocks at this Y layer. Blank = none.", mx, rowY);
        placeBox(hideLayerBox, rx2, y2, 30, 13);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timeline
    // ─────────────────────────────────────────────────────────────────────────

    private void renderTimeline(GuiGraphics g, int mx, int my) {
        int tlY = this.height - TIMELINE_H;
        int margin = 30;
        int trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        int midY = tlY + TIMELINE_H / 2;

        g.fill(0, tlY, this.width, this.height, C_PANEL);
        g.fill(0, tlY, this.width, tlY + 1, 0x33FFFFFF);

        g.fill(margin, midY - 1, margin + trackW, midY + 1, 0xFF1A2C3C);
        g.fill(margin - 1, midY - 3, margin, midY + 3, 0xFF3A506A);
        g.fill(margin + trackW, midY - 3, margin + trackW + 1, midY + 3, 0xFF3A506A);

        if (data.getSteps().isEmpty()) return;

        // ── Ghost + click-to-add indicator ────────────────────────────────────
        timelineGhostX = -1;
        timelineGhostTick = -1;
        boolean mouseOnTrack = isOver(mx, my, margin, tlY, trackW, TIMELINE_H);
        if (mouseOnTrack && draggingTimelineDot < 0) {
            boolean nearDot = false;
            for (PhantasiaScriptData.StepData s : data.getSteps()) {
                float t = total > 0 ? (float) s.tick / total : 0f;
                int dotX = margin + (int) (t * trackW);
                if (Math.abs(mx - dotX) < 14) {
                    nearDot = true;
                    break;
                }
            }
            if (!nearDot) {
                timelineGhostX = mx;
                timelineGhostTick = total > 0 ? Math.round((float) (mx - margin) / trackW * total) : 0;
                g.fill(mx, tlY + 2, mx + 1, tlY + TIMELINE_H - 2, 0x554FC3F7);
                int ghW = font.width("+") + 6;
                g.fill(mx - ghW / 2, midY - 7, mx + ghW / 2, midY + 7, 0x884FC3F7);
                g.drawCenteredString(font, "+", mx, midY - 3, 0xFFFFFFFF);
                g.drawCenteredString(font, "t=" + timelineGhostTick, mx, midY + 8, 0x664FC3F7);
            }
        }

        // ── Preview playhead ─────────────────────────────────────────────────
        if (previewing && total > 0) {
            int px = margin + (int) ((float) previewTick / total * trackW);
            g.fill(px - 1, tlY + 2, px + 1, tlY + TIMELINE_H - 2, 0xAAFFFFFF);
        }

        // ── Step dots ────────────────────────────────────────────────────────
        for (int i = 0; i < data.getSteps().size(); i++) {
            PhantasiaScriptData.StepData s = data.getSteps().get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            boolean sel = (i == selectedStep);
            boolean hov = isOver(mx, my, dotX - 9, midY - 9, 18, 18);
            boolean dragging = (draggingTimelineDot == i);

            int ringCol = sel || dragging ? C_ACCENT : (hov ? 0xFFAADDFF : 0xFF3A506A);
            g.fill(dotX - 7, midY - 7, dotX + 7, midY + 7, ringCol);

            int fillCol = dragging ? 0xFFFFFFFF : (sel ? 0xFF1A3C5C : 0xFF0A1520);
            g.fill(dotX - 5, midY - 5, dotX + 5, midY + 5, fillCol);

            // Camera-step indicator: small accent pip on top of dot
            if (s.camera != null) {
                g.fill(dotX - 3, midY - 7, dotX + 3, midY - 5, C_ACCENT);
            }

            String numLbl = String.valueOf(i + 1);
            int numCol = sel || dragging ? C_ACCENT : (hov ? 0xFFCCEEFF : C_DIM);
            g.drawCenteredString(font, numLbl, dotX, midY - 3, numCol);

            // Connecting line
            if (i + 1 < data.getSteps().size()) {
                PhantasiaScriptData.StepData next = data.getSteps().get(i + 1);
                float nt = total > 0 ? (float) next.tick / total : 0f;
                int nextX = margin + (int) (nt * trackW);
                // Tint the connector cyan if the *next* step has a camera lerp
                int lineCol = (next.camera != null && LerpType.fromString(next.camera.lerpType) != LerpType.SNAP) ?
                        0xFF1A4060 : 0xFF1E3A52;
                g.fill(dotX + 7, midY, nextX - 7, midY + 1, lineCol);
            }

            // Label below dot
            String lbl = (sel || dragging) ? "#" + (i + 1) + "  t=" + s.tick + "  [" + s.show + "]" : "#" + (i + 1);
            int lx = Mth.clamp(dotX - font.width(lbl) / 2, margin, margin + trackW - font.width(lbl));
            g.drawString(font, lbl, lx, midY + 9, sel || dragging ? C_ACCENT : C_DIM, false);

            // Caption snippet above selected dot
            if (sel && s.caption != null && !s.caption.isBlank()) {
                String cap = trunc(s.caption, 180);
                int cx = Mth.clamp(dotX - font.width(cap) / 2, 4, this.width - font.width(cap) - 4);
                g.drawString(font, cap, cx, midY - 19, C_TEXT, false);
            }

            if (hov && !sel && !dragging)
                g.drawCenteredString(font, "✕", dotX, midY - 16, 0x88FF5252);
            // Note: dot clicks handled directly in mouseClicked to allow drag detection
        }

        // ── Tick ruler ───────────────────────────────────────────────────────
        if (total > 0) {
            for (int tick = 0; tick <= total; tick += 60) {
                int rx = margin + (int) ((float) tick / total * trackW);
                g.fill(rx, midY + 1, rx + 1, midY + 4, 0x33FFFFFF);
            }
        }

        g.drawString(font, "◄ ►", margin - 24, midY - 4, C_DIM, false);

        // ── Script duration override (far right of timeline bar) ──────────────
        int durLabelW = font.width("Total:") + 4;
        int durX = this.width - 4 - 46 - durLabelW;
        g.drawString(font, "Total:", durX, midY - 4, C_DIM, false);
        placeBox(scriptDurationBox, durX + durLabelW, tlY + 5, 46, 12);
        scriptDurationBox.visible = true;
        scriptDurationBox.active = true;
        if (isOver(mx, my, durX, tlY, durLabelW + 50, TIMELINE_H))
            g.renderTooltip(font,
                    Component.literal(
                            "Total script length in ticks. Leave blank to auto-compute (last step tick + 60)."),
                    mx, my);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Start-camera panel
    // ─────────────────────────────────────────────────────────────────────────

    private void renderStartCamPanel(GuiGraphics g, int mx, int my) {
        int pw = 370, ph = 60;
        int px = 6, py = TOP_BAR_H + 2;

        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + pw, py + 1, C_ACCENT);
        g.fill(px, py, px + 1, py + ph, 0x33FFFFFF);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x33FFFFFF);

        PhantasiaScriptData.StartCameraData sc = data.getStartCamera();
        boolean hasSC = sc != null;

        // Row 1: Yaw, Pitch, Zoom
        int row1 = py + 6;
        int x = px + 6;
        g.drawString(font, "Start Cam", x, row1 - 1, C_ACCENT, false);
        x += font.width("Start Cam") + 10;

        x = scField(g, mx, my, x, row1, "Yaw", scYawBox,
                "Yaw of initial camera (degrees). Blank = face machine direction.", hasSC);
        x = scField(g, mx, my, x, row1, "Pitch", scPitchBox,
                "Pitch of initial camera (degrees). Blank = default (−35°).", hasSC);
        x = scField(g, mx, my, x, row1, "Zoom", scZoomBox,
                "Initial zoom distance in world units. Blank = auto-fit from bounding box.", hasSC);

        // Row 2: Target offsets + capture + clear
        int row2 = py + 26;
        x = px + 6;
        g.drawString(font, "Target offset", x, row2 + 2, C_DIM, false);
        x += font.width("Target offset") + 6;

        x = scField(g, mx, my, x, row2, "X", scOffsetXBox, "World-space offset added to the look-at centre (X axis).",
                hasSC);
        x = scField(g, mx, my, x, row2, "Y", scOffsetYBox,
                "World-space offset added to the look-at centre (Y axis, positive = up).", hasSC);
        x = scField(g, mx, my, x, row2, "Z", scOffsetZBox, "World-space offset added to the look-at centre (Z axis).",
                hasSC);

        x += 8;
        btn(g, mx, my, x, row2, 90, 13, "⊙ Capture View", C_BTN, this::captureStartCam);
        x += 96;
        if (hasSC) btn(g, mx, my, x, row2, 60, 13, "✕ Clear", C_BTN, this::clearStartCam);

        // Show all sc fields when panel is open regardless of hasSC
        // (boxes are already placed by scField; just ensure enabled)
        for (var box : List.of(scYawBox, scPitchBox, scZoomBox, scOffsetXBox, scOffsetYBox, scOffsetZBox)) {
            box.visible = true;
            box.active = true;
        }
    }

    /** Renders a label + EditBox pair for the start-cam panel. Returns updated x. */
    private int scField(GuiGraphics g, int mx, int my, int x, int y,
                        String label, EditBox box, String tooltip, boolean hasSC) {
        int labelW = font.width(label) + 3;
        g.drawString(font, label, x, y + 2, C_DIM, false);
        placeBox(box, x + labelW, y, 44, 13);
        if (!hasSC) {
            // Dim the box to signal it doesn't exist yet — first edit creates it
            g.fill(x + labelW, y, x + labelW + 44, y + 13, 0x33000000);
        }
        if (isOver(mx, my, x, y, labelW + 44, 13))
            g.renderTooltip(font, Component.literal(tooltip), mx, my);
        return x + labelW + 48;
    }

    private void captureStartCam() {
        checkpoint();
        PhantasiaScriptData.StartCameraData sc = ensureStartCam();
        if (camera != null) {
            sc.yaw = camera.getYaw();
            sc.pitch = camera.getPitch();
            sc.zoom = camera.getZoom();
        }
        populateStartCamBoxes();
        dirty = true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mouse / keyboard input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (showingCloseConfirm) {
            for (Btn b : btns) if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
            return true;
        }

        // Button registry — checked before super so our custom buttons always fire
        for (Btn b : btns) if (b.hit(mx, my)) {
            b.action().run();
            return true;
        }

        // Manually route clicks to visible EditBoxes for focus (not via super which can eat clicks)
        for (var child : children()) {
            if (child instanceof EditBox eb && eb.visible && eb.active && mx >= eb.getX() &&
                    mx < eb.getX() + eb.getWidth() && my >= eb.getY() && my < eb.getY() + eb.getHeight()) {
                setFocused(eb);
                eb.mouseClicked(mx, my, btn);
                return true;
            }
        }

        // Layer slider
        if (startLayerSliderDrag(mx, my)) return true;

        // Timeline
        int tlY = this.height - TIMELINE_H;
        int midY = tlY + TIMELINE_H / 2;
        int margin = 30, trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        boolean onTimeline = isOver(mx, my, 0, tlY, this.width, TIMELINE_H);

        if (onTimeline) {
            if (btn == 1) {
                for (int i = 0; i < data.getSteps().size(); i++) {
                    PhantasiaScriptData.StepData s = data.getSteps().get(i);
                    float t = total > 0 ? (float) s.tick / total : 0f;
                    int dotX = margin + (int) (t * trackW);
                    if (isOver(mx, my, dotX - 9, midY - 9, 18, 18)) {
                        if (data.getSteps().size() > 1) {
                            checkpoint();
                            data.getSteps().remove(i);
                            selectStep(Math.min(selectedStep, data.getSteps().size() - 1));
                            dirty = true;
                        }
                        return true;
                    }
                }
            }
            if (btn == 0) {
                // Try to start a dot drag — this also selects the step immediately.
                // The Btn registry is intentionally NOT used for dots so drag takes priority.
                if (startTimelineDotDrag(mx, my)) return true;
                // Click on empty track area — add a new step
                if (isOver(mx, my, margin, tlY, trackW, TIMELINE_H)) {
                    boolean nearDot = false;
                    for (PhantasiaScriptData.StepData s : data.getSteps()) {
                        float t = total > 0 ? (float) s.tick / total : 0f;
                        int dotX = margin + (int) (t * trackW);
                        if (Math.abs(mx - dotX) < 14) {
                            nearDot = true;
                            break;
                        }
                    }
                    if (!nearDot) {
                        int newTick = total > 0 ? Math.round((float) (mx - margin) / trackW * total) : 0;
                        addStepAtTick(newTick);
                        return true;
                    }
                }
            }
            return true; // consume all timeline clicks
        }

        int sceneBottom = this.height - BOTTOM_H;
        if (my < TOP_BAR_H || my >= sceneBottom) return false;

        if (mode == Mode.SELECT) {
            selectClickPending = true;
            selectClickBtn = btn;
            selectClickMX = mx;
            selectClickMY = my;
            return true;
        }
        if (mode == Mode.ANNOTATE) return handleAnnotateClick(mx, my, btn);
        return false;
    }

    private boolean startLayerSliderDrag(double mx, double my) {
        PhantasiaScriptData.StepData s = step();
        if (!"layer".equals(s.show) && !"layers".equals(s.show)) return false;
        if (pattern == null) return false;

        int sliderX = 10;
        int sceneTop = TOP_BAR_H, sceneBottom = this.height - BOTTOM_H;
        int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
        int range = Math.max(1, pattern.maxY - pattern.minY);

        if ("layer".equals(s.show)) {
            float t = 1f - (float) (s.layer - pattern.minY) / range;
            int thumbY = sliderY + (int) (t * sliderH);
            if (isOver(mx, my, sliderX - 2, thumbY - 7, 18, 14)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
        } else {
            float tMin = 1f - (float) (s.layerMin - pattern.minY) / range;
            float tMax = 1f - (float) (s.layerMax - pattern.minY) / range;
            int tyMin = sliderY + (int) (tMin * sliderH);
            int tyMax = sliderY + (int) (tMax * sliderH);
            if (isOver(mx, my, sliderX - 2, tyMin - 6, 18, 12)) {
                draggingLayer = true;
                draggingLayerMax = false;
                return true;
            }
            if (isOver(mx, my, sliderX - 2, tyMax - 6, 18, 12)) {
                draggingLayer = true;
                draggingLayerMax = true;
                return true;
            }
        }
        return false;
    }

    private boolean startTimelineDotDrag(double mx, double my) {
        int tlY = this.height - TIMELINE_H;
        int midY = tlY + TIMELINE_H / 2;
        int margin = 30, trackW = this.width - margin * 2;
        int total = computeTotalTicks();
        for (int i = 0; i < data.getSteps().size(); i++) {
            PhantasiaScriptData.StepData s = data.getSteps().get(i);
            float t = total > 0 ? (float) s.tick / total : 0f;
            int dotX = margin + (int) (t * trackW);
            if (isOver(mx, my, dotX - 9, midY - 9, 18, 18)) {
                checkpoint();
                draggingTimelineDot = i;
                dotDragMoved = false;
                dotDragStartMX = mx;
                selectStep(i);
                return true;
            }
        }
        return false;
    }

    private boolean handleSelectClick(double mx, double my, int btn) {
        if (hoveredWorldPos == null || pattern == null) return false;
        if (pattern.baseplatePositions.contains(hoveredWorldPos)) return false;
        BlockPos local = pattern.toLocal(hoveredWorldPos);
        if (local == null) return false;

        PhantasiaScriptData.StepData s = step();
        s.show = "pos";

        checkpoint();
        if (btn == 0) {
            if (isInPositionList(local)) {
                s.positions.removeIf(
                        p -> p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ());
                selectedWorldPos.remove(hoveredWorldPos);
            } else {
                s.positions.add(new int[] { local.getX(), local.getY(), local.getZ() });
                selectedWorldPos.add(hoveredWorldPos);
            }
        } else if (btn == 1) {
            s.positions.removeIf(
                    p -> p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ());
            selectedWorldPos.remove(hoveredWorldPos);
        }

        dirty = true;
        rebuildVisibility();
        return true;
    }

    private boolean handleAnnotateClick(double mx, double my, int btn) {
        if (btn == 1 && hoveredMistakeIndex >= 0) {
            checkpoint();
            data.getMistakes().remove(hoveredMistakeIndex);
            hoveredMistakeIndex = -1;
            dirty = true;
            return true;
        }
        if (btn != 0) return false;
        if (pendingAnnotationLocalPos != null) {
            confirmAnnotation();
            return true;
        }
        if (hoveredWorldPos == null || pattern == null) return false;
        BlockPos local = pattern.toLocal(hoveredWorldPos);
        if (local == null) return false;
        pendingAnnotationLocalPos = local;
        pendingAnnotationLabel = "";
        // Open the label input immediately so the user can type right away
        openAnnotationLabelInput();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        // Layer slider drag
        if (draggingLayer && pattern != null) {
            int sceneTop = TOP_BAR_H, sceneBottom = this.height - BOTTOM_H;
            int sliderH = sceneBottom - sceneTop - 24, sliderY = sceneTop + 12;
            float t = 1f - Mth.clamp((float) (my - sliderY) / sliderH, 0f, 1f);
            int layer = pattern.minY + Math.round(t * (pattern.maxY - pattern.minY));
            PhantasiaScriptData.StepData s = step();
            if (draggingLayerMax) s.layerMax = Math.max(s.layerMin, layer);
            else if ("layers".equals(s.show)) s.layerMin = Math.min(layer, s.layerMax);
            else s.layer = layer;
            dirty = true;
            rebuildVisibility();
            return true;
        }

        // Timeline dot drag
        if (draggingTimelineDot >= 0 && draggingTimelineDot < data.getSteps().size()) {
            int margin = 30, trackW = this.width - margin * 2;
            int total = computeTotalTicks();
            float t = Mth.clamp((float) (mx - margin) / trackW, 0f, 1f);
            int newTick = Math.round(t * total);
            data.getSteps().get(draggingTimelineDot).tick = Math.max(0, newTick);
            if (Math.abs(mx - dotDragStartMX) > 3) dotDragMoved = true;
            populateInputsFromStep();
            dirty = true;
            return true;
        }

        // Camera drag — in scene area only
        int sceneBottom = this.height - BOTTOM_H;
        if (my < TOP_BAR_H || my >= sceneBottom) return super.mouseDragged(mx, my, btn, dx, dy);

        if (camera != null && (btn == 2 || btn == 0)) {
            selectClickPending = false; // dragging — don't commit a block click on release
            if (btn == 2) {
                // Middle-drag: pan
                Vector3f rgt = new Vector3f(), up = new Vector3f();
                camera.getRightAndUp(rgt, up);
                float pd = CAM_PAN_SENSITIVITY;
                camera.pan(rgt.x * -(float) dx * pd + up.x * (float) dy * pd,
                        rgt.y * -(float) dx * pd + up.y * (float) dy * pd,
                        rgt.z * -(float) dx * pd + up.z * (float) dy * pd);
            } else {
                // Left-drag: orbit
                camera.orbit((float) dx * CAM_ORBIT_SENSITIVITY,
                        (float) dy * CAM_ORBIT_SENSITIVITY);
            }
            return true;
        }

        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int sceneBottom = this.height - BOTTOM_H;
        if (my >= TOP_BAR_H && my < sceneBottom && camera != null) {
            camera.zoom(delta > 0 ? 0.9f : 1.1f, 2f, 150f);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        // Commit a SELECT-mode click only if the mouse didn't move (wasn't a camera drag)
        if (selectClickPending && btn == selectClickBtn) {
            selectClickPending = false;
            double moved = Math.abs(mx - selectClickMX) + Math.abs(my - selectClickMY);
            if (moved < 4) handleSelectClick(selectClickMX, selectClickMY, selectClickBtn);
        }

        if (draggingTimelineDot >= 0 && dotDragMoved) {
            PhantasiaScriptData.StepData dragged = data.getSteps().get(draggingTimelineDot);
            data.getSteps().sort(Comparator.comparingInt(s -> s.tick));
            selectedStep = data.getSteps().indexOf(dragged);
            populateInputsFromStep();
            rebuildVisibility();
        }
        draggingTimelineDot = -1;
        dotDragMoved = false;
        draggingLayer = false;
        draggingLayerMax = false;
        reorderingStep = -1;
        reorderInsertAt = -1;
        selectClickPending = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean charTyped(char c, int mod) {
        // Forward to focused widget first — without this EditBoxes never receive typed characters
        if (super.charTyped(c, mod)) return true;
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        // Always let a focused EditBox handle the key first
        if (getFocused() != null && getFocused().keyPressed(kc, sc, mod)) return true;

        if (pendingAnnotationLocalPos != null) {
            if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
                confirmAnnotation();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_ESCAPE) {
                cancelAnnotation();
                return true;
            }
        }
        // Escape exits the current mode before closing the screen
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            if (showingCloseConfirm) {
                showingCloseConfirm = false;
                return true;
            }
            if (mode != null) {
                setMode(null);
                return true;
            }
            onClose();
            return true;
        }
        // Delete key removes the selected step
        if (kc == GLFW.GLFW_KEY_DELETE) {
            deleteStep();
            return true;
        }

        boolean ctrl = (mod & 2) != 0;
        if (ctrl && kc == 90) {
            undo();
            return true;
        }                   // Ctrl+Z
        if (ctrl && kc == 67) {
            copyStep();
            return true;
        }               // Ctrl+C — copy step
        if (ctrl && kc == 86) {
            pasteStep();
            return true;
        }              // Ctrl+V — paste step

        // Ctrl+Left/Right reorders the step; plain Left/Right navigates
        if (kc == GLFW.GLFW_KEY_RIGHT) {
            if (ctrl) moveStep(selectedStep, +1);
            else selectStep(Math.min(selectedStep + 1, data.getSteps().size() - 1));
            return true;
        }
        if (kc == GLFW.GLFW_KEY_LEFT) {
            if (ctrl) moveStep(selectedStep, -1);
            else selectStep(Math.max(selectedStep - 1, 0));
            return true;
        }

        if (mode == Mode.SELECT && ctrl) {
            if (kc == 65) {
                selectAllBlocks();
                return true;
            }            // Ctrl+A
            if (kc == 68) {
                deselectAll();
                return true;
            }            // Ctrl+D
        }

        return super.keyPressed(kc, sc, mod);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Undo
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call immediately before any mutation to {@link #data}.
     * Pushes a deep copy onto the undo stack, capped at {@link #MAX_UNDO} entries.
     */
    private void checkpoint() {
        if (undoStack.size() >= MAX_UNDO) undoStack.pollFirst();
        undoStack.addLast(data.copy());
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        data = undoStack.pollLast();
        selectedStep = Mth.clamp(selectedStep, 0, data.getSteps().size() - 1);
        dirty = !undoStack.isEmpty(); // dirty stays true unless we've undone all the way back
        populateInputsFromStep();
        syncSelectedFromStep();
        rebuildVisibility();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    private void setMode(Mode m) {
        mode = m;
        if (m == Mode.SELECT) {
            syncSelectedFromStep();
            if (!"pos".equals(step().show)) {
                step().show = "pos";
                dirty = true;
            }
        }
        pendingAnnotationLocalPos = null;
        rebuildVisibility();
    }

    private void togglePreview() {
        previewing = !previewing;
        previewTick = 0;
        previewAccum = 0f;
        if (!previewing) rebuildVisibility();
    }

    private void selectStep(int i) {
        selectedStep = Mth.clamp(i, 0, data.getSteps().size() - 1);
        populateInputsFromStep();
        if (mode == Mode.SELECT) syncSelectedFromStep();
        rebuildVisibility();
    }

    private void addStepAtTick(int tick) {
        String inheritedShow = "all";
        for (PhantasiaScriptData.StepData s : data.getSteps()) {
            if (s.tick <= tick) inheritedShow = s.show;
            else break;
        }
        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(tick, null);
        s.show = inheritedShow;
        int insertAt = data.getSteps().size();
        for (int i = 0; i < data.getSteps().size(); i++) {
            if (data.getSteps().get(i).tick > tick) {
                insertAt = i;
                break;
            }
        }
        checkpoint();
        data.getSteps().add(insertAt, s);
        selectStep(insertAt);
        dirty = true;
    }

    private void addStep() {
        int lastTick = data.getSteps().isEmpty() ? 0 : data.getSteps().get(data.getSteps().size() - 1).tick + 60;
        PhantasiaScriptData.StepData s = new PhantasiaScriptData.StepData(lastTick, null);
        s.show = "all";
        checkpoint();
        data.getSteps().add(s);
        selectStep(data.getSteps().size() - 1);
        dirty = true;
    }

    private void deleteStep() {
        if (data.getSteps().size() <= 1) return;
        checkpoint();
        data.getSteps().remove(selectedStep);
        selectStep(Math.min(selectedStep, data.getSteps().size() - 1));
        dirty = true;
    }

    private void duplicateStep() {
        if (selectedStep < 0 || selectedStep >= data.getSteps().size()) return;
        PhantasiaScriptData.StepData copy = data.getSteps().get(selectedStep).copy();
        copy.tick += 60;
        checkpoint();
        data.getSteps().add(selectedStep + 1, copy);
        selectStep(selectedStep + 1);
        dirty = true;
    }

    /** Ctrl+C: copies the selected step into an internal clipboard without moving it. */
    private PhantasiaScriptData.StepData stepClipboard = null;

    private void copyStep() {
        if (selectedStep < 0 || selectedStep >= data.getSteps().size()) return;
        stepClipboard = data.getSteps().get(selectedStep).copy();
    }

    /** Ctrl+V: pastes the clipboard step after the current selection. */
    private void pasteStep() {
        if (stepClipboard == null) return;
        PhantasiaScriptData.StepData copy = stepClipboard.copy();
        copy.tick = step().tick + 60;
        checkpoint();
        data.getSteps().add(selectedStep + 1, copy);
        selectStep(selectedStep + 1);
        dirty = true;
    }

    private void moveStep(int from, int delta) {
        int to = from + delta;
        if (to < 0 || to >= data.getSteps().size()) return;
        checkpoint();
        Collections.swap(data.getSteps(), from, to);
        selectedStep = to;
        rebuildVisibility();
        dirty = true;
    }

    private void captureCamera() {
        PhantasiaScriptData.StepData s = step();
        if (s.camera == null) s.camera = new PhantasiaScriptData.CameraData();
        checkpoint();
        if (camera != null) {
            s.camera.yaw = camera.getYaw();
            s.camera.pitch = camera.getPitch();
            s.camera.zoom = camera.getZoom();
        }
        if (camZoomBox != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
        dirty = true;
    }

    private void confirmAnnotation() {
        if (pendingAnnotationLocalPos == null) return;
        String label = pendingAnnotationLabel.trim();
        if (!label.isEmpty()) {
            checkpoint();
            BlockPos lp = pendingAnnotationLocalPos;
            String colorHex = String.format("%06X", MISTAKE_COLORS[selectedMistakeColor] & 0xFFFFFF);
            data.getMistakes().removeIf(m -> m.x == lp.getX() && m.y == lp.getY() && m.z == lp.getZ());
            data.getMistakes()
                    .add(new PhantasiaScriptData.MistakeData(lp.getX(), lp.getY(), lp.getZ(), label, colorHex));
            dirty = true;
        }
        pendingAnnotationLocalPos = null;
        pendingAnnotationLabel = "";
    }

    private void cancelAnnotation() {
        pendingAnnotationLocalPos = null;
        pendingAnnotationLabel = "";
    }

    private void selectAllBlocks() {
        if (pattern == null) return;
        PhantasiaScriptData.StepData s = step();
        checkpoint();
        s.positions.clear();
        selectedWorldPos.clear();
        for (Map.Entry<BlockPos, BlockPos> e : pattern.localToWorld.entrySet()) {
            if (pattern.baseplatePositions.contains(e.getValue())) continue;
            s.positions.add(new int[] { e.getKey().getX(), e.getKey().getY(), e.getKey().getZ() });
            selectedWorldPos.add(e.getValue());
        }
        dirty = true;
        rebuildVisibility();
    }

    private void deselectAll() {
        checkpoint();
        step().positions.clear();
        selectedWorldPos.clear();
        dirty = true;
        rebuildVisibility();
    }

    private void save() {
        PhantasiaScriptLoader.save(machineId, data);
        dirty = false;
        if (parentScene != null) parentScene.reloadScript();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int computeTotalTicks() {
        int dur = data.getScriptDuration();
        if (dur > 0) return dur;
        return data.getSteps().isEmpty() ? 60 : data.getSteps().get(data.getSteps().size() - 1).tick + 60;
    }

    private void syncSelectedFromStep() {
        selectedWorldPos.clear();
        if (pattern == null) return;
        for (int[] xyz : step().positions) {
            if (xyz.length < 3) continue;
            BlockPos world = pattern.toWorld(new BlockPos(xyz[0], xyz[1], xyz[2]));
            if (world != null) selectedWorldPos.add(world);
        }
    }

    private boolean isInPositionList(BlockPos local) {
        for (int[] p : step().positions)
            if (p.length >= 3 && p[0] == local.getX() && p[1] == local.getY() && p[2] == local.getZ()) return true;
        return false;
    }

    private void populateInputsFromStep() {
        if (tickBox == null) return;
        PhantasiaScriptData.StepData s = step();
        tickBox.setValue(String.valueOf(s.tick));
        hideLayerBox.setValue(s.hideLayer >= 0 ? String.valueOf(s.hideLayer) : "");
        hidePosBox.setValue(serializePosList(s.hidePositions));
        if (fakeRecipeBox != null)
            fakeRecipeBox.setValue(s.fakeRecipeId != null ? s.fakeRecipeId : "");
        if (lerpTicksBox != null && s.camera != null)
            lerpTicksBox.setValue(String.valueOf(s.camera.lerpTicks > 0 ? s.camera.lerpTicks : 20));
        if (camZoomBox != null && s.camera != null)
            camZoomBox.setValue(s.camera.zoom > 0 ? String.valueOf(s.camera.zoom) : "");
        if (scriptDurationBox != null)
            scriptDurationBox.setValue(data.getScriptDuration() > 0 ? String.valueOf(data.getScriptDuration()) : "");
        populateStartCamBoxes();
    }

    private void populateStartCamBoxes() {
        PhantasiaScriptData.StartCameraData sc = data.getStartCamera();
        if (scYawBox == null) return;
        if (sc == null) {
            scYawBox.setValue("");
            scPitchBox.setValue("");
            scZoomBox.setValue("");
            scOffsetXBox.setValue("");
            scOffsetYBox.setValue("");
            scOffsetZBox.setValue("");
        } else {
            scYawBox.setValue(sc.hasYaw() ? String.valueOf(sc.yaw) : "");
            scPitchBox.setValue(sc.hasPitch() ? String.valueOf(sc.pitch) : "");
            scZoomBox.setValue(sc.hasZoom() ? String.valueOf(sc.zoom) : "");
            scOffsetXBox.setValue(sc.targetOffsetX != 0f ? String.valueOf(sc.targetOffsetX) : "");
            scOffsetYBox.setValue(sc.targetOffsetY != 0f ? String.valueOf(sc.targetOffsetY) : "");
            scOffsetZBox.setValue(sc.targetOffsetZ != 0f ? String.valueOf(sc.targetOffsetZ) : "");
        }
    }

    private PhantasiaScriptData.StepData step() {
        if (selectedStep >= 0 && selectedStep < data.getSteps().size())
            return data.getSteps().get(selectedStep);
        return new PhantasiaScriptData.StepData();
    }

    private float[] projectToScreen(float wx, float wy, float wz,
                                    Vector3f eye, Vector3f fwd, Vector3f rgt, Vector3f upv, float fov) {
        Vector3f toP = new Vector3f(wx - eye.x(), wy - eye.y(), wz - eye.z());
        float depth = toP.dot(fwd);
        if (depth < 0.3f) return null;
        float sx = this.width / 2f + (toP.dot(rgt) / depth) * fov;
        float sy = this.height / 2f - (toP.dot(upv) / depth) * fov;
        return new float[] { sx, sy, depth };
    }

    /** Renders a tooltip pinned just above {@code anchorY}, never spilling into the step row or timeline. */
    private void safeTooltip(GuiGraphics g, String text, int mx, int anchorY) {
        int tw = font.width(text) + 8;
        int th = font.lineHeight + 4;
        int tx = Math.max(2, Math.min(mx - tw / 2, this.width - tw - 2));
        int ty = anchorY - th - 2;
        if (ty < TOP_BAR_H + 2) ty = TOP_BAR_H + 2;
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + th + 1, 0xEE0A0A18);
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty, C_ACCENT);
        g.drawString(font, text, tx + 4, ty + 2, C_TEXT, false);
    }

    private void drawBanner(GuiGraphics g, String text, int y, int accentColor) {
        int tw = font.width(text) + 20;
        int tx = (this.width - tw) / 2;
        g.fill(tx, y, tx + tw, y + 16, 0xBB0C0C1A);
        g.fill(tx, y, tx + tw, y + 1, accentColor);
        g.drawString(font, text, tx + 10, y + 4, C_DIM, false);
    }

    private void btn(GuiGraphics g, int mx, int my, int x, int y, int w, int h,
                     String label, int base, Runnable action) {
        boolean hov = isOver(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? C_BTN_HOV : base);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hov ? C_ACCENT : C_TEXT, false);
        btns.add(new Btn(x, y, w, h, action));
    }

    private void placeBox(EditBox box, int x, int y, int w, int h) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = true;
        box.active = true;
    }

    private void hideAllInputs() {
        for (var box : List.of(tickBox, hideLayerBox, hidePosBox,
                fakeRecipeBox,
                lerpTicksBox, camZoomBox, scriptDurationBox,
                scYawBox, scPitchBox, scZoomBox,
                scOffsetXBox, scOffsetYBox, scOffsetZBox)) {
            if (box == null) continue;
            box.visible = false;
            box.active = false;
        }
    }

    private boolean isOver(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        while (font.width(s) > maxPx && s.length() > 2) s = s.substring(0, s.length() - 2) + "\u2026";
        return s;
    }

    private static List<int[]> parsePosList(String raw) {
        List<int[]> r = new ArrayList<>();
        if (raw == null || raw.isBlank()) return r;
        for (String e : raw.split(";")) {
            String[] p = e.trim().split(",");
            if (p.length >= 3) try {
                r.add(new int[] { Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()) });
            } catch (NumberFormatException ignored) {}
        }
        return r;
    }

    private static String serializePosList(List<int[]> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int[] p : list) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        if (dirty && !showingCloseConfirm) {
            showingCloseConfirm = true;
            return;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (parentScene != null) parentScene.reloadScript();
        Minecraft.getInstance().setScreen(parentScene);
    }

    private void forceClose() {
        dirty = false;
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
