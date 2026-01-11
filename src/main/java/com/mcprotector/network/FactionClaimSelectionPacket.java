package com.mcprotector.network;

import com.mcprotector.service.FactionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FactionClaimSelectionPacket {
    private final List<ChunkPos> chunks;
    private final ClaimType claimType;
    private final String factionName;

    public FactionClaimSelectionPacket(List<ChunkPos> chunks, ClaimType claimType, String factionName) {
        this.chunks = chunks;
        this.claimType = claimType;
        this.factionName = factionName;
    }

    public static void encode(FactionClaimSelectionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.chunks.size());
        for (ChunkPos chunk : packet.chunks) {
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
        }
        buffer.writeEnum(packet.claimType);
        buffer.writeUtf(packet.factionName == null ? "" : packet.factionName);
    }

    public static FactionClaimSelectionPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }
        ClaimType claimType = buffer.readEnum(ClaimType.class);
        String factionName = buffer.readUtf();
        return new FactionClaimSelectionPacket(chunks, claimType, factionName);
    }

    public static void handle(FactionClaimSelectionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            if (packet.chunks.size() > 9) {
                player.sendSystemMessage(Component.literal("Chunk selection is limited to 9 chunks."));
                return;
            }
            try {
                switch (packet.claimType) {
                    case FACTION -> FactionService.toggleFactionChunks(player.createCommandSourceStack(), packet.chunks);
                    case PERSONAL -> FactionService.togglePersonalChunks(player.createCommandSourceStack(), packet.chunks);
                    case SAFEZONE -> FactionService.toggleSafeZoneChunks(player.createCommandSourceStack(), packet.chunks, packet.factionName);
                }
            } catch (Exception ex) {
                player.sendSystemMessage(Component.literal("Failed to update claims: " + ex.getMessage()));
            }
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        });
        ctx.setPacketHandled(true);
    }

    public enum ClaimType {
        FACTION,
        PERSONAL,
        SAFEZONE
    }
}
