package com.mcprotector.dynmap;

import com.mcprotector.McProtectorMod;
import com.mcprotector.data.Faction;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Method;
import java.util.Optional;

public final class DynmapBridge {
    private static boolean available;
    private static Object markerApi;
    private static Object markerSet;

    private DynmapBridge() {
    }

    public static void init() {
        try {
            Class<?> dynmapClass = Class.forName("org.dynmap.DynmapAPI");
            Object dynmap = org.dynmap.DynmapAPIListener.getAPI();
            if (dynmap == null) {
                McProtectorMod.LOGGER.info("Dynmap detected but API not ready yet.");
                return;
            }
            Method getMarkerAPI = dynmapClass.getMethod("getMarkerAPI");
            markerApi = getMarkerAPI.invoke(dynmap);
            if (markerApi == null) {
                McProtectorMod.LOGGER.warn("Dynmap MarkerAPI not available.");
                return;
            }
            Method getMarkerSet = markerApi.getClass().getMethod("getMarkerSet", String.class);
            markerSet = getMarkerSet.invoke(markerApi, "mcprotector_claims");
            if (markerSet == null) {
                Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class, String.class, String.class, boolean);
                markerSet = createMarkerSet.invoke(markerApi, "mcprotector_claims", "Faction Claims", null, false);
            }
            available = markerSet != null;
            if (available) {
                McProtectorMod.LOGGER.info("Dynmap integration enabled.");
            }
        } catch (Throwable error) {
            McProtectorMod.LOGGER.info("Dynmap not available: {}", error.getMessage());
            available = false;
        }
    }

    public static void updateClaim(ChunkPos chunkPos, Optional<Faction> faction) {
        if (!available || markerSet == null) {
            return;
        }
        try {
            String markerId = "claim_" + chunkPos.x + "_" + chunkPos.z;
            Method findMarker = markerSet.getClass().getMethod("findAreaMarker", String.class);
            Object existing = findMarker.invoke(markerSet, markerId);
            if (faction.isEmpty()) {
                if (existing != null) {
                    Method delete = existing.getClass().getMethod("deleteMarker");
                    delete.invoke(existing);
                }
                return;
            }
            double[] x = new double[]{chunkPos.getMinBlockX(), chunkPos.getMinBlockX(), chunkPos.getMaxBlockX() + 1, chunkPos.getMaxBlockX() + 1};
            double[] z = new double[]{chunkPos.getMinBlockZ(), chunkPos.getMaxBlockZ() + 1, chunkPos.getMaxBlockZ() + 1, chunkPos.getMinBlockZ()};
            if (existing == null) {
                Method createArea = markerSet.getClass().getMethod(
                    "createAreaMarker",
                    String.class,
                    String.class,
                    boolean.class,
                    String.class,
                    double[].class,
                    double[].class,
                    boolean.class
                );
                existing = createArea.invoke(markerSet, markerId, faction.get().getName(), false, "world", x, z, false);
            }
            if (existing != null) {
                Method setLabel = existing.getClass().getMethod("setLabel", String.class);
                setLabel.invoke(existing, faction.get().getName());
            }
        } catch (Throwable error) {
            McProtectorMod.LOGGER.warn("Failed to update Dynmap marker: {}", error.getMessage());
        }
    }
}
