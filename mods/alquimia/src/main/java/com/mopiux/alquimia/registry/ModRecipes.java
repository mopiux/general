package com.mopiux.alquimia.registry;

import com.mopiux.alquimia.Alquimia;
import com.mopiux.alquimia.recipe.AlchemicalThrowableRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Alquimia.MOD_ID);

    public static final RegistryObject<RecipeSerializer<AlchemicalThrowableRecipe>> ALCHEMICAL_THROWABLE =
            SERIALIZERS.register("alchemical_throwable", () -> new SimpleCraftingRecipeSerializer<>(AlchemicalThrowableRecipe::new));

    private ModRecipes() {
    }
}
