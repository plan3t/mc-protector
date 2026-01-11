package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import com.mcprotector.service.FactionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class FactionClaimSelectionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionClaimSelectionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McProtectorMod.MOD_ID, "faction_claim_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionClaimSelectionPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionClaimSelectionPacket::write, FactionClaimSelectionPacket::decode);
    private final List<ChunkPos> chunks;
    private final ClaimType claimType;
    private final String factionName;

    public FactionClaimSelectionPacket(List<ChunkPos> chunks, ClaimType claimType, String factionName) {
        this.chunks = chunks;
        this.claimType = claimType;
        this.factionName = factionName;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(chunks.size());
        for (ChunkPos chunk : chunks) {
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
        }
        buffer.writeEnum(claimType);
        buffer.writeUtf(factionName == null ? "" : factionName);
    }

    public static FactionClaimSelectionPacket decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }
        ClaimType claimType = buffer.readEnum(ClaimType.class);
        String factionName = buffer.readUtf();
        return new FactionClaimSelectionPacket(chunks, claimType, factionName);
    }

    public static void handle(FactionClaimSelectionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ClaimType {
        FACTION,
        PERSONAL,
        SAFEZONE
    }
}
