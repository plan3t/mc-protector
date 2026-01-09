package com.mcprotector.client;

import com.mcprotector.network.FactionStatePacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public final class FactionClientData {
    private static FactionSnapshot snapshot = FactionSnapshot.empty();

    private FactionClientData() {
    }

    public static void applyState(FactionStatePacket packet) {
        snapshot = new FactionSnapshot(
            packet.inFaction(),
            packet.factionName(),
            packet.roleName(),
            packet.members(),
            packet.invites(),
            packet.permissions(),
            packet.relations(),
            packet.claims(),
            packet.pendingInviteFaction(),
            packet.claimCount(),
            packet.maxClaims(),
            packet.protectionTier(),
            packet.factionLevel()
        );
    }

    public static FactionSnapshot getSnapshot() {
        return snapshot;
    }

    public static void requestUpdate() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            com.mcprotector.network.NetworkHandler.CHANNEL.sendToServer(new com.mcprotector.network.FactionStateRequestPacket());
        }
    }

    public record FactionSnapshot(boolean inFaction, String factionName, String roleName,
                                 List<FactionStatePacket.MemberEntry> members,
                                 List<FactionStatePacket.InviteEntry> invites,
                                 List<FactionStatePacket.PermissionEntry> permissions,
                                 List<FactionStatePacket.RelationEntry> relations,
                                 List<FactionStatePacket.ClaimEntry> claims,
                                 String pendingInviteFaction,
                                 int claimCount,
                                 int maxClaims,
                                 String protectionTier,
                                 int factionLevel) {
        public static FactionSnapshot empty() {
            return new FactionSnapshot(false, "", "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), "", 0, 0, "", 0);
        }
    }
}
