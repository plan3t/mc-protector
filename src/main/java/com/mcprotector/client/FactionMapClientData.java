package com.mcprotector.client;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.network.FactionClaimMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import com.mcprotector.client.ClientNetworkSender;

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
        int radius = Math.max(0, FactionConfig.SERVER.claimMapRadiusChunks.get());
        snapshot = new MapSnapshot(packet.centerChunkX(), packet.centerChunkZ(), radius, claims);
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

    public record MapSnapshot(int centerChunkX, int centerChunkZ, int radius,
                              Map<Long, FactionClaimMapPacket.ClaimEntry> claims) {
        public static MapSnapshot empty() {
            return new MapSnapshot(0, 0, 0, new HashMap<>());
        }
    }
}
