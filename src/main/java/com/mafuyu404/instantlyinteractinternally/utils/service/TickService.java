package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.FakeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class TickService {
    private TickService() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer sp : spIterable()) {
            var ctx = WorldContextRegistry.getContext(sp);
            if (ctx == null) continue;

            var provider = FakeLevelAPI.getKeyPosProvider(sp);
            FakeLevel level = ctx.level;

            Set<BlockPos> positions = new HashSet<>();
            if (provider != null) {
                for (BlockPos p : provider.allPositions(sp)) {
                    positions.add(p);
                }
            }
            for (Map.Entry<?, BlockPos> e : ctx.sessionToPos.entrySet()) {
                positions.add(e.getValue());
            }
            positions.addAll(level.allBlockEntityPositions());

            tickBlockEntitiesPositions(level, positions);

            FakeLevelAPI.drainTasks(sp, 0);

            sp.containerMenu.broadcastChanges();
        }
    }

    private static void tickBlockEntitiesPositions(FakeLevel level, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            tickBlockEntityAt(level, pos);
        }
    }

    private static void tickBlockEntities(FakeLevel level, Iterable<? extends Map.Entry<?, BlockPos>> entries) {
        for (Map.Entry<?, BlockPos> entry : entries) {
            tickBlockEntityAt(level, entry.getValue());
        }
    }

    private static void tickBlockEntityAt(FakeLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            if (be.getLevel() != level) {
                be.setLevel(level);
            }
            @SuppressWarnings("unchecked")
            BlockEntityTicker<BlockEntity> ticker =
                    state.getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
            if (ticker != null) {
                ticker.tick(level, pos, state, be);
            }
        }
    }

    private static Iterable<ServerPlayer> spIterable() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayers() : Collections.emptyList();
    }
}