package com.mopiux.alquimia.client;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyIngredient;
import com.mopiux.alquimia.alchemy.AlchemyMap;
import com.mopiux.alquimia.alchemy.BrewState;
import com.mopiux.alquimia.alchemy.Grinding;
import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import com.mopiux.alquimia.block.CauldronAction;
import com.mopiux.alquimia.config.AlquimiaConfig;
import com.mopiux.alquimia.item.AlchemicalPotionItem;
import com.mopiux.alquimia.menu.CauldronMenu;
import com.mopiux.alquimia.network.CauldronActionPacket;
import com.mopiux.alquimia.network.Network;
import com.mopiux.alquimia.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla del caldero alquímico: el mapa de esencias a la izquierda, las ranuras, el estado de
 * la mezcla y el inventario a la derecha, y los botones de acción debajo del mapa.
 */
public class CauldronScreen extends AbstractContainerScreen<CauldronMenu> {
    public static final ResourceLocation BG = Alquimia.id("textures/gui/cauldron.png");
    private static final int TEX_W = 512, TEX_H = 256;
    private static final int MAP_X = 7, MAP_Y = 17, MAP_W = 176, MAP_H = 126;
    private static final int PANEL_X = 190, ESSENCE_X = 262;

    private final AlchemyMapView view = new AlchemyMapView();
    private final List<ActionButton> actionButtons = new ArrayList<>();
    private boolean stirHeld;
    private boolean stirKeyDown;
    private boolean centered;
    private int stopResendCooldown;
    @Nullable
    private Component status;
    private int statusTicks;

    private record ActionButton(Button button, CauldronAction action, String tooltipKey) {
    }

    public CauldronScreen(CauldronMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 358;
        imageHeight = 188;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = PANEL_X;
        inventoryLabelY = 96;
        if (AlquimiaConfig.CLIENT_SPEC.isLoaded()) view.zoom = AlquimiaConfig.DEFAULT_ZOOM.get().floatValue();
    }

    @Override
    protected void init() {
        super.init();
        view.setBounds(leftPos + MAP_X, topPos + MAP_Y, MAP_W, MAP_H);
        if (!centered) {
            view.centerOn(menu.brew().x, menu.brew().y);
            centered = true;
        }
        actionButtons.clear();
        int bx = leftPos + 7, by = topPos + 147, bw = 58, bh = 18;

        HoldButton stir = new HoldButton(bx, by, bw, bh, Component.translatable("gui.alquimia.stir"),
                b -> onStirPressed(), this::onStirReleased);
        addAction(stir, CauldronAction.STIR_START, "gui.alquimia.stir.tooltip");
        addAction(Button.builder(Component.translatable("gui.alquimia.dilute"), b -> act(CauldronAction.DILUTE))
                .bounds(bx + 59, by, bw, bh).build(), CauldronAction.DILUTE, "gui.alquimia.dilute.tooltip");
        addAction(Button.builder(Component.translatable("gui.alquimia.fix"), b -> act(CauldronAction.FIX))
                .bounds(bx + 118, by, bw, bh).build(), CauldronAction.FIX, "gui.alquimia.fix.tooltip");
        addAction(Button.builder(Component.translatable("gui.alquimia.empower"), b -> act(CauldronAction.EMPOWER))
                .bounds(bx, by + 20, bw, bh).build(), CauldronAction.EMPOWER, "gui.alquimia.empower.tooltip");
        addAction(Button.builder(Component.translatable("gui.alquimia.prolong"), b -> act(CauldronAction.PROLONG))
                .bounds(bx + 59, by + 20, bw, bh).build(), CauldronAction.PROLONG, "gui.alquimia.prolong.tooltip");
        addAction(Button.builder(Component.translatable("gui.alquimia.bottle"), b -> act(CauldronAction.BOTTLE))
                .bounds(bx + 118, by + 20, bw, bh).build(), CauldronAction.BOTTLE, "gui.alquimia.bottle.tooltip");

        // Botones sobre el mapa
        int mx = leftPos + MAP_X + MAP_W - 13, my = topPos + MAP_Y + 2;
        SmallButton zoomIn = new SmallButton(mx, my, 11, Component.translatable("gui.alquimia.zoom_in"), 0, 48, 0xFFFFFF,
                b -> view.zoomBy(1.25f));
        zoomIn.setTooltip(Tooltip.create(Component.translatable("gui.alquimia.zoom_in")));
        addRenderableWidget(zoomIn);
        SmallButton zoomOut = new SmallButton(mx, my + 12, 11, Component.translatable("gui.alquimia.zoom_out"), 8, 48, 0xFFFFFF,
                b -> view.zoomBy(0.8f));
        zoomOut.setTooltip(Tooltip.create(Component.translatable("gui.alquimia.zoom_out")));
        addRenderableWidget(zoomOut);
        SmallButton center = new SmallButton(mx, my + 24, 11, Component.translatable("gui.alquimia.center"), 16, 48, 0xFFFFFF,
                b -> recenter());
        center.setTooltip(Tooltip.create(Component.translatable("gui.alquimia.center.tooltip", ClientSetup.KEY_CENTER.getTranslatedKeyMessage())));
        addRenderableWidget(center);
        SmallButton empty = new SmallButton(mx, my + 38, 11, Component.translatable("gui.alquimia.empty"), 24, 48, 0xFFFFFF,
                b -> {
                    if (hasShiftDown()) sendAction(CauldronAction.EMPTY);
                    else showStatus(Component.translatable("gui.alquimia.empty.confirm"));
                });
        empty.setTooltip(Tooltip.create(Component.translatable("gui.alquimia.empty.tooltip")));
        addRenderableWidget(empty);

        SmallButton help = new SmallButton(leftPos + imageWidth - 17, topPos + 4, 11, Component.translatable("gui.alquimia.help"),
                32, 48, 0xFFFFFF, b -> showStatus(Component.translatable("gui.alquimia.help.short")));
        help.setTooltip(Tooltip.create(Component.translatable("gui.alquimia.help.tooltip")));
        addRenderableWidget(help);
        updateButtons();
    }

