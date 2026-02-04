package com.mcprotector.webmap;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.dynmap.DynmapBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

public final class WebmapBridge {
    private WebmapBridge() {
    }

    public static void init() {
        DynmapBridge.init();
        SquaremapBridge.init();
    }

    public static void updateClaim(ChunkPos chunkPos, Optional<Faction> faction, String dimension) {
        DynmapBridge.updateClaim(chunkPos, faction, dimension);
        SquaremapBridge.updateClaim(chunkPos, faction, dimension);
    }

    public static void syncClaims(ServerLevel level, FactionData data) {
        DynmapBridge.syncClaims(level, data);
        SquaremapBridge.syncClaims(level, data);
    }
}
