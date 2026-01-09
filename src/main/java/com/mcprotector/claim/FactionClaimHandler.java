package com.mcprotector.claim;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.dynmap.DynmapBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

public class FactionClaimHandler {
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
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
            DynmapBridge.updateClaim(chunkPos, faction);
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
        spawnBorderParticles(level, player, chunkPos);
    }

    private void spawnBorderParticles(ServerLevel level, ServerPlayer player, ChunkPos chunkPos) {
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        double y = player.getY() + 1.0;
        int step = 4;
        for (int x = minX; x <= maxX; x += step) {
            level.sendParticles(player, ParticleTypes.FLAME, true, x + 0.5, y, minZ + 0.5, 2, 0.05, 0.05, 0.05, 0);
            level.sendParticles(player, ParticleTypes.FLAME, true, x + 0.5, y, maxZ + 0.5, 2, 0.05, 0.05, 0.05, 0);
        }
        for (int z = minZ; z <= maxZ; z += step) {
            level.sendParticles(player, ParticleTypes.FLAME, true, minX + 0.5, y, z + 0.5, 2, 0.05, 0.05, 0.05, 0);
            level.sendParticles(player, ParticleTypes.FLAME, true, maxX + 0.5, y, z + 0.5, 2, 0.05, 0.05, 0.05, 0);
        }
    }
}
