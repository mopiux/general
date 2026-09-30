package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /** Fuentes de calor suave debajo del caldero (fogata, fuego, bloque de magma). */
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, new ResourceLocation(Alquimia.MOD_ID, "heat_sources"));
    /** Fuentes de calor intenso (fuego de almas, lava): la mezcla avanza más rápido. */
    public static final TagKey<Block> STRONG_HEAT_SOURCES = TagKey.create(Registries.BLOCK, new ResourceLocation(Alquimia.MOD_ID, "strong_heat_sources"));

    private ModTags() {
    }
}
