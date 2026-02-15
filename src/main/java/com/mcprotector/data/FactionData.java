package com.mcprotector.data;

import com.mcprotector.config.FactionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FactionData extends SavedData {
    private static final String DATA_NAME = "mcprotector_factions";
    private static final int DATA_VERSION = 12;

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<Long, UUID> claims = new HashMap<>();
    private final Map<Long, UUID> safeZoneClaims = new HashMap<>();
    private final Map<Long, UUID> personalClaims = new HashMap<>();
    private final Map<UUID, Integer> claimBoosts = new HashMap<>();
    private final Map<UUID, Map<UUID, FactionRelation>> relations = new HashMap<>();
    private final Map<UUID, FactionInvite> pendingInvites = new HashMap<>();
    private final Map<UUID, VassalInvite> pendingVassalInvites = new HashMap<>();
    private final Map<UUID, AllyInvite> pendingAllyInvites = new HashMap<>();
    private final Map<UUID, VassalContract> vassalContracts = new HashMap<>();
    private final Map<UUID, VassalBreakaway> vassalBreakaways = new HashMap<>();
    private final Map<Long, Deque<FactionAccessLog>> accessLogs = new HashMap<>();
    private final Map<UUID, Boolean> autoClaimSettings = new HashMap<>();
    private final Map<UUID, Boolean> borderSettings = new HashMap<>();
    private final Map<UUID, FactionHome> factionHomes = new HashMap<>();
    private final Map<String, Long> warDeclaredAt = new HashMap<>();
    private final Map<String, UUID> warDeclaredBy = new HashMap<>();
    private final Map<String, WarEndRequest> pendingWarEndRequests = new HashMap<>();

    public static FactionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(FactionData::new, FactionData::load),
            DATA_NAME
        );
    }

    public static FactionData load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag);
    }

    private static FactionData load(CompoundTag tag) {
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
            if (factionTag.contains("Roles")) {
                ListTag rolesTag = factionTag.getList("Roles", Tag.TAG_COMPOUND);
                if (!rolesTag.isEmpty()) {
                    faction.clearRolesAndPermissions();
                    for (Tag roleEntry : rolesTag) {
                        CompoundTag roleTag = (CompoundTag) roleEntry;
                        String roleName = roleTag.getString("Name");
                        if (roleName == null || roleName.isBlank()) {
                            continue;
                        }
                        String displayName = roleTag.contains("Display") ? roleTag.getString("Display") : roleName;
                        faction.addRole(roleName, displayName);
                    }
                    faction.ensureReservedRoles();
                }
            } else if (factionTag.contains("RankNames")) {
                CompoundTag ranksTag = factionTag.getCompound("RankNames");
                for (String roleName : ranksTag.getAllKeys()) {
                    faction.setRoleDisplayName(roleName, ranksTag.getString(roleName));
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
                String role = member.getString("Role");
                faction.setRole(memberId, role);
                data.playerFaction.put(memberId, id);
            }
            faction.setRole(owner, Faction.ROLE_OWNER);
            CompoundTag permissionsTag = factionTag.getCompound("Permissions");
            if (!permissionsTag.isEmpty()) {
                for (String roleName : permissionsTag.getAllKeys()) {
                    ListTag permsList = permissionsTag.getList(roleName, Tag.TAG_STRING);
                    EnumSet<FactionPermission> perms = EnumSet.noneOf(FactionPermission.class);
                    for (Tag permTag : permsList) {
                        perms.add(FactionPermission.valueOf(permTag.getAsString()));
                    }
                    String normalizedRole = Faction.normalizeRoleName(roleName);
                    if (!faction.hasRole(normalizedRole)) {
                        faction.addRole(normalizedRole, normalizedRole);
                    }
                    faction.setPermissions(normalizedRole, perms);
                }
            }
            if (factionTag.contains("Rules")) {
                ListTag rulesTag = factionTag.getList("Rules", Tag.TAG_STRING);
                for (Tag ruleTag : rulesTag) {
                    faction.addRule(ruleTag.getAsString());
                }
            }
            if (factionTag.contains("RelationPermissions")) {
                CompoundTag relationPermissionsTag = factionTag.getCompound("RelationPermissions");
                for (FactionRelation relation : FactionRelation.values()) {
                    if (!relationPermissionsTag.contains(relation.name())) {
                        continue;
                    }
                    ListTag permsList = relationPermissionsTag.getList(relation.name(), Tag.TAG_STRING);
                    EnumSet<FactionPermission> perms = EnumSet.noneOf(FactionPermission.class);
                    for (Tag permTag : permsList) {
                        perms.add(FactionPermission.valueOf(permTag.getAsString()));
                    }
                    faction.setRelationPermissions(relation, perms);
                }
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
        if (dataVersion >= 4 && tag.contains("SafeZoneClaims")) {
            ListTag safeZoneTag = tag.getList("SafeZoneClaims", Tag.TAG_COMPOUND);
            for (Tag claimEntry : safeZoneTag) {
                CompoundTag claim = (CompoundTag) claimEntry;
                long pos = claim.getLong("Chunk");
                UUID factionId = claim.getUUID("Faction");
                data.safeZoneClaims.put(pos, factionId);
            }
        }
        if (dataVersion >= 5 && tag.contains("PersonalClaims")) {
            ListTag personalTag = tag.getList("PersonalClaims", Tag.TAG_COMPOUND);
            for (Tag claimEntry : personalTag) {
                CompoundTag claim = (CompoundTag) claimEntry;
                long pos = claim.getLong("Chunk");
                UUID playerId = claim.getUUID("Player");
                data.personalClaims.put(pos, playerId);
            }
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
        if (dataVersion >= 8 && tag.contains("VassalInvites")) {
            ListTag invitesTag = tag.getList("VassalInvites", Tag.TAG_COMPOUND);
            for (Tag inviteEntry : invitesTag) {
                CompoundTag invite = (CompoundTag) inviteEntry;
                UUID vassalId = invite.getUUID("Vassal");
                UUID overlordId = invite.getUUID("Overlord");
                long expiresAt = invite.getLong("ExpiresAt");
                data.pendingVassalInvites.put(vassalId, new VassalInvite(overlordId, expiresAt));
            }
        }
        if (dataVersion >= 10 && tag.contains("AllyInvites")) {
            ListTag invitesTag = tag.getList("AllyInvites", Tag.TAG_COMPOUND);
            for (Tag inviteEntry : invitesTag) {
                CompoundTag invite = (CompoundTag) inviteEntry;
                UUID targetId = invite.getUUID("Target");
                UUID proposerId = invite.getUUID("Proposer");
                long expiresAt = invite.getLong("ExpiresAt");
                data.pendingAllyInvites.put(targetId, new AllyInvite(proposerId, expiresAt));
            }
        }
        if (dataVersion >= 8 && tag.contains("VassalContracts")) {
            ListTag contractsTag = tag.getList("VassalContracts", Tag.TAG_COMPOUND);
            for (Tag contractEntry : contractsTag) {
                CompoundTag contract = (CompoundTag) contractEntry;
                UUID vassalId = contract.getUUID("Vassal");
                UUID overlordId = contract.getUUID("Overlord");
                long startedAt = contract.getLong("StartedAt");
                data.vassalContracts.put(vassalId, new VassalContract(overlordId, startedAt));
            }
        }
        if (dataVersion >= 8 && tag.contains("VassalBreakaways")) {
            ListTag breakawayTag = tag.getList("VassalBreakaways", Tag.TAG_COMPOUND);
            for (Tag breakawayEntry : breakawayTag) {
                CompoundTag breakaway = (CompoundTag) breakawayEntry;
                UUID vassalId = breakaway.getUUID("Vassal");
                UUID overlordId = breakaway.getUUID("Overlord");
                int requiredClaims = breakaway.getInt("RequiredClaims");
                int capturedClaims = breakaway.getInt("CapturedClaims");
                long startedAt = breakaway.contains("StartedAt") ? breakaway.getLong("StartedAt") : System.currentTimeMillis();
                data.vassalBreakaways.put(vassalId, new VassalBreakaway(overlordId, requiredClaims, capturedClaims, startedAt));
            }
        }
        if (dataVersion >= 3 && tag.contains("AccessLogs")) {
            ListTag logsTag = tag.getList("AccessLogs", Tag.TAG_COMPOUND);
            for (Tag logEntry : logsTag) {
                CompoundTag logTag = (CompoundTag) logEntry;
                long chunkKey = logTag.getLong("Chunk");
                ListTag entries = logTag.getList("Entries", Tag.TAG_COMPOUND);
                Deque<FactionAccessLog> logs = new ArrayDeque<>();
                for (Tag entryTag : entries) {
                    CompoundTag entry = (CompoundTag) entryTag;
                    long timestamp = entry.getLong("Timestamp");
                    UUID playerId = entry.getUUID("PlayerId");
                    String playerName = entry.getString("PlayerName");
                    String action = entry.getString("Action");
                    boolean allowed = entry.getBoolean("Allowed");
                    String blockName = entry.getString("BlockName");
                    BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
                    logs.addLast(new FactionAccessLog(timestamp, playerId, playerName, pos, action, allowed, blockName));
                }
                if (!logs.isEmpty()) {
                    data.accessLogs.put(chunkKey, logs);
                }
            }
        }
        if (dataVersion >= 3 && tag.contains("PlayerSettings")) {
            ListTag settingsTag = tag.getList("PlayerSettings", Tag.TAG_COMPOUND);
            for (Tag entry : settingsTag) {
                CompoundTag setting = (CompoundTag) entry;
                UUID playerId = setting.getUUID("Player");
                if (setting.contains("AutoClaim")) {
                    data.autoClaimSettings.put(playerId, setting.getBoolean("AutoClaim"));
                }
                if (setting.contains("BorderEnabled")) {
                    data.borderSettings.put(playerId, setting.getBoolean("BorderEnabled"));
                }
            }
        }
        if (dataVersion >= 3 && tag.contains("Homes")) {
            ListTag homesTag = tag.getList("Homes", Tag.TAG_COMPOUND);
            for (Tag entry : homesTag) {
                CompoundTag homeTag = (CompoundTag) entry;
                UUID factionId = homeTag.getUUID("Faction");
                String dimension = homeTag.getString("Dimension");
                BlockPos pos = new BlockPos(homeTag.getInt("X"), homeTag.getInt("Y"), homeTag.getInt("Z"));
                data.factionHomes.put(factionId, new FactionHome(dimension, pos));
            }
        }
        if (dataVersion >= 9 && tag.contains("ClaimBoosts")) {
            ListTag boostsTag = tag.getList("ClaimBoosts", Tag.TAG_COMPOUND);
            for (Tag entry : boostsTag) {
                CompoundTag boostTag = (CompoundTag) entry;
                UUID factionId = boostTag.getUUID("Faction");
                int boost = boostTag.getInt("Boost");
                if (boost > 0) {
                    data.claimBoosts.put(factionId, boost);
                }
            }
        }
        if (dataVersion >= 12 && tag.contains("WarStates")) {
            ListTag warTag = tag.getList("WarStates", Tag.TAG_COMPOUND);
            for (Tag entry : warTag) {
                CompoundTag warState = (CompoundTag) entry;
                UUID first = warState.getUUID("First");
                UUID second = warState.getUUID("Second");
                String key = createWarPairKey(first, second);
                if (warState.contains("DeclaredAt")) {
                    data.warDeclaredAt.put(key, warState.getLong("DeclaredAt"));
                }
                if (warState.contains("DeclaredBy")) {
                    data.warDeclaredBy.put(key, warState.getUUID("DeclaredBy"));
                }
            }
        }
        if (dataVersion >= 12 && tag.contains("WarEndRequests")) {
            ListTag requestsTag = tag.getList("WarEndRequests", Tag.TAG_COMPOUND);
            for (Tag entry : requestsTag) {
                CompoundTag requestTag = (CompoundTag) entry;
                UUID first = requestTag.getUUID("First");
                UUID second = requestTag.getUUID("Second");
                String key = createWarPairKey(first, second);
                UUID requester = requestTag.getUUID("Requester");
                long createdAt = requestTag.getLong("CreatedAt");
                data.pendingWarEndRequests.put(key, new WarEndRequest(requester, createdAt));
            }
        }
        return data;
    }

    public void restoreFromTag(CompoundTag tag) {
        FactionData loaded = load(tag);
        factions.clear();
        playerFaction.clear();
        claims.clear();
        safeZoneClaims.clear();
        personalClaims.clear();
        claimBoosts.clear();
        relations.clear();
        pendingInvites.clear();
        pendingVassalInvites.clear();
        pendingAllyInvites.clear();
        vassalContracts.clear();
        vassalBreakaways.clear();
        accessLogs.clear();
        autoClaimSettings.clear();
        borderSettings.clear();
        factionHomes.clear();
        warDeclaredAt.clear();
        warDeclaredBy.clear();
        pendingWarEndRequests.clear();
        factions.putAll(loaded.factions);
        playerFaction.putAll(loaded.playerFaction);
        claims.putAll(loaded.claims);
        safeZoneClaims.putAll(loaded.safeZoneClaims);
        personalClaims.putAll(loaded.personalClaims);
        claimBoosts.putAll(loaded.claimBoosts);
        relations.putAll(loaded.relations);
        pendingInvites.putAll(loaded.pendingInvites);
        pendingVassalInvites.putAll(loaded.pendingVassalInvites);
        pendingAllyInvites.putAll(loaded.pendingAllyInvites);
        vassalContracts.putAll(loaded.vassalContracts);
        vassalBreakaways.putAll(loaded.vassalBreakaways);
        accessLogs.putAll(loaded.accessLogs);
        autoClaimSettings.putAll(loaded.autoClaimSettings);
        borderSettings.putAll(loaded.borderSettings);
        factionHomes.putAll(loaded.factionHomes);
        warDeclaredAt.putAll(loaded.warDeclaredAt);
        warDeclaredBy.putAll(loaded.warDeclaredBy);
        pendingWarEndRequests.putAll(loaded.pendingWarEndRequests);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
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
            ListTag rolesTag = new ListTag();
            for (Map.Entry<String, String> entry : faction.getRoleDisplayNames().entrySet()) {
                CompoundTag roleTag = new CompoundTag();
                roleTag.putString("Name", entry.getKey());
                roleTag.putString("Display", entry.getValue());
                rolesTag.add(roleTag);
            }
            factionTag.put("Roles", rolesTag);
            ListTag trustedTag = new ListTag();
            for (UUID trusted : faction.getTrustedPlayers()) {
                trustedTag.add(net.minecraft.nbt.StringTag.valueOf(trusted.toString()));
            }
            factionTag.put("TrustedPlayers", trustedTag);
            ListTag membersTag = new ListTag();
            for (Map.Entry<UUID, String> member : faction.getMembers().entrySet()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("Id", member.getKey());
                memberTag.putString("Role", member.getValue());
                membersTag.add(memberTag);
            }
            factionTag.put("Members", membersTag);
            CompoundTag permissionsTag = new CompoundTag();
            for (Map.Entry<String, EnumSet<FactionPermission>> entry : faction.getPermissions().entrySet()) {
                ListTag permsList = new ListTag();
                for (FactionPermission permission : entry.getValue()) {
                    permsList.add(net.minecraft.nbt.StringTag.valueOf(permission.name()));
                }
                permissionsTag.put(entry.getKey(), permsList);
            }
            factionTag.put("Permissions", permissionsTag);
            ListTag rulesTag = new ListTag();
            for (String rule : faction.getRules()) {
                rulesTag.add(net.minecraft.nbt.StringTag.valueOf(rule));
            }
            factionTag.put("Rules", rulesTag);
            CompoundTag relationPermissionsTag = new CompoundTag();
            for (Map.Entry<FactionRelation, EnumSet<FactionPermission>> entry : faction.getRelationPermissions().entrySet()) {
                ListTag permsList = new ListTag();
                for (FactionPermission permission : entry.getValue()) {
                    permsList.add(net.minecraft.nbt.StringTag.valueOf(permission.name()));
                }
                relationPermissionsTag.put(entry.getKey().name(), permsList);
            }
            factionTag.put("RelationPermissions", relationPermissionsTag);
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
        ListTag safeZoneTag = new ListTag();
        for (Map.Entry<Long, UUID> entry : safeZoneClaims.entrySet()) {
            CompoundTag claim = new CompoundTag();
            claim.putLong("Chunk", entry.getKey());
            claim.putUUID("Faction", entry.getValue());
            safeZoneTag.add(claim);
        }
        tag.put("SafeZoneClaims", safeZoneTag);
        ListTag personalTag = new ListTag();
        for (Map.Entry<Long, UUID> entry : personalClaims.entrySet()) {
            CompoundTag claim = new CompoundTag();
            claim.putLong("Chunk", entry.getKey());
            claim.putUUID("Player", entry.getValue());
            personalTag.add(claim);
        }
        tag.put("PersonalClaims", personalTag);
        ListTag boostsTag = new ListTag();
        for (Map.Entry<UUID, Integer> entry : claimBoosts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            CompoundTag boostTag = new CompoundTag();
            boostTag.putUUID("Faction", entry.getKey());
            boostTag.putInt("Boost", entry.getValue());
            boostsTag.add(boostTag);
        }
        tag.put("ClaimBoosts", boostsTag);
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
        ListTag vassalInvitesTag = new ListTag();
        for (Map.Entry<UUID, VassalInvite> entry : pendingVassalInvites.entrySet()) {
            CompoundTag invite = new CompoundTag();
            invite.putUUID("Vassal", entry.getKey());
            invite.putUUID("Overlord", entry.getValue().overlordId());
            invite.putLong("ExpiresAt", entry.getValue().expiresAt());
            vassalInvitesTag.add(invite);
        }
        tag.put("VassalInvites", vassalInvitesTag);
        ListTag allyInvitesTag = new ListTag();
        for (Map.Entry<UUID, AllyInvite> entry : pendingAllyInvites.entrySet()) {
            CompoundTag invite = new CompoundTag();
            invite.putUUID("Target", entry.getKey());
            invite.putUUID("Proposer", entry.getValue().proposerId());
            invite.putLong("ExpiresAt", entry.getValue().expiresAt());
            allyInvitesTag.add(invite);
        }
        tag.put("AllyInvites", allyInvitesTag);
        ListTag vassalContractsTag = new ListTag();
        for (Map.Entry<UUID, VassalContract> entry : vassalContracts.entrySet()) {
            CompoundTag contract = new CompoundTag();
            contract.putUUID("Vassal", entry.getKey());
            contract.putUUID("Overlord", entry.getValue().overlordId());
            contract.putLong("StartedAt", entry.getValue().startedAt());
            vassalContractsTag.add(contract);
        }
        tag.put("VassalContracts", vassalContractsTag);
        ListTag breakawayTag = new ListTag();
        for (Map.Entry<UUID, VassalBreakaway> entry : vassalBreakaways.entrySet()) {
            CompoundTag breakaway = new CompoundTag();
            breakaway.putUUID("Vassal", entry.getKey());
            breakaway.putUUID("Overlord", entry.getValue().overlordId());
            breakaway.putInt("RequiredClaims", entry.getValue().requiredClaims());
            breakaway.putInt("CapturedClaims", entry.getValue().capturedClaims());
            breakaway.putLong("StartedAt", entry.getValue().startedAt());
            breakawayTag.add(breakaway);
        }
        tag.put("VassalBreakaways", breakawayTag);
        ListTag logsTag = new ListTag();
        for (Map.Entry<Long, Deque<FactionAccessLog>> entry : accessLogs.entrySet()) {
            CompoundTag logTag = new CompoundTag();
            logTag.putLong("Chunk", entry.getKey());
            ListTag entries = new ListTag();
            for (FactionAccessLog log : entry.getValue()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putLong("Timestamp", log.timestamp());
                entryTag.putUUID("PlayerId", log.playerId());
                entryTag.putString("PlayerName", log.playerName());
                entryTag.putString("Action", log.action());
                entryTag.putBoolean("Allowed", log.allowed());
                entryTag.putString("BlockName", log.blockName());
                entryTag.putInt("X", log.pos().getX());
                entryTag.putInt("Y", log.pos().getY());
                entryTag.putInt("Z", log.pos().getZ());
                entries.add(entryTag);
            }
            logTag.put("Entries", entries);
            logsTag.add(logTag);
        }
        tag.put("AccessLogs", logsTag);
        ListTag settingsTag = new ListTag();
        for (UUID playerId : autoClaimSettings.keySet()) {
            CompoundTag setting = new CompoundTag();
            setting.putUUID("Player", playerId);
            setting.putBoolean("AutoClaim", autoClaimSettings.getOrDefault(playerId, false));
            setting.putBoolean("BorderEnabled", borderSettings.getOrDefault(playerId, false));
            settingsTag.add(setting);
        }
        for (UUID playerId : borderSettings.keySet()) {
            if (autoClaimSettings.containsKey(playerId)) {
                continue;
            }
            CompoundTag setting = new CompoundTag();
            setting.putUUID("Player", playerId);
            setting.putBoolean("AutoClaim", autoClaimSettings.getOrDefault(playerId, false));
            setting.putBoolean("BorderEnabled", borderSettings.getOrDefault(playerId, false));
            settingsTag.add(setting);
        }
        tag.put("PlayerSettings", settingsTag);
        ListTag homesTag = new ListTag();
        for (Map.Entry<UUID, FactionHome> entry : factionHomes.entrySet()) {
            CompoundTag homeTag = new CompoundTag();
            homeTag.putUUID("Faction", entry.getKey());
            homeTag.putString("Dimension", entry.getValue().dimension());
            homeTag.putInt("X", entry.getValue().pos().getX());
            homeTag.putInt("Y", entry.getValue().pos().getY());
            homeTag.putInt("Z", entry.getValue().pos().getZ());
            homesTag.add(homeTag);
        }
        tag.put("Homes", homesTag);
        ListTag warTag = new ListTag();
        for (Map.Entry<String, Long> entry : warDeclaredAt.entrySet()) {
            UUID[] pair = parseWarPairKey(entry.getKey());
            if (pair == null) {
                continue;
            }
            CompoundTag warState = new CompoundTag();
            warState.putUUID("First", pair[0]);
            warState.putUUID("Second", pair[1]);
            warState.putLong("DeclaredAt", entry.getValue());
            UUID declarer = warDeclaredBy.get(entry.getKey());
            if (declarer != null) {
                warState.putUUID("DeclaredBy", declarer);
            }
            warTag.add(warState);
        }
        tag.put("WarStates", warTag);
        ListTag requestTag = new ListTag();
        for (Map.Entry<String, WarEndRequest> entry : pendingWarEndRequests.entrySet()) {
            UUID[] pair = parseWarPairKey(entry.getKey());
            if (pair == null) {
                continue;
            }
            CompoundTag request = new CompoundTag();
            request.putUUID("First", pair[0]);
            request.putUUID("Second", pair[1]);
            request.putUUID("Requester", entry.getValue().requesterFactionId());
            request.putLong("CreatedAt", entry.getValue().createdAt());
            requestTag.add(request);
        }
        tag.put("WarEndRequests", requestTag);
        return tag;
    }

    public Optional<Faction> getFaction(UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public Map<UUID, Faction> getFactions() {
        return Collections.unmodifiableMap(factions);
    }

    public Optional<Faction> findFactionByName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return factions.values().stream()
            .filter(faction -> faction.getName().equalsIgnoreCase(trimmed))
            .findFirst();
    }

    public boolean hasSimilarFactionName(String name, UUID excludedFactionId) {
        String normalized = normalizeFactionName(name);
        if (normalized.isEmpty()) {
            return false;
        }
        for (Faction faction : factions.values()) {
            if (excludedFactionId != null && faction.getId().equals(excludedFactionId)) {
                continue;
            }
            if (isVerySimilarFactionName(normalized, normalizeFactionName(faction.getName()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVerySimilarFactionName(String first, String second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        int lengthDifference = Math.abs(first.length() - second.length());
        if ((first.startsWith(second) || second.startsWith(first)) && lengthDifference <= 2) {
            return true;
        }
        if (Math.min(first.length(), second.length()) < 4) {
            return false;
        }
        int threshold = Math.min(first.length(), second.length()) >= 10 ? 2 : 1;
        if (lengthDifference > threshold) {
            return false;
        }
        return levenshteinDistance(first, second) <= threshold;
    }

    private static int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private static String normalizeFactionName(String name) {
        String trimmed = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    public boolean renameFaction(UUID factionId, String newName) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (hasSimilarFactionName(trimmed, factionId)) {
            return false;
        }
        faction.setName(trimmed);
        setDirty();
        return true;
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

    public Faction createSystemFaction(String name) {
        UUID id = UUID.randomUUID();
        UUID ownerId = new UUID(0L, 0L);
        Faction faction = new Faction(id, name, ownerId);
        factions.put(id, faction);
        setDirty();
        return faction;
    }

    public boolean addMember(UUID factionId, UUID playerId, String role) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        if (!faction.hasRole(role)) {
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

    public void clearInvite(UUID playerId) {
        if (pendingInvites.remove(playerId) != null) {
            setDirty();
        }
    }

    public void inviteVassal(UUID overlordId, UUID vassalId) {
        long expiresAt = Instant.now().plus(Duration.ofMinutes(FactionConfig.SERVER.inviteExpirationMinutes.get())).toEpochMilli();
        pendingVassalInvites.put(vassalId, new VassalInvite(overlordId, expiresAt));
        setDirty();
    }

    public void inviteAlly(UUID proposerId, UUID targetId) {
        long expiresAt = Instant.now().plus(Duration.ofMinutes(FactionConfig.SERVER.inviteExpirationMinutes.get())).toEpochMilli();
        pendingAllyInvites.put(targetId, new AllyInvite(proposerId, expiresAt));
        setDirty();
    }

    public Optional<VassalInvite> getVassalInvite(UUID vassalId) {
        VassalInvite invite = pendingVassalInvites.get(vassalId);
        if (invite == null) {
            return Optional.empty();
        }
        if (invite.expiresAt() < System.currentTimeMillis()) {
            pendingVassalInvites.remove(vassalId);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(invite);
    }

    public void clearVassalInvite(UUID vassalId) {
        if (pendingVassalInvites.remove(vassalId) != null) {
            setDirty();
        }
    }

    public Optional<AllyInvite> getAllyInvite(UUID targetId) {
        AllyInvite invite = pendingAllyInvites.get(targetId);
        if (invite == null) {
            return Optional.empty();
        }
        if (invite.expiresAt() < System.currentTimeMillis()) {
            pendingAllyInvites.remove(targetId);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(invite);
    }

    public void clearAllyInvite(UUID targetId) {
        if (pendingAllyInvites.remove(targetId) != null) {
            setDirty();
        }
    }

    public Optional<UUID> getOverlord(UUID vassalId) {
        VassalContract contract = vassalContracts.get(vassalId);
        if (contract == null) {
            return Optional.empty();
        }
        return Optional.of(contract.overlordId());
    }

    public boolean hasActiveBreakaway(UUID vassalId) {
        return vassalBreakaways.containsKey(vassalId);
    }

    public Map<UUID, VassalBreakaway> getActiveBreakaways() {
        return Collections.unmodifiableMap(vassalBreakaways);
    }

    public Map<UUID, VassalContract> getVassalContracts() {
        return Collections.unmodifiableMap(vassalContracts);
    }

    public boolean isVassalRelationship(UUID factionId, UUID otherId) {
        Optional<UUID> overlord = getOverlord(factionId);
        if (overlord.isPresent() && overlord.get().equals(otherId)) {
            return true;
        }
        Optional<UUID> otherOverlord = getOverlord(otherId);
        return otherOverlord.isPresent() && otherOverlord.get().equals(factionId);
    }

    public boolean createVassalContract(UUID overlordId, UUID vassalId) {
        if (vassalContracts.containsKey(vassalId)) {
            return false;
        }
        vassalContracts.put(vassalId, new VassalContract(overlordId, System.currentTimeMillis()));
        setDirty();
        return true;
    }

    public boolean releaseVassal(UUID overlordId, UUID vassalId) {
        VassalContract contract = vassalContracts.get(vassalId);
        if (contract == null || !contract.overlordId().equals(overlordId)) {
            return false;
        }
        vassalContracts.remove(vassalId);
        vassalBreakaways.remove(vassalId);
        setDirty();
        return true;
    }

    public void startVassalBreakaway(UUID vassalId, UUID overlordId, int requiredClaims) {
        vassalBreakaways.put(vassalId, new VassalBreakaway(overlordId, requiredClaims, 0, System.currentTimeMillis()));
        setDirty();
    }

    public boolean recordVassalBreakawayCapture(UUID vassalId, UUID overlordId) {
        VassalBreakaway breakaway = vassalBreakaways.get(vassalId);
        if (breakaway == null || !breakaway.overlordId().equals(overlordId)) {
            return false;
        }
        int captured = breakaway.capturedClaims() + 1;
        int required = breakaway.requiredClaims();
        if (captured >= required) {
            vassalBreakaways.remove(vassalId);
            vassalContracts.remove(vassalId);
            setDirty();
            return true;
        }
        vassalBreakaways.put(vassalId, new VassalBreakaway(overlordId, required, captured, breakaway.startedAt()));
        setDirty();
        return false;
    }

    public void cancelVassalBreakaway(UUID factionId, UUID targetId) {
        VassalBreakaway breakaway = vassalBreakaways.get(factionId);
        if (breakaway != null && breakaway.overlordId().equals(targetId)) {
            vassalBreakaways.remove(factionId);
            setDirty();
        }
    }

    public int calculateBreakawayClaimGoal(UUID overlordId) {
        int claims = getClaimCount(overlordId);
        if (claims <= 0) {
            return 0;
        }
        double percent = FactionConfig.SERVER.vassalBreakawayClaimPercent.get();
        return Math.max(1, (int) Math.ceil(claims * percent));
    }

    public boolean isFactionOnline(ServerLevel level, UUID factionId) {
        Faction faction = factions.get(factionId);
        if (faction == null) {
            return false;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (faction.getMembers().containsKey(player.getUUID())) {
                return true;
            }
        }
        return false;
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
        pendingVassalInvites.remove(factionId);
        pendingAllyInvites.remove(factionId);
        claims.values().removeIf(id -> id.equals(factionId));
        safeZoneClaims.values().removeIf(id -> id.equals(factionId));
        claimBoosts.remove(factionId);
        relations.remove(factionId);
        for (Map<UUID, FactionRelation> entry : relations.values()) {
            entry.remove(factionId);
        }
        vassalContracts.remove(factionId);
        vassalBreakaways.remove(factionId);
        pendingVassalInvites.entrySet().removeIf(entry -> entry.getValue().overlordId().equals(factionId));
        pendingAllyInvites.entrySet().removeIf(entry -> entry.getValue().proposerId().equals(factionId));
        vassalContracts.entrySet().removeIf(entry -> entry.getValue().overlordId().equals(factionId));
        vassalBreakaways.entrySet().removeIf(entry -> entry.getValue().overlordId().equals(factionId));
        warDeclaredAt.entrySet().removeIf(entry -> entry.getKey().contains(factionId.toString()));
        warDeclaredBy.entrySet().removeIf(entry -> entry.getKey().contains(factionId.toString()) || factionId.equals(entry.getValue()));
        pendingWarEndRequests.entrySet().removeIf(entry -> entry.getKey().contains(factionId.toString())
            || factionId.equals(entry.getValue().requesterFactionId()));
        setDirty();
    }

    public boolean claimChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        if (claims.containsKey(key) || safeZoneClaims.containsKey(key) || personalClaims.containsKey(key)) {
            return false;
        }
        if (getClaimCount(factionId) >= getMaxClaims(factionId)) {
            return false;
        }
        if (!isAdjacentToFactionClaim(chunk, factionId)) {
            return false;
        }
        claims.put(key, factionId);
        setDirty();
        return true;
    }

    public boolean overtakeChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        if (safeZoneClaims.containsKey(key) || personalClaims.containsKey(key)) {
            return false;
        }
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
        if (safeZoneClaims.containsKey(key) || personalClaims.containsKey(key)) {
            return false;
        }
        if (!factionId.equals(claims.get(key))) {
            return false;
        }
        claims.remove(key);
        setDirty();
        return true;
    }

    public boolean claimSafeZoneChunk(ChunkPos chunk, UUID factionId) {
        long key = chunk.toLong();
        UUID existing = claims.get(key);
        if (personalClaims.containsKey(key)) {
            return false;
        }
        if (safeZoneClaims.containsKey(key)) {
            return false;
        }
        if (existing != null && !existing.equals(factionId)) {
            return false;
        }
        claims.remove(key);
        safeZoneClaims.put(key, factionId);
        setDirty();
        return true;
    }

    public boolean unclaimSafeZoneChunk(ChunkPos chunk) {
        long key = chunk.toLong();
        if (!safeZoneClaims.containsKey(key)) {
            return false;
        }
        safeZoneClaims.remove(key);
        setDirty();
        return true;
    }

    public boolean claimPersonalChunk(ChunkPos chunk, UUID playerId) {
        long key = chunk.toLong();
        if (claims.containsKey(key) || safeZoneClaims.containsKey(key) || personalClaims.containsKey(key)) {
            return false;
        }
        personalClaims.put(key, playerId);
        setDirty();
        return true;
    }

    public boolean unclaimPersonalChunk(ChunkPos chunk, UUID playerId) {
        long key = chunk.toLong();
        if (!playerId.equals(personalClaims.get(key))) {
            return false;
        }
        personalClaims.remove(key);
        setDirty();
        return true;
    }

    public Optional<Faction> getFactionAt(BlockPos pos) {
        long key = new ChunkPos(pos).toLong();
        UUID factionId = safeZoneClaims.getOrDefault(key, claims.get(key));
        if (factionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factions.get(factionId));
    }

    public boolean isClaimed(BlockPos pos) {
        long key = new ChunkPos(pos).toLong();
        return claims.containsKey(key) || safeZoneClaims.containsKey(key) || personalClaims.containsKey(key);
    }

    public boolean isClaimed(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        return claims.containsKey(key) || safeZoneClaims.containsKey(key) || personalClaims.containsKey(key);
    }

    public boolean isSafeZoneClaimed(BlockPos pos) {
        return safeZoneClaims.containsKey(new ChunkPos(pos).toLong());
    }

    public boolean isSafeZoneClaimed(ChunkPos chunkPos) {
        return safeZoneClaims.containsKey(chunkPos.toLong());
    }

    public Optional<UUID> getClaimOwner(BlockPos pos) {
        long key = new ChunkPos(pos).toLong();
        UUID owner = safeZoneClaims.getOrDefault(key, claims.get(key));
        return Optional.ofNullable(owner);
    }

    public Optional<UUID> getClaimOwner(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        UUID owner = safeZoneClaims.getOrDefault(key, claims.get(key));
        return Optional.ofNullable(owner);
    }

    public Optional<UUID> getPersonalClaimOwner(BlockPos pos) {
        long key = new ChunkPos(pos).toLong();
        return Optional.ofNullable(personalClaims.get(key));
    }

    public Optional<UUID> getPersonalClaimOwner(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        return Optional.ofNullable(personalClaims.get(key));
    }

    public boolean hasPermission(Player player, BlockPos pos, FactionPermission permission) {
        Optional<UUID> personalOwner = getPersonalClaimOwner(pos);
        if (personalOwner.isPresent()) {
            return personalOwner.get().equals(player.getUUID());
        }
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
            if (permission == FactionPermission.BLOCK_BREAK
                || permission == FactionPermission.BLOCK_PLACE
                || permission == FactionPermission.FLUID_PLACE) {
                return FactionConfig.SERVER.trustedAllowBuild.get();
            }
            return true;
        }
        Optional<UUID> playerFactionId = getFactionIdByPlayer(player.getUUID());
        if (playerFactionId.isPresent()) {
            Optional<UUID> overlordId = getOverlord(ownerId.get());
            if (overlordId.isPresent() && overlordId.get().equals(playerFactionId.get())) {
                return permission != FactionPermission.BLOCK_BREAK
                    && permission != FactionPermission.BLOCK_PLACE
                    && permission != FactionPermission.FLUID_PLACE;
            }
        }
        if (playerFactionId.isEmpty()) {
            return false;
        }
        FactionRelation relation = getRelation(playerFactionId.get(), ownerId.get());
        if (relation == FactionRelation.ALLY) {
            return isAllowedForAllies(permission, ownerFaction.get());
        }
        if (relation == FactionRelation.WAR) {
            if (FactionConfig.SERVER.protectOfflineFactions.get() && player.level() instanceof ServerLevel serverLevel) {
                if (!isFactionOnline(serverLevel, ownerId.get())) {
                    return false;
                }
            }
            return isAllowedForWar(permission, ownerFaction.get());
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
        int boost = claimBoosts.getOrDefault(factionId, 0);
        return Math.max(base, base + (faction.getMemberCount() * perMember) + levelBonus + Math.max(0, boost));
    }

    public Map<Long, UUID> getClaims() {
        return claims;
    }

    public Map<Long, UUID> getSafeZoneClaims() {
        return safeZoneClaims;
    }

    public Map<Long, UUID> getPersonalClaims() {
        return personalClaims;
    }

    private boolean isAllowedForAllies(FactionPermission permission, Faction faction) {
        if (faction == null) {
            return false;
        }
        return faction.getRelationPermissions(FactionRelation.ALLY).contains(permission);
    }

    private boolean isAllowedForWar(FactionPermission permission, Faction faction) {
        if (faction == null) {
            return false;
        }
        return faction.getRelationPermissions(FactionRelation.WAR).contains(permission);
    }

    public void setRelation(UUID source, UUID target, FactionRelation relation) {
        relations.computeIfAbsent(source, key -> new HashMap<>()).put(target, relation);
        relations.computeIfAbsent(target, key -> new HashMap<>()).put(source, relation);
        if (relation != FactionRelation.WAR) {
            String key = createWarPairKey(source, target);
            warDeclaredAt.remove(key);
            warDeclaredBy.remove(key);
            pendingWarEndRequests.remove(key);
        }
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
        String key = createWarPairKey(source, target);
        warDeclaredAt.remove(key);
        warDeclaredBy.remove(key);
        pendingWarEndRequests.remove(key);
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

    public boolean isFactionAtWar(UUID factionId) {
        Map<UUID, FactionRelation> factionRelations = relations.get(factionId);
        if (factionRelations == null || factionRelations.isEmpty()) {
            return false;
        }
        for (FactionRelation relation : factionRelations.values()) {
            if (relation == FactionRelation.WAR) {
                return true;
            }
        }
        return false;
    }

    public long recordWarDeclaration(UUID source, UUID target) {
        long now = System.currentTimeMillis();
        String key = createWarPairKey(source, target);
        warDeclaredAt.put(key, now);
        warDeclaredBy.put(key, source);
        pendingWarEndRequests.remove(key);
        setDirty();
        return now;
    }

    public Optional<Long> getWarDeclaredAt(UUID source, UUID target) {
        return Optional.ofNullable(warDeclaredAt.get(createWarPairKey(source, target)));
    }

    public Optional<UUID> getWarDeclarer(UUID source, UUID target) {
        return Optional.ofNullable(warDeclaredBy.get(createWarPairKey(source, target)));
    }

    public Optional<WarEndRequest> getWarEndRequest(UUID source, UUID target) {
        String key = createWarPairKey(source, target);
        WarEndRequest request = pendingWarEndRequests.get(key);
        if (request == null) {
            return Optional.empty();
        }
        if (request.createdAt() + 120_000L < System.currentTimeMillis()) {
            pendingWarEndRequests.remove(key);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public void requestWarEnd(UUID source, UUID target, UUID requesterFactionId) {
        String key = createWarPairKey(source, target);
        pendingWarEndRequests.put(key, new WarEndRequest(requesterFactionId, System.currentTimeMillis()));
        setDirty();
    }

    public void clearWarEndRequest(UUID source, UUID target) {
        String key = createWarPairKey(source, target);
        if (pendingWarEndRequests.remove(key) != null) {
            setDirty();
        }
    }

    public void clearWarState(UUID source, UUID target) {
        String key = createWarPairKey(source, target);
        warDeclaredAt.remove(key);
        warDeclaredBy.remove(key);
        pendingWarEndRequests.remove(key);
        setDirty();
    }

    public int getPersonalClaimCount(UUID playerId) {
        int count = 0;
        for (UUID id : personalClaims.values()) {
            if (playerId.equals(id)) {
                count++;
            }
        }
        return count;
    }

    public int removeAllPersonalClaims(UUID playerId) {
        int before = personalClaims.size();
        personalClaims.values().removeIf(playerId::equals);
        int removed = before - personalClaims.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public int clearPersonalClaims() {
        int removed = personalClaims.size();
        if (removed > 0) {
            personalClaims.clear();
            setDirty();
        }
        return removed;
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
        setDirty();
    }

    public Deque<FactionAccessLog> getAccessLogs(BlockPos pos) {
        return accessLogs.getOrDefault(ChunkPos.asLong(pos), new ArrayDeque<>());
    }

    public boolean isAutoClaimEnabled(UUID playerId) {
        return autoClaimSettings.getOrDefault(playerId, false);
    }

    public void setAutoClaimEnabled(UUID playerId, boolean enabled) {
        autoClaimSettings.put(playerId, enabled);
        setDirty();
    }

    public boolean isBorderEnabled(UUID playerId) {
        return borderSettings.getOrDefault(playerId, false);
    }

    public void setBorderEnabled(UUID playerId, boolean enabled) {
        borderSettings.put(playerId, enabled);
        setDirty();
    }

    public int getClaimBoost(UUID factionId) {
        return claimBoosts.getOrDefault(factionId, 0);
    }

    public void setClaimBoost(UUID factionId, int boost) {
        if (boost <= 0) {
            if (claimBoosts.remove(factionId) != null) {
                setDirty();
            }
            return;
        }
        claimBoosts.put(factionId, boost);
        setDirty();
    }

    public boolean isAdjacentToFactionClaim(ChunkPos chunk, UUID factionId) {
        if (getClaimCount(factionId) == 0) {
            return true;
        }
        int x = chunk.x;
        int z = chunk.z;
        return factionId.equals(claims.get(ChunkPos.asLong(x + 1, z)))
            || factionId.equals(claims.get(ChunkPos.asLong(x - 1, z)))
            || factionId.equals(claims.get(ChunkPos.asLong(x, z + 1)))
            || factionId.equals(claims.get(ChunkPos.asLong(x, z - 1)));
    }

    public Optional<FactionHome> getFactionHome(UUID factionId) {
        return Optional.ofNullable(factionHomes.get(factionId));
    }

    public void setFactionHome(UUID factionId, String dimension, BlockPos pos) {
        factionHomes.put(factionId, new FactionHome(dimension, pos));
        setDirty();
    }

    public boolean canUseProtectionTier(UUID factionId, FactionProtectionTier tier) {
        if (tier == FactionProtectionTier.STRICT) {
            return getFactionLevel(factionId) >= FactionConfig.SERVER.strictProtectionMinLevel.get();
        }
        return true;
    }

    private static String createWarPairKey(UUID source, UUID target) {
        String a = source.toString();
        String b = target.toString();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static UUID[] parseWarPairKey(String key) {
        String[] parts = key.split("\\|", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new UUID[]{UUID.fromString(parts[0]), UUID.fromString(parts[1])};
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record WarEndRequest(UUID requesterFactionId, long createdAt) {
    }

    public record FactionInvite(UUID factionId, long expiresAt) {
    }

    public record AllyInvite(UUID proposerId, long expiresAt) {
    }

    public record VassalInvite(UUID overlordId, long expiresAt) {
    }

    public record VassalContract(UUID overlordId, long startedAt) {
    }

    public record VassalBreakaway(UUID overlordId, int requiredClaims, int capturedClaims, long startedAt) {
    }

    public record FactionAccessLog(long timestamp, UUID playerId, String playerName, BlockPos pos, String action,
                                   boolean allowed, String blockName) {
    }

    public record FactionHome(String dimension, BlockPos pos) {
    }
}
