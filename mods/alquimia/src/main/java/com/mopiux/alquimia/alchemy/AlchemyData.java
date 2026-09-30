package com.mopiux.alquimia.alchemy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mopiux.alquimia.Alquimia;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Todos los mapas e ingredientes alquímicos cargados. Hay una copia para el servidor (cargada
 * desde los datapacks) y otra para el cliente (recibida por red al entrar al mundo).
 */
public final class AlchemyData {
    public static final AlchemyData EMPTY = new AlchemyData(Map.of(), List.of());
    private static volatile AlchemyData server = EMPTY;
    private static volatile AlchemyData client = EMPTY;

    private final Map<ResourceLocation, AlchemyMap> maps;
    private final List<AlchemyIngredient> ingredients;

    public AlchemyData(Map<ResourceLocation, AlchemyMap> maps, List<AlchemyIngredient> ingredients) {
        this.maps = Map.copyOf(maps);
        List<AlchemyIngredient> sorted = new ArrayList<>(ingredients);
        sorted.sort(Comparator.comparing(i -> i.id().toString()));
        this.ingredients = List.copyOf(sorted);
    }

    public static AlchemyData get(boolean clientSide) {
        return clientSide ? client : server;
    }

    public static AlchemyData get(Level level) {
        return get(level.isClientSide);
    }

    public static AlchemyData server() {
        return server;
    }

    public static void setClient(AlchemyData data) {
        client = data;
    }

    public Collection<AlchemyMap> maps() {
        return maps.values();
    }

    public List<AlchemyIngredient> ingredients() {
        return ingredients;
    }

    @Nullable
    public AlchemyMap map(ResourceLocation id) {
        return maps.get(id);
    }

    /** Mapa que corresponde a una dimensión: primero uno que la nombre, si no el mapa por defecto. */
    @Nullable
    public AlchemyMap mapFor(ResourceLocation dimension) {
        AlchemyMap fallback = null;
        for (AlchemyMap m : maps.values()) {
            if (m.dimensions().contains(dimension)) return m;
            if (m.isFallback() && (fallback == null || m.id().compareTo(fallback.id()) < 0)) fallback = m;
        }
        if (fallback != null) return fallback;
        return maps.values().stream().min(Comparator.comparing(AlchemyMap::id)).orElse(null);
    }

    @Nullable
    public AlchemyIngredient find(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (AlchemyIngredient i : ingredients) {
            if (i.matches(stack)) return i;
        }
        return null;
    }

    @Nullable
    public AlchemyIngredient ingredient(ResourceLocation id) {
        for (AlchemyIngredient i : ingredients) if (i.id().equals(id)) return i;
        return null;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(maps.size());
        for (AlchemyMap m : maps.values()) m.toNetwork(buf);
        buf.writeVarInt(ingredients.size());
        for (AlchemyIngredient i : ingredients) i.toNetwork(buf);
    }

    public static AlchemyData read(FriendlyByteBuf buf) {
        int nm = buf.readVarInt();
        Map<ResourceLocation, AlchemyMap> maps = new LinkedHashMap<>();
        for (int i = 0; i < nm; i++) {
            AlchemyMap m = AlchemyMap.fromNetwork(buf);
            maps.put(m.id(), m);
        }
        int ni = buf.readVarInt();
        List<AlchemyIngredient> ings = new ArrayList<>(ni);
        for (int i = 0; i < ni; i++) ings.add(AlchemyIngredient.fromNetwork(buf));
        return new AlchemyData(maps, ings);
    }

    /** Carga {@code data/<ns>/alquimia/maps/*.json} y {@code data/<ns>/alquimia/ingredients/*.json}. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().setLenient().create();

        public Loader() {
            super(GSON, "alquimia");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, AlchemyMap> maps = new LinkedHashMap<>();
            List<AlchemyIngredient> ings = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                try {
                    if (path.startsWith("maps/")) {
                        ResourceLocation id = new ResourceLocation(file.getNamespace(), path.substring(5));
                        maps.put(id, AlchemyMap.fromJson(id, e.getValue().getAsJsonObject()));
                    } else if (path.startsWith("ingredients/")) {
                        ResourceLocation id = new ResourceLocation(file.getNamespace(), path.substring(12));
                        ings.add(AlchemyIngredient.fromJson(id, e.getValue().getAsJsonObject()));
                    }
                } catch (Exception ex) {
                    Alquimia.LOGGER.error("No se pudo leer el archivo alquímico {}: {}", file, ex.getMessage());
                }
            }
            server = new AlchemyData(maps, ings);
            Alquimia.LOGGER.info("Alquimia: {} mapas y {} ingredientes cargados", maps.size(), ings.size());
        }
    }
}
