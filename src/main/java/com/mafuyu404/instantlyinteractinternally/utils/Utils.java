package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class Utils {
    public static void interactBlockInSandbox(ItemStack stack, ServerPlayer player) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        ensurePerInstanceTag(stack);

        FakeLevel fake = VirtualWorldManager.getOrCreateLevel(player);

        BlockPos pos = VirtualWorldManager.ensurePosNearPlayer(player, stack);

        BlockState target = blockItem.getBlock().defaultBlockState();
        if (fake.getBlockState(pos).getBlock() != target.getBlock()) {
            fake.putBlock(pos, target);
        }

        ClipContext clipContext = new ClipContext(
                player.position(),
                pos.getCenter(),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                null
        );
        BlockHitResult traceResult = fake.clip(clipContext);

        MenuProvider provider = target.getMenuProvider(fake, pos);
        if (provider == null) {
            InteractionResult result = target.use(fake, player, InteractionHand.MAIN_HAND, traceResult);
            return;
        }

        VirtualContainerGuard.begin(player);
        player.openMenu(provider);
    }

    public static void consumeItemInstant(Slot slot, ServerPlayer player) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) return;

        if (inSlot.isEdible()) {
            FoodProperties food = inSlot.getFoodProperties(player);
            boolean always = food != null && food.canAlwaysEat();
            if (!player.canEat(always)) {
                return;
            }
        } else if (!(inSlot.getItem() instanceof PotionItem)) {
            return;
        }

        var level = player.level();
        ItemStack one = inSlot.copy();
        one.setCount(1);

        ItemStack containerResult = one.finishUsingItem(level, player);

        inSlot.shrink(1);
        if (inSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.set(inSlot);
        }
        slot.setChanged();

        if (!containerResult.isEmpty()) {
            boolean added = player.getInventory().add(containerResult);
            if (!added) {
                player.drop(containerResult, false);
            }
        }

        player.containerMenu.broadcastChanges();
    }

    public static boolean useUsableItemInstant(Slot slot, ServerPlayer player) {
        if (player.level().isClientSide) return false;

        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) return false;

        Item item = inSlot.getItem();
        if (inSlot.isEdible() || (item instanceof PotionItem && !(item instanceof ThrowablePotionItem))) {
            return false;
        }

        var level = player.serverLevel();
        var hand = InteractionHand.MAIN_HAND;

        ItemStack one = inSlot.copy();
        one.setCount(1);

        ItemStack handBackup = player.getItemInHand(hand).copy();
        player.setItemInHand(hand, one);

        boolean consumed = false;
        ItemStack afterHand = ItemStack.EMPTY;

        try {
            InteractionResultHolder<ItemStack> useResult =
                    one.use(level, player, hand);

            if (useResult != null && useResult.getResult().consumesAction()) {
                consumed = true;
                afterHand = player.getItemInHand(hand).copy();
            } else {
                var hit = Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
                if (hit.getType() == HitResult.Type.BLOCK) {

                    var useOnResult = player.getItemInHand(hand).onItemUseFirst(
                            new UseOnContext(player, hand, hit)
                    );
                    if (useOnResult != InteractionResult.PASS && useOnResult.consumesAction()) {
                        consumed = true;
                        afterHand = player.getItemInHand(hand).copy();
                    } else {
                        var finalResult = player.getItemInHand(hand).useOn(
                                new UseOnContext(player, hand, hit)
                        );
                        if (finalResult.consumesAction()) {
                            consumed = true;
                            afterHand = player.getItemInHand(hand).copy();
                        }
                    }
                }
            }
        } finally {
            player.setItemInHand(hand, handBackup);
        }

        if (!consumed) {
            return false;
        }

        inSlot.shrink(1);
        if (inSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.set(inSlot);
        }
        slot.setChanged();

        if (afterHand != null && !afterHand.isEmpty()) {
            boolean added = player.getInventory().add(afterHand);
            if (!added) {
                player.drop(afterHand, false);
            }
        }

        player.swing(hand, true);
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static void ensurePerInstanceTag(ItemStack stack) {
        var tag = stack.getOrCreateTag();
        if (!tag.contains("i3_instance")) {
            tag.putUUID("i3_instance", UUID.randomUUID());
        }
    }

    public static void clearPerInstanceTag(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return;
        tag.remove("i3_instance");
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}