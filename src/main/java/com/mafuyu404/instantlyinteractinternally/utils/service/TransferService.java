package com.mafuyu404.instantlyinteractinternally.utils.service;

import com.mafuyu404.instantlyinteractinternally.api.FakeLevelAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TransferService {
    private TransferService() {
    }

    public static void transferFromFakeToReal(ServerPlayer player, ItemStack placedStack, ServerLevel realLevel, BlockPos realPos) {
        CompoundTag itemBeTag = BlockItem.getBlockEntityData(placedStack);
        if (itemBeTag != null && !itemBeTag.isEmpty()) {
            BlockState realState = realLevel.getBlockState(realPos);
            BlockEntity realBE = realLevel.getBlockEntity(realPos);
            if (realBE == null && realState.getBlock() instanceof BaseEntityBlock beb) {
                realBE = beb.newBlockEntity(realPos, realState);
                if (realBE != null) {
                    realLevel.setBlockEntity(realBE);
                }
            }
            if (realBE != null) {
                CompoundTag copy = itemBeTag.copy();
                copy.putInt("x", realPos.getX());
                copy.putInt("y", realPos.getY());
                copy.putInt("z", realPos.getZ());
                realBE.load(copy);
                realBE.setChanged();
                realLevel.sendBlockUpdated(realPos, realState, realState, 3);
            }
            return;
        }

        WorldContextRegistry.maybeLoadContext(player);
        var ctx = WorldContextRegistry.getContext(player);
        if (ctx == null) return;

        String key = FakeLevelAPI.computeKey(placedStack);
        BlockPos fakePos = FakeLevelAPI.resolveKeyPos(player, key);
        if (fakePos == null) return;

        var fakeState = ctx.level.getBlockState(fakePos);
        var realState = realLevel.getBlockState(realPos);
        if (fakeState.getBlock() != realState.getBlock()) return;

        var fakeBE = ctx.level.getBlockEntity(fakePos);
        if (fakeBE == null) return;

        CompoundTag tag = fakeBE.saveWithFullMetadata();
        if (tag == null) return;

        var realBE2 = realLevel.getBlockEntity(realPos);
        if (realBE2 == null && realState.getBlock() instanceof BaseEntityBlock beb2) {
            realBE2 = beb2.newBlockEntity(realPos, realState);
            if (realBE2 != null) {
                realLevel.setBlockEntity(realBE2);
            }
        }
        if (realBE2 == null) return;

        realBE2.load(tag);
        realBE2.setChanged();
        realLevel.sendBlockUpdated(realPos, realState, realState, 3);
    }
}