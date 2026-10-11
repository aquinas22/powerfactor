"""Generates PowerFactor's textures as original pixel art. Run: python3 tools/gen_textures.py"""
import os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "powerfactor", "textures")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def save(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


STEEL = hexc("6f767d")
STEEL_DARK = hexc("474d53")
STEEL_LIGHT = hexc("9aa3ab")
RIVET = hexc("b9c2c9")


def steel_plate(seed):
    """Brushed steel with a bevelled edge."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        streak = r.uniform(-0.04, 0.04)
        for x in range(16):
            img.putpixel((x, y), shade(STEEL, 1 + streak + r.uniform(-0.05, 0.05)))
    for i in range(16):
        img.putpixel((i, 0), STEEL_LIGHT)
        img.putpixel((0, i), STEEL_LIGHT)
        img.putpixel((i, 15), STEEL_DARK)
        img.putpixel((15, i), STEEL_DARK)
    return img


def rivets(img, pts):
    for x, y in pts:
        img.putpixel((x, y), RIVET)
        img.putpixel((x + 1, y + 1), STEEL_DARK)


CORNERS = [(2, 2), (12, 2), (2, 12), (12, 12)]


def side():
    img = steel_plate(1)
    # Turbine casing: a horizontal drum shaded like a cylinder.
    band = [0.55, 0.75, 0.95, 1.1, 1.15, 1.05, 0.85, 0.6]
    casing = hexc("3d5a73")
    for i, f in enumerate(band):
        for x in range(1, 15):
            img.putpixel((x, 4 + i), shade(casing, f))
    # Flange bolts on the drum.
    for x in (3, 7, 11):
        img.putpixel((x, 4), RIVET)
        img.putpixel((x, 11), RIVET)
    # Hazard stripe along the base.
    for x in range(1, 15):
        for y in (13, 14):
            img.putpixel((x, y), hexc("e8b923") if (x + y) // 2 % 2 == 0 else hexc("222222"))
    rivets(img, [(2, 1), (12, 1)])
    return img


def top():
    img = steel_plate(2)
    d = ImageDraw.Draw(img)
    # Exhaust stack: a dark bore inside a ring.
    d.ellipse((4, 4, 11, 11), fill=STEEL_DARK)
    d.ellipse((5, 5, 10, 10), fill=hexc("1b1d1f"))
    img.putpixel((6, 6), hexc("2c2f32"))
    for x, y in [(5, 4), (4, 5)]:
        img.putpixel((x, y), STEEL_LIGHT)
    rivets(img, CORNERS)
    return img


def bottom():
    img = steel_plate(3)
    rivets(img, CORNERS)
    return img


def front(glow=None, light=None):
    img = steel_plate(4)
    d = ImageDraw.Draw(img)
    # Pressure gauge, top left.
    d.ellipse((2, 1, 7, 6), fill=hexc("2a2a2a"))
    d.ellipse((3, 2, 6, 5), fill=hexc("e8e4d8"))
    img.putpixel((4, 3), hexc("cc2222"))
    img.putpixel((5, 3) if glow else (4, 4), hexc("cc2222"))
    # Status lamp, top right.
    lamp = light or hexc("2d3a2d")
    d.rectangle((11, 2, 13, 4), fill=hexc("222222"))
    img.putpixel((12, 3), lamp)
    # Firebox door with a grate.
    d.rectangle((2, 8, 13, 14), fill=hexc("2b2e31"))
    d.rectangle((3, 9, 12, 13), fill=hexc("121314"))
    if glow:
        for y in range(9, 14):
            for x in range(3, 13):
                img.putpixel((x, y), glow[(x * 7 + y * 3) % len(glow)] if y > 9 else glow[0])
    for x in (4, 6, 8, 10, 12):
        for y in range(9, 14):
            img.putpixel((x, y), hexc("3c4044"))
    for x in range(2, 14):
        img.putpixel((x, 8), STEEL_LIGHT)
    return img


def front_on():
    """Three flickering frames stacked vertically."""
    palettes = [
        [hexc("ffb12b"), hexc("ff7a1a"), hexc("ffd25c"), hexc("e0501a")],
        [hexc("ff8f1f"), hexc("ffc845"), hexc("e85d1c"), hexc("ffad33")],
        [hexc("ffd25c"), hexc("ff7a1a"), hexc("ffb12b"), hexc("f06a1d")],
    ]
    sheet = Image.new("RGBA", (16, 48))
    for i, pal in enumerate(palettes):
        sheet.paste(front(glow=pal, light=hexc("5cff6a")), (0, i * 16))
    return sheet


# ------------------------------------------------------------------ GUI
PANEL = hexc("c6c6c6")
WHITE = hexc("ffffff")
DARK = hexc("555555")
SLOT_DARK = hexc("373737")
SLOT_FILL = hexc("8b8b8b")


def bevel(d, x, y, w, h, top_left, bottom_right, fill):
    d.rectangle((x, y, x + w - 1, y + h - 1), fill=fill)
    d.line((x, y, x + w - 2, y), fill=top_left)
    d.line((x, y, x, y + h - 2), fill=top_left)
    d.line((x + 1, y + h - 1, x + w - 1, y + h - 1), fill=bottom_right)
    d.line((x + w - 1, y + 1, x + w - 1, y + h - 1), fill=bottom_right)


def slot(d, x, y):
    """x, y are the slot item position; the frame sits one pixel outside it."""
    bevel(d, x - 1, y - 1, 18, 18, SLOT_DARK, WHITE, SLOT_FILL)


def panel(img, d, W, H):
    """Vanilla container background: rounded black outline and a white/grey bevel."""
    d.rectangle((1, 1, W - 2, H - 2), fill=PANEL)
    d.line((2, 0, W - 4, 0), fill=hexc("000000"))
    d.line((0, 2, 0, H - 4), fill=hexc("000000"))
    d.line((3, H - 1, W - 3, H - 1), fill=hexc("000000"))
    d.line((W - 1, 3, W - 1, H - 3), fill=hexc("000000"))
    for p in [(1, 1), (W - 3, 1), (W - 2, 2), (1, H - 3), (2, H - 2), (W - 2, H - 2)]:
        img.putpixel(p, hexc("000000"))
    d.line((2, 1, W - 4, 1), fill=WHITE)
    d.line((1, 2, 1, H - 4), fill=WHITE)
    d.line((2, 2, 3, 2), fill=WHITE)
    d.line((3, H - 2, W - 3, H - 2), fill=DARK)
    d.line((W - 2, 3, W - 2, H - 3), fill=DARK)
    d.line((W - 3, H - 3, W - 4, H - 3), fill=DARK)


def gui():
    W, H = 176, 176
    img = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(img)
    # Panel with the vanilla rounded black outline and bevel.
    panel(img, d, W, H)

    # Fuel slot and the unlit flame above it (the lit sprite draws over this).
    slot(d, 17, 58)
    flame = [
        "......##......",
        ".....####.....",
        ".....####.....",
        "....######....",
        "....######....",
        "...########...",
        "...###..###...",
        "..###....###..",
        "..##......##..",
        ".###......###.",
        ".###......###.",
        ".####....####.",
        "..##########..",
        "....######....",
    ]
    for y, row in enumerate(flame):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((19 + x, 40 + y), hexc("8b8b8b"))

    # SCADA readout.
    bevel(d, 40, 16, 110, 64, SLOT_DARK, WHITE, hexc("0d1710"))
    for y in range(18, 79, 2):
        d.line((41, y, 148, y), fill=hexc("101d14"))

    # FE buffer gauge.
    bevel(d, 156, 16, 12, 64, SLOT_DARK, WHITE, hexc("2a1716"))
    for y in range(24, 79, 8):
        img.putpixel((157, y), hexc("5a3533"))

    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18, 94 + row * 18)
    for col in range(9):
        slot(d, 8 + col * 18, 152)
    return img


# ================================================================== shared helpers
CLEAR = (0, 0, 0, 0)


def blank(w=16, h=16):
    return Image.new("RGBA", (w, h), CLEAR)


def pixmap(img, rows, pal, ox=0, oy=0):
    """Stamp an ASCII pixel map; characters missing from the palette are left untouched."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((ox + x, oy + y), pal[ch])
    return img


def outline(img, color):
    """Vanilla item outline: every empty pixel touching the sprite (4-neighbourhood) gets `color`."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3]:
                    img.putpixel((x, y), color)
                    break
    return img


def jitter(img, seed, amount=0.05, mask=None):
    """Per-pixel brightness noise on every opaque pixel (or only those in `mask`)."""
    r = random.Random(seed)
    w, h = img.size
    for y in range(h):
        for x in range(w):
            c = img.getpixel((x, y))
            f = 1 + r.uniform(-amount, amount)
            if c[3] and (mask is None or (x, y) in mask):
                img.putpixel((x, y), shade(c, f))
    return img


def value_noise(w, h, seed, cell=4):
    """Smooth tileable noise in [0, 1] (bilinear over a coarse random grid)."""
    r = random.Random(seed)
    gw, gh = max(1, w // cell), max(1, h // cell)
    grid = [[r.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx) % gw, int(fy) % gh
            x1, y1 = (x0 + 1) % gw, (y0 + 1) % gh
            tx, ty = fx - int(fx), fy - int(fy)
            a = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            b = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def ramp_pick(ramp, t):
    t = max(0.0, min(0.999, t))
    return ramp[int(t * len(ramp))]


def edges(img, box, light, dark):
    """1px bevel on an inclusive box: light top/left, dark bottom/right."""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.line((x0, y0, x1, y0), fill=light)
    d.line((x0, y0, x0, y1), fill=light)
    d.line((x0, y1, x1, y1), fill=dark)
    d.line((x1, y0, x1, y1), fill=dark)


def hazard(img, x0, x1, y0, y1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            img.putpixel((x, y), hexc("e8b923") if (x + y) // 2 % 2 == 0 else hexc("222222"))


def dark_steel(seed, w=16, h=16):
    r = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    base = hexc("3b4146")
    for y in range(h):
        streak = r.uniform(-0.05, 0.05)
        for x in range(w):
            img.putpixel((x, y), shade(base, 1 + streak + r.uniform(-0.05, 0.05)))
    return img


# Metal ramps: 1 = highlight ... 5 = deep shade, o = outline.
def metal(h1, h2, h3, h4, h5, o):
    return {"1": hexc(h1), "2": hexc(h2), "3": hexc(h3), "4": hexc(h4), "5": hexc(h5), "o": hexc(o)}


IRON_P = metal("f4f4f4", "d6d6d6", "b0b0b0", "868686", "626262", "353535")
ZINC_P = metal("eef5f8", "c9d4db", "a7b4bc", "808e98", "5d6973", "2c343b")
RAW_ZINC_P = metal("cfd9df", "a9b6be", "8a98a2", "6a7781", "4d5860", "262c31")
BRASS_P = metal("f6e39a", "e0c065", "c9a13b", "a5822c", "8a6a22", "3f300d")
COPPER_P = metal("f3a988", "e07b57", "b4684d", "8f4f38", "6b3a2a", "34190f")
GOLD_P = metal("fffbbd", "fdf160", "f5cc27", "dca020", "b37718", "5a3708")
COAL_P = metal("6a6a6a", "4b4b4b", "373737", "282828", "1c1c1c", "0a0a0a")
TEAL_P = metal("9af7dc", "4ce6bb", "2bbf95", "1d8a6b", "135c48", "082e24")
CYAN = hexc("4fe6ff")


def sprite(rows, pal, outline_color=None):
    img = pixmap(blank(), rows, pal)
    return outline(img, outline_color or pal["o"])


# ================================================================== blocks: world
STONE_RAMP = [hexc("686868"), hexc("737373"), hexc("7d7d7d"), hexc("878787"), hexc("919191")]
DEEP_RAMP = [hexc("2f2f35"), hexc("3a3a40"), hexc("45454b"), hexc("515157"), hexc("5e5e63")]


def stone(seed):
    n = value_noise(16, 16, seed, 4)
    r = random.Random(seed + 1)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), ramp_pick(STONE_RAMP, n[y][x] * 0.8 + r.random() * 0.35 - 0.05))
    return img


def deepslate(seed):
    """Dark stone with horizontal banding, like vanilla deepslate."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    row_bias = [r.uniform(-0.12, 0.12) for _ in range(16)]
    for y in range(16):
        x = 0
        while x < 16:
            run = r.randint(2, 6)
            t = 0.5 + row_bias[y] + r.uniform(-0.25, 0.25)
            for k in range(run):
                img.putpixel(((x + k) % 16, y), ramp_pick(DEEP_RAMP, t + r.uniform(-0.06, 0.06)))
            x += run
    # Distinct dark seams every few rows give the layered look.
    for y in (3, 8, 12):
        for x in range(16):
            if r.random() < 0.7:
                img.putpixel((x, y), DEEP_RAMP[0])
    return img


ORE_MAP = [
    "................",
    "................",
    "..hm............",
    ".hmmd......hm...",
    "..mdd.....hmmd..",
    "...........mdd..",
    "................",
    ".....hm.........",
    "....hhmd........",
    "....mmdd....hm..",
    ".....dd....hmmd.",
    "............mdd.",
    "..hm............",
    ".hmmd....hm.....",
    "..dd....hmmd....",
    ".........dd.....",
]


def zinc_ore(deep=False):
    img = deepslate(31) if deep else stone(30)
    pal = {"h": hexc("e6f0f5"), "m": hexc("9fb4c3"), "d": hexc("4f5a63") if not deep else hexc("25292e")}
    return pixmap(img, ORE_MAP, pal)


def raw_metal_block(pal, seed):
    """Lumpy raw-ore storage block: overlapping shaded nodules."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), pal["4"])
    jitter(img, seed, 0.06)
    for _ in range(11):
        cx, cy, rad = r.uniform(0, 16), r.uniform(0, 16), r.uniform(2.2, 3.6)
        for y in range(-5, 6):
            for x in range(-5, 6):
                px, py = int(cx) + x, int(cy) + y
                dx, dy = px + 0.5 - cx, py + 0.5 - cy
                dist = (dx * dx + dy * dy) ** 0.5
                if dist > rad:
                    continue
                # Light from the top-left: shade by the normal's projection.
                lit = -(dx + dy) / (rad * 1.6)
                edge = dist > rad - 0.9
                if edge and dx + dy > 0:
                    c = pal["5"]
                elif lit > 0.5:
                    c = pal["1"]
                elif lit > 0.15:
                    c = pal["2"]
                elif lit > -0.3:
                    c = pal["3"]
                else:
                    c = pal["4"]
                img.putpixel((px % 16, py % 16), c)
    return img


def metal_block(pal, seed, base="2", sheen="1"):
    """Vanilla-style storage block: bevelled frame, inset panel and a diagonal sheen."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = 0.5 + r.uniform(-0.05, 0.05)
            img.putpixel((x, y), pal[base] if t > 0.47 else shade(pal[base], 0.95))
    # Diagonal sheen bands.
    for y in range(16):
        for x in range(16):
            k = x + y
            if k in (9, 10, 20):
                img.putpixel((x, y), pal[sheen])
            elif k in (8, 11, 19, 21):
                img.putpixel((x, y), pal[str(int(sheen) + 1)] if r.random() < 0.6 else pal[base])
    # Inset groove around an inner panel.
    edges(img, (2, 2, 13, 13), pal["4"], pal["1"])
    # Outer bevel.
    edges(img, (0, 0, 15, 15), pal["1"], pal["5"])
    edges(img, (1, 1, 14, 14), pal["2"], pal["4"])
    return img


# ================================================================== blocks: machines
def crusher_front(o=0):
    """Two toothed jaws over a dark throat; `o` slides the teeth 1px in opposite directions."""
    img = steel_plate(10)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 13, 12), fill=hexc("2b2e31"))
    d.rectangle((3, 3, 12, 11), fill=hexc("0f1011"))
    jaw, jaw_hi, jaw_lo = hexc("8d969e"), hexc("c3ccd3"), hexc("50575d")
    for x in range(3, 13):
        img.putpixel((x, 3), jaw_hi)
        img.putpixel((x, 4), jaw)
        img.putpixel((x, 10), jaw)
        img.putpixel((x, 11), jaw_lo)
    for x in range(3, 13):
        k = (x - 3 + o) % 3
        if k < 2:
            img.putpixel((x, 5), jaw if k == 0 else jaw_lo)
        if k == 0:
            img.putpixel((x, 6), jaw_lo)
        k = (x - 4 - o) % 3
        if k < 2:
            img.putpixel((x, 9), jaw_hi if k == 0 else jaw)
        if k == 1:
            img.putpixel((x, 8), jaw_hi)
    hazard(img, 1, 14, 13, 14)
    for x in range(2, 14):
        img.putpixel((x, 2), STEEL_LIGHT)
    return img


