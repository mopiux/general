#!/usr/bin/env python3
"""Genera todas las texturas de Alquimia Cartográfica (pixel art 16x16 y GUIs).

    python3 tools/gen_alquimia_textures.py [--sheet]

Con --sheet además crea build/alquimia_sheet.png para revisarlas ampliadas.
Todas las texturas son originales (generadas por código). Las menas NO copian la piedra
del juego: son solo las vetas con fondo transparente y el modelo las dibuja encima de la
textura de piedra/pizarra/netherrack que tenga el juego (o el resource pack activo).
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import *  # noqa

ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "alquimia", "src", "main", "resources")
TEX = os.path.join(ROOT, "assets", "alquimia", "textures")
OUT = []  # (nombre, imagen) para la hoja de contacto


def put(name, img):
    save(img, os.path.join(TEX, name + ".png"))
    OUT.append((name, img))


# =============================================================================== menas
# Tonos de "hendidura" para cada roca base (oscuros, para que la veta parezca incrustada)
EMBED_STONE = "#5c5c5c"
EMBED_DEEPSLATE = "#2b2b31"
EMBED_NETHERRACK = "#3f1414"

# Distribuciones de vetas elegidas a mano (x, y, forma) para que se vean equilibradas
LAYOUT_A = [(2, 2, 5), (9, 1, 1), (12, 6, 3), (4, 8, 2), (9, 10, 0), (1, 12, 6), (12, 12, 7)]
LAYOUT_B = [(1, 1, 2), (7, 3, 0), (12, 2, 3), (3, 7, 6), (10, 8, 5), (2, 12, 1), (8, 13, 4)]
LAYOUT_C = [(2, 1, 1), (10, 1, 2), (5, 5, 5), (12, 7, 0), (1, 9, 3), (7, 11, 6), (12, 12, 4)]

SALT = ["#b9a9ad", "#e7ddde", "#faf6f6", "#ffffff"]
CINNABAR = ["#7a1016", "#b3241f", "#dc4a33", "#ff9a7a"]
SULFUR = ["#a8840e", "#dcc02a", "#f2e35a", "#fffbb0"]

put("block/salt_ore_overlay", ore_overlay(SALT, 11, EMBED_STONE, layout=LAYOUT_A, sparkle="#ffffff"))
put("block/deepslate_salt_ore_overlay", ore_overlay(SALT, 12, EMBED_DEEPSLATE, layout=LAYOUT_B, sparkle="#ffffff"))
put("block/cinnabar_ore_overlay", ore_overlay(CINNABAR, 13, EMBED_STONE, layout=LAYOUT_C))
put("block/deepslate_cinnabar_ore_overlay", ore_overlay(CINNABAR, 14, EMBED_DEEPSLATE, layout=LAYOUT_A))
put("block/nether_sulfur_ore_overlay", ore_overlay(SULFUR, 15, EMBED_NETHERRACK, layout=LAYOUT_B, sparkle="#fffde0"))


# =============================================================================== bloques de almacenamiento
def crystal_block(seed, dark, mid, light, line, bright):
    """Bloque de cristales: ruido suave + líneas de clivaje en cuadrícula irregular + brillos."""
    rng = random.Random(seed)
    base = noise_texture([dark, mid, mid, light], seed, octaves=3, base_period=4, speckle=0.18)
    px = base.load()
    # líneas de clivaje (cristales cúbicos)
    cuts_x = [0, 5, 11]
    cuts_y = [0, 4, 9, 13]
    for y in range(16):
        for x in range(16):
            if x in cuts_x or y in cuts_y:
                px[x, y] = hex2rgba(line)
    # bisel de cada cristal: luz arriba/izq, sombra abajo/der
    for y in range(16):
        for x in range(16):
            if x in cuts_x or y in cuts_y:
                continue
            if (x - 1) in cuts_x or (y - 1) in cuts_y:
                px[x, y] = shade(px[x, y], 0.22)
            elif (x + 1) % 16 in cuts_x or (y + 1) % 16 in cuts_y:
                px[x, y] = shade(px[x, y], -0.12)
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        if x not in cuts_x and y not in cuts_y:
            px[x, y] = hex2rgba(bright)
    return base


def sparkle(img, seed, color, n=4):
    rng = random.Random(seed)
    px = img.load()
    for _ in range(n):
        px[rng.randrange(16), rng.randrange(16)] = hex2rgba(color)
    return img


put("block/salt_block", sparkle(voronoi_crystals(
    ["#c9b6ba", "#dccdd0", "#eadfe0", "#f5eeee", "#fdfafa"], 21, cells=10, edge="#b9a4a9"), 2, "#ffffff"))
put("block/sulfur_block", sparkle(voronoi_crystals(
    ["#b08c10", "#cfb020", "#e4cc36", "#f2e35a", "#faf08c"], 22, cells=11, edge="#9a7a0c"), 3, "#fffbd0"))


def cinnabar_block():
    """Bloque pulido de cinabrio: rojo profundo con vetas oscuras y marco biselado."""
    img = noise_texture(["#6e0d12", "#8c1418", "#a31d1b", "#b52620"], 31, octaves=3, base_period=4, speckle=0.1)
    px = img.load()
    rng = random.Random(3)
    # vetas diagonales finas
    for start in (3, 9, 14):
        x, y = start, 0
        while y < 16:
            px[x % 16, y] = hex2rgba("#5a0a10")
            y += 1
            x += rng.choice((0, 1, 1))
    img = bevel(img, light=0.25, dark=0.3)
    img = bevel(img, light=0.08, dark=0.1, rect=(1, 1, 14, 14))
    return img


put("block/cinnabar_block", cinnabar_block())


# =============================================================================== caldero alquímico
IRON = ["#26232b", "#34303a", "#403b47", "#4d4755", "#5c5566"]
COPPER = ["#6d3a24", "#9c5534", "#c4714a", "#e39a6c", "#f5c49a"]
GOLD = ["#8a5a10", "#c89a2a", "#f0d060"]


def iron_noise(seed):
    return noise_texture(IRON[1:4], seed, octaves=2, base_period=4, speckle=0.2)


def cauldron_side():
    img = iron_noise(41)
    px = img.load()
    for x in range(16):
        # borde superior (labio)
        px[x, 0] = hex2rgba(COPPER[3])
        px[x, 1] = hex2rgba(COPPER[2])
        px[x, 2] = hex2rgba(COPPER[0])
        # banda de cobre
        px[x, 5] = hex2rgba(COPPER[2])
        px[x, 6] = hex2rgba(COPPER[1])
        # base oscura del cuerpo
        px[x, 12] = hex2rgba(IRON[0])
    # remaches en la banda
    for x in (1, 5, 10, 14):
        px[x, 5] = hex2rgba(COPPER[4])
        px[x, 6] = hex2rgba(COPPER[2])
    # símbolo alquímico (triángulo del fuego con un punto) grabado en oro
    tri = [(7, 7), (8, 7), (6, 8), (9, 8), (6, 9), (9, 9), (5, 10), (10, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11)]
    for x, y in tri:
        px[x, y] = hex2rgba(GOLD[1])
    px[7, 7] = hex2rgba(GOLD[2])
    px[8, 7] = hex2rgba(GOLD[2])
    px[7, 9] = hex2rgba(GOLD[0])
    px[8, 9] = hex2rgba(GOLD[0])
    # patas (filas 13-15): hierro oscuro con pie de cobre; el centro no se ve en el modelo
    for y in range(13, 16):
        for x in range(16):
            px[x, y] = hex2rgba(IRON[1] if x in (0, 15) or y == 15 else IRON[2])
        for x in (0, 1, 2, 3, 12, 13, 14, 15):
            if y == 13:
                px[x, y] = hex2rgba(IRON[3])
            if y == 15:
                px[x, y] = hex2rgba(COPPER[1])
    return img


def cauldron_top():
    img = iron_noise(42)
    px = img.load()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                px[x, y] = hex2rgba(COPPER[2] if (x + y) % 5 else COPPER[3])
            elif edge == 1:
                px[x, y] = hex2rgba(COPPER[1])
            else:
                px[x, y] = hex2rgba(IRON[0])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        px[x, y] = hex2rgba(COPPER[4])
    return img


def cauldron_inner():
    img = noise_texture(["#1a171f", "#221e28", "#2b2632", "#332d3b"], 43, octaves=3, base_period=4, speckle=0.15)
    px = img.load()
    # hollín más oscuro abajo
    for y in range(10, 16):
        for x in range(16):
            px[x, y] = shade(px[x, y], -0.12 * (y - 9) / 6)
    return img


def cauldron_bottom():
    img = iron_noise(44)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if 3 <= x <= 12 and 3 <= y <= 12:
                px[x, y] = shade(px[x, y], -0.25)
    # anillo de cobre (la marca de la base)
    for a in range(0, 360, 12):
        x = int(round(7.5 + math.cos(math.radians(a)) * 3.2))
        y = int(round(7.5 + math.sin(math.radians(a)) * 3.2))
        px[x, y] = hex2rgba(COPPER[1])
    return img


put("block/alchemical_cauldron_side", cauldron_side())
put("block/alchemical_cauldron_top", cauldron_top())
put("block/alchemical_cauldron_inner", cauldron_inner())
put("block/alchemical_cauldron_bottom", cauldron_bottom())


# =============================================================================== mortero
def mortar_texture():
    img = noise_texture(["#8f8a86", "#a39e99", "#b5b0aa", "#c6c1ba"], 51, octaves=2, base_period=4, speckle=0.18)
    px = img.load()
    # franjas talladas horizontales
    for x in range(16):
        px[x, 5] = shade(px[x, 5], -0.18)
        px[x, 6] = shade(px[x, 6], 0.12)
        px[x, 11] = shade(px[x, 11], -0.18)
        px[x, 12] = shade(px[x, 12], 0.12)
    return img


def mortar_inner():
    img = noise_texture(["#6f6a66", "#7c7772", "#8a847f"], 52, octaves=2, base_period=4, speckle=0.2)
    px = img.load()
    # restos de polvo de colores en el fondo
    rng = random.Random(9)
    for _ in range(10):
        x, y = rng.randrange(3, 13), rng.randrange(3, 13)
        px[x, y] = hex2rgba(rng.choice(["#c9b98a", "#9fbf6a", "#c47a5a", "#d8d0c0"]))
    return img


def pestle_texture():
    img = new()
    px = img.load()
    wood = ["#4a3020", "#6a4630", "#8a5e3e", "#a8774e"]
    stone = ["#8f8a86", "#b5b0aa", "#d0cbc4"]
    for y in range(16):
        for x in range(16):
            if y < 10:
                px[x, y] = hex2rgba(wood[(x + (y // 3)) % 3 + (1 if x % 4 == 0 else 0)])
            else:
                px[x, y] = hex2rgba(stone[(x + y) % 3])
    for x in range(16):
        px[x, 9] = hex2rgba(wood[0])
    return img


put("block/mortar", mortar_texture())
put("block/mortar_inner", mortar_inner())
put("block/pestle", pestle_texture())


# =============================================================================== ítems
OUTLINE = "#241d2b"


def iso_cube(img, x0, y0, size, depth, top, front, right, outline_col=None):
    """Cubo en perspectiva caballera: cara frontal cuadrada, techo y lado derecho."""
    px = img.load()
    cells = {}
    for y in range(y0, y0 + size):
        for x in range(x0, x0 + size):
            cells[(x, y)] = front
    for k in range(1, depth + 1):
        for x in range(x0 + k, x0 + size + k):
            cells[(x, y0 - k)] = top
        for y in range(y0 - k + 1, y0 + size - k):
            cells[(x0 + size - 1 + k, y)] = right
    for (x, y), c in cells.items():
        if 0 <= x < img.width and 0 <= y < img.height:
            px[x, y] = hex2rgba(c)
    return cells


def item_salt():
    img = new()
    iso_cube(img, 2, 6, 8, 3, "#fbf8f8", "#e8dcdd", "#c5b1b6")
    iso_cube(img, 10, 11, 4, 2, "#fdfbfb", "#eadfe0", "#c9b6ba")
    iso_cube(img, 1, 12, 3, 1, "#fbf8f8", "#e3d6d8", "#c0acb1")
    px = img.load()
    # líneas de clivaje y brillos
    for x in range(3, 9):
        px[x, 9] = hex2rgba("#d9cacd")
    for y in range(7, 13):
        px[5, y] = hex2rgba("#dccfd1")
    px[3, 7] = hex2rgba("#ffffff")
    px[4, 7] = hex2rgba("#ffffff")
    px[6, 4] = hex2rgba("#ffffff")
    return outline(img, "#6e5a62")


def item_cinnabar():
    img = from_ascii([
        "................",
        "..........a.....",
        ".........abc....",
        "....a....abcc...",
        "...abc..abbcc...",
        "...abcc.abbccd..",
        "..abbccdabbccd..",
        "..abbccdabbcdd..",
        "..abbcddabbcd...",
        "..abccdd.abcd...",
        ".eabccd..abdd...",
        "eeebcdd.eabd....",
        "efeecdeeeeedd...",
        ".fffeeeeeeee....",
        "..ffffffff......",
        "................",
    ], {"a": "#ff9a7a", "b": "#e0503a", "c": "#b8281f", "d": "#7c1116", "e": "#6b5e5a", "f": "#4a403e"})
    return outline(img, OUTLINE)


def mound(img, cx, base_y, half_w, height, palette, seed, sparkle=None):
    """Montículo de polvo con luz desde arriba a la izquierda y un poco de tramado."""
    rng = random.Random(seed)
    px = img.load()
    for x in range(cx - half_w, cx + half_w + 1):
        t = (x - cx) / (half_w + 0.5)
        hcol = int(round(height * (1 - t * t)))
        for y in range(base_y - hcol, base_y + 1):
            depth = (y - (base_y - hcol)) / max(1, hcol)
            light = 0.65 - t * 0.45 - depth * 0.35 + (rng.random() - 0.5) * 0.18
            i = int(max(0, min(len(palette) - 1, round(light * (len(palette) - 1)))))
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = hex2rgba(palette[i])
    if sparkle:
        for _ in range(4):
            x = rng.randint(cx - half_w + 2, cx + half_w - 2)
            y = rng.randint(base_y - height + 2, base_y - 1)
            if px[x, y][3]:
                px[x, y] = hex2rgba(sparkle)


def item_sulfur():
    img = new()
    mound(img, 7, 13, 6, 6, ["#8a6a08", "#b89514", "#dcc02a", "#f0de4e", "#faf088"], 5, sparkle="#fffbd0")
    mound(img, 11, 13, 3, 3, ["#8a6a08", "#b89514", "#dcc02a", "#f0de4e"], 6)
    px = img.load()
    # cristalitos puntiagudos
    for (x, y, c) in [(6, 5, "#faf088"), (6, 6, "#f0de4e"), (7, 6, "#b89514"), (9, 7, "#faf088"), (9, 8, "#dcc02a"),
                      (4, 8, "#f0de4e"), (4, 9, "#dcc02a")]:
        px[x, y] = hex2rgba(c)
    return outline(img, "#5a4506")


def item_quicksilver():
    img = new()
    px = img.load()
    glass_out = "#3a3d52"
    # tubo de ensayo inclinado (vertical, fondo redondeado)
    tube = set()
    for y in range(3, 14):
        for x in range(6, 10):
            tube.add((x, y))
    tube -= {(6, 13), (9, 13)}
    for (x, y) in tube:
        px[x, y] = hex2rgba("#c8d8ec80")
    # mercurio (plateado con reflejo)
    silver = ["#5d6470", "#8f98a6", "#c3cbd6", "#eef2f7"]
    for (x, y) in tube:
        if y >= 8:
            i = 2 if x == 7 else (1 if x in (6, 8) else 0)
            if y == 8:
                i = 3
            px[x, y] = hex2rgba(silver[i])
    # corcho
    for x in range(6, 10):
        px[x, 2] = hex2rgba("#8a5e3e")
        px[x, 1] = hex2rgba("#a8774e")
    px[6, 1] = hex2rgba("#6a4630")
    # brillo del vidrio
    px[7, 4] = hex2rgba("#ffffffc0")
    px[7, 5] = hex2rgba("#ffffffa0")
    img = outline(img, glass_out)
    px = img.load()
    # gota de mercurio suelta
    for (x, y, c) in [(12, 11, silver[2]), (13, 11, silver[1]), (12, 12, silver[1]), (13, 12, silver[0]), (12, 10, silver[3])]:
        px[x, y] = hex2rgba(c)
    return img


def flask(shape):
    """Devuelve (capa_liquido_en_gris, capa_frasco) para las pociones alquímicas."""
    body = set()
    if shape == "round":
        cx, cy, r = 7.5, 9.8, 4.9
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    body.add((x, y))
        neck = {(x, y) for x in range(6, 10) for y in range(2, 6)}
    elif shape == "splash":
        cx, cy, r = 7.5, 10.2, 4.6
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 / 1.1 + (y - cy) ** 2 <= r * r:
                    body.add((x, y))
        neck = {(x, y) for x in range(6, 10) for y in range(3, 6)}
    else:  # lingering: cuerpo de cebolla con cuello largo
        for y in range(16):
            for x in range(16):
                dy = y - 10.5
                w = 5.2 - 0.12 * dy * dy - (0.9 if dy < -2 else 0)
                if abs(x - 7.5) <= w and 6 <= y <= 14:
                    body.add((x, y))
        neck = {(x, y) for x in range(7, 9) for y in range(1, 7)} | {(6, 5), (9, 5)}
    shape_px = body | neck
    liquid_top = 8 if shape != "lingering" else 9
    liquid = new()
    lp = liquid.load()
    for (x, y) in body:
        if y >= liquid_top:
            g = 245 if y == liquid_top else (225 if x < 8 else 200)
            if y >= 13:
                g -= 30
            lp[x, y] = (g, g, g, 255)
    bottle = new()
    bp = bottle.load()
    for (x, y) in shape_px:
        if not (y >= liquid_top and (x, y) in body):
            bp[x, y] = hex2rgba("#d4e2f0a0")
    # reflejo del vidrio (por encima del líquido)
    for (x, y) in [(5, 8), (5, 9), (5, 10), (6, 7)]:
        if (x, y) in shape_px:
            bp[x, y] = hex2rgba("#ffffffd0")
    # corcho
    top_y = min(y for (_, y) in neck)
    for x in range(6, 10):
        if shape == "lingering" and x in (6, 9):
            continue
        bp[x, top_y - 1] = hex2rgba("#a8774e")
        bp[x, top_y] = hex2rgba("#8a5e3e")
    bottle = outline(bottle, "#2c2838")
    if shape == "splash":
        # asa lateral en forma de gancho, unida al cuello y al cuerpo
        bpx = bottle.load()
        for (x, y) in [(10, 3), (11, 3), (12, 4), (12, 5), (12, 6), (11, 7)]:
            bpx[x, y] = hex2rgba("#2c2838")
        bpx[11, 4] = hex2rgba("#6d6680")
    return liquid, bottle


for name, shape in [("alchemical_potion", "round"), ("alchemical_splash_potion", "splash"),
                    ("alchemical_lingering_potion", "lingering")]:
    liq, bot = flask(shape)
    put("item/" + name + "_overlay", liq)
    put("item/" + name, bot)


def item_grimoire():
    img = new()
    px = img.load()
    leather = ["#3b1d16", "#5a2c20", "#74392a", "#8e4a36"]
    # páginas (borde derecho e inferior)
    for y in range(2, 15):
        px[13, y] = hex2rgba("#efe4c6" if y % 2 else "#d9cba4")
    for x in range(4, 14):
        px[x, 14] = hex2rgba("#efe4c6" if x % 2 else "#d9cba4")
    # tapa
    for y in range(1, 14):
        for x in range(3, 13):
            i = 2 if (x + y) % 7 else 3
            if x == 3 or y == 13:
                i = 1
            px[x, y] = hex2rgba(leather[i])
    # lomo
    for y in range(1, 15):
        px[2, y] = hex2rgba(leather[0])
        px[3, y] = hex2rgba(leather[1])
    for y in (3, 11):
        px[2, y] = hex2rgba("#c89a2a")
        px[3, y] = hex2rgba("#f0d060")
    # símbolo: círculo con triángulo (oro)
    circle = [(7, 4), (8, 4), (6, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9),
              (7, 10), (8, 10)]
    for x, y in circle:
        px[x, y] = hex2rgba("#c89a2a")
    for x, y in [(7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8), (9, 8)]:
        px[x, y] = hex2rgba("#f0d060")
    # broche
    px[12, 7] = hex2rgba("#f0d060")
    px[12, 8] = hex2rgba("#c89a2a")
    return outline(img, "#1c0e0a")


put("item/salt", item_salt())
put("item/cinnabar", item_cinnabar())
put("item/sulfur", item_sulfur())
put("item/quicksilver", item_quicksilver())
put("item/grimoire", item_grimoire())

if "--gui" not in sys.argv:
    pass

# Las texturas de GUI se generan en gen_alquimia_gui.py
if "--sheet" in sys.argv:
    os.makedirs("build", exist_ok=True)
    # previsualización de menas sobre una piedra aproximada (solo para revisar, no se exporta)
    stone = noise_texture(["#6e6e6e", "#7f7f7f", "#8f8f8f", "#9d9d9d"], 1, octaves=3, base_period=4, speckle=0.2)
    deep = noise_texture(["#3a3a40", "#46464d", "#515159", "#5c5c64"], 2, octaves=3, base_period=4, speckle=0.2)
    nether = noise_texture(["#5e2222", "#6f2a2a", "#823232", "#8e3a3a"], 3, octaves=3, base_period=4, speckle=0.3)
    previews = []
    for name, img in OUT:
        if name.endswith("_overlay") and name.startswith("block/"):
            base = deep if "deepslate" in name else (nether if "nether" in name else stone)
            previews.append(("prev:" + name.split("/")[1][:-8], overlay(base, img)))
    liq, bot = flask("round")
    previews.append(("prev:potion", overlay(tint(liq, "#3fbfff"), bot)))
    liq, bot = flask("splash")
    previews.append(("prev:splash", overlay(tint(liq, "#ff5050"), bot)))
    liq, bot = flask("lingering")
    previews.append(("prev:linger", overlay(tint(liq, "#a050ff"), bot)))
    contact_sheet(OUT + previews, scale=6, cols=8, path="build/alquimia_sheet.png")
    print("hoja:", "build/alquimia_sheet.png")
print(len(OUT), "texturas generadas")
