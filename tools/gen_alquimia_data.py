#!/usr/bin/env python3
"""Genera los JSON de Alquimia Cartográfica: modelos, estados de bloque, recetas, botines,
etiquetas, generación de mundo, mapas alquímicos, ingredientes, logros y traducciones.

    python3 tools/gen_alquimia_data.py

Se regeneran todos los archivos listados acá; no editar los JSON a mano.
"""
import json
import math
import os
import shutil
import sys

MOD = "alquimia"
ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", MOD, "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data")

written = []


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")
    written.append(path)


def asset(*p):
    return os.path.join(ASSETS, *p)


def data(ns, *p):
    return os.path.join(DATA, ns, *p)


# Limpieza de carpetas generadas (evita archivos huérfanos)
for d in [asset("blockstates"), asset("models"), asset("lang"), data(MOD, "loot_tables"), data(MOD, "recipes"),
          data(MOD, "tags"), data(MOD, "worldgen"), data(MOD, "alquimia"), data(MOD, "advancements"),
          data(MOD, "forge"), data("forge", "tags"), data("minecraft", "tags")]:
    if os.path.isdir(d):
        shutil.rmtree(d)

# =============================================================================== bloques y modelos
ORES = {
    # id: (textura base de vanilla, overlay)
    "salt_ore": ("minecraft:block/stone", "salt_ore_overlay"),
    "deepslate_salt_ore": ("minecraft:block/deepslate", "deepslate_salt_ore_overlay"),
    "cinnabar_ore": ("minecraft:block/stone", "cinnabar_ore_overlay"),
    "deepslate_cinnabar_ore": ("minecraft:block/deepslate", "deepslate_cinnabar_ore_overlay"),
    "nether_sulfur_ore": ("minecraft:block/netherrack", "nether_sulfur_ore_overlay"),
}
STORAGE = ["salt_block", "cinnabar_block", "sulfur_block"]
DIRS = ["down", "up", "north", "south", "west", "east"]


def simple_blockstate(name):
    write(asset("blockstates", name + ".json"), {"variants": {"": {"model": f"{MOD}:block/{name}"}}})


for ore, (base, ov) in ORES.items():
    simple_blockstate(ore)
    faces_base = {d: {"texture": "#base", "cullface": d} for d in DIRS}
    faces_ov = {d: {"texture": "#overlay", "cullface": d} for d in DIRS}
    write(asset("models", "block", ore + ".json"), {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout_mipped",
        "textures": {"particle": base, "base": base, "overlay": f"{MOD}:block/{ov}"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces_base},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces_ov},
        ],
    })
    write(asset("models", "item", ore + ".json"), {"parent": f"{MOD}:block/{ore}"})

for b in STORAGE:
    simple_blockstate(b)
    write(asset("models", "block", b + ".json"), {"parent": "minecraft:block/cube_all",
                                                   "textures": {"all": f"{MOD}:block/{b}"}})
    write(asset("models", "item", b + ".json"), {"parent": f"{MOD}:block/{b}"})

simple_blockstate("alchemical_cauldron")
write(asset("models", "block", "alchemical_cauldron.json"), {
    "parent": "minecraft:block/cauldron",
    "textures": {
        "particle": f"{MOD}:block/alchemical_cauldron_side",
        "top": f"{MOD}:block/alchemical_cauldron_top",
        "bottom": f"{MOD}:block/alchemical_cauldron_bottom",
        "side": f"{MOD}:block/alchemical_cauldron_side",
        "inside": f"{MOD}:block/alchemical_cauldron_inner",
    },
})
write(asset("models", "item", "alchemical_cauldron.json"), {"parent": f"{MOD}:block/alchemical_cauldron"})


def box(frm, to, tex, faces=None, rotation=None, uv_all=None):
    faces = faces or DIRS
    el = {"from": frm, "to": to, "faces": {}}
    for d in faces:
        f = {"texture": tex}
        if uv_all:
            f["uv"] = uv_all
        el["faces"][d] = f
    if rotation:
        el["rotation"] = rotation
    return el


mortar_elements = [
    # base
    {"from": [3, 0, 3], "to": [13, 2, 13], "faces": {
        "down": {"texture": "#stone", "cullface": "down"},
        "up": {"texture": "#inner"},
        "north": {"texture": "#stone"}, "south": {"texture": "#stone"},
        "west": {"texture": "#stone"}, "east": {"texture": "#stone"}}},
    # paredes del cuenco
    box([2, 2, 2], [14, 6, 3], "#stone"),
    box([2, 2, 13], [14, 6, 14], "#stone"),
    box([2, 2, 3], [3, 6, 13], "#stone"),
    box([13, 2, 3], [14, 6, 13], "#stone"),
    # fondo interior
    {"from": [3, 2, 3], "to": [13, 2.5, 13], "faces": {"up": {"texture": "#inner"}}},
    # mano del mortero (inclinada)
    {"from": [9, 3, 7], "to": [11, 12, 9],
     "rotation": {"origin": [10, 3, 8], "axis": "z", "angle": -22.5},
     "faces": {
         "north": {"texture": "#pestle", "uv": [0, 0, 2, 9]},
         "south": {"texture": "#pestle", "uv": [2, 0, 4, 9]},
         "west": {"texture": "#pestle", "uv": [4, 0, 6, 9]},
         "east": {"texture": "#pestle", "uv": [6, 0, 8, 9]},
         "up": {"texture": "#pestle", "uv": [0, 0, 2, 2]},
         "down": {"texture": "#pestle", "uv": [0, 12, 2, 14]}}},
]
write(asset("models", "block", "mortar.json"), {
    "parent": "minecraft:block/block",
    "textures": {"particle": f"{MOD}:block/mortar", "stone": f"{MOD}:block/mortar",
                 "inner": f"{MOD}:block/mortar_inner", "pestle": f"{MOD}:block/pestle"},
    "elements": mortar_elements,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.5, 0.5, 0.5]},
    },
})
write(asset("blockstates", "mortar.json"), {"variants": {
    f"facing={f}": {"model": f"{MOD}:block/mortar", **({"y": y} if y else {})}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
write(asset("models", "item", "mortar.json"), {"parent": f"{MOD}:block/mortar"})

for item in ["salt", "cinnabar", "quicksilver", "sulfur", "grimoire"]:
    write(asset("models", "item", item + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}})
for item in ["alchemical_potion", "alchemical_splash_potion", "alchemical_lingering_potion"]:
    write(asset("models", "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {
        "layer0": f"{MOD}:item/{item}_overlay", "layer1": f"{MOD}:item/{item}"}})

# =============================================================================== botines
def self_drop(name):
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def ore_drop(name, item, count_min, count_max, formula):
    fn = [{"function": "minecraft:set_count",
           "count": {"type": "minecraft:uniform", "min": count_min, "max": count_max}}] if count_max > 1 else []
    if formula == "ore_drops":
        fn.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"})
    else:
        fn.append({"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                   "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}})
    fn.append({"function": "minecraft:explosion_decay"})
    silk = {"condition": "minecraft:match_tool", "predicate": {
        "enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
        "type": "minecraft:alternatives",
        "children": [
            {"type": "minecraft:item", "name": f"{MOD}:{name}", "conditions": [silk]},
            {"type": "minecraft:item", "name": f"{MOD}:{item}", "functions": fn},
        ]}]}]}


for b in ["alchemical_cauldron", "mortar"] + STORAGE:
    write(data(MOD, "loot_tables", "blocks", b + ".json"), self_drop(b))
