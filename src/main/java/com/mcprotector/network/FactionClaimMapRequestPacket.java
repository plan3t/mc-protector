package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FactionClaimMapRequestPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionClaimMapRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(McProtectorMod.MOD_ID, "faction_claim_map_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionClaimMapRequestPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionClaimMapRequestPacket::write, FactionClaimMapRequestPacket::decode);

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    public static FactionClaimMapRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FactionClaimMapRequestPacket();
    }

    public static void handle(FactionClaimMapRequestPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
