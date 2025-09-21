package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import com.mafuyu404.instantlyinteractinternally.utils.Utils;
import com.mafuyu404.instantlyinteractinternally.utils.VirtualContainerGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;

public final class SessionService {

    private SessionService() {
    }

    public static void beginSession(ServerPlayer player, String sessionId, BlockPos pos) {
        VirtualContainerGuard.beginSession(player, sessionId, pos);
        Instantlyinteractinternally.debug("[SS] 开始会话 id={} pos={} 玩家={}", sessionId, pos, player.getGameProfile().getName());
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

    public static String getActiveSessionId(ServerPlayer sp) {
        var session = VirtualContainerGuard.getTopItemSession(sp);
        return session != null ? session.sessionId : null;
    }

    public static BlockPos ensurePosForSession(ServerPlayer player, String sessionId) {
        return WorldContextRegistry.ensurePosForSession(player, sessionId);
    }

    public static void flushActiveSession(ServerPlayer sp) {
        flushActiveSession(sp, true);
    }

    private static final class SessionCtx {
        final ServerPlayer sp;
        final VirtualContainerGuard.Session sess;
        final String sessionId;
        final BlockEntity be;
        final AbstractContainerMenu preferMenu;

        SessionCtx(ServerPlayer sp, VirtualContainerGuard.Session sess, BlockEntity be) {
            this.sp = sp;
            this.sess = sess;
            this.sessionId = sess.sessionId;
            this.be = be;
            this.preferMenu = sess.parentContainer;
        }
    }

    private static SessionCtx resolveSessionCtx(ServerPlayer sp) {
        var sess = VirtualContainerGuard.getTopItemSession(sp);
        if (sess == null) return null;

        WorldContextRegistry.maybeLoadContext(sp);
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx == null) {
            VirtualContainerGuard.endCurrentSession(sp);
            return null;
        }
        var pos = ctx.sessionToPos.get(sess.sessionId);
        if (pos == null) return null;

        var be = ctx.level.getBlockEntity(pos);
        return new SessionCtx(sp, sess, be);
    }

