package net.phoenixvine.phantasia.compat.pack;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockDefinition;
import net.phoenixvine.phantasia.common.multiblock.IPhantasiaMultiblockProvider;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomLayout;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomMultiblockDefinition;
import net.phoenixvine.phantasia.utils.PhantasiaBlockInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Scenes that mods and resource packs ship themselves, with no code on Phantasia's side. Drop either of these under
 * {@code assets/<namespace>/phantasia/multiblocks/}:
 * <ul>
 * <li>a {@code .json} in the same format the in-game editor saves (id, displayName, iconBlock, blocks), or</li>
 * <li>a vanilla structure {@code .nbt}, such as the ones the structure block saves.</li>
 * </ul>
 * A file with no id of its own is named after its path: {@code assets/mymod/phantasia/multiblocks/forge.nbt} becomes
 * {@code mymod:forge}. They are read on the client, so a resource pack works on a server that has neither.
 */
public class PhantasiaPackProvider implements IPhantasiaMultiblockProvider, ResourceManagerReloadListener {

    public static final String FOLDER = "phantasia/multiblocks";

    private final Map<ResourceLocation, PhantasiaCustomMultiblockDefinition> loaded = new LinkedHashMap<>();

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation, PhantasiaCustomMultiblockDefinition> next = new LinkedHashMap<>();
        manager.listResources(FOLDER, rl -> rl.getPath().endsWith(".json") || rl.getPath().endsWith(".nbt"))
                .forEach((rl, resource) -> {
                    try (InputStream in = resource.open()) {
                        String path = rl.getPath().substring(FOLDER.length() + 1);
                        String stem = path.substring(0, path.lastIndexOf('.'));
                        String fallbackId = rl.getNamespace() + ":" + stem;
                        PhantasiaCustomLayout layout = rl.getPath().endsWith(".json") ?
                                fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), fallbackId) :
                                fromStructure(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()), fallbackId, stem);
                        if (layout == null || layout.blocks.isEmpty()) return;
                        PhantasiaCustomMultiblockDefinition def = new PhantasiaCustomMultiblockDefinition(layout);
                        next.put(def.getId(), def);
                    } catch (Exception e) {
                        Phantasia.LOGGER.warn("[Phantasia/Packs] Could not read {}: {}", rl, e.toString());
                    }
                });
        loaded.clear();
        loaded.putAll(next);
        Phantasia.LOGGER.info("[Phantasia/Packs] {} scene(s) from resource packs and mods.", loaded.size());
    }

    private static PhantasiaCustomLayout fromJson(String json, String fallbackId) {
        PhantasiaCustomLayout layout = PhantasiaCustomLayout.fromJson(json);
        if (layout.id == null || layout.id.equals("phantasia:unnamed")) layout.id = fallbackId;
        return layout;
    }

    /** A vanilla structure template: a palette of block states and a list of positions that point into it. */
    static PhantasiaCustomLayout fromStructure(CompoundTag root, String id, String name) {
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty() && root.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = root.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) palette = palettes.getList(0);
        }
        String[] states = new String[palette.size()];
        for (int i = 0; i < states.length; i++) states[i] = stateString(palette.getCompound(i));

        PhantasiaCustomLayout layout = new PhantasiaCustomLayout();
        layout.id = id;
        layout.displayName = prettify(name);
        for (Tag t : root.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            int s = b.getInt("state");
            if (s < 0 || s >= states.length) continue;
            String state = states[s];
            if (state.startsWith("minecraft:air") || state.startsWith("minecraft:cave_air") ||
                    state.startsWith("minecraft:void_air") || state.startsWith("minecraft:structure_void") ||
                    state.startsWith("minecraft:jigsaw") || state.startsWith("minecraft:structure_block")) {
                continue;
            }
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) continue;
            layout.blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state);
        }
        return layout;
    }

    private static String stateString(CompoundTag entry) {
        String name = entry.getString("Name");
        CompoundTag props = entry.getCompound("Properties");
        if (props.isEmpty()) return name;
        StringJoiner sj = new StringJoiner(",", name + "[", "]");
        for (String key : new TreeSet<>(props.getAllKeys())) sj.add(key + "=" + props.getString(key));
        return sj.toString();
    }

    private static String prettify(String stem) {
        String last = stem.contains("/") ? stem.substring(stem.lastIndexOf('/') + 1) : stem;
        String spaced = last.replace('_', ' ');
        return spaced.isEmpty() ? "Scene" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    @Override
    public String getModId() {
        return "phantasia_packs";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolve(String machineId) {
        try {
            return Optional.ofNullable(loaded.get(ResourceLocation.parse(machineId)));
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
        return new ArrayList<>(loaded.values());
    }

    @Override
    public boolean isControllerBlock(BlockState state) {
        return false;
    }

    @Override
    public boolean isPartBlock(BlockState state) {
        return false;
    }

    @Override
    public Optional<IPhantasiaMultiblockDefinition> resolveFromItem(ItemStack stack) {
        return Optional.empty();
    }
}
