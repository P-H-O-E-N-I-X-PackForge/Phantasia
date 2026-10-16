package net.phoenixvine.phantasia.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Small stack-data helpers: what 1.20.1 kept in the stack tag lives in the {@code custom_data} component now. */
public final class PhantasiaItemData {

    private PhantasiaItemData() {}

    /** A copy of the stack's custom data, or null when it has none. */
    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    /** Edits the stack's custom data in place, creating it when missing. */
    public static void edit(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }
}
