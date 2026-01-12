package com.mcprotector.claim;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.dynmap.DynmapBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

public class FactionClaimHandler {
    private static final int SAFE_ZONE_COLOR = 0xFFF9A825;
    private static final int PERSONAL_CLAIM_COLOR = 0xFF9C27B0;
    private static final float FORCE_FIELD_SCALE = 1.2f;
    private static final int MIN_RENDER_RADIUS = 2;

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
        handleClaimBorder(player, currentChunk);
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
        Optional<UUID> ownerId = owner.map(Faction::getId);
        Optional<UUID> lastOwner = FactionClaimManager.getLastTerritory(player.getUUID());
        if (!lastOwner.equals(ownerId)) {
            if (owner.isEmpty()) {
                player.displayClientMessage(Component.literal("Entering wilderness").withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(
                    Component.literal("Entering " + owner.get().getName() + " territory").withStyle(owner.get().getColor()),
                    true
                );
            }
        }
        FactionClaimManager.setLastTerritory(player.getUUID(), ownerId);
    }

    private void handleClaimBorder(ServerPlayer player, ChunkPos chunkPos) {
        if (!FactionClaimManager.isBorderEnabled(player.getUUID())) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastParticle = FactionClaimManager.getLastBorderParticle(player.getUUID());
        if (now - lastParticle < 1000L) {
            return;
        }
        FactionClaimManager.setLastBorderParticle(player.getUUID(), now);
        ServerLevel level = player.serverLevel();
        FactionData data = FactionData.get(level);
        int renderRadius = Math.max(MIN_RENDER_RADIUS, player.serverLevel().getServer().getPlayerList().getViewDistance());
        spawnBorderParticlesInRange(level, player, chunkPos, renderRadius, data);
    }

    private void spawnBorderParticlesInRange(ServerLevel level, ServerPlayer player, ChunkPos center, int radius,
                                             FactionData data) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos chunkPos = new ChunkPos(center.x + dx, center.z + dz);
                boolean isSafeZone = data.isSafeZoneClaimed(chunkPos);
                boolean isPersonal = !isSafeZone && data.getPersonalClaimOwner(chunkPos).isPresent();
                if (!isSafeZone && !isPersonal) {
                    continue;
                }
                int color = isSafeZone ? SAFE_ZONE_COLOR : PERSONAL_CLAIM_COLOR;
                spawnBorderParticles(level, player, chunkPos, color);
            }
        }
    }

    private void spawnBorderParticles(ServerLevel level, ServerPlayer player, ChunkPos chunkPos, int color) {
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        double y = player.getY() + 1.0;
        int step = 3;
        DustColorTransitionOptions particle = buildBorderParticle(color);
        for (int x = minX; x <= maxX; x += step) {
            level.sendParticles(player, particle, true, x + 0.5, y, minZ + 0.5, 4, 0.08, 0.12, 0.08, 0);
            level.sendParticles(player, particle, true, x + 0.5, y, maxZ + 0.5, 4, 0.08, 0.12, 0.08, 0);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            level.sendParticles(player, particle, true, minX + 0.5, y, z + 0.5, 4, 0.08, 0.12, 0.08, 0);
            level.sendParticles(player, particle, true, maxX + 0.5, y, z + 0.5, 4, 0.08, 0.12, 0.08, 0);
        }
    }

    private DustColorTransitionOptions buildBorderParticle(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        Vector3f rgb = new Vector3f(red / 255.0f, green / 255.0f, blue / 255.0f);
        Vector3f highlight = new Vector3f(
            Math.min(1.0f, rgb.x + 0.35f),
            Math.min(1.0f, rgb.y + 0.35f),
            Math.min(1.0f, rgb.z + 0.35f)
        );
        return new DustColorTransitionOptions(rgb, highlight, FORCE_FIELD_SCALE);
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