def crusher_front_on():
    sheet = Image.new("RGBA", (16, 32))
    sheet.paste(crusher_front(0), (0, 0))
    sheet.paste(crusher_front(1), (0, 16))
    return sheet


def crusher_side():
    img = steel_plate(11)
    d = ImageDraw.Draw(img)
    # Motor housing with cooling fins.
    d.rectangle((3, 2, 12, 12), fill=hexc("3a4045"))
    for y in range(3, 12):
        fin = (y % 2 == 1)
        for x in range(4, 12):
            img.putpixel((x, y), STEEL_LIGHT if fin else hexc("2a2e32"))
        img.putpixel((11, y), STEEL if fin else hexc("2a2e32"))
    for x in range(3, 13):
        img.putpixel((x, 2), hexc("5a6168"))
        img.putpixel((x, 12), hexc("24282b"))
    # Terminal box (copper lugs) under the housing.
    d.rectangle((5, 13, 10, 14), fill=hexc("2b2e31"))
    img.putpixel((6, 13), COPPER_P["2"])
    img.putpixel((9, 13), COPPER_P["2"])
    rivets(img, [(1, 1), (13, 1)])
    return img


def crusher_top():
    img = steel_plate(12)
    # Square hopper funnel: walls shaded by which side they face.
    for y in range(2, 14):
        for x in range(2, 14):
            ring = min(x - 2, y - 2, 13 - x, 13 - y)
            if ring >= 4:
                c = hexc("0d0e0f")
            else:
                side_ = min((x - 2, "l"), (y - 2, "t"), (13 - x, "r"), (13 - y, "b"))[1]
                base = {"t": 0.55, "l": 0.65, "r": 0.9, "b": 1.0}[side_]
                c = shade(STEEL, base * (1 - ring * 0.14))
            img.putpixel((x, y), c)
    edges(img, (2, 2, 13, 13), STEEL_DARK, STEEL_LIGHT)
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


