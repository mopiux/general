package com.mopiux.alquimia.network;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.alchemy.AlchemyData;
import com.mopiux.alquimia.alchemy.AlchemyKnowledge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod y utilidades para enviar datos a los jugadores. */
public final class Network {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Alquimia.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Network() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncDataPacket::encode).decoder(SyncDataPacket::decode)
                .consumerMainThread(SyncDataPacket::handle).add();
        CHANNEL.messageBuilder(SyncKnowledgePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncKnowledgePacket::encode).decoder(SyncKnowledgePacket::decode)
                .consumerMainThread(SyncKnowledgePacket::handle).add();
        CHANNEL.messageBuilder(SyncBrewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncBrewPacket::encode).decoder(SyncBrewPacket::decode)
                .consumerMainThread(SyncBrewPacket::handle).add();
        CHANNEL.messageBuilder(CauldronActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CauldronActionPacket::encode).decoder(CauldronActionPacket::decode)
                .consumerMainThread(CauldronActionPacket::handle).add();
    }

    public static void sendData(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncDataPacket(AlchemyData.server()));
    }

    public static void sendKnowledge(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncKnowledgePacket(AlchemyKnowledge.get(player.server).of(player.getUUID())));
    }
}
