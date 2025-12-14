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

    // finalize=true：完全刷盘（写回后移除 i3_session 与映射）；finalize=false：部分刷盘（写回但保留 i3_session 与映射）
    public static void flushActiveSession(ServerPlayer sp, boolean finalize) {
        // 只针对子会话进行刷盘，避免拿到父容器占位符
        var sess = VirtualContainerGuard.getTopItemSession(sp);
        if (sess == null) return;

        WorldContextRegistry.maybeLoadContext(sp);
        var ctx = WorldContextRegistry.getContext(sp);
        if (ctx == null) {
            VirtualContainerGuard.endCurrentSession(sp);
            return;
        }

        String sessionId = sess.sessionId;
        BlockPos pos = ctx.sessionToPos.get(sessionId);
        BlockEntity be = null;
        if (pos != null) {
            be = ctx.level.getBlockEntity(pos);
        }

        AbstractContainerMenu preferMenu = sess.parentContainer;
        boolean wrote;
        ItemStack usedStack;

        // 路径1：父容器菜单中精确 Slot 写回
        var slotResult = tryWriteViaPreferSlot(be, preferMenu, sessionId);
        wrote = slotResult.wrote;
        usedStack = slotResult.stack;

        // 路径2：玩家/当前或父菜单定位 Stack 写回
        if (!wrote) {
            var stackResult = tryWriteViaLocatedStack(sp, be, preferMenu, sessionId);
            wrote = stackResult.wrote;
            usedStack = stackResult.stack;
        }

        // 路径3：直接扫描方块实体真实库存写回（父容器已关闭或前两条失败）
        if (!wrote) {
            var beResult = tryWriteViaBEInventory(be, sessionId);
            wrote = beResult.wrote;
            usedStack = beResult.stack;
        }

        // 无库存菜单：没有写回动作，但依然需要 finalize 时清理标签与映射
        if (!wrote && be != null && !ContainerHelper.isContainerLike(be)) {
            Instantlyinteractinternally.debug("[SS] 目标方块不含库存，sid={} 仅做会话清理", sessionId);
        }

        // finalize 后统一清理标签与映射（若能定位到栈则移除标签；无论是否定位到栈，均移除映射）
        if (finalize) {
            if (!usedStack.isEmpty()) {
                removeSessionTag(usedStack);
            } else {
                // 未定位到栈：在玩家/父菜单/当前菜单里再查一次以清理标签
                ItemStack fallback = findStackBySession(sp, sessionId, preferMenu);
                if (!fallback.isEmpty()) {
                    removeSessionTag(fallback);
                }
            }
            ctx.sessionToPos.remove(sessionId);
            Instantlyinteractinternally.debug("[SS] 完成最终刷盘：移除 sid={} 的映射与标签", sessionId);

            // 结束当前子会话
            VirtualContainerGuard.endCurrentSession(sp);
            // 若栈顶是一个父容器会话占位符，且该容器并非当前打开的 GUI，则清理掉占位
            var top = VirtualContainerGuard.getSession(sp);
            if (top != null && top.isContainerSession) {
                if (sp.containerMenu != top.containerMenu) {
                    Instantlyinteractinternally.debug("[SS] finalize 后清理失效父容器占位 menu={}",
                            top.containerMenu != null ? top.containerMenu.getClass().getSimpleName() : "null");
                    VirtualContainerGuard.endCurrentSession(sp);
                }
            }
        }

        scheduleForPlayer(sp, () -> sp.containerMenu.broadcastChanges(), "ui/refresh", 0, 1, true);
    }

    // 路径1：父容器菜单中精确 Slot 写回并标脏
    private static WriteBackResult tryWriteViaPreferSlot(BlockEntity be, AbstractContainerMenu preferMenu, String sessionId) {
        WriteBackResult res = new WriteBackResult();
        if (be == null || !ContainerHelper.isContainerLike(be)) return res;
        Slot preferSlot = findSlotWithSessionInContainer(preferMenu, sessionId);
        if (preferSlot == null) return res;

        ItemStack s = preferSlot.getItem();
        boolean empty = ContainerHelper.isContainerEmpty(be);
        if (!empty) {
            Utils.writeBlockEntityTagToItem(be, s);
            Instantlyinteractinternally.debug("[SS] [Slot] 写入 BlockEntityTag sid={} empty=false", sessionId);
        } else {
            Utils.clearBlockEntityTag(s);
            Instantlyinteractinternally.debug("[SS] [Slot] 清除 BlockEntityTag sid={} empty=true", sessionId);
        }

        preferSlot.setChanged();
        if (preferSlot.container != null) {
            preferSlot.container.setChanged();
        }
        res.wrote = true;
        res.stack = s;
        return res;
    }

    // 路径2：定位到 Stack 写回（玩家背包/当前或父菜单），并尽力标脏
    private static WriteBackResult tryWriteViaLocatedStack(ServerPlayer sp, BlockEntity be, AbstractContainerMenu preferMenu, String sessionId) {
        WriteBackResult res = new WriteBackResult();
        if (be == null || !ContainerHelper.isContainerLike(be)) return res;

        ItemStack stack = findStackBySession(sp, sessionId, preferMenu);
        Instantlyinteractinternally.debug("[SS] [Stack] sid={} preferMenu={} foundStack={}",
                sessionId, preferMenu != null ? preferMenu.getClass().getSimpleName() : "null", !stack.isEmpty());

        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return res;
        }

        boolean empty = ContainerHelper.isContainerEmpty(be);
        if (!empty) {
            Utils.writeBlockEntityTagToItem(be, stack);
            Instantlyinteractinternally.debug("[SS] [Stack] 写入 BlockEntityTag sid={} empty=false", sessionId);
        } else {
            Utils.clearBlockEntityTag(stack);
            Instantlyinteractinternally.debug("[SS] [Stack] 清除 BlockEntityTag sid={} empty=true", sessionId);
        }

        boolean dirtyDone = false;
        if (preferMenu != null) {
            for (Slot sl : preferMenu.slots) {
                if (sl.getItem() == stack) {
                    sl.setChanged();
                    if (sl.container != null) sl.container.setChanged();
                    dirtyDone = true;
                    break;
                }
            }
        }
        if (!dirtyDone) {
            sp.getInventory().setChanged();
        }
        res.wrote = true;
        res.stack = stack;
        return res;
    }

    // 路径3：BE 实库存扫描写回并标脏
    private static WriteBackResult tryWriteViaBEInventory(BlockEntity be, String sessionId) {
        WriteBackResult res = new WriteBackResult();
        if (!(be instanceof Container container)) return res;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty() || !s.hasTag()) continue;
            if (!sessionId.equals(s.getTag().getString("i3_session"))) continue;

            boolean empty = ContainerHelper.isContainerEmpty(be);
            if (!empty) {
                Utils.writeBlockEntityTagToItem(be, s);
                Instantlyinteractinternally.debug("[SS] [BE库存] 写入 BlockEntityTag sid={} slot={} empty=false", sessionId, i);
            } else {
                Utils.clearBlockEntityTag(s);
                Instantlyinteractinternally.debug("[SS] [BE库存] 清除 BlockEntityTag sid={} slot={} empty=true", sessionId, i);
            }

            container.setChanged();
            be.setChanged();
            res.wrote = true;
            res.stack = s;
            break;
        }
        return res;
    }

    private static void removeSessionTag(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null) {
            tag.remove("i3_session");
            if (tag.isEmpty()) stack.setTag(null);
        }
    }

    private static class WriteBackResult {
        boolean wrote = false;
        ItemStack stack = ItemStack.EMPTY;
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
            if (slot.container instanceof Inventory) {
                continue;
            }

            ItemStack s = slot.getItem();
            if (s.isEmpty() || !s.hasTag()) continue;
            String sid = s.getTag().getString("i3_session");
            if (sid == null || sid.isEmpty() || !handled.add(sid)) continue;

            // 被抑制：做部分写回，但保留 i3_session 与映射
            if (suppressed.contains(sid)) {
                Instantlyinteractinternally.debug("[SS] 关闭刷盘（部分写回，保留会话） sid={} slot={}", sid, slot.index);

                BlockPos pos = ctx.sessionToPos.get(sid);
                if (pos != null) {
                    BlockEntity be = ctx.level.getBlockEntity(pos);
                    if (be != null && ContainerHelper.isContainerLike(be)) {
                        boolean empty = ContainerHelper.isContainerEmpty(be);
                        if (!empty) {
                            Utils.writeBlockEntityTagToItem(be, s);
                        } else {
                            Utils.clearBlockEntityTag(s);
                        }
                        slot.setChanged();
                        if (slot.container != null) {
                            slot.container.setChanged();
                        }
                    }
                }
                // 注意：不移除 i3_session，不移除 ctx 映射，不结束会话
                continue;
            }

            // 非抑制：按原逻辑完整写回并清理
            boolean wroteBack = false;
            BlockPos pos = ctx.sessionToPos.get(sid);
            if (pos != null) {
                BlockEntity be = ctx.level.getBlockEntity(pos);
                if (be != null && ContainerHelper.isContainerLike(be)) {
                    boolean empty = ContainerHelper.isContainerEmpty(be);
                    if (!empty) {
                        Utils.writeBlockEntityTagToItem(be, s);
                    } else {
                        Utils.clearBlockEntityTag(s);
                    }
                    wroteBack = true;
                    Instantlyinteractinternally.debug("[SS] 关闭容器写回 sid={} slot={}", sid, slot.index);
                }
            }
            if (wroteBack) {
                ctx.sessionToPos.remove(sid);
                // 写回成功后，同时结束该 sid 的会话，避免会话遗留
                VirtualContainerGuard.endSessionById(sp, sid);
            }
            var tag = s.getTag();
            if (tag != null) {
                tag.remove("i3_session");
                if (tag.isEmpty()) s.setTag(null);
            }
            slot.setChanged();
            if (slot.container != null) {
                slot.container.setChanged();
            }
        }
        sp.containerMenu.broadcastChanges();
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