BOLT = [
    "...##",
    "..##.",
    ".###.",
    "#####",
    ".###.",
    ".##..",
    "##...",
]


def charger_front(on=False):
    img = steel_plate(13)
    d = ImageDraw.Draw(img)
    # Charging cradle: a recessed bay with copper contacts on its floor.
    d.rectangle((2, 4, 13, 13), fill=hexc("2b2e31"))
    d.rectangle((3, 5, 12, 12), fill=hexc("17191b"))
    for x in range(3, 13):
        img.putpixel((x, 12), hexc("3a3f44"))
    for x in (4, 5, 10, 11):
        img.putpixel((x, 11), COPPER_P["2"])
        img.putpixel((x, 12), COPPER_P["4"])
    for x in range(2, 14):
        img.putpixel((x, 13), STEEL_LIGHT)
    if on:
        glow = hexc("3a3410")
        for y in range(5, 12):
            for x in range(3, 13):
                if img.getpixel((x, y)) == hexc("17191b"):
                    img.putpixel((x, y), glow if (x + y) % 2 else hexc("2a2610"))
        pixmap(img, BOLT, {"#": hexc("ffe14d")}, 6, 4 + 1)
        for x, y in [(8, 6), (8, 7), (7, 8), (8, 8), (7, 9)]:
            img.putpixel((x, y), hexc("fffbd0"))
    # Indicator lamp and a label plate.
    d.rectangle((11, 1, 13, 3), fill=hexc("222222"))
    img.putpixel((12, 2), hexc("ff3b30") if on else hexc("4a1d1b"))
    if on:
        img.putpixel((12, 1), hexc("ff8a80"))
    d.rectangle((2, 1, 8, 2), fill=hexc("c9a13b"))
    d.line((2, 2, 8, 2), fill=hexc("8a6a22"))
    return img


def charger_side():
    img = steel_plate(14)
    d = ImageDraw.Draw(img)
    # Vent slots.
    for y in (3, 5, 7):
        d.line((3, y, 12, y), fill=hexc("202326"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    # Copper bus bar running across.
    for x in range(1, 15):
        img.putpixel((x, 11), COPPER_P["1"])
        img.putpixel((x, 12), COPPER_P["3"])
        img.putpixel((x, 13), COPPER_P["5"])
    for x in (3, 12):
        img.putpixel((x, 12), RIVET)
    return img


def charger_top():
    img = steel_plate(15)
    # Copper contact plate with insulating border and contact grooves.
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 13, 13), fill=hexc("2b2e31"))
    for y in range(3, 13):
        for x in range(3, 13):
            img.putpixel((x, y), COPPER_P["2"] if (x + y) % 5 else COPPER_P["1"])
    edges(img, (3, 3, 12, 12), COPPER_P["1"], COPPER_P["4"])
    for x in range(5, 11):
        img.putpixel((x, 6), COPPER_P["4"])
        img.putpixel((x, 9), COPPER_P["4"])
    for x, y in [(5, 5), (10, 5), (5, 10), (10, 10)]:
        img.putpixel((x, y), COPPER_P["5"])
    jitter(img, 16, 0.04, mask={(x, y) for x in range(4, 12) for y in range(4, 12)})
    rivets(img, [(0, 0), (14, 0), (0, 14)])
    return img








