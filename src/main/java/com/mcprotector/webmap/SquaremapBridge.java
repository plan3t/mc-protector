package com.mcprotector.webmap;

import com.mcprotector.McProtectorMod;
import com.mcprotector.data.Faction;
import net.minecraft.world.level.ChunkPos;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SquaremapBridge {
    private static final String LAYER_KEY = "mcprotector_claims";
    private static final int LAYER_PRIORITY = 10;
    private static final int SAFE_ZONE_COLOR = 0xF9A825;
    private static boolean available;
    private static Object squaremap;
    private static Class<?> worldIdentifierClass;
    private static Class<?> keyClass;
    private static Class<?> pointClass;
    private static Class<?> markerClass;
    private static Class<?> markerOptionsClass;
    private static Class<?> markerOptionsBuilderClass;
    private static Class<?> layerProviderClass;
    private static final Map<String, Map<String, Object>> markersByWorld = new HashMap<>();
    private static final Map<String, Object> providersByWorld = new HashMap<>();

    private SquaremapBridge() {
    }

    public static void init() {
        try {
            Class<?> providerClass = Class.forName("xyz.jpenilla.squaremap.api.SquaremapProvider");
            Method get = providerClass.getMethod("get");
            squaremap = get.invoke(null);
            if (squaremap == null) {
                McProtectorMod.LOGGER.info("Squaremap detected but API not ready yet.");
                return;
            }
            worldIdentifierClass = Class.forName("xyz.jpenilla.squaremap.api.WorldIdentifier");
            keyClass = Class.forName("xyz.jpenilla.squaremap.api.Key");
            pointClass = Class.forName("xyz.jpenilla.squaremap.api.Point");
            markerClass = Class.forName("xyz.jpenilla.squaremap.api.marker.Marker");
            markerOptionsClass = Class.forName("xyz.jpenilla.squaremap.api.marker.MarkerOptions");
            markerOptionsBuilderClass = Class.forName("xyz.jpenilla.squaremap.api.marker.MarkerOptions$Builder");
            layerProviderClass = Class.forName("xyz.jpenilla.squaremap.api.LayerProvider");
            available = true;
            McProtectorMod.LOGGER.info("Squaremap integration enabled.");
        } catch (Throwable error) {
            McProtectorMod.LOGGER.info("Squaremap not available: {}", error.getMessage());
            available = false;
        }
    }


    public static boolean isAvailable() {
        return available && squaremap != null;
    }

    public static void updateClaim(ChunkPos chunkPos, Optional<Faction> faction, String dimension) {
        if (!available || squaremap == null) {
            return;
        }
        try {
            Object world = getWorld(dimension);
            if (world == null) {
                return;
            }
            ensureLayerProvider(world, dimension);
            Map<String, Object> markers = markersByWorld.computeIfAbsent(dimension, key -> new HashMap<>());
            String markerId = markerId(chunkPos);
            if (faction.isEmpty()) {
                markers.remove(markerId);
                return;
            }
            markers.put(markerId, createMarker(chunkPos, faction.get(), false));
        } catch (Throwable error) {
            McProtectorMod.LOGGER.warn("Failed to update Squaremap marker: {}", error.getMessage());
        }
    }

    public static void syncClaims(net.minecraft.server.level.ServerLevel level, com.mcprotector.data.FactionData data) {
        if (!available || squaremap == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        try {
            Object world = getWorld(dimension);
            if (world == null) {
                return;
            }
            ensureLayerProvider(world, dimension);
            Map<String, Object> markers = markersByWorld.computeIfAbsent(dimension, key -> new HashMap<>());
            markers.clear();
            for (Map.Entry<Long, java.util.UUID> entry : data.getClaims().entrySet()) {
                ChunkPos chunkPos = new ChunkPos(entry.getKey());
                Optional<Faction> faction = data.getFaction(entry.getValue());
                if (faction.isPresent()) {
                    markers.put(markerId(chunkPos), createMarker(chunkPos, faction.get(), false));
                }
            }
            for (Map.Entry<Long, java.util.UUID> entry : data.getSafeZoneClaims().entrySet()) {
                ChunkPos chunkPos = new ChunkPos(entry.getKey());
                Optional<Faction> faction = data.getFaction(entry.getValue());
                if (faction.isPresent()) {
                    markers.put(markerId(chunkPos), createMarker(chunkPos, faction.get(), true));
                }
            }
        } catch (Throwable error) {
            McProtectorMod.LOGGER.warn("Failed to sync Squaremap markers: {}", error.getMessage());
        }
    }

    private static Object getWorld(String dimension) throws Exception {
        Method parse = worldIdentifierClass.getMethod("parse", String.class);
        Object worldId = parse.invoke(null, dimension);
        Method getWorldIfEnabled = squaremap.getClass().getMethod("getWorldIfEnabled", worldIdentifierClass);
        Object optionalWorld = getWorldIfEnabled.invoke(squaremap, worldId);
        if (optionalWorld instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return null;
    }

    private static void ensureLayerProvider(Object world, String dimension) throws Exception {
        if (providersByWorld.containsKey(dimension)) {
            return;
        }
        Method layerRegistryMethod = world.getClass().getMethod("layerRegistry");
        Object registry = layerRegistryMethod.invoke(world);
        Object key = keyClass.getMethod("of", String.class).invoke(null, LAYER_KEY);
        Method hasEntry = registry.getClass().getMethod("hasEntry", keyClass);
        boolean exists = (boolean) hasEntry.invoke(registry, key);
        if (exists) {
            Method get = registry.getClass().getMethod("get", keyClass);
            Object provider = get.invoke(registry, key);
            providersByWorld.put(dimension, provider);
            return;
        }
        Object provider = createLayerProvider(dimension);
        Method register = registry.getClass().getMethod("register", keyClass, layerProviderClass);
        register.invoke(registry, key, provider);
        providersByWorld.put(dimension, provider);
    }

    private static Object createLayerProvider(String dimension) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getLabel" -> "Faction Claims";
            case "layerPriority", "zIndex" -> LAYER_PRIORITY;
            case "showControls" -> true;
            case "defaultHidden" -> false;
            case "getMarkers" -> new ArrayList<>(markersByWorld.computeIfAbsent(dimension, key -> new HashMap<>()).values());
            default -> null;
        };
        return Proxy.newProxyInstance(layerProviderClass.getClassLoader(), new Class<?>[]{layerProviderClass}, handler);
    }

    private static Object createMarker(ChunkPos chunkPos, Faction faction, boolean safeZone) throws Exception {
        Method pointOf = pointClass.getMethod("of", double.class, double.class);
        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        double maxX = chunkPos.getMaxBlockX() + 1;
        double maxZ = chunkPos.getMaxBlockZ() + 1;
        Object point1 = pointOf.invoke(null, minX, minZ);
        Object point2 = pointOf.invoke(null, maxX, maxZ);
        Method rectangleMethod = markerClass.getMethod("rectangle", pointClass, pointClass);
        Object marker = rectangleMethod.invoke(null, point1, point2);
        Object builder = markerOptionsClass.getMethod("builder").invoke(null);
        int rgb = resolveFactionColor(faction, safeZone);
        Color color = new Color(rgb);
        markerOptionsBuilderClass.getMethod("strokeColor", Color.class).invoke(builder, color);
        markerOptionsBuilderClass.getMethod("fillColor", Color.class).invoke(builder, color);
        markerOptionsBuilderClass.getMethod("fillOpacity", double.class).invoke(builder, 0.35D);
        markerOptionsBuilderClass.getMethod("strokeWeight", int.class).invoke(builder, 1);
        markerOptionsBuilderClass.getMethod("hoverTooltip", String.class).invoke(builder, "Faction: " + faction.getName());
        markerOptionsBuilderClass.getMethod("clickTooltip", String.class).invoke(builder, "Faction: " + faction.getName());
        Object options = markerOptionsBuilderClass.getMethod("build").invoke(builder);
        markerClass.getMethod("markerOptions", markerOptionsClass).invoke(marker, options);
        return marker;
    }

    private static int resolveFactionColor(Faction faction, boolean safeZone) {
        if (safeZone) {
            return SAFE_ZONE_COLOR;
        }
        int rgb = faction.getColorRgb();
        if (rgb != 0) {
            return rgb;
        }
        int hash = Math.abs(faction.getName().hashCode());
        int red = 64 + (hash & 0x7F);
        int green = 64 + ((hash >> 7) & 0x7F);
        int blue = 64 + ((hash >> 14) & 0x7F);
        return (red << 16) | (green << 8) | blue;
    }

    private static String markerId(ChunkPos chunkPos) {
        return chunkPos.x + "_" + chunkPos.z;
    }
}