write(data(MOD, "loot_tables", "blocks", "salt_ore.json"), ore_drop("salt_ore", "salt", 2, 4, "uniform"))
write(data(MOD, "loot_tables", "blocks", "deepslate_salt_ore.json"), ore_drop("deepslate_salt_ore", "salt", 2, 4, "uniform"))
write(data(MOD, "loot_tables", "blocks", "cinnabar_ore.json"), ore_drop("cinnabar_ore", "cinnabar", 1, 1, "ore_drops"))
write(data(MOD, "loot_tables", "blocks", "deepslate_cinnabar_ore.json"),
      ore_drop("deepslate_cinnabar_ore", "cinnabar", 1, 1, "ore_drops"))
write(data(MOD, "loot_tables", "blocks", "nether_sulfur_ore.json"), ore_drop("nether_sulfur_ore", "sulfur", 2, 5, "uniform"))

# =============================================================================== recetas
def shaped(name, pattern, key, result, count=1, category="misc"):
    write(data(MOD, "recipes", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": key, "result": {"item": result, "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    write(data(MOD, "recipes", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": ingredients, "result": {"item": result, "count": count}})


def cooking(name, ingredient, result, xp, time, kind="smelting"):
    write(data(MOD, "recipes", name + ".json"), {
        "type": f"minecraft:{kind}", "category": "misc", "ingredient": ingredient,
        "result": result, "experience": xp, "cookingtime": time})


I = lambda i: {"item": i}  # noqa
T = lambda t: {"tag": t}  # noqa

shaped("alchemical_cauldron", ["c c", "cKc", " s "],
       {"c": T("forge:ingots/copper"), "K": I("minecraft:cauldron"), "s": I(f"{MOD}:salt")},
       f"{MOD}:alchemical_cauldron", category="misc")
shaped("mortar", ["  |", "# #", " # "],
       {"|": I("minecraft:stick"), "#": T("minecraft:stone_crafting_materials")}, f"{MOD}:mortar")
shapeless("grimoire", [I("minecraft:book"), I(f"{MOD}:salt"), I("minecraft:ink_sac"), I("minecraft:feather")],
          f"{MOD}:grimoire")
shapeless("gunpowder_from_sulfur", [I(f"{MOD}:sulfur"), T("minecraft:coals"), I(f"{MOD}:salt")], "minecraft:gunpowder", 2)
for item, block in (("salt", "salt_block"), ("cinnabar", "cinnabar_block"), ("sulfur", "sulfur_block")):
    shaped(block, ["###", "###", "###"], {"#": I(f"{MOD}:{item}")}, f"{MOD}:{block}", category="building")
    shapeless(f"{item}_from_{block}", [I(f"{MOD}:{block}")], f"{MOD}:{item}", 9)
for kind, time in (("smelting", 200), ("blasting", 100)):
    cooking(f"quicksilver_from_{kind}", I(f"{MOD}:cinnabar"), f"{MOD}:quicksilver", 0.7, time, kind)
    cooking(f"salt_from_ore_{kind}", [I(f"{MOD}:salt_ore"), I(f"{MOD}:deepslate_salt_ore")], f"{MOD}:salt", 0.3, time, kind)
    cooking(f"quicksilver_from_ore_{kind}", [I(f"{MOD}:cinnabar_ore"), I(f"{MOD}:deepslate_cinnabar_ore")],
            f"{MOD}:quicksilver", 0.8, time, kind)
    cooking(f"sulfur_from_ore_{kind}", I(f"{MOD}:nether_sulfur_ore"), f"{MOD}:sulfur", 0.3, time, kind)
write(data(MOD, "recipes", "alchemical_throwable.json"), {"type": f"{MOD}:alchemical_throwable", "category": "misc"})

# =============================================================================== etiquetas
def tag(ns, kind, name, values):
    write(data(ns, "tags", kind, name + ".json"), {"replace": False, "values": values})


ore_ids = [f"{MOD}:{o}" for o in ORES]
tag("minecraft", "blocks", "mineable/pickaxe", ore_ids + [f"{MOD}:{b}" for b in STORAGE] +
    [f"{MOD}:alchemical_cauldron", f"{MOD}:mortar"])
tag("minecraft", "blocks", "needs_stone_tool", [f"{MOD}:salt_ore", f"{MOD}:deepslate_salt_ore",
                                                f"{MOD}:nether_sulfur_ore", f"{MOD}:alchemical_cauldron"])
tag("minecraft", "blocks", "needs_iron_tool", [f"{MOD}:cinnabar_ore", f"{MOD}:deepslate_cinnabar_ore",
                                               f"{MOD}:cinnabar_block"])
for kind in ("blocks", "items"):
    tag("forge", kind, "ores", ore_ids)
    tag("forge", kind, "ores/salt", [f"{MOD}:salt_ore", f"{MOD}:deepslate_salt_ore"])
    tag("forge", kind, "ores/cinnabar", [f"{MOD}:cinnabar_ore", f"{MOD}:deepslate_cinnabar_ore"])
    tag("forge", kind, "ores/sulfur", [f"{MOD}:nether_sulfur_ore"])
    tag("forge", kind, "ores_in_ground/stone", [f"{MOD}:salt_ore", f"{MOD}:cinnabar_ore"])
    tag("forge", kind, "ores_in_ground/deepslate", [f"{MOD}:deepslate_salt_ore", f"{MOD}:deepslate_cinnabar_ore"])
    tag("forge", kind, "ores_in_ground/netherrack", [f"{MOD}:nether_sulfur_ore"])
    tag("forge", kind, "storage_blocks", [f"{MOD}:{b}" for b in STORAGE])
    for b in STORAGE:
        tag("forge", kind, "storage_blocks/" + b.replace("_block", ""), [f"{MOD}:{b}"])
tag("forge", "items", "dusts/salt", [f"{MOD}:salt"])
tag("forge", "items", "dusts/sulfur", [f"{MOD}:sulfur"])
tag("forge", "items", "dusts", [f"{MOD}:salt", f"{MOD}:sulfur"])
tag("forge", "items", "gems/cinnabar", [f"{MOD}:cinnabar"])
tag("forge", "items", "gems", [f"{MOD}:cinnabar"])
tag("forge", "items", "salt", [f"{MOD}:salt"])
tag(MOD, "blocks", "heat_sources", ["minecraft:campfire", "minecraft:fire", "minecraft:magma_block"])
tag(MOD, "blocks", "strong_heat_sources", ["minecraft:soul_campfire", "minecraft:soul_fire", "minecraft:lava"])

# =============================================================================== generación de mundo
def ore_feature(name, targets, size, discard):
    write(data(MOD, "worldgen", "configured_feature", name + ".json"), {
        "type": "minecraft:ore",
        "config": {"size": size, "discard_chance_on_air_exposure": discard, "targets": targets}})


def placed(name, key, height):
    write(data(MOD, "worldgen", "placed_feature", name + ".json"), {
        "feature": f"{MOD}:{name}",
        "placement": [{"type": f"{MOD}:config_count", "key": key}, {"type": "minecraft:in_square"},
                      {"type": "minecraft:height_range", "height": height}, {"type": "minecraft:biome"}]})


stone_t = {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}
deep_t = {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}
nether_t = {"predicate_type": "minecraft:block_match", "block": "minecraft:netherrack"}
ore_feature("salt_ore", [{"target": stone_t, "state": {"Name": f"{MOD}:salt_ore"}},
                         {"target": deep_t, "state": {"Name": f"{MOD}:deepslate_salt_ore"}}], 10, 0.0)
ore_feature("cinnabar_ore", [{"target": stone_t, "state": {"Name": f"{MOD}:cinnabar_ore"}},
                             {"target": deep_t, "state": {"Name": f"{MOD}:deepslate_cinnabar_ore"}}], 7, 0.25)
ore_feature("nether_sulfur_ore", [{"target": nether_t, "state": {"Name": f"{MOD}:nether_sulfur_ore"}}], 12, 0.0)
placed("salt_ore", "salt", {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -40},
                            "max_inclusive": {"absolute": 96}})
placed("cinnabar_ore", "cinnabar", {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -64},
                                    "max_inclusive": {"absolute": 32}})
placed("nether_sulfur_ore", "sulfur", {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 10},
                                       "max_inclusive": {"below_top": 10}})
write(data(MOD, "forge", "biome_modifier", "overworld_ores.json"), {
    "type": "forge:add_features", "biomes": "#minecraft:is_overworld",
    "features": [f"{MOD}:salt_ore", f"{MOD}:cinnabar_ore"], "step": "underground_ores"})
write(data(MOD, "forge", "biome_modifier", "nether_ores.json"), {
    "type": "forge:add_features", "biomes": "#minecraft:is_nether",
    "features": [f"{MOD}:nether_sulfur_ore"], "step": "underground_decoration"})

# =============================================================================== mapas alquímicos
def polar(angle, dist):
    a = math.radians(angle)
    return round(math.cos(a) * dist, 1), round(math.sin(a) * dist, 1)


def zone(effect, angle, dist, radius=7, duration=3600, max_amp=2):
    x, y = polar(angle, dist)
    return {"effect": "minecraft:" + effect, "x": x, "y": y, "radius": radius, "duration": duration,
            "max_amplifier": max_amp}


def hz(x, y, r=5):
    return {"x": x, "y": y, "radius": r}


OVERWORLD = {
    "dimensions": ["minecraft:overworld"], "fallback": True, "radius": 120,
    "zones": [
        # anillo cercano (fáciles)
        zone("speed", 55, 30), zone("jump_boost", 125, 32), zone("night_vision", 195, 30),
        zone("fire_resistance", 335, 32), zone("water_breathing", 265, 34),
        zone("slowness", 10, 38, 6, 1800), zone("poison", 225, 36, 6, 900), zone("weakness", 95, 40, 6, 1800),
        # anillo medio
        zone("strength", 40, 62), zone("regeneration", 150, 60, 7, 900), zone("invisibility", 100, 70),
        zone("haste", 330, 64), zone("slow_falling", 210, 62, 7, 1800), zone("resistance", 285, 64, 7, 1800),
        zone("luck", 250, 72, 6, 6000), zone("absorption", 5, 70, 6, 2400), zone("instant_health", 175, 72, 6, 1, 1),
        zone("hunger", 75, 56, 6, 900), zone("nausea", 305, 48, 6, 600), zone("mining_fatigue", 240, 48, 6, 1200),
        zone("blindness", 125, 52, 6, 600), zone("glowing", 20, 52, 6, 3600),
        # anillo lejano (raras)
        zone("health_boost", 60, 95, 6, 2400), zone("saturation", 160, 92, 6, 1, 1), zone("conduit_power", 290, 98, 6, 3600),
        zone("dolphins_grace", 245, 100, 6, 1200, 1), zone("hero_of_the_village", 105, 104, 5, 6000, 1),
        zone("levitation", 200, 98, 5, 200, 1), zone("wither", 340, 96, 5, 400, 1), zone("darkness", 270, 110, 5, 400, 0),
        zone("instant_damage", 15, 100, 5, 1, 1), zone("unluck", 135, 88, 5, 6000, 0),
    ],
    "hazards": [hz(42, 28), hz(-38, 16), hz(14, -48), hz(-44, -48, 6), hz(60, -10), hz(-8, 55), hz(70, 60, 6),
                hz(-70, -60, 6), hz(80, -60, 6), hz(-78, 48, 6), hz(5, 88, 6), hz(-5, -80, 6), hz(100, -5),
                hz(-100, 0), hz(38, 72, 4), hz(-40, 84, 4)],
}
NETHER = {
    "dimensions": ["minecraft:the_nether"], "radius": 100,
    "zones": [
        zone("fire_resistance", 300, 24, 8, 4800), zone("weakness", 120, 30, 6, 1800), zone("slowness", 180, 30, 6, 1800),
        zone("strength", 40, 40), zone("regeneration", 150, 45, 7, 900), zone("resistance", 250, 55, 7, 1800),
        zone("absorption", 95, 60, 6, 2400), zone("haste", 0, 60, 6, 3600), zone("health_boost", 200, 75, 6, 2400),
        zone("wither", 320, 70, 6, 400, 1), zone("nausea", 60, 75, 6, 600), zone("poison", 270, 85, 5, 900),
        zone("night_vision", 130, 80, 5, 4800), zone("instant_health", 350, 90, 5, 1, 1), zone("saturation", 170, 90, 5, 1, 1),
    ],
    "hazards": [hz(20, 10, 4), hz(5, 32), hz(35, -25), hz(-28, -22), hz(10, -40, 6), hz(-45, 50, 6), hz(55, 40, 6),
                hz(-60, -35, 6), hz(75, -40), hz(-75, 15, 5), hz(15, 75, 5), hz(80, 25, 5)],
}
END = {
    "dimensions": ["minecraft:the_end"], "radius": 100,
    "zones": [
        zone("levitation", 90, 30, 8, 200, 1), zone("slow_falling", 270, 30, 8, 2400), zone("night_vision", 180, 35, 7, 4800),
        zone("invisibility", 0, 40, 7, 3600), zone("speed", 45, 55, 6), zone("jump_boost", 135, 55, 6),
        zone("glowing", 225, 55, 6), zone("darkness", 315, 55, 6, 400, 0), zone("resistance", 0, 80, 5, 1800),
        zone("absorption", 90, 80, 5, 2400), zone("regeneration", 180, 80, 5, 900), zone("luck", 270, 80, 5, 6000),
        zone("health_boost", 45, 90, 5, 2400),
    ],
    "hazards": [hz(25, 25), hz(-25, 25), hz(-25, -25), hz(25, -25), hz(0, 60, 6), hz(60, 0, 6), hz(0, -60, 6),
                hz(-60, 0, 6), hz(42, 70, 5), hz(-60, -60, 5)],
}


def check_map(name, m):
    zs, hs, R = m["zones"], m["hazards"], m["radius"]
    problems = []
    for i, a in enumerate(zs):
        if math.hypot(a["x"], a["y"]) + a["radius"] > R:
            problems.append(f"{a['effect']} fuera del mapa")
        for b in zs[i + 1:]:
            if math.hypot(a["x"] - b["x"], a["y"] - b["y"]) < a["radius"] + b["radius"] + 1:
                problems.append(f"{a['effect']} se superpone con {b['effect']}")
        for h in hs:
            if math.hypot(a["x"] - h["x"], a["y"] - h["y"]) < a["radius"] + h["radius"] + 0.5:
                problems.append(f"{a['effect']} toca el peligro ({h['x']},{h['y']})")
    for h in hs:
        if math.hypot(h["x"], h["y"]) < h["radius"] + 12:
            problems.append(f"peligro ({h['x']},{h['y']}) demasiado cerca del centro")
    if problems:
        print(f"[{name}] PROBLEMAS:\n  " + "\n  ".join(problems))
        sys.exit(1)


for name, m in (("overworld", OVERWORLD), ("the_nether", NETHER), ("the_end", END)):
    check_map(name, m)
    write(data(MOD, "alquimia", "maps", name + ".json"), m)


# =============================================================================== ingredientes
def curve(angle, dist, bend=0.0, wobble=0.0, steps=10):
    """Camino desde (0,0) hasta el punto polar, curvado (bend = desvío lateral relativo)
    y opcionalmente ondulado (wobble)."""
    ex, ey = polar(angle, dist)
    nx, ny = -ey / max(dist, 1e-6), ex / max(dist, 1e-6)
    cx, cy = ex / 2 + nx * dist * bend, ey / 2 + ny * dist * bend
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * 0 + 2 * (1 - t) * t * cx + t * t * ex
        y = (1 - t) ** 2 * 0 + 2 * (1 - t) * t * cy + t * t * ey
        if wobble:
            w = math.sin(t * math.pi * 3) * dist * wobble * (1 - abs(2 * t - 1))
            x += nx * w
            y += ny * w
        pts.append([round(x, 2), round(y, 2)])
    return pts


def zigzag(angle, dist, teeth=3, amp=4.0):
    ex, ey = polar(angle, dist)
    nx, ny = -ey / dist, ex / dist
    pts = []
    n = teeth * 2
    for i in range(1, n + 1):
        t = i / n
        side = (1 if i % 2 else -1) * amp if i < n else 0
        pts.append([round(ex * t + nx * side, 2), round(ey * t + ny * side, 2)])
    return pts


INGREDIENTS = {
    # --- temáticos (llegan cerca de su esencia si están molidos)
    "sugar": ("minecraft:sugar", curve(55, 29, 0.15)),
    "rabbit_foot": ("minecraft:rabbit_foot", curve(125, 32, -0.2)),
    "golden_carrot": ("minecraft:golden_carrot", curve(195, 30, 0.1)),
    "magma_cream": ("minecraft:magma_cream", curve(335, 31, -0.15)),
    "pufferfish": ("minecraft:pufferfish", curve(265, 33, 0.0, wobble=0.12)),
    "spider_eye": ("minecraft:spider_eye", curve(225, 35, 0.1)),
    "blaze_powder": ("minecraft:blaze_powder", curve(40, 62, 0.2)),
    "ghast_tear": ("minecraft:ghast_tear", curve(150, 60, -0.2)),
    "glistering_melon_slice": ("minecraft:glistering_melon_slice", curve(175, 71, 0.15)),
    "turtle_scute": ("minecraft:scute", curve(285, 63, -0.1)),
    "phantom_membrane": ("minecraft:phantom_membrane", curve(210, 62, 0.2)),
    "golden_apple": ("minecraft:golden_apple", curve(5, 70, 0.25)),
    "rotten_flesh": ("minecraft:rotten_flesh", curve(75, 55, -0.15)),
    "poisonous_potato": ("minecraft:poisonous_potato", curve(228, 30, -0.2)),
    "snowball": ("minecraft:snowball", curve(12, 37, 0.1)),
    "ink_sac": ("minecraft:ink_sac", curve(125, 40, 0.15)),
    "glow_ink_sac": ("minecraft:glow_ink_sac", curve(190, 34, 0.3)),
    "emerald": ("minecraft:emerald", curve(105, 52, 0.12)),
    "lapis_lazuli": ("minecraft:lapis_lazuli", curve(250, 48, -0.1)),
    "nautilus_shell": ("minecraft:nautilus_shell", curve(290, 70, 0.2)),
    "prismarine_crystals": ("minecraft:prismarine_crystals", curve(290, 40, -0.2)),
    "sea_pickle": ("minecraft:sea_pickle", curve(280, 24, 0.4)),
    "wither_rose": ("minecraft:wither_rose", curve(340, 48, 0.2)),
    "torchflower": ("minecraft:torchflower", curve(100, 40, -0.3)),
    "pitcher_plant": ("minecraft:pitcher_plant", curve(60, 50, 0.2)),
    "feather": ("minecraft:feather", curve(215, 24, 0.1)),
    "slime_ball": ("minecraft:slime_ball", curve(130, 22, 0.5)),
    "crimson_fungus": ("minecraft:crimson_fungus", curve(20, 30, 0.5)),
    "warped_fungus": ("minecraft:warped_fungus", curve(260, 30, -0.5)),
    "cocoa_beans": ("minecraft:cocoa_beans", curve(330, 20, 0.3)),
    "honeycomb": ("minecraft:honeycomb", curve(320, 26, 0.3)),
    # --- pasos cortos para ajustar (flores, semillas, polvos)
    "glowstone_dust": ("minecraft:glowstone_dust", curve(90, 10)),
    "redstone": ("minecraft:redstone", curve(270, 10)),
    "nether_wart": ("minecraft:nether_wart", curve(180, 10)),
    "bone_meal": ("minecraft:bone_meal", curve(0, 10)),
    "wheat_seeds": ("minecraft:wheat_seeds", curve(45, 6)),
    "pumpkin_seeds": ("minecraft:pumpkin_seeds", curve(135, 6)),
    "melon_seeds": ("minecraft:melon_seeds", curve(225, 6)),
    "beetroot_seeds": ("minecraft:beetroot_seeds", curve(315, 6)),
    "sweet_berries": ("minecraft:sweet_berries", curve(15, 14, 0.2)),
    "glow_berries": ("minecraft:glow_berries", curve(100, 16, -0.3)),
    "apple": ("minecraft:apple", curve(160, 12)),
    "carrot": ("minecraft:carrot", curve(200, 14)),
    "wheat": ("minecraft:wheat", curve(30, 12, 0.15)),
    "dandelion": ("minecraft:dandelion", curve(45, 12, 0.3)),
    "poppy": ("minecraft:poppy", curve(135, 12, -0.3)),
    "blue_orchid": ("minecraft:blue_orchid", curve(250, 14, 0.2)),
    "allium": ("minecraft:allium", curve(300, 14, -0.2)),
    "azure_bluet": ("minecraft:azure_bluet", curve(125, 22, 0.1)),
    "oxeye_daisy": ("minecraft:oxeye_daisy", curve(170, 14, -0.1)),
    "cornflower": ("minecraft:cornflower", curve(80, 18, 0.2)),
    "lily_of_the_valley": ("minecraft:lily_of_the_valley", curve(230, 18, 0.2)),
    "red_tulip": ("minecraft:red_tulip", curve(20, 14)),
    "orange_tulip": ("minecraft:orange_tulip", curve(340, 14)),
    "white_tulip": ("minecraft:white_tulip", curve(200, 14, 0.2)),
    "pink_tulip": ("minecraft:pink_tulip", curve(110, 14, -0.2)),
    "brown_mushroom": ("minecraft:brown_mushroom", curve(240, 16, 0.3)),
    "red_mushroom": ("minecraft:red_mushroom", curve(300, 16, -0.3)),
    "kelp": ("minecraft:kelp", curve(275, 16, 0.0, wobble=0.25)),
    "dried_kelp": ("minecraft:dried_kelp", curve(95, 12, 0.0, wobble=0.2)),
    "cactus": ("minecraft:cactus", curve(345, 20, -0.2)),
    "bamboo": ("minecraft:bamboo", zigzag(100, 24, teeth=3, amp=3)),
}
TRANSFORMS = {
    "fermented_spider_eye": ("minecraft:fermented_spider_eye", {"type": "rotate", "angle": 180}),
    "chorus_fruit": ("minecraft:chorus_fruit", {"type": "rotate", "angle": 90}),
    "popped_chorus_fruit": ("minecraft:popped_chorus_fruit", {"type": "rotate", "angle": -90}),
    "amethyst_shard": ("minecraft:amethyst_shard", {"type": "scale", "factor": 0.5}),
    "echo_shard": ("minecraft:echo_shard", {"type": "scale", "factor": 1.5}),
    "ender_pearl": ("minecraft:ender_pearl", {"type": "mirror"}),
}
for name, (item, path) in INGREDIENTS.items():
    write(data(MOD, "alquimia", "ingredients", name + ".json"), {"ingredient": {"item": item}, "path": path})
for name, (item, tr) in TRANSFORMS.items():
    write(data(MOD, "alquimia", "ingredients", name + ".json"), {"ingredient": {"item": item}, "transform": tr})

# =============================================================================== logros
def adv(name, parent, icon, frame, criteria, requirements=None, hidden=False, announce=True, toast=True, bg=None,
        xp=0):
    d = {"display": {"icon": {"item": icon}, "title": {"translate": f"advancements.{MOD}.{name}.title"},
                     "description": {"translate": f"advancements.{MOD}.{name}.description"},
                     "frame": frame, "show_toast": toast, "announce_to_chat": announce, "hidden": hidden},
         "criteria": criteria}
    if bg:
        d["display"]["background"] = bg
    if parent:
        d["parent"] = f"{MOD}:{parent}"
    if requirements:
        d["requirements"] = requirements
    if xp:
        d["rewards"] = {"experience": xp}
    write(data(MOD, "advancements", name + ".json"), d)


def has(*items):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": list(items)}]}}


def ev(event, minimum=1):
    return {"trigger": f"{MOD}:alchemy", "conditions": {"event": event, "min": minimum}}


adv("root", None, f"{MOD}:alchemical_cauldron", "task",
    {"salt": has(f"{MOD}:salt"), "cinnabar": has(f"{MOD}:cinnabar"), "sulfur": has(f"{MOD}:sulfur")},
    requirements=[["salt", "cinnabar", "sulfur"]], announce=False, toast=False,
    bg=f"{MOD}:textures/block/salt_block.png")
adv("cauldron", "root", f"{MOD}:alchemical_cauldron", "task", {"c": has(f"{MOD}:alchemical_cauldron")})
adv("grind", "cauldron", f"{MOD}:mortar", "task", {"g": ev("grind")})
adv("first_essence", "cauldron", f"{MOD}:salt", "task", {"f": ev("fix")})
adv("first_elixir", "first_essence", f"{MOD}:alchemical_potion", "task", {"b": ev("bottle")})
adv("compound", "first_elixir", f"{MOD}:alchemical_potion", "goal", {"b": ev("bottle", 3)}, xp=50)
adv("tria_prima", "root", f"{MOD}:quicksilver", "goal",
    {"t": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
        {"items": [f"{MOD}:salt"]}, {"items": [f"{MOD}:quicksilver"]}, {"items": [f"{MOD}:sulfur"]}]}}})
