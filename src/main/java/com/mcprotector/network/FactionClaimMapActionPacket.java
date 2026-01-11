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

public class FactionClaimMapActionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionClaimMapActionPacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(McProtectorMod.MOD_ID, "faction_claim_map_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionClaimMapActionPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionClaimMapActionPacket::write, FactionClaimMapActionPacket::decode);
    private final int chunkX;
    private final int chunkZ;
    private final ActionType action;

    public FactionClaimMapActionPacket(int chunkX, int chunkZ, ActionType action) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.action = action;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeEnum(action);
    }

    public static FactionClaimMapActionPacket decode(RegistryFriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        ActionType action = buffer.readEnum(ActionType.class);
        return new FactionClaimMapActionPacket(chunkX, chunkZ, action);
    }

    public static void handle(FactionClaimMapActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ActionType {
        CLAIM,
        UNCLAIM,
        OVERTAKE
    }
}
