package com.mafuyu404.instantlyinteractinternally.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class DefaultKeyStrategy implements KeyStrategy {
    public static final DefaultKeyStrategy INSTANCE = new DefaultKeyStrategy();

    private DefaultKeyStrategy() {
    }

    /**
     * 计算“实例敏感”的 key：
     * <p>
     * - 若物品为 BlockItem 且带有 BE NBT，则对 NBT 文本序列做 hash 以区分实例；
     * <p>
     * - 若无 NBT，则以 “block_id#noinst” 作为基类键；
     * <p>
     * - 非 BlockItem 则退化为物品 toString。
     * <p>
     * 注意：字符串哈希用于在多数场景下稳定区分，但仍可能产生碰撞；若附属模组对碰撞敏感，请替换全局 KeyStrategy。
     */
    @Override
    public String computeKey(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
            CompoundTag beTag = BlockItem.getBlockEntityData(stack);
            if (beTag != null && !beTag.isEmpty()) {
                int h = beTag.toString().hashCode();
                String hex = Integer.toHexString(h);
                return id + "#nbt:" + hex;
            }
            return id + "#noinst";
        }
        return stack.getItem().toString();
    }

    /**
     * 计算“基类” key：不考虑 NBT，仅由方块注册名给出稳定基类键。
     * <p>
     * 非 BlockItem 则退化为物品 toString。
     */
    @Override
    public String computeBaseKey(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
            return id + "#noinst";
        }
        return stack.getItem().toString();
    }
}