package com.mopiux.alquimia;

import com.mojang.logging.LogUtils;
import com.mopiux.alquimia.advancement.AlchemyTrigger;
import com.mopiux.alquimia.config.AlquimiaConfig;
import com.mopiux.alquimia.item.AlchemicalThrowableItem;
import com.mopiux.alquimia.network.Network;
import com.mopiux.alquimia.registry.ModBlockEntities;
import com.mopiux.alquimia.registry.ModBlocks;
import com.mopiux.alquimia.registry.ModItems;
import com.mopiux.alquimia.registry.ModMenus;
import com.mopiux.alquimia.registry.ModRecipes;
import com.mopiux.alquimia.registry.ModTabs;
import com.mopiux.alquimia.registry.ModWorldgen;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Alquimia Cartográfica: alquimia al estilo Potion Craft para Minecraft 1.20.1 (Forge).
 * Cada ingrediente traza un camino sobre un mapa de esencias; el jugador remueve la mezcla,
 * la diluye y fija esencias con la tria prima (sal, mercurio y azufre).
 */
@Mod(Alquimia.MOD_ID)
public final class Alquimia {
    public static final String MOD_ID = "alquimia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Alquimia() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModTabs.TABS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModWorldgen.PLACEMENT_MODIFIERS.register(modBus);
        AlquimiaConfig.register(ModLoadingContext.get());
        Network.register();
        modBus.addListener(this::commonSetup);
        if (FMLEnvironment.dist.isClient()) {
            com.mopiux.alquimia.client.ClientSetup.init(modBus);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CriteriaTriggers.register(AlchemyTrigger.INSTANCE);
            for (var item : new AlchemicalThrowableItem[]{
                    (AlchemicalThrowableItem) ModItems.ALCHEMICAL_SPLASH_POTION.get(),
                    (AlchemicalThrowableItem) ModItems.ALCHEMICAL_LINGERING_POTION.get()}) {
                DispenserBlock.registerBehavior(item, item.dispenseBehavior());
            }
        });
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
