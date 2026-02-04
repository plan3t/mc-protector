package com.mcprotector.protection;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionBypassManager {
    private static final Set<UUID> DISABLED_BYPASS = ConcurrentHashMap.newKeySet();

    private FactionBypassManager() {
    }

    public static boolean isBypassEnabled(ServerPlayer player) {
        return !DISABLED_BYPASS.contains(player.getUUID());
    }

    public static boolean toggle(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (DISABLED_BYPASS.remove(playerId)) {
            return true;
        }
        DISABLED_BYPASS.add(playerId);
        return false;
    }

    public static void setEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            DISABLED_BYPASS.remove(player.getUUID());
        } else {
            DISABLED_BYPASS.add(player.getUUID());
        }
    }
}
