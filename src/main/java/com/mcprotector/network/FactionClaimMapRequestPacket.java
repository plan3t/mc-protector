package com.mcprotector.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FactionClaimMapRequestPacket {
    public static void encode(FactionClaimMapRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static FactionClaimMapRequestPacket decode(FriendlyByteBuf buffer) {
        return new FactionClaimMapRequestPacket();
    }

    public static void handle(FactionClaimMapRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player)));
        ctx.setPacketHandled(true);
    }
}