adv("empower", "first_essence", f"{MOD}:sulfur", "task", {"e": ev("empower")})
adv("prolong", "first_essence", f"{MOD}:quicksilver", "task", {"p": ev("prolong")})
adv("cartographer", "first_essence", f"{MOD}:grimoire", "goal", {"d": ev("discover", 10)})
adv("master_cartographer", "cartographer", f"{MOD}:grimoire", "challenge", {"d": ev("discover", 30)}, xp=200)
adv("boom", "cauldron", "minecraft:tnt", "task", {"x": ev("explode")}, hidden=True)
adv("throwable", "first_elixir", f"{MOD}:alchemical_splash_potion", "task", {"s": has(f"{MOD}:alchemical_splash_potion")})

# =============================================================================== traducciones
# clave: (español neutro, inglés, [voseo rioplatense opcional])
L = {}


def t(key, es, en, ar=None):
    L[key] = (es, en, ar or es)


# bloques e ítems
t("block.alquimia.alchemical_cauldron", "Caldero alquímico", "Alchemical Cauldron")
t("block.alquimia.mortar", "Mortero", "Mortar and Pestle")
t("block.alquimia.salt_ore", "Mena de sal", "Salt Ore")
t("block.alquimia.deepslate_salt_ore", "Mena de sal de pizarra profunda", "Deepslate Salt Ore")
t("block.alquimia.cinnabar_ore", "Mena de cinabrio", "Cinnabar Ore")
t("block.alquimia.deepslate_cinnabar_ore", "Mena de cinabrio de pizarra profunda", "Deepslate Cinnabar Ore")
t("block.alquimia.nether_sulfur_ore", "Mena de azufre del Nether", "Nether Sulfur Ore")
t("block.alquimia.salt_block", "Bloque de sal", "Block of Salt")
t("block.alquimia.cinnabar_block", "Bloque de cinabrio", "Block of Cinnabar")
t("block.alquimia.sulfur_block", "Bloque de azufre", "Block of Sulfur")
t("item.alquimia.salt", "Sal", "Salt")
t("item.alquimia.salt.desc", "Tria prima: fija una esencia en el caldero", "Tria prima: fixes an essence in the cauldron")
t("item.alquimia.cinnabar", "Cinabrio", "Cinnabar")
t("item.alquimia.cinnabar.desc", "Fundido en un horno da mercurio", "Smelt it in a furnace to get quicksilver")
t("item.alquimia.quicksilver", "Mercurio", "Quicksilver")
t("item.alquimia.quicksilver.desc", "Tria prima: prolonga la duración de las esencias",
  "Tria prima: prolongs the duration of essences")
