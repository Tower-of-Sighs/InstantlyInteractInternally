package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.utils.FakeLevel;
import com.mafuyu404.instantlyinteractinternally.utils.VirtualWorldStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldContextRegistry {

    private WorldContextRegistry() {
    }

    private static final Map<UUID, WorldContext> CONTEXTS = new ConcurrentHashMap<>();

    public static FakeLevel getOrCreateLevel(ServerPlayer player) {
        maybeLoadContext(player);
        return CONTEXTS.computeIfAbsent(player.getUUID(), id -> new WorldContext(player)).level;
    }

    public static WorldContext getContext(ServerPlayer player) {
        maybeLoadContext(player);
        return CONTEXTS.get(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        CONTEXTS.remove(player.getUUID());
    }

    public static void saveAllAndClear(MinecraftServer server) {
        // 保存所有玩家的 WorldContext
        for (var entry : CONTEXTS.entrySet()) {
            var uuid = entry.getKey();
            var ctx = entry.getValue();
            StorageService.saveContext(server, uuid, ctx);
        }
        CONTEXTS.clear();
    }

    public static BlockPos ensurePosNearPlayer(ServerPlayer player, String key) {
        maybeLoadContext(player);
        var ctx = CONTEXTS.computeIfAbsent(player.getUUID(), id -> new WorldContext(player));
        var pos = ctx.keyToPos.get(key);
        if (pos == null) {
            pos = computeNearPos(player, key, ctx.nextIndex++);
            ctx.keyToPos.put(key, pos);
            return pos;
        }

        // ≤ 8 格
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double dist2 = (px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz);
        if (dist2 <= 64.0) return pos;

        // 迁移
        var level = ctx.level;
        BlockPos newPos = computeNearPos(player, key, ctx.nextIndex++);
        if (!newPos.equals(pos)) {
            level.moveBlockWithBE(pos, newPos);
            ctx.keyToPos.put(key, newPos);
            return newPos;
        }
        return pos;
    }

    public static BlockPos ensurePosForSession(ServerPlayer player, String sessionId) {
        maybeLoadContext(player);
        var ctx = CONTEXTS.computeIfAbsent(player.getUUID(), id -> new WorldContext(player));
        BlockPos pos = ctx.sessionToPos.get(sessionId);
        if (pos != null) return pos;
        BlockPos newPos = computeNearPos(player, sessionId, ctx.nextIndex++);
        ctx.sessionToPos.put(sessionId, newPos);
        return newPos;
    }

    public static void maybeLoadContext(ServerPlayer player) {
        UUID id = player.getUUID();
        if (CONTEXTS.containsKey(id)) return;

        var stored = VirtualWorldStorage.load(player.getServer(), id);
        if (stored == null) return;

        var ctx = new WorldContext(player);
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
        }
        ctx.nextIndex = Math.max(ctx.nextIndex, stored.nextIndex());
        CONTEXTS.put(id, ctx);
    }

    public static BlockPos computeNearPos(ServerPlayer player, String key, int index) {
        int baseX = (int) Math.floor(player.getX());
        int baseY = player.getBlockY();
        int baseZ = (int) Math.floor(player.getZ());

        int h = key.hashCode() ^ (index * 31);
        int dx = 2 + (h & 3);        // 2..5
        int dz = 2 + ((h >> 2) & 3); // 2..5
        int dy = ((h >> 4) & 1) == 0 ? 0 : 1;

        return new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
    }
}