package com.mcprotector;

import com.mcprotector.chat.FactionChatHandler;
import com.mcprotector.claim.FactionClaimHandler;
import com.mcprotector.command.FactionCommands;
import com.mcprotector.command.FactionRelationCommands;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.FactionData;
import com.mcprotector.dynmap.DynmapBridge;
import com.mcprotector.network.FactionClaimMapPacket;
import com.mcprotector.network.FactionStatePacket;
import com.mcprotector.network.NetworkHandler;
import com.mcprotector.protection.ClaimProtectionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McProtectorMod.MOD_ID)
public class McProtectorMod {
    public static final String MOD_ID = "mcprotector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int CLAIM_MAP_SYNC_INTERVAL_TICKS = 100;
    private int claimMapSyncTicks;

    public McProtectorMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(NetworkHandler::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.SERVER, FactionConfig.SERVER_SPEC);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onChunkSent);
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

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerClaimState(player);
        }
    }

    private void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncPlayerClaimState(player);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++claimMapSyncTicks < CLAIM_MAP_SYNC_INTERVAL_TICKS) {
            return;
        }
        claimMapSyncTicks = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        }
    }

    private void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerPlayer player = event.getPlayer();
        NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
    }

    private void syncPlayerClaimState(ServerPlayer player) {
        NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
        NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
    }
}
