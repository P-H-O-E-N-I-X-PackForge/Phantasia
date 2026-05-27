package net.phoenixvine.phantasia.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class PhantasiaScriptData {

    @SerializedName("machine")
    private String machine = "";

    @Setter
    @SerializedName("startCamera")
    private StartCameraData startCamera = null;

    @SerializedName("steps")
    private List<StepData> steps = new ArrayList<>();

    @Setter
    @SerializedName("scriptDuration")
    private int scriptDuration = -1;

    @SerializedName("mistakes")
    private List<MistakeData> mistakes = new ArrayList<>();

    @SerializedName("globalMistakes")
    private List<String> globalMistakes = new ArrayList<>();

    @Getter
    public static class StepData {

        @SerializedName("tick")
        public int tick = 0;

        @SerializedName("caption")
        public String caption = null;

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

        @SerializedName("fakeRecipeId")
        public String fakeRecipeId = null;

        @SerializedName("camera")
        public CameraData camera = null;

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

            c.fakeRecipeId = fakeRecipeId;
            c.camera = camera == null ? null : new CameraData(camera.yaw, camera.pitch, camera.zoom,
                    camera.lerpType, camera.lerpTicks);
            for (int[] p : positions) c.positions.add(new int[] { p[0], p[1], p[2] });
            for (int[] p : hidePositions) c.hidePositions.add(new int[] { p[0], p[1], p[2] });
            return c;
        }
    }

    @Getter
    public static class StartCameraData {

        @SerializedName("yaw")
        public float yaw = Float.NaN;

        @SerializedName("pitch")
        public float pitch = Float.NaN;

        @SerializedName("zoom")
        public float zoom = -1f;

        @SerializedName("targetOffsetX")
        public float targetOffsetX = 0f;

        @SerializedName("targetOffsetY")
        public float targetOffsetY = 0f;

        @SerializedName("targetOffsetZ")
        public float targetOffsetZ = 0f;

        public StartCameraData() {}

        public boolean hasYaw() {
            return !Float.isNaN(yaw);
        }

        public boolean hasPitch() {
            return !Float.isNaN(pitch);
        }

        public boolean hasZoom() {
            return zoom > 0f;
        }

        public boolean hasTargetOffset() {
            return targetOffsetX != 0f || targetOffsetY != 0f || targetOffsetZ != 0f;
        }

        public StartCameraData copy() {
            StartCameraData c = new StartCameraData();
            c.yaw = yaw;
            c.pitch = pitch;
            c.zoom = zoom;
            c.targetOffsetX = targetOffsetX;
            c.targetOffsetY = targetOffsetY;
            c.targetOffsetZ = targetOffsetZ;
            return c;
        }
    }

    @Getter
    public static class CameraData {

        @SerializedName("yaw")
        public float yaw = -135f;

        @SerializedName("pitch")
        public float pitch = -35f;

        @SerializedName("zoom")
        public float zoom = -1f;

        @SerializedName("lerpType")
        public String lerpType = "SNAP";

        @SerializedName("lerpTicks")
        public int lerpTicks = 0;

        public CameraData() {}

        public CameraData(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public CameraData(float yaw, float pitch, float zoom) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
        }

        public CameraData(float yaw, float pitch, float zoom, String lerpType, int lerpTicks) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
            this.lerpType = lerpType;
            this.lerpTicks = lerpTicks;
        }
    }

    @Getter
    public static class MistakeData {

        @SerializedName("x")
        public int x = 0;
        @SerializedName("y")
        public int y = 0;
        @SerializedName("z")
        public int z = 0;
        @SerializedName("label")
        public String label = "";

        @SerializedName("color")
        public String color = "FFB74D";

        public MistakeData() {}

        public MistakeData(int x, int y, int z, String label) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
        }

        public MistakeData(int x, int y, int z, String label, String color) {
            this(x, y, z, label);
            this.color = color;
        }

        public int colorArgb() {
            try {
                return (int) (Long.parseLong(color, 16) | 0xFF000000L);
            } catch (NumberFormatException e) {
                return 0xFFFFB74D;
            }
        }
    }

    public PhantasiaScriptData() {}

    public PhantasiaScriptData(String machine) {
        this.machine = machine;
    }

    public static PhantasiaScriptData defaultFor(String machine) {
        PhantasiaScriptData d = new PhantasiaScriptData(machine);
        StepData s = new StepData(0, null);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public static PhantasiaScriptData simpleFor(String machine, String caption) {
        PhantasiaScriptData d = new PhantasiaScriptData(machine);
        StepData s = new StepData(0, caption);
        s.show = "all";
        d.steps.add(s);
        return d;
    }

    public PhantasiaScriptData copy() {
        PhantasiaScriptData c = new PhantasiaScriptData(machine);
        c.startCamera = startCamera == null ? null : startCamera.copy();
        c.scriptDuration = scriptDuration;
        for (StepData s : steps) c.steps.add(s.copy());
        for (MistakeData m : mistakes) {
            c.mistakes.add(new MistakeData(m.x, m.y, m.z, m.label, m.color));
        }
        c.globalMistakes.addAll(globalMistakes);
        return c;
    }

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PhantasiaScriptData fromJson(String json) {
        return GSON.fromJson(json, PhantasiaScriptData.class);
    }

    public static PhantasiaScriptData fromJson(java.io.Reader reader) {
        return GSON.fromJson(reader, PhantasiaScriptData.class);
    }
}
