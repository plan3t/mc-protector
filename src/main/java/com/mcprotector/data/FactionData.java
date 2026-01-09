package com.mcprotector.data;

import com.mcprotector.config.FactionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FactionData extends SavedData {
    private static final String DATA_NAME = "mcprotector_factions";
    private static final int DATA_VERSION = 2;

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<Long, UUID> claims = new HashMap<>();
    private final Map<UUID, Map<UUID, FactionRelation>> relations = new HashMap<>();
    private final Map<UUID, FactionInvite> pendingInvites = new HashMap<>();
    private final Map<Long, Deque<FactionAccessLog>> accessLogs = new HashMap<>();

    public static FactionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FactionData::load, FactionData::new, DATA_NAME);
    }

    public static FactionData load(CompoundTag tag) {
        FactionData data = new FactionData();
        int dataVersion = tag.contains("DataVersion") ? tag.getInt("DataVersion") : 1;
        ListTag factionsTag = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (Tag entry : factionsTag) {
            CompoundTag factionTag = (CompoundTag) entry;
            UUID id = factionTag.getUUID("Id");
            UUID owner = factionTag.getUUID("Owner");
            String name = factionTag.getString("Name");
            Faction faction = new Faction(id, name, owner);
            if (factionTag.contains("Color")) {
                faction.setColorName(factionTag.getString("Color"));
            }
            if (factionTag.contains("Motd")) {
                faction.setMotd(factionTag.getString("Motd"));
            }
            if (factionTag.contains("Description")) {
                faction.setDescription(factionTag.getString("Description"));
            }
            if (factionTag.contains("BannerColor")) {
                faction.setBannerColor(factionTag.getString("BannerColor"));
            }
            if (factionTag.contains("ProtectionTier")) {
                faction.setProtectionTier(FactionProtectionTier.valueOf(factionTag.getString("ProtectionTier")));
            }
            CompoundTag ranksTag = factionTag.getCompound("RankNames");
            for (FactionRole role : FactionRole.values()) {
                if (ranksTag.contains(role.name())) {
                    faction.setRankName(role, ranksTag.getString(role.name()));
                }
            }
            if (factionTag.contains("TrustedPlayers")) {
                ListTag trustedTag = factionTag.getList("TrustedPlayers", Tag.TAG_STRING);
                for (Tag trustedEntry : trustedTag) {
                    faction.addTrustedPlayer(UUID.fromString(trustedEntry.getAsString()));
                }
            }
            ListTag members = factionTag.getList("Members", Tag.TAG_COMPOUND);
            for (Tag memberTag : members) {
                CompoundTag member = (CompoundTag) memberTag;
                UUID memberId = member.getUUID("Id");
                FactionRole role = FactionRole.valueOf(member.getString("Role"));
                faction.setRole(memberId, role);
                data.playerFaction.put(memberId, id);
            }
            CompoundTag permissionsTag = factionTag.getCompound("Permissions");
            for (FactionRole role : FactionRole.values()) {
                if (!permissionsTag.contains(role.name())) {
                    continue;
                }
                ListTag permsList = permissionsTag.getList(role.name(), Tag.TAG_STRING);
                EnumSet<FactionPermission> perms = EnumSet.noneOf(FactionPermission.class);
                for (Tag permTag : permsList) {
                    perms.add(FactionPermission.valueOf(permTag.getAsString()));
                }
                faction.setPermissions(role, perms);
            }
            data.factions.put(id, faction);
        }
        ListTag claimsTag = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (Tag claimEntry : claimsTag) {
            CompoundTag claim = (CompoundTag) claimEntry;
            long pos = claim.getLong("Chunk");
            UUID factionId = claim.getUUID("Faction");
            data.claims.put(pos, factionId);
        }
        ListTag relationsTag = tag.getList("Relations", Tag.TAG_COMPOUND);
        for (Tag relationEntry : relationsTag) {
            CompoundTag relation = (CompoundTag) relationEntry;
            UUID source = relation.getUUID("Source");
            UUID target = relation.getUUID("Target");
            FactionRelation relationType = FactionRelation.valueOf(relation.getString("Type"));
            data.relations.computeIfAbsent(source, key -> new HashMap<>()).put(target, relationType);
        }
        if (dataVersion >= 2 && tag.contains("Invites")) {
            ListTag invitesTag = tag.getList("Invites", Tag.TAG_COMPOUND);
            for (Tag inviteEntry : invitesTag) {
                CompoundTag invite = (CompoundTag) inviteEntry;
                UUID playerId = invite.getUUID("Player");
                UUID factionId = invite.getUUID("Faction");
                long expiresAt = invite.getLong("ExpiresAt");
                data.pendingInvites.put(playerId, new FactionInvite(factionId, expiresAt));
            }
        }
        return data;
    }

    public void restoreFromTag(CompoundTag tag) {
        FactionData loaded = load(tag);
        factions.clear();
        playerFaction.clear();
        claims.clear();
        relations.clear();
        pendingInvites.clear();
        accessLogs.clear();
        factions.putAll(loaded.factions);
        playerFaction.putAll(loaded.playerFaction);
        claims.putAll(loaded.claims);
        relations.putAll(loaded.relations);
        pendingInvites.putAll(loaded.pendingInvites);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag factionsTag = new ListTag();
        for (Faction faction : factions.values()) {
            CompoundTag factionTag = new CompoundTag();
            factionTag.putUUID("Id", faction.getId());
            factionTag.putUUID("Owner", faction.getOwner());
            factionTag.putString("Name", faction.getName());
            factionTag.putString("Color", faction.getColorName());
            factionTag.putString("Motd", faction.getMotd());
            factionTag.putString("Description", faction.getDescription());
            factionTag.putString("BannerColor", faction.getBannerColor());
            factionTag.putString("ProtectionTier", faction.getProtectionTier().name());
            CompoundTag ranksTag = new CompoundTag();
            for (Map.Entry<FactionRole, String> entry : faction.getRankNames().entrySet()) {
                ranksTag.putString(entry.getKey().name(), entry.getValue());
            }
            factionTag.put("RankNames", ranksTag);
            ListTag trustedTag = new ListTag();
            for (UUID trusted : faction.getTrustedPlayers()) {
                trustedTag.add(net.minecraft.nbt.StringTag.valueOf(trusted.toString()));
            }
            factionTag.put("TrustedPlayers", trustedTag);
            ListTag membersTag = new ListTag();
            for (Map.Entry<UUID, FactionRole> member : faction.getMembers().entrySet()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("Id", member.getKey());
                memberTag.putString("Role", member.getValue().name());
                membersTag.add(memberTag);
            }
            factionTag.put("Members", membersTag);
            CompoundTag permissionsTag = new CompoundTag();
            for (Map.Entry<FactionRole, EnumSet<FactionPermission>> entry : faction.getPermissions().entrySet()) {
                ListTag permsList = new ListTag();
                for (FactionPermission permission : entry.getValue()) {
                    permsList.add(net.minecraft.nbt.StringTag.valueOf(permission.name()));
                }
                permissionsTag.put(entry.getKey().name(), permsList);
            }
            factionTag.put("Permissions", permissionsTag);
            factionsTag.add(factionTag);
        }
        tag.put("Factions", factionsTag);
        ListTag claimsTag = new ListTag();
        for (Map.Entry<Long, UUID> entry : claims.entrySet()) {
            CompoundTag claim = new CompoundTag();
            claim.putLong("Chunk", entry.getKey());
            claim.putUUID("Faction", entry.getValue());
            claimsTag.add(claim);
        }
        tag.put("Claims", claimsTag);
        ListTag relationsTag = new ListTag();
        for (Map.Entry<UUID, Map<UUID, FactionRelation>> entry : relations.entrySet()) {
            for (Map.Entry<UUID, FactionRelation> relationEntry : entry.getValue().entrySet()) {
                CompoundTag relationTag = new CompoundTag();
                relationTag.putUUID("Source", entry.getKey());
                relationTag.putUUID("Target", relationEntry.getKey());
                relationTag.putString("Type", relationEntry.getValue().name());
                relationsTag.add(relationTag);
            }
        }
        tag.put("Relations", relationsTag);
        ListTag invitesTag = new ListTag();
        for (Map.Entry<UUID, FactionInvite> entry : pendingInvites.entrySet()) {
            CompoundTag invite = new CompoundTag();
            invite.putUUID("Player", entry.getKey());
            invite.putUUID("Faction", entry.getValue().factionId());
            invite.putLong("ExpiresAt", entry.getValue().expiresAt());
            invitesTag.add(invite);
        }
        tag.put("Invites", invitesTag);
        return tag;
    }

    public Optional<Faction> getFaction(UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public Map<UUID, Faction> getFactions() {
        return Collections.unmodifiableMap(factions);
    }

    public Optional<Faction> findFactionByName(String name) {
        return factions.values().stream()
            .filter(faction -> faction.getName().equalsIgnoreCase(name))
            .findFirst();
    }

    public Optional<Faction> getFactionByPlayer(UUID playerId) {
        UUID factionId = playerFaction.get(playerId);
        if (factionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factions.get(factionId));
    }

    public Optional<UUID> getFactionIdByPlayer(UUID playerId) {
        return Optional.ofNullable(playerFaction.get(playerId));
    }

    public Faction createFaction(String name, Player owner) {
        UUID id = UUID.randomUUID();
        Faction faction = new Faction(id, name, owner.getUUID());
        factions.put(id, faction);
        playerFaction.put(owner.getUUID(), id);
        setDirty();
        return faction;
    }

    public boolean addMember(UUID factionId, UUID playerId, FactionRole role) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        faction.setRole(playerId, role);
        playerFaction.put(playerId, factionId);
        pendingInvites.remove(playerId);
        setDirty();
        return true;
    }

    public boolean removeMember(UUID playerId) {
        UUID factionId = playerFaction.remove(playerId);
        if (factionId == null) {
            return false;
        }
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        faction.removeMember(playerId);
        setDirty();
        return true;
    }

    public void invitePlayer(UUID playerId, UUID factionId) {
        long expiresAt = Instant.now().plus(Duration.ofMinutes(FactionConfig.SERVER.inviteExpirationMinutes.get())).toEpochMilli();
        pendingInvites.put(playerId, new FactionInvite(factionId, expiresAt));
        setDirty();
    }

    public Optional<FactionInvite> getInvite(UUID playerId) {
        FactionInvite invite = pendingInvites.get(playerId);
        if (invite == null) {
            return Optional.empty();
        }
        if (invite.expiresAt() < System.currentTimeMillis()) {
            pendingInvites.remove(playerId);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(invite);
    }

    public Map<UUID, FactionInvite> getInvitesForFaction(UUID factionId) {
        Map<UUID, FactionInvite> invites = new HashMap<>();
        for (Map.Entry<UUID, FactionInvite> entry : pendingInvites.entrySet()) {
            if (factionId.equals(entry.getValue().factionId())) {
                invites.put(entry.getKey(), entry.getValue());
            }
        }
        return invites;
    }

    public void disbandFaction(UUID factionId) {
        Faction faction = factions.remove(factionId);
        if (faction == null) {
            return;
        }
        for (UUID member : faction.getMembers().keySet()) {
            playerFaction.remove(member);
            pendingInvites.remove(member);
        }
        claims.values().removeIf(id -> id.equals(factionId));
        relations.remove(factionId);
        for (Map<UUID, FactionRelation> entry : relations.values()) {
            entry.remove(factionId);
        }
        setDirty();
    }

    public boolean claimChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        if (claims.containsKey(key)) {
            return false;
        }
        if (getClaimCount(factionId) >= getMaxClaims(factionId)) {
            return false;
        }
        claims.put(key, factionId);
        setDirty();
        return true;
    }

    public boolean overtakeChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        UUID currentOwner = claims.get(key);
        if (currentOwner == null || currentOwner.equals(factionId)) {
            return false;
        }
        if (!isAtWar(factionId, currentOwner)) {
            return false;
        }
        if (getClaimCount(factionId) >= getMaxClaims(factionId)) {
            return false;
        }
        claims.put(key, factionId);
        setDirty();
        return true;
    }

    public boolean unclaimChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        if (!factionId.equals(claims.get(key))) {
            return false;
        }
        claims.remove(key);
        setDirty();
        return true;
    }

    public Optional<Faction> getFactionAt(BlockPos pos) {
        long key = new ChunkPos(pos).toLong();
        UUID factionId = claims.get(key);
        if (factionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factions.get(factionId));
    }

    public boolean isClaimed(BlockPos pos) {
        return claims.containsKey(new ChunkPos(pos).toLong());
    }

    public boolean isClaimed(ChunkPos chunkPos) {
        return claims.containsKey(chunkPos.toLong());
    }

    public Optional<UUID> getClaimOwner(BlockPos pos) {
        return Optional.ofNullable(claims.get(new ChunkPos(pos).toLong()));
    }

    public Optional<UUID> getClaimOwner(ChunkPos chunkPos) {
        return Optional.ofNullable(claims.get(chunkPos.toLong()));
    }

    public boolean hasPermission(Player player, BlockPos pos, FactionPermission permission) {
        Optional<UUID> ownerId = getClaimOwner(pos);
        if (ownerId.isEmpty()) {
            return true;
        }
        Optional<Faction> ownerFaction = getFaction(ownerId.get());
        if (ownerFaction.isEmpty()) {
            return true;
        }
        if (ownerFaction.get().hasPermission(player.getUUID(), permission)) {
            return true;
        }
        if (ownerFaction.get().isTrusted(player.getUUID())) {
            if (permission == FactionPermission.BLOCK_BREAK || permission == FactionPermission.BLOCK_PLACE) {
                return FactionConfig.SERVER.trustedAllowBuild.get();
            }
            return true;
        }
        Optional<UUID> playerFactionId = getFactionIdByPlayer(player.getUUID());
        if (playerFactionId.isEmpty()) {
            return false;
        }
        FactionRelation relation = getRelation(playerFactionId.get(), ownerId.get());
        if (relation == FactionRelation.ALLY) {
            return isAllowedForAllies(permission, ownerFaction.get());
        }
        if (relation == FactionRelation.WAR) {
            return isAllowedForWar(permission);
        }
        return false;
    }

    public int getClaimCount(UUID factionId) {
        int count = 0;
        for (UUID id : claims.values()) {
            if (factionId.equals(id)) {
                count++;
            }
        }
        return count;
    }

    public int getMaxClaims(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return 0;
        }
        int base = FactionConfig.SERVER.baseClaims.get();
        int perMember = FactionConfig.SERVER.claimsPerMember.get();
        int levelBonus = (getFactionLevel(factionId) - 1) * FactionConfig.SERVER.bonusClaimsPerLevel.get();
        return Math.max(base, base + (faction.getMemberCount() * perMember) + levelBonus);
    }

    public Map<Long, UUID> getClaims() {
        return claims;
    }

    private boolean isAllowedForAllies(FactionPermission permission, Faction faction) {
        FactionProtectionTier tier = faction != null ? faction.getProtectionTier() : FactionProtectionTier.STANDARD;
        return switch (permission) {
            case BLOCK_USE -> true;
            case CONTAINER_OPEN, ENTITY_INTERACT, CREATE_MACHINE_INTERACT -> tier != FactionProtectionTier.STRICT;
            case REDSTONE_TOGGLE -> tier == FactionProtectionTier.RELAXED;
            default -> false;
        };
    }

    private boolean isAllowedForWar(FactionPermission permission) {
        return switch (permission) {
            case BLOCK_BREAK, BLOCK_PLACE, BLOCK_USE, CONTAINER_OPEN, REDSTONE_TOGGLE, ENTITY_INTERACT, CREATE_MACHINE_INTERACT -> true;
            default -> false;
        };
    }

    public void setRelation(UUID source, UUID target, FactionRelation relation) {
        relations.computeIfAbsent(source, key -> new HashMap<>()).put(target, relation);
        relations.computeIfAbsent(target, key -> new HashMap<>()).put(source, relation);
        setDirty();
    }

    public void clearRelation(UUID source, UUID target) {
        Map<UUID, FactionRelation> sourceRelations = relations.get(source);
        if (sourceRelations != null) {
            sourceRelations.remove(target);
        }
        Map<UUID, FactionRelation> targetRelations = relations.get(target);
        if (targetRelations != null) {
            targetRelations.remove(source);
        }
        setDirty();
    }

    public FactionRelation getRelation(UUID source, UUID target) {
        Map<UUID, FactionRelation> map = relations.get(source);
        if (map == null) {
            return FactionRelation.NEUTRAL;
        }
        return map.getOrDefault(target, FactionRelation.NEUTRAL);
    }

    public boolean isAtWar(UUID source, UUID target) {
        return getRelation(source, target) == FactionRelation.WAR;
    }

    public int getFactionLevel(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return 1;
        }
        int membersPerLevel = Math.max(1, FactionConfig.SERVER.membersPerLevel.get());
        int level = 1 + Math.max(0, (faction.getMemberCount() - 1) / membersPerLevel);
        return Math.min(level, FactionConfig.SERVER.maxFactionLevel.get());
    }

    public boolean addTrustedPlayer(UUID factionId, UUID playerId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        faction.addTrustedPlayer(playerId);
        setDirty();
        return true;
    }

    public boolean removeTrustedPlayer(UUID factionId, UUID playerId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        faction.removeTrustedPlayer(playerId);
        setDirty();
        return true;
    }

    public Set<UUID> getTrustedPlayers(UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return Set.of();
        }
        return new HashSet<>(faction.getTrustedPlayers());
    }

    public void logAccess(BlockPos pos, UUID playerId, String playerName, String action, boolean allowed, String blockName) {
        long key = ChunkPos.asLong(pos);
        Deque<FactionAccessLog> logs = accessLogs.computeIfAbsent(key, k -> new ArrayDeque<>());
        logs.addFirst(new FactionAccessLog(Instant.now().toEpochMilli(), playerId, playerName, pos, action, allowed, blockName));
        int maxSize = Math.max(1, FactionConfig.SERVER.accessLogSize.get());
        while (logs.size() > maxSize) {
            logs.removeLast();
        }
    }

    public Deque<FactionAccessLog> getAccessLogs(BlockPos pos) {
        return accessLogs.getOrDefault(ChunkPos.asLong(pos), new ArrayDeque<>());
    }

    public boolean canUseProtectionTier(UUID factionId, FactionProtectionTier tier) {
        if (tier == FactionProtectionTier.STRICT) {
            return getFactionLevel(factionId) >= FactionConfig.SERVER.strictProtectionMinLevel.get();
        }
        return true;
    }

    public record FactionInvite(UUID factionId, long expiresAt) {
    }

    public record FactionAccessLog(long timestamp, UUID playerId, String playerName, BlockPos pos, String action,
                                   boolean allowed, String blockName) {
    }
}
