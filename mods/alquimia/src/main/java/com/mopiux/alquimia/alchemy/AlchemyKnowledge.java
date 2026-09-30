package com.mopiux.alquimia.alchemy;

import com.mopiux.alquimia.config.AlquimiaConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda el conocimiento alquímico de todos los jugadores en el mundo (archivo
 * {@code data/alquimia_knowledge.dat}). Se guarda en el mundo y no en el jugador para que no se
 * pierda al morir ni haga falta copiarlo entre dimensiones.
 */
public final class AlchemyKnowledge extends SavedData {
    private static final String NAME = "alquimia_knowledge";
    private final Map<UUID, PlayerKnowledge> players = new HashMap<>();
    private final PlayerKnowledge shared = new PlayerKnowledge();

    public static AlchemyKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(AlchemyKnowledge::load, AlchemyKnowledge::new, NAME);
    }

    public PlayerKnowledge of(UUID player) {
        if (AlquimiaConfig.SHARED_DISCOVERIES.get()) return shared;
        return players.computeIfAbsent(player, k -> new PlayerKnowledge());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag p = new CompoundTag();
        players.forEach((k, v) -> p.put(k.toString(), v.save()));
        tag.put("players", p);
        tag.put("shared", shared.save());
        return tag;
    }

    public static AlchemyKnowledge load(CompoundTag tag) {
        AlchemyKnowledge k = new AlchemyKnowledge();
        CompoundTag p = tag.getCompound("players");
        for (String key : p.getAllKeys()) {
            try {
                k.players.put(UUID.fromString(key), PlayerKnowledge.load(p.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        k.shared.copyFrom(PlayerKnowledge.load(tag.getCompound("shared")));
        return k;
    }
}
