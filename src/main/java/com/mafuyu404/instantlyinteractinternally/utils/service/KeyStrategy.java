package com.mafuyu404.instantlyinteractinternally.utils.service;

import net.minecraft.world.item.ItemStack;

public interface KeyStrategy {
    String computeKey(ItemStack stack);

    String computeBaseKey(ItemStack stack);
}