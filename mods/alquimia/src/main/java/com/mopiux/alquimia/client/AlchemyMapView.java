package com.mopiux.alquimia.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyMap;
import com.mopiux.alquimia.alchemy.BrewState;
import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import com.mopiux.alquimia.config.AlquimiaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja e interactúa con un mapa alquímico: fondo de pergamino, cuadrícula, zonas de esencia,
 * peligros, niebla de lo inexplorado, la estela de la mezcla, el camino pendiente y la vista
 * previa del próximo ingrediente. Se usa en el caldero y en el grimorio.
 */
public class AlchemyMapView {
    public static final ResourceLocation ICONS = Alquimia.id("textures/gui/icons.png");
    public static final ResourceLocation PAPER = Alquimia.id("textures/gui/parchment.png");
    public static final int ICONS_SIZE = 64;

    /** Camino que se dibuja como vista previa (ingrediente en la mano o bajo el cursor). */
    public record Preview(float startX, float startY, List<float[]> segments, boolean transform) {
    }

    public int x, y, w, h;
    public float viewX, viewY;
    public float zoom = 1f;
    public boolean follow = true;
    private boolean dragging;
    private double lastMouseX, lastMouseY;
    private int ticks;

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ------------------------------------------------------------------ transformaciones
    public float sx(float mapX) {
        return x + w / 2f + (mapX - viewX) * zoom;
    }

    public float sy(float mapY) {
        return y + h / 2f - (mapY - viewY) * zoom;
    }

    public float mapX(double screenX) {
        return (float) ((screenX - x - w / 2f) / zoom + viewX);
    }

    public float mapY(double screenY) {
        return (float) (-(screenY - y - h / 2f) / zoom + viewY);
    }

    public void centerOn(float mx, float my) {
        viewX = mx;
        viewY = my;
    }

    public void zoomBy(float factor) {
        zoom = Mth.clamp(zoom * factor, 0.35f, 4f);
    }

    public void tick(@Nullable BrewState brew) {
        ticks++;
        if (follow && brew != null) {
            if (reduceMotion()) {
                viewX = brew.x;
                viewY = brew.y;
            } else {
                viewX += (brew.x - viewX) * 0.3f;
                viewY += (brew.y - viewY) * 0.3f;
            }
        }
    }

    private static boolean reduceMotion() {
        return AlquimiaConfig.CLIENT_SPEC.isLoaded() && AlquimiaConfig.REDUCE_MOTION.get();
    }

    private static boolean highContrast() {
        return AlquimiaConfig.CLIENT_SPEC.isLoaded() && AlquimiaConfig.HIGH_CONTRAST.get();
    }

    private static boolean showLabels() {
        return !AlquimiaConfig.CLIENT_SPEC.isLoaded() || AlquimiaConfig.SHOW_ZONE_LABELS.get();
    }

