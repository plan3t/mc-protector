package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(ResourceLocation.fromNamespaceAndPath(McProtectorMod.MOD_ID, "main"))
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .simpleChannel();

    private static int packetId;

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(FactionStateRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactionStateRequestPacket::encode)
            .decoder(FactionStateRequestPacket::decode)
            .consumerMainThread(FactionStateRequestPacket::handle)
            .add();
        CHANNEL.messageBuilder(FactionStatePacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(FactionStatePacket::encode)
            .decoder(FactionStatePacket::decode)
            .consumerMainThread(FactionStatePacket::handle)
            .add();
        CHANNEL.messageBuilder(FactionActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactionActionPacket::encode)
            .decoder(FactionActionPacket::decode)
            .consumerMainThread(FactionActionPacket::handle)
            .add();
        CHANNEL.messageBuilder(FactionClaimMapRequestPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactionClaimMapRequestPacket::encode)
            .decoder(FactionClaimMapRequestPacket::decode)
            .consumerMainThread(FactionClaimMapRequestPacket::handle)
            .add();
        CHANNEL.messageBuilder(FactionClaimMapPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
            .encoder(FactionClaimMapPacket::encode)
            .decoder(FactionClaimMapPacket::decode)
            .consumerMainThread(FactionClaimMapPacket::handle)
            .add();
        CHANNEL.messageBuilder(FactionClaimMapActionPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
            .encoder(FactionClaimMapActionPacket::encode)
            .decoder(FactionClaimMapActionPacket::decode)
            .consumerMainThread(FactionClaimMapActionPacket::handle)
            .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static int nextId() {
        return packetId++;
    }
}
