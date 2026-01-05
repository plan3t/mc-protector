package com.mcprotector.command;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.dynmap.DynmapBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

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
        data.disbandFaction(faction.get().getId());
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
            source.sendFailure(Component.literal("This chunk is already claimed."));
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
            source.sendFailure(Component.literal("You cannot overtake this chunk unless you are at war with the owner."));
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
        source.sendSuccess(() -> Component.literal("Faction: " + faction.get().getName()), false);
        return 1;
    }
}
