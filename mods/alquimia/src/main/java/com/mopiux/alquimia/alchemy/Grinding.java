package com.mopiux.alquimia.alchemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Molienda de ingredientes. Un ingrediente sin moler aporta la mitad de su camino; molido del
 * todo, el camino completo. El grado de molienda se guarda en el NBT del ítem.
 */
public final class Grinding {
    public static final String TAG = "AlquimiaGrind";

    private Grinding() {
    }

    public static float get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0f : Math.max(0f, Math.min(1f, tag.getFloat(TAG)));
    }

    public static void set(ItemStack stack, float value) {
        stack.getOrCreateTag().putFloat(TAG, Math.max(0f, Math.min(1f, value)));
    }

    /** Fracción del camino que aporta el ingrediente según su molienda. */
    public static float pathFraction(ItemStack stack) {
        return 0.5f + 0.5f * get(stack);
    }
}
