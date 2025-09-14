package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.VirtualWorldStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class StorageService {
    private StorageService() {
    }

    public static void saveContext(MinecraftServer server, UUID playerId, WorldContext ctx) {
        if (server == null || playerId == null || ctx == null) return;
        ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
        if (sp != null) {
            // 每玩家独立分组：i3:save/<uuid>，合并 + 每 tick 1 次
            ResourceLocation group = new ResourceLocation("i3", "save/" + playerId);
            FakeLevelAPI.scheduleTaskEx(sp,
                    () -> VirtualWorldStorage.save(server, playerId, ctx),
                    group,
                    2,   // 轻微延迟，便于合并
                    1,   // 每 tick 最多 1 次
                    true // 合并
            );
        } else {
            // 离线玩家：直接保存
            VirtualWorldStorage.save(server, playerId, ctx);
        }
    }
}