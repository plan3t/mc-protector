package com.mcprotector.client.map;

import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.client.gui.FactionMapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class XaeroWorldMapBackgroundProvider implements MapBackgroundProvider {
    public static final XaeroWorldMapBackgroundProvider INSTANCE = new XaeroWorldMapBackgroundProvider();

    private XaeroWorldMapBackgroundProvider() {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics,
                                 FactionMapClientData.MapSnapshot mapSnapshot,
                                 FactionMapRenderer.MapRegion region) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int radius = region.radius();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = mapSnapshot.centerChunkX() + dx;
                int chunkZ = mapSnapshot.centerChunkZ() + dz;
                int x = region.originX() + (dx + radius) * region.cellSize();
                int y = region.originY() + (dz + radius) * region.cellSize();
                int color = resolveChunkSurfaceColor(chunkX, chunkZ);
                guiGraphics.fill(x, y, x + region.cellSize(), y + region.cellSize(), color);
            }
        }
    }

    private int resolveChunkSurfaceColor(int chunkX, int chunkZ) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0xFF2F2F2F;
        }
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        BlockPos pos = new BlockPos(blockX, topY - 1, blockZ);
        BlockState state = level.getBlockState(pos);
        MapColor mapColor = state.getMapColor(level, pos);
        int rgb = mapColor.col;
        if (rgb == 0) {
            rgb = 0x4A4A4A;
        }
        return 0xFF000000 | rgb;
    }
}
