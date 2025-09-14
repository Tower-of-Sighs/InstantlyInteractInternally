package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualContainerGuard {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private VirtualContainerGuard() {
    }

    public static void begin(ServerPlayer player) {
        ACTIVE.add(player.getUUID());
    }

    public static void end(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public record Session(String sessionId, BlockPos pos) {
    }

    public static void beginSession(ServerPlayer player, String sessionId, BlockPos pos) {
        ACTIVE.add(player.getUUID());
        SESSIONS.put(player.getUUID(), new Session(sessionId, pos));
    }

    public static void endSession(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
    }

    public static Session getSession(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }
}