package com.mcprotector.service;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRole;
import com.mcprotector.dynmap.DynmapBridge;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionService {
    private static final ConcurrentHashMap<UUID, Long> LAST_CLAIM = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> LAST_UNCLAIM = new ConcurrentHashMap<>();

    private FactionService() {
    }

    public static int claimChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        return claimChunk(source, chunk);
    }

    public static int claimChunk(CommandSourceStack source, ChunkPos chunk) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.CHUNK_CLAIM)) {
            source.sendFailure(Component.literal("You lack permission to claim chunks."));
            return 0;
        }
        if (!isClaimingAllowed(source)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int level = data.getFactionLevel(faction.get().getId());
        int baseCooldownSeconds = Math.max(0, FactionConfig.SERVER.claimCooldownSeconds.get()
            - (level - 1) * FactionConfig.SERVER.claimCooldownReductionPerLevel.get());
        int cooldownSeconds = applyOwnerCooldownMultiplier(baseCooldownSeconds, faction.get(), player.getUUID(),
            FactionConfig.SERVER.claimCooldownOwnerMultiplier.get());
        long lastClaim = LAST_CLAIM.getOrDefault(player.getUUID(), 0L);
        if (now - lastClaim < cooldownSeconds * 1000L) {
            source.sendFailure(Component.literal("You must wait before claiming again."));
            return 0;
        }
        if (!data.claimChunk(chunk, faction.get().getId())) {
            if (data.isClaimed(chunk)) {
                source.sendFailure(Component.literal("This chunk is already claimed."));
            } else {
                source.sendFailure(Component.literal("Your faction has reached its claim limit."));
            }
            return 0;
        }
        DynmapBridge.updateClaim(chunk, faction);
        LAST_CLAIM.put(player.getUUID(), now);
        source.sendSuccess(() -> Component.literal("Chunk claimed for " + faction.get().getName()), false);
        return 1;
    }

    public static int unclaimChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        return unclaimChunk(source, chunk);
    }

    public static int unclaimChunk(CommandSourceStack source, ChunkPos chunk) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.CHUNK_CLAIM)) {
            source.sendFailure(Component.literal("You lack permission to unclaim chunks."));
            return 0;
        }
        long now = System.currentTimeMillis();
        int level = data.getFactionLevel(faction.get().getId());
        int baseCooldownSeconds = Math.max(0, FactionConfig.SERVER.unclaimCooldownSeconds.get()
            - (level - 1) * FactionConfig.SERVER.unclaimCooldownReductionPerLevel.get());
        int cooldownSeconds = applyOwnerCooldownMultiplier(baseCooldownSeconds, faction.get(), player.getUUID(),
            FactionConfig.SERVER.unclaimCooldownOwnerMultiplier.get());
        long lastUnclaim = LAST_UNCLAIM.getOrDefault(player.getUUID(), 0L);
        if (now - lastUnclaim < cooldownSeconds * 1000L) {
            source.sendFailure(Component.literal("You must wait before unclaiming again."));
            return 0;
        }
        if (!data.unclaimChunk(chunk, faction.get().getId())) {
            source.sendFailure(Component.literal("Your faction does not own this chunk."));
            return 0;
        }
        DynmapBridge.updateClaim(chunk, Optional.empty());
        LAST_UNCLAIM.put(player.getUUID(), now);
        source.sendSuccess(() -> Component.literal("Chunk unclaimed."), false);
        return 1;
    }

    public static int invitePlayer(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You cannot invite yourself."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_MEMBERS)) {
            source.sendFailure(Component.literal("You lack permission to invite members."));
            return 0;
        }
        if (data.getFactionByPlayer(target.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("That player already belongs to a faction."));
            return 0;
        }
        data.invitePlayer(target.getUUID(), faction.get().getId());
        source.sendSuccess(() -> Component.literal("Invited " + target.getName().getString() + " to " + faction.get().getName()), true);
        int minutes = FactionConfig.SERVER.inviteExpirationMinutes.get();
        target.sendSystemMessage(Component.literal(
            "You have been invited to join " + faction.get().getName() + ". Use /faction join " + faction.get().getName()
                + ". Invite expires in " + minutes + " minute(s)."
        ));
        return 1;
    }

    public static int updatePermission(CommandSourceStack source, String roleName, String permissionName, boolean grant)
        throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_PERMISSIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage permissions."));
            return 0;
        }
        FactionRole role;
        try {
            role = FactionRole.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown role."));
            return 0;
        }
        FactionPermission permission;
        try {
            permission = FactionPermission.valueOf(permissionName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown permission."));
            return 0;
        }
        EnumSet<FactionPermission> perms = faction.get().getPermissions().getOrDefault(role, EnumSet.noneOf(FactionPermission.class));
        if (grant) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
        faction.get().setPermissions(role, perms);
        data.setDirty();
        source.sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + permission.name() + " for " + role.name()), true);
        return 1;
    }

    public static int overtakeChunk(CommandSourceStack source, ChunkPos chunk) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.CHUNK_OVERTAKE)) {
            source.sendFailure(Component.literal("You lack permission to overtake chunks."));
            return 0;
        }
        if (!isClaimingAllowed(source)) {
            return 0;
        }
        if (!data.overtakeChunk(chunk, faction.get().getId())) {
            if (data.isClaimed(chunk) && data.getClaimOwner(chunk).isPresent()) {
                source.sendFailure(Component.literal("You cannot overtake this chunk unless you are at war with the owner and have claim capacity."));
            } else {
                source.sendFailure(Component.literal("This chunk cannot be overtaken."));
            }
            return 0;
        }
        DynmapBridge.updateClaim(chunk, faction);
        source.sendSuccess(() -> Component.literal("Chunk overtaken for " + faction.get().getName()), false);
        return 1;
    }

    public static int syncDynmap(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DynmapBridge.syncClaims(FactionData.get(player.serverLevel()));
        source.sendSuccess(() -> Component.literal("Synced faction claims to Dynmap."), false);
        return 1;
    }

    private static boolean isClaimingAllowed(CommandSourceStack source) {
        String dimension = source.getLevel().dimension().location().toString();
        if (FactionConfig.SERVER.safeZoneDimensions.get().contains(dimension)) {
            source.sendFailure(Component.literal("Claiming is disabled in safe zone dimensions."));
            return false;
        }
        if (FactionConfig.SERVER.warZoneDimensions.get().contains(dimension)) {
            source.sendFailure(Component.literal("Claiming is disabled in war zone dimensions."));
            return false;
        }
        return true;
    }

    private static int applyOwnerCooldownMultiplier(int baseSeconds, Faction faction, UUID playerId, double multiplier) {
        if (faction.getRole(playerId) == FactionRole.OWNER) {
            return Math.max(0, (int) Math.ceil(baseSeconds * Math.max(0.0, multiplier)));
        }
        return baseSeconds;
    }
}
