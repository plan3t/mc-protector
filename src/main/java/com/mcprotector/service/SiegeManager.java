package com.mcprotector.service;

import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.webmap.WebmapBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SiegeManager {
    private static final long REQUIRED_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long BREAKAWAY_DEFENSE_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long DEFENSE_BREAK_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final long STATUS_BROADCAST_INTERVAL_MILLIS = 1000L;
    private static final Map<UUID, SiegeState> ACTIVE_SIEGES = new ConcurrentHashMap<>();

    private SiegeManager() {
    }

    public static boolean startSiege(ServerPlayer player, UUID attackerFactionId, UUID defenderFactionId, ChunkPos chunk) {
        if (ACTIVE_SIEGES.containsKey(attackerFactionId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        SiegeState state = new SiegeState(attackerFactionId, defenderFactionId, player.getUUID(), chunk,
            player.level().dimension().location().toString(), now, now, 0L, 0L, now, 0L, 0L);
        ACTIVE_SIEGES.put(attackerFactionId, state);
        return true;
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<SiegeState> iterator = ACTIVE_SIEGES.values().iterator();
        while (iterator.hasNext()) {
            SiegeState state = iterator.next();
            ResourceLocation dimensionId = ResourceLocation.tryParse(state.dimension());
            if (dimensionId == null) {
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) {
                continue;
            }
            FactionData data = FactionData.get(level);
            Optional<UUID> ownerId = data.getClaimOwner(state.chunk());
            if (ownerId.isEmpty() || !ownerId.get().equals(state.defenderFactionId())) {
                iterator.remove();
                continue;
            }
            if (!data.isAtWar(state.attackerFactionId(), state.defenderFactionId())) {
                iterator.remove();
                continue;
            }
            ServerPlayer attacker = server.getPlayerList().getPlayer(state.attackerPlayerId());
            boolean attackerInChunk = isAttackerInChunk(level, data, state.attackerFactionId(), state.chunk());
            boolean breakawayDefense = isBreakawayDefense(data, state.attackerFactionId(), state.defenderFactionId());
            long elapsed = now - state.lastTickMillis();
            if (elapsed <= 0) {
                state.updateLastTick(now);
                continue;
            }
            if (attacker != null
                && attacker.level().dimension().location().toString().equals(state.dimension())
                && new ChunkPos(attacker.blockPosition()).equals(state.chunk())
                && state.leaderKilledAtMillis() <= 0L) {
                state.addElapsed(elapsed);
            }
            state.updateLastTick(now);
            if (breakawayDefense) {
                if (!attackerInChunk) {
                    state.addDefenseElapsed(elapsed);
                } else {
                    state.resetDefenseElapsed();
                }
                if (state.defenseElapsedMillis() >= BREAKAWAY_DEFENSE_MILLIS) {
                    handleBreakawayDefenseSuccess(server, data, state);
                    iterator.remove();
                    continue;
                }
            }
            if (state.leaderKilledAtMillis() > 0L) {
                if (attackerInChunk) {
                    state.updateLastAttackerPresence(now);
                } else if (now - state.lastAttackerPresenceMillis() >= DEFENSE_BREAK_MILLIS) {
                    notifyFactionMembers(server, data, state.attackerFactionId(),
                        "Siege failed. The defenders pushed all attackers out for 2 minutes.");
                    notifyFactionMembers(server, data, state.defenderFactionId(),
                        "Siege broken. Your faction held the claim.");
                    iterator.remove();
                    continue;
                }
            }
            long requiredMillis = breakawayDefense ? BREAKAWAY_DEFENSE_MILLIS : REQUIRED_MILLIS;
            maybeSendStatus(server, data, state, now, attackerInChunk, breakawayDefense, requiredMillis);
            if (state.elapsedMillis() < requiredMillis) {
                continue;
            }
            Optional<Faction> attackerFaction = data.getFaction(state.attackerFactionId());
            Optional<Faction> defenderFaction = data.getFaction(state.defenderFactionId());
            if (attackerFaction.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (!data.overtakeChunk(state.chunk(), state.attackerFactionId())) {
                iterator.remove();
                continue;
            }
            WebmapBridge.updateClaim(state.chunk(), attackerFaction, level.dimension().location().toString());
            notifyFactionMembers(server, data, state.attackerFactionId(),
                "Siege successful. You have overtaken a claim from "
                    + defenderFaction.map(Faction::getName).orElse("an unknown faction") + ".");
            notifyFactionMembers(server, data, state.defenderFactionId(),
                "Your faction lost a claim to " + attackerFaction.get().getName() + ".");
            boolean breakawayComplete = data.recordVassalBreakawayCapture(state.attackerFactionId(), state.defenderFactionId());
            if (breakawayComplete) {
                data.clearRelation(state.attackerFactionId(), state.defenderFactionId());
                notifyFactionMembers(server, data, state.attackerFactionId(),
                    "Your faction has won its breakaway war and is now independent.");
                notifyFactionMembers(server, data, state.defenderFactionId(),
                    "Your vassal has won their breakaway war and is now independent.");
            }
            syncClaimMap(level);
            iterator.remove();
        }
    }

    public static void handleAttackerKilled(ServerPlayer attacker, ServerPlayer killer) {
        Optional<UUID> attackerFactionId = FactionData.get(attacker.serverLevel()).getFactionIdByPlayer(attacker.getUUID());
        Optional<UUID> killerFactionId = FactionData.get(killer.serverLevel()).getFactionIdByPlayer(killer.getUUID());
        if (attackerFactionId.isEmpty() || killerFactionId.isEmpty()) {
            return;
        }
        SiegeState state = ACTIVE_SIEGES.get(attackerFactionId.get());
        if (state == null || !state.attackerPlayerId().equals(attacker.getUUID())) {
            return;
        }
        if (!state.defenderFactionId().equals(killerFactionId.get())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (state.leaderKilledAtMillis() <= 0L) {
            state.setLeaderKilledAtMillis(now);
            state.updateLastAttackerPresence(now);
            MinecraftServer server = attacker.server;
            notifyFactionMembers(server, FactionData.get(attacker.serverLevel()), state.attackerFactionId(),
                "Siege leader killed by " + killer.getName().getString()
                    + ". Keep attackers in the claim or lose the siege.");
            notifyFactionMembers(server, FactionData.get(attacker.serverLevel()), state.defenderFactionId(),
                "Siege leader killed. Keep attackers out for 2 minutes to break the siege.");
        }
    }

    private static void notifyFactionMembers(MinecraftServer server, FactionData data, UUID factionId, String message) {
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isPresent() && recipientFactionId.get().equals(factionId)) {
                recipient.sendSystemMessage(Component.literal(message));
            }
        }
    }

    private static void sendFactionActionBar(MinecraftServer server, FactionData data, UUID factionId, String message) {
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isPresent() && recipientFactionId.get().equals(factionId)) {
                recipient.displayClientMessage(Component.literal(message), true);
            }
        }
    }

    private static void maybeSendStatus(MinecraftServer server, FactionData data, SiegeState state, long now,
                                        boolean attackerInChunk, boolean breakawayDefense, long requiredMillis) {
        if (now - state.lastStatusBroadcastMillis() < STATUS_BROADCAST_INTERVAL_MILLIS) {
            return;
        }
        long remainingSiege = Math.max(0L, requiredMillis - state.elapsedMillis());
        String siegeTimer = formatDuration(remainingSiege);
        String message = "Siege timer: " + siegeTimer;
        if (breakawayDefense) {
            long remainingDefense = Math.max(0L, BREAKAWAY_DEFENSE_MILLIS - state.defenseElapsedMillis());
            String defenseTimer = formatDuration(remainingDefense);
            message = "Breakaway defense: " + defenseTimer
                + (attackerInChunk ? " (attackers in claim)" : " (defenders holding)");
        }
        if (state.leaderKilledAtMillis() > 0L) {
            long remainingDefense = Math.max(0L, DEFENSE_BREAK_MILLIS - (now - state.lastAttackerPresenceMillis()));
            String defenseTimer = formatDuration(remainingDefense);
            message = "Siege timer: " + siegeTimer + " | Defense timer: " + defenseTimer
                + (attackerInChunk ? " (attackers in claim)" : " (no attackers)");
        }
        sendFactionActionBar(server, data, state.attackerFactionId(), message);
        sendFactionActionBar(server, data, state.defenderFactionId(), message);
        state.setLastStatusBroadcastMillis(now);
    }

    private static boolean isBreakawayDefense(FactionData data, UUID attackerFactionId, UUID defenderFactionId) {
        Optional<UUID> overlordId = data.getOverlord(defenderFactionId);
        return overlordId.isPresent()
            && overlordId.get().equals(attackerFactionId)
            && data.hasActiveBreakaway(defenderFactionId);
    }

    private static void handleBreakawayDefenseSuccess(MinecraftServer server, FactionData data, SiegeState state) {
        if (data.releaseVassal(state.attackerFactionId(), state.defenderFactionId())) {
            data.clearRelation(state.defenderFactionId(), state.attackerFactionId());
        }
        notifyFactionMembers(server, data, state.defenderFactionId(),
            "Your faction has defended long enough to win its breakaway war and is now independent.");
        notifyFactionMembers(server, data, state.attackerFactionId(),
            "Your vassal has defended long enough to win their breakaway war and is now independent.");
    }

    private static boolean isAttackerInChunk(ServerLevel level, FactionData data, UUID attackerFactionId, ChunkPos chunk) {
        for (ServerPlayer player : level.players()) {
            Optional<UUID> playerFactionId = data.getFactionIdByPlayer(player.getUUID());
            if (playerFactionId.isPresent()
                && playerFactionId.get().equals(attackerFactionId)
                && new ChunkPos(player.blockPosition()).equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void syncClaimMap(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            com.mcprotector.network.NetworkHandler.sendToPlayer(player,
                com.mcprotector.network.FactionClaimMapPacket.fromPlayer(player));
        }
    }

    private static final class SiegeState {
        private final UUID attackerFactionId;
        private final UUID defenderFactionId;
        private final UUID attackerPlayerId;
        private final ChunkPos chunk;
        private final String dimension;
        private final long startedAtMillis;
        private long lastTickMillis;
        private long elapsedMillis;
        private long leaderKilledAtMillis;
        private long lastAttackerPresenceMillis;
        private long lastStatusBroadcastMillis;
        private long defenseElapsedMillis;

        private SiegeState(UUID attackerFactionId, UUID defenderFactionId, UUID attackerPlayerId, ChunkPos chunk,
                           String dimension, long startedAtMillis, long lastTickMillis, long elapsedMillis,
                           long leaderKilledAtMillis, long lastAttackerPresenceMillis, long lastStatusBroadcastMillis,
                           long defenseElapsedMillis) {
            this.attackerFactionId = attackerFactionId;
            this.defenderFactionId = defenderFactionId;
            this.attackerPlayerId = attackerPlayerId;
            this.chunk = chunk;
            this.dimension = dimension;
            this.startedAtMillis = startedAtMillis;
            this.lastTickMillis = lastTickMillis;
            this.elapsedMillis = elapsedMillis;
            this.leaderKilledAtMillis = leaderKilledAtMillis;
            this.lastAttackerPresenceMillis = lastAttackerPresenceMillis;
            this.lastStatusBroadcastMillis = lastStatusBroadcastMillis;
            this.defenseElapsedMillis = defenseElapsedMillis;
        }

        public UUID attackerFactionId() {
            return attackerFactionId;
        }

        public UUID defenderFactionId() {
            return defenderFactionId;
        }

        public UUID attackerPlayerId() {
            return attackerPlayerId;
        }

        public ChunkPos chunk() {
            return chunk;
        }

        public String dimension() {
            return dimension;
        }

        public long lastTickMillis() {
            return lastTickMillis;
        }

        public long elapsedMillis() {
            return elapsedMillis;
        }

        public long leaderKilledAtMillis() {
            return leaderKilledAtMillis;
        }

        public long lastAttackerPresenceMillis() {
            return lastAttackerPresenceMillis;
        }

        public long lastStatusBroadcastMillis() {
            return lastStatusBroadcastMillis;
        }

        public long defenseElapsedMillis() {
            return defenseElapsedMillis;
        }

        public void updateLastTick(long now) {
            lastTickMillis = now;
        }

        public void addElapsed(long delta) {
            elapsedMillis += delta;
        }

        public void addDefenseElapsed(long delta) {
            defenseElapsedMillis += delta;
        }

        public void resetDefenseElapsed() {
            defenseElapsedMillis = 0L;
        }

        public void setLeaderKilledAtMillis(long now) {
            leaderKilledAtMillis = now;
        }

        public void updateLastAttackerPresence(long now) {
            lastAttackerPresenceMillis = now;
        }

        public void setLastStatusBroadcastMillis(long now) {
            lastStatusBroadcastMillis = now;
        }
    }
}
