"""Pequeña librería de pixel art para generar las texturas de los mods.

Todas las texturas se generan con código (sin assets de Mojang), con paletas
pensadas para encajar con el estilo de Minecraft: rampas de 4-6 tonos, luz desde
arriba a la izquierda y contornos oscuros en lugar de negro puro.

Uso típico:
    from pixelart import *
    img = from_ascii(ROWS, {"a": "#ffcc00", ...})
    save(img, "ruta/textura.png")
"""
from __future__ import annotations

import math
import os
import random
from typing import Callable, Dict, Iterable, List, Sequence, Tuple

from PIL import Image

RGBA = Tuple[int, int, int, int]


# --------------------------------------------------------------------------- color
def hex2rgba(c) -> RGBA:
    if isinstance(c, tuple):
        return c if len(c) == 4 else (c[0], c[1], c[2], 255)
    c = c.lstrip("#")
    if len(c) == 6:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    if len(c) == 8:
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16))
    raise ValueError(c)


def mix(a, b, t: float) -> RGBA:
    a, b = hex2rgba(a), hex2rgba(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))  # type: ignore


def shade(c, f: float) -> RGBA:
    """f>0 aclara hacia blanco, f<0 oscurece hacia un tono frío (no negro puro)."""
    c = hex2rgba(c)
    if f >= 0:
        return mix(c, (255, 255, 255, c[3]), f)
    return mix(c, (18, 16, 28, c[3]), -f)


def ramp(dark, light, n: int) -> List[RGBA]:
    return [mix(dark, light, i / (n - 1)) for i in range(n)]


def hue_ramp(base, n=5, spread=0.55, hue_shift=0.04) -> List[RGBA]:
    """Rampa con desplazamiento de tono: sombras más frías/saturadas, luces más cálidas."""
    import colorsys
    r, g, b, a = hex2rgba(base)
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    out = []
    for i in range(n):
        t = i / (n - 1) - 0.5  # -0.5 .. 0.5
        hh = (h - t * hue_shift * 2) % 1.0
        ll = min(0.97, max(0.04, l + t * spread))
        ss = min(1.0, max(0.0, s * (1.0 + (-t) * 0.35)))
        rr, gg, bb = colorsys.hls_to_rgb(hh, ll, ss)
        out.append((int(rr * 255), int(gg * 255), int(bb * 255), a))
    return out


# --------------------------------------------------------------------------- imagen
def new(w=16, h=16, fill=(0, 0, 0, 0)) -> Image.Image:
    return Image.new("RGBA", (w, h), hex2rgba(fill))


def from_ascii(rows: Sequence[str], palette: Dict[str, object]) -> Image.Image:
    rows = [r for r in rows]
    h = len(rows)
    w = max(len(r) for r in rows)
    img = new(w, h)
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in (".", " "):
                continue
            if ch not in palette:
                raise KeyError(f"color '{ch}' sin definir en la paleta")
            px[x, y] = hex2rgba(palette[ch])
    return img


def overlay(base: Image.Image, top: Image.Image) -> Image.Image:
    out = base.copy()
    out.alpha_composite(top)
    return out


