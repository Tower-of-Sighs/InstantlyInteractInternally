package com.mafuyu404.instantlyinteractinternally.utils.service;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class ContainerHelper {
    private ContainerHelper() {
    }

    public static boolean isContainerEmpty(BlockEntity be) {
        if (be instanceof Container c) {
            return c.isEmpty();
        }
        var opt = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        return opt.map(handler -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (!handler.getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        }).orElse(true);
    }

    public static boolean isContainerLike(BlockEntity be) {
        if (be instanceof Container) return true;
        return hasItemHandler(be);
    }

    public static boolean hasItemHandler(BlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }
}