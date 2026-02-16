package com.mcprotector.client.map;

import com.mcprotector.client.FactionMapClientData;

public final class MapBackgroundProviders {
    private MapBackgroundProviders() {
    }

    public static MapBackgroundProvider resolve(FactionMapClientData.MapBackgroundState backgroundState) {
        if (backgroundState == null || !backgroundState.available() || !backgroundState.enabled()) {
            return NoopBackgroundProvider.INSTANCE;
        }
        return switch (backgroundState.providerType()) {
            case SQUAREMAP -> SquaremapTileBackgroundProvider.INSTANCE;
            case NONE -> NoopBackgroundProvider.INSTANCE;
        };
    }
}
