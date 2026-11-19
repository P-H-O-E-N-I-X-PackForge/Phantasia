package net.phoenixvine.phantasia.firebird;

import com.gregtechceu.gtceu.api.recipe.gui.viewer.GTViewerParts;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.recipeviewer.widgets.MultiblockPreviewExtensions;
import com.gregtechceu.gtceu.integration.recipeviewer.widgets.MultiblockPreviewExtensions.Slot;
import com.gregtechceu.gtceu.integration.recipeviewer.widgets.MultiblockPreviewWidget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.dynamic.DynamicHandler;
import brachy.modularui.widgets.dynamic.DynamicWidget;
import brachy.modularui.widgets.ButtonWidget;
import net.phoenixvine.phantasia.api.PhantasiaAPI;
import net.phoenixvine.phantasia.compat.PhantasiaBlockInspectCompat;
import net.phoenixvine.phantasia.common.PhantasiaItems;
import net.phoenixvine.phantasia.compat.gtceu.FirebirdBridge;
import net.phoenixvine.phantasia.compat.gtceu.GTCEuMultiblockDefinition;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Locale;
import java.util.List;

/**
 * Phantasia's additions to Firebird's GregTech recipe viewer. Everything here uses classes real GTM does not have, so
 * this source set is only ever loaded through {@link net.phoenixvine.phantasia.compat.gtceu.GtFlavor}.
 */
public final class FirebirdSupport implements FirebirdBridge {

    private static final String PROBE_LAYER = "phantasia:probe";
    private static final int CARD_WIDTH = 112;
    private static final int CARD_LINES = 6;
    /** The card is dark, so text with no colour of its own is light. */
    private static final int CARD_TEXT = 0xFFE6E6E6;

    /** The card of each open preview, to redraw it when another block is clicked. */
    private static final Map<MultiblockPreviewWidget, DynamicHandler> CARDS = new WeakHashMap<>();

    @Override
    public void init() {
        // under every multiblock preview, how much room the structure takes
        MultiblockPreviewExtensions.register(Slot.BELOW, FirebirdSupport::footprintLine);

        // beside the 3D view, what the clicked block is for and what Phantasia's inspectors know about it
        MultiblockPreviewExtensions.register(Slot.LEFT, FirebirdSupport::inspectorCard);
        MultiblockPreviewExtensions.onSelectionChanged(preview -> {
            DynamicHandler handler = CARDS.get(preview);
            if (handler != null) handler.notifyUpdate();
        });

        // for multiblocks Phantasia has a scene for, a button that opens it; its footprint and cost screens are there
        MultiblockPreviewExtensions.register(Slot.TOOLBAR,
                definition -> PhantasiaAPI.isAvailable(definition.getId().toString()),
                FirebirdSupport::openInPhantasia);

        // A probe to prove the hooks work on both GregTech builds; start the game with
        // -Dphantasia.firebirdProbe=true (or -Pphantasia_probe=true on runClient) to see it.
        if (Boolean.getBoolean("phantasia.firebirdProbe")) {
            MultiblockPreviewExtensions.register(Slot.BELOW, preview -> Text.dynamic(() -> Component.literal(
                    "Phantasia probe: " + (preview.hasSelection() ?
                            preview.getSelectedStack().getHoverName().getString() : "click a block")))
                    .asWidget());
            // and a variant to hide the top layer, to see the variants menu and the parts list follow it
            MultiblockPreviewExtensions.registerVariants(definition -> true, preview -> List.of(
                    new MultiblockPreviewExtensions.Variant("phantasia:probe_top",
                            Component.literal("Top layer (probe)"), true, FirebirdSupport::topLayer)));
            MultiblockPreviewExtensions.onSchemaChanged(FirebirdSupport::outline);
            MultiblockPreviewExtensions.onSelectionChanged(FirebirdSupport::outline);
        }
    }

