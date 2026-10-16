package net.phoenixvine.phantasia.common;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.common.item.PhantasiaManualItem;
import net.phoenixvine.phantasia.common.item.PhantasiaWandItem;

public final class PhantasiaItems {

    private PhantasiaItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Phantasia.MOD_ID);

    public static final DeferredHolder<Item, PhantasiaManualItem> PHANTASIA_MANUAL = ITEMS.register("phantasia_manual",
            () -> new PhantasiaManualItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, PhantasiaWandItem> CAPTURE_WAND = ITEMS.register("capture_wand",
            () -> new PhantasiaWandItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