    private void addAction(Button button, CauldronAction action, String tooltipKey) {
        addRenderableWidget(button);
        actionButtons.add(new ActionButton(button, action, tooltipKey));
    }

    private void recenter() {
        view.follow = true;
        view.centerOn(menu.brew().x, menu.brew().y);
    }

    // ------------------------------------------------------------------ acciones
    private static boolean holdMode() {
        return !AlquimiaConfig.CLIENT_SPEC.isLoaded() || AlquimiaConfig.HOLD_TO_STIR.get();
    }

    private void onStirPressed() {
        if (holdMode()) {
            String err = validate(CauldronAction.STIR_START);
            if (err != null) {
                showError(err);
                return;
            }
            stirHeld = true;
            sendAction(CauldronAction.STIR_START);
        } else {
            if (!menu.isStirringByMe()) {
                String err = validate(CauldronAction.STIR_START);
                if (err != null) {
                    showError(err);
                    return;
                }
            }
            sendAction(CauldronAction.STIR_TOGGLE);
        }
    }

    private void onStirReleased() {
        if (holdMode() && stirHeld) {
            stirHeld = false;
            sendAction(CauldronAction.STIR_STOP);
        }
    }

    private void act(CauldronAction action) {
        String err = validate(action);
        if (err != null) {
            showError(err);
            return;
        }
        sendAction(action);
    }

    private void sendAction(CauldronAction action) {
        Network.CHANNEL.sendToServer(new CauldronActionPacket(menu.containerId, action));
    }

    private ItemStack slotItem(int machineSlot) {
        return menu.getSlot(machineSlot).getItem();
    }

