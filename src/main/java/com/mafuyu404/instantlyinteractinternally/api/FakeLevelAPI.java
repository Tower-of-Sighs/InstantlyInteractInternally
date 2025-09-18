package com.mafuyu404.instantlyinteractinternally.api;

import com.mafuyu404.instantlyinteractinternally.utils.FakeLevel;
import com.mafuyu404.instantlyinteractinternally.utils.service.WorldContextRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class FakeLevelAPI {
    private FakeLevelAPI() {
    }

    // 方向侧向物品访问接口：其它模组可实现自己的桥接逻辑
    public interface ISidedItemAccess {
        int getSlots(Direction side);

        ItemStack getStackInSlot(Direction side, int slot);

        void setStackInSlot(Direction side, int slot, ItemStack stack);
    }

    /**
     * 注册“按方向物品访问器”。
     * <p>同一位置重复注册将覆盖旧的访问器。
     *
     * @param player 所属玩家
     * @param pos    虚拟层位置
     * @param access 访问器实例（非空）
     */
    public static void registerSidedItemAccess(ServerPlayer player, BlockPos pos, ISidedItemAccess access) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null) {
            ctx.level.registerSidedAccess(pos, access);
        }
    }

    /**
     * 注销某位置的方向物品访问器。
     */
    public static void unregisterSidedItemAccess(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null) {
            ctx.level.unregisterSidedAccess(pos);
        }
    }

    /**
     * 读取已注册的方向物品访问器。
     *
     * @return 访问器实例或 null
     */
    public static ISidedItemAccess getSidedItemAccess(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        return (ctx != null) ? ctx.level.getSidedAccess(pos) : null;
    }

    /**
     * 简易构造器：把某个 Container 的槽位数组按方向映射出去
     * <p>
     * 例如：
     * <p>
     * - UP    -> 上行输入区槽位数组
     * <p>
     * - DOWN  -> 下行输出区槽位数组
     * <p>
     * - NORTH/EAST/SOUTH/WEST -> 自定义“左/右”或其它侧面逻辑
     */
    public static ISidedItemAccess ofContainerMapping(
            Supplier<Container> containerSupplier,
            Map<Direction, int[]> sideToSlots
    ) {
        Objects.requireNonNull(containerSupplier, "containerSupplier");
        Map<Direction, int[]> map = new EnumMap<>(Direction.class);
        map.putAll(sideToSlots);

        return new ISidedItemAccess() {
            @Override
            public int getSlots(Direction side) {
                int[] idx = map.get(side);
                return idx == null ? 0 : idx.length;
            }

            @Override
            public ItemStack getStackInSlot(Direction side, int slot) {
                int[] idx = map.get(side);
                if (idx == null || slot < 0 || slot >= idx.length) return ItemStack.EMPTY;
                Container c = containerSupplier.get();
                return (c == null) ? ItemStack.EMPTY : c.getItem(idx[slot]);
            }

            @Override
            public void setStackInSlot(Direction side, int slot, ItemStack stack) {
                int[] idx = map.get(side);
                if (idx == null || slot < 0 || slot >= idx.length) return;
                Container c = containerSupplier.get();
                if (c == null) return;
                c.setItem(idx[slot], stack);
            }
        };
    }

    // 事件监听：可由其它模组订阅虚拟层中的方块/BE生命周期
    public static void addVirtualLevelListener(ServerPlayer player, VirtualLevelListener listener) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && listener != null) {
            ctx.level.addListener(listener);
        }
    }

    public static void removeVirtualLevelListener(ServerPlayer player, VirtualLevelListener listener) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && listener != null) {
            ctx.level.removeListener(listener);
        }
    }

    // 事务：快照与回滚，便于批量操作失败恢复
    public static VirtualTransaction beginTransaction(ServerPlayer player) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null) return VirtualTransaction.NOOP;
        return ctx.level.beginTransaction();
    }

    /**
     * 向玩家的虚拟层提交一个任务。
     * 注意：任务将在 TickService 驱动时统一执行（同线程），请避免阻塞或长耗时操作。
     */
    public static void scheduleTask(ServerPlayer player, Runnable task) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && task != null) {
            ctx.level.schedule(task);
        }
    }

    /**
     * 扩展任务调度：支持分组/延迟/节流/合并。
     * <ul>
     *     <li>group：同组任务每 tick 按 throttlePerTick 限流；coalesce=true 时仅保留一个未执行实例</li>
     *     <li>delayTicks：延迟执行的 tick 数（可为 0）</li>
     *     <li>throttlePerTick：每 tick 同组任务执行上限（0 表示不限）</li>
     *     <li>coalesce：是否合并同组重复任务</li>
     * </ul>
     */
    public static void scheduleTaskEx(ServerPlayer player, Runnable task,
                                      ResourceLocation group, int delayTicks,
                                      int throttlePerTick, boolean coalesce) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && task != null) {
            ctx.level.schedule(task, group, delayTicks, throttlePerTick, coalesce);
        }
    }

    public static int drainTasks(ServerPlayer player, int maxCount) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null) return 0;
        return ctx.level.drainTasks(maxCount);
    }

    // 附件：为某个虚拟坐标挂任意对象，便于跨模组桥接（如能力句柄、路由器、缓存等）
    public static <T> void putAttachment(ServerPlayer player, BlockPos pos, ResourceLocation key, T value) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && key != null) {
            ctx.level.putAttachment(pos, key, value);
        }
    }

    public static <T> T getAttachment(ServerPlayer player, BlockPos pos, ResourceLocation key) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null || key == null) return null;
        return ctx.level.getAttachment(pos, key);
    }

    public static void removeAttachment(ServerPlayer player, BlockPos pos, ResourceLocation key) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && key != null) {
            ctx.level.removeAttachment(pos, key);
        }
    }

    public static void clearAttachments(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null) {
            ctx.level.clearAttachments(pos);
        }
    }

    // 虚拟能力桥接（类型安全）
    public static <T> void putCapability(ServerPlayer player, BlockPos pos, Capability<T> cap, Direction side, LazyOptional<T> value) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && cap != null) {
            ctx.level.putCapability(pos, cap, side, value);
        }
    }

    public static <T> LazyOptional<T> getCapability(ServerPlayer player, BlockPos pos, Capability<T> cap, Direction side) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null || cap == null) return LazyOptional.empty();
        return ctx.level.getCapability(pos, cap, side);
    }

    public static void removeCapability(ServerPlayer player, BlockPos pos, Capability<?> cap, Direction side) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && cap != null) {
            ctx.level.removeCapability(pos, cap, side);
        }
    }

    public static void clearCapabilities(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null) {
            ctx.level.clearCapabilities(pos);
        }
    }

    /**
     * 将虚拟位置 pos 的 from 方向物品搬运到 to 方向。
     *
     * @see FakeLevel#transferItems
     */
    public static int transfer(ServerPlayer player, BlockPos pos, Direction from, Direction to,
                               Predicate<ItemStack> filter, int maxMove) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null) return 0;
        return ctx.level.transferItems(pos, from, to, filter, maxMove);
    }

    /**
     * 将 from 方向所有可合并/迁移的物品合并到 to 方向。
     *
     * @see FakeLevel#mergeAllItems。
     */
    public static int mergeAll(ServerPlayer player, BlockPos pos, Direction from, Direction to) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null) return 0;
        return ctx.level.mergeAllItems(pos, from, to);
    }

    /**
     * 压缩某方向上的物品栈（尽量合并成满栈）。
     *
     * @see FakeLevel#compressItems。
     */
    public static int compress(ServerPlayer player, BlockPos pos, Direction side) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null) return 0;
        return ctx.level.compressItems(pos, side);
    }

    // 方块/BE 基础操作（虚拟层）
    public static void putBlock(ServerPlayer player, BlockPos pos, BlockState state) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && state != null) {
            ctx.level.putBlock(pos, state);
        }
    }

    public static void removeBlock(ServerPlayer player, BlockPos pos, boolean isMoving) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null) {
            ctx.level.removeBlock(pos, isMoving);
        }
    }

    public static BlockEntity getOrCreateBlockEntity(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null) return null;
        return ctx.level.getOrCreateBlockEntity(pos);
    }

    public static CompoundTag saveBlockEntityTag(ServerPlayer player, BlockPos pos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null || pos == null) return null;
        return ctx.level.saveBlockEntityTag(pos);
    }

    public static void loadBlockEntityTag(ServerPlayer player, BlockPos pos, CompoundTag tag) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && pos != null && tag != null) {
            ctx.level.loadBlockEntityTag(pos, tag);
        }
    }

    // 与真实世界桥接：从真实世界复制到虚拟世界 / 从虚拟世界回写到真实世界
    public static void copyFromReal(ServerPlayer player, ServerLevel real, BlockPos realPos, BlockPos virtualPos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && real != null && realPos != null && virtualPos != null) {
            ctx.level.copyFromReal(real, realPos, virtualPos);
        }
    }

    public static void flushToReal(ServerPlayer player, ServerLevel real, BlockPos virtualPos, BlockPos realPos) {
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx != null && real != null && virtualPos != null && realPos != null) {
            ctx.level.flushToReal(real, virtualPos, realPos);
        }
    }
}