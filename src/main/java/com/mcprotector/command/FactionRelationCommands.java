package com.mcprotector.command;

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

public final class FactionRelationCommands {
    private FactionRelationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("faction")
                .then(Commands.literal("ally")
                    .then(Commands.literal("add")
                        .then(Commands.argument("faction", StringArgumentType.string())
                            .executes(context -> setRelation(context.getSource(), StringArgumentType.getString(context, "faction"), FactionRelation.ALLY))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("faction", StringArgumentType.string())
                            .executes(context -> clearRelation(context.getSource(), StringArgumentType.getString(context, "faction"))))))
                .then(Commands.literal("war")
                    .then(Commands.literal("declare")
                        .then(Commands.argument("faction", StringArgumentType.string())
                            .executes(context -> setRelation(context.getSource(), StringArgumentType.getString(context, "faction"), FactionRelation.WAR))))
                    .then(Commands.literal("end")
                        .then(Commands.argument("faction", StringArgumentType.string())
                            .executes(context -> clearRelation(context.getSource(), StringArgumentType.getString(context, "faction"))))))
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
        Optional<Faction> target = data.findFactionByName(targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        if (target.get().getId().equals(faction.get().getId())) {
            source.sendFailure(Component.literal("Cannot change relation with your own faction."));
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
        Optional<Faction> target = data.findFactionByName(targetName);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Faction not found."));
            return 0;
        }
        data.clearRelation(faction.get().getId(), target.get().getId());
        source.sendSuccess(() -> Component.literal("Relation cleared with " + target.get().getName()), true);
        return 1;
    }
}
