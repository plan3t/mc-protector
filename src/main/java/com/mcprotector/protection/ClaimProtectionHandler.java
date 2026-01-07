package com.mcprotector.protection;

import com.mcprotector.data.FactionData;
import com.mcprotector.data.FactionPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClaimProtectionHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (!isAllowed(player, pos, FactionPermission.BLOCK_BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
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
    public void onFireSpread(BlockEvent.FireSpreadEvent event) {
        BlockPos pos = event.getPos();
        if (isClaimed(event.getLevel(), pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        FactionPermission permission = permissionForBlockUse(event.getLevel().getBlockState(pos), event.getLevel(), pos);
        if (!isAllowed(player, pos, permission)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        if (!isAllowed(player, pos, FactionPermission.ENTITY_INTERACT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        if (!isAllowed(player, pos, FactionPermission.ENTITY_INTERACT)) {
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

    private boolean isAllowed(Player player, BlockPos pos, FactionPermission permission) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return !isClaimed(player.level(), pos);
        }
        return FactionData.get(serverPlayer.serverLevel()).hasPermission(serverPlayer, pos, permission);
    }

    private boolean isClaimed(net.minecraft.world.level.Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        return FactionData.get(serverLevel).isClaimed(pos);
    }

    private FactionPermission permissionForBlockUse(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        Block block = state.getBlock();
        if (block == Blocks.LEVER || state.is(BlockTags.BUTTONS)) {
            return FactionPermission.REDSTONE_TOGGLE;
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (blockEntity instanceof MenuProvider || block instanceof MenuProvider) {
            return FactionPermission.CONTAINER_OPEN;
        }
        return FactionPermission.BLOCK_USE;
    }
}
