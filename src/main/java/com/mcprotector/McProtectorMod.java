package com.mcprotector;

import com.mcprotector.chat.FactionChatHandler;
import com.mcprotector.claim.FactionClaimHandler;
import com.mcprotector.command.FactionCommands;
import com.mcprotector.command.FactionRelationCommands;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.FactionData;
import com.mcprotector.dynmap.DynmapBridge;
import com.mcprotector.network.NetworkHandler;
import com.mcprotector.protection.ClaimProtectionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McProtectorMod.MOD_ID)
public class McProtectorMod {
    public static final String MOD_ID = "mcprotector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public McProtectorMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(NetworkHandler::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, FactionConfig.SERVER_SPEC);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.register(new ClaimProtectionHandler());
        NeoForge.EVENT_BUS.register(new FactionChatHandler());
        NeoForge.EVENT_BUS.register(new FactionClaimHandler());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DynmapBridge.init();
        });
    }

    private void registerCommands(RegisterCommandsEvent event) {
        FactionCommands.register(event.getDispatcher());
        FactionRelationCommands.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        for (var level : event.getServer().getAllLevels()) {
            if (FactionConfig.SERVER.dynmapFullSyncOnStart.get()) {
                DynmapBridge.syncClaims(level, FactionData.get(level));
            }
        }
    }
}