def capacitor_bank_parts():
    """
    Pixel-exact parts sheet for the 3D capacitor bank models (gen_data.py maps each face's uv here):
      can side off  x0-4  y0-9    can side lit   x4-8  y0-9    can lid    x8-12 y0-4
      bushing side  x12-14 y0-3   bushing top    x14-16 y0-2   controller x8-16 y4-10
      bus bar       x0-16 y10-12  steel rail     x0-16 y12-16
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # Can sleeves: lit from the left, a white rating label, a dark base band.
    for ox, lit in ((0, False), (4, True)):
        ramp = [TEAL_P["2"], TEAL_P["3"], TEAL_P["4"], TEAL_P["5"]] if not lit else [TEAL_P["1"], TEAL_P["1"], TEAL_P["2"], TEAL_P["3"]]
        if not lit:
            ramp = [shade(c, 0.78) for c in ramp]
        for x in range(4):
            for y in range(9):
                img.putpixel((ox + x, y), ramp[x])
            img.putpixel((ox + x, 0), shade(ramp[x], 1.15))
            img.putpixel((ox + x, 3), hexc("e8efe9") if x < 3 else hexc("b9c2bd"))
            img.putpixel((ox + x, 8), shade(ramp[x], 0.55))
        if lit:
            img.putpixel((ox + 1, 6), hexc("5cff6a"))
    # Aluminium lid with a centre terminal boss.
    lid = [[hexc("eef2f4"), hexc("d4dbe0"), hexc("c3cbd1"), hexc("a8b1b8")],
           [hexc("d4dbe0"), hexc("8a939a"), hexc("8a939a"), hexc("9aa3aa")],
           [hexc("c3cbd1"), hexc("8a939a"), hexc("6c757c"), hexc("8a939a")],
           [hexc("a8b1b8"), hexc("9aa3aa"), hexc("8a939a"), hexc("7c858c")]]
    for y in range(4):
        for x in range(4):
            img.putpixel((8 + x, y), lid[y][x])
    # Porcelain bushing: white sheds with grey grooves; copper terminal on top.
    for y, row in enumerate([("fbfbf6", "d9d9d0"), ("a9a99f", "8c8c83"), ("f2f2ea", "cfcfc6")]):
        for x, c in enumerate(row):
            img.putpixel((12 + x, y), hexc(c))
    for y in range(2):
        for x in range(2):
            img.putpixel((14 + x, y), COPPER_P["2"] if (x + y) % 2 == 0 else COPPER_P["3"])
    # Controller face: dark display, amber PF needle, six step LEDs.
    for y in range(4, 10):
        for x in range(8, 16):
            img.putpixel((x, y), STEEL_DARK if y in (4, 9) or x in (8, 15) else hexc("0b0f0d"))
    for x in range(9, 13):
        img.putpixel((x, 7), hexc("3a2a10"))
    img.putpixel((11, 6), AMBER)
    img.putpixel((11, 5), AMBER)
    for i, x in enumerate((9, 10, 11, 12, 13, 14)):
        img.putpixel((x, 8), hexc("5cff6a") if i < 2 else hexc("1f3a24"))
    img.putpixel((14, 5), hexc("ff4040"))
    # Copper bus bar: side row and top row.
    for x in range(16):
        img.putpixel((x, 10), COPPER_P["3"] if x % 4 else COPPER_P["4"])
        img.putpixel((x, 11), COPPER_P["2"])
    # Steel rail strip for posts and rails.
    for y in range(12, 16):
        for x in range(16):
            img.putpixel((x, y), [STEEL_LIGHT, hexc("7d858c"), hexc("6f767d"), STEEL_DARK][y - 12])
    return img


SCADA_GREEN = hexc("55ff55")
SCADA_DIM = hexc("1f7a2a")
AMBER = hexc("ffb000")


def scada_front(frame=0):
    img = steel_plate(19)
    d = ImageDraw.Draw(img)
    # Bezel and phosphor screen with scanlines.
    d.rectangle((1, 1, 14, 11), fill=hexc("26292c"))
    d.rectangle((2, 2, 13, 10), fill=hexc("0c2412"))
    for y in range(2, 11, 2):
        d.line((2, y, 13, y), fill=hexc("0a1e0f"))
    # One-line diagram: incoming bus, three feeders with breakers, loads.
    g, a = SCADA_GREEN, AMBER
    for x in range(3, 13):
        img.putpixel((x, 3), g)
    for x in (4, 8, 11):
        img.putpixel((x, 4), g)
        img.putpixel((x, 5), a if x == 8 else g)
        img.putpixel((x, 6), SCADA_DIM if x == 8 else g)
    img.putpixel((8, 6), SCADA_DIM)
    # Bar graph along the bottom of the screen.
    for x, hgt in zip(range(3, 13), [1, 2, 1, 2, 2, 1, 0, 2, 1, 2]):
        img.putpixel((x, 9), SCADA_DIM if hgt == 0 else (a if x == 12 else g))
        if hgt == 2:
            img.putpixel((x, 8), a if x == 12 else g)
    # Cursor / alarm blink on frame 1.
    if frame:
        img.putpixel((12, 5), g)
        img.putpixel((12, 6), g)
    for x in range(1, 15):
        img.putpixel((x, 11), STEEL_LIGHT)
    # Buttons and a speaker grille below the screen.
    for x, c in [(2, hexc("d03a2f")), (4, hexc("3fbf4a")), (6, hexc("e0a020"))]:
        img.putpixel((x, 13), c)
        img.putpixel((x, 14), shade(c, 0.55))
    lamp = hexc("ff3b30") if frame else hexc("4a1d1b")
    d.rectangle((8, 12, 10, 14), fill=hexc("222222"))
    img.putpixel((9, 13), lamp)
    for x in (12, 13):
        for y in (12, 14):
            img.putpixel((x, y), hexc("2b2e31"))
    return img


def scada_front_on():
    sheet = Image.new("RGBA", (16, 32))
    sheet.paste(scada_front(0), (0, 0))
    sheet.paste(scada_front(1), (0, 16))
    return sheet


def scada_side():
    img = steel_plate(20)
    d = ImageDraw.Draw(img)
    # Cabinet: a door seam, louvred vents, and a hinge.
    for y in range(3, 13, 2):
        d.line((4, y, 11, y), fill=hexc("202326"))
        d.line((5, y + 1, 11, y + 1), fill=STEEL_LIGHT)
    for y in range(1, 15):
        img.putpixel((2, y), STEEL_DARK)
    for y in (3, 4, 11, 12):
        img.putpixel((1, y), RIVET)
    rivets(img, [(12, 13)])
    return img


def copper_wire():
    """Twisted copper strands; any 4px slice reads as a cable."""
    r = random.Random(21)
    img = Image.new("RGBA", (16, 16))
    tones = [COPPER_P["4"], COPPER_P["3"], COPPER_P["2"], COPPER_P["1"]]
    for y in range(16):
        for x in range(16):
            t = (x - y) % 4
            c = tones[t]
            if y % 4 == 3:
                c = shade(c, 0.8)
            img.putpixel((x, y), shade(c, 1 + r.uniform(-0.04, 0.04)))
    return img


def fiber_optic_cable():
    r = random.Random(22)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        base = hexc("23262a") if y % 4 in (1, 2) else hexc("17191c")
        for x in range(16):
            img.putpixel((x, y), shade(base, 1 + r.uniform(-0.08, 0.08)))
    for x in range(16):
        img.putpixel((x, 5), hexc("2d3136"))
        img.putpixel((x, 13), hexc("2d3136"))
    for x, y in [(2, 2), (9, 7), (13, 1), (6, 11), (12, 10), (4, 7), (14, 14)]:
        img.putpixel((x, y), CYAN)
        for dx, dy in ((1, 0), (-1, 0)):
            img.putpixel(((x + dx) % 16, y), hexc("1d5a66"))
    return img


def turbine_back_inner():
    """Recessed back wall behind the rotor: bearing and faint stator vanes."""
    import math
    img = dark_steel(23)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            rr = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            c = img.getpixel((x, y))
            if 4.2 < rr < 7.6:
                # 8 radial vanes, slightly swept.
                if (ang * 8 / (2 * math.pi) + rr * 0.08) % 1 < 0.22:
                    c = shade(c, 1.25)
            if rr <= 1.2:
                c = hexc("1a1c1e")
            elif rr <= 2.4:
                c = hexc("8a939a") if dx + dy < 0 else hexc("5a6168")
            elif rr <= 3.4:
                c = hexc("2a2e32")
            elif rr <= 4.2:
                c = hexc("666e75") if dx + dy < 0 else hexc("41474c")
            img.putpixel((x, y), c)
    for i in range(16):
        img.putpixel((i, 0), hexc("1f2225"))
        img.putpixel((0, i), hexc("1f2225"))
        img.putpixel((i, 15), hexc("50575d"))
        img.putpixel((15, i), hexc("50575d"))
    return img


def turbine_frame():
    img = steel_plate(24)
    rivets(img, CORNERS)
    return img


OAK = [hexc("7e6237"), hexc("9c7f4e"), hexc("af8f55"), hexc("b8945f"), hexc("c29d62")]


def planks(w, h, seed, plank=4):
    """Horizontal oak planks with staggered end seams and grain streaks."""
    r = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    rows = (h + plank - 1) // plank
    for p in range(rows):
        seam = r.randrange(w)
        tone = r.uniform(-0.04, 0.04)
        for yy in range(plank):
            y = p * plank + yy
            if y >= h:
                break
            grain = r.uniform(-0.05, 0.05)
            for x in range(w):
                if yy == plank - 1:
                    c = OAK[0]
                elif x == seam:
                    c = OAK[1]
                else:
                    c = OAK[3] if r.random() > 0.25 else OAK[2]
                    if yy == 0:
                        c = OAK[4] if r.random() > 0.3 else OAK[3]
                    c = shade(c, 1 + tone + grain)
                img.putpixel((x, y), c)
    return img


COBBLE = [hexc("4f4f4f"), hexc("6b6b6b"), hexc("7a7a7a"), hexc("8a8a8a"), hexc("9b9b9b")]


def cobble(w, h, seed, count=None):
    """Voronoi cobbles: each stone gets its own shade, mortar where cells meet."""
    r = random.Random(seed)
    count = count or max(3, w * h // 22)
    pts = [(r.uniform(0, w), r.uniform(0, h), r.choice([1, 2, 2, 3, 3, 4])) for _ in range(count)]

    def cell(x, y):
        best, bi = 1e9, 0
        for i, (px, py, _) in enumerate(pts):
            ddx = min(abs(x - px), w - abs(x - px))
            ddy = min(abs(y - py), h - abs(y - py))
            dd = ddx * ddx + ddy * ddy
            if dd < best:
                best, bi = dd, i
        return bi

    ids = [[cell(x + 0.5, y + 0.5) for x in range(w)] for y in range(h)]
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            i = ids[y][x]
            right = ids[y][(x + 1) % w]
            down = ids[(y + 1) % h][x]
            up = ids[(y - 1) % h][x]
            left = ids[y][(x - 1) % w]
            tone = pts[i][2]
            if right != i or down != i:
                c = COBBLE[0]
            elif up != i or left != i:
                c = COBBLE[min(4, tone + 1)]
            else:
                c = shade(COBBLE[tone], 1 + r.uniform(-0.05, 0.05))
            img.putpixel((x, y), c)
    return img


def framed(img, dark, darker, width=2):
    w, h = img.size
    for y in range(h):
        for x in range(w):
            k = min(x, y, w - 1 - x, h - 1 - y)
            if k == 0:
                img.putpixel((x, y), darker)
            elif k < width:
                img.putpixel((x, y), shade(dark, 1 + ((x * 7 + y * 3) % 5 - 2) * 0.03))
    return img


def redstone_engine_base():
    img = planks(16, 16, 25)
    framed(img, hexc("6b5230"), hexc("3f2f19"))
    for x, y in CORNERS:
        img.putpixel((x - 1, y - 1), hexc("9aa3ab"))
    return img


def stirling_engine_base():
    img = cobble(16, 16, 26)
    framed(img, hexc("4a4a4a"), hexc("2a2a2a"))
    for x, y in CORNERS:
        img.putpixel((x - 1, y - 1), hexc("9aa3ab"))
    return img


# ================================================================== entity (box-UV) textures
def box_uv(img, u, v, w, h, d, paint):
    """Paint each face of a standard Minecraft cuboid unwrap. paint(face, fw, fh) -> Image."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }
    for name, (x, y, fw, fh) in faces.items():
        if fw and fh:
            img.paste(paint(name, fw, fh), (x, y))


