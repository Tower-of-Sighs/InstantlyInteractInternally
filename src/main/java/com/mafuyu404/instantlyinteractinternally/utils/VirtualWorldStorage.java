package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.utils.service.WorldContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.UUID;

@Deprecated
public final class VirtualWorldStorage {

    private VirtualWorldStorage() {
    }

    public record StoredEntry(String key, String blockId, CompoundTag beTag) {
    }

    public record StoredContext(List<StoredEntry> entries, int nextIndex) {
    }

    public static void save(MinecraftServer server, UUID playerId, WorldContext ctx) {
        // no-op：key->pos 的持久化由外部 Provider 负责
    }

    public static StoredContext load(MinecraftServer server, UUID playerId) {
        return null;
    }
}