t("item.alquimia.sulfur", "Azufre", "Sulfur")
t("item.alquimia.sulfur.desc", "Tria prima: potencia la última esencia fijada", "Tria prima: empowers the last fixed essence")
t("item.alquimia.grimoire", "Grimorio alquímico", "Alchemical Grimoire")
t("item.alquimia.grimoire.desc", "Guarda tus mapas, esencias e ingredientes", "Keeps your maps, essences and ingredients",
  "Guarda tus mapas, esencias e ingredientes")
for item, es_base, en_base in (("alchemical_potion", "Elixir", "Elixir"),
                               ("alchemical_splash_potion", "Elixir arrojadizo", "Splash Elixir"),
                               ("alchemical_lingering_potion", "Elixir persistente", "Lingering Elixir")):
    k = f"item.alquimia.{item}"
    t(k, es_base + " alquímico", "Alchemical " + en_base)
    t(k + ".empty", es_base + " fallido", "Failed " + en_base)
    t(k + ".1", es_base + " de %s", en_base + " of %s")
    t(k + ".2", es_base + " de %s y %s", en_base + " of %s and %s")
    t(k + ".3", es_base + " de %s, %s y %s", en_base + " of %s, %s and %s")
    t(k + ".many", es_base + " compuesto", "Compound " + en_base)
