package com.mopiux.alquimia.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.Collection;
import java.util.List;

/**
 * Elixir alquímico: una poción con los efectos fijados en el caldero. Usa las etiquetas NBT
 * estándar de Minecraft ({@code CustomPotionEffects} y {@code CustomPotionColor}), así que el juego
 * aplica los efectos, muestra la descripción y el color sin lógica extra.
 */
public class AlchemicalPotionItem extends PotionItem {
    public AlchemicalPotionItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, Collection<MobEffectInstance> effects) {
        ItemStack stack = new ItemStack(item);
        PotionUtils.setCustomEffects(stack, effects);
        stack.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, PotionUtils.getColor(effects));
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return new ItemStack(this);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();
    }

    @Override
    public Component getName(ItemStack stack) {
        List<MobEffectInstance> effects = PotionUtils.getCustomEffects(stack);
        String base = getDescriptionId();
        if (effects.isEmpty()) return Component.translatable(base + ".empty");
        if (effects.size() > 3) return Component.translatable(base + ".many");
        Object[] names = new Object[effects.size()];
        for (int i = 0; i < names.length; i++) names[i] = effectName(effects.get(i));
        return Component.translatable(base + "." + effects.size(), names);
    }

    public static Component effectName(MobEffectInstance e) {
        MutableComponent name = e.getEffect().getDisplayName().copy();
        if (e.getAmplifier() > 0 && e.getAmplifier() < 10) {
            return Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + e.getAmplifier()));
        }
        return name;
    }
}
