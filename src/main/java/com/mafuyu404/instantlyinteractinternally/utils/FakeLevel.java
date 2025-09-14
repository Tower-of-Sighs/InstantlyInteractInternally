package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.api.VirtualLevelListener;
import com.mafuyu404.instantlyinteractinternally.api.VirtualTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class FakeLevel extends Level {

    private final ServerLevel delegate;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final Map<BlockPos, FakeLevelAPI.ISidedItemAccess> sidedAccess = new HashMap<>();
    private final List<VirtualLevelListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<BlockPos, Map<ResourceLocation, Object>> attachments = new HashMap<>();
    private final ArrayDeque<Runnable> taskQueue = new ArrayDeque<>();
    private final Map<BlockPos, Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>>> capabilities = new HashMap<>();
    private final List<ScheduledTask> scheduledTasks = new ArrayList<>();
    private final Map<ResourceLocation, Integer> groupTickCount = new HashMap<>();
    private long lastDrainTick = Long.MIN_VALUE;

    public FakeLevel(ServerLevel delegate) {
        super(
                (WritableLevelData) delegate.getLevelData(),
                delegate.dimension(),
                delegate.registryAccess(),
                delegate.dimensionTypeRegistration(),
                delegate.getProfilerSupplier(),
                false, // isClientSide
                delegate.isDebug(),
                delegate.getSeed(),
                delegate.getServer().getMaxChainedNeighborUpdates()
        );
        this.delegate = delegate;
    }

    // 监听器注册
    public void addListener(VirtualLevelListener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(VirtualLevelListener l) {
        if (l != null) listeners.remove(l);
    }

    // 任务调度
    public void schedule(Runnable r) {
        if (r != null) taskQueue.addLast(r);
    }

    // 带分组/延迟/节流/合并的调度
    public void schedule(Runnable r, @Nullable ResourceLocation group, int delayTicks, int throttlePerTick, boolean coalesce) {
        if (r == null) return;
        long runAt = delegate.getGameTime() + Math.max(0, delayTicks);
        if (coalesce && group != null) {
            for (ScheduledTask t : scheduledTasks) {
                if (group.equals(t.group) && !t.executed) {
                    // 已有同组任务，跳过新增以达到“合并”效果
                    return;
                }
            }
        }
        scheduledTasks.add(new ScheduledTask(r, group, runAt, throttlePerTick, coalesce));
    }

    public int drainTasks(int maxCount) {
        int n = 0;
        // 先 drain 定时/分组任务（按节流/时间）
        long now = delegate.getGameTime();
        if (lastDrainTick != now) {
            groupTickCount.clear();
            lastDrainTick = now;
        }
        Iterator<ScheduledTask> it = scheduledTasks.iterator();
        while (it.hasNext() && (maxCount <= 0 || n < maxCount)) {
            ScheduledTask t = it.next();
            if (t.executed || t.runAt > now) continue;
            if (t.group != null && t.throttlePerTick > 0) {
                int used = groupTickCount.getOrDefault(t.group, 0);
                if (used >= t.throttlePerTick) {
                    // 本 tick 达到节流，留到下个 tick
                    continue;
                }
                groupTickCount.put(t.group, used + 1);
            }
            try {
                t.r.run();
            } catch (Throwable ignore) {
            }
            t.executed = true;
            it.remove();
            n++;
        }
        // 再 drain 立即任务
        while (!taskQueue.isEmpty() && (maxCount <= 0 || n < maxCount)) {
            Runnable r = taskQueue.pollFirst();
            if (r != null) {
                try {
                    r.run();
                } catch (Throwable t) { /* 避免影响宿主 */ }
                n++;
            }
        }
        return n;
    }

    // 附件操作（任意对象）
    public <T> void putAttachment(BlockPos pos, ResourceLocation key, T value) {
        Map<ResourceLocation, Object> map = attachments.computeIfAbsent(pos.immutable(), p -> new HashMap<>());
        if (value == null) map.remove(key);
        else map.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttachment(BlockPos pos, ResourceLocation key) {
        Map<ResourceLocation, Object> map = attachments.get(pos);
        return map == null ? null : (T) map.get(key);
    }

    public void removeAttachment(BlockPos pos, ResourceLocation key) {
        Map<ResourceLocation, Object> map = attachments.get(pos);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) attachments.remove(pos);
        }
    }

    public void clearAttachments(BlockPos pos) {
        attachments.remove(pos);
    }

    // 能力桥接（类型安全）
    public <T> void putCapability(BlockPos pos, Capability<T> cap, @Nullable Direction side, LazyOptional<T> value) {
        if (pos == null || cap == null) return;
        EnumMap<Direction, LazyOptional<?>> bySide = capabilities
                .computeIfAbsent(pos.immutable(), p -> new HashMap<>())
                .computeIfAbsent(cap, c -> new EnumMap<>(Direction.class));
        bySide.put(side == null ? Direction.UP : side, value == null ? LazyOptional.empty() : value);
    }

    @SuppressWarnings("unchecked")
    public <T> LazyOptional<T> getCapability(BlockPos pos, Capability<T> cap, @Nullable Direction side) {
        if (pos == null || cap == null) return LazyOptional.empty();
        Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>> byCap = capabilities.get(pos);
        if (byCap == null) return LazyOptional.empty();
        EnumMap<Direction, LazyOptional<?>> bySide = byCap.get(cap);
        if (bySide == null) return LazyOptional.empty();
        LazyOptional<?> lo = bySide.get(side == null ? Direction.UP : side);
        return (LazyOptional<T>) (lo == null ? LazyOptional.empty() : lo);
    }

    public void removeCapability(BlockPos pos, Capability<?> cap, @Nullable Direction side) {
        Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>> byCap = capabilities.get(pos);
        if (byCap == null) return;
        EnumMap<Direction, LazyOptional<?>> bySide = byCap.get(cap);
        if (bySide == null) return;
        if (side == null) bySide.clear();
        else bySide.remove(side);
        if (bySide.isEmpty()) byCap.remove(cap);
        if (byCap.isEmpty()) capabilities.remove(pos);
    }

    public void clearCapabilities(BlockPos pos) {
        capabilities.remove(pos);
    }

    // 在指定位置放置（仅内存）
    public void putBlock(BlockPos pos, BlockState newState) {
        BlockState oldState = blocks.get(pos);
        if (oldState != null && oldState != newState) {
            oldState.onRemove(this, pos, newState, false);
        }
        blocks.put(pos, newState);

        if (newState.getBlock() instanceof BaseEntityBlock beb) {
            BlockEntity be = blockEntities.get(pos);
            if (be == null || be.getType() == null || !be.getType().isValid(newState)) {
                be = beb.newBlockEntity(pos, newState);
            }
            if (be != null) {
                be.setLevel(this);
                blockEntities.put(pos, be);
                // 触发 BE 加载监听
                for (var l : listeners) {
                    try {
                        l.onBlockEntityLoaded(this, pos, be);
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                blockEntities.remove(pos);
            }
        } else {
            blockEntities.remove(pos);
        }

        if (oldState == null || oldState.getBlock() != newState.getBlock()) {
            newState.onPlace(this, pos, oldState == null ? Blocks.AIR.defaultBlockState() : oldState, false);
        }

        // 触发放置监听
        for (var l : listeners) {
            try {
                l.onPutBlock(this, pos, newState, oldState);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        BlockState oldState = blocks.remove(pos);
        BlockState newState = Blocks.AIR.defaultBlockState();
        blockEntities.remove(pos);
        // 清理附件与能力
        attachments.remove(pos);
        capabilities.remove(pos);
        if (oldState != null) {
            oldState.onRemove(this, pos, newState, isMoving);
            // 触发移除监听
            for (var l : listeners) {
                try {
                    l.onRemoveBlock(this, pos, oldState);
                } catch (Throwable ignored) {
                }
            }
        }
        return true;
    }

    public void moveBlockWithBE(BlockPos from, BlockPos to) {
        if (from.equals(to)) return;

        BlockState state = blocks.get(from);
        BlockEntity oldBe = blockEntities.get(from);

        BlockState toOld = blocks.get(to);
        if (toOld != null) {
            removeBlock(to, false);
        }

        if (state == null) {
            removeBlock(from, false);
            return;
        }

        blocks.put(to, state);
        BlockEntity newBe = null;
        if (oldBe != null) {
            CompoundTag tag = oldBe.saveWithFullMetadata();
            newBe = BlockEntity.loadStatic(to, state, tag);
            if (newBe == null && state.getBlock() instanceof BaseEntityBlock beb) {
                newBe = beb.newBlockEntity(to, state);
            }
        } else if (state.getBlock() instanceof BaseEntityBlock beb) {
            newBe = beb.newBlockEntity(to, state);
        }
        if (newBe != null) {
            newBe.setLevel(this);
            blockEntities.put(to, newBe);
        } else {
            blockEntities.remove(to);
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        state.onPlace(this, to, air, false);
        BlockState fromOld = blocks.remove(from);
        blockEntities.remove(from);
        if (fromOld != null) {
            fromOld.onRemove(this, from, air, false);
        }

        // 触发移动监听
        for (var l : listeners) {
            try {
                l.onMoveBlock(this, from, to, state);
            } catch (Throwable ignored) {
            }
        }
    }

    // BE 便捷访问
    @Nullable
    public BlockEntity getOrCreateBlockEntity(BlockPos pos) {
        BlockState state = blocks.get(pos);
        if (state == null) return null;
        BlockEntity be = blockEntities.get(pos);
        if (be == null && state.getBlock() instanceof BaseEntityBlock beb) {
            be = beb.newBlockEntity(pos, state);
            if (be != null) {
                be.setLevel(this);
                blockEntities.put(pos, be);
                for (var l : listeners) {
                    try {
                        l.onBlockEntityLoaded(this, pos, be);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return be;
    }

    public void loadBlockEntityTag(BlockPos pos, CompoundTag tag) {
        BlockState state = blocks.get(pos);
        if (state == null) return;
        BlockEntity be = BlockEntity.loadStatic(pos, state, tag);
        if (be != null) {
            be.setLevel(this);
            blockEntities.put(pos, be);
            for (var l : listeners) {
                try {
                    l.onBlockEntityLoaded(this, pos, be);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public CompoundTag saveBlockEntityTag(BlockPos pos) {
        BlockEntity be = blockEntities.get(pos);
        return (be == null) ? null : be.saveWithFullMetadata();
    }

    // 与真实世界桥接
    public void copyFromReal(ServerLevel real, BlockPos realPos, BlockPos virtualPos) {
        BlockState rs = real.getBlockState(realPos);
        putBlock(virtualPos, rs);
        BlockEntity rbe = real.getBlockEntity(realPos);
        if (rbe != null) {
            CompoundTag tag = rbe.saveWithFullMetadata();
            loadBlockEntityTag(virtualPos, tag);
        }
        for (var l : listeners) {
            try {
                l.onCopyFromReal(real, realPos, this, virtualPos);
            } catch (Throwable ignored) {
            }
        }
    }

    public void flushToReal(ServerLevel real, BlockPos virtualPos, BlockPos realPos) {
        BlockState vs = blocks.get(virtualPos);
        if (vs == null) {
            // 空则清空真实世界方块
            real.setBlock(realPos, Blocks.AIR.defaultBlockState(), 3);
            real.removeBlockEntity(realPos);
        } else {
            real.setBlock(realPos, vs, 3);
            BlockEntity vbe = blockEntities.get(virtualPos);
            if (vbe != null) {
                CompoundTag tag = vbe.saveWithFullMetadata();
                BlockEntity rbe = real.getBlockEntity(realPos);
                if (rbe == null && vs.getBlock() instanceof BaseEntityBlock beb) {
                    rbe = beb.newBlockEntity(realPos, vs);
                    if (rbe != null) {
                        real.setBlockEntity(rbe);
                    }
                }
                if (rbe != null) {
                    rbe.load(tag);
                    rbe.setChanged();
                }
                real.sendBlockUpdated(realPos, vs, vs, 3);
            }
        }
        for (var l : listeners) {
            try {
                l.onFlushToReal(this, virtualPos, real, realPos);
            } catch (Throwable ignored) {
            }
        }
    }

    // 事务（基于整张虚拟层快照）
    public VirtualTransaction beginTransaction() {
        Snapshot snap = Snapshot.capture(this);
        return new VirtualTransaction() {
            private boolean active = true;

            @Override
            public void commit() {
                active = false;
            }

            @Override
            public void rollback() {
                if (!active) return;
                snap.restore(FakeLevel.this);
                active = false;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public void close() {
                // 未手动 commit 时自动回滚
                if (isActive()) rollback();
            }
        };
    }

    private record Snapshot(Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> bes,
                            Map<BlockPos, Map<ResourceLocation, Object>> attachments,
                            Map<BlockPos, FakeLevelAPI.ISidedItemAccess> sided,
                            Map<BlockPos, Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>>> caps,
                            List<ScheduledTask> scheduledCopy) {

        static Snapshot capture(FakeLevel level) {
            Map<BlockPos, BlockState> blocksCopy = new HashMap<>(level.blocks);
            Map<BlockPos, CompoundTag> beCopy = new HashMap<>();
            for (var e : level.blockEntities.entrySet()) {
                beCopy.put(e.getKey(), e.getValue().saveWithFullMetadata());
            }
            Map<BlockPos, Map<ResourceLocation, Object>> attCopy = new HashMap<>();
            for (var e : level.attachments.entrySet()) {
                attCopy.put(e.getKey(), new HashMap<>(e.getValue()));
            }
            Map<BlockPos, FakeLevelAPI.ISidedItemAccess> sidedCopy = new HashMap<>(level.sidedAccess);
            Map<BlockPos, Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>>> capCopy = new HashMap<>();
            for (var e : level.capabilities.entrySet()) {
                Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>> inner = new HashMap<>();
                for (var c : e.getValue().entrySet()) {
                    inner.put(c.getKey(), new EnumMap<>(c.getValue()));
                }
                capCopy.put(e.getKey(), inner);
            }
            List<ScheduledTask> schedCopy = new ArrayList<>();
            for (var t : level.scheduledTasks) schedCopy.add(t.copy());
            return new Snapshot(blocksCopy, beCopy, attCopy, sidedCopy, capCopy, schedCopy);
        }

        void restore(FakeLevel level) {
            // 恢复 blocks
            level.blocks.clear();
            level.blocks.putAll(this.blocks);

            // 恢复 blockEntities
            level.blockEntities.clear();
            for (var e : this.bes.entrySet()) {
                BlockPos pos = e.getKey();
                BlockState st = level.blocks.get(pos);
                if (st == null) continue;
                BlockEntity be = BlockEntity.loadStatic(pos, st, e.getValue());
                if (be == null && st.getBlock() instanceof BaseEntityBlock beb) {
                    be = beb.newBlockEntity(pos, st);
                }
                if (be != null) {
                    be.setLevel(level);
                    level.blockEntities.put(pos, be);
                }
            }
            // 恢复附件、侧向访问、能力与调度
            level.attachments.clear();
            for (var e : this.attachments.entrySet()) {
                level.attachments.put(e.getKey(), new HashMap<>(e.getValue()));
            }
            level.sidedAccess.clear();
            level.sidedAccess.putAll(this.sided);
            level.capabilities.clear();
            for (var e : this.caps.entrySet()) {
                Map<Capability<?>, EnumMap<Direction, LazyOptional<?>>> inner = new HashMap<>();
                for (var c : e.getValue().entrySet()) {
                    inner.put(c.getKey(), new EnumMap<>(c.getValue()));
                }
                level.capabilities.put(e.getKey(), inner);
            }
            level.scheduledTasks.clear();
            for (var t : this.scheduledCopy) level.scheduledTasks.add(t.copy());
        }
    }

    // 调度任务实体
    private static final class ScheduledTask {
        final Runnable r;
        @Nullable
        final ResourceLocation group;
        final long runAt;
        final int throttlePerTick;
        final boolean coalesce;
        boolean executed;

        ScheduledTask(Runnable r, @Nullable ResourceLocation group, long runAt, int throttlePerTick, boolean coalesce) {
            this.r = r;
            this.group = group;
            this.runAt = runAt;
            this.throttlePerTick = Math.max(0, throttlePerTick);
            this.coalesce = coalesce;
            this.executed = false;
        }

        ScheduledTask copy() {
            ScheduledTask c = new ScheduledTask(this.r, this.group, this.runAt, this.throttlePerTick, this.coalesce);
            c.executed = this.executed;
            return c;
        }
    }

    // 注册/获取/注销 侧向物品访问
    public void registerSidedAccess(BlockPos pos, FakeLevelAPI.ISidedItemAccess access) {
        if (pos == null || access == null) return;
        sidedAccess.put(pos.immutable(), access);
    }

    public void unregisterSidedAccess(BlockPos pos) {
        if (pos == null) return;
        sidedAccess.remove(pos);
    }

    public FakeLevelAPI.ISidedItemAccess getSidedAccess(BlockPos pos) {
        return pos == null ? null : sidedAccess.get(pos);
    }

    /**
     * 在同一虚拟位置的两个方向“侧向物品访问”之间搬运物品，支持过滤与最大搬运数量。
     *
     * @param pos     虚拟坐标
     * @param from    源方向
     * @param to      目标方向
     * @param filter  过滤器（可为 null 表示不过滤）
     * @param maxMove 最大搬运数量；-1 表示不限
     * @return 实际搬运数量
     * @see FakeLevelAPI#transfer
     * <p>
     * 线程：与 Level 同线程；请勿在异步线程调用
     */
    public int transferItems(BlockPos pos,
                             Direction from,
                             Direction to,
                             @Nullable Predicate<ItemStack> filter,
                             int maxMove) {
        FakeLevelAPI.ISidedItemAccess acc = getSidedAccess(pos);
        if (acc == null || maxMove == 0) return 0;

        int moved = 0;
        int srcSlots = acc.getSlots(from);
        int dstSlots = acc.getSlots(to);

        for (int si = 0; si < srcSlots && (maxMove < 0 || moved < maxMove); si++) {
            ItemStack s = acc.getStackInSlot(from, si);
            if (s.isEmpty() || (filter != null && !filter.test(s))) continue;

            // 先尝试合并到相同物品栈
            for (int di = 0; di < dstSlots && (maxMove < 0 || moved < maxMove); di++) {
                ItemStack d = acc.getStackInSlot(to, di);
                if (d.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(s, d) && d.getCount() < d.getMaxStackSize()) {
                    int can = Math.min(s.getCount(), d.getMaxStackSize() - d.getCount());
                    if (maxMove > 0) can = Math.min(can, maxMove - moved);
                    if (can <= 0) continue;

                    s = applyItemTransfer(acc, from, si, s, to, di, d, can);
                    moved += can;
                    if (s.isEmpty()) break;
                }
            }

            // 再尝试放入空位
            if (!s.isEmpty() && (maxMove < 0 || moved < maxMove)) {
                for (int di = 0; di < dstSlots && (maxMove < 0 || moved < maxMove); di++) {
                    ItemStack d = acc.getStackInSlot(to, di);
                    if (!d.isEmpty()) continue;

                    int can = maxMove < 0 ? s.getCount() : Math.min(s.getCount(), maxMove - moved);
                    if (can <= 0) continue;

                    ItemStack part = s.copy();
                    part.setCount(can);
                    s = applyItemTransfer(acc, from, si, s, to, di, ItemStack.EMPTY, can, part);
                    moved += can;
                    if (s.isEmpty()) break;
                }
            }
        }
        return moved;
    }

    /**
     * 将 from 方向所有可搬运物品尽量合并/迁移到 to 方向。
     *
     * @see FakeLevelAPI#mergeAll
     */
    public int mergeAllItems(BlockPos pos, Direction from, Direction to) {
        return transferItems(pos, from, to, stack -> true, -1);
    }

    /**
     * 压缩同一方向上的物品栈（尽可能把相同物品合并为满栈）。
     *
     * @return 合并的总数量（增加到目标栈中的总数）
     * @see FakeLevelAPI#compress
     */
    public int compressItems(BlockPos pos, Direction side) {
        FakeLevelAPI.ISidedItemAccess acc = getSidedAccess(pos);
        if (acc == null) return 0;

        int slots = acc.getSlots(side);
        int compressed = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack a = acc.getStackInSlot(side, i);
            if (a.isEmpty() || a.getCount() >= a.getMaxStackSize()) continue;

            for (int j = i + 1; j < slots; j++) {
                ItemStack b = acc.getStackInSlot(side, j);
                if (b.isEmpty()) continue;
                if (ItemStack.isSameItemSameTags(a, b)) {
                    int can = Math.min(b.getCount(), a.getMaxStackSize() - a.getCount());
                    if (can <= 0) continue;

                    a = applyItemTransfer(acc, side, i, a, side, j, b, can);
                    compressed += can;
                    if (a.getCount() >= a.getMaxStackSize()) break;
                }
            }
        }
        return compressed;
    }

    /**
     * 通用的物品搬运/合并逻辑，复制并更新两个槽位。
     *
     * @param acc      物品访问器
     * @param from     源方向
     * @param fromSlot 源槽位索引
     * @param src      源物品栈
     * @param to       目标方向
     * @param toSlot   目标槽位索引
     * @param dst      目标物品栈（可能为空）
     * @param amount   搬运数量
     * @return 更新后的源物品栈
     */
    private ItemStack applyItemTransfer(FakeLevelAPI.ISidedItemAccess acc,
                                        Direction from, int fromSlot, ItemStack src,
                                        Direction to, int toSlot, ItemStack dst,
                                        int amount) {
        return applyItemTransfer(acc, from, fromSlot, src, to, toSlot, dst, amount, null);
    }

    /**
     * 带自定义插入物品的版本（用于空槽插入）。
     */
    private ItemStack applyItemTransfer(FakeLevelAPI.ISidedItemAccess acc,
                                        Direction from, int fromSlot, ItemStack src,
                                        Direction to, int toSlot, ItemStack dst,
                                        int amount, @Nullable ItemStack inserted) {
        ItemStack newDst = inserted != null ? inserted : dst.copy();
        newDst.grow(amount);
        ItemStack newSrc = src.copy();
        newSrc.shrink(amount);

        acc.setStackInSlot(to, toSlot, newDst);
        acc.setStackInSlot(from, fromSlot, newSrc);
        return newSrc;
    }

    @Override
    public @Nullable MinecraftServer getServer() {
        return delegate.getServer();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int p_204159_, int p_204160_, int p_204161_) {
        return delegate.getUncachedNoiseBiome(p_204159_, p_204160_, p_204161_);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public float getShade(Direction p_45522_, boolean p_45523_) {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public LevelChunk getChunk(int x, int z) {
        return delegate.getChunk(x, z);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return delegate.enabledFeatures();
    }

    @Override
    public LevelChunk getChunkAt(BlockPos pos) {
        return delegate.getChunkAt(pos);
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        // 对于虚拟坐标总是视为已加载
        return true;
    }

    @Override
    public @Nullable Entity getEntity(int p_46492_) {
        return delegate.getEntity(p_46492_);
    }

    @Override
    public @Nullable MapItemSavedData getMapData(String p_46650_) {
        return delegate.getMapData(p_46650_);
    }

    @Override
    public void setMapData(String p_151533_, MapItemSavedData p_151534_) {
        delegate.setMapData(p_151533_, p_151534_);
    }

    @Override
    public int getFreeMapId() {
        return delegate.getFreeMapId();
    }

    @Override
    public void destroyBlockProgress(int p_46506_, BlockPos p_46507_, int p_46508_) {
        delegate.destroyBlockProgress(p_46506_, p_46507_, p_46508_);
    }

    @Override
    public Scoreboard getScoreboard() {
        return delegate.getScoreboard();
    }

    @Override
    public RecipeManager getRecipeManager() {
        return delegate.getRecipeManager();
    }

    public DimensionDataStorage getDataStorage() {
        return delegate.getDataStorage();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        return delegate.getCapability(cap, side);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return delegate.getEntities();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = blocks.get(pos);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public void playSeededSound(@Nullable Player p_262953_, double p_263004_, double p_263398_, double p_263376_, Holder<SoundEvent> p_263359_, SoundSource p_263020_, float p_263055_, float p_262914_, long p_262991_) {
        delegate.playSeededSound(p_262953_, p_263004_, p_263398_, p_263376_, p_263359_, p_263020_, p_263055_, p_262914_, p_262991_);
    }

    @Override
    public void playSeededSound(@Nullable Player p_220372_, Entity p_220373_, Holder<SoundEvent> p_263500_, SoundSource p_220375_, float p_220376_, float p_220377_, long p_220378_) {
        delegate.playSeededSound(p_220372_, p_220373_, p_263500_, p_220375_, p_220376_, p_220377_, p_220378_);
    }

    @Override
    public String gatherChunkSourceStats() {
        return delegate.gatherChunkSourceStats();
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        putBlock(pos, state);
        return true;
    }

    @Override
    public void sendBlockUpdated(BlockPos p_46612_, BlockState p_46613_, BlockState p_46614_, int p_46615_) {
        delegate.sendBlockUpdated(p_46612_, p_46613_, p_46614_, p_46615_);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return delegate.getBlockTicks();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return delegate.getFluidTicks();
    }

    @Override
    public ChunkSource getChunkSource() {
        return delegate.getChunkSource();
    }

    @Override
    public void levelEvent(@Nullable Player p_46771_, int p_46772_, BlockPos p_46773_, int p_46774_) {
        delegate.levelEvent(p_46771_, p_46772_, p_46773_, p_46774_);
    }

    @Override
    public void gameEvent(GameEvent p_220404_, Vec3 p_220405_, GameEvent.Context p_220406_) {
        delegate.gameEvent(p_220404_, p_220405_, p_220406_);
    }

    @Override
    public List<? extends Player> players() {
        return delegate.players();
    }
}