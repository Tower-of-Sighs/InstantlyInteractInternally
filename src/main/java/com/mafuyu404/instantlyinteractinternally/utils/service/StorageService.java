package com.mafuyu404.instantlyinteractinternally.utils.service;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class StorageService {
    private StorageService() {
    }

    public static void saveContext(MinecraftServer server, UUID playerId, WorldContext ctx) {
        // key->pos 的持久化已移除；若需要持久化，请在其 Provider 内自行处理
    }
}