package net.phoenixvine.phantasia.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.editors.PhantasiaCustomEditorScreen;
import net.phoenixvine.phantasia.compat.custom.PhantasiaCustomLayout;
import net.phoenixvine.phantasia.utils.PhantasiaLang;

import java.util.List;

import javax.annotation.Nullable;

public class PhantasiaWandItem extends Item {

    private static final String KEY_POS1 = "pos1";
    private static final String KEY_POS2 = "pos2";
    private static final int MAX_CAPTURE_BLOCKS = 20_000;

    public PhantasiaWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        BlockPos clicked = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();

        if (player != null && player.isShiftKeyDown()) {
            setPos(stack, KEY_POS2, clicked);
            player.sendSystemMessage(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_corner_2_set_to",
                    clicked.toShortString()));
        } else {
            setPos(stack, KEY_POS1, clicked);
            if (player != null) player.sendSystemMessage(
                    PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_corner_1_set_to",
                            clicked.toShortString()));
        }

        if (player != null) {
            BlockPos p1 = getPos(stack, KEY_POS1);
            BlockPos p2 = getPos(stack, KEY_POS2);
            if (p1 != null && p2 != null) {
                int dx = Math.abs(p1.getX() - p2.getX()) + 1;
                int dy = Math.abs(p1.getY() - p2.getY()) + 1;
                int dz = Math.abs(p1.getZ() - p2.getZ()) + 1;
                player.sendSystemMessage(
                        PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.selection_blocks_right_click_air", dx, dy,
                                dz, (dx * dy * dz)));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            BlockPos p1 = getPos(stack, KEY_POS1);
            BlockPos p2 = getPos(stack, KEY_POS2);
            if (p1 != null && p2 != null) {
                captureAndOpen(stack, level, player);
            } else {
                player.sendSystemMessage(
                        PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_set_both_corners_first"));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void captureAndOpen(ItemStack wand, Level level, Player player) {
        BlockPos p1 = getPos(wand, KEY_POS1);
        BlockPos p2 = getPos(wand, KEY_POS2);
        if (p1 == null || p2 == null) return;

        int minX = Math.min(p1.getX(), p2.getX()), maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY()), maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ()), maxZ = Math.max(p1.getZ(), p2.getZ());

        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_CAPTURE_BLOCKS) {
            player.sendSystemMessage(
                    PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_selection_too_large_blocks", volume,
                            MAX_CAPTURE_BLOCKS));
            return;
        }

        PhantasiaCustomLayout layout = new PhantasiaCustomLayout();
        layout.id = "phantasia:captured_scene";
        layout.displayName = "Captured Scene";

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;
                    BlockPos local = new BlockPos(x - minX, y - minY, z - minZ);
                    layout.blocks.put(local, PhantasiaCustomLayout.serializeBlockState(state));
                    count++;
                }
            }
        }

        if (count == 0) {
            player.sendSystemMessage(
                    PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_selection_contains_no_non"));
            return;
        }

        player.sendSystemMessage(
                PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.phantasia_captured_blocks_opening_editor", count));
        Minecraft.getInstance().setScreen(
                new PhantasiaCustomEditorScreen(Phantasia.CUSTOM_PROVIDER, layout));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> lines, TooltipFlag flag) {
        BlockPos p1 = getPos(stack, KEY_POS1);
        BlockPos p2 = getPos(stack, KEY_POS2);

        lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.right_click_block_set_corner")
                .withStyle(ChatFormatting.GRAY));
        lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.shift_right_click_set_corner")
                .withStyle(ChatFormatting.GRAY));
        lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.right_click_air_import_selection")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());

        if (p1 != null) {
            lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.corner_1", p1.toShortString()));
        } else {
            lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.corner_1_not_set"));
        }
        if (p2 != null) {
            lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.corner_2", p2.toShortString()));
        } else {
            lines.add(PhantasiaLang.tr("ui.phantasia.phantasia_wand_item.corner_2_not_set"));
        }
    }

    private static void setPos(ItemStack stack, String key, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        net.phoenixvine.phantasia.utils.PhantasiaItemData.edit(stack, root -> root.put(key, tag));
    }

    @Nullable
    public static BlockPos getPos(ItemStack stack, String key) {
        CompoundTag root = net.phoenixvine.phantasia.utils.PhantasiaItemData.getTag(stack);
        if (root == null || !root.contains(key)) return null;
        CompoundTag tag = root.getCompound(key);
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    @Nullable
    public static BlockPos getPos1(ItemStack stack) {
        return getPos(stack, KEY_POS1);
    }

    @Nullable
    public static BlockPos getPos2(ItemStack stack) {
        return getPos(stack, KEY_POS2);
    }
}
