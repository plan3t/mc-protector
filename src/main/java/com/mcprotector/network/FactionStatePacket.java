package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.data.FactionRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
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

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(inFaction);
        buffer.writeUtf(factionName);
        buffer.writeUtf(roleName);
        buffer.writeUtf(pendingInviteFaction);
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
        buffer.writeVarInt(relations.size());
        for (RelationEntry entry : relations) {
            buffer.writeUUID(entry.factionId());
            buffer.writeUtf(entry.factionName());
            buffer.writeUtf(entry.relation());
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
    }

    public static FactionStatePacket decode(RegistryFriendlyByteBuf buffer) {
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
