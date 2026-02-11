package com.mcprotector.client;

import com.mcprotector.McProtectorMod;
import com.mcprotector.client.gui.FactionMainScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = McProtectorMod.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private static KeyMapping factionUiKey;
    private static final int MAP_UPDATE_INTERVAL_TICKS = 80;
    private static int claimMapTickCounter = 0;

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onRenderLevelStage);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        factionUiKey = new KeyMapping("key.mcprotector.faction_ui", GLFW.GLFW_KEY_G, "key.categories.ui");
        event.register(factionUiKey);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof FactionMainScreen screen && screen.isMapTabSelected()) {
            claimMapTickCounter++;
            if (claimMapTickCounter >= MAP_UPDATE_INTERVAL_TICKS) {
                claimMapTickCounter = 0;
                FactionMapClientData.requestUpdate();
            }
        } else {
            claimMapTickCounter = 0;
        }
        if (factionUiKey == null) {
            return;
        }
        while (factionUiKey.consumeClick()) {
            if (client.player == null) {
                return;
            }
            client.setScreen(new FactionMainScreen());
            FactionClientData.requestUpdate();
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        FactionClaimBorderRenderer.render(event);
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        FactionClientData.requestUpdate();
        FactionMapClientData.requestUpdate();
    }
}
