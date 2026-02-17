package com.mcprotector.client;

public final class ClientColorHelper {
    private ClientColorHelper() {
    }

    /**
     * Converts a standard ARGB color value to the color ordering used by client GUI draw calls.
     */
    public static int toGuiColor(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    public static float red(int argb) {
        return (toGuiColor(argb) & 0xFF) / 255.0f;
    }

    public static float green(int argb) {
        return ((toGuiColor(argb) >>> 8) & 0xFF) / 255.0f;
    }

    public static float blue(int argb) {
        return ((toGuiColor(argb) >>> 16) & 0xFF) / 255.0f;
    }

    public static float alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }
}
