package com.mcprotector.command;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.data.FactionRole;
import com.mcprotector.dynmap.DynmapBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public final class FactionCommands {
    private FactionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("faction")
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> createFaction(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("disband")
                    .executes(context -> disbandFaction(context.getSource())))
                .then(Commands.literal("claim")
                    .executes(context -> claimChunk(context.getSource())))
                .then(Commands.literal("unclaim")
                    .executes(context -> unclaimChunk(context.getSource())))
                .then(Commands.literal("overtake")
                    .executes(context -> overtakeChunk(context.getSource())))
                .then(Commands.literal("info")
                    .executes(context -> factionInfo(context.getSource())))
                .then(Commands.literal("invite")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> invitePlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("join")
                    .then(Commands.argument("faction", StringArgumentType.string())
                        .executes(context -> joinFaction(context.getSource(), StringArgumentType.getString(context, "faction")))))
                .then(Commands.literal("leave")
                    .executes(context -> leaveFaction(context.getSource())))
                .then(Commands.literal("kick")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> kickMember(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("promote")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setRole(context.getSource(), EntityArgument.getPlayer(context, "player"), FactionRole.OFFICER))))
                .then(Commands.literal("demote")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setRole(context.getSource(), EntityArgument.getPlayer(context, "player"), FactionRole.MEMBER))))
                .then(Commands.literal("perms")
                    .then(Commands.literal("list")
                        .executes(context -> listPermissions(context.getSource())))
                    .then(Commands.literal("add")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(context -> updatePermission(context.getSource(), StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "permission"), true)))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .then(Commands.argument("permission", StringArgumentType.word())
                                .executes(context -> updatePermission(context.getSource(), StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "permission"), false))))))
                .then(Commands.literal("claiminfo")
                    .executes(context -> claimInfo(context.getSource())))
                .then(Commands.literal("map")
                    .executes(context -> factionMap(context.getSource(), 4))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                        .executes(context -> factionMap(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
        );
    }

    private static int createFaction(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        if (data.getFactionByPlayer(player.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("You already belong to a faction."));
            return 0;
        }
        Faction faction = data.createFaction(name, player);
        source.sendSuccess(() -> Component.literal("Created faction " + faction.getName()), false);
        return 1;
    }

    private static int disbandFaction(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the owner can disband the faction."));
            return 0;
        }
        UUID factionId = faction.get().getId();
        for (var entry : data.getClaims().entrySet()) {
            if (factionId.equals(entry.getValue())) {
                ChunkPos chunkPos = new ChunkPos(entry.getKey());
                DynmapBridge.updateClaim(chunkPos, Optional.empty());
            }
        }
        data.disbandFaction(factionId);
        source.sendSuccess(() -> Component.literal("Faction disbanded."), true);
        return 1;
    }

    private static int claimChunk(CommandSourceStack source) {
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
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (!data.claimChunk(chunk, faction.get().getId())) {
            if (data.isClaimed(player.blockPosition())) {
                source.sendFailure(Component.literal("This chunk is already claimed."));
            } else {
                source.sendFailure(Component.literal("Your faction has reached its claim limit."));
            }
            return 0;
        }
        DynmapBridge.updateClaim(chunk, faction);
        source.sendSuccess(() -> Component.literal("Chunk claimed for " + faction.get().getName()), false);
        return 1;
    }

    private static int unclaimChunk(CommandSourceStack source) {
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
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (!data.unclaimChunk(chunk, faction.get().getId())) {
            source.sendFailure(Component.literal("Your faction does not own this chunk."));
            return 0;
        }
        DynmapBridge.updateClaim(chunk, Optional.empty());
        source.sendSuccess(() -> Component.literal("Chunk unclaimed."), false);
        return 1;
    }

    private static int overtakeChunk(CommandSourceStack source) {
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
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (!data.overtakeChunk(chunk, faction.get().getId())) {
            if (data.isClaimed(player.blockPosition()) && data.getClaimOwner(player.blockPosition()).isPresent()) {
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

    private static int factionInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        int claims = data.getClaimCount(faction.get().getId());
        int maxClaims = data.getMaxClaims(faction.get().getId());
        FactionRole role = faction.get().getRole(player.getUUID());
        source.sendSuccess(() -> Component.literal("Faction: " + faction.get().getName()
            + " | Role: " + role.name()
            + " | Claims: " + claims + "/" + maxClaims
            + " | Members: " + faction.get().getMemberCount()), false);
        return 1;
    }

    private static int invitePlayer(CommandSourceStack source, ServerPlayer target) {
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
        target.sendSystemMessage(Component.literal("You have been invited to join " + faction.get().getName() + ". Use /faction join " + faction.get().getName() + "."));
        return 1;
    }

    private static int joinFaction(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        if (data.getFactionByPlayer(player.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("You already belong to a faction."));
            return 0;
        }
        Optional<Faction> faction = data.findFactionByName(name);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<UUID> invite = data.getInvite(player.getUUID());
        if (invite.isEmpty() || !invite.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("You do not have an invite to that faction."));
            return 0;
        }
        if (!data.addMember(faction.get().getId(), player.getUUID(), FactionRole.MEMBER)) {
            source.sendFailure(Component.literal("Failed to join faction."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Joined faction " + faction.get().getName()), true);
        return 1;
    }

    private static int leaveFaction(CommandSourceStack source) {
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

    private static int kickMember(CommandSourceStack source, ServerPlayer target) {
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

    private static int setRole(CommandSourceStack source, ServerPlayer target, FactionRole role) {
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
        Optional<UUID> targetFactionId = data.getFactionIdByPlayer(target.getUUID());
        if (targetFactionId.isEmpty() || !targetFactionId.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("That player is not in your faction."));
            return 0;
        }
        faction.get().setRole(target.getUUID(), role);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString() + " to " + role.name()), true);
        return 1;
    }

    private static int listPermissions(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        StringBuilder message = new StringBuilder("Permissions:");
        for (var entry : faction.get().getPermissions().entrySet()) {
            message.append("\n").append(entry.getKey().name()).append(": ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int updatePermission(CommandSourceStack source, String roleName, String permissionName, boolean grant) {
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

    private static int claimInfo(CommandSourceStack source) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<UUID> ownerId = data.getClaimOwner(player.blockPosition());
        if (ownerId.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This chunk is unclaimed."), false);
            return 1;
        }
        Optional<Faction> owner = data.getFaction(ownerId.get());
        if (owner.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This chunk is claimed by an unknown faction."), false);
            return 1;
        }
        Optional<UUID> playerFaction = data.getFactionIdByPlayer(player.getUUID());
        FactionRelation relation = FactionRelation.NEUTRAL;
        if (playerFaction.isPresent()) {
            relation = data.getRelation(playerFaction.get(), ownerId.get());
        }
        source.sendSuccess(() -> Component.literal("Claim owner: " + owner.get().getName() + " | Relation: " + relation.name()), false);
        return 1;
    }

    private static int factionMap(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        ChunkPos center = new ChunkPos(player.blockPosition());
        Optional<UUID> playerFaction = data.getFactionIdByPlayer(player.getUUID());
        StringBuilder map = new StringBuilder("Chunk map (radius " + radius + "):");
        for (int dz = -radius; dz <= radius; dz++) {
            map.append("\n");
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                char symbol = '.';
                BlockPos blockPos = new BlockPos(pos.getMinBlockX(), player.blockPosition().getY(), pos.getMinBlockZ());
                Optional<UUID> ownerId = data.getClaimOwner(blockPos);
                if (ownerId.isPresent()) {
                    if (playerFaction.isPresent() && ownerId.get().equals(playerFaction.get())) {
                        symbol = 'O';
                    } else if (playerFaction.isPresent()) {
                        FactionRelation relation = data.getRelation(playerFaction.get(), ownerId.get());
                        symbol = relation == FactionRelation.ALLY ? 'A' : relation == FactionRelation.WAR ? 'W' : 'N';
                    } else {
                        symbol = 'N';
                    }
                }
                if (dx == 0 && dz == 0) {
                    symbol = '*';
                }
                map.append(symbol);
            }
        }
        map.append("\nLegend: * you, O owned, A ally, W war, N neutral, . unclaimed");
        source.sendSuccess(() -> Component.literal(map.toString()), false);
        return 1;
    }
}
