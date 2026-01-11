package com.mcprotector.network;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionRelation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class FactionClaimMapPacket {
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radius;
    private final List<ClaimEntry> claims;

    public FactionClaimMapPacket(int centerChunkX, int centerChunkZ, int radius, List<ClaimEntry> claims) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = radius;
        this.claims = claims;
    }

    public static FactionClaimMapPacket fromPlayer(ServerPlayer player) {
        FactionData data = FactionData.get(player.serverLevel());
        ChunkPos center = new ChunkPos(player.blockPosition());
        int radius = Math.max(0, FactionConfig.SERVER.claimMapRadiusChunks.get());
        boolean fullSync = FactionConfig.SERVER.claimMapFullSync.get();
        Optional<UUID> playerFactionId = data.getFactionIdByPlayer(player.getUUID());
        List<ClaimEntry> entries = new ArrayList<>();
        for (var entry : data.getClaims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            if (!fullSync
                && (Math.abs(chunkPos.x - center.x) > radius || Math.abs(chunkPos.z - center.z) > radius)) {
                continue;
            }
            Optional<Faction> faction = data.getFaction(entry.getValue());
            String factionName = faction.map(Faction::getName).orElse("Unknown");
            String relation = playerFactionId
                .map(id -> id.equals(entry.getValue()) ? "OWN" : data.getRelation(id, entry.getValue()).name())
                .orElse(FactionRelation.NEUTRAL.name());
            entries.add(new ClaimEntry(chunkPos.x, chunkPos.z, factionName, relation, false));
        }
        for (var entry : data.getSafeZoneClaims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            if (!fullSync
                && (Math.abs(chunkPos.x - center.x) > radius || Math.abs(chunkPos.z - center.z) > radius)) {
                continue;
            }
            Optional<Faction> faction = data.getFaction(entry.getValue());
            String factionName = faction.map(Faction::getName).orElse("Unknown");
            String relation = playerFactionId
                .map(id -> id.equals(entry.getValue()) ? "OWN" : data.getRelation(id, entry.getValue()).name())
                .orElse(FactionRelation.NEUTRAL.name());
            entries.add(new ClaimEntry(chunkPos.x, chunkPos.z, factionName, relation, true));
        }
        return new FactionClaimMapPacket(center.x, center.z, radius, entries);
    }

    public static void encode(FactionClaimMapPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.centerChunkX);
        buffer.writeInt(packet.centerChunkZ);
        buffer.writeInt(packet.radius);
        buffer.writeVarInt(packet.claims.size());
        for (ClaimEntry entry : packet.claims) {
            buffer.writeInt(entry.chunkX());
            buffer.writeInt(entry.chunkZ());
            buffer.writeUtf(entry.factionName());
            buffer.writeUtf(entry.relation());
            buffer.writeBoolean(entry.safeZone());
        }
    }

    public static FactionClaimMapPacket decode(FriendlyByteBuf buffer) {
        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int radius = buffer.readInt();
        int claimCount = buffer.readVarInt();
        List<ClaimEntry> claims = new ArrayList<>();
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimEntry(buffer.readInt(), buffer.readInt(), buffer.readUtf(), buffer.readUtf(), buffer.readBoolean()));
        }
        return new FactionClaimMapPacket(centerChunkX, centerChunkZ, radius, claims);
    }

    public static void handle(FactionClaimMapPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.mcprotector.client.ClientPacketHandler.handleClaimMap(packet);
        }));
        ctx.setPacketHandled(true);
    }

    public int centerChunkX() {
        return centerChunkX;
    }

    public int centerChunkZ() {
        return centerChunkZ;
    }

    public int radius() {
        return radius;
    }

    public List<ClaimEntry> claims() {
        return claims;
    }

    public record ClaimEntry(int chunkX, int chunkZ, String factionName, String relation, boolean safeZone) {
    }
}
