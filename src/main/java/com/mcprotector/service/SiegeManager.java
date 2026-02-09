package com.mcprotector.service;

import com.mcprotector.config.FactionConfig;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SiegeManager {
    private static final long REQUIRED_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long BREAKAWAY_ATTACK_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long BREAKAWAY_DEFENSE_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long DEFENSE_BREAK_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final long STATUS_BROADCAST_INTERVAL_MILLIS = 1000L;
    private static final Map<UUID, SiegeState> ACTIVE_SIEGES = new ConcurrentHashMap<>();

    private SiegeManager() {
    }

    public static boolean startSiege(ServerPlayer player, UUID attackerFactionId, UUID defenderFactionId, ChunkPos chunk) {
        if (!FactionConfig.SERVER.enableSieges.get()) {
            return false;
        }
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
        if (!FactionConfig.SERVER.enableSieges.get()) {
            clearAllSieges();
            return;
        }
        startBreakawayCapitalSieges(server);
        long now = System.currentTimeMillis();
        Iterator<SiegeState> iterator = ACTIVE_SIEGES.values().iterator();
        while (iterator.hasNext()) {
            SiegeState state = iterator.next();
            ResourceLocation dimensionId = ResourceLocation.tryParse(state.dimension());
            if (dimensionId == null) {
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) {
                continue;
            }
            FactionData data = FactionData.get(level);
            boolean breakawayAttack = isBreakawayAttack(data, state.attackerFactionId(), state.defenderFactionId(), state.chunk(), state.dimension());
            if (!breakawayAttack) {
                Optional<UUID> ownerId = data.getClaimOwner(state.chunk());
                if (ownerId.isEmpty() || !ownerId.get().equals(state.defenderFactionId())) {
                    clearBossBars(state);
                    iterator.remove();
                    continue;
                }
            }
            if (!data.isAtWar(state.attackerFactionId(), state.defenderFactionId())) {
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            boolean attackerInChunk = isAttackerInChunk(level, data, state.attackerFactionId(), state.chunk());
            boolean breakawayDefense = !breakawayAttack && isBreakawayDefense(data, state.attackerFactionId(), state.defenderFactionId());
            long elapsed = now - state.lastTickMillis();
            if (elapsed <= 0) {
                state.updateLastTick(now);
                continue;
            }
            if (attackerInChunk) {
                state.addElapsed(elapsed);
            }
            if (attackerInChunk) {
                state.updateLastAttackerPresence(now);
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
                    clearBossBars(state);
                    iterator.remove();
                    continue;
                }
            }
            if (!attackerInChunk && now - state.lastAttackerPresenceMillis() >= DEFENSE_BREAK_MILLIS) {
                notifyFactionMembers(server, data, state.attackerFactionId(),
                    "Siege failed. The defenders held the claim for 2 minutes with no attackers present.");
                notifyFactionMembers(server, data, state.defenderFactionId(),
                    "Siege broken. Your faction held the claim for 2 minutes.");
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            long requiredMillis = breakawayAttack ? BREAKAWAY_ATTACK_MILLIS : REQUIRED_MILLIS;
            updateBossBars(server, data, state, now, attackerInChunk, breakawayAttack, requiredMillis);
            if (state.elapsedMillis() < requiredMillis) {
                continue;
            }
            if (breakawayAttack) {
                handleBreakawayAttackSuccess(server, data, state);
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            Optional<Faction> attackerFaction = data.getFaction(state.attackerFactionId());
            Optional<Faction> defenderFaction = data.getFaction(state.defenderFactionId());
            if (attackerFaction.isEmpty()) {
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            if (!data.overtakeChunk(state.chunk(), state.attackerFactionId())) {
                clearBossBars(state);
                iterator.remove();
                continue;
            }
            WebmapBridge.updateClaim(state.chunk(), attackerFaction, level.dimension().location().toString());
            notifyFactionMembers(server, data, state.attackerFactionId(),
                "Siege successful. You have overtaken a claim from "
                    + defenderFaction.map(Faction::getName).orElse("an unknown faction") + ".");
            notifyFactionMembers(server, data, state.defenderFactionId(),
                "Your faction lost a claim to " + attackerFaction.get().getName() + ".");
            syncClaimMap(level);
            clearBossBars(state);
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

    private static void startBreakawayCapitalSieges(MinecraftServer server) {
        if (!FactionConfig.SERVER.enableVassals.get() || !FactionConfig.SERVER.enableVassalBreakaways.get()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            FactionData data = FactionData.get(level);
            for (Map.Entry<UUID, FactionData.VassalBreakaway> entry : data.getActiveBreakaways().entrySet()) {
                UUID vassalId = entry.getKey();
                UUID overlordId = entry.getValue().overlordId();
                Optional<FactionData.FactionHome> home = data.getFactionHome(vassalId);
                if (home.isEmpty()) {
                    continue;
                }
                if (!home.get().dimension().equals(level.dimension().location().toString())) {
                    continue;
                }
                ChunkPos capitalChunk = new ChunkPos(home.get().pos());
                if (ACTIVE_SIEGES.containsKey(overlordId)) {
                    continue;
                }
                for (ServerPlayer player : level.players()) {
                    Optional<UUID> factionId = data.getFactionIdByPlayer(player.getUUID());
                    if (factionId.isEmpty() || !factionId.get().equals(overlordId)) {
                        continue;
                    }
                    if (!new ChunkPos(player.blockPosition()).equals(capitalChunk)) {
                        continue;
                    }
                    if (data.isAtWar(overlordId, vassalId)) {
                        startSiege(player, overlordId, vassalId, capitalChunk);
                    }
                    break;
                }
            }
        }
    }


    private static void clearAllSieges() {
        Iterator<SiegeState> iterator = ACTIVE_SIEGES.values().iterator();
        while (iterator.hasNext()) {
            SiegeState state = iterator.next();
            clearBossBars(state);
            iterator.remove();
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

    private static void updateBossBars(MinecraftServer server, FactionData data, SiegeState state, long now,
                                       boolean attackerInChunk, boolean breakawayAttack, long requiredMillis) {
        float siegeProgress = progressFor(requiredMillis - state.elapsedMillis(), requiredMillis);
        ServerBossEvent siegeBar = state.ensureSiegeBossBar();
        siegeBar.setName(Component.literal("Siege Timer: " + formatDuration(Math.max(0L, requiredMillis - state.elapsedMillis()))));
        siegeBar.setProgress(siegeProgress);
        siegeBar.setVisible(true);
        syncBossBarPlayers(server, data, state, siegeBar);

        ServerBossEvent defenseBar = state.ensureDefenseBossBar();
        if (attackerInChunk) {
            defenseBar.setVisible(false);
        } else {
            long remainingDefense = Math.max(0L, DEFENSE_BREAK_MILLIS - (now - state.lastAttackerPresenceMillis()));
            defenseBar.setName(Component.literal("Defense Timer: " + formatDuration(remainingDefense)));
            defenseBar.setProgress(progressFor(remainingDefense, DEFENSE_BREAK_MILLIS));
            defenseBar.setVisible(true);
            syncBossBarPlayers(server, data, state, defenseBar);
        }

        ServerBossEvent breakawayBar = state.ensureBreakawayBossBar();
        if (breakawayAttack) {
            long remainingBreakaway = Math.max(0L, BREAKAWAY_ATTACK_MILLIS - state.elapsedMillis());
            breakawayBar.setName(Component.literal("Breakaway Attack: " + formatDuration(remainingBreakaway)));
            breakawayBar.setProgress(progressFor(remainingBreakaway, BREAKAWAY_ATTACK_MILLIS));
            breakawayBar.setVisible(true);
            syncBossBarPlayers(server, data, state, breakawayBar);
        } else {
            breakawayBar.setVisible(false);
        }
    }

    private static void syncBossBarPlayers(MinecraftServer server, FactionData data, SiegeState state, ServerBossEvent bar) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<UUID> factionId = data.getFactionIdByPlayer(player.getUUID());
            if (factionId.isPresent()
                && (factionId.get().equals(state.attackerFactionId()) || factionId.get().equals(state.defenderFactionId()))) {
                if (!bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                }
            } else if (bar.getPlayers().contains(player)) {
                bar.removePlayer(player);
            }
        }
    }

    private static void clearBossBars(SiegeState state) {
        state.clearBossBars();
    }

    private static float progressFor(long remaining, long total) {
        if (total <= 0L) {
            return 0.0f;
        }
        float value = (float) remaining / (float) total;
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    private static void maybeSendStatus(MinecraftServer server, FactionData data, SiegeState state, long now,
                                        boolean attackerInChunk, boolean breakawayAttack, long requiredMillis) {
        if (now - state.lastStatusBroadcastMillis() < STATUS_BROADCAST_INTERVAL_MILLIS) {
            return;
        }
        long remainingSiege = Math.max(0L, requiredMillis - state.elapsedMillis());
        String siegeTimer = formatDuration(remainingSiege);
        String message = "Siege timer: " + siegeTimer;
        if (breakawayAttack) {
            long remainingDefense = Math.max(0L, BREAKAWAY_ATTACK_MILLIS - state.elapsedMillis());
            String defenseTimer = formatDuration(remainingDefense);
            message = "Breakaway attack: " + defenseTimer
                + (attackerInChunk ? " (attackers in claim)" : " (defenders holding)");
        }
        if (!attackerInChunk) {
            long remainingDefense = Math.max(0L, DEFENSE_BREAK_MILLIS - (now - state.lastAttackerPresenceMillis()));
            String defenseTimer = formatDuration(remainingDefense);
            message = "Siege timer: " + siegeTimer + " | Defense timer: " + defenseTimer
                + " (no attackers)";
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

    private static boolean isBreakawayAttack(FactionData data, UUID attackerFactionId, UUID defenderFactionId,
                                             ChunkPos targetChunk, String targetDimension) {
        Optional<UUID> overlordId = data.getOverlord(defenderFactionId);
        if (overlordId.isEmpty()
            || !overlordId.get().equals(attackerFactionId)
            || !data.hasActiveBreakaway(defenderFactionId)) {
            return false;
        }
        Optional<FactionData.FactionHome> home = data.getFactionHome(defenderFactionId);
        if (home.isEmpty() || !home.get().dimension().equals(targetDimension)) {
            return false;
        }
        return new ChunkPos(home.get().pos()).equals(targetChunk);
    }

    private static void handleBreakawayAttackSuccess(MinecraftServer server, FactionData data, SiegeState state) {
        if (data.hasActiveBreakaway(state.defenderFactionId())) {
            data.cancelVassalBreakaway(state.defenderFactionId(), state.attackerFactionId());
        }
        data.clearRelation(state.attackerFactionId(), state.defenderFactionId());
        notifyFactionMembers(server, data, state.attackerFactionId(),
            "Your faction has won the breakaway war by holding the enemy capital and kept your vassal.");
        notifyFactionMembers(server, data, state.defenderFactionId(),
            "Your breakaway war has failed. The overlord held your capital long enough.");
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
        private ServerBossEvent siegeBossBar;
        private ServerBossEvent defenseBossBar;
        private ServerBossEvent breakawayBossBar;

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

        public ServerBossEvent ensureSiegeBossBar() {
            if (siegeBossBar == null) {
                siegeBossBar = new ServerBossEvent(Component.literal("Siege Timer"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            }
            return siegeBossBar;
        }

        public ServerBossEvent ensureDefenseBossBar() {
            if (defenseBossBar == null) {
                defenseBossBar = new ServerBossEvent(Component.literal("Defense Timer"),
                    BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
            }
            return defenseBossBar;
        }

        public ServerBossEvent ensureBreakawayBossBar() {
            if (breakawayBossBar == null) {
                breakawayBossBar = new ServerBossEvent(Component.literal("Breakaway Defense"),
                    BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
            }
            return breakawayBossBar;
        }

        public void clearBossBars() {
            if (siegeBossBar != null) {
                siegeBossBar.removeAllPlayers();
            }
            if (defenseBossBar != null) {
                defenseBossBar.removeAllPlayers();
            }
            if (breakawayBossBar != null) {
                breakawayBossBar.removeAllPlayers();
            }
        }
    }
}
