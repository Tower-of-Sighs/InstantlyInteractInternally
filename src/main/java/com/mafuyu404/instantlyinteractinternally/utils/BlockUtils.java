package com.mafuyu404.instantlyinteractinternally.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BlockUtils {
    public static InteractionResult interactBlockFromItem(ItemStack itemStack, Player player) {
        if (itemStack.getItem() instanceof BlockItem blockItem) {
            if (player.hasContainerOpen()) player.closeContainer();
            Block block = blockItem.getBlock();
            BlockState state = block.defaultBlockState();
            BlockPos pos = player.blockPosition();
            Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
            InteractionResult interactionResult = block.use(block.defaultBlockState(), player.level(), pos, player, InteractionHand.MAIN_HAND, hitResult);
            if (block instanceof BaseEntityBlock entityBlock) {
                player.openMenu((MenuProvider) entityBlock.newBlockEntity(pos, state));
            }
            return interactionResult;
        }
        return InteractionResult.FAIL;
    }
}
