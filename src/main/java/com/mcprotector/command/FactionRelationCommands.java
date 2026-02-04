package com.mcprotector.command;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRelation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public final class FactionRelationCommands {
    private FactionRelationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("faction")
                .then(Commands.literal("ally")
                    .then(Commands.literal("add")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> setRelation(context.getSource(), StringArgumentType.getString(context, "faction"), FactionRelation.ALLY))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> clearRelation(context.getSource(), StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("war")
                    .then(Commands.literal("declare")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> setRelation(context.getSource(), StringArgumentType.getString(context, "faction"), FactionRelation.WAR))))
                    .then(Commands.literal("end")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> clearRelation(context.getSource(), StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("vassal")
                    .then(Commands.literal("offer")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> offerVassal(context.getSource(), StringArgumentType.getString(context, "faction")))))
                    .then(Commands.literal("accept")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> acceptVassal(context.getSource(), StringArgumentType.getString(context, "faction")))))
                    .then(Commands.literal("decline")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> declineVassal(context.getSource(), StringArgumentType.getString(context, "faction")))))
                    .then(Commands.literal("release")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> releaseVassal(context.getSource(), StringArgumentType.getString(context, "faction")))))
                    .then(Commands.literal("break")
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .executes(context -> breakVassal(context.getSource(), StringArgumentType.getString(context, "faction"))))))
        );
    }

    private static int setRelation(CommandSourceStack source, String targetName, FactionRelation relation) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        String trimmed = targetName == null ? "" : targetName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> target = data.findFactionByName(trimmed);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        if (target.get().getId().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("Cannot change relation with your own faction."));
            return 0;
        }
        if (relation == FactionRelation.WAR && data.isVassalRelationship(faction.get().getId(), target.get().getId())) {
            source.sendFailure(Component.literal("Use /faction vassal break to start a breakaway war."));
            return 0;
        }
        if (relation == FactionRelation.WAR && FactionConfig.SERVER.protectOfflineFactions.get()
            && !data.isFactionOnline(player.serverLevel(), target.get().getId())) {
            source.sendFailure(Component.literal("You cannot declare war on an offline faction."));
            return 0;
        }
        data.setRelation(faction.get().getId(), target.get().getId(), relation);
        source.sendSuccess(() -> Component.literal("Relation set to " + relation.name() + " with " + target.get().getName()), true);
        return 1;
    }

    private static int clearRelation(CommandSourceStack source, String targetName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        String trimmed = targetName == null ? "" : targetName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> target = data.findFactionByName(trimmed);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<UUID> overlord = data.getOverlord(faction.get().getId());
        if (overlord.isPresent() && overlord.get().equals(target.get().getId())) {
            source.sendFailure(Component.literal("Vassals cannot end relations with their overlord."));
            return 0;
        }
        data.clearRelation(faction.get().getId(), target.get().getId());
        data.cancelVassalBreakaway(faction.get().getId(), target.get().getId());
        source.sendSuccess(() -> Component.literal("Relation cleared with " + target.get().getName()), true);
        return 1;
    }

    private static int offerVassal(CommandSourceStack source, String targetName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        if (data.getOverlord(faction.get().getId()).isPresent()) {
            source.sendFailure(Component.literal("Your faction is already a vassal."));
            return 0;
        }
        String trimmed = targetName == null ? "" : targetName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> target = data.findFactionByName(trimmed);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        if (target.get().getId().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("Cannot vassalize your own faction."));
            return 0;
        }
        if (data.getOverlord(target.get().getId()).isPresent()) {
            source.sendFailure(Component.literal("That faction already has an overlord."));
            return 0;
        }
        data.inviteVassal(faction.get().getId(), target.get().getId());
        source.sendSuccess(() -> Component.literal("Vassal offer sent to " + target.get().getName()), true);
        notifyFactionMembers(player, data, target.get().getId(),
            "Your faction has received a vassal offer from " + faction.get().getName() + ". Use /faction vassal accept "
                + faction.get().getName() + " to accept.");
        return 1;
    }

    private static int acceptVassal(CommandSourceStack source, String overlordName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        if (data.getOverlord(faction.get().getId()).isPresent()) {
            source.sendFailure(Component.literal("Your faction already has an overlord."));
            return 0;
        }
        String trimmed = overlordName == null ? "" : overlordName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> overlord = data.findFactionByName(trimmed);
        if (overlord.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<FactionData.VassalInvite> invite = data.getVassalInvite(faction.get().getId());
        if (invite.isEmpty() || !invite.get().overlordId().equals(overlord.get().getId())) {
            source.sendFailure(Component.literal("You do not have a vassal offer from that faction."));
            return 0;
        }
        data.createVassalContract(overlord.get().getId(), faction.get().getId());
        data.clearVassalInvite(faction.get().getId());
        source.sendSuccess(() -> Component.literal("Your faction is now a vassal of " + overlord.get().getName()), true);
        notifyFactionMembers(player, data, overlord.get().getId(),
            faction.get().getName() + " has accepted your vassal offer.");
        return 1;
    }

    private static int declineVassal(CommandSourceStack source, String overlordName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        String trimmed = overlordName == null ? "" : overlordName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> overlord = data.findFactionByName(trimmed);
        if (overlord.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<FactionData.VassalInvite> invite = data.getVassalInvite(faction.get().getId());
        if (invite.isEmpty() || !invite.get().overlordId().equals(overlord.get().getId())) {
            source.sendFailure(Component.literal("You do not have a vassal offer from that faction."));
            return 0;
        }
        data.clearVassalInvite(faction.get().getId());
        source.sendSuccess(() -> Component.literal("Vassal offer declined."), true);
        notifyFactionMembers(player, data, overlord.get().getId(),
            faction.get().getName() + " has declined your vassal offer.");
        return 1;
    }

    private static int releaseVassal(CommandSourceStack source, String vassalName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        String trimmed = vassalName == null ? "" : vassalName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> vassal = data.findFactionByName(trimmed);
        if (vassal.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        if (!data.releaseVassal(faction.get().getId(), vassal.get().getId())) {
            source.sendFailure(Component.literal("That faction is not your vassal."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Released vassal " + vassal.get().getName()), true);
        notifyFactionMembers(player, data, vassal.get().getId(),
            "Your faction has been released from vassalage by " + faction.get().getName() + ".");
        return 1;
    }

    private static int breakVassal(CommandSourceStack source, String overlordName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a faction."));
            return 0;
        }
        if (!faction.get().hasPermission(player.getUUID(), FactionPermission.MANAGE_RELATIONS)) {
            source.sendFailure(Component.literal("You lack permission to manage relations."));
            return 0;
        }
        String trimmed = overlordName == null ? "" : overlordName.trim();
        if (trimmed.isEmpty()) {
            source.sendFailure(Component.literal("Faction name cannot be blank."));
            return 0;
        }
        Optional<Faction> overlord = data.findFactionByName(trimmed);
        if (overlord.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        Optional<UUID> currentOverlord = data.getOverlord(faction.get().getId());
        if (currentOverlord.isEmpty() || !currentOverlord.get().equals(overlord.get().getId())) {
            source.sendFailure(Component.literal("That faction is not your overlord."));
            return 0;
        }
        if (FactionConfig.SERVER.protectOfflineFactions.get()
            && !data.isFactionOnline(player.serverLevel(), overlord.get().getId())) {
            source.sendFailure(Component.literal("You cannot declare a breakaway war while your overlord is offline."));
            return 0;
        }
        if (data.hasActiveBreakaway(faction.get().getId())) {
            source.sendFailure(Component.literal("Your faction is already in a breakaway war."));
            return 0;
        }
        int requiredClaims = data.calculateBreakawayClaimGoal(overlord.get().getId());
        if (requiredClaims <= 0) {
            data.releaseVassal(overlord.get().getId(), faction.get().getId());
            data.clearRelation(faction.get().getId(), overlord.get().getId());
            source.sendSuccess(() -> Component.literal("Your faction is now independent."), true);
            notifyFactionMembers(player, data, overlord.get().getId(),
                faction.get().getName() + " has broken free from vassalage.");
            return 1;
        }
        data.startVassalBreakaway(faction.get().getId(), overlord.get().getId(), requiredClaims);
        data.setRelation(faction.get().getId(), overlord.get().getId(), FactionRelation.WAR);
        source.sendSuccess(() -> Component.literal("Breakaway war declared. Capture " + requiredClaims
            + " overlord claim(s) to gain independence."), true);
        notifyFactionMembers(player, data, overlord.get().getId(),
            faction.get().getName() + " has declared a breakaway war.");
        return 1;
    }

    private static void notifyFactionMembers(ServerPlayer sender, FactionData data, UUID factionId, String message) {
        for (ServerPlayer recipient : sender.server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isPresent() && recipientFactionId.get().equals(factionId)) {
                recipient.sendSystemMessage(Component.literal(message));
            }
        }
    }
}
