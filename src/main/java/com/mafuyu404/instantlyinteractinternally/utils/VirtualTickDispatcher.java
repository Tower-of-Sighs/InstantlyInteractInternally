package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VirtualTickDispatcher {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerPlayer sp : spIterable()) {
            var ctx = getContext(sp);
            if (ctx == null) continue;

            FakeLevel level = ctx.level;

            List<Map.Entry<String, BlockPos>> entries = new ArrayList<>(ctx.keyToPos.entrySet());
            List<Map.Entry<String, BlockPos>> toClear = new ArrayList<>();
            List<String[]> rekeys = new ArrayList<>(); // oldKey -> newKey

            for (Map.Entry<String, BlockPos> entry : entries) {
                String key = entry.getKey();
                BlockPos pos = entry.getValue();

                var state = level.getBlockState(pos);
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    if (be.getLevel() != level) {
                        be.setLevel(level);
                    }
                    @SuppressWarnings("unchecked")
                    BlockEntityTicker<BlockEntity> ticker =
                            state.getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
                    if (ticker != null) {
                        ticker.tick(level, pos, state, be);
                    }
                }

                boolean canClear = !VirtualContainerGuard.isActive(sp);
                boolean empty = (be != null) && VirtualWorldManager.isContainerEmpty(be);

                if (be != null && !empty) {
                    // 虚拟容器非空，确保一个对应物品被打上实例标签，并把 BE NBT 写回该物品
                    var inv = sp.getInventory();
                    ItemStack targetStack = null;

                    //  优先找 computeKeyFor 完全匹配当前 key 的物品
                    for (int i = 0; i < inv.items.size(); i++) {
                        var s = inv.items.get(i);
                        if (s.isEmpty()) continue;
                        if (VirtualWorldManager.computeKeyFor(s).equals(key)) {
                            targetStack = s;
                            break;
                        }
                    }
                    if (targetStack == null) {
                        for (int i = 0; i < inv.offhand.size(); i++) {
                            var s = inv.offhand.get(i);
                            if (s.isEmpty()) continue;
                            if (VirtualWorldManager.computeKeyFor(s).equals(key)) {
                                targetStack = s;
                                break;
                            }
                        }
                    }

                    // 若没找到且 key 仍是无实例（#noinst），从同种方块里找一个“未打标签”的物品来绑定
                    boolean isNoInst = key.endsWith("#noinst");
                    if (targetStack == null && isNoInst) {
                        for (int i = 0; i < inv.items.size(); i++) {
                            var s = inv.items.get(i);
                            if (s.isEmpty()) continue;
                            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == state.getBlock()) {
                                var t = s.getTag();
                                if (t == null || !t.contains("i3_instance")) {
                                    targetStack = s;
                                    break;
                                }
                            }
                        }
                        if (targetStack == null) {
                            for (int i = 0; i < inv.offhand.size(); i++) {
                                var s = inv.offhand.get(i);
                                if (s.isEmpty()) continue;
                                if (s.getItem() instanceof BlockItem bi && bi.getBlock() == state.getBlock()) {
                                    var t = s.getTag();
                                    if (t == null || !t.contains("i3_instance")) {
                                        targetStack = s;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (targetStack != null) {
                        boolean newlyTagged = Utils.ensureInstanceTag(targetStack); // 若原为 #noinst，这里会生成 UUID
                        Utils.writeBlockEntityTagToItem(be, targetStack);
                        inv.setChanged();

                        if (newlyTagged) {
                            String newKey = VirtualWorldManager.computeKeyFor(targetStack);
                            if (!newKey.equals(key)) {
                                rekeys.add(new String[]{key, newKey});
                            }
                        }
                    }
                    continue;
                }

                if (canClear && empty) {
                    toClear.add(entry);
                }
            }

            for (Map.Entry<String, BlockPos> entry : toClear) {
                String key = entry.getKey();
                VirtualWorldManager.clearInstanceTagFromInventory(sp, key);
            }

            // 批量重映射 key -> pos，保持位置绑定持续有效
            for (String[] rk : rekeys) {
                String oldKey = rk[0], newKey = rk[1];
                BlockPos p = ctx.keyToPos.remove(oldKey);
                if (p != null) {
                    ctx.keyToPos.put(newKey, p);
                }
            }

            sp.containerMenu.broadcastChanges();
        }
    }

    private static Iterable<ServerPlayer> spIterable() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayers() : Collections.emptyList();
    }

    private static VirtualWorldManager.Context getContext(ServerPlayer sp) {
        return VirtualWorldManager.getContext(sp);
    }
}