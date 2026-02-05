package com.mcprotector.claim;

import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionClaimManager {
    private static final Map<UUID, Boolean> AUTO_CLAIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> BORDER_ENABLED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_AUTO_CLAIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_BORDER_PARTICLE = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkPos> LAST_CHUNK = new ConcurrentHashMap<>();
    private static final Map<UUID, Optional<UUID>> LAST_TERRITORY = new ConcurrentHashMap<>();

    private FactionClaimManager() {
    }

    public static boolean isAutoClaimEnabled(UUID playerId) {
        return AUTO_CLAIM.getOrDefault(playerId, false);
    }

    public static boolean hasAutoClaimSetting(UUID playerId) {
        return AUTO_CLAIM.containsKey(playerId);
    }

    public static void setAutoClaimEnabled(UUID playerId, boolean enabled) {
        AUTO_CLAIM.put(playerId, enabled);
    }

    public static boolean isBorderEnabled(UUID playerId) {
        return BORDER_ENABLED.getOrDefault(playerId, false);
    }

    public static boolean hasBorderSetting(UUID playerId) {
        return BORDER_ENABLED.containsKey(playerId);
    }

    public static void setBorderEnabled(UUID playerId, boolean enabled) {
        BORDER_ENABLED.put(playerId, enabled);
        if (!enabled) {
            LAST_BORDER_PARTICLE.remove(playerId);
        }
    }

    public static long getLastAutoClaim(UUID playerId) {
        return LAST_AUTO_CLAIM.getOrDefault(playerId, 0L);
    }

    public static void setLastAutoClaim(UUID playerId, long timestamp) {
        LAST_AUTO_CLAIM.put(playerId, timestamp);
    }

    public static long getLastBorderParticle(UUID playerId) {
        return LAST_BORDER_PARTICLE.getOrDefault(playerId, 0L);
    }

    public static void setLastBorderParticle(UUID playerId, long timestamp) {
        LAST_BORDER_PARTICLE.put(playerId, timestamp);
    }

    public static ChunkPos getLastChunk(UUID playerId) {
        return LAST_CHUNK.get(playerId);
    }

    public static void setLastChunk(UUID playerId, ChunkPos chunkPos) {
        if (chunkPos == null) {
            LAST_CHUNK.remove(playerId);
            return;
        }
        LAST_CHUNK.put(playerId, chunkPos);
    }

    public static Optional<UUID> getLastTerritory(UUID playerId) {
        return LAST_TERRITORY.getOrDefault(playerId, Optional.empty());
    }

    public static void setLastTerritory(UUID playerId, Optional<UUID> factionId) {
        if (factionId == null) {
            LAST_TERRITORY.remove(playerId);
            return;
        }
        LAST_TERRITORY.put(playerId, factionId);
    }
}
