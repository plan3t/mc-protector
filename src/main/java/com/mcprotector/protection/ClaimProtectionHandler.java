package com.mcprotector.protection;

import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.Faction;
import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

public class ClaimProtectionHandler {
    private static final String CREATE_MOD_ID = "create";

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (!isAllowed(player, pos, FactionPermission.BLOCK_BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockDrops(BlockDropsEvent event) {
        Entity breaker = event.getBreaker();
        if (breaker instanceof Player player) {
            if (isAllowed(player, event.getPos(), FactionPermission.BLOCK_BREAK)) {
                return;
            }
        } else if (!isClaimed(event.getLevel(), event.getPos())) {
            return;
        }
        event.setCanceled(true);
        restoreBlock(event.getLevel(), event.getPos(), event.getState(), event.getBlockEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        BlockPos pos = event.getPos();
        if (!(entity instanceof Player player)) {
            if (isClaimed(event.getLevel(), pos)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!isAllowed(player, pos, FactionPermission.BLOCK_PLACE)) {
            event.setCanceled(true);
            return;
        }
        if (isCreateBlock(event.getPlacedBlock().getBlock())
            && !isAllowed(player, pos, FactionPermission.CREATE_MACHINE_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        Entity entity = event.getEntity();
        BlockPos pos = event.getPos();
        if (!(entity instanceof Player player)) {
            if (isClaimed(event.getLevel(), pos)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!isAllowed(player, pos, FactionPermission.BLOCK_PLACE)) {
            event.setCanceled(true);
            return;
        }
        if (isCreateBlock(event.getPlacedBlock().getBlock())
            && !isAllowed(player, pos, FactionPermission.CREATE_MACHINE_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        BlockPos pos = event.getPos();
        if (isClaimed(event.getLevel(), pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onFireSpread(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getPlacedBlock().getBlock() instanceof BaseFireBlock)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (isClaimed(event.getLevel(), pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (isDoorLike(state) && !FactionConfig.SERVER.allowDoorUseInClaims.get()) {
            boolean allowedDoor = isMemberOrTrusted(player, pos) || isAllowed(player, pos, FactionPermission.BLOCK_USE);
            logAccess(player, pos, FactionPermission.BLOCK_USE, allowedDoor, state.getBlock().getDescriptionId());
            if (!allowedDoor) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            }
            return;
        }
        FactionPermission permission = permissionForBlockUse(state, event.getLevel(), pos);
        boolean allowed = isAllowed(player, pos, permission);
        logAccess(player, pos, permission, allowed, event.getLevel().getBlockState(pos).getBlock().getDescriptionId());
        if (!allowed) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        boolean allowed = isAllowed(player, pos, FactionPermission.ENTITY_INTERACT);
        logAccess(player, pos, FactionPermission.ENTITY_INTERACT, allowed, event.getTarget().getType().toString());
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        boolean allowed = isAllowed(player, pos, FactionPermission.ENTITY_INTERACT);
        logAccess(player, pos, FactionPermission.ENTITY_INTERACT, allowed, event.getTarget().getType().toString());
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        BlockPos pos = target.blockPosition();
        boolean allowed = isAllowed(player, pos, FactionPermission.ENTITY_INTERACT);
        logAccess(player, pos, FactionPermission.ENTITY_INTERACT, allowed, target.getType().toString());
        if (!allowed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> FactionData.get(serverLevel).isClaimed(pos));
    }

    @SubscribeEvent
    public void onPlayerAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isWarZone(serverLevel)) {
            return;
        }
        if (isSafeZone(serverLevel)) {
            event.setCanceled(true);
            return;
        }
        if (FactionConfig.SERVER.allowPvpInClaims.get()) {
            return;
        }
        if (!FactionData.get(serverLevel).isClaimed(victim.blockPosition())) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onMobSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isSafeZone(serverLevel) || FactionData.get(serverLevel).isSafeZoneClaimed(event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    private boolean isAllowed(Player player, BlockPos pos, FactionPermission permission) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return !isClaimed(player.level(), pos);
        }
        boolean isFakePlayer = serverPlayer instanceof FakePlayer;
        if (isWarZone(serverPlayer.serverLevel())) {
            return true;
        }
        if (isFakePlayer && isClaimed(serverPlayer.serverLevel(), pos)
            && !FactionConfig.SERVER.allowFakePlayerActionsInClaims.get()) {
            return false;
        }
        boolean hasBypassPermission = !isFakePlayer && serverPlayer.hasPermissions(FactionConfig.SERVER.adminBypassPermissionLevel.get());
        if (isSafeZone(serverPlayer.serverLevel())) {
            return hasBypassPermission && FactionBypassManager.isBypassEnabled(serverPlayer);
        }
        if (hasBypassPermission && FactionBypassManager.isBypassEnabled(serverPlayer)) {
            logAccess(serverPlayer, pos, permission, true, "ADMIN_BYPASS");
            return true;
        }
        return FactionData.get(serverPlayer.serverLevel()).hasPermission(serverPlayer, pos, permission);
    }

    private boolean isClaimed(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return FactionData.get(serverLevel).isClaimed(pos);
    }

    private void restoreBlock(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (blockEntity == null) {
            return;
        }
        blockEntity.clearRemoved();
        blockEntity.setLevel(level);
        level.setBlockEntity(blockEntity);
        blockEntity.setChanged();
    }

    private FactionPermission permissionForBlockUse(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        Block block = state.getBlock();
        if (block == Blocks.LEVER || state.is(BlockTags.BUTTONS)) {
            if (!FactionConfig.SERVER.allowRedstoneInClaims.get()) {
                return FactionPermission.BLOCK_USE;
            }
            return FactionPermission.REDSTONE_TOGGLE;
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (isCreateMachine(block, blockEntity)) {
            return FactionPermission.CREATE_MACHINE_INTERACT;
        }
        if (blockEntity instanceof MenuProvider || block instanceof MenuProvider) {
            return FactionPermission.CONTAINER_OPEN;
        }
        return FactionPermission.BLOCK_USE;
    }

    private void logAccess(Player player, BlockPos pos, FactionPermission permission, boolean allowed, String targetName) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!FactionData.get(serverLevel).isClaimed(pos)) {
            return;
        }
        FactionData.get(serverLevel).logAccess(
            pos,
            serverPlayer.getUUID(),
            serverPlayer.getName().getString(),
            permission.name(),
            allowed,
            targetName
        );
    }

    private boolean isMemberOrTrusted(Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ServerLevel level = serverPlayer.serverLevel();
        FactionData data = FactionData.get(level);
        Optional<UUID> ownerId = data.getClaimOwner(pos);
        if (ownerId.isEmpty()) {
            return true;
        }
        if (ownerId.get().equals(data.getFactionIdByPlayer(serverPlayer.getUUID()).orElse(null))) {
            return true;
        }
        Optional<Faction> ownerFaction = data.getFaction(ownerId.get());
        return ownerFaction.isPresent() && ownerFaction.get().isTrusted(serverPlayer.getUUID());
    }

    private boolean isDoorLike(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock;
    }

    private boolean isSafeZone(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return FactionConfig.SERVER.safeZoneDimensions.get().contains(dimension);
    }

    private boolean isWarZone(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return FactionConfig.SERVER.warZoneDimensions.get().contains(dimension);
    }

    private boolean isCreateMachine(Block block, BlockEntity blockEntity) {
        return isCreateBlock(block) || isCreateBlockEntity(blockEntity);
    }

    private boolean isCreateBlock(Block block) {
        return CREATE_MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace());
    }

    private boolean isCreateBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        return CREATE_MOD_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).getNamespace());
    }
}