    /** "Footprint: 5 × 5, 7 high": the structure's extent, from its blocks, so it follows flipping. */
    private static IWidget footprintLine(MultiblockPreviewWidget preview) {
        return Text.dynamic(() -> {
            var blocks = preview.getMultiblockSchemaInfo().getStructureBlocks();
            if (blocks.isEmpty()) return Component.empty();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blocks.keySet()) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return Component.translatable("phantasia.preview.footprint", maxX - minX + 1, maxZ - minZ + 1,
                    maxY - minY + 1);
        }).asWidget().color(0xFF404040).marginTop(3);
    }

    private static IWidget inspectorCard(MultiblockPreviewWidget preview) {
        DynamicHandler handler = new DynamicHandler();
        handler.widgetProvider(() -> buildCard(preview));
        CARDS.put(preview, handler);
        return new DynamicWidget<>().coverChildren().clientOnlyHandler(handler);
    }

    /** The card for the selected block, or nothing while none is. */
    private static IWidget buildCard(MultiblockPreviewWidget preview) {
        if (!preview.hasSelection()) return null;
        var state = preview.getSelectedState();
        Flow card = Flow.col().coverChildren().childPadding(1).padding(3)
                .background(GTGuiTextures.BACKGROUND_INVERSE);
        card.child(Text.of(preview.getSelectedStack().getHoverName()).style(ChatFormatting.BOLD).color(0xFFFFFFFF)
                .asWidget().width(CARD_WIDTH));

        // the block's own tooltip, without its name line, and what Phantasia's inspectors add to it
        List<Component> lines = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<Component> tooltip = preview.getSelectedStack().getTooltipLines(Item.TooltipContext.of(level),
                    Minecraft.getInstance().player, TooltipFlag.Default.NORMAL);
            if (tooltip.size() > 1) lines.addAll(tooltip.subList(1, tooltip.size()));
        }
        boolean controller = preview.getSelectedPos() != null &&
                preview.getSelectedPos().equals(preview.toPreviewPos(BlockPos.ZERO));
        Component[] role = { Component.translatable(controller ? "role.phantasia.controller" :
                "role.phantasia.standard") };
        PhantasiaBlockInspectCompat.apply(state.getBlock(), lines, r -> role[0] = r);

        card.child(GTViewerParts.unique(Text.of(role[0].copy().withStyle(ChatFormatting.AQUA)).asWidget()
                .width(CARD_WIDTH)));
        String modName = modName(state.getBlock());
        int shown = 0;
        for (Component line : lines) {
            String text = line.getString();
            // tooltip noise that says nothing in a preview: the shift prompt, blank lines and the mod's own name
            if (shown >= CARD_LINES || text.isBlank() || text.toLowerCase(Locale.ROOT).contains("hold shift") ||
                    text.equals(modName)) {
                continue;
            }
            card.child(GTViewerParts.unique(Text.of(line).color(CARD_TEXT).asWidget().width(CARD_WIDTH)));
            shown++;
        }
        return card;
    }

    private static String modName(net.minecraft.world.level.block.Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private static IWidget openInPhantasia(MultiblockPreviewWidget preview) {
        var definition = preview.getMultiblockDefinition();
        return new ButtonWidget<>()
                .size(16, 16)
                .overlay(new ItemDrawable(new ItemStack(PhantasiaItems.PHANTASIA_MANUAL.get())).asIcon().size(14))
                .tooltip(r -> r.addLine(Text.lang("phantasia.preview.open_scene")))
                .onMousePressed((context, button) -> {
                    PhantasiaAPI.openForDefinition(new GTCEuMultiblockDefinition(definition),
                            Minecraft.getInstance().screen);
                    return true;
                });
    }

    /** Every block on the highest level of the structure. */
    private static List<BlockPos> topLayer(MultiblockPreviewWidget preview) {
        var blocks = preview.getMultiblockSchemaInfo().getStructureBlocks().keySet();
        int top = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) top = Math.max(top, pos.getY());
        final int y = top;
        return blocks.stream().filter(pos -> pos.getY() == y).toList();
    }

    private static void outline(MultiblockPreviewWidget preview) {
        BlockPos controller = preview.toPreviewPos(BlockPos.ZERO);
        BlockPos selected = preview.getSelectedPos();
        preview.highlight(PROBE_LAYER, selected == null ? List.of(controller) : List.of(controller, selected),
                0xCCFFCC00);
    }
}
