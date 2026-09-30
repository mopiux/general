package com.mopiux.alquimia.client;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.AlchemyMap;
import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Grimorio alquímico: a la izquierda el mapa explorado; a la derecha las esencias descubiertas o
 * los ingredientes probados. Al pasar el ratón por un ingrediente se dibuja su camino en el mapa.
 */
public class GrimoireScreen extends Screen {
    private static final ResourceLocation BG = Alquimia.id("textures/gui/grimoire.png");
    private static final int W = 300, H = 190;
    private static final int PAGE_X = 158, PAGE_W = 128, LIST_Y = 40, LIST_H = 136;

    private enum Tab {ESSENCES, INGREDIENTS}

    private final ResourceLocation dimension;
    private final AlchemyMapView view = new AlchemyMapView();
    private final List<AlchemyMap> maps = new ArrayList<>();
    private int mapIndex;
    private Tab tab = Tab.ESSENCES;
    private int scroll;
    private int left, top;
    private Button essencesTab, ingredientsTab;
    @Nullable
    private AlchemyIngredient hoveredIngredient;

    public GrimoireScreen(ResourceLocation dimension) {
        super(Component.translatable("gui.alquimia.grimoire"));
        this.dimension = dimension;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        maps.clear();
        AlchemyData data = AlchemyData.get(true);
        maps.addAll(data.maps());
        maps.sort(Comparator.comparing(m -> m.id().toString()));
        AlchemyMap current = data.mapFor(dimension);
        mapIndex = Math.max(0, maps.indexOf(current));
        view.setBounds(left + 16, top + 30, 128, 140);
        view.follow = false;
        view.zoom = 0.55f;
        view.centerOn(0, 0);

        addRenderableWidget(Button.builder(Component.literal("<"), b -> switchMap(-1))
                .bounds(left + 16, top + 13, 14, 13).tooltip(Tooltip.create(Component.translatable("gui.alquimia.grimoire.prev_map"))).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> switchMap(1))
                .bounds(left + 130, top + 13, 14, 13).tooltip(Tooltip.create(Component.translatable("gui.alquimia.grimoire.next_map"))).build());
        essencesTab = addRenderableWidget(Button.builder(Component.translatable("gui.alquimia.grimoire.essences"), b -> setTab(Tab.ESSENCES))
                .bounds(left + PAGE_X, top + 13, 54, 14).build());
        ingredientsTab = addRenderableWidget(Button.builder(Component.translatable("gui.alquimia.grimoire.ingredients"), b -> setTab(Tab.INGREDIENTS))
                .bounds(left + PAGE_X + 56, top + 13, 72, 14).build());
        updateTabs();
    }

    private void switchMap(int d) {
        if (maps.isEmpty()) return;
        mapIndex = Math.floorMod(mapIndex + d, maps.size());
        view.centerOn(0, 0);
        scroll = 0;
    }

    private void setTab(Tab t) {
        tab = t;
        scroll = 0;
        updateTabs();
    }

    private void updateTabs() {
        essencesTab.active = tab != Tab.ESSENCES;
        ingredientsTab.active = tab != Tab.INGREDIENTS;
    }

    @Nullable
    private AlchemyMap map() {
        return maps.isEmpty() ? null : maps.get(mapIndex);
    }

    @Override
    public void tick() {
        view.tick(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Draw2D.alphaBlend();
        g.blit(BG, left, top, 0f, 0f, W, H, 512, 256);
        AlchemyMap map = map();
        PlayerKnowledge k = ClientAccess.knowledge();
        hoveredIngredient = null;
        List<Component> tooltip = new ArrayList<>();

        if (map != null) {
            Component mapName = Component.translatable("alquimia.map." + map.id().getNamespace() + "." + map.id().getPath());
            int w = font.width(mapName);
            float scale = Math.min(1f, 94f / Math.max(1, w));
            g.pose().pushPose();
            g.pose().translate(left + 80, top + 19.5f - 4 * scale, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, mapName, -w / 2, 0, 0x3A2816, false);
            g.pose().popPose();
        }

        // Página derecha
        if (map != null) {
            if (tab == Tab.ESSENCES) renderEssences(g, map, k, mouseX, mouseY, tooltip);
            else renderIngredients(g, k, mouseX, mouseY, tooltip);
        }

        if (map != null) {
            AlchemyMapView.Preview preview = null;
            if (hoveredIngredient != null && !hoveredIngredient.isTransform()) {
                preview = new AlchemyMapView.Preview(0, 0, hoveredIngredient.segments(1f), false);
            }
            view.render(g, map, k, null, preview, false, partial);
            if (tooltip.isEmpty()) tooltip.addAll(view.tooltip(map, k, null, mouseX, mouseY));
        }

        super.render(g, mouseX, mouseY, partial);
        if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderEssences(GuiGraphics g, AlchemyMap map, PlayerKnowledge k, int mouseX, int mouseY, List<Component> tooltip) {
        Draw2D.alphaBlend();
        List<AlchemyMap.Zone> zones = new ArrayList<>(map.zones());
        zones.sort(Comparator.comparingDouble(z -> z.x() * z.x() + z.y() * z.y()));
        int discovered = 0;
        for (AlchemyMap.Zone z : zones) if (k.isDiscovered(map.id(), z.effect())) discovered++;
        g.drawString(font, Component.translatable("gui.alquimia.grimoire.discovered", discovered, zones.size()),
                left + PAGE_X, top + 30, 0x3A2816, false);
        int rowH = 11;
        int visible = LIST_H / rowH;
        scroll = Mth.clamp(scroll, 0, Math.max(0, zones.size() - visible));
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < visible && i + scroll < zones.size(); i++) {
            AlchemyMap.Zone z = zones.get(i + scroll);
            int x = left + PAGE_X, y = top + LIST_Y + i * rowH;
            MobEffect effect = z.mobEffect();
            boolean known = k.isDiscovered(map.id(), z.effect()) && effect != null;
            if (known) {
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
                g.blit(x, y, 0, 9, 9, sprite);
                String name = font.plainSubstrByWidth(effect.getDisplayName().getString(), PAGE_W - 12);
                g.drawString(font, name, x + 12, y + 1, 0x2A1A0C, false);
            } else {
                g.blit(AlchemyMapView.ICONS, x, y, 12f, 12f, 9, 9, AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
                g.drawString(font, Component.translatable("gui.alquimia.grimoire.unknown"), x + 12, y + 1, 0x8A7A60, false);
            }
            if (mouseX >= x && mouseX < x + PAGE_W && mouseY >= y && mouseY < y + rowH) {
                if (known) {
                    tooltip.add(effect.getDisplayName());
                    tooltip.add(Component.translatable("gui.alquimia.grimoire.click_to_locate").withStyle(ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.translatable("gui.alquimia.map.unknown.desc").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (zones.size() > visible) drawScrollHint(g, zones.size(), visible);
    }

    private void renderIngredients(GuiGraphics g, PlayerKnowledge k, int mouseX, int mouseY, List<Component> tooltip) {
        Draw2D.alphaBlend();
        List<AlchemyIngredient> list = AlchemyData.get(true).ingredients();
        int known = 0;
        for (AlchemyIngredient i : list) if (k.ingredients().contains(i.id())) known++;
        g.drawString(font, Component.translatable("gui.alquimia.grimoire.tried", known, list.size()),
                left + PAGE_X, top + 30, 0x3A2816, false);
        int cols = 6, cell = 21;
        int rowsVisible = LIST_H / cell;
        int rows = (list.size() + cols - 1) / cols;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - rowsVisible));
        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scroll) * cols + c;
                if (idx >= list.size()) break;
                AlchemyIngredient ing = list.get(idx);
                int x = left + PAGE_X + c * cell, y = top + LIST_Y + r * cell;
                g.fill(x, y, x + 18, y + 18, 0x40A08058);
                boolean isKnown = k.ingredients().contains(ing.id());
                ItemStack[] items = ing.ingredient().getItems();
                ItemStack icon = items.length > 0 ? items[0] : ItemStack.EMPTY;
                if (isKnown && !icon.isEmpty()) {
                    g.renderItem(icon, x + 1, y + 1);
                } else {
                    g.blit(AlchemyMapView.ICONS, x + 4, y + 4, 12f, 12f, 10, 10, AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
                }
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    g.renderOutline(x - 1, y - 1, 20, 20, 0xFF3A2816);
                    if (isKnown && !icon.isEmpty()) {
                        hoveredIngredient = ing;
                        tooltip.add(icon.getHoverName());
                        tooltip.addAll(ClientSetup.ingredientLines(ing, 1f));
                    } else {
                        tooltip.add(Component.translatable("gui.alquimia.grimoire.untried").withStyle(ChatFormatting.GRAY));
                    }
                }
            }
        }
        if (rows > rowsVisible) drawScrollHint(g, rows, rowsVisible);
    }

    private void drawScrollHint(GuiGraphics g, int total, int visible) {
        int x = left + PAGE_X + PAGE_W + 2, y0 = top + LIST_Y, h = LIST_H;
        g.fill(x, y0, x + 2, y0 + h, 0x40000000);
        int barH = Math.max(8, h * visible / total);
        int barY = y0 + (h - barH) * scroll / Math.max(1, total - visible);
        g.fill(x, barY, x + 2, barY + barH, 0xFF6B4A2A);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (view.mouseScrolled(mx, my, delta)) return true;
        scroll -= (int) Math.signum(delta);
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        AlchemyMap map = map();
        if (map != null && tab == Tab.ESSENCES && mx >= left + PAGE_X && mx < left + PAGE_X + PAGE_W) {
            int i = (int) ((my - top - LIST_Y) / 11) + scroll;
            List<AlchemyMap.Zone> zones = new ArrayList<>(map.zones());
            zones.sort(Comparator.comparingDouble(z -> z.x() * z.x() + z.y() * z.y()));
            if (my >= top + LIST_Y && i >= 0 && i < zones.size() && ClientAccess.knowledge().isDiscovered(map.id(), zones.get(i).effect())) {
                view.centerOn(zones.get(i).x(), zones.get(i).y());
                return true;
            }
        }
        return view.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (view.mouseDragged(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        view.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
