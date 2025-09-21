package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualContainerGuard {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Deque<Session>> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> SUPPRESS_CLOSE = new ConcurrentHashMap<>();

    private VirtualContainerGuard() {
    }

    public static void begin(ServerPlayer player) {
        ACTIVE.add(player.getUUID());
    }

    public static void end(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static final class Session {
        public final String sessionId;
        public final BlockPos pos;
        public AbstractContainerMenu containerMenu; // 当前活动 GUI 容器（子容器）
        public AbstractContainerMenu parentContainer; // 会话物品所在的父容器（开始会话时记录）
        public boolean isContainerSession; // 父容器会话占位（非物品会话）

        public Session(String sessionId, BlockPos pos) {
            this.sessionId = sessionId;
            this.pos = pos;
        }
    }

    public static void beginSession(ServerPlayer player, String sessionId, BlockPos pos) {
        ACTIVE.add(player.getUUID());
        var dq = SESSIONS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());

        AbstractContainerMenu parent = player.containerMenu;
        pushParentContainerPlaceholderIfNeeded(dq, player, parent, sessionId, pos);

        Session s = new Session(sessionId, pos);
        s.parentContainer = parent;
        s.isContainerSession = false;
        dq.push(s);

        Instantlyinteractinternally.debug(
                "[VCG] 开始子会话 id={} pos={} parentMenu={} 玩家={}",
                sessionId, pos, parent != null ? parent.getClass().getSimpleName() : "null",
                player.getGameProfile().getName());
    }

    private static void pushParentContainerPlaceholderIfNeeded(Deque<Session> dq,
                                                               ServerPlayer player,
                                                               AbstractContainerMenu parent,
                                                               String sessionId,
                                                               BlockPos pos) {
        if (parent == null) return;
        var top = dq.peekFirst();
        if (top == null || top.containerMenu != parent || !top.isContainerSession) {
            Session parentSess = new Session("__container__:" + sessionId, pos);
            parentSess.parentContainer = null;
            parentSess.containerMenu = parent;
            parentSess.isContainerSession = true;
            dq.push(parentSess);
            Instantlyinteractinternally.debug(
                    "[VCG] 推入父容器会话占位符 menu={} 玩家={}",
                    parent.getClass().getSimpleName(), player.getGameProfile().getName());
        }
    }

    public static void endSession(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        SESSIONS.remove(player.getUUID());
        SUPPRESS_CLOSE.remove(player.getUUID());
    }

    public static void endCurrentSession(ServerPlayer player) {
        var dq = SESSIONS.get(player.getUUID());
        if (dq == null) return;
        var popped = dq.pollFirst();
        if (popped != null) {
            com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally.debug(
                    "[VCG] 弹出会话 id={} isContainerSession={} 玩家={}",
                    popped.sessionId, popped.isContainerSession, player.getGameProfile().getName());
        }
        if (dq.isEmpty()) {
            SESSIONS.remove(player.getUUID());
        }
    }

    public static Session getSession(ServerPlayer player) {
        var dq = SESSIONS.get(player.getUUID());
        return dq != null ? dq.peekFirst() : null;
    }

    public static void setCurrentContainer(ServerPlayer player, AbstractContainerMenu menu) {
        var dq = SESSIONS.get(player.getUUID());
        if (dq != null) {
            var s = dq.peekFirst();
            if (s != null) {
                s.containerMenu = menu;
            }
        }
    }

    public static void setSuppressCloseSession(ServerPlayer player, String sessionId) {
        var set = SUPPRESS_CLOSE.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        set.add(sessionId);
    }

    public static Set<String> getSuppressedCloseSessionIds(ServerPlayer player) {
        var set = SUPPRESS_CLOSE.get(player.getUUID());
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public static void clearSuppressedCloseSession(ServerPlayer player, String sessionId) {
        var set = SUPPRESS_CLOSE.get(player.getUUID());
        if (set != null) {
            set.remove(sessionId);
            if (set.isEmpty()) {
                SUPPRESS_CLOSE.remove(player.getUUID());
            }
        }
    }

    public static boolean endSessionById(ServerPlayer player, String sessionId) {
        var dq = SESSIONS.get(player.getUUID());
        if (dq == null || sessionId == null) return false;
        var it = dq.iterator();
        while (it.hasNext()) {
            var s = it.next();
            if (!s.isContainerSession && sessionId.equals(s.sessionId)) {
                it.remove();
                com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally.debug(
                        "[VCG] 按ID结束会话 id={} 玩家={}", sessionId, player.getGameProfile().getName());
                if (dq.isEmpty()) {
                    SESSIONS.remove(player.getUUID());
                }
                return true;
            }
        }
        return false;
    }

    // 获取栈顶的子会话（跳过父容器占位符）
    public static Session getTopItemSession(ServerPlayer player) {
        var dq = SESSIONS.get(player.getUUID());
        if (dq == null) return null;
        for (var s : dq) {
            if (!s.isContainerSession) return s;
        }
        return null;
    }
}