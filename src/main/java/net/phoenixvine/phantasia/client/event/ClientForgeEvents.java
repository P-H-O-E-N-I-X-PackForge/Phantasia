package net.phoenixvine.phantasia.client.event;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.phoenixvine.phantasia.Phantasia;
import net.phoenixvine.phantasia.client.screens.PhantasiaSceneSelectionScreen;

@EventBusSubscriber(modid = Phantasia.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("phantasia")
                        .executes(context -> {
                            Minecraft.getInstance().tell(() -> {
                                Minecraft.getInstance().setScreen(
                                        new PhantasiaSceneSelectionScreen(null));
                            });
                            return 1;
                        }));
    }
}
