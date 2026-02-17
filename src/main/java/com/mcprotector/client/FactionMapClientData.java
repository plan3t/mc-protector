package com.mcprotector.client;

import com.mcprotector.network.FactionClaimMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;

public final class FactionMapClientData {
    private static MapSnapshot snapshot = MapSnapshot.empty();

    private FactionMapClientData() {
    }

    public static void applyMap(FactionClaimMapPacket packet) {
        Map<Long, FactionClaimMapPacket.ClaimEntry> claims = new HashMap<>();
        for (FactionClaimMapPacket.ClaimEntry entry : packet.claims()) {
            claims.put(new ChunkPos(entry.chunkX(), entry.chunkZ()).toLong(), entry);
        }
        int radius = Math.max(0, packet.radius());
        snapshot = new MapSnapshot(packet.centerChunkX(), packet.centerChunkZ(), radius, claims, MapBackgroundState.auto());
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
        snapshot = snapshot.withBackgroundState(state.withEnabled(enabled));
    }

    public static void adjustZoom(int delta) {
        // Background zoom controls were removed.
    }

    public record MapSnapshot(int centerChunkX, int centerChunkZ, int radius,
                              Map<Long, FactionClaimMapPacket.ClaimEntry> claims,
                              MapBackgroundState backgroundState) {
        public static MapSnapshot empty() {
            return new MapSnapshot(0, 0, 0, new HashMap<>(), MapBackgroundState.none());
        }

        public MapSnapshot withBackgroundState(MapBackgroundState newBackgroundState) {
            return new MapSnapshot(centerChunkX, centerChunkZ, radius, claims, newBackgroundState);
        }
    }

    public enum MapBackgroundProviderType {
        NONE,
        XAERO
    }

    public record MapBackgroundState(MapBackgroundProviderType providerType, boolean enabled) {
        public static MapBackgroundState none() {
            return new MapBackgroundState(MapBackgroundProviderType.NONE, false);
        }

        public static MapBackgroundState auto() {
            boolean xaeroInstalled = ModList.get().isLoaded("xaeroworldmap") || ModList.get().isLoaded("xaerominimap");
            return xaeroInstalled
                ? new MapBackgroundState(MapBackgroundProviderType.XAERO, true)
                : none();
        }

        public MapBackgroundState withEnabled(boolean value) {
            return new MapBackgroundState(providerType, value);
        }
    }
}
