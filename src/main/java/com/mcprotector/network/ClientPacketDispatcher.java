package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;

public final class ClientPacketDispatcher {
    private static final String CLIENT_HANDLER_CLASS = "com.mcprotector.client.ClientPacketHandler";

    private ClientPacketDispatcher() {
    }

    public static void handleFactionState(FactionStatePacket packet) {
        invokeClientHandler("handleFactionState", new Class<?>[]{FactionStatePacket.class}, new Object[]{packet});
    }

    public static void handleClaimMap(FactionClaimMapPacket packet) {
        invokeClientHandler("handleClaimMap", new Class<?>[]{FactionClaimMapPacket.class}, new Object[]{packet});
    }

    private static void invokeClientHandler(String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> handlerClass = Class.forName(CLIENT_HANDLER_CLASS);
            Method method = handlerClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ClassNotFoundException ignored) {
            // Client handler is not present in server-only environments.
        } catch (ReflectiveOperationException ex) {
            McProtectorMod.LOGGER.warn("Failed to invoke client packet handler {}: {}", methodName, ex.getMessage());
        }
    }
}
