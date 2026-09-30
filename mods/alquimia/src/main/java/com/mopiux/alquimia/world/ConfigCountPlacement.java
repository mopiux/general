package com.mopiux.alquimia.world;

import com.mojang.serialization.Codec;
import com.mopiux.alquimia.config.AlquimiaConfig;
import com.mopiux.alquimia.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/**
 * Modificador de colocación que repite un feature tantas veces como diga la configuración.
 * Permite que el jugador ajuste (o desactive) la cantidad de vetas de cada mineral sin
 * tocar los datapacks. Uso en JSON: {@code {"type": "alquimia:config_count", "key": "salt"}}.
 */
public final class ConfigCountPlacement extends RepeatingPlacement {
    public static final Codec<ConfigCountPlacement> CODEC = Codec.STRING.fieldOf("key")
            .xmap(ConfigCountPlacement::new, p -> p.key).codec();

    private final String key;

    public ConfigCountPlacement(String key) {
        this.key = key;
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        return AlquimiaConfig.veinsFor(key);
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModWorldgen.CONFIG_COUNT.get();
    }
}
