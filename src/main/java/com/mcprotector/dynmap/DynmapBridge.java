package com.mcprotector.dynmap;

import com.mcprotector.McProtectorMod;
import com.mcprotector.data.Faction;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DynmapBridge {
    private static boolean available;
    private static Object markerApi;
    private static final Map<String, Object> markerSets = new HashMap<>();
    private static final Map<Long, Optional<Faction>> pendingUpdates = new HashMap<>();

    private DynmapBridge() {
    }

    public static void init() {
        try {
            Class<?> dynmapClass = Class.forName("org.dynmap.DynmapAPI");
            Class<?> dynmapListenerClass = Class.forName("org.dynmap.DynmapAPIListener");
            Method getApi = dynmapListenerClass.getMethod("getAPI");
            Object dynmap = getApi.invoke(null);
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
            available = true;
            if (available) {
                McProtectorMod.LOGGER.info("Dynmap integration enabled.");
                flushPendingUpdates();
            }
        } catch (Throwable error) {
            McProtectorMod.LOGGER.info("Dynmap not available: {}", error.getMessage());
            available = false;
        }
    }

    public static void updateClaim(ChunkPos chunkPos, Optional<Faction> faction, String dimension) {
        if (!available || markerApi == null) {
            pendingUpdates.put(chunkPos.toLong(), faction);
            return;
        }
        updateMarker(chunkPos, faction, dimension);
    }

    private static void updateMarker(ChunkPos chunkPos, Optional<Faction> faction, String dimension) {
        Object markerSet = getMarkerSet(dimension);
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
                existing = createArea.invoke(markerSet, markerId, faction.get().getName(), false, dimension, x, z, false);
            }
            if (existing != null) {
                Method setLabel = existing.getClass().getMethod("setLabel", String.class);
                setLabel.invoke(existing, faction.get().getName());
                try {
                    Method setDescription = existing.getClass().getMethod("setDescription", String.class);
                    setDescription.invoke(existing, "Faction: " + faction.get().getName());
                } catch (NoSuchMethodException ignored) {
                    // Dynmap versions without setDescription can ignore this.
                }
            }
        } catch (Throwable error) {
            McProtectorMod.LOGGER.warn("Failed to update Dynmap marker: {}", error.getMessage());
        }
    }

    public static void syncClaims(net.minecraft.server.level.ServerLevel level, com.mcprotector.data.FactionData data) {
        if (!available || markerApi == null) {
            return;
        }
        for (Map.Entry<Long, java.util.UUID> entry : data.getClaims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            Optional<Faction> faction = data.getFaction(entry.getValue());
            updateMarker(chunkPos, faction, level.dimension().location().toString());
        }
        for (Map.Entry<Long, java.util.UUID> entry : data.getSafeZoneClaims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            Optional<Faction> faction = data.getFaction(entry.getValue());
            updateMarker(chunkPos, faction, level.dimension().location().toString());
        }
    }

    private static void flushPendingUpdates() {
        if (!available || markerApi == null) {
            return;
        }
        for (Map.Entry<Long, Optional<Faction>> entry : pendingUpdates.entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            updateMarker(chunkPos, entry.getValue(), "minecraft:overworld");
        }
        pendingUpdates.clear();
    }

    private static Object getMarkerSet(String dimension) {
        if (!available || markerApi == null) {
            return null;
        }
        String safeDimension = sanitizeDimensionId(dimension);
        return markerSets.computeIfAbsent(safeDimension, key -> {
            try {
                Method getMarkerSet = markerApi.getClass().getMethod("getMarkerSet", String.class);
                Object existing = getMarkerSet.invoke(markerApi, "mcprotector_claims_" + key);
                if (existing != null) {
                    return existing;
                }
                Method createMarkerSet = markerApi.getClass().getMethod("createMarkerSet", String.class, String.class, String.class, boolean.class);
                return createMarkerSet.invoke(markerApi, "mcprotector_claims_" + key, "Faction Claims (" + dimension + ")", null, false);
            } catch (Throwable error) {
                McProtectorMod.LOGGER.warn("Failed to access Dynmap marker set: {}", error.getMessage());
                return null;
            }
        });
    }

    private static String sanitizeDimensionId(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "unknown";
        }
        return dimension.replace(':', '_');
    }
}