    private static void removeSessionMapping(ServerPlayer sp, String sid) {
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx != null) {
            ctx.sessionToPos.remove(sid);
        }
    }

    private static void finalizeTagAndMaybeClearMapping(SessionCtx sc, ItemStack s, boolean finalize) {
        if (!finalize) return;
        var tag = s.getTag();
        if (tag != null) {
            tag.remove("i3_session");
            if (tag.isEmpty()) s.setTag(null);
        }
        removeSessionMapping(sc.sp, sc.sessionId);
        Instantlyinteractinternally.debug("[SS] 完成最终刷盘：移除 sid={} 的映射与标签", sc.sessionId);
    }

    private static void markDirtySlot(Slot slot) {
        if (slot == null) return;
        slot.setChanged();
        if (slot.container != null) {
            slot.container.setChanged();
        }
    }

    private static void markDirtyPreferMenuOrInv(ServerPlayer sp, AbstractContainerMenu preferMenu, ItemStack stack) {
        boolean dirtyDone = false;
        if (preferMenu != null) {
            for (Slot sl : preferMenu.slots) {
                if (sl.getItem() == stack) {
                    markDirtySlot(sl);
                    dirtyDone = true;
                    break;
                }
            }
        }
        if (!dirtyDone) {
            sp.getInventory().setChanged();
        }
    }

    private static boolean writeBeTag(BlockEntity be, ItemStack s) {
        if (be == null || s == null) return false;
        boolean empty = ContainerHelper.isContainerEmpty(be);
        if (!empty) {
            Utils.writeBlockEntityTagToItem(be, s);
            return true;
        } else {
            Utils.clearBlockEntityTag(s);
            return true;
        }
    }

    // 路径1：优先用父容器菜单中的 Slot 写回
    private static boolean writeBackViaMenuSlot(SessionCtx sc, boolean finalize) {
        if (sc.be == null || !ContainerHelper.isContainerLike(sc.be)) return false;
        var slot = findSlotWithSessionInContainer(sc.preferMenu, sc.sessionId);
        if (slot == null) return false;

        ItemStack s = slot.getItem();
        Instantlyinteractinternally.debug("[SS] 刷盘活动会话 finalize={} sid={} preferMenu={} foundSlot=true",
                finalize, sc.sessionId, sc.preferMenu != null ? sc.preferMenu.getClass().getSimpleName() : "null");

        if (writeBeTag(sc.be, s)) {
            Instantlyinteractinternally.debug("[SS] 写回(菜单Slot) sid={} empty={}", sc.sessionId, ContainerHelper.isContainerEmpty(sc.be));
            finalizeTagAndMaybeClearMapping(sc, s, finalize);
            markDirtySlot(slot);
            return true;
        }
        return false;
    }

    // 路径2：通过“在玩家背包/当前或父菜单搜索栈”写回
    private static boolean writeBackViaStackSearch(SessionCtx sc, boolean finalize) {
        if (sc.be == null || !ContainerHelper.isContainerLike(sc.be)) return false;

        ItemStack stack = findStackBySession(sc.sp, sc.sessionId, sc.preferMenu);
        Instantlyinteractinternally.debug("[SS] 刷盘活动会话 finalize={} sid={} preferMenu={} foundStack={}",
                finalize, sc.sessionId, sc.preferMenu != null ? sc.preferMenu.getClass().getSimpleName() : "null",
                !stack.isEmpty());

        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
            if (writeBeTag(sc.be, stack)) {
                Instantlyinteractinternally.debug("[SS] 写回(搜索栈) sid={} empty={}", sc.sessionId, ContainerHelper.isContainerEmpty(sc.be));
                finalizeTagAndMaybeClearMapping(sc, stack, finalize);
                markDirtyPreferMenuOrInv(sc.sp, sc.preferMenu, stack);
                return true;
            }
        }
        return false;
    }

    // 路径3：父容器已关闭或未找到栈，直接扫描方块实体的真实库存写回
    private static boolean writeBackViaBEInventory(SessionCtx sc, boolean finalize) {
        if (!(sc.be instanceof Container container)) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || !s.hasTag()) continue;
            if (!sc.sessionId.equals(s.getTag().getString("i3_session"))) continue;

            if (writeBeTag(sc.be, s)) {
                Instantlyinteractinternally.debug("[SS] [BE库存] 写回 sid={} slot={} empty={}",
                        sc.sessionId, i, ContainerHelper.isContainerEmpty(sc.be));
                finalizeTagAndMaybeClearMapping(sc, s, finalize);
                container.setChanged();
                sc.be.setChanged();
                return true;
            }
        }
        return false;
    }

    private static void finalizeSessionStackCleanIfNeeded(ServerPlayer sp) {
        var top = VirtualContainerGuard.getSession(sp);
        if (top != null && top.isContainerSession) {
            if (sp.containerMenu != top.containerMenu) {
                Instantlyinteractinternally.debug("[SS] finalize 后清理失效父容器占位 menu={}",
                        top.containerMenu != null ? top.containerMenu.getClass().getSimpleName() : "null");
                VirtualContainerGuard.endCurrentSession(sp);
            }
        }
    }

    public static void flushActiveSession(ServerPlayer sp, boolean finalize) {
        var sc = resolveSessionCtx(sp);
        if (sc == null) return;

        boolean handled = writeBackViaMenuSlot(sc, finalize)
                || writeBackViaStackSearch(sc, finalize)
                || writeBackViaBEInventory(sc, finalize);

        if (finalize) {
            VirtualContainerGuard.endCurrentSession(sp);
            finalizeSessionStackCleanIfNeeded(sp);
        }

        scheduleForPlayer(sp, () -> sp.containerMenu.broadcastChanges(), "ui/refresh", 0, 1, true);
    }

    // 在容器关闭时，对该容器内所有带 i3_session 的物品逐一落盘并移除标签（仅当成功写回到物品栈时移除映射）
    public static void flushSessionsInContainer(ServerPlayer sp, AbstractContainerMenu container) {
        if (container == null) return;
        WorldContextRegistry.maybeLoadContext(sp);
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx == null) return;

        var suppressed = VirtualContainerGuard.getSuppressedCloseSessionIds(sp);
        Set<String> handled = new HashSet<>();
        for (Slot slot : container.slots) {
            if (slot.container instanceof Inventory) continue;

            ItemStack s = slot.getItem();
            if (s.isEmpty() || !s.hasTag()) continue;
            String sid = s.getTag().getString("i3_session");
            if (sid == null || sid.isEmpty() || !handled.add(sid)) continue;

            if (suppressed.contains(sid)) {
                handleSuppressedPartialWriteBack(sp, sid, slot);
                continue;
            }
            handleFinalizeWriteBack(sp, sid, slot);
        }
        sp.containerMenu.broadcastChanges();
    }

    private static void handleSuppressedPartialWriteBack(ServerPlayer sp, String sid, Slot slot) {
        Instantlyinteractinternally.debug("[SS] 关闭刷盘（部分写回，保留会话） sid={} slot={}", sid, slot.index);

        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx == null) return;

        var pos = ctx.sessionToPos.get(sid);
        if (pos == null) return;

        BlockEntity be = ctx.level.getBlockEntity(pos);
        if (be == null || !ContainerHelper.isContainerLike(be)) return;

        ItemStack s = slot.getItem();
        writeBeTag(be, s);
        markDirtySlot(slot);
        // 保留 i3_session 与 ctx 映射
    }

    private static void handleFinalizeWriteBack(ServerPlayer sp, String sid, Slot slot) {
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx != null) {
            var pos = ctx.sessionToPos.get(sid);
            if (pos != null) {
                BlockEntity be = ctx.level.getBlockEntity(pos);
                if (be != null && ContainerHelper.isContainerLike(be)) {
                    ItemStack s = slot.getItem();
                    writeBeTag(be, s);
                    Instantlyinteractinternally.debug("[SS] 关闭容器写回 sid={} slot={}", sid, slot.index);

                    removeSessionMapping(sp, sid);
                    VirtualContainerGuard.endSessionById(sp, sid);
                    var tag = s.getTag();
                    if (tag != null) {
                        tag.remove("i3_session");
                        if (tag.isEmpty()) s.setTag(null);
                    }
                    markDirtySlot(slot);
                }
            }
        }
    }

    private static ItemStack findStackBySession(ServerPlayer sp, String sessionId, AbstractContainerMenu menu) {
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
        if (sp.containerMenu != null) {
            ItemStack carried = sp.containerMenu.getCarried();
            if (!carried.isEmpty() && carried.hasTag() && sessionId.equals(carried.getTag().getString("i3_session"))) {
                return carried;
            }
        }
        AbstractContainerMenu target = (menu != null) ? menu : sp.containerMenu;
        if (target != null) {
            for (Slot slot : target.slots) {
                ItemStack s = slot.getItem();
                if (!s.isEmpty() && s.hasTag() && sessionId.equals(s.getTag().getString("i3_session"))) {
                    return s;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static Slot findSlotWithSessionInContainer(AbstractContainerMenu menu, String sessionId) {
        if (menu == null) return null;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack s = slot.getItem();
            if (!s.isEmpty() && s.hasTag()) {
                String sid = s.getTag().getString("i3_session");
                if (sessionId.equals(sid)) {
                    return slot;
                }
            }
        }
        return null;
    }

    // 在容器关闭时，如果当前栈顶是该容器的子会话，则循环 finalize 直至不再匹配
    public static void finalizeSessionsForParentClose(ServerPlayer sp, AbstractContainerMenu parent) {
        if (parent == null) return;
        // 避免在父容器因打开子容器而关闭的时序里提前 finalize 子会话
        var suppressed = VirtualContainerGuard.getSuppressedCloseSessionIds(sp);

        while (true) {
            var top = VirtualContainerGuard.getSession(sp);
            if (top == null) break;
            if (top.isContainerSession) {
                // 父容器占位符留给 VirtualWorldEvents 去弹出
                break;
            }
            if (top.parentContainer == parent) {
                if (suppressed.contains(top.sessionId)) {
                    Instantlyinteractinternally.LOGGER.info(
                            "[SS] 父容器关闭时跳过被抑制的子会话 sid={} parent={}",
                            top.sessionId, parent.getClass().getSimpleName());
                    // 这是刚启动的子会话（例如从父容器里继续打开的 容器GUI），不能现在 finalize
                    break;
                }
                // 完整刷盘并弹出该子会话
                flushActiveSession(sp, true);
                // 继续看下一个栈顶是否仍属于同一父容器
                continue;
            }
            break;
        }
    }
}