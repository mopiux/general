package com.mopiux.alquimia.alchemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * El estado de una mezcla dentro del caldero: dónde está el cursor en el mapa, qué camino falta
 * recorrer (lo que aportaron los ingredientes) y qué esencias ya se fijaron.
 */
public final class BrewState {
    public static final int MAX_WATER = 3;
    public static final int MAX_TRAIL = 480;
    public static final int MAX_PROLONGS = 2;
    public static final float DILUTE_AMOUNT = 12f;

    public record FixedEssence(ResourceLocation effect, int amplifier, int duration, boolean empowered) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("effect", effect.toString());
            t.putInt("amplifier", amplifier);
            t.putInt("duration", duration);
            t.putBoolean("empowered", empowered);
            return t;
        }

        static FixedEssence load(CompoundTag t) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("effect"));
            return new FixedEssence(id == null ? new ResourceLocation("minecraft:luck") : id, t.getInt("amplifier"),
                    t.getInt("duration"), t.getBoolean("empowered"));
        }
    }

    /** Visitante de puntos a lo largo del recorrido; devuelve false para detener la mezcla. */
    @FunctionalInterface
    public interface PointVisitor {
        boolean visit(float x, float y);
    }

    public float x, y;
    public final ArrayDeque<float[]> pending = new ArrayDeque<>();
    public final List<float[]> trail = new ArrayList<>();
    public final List<FixedEssence> fixed = new ArrayList<>();
    public int water;
    public int prolongs;
    public int ingredientsUsed;

    public boolean isPristine() {
        return pending.isEmpty() && fixed.isEmpty() && ingredientsUsed == 0 && x == 0 && y == 0;
    }

    public void reset() {
        x = 0;
        y = 0;
        pending.clear();
        trail.clear();
        fixed.clear();
        water = 0;
        prolongs = 0;
        ingredientsUsed = 0;
    }

    public float pendingLength() {
        float l = 0;
        for (float[] s : pending) l += Mth.sqrt(s[0] * s[0] + s[1] * s[1]);
        return l;
    }

    /** Punto final del camino pendiente (donde terminaría la mezcla si se remueve todo). */
    public float[] pendingEnd() {
        float ex = x, ey = y;
        for (float[] s : pending) {
            ex += s[0];
            ey += s[1];
        }
        return new float[]{ex, ey};
    }

    public void append(List<float[]> segments) {
        for (float[] s : segments) pending.addLast(new float[]{s[0], s[1]});
    }

    public void rotatePending(float degrees) {
        float rad = (float) Math.toRadians(degrees);
        float c = Mth.cos(rad), s = Mth.sin(rad);
        for (float[] seg : pending) {
            float nx = seg[0] * c - seg[1] * s;
            float ny = seg[0] * s + seg[1] * c;
            seg[0] = nx;
            seg[1] = ny;
        }
    }

    public void scalePending(float factor) {
        for (float[] seg : pending) {
            seg[0] *= factor;
            seg[1] *= factor;
        }
    }

    public void mirrorPending() {
        for (float[] seg : pending) seg[0] = -seg[0];
    }

    /**
     * Avanza la mezcla {@code distance} unidades por el camino pendiente, llamando al visitante
     * cada {@code step} unidades. Devuelve la distancia realmente recorrida.
     */
    public float advance(float distance, float step, PointVisitor visitor) {
        float travelled = 0;
        float sinceSample = 0;
        while (distance > 1e-5f && !pending.isEmpty()) {
            float[] seg = pending.peekFirst();
            float len = Mth.sqrt(seg[0] * seg[0] + seg[1] * seg[1]);
            if (len < 1e-4f) {
                pending.pollFirst();
                continue;
            }
            float move = Math.min(distance, Math.min(len, step - sinceSample));
            float t = move / len;
            x += seg[0] * t;
            y += seg[1] * t;
            seg[0] -= seg[0] * t;
            seg[1] -= seg[1] * t;
            distance -= move;
            travelled += move;
            sinceSample += move;
            if (move >= len - 1e-5f) pending.pollFirst();
            recordTrail();
            if (sinceSample >= step - 1e-5f) {
                sinceSample = 0;
                if (!visitor.visit(x, y)) return travelled;
            }
        }
        if (sinceSample > 0) visitor.visit(x, y);
        return travelled;
    }

    /** Acerca la mezcla al centro del mapa (agregar agua la diluye). */
    public void pullTowardOrigin(float amount) {
        float d = Mth.sqrt(x * x + y * y);
        if (d <= amount) {
            x = 0;
            y = 0;
        } else {
            float k = (d - amount) / d;
            x *= k;
            y *= k;
        }
        recordTrail();
    }

    private void recordTrail() {
        if (!trail.isEmpty()) {
            float[] last = trail.get(trail.size() - 1);
            float dx = last[0] - x, dy = last[1] - y;
            if (dx * dx + dy * dy < 1.0f) return;
        }
        trail.add(new float[]{x, y});
        if (trail.size() > MAX_TRAIL) trail.remove(0);
    }

    public boolean hasFixed(ResourceLocation effect) {
        for (FixedEssence f : fixed) if (f.effect().equals(effect)) return true;
        return false;
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("x", x);
        t.putFloat("y", y);
        t.putInt("water", water);
        t.putInt("prolongs", prolongs);
        t.putInt("ingredients", ingredientsUsed);
        ListTag p = new ListTag();
        for (float[] s : pending) {
            p.add(FloatTag.valueOf(s[0]));
            p.add(FloatTag.valueOf(s[1]));
        }
        t.put("pending", p);
        ListTag tr = new ListTag();
        for (float[] s : trail) {
            tr.add(FloatTag.valueOf(s[0]));
            tr.add(FloatTag.valueOf(s[1]));
        }
        t.put("trail", tr);
        ListTag f = new ListTag();
        for (FixedEssence e : fixed) f.add(e.save());
        t.put("fixed", f);
        return t;
    }

    public void load(CompoundTag t) {
        reset();
        x = t.getFloat("x");
        y = t.getFloat("y");
        water = Mth.clamp(t.getInt("water"), 0, MAX_WATER);
        prolongs = t.getInt("prolongs");
        ingredientsUsed = t.getInt("ingredients");
        ListTag p = t.getList("pending", Tag.TAG_FLOAT);
        for (int i = 0; i + 1 < p.size(); i += 2) pending.addLast(new float[]{p.getFloat(i), p.getFloat(i + 1)});
        ListTag tr = t.getList("trail", Tag.TAG_FLOAT);
        for (int i = 0; i + 1 < tr.size(); i += 2) trail.add(new float[]{tr.getFloat(i), tr.getFloat(i + 1)});
        ListTag f = t.getList("fixed", Tag.TAG_COMPOUND);
        for (int i = 0; i < f.size(); i++) fixed.add(FixedEssence.load(f.getCompound(i)));
    }

    public BrewState copy() {
        BrewState b = new BrewState();
        b.load(save());
        return b;
    }

    /** Recorta la estela a los últimos puntos (para enviar menos datos por red). */
    public void trimTrail(int max) {
        Iterator<float[]> it = trail.iterator();
        int remove = trail.size() - max;
        while (remove-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
