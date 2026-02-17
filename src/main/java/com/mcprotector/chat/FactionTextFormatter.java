package com.mcprotector.chat;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

public final class FactionTextFormatter {
    private FactionTextFormatter() {
    }

    public static Component formatChat(FactionChatMode mode, ServerPlayer sender, Faction faction, String message) {
        String format = switch (mode) {
            case FACTION -> FactionConfig.SERVER.factionChatFormat.get();
            case ALLY -> FactionConfig.SERVER.allyChatFormat.get();
            case PUBLIC -> FactionConfig.SERVER.publicChatFormat.get();
        };
        return applyFormat(format, sender, faction, message);
    }

    public static Component formatTabList(ServerPlayer player, Faction faction) {
        String format = FactionConfig.SERVER.tabListFormat.get();
        return applyFormat(format, player, faction, "");
    }

    private static Component applyFormat(String format, ServerPlayer sender, Faction faction, String message) {
        String factionName = faction != null ? faction.getName() : "NoFaction";
        String role = faction != null && faction.getRole(sender.getUUID()) != null
            ? faction.getRoleDisplayName(faction.getRole(sender.getUUID()))
            : "Wanderer";
        String playerName = sender.getName().getString();
        int factionRgb = faction != null ? faction.getColorRgb() : 0xFFFFFF;
        Style currentStyle = Style.EMPTY;
        MutableComponent result = Component.empty();

        int index = 0;
        while (index < format.length()) {
            int start = format.indexOf('{', index);
            if (start < 0) {
                appendLiteral(result, format.substring(index), currentStyle);
                break;
            }
            if (start > index) {
                appendLiteral(result, format.substring(index, start), currentStyle);
            }
            int end = format.indexOf('}', start);
            if (end < 0) {
                appendLiteral(result, format.substring(start), currentStyle);
                break;
            }
            String token = format.substring(start, end + 1);
            switch (token) {
                case "{faction_color}" -> currentStyle = currentStyle.withColor(TextColor.fromRgb(factionRgb));
                case "{reset}" -> currentStyle = Style.EMPTY;
                case "{faction}" -> appendLiteral(result, factionName, currentStyle);
                case "{player}" -> appendLiteral(result, playerName, currentStyle);
                case "{message}" -> appendLiteral(result, message, currentStyle);
                case "{role}" -> appendLiteral(result, role, currentStyle);
                default -> appendLiteral(result, token, currentStyle);
            }
            index = end + 1;
        }

        return result;
    }

    private static void appendLiteral(MutableComponent target, String text, Style style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        target.append(Component.literal(text).withStyle(style));
    }
}
