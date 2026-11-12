"""Shared pixel-art toolkit for PowerFactor's machinery, used by gen_textures.py.

Everything is lit from the top left. Materials are short colour ramps that shift hue as they
darken (cool shadows, warm highlights) instead of scaling one colour, and surfaces get their
texture from a few deliberate marks (brushed streaks, wear along edges, bolts with a highlight and
a cast shadow) rather than per-pixel noise.
"""
import colorsys, random
from PIL import Image, ImageDraw

CLEAR = (0, 0, 0, 0)


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def ramp(mid, steps=7, contrast=1.0, cool=0.035, warm=0.02):
    """A ramp around `mid` (hex), darkest first. Shadows drift toward blue and gain saturation,
    highlights drift toward yellow and wash out, as painted metal does under daylight."""
    r, g, b = [v / 255 for v in hexc(mid)[:3]]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    out = []
    for i in range(steps):
        t = (i - (steps - 1) / 2) / ((steps - 1) / 2)            # -1 (dark) .. 1 (light)
        if t < 0:
            # Toward blue-violet (hue ~0.66) by the shortest way round.
            d = ((0.66 - h + 0.5) % 1.0) - 0.5
            hh = (h + d * cool * -t * 3) % 1.0
            ss = min(1.0, s * (1 + 0.28 * -t) + 0.04 * -t)
            vv = v * (1 - 0.52 * contrast * -t)
        else:
            d = ((0.13 - h + 0.5) % 1.0) - 0.5
            hh = (h + d * warm * t * 3) % 1.0
            ss = s * (1 - 0.42 * t)
            vv = min(1.0, v + (1 - v) * 0.62 * contrast * t)
        rr, gg, bb = colorsys.hsv_to_rgb(hh, max(0.0, ss), max(0.0, vv))
        out.append((int(round(rr * 255)), int(round(gg * 255)), int(round(bb * 255)), 255))
    return out


# Materials. Index 3 of 7 is the flat colour; 0 the deepest shadow, 6 the brightest glint.
STEEL = ramp("7c858c")
DARK = ramp("3a4249")
IRON = ramp("555d63")
CAST = ramp("2f353a", contrast=0.85)
CHROME = ramp("b9c4cb", contrast=0.9)
RED = ramp("b8392c")
ORANGE = ramp("cf6a25")
YELLOW = ramp("e3b22b")
BLUE = ramp("3f6583")
TEAL = ramp("2f7c78")
GREEN = ramp("4b7a4a")
OLIVE = ramp("6c6f4f")
BRASS = ramp("b98d3c")
COPPER = ramp("b5683f")
RUBBER = ramp("26292c", contrast=0.8)
CONCRETE = ramp("8b8d88", contrast=0.7)
WHITE = ramp("d9dedf", contrast=0.75)
INSULATION = ramp("b7bcb5", contrast=0.8)
BRICK = ramp("8a4a34")
BLACK = hexc("14171a")
GLASS_DARK = hexc("0d1a1f")
LCD = [hexc("0b2018"), hexc("1d5a3f"), hexc("3fbf7f"), hexc("9dffc8")]
AMBER = [hexc("3a2408"), hexc("9a5a12"), hexc("f0a030"), hexc("ffe08a")]
FLAME = [hexc("7a1f0c"), hexc("d8481a"), hexc("ff8a22"), hexc("ffc542"), hexc("fff3b0")]
HAZARD_A, HAZARD_B = hexc("e8b62a"), hexc("1d2023")


def blank(w=16, h=16, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):
    """Inclusive filled rectangle, clipped to the image."""
    for y in range(max(0, y0), min(img.height - 1, y1) + 1):
        for x in range(max(0, x0), min(img.width - 1, x1) + 1):
            img.putpixel((x, y), c)


def hline(img, x0, x1, y, c):
    rect(img, x0, y, x1, y, c)


def vline(img, x, y0, y1, c):
    rect(img, x, y0, x, y1, c)


