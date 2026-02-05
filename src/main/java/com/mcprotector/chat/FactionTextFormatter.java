package com.mcprotector.chat;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public final class FactionTextFormatter {
    private FactionTextFormatter() {
    }

    public static Component formatChat(FactionChatMode mode, ServerPlayer sender, Faction faction, String message) {
        String format = switch (mode) {
            case FACTION -> FactionConfig.SERVER.factionChatFormat.get();
            case ALLY -> FactionConfig.SERVER.allyChatFormat.get();
            case PUBLIC -> FactionConfig.SERVER.publicChatFormat.get();
        };
        return Component.literal(applyFormat(format, sender, faction, message));
    }

    public static Component formatTabList(ServerPlayer player, Faction faction) {
        String format = FactionConfig.SERVER.tabListFormat.get();
        return Component.literal(applyFormat(format, player, faction, ""));
    }

    private static String applyFormat(String format, ServerPlayer sender, Faction faction, String message) {
        Map<String, String> replacements = new HashMap<>();
        ChatFormatting color = faction != null ? faction.getColor() : ChatFormatting.WHITE;
        String factionName = faction != null ? faction.getName() : "NoFaction";
        replacements.put("{faction}", color + factionName + ChatFormatting.RESET);
        replacements.put("{player}", sender.getName().getString());
        replacements.put("{message}", ChatFormatting.RESET + message);
        replacements.put("{faction_color}", color.toString());
        replacements.put("{reset}", ChatFormatting.RESET.toString());
        String role = faction != null ? faction.getRole(sender.getUUID()) : null;
        replacements.put("{role}", role != null ? faction.getRoleDisplayName(role) : "Wanderer");
        String result = format;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