    @Nullable
    private AlchemyMap currentMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return AlchemyData.get(true).mapFor(mc.level.dimension().location());
    }

    private int maxEffects() {
        return AlquimiaConfig.COMMON_SPEC.isLoaded() ? AlquimiaConfig.MAX_EFFECTS.get() : 3;
    }

    /** Predice en el cliente si la acción puede hacerse; devuelve la clave del motivo si no. */
    @Nullable
    private String validate(CauldronAction action) {
        BrewState b = menu.brew();
        ItemStack reagent = slotItem(AlchemicalCauldronBlockEntity.SLOT_REAGENT);
        AlchemyMap map = currentMap();
        switch (action) {
            case STIR_START, STIR_TOGGLE -> {
                if (b.water <= 0) return "no_water";
                if (b.pending.isEmpty()) return "nothing_to_stir";
                return null;
            }
            case DILUTE -> {
                if (!AlchemicalCauldronBlockEntity.isWaterBottle(reagent)) return "dilute.no_water";
                if (b.water >= BrewState.MAX_WATER && b.x == 0 && b.y == 0) return "dilute.nothing";
                return null;
            }
            case FIX -> {
                if (!reagent.is(ModItems.SALT.get())) return "fix.no_salt";
                AlchemyMap.Zone z = map == null ? null : map.zoneAt(b.x, b.y);
                if (z == null) return "fix.no_zone";
                if (b.hasFixed(z.effect())) return "fix.already";
                if (b.fixed.size() >= maxEffects()) return "fix.full_short";
                return null;
            }
            case EMPOWER -> {
                if (!reagent.is(ModItems.SULFUR.get())) return "empower.no_sulfur";
                if (b.fixed.isEmpty()) return "no_essence";
                if (b.fixed.get(b.fixed.size() - 1).empowered()) return "empower.already";
                return null;
            }
            case PROLONG -> {
                if (!reagent.is(ModItems.QUICKSILVER.get())) return "prolong.no_quicksilver";
                if (b.fixed.isEmpty()) return "no_essence";
                if (b.prolongs >= BrewState.MAX_PROLONGS) return "prolong.max";
                return null;
            }
            case BOTTLE -> {
                if (b.fixed.isEmpty()) return "no_essence";
                if (!slotItem(AlchemicalCauldronBlockEntity.SLOT_BOTTLES).is(Items.GLASS_BOTTLE)) return "bottle.no_bottles";
                boolean free = false;
                for (int i = 0; i < AlchemicalCauldronBlockEntity.OUTPUTS; i++) {
                    if (slotItem(AlchemicalCauldronBlockEntity.SLOT_OUTPUT + i).isEmpty()) free = true;
                }
                if (!free) return "bottle.full";
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private void showError(String key) {
        showStatus(Component.translatable("message.alquimia." + key));
    }

    private void showStatus(Component message) {
        status = message;
        statusTicks = 80;
        Minecraft.getInstance().getNarrator().sayNow(message);
    }

    private void updateButtons() {
        for (ActionButton ab : actionButtons) {
            String err = ab.action() == CauldronAction.STIR_START && menu.isStirringByMe() ? null : validate(ab.action());
            ab.button().active = err == null;
            Component keyName = switch (ab.action()) {
                case STIR_START -> ClientSetup.KEY_STIR.getTranslatedKeyMessage();
                case DILUTE -> ClientSetup.KEY_DILUTE.getTranslatedKeyMessage();
                case FIX -> ClientSetup.KEY_FIX.getTranslatedKeyMessage();
                case EMPOWER -> ClientSetup.KEY_EMPOWER.getTranslatedKeyMessage();
                case PROLONG -> ClientSetup.KEY_PROLONG.getTranslatedKeyMessage();
                case BOTTLE -> ClientSetup.KEY_BOTTLE.getTranslatedKeyMessage();
                default -> Component.empty();
            };
            MutableComponent tip = Component.translatable(ab.tooltipKey(), keyName);
            if (err != null) {
                tip.append("\n").append(Component.translatable("message.alquimia." + err).withStyle(ChatFormatting.RED));
            }
            ab.button().setTooltip(Tooltip.create(tip));
        }
    }

    // ------------------------------------------------------------------ tick y entrada
    @Override
    protected void containerTick() {
        super.containerTick();
        view.tick(menu.brew());
        if (statusTicks > 0 && --statusTicks == 0) status = null;
        updateButtons();
        if (stopResendCooldown > 0) stopResendCooldown--;
        if (menu.isStirringByMe() && holdMode() && !stirHeld && stopResendCooldown == 0) {
            // El jugador soltó el botón pero el servidor sigue removiendo (por ejemplo, tras un lag)
            sendAction(CauldronAction.STIR_STOP);
            stopResendCooldown = 10;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (ClientSetup.KEY_STIR.matches(key, scan)) {
            if (!stirKeyDown) {
                stirKeyDown = true;
                onStirPressed();
            }
            return true;
        }
        if (ClientSetup.KEY_DILUTE.matches(key, scan)) {
            act(CauldronAction.DILUTE);
            return true;
        }
        if (ClientSetup.KEY_FIX.matches(key, scan)) {
            act(CauldronAction.FIX);
            return true;
        }
        if (ClientSetup.KEY_EMPOWER.matches(key, scan)) {
            act(CauldronAction.EMPOWER);
            return true;
        }
        if (ClientSetup.KEY_PROLONG.matches(key, scan)) {
            act(CauldronAction.PROLONG);
            return true;
        }
        if (ClientSetup.KEY_BOTTLE.matches(key, scan)) {
            act(CauldronAction.BOTTLE);
            return true;
        }
        if (ClientSetup.KEY_CENTER.matches(key, scan)) {
            recenter();
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            view.zoomBy(1.25f);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            view.zoomBy(0.8f);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (ClientSetup.KEY_STIR.matches(key, scan)) {
            stirKeyDown = false;
            onStirReleased();
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean overWidget = false;
        for (var child : children()) {
            if (child.isMouseOver(mx, my)) {
                overWidget = true;
                break;
            }
        }
        if (!overWidget && view.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (view.mouseDragged(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = view.mouseReleased();
        if (button == 0 && stirHeld && !stirKeyDown) onStirReleased();
        return super.mouseReleased(mx, my, button) || handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (view.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            List<Component> tip = panelTooltip(mouseX - leftPos, mouseY - topPos);
            if (tip.isEmpty()) {
                AlchemyMap map = currentMap();
                boolean overWidget = false;
                for (var child : children()) if (child.isMouseOver(mouseX, mouseY)) overWidget = true;
                if (map != null && !overWidget) tip = view.tooltip(map, ClientAccess.knowledge(), menu.brew(), mouseX, mouseY);
            }
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        Draw2D.alphaBlend();
        g.blit(BG, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, TEX_W, TEX_H);
        // Íconos fantasma en las ranuras vacías
        ghost(g, AlchemicalCauldronBlockEntity.SLOT_INGREDIENT, CauldronMenu.INGREDIENT_X, 0);
        ghost(g, AlchemicalCauldronBlockEntity.SLOT_REAGENT, CauldronMenu.REAGENT_X, 16);
        ghost(g, AlchemicalCauldronBlockEntity.SLOT_BOTTLES, CauldronMenu.BOTTLES_X, 32);

        AlchemyMap map = currentMap();
        if (map != null) {
            view.render(g, map, ClientAccess.knowledge(), menu.brew(), computePreview(map), menu.isStirring(), partial);
        } else {
            g.fill(view.x, view.y, view.x + view.w, view.y + view.h, 0xFF202020);
            g.drawWordWrap(font, Component.translatable("gui.alquimia.no_map"), view.x + 8, view.y + 50, view.w - 16, 0xFFFFFF);
        }
        renderStatusStrip(g);
    }

    private void ghost(GuiGraphics g, int slot, int x, int u) {
        if (!slotItem(slot).isEmpty()) return;
        Draw2D.alphaBlend();
        g.setColor(1f, 1f, 1f, 0.35f);
        g.blit(AlchemyMapView.ICONS, leftPos + x, topPos + CauldronMenu.SLOTS_Y, u, 32, 16, 16,
                AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private void renderStatusStrip(GuiGraphics g) {
        Component text = status != null ? status : hint();
        if (text == null) return;
        float s = 0.75f;
        int maxW = (int) ((view.w - 6) / s);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, maxW);
        int shown = Math.min(2, lines.size());
        int x0 = view.x, y1 = view.y + view.h, y0 = y1 - (shown == 2 ? 17 : 11);
        g.fill(x0, y0, x0 + view.w, y1, 0xC8201408);
        g.pose().pushPose();
        g.pose().translate(x0 + 3, y0 + 2.5f, 0);
        g.pose().scale(s, s, 1f);
        int color = status != null ? 0xFFFFD27F : 0xFFE8DCC0;
        for (int i = 0; i < shown; i++) g.drawString(font, lines.get(i), 0, i * 8, color, false);
        g.pose().popPose();
    }

    /** Sugerencia según el estado actual (tutorial contextual). */
    @Nullable
    private Component hint() {
        BrewState b = menu.brew();
        AlchemyMap map = currentMap();
        if (map == null) return null;
        if (b.water <= 0) return Component.translatable("gui.alquimia.hint.water");
        if (menu.heat() <= 0) return Component.translatable("gui.alquimia.hint.heat");
        if (menu.isStirring()) return Component.translatable("gui.alquimia.hint.stirring");
        AlchemyMap.Zone z = map.zoneAt(b.x, b.y);
        if (z != null && z.mobEffect() != null && !b.hasFixed(z.effect())) {
            return Component.translatable("gui.alquimia.hint.over_zone", z.mobEffect().getDisplayName());
        }
        if (!b.pending.isEmpty()) return Component.translatable("gui.alquimia.hint.stir");
        if (!b.fixed.isEmpty()) return Component.translatable("gui.alquimia.hint.bottle");
        return Component.translatable("gui.alquimia.hint.ingredients");
    }

    @Nullable
    private AlchemyMapView.Preview computePreview(AlchemyMap map) {
        ItemStack stack = menu.getCarried();
        if (stack.isEmpty() && hoveredSlot != null && hoveredSlot.hasItem()) stack = hoveredSlot.getItem();
        if (stack.isEmpty()) return null;
        AlchemyIngredient ing = AlchemyData.get(true).find(stack);
        if (ing == null) return null;
        BrewState b = menu.brew();
        if (ing.isTransform()) {
            BrewState copy = b.copy();
            switch (ing.transform()) {
                case ROTATE -> copy.rotatePending(ing.amount());
                case SCALE -> copy.scalePending(ing.amount());
                case MIRROR -> copy.mirrorPending();
                default -> {
                }
            }
            return new AlchemyMapView.Preview(b.x, b.y, new ArrayList<>(copy.pending), true);
        }
        float[] end = b.pendingEnd();
        return new AlchemyMapView.Preview(end[0], end[1], ing.segments(Grinding.pathFraction(stack)), false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);

        BrewState b = menu.brew();
        Draw2D.alphaBlend();
        // Agua
        g.drawString(font, Component.translatable("gui.alquimia.water"), PANEL_X, 42, 0x404040, false);
        for (int i = 0; i < BrewState.MAX_WATER; i++) {
            g.blit(AlchemyMapView.ICONS, PANEL_X + 34 + i * 10, 41, i < b.water ? 24f : 33f, 0f, 9, 9,
                    AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
        }
        // Calor
        int heat = menu.heat();
        g.blit(AlchemyMapView.ICONS, PANEL_X, 53, heat > 0 ? 42f : 51f, 0f, 9, 9, AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
        Component heatText = Component.translatable("gui.alquimia.heat." + Math.min(heat, 2));
        drawSmall(g, heatText, PANEL_X + 11, 55, heat > 0 ? 0x404040 : 0xA02020, 56);
        // Camino pendiente
        Component pathText = menu.isStirring()
                ? Component.translatable("gui.alquimia.stirring")
                : Component.translatable("gui.alquimia.path", String.format("%.0f", b.pendingLength()));
        drawSmall(g, pathText, PANEL_X, 67, 0x404040, 68);
        drawSmall(g, Component.translatable("gui.alquimia.ingredients_used", b.ingredientsUsed), PANEL_X, 77, 0x606060, 68);

        // Esencias fijadas
        drawSmall(g, Component.translatable("gui.alquimia.essences", b.fixed.size(), maxEffects()), ESSENCE_X, 42, 0x404040, 88);
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < Math.min(4, b.fixed.size()); i++) {
            BrewState.FixedEssence f = b.fixed.get(i);
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(f.effect());
            if (effect == null) continue;
            int ry = 52 + i * 10;
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
            g.blit(ESSENCE_X, ry, 0, 9, 9, sprite);
            Component name = AlchemicalPotionItem.effectName(new MobEffectInstance(effect, 20, f.amplifier()));
            drawSmall(g, name, ESSENCE_X + 11, ry + 2, f.empowered() ? 0x8A5A00 : 0x303030, 77);
        }
        if (b.prolongs > 0) {
            drawSmall(g, Component.translatable("gui.alquimia.prolonged", b.prolongs), ESSENCE_X, 92 - 8, 0x3050A0, 88);
        }
    }

    private void drawSmall(GuiGraphics g, Component text, int x, int y, int color, int maxWidth) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float s = 0.75f;
        g.pose().scale(s, s, 1f);
        String str = font.plainSubstrByWidth(text.getString(), (int) (maxWidth / s));
        g.drawString(font, str, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Tooltips del panel de estado (agua, calor, esencias). Coordenadas relativas a la pantalla. */
    private List<Component> panelTooltip(int rx, int ry) {
        List<Component> out = new ArrayList<>();
        BrewState b = menu.brew();
        if (rx >= PANEL_X && rx < PANEL_X + 66 && ry >= 40 && ry < 51) {
            out.add(Component.translatable("gui.alquimia.water.tooltip", b.water, BrewState.MAX_WATER));
        } else if (rx >= PANEL_X && rx < PANEL_X + 66 && ry >= 52 && ry < 63) {
            out.add(Component.translatable("gui.alquimia.heat.tooltip"));
        } else if (rx >= ESSENCE_X && rx < ESSENCE_X + 88 && ry >= 52 && ry < 92) {
            int i = (ry - 52) / 10;
            if (i < b.fixed.size()) {
                BrewState.FixedEssence f = b.fixed.get(i);
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(f.effect());
                if (effect != null) {
                    out.add(AlchemicalPotionItem.effectName(new MobEffectInstance(effect, 20, f.amplifier())));
                    if (!effect.isInstantenous()) {
                        double mult = AlquimiaConfig.COMMON_SPEC.isLoaded() ? AlquimiaConfig.DURATION_MULTIPLIER.get() : 1.0;
                        out.add(Component.translatable("gui.alquimia.map.duration",
                                StringUtil.formatTickDuration((int) Math.round(f.duration() * mult))).withStyle(ChatFormatting.GRAY));
                    }
                    if (f.empowered()) out.add(Component.translatable("gui.alquimia.empowered").withStyle(ChatFormatting.GOLD));
                }
            }
        }
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
