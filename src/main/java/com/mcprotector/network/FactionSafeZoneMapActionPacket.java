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

public class FactionSafeZoneMapActionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionSafeZoneMapActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McProtectorMod.MOD_ID, "faction_safezone_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionSafeZoneMapActionPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionSafeZoneMapActionPacket::write, FactionSafeZoneMapActionPacket::decode);
    private final List<ChunkPos> chunks;
    private final String factionName;
    private final ActionType action;

    public FactionSafeZoneMapActionPacket(List<ChunkPos> chunks, String factionName, ActionType action) {
        this.chunks = chunks;
        this.factionName = factionName;
        this.action = action;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(chunks.size());
        for (ChunkPos chunk : chunks) {
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
        }
        buffer.writeUtf(factionName == null ? "" : factionName);
        buffer.writeEnum(action);
    }

    public static FactionSafeZoneMapActionPacket decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }
        String factionName = buffer.readUtf();
        ActionType action = buffer.readEnum(ActionType.class);
        return new FactionSafeZoneMapActionPacket(chunks, factionName, action);
    }

    public static void handle(FactionSafeZoneMapActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            if (packet.chunks.size() > 9) {
                player.sendSystemMessage(Component.literal("Safe zone selection is limited to 9 chunks."));
                return;
            }
            try {
                switch (packet.action) {
                    case CLAIM -> FactionService.claimSafeZoneChunks(player.createCommandSourceStack(), packet.chunks, packet.factionName);
                    case UNCLAIM -> FactionService.unclaimSafeZoneChunks(player.createCommandSourceStack(), packet.chunks);
                }
            } catch (Exception ex) {
                player.sendSystemMessage(Component.literal("Failed to update safe zones: " + ex.getMessage()));
            }
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ActionType {
        CLAIM,
        UNCLAIM
    }
}
