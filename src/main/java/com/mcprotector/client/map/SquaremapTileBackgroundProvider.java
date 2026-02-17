package com.mcprotector.client.map;

import com.mcprotector.McProtectorMod;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.client.gui.FactionMapRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SquaremapTileBackgroundProvider implements MapBackgroundProvider {
    public static final SquaremapTileBackgroundProvider INSTANCE = new SquaremapTileBackgroundProvider();
    private static final int TILE_TEXTURE_SIZE = 256;
    private static final int MAX_CACHED_TILES = 128;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "mcprotector-squaremap-fetch");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<TileKey, TileTexture> CACHE = new LinkedHashMap<>(128, 0.75f, true);
    private static final Set<TileKey> IN_FLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private SquaremapTileBackgroundProvider() {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics,
                                 FactionMapClientData.MapSnapshot mapSnapshot,
                                 FactionMapRenderer.MapRegion region) {
        FactionMapClientData.MapBackgroundState state = mapSnapshot.backgroundState();
        if (state == null || !state.enabled() || !state.available()) {
            return;
        }
        int minChunkX = mapSnapshot.centerChunkX() - region.radius();
        int minChunkZ = mapSnapshot.centerChunkZ() - region.radius();
        int maxChunkX = mapSnapshot.centerChunkX() + region.radius() + 1;
        int maxChunkZ = mapSnapshot.centerChunkZ() + region.radius() + 1;

        int minBlockX = minChunkX * 16;
        int minBlockZ = minChunkZ * 16;
        int maxBlockX = maxChunkX * 16;
        int maxBlockZ = maxChunkZ * 16;

        int tileSpan = tileSpanBlocks(state);
        int minTileX = floorDiv(minBlockX, tileSpan);
        int minTileY = floorDiv(minBlockZ, tileSpan);
        int maxTileX = floorDiv(maxBlockX - 1, tileSpan);
        int maxTileY = floorDiv(maxBlockZ - 1, tileSpan);

        for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                TileKey key = new TileKey(state.worldName(), state.zoom(), tileX, tileY, state.tileUrlTemplate(), state.tileBlockSpan());
                TileTexture texture = getOrQueue(key);
                if (texture == null || texture.resource() == null) {
                    continue;
                }
                int tileMinBlockX = tileX * tileSpan;
                int tileMinBlockZ = tileY * tileSpan;
                int tileMaxBlockX = tileMinBlockX + tileSpan;
                int tileMaxBlockZ = tileMinBlockZ + tileSpan;

                int drawX = region.originX() + Math.round(((tileMinBlockX - minBlockX) / 16f) * region.cellSize());
                int drawY = region.originY() + Math.round(((tileMinBlockZ - minBlockZ) / 16f) * region.cellSize());
                int drawEndX = region.originX() + Math.round(((tileMaxBlockX - minBlockX) / 16f) * region.cellSize());
                int drawEndY = region.originY() + Math.round(((tileMaxBlockZ - minBlockZ) / 16f) * region.cellSize());
                int width = drawEndX - drawX;
                int height = drawEndY - drawY;
                if (width <= 0 || height <= 0) {
                    continue;
                }
                guiGraphics.blit(texture.resource(), drawX, drawY, 0, 0.0f, 0.0f, width, height, TILE_TEXTURE_SIZE, TILE_TEXTURE_SIZE);
            }
        }
    }

    private static int tileSpanBlocks(FactionMapClientData.MapBackgroundState state) {
        return state.tileBlockSpan() * (1 << state.zoom());
    }

    private static int floorDiv(int value, int divisor) {
        int result = value / divisor;
        if ((value ^ divisor) < 0 && value % divisor != 0) {
            result--;
        }
        return result;
    }

    private static TileTexture getOrQueue(TileKey key) {
        synchronized (CACHE) {
            TileTexture existing = CACHE.get(key);
            if (existing != null) {
                return existing;
            }
        }
        if (IN_FLIGHT.add(key)) {
            FETCH_EXECUTOR.submit(() -> fetchTile(key));
        }
        return null;
    }

    private static void fetchTile(TileKey key) {
        try {
            String url = key.template()
                .replace("{world}", key.world())
                .replace("{z}", String.valueOf(key.zoom()))
                .replace("{x}", String.valueOf(key.tileX()))
                .replace("{y}", String.valueOf(key.tileY()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();
            HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return;
            }
            byte[] body = response.body();
            Minecraft.getInstance().execute(() -> registerTexture(key, body));
        } catch (Exception error) {
            McProtectorMod.LOGGER.debug("Squaremap tile fetch failed for {}: {}", key, error.getMessage());
        } finally {
            IN_FLIGHT.remove(key);
        }
    }

    private static void registerTexture(TileKey key, byte[] imageBytes) {
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes))) {
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation textureId = Minecraft.getInstance().getTextureManager().register(
                "mcprotector/squaremap_" + key.zoom() + "_" + key.tileX() + "_" + key.tileY(), texture
            );
            synchronized (CACHE) {
                CACHE.put(key, new TileTexture(textureId));
                while (CACHE.size() > MAX_CACHED_TILES) {
                    Map.Entry<TileKey, TileTexture> first = CACHE.entrySet().iterator().next();
                    Minecraft.getInstance().getTextureManager().release(first.getValue().resource());
                    CACHE.remove(first.getKey());
                }
            }
        } catch (Exception error) {
            McProtectorMod.LOGGER.debug("Failed to decode Squaremap tile {}: {}", key, error.getMessage());
        }
    }

    private record TileKey(String world, int zoom, int tileX, int tileY, String template, int baseTileSpan) {
    }

    private record TileTexture(ResourceLocation resource) {
    }
}
