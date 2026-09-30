package com.mopiux.alquimia.registry;

import com.mojang.serialization.Codec;
import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.world.ConfigCountPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModWorldgen {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Alquimia.MOD_ID);

    public static final RegistryObject<PlacementModifierType<ConfigCountPlacement>> CONFIG_COUNT =
            PLACEMENT_MODIFIERS.register("config_count", () -> new PlacementModifierType<ConfigCountPlacement>() {
                @Override
                public Codec<ConfigCountPlacement> codec() {
                    return ConfigCountPlacement.CODEC;
                }
            });

    private ModWorldgen() {
    }
}
