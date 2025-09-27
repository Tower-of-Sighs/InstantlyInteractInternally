package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.utils.FakeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        CONTEXTS.clear();
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
        java.util.UUID id = player.getUUID();
        if (CONTEXTS.containsKey(id)) return;

        // 创建空上下文，外部 Provider 自行恢复其 key→pos
        var ctx = new WorldContext(player);
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