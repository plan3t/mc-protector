package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionRelation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FactionClaimMapPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionClaimMapPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McProtectorMod.MOD_ID, "faction_claim_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionClaimMapPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionClaimMapPacket::write, FactionClaimMapPacket::decode);
    private static final int SAFE_ZONE_COLOR = 0xFFF9A825;
    private static final int PERSONAL_CLAIM_COLOR = 0xFF9C27B0;
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
        int viewDistance = player.getServer().getPlayerList().getViewDistance();
        radius = Math.max(radius, viewDistance);
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
            int color = resolveClaimColor(false, false, relation);
            entries.add(new ClaimEntry(chunkPos.x, chunkPos.z, factionName, relation, false, false, color));
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
            int color = resolveClaimColor(true, false, relation);
            entries.add(new ClaimEntry(chunkPos.x, chunkPos.z, factionName, relation, true, false, color));
        }
        var server = player.getServer();
        for (var entry : data.getPersonalClaims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            if (!fullSync
                && (Math.abs(chunkPos.x - center.x) > radius || Math.abs(chunkPos.z - center.z) > radius)) {
                continue;
            }
            UUID ownerId = entry.getValue();
            String ownerName = resolveName(server, ownerId);
            String relation = ownerId.equals(player.getUUID()) ? "OWN" : "PERSONAL";
            int color = resolveClaimColor(false, true, relation);
            entries.add(new ClaimEntry(chunkPos.x, chunkPos.z, ownerName, relation, false, true, color));
        }
        return new FactionClaimMapPacket(center.x, center.z, radius, entries);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(centerChunkX);
        buffer.writeInt(centerChunkZ);
        buffer.writeInt(radius);
        buffer.writeVarInt(claims.size());
        for (ClaimEntry entry : claims) {
            buffer.writeInt(entry.chunkX());
            buffer.writeInt(entry.chunkZ());
            buffer.writeUtf(entry.factionName());
            buffer.writeUtf(entry.relation());
            buffer.writeBoolean(entry.safeZone());
            buffer.writeBoolean(entry.personal());
            buffer.writeInt(entry.color());
        }
    }

    public static FactionClaimMapPacket decode(RegistryFriendlyByteBuf buffer) {
        int centerChunkX = buffer.readInt();
        int centerChunkZ = buffer.readInt();
        int radius = buffer.readInt();
        int claimCount = buffer.readVarInt();
        List<ClaimEntry> claims = new ArrayList<>();
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimEntry(buffer.readInt(), buffer.readInt(), buffer.readUtf(), buffer.readUtf(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readInt()));
        }
        return new FactionClaimMapPacket(centerChunkX, centerChunkZ, radius, claims);
    }

    public static void handle(FactionClaimMapPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketDispatcher.handleClaimMap(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    private static String resolveName(net.minecraft.server.MinecraftServer server, UUID playerId) {
        if (server == null) {
            return playerId.toString();
        }
        var player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return server.getProfileCache()
            .get(playerId)
            .map(profile -> profile.getName())
            .orElse(playerId.toString());
    }

    private static int resolveClaimColor(boolean safeZone, boolean personal, String relation) {
        if (safeZone) {
            return SAFE_ZONE_COLOR;
        }
        if (personal) {
            return PERSONAL_CLAIM_COLOR;
        }
        return switch (relation) {
            case "OWN" -> 0xFF4CAF50;
            case "ALLY" -> 0xFF4FC3F7;
            case "WAR" -> 0xFFEF5350;
            default -> 0xFF8D8D8D;
        };
    }

    public record ClaimEntry(int chunkX, int chunkZ, String factionName, String relation, boolean safeZone,
                             boolean personal, int color) {
    }
}
