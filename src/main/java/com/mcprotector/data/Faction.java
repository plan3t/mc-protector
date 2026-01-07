package com.mcprotector.data;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Faction {
    private final UUID id;
    private String name;
    private final UUID owner;
    private final Map<UUID, FactionRole> members = new HashMap<>();
    private final EnumMap<FactionRole, EnumSet<FactionPermission>> permissions = new EnumMap<>(FactionRole.class);

    public Faction(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        members.put(owner, FactionRole.OWNER);
        permissions.put(FactionRole.OWNER, EnumSet.allOf(FactionPermission.class));
        permissions.put(FactionRole.OFFICER, EnumSet.allOf(FactionPermission.class));
        permissions.put(FactionRole.MEMBER, EnumSet.of(
            FactionPermission.BLOCK_BREAK,
            FactionPermission.BLOCK_PLACE,
            FactionPermission.BLOCK_USE,
            FactionPermission.CONTAINER_OPEN,
            FactionPermission.REDSTONE_TOGGLE,
            FactionPermission.ENTITY_INTERACT,
            FactionPermission.CREATE_MACHINE_INTERACT,
            FactionPermission.CHUNK_CLAIM
        ));
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<UUID, FactionRole> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public void setRole(UUID player, FactionRole role) {
        members.put(player, role);
    }

    public FactionRole getRole(UUID player) {
        return members.get(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean hasPermission(UUID player, FactionPermission permission) {
        FactionRole role = members.get(player);
        if (role == null) {
            return false;
        }
        Set<FactionPermission> allowed = permissions.get(role);
        return allowed != null && allowed.contains(permission);
    }

    public EnumMap<FactionRole, EnumSet<FactionPermission>> getPermissions() {
        return permissions;
    }

    public void setPermissions(FactionRole role, EnumSet<FactionPermission> perms) {
        permissions.put(role, perms);
    }
}
