package com.mopiux.alquimia.event;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyCommand;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.network.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Alquimia.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new AlchemyData.Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            Network.sendData(event.getPlayer());
        } else {
            for (ServerPlayer p : event.getPlayerList().getPlayers()) Network.sendData(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) Network.sendKnowledge(sp);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AlchemyCommand.register(event.getDispatcher());
    }
}
