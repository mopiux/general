package com.mopiux.alquimia.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pantalla de configuración genérica: recorre un {@link ForgeConfigSpec} y crea un control por
 * cada opción (interruptor, número, texto o lista de valores). Los nombres y descripciones se
 * traducen con las claves {@code <modid>.config.<ruta>} y {@code <modid>.config.<ruta>.tooltip}.
 * <p>
 * Forge 1.20.1 no trae una pantalla de configuración propia, así que cada mod incluye esta.
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private final String modId;
    private final List<Section> sections;
    private OptionList list;
    private final List<PendingChange<?>> changes = new ArrayList<>();

    public record Section(Component title, ForgeConfigSpec spec, boolean requiresWorld) {}

    public ConfigScreen(Screen parent, String modId, Component title, List<Section> sections) {
        super(title);
        this.parent = parent;
        this.modId = modId;
        this.sections = sections;
    }

    @Override
    protected void init() {
        changes.clear();
        list = new OptionList(minecraft, width, height, 32, height - 36, 24);
        for (Section section : sections) {
            if (!section.spec().isLoaded()) {
                list.addHeader(section.title().copy().append(" ").append(
                        Component.translatable("alquimia.config.not_loaded").withStyle(ChatFormatting.GRAY)));
                continue;
            }
            list.addHeader(section.title());
            addValues(section.spec(), section.spec().getValues(), new ArrayList<>());
        }
        addWidget(list);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> saveAndClose())
                .bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("alquimia.config.reset"), b -> resetAll())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    private void addValues(ForgeConfigSpec spec, UnmodifiableConfig values, List<String> path) {
        for (Map.Entry<String, Object> e : values.valueMap().entrySet()) {
            List<String> p = new ArrayList<>(path);
            p.add(e.getKey());
            Object v = e.getValue();
            if (v instanceof UnmodifiableConfig sub) {
                list.addHeader(Component.translatable(modId + ".config." + String.join(".", p)).withStyle(ChatFormatting.YELLOW));
                addValues(spec, sub, p);
            } else if (v instanceof ForgeConfigSpec.ConfigValue<?> cv) {
                ForgeConfigSpec.ValueSpec vs = spec.getSpec().get(p);
                addOption(spec, cv, vs, p);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addOption(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> cv, ForgeConfigSpec.ValueSpec vs, List<String> path) {
        String key = modId + ".config." + String.join(".", path);
        Component label = I18n.exists(key) ? Component.translatable(key) : Component.literal(path.get(path.size() - 1));
        String tipKey = key + ".tooltip";
        Component tip = I18n.exists(tipKey) ? Component.translatable(tipKey)
                : Component.literal(vs != null && vs.getComment() != null ? vs.getComment() : "");
        Object current = cv.get();
        AbstractWidget widget;
        if (current instanceof Boolean b) {
            PendingChange<Boolean> ch = new PendingChange<>((ForgeConfigSpec.ConfigValue<Boolean>) cv, b, spec);
            changes.add(ch);
            widget = CycleButton.onOffBuilder(b).displayOnlyValue().create(0, 0, 90, 20, label, (btn, val) -> ch.value = val);
            ch.resetter = () -> ((CycleButton<Boolean>) ch.widget).setValue((Boolean) ch.config.getDefault());
        } else if (current instanceof Enum<?> en) {
            Enum[] constants = en.getDeclaringClass().getEnumConstants();
            PendingChange ch = new PendingChange((ForgeConfigSpec.ConfigValue) cv, en, spec);
            changes.add(ch);
            widget = CycleButton.<Enum>builder(val -> {
                        String k = modId + ".config.enum." + val.name().toLowerCase();
                        return I18n.exists(k) ? Component.translatable(k) : Component.literal(val.name());
                    })
                    .withValues(Arrays.asList(constants)).withInitialValue(en).displayOnlyValue()
                    .create(0, 0, 90, 20, label, (btn, val) -> ch.value = val);
            ch.resetter = () -> ((CycleButton) ch.widget).setValue(ch.config.getDefault());
        } else if (current instanceof Integer || current instanceof Long || current instanceof Double || current instanceof String) {
            PendingChange ch = new PendingChange((ForgeConfigSpec.ConfigValue) cv, current, spec);
            changes.add(ch);
            EditBox box = new EditBox(font, 0, 0, 88, 18, label);
            box.setMaxLength(64);
            box.setValue(String.valueOf(current));
            box.setResponder(text -> {
                Object parsed = parse(current, text);
                boolean ok = parsed != null && (vs == null || vs.test(parsed));
                box.setTextColor(ok ? 0xE0E0E0 : 0xFF5555);
                if (ok) ch.value = parsed;
            });
            widget = box;
            ch.resetter = () -> box.setValue(String.valueOf(ch.config.getDefault()));
            if (vs != null && vs.getRange() != null) {
                tip = tip.copy().append("\n").append(Component.translatable("alquimia.config.range",
                        String.valueOf(vs.getRange().getMin()), String.valueOf(vs.getRange().getMax())).withStyle(ChatFormatting.GRAY));
            }
        } else {
            widget = Button.builder(Component.translatable("alquimia.config.edit_in_file"), b -> {}).bounds(0, 0, 90, 20).build();
            widget.active = false;
        }
        if (!changes.isEmpty() && changes.get(changes.size() - 1).config == cv) {
            changes.get(changes.size() - 1).widget = widget;
        }
        if (vs != null) {
            tip = tip.copy().append("\n").append(Component.translatable("alquimia.config.default", String.valueOf(vs.getDefault()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        widget.setTooltip(Tooltip.create(tip));
        list.addOption(label, widget);
    }

    private static Object parse(Object sample, String text) {
        try {
            if (sample instanceof Integer) return Integer.parseInt(text.trim());
            if (sample instanceof Long) return Long.parseLong(text.trim());
            if (sample instanceof Double) return Double.parseDouble(text.trim().replace(',', '.'));
            if (sample instanceof String) return text;
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void resetAll() {
        for (PendingChange<?> ch : changes) {
            if (ch.resetter != null) ch.resetter.run();
            ch.resetToDefault();
        }
    }

    private void saveAndClose() {
        for (PendingChange<?> ch : changes) ch.apply();
        for (Section s : sections) {
            if (s.spec().isLoaded()) s.spec().save();
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static final class PendingChange<T> {
        final ForgeConfigSpec.ConfigValue<T> config;
        final ForgeConfigSpec spec;
        T value;
        AbstractWidget widget;
        Runnable resetter;

        PendingChange(ForgeConfigSpec.ConfigValue<T> config, T value, ForgeConfigSpec spec) {
            this.config = config;
            this.value = value;
            this.spec = spec;
        }

        void resetToDefault() {
            value = config.getDefault();
        }

        void apply() {
            if (value != null && !value.equals(config.get())) config.set(value);
        }
    }

    // ------------------------------------------------------------------ lista
    private class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
        OptionList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }

        void addHeader(Component text) {
            addEntry(new HeaderEntry(text));
        }

        void addOption(Component label, AbstractWidget widget) {
            addEntry(new OptionEntry(label, widget));
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, width - 20);
        }

        @Override
        protected int getScrollbarPosition() {
            return width / 2 + getRowWidth() / 2 + 6;
        }

        abstract class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        }

        class HeaderEntry extends Entry {
            private final Component text;

            HeaderEntry(Component text) {
                this.text = text;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partial) {
                Font f = Minecraft.getInstance().font;
                g.drawCenteredString(f, text.copy().withStyle(ChatFormatting.BOLD), left + width / 2, top + (height - 8) / 2, 0xFFD27F);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of();
            }
        }

        class OptionEntry extends Entry {
            private final Component label;
            private final AbstractWidget widget;

            OptionEntry(Component label, AbstractWidget widget) {
                this.label = label;
                this.widget = widget;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partial) {
                Font f = Minecraft.getInstance().font;
                g.drawString(f, label, left, top + (height - 8) / 2, 0xFFFFFF, false);
                widget.setX(left + width - widget.getWidth());
                widget.setY(top + (height - widget.getHeight()) / 2);
                widget.render(g, mouseX, mouseY, partial);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(widget);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(widget);
            }
        }
    }

    /** Utilidad para registrar la pantalla desde el constructor del mod. */
    public static Supplier<Screen> factory(Screen parent, String modId, Component title, List<Section> sections) {
        return () -> new ConfigScreen(parent, modId, title, sections);
    }
}
