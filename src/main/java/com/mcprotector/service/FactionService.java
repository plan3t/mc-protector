package com.mcprotector.service;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.webmap.WebmapBridge;
import com.mcprotector.network.FactionClaimMapPacket;
import com.mcprotector.network.NetworkHandler;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
                if (data.isSafeZoneClaimed(chunk)) {
                    source.sendFailure(Component.literal("This chunk is a safe zone and cannot be claimed."));
                } else {
                    source.sendFailure(Component.literal("This chunk is already claimed."));
                }
            } else {
                source.sendFailure(Component.literal("Your faction has reached its claim limit."));
            }
            return 0;
        }
        WebmapBridge.updateClaim(chunk, faction, player.level().dimension().location().toString());
        LAST_CLAIM.put(player.getUUID(), now);
        source.sendSuccess(() -> Component.literal("Chunk claimed for " + faction.get().getName()), false);
        syncClaimMap(player.serverLevel());
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
            if (data.isSafeZoneClaimed(chunk)) {
                source.sendFailure(Component.literal("Safe zone claims can only be removed by a server operator."));
            } else {
                source.sendFailure(Component.literal("Your faction does not own this chunk."));
            }
            return 0;
        }
        WebmapBridge.updateClaim(chunk, Optional.empty(), player.level().dimension().location().toString());
        LAST_UNCLAIM.put(player.getUUID(), now);
        source.sendSuccess(() -> Component.literal("Chunk unclaimed."), false);
        syncClaimMap(player.serverLevel());
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
        String role = Faction.normalizeRoleName(roleName);
        if (!faction.get().hasRole(role)) {
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
        source.sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + permission.name() + " for " + role), true);
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
        Optional<UUID> ownerId = data.getClaimOwner(chunk);
        if (ownerId.isPresent() && FactionConfig.SERVER.protectOfflineFactions.get()
            && !data.isFactionOnline(player.serverLevel(), ownerId.get())) {
            source.sendFailure(Component.literal("You cannot overtake claims from an offline faction."));
            return 0;
        }
        if (!data.overtakeChunk(chunk, faction.get().getId())) {
            if (data.isSafeZoneClaimed(chunk)) {
                source.sendFailure(Component.literal("Safe zone claims cannot be overtaken."));
            } else if (data.isClaimed(chunk) && data.getClaimOwner(chunk).isPresent()) {
                source.sendFailure(Component.literal("You cannot overtake this chunk unless you are at war with the owner and have claim capacity."));
            } else {
                source.sendFailure(Component.literal("This chunk cannot be overtaken."));
            }
            return 0;
        }
        WebmapBridge.updateClaim(chunk, faction, player.level().dimension().location().toString());
        source.sendSuccess(() -> Component.literal("Chunk overtaken for " + faction.get().getName()), false);
        if (ownerId.isPresent()) {
            boolean breakawayComplete = data.recordVassalBreakawayCapture(faction.get().getId(), ownerId.get());
            if (breakawayComplete) {
                data.clearRelation(faction.get().getId(), ownerId.get());
                notifyFactionMembers(player, data, faction.get().getId(),
                    "Your faction has won its breakaway war and is now independent.");
                notifyFactionMembers(player, data, ownerId.get(),
                    "Your vassal has won their breakaway war and is now independent.");
            }
        }
        syncClaimMap(player.serverLevel());
        return 1;
    }

    public static int syncDynmap(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WebmapBridge.syncClaims(player.serverLevel(), FactionData.get(player.serverLevel()));
        source.sendSuccess(() -> Component.literal("Synced faction claims to web maps."), false);
        return 1;
    }

    public static int toggleFactionChunks(CommandSourceStack source, Iterable<ChunkPos> chunks) throws CommandSyntaxException {
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
        int claimed = 0;
        int unclaimed = 0;
        for (ChunkPos chunk : chunks) {
            if (data.unclaimChunk(chunk, faction.get().getId())) {
                WebmapBridge.updateClaim(chunk, Optional.empty(), player.level().dimension().location().toString());
                unclaimed++;
                continue;
            }
            if (data.claimChunk(chunk, faction.get().getId())) {
                WebmapBridge.updateClaim(chunk, faction, player.level().dimension().location().toString());
                claimed++;
            }
        }
        if (claimed == 0 && unclaimed == 0) {
            source.sendFailure(Component.literal("No chunks were updated."));
            return 0;
        }
        String message = "Claimed " + claimed + " chunk(s), unclaimed " + unclaimed + " chunk(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        syncClaimMap(player.serverLevel());
        return 1;
    }

    public static int togglePersonalChunks(CommandSourceStack source, Iterable<ChunkPos> chunks) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        if (!isClaimingAllowed(source)) {
            return 0;
        }
        int claimed = 0;
        int unclaimed = 0;
        for (ChunkPos chunk : chunks) {
            if (data.unclaimPersonalChunk(chunk, player.getUUID())) {
                unclaimed++;
                continue;
            }
            if (data.claimPersonalChunk(chunk, player.getUUID())) {
                claimed++;
            }
        }
        if (claimed == 0 && unclaimed == 0) {
            source.sendFailure(Component.literal("No personal chunks were updated."));
            return 0;
        }
        String message = "Claimed " + claimed + " personal chunk(s), unclaimed " + unclaimed + " personal chunk(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        syncClaimMap(player.serverLevel());
        return 1;
    }

    public static int claimSafeZoneChunks(CommandSourceStack source, Iterable<ChunkPos> chunks, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!hasAdminPermission(source)) {
            source.sendFailure(Component.literal("Only server operators can create safe zones."));
            return 0;
        }
        if (factionName == null || factionName.isBlank()) {
            source.sendFailure(Component.literal("Provide a faction name for the safe zone claim."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        String trimmedName = factionName.trim();
        Optional<Faction> faction = data.findFactionByName(trimmedName);
        if (faction.isEmpty()) {
            faction = Optional.of(data.createSystemFaction(trimmedName));
        }
        int claimed = 0;
        for (ChunkPos chunk : chunks) {
            if (data.claimSafeZoneChunk(chunk, faction.get().getId())) {
                WebmapBridge.updateClaim(chunk, faction, player.level().dimension().location().toString());
                claimed++;
            }
        }
        if (claimed == 0) {
            source.sendFailure(Component.literal("No safe zone chunks were claimed."));
            return 0;
        }
        String message = "Claimed " + claimed + " safe zone chunk(s) for " + faction.get().getName();
        source.sendSuccess(() -> Component.literal(message), false);
        syncClaimMap(player.serverLevel());
        return 1;
    }

    public static int toggleSafeZoneChunks(CommandSourceStack source, Iterable<ChunkPos> chunks, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!hasAdminPermission(source)) {
            source.sendFailure(Component.literal("Only server operators can create safe zones."));
            return 0;
        }
        if (factionName == null || factionName.isBlank()) {
            source.sendFailure(Component.literal("Provide a faction name for the safe zone claim."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        String trimmedName = factionName.trim();
        Optional<Faction> faction = data.findFactionByName(trimmedName);
        if (faction.isEmpty()) {
            faction = Optional.of(data.createSystemFaction(trimmedName));
        }
        int claimed = 0;
        int unclaimed = 0;
        for (ChunkPos chunk : chunks) {
            if (data.unclaimSafeZoneChunk(chunk)) {
                WebmapBridge.updateClaim(chunk, Optional.empty(), player.level().dimension().location().toString());
                unclaimed++;
                continue;
            }
            if (data.claimSafeZoneChunk(chunk, faction.get().getId())) {
                WebmapBridge.updateClaim(chunk, faction, player.level().dimension().location().toString());
                claimed++;
            }
        }
        if (claimed == 0 && unclaimed == 0) {
            source.sendFailure(Component.literal("No safe zone chunks were updated."));
            return 0;
        }
        String message = "Claimed " + claimed + " safe zone chunk(s), unclaimed " + unclaimed + " safe zone chunk(s) for "
            + faction.get().getName();
        source.sendSuccess(() -> Component.literal(message), false);
        syncClaimMap(player.serverLevel());
        return 1;
    }

    public static int unclaimSafeZoneChunks(CommandSourceStack source, Iterable<ChunkPos> chunks) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!hasAdminPermission(source)) {
            source.sendFailure(Component.literal("Only server operators can remove safe zones."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        int removed = 0;
        for (ChunkPos chunk : chunks) {
            if (data.unclaimSafeZoneChunk(chunk)) {
                WebmapBridge.updateClaim(chunk, Optional.empty(), player.level().dimension().location().toString());
                removed++;
            }
        }
        if (removed == 0) {
            source.sendFailure(Component.literal("No safe zone chunks were removed."));
            return 0;
        }
        String message = "Removed " + removed + " safe zone chunk(s).";
        source.sendSuccess(() -> Component.literal(message), false);
        syncClaimMap(player.serverLevel());
        return 1;
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(FactionConfig.SERVER.adminBypassPermissionLevel.get());
    }

    private static void syncClaimMap(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        }
    }

    private static void notifyFactionMembers(ServerPlayer sender, FactionData data, UUID factionId, String message) {
        for (ServerPlayer recipient : sender.server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isPresent() && recipientFactionId.get().equals(factionId)) {
                recipient.sendSystemMessage(Component.literal(message));
            }
        }
    }

    public static int joinFaction(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        if (data.getFactionByPlayer(player.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("You already belong to a faction."));
            return 0;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> faction = data.findFactionByName(trimmed);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<FactionData.FactionInvite> invite = data.getInvite(player.getUUID());
        if (invite.isEmpty() || !invite.get().factionId().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("You do not have an invite to that faction."));
            return 0;
        }
        if (!data.addMember(faction.get().getId(), player.getUUID(), Faction.ROLE_MEMBER)) {
            source.sendFailure(Component.literal("Failed to join faction."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Joined faction " + faction.get().getName()), true);
        return 1;
    }

    public static int declineInvite(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        if (data.getInvite(player.getUUID()).isEmpty()) {
            source.sendFailure(Component.literal("You have no pending invites."));
            return 0;
        }
        data.clearInvite(player.getUUID());
        source.sendSuccess(() -> Component.literal("Invite declined."), true);
        return 1;
    }

    public static int leaveFaction(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (faction.get().getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("The owner must disband the faction."));
            return 0;
        }
        if (!data.removeMember(player.getUUID())) {
            source.sendFailure(Component.literal("Failed to leave the faction."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You left the faction."), true);
        return 1;
    }

    public static int kickMember(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_MEMBERS)) {
            source.sendFailure(Component.literal("You lack permission to manage members."));
            return 0;
        }
        Optional<UUID> targetFactionId = data.getFactionIdByPlayer(target.getUUID());
        if (targetFactionId.isEmpty() || !targetFactionId.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("That player is not in your faction."));
            return 0;
        }
        if (faction.get().getOwner().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You cannot kick the owner."));
            return 0;
        }
        if (!data.removeMember(target.getUUID())) {
            source.sendFailure(Component.literal("Failed to remove member."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + " from the faction."), true);
        target.sendSystemMessage(Component.literal("You were removed from " + faction.get().getName() + "."));
        return 1;
    }

    public static int setRole(CommandSourceStack source, ServerPlayer target, String roleName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_MEMBERS)) {
            source.sendFailure(Component.literal("You lack permission to manage members."));
            return 0;
        }
        if (faction.get().getOwner().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You cannot change the owner's role."));
            return 0;
        }
        String role = Faction.normalizeRoleName(roleName);
        if (!faction.get().hasRole(role)) {
            source.sendFailure(Component.literal("Unknown role."));
            return 0;
        }
        if (Faction.ROLE_OWNER.equals(role)) {
            source.sendFailure(Component.literal("You cannot assign the owner role."));
            return 0;
        }
        Optional<UUID> targetFactionId = data.getFactionIdByPlayer(target.getUUID());
        if (targetFactionId.isEmpty() || !targetFactionId.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("That player is not in your faction."));
            return 0;
        }
        faction.get().setRole(target.getUUID(), role);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString() + " to " + role), true);
        return 1;
    }

    public static int updateRelationPermission(CommandSourceStack source, String relationName, String permissionName, boolean grant)
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
        FactionRelation relation;
        try {
            relation = FactionRelation.valueOf(relationName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown relation."));
            return 0;
        }
        if (relation == FactionRelation.NEUTRAL) {
            source.sendFailure(Component.literal("Neutral relations do not have configurable permissions."));
            return 0;
        }
        FactionPermission permission;
        try {
            permission = FactionPermission.valueOf(permissionName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown permission."));
            return 0;
        }
        EnumSet<FactionPermission> perms = EnumSet.copyOf(faction.get().getRelationPermissions(relation));
        if (grant) {
            perms.add(permission);
        } else {
            perms.remove(permission);
        }
        faction.get().setRelationPermissions(relation, perms);
        data.setDirty();
        source.sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + permission.name() + " for " + relation.name()), true);
        return 1;
    }

    public static int addRule(CommandSourceStack source, String rule) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_SETTINGS)) {
            source.sendFailure(Component.literal("You lack permission to manage faction rules."));
            return 0;
        }
        if (!faction.get().addRule(rule)) {
            source.sendFailure(Component.literal("Rule was empty or already exists."));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Added a new faction rule."), true);
        return 1;
    }

    public static int removeRule(CommandSourceStack source, String rule) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_SETTINGS)) {
            source.sendFailure(Component.literal("You lack permission to manage faction rules."));
            return 0;
        }
        if (!faction.get().removeRule(rule)) {
            source.sendFailure(Component.literal("Rule not found."));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Removed a faction rule."), true);
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
        if (Faction.ROLE_OWNER.equals(faction.getRole(playerId))) {
            return Math.max(0, (int) Math.ceil(baseSeconds * Math.max(0.0, multiplier)));
        }
        return baseSeconds;
    }

    public static int createRole(CommandSourceStack source, String roleName, String displayName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        Faction factionData = faction.get();
        if (!factionData.hasPermission(player.getUUID(), FactionPermission.MANAGE_PERMISSIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage roles."));
            return 0;
        }
        String normalized = Faction.normalizeRoleName(roleName);
        if (normalized.isBlank()) {
            source.sendFailure(Component.literal("Role name cannot be empty."));
            return 0;
        }
        if (Faction.isReservedRole(normalized)) {
            source.sendFailure(Component.literal("That role name is reserved."));
            return 0;
        }
        if (!factionData.addRole(normalized, displayName)) {
            source.sendFailure(Component.literal("That role already exists."));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Created role " + normalized + "."), true);
        return 1;
    }

    public static int deleteRole(CommandSourceStack source, String roleName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        Faction factionData = faction.get();
        if (!factionData.hasPermission(player.getUUID(), FactionPermission.MANAGE_PERMISSIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage roles."));
            return 0;
        }
        String normalized = Faction.normalizeRoleName(roleName);
        if (!factionData.hasRole(normalized)) {
            source.sendFailure(Component.literal("Role not found."));
            return 0;
        }
        if (Faction.isReservedRole(normalized)) {
            source.sendFailure(Component.literal("You cannot remove a reserved role."));
            return 0;
        }
        for (Map.Entry<UUID, String> entry : factionData.getMembers().entrySet()) {
            if (normalized.equals(entry.getValue())) {
                if (factionData.getOwner().equals(entry.getKey())) {
                    factionData.setRole(entry.getKey(), Faction.ROLE_OWNER);
                } else {
                    factionData.setRole(entry.getKey(), Faction.ROLE_MEMBER);
                }
            }
        }
        if (!factionData.removeRole(normalized)) {
            source.sendFailure(Component.literal("Failed to remove role."));
            return 0;
        }
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Removed role " + normalized + "."), true);
        return 1;
    }
}
