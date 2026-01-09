package com.mcprotector.network;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.data.FactionRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class FactionStatePacket {
    private final boolean inFaction;
    private final String factionName;
    private final String roleName;
    private final List<MemberEntry> members;
    private final List<InviteEntry> invites;
    private final List<PermissionEntry> permissions;
    private final List<RelationEntry> relations;
    private final List<ClaimEntry> claims;
    private final String pendingInviteFaction;
    private final int claimCount;
    private final int maxClaims;
    private final String protectionTier;
    private final int factionLevel;

    public FactionStatePacket(boolean inFaction, String factionName, String roleName, List<MemberEntry> members,
                              List<InviteEntry> invites, List<PermissionEntry> permissions, List<RelationEntry> relations,
                              List<ClaimEntry> claims, String pendingInviteFaction, int claimCount, int maxClaims,
                              String protectionTier, int factionLevel) {
        this.inFaction = inFaction;
        this.factionName = factionName;
        this.roleName = roleName;
        this.members = members;
        this.invites = invites;
        this.permissions = permissions;
        this.relations = relations;
        this.claims = claims;
        this.pendingInviteFaction = pendingInviteFaction;
        this.claimCount = claimCount;
        this.maxClaims = maxClaims;
        this.protectionTier = protectionTier;
        this.factionLevel = factionLevel;
    }

    public static FactionStatePacket fromPlayer(ServerPlayer player) {
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            String inviteFactionName = data.getInvite(player.getUUID())
                .flatMap(invite -> data.getFaction(invite.factionId()))
                .map(Faction::getName)
                .orElse("");
            return new FactionStatePacket(false, "", "", List.of(), List.of(), List.of(), List.of(), List.of(),
                inviteFactionName, 0, 0, "", 0);
        }
        Faction factionData = faction.get();
        MinecraftServer server = player.getServer();
        List<MemberEntry> members = new ArrayList<>();
        for (Map.Entry<UUID, FactionRole> entry : factionData.getMembers().entrySet()) {
            String name = resolveName(server, entry.getKey());
            members.add(new MemberEntry(entry.getKey(), name, entry.getValue().name()));
        }
        List<InviteEntry> invites = new ArrayList<>();
        for (Map.Entry<UUID, FactionData.FactionInvite> entry : data.getInvitesForFaction(factionData.getId()).entrySet()) {
            String name = resolveName(server, entry.getKey());
            invites.add(new InviteEntry(entry.getKey(), name, entry.getValue().expiresAt()));
        }
        List<PermissionEntry> permissions = new ArrayList<>();
        for (Map.Entry<FactionRole, EnumSet<FactionPermission>> entry : factionData.getPermissions().entrySet()) {
            List<String> perms = entry.getValue().stream().map(Enum::name).toList();
            permissions.add(new PermissionEntry(entry.getKey().name(), perms));
        }
        List<RelationEntry> relations = new ArrayList<>();
        for (Map.Entry<UUID, Faction> entry : data.getFactions().entrySet()) {
            if (entry.getKey().equals(factionData.getId())) {
                continue;
            }
            FactionRelation relation = data.getRelation(factionData.getId(), entry.getKey());
            if (relation == FactionRelation.NEUTRAL) {
                continue;
            }
            relations.add(new RelationEntry(entry.getKey(), entry.getValue().getName(), relation.name()));
        }
        List<ClaimEntry> claims = new ArrayList<>();
        for (Map.Entry<Long, UUID> entry : data.getClaims().entrySet()) {
            if (!entry.getValue().equals(factionData.getId())) {
                continue;
            }
            long key = entry.getKey();
            int x = (int) key;
            int z = (int) (key >> 32);
            claims.add(new ClaimEntry(x, z));
        }
        String roleName = factionData.getRole(player.getUUID()).name();
        int claimCount = data.getClaimCount(factionData.getId());
        int maxClaims = data.getMaxClaims(factionData.getId());
        int factionLevel = data.getFactionLevel(factionData.getId());
        String protectionTier = factionData.getProtectionTier().name();
        return new FactionStatePacket(true, factionData.getName(), roleName, members, invites, permissions, relations, claims, "",
            claimCount, maxClaims, protectionTier, factionLevel);
    }

    private static String resolveName(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return server.getProfileCache()
            .get(playerId)
            .map(profile -> profile.getName())
            .orElse(playerId.toString());
    }

    public static void encode(FactionStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.inFaction);
        buffer.writeUtf(packet.factionName);
        buffer.writeUtf(packet.roleName);
        buffer.writeUtf(packet.pendingInviteFaction);
        buffer.writeVarInt(packet.members.size());
        for (MemberEntry entry : packet.members) {
            buffer.writeUUID(entry.playerId());
            buffer.writeUtf(entry.name());
            buffer.writeUtf(entry.role());
        }
        buffer.writeVarInt(packet.invites.size());
        for (InviteEntry entry : packet.invites) {
            buffer.writeUUID(entry.playerId());
            buffer.writeUtf(entry.name());
            buffer.writeLong(entry.expiresAt());
        }
        buffer.writeVarInt(packet.permissions.size());
        for (PermissionEntry entry : packet.permissions) {
            buffer.writeUtf(entry.role());
            buffer.writeVarInt(entry.permissions().size());
            for (String perm : entry.permissions()) {
                buffer.writeUtf(perm);
            }
        }
        buffer.writeVarInt(packet.relations.size());
        for (RelationEntry entry : packet.relations) {
            buffer.writeUUID(entry.factionId());
            buffer.writeUtf(entry.factionName());
            buffer.writeUtf(entry.relation());
        }
        buffer.writeVarInt(packet.claims.size());
        for (ClaimEntry entry : packet.claims) {
            buffer.writeInt(entry.chunkX());
            buffer.writeInt(entry.chunkZ());
        }
        buffer.writeVarInt(packet.claimCount);
        buffer.writeVarInt(packet.maxClaims);
        buffer.writeUtf(packet.protectionTier);
        buffer.writeVarInt(packet.factionLevel);
    }

    public static FactionStatePacket decode(FriendlyByteBuf buffer) {
        boolean inFaction = buffer.readBoolean();
        String factionName = buffer.readUtf();
        String roleName = buffer.readUtf();
        String pendingInviteFaction = buffer.readUtf();
        int memberCount = buffer.readVarInt();
        List<MemberEntry> members = new ArrayList<>();
        for (int i = 0; i < memberCount; i++) {
            members.add(new MemberEntry(buffer.readUUID(), buffer.readUtf(), buffer.readUtf()));
        }
        int inviteCount = buffer.readVarInt();
        List<InviteEntry> invites = new ArrayList<>();
        for (int i = 0; i < inviteCount; i++) {
            invites.add(new InviteEntry(buffer.readUUID(), buffer.readUtf(), buffer.readLong()));
        }
        int permissionCount = buffer.readVarInt();
        List<PermissionEntry> permissions = new ArrayList<>();
        for (int i = 0; i < permissionCount; i++) {
            String role = buffer.readUtf();
            int permCount = buffer.readVarInt();
            List<String> perms = new ArrayList<>();
            for (int j = 0; j < permCount; j++) {
                perms.add(buffer.readUtf());
            }
            permissions.add(new PermissionEntry(role, perms));
        }
        int relationCount = buffer.readVarInt();
        List<RelationEntry> relations = new ArrayList<>();
        for (int i = 0; i < relationCount; i++) {
            relations.add(new RelationEntry(buffer.readUUID(), buffer.readUtf(), buffer.readUtf()));
        }
        int claimCount = buffer.readVarInt();
        List<ClaimEntry> claims = new ArrayList<>();
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimEntry(buffer.readInt(), buffer.readInt()));
        }
        int factionClaimCount = buffer.readVarInt();
        int maxClaims = buffer.readVarInt();
        String protectionTier = buffer.readUtf();
        int factionLevel = buffer.readVarInt();
        return new FactionStatePacket(inFaction, factionName, roleName, members, invites, permissions, relations, claims,
            pendingInviteFaction, factionClaimCount, maxClaims, protectionTier, factionLevel);
    }

    public static void handle(FactionStatePacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.mcprotector.client.ClientPacketHandler.handleFactionState(packet);
        }));
        ctx.setPacketHandled(true);
    }

    public boolean inFaction() {
        return inFaction;
    }

    public String factionName() {
        return factionName;
    }

    public String roleName() {
        return roleName;
    }

    public List<MemberEntry> members() {
        return members;
    }

    public List<InviteEntry> invites() {
        return invites;
    }

    public List<PermissionEntry> permissions() {
        return permissions;
    }

    public List<RelationEntry> relations() {
        return relations;
    }

    public List<ClaimEntry> claims() {
        return claims;
    }

    public String pendingInviteFaction() {
        return pendingInviteFaction;
    }

    public int claimCount() {
        return claimCount;
    }

    public int maxClaims() {
        return maxClaims;
    }

    public String protectionTier() {
        return protectionTier;
    }

    public int factionLevel() {
        return factionLevel;
    }

    public record MemberEntry(UUID playerId, String name, String role) {
    }

    public record InviteEntry(UUID playerId, String name, long expiresAt) {
    }

    public record PermissionEntry(String role, List<String> permissions) {
    }

    public record RelationEntry(UUID factionId, String factionName, String relation) {
    }

    public record ClaimEntry(int chunkX, int chunkZ) {
    }
}
