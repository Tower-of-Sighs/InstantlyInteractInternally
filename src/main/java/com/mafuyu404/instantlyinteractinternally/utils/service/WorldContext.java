package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.utils.FakeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldContext {
    public final FakeLevel level;
    public final Map<String, BlockPos> sessionToPos = new ConcurrentHashMap<>();
    public int nextIndex = 0;

    public WorldContext(ServerPlayer player) {
        this.level = new FakeLevel(player.serverLevel());
    }
}