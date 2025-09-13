package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualContainerGuard {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private VirtualContainerGuard() {
    }

    public static void begin(ServerPlayer player) {
        ACTIVE.add(player.getUUID());
    }

    public static void end(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }
}