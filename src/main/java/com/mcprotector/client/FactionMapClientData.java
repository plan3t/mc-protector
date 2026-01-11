package com.mcprotector.client;

import com.mcprotector.network.FactionClaimMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
        snapshot = new MapSnapshot(packet.centerChunkX(), packet.centerChunkZ(), packet.radius(), claims);
    }

    public static MapSnapshot getSnapshot() {
        return snapshot;
    }

    public static void requestUpdate() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientPacketDistributor.sendToServer(new com.mcprotector.network.FactionClaimMapRequestPacket());
        }
    }

    public record MapSnapshot(int centerChunkX, int centerChunkZ, int radius,
                              Map<Long, FactionClaimMapPacket.ClaimEntry> claims) {
        public static MapSnapshot empty() {
            return new MapSnapshot(0, 0, 0, new HashMap<>());
        }
    }
}