t("itemGroup.alquimia.main", "Alquimia Cartográfica", "Cartographic Alchemy")
t("container.alquimia.alchemical_cauldron", "Caldero alquímico", "Alchemical Cauldron")

# mapas
t("alquimia.map.alquimia.overworld", "Mapa de la superficie", "Overworld Map")
t("alquimia.map.alquimia.the_nether", "Mapa del Nether", "Nether Map")
t("alquimia.map.alquimia.the_end", "Mapa del End", "End Map")

# interfaz del caldero
t("gui.alquimia.stir", "Remover", "Stir")
t("gui.alquimia.stir.tooltip",
  "Mantén presionado para remover: la mezcla avanza por el camino pendiente. Atajo: %s",
  "Hold to stir: the mixture moves along the pending path. Shortcut: %s",
  "Mantené apretado para remover: la mezcla avanza por el camino pendiente. Atajo: %s")
t("gui.alquimia.dilute", "Diluir", "Dilute")
t("gui.alquimia.dilute.tooltip",
  "Usa una botella de agua de la ranura de reactivo: acerca la mezcla al centro y suma un nivel de agua. Atajo: %s",
  "Uses a water bottle from the reagent slot: pulls the mixture toward the center and adds one water level. Shortcut: %s")
t("gui.alquimia.fix", "Fijar", "Fix")
t("gui.alquimia.fix.tooltip",
  "Usa sal: fija la esencia sobre la que está la mezcla. Cuanto más cerca del centro de la esencia, más potente. Atajo: %s",
  "Uses salt: fixes the essence under the mixture. The closer to the essence's center, the stronger. Shortcut: %s")
