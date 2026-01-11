package com.mcprotector.network;

import com.mcprotector.McProtectorMod;
import com.mcprotector.service.FactionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class FactionActionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FactionActionPacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(McProtectorMod.MOD_ID, "faction_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionActionPacket> STREAM_CODEC =
        StreamCodec.ofMember(FactionActionPacket::write, FactionActionPacket::decode);
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

    public static FactionActionPacket syncDynmap() {
        return new FactionActionPacket(ActionType.SYNC_DYNMAP, "", "", "", false);
    }

    public static FactionActionPacket joinFaction(String factionName) {
        return new FactionActionPacket(ActionType.JOIN_FACTION, factionName, "", "", false);
    }

    public static FactionActionPacket declineInvite() {
        return new FactionActionPacket(ActionType.DECLINE_INVITE, "", "", "", false);
    }

    public static FactionActionPacket leaveFaction() {
        return new FactionActionPacket(ActionType.LEAVE_FACTION, "", "", "", false);
    }

    public static FactionActionPacket kickMember(String targetName) {
        return new FactionActionPacket(ActionType.KICK_MEMBER, targetName, "", "", false);
    }

    public static FactionActionPacket promoteMember(String targetName) {
        return new FactionActionPacket(ActionType.PROMOTE_MEMBER, targetName, "", "", false);
    }

    public static FactionActionPacket demoteMember(String targetName) {
        return new FactionActionPacket(ActionType.DEMOTE_MEMBER, targetName, "", "", false);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeUtf(targetName);
        buffer.writeUtf(role);
        buffer.writeUtf(permission);
        buffer.writeBoolean(grant);
    }

    public static FactionActionPacket decode(RegistryFriendlyByteBuf buffer) {
        ActionType action = buffer.readEnum(ActionType.class);
        String targetName = buffer.readUtf();
        String role = buffer.readUtf();
        String permission = buffer.readUtf();
        boolean grant = buffer.readBoolean();
        return new FactionActionPacket(action, targetName, role, permission, grant);
    }

    public static void handle(FactionActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
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
                case SYNC_DYNMAP -> {
                    try {
                        FactionService.syncDynmap(player.createCommandSourceStack());
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to sync Dynmap: " + ex.getMessage()));
                    }
                }
                case JOIN_FACTION -> {
                    try {
                        FactionService.joinFaction(player.createCommandSourceStack(), packet.targetName);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to join faction: " + ex.getMessage()));
                    }
                }
                case DECLINE_INVITE -> {
                    try {
                        FactionService.declineInvite(player.createCommandSourceStack());
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to decline invite: " + ex.getMessage()));
                    }
                }
                case LEAVE_FACTION -> {
                    try {
                        FactionService.leaveFaction(player.createCommandSourceStack());
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to leave faction: " + ex.getMessage()));
                    }
                }
                case KICK_MEMBER -> {
                    ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(packet.targetName);
                    if (target == null) {
                        player.sendSystemMessage(Component.literal("Player not found."));
                        return;
                    }
                    try {
                        FactionService.kickMember(player.createCommandSourceStack(), target);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to kick member: " + ex.getMessage()));
                    }
                }
                case PROMOTE_MEMBER -> {
                    ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(packet.targetName);
                    if (target == null) {
                        player.sendSystemMessage(Component.literal("Player not found."));
                        return;
                    }
                    try {
                        FactionService.setRole(player.createCommandSourceStack(), target, com.mcprotector.data.FactionRole.OFFICER);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to promote member: " + ex.getMessage()));
                    }
                }
                case DEMOTE_MEMBER -> {
                    ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(packet.targetName);
                    if (target == null) {
                        player.sendSystemMessage(Component.literal("Player not found."));
                        return;
                    }
                    try {
                        FactionService.setRole(player.createCommandSourceStack(), target, com.mcprotector.data.FactionRole.MEMBER);
                    } catch (Exception ex) {
                        player.sendSystemMessage(Component.literal("Failed to demote member: " + ex.getMessage()));
                    }
                }
            }
            NetworkHandler.sendToPlayer(player, FactionStatePacket.fromPlayer(player));
            NetworkHandler.sendToPlayer(player, FactionClaimMapPacket.fromPlayer(player));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum ActionType {
        INVITE,
        CLAIM,
        UNCLAIM,
        SET_PERMISSION,
        SYNC_DYNMAP,
        JOIN_FACTION,
        DECLINE_INVITE,
        LEAVE_FACTION,
        KICK_MEMBER,
        PROMOTE_MEMBER,
        DEMOTE_MEMBER
    }
}
