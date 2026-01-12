package com.mcprotector.claim;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.dynmap.DynmapBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

public class FactionClaimHandler {
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ensureSettingsLoaded(player);
        ChunkPos currentChunk = new ChunkPos(player.blockPosition());
        ChunkPos lastChunk = FactionClaimManager.getLastChunk(player.getUUID());
        boolean chunkChanged = lastChunk == null || !lastChunk.equals(currentChunk);
        if (chunkChanged) {
            FactionClaimManager.setLastChunk(player.getUUID(), currentChunk);
            handleAutoClaim(player, currentChunk);
            handleTerritoryOverlay(player);
        }
    }

    private void handleAutoClaim(ServerPlayer player, ChunkPos chunkPos) {
        if (!FactionClaimManager.isAutoClaimEnabled(player.getUUID())) {
            return;
        }
        String dimension = player.level().dimension().location().toString();
        if (FactionConfig.SERVER.safeZoneDimensions.get().contains(dimension)
            || FactionConfig.SERVER.warZoneDimensions.get().contains(dimension)) {
            return;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            return;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.CHUNK_CLAIM)) {
            return;
        }
        long now = System.currentTimeMillis();
        int level = data.getFactionLevel(faction.get().getId());
        int cooldownSeconds = Math.max(0, FactionConfig.SERVER.autoClaimCooldownSeconds.get()
            - (level - 1) * FactionConfig.SERVER.claimCooldownReductionPerLevel.get());
        long lastClaim = FactionClaimManager.getLastAutoClaim(player.getUUID());
        if (now - lastClaim < cooldownSeconds * 1000L) {
            return;
        }
        if (data.claimChunk(chunkPos, faction.get().getId())) {
            FactionClaimManager.setLastAutoClaim(player.getUUID(), now);
            DynmapBridge.updateClaim(chunkPos, faction, player.level().dimension().location().toString());
            player.sendSystemMessage(Component.literal("Auto-claimed chunk for " + faction.get().getName()));
        }
    }

    private void handleTerritoryOverlay(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        FactionData data = FactionData.get(level);
        BlockPos pos = player.blockPosition();
        Optional<Faction> owner = data.getFactionAt(pos);
        boolean isSafeZone = data.isSafeZoneClaimed(pos);
        boolean isPersonal = data.getPersonalClaimOwner(pos).isPresent();
        Optional<UUID> ownerId = owner.map(Faction::getId);
        Optional<UUID> lastOwner = FactionClaimManager.getLastTerritory(player.getUUID());
        if (!lastOwner.equals(ownerId)) {
            if (owner.isEmpty()) {
                player.displayClientMessage(Component.literal("Entering wilderness").withStyle(ChatFormatting.GRAY), true);
            } else {
                TextColor mapColor = getMapColor(player, data, ownerId.orElse(null), isSafeZone, isPersonal);
                player.displayClientMessage(
                    Component.literal("Entering " + owner.get().getName() + " territory").withStyle(style -> style.withColor(mapColor)),
                    true
                );
            }
        }
        FactionClaimManager.setLastTerritory(player.getUUID(), ownerId);
    }

    private TextColor getMapColor(ServerPlayer player, FactionData data, UUID ownerId, boolean safeZone, boolean personal) {
        if (safeZone) {
            return TextColor.fromRgb(0xF9A825);
        }
        if (personal) {
            return TextColor.fromRgb(0x9C27B0);
        }
        Optional<UUID> playerFactionId = data.getFactionIdByPlayer(player.getUUID());
        if (ownerId == null || playerFactionId.isEmpty()) {
            return TextColor.fromRgb(0x8D8D8D);
        }
        if (ownerId.equals(playerFactionId.get())) {
            return TextColor.fromRgb(0x4CAF50);
        }
        FactionRelation relation = data.getRelation(playerFactionId.get(), ownerId);
        return switch (relation) {
            case ALLY -> TextColor.fromRgb(0x4FC3F7);
            case WAR -> TextColor.fromRgb(0xEF5350);
            default -> TextColor.fromRgb(0x8D8D8D);
        };
    }

    private void ensureSettingsLoaded(ServerPlayer player) {
        FactionData data = FactionData.get(player.serverLevel());
        UUID playerId = player.getUUID();
        if (!FactionClaimManager.hasAutoClaimSetting(playerId)) {
            FactionClaimManager.setAutoClaimEnabled(playerId, data.isAutoClaimEnabled(playerId));
        }
        if (!FactionClaimManager.hasBorderSetting(playerId)) {
            FactionClaimManager.setBorderEnabled(playerId, data.isBorderEnabled(playerId));
        }
    }
}
