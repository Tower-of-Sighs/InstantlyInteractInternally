package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualWorldEvents {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            VirtualContainerGuard.end(sp);
            var ctx = VirtualWorldManager.getContext(sp);
            if (ctx != null) {
                VirtualWorldStorage.save(sp.getServer(), sp.getUUID(), ctx);
            }
            VirtualWorldManager.clear(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        VirtualContainerGuard.end(sp);
        var ctx = VirtualWorldManager.getContext(sp);
        if (ctx != null) {
            VirtualWorldStorage.save(sp.getServer(), sp.getUUID(), ctx);
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel realLevel)) return;

        BlockPos pos = event.getPos();
        var placedState = event.getState();

        var ctx = VirtualWorldManager.getContext(sp);
        ItemStack candidate = ItemStack.EMPTY;
        var main = sp.getMainHandItem();
        if (ctx != null) {
            // 优先匹配上下文中存在 key 的手持物品
            var off = sp.getOffhandItem();
            var mainMatches = (main.getItem() instanceof BlockItem bi && bi.getBlock() == placedState.getBlock());
            var offMatches = (off.getItem() instanceof BlockItem bj && bj.getBlock() == placedState.getBlock());

            if (mainMatches && ctx.keyToPos.containsKey(VirtualWorldManager.computeKeyFor(main))) {
                candidate = main;
            } else if (offMatches && ctx.keyToPos.containsKey(VirtualWorldManager.computeKeyFor(off))) {
                candidate = off;
            } else if (mainMatches) {
                candidate = main; // 至少 Block 类型一致
            } else if (offMatches) {
                candidate = off;
            }
        } else {
            candidate = getItemStack(sp, main, placedState);
        }

        if (!candidate.isEmpty()) {
            VirtualWorldManager.transferFromFakeToReal(sp, candidate, realLevel, pos);
        }
    }

    private static @NotNull ItemStack getItemStack(ServerPlayer sp, ItemStack main, BlockState placedState) {
        var off = sp.getOffhandItem();

        ItemStack candidate = ItemStack.EMPTY;
        if (main.getItem() instanceof BlockItem bi1 && bi1.getBlock() == placedState.getBlock()) {
            candidate = main;
        } else if (off.getItem() instanceof BlockItem bi2 && bi2.getBlock() == placedState.getBlock()) {
            candidate = off;
        } else if (main.getItem() instanceof BlockItem) {
            candidate = main; // 退化匹配（少数模组可能修改了 Item -> Block 的取法）
        } else if (off.getItem() instanceof BlockItem) {
            candidate = off;
        }
        return candidate;
    }


    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        VirtualWorldManager.saveAllAndClear(server);
    }
}