def face_edges(img, c):
    w, h = img.size
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), outline=c)
    return img


LEATHER = [hexc("4a2a14"), hexc("6b3d1f"), hexc("8b5a2b"), hexc("9c6a36")]


def leather(fw, fh, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (fw, fh))
    for y in range(fh):
        for x in range(fw):
            img.putpixel((x, y), shade(LEATHER[2] if r.random() > 0.3 else LEATHER[3], 1 + r.uniform(-0.05, 0.05)))
    if fw >= 6 and fh >= 6:
        # Concentric fold creases seen from above.
        for y in range(fh):
            for x in range(fw):
                k = min(x, y, fw - 1 - x, fh - 1 - y)
                if k % 3 == 2:
                    img.putpixel((x, y), LEATHER[1])
    return face_edges(img, LEATHER[0])


def engine_sheet(plate_fn):
    img = Image.new("RGBA", (64, 64), CLEAR)

    def plate(face, fw, fh):
        t = plate_fn(fw, fh, {"top": 1, "bottom": 2, "north": 3, "south": 4, "east": 5, "west": 6}[face])
        if fh >= 8:
            framed(t, shade(t.getpixel((fw // 2, fh // 2)), 0.62), shade(t.getpixel((fw // 2, fh // 2)), 0.4))
        else:
            face_edges(t, shade(t.getpixel((fw // 2, fh // 2)), 0.45))
        return t

    box_uv(img, 0, 0, 16, 4, 16, plate)
    box_uv(img, 0, 20, 14, 1, 14, lambda f, fw, fh: leather(fw, fh, 40 + fw + fh))
    return img


def engine_trunk():
    img = Image.new("RGBA", (64, 64), CLEAR)
    rib, light, hi = hexc("9a9a9a"), hexc("d8d8d8"), hexc("f4f4f4")

    def paint(face, fw, fh):
        r = random.Random(50 + fw * 3 + fh)
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                t.putpixel((x, y), shade(light, 1 + r.uniform(-0.03, 0.05)))
        if face in ("top", "bottom"):
            ImageDraw.Draw(t).rectangle((2, 2, fw - 3, fh - 3), outline=hexc("b4b4b4"))
            t.putpixel((fw // 2, fh // 2), rib)
        else:
            for y in (2, 5, 8):
                for x in range(fw):
                    t.putpixel((x, y), rib)
                    t.putpixel((x, y + 1), hi)
            for y in range(fh):
                t.putpixel((1, y), hi)
        return face_edges(t, rib)

    box_uv(img, 0, 40, 8, 12, 8, paint)
    return img


def turbine_rotor():
    img = Image.new("RGBA", (32, 32), CLEAR)

    def hub(face, fw, fh):
        t = dark_steel(60 + fw + fh, fw, fh)
        if face in ("north", "south"):
            pixmap(t, ["oooo", "o12o", "o23o", "oooo"], {"o": BRASS_P["5"], "1": BRASS_P["1"], "2": BRASS_P["2"], "3": BRASS_P["4"]})
            return t
        return face_edges(t, hexc("24282b"))

    def blade(face, fw, fh):
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                c = hexc("e4eaee") if x == 0 else hexc("b9c2c9")
                t.putpixel((x, y), c)
        if fh >= 4:
            for x in range(fw):
                t.putpixel((x, fh - 1), BRASS_P["3"])
            t.putpixel((0, 0), hexc("ffffff"))
        return t

    box_uv(img, 0, 0, 4, 4, 3, hub)
    box_uv(img, 0, 8, 2, 4, 1, blade)
    return img


# ================================================================== items
# Vanilla item style (see analysis of the vanilla ingot/nugget/raw/dust sprites):
#  * every ingot shares ONE silhouette and shading layout; metals differ only by a palette swap,
#    and likewise every dust pile (redstone, glowstone, gunpowder, sugar) shares one layout;
#  * there is no separate black outline: the darkest ramp colour is the bottom/right rim and a
#    mid-dark ramp colour is the top/left rim, so the outline is tinted with the metal;
#  * light comes from the top-left; highlights are scattered single pixels / short streaks.
# The maps below are role grids (0 = darkest ... N = brightest role); `recolor` stamps a ramp
# onto them, index-for-index, the same way vanilla's own items are palette swaps.

# 8 roles: 0 bottom/right rim, 1 rim accent, 2 top/left rim, 3-4 front face, 5 bevel, 6 top face, 7 glint.
INGOT_MAP = [
    "................",
    "................",
    "..........22....",
    ".......222553...",
    "....2225666653..",
    ".22256666666653.",
    "2766666666667763",
    "2576666667776350",
    "2557667776433450",
    "2555776433335550",
    "245564333355400.",
    ".245643344000...",
    "..24541000......",
    "...2200.........",
    "................",
    "................",
]

# Two nugget shapes (vanilla gives every nugget its own small lump). 7 roles:
# 0 bottom/right rim, 1 top/left rim, 2-3 shade, 4-5 body, 6 glint.
NUGGET_TALL = [
    "................",
    "................",
    "................",
    "................",
    "......111.......",
    ".....14540......",
    ".....155560.....",
    ".....155630.....",
    ".....166320.....",
    "......14220.....",
    "......1420......",
    ".......10.......",
    "................",
    "................",
    "................",
    "................",
]
NUGGET_LUMP = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "........11......",
    ".......1441.....",
    "......14451.....",
    ".....156541.....",
    "....1432230.....",
    "....032200......",
    ".....000........",
    "................",
    "................",
    "................",
    "................",
]

# Raw ore chunk, 8 roles: 0 rim, 1 dark crevices, 2-3 rock shade, 4-5 body, 6-7 lit facets.
RAW_MAP = [
    "................",
    "..11111.........",
    ".146654111......",
    ".167676564111...",
    "15777676766541..",
    "154577665654541.",
    "053354332234341.",
    "042343332112331.",
    "042243321111120.",
    "032221111675330.",
    "0332211155564320",
    ".032220035542110",
    "..0000..03332110",
    ".........033110.",
    "..........0000..",
    "................",
]

# Dust pile, 7 roles: 0 bottom rim, 1 dark grains, 2 shadow grains, 3 top/left rim,
# 4 mid grains, 5 body, 6 bright grains.
DUST_MAP = [
    "................",
    "................",
    "................",
    ".......33.......",
    "......3453......",
    ".....356520.....",
    "....35654620....",
    "...3555652320...",
    "..346542642510..",
    "..354565422320..",
    "..025654252210..",
    "...0254232210...",
    "....00122100....",
    "......0000......",
    "................",
    "................",
]


def recolor(rows, ramp):
    """Stamp a role grid with a ramp (hex strings, index = role digit)."""
    cols = [hexc(h) for h in ramp]
    img = blank()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch.isdigit():
                img.putpixel((x, y), cols[int(ch)])
    return img


# Ingot ramps (8 roles, INGOT_MAP order).
ZINC_INGOT = ["283035", "414c53", "47535b", "5a6871", "687781", "8798a3", "b1c0c8", "d8e3e8"]
BRASS_INGOT = ["4a3a10", "6f581b", "7a621f", "977c27", "ab9030", "c0a83f", "d4c158", "ece08f"]
# Nugget ramps (7 roles), drawn from the ingot ramps as vanilla does.
ZINC_NUGGET = ["283035", "47535b", "687781", "8798a3", "9dadb7", "b1c0c8", "d8e3e8"]
BRASS_NUGGET = ["4a3a10", "7a621f", "977c27", "ab9030", "c0a83f", "d4c158", "ece08f"]
# Raw ore ramp (8 roles, RAW_MAP order).
RAW_ZINC = ["262c31", "3c464d", "5b6870", "7a8892", "98a6af", "b2bfc6", "cad5db", "e2eaee"]
# Dust ramps (7 roles, DUST_MAP order).
DUSTS = {
    "iron":   ["45403a", "6b655d", "8e877d", "7a746b", "b5aea4", "d3cdc4", "efebe5"],
    "gold":   ["752802", "a45a0e", "d18b12", "b26411", "e9b115", "fad64a", "fdf55f"],
    "copper": ["6d3421", "8a4129", "9c4529", "9c4e31", "c15a36", "e77c56", "fc9982"],
    "zinc":   ["2e363c", "57636b", "76848d", "626f78", "99a7b0", "b6c3ca", "d9e3e8"],
    "brass":  ["4a3a10", "6f581b", "937a2a", "7d6522", "b29a3a", "c9b450", "e2d685"],
    "coal":   ["0c0c0d", "151516", "1d1d1f", "262628", "2c2c2e", "343436", "58585a"],
}


def gear(pal, seed, teeth=8):
    import math
    img = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            rr = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = (ang * teeth / (2 * math.pi) + 0.25) % 1 < 0.5
            outer = 6.6 if tooth else 5.0
            if rr > outer or rr < 1.6:
                continue
            lit = -(dx + dy) / max(rr, 0.01)
            if rr < 2.6:
                c = pal["4"] if lit > 0 else pal["2"]  # hole bore: inner wall lit opposite
            elif rr > 4.3:
                c = pal["2"] if lit > 0.3 else pal["3"] if lit > -0.4 else pal["4"]
            else:
                c = pal["3"] if lit > -0.2 else pal["4"]
                if 3.0 < rr < 3.8:
                    c = pal["1"] if lit > 0.5 else c
            img.putpixel((x, y), c)
    return outline(img, pal["o"])


def copper_coil():
    img = blank()
    iron = {"a": IRON_P["2"], "b": IRON_P["3"], "c": IRON_P["4"], "d": IRON_P["5"]}
    # Flanged spool, windings between.
    for y in range(3, 13):
        img.putpixel((2, y), IRON_P["2"])
        img.putpixel((3, y), IRON_P["4"])
        img.putpixel((12, y), IRON_P["2"])
        img.putpixel((13, y), IRON_P["4"])
    for x in (2, 3, 12, 13):
        img.putpixel((x, 2), IRON_P["1"] if x in (2, 12) else IRON_P["3"])
        img.putpixel((x, 13), IRON_P["5"])
    cyl = [COPPER_P["3"], COPPER_P["2"], COPPER_P["1"], COPPER_P["2"], COPPER_P["2"], COPPER_P["3"], COPPER_P["3"], COPPER_P["4"], COPPER_P["5"]]
    for y in range(4, 13):
        for x in range(4, 12):
            c = cyl[y - 4]
            if (x + (y // 1) * 0) % 2 == 1:
                c = shade(c, 0.82)
            img.putpixel((x, y), c)
    # Axle stubs.
    for y in (7, 8):
        img.putpixel((1, y), IRON_P["3"])
        img.putpixel((14, y), IRON_P["4"])
    return outline(img, hexc("2a1a12"))


def electric_motor():
    img = blank()
    body = [IRON_P["2"], IRON_P["1"], IRON_P["1"], IRON_P["2"], IRON_P["2"], IRON_P["3"], IRON_P["3"], IRON_P["4"], IRON_P["5"]]
    for i, c in enumerate(body):
        y = 3 + i
        for x in range(3, 12):
            img.putpixel((x, y), shade(c, 0.88) if x % 2 == 0 else c)
        # Domed end bell on the left, flat end plate on the right.
        if 0 < i < len(body) - 1:
            img.putpixel((2, y), shade(c, 0.8))
        img.putpixel((12, y), shade(c, 0.7))
    # Window onto the copper windings.
    for y in range(5, 10):
        for x in range(5, 10):
            img.putpixel((x, y), COPPER_P["2"] if (x + y) % 2 else COPPER_P["4"])
        img.putpixel((4, y), IRON_P["5"])
        img.putpixel((10, y), IRON_P["2"])
    for x in range(4, 11):
        img.putpixel((x, 4), IRON_P["5"])
        img.putpixel((x, 10), IRON_P["2"])
    # Output shaft and mounting feet.
    for x in (13, 14):
        img.putpixel((x, 7), IRON_P["1"])
        img.putpixel((x, 8), IRON_P["3"])
    for x in (3, 4, 9, 10):
        img.putpixel((x, 12), IRON_P["4"])
    for x in (2, 3, 4, 9, 10, 11):
        img.putpixel((x, 13), IRON_P["5"])
    return outline(img, hexc("262a2e"))


def turbine_blade():
    """Cambered airfoil blade on a fir-tree root, rasterised along a diagonal axis."""
    import math
    img = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            u = (dx - dy) / math.sqrt(2)  # along the blade, toward the tip (upper-right)
            v = (dx + dy) / math.sqrt(2)  # across it
            c = None
            if -5.0 <= u < 9.6:
                t = (u + 5.0) / 14.6
                half = 0.7 + 2.0 * math.sin(math.pi * min(1.0, t * 1.15)) ** 0.8
                mid = -0.9 * math.sin(math.pi * t)  # camber bows toward the upper-left
                rel = (v - mid) / half
                if abs(rel) <= 1.0:
                    if rel < -0.55:
                        c = IRON_P["1"]
                    elif rel < 0.0:
                        c = IRON_P["2"]
                    elif rel < 0.55:
                        c = IRON_P["3"]
                    else:
                        c = IRON_P["4"]
            elif -7.6 <= u < -5.0 and abs(v) <= 1.9:
                # Root block with serrations.
                c = BRASS_P["2"] if v < -0.6 else BRASS_P["3"] if v < 0.6 else BRASS_P["5"]
                if u < -6.3 and abs(v) > 1.1:
                    c = None
            if c:
                img.putpixel((x, y), c)
    return outline(img, hexc("2c3136"))


CAPACITOR = [
    "................",
    "................",
    "......ssss......",
    ".....sSSSSs.....",
    ".....tsxsst.....",
    ".....1223mm.....",
    ".....1w23m4.....",
    ".....1223m4.....",
    ".....1w23m4.....",
    ".....1223m4.....",
    ".....1w23m4.....",
    ".....qqqqqq.....",
    "......l..l......",
    "......l..l......",
    "......l...l.....",
    "................",
]


def capacitor():
    pal = {
        "1": TEAL_P["1"], "2": TEAL_P["2"], "3": TEAL_P["3"], "4": TEAL_P["5"], "m": TEAL_P["4"],
        "s": hexc("cfd6db"), "S": hexc("eef2f4"), "x": hexc("8a939a"), "t": hexc("a8b1b8"),
        "w": hexc("e8fff7"), "q": hexc("2a2e32"), "l": hexc("b0b0b0"),
    }
    img = pixmap(blank(), CAPACITOR, pal)
    return outline(img, TEAL_P["o"])


def optical_fiber():
    img = blank()
    d = ImageDraw.Draw(img)
    # Three loops of fibre, then a tail running up to a lit tip.
    d.ellipse((1, 5, 11, 14), outline=hexc("1d8fa3"))
    d.ellipse((2, 6, 10, 13), outline=hexc("8af1ff"))
    d.ellipse((3, 7, 9, 12), outline=hexc("2ab8d0"))
    for x, y in [(10, 6), (11, 5), (12, 4)]:
        img.putpixel((x, y), hexc("4fe6ff"))
    img.putpixel((13, 3), hexc("ffffff"))
    outline(img, hexc("0b2a33"))
    for x, y in [(14, 2), (14, 3), (13, 2), (12, 2), (14, 4)]:
        img.putpixel((x, y), hexc("a6f5ff", 170))
    return img


CIRCUIT = [
    "................",
    "................",
    "................",
    ".gggggggggggggg.",
    ".gtttgggggggaAg.",
    ".gggtgggggggggg.",
    ".gggtgKKKKKgttg.",
    ".gpgtgKkkkKgtgg.",
    ".gggtgKkkkKgtgg.",
    ".gtttgKKKKKgtgg.",
    ".gggggggggggtgg.",
    ".gptttttttttt.g.",
    ".gggggggggggggg.",
    ".hhhhhhhhhhhhhh.",
    "................",
    "................",
]


def control_circuit():
    pal = {
        "g": hexc("2f8a3a"), "h": hexc("1d5a25"), "t": hexc("d9a441"), "p": hexc("f0d27a"),
        "K": hexc("1a1a1a"), "k": hexc("333333"), "a": hexc("ffb000"), "A": hexc("ffe08a"),
        ".": None,
    }
    pal.pop(".")
    img = pixmap(blank(), CIRCUIT, pal)
    img.putpixel((13, 11), pal["g"])
    # Chip legs.
    for x in (7, 9):
        img.putpixel((x, 5), hexc("b0b0b0"))
        img.putpixel((x, 10), hexc("b0b0b0"))
    jitter(img, 27, 0.04, mask={(x, y) for x in range(1, 15) for y in range(3, 13)
                                 if img.getpixel((x, y)) == pal["g"]})
    return outline(img, hexc("0f2e14"))


DRILL_HEAD = [
    "................",
    ".......1........",
    ".......12.......",
    "......1D3.......",
    "......D234......",
    ".....12D34......",
    ".....1D2D4......",
    "....12D23D4.....",
    "....1D23D34.....",
    "...12D23D234....",
    "...1D23D23D4....",
    "...D23D23D34....",
    "....bbbbbbb.....",
    "....BBBBBBB.....",
    ".....cccccc.....",
    "................",
]


def drill_head():
    pal = dict(IRON_P)
    pal.update({"D": IRON_P["5"], "b": BRASS_P["2"], "B": BRASS_P["4"], "c": IRON_P["4"]})
    pal.pop("o")
    img = pixmap(blank(), DRILL_HEAD, pal)
    return outline(img, hexc("2c3136"))


def electric_drill():
    """Pistol-grip drill, bit pointing up-right like vanilla tools."""
    import math
    img = blank()
    red = [hexc("f06a55"), hexc("e04a3a"), hexc("b8302a"), hexc("7a1d18")]
    grip = [hexc("4a4a4a"), hexc("333333"), hexc("222222")]
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8.3) * 1.18, (y + 0.5 - 7.4) * 1.18
            u = (dx - dy) / math.sqrt(2)  # toward upper-right
            v = (dx + dy) / math.sqrt(2)  # toward lower-right
            c = None
            if 3.2 < u < 9.4 and abs(v + 1.4) < 0.75:
                c = hexc("f4f4f4") if int(u * 1.42) % 2 else hexc("8f979e")
            elif 1.0 < u <= 3.2 and abs(v + 1.4) < 1.5:
                c = hexc("b9c2c9") if v < -1.4 else hexc("6f767d")
            elif -6.2 < u <= 1.0 and -4.0 < v < 1.0 and not (u < -5.0 and abs(v + 1.5) > 1.6):
                t = (v + 4.0) / 5.0
                c = red[0] if t < 0.22 else red[1] if t < 0.55 else red[2] if t < 0.85 else red[3]
            elif -4.6 < u < -1.4 and 1.0 <= v < 4.4:
                c = grip[0] if u > -2.4 else grip[1] if u > -3.8 else grip[2]
            elif -6.0 < u < 0.0 and 4.4 <= v < 7.4:
                c = hexc("33e0b0") if v < 5.2 else hexc("5a6066") if u > -3.0 else hexc("40454b")
            if c:
                img.putpixel((x, y), c)
    return outline(img, hexc("1c1c1c"))


# ================================================================== GUIs
def inventory(d, y0=84):
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18, y0 + row * 18)
    for col in range(9):
        slot(d, 8 + col * 18, y0 + 58)


def fe_gauge(img, d, x=156, y=16, w=12, h=54):
    bevel(d, x, y, w, h, SLOT_DARK, WHITE, hexc("2a1716"))
    for ty in range(y + 8, y + h - 1, 8):
        img.putpixel((x + 1, ty), hexc("5a3533"))


ARROW = [
    "..............##........",
    "..............###.......",
    "..............####......",
    "..............#####.....",
    "..............######....",
    "#######################.",
    "########################",
    "########################",
    "########################",
    "#######################.",
    "..............######....",
    "..............#####.....",
    "..............####......",
    "..............###.......",
    "..............##........",
    "........................",
]

FLAME = [
    "......##......",
    ".....####.....",
    ".....####.....",
    "....######....",
    "....######....",
    "...########...",
    "...###..###...",
    "..###....###..",
    "..##......##..",
    ".###......###.",
    ".###......###.",
    ".####....####.",
    "..##########..",
    "....######....",
]


def machine_gui():
    img = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(img)
    panel(img, d, 176, 166)
    inventory(d)
    fe_gauge(img, d)
    return img, d


def gui_crusher():
    img, d = machine_gui()
    slot(d, 44, 35)
    slot(d, 104, 35)
    slot(d, 130, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 70, 34)
    return img


def gui_charger():
    img, d = machine_gui()
    slot(d, 80, 35)
    bevel(d, 62, 56, 52, 6, SLOT_DARK, WHITE, hexc("2a1716"))
    return img


def gui_engine():
    img, d = machine_gui()
    slot(d, 44, 53)
    pixmap(img, FLAME, {"#": hexc("8b8b8b")}, 45, 36)
    x, y, w, h = 80, 16, 16, 54
    bevel(d, x, y, w, h, SLOT_DARK, WHITE, hexc("1a1a1a"))
    for frac in (0.25, 0.5, 0.75):
        ty = y + h - 1 - round((h - 2) * frac)
        d.line((x + 1, ty, x + 3, ty), fill=hexc("4a4a4a"))
        d.line((x + w - 4, ty, x + w - 2, ty), fill=hexc("4a4a4a"))
    return img


# ------------------------------------------------------------------ electric furnace

def electric_furnace_front(glow=None):
    """Steel face with a heat-proof window onto three zig-zag heating elements."""
    img = steel_plate(31)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 3, 13, 12), fill=hexc("1d2023"))
    d.rectangle((3, 4, 12, 11), fill=hexc("0f1112") if not glow else hexc("3a1608"))
    coil_off = hexc("4a4f54")
    for row, y in enumerate((5, 8, 10)):
        for x in range(3, 13):
            yy = y + (1 if (x + row) % 2 else 0)
            if yy <= 11:
                img.putpixel((x, yy), glow[(x + row * 3) % len(glow)] if glow else coil_off)
    if glow:
        # Warm light washing the glass.
        for x in range(3, 13):
            img.putpixel((x, 4), hexc("6a2a10"))
    for x in range(2, 14):
        img.putpixel((x, 3), STEEL_LIGHT)
    # Status lamp and a little "HOT" amber light.
    img.putpixel((12, 1), hexc("ff5a2a") if glow else hexc("3a2a22"))
    img.putpixel((3, 14), hexc("ffb000") if glow else hexc("3a3220"))
    rivets(img, [(1, 1), (13, 13)])
    return img


def electric_furnace_front_on():
    frames = [
        [hexc("ffb347"), hexc("ff7b2a"), hexc("ffd27a"), hexc("ff5a1f")],
        [hexc("ff8c32"), hexc("ffc35a"), hexc("ff6a24"), hexc("ffe08a")],
        [hexc("ffd27a"), hexc("ff7b2a"), hexc("ffb347"), hexc("ff6a24")],
    ]
    sheet = Image.new("RGBA", (16, 48))
    for i, pal in enumerate(frames):
        sheet.paste(electric_furnace_front(pal), (0, i * 16))
    return sheet


def electric_furnace_side():
    img = steel_plate(32)
    # Ceramic insulation band and vent slots.
    for x in range(1, 15):
        for y in (6, 7, 8, 9):
            img.putpixel((x, y), hexc("d8d2c4") if y in (7, 8) else hexc("b5ae9f"))
    for x in (3, 6, 9, 12):
        for y in range(2, 5):
            img.putpixel((x, y), hexc("1d2023"))
    for x in range(1, 15):
        img.putpixel((x, 13), COPPER_P["3"])
    return img


def electric_furnace_top():
    img = steel_plate(33)
    for y in (4, 6, 8, 10):
        for x in range(3, 13):
            img.putpixel((x, y), hexc("1d2023"))
            img.putpixel((x, y + 1), STEEL_LIGHT)
    rivets(img, [(1, 1), (13, 1), (1, 13), (13, 13)])
    return img


def gui_electric_furnace():
    img, d = machine_gui()
    slot(d, 56, 35)
    slot(d, 116, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 79, 34)
    return img


# ------------------------------------------------------------------ upgrade cards

CARD = [
    "................",
    "..############..",
    ".#bbbbbbbbbbbb#.",
    ".#bccccccccccb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bc........cb#.",
    ".#bccccccccccb#.",
    ".#bbbbbbbbbbbb#.",
    ".#bgbgbgbgbgbb#.",
    "..############..",
    "................",
]

EMBLEMS = {
    # 8x7 emblems drawn in the card window (x 4..11, y 4..10).
    "speed": ["#..#....", ".#..#...", "..#..#..", "...#..#.", "..#..#..", ".#..#...", "#..#...."],
    "efficiency": ["...##...", "..####..", ".##..##.", "##.##.##", ".##..##.", "..####..", "...##..."],
    "capacitor": ["..####..", ".#....#.", ".######.", ".######.", ".######.", ".######.", "..#..#.."],
    "vfd": ["........", ".##.....", "#..#....", "....#..#", ".....##.", "........", "########"],
    "surge": ["....##..", "...##...", "..####..", "...##...", "..##....", ".##.....", ".#......"],
    "capacity": ["..####..", ".######.", ".#....#.", ".######.", ".#....#.", ".######.", ".######."],
}

EMBLEM_COLORS = {"speed": "ff5a4a", "efficiency": "55ff55", "capacitor": "33e0b0", "vfd": "4aa8ff",
                 "surge": "ffd24a", "capacity": "e070ff"}


def upgrade_card(kind):
    pal = {"#": hexc("0b2a17"), "b": hexc("1f6b3a"), "c": hexc("2f8a4c"), "g": hexc("e6c34a")}
    img = pixmap(blank(), CARD, pal)
    # Card window background, a darker board.
    for y in range(4, 11):
        for x in range(4, 12):
            img.putpixel((x, y), hexc("123d22"))
    color = hexc(EMBLEM_COLORS[kind])
    for y, row in enumerate(EMBLEMS[kind]):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((4 + x, 4 + y), color)
    # Chip glint and copper trace.
    img.putpixel((3, 3), hexc("4fbf74"))
    return img


def write_mcmeta(path, frametime):
    full = os.path.join(ROOT, path + ".mcmeta")
    with open(full, "w") as f:
        f.write('{\n  "animation": {\n    "frametime": %d\n  }\n}\n' % frametime)




if __name__ == "__main__":
    save(side(), "block/coal_turbine_side.png")
    save(top(), "block/coal_turbine_top.png")
    save(bottom(), "block/coal_turbine_bottom.png")
    save(front(), "block/coal_turbine_front.png")
    save(front_on(), "block/coal_turbine_front_on.png")
    save(gui(), "gui/coal_turbine.png")
    save(turbine_back_inner(), "block/coal_turbine_back_inner.png")
    save(turbine_frame(), "block/coal_turbine_frame.png")

    save(zinc_ore(), "block/zinc_ore.png")
    save(zinc_ore(deep=True), "block/deepslate_zinc_ore.png")
    save(raw_metal_block(metal("bfcad1", "97a5ae", "7b8994", "5f6b75", "434c54", "262c31"), 32), "block/raw_zinc_block.png")
    save(metal_block(metal("dfe8ee", "bcc8d0", "a7b4bc", "86939d", "5e6a74", "2c343b"), 33, base="3", sheen="2"), "block/zinc_block.png")
    save(metal_block(BRASS_P, 34), "block/brass_block.png")

    save(crusher_front(), "block/crusher_front.png")
    save(crusher_front_on(), "block/crusher_front_on.png")
    write_mcmeta("block/crusher_front_on.png", 2)
    save(crusher_side(), "block/crusher_side.png")
    save(crusher_top(), "block/crusher_top.png")
    save(charger_front(), "block/charger_front.png")
    save(charger_front(on=True), "block/charger_front_on.png")
    save(charger_side(), "block/charger_side.png")
    save(charger_top(), "block/charger_top.png")
    save(capacitor_bank_parts(), "block/capacitor_bank_parts.png")
    save(scada_front(), "block/scada_terminal_front.png")
    save(scada_front_on(), "block/scada_terminal_front_on.png")
    write_mcmeta("block/scada_terminal_front_on.png", 10)
    save(scada_side(), "block/scada_terminal_side.png")
    save(copper_wire(), "block/copper_wire.png")
    save(fiber_optic_cable(), "block/fiber_optic_cable.png")
    save(redstone_engine_base(), "block/redstone_engine_base.png")
    save(stirling_engine_base(), "block/stirling_engine_base.png")

    save(engine_sheet(planks), "entity/engine_redstone.png")
    save(engine_sheet(cobble), "entity/engine_stirling.png")
    save(engine_trunk(), "entity/engine_trunk.png")
    save(turbine_rotor(), "entity/turbine_rotor.png")

    save(recolor(RAW_MAP, RAW_ZINC), "item/raw_zinc.png")
    save(recolor(INGOT_MAP, ZINC_INGOT), "item/zinc_ingot.png")
    save(recolor(NUGGET_TALL, ZINC_NUGGET), "item/zinc_nugget.png")
    save(recolor(INGOT_MAP, BRASS_INGOT), "item/brass_ingot.png")
    save(recolor(NUGGET_LUMP, BRASS_NUGGET), "item/brass_nugget.png")
    for name, ramp in DUSTS.items():
        save(recolor(DUST_MAP, ramp), "item/%s_dust.png" % name)
    save(copper_coil(), "item/copper_coil.png")
    save(electric_motor(), "item/electric_motor.png")
    save(gear(BRASS_P, 60), "item/brass_gear.png")
    save(gear(IRON_P, 61), "item/iron_gear.png")
    save(turbine_blade(), "item/turbine_blade.png")
    save(capacitor(), "item/capacitor.png")
    save(optical_fiber(), "item/optical_fiber.png")
    save(control_circuit(), "item/control_circuit.png")
    save(drill_head(), "item/drill_head.png")
    save(electric_drill(), "item/electric_drill.png")

    save(gui_crusher(), "gui/crusher.png")
    save(gui_electric_furnace(), "gui/electric_furnace.png")
    save(electric_furnace_front(), "block/electric_furnace_front.png")
    save(electric_furnace_front_on(), "block/electric_furnace_front_on.png")
    write_mcmeta("block/electric_furnace_front_on.png", 3)
    save(electric_furnace_side(), "block/electric_furnace_side.png")
    save(electric_furnace_top(), "block/electric_furnace_top.png")
    for kind in EMBLEMS:
        save(upgrade_card(kind), f"item/{kind}_upgrade.png")
    save(gui_charger(), "gui/charger.png")
    save(gui_engine(), "gui/engine.png")
    print("textures written to", os.path.normpath(ROOT))
