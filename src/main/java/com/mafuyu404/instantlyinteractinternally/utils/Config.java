package com.mafuyu404.instantlyinteractinternally.utils;

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

    public static final ForgeConfigSpec.BooleanValue ENABLE_USE_PROGRESS;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_X_RATIO;
    public static final ForgeConfigSpec.DoubleValue PROGRESS_Y_RATIO;
    public static final ForgeConfigSpec.IntValue PROGRESS_OUTER_RADIUS;
    public static final ForgeConfigSpec.IntValue PROGRESS_THICKNESS;
    public static final ForgeConfigSpec.LongValue PROGRESS_START_COLOR;
    public static final ForgeConfigSpec.LongValue PROGRESS_END_COLOR;
    public static final ForgeConfigSpec.BooleanValue PROGRESS_SHOW_COUNTDOWN;

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

        b.comment("Use progress behavior")
                .push("use_progress");

        ENABLE_USE_PROGRESS = b
                .comment("Enable vanilla-time use progress for edible/drinkable items (uninterrupted by closing screens)")
                .define("enableUseProgress", true);

        PROGRESS_X_RATIO = b
                .comment("Progress ring X position ratio (0.0~1.0)")
                .defineInRange("xRatio", 0.5D, 0.0D, 1.0D);

        PROGRESS_Y_RATIO = b
                .comment("Progress ring Y position ratio (0.0~1.0)")
                .defineInRange("yRatio", 0.76D, 0.0D, 1.0D);

        PROGRESS_OUTER_RADIUS = b
                .comment("Progress ring outer radius (pixels)")
                .defineInRange("outerRadius", 16, 6, 64);

        PROGRESS_THICKNESS = b
                .comment("Progress ring thickness (pixels)")
                .defineInRange("thickness", 5, 2, 32);

        PROGRESS_START_COLOR = b
                .comment("Progress ring start color (ARGB integer)")
                .defineInRange("startColorARGB", 0xCC66CCFFL, 0x00000000L, 0xFFFFFFFFL);

        PROGRESS_END_COLOR = b
                .comment("Progress ring end color (ARGB integer)")
                .defineInRange("endColorARGB", 0xCC66FF66L, 0x00000000L, 0xFFFFFFFFL);

        PROGRESS_SHOW_COUNTDOWN = b
                .comment("Show centered countdown seconds")
                .define("showCountdown", true);

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