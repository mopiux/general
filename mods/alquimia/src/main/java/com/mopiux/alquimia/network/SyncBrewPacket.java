package com.mopiux.alquimia.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: estado de la mezcla del caldero que el jugador tiene abierto. */
public record SyncBrewPacket(int containerId, CompoundTag brew, int heat, boolean stirring, boolean stirringByMe) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeNbt(brew);
        buf.writeVarInt(heat);
        buf.writeBoolean(stirring);
        buf.writeBoolean(stirringByMe);
    }

    public static SyncBrewPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        CompoundTag tag = buf.readNbt();
        return new SyncBrewPacket(id, tag == null ? new CompoundTag() : tag, buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mopiux.alquimia.client.ClientAccess.applyBrew(this));
    }
}
