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
    private static final long REQUIRED_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final Map<UUID, SiegeState> ACTIVE_SIEGES = new ConcurrentHashMap<>();

    private SiegeManager() {
    }

    public static boolean startSiege(ServerPlayer player, UUID attackerFactionId, UUID defenderFactionId, ChunkPos chunk) {
        if (ACTIVE_SIEGES.containsKey(attackerFactionId)) {
            return false;
        }
        long now = System.currentTimeMillis();
        SiegeState state = new SiegeState(attackerFactionId, defenderFactionId, player.getUUID(), chunk,
            player.level().dimension().location().toString(), now, now, 0L);
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
            if (attacker == null) {
                state.updateLastTick(now);
                continue;
            }
            if (!attacker.level().dimension().location().toString().equals(state.dimension())) {
                state.updateLastTick(now);
                continue;
            }
            long elapsed = now - state.lastTickMillis();
            if (elapsed <= 0) {
                state.updateLastTick(now);
                continue;
            }
            if (new ChunkPos(attacker.blockPosition()).equals(state.chunk())) {
                state.addElapsed(elapsed);
            }
            state.updateLastTick(now);
            if (state.elapsedMillis() < REQUIRED_MILLIS) {
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
        ACTIVE_SIEGES.remove(attackerFactionId.get());
        MinecraftServer server = attacker.server;
        notifyFactionMembers(server, FactionData.get(attacker.serverLevel()), state.attackerFactionId(),
            "Siege failed. Your siege leader was killed by " + killer.getName().getString() + ".");
        notifyFactionMembers(server, FactionData.get(attacker.serverLevel()), state.defenderFactionId(),
            "Siege ended. Your faction defended the claim.");
    }

    private static void notifyFactionMembers(MinecraftServer server, FactionData data, UUID factionId, String message) {
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            Optional<UUID> recipientFactionId = data.getFactionIdByPlayer(recipient.getUUID());
            if (recipientFactionId.isPresent() && recipientFactionId.get().equals(factionId)) {
                recipient.sendSystemMessage(Component.literal(message));
            }
        }
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

        private SiegeState(UUID attackerFactionId, UUID defenderFactionId, UUID attackerPlayerId, ChunkPos chunk,
                           String dimension, long startedAtMillis, long lastTickMillis, long elapsedMillis) {
            this.attackerFactionId = attackerFactionId;
            this.defenderFactionId = defenderFactionId;
            this.attackerPlayerId = attackerPlayerId;
            this.chunk = chunk;
            this.dimension = dimension;
            this.startedAtMillis = startedAtMillis;
            this.lastTickMillis = lastTickMillis;
            this.elapsedMillis = elapsedMillis;
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

        public void updateLastTick(long now) {
            lastTickMillis = now;
        }

        public void addElapsed(long delta) {
            elapsedMillis += delta;
        }
    }
}
