package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.service.SessionService;
import com.mafuyu404.instantlyinteractinternally.utils.service.TransferService;
import com.mafuyu404.instantlyinteractinternally.utils.service.WorldContextRegistry;
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
            while (SessionService.getActiveSessionId(sp) != null) {
                SessionService.flushActiveSession(sp, true);
            }
            VirtualContainerGuard.end(sp);
            WorldContextRegistry.clear(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        VirtualContainerGuard.setCurrentContainer(sp, event.getContainer());

        // 如果是刚启动的新会话导致的打开，清除该会话的关闭抑制标记
        String active = SessionService.getActiveSessionId(sp);
        if (active != null) {
            VirtualContainerGuard.clearSuppressedCloseSession(sp, active);
        }

        Instantlyinteractinternally.debug(
                "[VWE] 打开容器 menu={} 玩家={} activeSid={}",
                event.getContainer().getClass().getSimpleName(),
                sp.getGameProfile().getName(),
                active);

    }

    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        Instantlyinteractinternally.debug(
                "[VWE] 正在关闭容器 menu={} 玩家={}",
                event.getContainer().getClass().getSimpleName(),
                sp.getGameProfile().getName());

        var s = VirtualContainerGuard.getSession(sp);
        if (s != null && s.containerMenu == event.getContainer() && !s.isContainerSession) {
            Instantlyinteractinternally.debug(
                    "[VWE] 捕获到关闭当前会话GUI sid={}，延迟判定是否最终刷盘", s.sessionId);
            SessionService.flushActiveSession(sp, true);
        }

        SessionService.flushSessionsInContainer(sp, event.getContainer());
        SessionService.finalizeSessionsForParentClose(sp, event.getContainer());

        var top = VirtualContainerGuard.getSession(sp);
        if (top != null && top.isContainerSession && top.containerMenu == event.getContainer()) {
            Instantlyinteractinternally.debug(
                    "[VWE] 弹出父容器会话占位符 menu={}",
                    event.getContainer().getClass().getSimpleName());
            VirtualContainerGuard.endCurrentSession(sp);
        }

        var inv = sp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            var sItem = inv.items.get(i);
            if (!sItem.isEmpty()) {
                Utils.clearPendingBind(sItem);
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            var sItem = inv.offhand.get(i);
            if (!sItem.isEmpty()) {
                Utils.clearPendingBind(sItem);
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

        ItemStack candidate = ItemStack.EMPTY;
        var main = sp.getMainHandItem();
        var off = sp.getOffhandItem();
        boolean mainResolvable = !main.isEmpty()
                && (main.getItem() instanceof BlockItem biMain && biMain.getBlock() == placedState.getBlock())
                && FakeLevelAPI.resolveKeyPos(sp, FakeLevelAPI.computeKey(main)) != null;

        boolean offResolvable = !off.isEmpty()
                && (off.getItem() instanceof BlockItem biOff && biOff.getBlock() == placedState.getBlock())
                && FakeLevelAPI.resolveKeyPos(sp, FakeLevelAPI.computeKey(off)) != null;

        if (mainResolvable) {
            candidate = main;
        } else if (offResolvable) {
            candidate = off;
        } else if (main.getItem() instanceof BlockItem) {
            candidate = main;
        } else if (off.getItem() instanceof BlockItem) {
            candidate = off;
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