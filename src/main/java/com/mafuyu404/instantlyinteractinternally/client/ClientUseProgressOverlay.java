package com.mafuyu404.instantlyinteractinternally.client;

import com.mafuyu404.instantlyinteractinternally.Instantlyinteractinternally;
import com.mafuyu404.instantlyinteractinternally.utils.Config;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = Instantlyinteractinternally.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientUseProgressOverlay {
    private static boolean active = false;
    private static long startMillis = 0L;
    private static int durationMs = 0;

    private ClientUseProgressOverlay() {
    }

    public static void start(int durationTicks) {
        active = true;
        int dt = Math.max(1, durationTicks);
        durationMs = dt * 50;
        startMillis = Util.getMillis();
    }

    public static void end() {
        active = false;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post evt) {
        if (!active) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = Util.getMillis();
        int elapsedMs = (int) Math.max(0, now - startMillis);
        float progress = Mth.clamp(elapsedMs / (float) durationMs, 0f, 1f);

        int sw = evt.getWindow().getGuiScaledWidth();
        int sh = evt.getWindow().getGuiScaledHeight();

        float cx = (float) (sw * Config.PROGRESS_X_RATIO.get());
        float cy = (float) (sh * Config.PROGRESS_Y_RATIO.get());

        int outerR = Config.PROGRESS_OUTER_RADIUS.get();
        int thickness = Config.PROGRESS_THICKNESS.get();
        int innerR = Math.max(1, outerR - thickness);

        int startARGB = Config.PROGRESS_START_COLOR.get().intValue();
        int endARGB = Config.PROGRESS_END_COLOR.get().intValue();

        drawRingProgress(cx, cy, outerR, innerR, progress, startARGB, endARGB);

        if (Config.PROGRESS_SHOW_COUNTDOWN.get()) {
            int remainMs = Math.max(0, durationMs - elapsedMs);
            String text = String.format(Locale.ROOT, "%.1f", remainMs / 1000f);

            GuiGraphics gg = evt.getGuiGraphics();
            Font font = mc.font;

            int tw = font.width(text);
            int th = font.lineHeight;

            gg.drawString(
                    font,
                    text,
                    (int) (cx - tw / 2f),
                    (int) (cy - th / 2f + th * 0.25f),
                    0xFFFFFFFF,
                    true
            );
        }

        if (progress >= 1f) {
            active = false;
        }
    }

    private static void drawRingProgress(float cx, float cy, float outerR, float innerR, float progress, int startARGB, int endARGB) {
        float sweep = 360f * progress;
        float startAngle = -90f;

        int segments = Math.max(96, (int) (progress * 192));

        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float a = startAngle + sweep * t;

            int c = lerpARGB(startARGB, endARGB, t);

            float cos = Mth.cos(a * Mth.DEG_TO_RAD);
            float sin = Mth.sin(a * Mth.DEG_TO_RAD);

            float xOuter = cx + cos * outerR;
            float yOuter = cy + sin * outerR;
            float xInner = cx + cos * innerR;
            float yInner = cy + sin * innerR;

            putVertex(buf, xOuter, yOuter, c);
            putVertex(buf, xInner, yInner, c);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    private static int lerpARGB(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (int) (((a >> 24) & 0xFF) * (1 - t) + ((b >> 24) & 0xFF) * t);
        int rr = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gg = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bb = (int) (((a & 0xFF) * (1 - t) + (b & 0xFF) * t));
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static void putVertex(BufferBuilder b, float x, float y, int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float bl = ((argb) & 0xFF) / 255f;
        float a = ((argb >> 24) & 0xFF) / 255f;
        b.vertex(x, y, 0).color(r, g, bl, a).endVertex();
    }
}