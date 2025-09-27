package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.network.NetworkHandler;
import com.mafuyu404.instantlyinteractinternally.network.UseProgressEnd;
import com.mafuyu404.instantlyinteractinternally.network.UseProgressStart;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class UseProgressTracker {

    private UseProgressTracker() {
    }

    private static final Map<UUID, InProgress> USING = new ConcurrentHashMap<>();

    private record InProgress(ItemStack toConsume, long endTick) {
    }

    public static void beginUseFromSlot(ServerPlayer player, Slot slot) {
        if (player == null || slot == null || !slot.hasItem()) return;

        if (USING.containsKey(player.getUUID())) return;

        ItemStack src = slot.getItem();
        ItemStack one = src.copy();
        one.setCount(1);

        src.shrink(1);
        slot.set(src.isEmpty() ? ItemStack.EMPTY : src);
        slot.setChanged();
        player.containerMenu.broadcastChanges();

        int duration = one.getUseDuration();
        if (duration <= 0) duration = 1;

        long end = getServer(player).getTickCount() + duration;
        USING.put(player.getUUID(), new InProgress(one, end));

        NetworkHandler.sendToClient(player, new UseProgressStart(duration));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) return;
        if (USING.isEmpty()) return;

        USING.forEach((uuid, prog) -> {
            ServerPlayer player = findPlayer(evt, uuid);
            if (player == null) {
                USING.remove(uuid);
                return;
            }
            long now = getServer(player).getTickCount();
            if (now >= prog.endTick()) {
                finish(player, prog);
                USING.remove(uuid);
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp) {
            USING.remove(sp.getUUID());
        }
    }

    private static void finish(ServerPlayer player, InProgress prog) {
        ItemStack after = prog.toConsume().finishUsingItem(player.level(), player);

        if (!after.isEmpty()) {
            boolean added = player.getInventory().add(after);
            if (!added) {
                player.drop(after, false);
            }
        }
        player.containerMenu.broadcastChanges();

        NetworkHandler.sendToClient(player, new UseProgressEnd());
    }

    private static MinecraftServer getServer(ServerPlayer p) {
        return p.getServer();
    }

    private static ServerPlayer findPlayer(TickEvent.ServerTickEvent evt, UUID id) {
        for (ServerLevel lvl : evt.getServer().getAllLevels()) {
            ServerPlayer p = lvl.getServer().getPlayerList().getPlayer(id);
            if (p != null) return p;
        }
        return null;
    }
}