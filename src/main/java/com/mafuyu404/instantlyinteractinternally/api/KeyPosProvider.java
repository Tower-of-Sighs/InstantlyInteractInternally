package com.mafuyu404.instantlyinteractinternally.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public interface KeyPosProvider {

    /**
     * 根据 key 解析虚拟层中的位置，返回 null 表示当前玩家不存在该键对应的位置。
     * 实现方可自行持久化/恢复映射（核心工程不再负责 key→pos 持久化）。
     *
     * @see FakeLevelAPI#resolveKeyPos
     */
    BlockPos resolve(ServerPlayer player, String key);

    /**
     * 枚举该玩家当前“应被 tick”的所有虚拟位置：
     * - 可用于驱动虚拟层逻辑（如定时任务、缓存刷新等）；
     * - 返回的集合应尽量稳定且规模可控，避免产生过多无效位置。
     *
     * @see com.mafuyu404.instantlyinteractinternally.utils.service.TickService#onServerTick
     */
    Iterable<BlockPos> allPositions(ServerPlayer player);
}
