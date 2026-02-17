package com.mcprotector.service;

import com.mcprotector.data.FactionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HomeTeleportManager {
    private static final int TELEPORT_DELAY_SECONDS = 20;
    private static final Map<UUID, PendingHomeTeleport> PENDING = new HashMap<>();

    public static void startTeleport(ServerPlayer player, ServerLevel level, BlockPos pos) {
        FactionData data = FactionData.get(level);
        var faction = data.getFactionByPlayer(player.getUUID());
        if (faction.isEmpty()) {
            return;
        }
        PENDING.put(player.getUUID(), new PendingHomeTeleport(level, pos, faction.get().getId(), player.position(),
            System.currentTimeMillis()));
        player.sendSystemMessage(Component.literal("Starting Home Teleport Sequence: Don't move!"));
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, PendingHomeTeleport>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingHomeTeleport> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.isDeadOrDying()) {
                iterator.remove();
                continue;
            }
            PendingHomeTeleport pending = entry.getValue();
            Vec3 delta = player.position().subtract(pending.startPos());
            if (delta.horizontalDistanceSqr() > 0.01D) {
                cancel(player, "Home teleport cancelled: you moved.");
                iterator.remove();
                continue;
            }
            if (System.currentTimeMillis() - pending.lastParticleAt() >= 700L) {
                pending.level().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0,
                    player.getZ(), 20, 0.6, 0.8, 0.6, 0.02);
                pending.lastParticleAt = System.currentTimeMillis();
            }
            int elapsed = (int) ((System.currentTimeMillis() - pending.startedAt()) / 1000L);
            int remaining = Math.max(0, TELEPORT_DELAY_SECONDS - elapsed);
            if (pending.shouldSendCountdownNotice(remaining)) {
                player.sendSystemMessage(Component.literal("Home teleport in " + remaining + "s..."));
            }
            if (remaining > 0) {
                continue;
            }
            Optional<UUID> ownerId = FactionData.get(pending.level()).getClaimOwner(new ChunkPos(pending.targetPos()));
            if (ownerId.isEmpty() || !ownerId.get().equals(pending.factionId())) {
                cancel(player, "Home teleport cancelled: home chunk is no longer claimed.");
                iterator.remove();
                continue;
            }
            player.teleportTo(pending.level(), pending.targetPos().getX() + 0.5, pending.targetPos().getY(),
                pending.targetPos().getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleported to faction home."));
            iterator.remove();
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING.containsKey(player.getUUID())) {
            cancel(player, "Home teleport cancelled: you were attacked.");
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING.containsKey(player.getUUID())) {
            cancel(player, "Home teleport cancelled: combat interaction detected.");
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING.containsKey(player.getUUID())) {
            cancel(player, "Home teleport cancelled: interaction detected.");
            PENDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PENDING.containsKey(player.getUUID())) {
            cancel(player, "Home teleport cancelled: interaction detected.");
            PENDING.remove(player.getUUID());
        }
    }

    private static void cancel(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason));
    }

    private static final class PendingHomeTeleport {
        private final ServerLevel level;
        private final BlockPos targetPos;
        private final UUID factionId;
        private final Vec3 startPos;
        private final long startedAt;
        private int lastNoticeRemaining;
        private long lastParticleAt;

        private PendingHomeTeleport(ServerLevel level, BlockPos targetPos, UUID factionId, Vec3 startPos, long startedAt) {
            this.level = level;
            this.targetPos = targetPos;
            this.factionId = factionId;
            this.startPos = startPos;
            this.startedAt = startedAt;
            this.lastNoticeRemaining = Integer.MIN_VALUE;
            this.lastParticleAt = startedAt;
        }

        private ServerLevel level() {
            return level;
        }

        private BlockPos targetPos() {
            return targetPos;
        }

        private UUID factionId() {
            return factionId;
        }

        private Vec3 startPos() {
            return startPos;
        }

        private long startedAt() {
            return startedAt;
        }

        private long lastParticleAt() {
            return lastParticleAt;
        }

        private boolean shouldSendCountdownNotice(int remaining) {
            if (remaining == lastNoticeRemaining) {
                return false;
            }
            lastNoticeRemaining = remaining;
            return remaining == 10 || (remaining <= 3 && remaining > 0);
        }
    }
}
