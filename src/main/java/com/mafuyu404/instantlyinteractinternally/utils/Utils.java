package com.mafuyu404.instantlyinteractinternally.utils;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.service.ContainerHelper;
import com.mafuyu404.instantlyinteractinternally.utils.service.SessionService;
import com.mafuyu404.instantlyinteractinternally.utils.service.WorldContextRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public class Utils {
    public static void interactBlockInSandbox(ItemStack stack, ServerPlayer player) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        Instantlyinteractinternally.LOGGER.debug("[I3I] interactBlockInSandbox begin: player={}, item={}, count={}",
                player.getGameProfile().getName(), stack.getItem(), stack.getCount());

        if (stack.getCount() > 1) {
            player.displayClientMessage(Component.translatable("iii.open_multiple_block_denied").withStyle(ChatFormatting.RED), true);
            Instantlyinteractinternally.LOGGER.info("[I3I] deny open: stacked container item. player={}, item={}, count={}",
                    player.getGameProfile().getName(), stack.getItem(), stack.getCount());
            return;
        }
        FakeLevel fake = WorldContextRegistry.getOrCreateLevel(player);

        SessionService.flushActiveSession(player);

        String sessionId = java.util.UUID.randomUUID().toString();
        BlockPos pos = SessionService.ensurePosForSession(player, sessionId);
        Instantlyinteractinternally.LOGGER.debug("[I3I] session allocated: player={}, session={}, pos={}", player.getGameProfile().getName(), sessionId, pos);

        BlockState target = blockItem.getBlock().defaultBlockState();
        if (fake.getBlockState(pos).getBlock() != target.getBlock()) {
            fake.putBlock(pos, target);
            Instantlyinteractinternally.LOGGER.debug("[I3I] fake block placed: player={}, session={}, state={}", player.getGameProfile().getName(), sessionId, target);
        }

        var be = fake.getBlockEntity(pos);
        if (be != null
                && ContainerHelper.isContainerEmpty(be)
                && ContainerHelper.isContainerLike(be)) {
            Instantlyinteractinternally.LOGGER.debug("[I3I] applying Item->BE tag: player={}, session={}, pos={}", player.getGameProfile().getName(), sessionId, pos);
            applyBlockEntityTagToBE(stack, be, pos);
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
            Instantlyinteractinternally.LOGGER.debug("[I3I] no MenuProvider, calling block.use. player={}, session={}", player.getGameProfile().getName(), sessionId);
            InteractionResult result = target.use(fake, player, InteractionHand.MAIN_HAND, traceResult);
            return;
        }

        stack.getOrCreateTag().putString("i3_session", sessionId);

        SessionService.beginSession(player, sessionId, pos);
        Instantlyinteractinternally.LOGGER.info("[I3I] opening menu: player={}, session={}, pos={}", player.getGameProfile().getName(), sessionId, pos);
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

    public static void clearPendingBind(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return;
        tag.remove("i3_pending_bind");
        tag.remove("i3_pending_key");
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static void writeBlockEntityTagToItem(BlockEntity be, ItemStack stack) {
        CompoundTag full = be.saveWithFullMetadata();
        if (full == null) return;

        full.remove("x");
        full.remove("y");
        full.remove("z");

        stack.getOrCreateTag().put("BlockEntityTag", full);
    }

    public static void applyBlockEntityTagToBE(ItemStack stack, BlockEntity be, BlockPos pos) {
        CompoundTag beTag = BlockItem.getBlockEntityData(stack);
        if (beTag == null) return;
        CompoundTag copy = beTag.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        be.load(copy);
        be.setChanged();

        clearBlockEntityTag(stack);
    }

    public static void clearBlockEntityTag(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return;
        tag.remove("BlockEntityTag");
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }
}