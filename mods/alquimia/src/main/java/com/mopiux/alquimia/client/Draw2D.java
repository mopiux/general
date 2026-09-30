package com.mopiux.alquimia.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Primitivas 2D (círculos, anillos, líneas con grosor) dibujadas con triángulos. GuiGraphics solo
 * sabe dibujar rectángulos, y el mapa alquímico necesita curvas.
 */
public final class Draw2D {
    private Draw2D() {
    }

    public static BufferBuilder begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    public static void end(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        alphaBlend();
    }

    /** Activa la mezcla alfa estándar (necesaria para dibujar íconos con transparencia). */
    public static void alphaBlend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int argb) {
        b.vertex(m, x, y, 0f).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    public static void tri(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        v(b, m, x1, y1, argb);
        v(b, m, x2, y2, argb);
        v(b, m, x3, y3, argb);
    }

    public static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2,
                            float x3, float y3, int argb) {
        tri(b, m, x0, y0, x1, y1, x2, y2, argb);
        tri(b, m, x0, y0, x2, y2, x3, y3, argb);
    }

    public static void rect(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        quad(b, m, x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    public static void line(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * width / 2f, ny = dx / len * width / 2f;
        quad(b, m, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    /** Línea discontinua; {@code phase} desplaza el patrón (para animarlo). */
    public static void dashedLine(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float width,
                                  float dash, float gap, float phase, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float ux = dx / len, uy = dy / len;
        float period = dash + gap;
        float t = -(phase % period);
        while (t < len) {
            float a = Math.max(0, t), c = Math.min(len, t + dash);
            if (c > a) line(b, m, x0 + ux * a, y0 + uy * a, x0 + ux * c, y0 + uy * c, width, argb);
            t += period;
        }
    }

    public static int segmentsFor(float radius) {
        return Math.max(12, Math.min(64, (int) (radius * 1.2f)));
    }

    public static void disc(BufferBuilder b, Matrix4f m, float cx, float cy, float r, int argb) {
        int seg = segmentsFor(r);
        float px = cx + r, py = cy;
        for (int i = 1; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            float nx = cx + (float) Math.cos(a) * r, ny = cy + (float) Math.sin(a) * r;
            tri(b, m, cx, cy, px, py, nx, ny, argb);
            px = nx;
            py = ny;
        }
    }

    public static void ring(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float width, int argb) {
        int seg = segmentsFor(r);
        float ri = Math.max(0, r - width / 2f), ro = r + width / 2f;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(b, m, cx + c0 * ri, cy + s0 * ri, cx + c0 * ro, cy + s0 * ro, cx + c1 * ro, cy + s1 * ro,
                    cx + c1 * ri, cy + s1 * ri, argb);
        }
    }

    public static void dashedRing(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float width, int dashes, int argb) {
        int seg = Math.max(dashes * 2, segmentsFor(r));
        seg -= seg % (dashes * 2);
        float ri = Math.max(0, r - width / 2f), ro = r + width / 2f;
        int per = seg / (dashes * 2);
        for (int i = 0; i < seg; i++) {
            if ((i / per) % 2 == 1) continue;
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(b, m, cx + c0 * ri, cy + s0 * ri, cx + c0 * ro, cy + s0 * ro, cx + c1 * ro, cy + s1 * ro,
                    cx + c1 * ri, cy + s1 * ri, argb);
        }
    }

    /** Punta de flecha en (x, y) apuntando en la dirección (dx, dy). */
    public static void arrowHead(BufferBuilder b, Matrix4f m, float x, float y, float dx, float dy, float size, int argb) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float ux = dx / len, uy = dy / len;
        float bx = x - ux * size, by = y - uy * size;
        float nx = -uy * size * 0.6f, ny = ux * size * 0.6f;
        tri(b, m, x, y, bx + nx, by + ny, bx - nx, by - ny, argb);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    public static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * (1 - f));
        int g = (int) (((rgb >> 8) & 0xFF) * (1 - f));
        int bl = (int) ((rgb & 0xFF) * (1 - f));
        return (rgb & 0xFF000000) | r << 16 | g << 8 | bl;
    }
}
