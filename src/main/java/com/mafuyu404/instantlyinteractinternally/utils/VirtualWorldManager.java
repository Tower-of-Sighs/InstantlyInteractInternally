package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualWorldManager {

    private VirtualWorldManager() {
    }

    private static final Map<UUID, Context> CONTEXTS = new ConcurrentHashMap<>();

    public static FakeLevel getOrCreateLevel(ServerPlayer player) {
        maybeLoadContext(player);
        return CONTEXTS.computeIfAbsent(player.getUUID(), id -> new Context(player)).level;
    }

    public static BlockPos ensurePosNearPlayer(ServerPlayer player, ItemStack stack) {
        maybeLoadContext(player);
        Context ctx = CONTEXTS.computeIfAbsent(player.getUUID(), id -> new Context(player));
        FakeLevel level = ctx.level;
        String key = computeKeyFor(stack);

        BlockPos pos = ctx.keyToPos.get(key);
        if (pos == null) {
            pos = computeNearPos(player, key, ctx.nextIndex++);
            ctx.keyToPos.put(key, pos);
            return pos;
        }

        // 保持与玩家 ≤ 8 格
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double dist2 = (px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz);
        if (dist2 <= 64.0) return pos;

        // 如果过远则迁移至玩家附近的新坐标，保持方块与方块实体状态
        BlockPos newPos = computeNearPos(player, key, ctx.nextIndex++);
        if (!newPos.equals(pos)) {
            level.moveBlockWithBE(pos, newPos);
            ctx.keyToPos.put(key, newPos);
            return newPos;
        }
        return pos;
    }

    // 基于玩家当前位置与 key 的稳定偏移，分配“附近”坐标
    private static BlockPos computeNearPos(ServerPlayer player, String key, int index) {
        int baseX = (int) Math.floor(player.getX());
        int baseY = player.getBlockY();
        int baseZ = (int) Math.floor(player.getZ());

        int h = key.hashCode() ^ (index * 31);
        int dx = 2 + (h & 3);        // 2..5
        int dz = 2 + ((h >> 2) & 3); // 2..5
        int dy = ((h >> 4) & 1) == 0 ? 0 : 1; // 在当前 Y 或 +1，尽量贴近玩家高度

        return new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
    }


    public static void clear(ServerPlayer player) {
        CONTEXTS.remove(player.getUUID());
    }

    public static String computeKeyFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
            int tagHash = stack.hasTag() ? stack.getTag().hashCode() : 0;
            return id + "#" + tagHash;
        }
        return stack.getItem().toString();
    }

    public static void saveAllAndClear(MinecraftServer server) {
        for (var entry : CONTEXTS.entrySet()) {
            UUID uuid = entry.getKey();
            Context ctx = entry.getValue();
            VirtualWorldStorage.save(server, uuid, ctx);
        }
        CONTEXTS.clear();
    }

    private static void maybeLoadContext(ServerPlayer player) {
        UUID id = player.getUUID();
        if (CONTEXTS.containsKey(id)) return;

        var stored = VirtualWorldStorage.load(player.getServer(), id);
        if (stored == null) return;

        Context ctx = new Context(player);
        int idx = 0;
        for (var e : stored.entries()) {
            var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.blockId()));
            if (block == null) continue;

            String key = e.key();
            BlockPos pos = computeNearPos(player, key, ctx.nextIndex++);
            ctx.keyToPos.put(key, pos);

            var state = block.defaultBlockState();
            ctx.level.putBlock(pos, state);

            if (e.beTag() != null) {
                var be = BlockEntity.loadStatic(pos, state, e.beTag());
                if (be == null && block instanceof BaseEntityBlock beb) {
                    be = beb.newBlockEntity(pos, state);
                }
                if (be != null) {
                    be.setLevel(ctx.level);
                    ctx.level.setBlockEntity(be);
                }
            }
            idx++;
        }
        ctx.nextIndex = Math.max(ctx.nextIndex, stored.nextIndex());
        CONTEXTS.put(id, ctx);
    }

    public static void transferFromFakeToReal(ServerPlayer player, ItemStack placedStack, ServerLevel realLevel, BlockPos realPos) {
        maybeLoadContext(player);
        Context ctx = CONTEXTS.get(player.getUUID());
        if (ctx == null) return;

        String key = computeKeyFor(placedStack);
        BlockPos fakePos = ctx.keyToPos.get(key);
        if (fakePos == null) return;

        var fakeState = ctx.level.getBlockState(fakePos);
        var realState = realLevel.getBlockState(realPos);

        if (fakeState.getBlock() != realState.getBlock()) return;

        var fakeBE = ctx.level.getBlockEntity(fakePos);
        if (fakeBE == null) return;

        CompoundTag tag = fakeBE.saveWithFullMetadata();
        if (tag == null) return;

        var realBE = realLevel.getBlockEntity(realPos);
        if (realBE == null && realState.getBlock() instanceof BaseEntityBlock beb) {
            realBE = beb.newBlockEntity(realPos, realState);
            if (realBE != null) {
                realLevel.setBlockEntity(realBE);
            }
        }
        if (realBE == null) return;

        realBE.load(tag);
        realBE.setChanged();

        realLevel.sendBlockUpdated(realPos, realState, realState, 3);
    }

    public static boolean isContainerEmpty(BlockEntity be) {
        if (be instanceof Container c) {
            return c.isEmpty();
        }
        var opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        return opt.map(handler -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!handler.getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }).orElse(false);
    }

    public static void clearInstanceTagFromInventory(ServerPlayer sp, String key) {
        var inv = sp.getInventory();

        for (int i = 0; i < inv.items.size(); i++) {
            var s = inv.items.get(i);
            if (s.isEmpty()) continue;
            if (key.equals(computeKeyFor(s))) {
                Utils.clearPerInstanceTag(s);
                inv.setChanged();
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            var s = inv.offhand.get(i);
            if (s.isEmpty()) continue;
            if (key.equals(computeKeyFor(s))) {
                Utils.clearPerInstanceTag(s);
                inv.setChanged();
            }
        }

        sp.containerMenu.broadcastChanges();
    }

    public static final class Context {
        final FakeLevel level;
        final Map<String, BlockPos> keyToPos = new ConcurrentHashMap<>();
        int nextIndex = 0;

        Context(ServerPlayer player) {
            this.level = new FakeLevel(player.serverLevel());
        }
    }

    public static Context getContext(ServerPlayer player) {
        return CONTEXTS.get(player.getUUID());
    }
}