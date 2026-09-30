package com.mopiux.alquimia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botón chico y plano que se dibuja sobre el mapa, con un ícono de icons.png. */
public class SmallButton extends Button {
    private final int iconU, iconV;
    private final int tint;

    public SmallButton(int x, int y, int size, Component narration, int iconU, int iconV, int tint, OnPress onPress) {
        super(x, y, size, size, narration, onPress, DEFAULT_NARRATION);
        this.iconU = iconU;
        this.iconV = iconV;
        this.tint = tint;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int bg = !active ? 0x80404040 : isHoveredOrFocused() ? 0xE0F4E4C1 : 0xC0DCC8A0;
        g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF3A2816);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);
        float r = ((tint >> 16) & 0xFF) / 255f, gg = ((tint >> 8) & 0xFF) / 255f, b = (tint & 0xFF) / 255f;
        Draw2D.alphaBlend();
        g.setColor(r, gg, b, 1f);
        g.blit(AlchemyMapView.ICONS, getX() + (width - 7) / 2, getY() + (height - 7) / 2, iconU, iconV, 7, 7,
                AlchemyMapView.ICONS_SIZE, AlchemyMapView.ICONS_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
        if (isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            g.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, 0xFFFFFFFF);
        }
    }
}
