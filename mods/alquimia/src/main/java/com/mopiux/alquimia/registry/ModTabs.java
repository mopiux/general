package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.item.AlchemicalPotionItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Alquimia.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.alquimia.main"))
            .icon(() -> new ItemStack(ModItems.ALCHEMICAL_CAULDRON.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.ALCHEMICAL_CAULDRON.get());
                out.accept(ModItems.MORTAR.get());
                out.accept(ModItems.GRIMOIRE.get());
                out.accept(ModItems.SALT.get());
                out.accept(ModItems.CINNABAR.get());
                out.accept(ModItems.QUICKSILVER.get());
                out.accept(ModItems.SULFUR.get());
                out.accept(ModItems.SALT_ORE.get());
                out.accept(ModItems.DEEPSLATE_SALT_ORE.get());
                out.accept(ModItems.CINNABAR_ORE.get());
                out.accept(ModItems.DEEPSLATE_CINNABAR_ORE.get());
                out.accept(ModItems.NETHER_SULFUR_ORE.get());
                out.accept(ModItems.SALT_BLOCK.get());
                out.accept(ModItems.CINNABAR_BLOCK.get());
                out.accept(ModItems.SULFUR_BLOCK.get());
                // Ejemplos de elixires para el modo creativo
                out.accept(AlchemicalPotionItem.create(ModItems.ALCHEMICAL_POTION.get(), List.of(
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 3600, 1),
                        new MobEffectInstance(MobEffects.JUMP, 3600, 1))));
                out.accept(AlchemicalPotionItem.create(ModItems.ALCHEMICAL_POTION.get(), List.of(
                        new MobEffectInstance(MobEffects.NIGHT_VISION, 6000, 0),
                        new MobEffectInstance(MobEffects.WATER_BREATHING, 6000, 0),
                        new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 1200, 0))));
                out.accept(AlchemicalPotionItem.create(ModItems.ALCHEMICAL_SPLASH_POTION.get(), List.of(
                        new MobEffectInstance(MobEffects.REGENERATION, 900, 1))));
                out.accept(AlchemicalPotionItem.create(ModItems.ALCHEMICAL_LINGERING_POTION.get(), List.of(
                        new MobEffectInstance(MobEffects.POISON, 900, 0),
                        new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 900, 1))));
            })
            .build());

    private ModTabs() {
    }

    @SuppressWarnings("unused")
    private static ResourceLocation id(String p) {
        return new ResourceLocation(Alquimia.MOD_ID, p);
    }
}
