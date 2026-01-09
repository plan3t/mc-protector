package com.mcprotector.network;

import com.mcprotector.service.FactionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FactionActionPacket {
    private final ActionType action;
    private final String targetName;
    private final String role;
    private final String permission;
    private final boolean grant;

    public FactionActionPacket(ActionType action, String targetName, String role, String permission, boolean grant) {
        this.action = action;
        this.targetName = targetName;
        this.role = role;
        this.permission = permission;
        this.grant = grant;
    }

    public static FactionActionPacket invite(String targetName) {
        return new FactionActionPacket(ActionType.INVITE, targetName, "", "", false);
    }

    public static FactionActionPacket claim() {
        return new FactionActionPacket(ActionType.CLAIM, "", "", "", false);
    }

    public static FactionActionPacket unclaim() {
        return new FactionActionPacket(ActionType.UNCLAIM, "", "", "", false);
    }

    public static FactionActionPacket setPermission(String role, String permission, boolean grant) {
        return new FactionActionPacket(ActionType.SET_PERMISSION, "", role, permission, grant);
    }

    public static void encode(FactionActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.targetName);
        buffer.writeUtf(packet.role);
        buffer.writeUtf(packet.permission);
        buffer.writeBoolean(packet.grant);
    }

    public static FactionActionPacket decode(FriendlyByteBuf buffer) {
        ActionType action = buffer.readEnum(ActionType.class);
        String targetName = buffer.readUtf();
        String role = buffer.readUtf();
        String permission = buffer.readUtf();
        boolean grant = buffer.readBoolean();
        return new FactionActionPacket(action, targetName, role, permission, grant);
    }

    public static void handle(FactionActionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            switch (packet.action) {
                case INVITE -> {
                    ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(packet.targetName);
                    if (target == null) {
                        player.sendSystemMessage(Component.literal("Player not found."));
                        return;
                    }
                    try {
                        FactionService.invitePlayer(player.createCommandSourceStack(), target);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to send invite: " + ex.getMessage()));
                    }
                }
                case CLAIM -> {
                    try {
                        FactionService.claimChunk(player.createCommandSourceStack());
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to claim chunk: " + ex.getMessage()));
                    }
                }
                case UNCLAIM -> {
                    try {
                        FactionService.unclaimChunk(player.createCommandSourceStack());
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to unclaim chunk: " + ex.getMessage()));
                    }
                }
                case SET_PERMISSION -> {
                    try {
                        FactionService.updatePermission(player.createCommandSourceStack(), packet.role, packet.permission, packet.grant);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to update permissions: " + ex.getMessage()));
                    }
                }
            }
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
        });
        ctx.setPacketHandled(true);
    }

    public enum ActionType {
        INVITE,
        CLAIM,
        UNCLAIM,
        SET_PERMISSION
    }
}