def save(img: Image.Image, path: str):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def contact_sheet(images: Sequence[Tuple[str, Image.Image]], scale=8, cols=8, path="sheet.png",
                  bg=(48, 48, 56, 255)):
    """Hoja de contacto ampliada para revisar las texturas a ojo."""
    from PIL import ImageDraw
    cell = 16 * scale + 8
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 12)), bg)
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(images):
        x = (i % cols) * cell + 4
        y = (i // cols) * (cell + 12) + 4
        big = im.resize((im.width * scale * 16 // max(im.width, 16), im.height * scale * 16 // max(im.width, 16)),
                        Image.NEAREST)
        # fondo a cuadros para ver transparencias
        for cy in range(0, big.height, scale * 2):
            for cx in range(0, big.width, scale * 2):
                c = (70, 70, 80, 255) if ((cx + cy) // (scale * 2)) % 2 == 0 else (60, 60, 70, 255)
                d.rectangle([x + cx, y + cy, x + cx + scale * 2 - 1, y + cy + scale * 2 - 1], fill=c)
        sheet.alpha_composite(big, (x, y))
        d.text((x, y + big.height + 1), name[:22], fill=(230, 230, 230, 255))
    sheet.save(path)
    return path


# --------------------------------------------------------------------------- ruido
class ValueNoise:
    def __init__(self, seed: int, period: int = 16):
        self.rng = random.Random(seed)
        self.period = period
        self.grid = {}

    def _v(self, x, y):
        key = (x % self.period, y % self.period)
        if key not in self.grid:
            self.grid[key] = self.rng.random()
        return self.grid[key]

    def at(self, x: float, y: float) -> float:
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        sx = fx * fx * (3 - 2 * fx)
        sy = fy * fy * (3 - 2 * fy)
        a = self._v(x0, y0)
        b = self._v(x0 + 1, y0)
        c = self._v(x0, y0 + 1)
        d = self._v(x0 + 1, y0 + 1)
        return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy


def fbm(seed: int, w=16, h=16, octaves=3, base_period=4) -> List[List[float]]:
    """Ruido fractal que se repite en los bordes (texturas tileables)."""
    layers = []
    for o in range(octaves):
        period = base_period * (2 ** o)
        layers.append((ValueNoise(seed * 31 + o, period), period, 0.5 ** o))
    out = [[0.0] * w for _ in range(h)]
    total = sum(l[2] for l in layers)
    for y in range(h):
        for x in range(w):
            v = 0.0
            for n, period, amp in layers:
                v += n.at(x * period / w, y * period / h) * amp
            out[y][x] = v / total
    # normalizar a 0..1
    lo = min(min(r) for r in out)
    hi = max(max(r) for r in out)
    for y in range(h):
        for x in range(w):
            out[y][x] = (out[y][x] - lo) / (hi - lo + 1e-9)
    return out


def noise_texture(palette: Sequence, seed: int, w=16, h=16, octaves=3, base_period=4, speckle=0.12,
                  gamma=1.0) -> Image.Image:
    """Textura de material (piedra, tierra...) cuantizando ruido fractal a una paleta."""
    rng = random.Random(seed)
    field = fbm(seed, w, h, octaves, base_period)
    img = new(w, h)
    px = img.load()
    n = len(palette)
    for y in range(h):
        for x in range(w):
            v = field[y][x] ** gamma
            v += (rng.random() - 0.5) * speckle
            i = int(max(0, min(n - 1, round(v * (n - 1)))))
            px[x, y] = hex2rgba(palette[i])
    return img


def bevel(img: Image.Image, light=0.18, dark=0.22, rect=None) -> Image.Image:
    """Resalta el borde superior/izquierdo y oscurece el inferior/derecho de un rectángulo."""
    img = img.copy()
    px = img.load()
    x0, y0, x1, y1 = rect or (0, 0, img.width - 1, img.height - 1)
    for x in range(x0, x1 + 1):
        px[x, y0] = shade(px[x, y0], light)
        px[x, y1] = shade(px[x, y1], -dark)
    for y in range(y0, y1 + 1):
        px[x0, y] = shade(px[x0, y], light * 0.7)
        px[x1, y] = shade(px[x1, y], -dark * 0.8)
    return img


def outline(img: Image.Image, color, diagonal=False) -> Image.Image:
    """Añade un contorno de 1px alrededor de los píxeles opacos."""
    out = img.copy()
    src = img.load()
    px = out.load()
    c = hex2rgba(color)
    dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonal:
        dirs += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
    for y in range(img.height):
        for x in range(img.width):
            if src[x, y][3] > 0:
                continue
            for dx, dy in dirs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and src[nx, ny][3] > 0:
                    px[x, y] = c
                    break
    return out


def recolor(img: Image.Image, mapping: Dict[RGBA, RGBA]) -> Image.Image:
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p in mapping:
                px[x, y] = mapping[p]
    return out


def tint(img: Image.Image, color, amount=1.0) -> Image.Image:
    """Multiplica por un color (útil para variantes)."""
    out = img.copy()
    px = out.load()
    c = hex2rgba(color)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            tr, tg, tb = r * c[0] // 255, g * c[1] // 255, b * c[2] // 255
            px[x, y] = (int(r + (tr - r) * amount), int(g + (tg - g) * amount), int(b + (tb - b) * amount), a)
    return out


def luminance_map(img: Image.Image, palette: Sequence) -> Image.Image:
    """Re-mapea una textura en escala de grises a una paleta (conserva alfa)."""
    out = img.copy()
    px = out.load()
    n = len(palette)
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            c = hex2rgba(palette[int(max(0, min(n - 1, round(l * (n - 1)))))])
            px[x, y] = (c[0], c[1], c[2], a)
    return out


# --------------------------------------------------------------------------- menas
ORE_SHAPES = [
    # cada forma es una lista de (dx, dy); se colorean con luz arriba-izquierda
    [(0, 0), (1, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 0), (2, 0), (1, 1), (2, 1)],
    [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)],
    [(0, 0), (1, 0), (1, 1), (2, 1)],
    [(0, 0), (0, 1), (1, 1), (1, 2), (2, 2)],
    [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)],
    [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 1), (1, 0)],
]


