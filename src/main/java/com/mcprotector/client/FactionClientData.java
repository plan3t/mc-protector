package com.mcprotector.client;

import com.mcprotector.network.FactionStatePacket;
import net.minecraft.client.Minecraft;
import com.mcprotector.client.ClientNetworkSender;

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
            packet.roles(),
            packet.members(),
            packet.invites(),
            packet.permissions(),
            packet.relationPermissions(),
            packet.relations(),
            packet.factionList(),
            packet.rules(),
            packet.claims(),
            packet.activityLogs(),
            packet.canViewActivityLogs(),
            packet.pendingInviteFaction(),
            packet.claimCount(),
            packet.maxClaims(),
            packet.protectionTier(),
            packet.factionLevel(),
            packet.motd(),
            packet.description(),
            packet.factionColor(),
            packet.bannerColor(),
            packet.borderEnabled()
        );
    }

    public static FactionSnapshot getSnapshot() {
        return snapshot;
    }

    public static void requestUpdate() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            ClientNetworkSender.sendToServer(new com.mcprotector.network.FactionStateRequestPacket());
        }
    }

    public record FactionSnapshot(boolean inFaction, String factionName, String roleName,
                                 List<FactionStatePacket.RoleEntry> roles,
                                 List<FactionStatePacket.MemberEntry> members,
                                 List<FactionStatePacket.InviteEntry> invites,
                                 List<FactionStatePacket.PermissionEntry> permissions,
                                 List<FactionStatePacket.RelationPermissionEntry> relationPermissions,
                                 List<FactionStatePacket.RelationEntry> relations,
                                 List<FactionStatePacket.FactionListEntry> factionList,
                                 List<String> rules,
                                 List<FactionStatePacket.ClaimEntry> claims,
                                 List<FactionStatePacket.ActivityLogEntry> activityLogs,
                                 boolean canViewActivityLogs,
                                 String pendingInviteFaction,
                                 int claimCount,
                                 int maxClaims,
                                 String protectionTier,
                                 int factionLevel,
                                 String motd,
                                 String description,
                                 String factionColor,
                                 String bannerColor,
                                 boolean borderEnabled) {
        public static FactionSnapshot empty() {
            return new FactionSnapshot(false, "", "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), false, "", 0, 0, "", 0, "", "", "", "", false);
        }
    }
}
