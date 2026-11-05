package net.phoenixvine.phantasia.common.data.variant;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PhantasiaVariantState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("phantasia_variants.json");

    private static final PhantasiaVariantState INSTANCE = new PhantasiaVariantState();

    public static PhantasiaVariantState get() {
        return INSTANCE;
    }

    private PhantasiaVariantState() {
        loadSelections();
    }

    private final Map<String, Integer> selections = new LinkedHashMap<>();

    private List<PhantasiaVariantGroup> groups = Collections.emptyList();

    private final Map<BlockPos, PhantasiaVariantGroup> positionToGroup = new HashMap<>();

    @Nullable
    private Runnable onChangeCallback = null;

    @Nullable
    private Runnable onToggleCallback = null;

    /** Positions of every switchable substructure, with the switches that cover them. */
    private final Map<BlockPos, List<PhantasiaVariantGroup>> toggleCover = new HashMap<>();

    private void loadSelections() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    this.selections.putAll(loaded);
                }
            } catch (Exception e) {
                net.phoenixvine.phantasia.Phantasia.LOGGER.error("[Phantasia] Failed to load variant selections: {}",
                        e.getMessage());
            }
        }
    }

    private void saveSelections() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this.selections, writer);
            }
        } catch (Exception e) {
            net.phoenixvine.phantasia.Phantasia.LOGGER.error("[Phantasia] Failed to save variant selections: {}",
                    e.getMessage());
        }
    }

    public void loadGroups(List<PhantasiaVariantGroup> newGroups) {
        groups = new ArrayList<>(newGroups);
        positionToGroup.clear();
        toggleCover.clear();

        for (PhantasiaVariantGroup group : groups) {

            selections.putIfAbsent(group.getId(), group.defaultIndex());

            if (group.isToggle()) {
                for (BlockPos wp : group.getPositionBaseIndex().keySet())
                    toggleCover.computeIfAbsent(wp, k -> new ArrayList<>()).add(group);
                continue;
            }

            for (BlockPos wp : group.getPositionBaseIndex().keySet()) {
                positionToGroup.put(wp, group);
            }
        }
    }

    public List<PhantasiaVariantGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public int getSelection(String groupId) {
        PhantasiaVariantGroup group = groups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst().orElse(null);
        return selections.getOrDefault(groupId, group != null ? group.defaultIndex() : 0);
    }

    private static String shapeKey(String machineId) {
        return "#shape:" + machineId;
    }

    public int getShapeIndex(String machineId) {
        return selections.getOrDefault(shapeKey(machineId), 0);
    }

    public void setShapeIndex(String machineId, int index) {
        String key = shapeKey(machineId);
        Integer current = selections.get(key);
        if (current != null && current == index) return;
        selections.put(key, index);
        saveSelections();
    }

    public void setSelection(String groupId, int index) {
        Integer current = selections.get(groupId);
        if (current != null && current == index) return;

        selections.put(groupId, index);
        saveSelections();

        boolean toggled = groups.stream().anyMatch(g -> g.isToggle() && g.getId().equals(groupId));
        if (toggled) {
            if (onToggleCallback != null) onToggleCallback.run();
            return;
        }
        if (onChangeCallback != null) onChangeCallback.run();
    }

    public void setOnToggleCallback(@Nullable Runnable callback) {
        this.onToggleCallback = callback;
    }

    public boolean isOn(PhantasiaVariantGroup group) {
        return selections.getOrDefault(group.getId(), group.defaultIndex()) == 0;
    }

    /**
     * True when every substructure that contains this position has been switched off. A block two substructures share
     * stays while either is on.
     */
    public boolean isToggleHidden(BlockPos worldPos) {
        List<PhantasiaVariantGroup> cover = toggleCover.get(worldPos);
        if (cover == null) return false;
        for (PhantasiaVariantGroup g : cover) if (isOn(g)) return false;
        return true;
    }

    public void setOnChangeCallback(@Nullable Runnable callback) {
        this.onChangeCallback = callback;
    }

    public BlockState resolveState(BlockPos worldPos, BlockState baseState) {
        PhantasiaVariantGroup group = positionToGroup.get(worldPos);
        if (group == null) return baseState;

        int selectedIdx = selections.getOrDefault(group.getId(), group.defaultIndex());
        List<BlockState> options = group.getOptions();
        if (selectedIdx < 0 || selectedIdx >= options.size()) return baseState;

        BlockState target = options.get(selectedIdx);
        if (target == baseState || target.getBlock() == baseState.getBlock()) return baseState;

        net.minecraft.world.level.block.state.StateDefinition<net.minecraft.world.level.block.Block, BlockState> targetDef = target
                .getBlock().getStateDefinition();
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : baseState.getBlock()
                .getStateDefinition().getProperties()) {
            if (targetDef.getProperty(prop.getName()) != null) {
                target = copyProperty(target, baseState, prop);
            }
        }
        return target;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(
                                                                     BlockState target, BlockState source,
                                                                     net.minecraft.world.level.block.state.properties.Property<T> prop) {
        try {
            net.minecraft.world.level.block.state.properties.Property<T> targetProp = (net.minecraft.world.level.block.state.properties.Property<T>) target
                    .getBlock().getStateDefinition().getProperty(prop.getName());
            if (targetProp == null) return target;
            T value = source.getValue(prop);
            if (target.getValues().containsKey(targetProp)) {
                return target.setValue(targetProp, value);
            }
        } catch (Exception ignored) {}
        return target;
    }

    public void clear() {
        groups = Collections.emptyList();
        positionToGroup.clear();
        toggleCover.clear();
        onChangeCallback = null;
        onToggleCallback = null;
    }
}
