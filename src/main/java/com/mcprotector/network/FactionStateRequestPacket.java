package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FactionStateRequestPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionStateRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McProtectorMod.MOD_ID, "faction_state_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionStateRequestPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionStateRequestPacket::write, FactionStateRequestPacket::decode);

    public FactionStateRequestPacket() {
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    public static FactionStateRequestPacket decode(RegistryFriendlyByteBuf buffer) {
        return new FactionStateRequestPacket();
    }

    public static void handle(FactionStateRequestPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
