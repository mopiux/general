package com.mopiux.alquimia.recipe;

import com.mopiux.alquimia.registry.ModItems;
import com.mopiux.alquimia.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Receta sin forma que conserva los efectos del elixir:
 * <ul>
 *     <li>elixir + pólvora → elixir arrojadizo</li>
 *     <li>elixir arrojadizo + aliento de dragón → elixir persistente</li>
 * </ul>
 */
public class AlchemicalThrowableRecipe extends CustomRecipe {
    public AlchemicalThrowableRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private record Match(ItemStack potion, Item result) {
    }

    private static Match find(CraftingContainer inv) {
        ItemStack potion = ItemStack.EMPTY;
        int gunpowder = 0, breath = 0, others = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.ALCHEMICAL_POTION.get()) || s.is(ModItems.ALCHEMICAL_SPLASH_POTION.get())) {
                if (!potion.isEmpty()) return null;
                potion = s;
            } else if (s.is(Items.GUNPOWDER)) {
                gunpowder++;
            } else if (s.is(Items.DRAGON_BREATH)) {
                breath++;
            } else {
                others++;
            }
        }
        if (potion.isEmpty() || others > 0) return null;
        if (potion.is(ModItems.ALCHEMICAL_POTION.get()) && gunpowder == 1 && breath == 0) {
            return new Match(potion, ModItems.ALCHEMICAL_SPLASH_POTION.get());
        }
        if (potion.is(ModItems.ALCHEMICAL_SPLASH_POTION.get()) && breath == 1 && gunpowder == 0) {
            return new Match(potion, ModItems.ALCHEMICAL_LINGERING_POTION.get());
        }
        return null;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return find(inv) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        Match m = find(inv);
        if (m == null) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(m.result());
        if (m.potion().getTag() != null) out.setTag(m.potion().getTag().copy());
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ALCHEMICAL_THROWABLE.get();
    }
}
