package com.mcprotector.command;

import com.mcprotector.chat.FactionChatManager;
import com.mcprotector.chat.FactionChatMode;
import com.mcprotector.claim.FactionClaimManager;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData.FactionAccessLog;
import com.mcprotector.data.FactionData.FactionInvite;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionProtectionTier;
import com.mcprotector.data.FactionRelation;
import com.mcprotector.protection.FactionBypassManager;
import com.mcprotector.webmap.WebmapBridge;
import com.mcprotector.service.FactionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionCommands {
    private static final long CONFIRM_TIMEOUT_MILLIS = 10_000L;
    private static final ConcurrentHashMap<UUID, Long> DISBAND_CONFIRMATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> OVERTAKE_CONFIRMATIONS = new ConcurrentHashMap<>();
    private FactionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("faction")
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> createFaction(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("rename")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> renameFaction(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("disband")
                    .executes(context -> disbandFaction(context.getSource()))
                    .then(Commands.literal("confirm")
                        .executes(context -> confirmDisband(context.getSource()))))
                .then(Commands.literal("claim")
                    .executes(context -> claimChunk(context.getSource()))
                    .then(Commands.literal("auto")
                        .executes(context -> toggleAutoClaim(context.getSource(), null))
                        .then(Commands.argument("state", StringArgumentType.word())
                            .suggests(FactionCommandSuggestions::onOff)
                            .executes(context -> toggleAutoClaim(context.getSource(), StringArgumentType.getString(context, "state"))))))
                .then(Commands.literal("unclaim")
                    .executes(context -> unclaimChunk(context.getSource())))
                .then(Commands.literal("overtake")
                    .executes(context -> overtakeChunk(context.getSource()))
                    .then(Commands.literal("confirm")
                        .executes(context -> confirmOvertake(context.getSource()))))
                .then(Commands.literal("info")
                    .executes(context -> factionInfo(context.getSource())))
                .then(Commands.literal("list")
                    .executes(context -> listFactions(context.getSource())))
                .then(Commands.literal("chat")
                    .executes(context -> showChatMode(context.getSource()))
                    .then(Commands.literal("toggle")
                        .executes(context -> toggleChatMode(context.getSource())))
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(FactionCommandSuggestions::chatModes)
                        .executes(context -> setChatMode(context.getSource(), StringArgumentType.getString(context, "mode")))))
                .then(Commands.literal("invite")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> invitePlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("join")
                    .then(Commands.argument("faction", StringArgumentType.greedyString())
                        .suggests(FactionCommandSuggestions::factionNames)
                        .executes(context -> joinFaction(context.getSource(), StringArgumentType.getString(context, "faction")))))
                .then(Commands.literal("leave")
                    .executes(context -> leaveFaction(context.getSource())))
                .then(Commands.literal("kick")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> kickMember(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("promote")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setRole(context.getSource(), EntityArgument.getPlayer(context, "player"), Faction.ROLE_OFFICER))))
                .then(Commands.literal("demote")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> setRole(context.getSource(), EntityArgument.getPlayer(context, "player"), Faction.ROLE_MEMBER))))
                .then(Commands.literal("role")
                    .then(Commands.literal("list")
                        .executes(context -> listRoles(context.getSource())))
                    .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> addRole(context.getSource(), StringArgumentType.getString(context, "name"), null))
                            .then(Commands.argument("display", StringArgumentType.greedyString())
                                .executes(context -> addRole(context.getSource(), StringArgumentType.getString(context, "name"),
                                    StringArgumentType.getString(context, "display"))))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> removeRole(context.getSource(), StringArgumentType.getString(context, "name"))))))
                    .then(Commands.literal("perms")
                    .then(Commands.literal("list")
                        .executes(context -> listPermissions(context.getSource())))
                    .then(Commands.literal("add")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .then(Commands.argument("permission", StringArgumentType.word())
                                .suggests(FactionCommandSuggestions::permissions)
                                .executes(context -> updatePermission(context.getSource(), StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "permission"), true)))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .then(Commands.argument("permission", StringArgumentType.word())
                                .suggests(FactionCommandSuggestions::permissions)
                                .executes(context -> updatePermission(context.getSource(), StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "permission"), false))))))
                .then(Commands.literal("motd")
                    .executes(context -> showMotd(context.getSource()))
                    .then(Commands.literal("set")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> setMotd(context.getSource(), StringArgumentType.getString(context, "message")))))
                    .then(Commands.literal("clear")
                        .executes(context -> clearMotd(context.getSource()))))
                .then(Commands.literal("description")
                    .executes(context -> showDescription(context.getSource()))
                    .then(Commands.literal("set")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(context -> setDescription(context.getSource(), StringArgumentType.getString(context, "message")))))
                    .then(Commands.literal("clear")
                        .executes(context -> clearDescription(context.getSource()))))
                .then(Commands.literal("color")
                    .executes(context -> showColor(context.getSource()))
                    .then(Commands.argument("color", StringArgumentType.word())
                        .executes(context -> setColor(context.getSource(), StringArgumentType.getString(context, "color")))))
                .then(Commands.literal("rank")
                    .then(Commands.literal("list")
                        .executes(context -> listRanks(context.getSource())))
                    .then(Commands.literal("set")
                        .then(Commands.argument("role", StringArgumentType.word())
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> setRankName(context.getSource(), StringArgumentType.getString(context, "role"), StringArgumentType.getString(context, "name"))))))
                    .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> applyRankPreset(context.getSource(), StringArgumentType.getString(context, "name")))))
                    .then(Commands.literal("presets")
                        .executes(context -> listRankPresets(context.getSource()))))
                .then(Commands.literal("banner")
                    .executes(context -> showBanner(context.getSource()))
                    .then(Commands.literal("set")
                        .then(Commands.argument("color", StringArgumentType.word())
                            .executes(context -> setBanner(context.getSource(), StringArgumentType.getString(context, "color")))))
                    .then(Commands.literal("clear")
                        .executes(context -> clearBanner(context.getSource()))))
                .then(Commands.literal("trust")
                    .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> trustPlayer(context.getSource(), EntityArgument.getPlayer(context, "player"), true))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> trustPlayer(context.getSource(), EntityArgument.getPlayer(context, "player"), false))))
                    .then(Commands.literal("list")
                        .executes(context -> listTrusted(context.getSource()))))
                .then(Commands.literal("protection")
                    .executes(context -> showProtectionTier(context.getSource()))
                    .then(Commands.literal("set")
                        .then(Commands.argument("tier", StringArgumentType.word())
                            .suggests(FactionCommandSuggestions::protectionTiers)
                            .executes(context -> setProtectionTier(context.getSource(), StringArgumentType.getString(context, "tier"))))))
                .then(Commands.literal("claiminfo")
                    .executes(context -> claimInfo(context.getSource())))
                .then(Commands.literal("logs")
                    .executes(context -> showClaimLogs(context.getSource())))
                .then(Commands.literal("home")
                    .executes(context -> goHome(context.getSource())))
                .then(Commands.literal("sethome")
                    .executes(context -> setHome(context.getSource())))
                .then(Commands.literal("map")
                    .executes(context -> factionMap(context.getSource(), 4))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                        .executes(context -> factionMap(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))
                    .then(Commands.literal("sync")
                        .executes(context -> syncDynmap(context.getSource()))))
                .then(Commands.literal("bypass")
                    .requires(source -> source.hasPermission(FactionConfig.SERVER.adminBypassPermissionLevel.get()))
                    .executes(context -> toggleBypass(context.getSource(), null))
                    .then(Commands.argument("state", StringArgumentType.word())
                        .suggests(FactionCommandSuggestions::onOff)
                        .executes(context -> toggleBypass(context.getSource(), StringArgumentType.getString(context, "state")))))
                .then(Commands.literal("border")
                    .executes(context -> toggleBorder(context.getSource(), null))
                    .then(Commands.argument("state", StringArgumentType.word())
                        .suggests(FactionCommandSuggestions::onOff)
                        .executes(context -> toggleBorder(context.getSource(), StringArgumentType.getString(context, "state")))))
                .then(Commands.literal("safezone")
                    .requires(source -> source.hasPermission(FactionConfig.SERVER.adminBypassPermissionLevel.get()))
                    .then(Commands.literal("claim")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .suggests(FactionCommandSuggestions::factionNames)
                            .executes(context -> claimSafeZone(context.getSource(), StringArgumentType.getString(context, "faction")))))
                    .then(Commands.literal("unclaim")
                        .executes(context -> unclaimSafeZone(context.getSource()))))
                .then(Commands.literal("boost")
                    .requires(source -> source.hasPermission(FactionConfig.SERVER.adminBypassPermissionLevel.get()))
                    .then(Commands.argument("faction", StringArgumentType.string())
                        .suggests(FactionCommandSuggestions::factionNames)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                            .executes(context -> setClaimBoost(
                                context.getSource(),
                                StringArgumentType.getString(context, "faction"),
                                IntegerArgumentType.getInteger(context, "amount")))))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .suggests(FactionCommandSuggestions::factionNames)
                            .executes(context -> setClaimBoost(
                                context.getSource(),
                                StringArgumentType.getString(context, "faction"),
                                IntegerArgumentType.getInteger(context, "amount"))))))
                .then(Commands.literal("data")
                    .then(Commands.literal("backup")
                        .executes(context -> backupData(context.getSource())))
                    .then(Commands.literal("restore")
                        .then(Commands.argument("file", StringArgumentType.string())
                            .executes(context -> restoreData(context.getSource(), StringArgumentType.getString(context, "file"))))));
        CommandNode<CommandSourceStack> factionNode = dispatcher.register(root);
        dispatcher.register(Commands.literal("f").redirect(factionNode));
    }

    private static int createFaction(CommandSourceStack source, String name) throws CommandSyntaxException {
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
        int maxLength = FactionConfig.SERVER.maxFactionNameLength.get();
        if (trimmed.length() > maxLength) {
            source.sendFailure(Component.literal("Faction name cannot exceed " + maxLength + " characters."));
            return 0;
        }
        Faction faction = data.createFaction(trimmed, player);
        source.sendSuccess(() -> Component.literal("Created faction " + faction.getName()), false);
        return 1;
    }

    private static int renameFaction(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().getOwner().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the owner can rename the faction."));
            return 0;
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        int maxLength = FactionConfig.SERVER.maxFactionNameLength.get();
        if (trimmed.length() > maxLength) {
            source.sendFailure(Component.literal("Faction name cannot exceed " + maxLength + " characters."));
            return 0;
        }
        if (!data.renameFaction(faction.get().getId(), trimmed)) {
            source.sendFailure(Component.literal("A faction with that name already exists."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Faction renamed to " + trimmed + "."), true);
        return 1;
    }

    private static int disbandFaction(CommandSourceStack source) throws CommandSyntaxException {
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
        DISBAND_CONFIRMATIONS.put(player.getUUID(), System.currentTimeMillis());
        source.sendSuccess(() -> Component.literal("Run /faction disband confirm within 10 seconds to confirm."), false);
        return 1;
    }

    private static int confirmDisband(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Long requestedAt = DISBAND_CONFIRMATIONS.get(player.getUUID());
        if (requestedAt == null || System.currentTimeMillis() - requestedAt > CONFIRM_TIMEOUT_MILLIS) {
            source.sendFailure(Component.literal("Disband confirmation expired. Run /faction disband again."));
            return 0;
        }
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
                WebmapBridge.updateClaim(chunkPos, Optional.empty(), player.level().dimension().location().toString());
            }
        }
        data.disbandFaction(factionId);
        DISBAND_CONFIRMATIONS.remove(player.getUUID());
        source.sendSuccess(() -> Component.literal("Faction disbanded."), true);
        return 1;
    }

    private static int claimChunk(CommandSourceStack source) throws CommandSyntaxException {
        return FactionService.claimChunk(source);
    }

    private static int unclaimChunk(CommandSourceStack source) throws CommandSyntaxException {
        return FactionService.unclaimChunk(source);
    }

    private static int overtakeChunk(CommandSourceStack source) throws CommandSyntaxException {
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
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (data.isClaimed(player.blockPosition())) {
            OVERTAKE_CONFIRMATIONS.put(player.getUUID(), System.currentTimeMillis());
            source.sendSuccess(() -> Component.literal("Run /faction overtake confirm within 10 seconds to confirm."), false);
            return 1;
        }
        return FactionService.overtakeChunk(source, chunk);
    }

    private static int confirmOvertake(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Long requestedAt = OVERTAKE_CONFIRMATIONS.get(player.getUUID());
        if (requestedAt == null || System.currentTimeMillis() - requestedAt > CONFIRM_TIMEOUT_MILLIS) {
            source.sendFailure(Component.literal("Overtake confirmation expired. Run /faction overtake again."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        int result = FactionService.overtakeChunk(source, chunk);
        if (result != 0) {
            OVERTAKE_CONFIRMATIONS.remove(player.getUUID());
        }
        return result;
    }

    private static int factionInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        int claims = data.getClaimCount(faction.get().getId());
        int maxClaims = data.getMaxClaims(faction.get().getId());
        int level = data.getFactionLevel(faction.get().getId());
        String role = faction.get().getRole(player.getUUID());
        String roleDisplay = role != null ? faction.get().getRoleDisplayName(role) : "Unknown";
        String message = "Faction: " + faction.get().getName()
            + " | Role: " + roleDisplay
            + " | Color: " + faction.get().getColorName()
            + " | Level: " + level
            + " | Claims: " + claims + "/" + maxClaims
            + " | Members: " + faction.get().getMemberCount()
            + " | Protection: " + faction.get().getProtectionTier().name()
            + "\nMOTD: " + faction.get().getMotd()
            + "\nDescription: " + faction.get().getDescription()
            + "\nBanner: " + faction.get().getBannerColor();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int invitePlayer(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        return FactionService.invitePlayer(source, target);
    }

    private static int joinFaction(CommandSourceStack source, String name) throws CommandSyntaxException {
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
        Optional<FactionInvite> invite = data.getInvite(player.getUUID());
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

    private static int leaveFaction(CommandSourceStack source) throws CommandSyntaxException {
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

    private static int kickMember(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
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

    private static int setRole(CommandSourceStack source, ServerPlayer target, String role) throws CommandSyntaxException {
        return FactionService.setRole(source, target, role);
    }

    private static int listRoles(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        StringBuilder message = new StringBuilder("Faction roles:");
        for (var entry : faction.get().getRoleDisplayNames().entrySet()) {
            message.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int addRole(CommandSourceStack source, String roleName, String displayName) throws CommandSyntaxException {
        String display = displayName == null ? roleName : displayName;
        return FactionService.createRole(source, roleName, display);
    }

    private static int removeRole(CommandSourceStack source, String roleName) throws CommandSyntaxException {
        return FactionService.deleteRole(source, roleName);
    }

    private static int listPermissions(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        StringBuilder message = new StringBuilder("Permissions:");
        for (var entry : faction.get().getPermissions().entrySet()) {
            message.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int updatePermission(CommandSourceStack source, String roleName, String permissionName, boolean grant) throws CommandSyntaxException {
        return FactionService.updatePermission(source, roleName, permissionName, grant);
    }

    private static int claimInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<UUID> personalOwner = data.getPersonalClaimOwner(player.blockPosition());
        if (personalOwner.isPresent()) {
            String ownerName = resolveName(player.getServer(), personalOwner.get());
            source.sendSuccess(() -> Component.literal("Personal claim owner: " + ownerName), false);
            return 1;
        }
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
        String message = "Claim owner: " + owner.get().getName() + " | Relation: " + relation.name();
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int factionMap(CommandSourceStack source, int radius) throws CommandSyntaxException {
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

    private static int showChatMode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionChatMode mode = FactionChatManager.getMode(player.getUUID());
        source.sendSuccess(() -> Component.literal("Current chat mode: " + mode.name()), false);
        return 1;
    }

    private static String resolveName(MinecraftServer server, UUID playerId) {
        if (server == null) {
            return playerId.toString();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return server.getProfileCache()
            .get(playerId)
            .map(profile -> profile.getName())
            .orElse(playerId.toString());
    }

    private static int toggleChatMode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionChatMode current = FactionChatManager.getMode(player.getUUID());
        FactionChatMode next = current == FactionChatMode.PUBLIC ? FactionChatMode.FACTION : FactionChatMode.PUBLIC;
        if (next != FactionChatMode.PUBLIC && FactionData.get(player.serverLevel()).getFactionByPlayer(player.getUUID()).isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        FactionChatManager.setMode(player.getUUID(), next);
        source.sendSuccess(() -> Component.literal("Chat mode set to " + next.name()), false);
        return 1;
    }

    private static int setChatMode(CommandSourceStack source, String modeName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionChatMode mode;
        try {
            mode = FactionChatMode.valueOf(modeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown chat mode. Use public, faction, or ally."));
            return 0;
        }
        if (mode != FactionChatMode.PUBLIC && FactionData.get(player.serverLevel()).getFactionByPlayer(player.getUUID()).isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        FactionChatManager.setMode(player.getUUID(), mode);
        source.sendSuccess(() -> Component.literal("Chat mode set to " + mode.name()), false);
        return 1;
    }

    private static int listFactions(CommandSourceStack source) {
        FactionData data = FactionData.get(source.getLevel());
        List<Faction> factions = data.getFactions().values().stream()
            .sorted((first, second) -> String.CASE_INSENSITIVE_ORDER.compare(first.getName(), second.getName()))
            .toList();
        if (factions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No factions found."), false);
            return 1;
        }
        Optional<UUID> playerFactionId = Optional.empty();
        try {
            ServerPlayer player = source.getPlayerOrException();
            playerFactionId = data.getFactionByPlayer(player.getUUID()).map(Faction::getId);
        } catch (CommandSyntaxException ignored) {
            // Non-player sources just list the factions without relations.
        }
        MutableComponent message = Component.literal("Factions (" + factions.size() + "):").withStyle(ChatFormatting.GOLD);
        for (Faction faction : factions) {
            String relationLabel = "";
            if (playerFactionId.isPresent()) {
                if (faction.getId().equals(playerFactionId.get())) {
                    relationLabel = "Your faction";
                } else {
                    FactionRelation relation = data.getRelation(playerFactionId.get(), faction.getId());
                    if (relation != FactionRelation.NEUTRAL) {
                        relationLabel = relation.name();
                    }
                }
            }
            MutableComponent line = Component.literal("\n- ")
                .append(Component.literal(faction.getName()).withStyle(faction.getColor()))
                .append(Component.literal(" (" + faction.getMemberCount() + " members"));
            if (!relationLabel.isEmpty()) {
                line = line.append(Component.literal(", " + relationLabel));
            }
            line = line.append(Component.literal(")"));
            message.append(line);
        }
        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static int toggleBypass(CommandSourceStack source, String state) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled;
        if (state == null) {
            enabled = FactionBypassManager.toggle(player);
        } else {
            String trimmed = state.trim().toLowerCase(Locale.ROOT);
            switch (trimmed) {
                case "on", "true", "enable", "enabled" -> FactionBypassManager.setEnabled(player, true);
                case "off", "false", "disable", "disabled" -> FactionBypassManager.setEnabled(player, false);
                default -> {
                    source.sendFailure(Component.literal("Unknown bypass state. Use on/off."));
                    return 0;
                }
            }
            enabled = FactionBypassManager.isBypassEnabled(player);
        }
        String message = enabled ? "Claim bypass enabled." : "Claim bypass disabled.";
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int showMotd(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("MOTD: " + faction.get().getMotd()), false);
        return 1;
    }

    private static int setMotd(CommandSourceStack source, String motd) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        faction.get().setMotd(motd);
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Faction MOTD updated."), true);
        return 1;
    }

    private static int clearMotd(CommandSourceStack source) throws CommandSyntaxException {
        return setMotd(source, FactionConfig.getDefaultMotd());
    }

    private static int showDescription(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Description: " + faction.get().getDescription()), false);
        return 1;
    }

    private static int setDescription(CommandSourceStack source, String description) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        faction.get().setDescription(description);
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Faction description updated."), true);
        return 1;
    }

    private static int clearDescription(CommandSourceStack source) throws CommandSyntaxException {
        return setDescription(source, FactionConfig.getDefaultDescription());
    }

    private static int showColor(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Faction color: " + faction.get().getColorName()), false);
        return 1;
    }

    private static int setColor(CommandSourceStack source, String colorName) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        ChatFormatting color = FactionConfig.parseColor(colorName);
        if (color == ChatFormatting.WHITE && !"white".equalsIgnoreCase(colorName)) {
            source.sendFailure(Component.literal("Unknown color name."));
            return 0;
        }
        faction.get().setColorName(color.getName());
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Faction color updated to " + color.getName()), true);
        return 1;
    }

    private static int listRanks(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        StringBuilder message = new StringBuilder("Faction roles:");
        for (var entry : faction.get().getRoleDisplayNames().entrySet()) {
            message.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int setRankName(CommandSourceStack source, String roleName, String name) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        String normalized = Faction.normalizeRoleName(roleName);
        if (!faction.get().hasRole(normalized)) {
            source.sendFailure(Component.literal("Unknown role."));
            return 0;
        }
        faction.get().setRoleDisplayName(normalized, name);
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Role display name updated for " + normalized), true);
        return 1;
    }

    private static int applyRankPreset(CommandSourceStack source, String presetName) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        var preset = FactionConfig.getPresetRoleDisplayNames(presetName);
        if (preset.isEmpty()) {
            source.sendFailure(Component.literal("Unknown preset name."));
            return 0;
        }
        for (var entry : preset.entrySet()) {
            faction.get().setRoleDisplayName(entry.getKey(), entry.getValue());
        }
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Applied rank preset " + presetName), true);
        return 1;
    }

    private static int listRankPresets(CommandSourceStack source) throws CommandSyntaxException {
        StringBuilder message = new StringBuilder("Rank presets:");
        for (var entry : FactionConfig.getPresetDisplayMap().entrySet()) {
            message.append("\n").append(entry.getKey()).append(" = ").append(entry.getValue());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int showBanner(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Faction banner color: " + faction.get().getBannerColor()), false);
        return 1;
    }

    private static int setBanner(CommandSourceStack source, String colorName) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        ChatFormatting color = FactionConfig.parseColor(colorName);
        if (color == ChatFormatting.WHITE && !"white".equalsIgnoreCase(colorName)) {
            source.sendFailure(Component.literal("Unknown banner color name."));
            return 0;
        }
        faction.get().setBannerColor(color.getName());
        FactionData.get(source.getLevel()).setDirty();
        source.sendSuccess(() -> Component.literal("Faction banner color updated to " + color.getName()), true);
        return 1;
    }

    private static int clearBanner(CommandSourceStack source) throws CommandSyntaxException {
        return setBanner(source, FactionConfig.getDefaultBannerColor());
    }

    private static int trustPlayer(CommandSourceStack source, ServerPlayer target, boolean trust) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        if (trust) {
            FactionData.get(source.getLevel()).addTrustedPlayer(faction.get().getId(), target.getUUID());
            source.sendSuccess(() -> Component.literal("Trusted " + target.getName().getString() + "."), true);
        } else {
            FactionData.get(source.getLevel()).removeTrustedPlayer(faction.get().getId(), target.getUUID());
            source.sendSuccess(() -> Component.literal("Untrusted " + target.getName().getString() + "."), true);
        }
        return 1;
    }

    private static int listTrusted(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        StringBuilder message = new StringBuilder("Trusted players:");
        for (UUID trusted : faction.get().getTrustedPlayers()) {
            message.append("\n").append(trusted);
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int showProtectionTier(CommandSourceStack source) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForMember(source);
        if (faction.isEmpty()) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Protection tier: " + faction.get().getProtectionTier().name()), false);
        return 1;
    }

    private static int setProtectionTier(CommandSourceStack source, String tierName) throws CommandSyntaxException {
        Optional<Faction> faction = getFactionForSettings(source);
        if (faction.isEmpty()) {
            return 0;
        }
        FactionProtectionTier tier;
        try {
            tier = FactionProtectionTier.valueOf(tierName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown protection tier."));
            return 0;
        }
        FactionData data = FactionData.get(source.getLevel());
        if (!data.canUseProtectionTier(faction.get().getId(), tier)) {
            source.sendFailure(Component.literal("Your faction level is too low for that tier."));
            return 0;
        }
        faction.get().setProtectionTier(tier);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Protection tier set to " + tier.name()), true);
        return 1;
    }

    private static int showClaimLogs(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_SETTINGS)
            && !player.hasPermissions(FactionConfig.SERVER.adminBypassPermissionLevel.get())) {
            source.sendFailure(Component.literal("You lack permission to view access logs."));
            return 0;
        }
        Deque<FactionAccessLog> logs = data.getAccessLogs(player.blockPosition());
        if (logs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No access logs for this claim."), false);
            return 1;
        }
        StringBuilder message = new StringBuilder("Access logs:");
        for (FactionAccessLog log : logs) {
            String timeAgo = formatTimeAgo(log.timestamp());
            message.append("\n")
                .append(timeAgo)
                .append(" | ")
                .append(log.playerName())
                .append(" | ")
                .append(log.action())
                .append(" | ")
                .append(log.allowed() ? "allowed" : "denied")
                .append(" | ")
                .append(log.blockName());
        }
        source.sendSuccess(() -> Component.literal(message.toString()), false);
        return 1;
    }

    private static int toggleAutoClaim(CommandSourceStack source, String state) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = FactionClaimManager.isAutoClaimEnabled(player.getUUID());
        if (state != null) {
            enabled = "on".equalsIgnoreCase(state) || "true".equalsIgnoreCase(state);
        } else {
            enabled = !enabled;
        }
        FactionClaimManager.setAutoClaimEnabled(player.getUUID(), enabled);
        FactionData.get(player.serverLevel()).setAutoClaimEnabled(player.getUUID(), enabled);
        String message = "Auto-claim " + (enabled ? "enabled" : "disabled") + ".";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int toggleBorder(CommandSourceStack source, String state) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = FactionClaimManager.isBorderEnabled(player.getUUID());
        if (state != null) {
            enabled = "on".equalsIgnoreCase(state) || "true".equalsIgnoreCase(state);
        } else {
            enabled = !enabled;
        }
        FactionClaimManager.setBorderEnabled(player.getUUID(), enabled);
        FactionData.get(player.serverLevel()).setBorderEnabled(player.getUUID(), enabled);
        com.mcprotector.network.NetworkHandler.sendToPlayer(
            player,
            com.mcprotector.network.FactionStatePacket.fromPlayer(player)
        );
        String message = "Claim borders " + (enabled ? "enabled" : "disabled") + ".";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int setClaimBoost(CommandSourceStack source, String factionName, int amount) {
        String trimmed = factionName == null ? "" : factionName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        FactionData data = FactionData.get(source.getLevel());
        Optional<Faction> faction = data.findFactionByName(trimmed);
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        data.setClaimBoost(faction.get().getId(), amount);
        String message = amount > 0
            ? "Claim boost set to +" + amount + " for " + faction.get().getName() + "."
            : "Claim boost cleared for " + faction.get().getName() + ".";
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    private static int claimSafeZone(CommandSourceStack source, String factionName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        return FactionService.claimSafeZoneChunks(source, List.of(chunk), factionName);
    }

    private static int unclaimSafeZone(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        return FactionService.unclaimSafeZoneChunks(source, List.of(chunk));
    }

    private static int setHome(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_SETTINGS)) {
            source.sendFailure(Component.literal("You lack permission to set the faction home."));
            return 0;
        }
        if (data.isFactionAtWar(faction.get().getId())) {
            source.sendFailure(Component.literal("You cannot move faction home while your faction is at war."));
            return 0;
        }
        Optional<UUID> ownerId = data.getClaimOwner(player.blockPosition());
        if (ownerId.isEmpty() || !ownerId.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("Faction home must be set inside your claimed chunks."));
            return 0;
        }
        String dimension = player.level().dimension().location().toString();
        data.setFactionHome(faction.get().getId(), dimension, player.blockPosition());
        source.sendSuccess(() -> Component.literal("Faction home set."), true);
        return 1;
    }

    private static int goHome(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        Optional<FactionData.FactionHome> home = data.getFactionHome(faction.get().getId());
        if (home.isEmpty()) {
            source.sendFailure(Component.literal("Your faction does not have a home set."));
            return 0;
        }
        String dimension = home.get().dimension();
        var server = player.getServer();
        if (server == null) {
            source.sendFailure(Component.literal("Server not available."));
            return 0;
        }
        net.minecraft.resources.ResourceLocation dimensionId = net.minecraft.resources.ResourceLocation.tryParse(dimension);
        if (dimensionId == null) {
            source.sendFailure(Component.literal("Faction home dimension is invalid."));
            return 0;
        }
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            dimensionId));
        if (level == null) {
            source.sendFailure(Component.literal("Faction home dimension is not available."));
            return 0;
        }
        BlockPos pos = home.get().pos();
        Optional<UUID> ownerId = FactionData.get(level).getClaimOwner(pos);
        if (ownerId.isEmpty() || !ownerId.get().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("Faction home is no longer inside your claimed chunks."));
            return 0;
        }
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to faction home."), true);
        return 1;
    }

    private static int syncDynmap(CommandSourceStack source) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("You lack permission to sync web maps."));
            return 0;
        }
        WebmapBridge.syncClaims(source.getLevel(), FactionData.get(source.getLevel()));
        source.sendSuccess(() -> Component.literal("Web map claims synced."), true);
        return 1;
    }

    private static int backupData(CommandSourceStack source) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("You lack permission to create backups."));
            return 0;
        }
        try {
            var level = source.getLevel();
            var data = FactionData.get(level);
            var backupId = "backup-" + System.currentTimeMillis() + ".nbt";
            var backupDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data")
                .resolve("mcprotector_backups");
            java.nio.file.Files.createDirectories(backupDir);
            var backupPath = backupDir.resolve(backupId);
            net.minecraft.nbt.NbtIo.writeCompressed(
                data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()),
                backupPath
            );
            source.sendSuccess(() -> Component.literal("Backup created: " + backupId), true);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Failed to create backup: " + ex.getMessage()));
            return 0;
        }
    }

    private static int restoreData(CommandSourceStack source, String fileName) {
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.literal("You lack permission to restore backups."));
            return 0;
        }
        try {
            var level = source.getLevel();
            var backupPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data")
                .resolve("mcprotector_backups")
                .resolve(fileName);
            if (!java.nio.file.Files.exists(backupPath)) {
                source.sendFailure(Component.literal("Backup file not found."));
                return 0;
            }
            var tag = net.minecraft.nbt.NbtIo.readCompressed(backupPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            var data = FactionData.get(level);
            data.restoreFromTag(tag);
            source.sendSuccess(() -> Component.literal("Backup restored from " + fileName), true);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Failed to restore backup: " + ex.getMessage()));
            return 0;
        }
    }

    private static Optional<Faction> getFactionForSettings(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return Optional.empty();
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_SETTINGS)) {
            source.sendFailure(Component.literal("You lack permission to manage faction settings."));
            return Optional.empty();
        }
        return faction;
    }

    private static Optional<Faction> getFactionForMember(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
        }
        return faction;
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

    private static String formatTimeAgo(long timestamp) {
        Duration duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = duration.toDays();
        return days + "d ago";
    }
}
