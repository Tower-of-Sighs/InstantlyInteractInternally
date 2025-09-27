package com.mafuyu404.instantlyinteractinternally.utils.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class Config {

    private Config() {
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_BLACKLIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_WHITELIST;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCK_WHITELIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("InstantlyInteractInternally server-side blacklist/whitelist controls")
         .push("controls");

        ENABLE_ITEM_BLACKLIST = b
                .comment("Enable item blacklist (disallow items in the list)")
                .define("enableItemBlacklist", false);

        ITEM_BLACKLIST = b
                .comment("Item blacklist: registry names (e.g. minecraft:diamond_sword)")
                .defineList("itemBlacklist", List.of(), o -> o instanceof String);

        ENABLE_BLOCK_BLACKLIST = b
                .comment("Enable block blacklist (disallow blocks in the list)")
                .define("enableBlockBlacklist", false);

        BLOCK_BLACKLIST = b
                .comment("Block blacklist: registry names (e.g. minecraft:chest)")
                .defineList("blockBlacklist", List.of(), o -> o instanceof String);

        ENABLE_ITEM_WHITELIST = b
                .comment("Enable item whitelist (only allow items in the list)")
                .define("enableItemWhitelist", false);

        ITEM_WHITELIST = b
                .comment("Item whitelist: registry names (e.g. minecraft:bread)")
                .defineList("itemWhitelist", List.of(), o -> o instanceof String);

        ENABLE_BLOCK_WHITELIST = b
                .comment("Enable block whitelist (only allow blocks in the list)")
                .define("enableBlockWhitelist", false);

        BLOCK_WHITELIST = b
                .comment("Block whitelist: registry names (e.g. minecraft:chest)")
                .defineList("blockWhitelist", List.of(), o -> o instanceof String);

        b.pop();

        SPEC = b.build();
    }

    public static boolean isItemAllowed(ResourceLocation itemId) {
        if (itemId == null) return true;

        if (ENABLE_ITEM_BLACKLIST.get() && contains(ITEM_BLACKLIST.get(), itemId)) {
            return false;
        }
        return !ENABLE_ITEM_WHITELIST.get() || contains(ITEM_WHITELIST.get(), itemId);
    }

    public static boolean isBlockAllowed(ResourceLocation blockId) {
        if (blockId == null) return true;

        if (ENABLE_BLOCK_BLACKLIST.get() && contains(BLOCK_BLACKLIST.get(), blockId)) {
            return false;
        }
        return !ENABLE_BLOCK_WHITELIST.get() || contains(BLOCK_WHITELIST.get(), blockId);
    }

    private static boolean contains(List<? extends String> list, ResourceLocation id) {
        if (list == null || list.isEmpty() || id == null) return false;
        String key = id.toString();
        for (String s : list) {
            if (key.equals(s)) return true;
        }
        return false;
    }
}