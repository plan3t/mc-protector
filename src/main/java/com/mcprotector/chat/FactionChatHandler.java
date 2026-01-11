package com.mcprotector.chat;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionRelation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

public class FactionChatHandler {
    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        FactionChatMode mode = FactionChatManager.getMode(player.getUUID());
        if (mode == FactionChatMode.PUBLIC) {
            if (!FactionConfig.SERVER.usePublicChatFormat.get()) {
                return;
            }
            FactionData data = FactionData.get(player.serverLevel());
            Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
            event.setCanceled(true);
            Component formatted = FactionTextFormatter.formatChat(mode, player, faction.orElse(null), event.getMessage().getString());
            for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
                recipient.sendSystemMessage(formatted);
            }
            return;
        }
        if (mode == FactionChatMode.FACTION && !FactionConfig.SERVER.enableFactionChat.get()) {
            player.sendSystemMessage(Component.literal("Faction chat is disabled."));
            event.setCanceled(true);
            return;
        }
        if (mode == FactionChatMode.ALLY && !FactionConfig.SERVER.enableAllyChat.get()) {
            player.sendSystemMessage(Component.literal("Ally chat is disabled."));
            event.setCanceled(true);
            return;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not in a faction."));
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        String message = event.getMessage().getString();
        Component formatted = FactionTextFormatter.formatChat(mode, player, faction.get(), message);
        UUID senderFactionId = faction.get().getId();
        for (ServerPlayer recipient : player.server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isEmpty()) {
                continue;
            }
            if (mode == FactionChatMode.FACTION) {
                if (senderFactionId.equals(recipientFactionId.get())) {
                    recipient.sendSystemMessage(formatted);
                }
                continue;
            }
            FactionRelation relation = data.getRelation(senderFactionId, recipientFactionId.get());
            if (senderFactionId.equals(recipientFactionId.get()) || relation == FactionRelation.ALLY) {
                recipient.sendSystemMessage(formatted);
            }
        }
    }

    @SubscribeEvent
    public void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FactionData data = FactionData.get(player.serverLevel());
        Optional<Faction> faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            return;
        }
        event.setDisplayName(FactionTextFormatter.formatTabList(player, faction.get()));
    }
}
