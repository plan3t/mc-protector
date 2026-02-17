package com.mcprotector.client.gui;

import com.mcprotector.client.ClientColorHelper;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.client.map.MapBackgroundProvider;
import com.mcprotector.client.map.MapBackgroundProviders;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.List;

public final class FactionMapRenderer {
    private FactionMapRenderer() {
    }

    public static MapRegion buildMapRegion(int startY, int radius, int width, int height, int panelPadding) {
        int gridSize = radius * 2 + 1;
        int maxWidth = width - panelPadding * 2;
        int maxHeight = height - startY - 70;
        int cellSize = Math.max(7, Math.min(18, Math.min(maxWidth / gridSize, maxHeight / gridSize)));
        int mapWidth = cellSize * gridSize;
        int mapHeight = cellSize * gridSize;
        int originX = (width - mapWidth) / 2;
        int originY = startY + 2;
        if (originY + mapHeight > height - panelPadding - 30) {
            originY = Math.max(startY + 12, height - panelPadding - 30 - mapHeight);
        }
        return new MapRegion(originX, originY, cellSize, radius);
    }

    public static int getMapClaimsListStart(MapRegion region) {
        return region.originY() + (region.cellSize() * (region.radius() * 2 + 1)) + 12;
    }





    public static ChunkPos getChunkFromMouse(MapRegion region, double mouseX, double mouseY,
                                             FactionMapClientData.MapSnapshot mapSnapshot) {
        if (region == null) {
            return null;
        }
        int size = region.cellSize() * (region.radius() * 2 + 1);
        if (mouseX < region.originX() || mouseY < region.originY()
            || mouseX >= region.originX() + size || mouseY >= region.originY() + size) {
            return null;
        }
        int dx = (int) ((mouseX - region.originX()) / region.cellSize()) - region.radius();
        int dz = (int) ((mouseY - region.originY()) / region.cellSize()) - region.radius();
        return new ChunkPos(mapSnapshot.centerChunkX() + dx, mapSnapshot.centerChunkZ() + dz);
    }

    public static void renderMapGrid(GuiGraphics guiGraphics, FactionMapClientData.MapSnapshot mapSnapshot, MapRegion region) {
        int radius = region.radius();
        int size = region.cellSize() * (radius * 2 + 1);
        int mapX = region.originX();
        int mapY = region.originY();
        int mapEndX = mapX + size;
        int mapEndY = mapY + size;
        guiGraphics.fill(mapX - 2, mapY - 2, mapEndX + 2, mapEndY + 2, 0x66000000);
        guiGraphics.renderOutline(mapX - 2, mapY - 2, size + 4, size + 4, 0xFFE6E6E6);
        guiGraphics.renderOutline(mapX, mapY, size, size, 0xFFFFFFFF);
        MapBackgroundProvider backgroundProvider = MapBackgroundProviders.resolve(mapSnapshot.backgroundState());
        backgroundProvider.renderBackground(guiGraphics, mapSnapshot, region);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = mapSnapshot.centerChunkX() + dx;
                int chunkZ = mapSnapshot.centerChunkZ() + dz;
                int x = region.originX() + (dx + radius) * region.cellSize();
                int y = region.originY() + (dz + radius) * region.cellSize();
                long key = new ChunkPos(chunkX, chunkZ).toLong();
                com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
                int color = getMapColor(entry);
                if (mapSnapshot.backgroundState() != null && mapSnapshot.backgroundState().enabled()) {
                    color = withAlpha(color, 0xA0);
                }
                guiGraphics.fill(x, y, x + region.cellSize(), y + region.cellSize(), color);
                int halfCell = Math.max(1, region.cellSize() / 2);
                guiGraphics.fill(x, y, x + halfCell, y + halfCell, 0x18FFFFFF);
                guiGraphics.fill(x + halfCell, y + halfCell, x + region.cellSize(), y + region.cellSize(), 0x18000000);
                int gridColor = shadeColor(color, 0.75f);
                guiGraphics.renderOutline(x, y, region.cellSize(), region.cellSize(), gridColor);
            }
        }
        int centerX = region.originX() + radius * region.cellSize();
        int centerY = region.originY() + radius * region.cellSize();
        guiGraphics.renderOutline(centerX, centerY, region.cellSize(), region.cellSize(), 0xFFFFFFFF);
    }

    public static void renderSelectionOverlay(GuiGraphics guiGraphics, FactionMapClientData.MapSnapshot mapSnapshot,
                                              MapRegion region, Collection<ChunkPos> selections) {
        int radius = region.radius();
        for (ChunkPos chunk : selections) {
            int dx = chunk.x - mapSnapshot.centerChunkX();
            int dz = chunk.z - mapSnapshot.centerChunkZ();
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                continue;
            }
            int x = region.originX() + (dx + radius) * region.cellSize();
            int y = region.originY() + (dz + radius) * region.cellSize();
            guiGraphics.fill(x + 1, y + 1, x + region.cellSize() - 1, y + region.cellSize() - 1, 0x66F9A825);
            guiGraphics.renderOutline(x, y, region.cellSize(), region.cellSize(), 0xFFF9A825);
        }
    }

    public static void renderMapTooltip(GuiGraphics guiGraphics, FactionMapClientData.MapSnapshot mapSnapshot, ChunkPos hovered,
                                        int mouseX, int mouseY, Font font) {
        long key = hovered.toLong();
        com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
        List<Component> lines;
        if (entry == null) {
            lines = List.of(Component.literal("Wilderness"));
        } else if (entry.safeZone()) {
            lines = List.of(
                Component.literal("Safe Zone"),
                Component.literal(entry.factionName())
            );
        } else if (entry.personal()) {
            String relation = entry.relation().equals("OWN") ? "Your personal claim" : "Personal claim";
            lines = List.of(
                Component.literal(entry.factionName()),
                Component.literal(relation)
            );
        } else {
            String relation = entry.relation().equals("OWN") ? "Your faction" : entry.relation();
            lines = List.of(
                Component.literal(entry.factionName()),
                Component.literal(relation)
            );
        }
        List<net.minecraft.util.FormattedCharSequence> tooltip = lines.stream()
            .map(Component::getVisualOrderText)
            .toList();
        guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static int getMapColor(com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry) {
        if (entry == null) {
            return 0xFF3A3A3A;
        }
        int color = entry.color();
        if (color != 0) {
            return color;
        }
        if (entry.safeZone()) {
            return 0xFFF9A825;
        }
        if (entry.personal()) {
            return 0xFF9C27B0;
        }
        return switch (entry.relation()) {
            case "OWN" -> 0xFF4CAF50;
            case "ALLY" -> 0xFF4FC3F7;
            case "WAR" -> 0xFFEF5350;
            default -> 0xFF8D8D8D;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int shadeColor(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        red = Math.min(255, Math.max(0, Math.round(red * factor)));
        green = Math.min(255, Math.max(0, Math.round(green * factor)));
        blue = Math.min(255, Math.max(0, Math.round(blue * factor)));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public record MapRegion(int originX, int originY, int cellSize, int radius) {
    }
}
