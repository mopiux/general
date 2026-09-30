package com.mopiux.alquimia.alchemy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Un mapa alquímico: un plano 2D con zonas de esencia (efectos de poción) y zonas de peligro.
 * Se define por datapack en {@code data/<ns>/alquimia/maps/<nombre>.json}, así que otros mods o
 * modpacks pueden agregar mapas o cambiar los existentes.
 */
public final class AlchemyMap {
    /** Tamaño en unidades de cada celda de la niebla de guerra. */
    public static final float FOG_CELL = 4f;

    public record Zone(ResourceLocation effect, float x, float y, float radius, int duration, int maxAmplifier) {
        @Nullable
        public MobEffect mobEffect() {
            return ForgeRegistries.MOB_EFFECTS.getValue(effect);
        }

        public float distance(float px, float py) {
            float dx = px - x, dy = py - y;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        public boolean contains(float px, float py) {
            return distance(px, py) <= radius;
        }

        /** Nivel de pureza 1..3 según qué tan cerca del centro está el punto. */
        public int tierAt(float px, float py) {
            float d = distance(px, py) / radius;
            if (d <= 0.25f) return 3;
            if (d <= 0.6f) return 2;
            return 1;
        }
    }

    public record Hazard(float x, float y, float radius) {
        public boolean contains(float px, float py) {
            float dx = px - x, dy = py - y;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private final ResourceLocation id;
    private final List<ResourceLocation> dimensions;
    private final boolean fallback;
    private final float radius;
    private final List<Zone> zones;
    private final List<Hazard> hazards;

    public AlchemyMap(ResourceLocation id, List<ResourceLocation> dimensions, boolean fallback, float radius,
                      List<Zone> zones, List<Hazard> hazards) {
        this.id = id;
        this.dimensions = List.copyOf(dimensions);
        this.fallback = fallback;
        this.radius = radius;
        this.zones = List.copyOf(zones);
        this.hazards = List.copyOf(hazards);
    }

    public ResourceLocation id() {
        return id;
    }

    public List<ResourceLocation> dimensions() {
        return dimensions;
    }

    public boolean isFallback() {
        return fallback;
    }

    public float radius() {
        return radius;
    }

    public List<Zone> zones() {
        return zones;
    }

    public List<Hazard> hazards() {
        return hazards;
    }

    /** Cantidad de celdas de niebla por lado. */
    public int fogSize() {
        return (int) Math.ceil(radius * 2 / FOG_CELL);
    }

    public int fogIndex(float x, float y) {
        int n = fogSize();
        int cx = (int) Math.floor((x + radius) / FOG_CELL);
        int cy = (int) Math.floor((y + radius) / FOG_CELL);
        if (cx < 0 || cy < 0 || cx >= n || cy >= n) return -1;
        return cy * n + cx;
    }

    @Nullable
    public Zone zoneAt(float x, float y) {
        Zone best = null;
        float bestD = Float.MAX_VALUE;
        for (Zone z : zones) {
            float d = z.distance(x, y);
            if (d <= z.radius() && d < bestD) {
                best = z;
                bestD = d;
            }
        }
        return best;
    }

    @Nullable
    public Hazard hazardAt(float x, float y) {
        for (Hazard h : hazards) {
            if (h.contains(x, y)) return h;
        }
        return null;
    }

    @Nullable
    public Zone zoneFor(ResourceLocation effect) {
        for (Zone z : zones) {
            if (z.effect().equals(effect)) return z;
        }
        return null;
    }

    // ------------------------------------------------------------------ serialización
    public static AlchemyMap fromJson(ResourceLocation id, JsonObject json) {
        List<ResourceLocation> dims = new ArrayList<>();
        if (json.has("dimensions")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "dimensions")) {
                dims.add(new ResourceLocation(e.getAsString()));
            }
        }
        boolean fallback = GsonHelper.getAsBoolean(json, "fallback", false);
        float radius = GsonHelper.getAsFloat(json, "radius", 120f);
        List<Zone> zones = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "zones", new JsonArray())) {
            JsonObject o = e.getAsJsonObject();
            zones.add(new Zone(
                    new ResourceLocation(GsonHelper.getAsString(o, "effect")),
                    GsonHelper.getAsFloat(o, "x"),
                    GsonHelper.getAsFloat(o, "y"),
                    GsonHelper.getAsFloat(o, "radius", 7f),
                    GsonHelper.getAsInt(o, "duration", 3600),
                    GsonHelper.getAsInt(o, "max_amplifier", 2)));
        }
        List<Hazard> hazards = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "hazards", new JsonArray())) {
            JsonObject o = e.getAsJsonObject();
            hazards.add(new Hazard(GsonHelper.getAsFloat(o, "x"), GsonHelper.getAsFloat(o, "y"),
                    GsonHelper.getAsFloat(o, "radius", 6f)));
        }
        return new AlchemyMap(id, dims, fallback, radius, zones, hazards);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeVarInt(dimensions.size());
        for (ResourceLocation d : dimensions) buf.writeResourceLocation(d);
        buf.writeBoolean(fallback);
        buf.writeFloat(radius);
        buf.writeVarInt(zones.size());
        for (Zone z : zones) {
            buf.writeResourceLocation(z.effect());
            buf.writeFloat(z.x());
            buf.writeFloat(z.y());
            buf.writeFloat(z.radius());
            buf.writeVarInt(z.duration());
            buf.writeVarInt(z.maxAmplifier());
        }
        buf.writeVarInt(hazards.size());
        for (Hazard h : hazards) {
            buf.writeFloat(h.x());
            buf.writeFloat(h.y());
            buf.writeFloat(h.radius());
        }
    }

    public static AlchemyMap fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int nd = buf.readVarInt();
        List<ResourceLocation> dims = new ArrayList<>(nd);
        for (int i = 0; i < nd; i++) dims.add(buf.readResourceLocation());
        boolean fallback = buf.readBoolean();
        float radius = buf.readFloat();
        int nz = buf.readVarInt();
        List<Zone> zones = new ArrayList<>(nz);
        for (int i = 0; i < nz; i++) {
            zones.add(new Zone(buf.readResourceLocation(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt()));
        }
        int nh = buf.readVarInt();
        List<Hazard> hazards = new ArrayList<>(nh);
        for (int i = 0; i < nh; i++) hazards.add(new Hazard(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return new AlchemyMap(id, dims, fallback, radius, zones, hazards);
    }
}