def plate(w, h, mat, seed=0, tone=3, grain="h", wear=0.5):
    """Sheet metal: flat tone, a soft top-left to bottom-right falloff, and sparse brushed streaks."""
    r = random.Random(seed)
    img = blank(w, h, mat[tone])
    long_side = w if grain == "h" else h
    lines = h if grain == "h" else w
    for line in range(lines):
        # A streak is a short run one step lighter or darker, never a lone speckle.
        tries = 1 if r.random() < 0.55 * wear + 0.2 else 0
        for _ in range(tries):
            length = r.randint(2, max(2, long_side // 3))
            start = r.randint(-1, long_side - 1)
            c = mat[tone + (1 if r.random() < 0.5 else -1)]
            for i in range(start, start + length):
                x, y = (i, line) if grain == "h" else (line, i)
                put(img, x, y, c)
    return img


def bevel(img, x0, y0, x1, y1, mat, tone=3, raised=True, depth=2):
    """One-pixel edge light on an inclusive box: raised boxes catch light on top and left."""
    hi, lo = mat[min(6, tone + depth)], mat[max(0, tone - depth)]
    if not raised:
        hi, lo = lo, hi
    hline(img, x0, x1, y0, hi)
    vline(img, x0, y0, y1, hi)
    hline(img, x0, x1, y1, lo)
    vline(img, x1, y0, y1, lo)
    # Corners where light meets shade stay neutral, which keeps the edge from reading as a frame.
    put(img, x1, y0, mat[tone])
    put(img, x0, y1, mat[tone])


def panel(img, x0, y0, x1, y1, mat, tone=3, raised=True, seed=None, grain="h"):
    """A bevelled sub-panel filled with its own plate."""
    if seed is not None:
        img.paste(plate(x1 - x0 + 1, y1 - y0 + 1, mat, seed, tone, grain), (x0, y0))
    else:
        rect(img, x0, y0, x1, y1, mat[tone])
    bevel(img, x0, y0, x1, y1, mat, tone, raised)


def bolt(img, x, y, mat=STEEL, tone=3):
    """Round-head bolt: a glint with a shadow on the lower right."""
    put(img, x, y, mat[min(6, tone + 3)])
    put(img, x + 1, y, mat[max(0, tone - 1)])
    put(img, x, y + 1, mat[max(0, tone - 1)])
    put(img, x + 1, y + 1, mat[max(0, tone - 3)])


def rivet(img, x, y, mat=STEEL, tone=3):
    put(img, x, y, mat[min(6, tone + 2)])
    put(img, x + 1, y + 1, mat[max(0, tone - 2)])


def seam(img, x0, y0, x1, y1, mat, tone=3):
    """A panel joint: dark line with a lit lip below or to its right."""
    if y0 == y1:
        hline(img, x0, x1, y0, mat[max(0, tone - 2)])
        hline(img, x0, x1, y0 + 1, mat[min(6, tone + 1)])
    else:
        vline(img, x0, y0, y1, mat[max(0, tone - 2)])
        vline(img, x0 + 1, y0, y1, mat[min(6, tone + 1)])


def vent(img, x0, y0, x1, y1, mat=DARK, horizontal=True, tone=2):
    """Louvres: each slat is a lit edge over a deep slot."""
    rect(img, x0, y0, x1, y1, mat[max(0, tone - 2)])
    if horizontal:
        for y in range(y0, y1 + 1, 2):
            hline(img, x0, x1, y, mat[min(6, tone + 2)])
    else:
        for x in range(x0, x1 + 1, 2):
            vline(img, x, y0, y1, mat[min(6, tone + 2)])


def stripes(img, x0, y0, x1, y1, a=HAZARD_A, b=HAZARD_B, width=2, lean=1, phase=0):
    """Diagonal warning stripes, `width` pixels each."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, a if ((x + lean * y + phase) // width) % 2 == 0 else b)
    # A line of shade under the band seats it on the surface.
    for x in range(x0, x1 + 1):
        c = img.getpixel((x, y1)) if 0 <= x < img.width and 0 <= y1 < img.height else None
        if c:
            put(img, x, y1, mix(c, BLACK, 0.25))


def tube_h(img, x0, y0, x1, y1, mat, light=0.3):
    """A horizontal pipe or drum: shaded across its height with the glint above centre."""
    n = y1 - y0 + 1
    for i in range(n):
        t = (i + 0.5) / n
        d = abs(t - light)
        idx = 6 - int(round(min(1.0, d / max(light, 1 - light)) * 6 * 0.95 + 0.6))
        hline(img, x0, x1, y0 + i, mat[max(0, min(6, idx))])


def tube_v(img, x0, y0, x1, y1, mat, light=0.3):
    n = x1 - x0 + 1
    for i in range(n):
        t = (i + 0.5) / n
        d = abs(t - light)
        idx = 6 - int(round(min(1.0, d / max(light, 1 - light)) * 6 * 0.95 + 0.6))
        vline(img, x0 + i, y0, y1, mat[max(0, min(6, idx))])


def disc(img, cx, cy, radius, mat, tone=3, rim=True):
    """A filled circle about (cx, cy), which may sit between pixels, lit on its upper left edge."""
    for y in range(int(cy - radius) - 1, int(cy + radius) + 2):
        for x in range(int(cx - radius) - 1, int(cx + radius) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d <= radius:
                c = mat[tone]
                if rim and d > radius - 1.0:
                    c = mat[min(6, tone + 2)] if dx + dy < 0 else mat[max(0, tone - 2)]
                put(img, x, y, c)


def gauge(img, cx, cy, radius, needle=0.25, body=DARK, face=WHITE, hand=RED):
    """A dial: dark bezel, pale face, a needle from the centre. `needle` runs 0..1 over the upper arc."""
    import math
    disc(img, cx, cy, radius, body, 2)
    disc(img, cx, cy, radius - 1, face, 4, rim=False)
    a = math.pi * (1.15 - 1.3 * needle)
    for i in range(1, int(radius)):
        put(img, int(cx + math.cos(a) * i * 0.8), int(cy - math.sin(a) * i * 0.8), hand[3])
    put(img, int(cx), int(cy), body[1])


def lamp(img, x, y, colours, on):
    """Two-by-two indicator in a dark bezel; `colours` is a four-step off-to-lit ramp."""
    rect(img, x - 1, y - 1, x + 2, y + 2, BLACK)
    if on:
        rect(img, x, y, x + 1, y + 1, colours[2])
        put(img, x, y, colours[3])
    else:
        rect(img, x, y, x + 1, y + 1, colours[0])
        put(img, x, y, colours[1])


def screen(img, x0, y0, x1, y1, lit=True, colours=LCD, pattern=None, frame=0):
    """A recessed display. `pattern(x, y, frame)` returns 0..3 into `colours` for each inner pixel."""
    rect(img, x0, y0, x1, y1, BLACK)
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            level = pattern(x - x0 - 1, y - y0 - 1, frame) if pattern else 0
            if not lit:
                level = min(level, 1)
            put(img, x, y, colours[level])
    # Glass catches the sky along its top edge.
    if lit:
        put(img, x0 + 1, y0 + 1, mix(colours[1], hexc("ffffff"), 0.25))


def shade_edges(img, mat, tone=3):
    """Outer bevel for a whole tile."""
    bevel(img, 0, 0, img.width - 1, img.height - 1, mat, tone)
    return img


def weather(img, seed, amount=0.5, colour=None):
    """Grime gathered toward the bottom edge and around dark features: a few darker pixels, clustered."""
    r = random.Random(seed)
    w, h = img.size
    for _ in range(int(w * h * 0.03 * amount)):
        x = r.randrange(w)
        y = min(h - 1, int(h - 1 - abs(r.gauss(0, h * 0.22))))
        c = img.getpixel((x, y))
        if c[3]:
            img.putpixel((x, y), mix(c, colour or BLACK, 0.16))
    return img


def frames(images):
    """Stack equally sized frames vertically, as Minecraft's animated textures expect."""
    w, h = images[0].size
    sheet = Image.new("RGBA", (w, h * len(images)), CLEAR)
    for i, im in enumerate(images):
        sheet.paste(im, (0, i * h))
    return sheet


def paste(sheet, tile, img):
    """Place a painted tile on a parts sheet; `tile` is (u, v, w, h) and crops anything larger."""
    u, v, w, h = tile
    sheet.paste(img.crop((0, 0, w, h)), (u, v))
