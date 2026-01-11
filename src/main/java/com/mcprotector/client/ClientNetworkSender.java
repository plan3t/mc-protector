package com.mcprotector.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ClientNetworkSender {
    private ClientNetworkSender() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            client.getConnection().send(payload);
        }
    }
}
