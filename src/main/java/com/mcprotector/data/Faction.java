package com.mcprotector.data;

import com.mcprotector.config.FactionConfig;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Faction {
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_OFFICER = "OFFICER";
    public static final String ROLE_MEMBER = "MEMBER";
    private static final Set<String> RESERVED_ROLES = Set.of(ROLE_OWNER, ROLE_OFFICER, ROLE_MEMBER);
    private final UUID id;
    private String name;
    private final UUID owner;
    private final Map<UUID, String> members = new HashMap<>();
    private final Map<String, EnumSet<FactionPermission>> permissions = new LinkedHashMap<>();
    private final Map<String, String> roleDisplayNames = new LinkedHashMap<>();
    private final EnumMap<FactionRelation, EnumSet<FactionPermission>> relationPermissions = new EnumMap<>(FactionRelation.class);
    private final Set<UUID> trustedPlayers = new HashSet<>();
    private final List<String> rules = new ArrayList<>();
    private String colorName;
    private String motd;
    private String description;
    private String bannerColor;
    private FactionProtectionTier protectionTier;

    public Faction(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        members.put(owner, ROLE_OWNER);
        applyDefaults();
        permissions.put(ROLE_OWNER, EnumSet.allOf(FactionPermission.class));
        permissions.put(ROLE_OFFICER, EnumSet.allOf(FactionPermission.class));
        permissions.put(ROLE_MEMBER, EnumSet.of(
            FactionPermission.BLOCK_BREAK,
            FactionPermission.BLOCK_PLACE,
            FactionPermission.BLOCK_USE,
            FactionPermission.CONTAINER_OPEN,
            FactionPermission.REDSTONE_TOGGLE,
            FactionPermission.ENTITY_INTERACT,
            FactionPermission.CREATE_MACHINE_INTERACT,
            FactionPermission.CHUNK_CLAIM
        ));
        applyRelationDefaults();
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

    public Map<UUID, String> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public void setRole(UUID player, String role) {
        String normalized = normalizeRoleName(role);
        if (!roleDisplayNames.containsKey(normalized)) {
            normalized = ROLE_MEMBER;
        }
        members.put(player, normalized);
    }

    public String getRole(UUID player) {
        return members.get(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean hasPermission(UUID player, FactionPermission permission) {
        String role = members.get(player);
        if (role == null || role.isBlank()) {
            return false;
        }
        Set<FactionPermission> allowed = permissions.get(normalizeRoleName(role));
        return allowed != null && allowed.contains(permission);
    }

    public Map<String, EnumSet<FactionPermission>> getPermissions() {
        return permissions;
    }

    public void setPermissions(String role, EnumSet<FactionPermission> perms) {
        String normalized = normalizeRoleName(role);
        if (normalized.isBlank()) {
            return;
        }
        permissions.put(normalized, perms);
    }

    public EnumMap<FactionRelation, EnumSet<FactionPermission>> getRelationPermissions() {
        return relationPermissions;
    }

    public EnumSet<FactionPermission> getRelationPermissions(FactionRelation relation) {
        return relationPermissions.getOrDefault(relation, EnumSet.noneOf(FactionPermission.class));
    }

    public void setRelationPermissions(FactionRelation relation, EnumSet<FactionPermission> permissions) {
        relationPermissions.put(relation, permissions);
    }

    public String getRoleDisplayName(String role) {
        String normalized = normalizeRoleName(role);
        return roleDisplayNames.getOrDefault(normalized, normalized);
    }

    public void setRoleDisplayName(String role, String name) {
        String normalized = normalizeRoleName(role);
        if (normalized.isBlank()) {
            return;
        }
        String displayName = name == null || name.isBlank() ? normalized : name.trim();
        roleDisplayNames.put(normalized, displayName);
    }

    public Map<String, String> getRoleDisplayNames() {
        return roleDisplayNames;
    }

    public List<String> getRoleNames() {
        return new ArrayList<>(roleDisplayNames.keySet());
    }

    public boolean hasRole(String role) {
        String normalized = normalizeRoleName(role);
        return roleDisplayNames.containsKey(normalized);
    }

    public boolean addRole(String roleName, String displayName) {
        String normalized = normalizeRoleName(roleName);
        if (normalized.isBlank() || roleDisplayNames.containsKey(normalized)) {
            return false;
        }
        String display = displayName == null || displayName.isBlank() ? normalized : displayName.trim();
        roleDisplayNames.put(normalized, display);
        permissions.putIfAbsent(normalized, EnumSet.noneOf(FactionPermission.class));
        return true;
    }

    public boolean removeRole(String roleName) {
        String normalized = normalizeRoleName(roleName);
        if (normalized.isBlank() || isReservedRole(normalized)) {
            return false;
        }
        if (roleDisplayNames.remove(normalized) == null) {
            return false;
        }
        permissions.remove(normalized);
        return true;
    }

    public Set<UUID> getTrustedPlayers() {
        return trustedPlayers;
    }

    public void addTrustedPlayer(UUID playerId) {
        trustedPlayers.add(playerId);
    }

    public void removeTrustedPlayer(UUID playerId) {
        trustedPlayers.remove(playerId);
    }

    public boolean isTrusted(UUID playerId) {
        return trustedPlayers.contains(playerId);
    }

    public String getColorName() {
        return colorName;
    }

    public ChatFormatting getColor() {
        return FactionConfig.parseColor(colorName);
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBannerColor() {
        return bannerColor;
    }

    public void setBannerColor(String bannerColor) {
        this.bannerColor = bannerColor;
    }

    public FactionProtectionTier getProtectionTier() {
        return protectionTier;
    }

    public void setProtectionTier(FactionProtectionTier protectionTier) {
        this.protectionTier = protectionTier;
    }

    public List<String> getRules() {
        return rules;
    }

    public boolean addRule(String rule) {
        String trimmed = rule == null ? "" : rule.trim();
        if (trimmed.isEmpty() || rules.contains(trimmed)) {
            return false;
        }
        rules.add(trimmed);
        return true;
    }

    public boolean removeRule(String rule) {
        String trimmed = rule == null ? "" : rule.trim();
        return rules.remove(trimmed);
    }

    private void applyDefaults() {
        colorName = FactionConfig.getDefaultColorName();
        motd = FactionConfig.getDefaultMotd();
        description = FactionConfig.getDefaultDescription();
        bannerColor = FactionConfig.getDefaultBannerColor();
        protectionTier = FactionConfig.getDefaultProtectionTier();
        roleDisplayNames.putAll(FactionConfig.getDefaultRoleDisplayNames());
    }

    private void applyRelationDefaults() {
        relationPermissions.put(FactionRelation.ALLY, defaultAllyPermissions());
        relationPermissions.put(FactionRelation.WAR, defaultWarPermissions());
    }

    private EnumSet<FactionPermission> defaultAllyPermissions() {
        return switch (protectionTier) {
            case STRICT -> EnumSet.of(FactionPermission.BLOCK_USE);
            case RELAXED -> EnumSet.of(
                FactionPermission.BLOCK_USE,
                FactionPermission.CONTAINER_OPEN,
                FactionPermission.ENTITY_INTERACT,
                FactionPermission.CREATE_MACHINE_INTERACT,
                FactionPermission.REDSTONE_TOGGLE
            );
            case STANDARD -> EnumSet.of(
                FactionPermission.BLOCK_USE,
                FactionPermission.CONTAINER_OPEN,
                FactionPermission.ENTITY_INTERACT,
                FactionPermission.CREATE_MACHINE_INTERACT
            );
        };
    }

    private EnumSet<FactionPermission> defaultWarPermissions() {
        return EnumSet.of(
            FactionPermission.BLOCK_BREAK,
            FactionPermission.BLOCK_PLACE,
            FactionPermission.BLOCK_USE,
            FactionPermission.CONTAINER_OPEN,
            FactionPermission.REDSTONE_TOGGLE,
            FactionPermission.ENTITY_INTERACT,
            FactionPermission.CREATE_MACHINE_INTERACT
        );
    }

    public static String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return "";
        }
        return roleName.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isReservedRole(String roleName) {
        String normalized = normalizeRoleName(roleName);
        return RESERVED_ROLES.contains(normalized);
    }

    public void ensureReservedRoles() {
        Map<String, String> defaults = FactionConfig.getDefaultRoleDisplayNames();
        for (String reserved : RESERVED_ROLES) {
            roleDisplayNames.putIfAbsent(reserved, defaults.getOrDefault(reserved, reserved));
            permissions.putIfAbsent(reserved, EnumSet.noneOf(FactionPermission.class));
        }
    }

    public void clearRolesAndPermissions() {
        roleDisplayNames.clear();
        permissions.clear();
    }
}
