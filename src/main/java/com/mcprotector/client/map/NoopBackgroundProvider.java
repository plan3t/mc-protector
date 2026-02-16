package com.mcprotector.client.map;

import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.client.gui.FactionMapRenderer;
import net.minecraft.client.gui.GuiGraphics;

public final class NoopBackgroundProvider implements MapBackgroundProvider {
    public static final NoopBackgroundProvider INSTANCE = new NoopBackgroundProvider();

    private NoopBackgroundProvider() {
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics,
                                 FactionMapClientData.MapSnapshot mapSnapshot,
                                 FactionMapRenderer.MapRegion region) {
        // Intentionally blank. Keeps existing grid-only rendering as fallback.
    }
}
