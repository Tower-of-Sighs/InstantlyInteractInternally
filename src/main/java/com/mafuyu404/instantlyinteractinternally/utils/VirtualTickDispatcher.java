package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.service.TickService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualTickDispatcher {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        TickService.onServerTick(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (event.phase != TickEvent.Phase.END) return;

        int executed = FakeLevelAPI.drainTasks(sp, 0); // 0 表示不限制数量，节流由 FakeLevel 内部按 group 控制
        if (executed > 0) {
            Instantlyinteractinternally.debug("[TickService] drained={} player={}", executed, sp.getGameProfile().getName());
        }
    }
}