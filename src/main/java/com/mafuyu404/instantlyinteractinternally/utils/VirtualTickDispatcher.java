package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualTickDispatcher {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer sp : spIterable()) {
            var ctx = getContext(sp);
            if (ctx == null) continue;


            FakeLevel level = ctx.level;

            List<Map.Entry<String, BlockPos>> entries = new ArrayList<>(ctx.keyToPos.entrySet());
            List<Map.Entry<String, BlockPos>> toClear = new ArrayList<>();

            for (Map.Entry<String, BlockPos> entry : entries) {
                BlockPos pos = entry.getValue();

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

                boolean canClear = !VirtualContainerGuard.isActive(sp);
                boolean empty = (be != null) && VirtualWorldManager.isContainerEmpty(be);
                if (canClear && empty) {
                    toClear.add(entry);
                }
            }

            for (Map.Entry<String, BlockPos> entry : toClear) {
                String key = entry.getKey();
                VirtualWorldManager.clearInstanceTagFromInventory(sp, key);
            }
        }
    }

    private static Iterable<ServerPlayer> spIterable() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayers() : Collections.emptyList();
    }

    private static VirtualWorldManager.Context getContext(ServerPlayer sp) {
        return VirtualWorldManager.getContext(sp);
    }
}