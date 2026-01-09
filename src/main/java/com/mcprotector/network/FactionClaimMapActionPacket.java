package com.mcprotector.network;

import com.mcprotector.service.FactionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FactionClaimMapActionPacket {
    private final int chunkX;
    private final int chunkZ;
    private final ActionType action;

    public FactionClaimMapActionPacket(int chunkX, int chunkZ, ActionType action) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.action = action;
    }

    public static void encode(FactionClaimMapActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.chunkX);
        buffer.writeInt(packet.chunkZ);
        buffer.writeEnum(packet.action);
    }

    public static FactionClaimMapActionPacket decode(FriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        ActionType action = buffer.readEnum(ActionType.class);
        return new FactionClaimMapActionPacket(chunkX, chunkZ, action);
    }

    public static void handle(FactionClaimMapActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            ChunkPos chunkPos = new ChunkPos(packet.chunkX, packet.chunkZ);
            try {
                switch (packet.action) {
                    case CLAIM -> FactionService.claimChunk(player.createCommandSourceStack(), chunkPos);
                    case UNCLAIM -> FactionService.unclaimChunk(player.createCommandSourceStack(), chunkPos);
                    case OVERTAKE -> FactionService.overtakeChunk(player.createCommandSourceStack(), chunkPos);
                }
            } catch (Exception ex) {
                player.sendSystemMessage(Component.literal("Failed to update claim: " + ex.getMessage()));
            }
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        });
        ctx.setPacketHandled(true);
    }

    public enum ActionType {
        CLAIM,
        UNCLAIM,
        OVERTAKE
    }
}
