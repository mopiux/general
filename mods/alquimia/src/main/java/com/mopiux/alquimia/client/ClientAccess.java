package com.mopiux.alquimia.client;

import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import com.mopiux.alquimia.menu.CauldronMenu;
import com.mopiux.alquimia.network.SyncBrewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Punto de entrada del código común hacia el cliente (solo se ejecuta en el cliente). */
public final class ClientAccess {
    private static PlayerKnowledge knowledge = new PlayerKnowledge();

    private ClientAccess() {
    }

    public static PlayerKnowledge knowledge() {
        return knowledge;
    }

    public static void setKnowledge(PlayerKnowledge k) {
        knowledge = k;
    }

    public static void applyBrew(SyncBrewPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof CauldronMenu menu && menu.containerId == packet.containerId()) {
            menu.applySync(packet);
        }
    }

    public static void openGrimoire(Level level) {
        Minecraft.getInstance().setScreen(new GrimoireScreen(level.dimension().location()));
    }

    public static void reset() {
        knowledge = new PlayerKnowledge();
    }
}
