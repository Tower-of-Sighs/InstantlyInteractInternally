package com.mafuyu404.instantlyinteractinternally.utils.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class DefaultKeyStrategy implements KeyStrategy {
    public static final DefaultKeyStrategy INSTANCE = new DefaultKeyStrategy();

    private DefaultKeyStrategy() {
    }

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

    @Override
    public String computeBaseKey(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(bi.getBlock());
            return id + "#noinst";
        }
        return stack.getItem().toString();
    }
}