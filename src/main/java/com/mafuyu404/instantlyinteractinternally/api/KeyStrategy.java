package com.mafuyu404.instantlyinteractinternally.api;

import net.minecraft.world.item.ItemStack;

public interface KeyStrategy {
    /**
     * 计算实例敏感的键：
     * <p>
     * - 需要在可能存在不同 NBT 的同类型方块物品之间作区分；
     */
    String computeKey(ItemStack stack);

    /**
     * 计算基类键：
     * <p>
     * - 忽略具体 NBT，代表该类方块的通用键；
     * <p>
     * - 常用于聚合或在没有具体实例信息时的回退路径。
     */
    String computeBaseKey(ItemStack stack);
}