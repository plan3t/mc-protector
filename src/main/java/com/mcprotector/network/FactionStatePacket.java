package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FactionStatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionStatePacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McProtectorMod.MOD_ID, "faction_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionStatePacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionStatePacket::write, FactionStatePacket::decode);
    private final boolean inFaction;
    private final String factionName;
    private final String roleName;
    private final List<RoleEntry> roles;
    private final List<MemberEntry> members;
    private final List<InviteEntry> invites;
    private final List<PermissionEntry> permissions;
    private final List<RelationPermissionEntry> relationPermissions;
    private final List<RelationEntry> relations;
    private final List<FactionListEntry> factionList;
    private final List<String> rules;
    private final List<ClaimEntry> claims;
    private final String pendingInviteFaction;
    private final int claimCount;
    private final int maxClaims;
    private final String protectionTier;
    private final int factionLevel;
    private final boolean borderEnabled;

    public FactionStatePacket(boolean inFaction, String factionName, String roleName, List<RoleEntry> roles,
                              List<MemberEntry> members, List<InviteEntry> invites, List<PermissionEntry> permissions,
                              List<RelationPermissionEntry> relationPermissions, List<RelationEntry> relations,
                              List<FactionListEntry> factionList, List<String> rules, List<ClaimEntry> claims,
                              String pendingInviteFaction, int claimCount, int maxClaims, String protectionTier,
                              int factionLevel, boolean borderEnabled) {
        this.inFaction = inFaction;
        this.factionName = factionName;
        this.roleName = roleName;
        this.roles = roles;
        this.members = members;
        this.invites = invites;
        this.permissions = permissions;
        this.relationPermissions = relationPermissions;
        this.relations = relations;
        this.factionList = factionList;
        this.rules = rules;
        this.claims = claims;
        this.pendingInviteFaction = pendingInviteFaction;
        this.claimCount = claimCount;
        this.maxClaims = maxClaims;
        this.protectionTier = protectionTier;
        this.factionLevel = factionLevel;
        this.borderEnabled = borderEnabled;
    }

    public static FactionStatePacket fromPlayer(ServerPlayer player) {
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        List<FactionListEntry> factionList = buildFactionList(data, faction);
        if (faction.isEmpty()) {
            String inviteFactionName = data.getInvite(player.getUUID())
                .flatMap(invite -> data.getFaction(invite.factionId()))
                .map(Faction::getName)
                .orElse("");
            boolean borderEnabled = data.isBorderEnabled(player.getUUID());
            return new FactionStatePacket(false, "", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                factionList, List.of(), List.of(), inviteFactionName, 0, 0, "", 0, borderEnabled);
        }
        Faction factionData = faction.get();
        MinecraftServer server = player.getServer();
        List<RoleEntry> roles = new ArrayList<>();
        for (Map.Entry<String, String> entry : factionData.getRoleDisplayNames().entrySet()) {
            roles.add(new RoleEntry(entry.getKey(), entry.getValue()));
        }
        List<MemberEntry> members = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : factionData.getMembers().entrySet()) {
            String name = resolveName(server, entry.getKey());
            members.add(new MemberEntry(entry.getKey(), name, entry.getValue()));
        }
        List<InviteEntry> invites = new ArrayList<>();
        for (Map.Entry<UUID, FactionData.FactionInvite> entry : data.getInvitesForFaction(factionData.getId()).entrySet()) {
            String name = resolveName(server, entry.getKey());
            invites.add(new InviteEntry(entry.getKey(), name, entry.getValue().expiresAt()));
        }
        List<PermissionEntry> permissions = new ArrayList<>();
        for (Map.Entry<String, EnumSet<FactionPermission>> entry : factionData.getPermissions().entrySet()) {
            List<String> perms = entry.getValue().stream().map(Enum::name).toList();
            permissions.add(new PermissionEntry(entry.getKey(), perms));
        }
        List<RelationPermissionEntry> relationPermissions = new ArrayList<>();
        for (Map.Entry<FactionRelation, EnumSet<FactionPermission>> entry : factionData.getRelationPermissions().entrySet()) {
            List<String> perms = entry.getValue().stream().map(Enum::name).toList();
            relationPermissions.add(new RelationPermissionEntry(entry.getKey().name(), perms));
        }
        List<RelationEntry> relations = new ArrayList<>();
        for (Map.Entry<UUID, Faction> entry : data.getFactions().entrySet()) {
            if (entry.getKey().equals(factionData.getId())) {
                continue;
            }
            FactionRelation relation = data.getRelation(factionData.getId(), entry.getKey());
            if (relation != FactionRelation.NEUTRAL) {
                relations.add(new RelationEntry(entry.getKey(), entry.getValue().getName(), relation.name()));
            }
        }
        data.getOverlord(factionData.getId())
            .flatMap(data::getFaction)
            .ifPresent(overlord -> relations.add(new RelationEntry(overlord.getId(), overlord.getName(), "OVERLORD")));
        for (Map.Entry<UUID, FactionData.VassalContract> entry : data.getVassalContracts().entrySet()) {
            if (!entry.getValue().overlordId().equals(factionData.getId())) {
                continue;
            }
            data.getFaction(entry.getKey())
                .ifPresent(vassal -> relations.add(new RelationEntry(vassal.getId(), vassal.getName(), "VASSAL")));
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
        String roleName = Optional.ofNullable(factionData.getRole(player.getUUID())).orElse("");
        int claimCount = data.getClaimCount(factionData.getId());
        int maxClaims = data.getMaxClaims(factionData.getId());
        int factionLevel = data.getFactionLevel(factionData.getId());
        String protectionTier = factionData.getProtectionTier().name();
        boolean borderEnabled = data.isBorderEnabled(player.getUUID());
        return new FactionStatePacket(true, factionData.getName(), roleName, roles, members, invites, permissions, relationPermissions,
            relations, factionList, factionData.getRules(), claims, "", claimCount, maxClaims, protectionTier,
            factionLevel, borderEnabled);
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

    private static List<FactionListEntry> buildFactionList(FactionData data, Optional<Faction> playerFaction) {
        List<FactionListEntry> entries = new ArrayList<>();
        Optional<UUID> playerFactionId = playerFaction.map(Faction::getId);
        for (Map.Entry<UUID, Faction> entry : data.getFactions().entrySet()) {
            Faction faction = entry.getValue();
            String relation = "NEUTRAL";
            if (playerFactionId.isPresent()) {
                if (entry.getKey().equals(playerFactionId.get())) {
                    relation = "OWN";
                } else {
                    relation = data.getRelation(playerFactionId.get(), entry.getKey()).name();
                }
            }
            int color = faction.getColorRgb();
            entries.add(new FactionListEntry(entry.getKey(), faction.getName(), faction.getMemberCount(), relation, color));
        }
        entries.sort(Comparator.comparing(FactionListEntry::factionName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(inFaction);
        buffer.writeUtf(factionName);
        buffer.writeUtf(roleName);
        buffer.writeUtf(pendingInviteFaction);
        buffer.writeVarInt(roles.size());
        for (RoleEntry entry : roles) {
            buffer.writeUtf(entry.name());
            buffer.writeUtf(entry.displayName());
        }
        buffer.writeVarInt(members.size());
        for (MemberEntry entry : members) {
            buffer.writeUUID(entry.playerId());
            buffer.writeUtf(entry.name());
            buffer.writeUtf(entry.role());
        }
        buffer.writeVarInt(invites.size());
        for (InviteEntry entry : invites) {
            buffer.writeUUID(entry.playerId());
            buffer.writeUtf(entry.name());
            buffer.writeLong(entry.expiresAt());
        }
        buffer.writeVarInt(permissions.size());
        for (PermissionEntry entry : permissions) {
            buffer.writeUtf(entry.role());
            buffer.writeVarInt(entry.permissions().size());
            for (String perm : entry.permissions()) {
                buffer.writeUtf(perm);
            }
        }
        buffer.writeVarInt(relationPermissions.size());
        for (RelationPermissionEntry entry : relationPermissions) {
            buffer.writeUtf(entry.relation());
            buffer.writeVarInt(entry.permissions().size());
            for (String perm : entry.permissions()) {
                buffer.writeUtf(perm);
            }
        }
        buffer.writeVarInt(relations.size());
        for (RelationEntry entry : relations) {
            buffer.writeUUID(entry.factionId());
            buffer.writeUtf(entry.factionName());
            buffer.writeUtf(entry.relation());
        }
        buffer.writeVarInt(factionList.size());
        for (FactionListEntry entry : factionList) {
            buffer.writeUUID(entry.factionId());
            buffer.writeUtf(entry.factionName());
            buffer.writeVarInt(entry.memberCount());
            buffer.writeUtf(entry.relation());
            buffer.writeInt(entry.color());
        }
        buffer.writeVarInt(rules.size());
        for (String rule : rules) {
            buffer.writeUtf(rule);
        }
        buffer.writeVarInt(claims.size());
        for (ClaimEntry entry : claims) {
            buffer.writeInt(entry.chunkX());
            buffer.writeInt(entry.chunkZ());
        }
        buffer.writeVarInt(claimCount);
        buffer.writeVarInt(maxClaims);
        buffer.writeUtf(protectionTier);
        buffer.writeVarInt(factionLevel);
        buffer.writeBoolean(borderEnabled);
    }

    public static FactionStatePacket decode(RegistryFriendlyByteBuf buffer) {
        boolean inFaction = buffer.readBoolean();
        String factionName = buffer.readUtf();
        String roleName = buffer.readUtf();
        String pendingInviteFaction = buffer.readUtf();
        int roleCount = buffer.readVarInt();
        List<RoleEntry> roles = new ArrayList<>();
        for (int i = 0; i < roleCount; i++) {
            roles.add(new RoleEntry(buffer.readUtf(), buffer.readUtf()));
        }
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
        int relationPermCount = buffer.readVarInt();
        List<RelationPermissionEntry> relationPermissions = new ArrayList<>();
        for (int i = 0; i < relationPermCount; i++) {
            String relation = buffer.readUtf();
            int permCount = buffer.readVarInt();
            List<String> perms = new ArrayList<>();
            for (int j = 0; j < permCount; j++) {
                perms.add(buffer.readUtf());
            }
            relationPermissions.add(new RelationPermissionEntry(relation, perms));
        }
        int relationCount = buffer.readVarInt();
        List<RelationEntry> relations = new ArrayList<>();
        for (int i = 0; i < relationCount; i++) {
            relations.add(new RelationEntry(buffer.readUUID(), buffer.readUtf(), buffer.readUtf()));
        }
        int factionCount = buffer.readVarInt();
        List<FactionListEntry> factionList = new ArrayList<>();
        for (int i = 0; i < factionCount; i++) {
            factionList.add(new FactionListEntry(buffer.readUUID(), buffer.readUtf(), buffer.readVarInt(), buffer.readUtf(), buffer.readInt()));
        }
        int ruleCount = buffer.readVarInt();
        List<String> rules = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            rules.add(buffer.readUtf());
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
        boolean borderEnabled = buffer.readBoolean();
        return new FactionStatePacket(inFaction, factionName, roleName, roles, members, invites, permissions, relationPermissions,
            relations, factionList, rules, claims, pendingInviteFaction, factionClaimCount, maxClaims, protectionTier,
            factionLevel, borderEnabled);
    }

    public static void handle(FactionStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketDispatcher.handleFactionState(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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

    public List<RoleEntry> roles() {
        return roles;
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

    public List<RelationPermissionEntry> relationPermissions() {
        return relationPermissions;
    }

    public List<RelationEntry> relations() {
        return relations;
    }

    public List<FactionListEntry> factionList() {
        return factionList;
    }

    public List<String> rules() {
        return rules;
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

    public boolean borderEnabled() {
        return borderEnabled;
    }

    public record RoleEntry(String name, String displayName) {
    }

    public record MemberEntry(UUID playerId, String name, String role) {
    }

    public record InviteEntry(UUID playerId, String name, long expiresAt) {
    }

    public record PermissionEntry(String role, List<String> permissions) {
    }

    public record RelationPermissionEntry(String relation, List<String> permissions) {
    }

    public record RelationEntry(UUID factionId, String factionName, String relation) {
    }

    public record FactionListEntry(UUID factionId, String factionName, int memberCount, String relation, int color) {
    }

    public record ClaimEntry(int chunkX, int chunkZ) {
    }
}
