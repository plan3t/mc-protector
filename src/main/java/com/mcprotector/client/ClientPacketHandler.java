package com.mcprotector.client;

import com.mcprotector.network.FactionStatePacket;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleFactionState(FactionStatePacket packet) {
        FactionClientData.applyState(packet);
    }

    public static void handleClaimMap(com.mcprotector.network.FactionClaimMapPacket packet) {
        FactionMapClientData.applyMap(packet);
    }
}