t("gui.alquimia.empower", "Potenciar", "Empower")
t("gui.alquimia.empower.tooltip",
  "Usa azufre: sube un nivel la última esencia fijada, pero reduce su duración a la mitad. Atajo: %s",
  "Uses sulfur: raises the last fixed essence by one level, but halves its duration. Shortcut: %s")
t("gui.alquimia.prolong", "Prolongar", "Prolong")
t("gui.alquimia.prolong.tooltip",
  "Usa mercurio: multiplica ×1,5 la duración de todas las esencias fijadas (hasta 2 veces). Atajo: %s",
  "Uses quicksilver: multiplies the duration of all fixed essences by 1.5 (up to 2 times). Shortcut: %s")
t("gui.alquimia.bottle", "Embotellar", "Bottle")
t("gui.alquimia.bottle.tooltip",
  "Llena los frascos vacíos con el elixir: uno por cada nivel de agua del caldero. Atajo: %s",
  "Fills empty bottles with the elixir: one per water level in the cauldron. Shortcut: %s")
t("gui.alquimia.zoom_in", "Acercar", "Zoom in")
t("gui.alquimia.zoom_out", "Alejar", "Zoom out")
t("gui.alquimia.center", "Centrar", "Center")
t("gui.alquimia.center.tooltip", "Centrar el mapa en la mezcla y seguirla. Atajo: %s",
  "Center the map on the mixture and follow it. Shortcut: %s")
t("gui.alquimia.empty", "Vaciar", "Empty")
t("gui.alquimia.empty.tooltip", "Vaciar el caldero (se pierde la mezcla). Mayús + clic para confirmar.",
  "Empty the cauldron (the mixture is lost). Shift + click to confirm.")
t("gui.alquimia.empty.confirm", "Mayús + clic para vaciar el caldero", "Shift + click to empty the cauldron")
t("gui.alquimia.help", "Ayuda", "Help")
t("gui.alquimia.help.short", "Pasa el ratón sobre «?» para ver cómo se hace un elixir",
  "Hover the «?» to see how to brew an elixir", "Pasá el mouse sobre «?» para ver cómo se hace un elixir")
t("gui.alquimia.help.tooltip",
  "Cómo hacer un elixir:\n1. Llena el caldero con agua y ponlo sobre fuego.\n2. Pon ingredientes en la ranura de la hoja: cada uno agrega un camino (línea negra).\n3. Mantén «Remover» para que la mezcla avance.\n4. Sobre una esencia, pon sal en la ranura de reactivo y pulsa «Fijar».\n5. Pon frascos vacíos y pulsa «Embotellar».\nMuele los ingredientes en un mortero para recorrer su camino completo. ¡Evita las calaveras!",
  "How to brew an elixir:\n1. Fill the cauldron with water and put it over a fire.\n2. Put ingredients in the leaf slot: each one adds a path (black line).\n3. Hold «Stir» to move the mixture.\n4. Over an essence, put salt in the reagent slot and press «Fix».\n5. Add empty bottles and press «Bottle».\nGrind ingredients in a mortar to travel their full path. Avoid the skulls!",
  "Cómo hacer un elixir:\n1. Llená el caldero con agua y ponelo sobre fuego.\n2. Poné ingredientes en la ranura de la hoja: cada uno agrega un camino (línea negra).\n3. Mantené «Remover» para que la mezcla avance.\n4. Sobre una esencia, poné sal en la ranura de reactivo y apretá «Fijar».\n5. Poné frascos vacíos y apretá «Embotellar».\nMolé los ingredientes en un mortero para recorrer su camino completo. ¡Evitá las calaveras!")
t("gui.alquimia.no_map", "No hay ningún mapa alquímico cargado. Revisa los datapacks.",
  "No alchemical map is loaded. Check your datapacks.", "No hay ningún mapa alquímico cargado. Revisá los datapacks.")
t("gui.alquimia.hint.water", "Llena el caldero con agua (balde o botellas)", "Fill the cauldron with water (bucket or bottles)",
  "Llená el caldero con agua (balde o botellas)")
t("gui.alquimia.hint.heat", "Pon fuego debajo del caldero (fogata, magma o lava)",
  "Put a heat source under the cauldron (campfire, magma or lava)", "Poné fuego debajo del caldero (fogata, magma o lava)")
t("gui.alquimia.hint.stirring", "Removiendo… la mezcla sigue el camino", "Stirring… the mixture follows the path")
t("gui.alquimia.hint.over_zone", "¡Estás sobre %s! Fíjala con sal", "You're over %s! Fix it with salt",
  "¡Estás sobre %s! Fijala con sal")
t("gui.alquimia.hint.stir", "Mantén «Remover» para avanzar por el camino", "Hold «Stir» to move along the path",
  "Mantené «Remover» para avanzar por el camino")
t("gui.alquimia.hint.bottle", "Embotella el elixir o busca otra esencia", "Bottle the elixir or look for another essence",
  "Embotellá el elixir o buscá otra esencia")
t("gui.alquimia.hint.ingredients", "Pon un ingrediente en la ranura de la hoja", "Put an ingredient in the leaf slot",
  "Poné un ingrediente en la ranura de la hoja")
t("gui.alquimia.water", "Agua", "Water")
t("gui.alquimia.water.tooltip", "Agua: %s de %s. Cada nivel llena un frasco.", "Water: %s of %s. Each level fills one bottle.")
t("gui.alquimia.heat.0", "Sin calor", "No heat")
t("gui.alquimia.heat.1", "Calor suave", "Gentle heat")
t("gui.alquimia.heat.2", "Calor intenso", "Strong heat")
t("gui.alquimia.heat.tooltip",
  "Sin fuego debajo no se puede remover. Fogata, fuego o magma dan calor suave; lava o fuego de almas, calor intenso (remueve más rápido).",
  "You can't stir without heat below. Campfire, fire or magma give gentle heat; lava or soul fire give strong heat (stirs faster).")
t("gui.alquimia.stirring", "Removiendo…", "Stirring…")
t("gui.alquimia.path", "Camino pendiente: %s", "Pending path: %s")
t("gui.alquimia.ingredients_used", "Ingredientes usados: %s", "Ingredients used: %s")
t("gui.alquimia.essences", "Esencias fijadas: %s/%s", "Fixed essences: %s/%s")
t("gui.alquimia.prolonged", "Prolongado ×%s", "Prolonged ×%s")
t("gui.alquimia.empowered", "Potenciada con azufre", "Empowered with sulfur")
t("gui.alquimia.map.mixture", "Mezcla", "Mixture")
t("gui.alquimia.map.over", "Sobre %s (pureza %s)", "Over %s (purity %s)")
t("gui.alquimia.map.pending", "Camino pendiente: %s", "Pending path: %s")
t("gui.alquimia.map.hazard", "Zona de peligro", "Hazard")
t("gui.alquimia.map.hazard.desc", "Si la mezcla la toca, explota", "If the mixture touches it, it explodes")
t("gui.alquimia.map.purity_here", "Pureza en este punto: %s", "Purity at this point: %s")
t("gui.alquimia.map.duration", "Duración: %s", "Duration: %s")
t("gui.alquimia.map.purity_hint", "Cuanto más cerca del centro, más potente", "The closer to the center, the stronger")
t("gui.alquimia.map.unknown", "Esencia desconocida", "Unknown essence")
t("gui.alquimia.map.unknown.desc", "Lleva la mezcla hasta aquí para descubrirla", "Bring the mixture here to discover it",
  "Llevá la mezcla hasta acá para descubrirla")
