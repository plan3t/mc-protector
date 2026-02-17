package com.mcprotector.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
    private NetworkHandler() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(FactionStateRequestPacket.TYPE, FactionStateRequestPacket.STREAM_CODEC, FactionStateRequestPacket::handle);
        registrar.playToClient(FactionStatePacket.TYPE, FactionStatePacket.STREAM_CODEC, FactionStatePacket::handle);
        registrar.playToServer(FactionActionPacket.TYPE, FactionActionPacket.STREAM_CODEC, FactionActionPacket::handle);
        registrar.playToServer(FactionClaimMapRequestPacket.TYPE, FactionClaimMapRequestPacket.STREAM_CODEC, FactionClaimMapRequestPacket::handle);
        registrar.playToClient(FactionClaimMapPacket.TYPE, FactionClaimMapPacket.STREAM_CODEC, FactionClaimMapPacket::handle);
        registrar.playToServer(FactionClaimMapActionPacket.TYPE, FactionClaimMapActionPacket.STREAM_CODEC, FactionClaimMapActionPacket::handle);
        registrar.playToServer(FactionClaimSelectionPacket.TYPE, FactionClaimSelectionPacket.STREAM_CODEC, FactionClaimSelectionPacket::handle);
        registrar.playToServer(FactionSafeZoneMapActionPacket.TYPE, FactionSafeZoneMapActionPacket.STREAM_CODEC, FactionSafeZoneMapActionPacket::handle);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
