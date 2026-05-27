package net.phoenixvine.phantasia.common;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PhantasiaSceneData — serialisable form of a manual multi-machine scene.
 *
 * Stored at: phantasia/scenes/<namespace>/<name>.json
 *
 * Steps reuse {@link PhantasiaScriptData.StepData} for camera, caption, tick,
 * and global visibility. Each step may additionally declare per-placement
 * visibility overrides via {@link StepData#machineOverrides}, keyed by
 * placement index (as a string for Gson compatibility).
 *
 * ── JSON schema ───────────────────────────────────────────────────────────────
 * {
 * "id": "phoenixvine:ore_processing_line",
 * "name": "Ore Processing Line",
 * "placements": [
 * { "machine": "gtceu:electric_blast_furnace", "x": 0, "y": 0, "z": 0 },
 * { "machine": "gtceu:large_chemical_reactor", "x": 0, "y": 0, "z": 20 }
 * ],
 * "steps": [
 * {
 * "tick": 0,
 * "caption": "First, the EBF smelts the ore.",
 * "show": "all",
 * "machineOverrides": {
 * "0": { "show": "layer", "layer": 2 },
 * "1": { "show": "all", "hideLayer": 3 }
 * },
 * "camera": { "yaw": -135, "pitch": -30, "lerpType": "EASE_OUT", "lerpTicks": 20 }
 * }
 * ]
 * }
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PhantasiaSceneData {

    @SerializedName("id")
    public String id = "";

    @SerializedName("name")
    public String name = "Unnamed Scene";

    @SerializedName("placements")
    public List<PlacementData> placements = new ArrayList<>();

    @SerializedName("steps")
    public List<StepData> steps = new ArrayList<>();

    // ── Placement ─────────────────────────────────────────────────────────────

    public static class PlacementData {

        @SerializedName("machine")
        public String machine = "";

        /** World-space offset of this machine's origin from the scene origin. */
        @SerializedName("x")
        public int x = 0;

        @SerializedName("y")
        public int y = 0;

        @SerializedName("z")
        public int z = 0;

        public PlacementData() {}

        public PlacementData(String machine, int x, int y, int z) {
            this.machine = machine;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public PlacementData copy() {
            return new PlacementData(machine, x, y, z);
        }
    }

    // ── Per-placement visibility override ─────────────────────────────────────

    /**
     * Visibility settings scoped to a single placement within a step.
     * Only visibility fields are meaningful here — tick, caption, camera are
     * always step-level. All fields default to "no override" sentinels.
     */
    public static class MachineOverride {

        /**
         * Visibility mode for this placement in this step.
         * Same values as {@link PhantasiaScriptData.StepData#show}.
         * Null = inherit step-level show mode.
         */
        @SerializedName("show")
        public String show = null;

        @SerializedName("layer")
        public int layer = 0;

        @SerializedName("layerMin")
        public int layerMin = 0;

        @SerializedName("layerMax")
        public int layerMax = 0;

        @SerializedName("positions")
        public List<int[]> positions = new ArrayList<>();

        /** ≥ 0 to hide that Y layer within this placement. -1 = none. */
        @SerializedName("hideLayer")
        public int hideLayer = -1;

        @SerializedName("hidePositions")
        public List<int[]> hidePositions = new ArrayList<>();

        public MachineOverride() {}

        public MachineOverride copy() {
            MachineOverride c = new MachineOverride();
            c.show = show;
            c.layer = layer;
            c.layerMin = layerMin;
            c.layerMax = layerMax;
            c.hideLayer = hideLayer;
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            return c;
        }
    }

    // ── Step ─────────────────────────────────────────────────────────────────

    /**
     * Scene step. Extends the machine-script step with a map of per-placement
     * visibility overrides. All other fields are identical to
     * {@link PhantasiaScriptData.StepData}.
     */
    public static class StepData {

        @SerializedName("tick")
        public int tick = 0;

        @SerializedName("caption")
        public String caption = null;

        /**
         * Global visibility mode applied to ALL placements unless overridden.
         * Same values as {@link PhantasiaScriptData.StepData#show}.
         */
        @SerializedName("show")
        public String show = "all";

        @SerializedName("layer")
        public int layer = 0;

        @SerializedName("layerMin")
        public int layerMin = 0;

        @SerializedName("layerMax")
        public int layerMax = 0;

        @SerializedName("positions")
        public List<int[]> positions = new ArrayList<>();

        @SerializedName("hideLayer")
        public int hideLayer = -1;

        @SerializedName("hidePositions")
        public List<int[]> hidePositions = new ArrayList<>();

        @SerializedName("working")
        public boolean working = false;

        @SerializedName("camera")
        public PhantasiaScriptData.CameraData camera = null;

        /**
         * Per-placement visibility overrides.
         * Key = placement index as string (e.g. "0", "1").
         * Absent key = placement uses global step show mode.
         */
        @SerializedName("machineOverrides")
        public Map<String, MachineOverride> machineOverrides = new LinkedHashMap<>();

        public StepData() {}

        public StepData(int tick, String caption) {
            this.tick = tick;
            this.caption = caption;
        }

        public StepData copy() {
            StepData c = new StepData(tick, caption);
            c.show = show;
            c.layer = layer;
            c.layerMin = layerMin;
            c.layerMax = layerMax;
            c.hideLayer = hideLayer;
            c.working = working;
            c.camera = camera == null ? null : new PhantasiaScriptData.CameraData(camera.yaw, camera.pitch, camera.zoom,
                    camera.lerpType, camera.lerpTicks);
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            for (Map.Entry<String, MachineOverride> e : machineOverrides.entrySet())
                c.machineOverrides.put(e.getKey(), e.getValue().copy());
            return c;
        }

        /** Returns the override for the given placement index, or null if none set. */
        public MachineOverride getOverride(int placementIndex) {
            return machineOverrides.get(String.valueOf(placementIndex));
        }

        /** Sets (or replaces) the override for the given placement index. */
        public void setOverride(int placementIndex, MachineOverride override) {
            machineOverrides.put(String.valueOf(placementIndex), override);
        }

        /** Removes the override for the given placement index (reverts to global). */
        public void removeOverride(int placementIndex) {
            machineOverrides.remove(String.valueOf(placementIndex));
        }
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public PhantasiaSceneData() {}

    public PhantasiaSceneData(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a default scene with one empty step. */
    public static PhantasiaSceneData blank(String id, String name) {
        PhantasiaSceneData d = new PhantasiaSceneData(id, name);
        StepData s = new StepData(0, null);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public PhantasiaSceneData copy() {
        PhantasiaSceneData c = new PhantasiaSceneData(id, name);
        for (PlacementData p : placements) c.placements.add(p.copy());
        for (StepData s : steps) c.steps.add(s.copy());
        return c;
    }

    // ── Gson codec ────────────────────────────────────────────────────────────

    public String toJson() {
        return PhantasiaScriptData.GSON.toJson(this);
    }

    public static PhantasiaSceneData fromJson(String json) {
        return PhantasiaScriptData.GSON.fromJson(json, PhantasiaSceneData.class);
    }

    public static PhantasiaSceneData fromJson(java.io.Reader reader) {
        return PhantasiaScriptData.GSON.fromJson(reader, PhantasiaSceneData.class);
    }
}
