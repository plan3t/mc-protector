package com.mcprotector.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionChatManager {
    private static final Map<UUID, FactionChatMode> CHAT_MODES = new ConcurrentHashMap<>();

    private FactionChatManager() {
    }

    public static FactionChatMode getMode(UUID playerId) {
        return CHAT_MODES.getOrDefault(playerId, FactionChatMode.PUBLIC);
    }

    public static void setMode(UUID playerId, FactionChatMode mode) {
        if (mode == null) {
            CHAT_MODES.remove(playerId);
            return;
        }
        CHAT_MODES.put(playerId, mode);
    }
}
