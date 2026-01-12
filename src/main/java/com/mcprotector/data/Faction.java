package com.mcprotector.data;

import com.mcprotector.config.FactionConfig;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Faction {
    private final UUID id;
    private String name;
    private final UUID owner;
    private final Map<UUID, FactionRole> members = new HashMap<>();
    private final EnumMap<FactionRole, EnumSet<FactionPermission>> permissions = new EnumMap<>(FactionRole.class);
    private final EnumMap<FactionRole, String> rankNames = new EnumMap<>(FactionRole.class);
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
        members.put(owner, FactionRole.OWNER);
        applyDefaults();
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

    public EnumMap<FactionRelation, EnumSet<FactionPermission>> getRelationPermissions() {
        return relationPermissions;
    }

    public EnumSet<FactionPermission> getRelationPermissions(FactionRelation relation) {
        return relationPermissions.getOrDefault(relation, EnumSet.noneOf(FactionPermission.class));
    }

    public void setRelationPermissions(FactionRelation relation, EnumSet<FactionPermission> permissions) {
        relationPermissions.put(relation, permissions);
    }

    public String getRankName(FactionRole role) {
        return rankNames.getOrDefault(role, role.name());
    }

    public void setRankName(FactionRole role, String name) {
        rankNames.put(role, name);
    }

    public EnumMap<FactionRole, String> getRankNames() {
        return rankNames;
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
        rankNames.putAll(FactionConfig.getDefaultRankNames());
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
}
