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

public class FactionSafeZoneMapActionPacket {
    private final List<ChunkPos> chunks;
    private final String factionName;
    private final ActionType action;

    public FactionSafeZoneMapActionPacket(List<ChunkPos> chunks, String factionName, ActionType action) {
        this.chunks = chunks;
        this.factionName = factionName;
        this.action = action;
    }

    public static void encode(FactionSafeZoneMapActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.chunks.size());
        for (ChunkPos chunk : packet.chunks) {
            buffer.writeInt(chunk.x);
            buffer.writeInt(chunk.z);
        }
        buffer.writeUtf(packet.factionName == null ? "" : packet.factionName);
        buffer.writeEnum(packet.action);
    }

    public static FactionSafeZoneMapActionPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }
        String factionName = buffer.readUtf();
        ActionType action = buffer.readEnum(ActionType.class);
        return new FactionSafeZoneMapActionPacket(chunks, factionName, action);
    }

    public static void handle(FactionSafeZoneMapActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
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
        ctx.setPacketHandled(true);
    }

    public enum ActionType {
        CLAIM,
        UNCLAIM
    }
}
