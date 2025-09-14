package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.service.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualWorldEvents {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            SessionService.flushActiveSession(sp);
            VirtualContainerGuard.end(sp);
            var ctx = WorldContextRegistry.getContext(sp);
            if (ctx != null) {
                StorageService.saveContext(sp.getServer(), sp.getUUID(), ctx);
            }
            WorldContextRegistry.clear(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (VirtualContainerGuard.getSession(sp) != null
                && !SessionService.hasActiveSessionItem(sp)) {
            SessionService.flushActiveSession(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        SessionService.flushActiveSession(sp);
        VirtualContainerGuard.end(sp);
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx != null) {
            StorageService.saveContext(sp.getServer(), sp.getUUID(), ctx);
        }
        // 其余清理保持
        var inv = sp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            var s = inv.items.get(i);
            if (!s.isEmpty()) {
                Utils.clearPendingBind(s);
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            var s = inv.offhand.get(i);
            if (!s.isEmpty()) {
                Utils.clearPendingBind(s);
            }
        }
        inv.setChanged();
        sp.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel realLevel)) return;

        SessionService.flushActiveSession(sp);

        BlockPos pos = event.getPos();
        var placedState = event.getState();

        var ctx = WorldContextRegistry.getContext(sp);
        ItemStack candidate = ItemStack.EMPTY;
        var main = sp.getMainHandItem();
        var off = sp.getOffhandItem();
        if (ctx != null) {
            var mainMatches = (main.getItem() instanceof BlockItem bi && bi.getBlock() == placedState.getBlock());
            var offMatches = (off.getItem() instanceof BlockItem bj && bj.getBlock() == placedState.getBlock());

            if (mainMatches && ctx.keyToPos.containsKey(DefaultKeyStrategy.INSTANCE.computeKey(main))) {
                candidate = main;
            } else if (offMatches && ctx.keyToPos.containsKey(DefaultKeyStrategy.INSTANCE.computeKey(off))) {
                candidate = off;
            } else if (mainMatches) {
                candidate = main;
            } else if (offMatches) {
                candidate = off;
            }
        } else {
            if (main.getItem() instanceof BlockItem bi1 && bi1.getBlock() == placedState.getBlock()) {
                candidate = main;
            } else if (off.getItem() instanceof BlockItem bi2 && bi2.getBlock() == placedState.getBlock()) {
                candidate = off;
            } else if (main.getItem() instanceof BlockItem) {
                candidate = main;
            } else if (off.getItem() instanceof BlockItem) {
                candidate = off;
            }
        }

        if (!candidate.isEmpty()) {
            TransferService.transferFromFakeToReal(sp, candidate, realLevel, pos);
        }
    }


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        WorldContextRegistry.saveAllAndClear(server);
    }
}