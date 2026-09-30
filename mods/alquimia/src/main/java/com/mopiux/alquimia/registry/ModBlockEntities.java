package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import com.mopiux.alquimia.block.MortarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Alquimia.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AlchemicalCauldronBlockEntity>> ALCHEMICAL_CAULDRON =
            BLOCK_ENTITIES.register("alchemical_cauldron", () -> BlockEntityType.Builder
                    .of(AlchemicalCauldronBlockEntity::new, ModBlocks.ALCHEMICAL_CAULDRON.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MortarBlockEntity>> MORTAR =
            BLOCK_ENTITIES.register("mortar", () -> BlockEntityType.Builder
                    .of(MortarBlockEntity::new, ModBlocks.MORTAR.get()).build(null));

    private ModBlockEntities() {
    }
}
