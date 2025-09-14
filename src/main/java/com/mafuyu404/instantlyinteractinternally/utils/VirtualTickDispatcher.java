package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.service.TickService;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualTickDispatcher {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        TickService.onServerTick(event);
    }
}