package com.mopiux.alquimia.network;

import com.mopiux.alquimia.alchemy.AlchemyData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: mapas e ingredientes alquímicos de los datapacks. */
public record SyncDataPacket(AlchemyData data) {
    public void encode(FriendlyByteBuf buf) {
        data.write(buf);
    }

    public static SyncDataPacket decode(FriendlyByteBuf buf) {
        return new SyncDataPacket(AlchemyData.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        AlchemyData.setClient(data);
    }
}
