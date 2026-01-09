package com.mcprotector.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FactionStateRequestPacket {
    public FactionStateRequestPacket() {
    }

    public static void encode(FactionStateRequestPacket packet, FriendlyByteBuf buffer) {
    }

    public static FactionStateRequestPacket decode(FriendlyByteBuf buffer) {
        return new FactionStateRequestPacket();
    }

    public static void handle(FactionStateRequestPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
        }
        ctx.setPacketHandled(true);
    }
}
