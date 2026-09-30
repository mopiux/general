package com.mopiux.alquimia.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botón que avisa también cuando se suelta (para "mantener apretado para remover"). */
public class HoldButton extends Button {
    private final Runnable onRelease;

    public HoldButton(int x, int y, int width, int height, Component message, OnPress onPress, Runnable onRelease) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.onRelease = onRelease;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        onRelease.run();
    }
}
