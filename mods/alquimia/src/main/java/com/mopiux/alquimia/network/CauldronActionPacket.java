package com.mopiux.alquimia.network;

import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import com.mopiux.alquimia.block.CauldronAction;
import com.mopiux.alquimia.menu.CauldronMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador apretó un botón del caldero. El servidor valida todo. */
public record CauldronActionPacket(int containerId, CauldronAction action) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeEnum(action);
    }

    public static CauldronActionPacket decode(FriendlyByteBuf buf) {
        return new CauldronActionPacket(buf.readVarInt(), buf.readEnum(CauldronAction.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        if (!(player.containerMenu instanceof CauldronMenu menu) || menu.containerId != containerId) return;
        if (!menu.stillValid(player)) return;
        AlchemicalCauldronBlockEntity be = menu.blockEntity();
        if (be != null && !be.isRemoved()) be.handleAction(player, action);
    }
}