    // ------------------------------------------------------------------ ratón
    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my) || button != 0) return false;
        dragging = true;
        lastMouseX = mx;
        lastMouseY = my;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging) return false;
        float dx = (float) (mx - lastMouseX), dy = (float) (my - lastMouseY);
        if (Math.abs(dx) + Math.abs(dy) > 0.5f) follow = false;
        viewX -= dx / zoom;
        viewY += dy / zoom;
        lastMouseX = mx;
        lastMouseY = my;
        return true;
    }

    public boolean mouseReleased() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        zoomBy(delta > 0 ? 1.25f : 0.8f);
        return true;
    }

    // ------------------------------------------------------------------ dibujo
    public void render(GuiGraphics g, AlchemyMap map, PlayerKnowledge k, @Nullable BrewState brew,
                       @Nullable Preview preview, boolean stirring, float partial) {
        boolean hc = highContrast();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int ink = hc ? 0xFFF2F2F2 : 0xFF3A2816;

        g.flush();
        g.enableScissor(x, y, x + w, y + h);
        if (hc) {
            g.fill(x, y, x + w, y + h, 0xFF15151D);
        } else {
            int ox = Math.floorMod(Math.round(-viewX * zoom), 64);
            int oy = Math.floorMod(Math.round(viewY * zoom), 64);
            for (int ty = y - 64 + oy; ty < y + h; ty += 64) {
                for (int tx = x - 64 + ox; tx < x + w; tx += 64) {
                    g.blit(PAPER, tx, ty, 0f, 0f, 64, 64, 64, 64);
                }
            }
        }
        g.flush();

        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Draw2D.begin();

        // Cuadrícula (cada 20 unidades) y ejes
        float minX = mapX(x), maxX = mapX(x + w), minY = mapY(y + h), maxY = mapY(y);
        float step = zoom < 0.6f ? 40f : 20f;
        int grid = hc ? 0x26FFFFFF : 0x223A2816;
        int axis = hc ? 0x55FFFFFF : 0x443A2816;
        for (float gx = (float) Math.floor(minX / step) * step; gx <= maxX; gx += step) {
            float s = sx(gx);
            Draw2D.line(b, m, s, y, s, y + h, gx == 0 ? 1.2f : 0.8f, gx == 0 ? axis : grid);
        }
        for (float gy = (float) Math.floor(minY / step) * step; gy <= maxY; gy += step) {
            float s = sy(gy);
            Draw2D.line(b, m, x, s, x + w, s, gy == 0 ? 1.2f : 0.8f, gy == 0 ? axis : grid);
        }

        // Zonas de esencia
        for (AlchemyMap.Zone z : map.zones()) {
            float zx = sx(z.x()), zy = sy(z.y()), zr = z.radius() * zoom;
            if (!onScreen(zx, zy, zr)) continue;
            boolean discovered = k.isDiscovered(map.id(), z.effect());
            if (discovered) {
                int col = effectColor(z);
                Draw2D.disc(b, m, zx, zy, zr, Draw2D.withAlpha(col, hc ? 0x8C : 0x5A));
                Draw2D.ring(b, m, zx, zy, zr, hc ? 2f : 1.4f, Draw2D.withAlpha(Draw2D.darken(col, 0.35f), 0xFF));
                Draw2D.dashedRing(b, m, zx, zy, zr * 0.6f, 0.9f, 8, Draw2D.withAlpha(Draw2D.darken(col, 0.45f), 0xC0));
                Draw2D.ring(b, m, zx, zy, zr * 0.25f, 0.9f, Draw2D.withAlpha(Draw2D.darken(col, 0.55f), 0xD0));
            } else if (isRevealedArea(map, k, z.x(), z.y(), z.radius())) {
                Draw2D.dashedRing(b, m, zx, zy, zr, 1.2f, 10, Draw2D.withAlpha(ink, 0xA0));
            }
        }

        // Zonas de peligro
        for (AlchemyMap.Hazard hz : map.hazards()) {
            float hx = sx(hz.x()), hy = sy(hz.y()), hr = hz.radius() * zoom;
            if (!onScreen(hx, hy, hr) || !isRevealedArea(map, k, hz.x(), hz.y(), hz.radius())) continue;
            Draw2D.disc(b, m, hx, hy, hr, hc ? 0xA0D01818 : 0x66781414);
            // rayado diagonal para distinguir el peligro sin depender del color
            for (int i = -3; i <= 3; i++) {
                float off = i * hr / 3.5f;
                float half = (float) Math.sqrt(Math.max(0, hr * hr - off * off));
                float c = 0.7071f;
                float ax = hx + off * c, ay = hy - off * c;
                Draw2D.line(b, m, ax - half * c, ay - half * c, ax + half * c, ay + half * c, 0.8f,
                        hc ? 0xC0FFFFFF : 0x996A1010);
            }
            Draw2D.ring(b, m, hx, hy, hr, hc ? 2f : 1.5f, hc ? 0xFFFF4040 : 0xFF5E0E0E);
        }

        // Niebla de lo inexplorado (celdas pixeladas, como un mapa de Minecraft)
        int n = map.fogSize();
        float cell = AlchemyMap.FOG_CELL * zoom;
        int cx0 = Math.max(0, (int) Math.floor((minX + map.radius()) / AlchemyMap.FOG_CELL));
        int cx1 = Math.min(n - 1, (int) Math.floor((maxX + map.radius()) / AlchemyMap.FOG_CELL));
        int cy0 = Math.max(0, (int) Math.floor((minY + map.radius()) / AlchemyMap.FOG_CELL));
        int cy1 = Math.min(n - 1, (int) Math.floor((maxY + map.radius()) / AlchemyMap.FOG_CELL));
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (k.isRevealed(map, cx, cy)) continue;
                float wx = cx * AlchemyMap.FOG_CELL - map.radius();
                float wy = cy * AlchemyMap.FOG_CELL - map.radius();
                float s0 = sx(wx), t0 = sy(wy + AlchemyMap.FOG_CELL);
                int col = hc ? 0xF0000000 : (((cx + cy) & 1) == 0 ? 0xF2B7A174 : 0xF2B09A6C);
                Draw2D.rect(b, m, s0, t0, s0 + cell, t0 + cell, col);
            }
        }

        // Borde del mapa (doble línea, como los mapas antiguos)
        Draw2D.ring(b, m, sx(0), sy(0), map.radius() * zoom, hc ? 2.5f : 2f, ink);
        Draw2D.ring(b, m, sx(0), sy(0), map.radius() * zoom + 3.5f, 0.8f, Draw2D.withAlpha(ink, 0x90));

        if (brew != null) {
            // Estela de lo ya recorrido
            int trail = hc ? 0x90FFFFFF : 0x996B4A2A;
            for (int i = 0; i < brew.trail.size(); i += 1) {
                float[] p = brew.trail.get(i);
                if (i % 2 == 0) Draw2D.disc(b, m, sx(p[0]), sy(p[1]), Math.max(0.7f, 0.6f * zoom), trail);
            }
            // Camino pendiente
            if (!brew.pending.isEmpty()) {
                float px = brew.x, py = brew.y;
                float lastDx = 0, lastDy = 0;
                for (float[] s : brew.pending) {
                    float nx = px + s[0], ny = py + s[1];
                    boolean danger = crossesKnownHazard(map, k, px, py, nx, ny);
                    int col = danger ? (hc ? 0xFFFF3030 : 0xFFB01818) : (hc ? 0xFFFFE040 : 0xFF2A1A0C);
                    Draw2D.line(b, m, sx(px), sy(py), sx(nx), sy(ny), hc ? 2.6f : 1.8f, col);
                    lastDx = s[0];
                    lastDy = s[1];
                    px = nx;
                    py = ny;
                }
                Draw2D.arrowHead(b, m, sx(px), sy(py), lastDx, -lastDy, 5f, hc ? 0xFFFFE040 : 0xFF2A1A0C);
            }
        }

        // Vista previa del ingrediente
        if (preview != null && !preview.segments().isEmpty()) {
            float px = preview.startX(), py = preview.startY();
            float phase = reduceMotion() ? 0 : ticks + partial;
            float lastDx = 0, lastDy = 0;
            for (float[] s : preview.segments()) {
                float nx = px + s[0], ny = py + s[1];
                boolean danger = crossesKnownHazard(map, k, px, py, nx, ny);
                int col = danger ? 0xFFE02020 : (hc ? 0xFF40E0FF : 0xFF1F4FA8);
                Draw2D.dashedLine(b, m, sx(px), sy(py), sx(nx), sy(ny), hc ? 2.2f : 1.5f, 4f, 3f, phase * 0.5f, col);
                lastDx = s[0];
                lastDy = s[1];
                px = nx;
                py = ny;
            }
            Draw2D.arrowHead(b, m, sx(px), sy(py), lastDx, -lastDy, 4.5f, hc ? 0xFF40E0FF : 0xFF1F4FA8);
        }

        // Halo de la mezcla
        if (brew != null) {
            float cx = sx(brew.x), cy = sy(brew.y);
            if (stirring && !reduceMotion()) {
                float pulse = ((ticks + partial) % 20f) / 20f;
                Draw2D.ring(b, m, cx, cy, 5f + pulse * 9f, 1.2f, Draw2D.withAlpha(ink, (int) (200 * (1 - pulse))));
            }
            Draw2D.disc(b, m, cx, cy, 7f, hc ? 0x60FFFF00 : 0x40FFFFFF);
        }
        Draw2D.end(b);

        // Íconos
        Draw2D.alphaBlend();
        for (AlchemyMap.Zone z : map.zones()) {
            float zx = sx(z.x()), zy = sy(z.y()), zr = z.radius() * zoom;
            if (!onScreen(zx, zy, zr)) continue;
            if (k.isDiscovered(map.id(), z.effect())) {
                MobEffect effect = z.mobEffect();
                if (effect == null) continue;
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
                int size = Mth.clamp((int) (zr * 1.3f), 9, 18);
                g.blit(Math.round(zx - size / 2f), Math.round(zy - size / 2f), 0, size, size, sprite);
            } else if (isRevealedArea(map, k, z.x(), z.y(), z.radius())) {
                int size = Mth.clamp(Math.round(zr * 1.3f), 6, 10);
                g.blit(ICONS, Math.round(zx - size / 2f), Math.round(zy - size / 2f), size, size, 12f, 12f, 10, 10, ICONS_SIZE, ICONS_SIZE);
            }
        }
        for (AlchemyMap.Hazard hz : map.hazards()) {
            float hx = sx(hz.x()), hy = sy(hz.y());
            if (!onScreen(hx, hy, hz.radius() * zoom) || !isRevealedArea(map, k, hz.x(), hz.y(), hz.radius())) continue;
            int size = Mth.clamp(Math.round(hz.radius() * zoom * 1.5f), 6, 12);
            g.blit(ICONS, Math.round(hx - size / 2f), Math.round(hy - size / 2f), size, size, 0f, 0f, 12, 12, ICONS_SIZE, ICONS_SIZE);
        }
        g.blit(ICONS, Math.round(sx(0) - 5), Math.round(sy(0) - 5), 0f, 12f, 10, 10, ICONS_SIZE, ICONS_SIZE);
        if (brew != null) {
            g.blit(ICONS, Math.round(sx(brew.x) - 6), Math.round(sy(brew.y) - 7), 12f, 0f, 12, 12, ICONS_SIZE, ICONS_SIZE);
        }

        // Nombres de las esencias
        if (showLabels() && zoom >= 0.75f) {
            for (AlchemyMap.Zone z : map.zones()) {
                if (!k.isDiscovered(map.id(), z.effect())) continue;
                MobEffect effect = z.mobEffect();
                if (effect == null) continue;
                float zx = sx(z.x()), zy = sy(z.y()), zr = z.radius() * zoom;
                if (!onScreen(zx, zy, zr + 20)) continue;
                g.pose().pushPose();
                float scale = 0.5f;
                Component name = effect.getDisplayName();
                int tw = font.width(name);
                g.pose().translate(zx, zy + Math.max(zr, 7) + 2, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, name, -tw / 2, 0, hc ? 0xFFFFFFFF : 0xFF2A1A0C, hc);
                g.pose().popPose();
            }
        }
        g.flush();
        g.disableScissor();
    }

    private boolean onScreen(float sx, float sy, float r) {
        return sx + r >= x && sx - r <= x + w && sy + r >= y && sy - r <= y + h;
    }

    private static int effectColor(AlchemyMap.Zone z) {
        MobEffect e = z.mobEffect();
        return e == null ? 0x888888 : e.getColor();
    }

    /** ¿El jugador ya vio alguna parte de este círculo? */
    public static boolean isRevealedArea(AlchemyMap map, PlayerKnowledge k, float cx, float cy, float r) {
        if (k.isRevealed(map, cx, cy)) return true;
        float d = r * 0.7f;
        return k.isRevealed(map, cx + d, cy) || k.isRevealed(map, cx - d, cy)
                || k.isRevealed(map, cx, cy + d) || k.isRevealed(map, cx, cy - d);
    }

    /** ¿El segmento cruza un peligro que el jugador ya conoce? */
    public static boolean crossesKnownHazard(AlchemyMap map, PlayerKnowledge k, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float px = x0 + dx * t, py = y0 + dy * t;
            AlchemyMap.Hazard hz = map.hazardAt(px, py);
            if (hz != null && isRevealedArea(map, k, hz.x(), hz.y(), hz.radius())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tooltips
    public List<Component> tooltip(AlchemyMap map, PlayerKnowledge k, @Nullable BrewState brew, double mouseX, double mouseY) {
        List<Component> out = new ArrayList<>();
        if (!contains(mouseX, mouseY)) return out;
        float mx = mapX(mouseX), my = mapY(mouseY);
        if (brew != null) {
            float dx = sx(brew.x) - (float) mouseX, dy = sy(brew.y) - (float) mouseY;
            if (dx * dx + dy * dy < 36) {
                out.add(Component.translatable("gui.alquimia.map.mixture").withStyle(ChatFormatting.GOLD));
                AlchemyMap.Zone z = map.zoneAt(brew.x, brew.y);
                if (z != null && z.mobEffect() != null) {
                    out.add(Component.translatable("gui.alquimia.map.over", z.mobEffect().getDisplayName(),
                            Component.translatable("enchantment.level." + z.tierAt(brew.x, brew.y))).withStyle(ChatFormatting.GRAY));
                }
                out.add(Component.translatable("gui.alquimia.map.pending", String.format("%.1f", brew.pendingLength()))
                        .withStyle(ChatFormatting.DARK_GRAY));
                return out;
            }
        }
        for (AlchemyMap.Hazard hz : map.hazards()) {
            if (hz.contains(mx, my) && isRevealedArea(map, k, hz.x(), hz.y(), hz.radius())) {
                out.add(Component.translatable("gui.alquimia.map.hazard").withStyle(ChatFormatting.RED));
                out.add(Component.translatable("gui.alquimia.map.hazard.desc").withStyle(ChatFormatting.GRAY));
                return out;
            }
        }
        for (AlchemyMap.Zone z : map.zones()) {
            if (!z.contains(mx, my)) continue;
            if (k.isDiscovered(map.id(), z.effect()) && z.mobEffect() != null) {
                MobEffect e = z.mobEffect();
                out.add(e.getDisplayName().copy().withStyle(s -> s.withColor(TextColor.fromRgb(e.getColor()))));
                out.add(Component.translatable("gui.alquimia.map.purity_here",
                        Component.translatable("enchantment.level." + z.tierAt(mx, my))).withStyle(ChatFormatting.GRAY));
                if (!e.isInstantenous()) {
                    out.add(Component.translatable("gui.alquimia.map.duration", StringUtil.formatTickDuration(z.duration()))
                            .withStyle(ChatFormatting.GRAY));
                }
                out.add(Component.translatable("gui.alquimia.map.purity_hint").withStyle(ChatFormatting.DARK_GRAY));
                return out;
            } else if (isRevealedArea(map, k, z.x(), z.y(), z.radius())) {
                out.add(Component.translatable("gui.alquimia.map.unknown").withStyle(ChatFormatting.LIGHT_PURPLE));
                out.add(Component.translatable("gui.alquimia.map.unknown.desc").withStyle(ChatFormatting.GRAY));
                return out;
            }
        }
        if (mx * mx + my * my < 25) {
            out.add(Component.translatable("gui.alquimia.map.origin").withStyle(ChatFormatting.AQUA));
        }
        return out;
    }
}
