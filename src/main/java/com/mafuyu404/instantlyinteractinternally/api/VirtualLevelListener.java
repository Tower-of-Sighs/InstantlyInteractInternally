package com.mafuyu404.instantlyinteractinternally.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * FakeLevel 方块与方块实体生命周期监听。
 * <p>用于外部模组在虚拟层内订阅放置、移除、移动、BE 加载、从真实世界复制、回写真实世界等事件。
 * <p>事件回调在服务器主线程中同步触发，请避免阻塞或长耗时操作。
 */
public interface VirtualLevelListener {

    /**
     * 虚拟层在 pos 放置了新方块。oldState 可能为 null。
     */
    default void onPutBlock(Level level, BlockPos pos, BlockState newState, @Nullable BlockState oldState) {
    }

    /**
     * 虚拟层在 pos 移除了方块。
     */
    default void onRemoveBlock(Level level, BlockPos pos, BlockState oldState) {
    }

    /**
     * 虚拟层将 from 位置的方块整体移动到 to 位置（含 BE 迁移）。
     */
    default void onMoveBlock(Level level, BlockPos from, BlockPos to, BlockState state) {
    }

    /**
     * 虚拟层在 pos 加载或创建了方块实体。
     */
    default void onBlockEntityLoaded(Level level, BlockPos pos, BlockEntity be) {
    }

    /**
     * 从真实世界 real 的 fromReal 复制一份方块/BE 到虚拟层 virtual 的 toVirtual。
     */
    default void onCopyFromReal(ServerLevel real, BlockPos fromReal, Level virtual, BlockPos toVirtual) {
    }

    /**
     * 将虚拟层 virtual 的 fromVirtual 回写到真实世界 real 的 toReal。
     */
    default void onFlushToReal(Level virtual, BlockPos fromVirtual, ServerLevel real, BlockPos toReal) {
    }
}