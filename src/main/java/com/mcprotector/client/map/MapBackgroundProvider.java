package com.mcprotector.client.map;

import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.client.gui.FactionMapRenderer;
import net.minecraft.client.gui.GuiGraphics;

public interface MapBackgroundProvider {
    void renderBackground(GuiGraphics guiGraphics,
                          FactionMapClientData.MapSnapshot mapSnapshot,
                          FactionMapRenderer.MapRegion region);
}
