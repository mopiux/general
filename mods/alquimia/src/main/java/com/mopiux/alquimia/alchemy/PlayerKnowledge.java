package com.mopiux.alquimia.alchemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que un jugador sabe de alquimia: qué partes de cada mapa exploró (niebla de guerra),
 * qué esencias descubrió y qué ingredientes probó.
 */
public final class PlayerKnowledge {
    /** Radio (en unidades del mapa) que se revela alrededor de la mezcla al moverse. */
    public static final float REVEAL_RADIUS = 11f;

    private final Map<ResourceLocation, BitSet> fog = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> discovered = new HashMap<>();
    private final Set<ResourceLocation> ingredients = new LinkedHashSet<>();

    public BitSet fog(ResourceLocation map) {
        return fog.computeIfAbsent(map, k -> new BitSet());
    }

    public boolean isRevealed(AlchemyMap map, float x, float y) {
        int idx = map.fogIndex(x, y);
        return idx >= 0 && fog(map.id()).get(idx);
    }

    public boolean isRevealed(AlchemyMap map, int cellX, int cellY) {
        int n = map.fogSize();
        if (cellX < 0 || cellY < 0 || cellX >= n || cellY >= n) return false;
        return fog(map.id()).get(cellY * n + cellX);
    }

    /** Revela un círculo alrededor del punto. Devuelve true si algo cambió. */
    public boolean reveal(AlchemyMap map, float x, float y, float radius) {
        BitSet bits = fog(map.id());
        int n = map.fogSize();
        float cell = AlchemyMap.FOG_CELL;
        int cx0 = (int) Math.floor((x - radius + map.radius()) / cell);
        int cx1 = (int) Math.floor((x + radius + map.radius()) / cell);
        int cy0 = (int) Math.floor((y - radius + map.radius()) / cell);
        int cy1 = (int) Math.floor((y + radius + map.radius()) / cell);
        boolean changed = false;
        float r2 = radius * radius;
        for (int cy = Math.max(0, cy0); cy <= Math.min(n - 1, cy1); cy++) {
            for (int cx = Math.max(0, cx0); cx <= Math.min(n - 1, cx1); cx++) {
                float px = cx * cell - map.radius() + cell / 2f;
                float py = cy * cell - map.radius() + cell / 2f;
                float dx = px - x, dy = py - y;
                if (dx * dx + dy * dy <= r2) {
                    int idx = cy * n + cx;
                    if (!bits.get(idx)) {
                        bits.set(idx);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    public Set<ResourceLocation> discovered(ResourceLocation map) {
        return discovered.computeIfAbsent(map, k -> new LinkedHashSet<>());
    }

    public boolean isDiscovered(ResourceLocation map, ResourceLocation effect) {
        Set<ResourceLocation> s = discovered.get(map);
        return s != null && s.contains(effect);
    }

    public boolean discover(ResourceLocation map, ResourceLocation effect) {
        return discovered(map).add(effect);
    }

    public int totalDiscovered() {
        Set<ResourceLocation> all = new LinkedHashSet<>();
        for (Set<ResourceLocation> s : discovered.values()) all.addAll(s);
        return all.size();
    }

    public Set<ResourceLocation> ingredients() {
        return ingredients;
    }

    public boolean learnIngredient(ResourceLocation id) {
        return ingredients.add(id);
    }

    public void copyFrom(PlayerKnowledge other) {
        fog.clear();
        discovered.clear();
        ingredients.clear();
        other.fog.forEach((k, v) -> fog.put(k, (BitSet) v.clone()));
        other.discovered.forEach((k, v) -> discovered.put(k, new LinkedHashSet<>(v)));
        ingredients.addAll(other.ingredients);
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag fogTag = new CompoundTag();
        fog.forEach((k, v) -> fogTag.putLongArray(k.toString(), v.toLongArray()));
        tag.put("fog", fogTag);
        CompoundTag disc = new CompoundTag();
        discovered.forEach((k, v) -> {
            ListTag l = new ListTag();
            for (ResourceLocation e : v) l.add(StringTag.valueOf(e.toString()));
            disc.put(k.toString(), l);
        });
        tag.put("discovered", disc);
        ListTag ing = new ListTag();
        for (ResourceLocation r : ingredients) ing.add(StringTag.valueOf(r.toString()));
        tag.put("ingredients", ing);
        return tag;
    }

    public static PlayerKnowledge load(CompoundTag tag) {
        PlayerKnowledge k = new PlayerKnowledge();
        CompoundTag fogTag = tag.getCompound("fog");
        for (String key : fogTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) k.fog.put(id, BitSet.valueOf(fogTag.getLongArray(key)));
        }
        CompoundTag disc = tag.getCompound("discovered");
        for (String key : disc.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;
            Set<ResourceLocation> set = new LinkedHashSet<>();
            ListTag l = disc.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) {
                ResourceLocation e = ResourceLocation.tryParse(l.getString(i));
                if (e != null) set.add(e);
            }
            k.discovered.put(id, set);
        }
        ListTag ing = tag.getList("ingredients", Tag.TAG_STRING);
        for (int i = 0; i < ing.size(); i++) {
            ResourceLocation r = ResourceLocation.tryParse(ing.getString(i));
            if (r != null) k.ingredients.add(r);
        }
        return k;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static PlayerKnowledge read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new PlayerKnowledge() : load(tag);
    }
}