def ore_overlay(colors: Sequence, seed: int, embed, clusters=6, shapes=None, sparkle=None,
                min_gap=1, layout=None) -> Image.Image:
    """Genera SOLO las vetas del mineral (fondo transparente) para dibujarlas encima
    de la piedra del juego (o del resource pack activo).

    colors: rampa [sombra, medio, claro, brillo] del mineral.
    embed: color opaco de la "hendidura" alrededor de la veta (tono oscuro de la roca base);
           así la veta parece incrustada en la piedra.
    layout: lista opcional de (x, y, índice_de_forma) para un diseño fijo.
    """
    rng = random.Random(seed)
    shapes = shapes or ORE_SHAPES
    occ = [[False] * 16 for _ in range(16)]
    placed: List[List[Tuple[int, int]]] = []

    def fits(cells):
        for x, y in cells:
            if not (1 <= x <= 14 and 1 <= y <= 14):
                return False
            for dx in range(-min_gap - 1, min_gap + 2):
                for dy in range(-min_gap - 1, min_gap + 2):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and occ[ny][nx]:
                        return False
        return True

    if layout:
        for (ox, oy, si) in layout:
            cells = [(ox + dx, oy + dy) for dx, dy in shapes[si % len(shapes)]]
            placed.append(cells)
            for x, y in cells:
                occ[y][x] = True
    else:
        tries = 0
        while len(placed) < clusters and tries < 4000:
            tries += 1
            shape = rng.choice(shapes)
            ox, oy = rng.randint(1, 13), rng.randint(1, 13)
            cells = [(ox + dx, oy + dy) for dx, dy in shape]
            if fits(cells):
                placed.append(cells)
                for x, y in cells:
                    occ[y][x] = True

    img = new()
    px = img.load()
    shadow, mid, light = hex2rgba(colors[0]), hex2rgba(colors[1]), hex2rgba(colors[2])
    bright = hex2rgba(colors[3]) if len(colors) > 3 else light
    emb = hex2rgba(embed)
    for cells in placed:
        s = set(cells)
        for x, y in cells:
            up = (x, y - 1) in s
            left = (x - 1, y) in s
            down = (x, y + 1) in s
            right = (x + 1, y) in s
            if not up and not left:
                c = light
            elif not down and not right:
                c = shadow
            elif not down or not right:
                c = mix(mid, shadow, 0.35)
            else:
                c = mid
            px[x, y] = c
        # un píxel de brillo en la esquina superior izquierda del cúmulo
        tl = min(cells, key=lambda p: (p[0] + p[1], p[1]))
        if len(cells) >= 4:
            px[tl[0], tl[1]] = bright
        # hendidura: abajo y a la derecha de la veta
        for x, y in cells:
            for dx, dy in ((1, 0), (0, 1), (1, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in s and px[nx, ny][3] == 0:
                    px[nx, ny] = emb
    if sparkle:
        sp = hex2rgba(sparkle)
        for cells in placed:
            if rng.random() < 0.5:
                x, y = rng.choice(cells)
                px[x, y] = sp
    return img


def upscale(img: Image.Image, f: int) -> Image.Image:
    return img.resize((img.width * f, img.height * f), Image.NEAREST)


def voronoi_crystals(palette, seed, cells=9, edge=None, w=16, h=16, light_dir=(-0.6, -0.8)):
    """Textura cristalina repetible: celdas de Voronoi con una cara plana iluminada cada una
    y bordes finos. palette va de oscuro a claro."""
    rng = random.Random(seed)
    pts = [(rng.uniform(0, w), rng.uniform(0, h)) for _ in range(cells)]
    normals = []
    for _ in pts:
        a = rng.uniform(0, math.pi * 2)
        normals.append((math.cos(a), math.sin(a), rng.uniform(0.3, 1.0)))
    img = new(w, h)
    px = img.load()
    owner = [[0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            best, second, bi = 1e9, 1e9, 0
            for i, (cx, cy) in enumerate(pts):
                dx = min(abs(x + 0.5 - cx), w - abs(x + 0.5 - cx))
                dy = min(abs(y + 0.5 - cy), h - abs(y + 0.5 - cy))
                d = dx * dx + dy * dy
                if d < best:
                    second, best, bi = best, d, i
                elif d < second:
                    second = d
            owner[y][x] = bi
            nx, ny, nz = normals[bi]
            lum = 0.55 + 0.45 * (nx * light_dir[0] + ny * light_dir[1]) * (1 - nz * 0.5)
            lum += (rng.random() - 0.5) * 0.08
            n = len(palette)
            px[x, y] = hex2rgba(palette[int(max(0, min(n - 1, round(lum * (n - 1)))))])
    if edge:
        e = hex2rgba(edge)
        out = img.copy()
        op = out.load()
        for y in range(h):
            for x in range(w):
                if owner[y][x] != owner[y][(x + 1) % w] or owner[y][x] != owner[(y + 1) % h][x]:
                    op[x, y] = e
        img = out
    return img
