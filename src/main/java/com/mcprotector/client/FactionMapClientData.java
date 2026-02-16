package com.mcprotector.client;

import com.mcprotector.network.FactionClaimMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public final class FactionMapClientData {
    private static MapSnapshot snapshot = MapSnapshot.empty();
    private static boolean backgroundEnabledPreference = true;
    private static int zoomPreference = Integer.MIN_VALUE;

    private FactionMapClientData() {
    }

    public static void applyMap(FactionClaimMapPacket packet) {
        Map<Long, FactionClaimMapPacket.ClaimEntry> claims = new HashMap<>();
        for (FactionClaimMapPacket.ClaimEntry entry : packet.claims()) {
            claims.put(new ChunkPos(entry.chunkX(), entry.chunkZ()).toLong(), entry);
        }
        int radius = Math.max(0, packet.radius());
        MapBackgroundState backgroundState = MapBackgroundState.from(packet.background(), backgroundEnabledPreference, zoomPreference);
        snapshot = new MapSnapshot(packet.centerChunkX(), packet.centerChunkZ(), radius, claims, backgroundState);
    }

    public static MapSnapshot getSnapshot() {
        return snapshot;
    }

    public static void requestUpdate() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientNetworkSender.sendToServer(new com.mcprotector.network.FactionClaimMapRequestPacket());
        }
    }

    public static void cycleBackgroundMode() {
        MapBackgroundState state = snapshot.backgroundState();
        boolean enabled = !(state != null && state.enabled());
        backgroundEnabledPreference = enabled;
        snapshot = snapshot.withBackgroundState(state.withEnabled(enabled));
    }

    public static void adjustZoom(int delta) {
        MapBackgroundState state = snapshot.backgroundState();
        if (state == null || state.providerType() == MapBackgroundProviderType.NONE) {
            return;
        }
        int zoom = Math.max(state.minZoom(), Math.min(state.maxZoom(), state.zoom() + delta));
        zoomPreference = zoom;
        snapshot = snapshot.withBackgroundState(state.withZoom(zoom));
    }

    public record MapSnapshot(int centerChunkX, int centerChunkZ, int radius,
                              Map<Long, FactionClaimMapPacket.ClaimEntry> claims,
                              MapBackgroundState backgroundState) {
        public static MapSnapshot empty() {
            return new MapSnapshot(0, 0, 0, new HashMap<>(), MapBackgroundState.none());
        }

        public MapSnapshot withBackgroundState(MapBackgroundState next) {
            return new MapSnapshot(centerChunkX, centerChunkZ, radius, claims, next == null ? MapBackgroundState.none() : next);
        }
    }

    public enum MapBackgroundProviderType {
        NONE,
        SQUAREMAP
    }

    public record MapBackgroundState(MapBackgroundProviderType providerType,
                                     boolean available,
                                     boolean enabled,
                                     String tileUrlTemplate,
                                     String worldName,
                                     int minZoom,
                                     int maxZoom,
                                     int zoom,
                                     int tileBlockSpan) {
        public static MapBackgroundState none() {
            return new MapBackgroundState(MapBackgroundProviderType.NONE, false, false, "", "", 0, 0, 0, 256);
        }

        public static MapBackgroundState from(FactionClaimMapPacket.MapBackgroundMetadata metadata,
                                              boolean enabledPreference,
                                              int zoomPreference) {
            if (metadata == null || !metadata.enabled()) {
                return none();
            }
            MapBackgroundProviderType type = "SQUAREMAP".equalsIgnoreCase(metadata.provider())
                ? MapBackgroundProviderType.SQUAREMAP
                : MapBackgroundProviderType.NONE;
            if (type == MapBackgroundProviderType.NONE || metadata.tileUrlTemplate().isBlank()) {
                return none();
            }
            int min = Math.max(0, metadata.minZoom());
            int max = Math.max(min, metadata.maxZoom());
            int fallbackDefault = Math.max(min, Math.min(max, metadata.defaultZoom()));
            int resolvedZoom = zoomPreference == Integer.MIN_VALUE ? fallbackDefault : Math.max(min, Math.min(max, zoomPreference));
            return new MapBackgroundState(
                type,
                true,
                enabledPreference,
                metadata.tileUrlTemplate(),
                metadata.worldName(),
                min,
                max,
                resolvedZoom,
                Math.max(16, metadata.tileBlockSpan())
            );
        }

        public MapBackgroundState withEnabled(boolean enabled) {
            if (!available) {
                return none();
            }
            return new MapBackgroundState(providerType, available, enabled, tileUrlTemplate, worldName,
                minZoom, maxZoom, zoom, tileBlockSpan);
        }

        public MapBackgroundState withZoom(int zoom) {
            if (!available) {
                return none();
            }
            int clamped = Math.max(minZoom, Math.min(maxZoom, zoom));
            return new MapBackgroundState(providerType, available, enabled, tileUrlTemplate, worldName,
                minZoom, maxZoom, clamped, tileBlockSpan);
        }
    }
}
