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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McProtectorMod.MOD_ID)
public class McProtectorMod {
    public static final String MOD_ID = "mcprotector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public McProtectorMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FactionConfig.SERVER_SPEC);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.register(new ClaimProtectionHandler());
        MinecraftForge.EVENT_BUS.register(new FactionChatHandler());
        MinecraftForge.EVENT_BUS.register(new FactionClaimHandler());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DynmapBridge.init();
            NetworkHandler.register();
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
