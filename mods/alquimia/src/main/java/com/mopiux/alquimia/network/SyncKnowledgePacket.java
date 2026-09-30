package com.mopiux.alquimia.network;

import com.mopiux.alquimia.alchemy.PlayerKnowledge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: lo que el jugador descubrió (niebla, esencias, ingredientes). */
public record SyncKnowledgePacket(PlayerKnowledge knowledge) {
    public void encode(FriendlyByteBuf buf) {
        knowledge.write(buf);
    }

    public static SyncKnowledgePacket decode(FriendlyByteBuf buf) {
        return new SyncKnowledgePacket(PlayerKnowledge.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mopiux.alquimia.client.ClientAccess.setKnowledge(knowledge));
    }
}
