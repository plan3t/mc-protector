package com.mcprotector.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FactionData extends SavedData {
    private static final String DATA_NAME = "mcprotector_factions";

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<Long, UUID> claims = new HashMap<>();
    private final Map<UUID, Map<UUID, FactionRelation>> relations = new HashMap<>();

    public static FactionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FactionData::load, FactionData::new, DATA_NAME);
    }

    public static FactionData load(CompoundTag tag) {
        FactionData data = new FactionData();
        ListTag factionsTag = tag.getList("Factions", Tag.TAG_COMPOUND);
        for (Tag entry : factionsTag) {
            CompoundTag factionTag = (CompoundTag) entry;
            UUID id = factionTag.getUUID("Id");
            UUID owner = factionTag.getUUID("Owner");
            String name = factionTag.getString("Name");
            Faction faction = new Faction(id, name, owner);
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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag factionsTag = new ListTag();
        for (Faction faction : factions.values()) {
            CompoundTag factionTag = new CompoundTag();
            factionTag.putUUID("Id", faction.getId());
            factionTag.putUUID("Owner", faction.getOwner());
            factionTag.putString("Name", faction.getName());
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
        return tag;
    }

    public Optional<Faction> getFaction(UUID id) {
        return Optional.ofNullable(factions.get(id));
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

    public Faction createFaction(String name, Player owner) {
        UUID id = UUID.randomUUID();
        Faction faction = new Faction(id, name, owner.getUUID());
        factions.put(id, faction);
        playerFaction.put(owner.getUUID(), id);
        setDirty();
        return faction;
    }

    public void disbandFaction(UUID factionId) {
        Faction faction = factions.remove(factionId);
        if (faction == null) {
            return;
        }
        for (UUID member : faction.getMembers().keySet()) {
            playerFaction.remove(member);
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
        long key = ChunkPos.asLong(pos);
        UUID factionId = claims.get(key);
        if (factionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factions.get(factionId));
    }

    public boolean isClaimed(BlockPos pos) {
        return claims.containsKey(ChunkPos.asLong(pos));
    }

    public Optional<UUID> getClaimOwner(BlockPos pos) {
        return Optional.ofNullable(claims.get(ChunkPos.asLong(pos)));
    }

    public boolean hasPermission(Player player, BlockPos pos, FactionPermission permission) {
        Optional<UUID> ownerId = getClaimOwner(pos);
        if (ownerId.isEmpty()) {
            return true;
        }
        Optional<Faction> faction = getFaction(ownerId.get());
        if (faction.isEmpty()) {
            return true;
        }
        return faction.get().hasPermission(player.getUUID(), permission);
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
}