t("gui.alquimia.map.origin", "Agua pura (el punto de partida)", "Pure water (the starting point)")
t("gui.alquimia.grimoire", "Grimorio alquímico", "Alchemical Grimoire")
t("gui.alquimia.grimoire.prev_map", "Mapa anterior", "Previous map")
t("gui.alquimia.grimoire.next_map", "Mapa siguiente", "Next map")
t("gui.alquimia.grimoire.essences", "Esencias", "Essences")
t("gui.alquimia.grimoire.ingredients", "Ingredientes", "Ingredients")
t("gui.alquimia.grimoire.discovered", "Descubiertas: %s/%s", "Discovered: %s/%s")
t("gui.alquimia.grimoire.unknown", "¿…?", "¿…?")
t("gui.alquimia.grimoire.click_to_locate", "Clic para ubicarla en el mapa", "Click to locate it on the map")
t("gui.alquimia.grimoire.tried", "Probados: %s/%s", "Tried: %s/%s")
t("gui.alquimia.grimoire.untried", "Todavía no probaste este ingrediente", "You haven't tried this ingredient yet")

# mensajes
for key, es, en, ar in [
    ("needs_heat", "El caldero necesita fuego debajo para remover", "The cauldron needs heat below to stir", None),
    ("edge", "La mezcla llegó al borde del mapa", "The mixture reached the edge of the map", None),
    ("exploded", "¡La mezcla tocó un peligro y explotó!", "The mixture touched a hazard and exploded!", None),
    ("discovered", "Nueva esencia descubierta: %s", "New essence discovered: %s", None),
    ("no_water", "El caldero no tiene agua", "The cauldron has no water", None),
    ("nothing_to_stir", "No hay camino para remover: agrega un ingrediente", "Nothing to stir: add an ingredient",
     "No hay camino para remover: agregá un ingrediente"),
    ("dilute.no_water", "Pon una botella de agua en la ranura de reactivo", "Put a water bottle in the reagent slot",
     "Poné una botella de agua en la ranura de reactivo"),
    ("dilute.nothing", "La mezcla ya está en el centro y el caldero está lleno", "The mixture is already centered and full",
     None),
    ("fix.no_salt", "Pon sal en la ranura de reactivo", "Put salt in the reagent slot", "Poné sal en la ranura de reactivo"),
    ("fix.no_zone", "La mezcla no está sobre ninguna esencia", "The mixture is not over any essence", None),
    ("fix.already", "Esa esencia ya está fijada", "That essence is already fixed", None),
    ("fix.full", "No caben más esencias (máximo %s)", "No room for more essences (max %s)", None),
    ("fix.full_short", "No caben más esencias en este elixir", "No room for more essences in this elixir", None),
    ("fix.unknown", "Esta esencia no existe en este mundo", "That essence doesn't exist in this world", None),
    ("fixed", "Esencia fijada: %s", "Essence fixed: %s", None),
    ("empower.no_sulfur", "Pon azufre en la ranura de reactivo", "Put sulfur in the reagent slot",
     "Poné azufre en la ranura de reactivo"),
    ("no_essence", "Primero fija una esencia con sal", "Fix an essence with salt first", "Primero fijá una esencia con sal"),
    ("empower.already", "La última esencia ya está potenciada", "The last essence is already empowered", None),
    ("empower.max", "Esa esencia ya tiene la potencia máxima", "That essence is already at maximum power", None),
    ("prolong.no_quicksilver", "Pon mercurio en la ranura de reactivo", "Put quicksilver in the reagent slot",
     "Poné mercurio en la ranura de reactivo"),
    ("prolong.max", "Ya se prolongó el máximo de veces", "Already prolonged as much as possible", None),
    ("bottle.no_bottles", "Pon frascos vacíos en la ranura de frascos", "Put empty bottles in the bottle slot",
     "Poné frascos vacíos en la ranura de frascos"),
    ("bottle.full", "Las ranuras de salida están llenas", "The output slots are full", None),
    ("mortar.not_ingredient", "Eso no es un ingrediente alquímico", "That's not an alchemical ingredient", None),
    ("mortar.cannot_grind", "Ese ingrediente no se puede moler", "That ingredient can't be ground", None),
    ("mortar.done", "Ya está molido. Agáchate y haz clic para sacarlo", "Already ground. Sneak and click to take it out",
     "Ya está molido. Agachate y hacé clic para sacarlo"),
    ("mortar.ground", "%s molido", "%s ground", None),
]:
    t("message.alquimia." + key, es, en, ar)

# tooltips de ingredientes
t("tooltip.alquimia.ingredient", "⚗ Ingrediente alquímico", "⚗ Alchemical ingredient")
t("tooltip.alquimia.rotate", "Gira el camino pendiente %s°", "Rotates the pending path by %s°")
t("tooltip.alquimia.shrink", "Acorta el camino pendiente al %s%%", "Shrinks the pending path to %s%%")
t("tooltip.alquimia.stretch", "Alarga el camino pendiente al %s%%", "Stretches the pending path to %s%%")
t("tooltip.alquimia.mirror", "Refleja el camino pendiente (izquierda ↔ derecha)", "Mirrors the pending path (left ↔ right)")
t("tooltip.alquimia.path", "Camino: %s unidades", "Path: %s units")
t("tooltip.alquimia.ground", "Molido: recorre el camino completo", "Ground: travels the full path")
t("tooltip.alquimia.unground", "Sin moler: solo la mitad del camino", "Unground: only half of the path")

# teclas
t("key.categories.alquimia", "Alquimia Cartográfica", "Cartographic Alchemy")
t("key.alquimia.stir", "Remover (caldero)", "Stir (cauldron)")
t("key.alquimia.dilute", "Diluir (caldero)", "Dilute (cauldron)")
t("key.alquimia.fix", "Fijar con sal (caldero)", "Fix with salt (cauldron)")
t("key.alquimia.empower", "Potenciar con azufre (caldero)", "Empower with sulfur (cauldron)")
t("key.alquimia.prolong", "Prolongar con mercurio (caldero)", "Prolong with quicksilver (cauldron)")
t("key.alquimia.bottle", "Embotellar (caldero)", "Bottle (cauldron)")
t("key.alquimia.center", "Centrar el mapa (caldero)", "Center the map (cauldron)")

# comandos
t("commands.alquimia.reveal", "Mapas alquímicos revelados para %s jugador(es)", "Alchemical maps revealed for %s player(s)")
t("commands.alquimia.forget", "Conocimiento alquímico borrado para %s jugador(es)", "Alchemical knowledge erased for %s player(s)")

