package com.mcprotector.client.gui;

import com.mcprotector.client.FactionMapClientData;
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
        int maxHeight = height - startY - 40;
        int cellSize = Math.max(8, Math.min(22, Math.min(maxWidth / gridSize, maxHeight / gridSize)));
        int mapWidth = cellSize * gridSize;
        int mapHeight = cellSize * gridSize;
        int originX = (width - mapWidth) / 2;
        int originY = startY + 6;
        if (originY + mapHeight > height - panelPadding - 30) {
            originY = Math.max(startY + 16, height - panelPadding - 30 - mapHeight);
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
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = mapSnapshot.centerChunkX() + dx;
                int chunkZ = mapSnapshot.centerChunkZ() + dz;
                int x = region.originX() + (dx + radius) * region.cellSize();
                int y = region.originY() + (dz + radius) * region.cellSize();
                long key = new ChunkPos(chunkX, chunkZ).toLong();
                com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
                int color = getMapColor(entry);
                guiGraphics.fill(x, y, x + region.cellSize(), y + region.cellSize(), color);
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

    public static int renderMapClaimsList(GuiGraphics guiGraphics,
                                          List<com.mcprotector.network.FactionStatePacket.ClaimEntry> claims,
                                          MapRegion region,
                                          int scrollOffset,
                                          int height,
                                          int panelPadding,
                                          Font font) {
        int startY = region.originY() + (region.cellSize() * (region.radius() * 2 + 1)) + 12;
        guiGraphics.drawString(font, "Claims:", panelPadding, startY, 0xFFFFFF);
        int y = startY + 12;
        if (claims.isEmpty()) {
            guiGraphics.drawString(font, "No claims.", panelPadding, y, 0x777777);
            return 0;
        }
        int lineHeight = 10;
        int availableHeight = Math.max(0, height - y - 6);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, claims.size() - visibleLines);
        int clampedOffset = Math.min(scrollOffset, maxOffset);
        List<com.mcprotector.network.FactionStatePacket.ClaimEntry> visibleClaims = claims
            .subList(clampedOffset, Math.min(claims.size(), clampedOffset + visibleLines));
        for (var claim : visibleClaims) {
            guiGraphics.drawString(font, "Chunk " + claim.chunkX() + ", " + claim.chunkZ(), panelPadding, y, 0xCCCCCC);
            y += lineHeight;
        }
        return clampedOffset;
    }

    private static int getMapColor(com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry) {
        if (entry == null) {
            return 0xFF3A3A3A;
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

    public record MapRegion(int originX, int originY, int cellSize, int radius) {
    }
}
