package com.mopiux.alquimia.alchemy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Un ingrediente alquímico. Puede ser de dos tipos:
 * <ul>
 *     <li><b>camino</b>: al agregarlo, su trazado (una polilínea) se suma al final del camino pendiente;</li>
 *     <li><b>transformación</b>: modifica el camino pendiente (girarlo, escalarlo o reflejarlo).</li>
 * </ul>
 * Se define por datapack en {@code data/<ns>/alquimia/ingredients/<nombre>.json}.
 */
public final class AlchemyIngredient {
    public enum Transform {
        NONE, ROTATE, SCALE, MIRROR;

        static Transform byName(String s) {
            for (Transform t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            throw new JsonParseException("Transformación desconocida: " + s);
        }
    }

    private final ResourceLocation id;
    private final Ingredient ingredient;
    /** Puntos relativos (x0,y0,x1,y1...). El primero siempre es (0,0). */
    private final float[] points;
    private final Transform transform;
    private final float amount;
    private final float length;

    public AlchemyIngredient(ResourceLocation id, Ingredient ingredient, float[] points, Transform transform, float amount) {
        this.id = id;
        this.ingredient = ingredient;
        this.points = points;
        this.transform = transform;
        this.amount = amount;
        float len = 0;
        for (int i = 2; i + 1 < points.length; i += 2) {
            float dx = points[i] - points[i - 2], dy = points[i + 1] - points[i - 1];
            len += (float) Math.sqrt(dx * dx + dy * dy);
        }
        this.length = len;
    }

    public ResourceLocation id() {
        return id;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ingredient.test(stack);
    }

    public float[] points() {
        return points;
    }

    public Transform transform() {
        return transform;
    }

    public float amount() {
        return amount;
    }

    public boolean isTransform() {
        return transform != Transform.NONE;
    }

    public float length() {
        return length;
    }

    /**
     * Devuelve los segmentos (dx, dy) del camino recortado al porcentaje indicado del largo total.
     * Un ingrediente sin moler recorre la mitad de su camino; molido del todo, el camino completo.
     */
    public List<float[]> segments(float fraction) {
        List<float[]> out = new ArrayList<>();
        float budget = length * Math.max(0f, Math.min(1f, fraction));
        for (int i = 2; i + 1 < points.length && budget > 1e-4f; i += 2) {
            float dx = points[i] - points[i - 2], dy = points[i + 1] - points[i - 1];
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 1e-5f) continue;
            if (l <= budget) {
                out.add(new float[]{dx, dy});
                budget -= l;
            } else {
                float t = budget / l;
                out.add(new float[]{dx * t, dy * t});
                budget = 0;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ serialización
    public static AlchemyIngredient fromJson(ResourceLocation id, JsonObject json) {
        Ingredient ing = Ingredient.fromJson(json.get("ingredient"));
        Transform tr = Transform.NONE;
        float amount = 0;
        float[] pts = new float[]{0, 0};
        if (json.has("transform")) {
            JsonObject t = GsonHelper.getAsJsonObject(json, "transform");
            tr = Transform.byName(GsonHelper.getAsString(t, "type"));
            amount = switch (tr) {
                case ROTATE -> GsonHelper.getAsFloat(t, "angle", 90f);
                case SCALE -> GsonHelper.getAsFloat(t, "factor", 0.5f);
                default -> 0f;
            };
        } else {
            JsonArray arr = GsonHelper.getAsJsonArray(json, "path");
            List<Float> list = new ArrayList<>();
            list.add(0f);
            list.add(0f);
            for (JsonElement e : arr) {
                JsonArray p = e.getAsJsonArray();
                list.add(p.get(0).getAsFloat());
                list.add(p.get(1).getAsFloat());
            }
            pts = new float[list.size()];
            for (int i = 0; i < pts.length; i++) pts[i] = list.get(i);
        }
        return new AlchemyIngredient(id, ing, pts, tr, amount);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        ingredient.toNetwork(buf);
        buf.writeEnum(transform);
        buf.writeFloat(amount);
        buf.writeVarInt(points.length);
        for (float f : points) buf.writeFloat(f);
    }

    public static AlchemyIngredient fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        Ingredient ing = Ingredient.fromNetwork(buf);
        Transform tr = buf.readEnum(Transform.class);
        float amount = buf.readFloat();
        int n = buf.readVarInt();
        float[] pts = new float[n];
        for (int i = 0; i < n; i++) pts[i] = buf.readFloat();
        return new AlchemyIngredient(id, ing, pts, tr, amount);
    }
}