# configuración
t("alquimia.config.title", "Alquimia Cartográfica: configuración", "Cartographic Alchemy: settings")
t("alquimia.config.section.client", "Accesibilidad y preferencias (este equipo)", "Accessibility & preferences (this computer)")
t("alquimia.config.section.common", "Reglas de juego y mundo", "Gameplay & world rules")
t("alquimia.config.not_loaded", "(disponible dentro de un mundo)", "(available inside a world)")
t("alquimia.config.reset", "Restablecer valores", "Reset to defaults")
t("alquimia.config.range", "Rango: %s a %s", "Range: %s to %s")
t("alquimia.config.default", "Por defecto: %s", "Default: %s")
t("alquimia.config.edit_in_file", "Editar en el archivo", "Edit in file")
t("alquimia.config.alquimia", "Reglas de la alquimia", "Alchemy rules")
t("alquimia.config.mundo", "Generación de minerales", "Ore generation")
t("alquimia.config.accesibilidad", "Accesibilidad", "Accessibility")
CONFIG = {
    "alquimia.max_effects": ("Esencias por elixir", "Essences per elixir",
                             "Cuántas esencias se pueden fijar en un mismo elixir.",
                             "How many essences can be fixed in one elixir."),
    "alquimia.max_amplifier": ("Nivel máximo", "Maximum level",
                               "Nivel máximo de potencia (0 = I, 1 = II, 2 = III…).",
                               "Maximum potency level (0 = I, 1 = II, 2 = III…)."),
    "alquimia.stir_speed": ("Velocidad al remover", "Stirring speed", "Multiplicador de la velocidad de la mezcla.",
                            "Multiplier for how fast the mixture moves."),
    "alquimia.require_heat": ("Requiere fuego", "Requires heat", "Si está activo, hace falta calor debajo para remover.",
                              "If on, heat below is needed to stir."),
    "alquimia.hazards_explode": ("Los peligros explotan", "Hazards explode",
                                 "Si está activo, tocar una calavera hace explotar la mezcla.",
                                 "If on, touching a skull makes the mixture explode."),
    "alquimia.explosions_break_blocks": ("Explosiones rompen bloques", "Explosions break blocks",
                                         "Si está activo, las explosiones del caldero rompen bloques.",
                                         "If on, cauldron explosions break blocks."),
    "alquimia.duration_multiplier": ("Multiplicador de duración", "Duration multiplier",
                                     "Multiplica la duración de todos los elixires.", "Multiplies the duration of all elixirs."),
    "alquimia.shared_discoveries": ("Descubrimientos compartidos", "Shared discoveries",
                                    "Todos los jugadores comparten el mapa explorado (ideal para servidores cooperativos).",
                                    "All players share the explored map (great for co-op servers)."),
    "mundo.salt_veins": ("Vetas de sal", "Salt veins", "Vetas por chunk (0 = desactivado).", "Veins per chunk (0 = off)."),
    "mundo.cinnabar_veins": ("Vetas de cinabrio", "Cinnabar veins", "Vetas por chunk (0 = desactivado).",
                             "Veins per chunk (0 = off)."),
    "mundo.sulfur_veins": ("Vetas de azufre (Nether)", "Sulfur veins (Nether)", "Vetas por chunk (0 = desactivado).",
                           "Veins per chunk (0 = off)."),
    "accesibilidad.hold_to_stir": ("Mantener para remover", "Hold to stir",
                                   "Activo: mantener apretado. Inactivo: un clic empieza y otro detiene (menos esfuerzo).",
                                   "On: hold the button. Off: one click starts, another stops (less effort)."),
    "accesibilidad.high_contrast": ("Mapa de alto contraste", "High-contrast map",
                                    "Fondo oscuro, líneas gruesas y colores fuertes.",
                                    "Dark background, thick lines and strong colors."),
    "accesibilidad.show_zone_labels": ("Nombres en el mapa", "Labels on the map",
                                       "Muestra el nombre de las esencias descubiertas.", "Shows discovered essence names."),
    "accesibilidad.reduce_motion": ("Reducir animaciones", "Reduce motion",
                                    "Sin desplazamientos suaves ni pulsos en el mapa.", "No smooth panning or pulses on the map."),
    "accesibilidad.bubble_particles": ("Burbujas del caldero", "Cauldron bubbles",
                                       "Partículas de burbujas sobre el caldero.", "Bubble particles above the cauldron."),
    "accesibilidad.default_zoom": ("Zoom inicial del mapa", "Default map zoom", "Zoom al abrir el caldero.",
                                   "Zoom when opening the cauldron."),
}
for path, (es, en, es_tip, en_tip) in CONFIG.items():
    t(f"alquimia.config.{path}", es, en)
    t(f"alquimia.config.{path}.tooltip", es_tip, en_tip)

# logros
ADV = {
    "root": ("Alquimia Cartográfica", "Cartographic Alchemy", "Encuentra sal, cinabrio o azufre",
             "Find salt, cinnabar or sulfur", "Encontrá sal, cinabrio o azufre"),
    "cauldron": ("El caldero", "The Cauldron", "Fabrica un caldero alquímico", "Craft an alchemical cauldron",
                 "Fabricá un caldero alquímico"),
    "grind": ("Muele que te muele", "Grind It Down", "Muele un ingrediente en el mortero",
              "Grind an ingredient in the mortar", "Molé un ingrediente en el mortero"),
    "first_essence": ("Primera esencia", "First Essence", "Fija una esencia con sal", "Fix an essence with salt",
                      "Fijá una esencia con sal"),
    "first_elixir": ("¡Salud!", "Cheers!", "Embotella tu primer elixir", "Bottle your first elixir",
                     "Embotellá tu primer elixir"),
    "compound": ("Mezcla magistral", "Masterful Blend", "Embotella un elixir con tres esencias",
                 "Bottle an elixir with three essences", "Embotellá un elixir con tres esencias"),
    "tria_prima": ("Tria prima", "Tria Prima", "Ten sal, mercurio y azufre a la vez",
                   "Have salt, quicksilver and sulfur at the same time", "Tené sal, mercurio y azufre a la vez"),
    "empower": ("Fuego interior", "Inner Fire", "Potencia una esencia con azufre", "Empower an essence with sulfur",
                "Potenciá una esencia con azufre"),
    "prolong": ("Tiempo líquido", "Liquid Time", "Prolonga una mezcla con mercurio", "Prolong a mixture with quicksilver",
                "Prolongá una mezcla con mercurio"),
    "cartographer": ("Cartógrafo alquímico", "Alchemical Cartographer", "Descubre 10 esencias", "Discover 10 essences",
                     "Descubrí 10 esencias"),
    "master_cartographer": ("Maestro cartógrafo", "Master Cartographer", "Descubre 30 esencias", "Discover 30 essences",
                            "Descubrí 30 esencias"),
    "boom": ("Eso no estaba en la receta", "That Wasn't in the Recipe", "Haz explotar una mezcla",
             "Make a mixture explode", "Hacé explotar una mezcla"),
    "throwable": ("Para compartir", "Sharing Is Caring", "Fabrica un elixir arrojadizo con pólvora",
                  "Craft a splash elixir with gunpowder", "Fabricá un elixir arrojadizo con pólvora"),
}
for name, (es, en, es_d, en_d, ar_d) in ADV.items():
    t(f"advancements.alquimia.{name}.title", es, en)
    t(f"advancements.alquimia.{name}.description", es_d, en_d, ar_d)

# escribir archivos de idioma
es_neutral = {k: v[0] for k, v in L.items()}
en = {k: v[1] for k, v in L.items()}
es_voseo = {k: v[2] for k, v in L.items()}
write(asset("lang", "en_us.json"), en)
for code in ("es_es", "es_mx", "es_cl", "es_ec", "es_ve"):
    write(asset("lang", code + ".json"), es_neutral)
for code in ("es_ar", "es_uy"):
    write(asset("lang", code + ".json"), es_voseo)

print(f"{len(written)} archivos JSON generados, {len(L)} claves de traducción")
