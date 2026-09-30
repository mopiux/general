#!/usr/bin/env python3
"""Genera las texturas de interfaz de Alquimia Cartográfica y el logo del mod.

    python3 tools/gen_alquimia_gui.py [--sheet]

Las coordenadas de ranuras y paneles coinciden con CauldronMenu/CauldronScreen y GrimoireScreen.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import *  # noqa
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "alquimia", "src", "main", "resources")
GUI = os.path.join(ROOT, "assets", "alquimia", "textures", "gui")

PANEL = (198, 198, 198, 255)
PANEL_DARK = (85, 85, 85, 255)
WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)
SLOT_DARK = (55, 55, 55, 255)
SLOT_FILL = (139, 139, 139, 255)


def panel(img, x0, y0, w, h):
    """Panel con el estilo de las interfaces de Minecraft (borde redondeado, luz y sombra)."""
    px = img.load()
    x1, y1 = x0 + w - 1, y0 + h - 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # esquinas redondeadas (radio ~3)
            dx = min(x - x0, x1 - x)
            dy = min(y - y0, y1 - y)
            if dx + dy < 3 and not (dx >= 1 and dy >= 1 and dx + dy == 2):
                if dx + dy < 2:
                    continue
            px[x, y] = PANEL
    # contorno negro
    for x in range(x0 + 3, x1 - 2):
        px[x, y0] = BLACK
        px[x, y1] = BLACK
    for y in range(y0 + 3, y1 - 2):
        px[x0, y] = BLACK
        px[x1, y] = BLACK
    for (dx, dy) in ((1, 2), (2, 1), (1, 1)):
        for (sx, sy, fx, fy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
            if (dx, dy) != (1, 1):
                px[sx + fx * dx, sy + fy * dy] = BLACK
    # luz arriba/izquierda y sombra abajo/derecha (2 px)
    for i in (1, 2):
        for x in range(x0 + 2, x1 - 1):
            if px[x, y0 + i] == PANEL:
                px[x, y0 + i] = WHITE
            if px[x, y1 - i] == PANEL:
                px[x, y1 - i] = PANEL_DARK
        for y in range(y0 + 2, y1 - 1):
            if px[x0 + i, y] == PANEL:
                px[x0 + i, y] = WHITE
            if px[x1 - i, y] == PANEL:
                px[x1 - i, y] = PANEL_DARK
    px[x0 + 2, y0 + 2] = WHITE
    px[x1 - 2, y1 - 2] = PANEL_DARK
    px[x1 - 1, y0 + 1] = PANEL
    px[x0 + 1, y1 - 1] = PANEL


def slot(img, x, y, size=18):
    px = img.load()
    for yy in range(y, y + size):
        for xx in range(x, x + size):
            px[xx, yy] = SLOT_FILL
    for i in range(size - 1):
        px[x + i, y] = SLOT_DARK
        px[x, y + i] = SLOT_DARK
        px[x + size - 1 - i, y + size - 1] = WHITE
        px[x + size - 1, y + size - 1 - i] = WHITE
    px[x + size - 1, y] = SLOT_FILL
    px[x, y + size - 1] = SLOT_FILL


def inset(img, x0, y0, x1, y1, fill=None):
    """Recuadro hundido (borde oscuro arriba/izquierda y claro abajo/derecha)."""
    px = img.load()
    if fill:
        for y in range(y0 + 1, y1):
            for x in range(x0 + 1, x1):
                px[x, y] = hex2rgba(fill)
    for x in range(x0, x1 + 1):
        px[x, y0] = SLOT_DARK
        px[x, y1] = WHITE
    for y in range(y0, y1 + 1):
        px[x0, y] = SLOT_DARK
        px[x1, y] = WHITE
    px[x1, y0] = PANEL
    px[x0, y1] = PANEL


def parchment(seed=7, size=64):
    field = fbm(seed, size, size, octaves=4, base_period=4)
    rng = random.Random(seed)
    img = new(size, size)
    px = img.load()
    base = (228, 211, 170)
    for y in range(size):
        for x in range(size):
            v = (field[y][x] - 0.5) * 26 + (rng.random() - 0.5) * 7
            px[x, y] = (int(base[0] + v), int(base[1] + v * 0.95), int(base[2] + v * 0.8), 255)
    # fibras horizontales
    for _ in range(40):
        x, y = rng.randrange(size), rng.randrange(size)
        length = rng.randint(3, 8)
        for i in range(length):
            c = px[(x + i) % size, y]
            px[(x + i) % size, y] = (c[0] - 12, c[1] - 12, c[2] - 12, 255)
    # motas
    for _ in range(18):
        x, y = rng.randrange(size), rng.randrange(size)
        c = px[x, y]
        px[x, y] = (c[0] - 35, c[1] - 38, c[2] - 40, 255)
    return img


# ------------------------------------------------------------------------------- caldero
MAP_X, MAP_Y, MAP_W, MAP_H = 7, 17, 176, 126
PANEL_X = 190


def cauldron_gui():
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 358, 188
    panel(img, 0, 0, W, H)
    # marco del mapa
    inset(img, MAP_X - 1, MAP_Y - 1, MAP_X + MAP_W, MAP_Y + MAP_H)
    paper = parchment(3, 64)
    for ty in range(0, MAP_H, 64):
        for tx in range(0, MAP_W, 64):
            crop = paper.crop((0, 0, min(64, MAP_W - tx), min(64, MAP_H - ty)))
            img.paste(crop, (MAP_X + tx, MAP_Y + ty))
    # ranuras de entrada y salida
    for x in (190, 212, 234):
        slot(img, x, 18)
    for i in range(3):
        slot(img, 282 + i * 18, 18)
    # flecha
    px = img.load()
    arrow = [
        "......................",
        "..............##......",
        "..............#a#.....",
        "#################a#...",
        "#aaaaaaaaaaaaaaaaaa#..",
        "#aaaaaaaaaaaaaaaaaaa#.",
        "#aaaaaaaaaaaaaaaaaa#..",
        "#################a#...",
        "..............#a#.....",
        "..............##......",
    ]
    for yy, row in enumerate(arrow):
        for xx, ch in enumerate(row):
            if ch == "#":
                px[256 + xx, 22 + yy] = (110, 110, 110, 255)
            elif ch == "a":
                px[256 + xx, 22 + yy] = (160, 160, 160, 255)
    # recuadro de estado
    inset(img, 188, 38, 352, 94, fill="#bdbdbd")
    for y in range(40, 93):
        px[259, y] = SLOT_DARK
        px[260, y] = WHITE
    # inventario del jugador
    for r in range(3):
        for c in range(9):
            slot(img, 190 + c * 18, 106 + r * 18)
    for c in range(9):
        slot(img, 190 + c * 18, 164)
    # pequeño adorno: símbolos de la tria prima junto al título (sal, mercurio, azufre)
    return img


# ------------------------------------------------------------------------------- grimorio
def grimoire_gui():
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 300, 190
    px = img.load()
    leather = [(58, 28, 20), (78, 38, 26), (96, 48, 32), (112, 58, 38)]
    field = fbm(11, W, H, octaves=3, base_period=8)
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if edge == 0 and (x in (0, W - 1)) and (y in (0, H - 1)):
                continue
            c = leather[int(min(3, field[y][x] * 3.2))]
            if edge <= 1:
                c = (34, 16, 12)
            px[x, y] = c + (255,) if len(c) == 3 else c
    # costura dorada
    for x in range(5, W - 5, 3):
        px[x, 4] = (176, 132, 52, 255)
        px[x, H - 5] = (176, 132, 52, 255)
    for y in range(5, H - 5, 3):
        px[4, y] = (176, 132, 52, 255)
        px[W - 5, y] = (176, 132, 52, 255)
    # páginas
    paper = parchment(5, 64)
    pp = paper.load()
    for (x0, x1) in ((8, 148), (152, 292)):
        for y in range(8, H - 8):
            for x in range(x0, x1):
                c = pp[x % 64, y % 64]
                # sombra hacia el lomo
                d = (x - 148) if x0 == 8 else (152 - x)
                if x0 == 8:
                    k = max(0, 1 - (148 - x) / 14)
                else:
                    k = max(0, 1 - (x - 152) / 14)
                f = 1 - 0.28 * k * k
                px[x, y] = (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)
        # grosor de las hojas abajo
        for x in range(x0, x1):
            px[x, H - 8] = (170, 150, 110, 255)
            px[x, H - 7] = (205, 188, 150, 255)
    # lomo
    for y in range(6, H - 6):
        for x in (148, 149, 150, 151):
            px[x, y] = (40, 20, 14, 255)
    # esquinas doradas
    for (cx, cy, fx, fy) in ((6, 6, 1, 1), (W - 7, 6, -1, 1), (6, H - 7, 1, -1), (W - 7, H - 7, -1, -1)):
        for i in range(7):
            px[cx + fx * i, cy] = (214, 170, 70, 255)
            px[cx, cy + fy * i] = (214, 170, 70, 255)
        px[cx + fx, cy + fy] = (250, 220, 120, 255)
    # marco del mapa en la página izquierda
    for x in range(15, 145):
        px[x, 29] = (90, 64, 40, 255)
        px[x, 170] = (90, 64, 40, 255)
    for y in range(29, 171):
        px[15, y] = (90, 64, 40, 255)
        px[144, y] = (90, 64, 40, 255)
    return img


# ------------------------------------------------------------------------------- íconos
def icons():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def paste(rows, pal, x, y):
        img.alpha_composite(from_ascii(rows, pal), (x, y))

    bone = {"o": "#2a1a14", "w": "#efe6d2", "s": "#c9bca2", "e": "#1a0f0a"}
    paste([
        "...oooooo...",
        "..owwwwwwo..",
        ".owwwwwwwwo.",
        ".owwwwwwwso.",
        ".oweewweeso.",
        ".oweewweeso.",
        ".owwwsswwso.",
        "..owwsswso..",
        "...owwwso...",
        "...owowoo...",
        "...oo.oo....",
        "............",
    ], bone, 0, 0)
    paste([
        "....oooo....",
        "....obbo....",
        "....oggo....",
        "....oggo....",
        "...oggggo...",
        "..oggwgggo..",
        ".oggwllllgo.",
        ".ogwllllllo.",
        ".ogllllllmo.",
        "..ollllmmo..",
        "...oommoo...",
        "....oooo....",
    ], {"o": "#1e1a28", "b": "#8a5e3e", "g": "#cfe0f0", "w": "#ffffff", "l": "#e0b040", "m": "#b0801c"}, 12, 0)
    drop = [
        "....o....",
        "...obo...",
        "...obo...",
        "..obbbo..",
        ".obwbbbo.",
        ".obwbbbo.",
        ".obbbbdo.",
        "..obddo..",
        "...ooo...",
    ]
    paste(drop, {"o": "#1b3a78", "b": "#3f76e4", "w": "#a8c8ff", "d": "#2a58b8"}, 24, 0)
    paste(drop, {"o": "#6e6e6e", "b": "#00000000", "w": "#00000000", "d": "#00000000"}, 33, 0)
    flame = [
        "....o....",
        "...oyo...",
        "..oyyo...",
        "..oyyyo..",
        ".oyrryo..",
        ".oyrrryo.",
        ".oyrwryo.",
        "..oyyyo..",
        "...ooo...",
    ]
    paste(flame, {"o": "#7a2a08", "y": "#f0a020", "r": "#e05010", "w": "#fff0a0"}, 42, 0)
    paste(flame, {"o": "#707070", "y": "#00000000", "r": "#00000000", "w": "#00000000"}, 51, 0)
    # origen: triángulo invertido (símbolo alquímico del agua)
    paste([
        "oooooooooo",
        "obbbbbbbbo",
        ".obbbbbbo.",
        ".obbwbbbo.",
        "..obbbbo..",
        "..obbbbo..",
        "...obbo...",
        "...obbo...",
        "....oo....",
        "..........",
    ], {"o": "#123a80", "b": "#3f86e8", "w": "#bcd8ff"}, 0, 12)
    # signo de pregunta (esencia desconocida)
    paste([
        "..oooooo..",
        ".oqqqqqqo.",
        "oqqoooqqqo",
        "oqqqqqoqqo",
        "oqqqqoqqqo",
        "oqqqoqqqqo",
        "oqqqqqqqqo",
        "oqqqoqqqqo",
        ".oqqqqqqo.",
        "..oooooo..",
    ], {"o": "#5a4020", "q": "#d8c49a"}, 12, 12)
    # íconos fantasma para las ranuras (se dibujan semitransparentes)
    g = {"x": "#3a3a3a"}
    paste([
        "................",
        "..........xx....",
        ".........xxxx...",
        "........xxxxxx..",
        ".......xxxxxxx..",
        "......xxxxxxxx..",
        ".....xxxxxxxx...",
        "....xxxxxxxx....",
        "...xxxxxxxx.....",
        "...xxxxxxx......",
        "..xxxxxxx.......",
        "..xxxxx.........",
        ".xx.............",
        "xx..............",
        "x...............",
        "................",
    ], g, 0, 32)
    paste([
        "................",
        "................",
        ".....xxxxxxx....",
        "....xxxxxxxx....",
        "...xxxxxxxxx....",
        "..xxxxxxxxxx....",
        "..xxxxxxxxxxx...",
        "..xxxxxxxxxxxx..",
        "..xxxxxxxxxxxx..",
        "..xxxxxxxxxxx...",
        "..xxxxxxxxxx....",
        "..xxxxxxxxx.....",
        "..xxxxxxxx......",
        "................",
        "................",
        "................",
    ], g, 16, 32)
    paste([
        "................",
        "......xxxx......",
        "......xxxx......",
        ".......xx.......",
        ".......xx.......",
        ".......xx.......",
        "......xxxx......",
        ".....xxxxxx.....",
        "....xxxxxxxx....",
        "...xxxxxxxxxx...",
        "...xxxxxxxxxx...",
        "...xxxxxxxxxx...",
        "...xxxxxxxxxx...",
        "....xxxxxxxx....",
        ".....xxxxxx.....",
        "................",
    ], g, 32, 32)
    # íconos de los botones chicos (7x7)
    ink = {"x": "#3a2816"}
    paste(["...x...", "...x...", "...x...", "xxxxxxx", "...x...", "...x...", "...x..."], ink, 0, 48)
    paste([".......", ".......", ".......", "xxxxxxx", ".......", ".......", "......."], ink, 8, 48)
    paste(["..xxx..", ".x...x.", "x.....x", "x..x..x", "x.....x", ".x...x.", "..xxx.."], ink, 16, 48)
    paste(["..xxx..", "xxxxxxx", ".x.x.x.", ".x.x.x.", ".x.x.x.", ".x.x.x.", "..xxx.."], {"x": "#8a1a10"}, 24, 48)
    paste(["..xxx..", ".x...x.", ".....x.", "...xx..", "...x...", ".......", "...x..."], ink, 32, 48)
    return img


# ------------------------------------------------------------------------------- logo
FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "Á": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10011", "10001", "10001", "01111"],
    "I": ["111", "010", "010", "010", "010", "010", "111"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10001", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    " ": ["000", "000", "000", "000", "000", "000", "000"],
}


def text_pixels(text, scale=1):
    cols = []
    for ch in text:
        g = FONT[ch]
        w = len(g[0])
        for x in range(w):
            cols.append([g[y][x] == "1" for y in range(7)])
        cols.append([False] * 7)
    img = new(len(cols) * scale, 7 * scale)
    return cols


def draw_text(img, text, x0, y0, color, shadow, scale=1):
    px = img.load()
    x = x0
    for ch in text:
        g = FONT[ch]
        pix = [(xx, yy) for yy, row in enumerate(g) for xx, bit in enumerate(row) if bit == "1"]
        if ch == "Á":  # tilde por encima de la letra
            pix += [(3, -2), (2, -1)]
        for layer, col in ((1, shadow), (0, color)):
            if not col:
                continue
            for xx, yy in pix:
                for sy in range(scale):
                    for sx in range(scale):
                        px[x + xx * scale + sx + layer, y0 + yy * scale + sy + layer] = hex2rgba(col)
        x += (len(g[0]) + 1) * scale
    return x


def logo():
    small = new(120, 34)
    # caldero con líquido burbujeante
    cauldron = [
        "........oo.o........",
        ".......obo.......o..",
        "....o...o...o...obo.",
        "..oooooooooooooooo..",
        ".occcccccccccccccco.",
        ".ogggggggggggggggggo",
        "..oiiiiiiiiiiiiiio..",
        "..oiiiiiyyyiiiiiio..",
        "..oiiiiyyyyyiiiiio..",
        "..occcccccccccccco..",
        "..oiiiiiiiiiiiiiio..",
        "...oiiiiiiiiiiiio...",
        "....oiiiiiiiiiio....",
        "....oo.......oo.....",
    ]
    pal = {"o": "#1c1622", "c": "#c4714a", "g": "#5ac85a", "b": "#a0f0a0", "i": "#403b47", "y": "#f0d060"}
    small.alpha_composite(upscale(from_ascii(cauldron, pal), 1), (4, 12))
    draw_text(small, "ALQUIMIA", 30, 4, "#f0d060", "#6a4a10", scale=2)
    draw_text(small, "CARTOGRÁFICA", 31, 24, "#e8dcc0", "#3a2816")
    return upscale(small, 4)


os.makedirs(GUI, exist_ok=True)
imgs = {
    "cauldron": cauldron_gui(),
    "grimoire": grimoire_gui(),
    "icons": icons(),
    "parchment": parchment(7, 64),
}
for name, img in imgs.items():
    img.save(os.path.join(GUI, name + ".png"))
lg = logo()
lg.save(os.path.join(ROOT, "logo.png"))
if "--sheet" in sys.argv:
    os.makedirs("build", exist_ok=True)
    upscale(imgs["cauldron"].crop((0, 0, 360, 190)), 2).save("build/gui_cauldron.png")
    upscale(imgs["grimoire"].crop((0, 0, 300, 190)), 2).save("build/gui_grimoire.png")
    upscale(imgs["icons"], 6).save("build/gui_icons.png")
    lg.save("build/logo.png")
print("GUI generada")
