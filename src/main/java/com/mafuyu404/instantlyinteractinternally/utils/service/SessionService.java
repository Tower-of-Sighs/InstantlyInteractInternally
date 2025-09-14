package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.Utils;
import com.mafuyu404.instantlyinteractinternally.utils.VirtualContainerGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class SessionService {

    private SessionService() {
    }

    public static void beginSession(ServerPlayer player, String sessionId, BlockPos pos) {
        VirtualContainerGuard.beginSession(player, sessionId, pos);
    }

    public static void endSession(ServerPlayer player) {
        VirtualContainerGuard.endSession(player);
    }

    public static ResourceLocation groupForPlayer(ServerPlayer sp, String suffix) {
        return new ResourceLocation("i3", "player/" + sp.getStringUUID() + "/" + suffix);
    }

    public static ResourceLocation groupForSession(ServerPlayer sp, String sessionId) {
        return new ResourceLocation("i3", "session/" + sp.getStringUUID() + "/" + sessionId);
    }

    public static void scheduleForPlayer(ServerPlayer sp, Runnable task,
                                         String suffix, int delayTicks, int throttlePerTick, boolean coalesce) {
        FakeLevelAPI.scheduleTaskEx(sp, task, groupForPlayer(sp, suffix), delayTicks, throttlePerTick, coalesce);
    }

    public static void scheduleForSession(ServerPlayer sp, String sessionId, Runnable task,
                                          int delayTicks, int throttlePerTick, boolean coalesce) {
        FakeLevelAPI.scheduleTaskEx(sp, task, groupForSession(sp, sessionId), delayTicks, throttlePerTick, coalesce);
    }

    public static boolean hasActiveSessionItem(ServerPlayer sp) {
        var sess = VirtualContainerGuard.getSession(sp);
        if (sess == null) return false;
        String sessionId = sess.sessionId();
        var inv = sp.getInventory();

        for (int i = 0; i < inv.items.size(); i++) {
            var s = inv.items.get(i);
            if (!s.isEmpty() && s.hasTag() && sessionId.equals(s.getTag().getString("i3_session"))) {
                return true;
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            var s = inv.offhand.get(i);
            if (!s.isEmpty() && s.hasTag() && sessionId.equals(s.getTag().getString("i3_session"))) {
                return true;
            }
        }
        return false;
    }

    public static BlockPos ensurePosForSession(ServerPlayer player, String sessionId) {
        return WorldContextRegistry.ensurePosForSession(player, sessionId);
    }

    public static void flushActiveSession(ServerPlayer sp) {
        var sess = VirtualContainerGuard.getSession(sp);
        if (sess == null) return;

        WorldContextRegistry.maybeLoadContext(sp);
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx == null) {
            VirtualContainerGuard.endSession(sp);
            return;
        }

        String sessionId = sess.sessionId();
        BlockPos pos = ctx.sessionToPos.get(sessionId);
        if (pos != null) {
            BlockEntity be = ctx.level.getBlockEntity(pos);
            if (be != null && ContainerHelper.isContainerLike(be)) {
                ItemStack stack = findStackBySession(sp, sessionId);
                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                    boolean empty = ContainerHelper.isContainerEmpty(be);
                    if (!empty) {
                        Utils.writeBlockEntityTagToItem(be, stack);
                    } else {
                        Utils.clearBlockEntityTag(stack);
                    }
                    var tag = stack.getTag();
                    if (tag != null) {
                        tag.remove("i3_session");
                        if (tag.isEmpty()) stack.setTag(null);
                    }
                    sp.getInventory().setChanged();
                }
            }
            ctx.sessionToPos.remove(sessionId);
        }

        VirtualContainerGuard.endSession(sp);
        // 合并/节流玩家侧 UI 刷新，避免高频抖动
        scheduleForPlayer(sp, () -> sp.containerMenu.broadcastChanges(),
                "ui/refresh", 0, 1, true);
    }

    private static ItemStack findStackBySession(ServerPlayer sp, String sessionId) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            var s = inv.items.get(i);
            if (!s.isEmpty() && s.hasTag() && sessionId.equals(s.getTag().getString("i3_session"))) {
                return s;
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            var s = inv.offhand.get(i);
            if (!s.isEmpty() && s.hasTag() && sessionId.equals(s.getTag().getString("i3_session"))) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}