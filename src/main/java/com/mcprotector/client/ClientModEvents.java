package com.mcprotector.client;

import com.mcprotector.McProtectorMod;
import com.mcprotector.client.gui.FactionMainScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = McProtectorMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private static KeyMapping factionUiKey;

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientTick);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        factionUiKey = new KeyMapping("key.mcprotector.faction_ui", GLFW.GLFW_KEY_G, "key.categories.ui");
        event.register(factionUiKey);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (factionUiKey == null) {
            return;
        }
        while (factionUiKey.consumeClick()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            client.setScreen(new FactionMainScreen());
            FactionClientData.requestUpdate();
        }
    }
}
