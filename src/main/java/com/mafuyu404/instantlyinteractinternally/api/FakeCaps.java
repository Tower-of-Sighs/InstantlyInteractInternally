package com.mafuyu404.instantlyinteractinternally.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 类型安全的虚拟能力桥接工具。
 * <p>
 * 使用 Key<T> 将 Capability<T> 与可选的侧面绑定，避免泛型擦除引起的误用。
 * 该工具仅封装对 FakeLevelAPI 能力相关方法的类型安全访问。
 *
 * <p><b>线程模型</b>：与服务器主线程一致；不要在异步线程中调用。
 * <p><b>生命周期</b>：能力挂载于玩家虚拟层内的某一虚拟坐标，随会话清理而失效。
 */
public final class FakeCaps {

    private FakeCaps() {
    }

    /**
     * 强类型的 Capability 键，绑定了 Capability<T> 与可选的侧面。
     * 可选的 id 仅用于业务标识（非必要），不会参与 get/put 路径逻辑。
     *
     * @param <T> 能力类型
     */
    public static final class Key<T> {
        private final Capability<T> cap;
        @Nullable
        private final Direction side;
        @Nullable
        private final ResourceLocation id;

        private Key(Capability<T> cap, @Nullable Direction side, @Nullable ResourceLocation id) {
            this.cap = Objects.requireNonNull(cap, "cap");
            this.side = side;
            this.id = id;
        }

        /**
         * 构造仅绑定 Capability 的键（无方向）。
         */
        public static <T> Key<T> of(Capability<T> cap) {
            return new Key<>(cap, null, null);
        }

        /**
         * 构造绑定 Capability 与方向的键。
         */
        public static <T> Key<T> of(Capability<T> cap, @Nullable Direction side) {
            return new Key<>(cap, side, null);
        }

        /**
         * 构造绑定 Capability、方向与业务 id 的键。id 不参与路径逻辑，仅用于标识与调试。
         */
        public static <T> Key<T> of(Capability<T> cap, @Nullable Direction side, @Nullable ResourceLocation id) {
            return new Key<>(cap, side, id);
        }

        public Capability<T> capability() {
            return cap;
        }

        @Nullable
        public Direction side() {
            return side;
        }

        @Nullable
        public ResourceLocation id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key<?> k)) return false;
            return cap.equals(k.cap) && side == k.side && Objects.equals(id, k.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cap, side, id);
        }
    }

    /**
     * 向玩家虚拟层的某位置放入一个能力实例（LazyOptional）。
     */
    public static <T> void put(ServerPlayer player, BlockPos pos, Key<T> key, LazyOptional<T> value) {
        FakeLevelAPI.putCapability(player, pos, key.capability(), key.side(), value);
    }

    /**
     * 从玩家虚拟层的某位置读取一个能力实例（LazyOptional）。
     */
    public static <T> LazyOptional<T> get(ServerPlayer player, BlockPos pos, Key<T> key) {
        return FakeLevelAPI.getCapability(player, pos, key.capability(), key.side());
    }

    /**
     * 从玩家虚拟层的某位置移除一个能力实例（可指定方向）。
     */
    public static void remove(ServerPlayer player, BlockPos pos, Key<?> key) {
        FakeLevelAPI.removeCapability(player, pos, key.capability(), key.side());
    }

    /**
     * 清空玩家虚拟层某位置挂载的全部能力。
     */
    public static void clearAll(ServerPlayer player, BlockPos pos) {
        FakeLevelAPI.clearCapabilities(player, pos);
    }
}