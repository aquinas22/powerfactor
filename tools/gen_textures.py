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



# ================================================================== phase B: more metals, ores, rubber, machines
# Everything below follows the vanilla-derived rules above. Metals are defined by a 6-stop gradient
# (t = 0 darkest ... 1 brightest); each vanilla template is then sampled at the *vanilla* luminance
# positions of its roles (normalised against the vanilla ramp it came from), so every new metal
# keeps vanilla's exact contrast structure and differs only by hue/brightness, like vanilla does.

def _lum(h):
    c = hexc(h)
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def grad(stops, t):
    """Sample evenly spaced hex stops at t in [0, 1]; returns a hex string."""
    t = max(0.0, min(1.0, t))
    f = t * (len(stops) - 1)
    i = min(int(f), len(stops) - 2)
    k = f - i
    a, b = hexc(stops[i]), hexc(stops[i + 1])
    return "%02x%02x%02x" % tuple(int(round(a[j] + (b[j] - a[j]) * k)) for j in range(3))


def ramp_at(stops, ts):
    return [grad(stops, t) for t in ts]


def _norm(ls, lo, hi):
    return [max(0.0, (v - lo) / (hi - lo)) for v in ls]


# Role positions, from the luminance of each role in the vanilla sprite the map was taken from.
INGOT_T = _norm([53, 87, 94, 113, 130, 168, 215, 255], 53, 255)          # iron_ingot
NUGGET_T = [INGOT_T[0], INGOT_T[2], INGOT_T[4], INGOT_T[5], (INGOT_T[5] + INGOT_T[6]) / 2, INGOT_T[6], 1.0]
DUST_T = _norm([_lum(h) for h in ["45403a", "6b655d", "8e877d", "7a746b", "b5aea4", "d3cdc4", "efebe5"]], 53, 255)
RAW_T = _norm([52, 77, 118, 149, 184, 207, 229, 246], 52, 246)           # raw_iron
IRON_BLOCK_T = _norm([176, 185, 193, 207, 214, 220, 224, 229, 234, 235, 242], 53, 255)  # vs iron_ingot
GOLD_BLOCK_T = _norm([148, 156, 189, 197, 210, 215, 223, 241, 247], 58, 255)            # vs gold_ingot
RAW_IRON_BLOCK_T = _norm([84, 101, 120, 149, 184, 207, 229], 52, 246)                  # vs raw_iron
RAW_GOLD_BLOCK_T = _norm([120, 145, 170, 194, 217, 239], 77, 254)                      # vs raw_gold


def recolor36(rows, ramp):
    """recolor() for maps with more than 10 roles (0-9 then a, b, ...)."""
    cols = [hexc(h) for h in ramp]
    img = blank()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), cols[int(ch, 36)])
    return img


# Storage block role grids taken from vanilla iron_block (11 roles) and gold_block (9 roles).
IRON_BLOCK_MAP = [
    "3223333333322223",
    "2aaaaa9998777772",
    "2777777777777771",
    "0555555566644440",
    "2aaaaaa999777771",
    "2777777777777771",
    "0555556666444440",
    "2aa9999977777771",
    "2777777777777771",
    "0555555556664440",
    "2aaaaaa999977771",
    "2777777777777771",
    "0555555666644440",
    "2aaaa99997777771",
    "2777777777777771",
    "2222222112111111",
]
GOLD_BLOCK_MAP = [
    "3333223332222331",
    "3788667776644771",
    "3844576643223571",
    "3845766543335561",
    "2656665433455461",
    "2666654334664320",
    "3766555446643220",
    "3765554455433230",
    "2655544554433341",
    "2655445544443541",
    "2454466444446530",
    "2344664334366430",
    "2346643223554341",
    "2255432234443351",
    "2323322344334560",
    "1101100011001100",
]
# Raw storage blocks: vanilla raw_iron_block (7 roles) and raw_gold_block (6 roles).
RAW_IRON_BLOCK_MAP = [
    "2114332345433210",
    "5322221234322465",
    "4431210222123553",
    "1201134530113342",
    "2103565442014433",
    "1023454321245443",
    "0112433220344332",
    "3432332110333212",
    "4544322101220001",
    "3453311254534201",
    "2233221565433311",
    "1001223544323221",
    "4200123443322144",
    "3421011333236554",
    "1323443212245432",
    "2215544111343331",
]
RAW_GOLD_BLOCK_MAP = [
    "4410012421123322",
    "5421025422012324",
    "5432155532100145",
    "3221355443210003",
    "3210233431105521",
    "1100113311035422",
    "2145301310044332",
    "1455330100212231",
    "1343110013521210",
    "0013110135532100",
    "0101101124433210",
    "2331012312432101",
    "3343113321221431",
    "1452203210125533",
    "3132110001155443",
    "1001100110234442",
]

# Plate: a square sheet seen slightly from above (leaning back, so the rows step left), INGOT_MAP roles (0 bottom/right rim, 2 top/left
# rim, 3-4 the sheet's front/right thickness, 5 bevel, 6 face, 7 glints and a diagonal sheen).
PLATE_MAP = [
    "................",
    "................",
    "...22222222222..",
    "..27777777766630",
    "..27666666677530",
    "..27666666775530",
    ".27666657766530.",
    ".27666577666530.",
    ".27665776666530.",
    "27667756666530..",
    "27677566666530..",
    "26555555555530..",
    "14444444444430..",
    ".000000000000...",
    "................",
    "................",
]

# Metal gradients: darkest -> brightest.
METALS = {
    # cool silvery, faint blue-white, softer contrast than iron
    "tin":      dict(stops=["2c3237", "5a646c", "86929b", "afbbc3", "d3dde3", "f2f7fa"], nugget=NUGGET_TALL, block="iron"),
    # dull dark blue-grey with a purple cast, no bright glints
    "lead":     dict(stops=["1d1b25", "363445", "4f4d62", "66647c", "7e7c95", "9997ad"], nugget=NUGGET_LUMP, block="iron"),
    # bright, clearly blue-white, pure white glints
    "silver":   dict(stops=["34405a", "607493", "93abc9", "bdd3ea", "e0edf9", "ffffff"], nugget=NUGGET_TALL, block="gold"),
    # very light, flat (compressed range), slightly warm
    "aluminum": dict(stops=["5f5c57", "908d87", "b3b0aa", "c9c6c0", "dbd8d3", "eeede9"], nugget=NUGGET_LUMP, block="iron"),
    # dark neutral grey, crisp highlight
    "steel":    dict(stops=["1b1d20", "34383c", "4e5358", "686d73", "868c92", "b3b9be"], nugget=NUGGET_TALL, block="iron"),
    # warm orange-brown, browner/olive compared with copper's pink-orange
    "bronze":   dict(stops=["3a220e", "663d1a", "8f5a26", "b07634", "cc9348", "e3b46b"], nugget=NUGGET_LUMP, block="gold"),
    # pale greenish gold
    "electrum": dict(stops=["4a4517", "7a742d", "a69f48", "cac36a", "e3de95", "f8f6cc"], nugget=NUGGET_TALL, block="gold"),
}
# Existing metals' plates use their existing / vanilla ingot colours, indexed by INGOT_MAP role
# (the dominant vanilla colour under each role of the iron layout).
IRON_INGOT = ["353535", "585858", "5e5e5e", "727272", "828282", "a8a8a8", "d8d8d8", "ffffff"]
GOLD_INGOT = ["752802", "e9b115", "b26411", "dc9613", "e9b115", "fad64a", "fdf55f", "fffde0"]
COPPER_INGOT = ["6d3421", "8a4129", "9c4529", "9c4e31", "c15a36", "c15a36", "e77c56", "fc9982"]

RAW_STOPS = {
    "tin":     ["3a3833", "66635c", "928e86", "bab6ad", "dad7cf", "f4f2ec"],
    "lead":    ["1b1922", "34313f", "4f4b5e", "69657b", "858198", "a5a1b6"],
    "silver":  ["36404f", "5d6e86", "8ea4be", "b9cde0", "dae8f4", "fafdff"],
    "bauxite": ["3a1a0e", "6a3420", "98522f", "b86f40", "d08f5c", "e8b585"],
}


def ingot_ramp(m):
    return ramp_at(METALS[m]["stops"], INGOT_T)


def metal_block_vanilla(stops, layout):
    if layout == "iron":
        return recolor36(IRON_BLOCK_MAP, ramp_at(stops, IRON_BLOCK_T))
    return recolor36(GOLD_BLOCK_MAP, ramp_at(stops, GOLD_BLOCK_T))


def raw_block_vanilla(stops, layout):
    if layout == "iron":
        return recolor36(RAW_IRON_BLOCK_MAP, ramp_at(stops, RAW_IRON_BLOCK_T))
    return recolor36(RAW_GOLD_BLOCK_MAP, ramp_at(stops, RAW_GOLD_BLOCK_T))


# ------------------------------------------------------------------ ores
# Base stone/deepslate and cluster masks are vanilla's own pixels (stone.png, deepslate.png and the
# per-pixel difference to iron/gold/copper/lapis ore). Digits = vanilla grey index (the grey
# shading vanilla paints around clusters), letters = ore role by brightness (a darkest);
# upper case is copper's second (patina) family / lapis' blue family.
STONE_GREYS = ["5c5c5c", "686868", "747474", "7f7f7f", "8f8f8f", "a2a2a2"]
DEEP_GREYS = ["2f2f37", "3d3d43", "515151", "646464", "797979"]
STONE_GRID = [
    "4444322321223333",
    "3323233333311232",
    "3211222122233333",
    "3344343344333222",
    "2332223234444233",
    "3433333332212124",
    "2334442442223333",
    "2211212233222333",
    "4443433444333332",
    "3343334443344443",
    "1323221123233222",
    "3333344333334434",
    "3222343223311212",
    "4432233222332224",
    "3223333333334444",
    "3333332234433233",
]
DEEPSLATE_GRID = [
    "4322221133322122",
    "2100111123221121",
    "3343322000011011",
    "1232211123443332",
    "0122111233343321",
    "4423220233232223",
    "4332201122110012",
    "3211113433210122",
    "2121112432321112",
    "1100222322121001",
    "3232122110012244",
    "3321344421211243",
    "3211243332111233",
    "1110023321010011",
    "4433211014322334",
    "4432322024332233",
]
ORE_MASKS = {
    "iron": ([
        ".333........2244",
        "...11..44.abba..",
        "..bcb.3...3bc...",
        ".bcd4321112.....",
        ".444.11bbccb112.",
        "....bcddedc3.bc.",
        "......cdd43..44.",
        "..3.12444..11...",
        "..1bc1123abca2..",
        ".4accdd.34cd....",
        "..44de4...44....",
        "....4.34...ab...",
        "..1.bc4.abcddcb.",
        ".bc.34..4cdec443",
        ".4ba2....444.333",
        "..44.....3.4....",
    ], [
        "................",
        "..........abba..",
        "..bcb10..14bc4..",
        ".bcd422...3.4...",
        "..44...bbccb....",
        "....bcddedc4.bc.",
        "....44cdd44.....",
        "......4.4.......",
        "...bc....abca...",
        "..accdd...cd4...",
        "...4de4...44....",
        "....4......ab...",
        "....bc4.abcddcb.",
        ".bc.44...cdec44.",
        "..ba.....3444...",
        "3344.....3......",
    ]),
    "gold": ([
        "....2...........",
        "....a..1.....0..",
        "....410a1..00aa.",
        ".4.310aba2.0aba.",
        "...10abca1..bb5.",
        "...1bccdb...55..",
        "....5bcba5......",
        ".....5b55....ba.",
        ".1ab.5a5....1...",
        ".2bc5.5...0001..",
        ".4554....0aab11.",
        "..111...1abbdca1",
        "..aab2.a.5bcc55.",
        ".abcdc45.3ab5...",
        "..5bc4....55..b.",
        "...45...........",
    ], [
        ".......2........",
        "....a42......00.",
        "....4.0a....0aa.",
        ".....0aba...aba.",
        ".....abca...bb4.",
        "....bccdb...44..",
        "....4bcba.......",
        ".....4b.4...2ba4",
        "..ab2.a......443",
        "..bc4.4...00.3..",
        "..4433....aab...",
        "..001..1.abbdca.",
        ".0aab..a.4bcc44.",
        ".abcdc.4..ab43..",
        "...bc4....44.2b.",
        "...44.........4.",
    ]),
    "copper": ([
        ".....44.......2.",
        ".....2.........3",
        "..BB100.11.cb422",
        "...20BB01..453.3",
        ".220Bacba.332...",
        "..1abcd54..00...",
        "...3.55.210aa02.",
        ".1200..3.ACDCb1.",
        "..1Ba013..ACacb.",
        ".CDDCa3.324B55..",
        "24BC54301.34533.",
        "..45.11b01...322",
        "....1Bacdb2.21.3",
        "..Ba54BaB54.1BD2",
        ".4554.4554....3.",
        "...2.234.33..44.",
    ], [
        "................",
        "...........00...",
        ".0BB410.11.cb4..",
        "...4.BB.....4...",
        "....Bacba.......",
        "...abcd4..1001..",
        "....444..10aa...",
        "...00....ACDCb..",
        "..0Ba.....ACacb4",
        ".CDDCa4....B444.",
        "..BC44.....4..3.",
        "..44...b........",
        ".....Bacdb......",
        "..Ba..BaB4...BD.",
        ".......443...44.",
        "................",
    ]),
    "lapis": ([
        ".33..........444",
        "..1001244...00..",
        "..FEFC100..4BC..",
        "4..54D2CG...54..",
        ".....00.4300....",
        "...10FD..1EB....",
        "..0BGHGC.3GH44..",
        ".DG555401135.44.",
        ".GH5..1D0FCC.00.",
        ".55.1.1DFGGC1D11",
        "..11D.2.BGH42GE3",
        "..CAA334554..HG3",
        "..554A11CGG.5BG4",
        "....GHF3GHB5.553",
        "....FGC5.55.2223",
        "....4553.33..344",
    ], [
        "................",
        "...100......0...",
        "..FEFC..1...BC4.",
        "..433D4CG43.44..",
        ".....4.44..3....",
        "...11FD3..EB....",
        "..1BGHGC..GH43..",
        ".DG3444...443...",
        ".GH2...D.FCC30..",
        ".444..3DFGGC3D..",
        ".4..D..4BGH4.GE.",
        "..CAA..3.4433HG.",
        "...44A..CGG23BG4",
        "....GHF.GHB4244.",
        "....FGC42.4.....",
        ".....4421.......",
    ]),
}
# Ore ramps per role letter. Each new ore borrows a different vanilla cluster layout.
ORES = {
    "tin":     ("iron",   {"a": "626a72", "b": "8d98a1", "c": "b6c2ca", "d": "d8e2e8", "e": "f2f7fa"}),
    "silver":  ("gold",   {"a": "4d6587", "b": "8fb0d4", "c": "c4def5", "d": "ffffff"}),
    "lead":    ("lapis",  {"A": "231f38", "B": "2b2742", "C": "34304e", "D": "3f3a5c", "E": "4c476d",
                           "F": "625c88", "G": "8c86b8", "H": "bab5e0"}),
    "bauxite": ("copper", {"a": "7a4a2a", "b": "a8532e", "c": "c46a3c", "d": "da8b56",
                           "A": "6e5034", "B": "82623e", "C": "a8865a", "D": "c9a676"}),
}


def vanilla_ore(kind, deep=False):
    mask_name, pal = ORES[kind]
    mask = ORE_MASKS[mask_name][1 if deep else 0]
    greys = DEEP_GREYS if deep else STONE_GREYS
    base = DEEPSLATE_GRID if deep else STONE_GRID
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ch = mask[y][x]
            if ch == ".":
                c = hexc(greys[int(base[y][x])])
            elif ch.isdigit():
                c = hexc(greys[int(ch)])
            else:
                c = hexc(pal[ch])
            img.putpixel((x, y), c)
    return img


# ------------------------------------------------------------------ rubber tree
# Role grids from vanilla oak_log (bark, 6 roles), oak_log_top (9), oak_leaves (4 + holes), oak_planks (7).
LOG_MAP = [
    "3143304330341432",
    "3152305230341332",
    "3022305231311332",
    "4032313232301342",
    "4132303252323143",
    "4313413152413143",
    "2313422343433143",
    "2303432433423243",
    "3105332432424243",
    "3315332332414303",
    "3334323032314303",
    "3134323053305313",
    "5133424052303323",
    "5313433142313333",
    "4314334031311433",
    "4314134131331432",
]
LOG_TOP_MAP = [
    "1012101120110111",
    "0777767686677770",
    "1733445455554481",
    "1738877776777471",
    "1647666666667561",
    "0757644554467460",
    "1646636666466471",
    "1657646556567561",
    "1657646546467461",
    "0657646666566460",
    "1647644345567561",
    "1657666666667461",
    "0757767777787460",
    "1754544444444471",
    "1777667668667771",
    "1011101120110211",
]
LEAVES_MAP = [
    "00.3.22.1102..31",
    "2...0012.212.323",
    "..11.2213.2..333",
    ".2100.3230....3.",
    "2121..23...022..",
    "221..31000..012.",
    "11.03232.11.2213",
    "100.333.0012.312",
    "32.00322.1212.21",
    "3...00012.222.32",
    ".22.0.2213.2...3",
    "3100...323.12...",
    "122.22..3.0013.3",
    "23.3100..0.23123",
    "3.3232..11..323.",
    "2.333..0001..3.2",
]
PLANKS_MAP = [
    "5456666656666652",
    "5544234555445533",
    "4555454333344552",
    "2112210011010010",
    "5636666366654435",
    "4554343234455554",
    "3345444255544333",
    "0011210000111210",
    "5665446666666653",
    "5455554333434332",
    "6544333344443353",
    "0012210001211000",
    "6356655366636566",
    "4455334335545444",
    "4334543255433333",
    "0110012221010000",
]
RUBBER_BARK = ["3a332c", "4d453c", "5f564b", "726859", "877c6b", "938875"]
RUBBER_WOOD = ["7f6c58", "94806a", "a8937b", "b29e86", "bea98f", "c6b298", "cfbca2"]
RUBBER_LEAF = ["2f5e40", "3f7652", "5a956b", "7cb68b"]
RESIN = [hexc("8a4a12"), hexc("d08a2a"), hexc("f4c060")]
LATEX = hexc("f4f1e6")


def rubber_log(tapped=False):
    img = recolor(LOG_MAP, RUBBER_BARK)
    # A few beads of amber resin weeping from the bark.
    for x, y in [(3, 4), (12, 9), (7, 13)]:
        img.putpixel((x, y), RESIN[1])
        img.putpixel((x, y + 1), RESIN[0])
        img.putpixel((x - 1, y), RESIN[2])
    if tapped:
        # Fresh diagonal cut showing pale wood, latex running down from it and pooling in a drop.
        cut = [(5, 4), (6, 4), (7, 5), (8, 5), (9, 6), (10, 6)]
        for x, y in cut:
            img.putpixel((x, y), hexc(RUBBER_WOOD[5]))
            img.putpixel((x, y - 1), hexc(RUBBER_BARK[0]))
            img.putpixel((x, y + 1), hexc(RUBBER_WOOD[2]))
        for y in range(7, 12):
            img.putpixel((9, y), LATEX)
            img.putpixel((10, y), hexc("cfcabb"))
        for x, y in [(8, 12), (9, 12), (10, 12), (9, 13)]:
            img.putpixel((x, y), LATEX if x < 10 else hexc("cfcabb"))
        img.putpixel((9, 13), hexc("cfcabb"))
        img.putpixel((8, 12), hexc("ffffff"))
    return img


def rubber_log_top():
    ramp = RUBBER_BARK[:2] + [RUBBER_BARK[2]] + RUBBER_WOOD[:6]
    return recolor(LOG_TOP_MAP, ramp)


def rubber_leaves():
    return recolor(LEAVES_MAP, RUBBER_LEAF)


def rubber_planks():
    return recolor(PLANKS_MAP, RUBBER_WOOD)


def rubber_sapling():
    """A slender grey stem with clusters of long glossy leaves (rubber trees are trifoliate)."""
    import math
    img = blank()
    stem = [hexc("4d453c"), hexc("726859"), hexc("938875")]
    for y in range(6, 16):
        x = 7 if y > 10 else 8 if y > 7 else 8
        img.putpixel((x, y), stem[1])
        if y > 11:
            img.putpixel((x + 1, y), stem[0])
    img.putpixel((7, 10), stem[2])
    img.putpixel((8, 8), stem[2])
    leaf = [hexc("2f5a3c"), hexc("3d6b4d"), hexc("4e845f"), hexc("69a47a"), hexc("8cc49b"), hexc("b5e0bf")]
    # (base x, base y, angle deg (0 = right, 90 = up), length)
    leaves = [(8, 6, 95, 5.5), (8, 6, 140, 5.0), (8, 6, 45, 5.0),
              (7, 10, 160, 4.6), (8, 9, 20, 4.6), (7, 12, 200, 3.4), (8, 12, -25, 3.4)]
    for bx, by, ang, ln in leaves:
        a = math.radians(ang)
        ux, uy = math.cos(a), -math.sin(a)
        for y in range(16):
            for x in range(16):
                px, py = x + 0.5 - (bx + 0.5), y + 0.5 - (by + 0.5)
                u = px * ux + py * uy
                v = -px * uy + py * ux
                if not (0.3 < u < ln):
                    continue
                half = 1.35 * math.sin(math.pi * u / ln) ** 0.7
                if abs(v) > half:
                    continue
                if abs(v) < 0.45 and u < ln - 1:
                    c = leaf[1]            # midrib
                elif v * (ux - uy) < 0:
                    c = leaf[4] if abs(v) > half * 0.5 else leaf[3]
                else:
                    c = leaf[2] if abs(v) < half * 0.6 else leaf[0]
                img.putpixel((x, y), c)
    # Glossy highlights.
    for x, y in [(8, 2), (4, 4), (12, 4)]:
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), leaf[5])
    return img


# ------------------------------------------------------------------ non-metal items
GRAPHITE_INGOT = ["0e0f11", "1a1b1e", "202226", "2a2c31", "34373d", "444850", "5a5f68", "a3abb6"]
GRAPHITE = ["0e0f11", "1f2125", "2b2e33", "3a3e45", "4d525a", "666c76", "8e959f", "c0c7d0"]

ELECTRODE_MAP = [
    "................",
    "......2672......",
    "......2641......",
    ".......31.......",
    "......2641......",
    ".......31.......",
    ".....2777652....",
    ".....2665430....",
    ".....2764320....",
    ".....2654320....",
    ".....2654320....",
    ".....2764320....",
    ".....2654320....",
    ".....2654320....",
    ".....1100000....",
    "................",
]


def graphite_electrode():
    return recolor(ELECTRODE_MAP, GRAPHITE)


def silicon():
    """Silicon wafer seen at an angle: a thin blue-grey disc with a bright cut edge below it,
    a diagonal reflection and a few crystal-grain boundaries across its face."""
    ramp = [hexc(h) for h in ["141b28", "243149", "33435e", "405474", "4d6589", "5e7aa2", "7f9cc4", "c8d8ee"]]
    edge = [hexc("c9d1db"), hexc("a3adb9"), hexc("7d8896")]
    cx, cy, rx, ry = 7.5, 6.6, 7.0, 4.7
    face = lambda x, y: ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1
    grains = {(4, 5), (4, 6), (5, 7), (5, 8), (6, 9), (6, 10), (10, 3), (10, 4), (11, 5), (11, 6), (12, 7),
              (6, 7), (7, 7), (8, 6), (9, 6)}
    img = blank()
    for y in range(16):
        for x in range(16):
            if face(x, y):
                u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                k = 4 - int(round((u + v) * 1.2))
                if abs((u - v) * 0.5 + 0.05) < 0.11:
                    k = 6
                elif abs((u - v) * 0.5 + 0.05) < 0.2:
                    k = max(k, 5)
                if (x, y) in grains:
                    k -= 1
                img.putpixel((x, y), ramp[max(2, min(6, k))])
            elif face(x, y - 1) or face(x, y - 2):
                img.putpixel((x, y), edge[0] if x < cx - 2 else edge[1] if x < cx + 3 else edge[2])
    src = img.copy()
    on = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and src.getpixel((x, y))[3]
    for y in range(16):
        for x in range(16):
            if not on(x, y):
                continue
            if not on(x, y + 1) or (not on(x + 1, y) and x > cx):
                img.putpixel((x, y), ramp[0])
            elif not on(x, y - 1) or (not on(x - 1, y) and x < cx):
                img.putpixel((x, y), ramp[3])
    for x, y in [(3, 4), (4, 3)]:
        img.putpixel((x, y), ramp[7])
    return img


def mica():
    """Three thin translucent amber sheets fanned on top of each other; each layer's edge shows."""
    def hexagon(ox, oy):
        return [(ox + 2, oy), (ox + 7, oy), (ox + 9, oy + 3.5), (ox + 7, oy + 7), (ox + 2, oy + 7), (ox, oy + 3.5)]

    flakes = [hexagon(0.5, 1), hexagon(3.5, 4), hexagon(6, 7.5)]

    def inside(poly, x, y):
        n, c = len(poly), False
        for i in range(n):
            (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c

    fill = [None, hexc("eab45a", 145), hexc("d4923a", 190), hexc("b5742a", 230)]
    img = blank()
    for y in range(16):
        for x in range(16):
            n = sum(inside(p, x + 0.5, y + 0.5) for p in flakes)
            if n:
                img.putpixel((x, y), fill[min(3, n)])
    for p in flakes:                      # back to front: front edges win
        ins = lambda xx, yy: inside(p, xx + 0.5, yy + 0.5)
        for y in range(16):
            for x in range(16):
                if not ins(x, y):
                    continue
                if not ins(x + 1, y) or not ins(x, y + 1):
                    img.putpixel((x, y), hexc("7a4a14"))
                elif not ins(x - 1, y) or not ins(x, y - 1):
                    img.putpixel((x, y), hexc("ffdc94", 245))
    for x, y in [(3, 3), (4, 3), (6, 6), (7, 6), (9, 10), (10, 10)]:
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), hexc("fffaf0", 250))
    return img


SLIME_MAP = [  # vanilla slime_ball role grid (9 roles)
    "................",
    "................",
    "......1111......",
    "....11455410....",
    "...1456656420...",
    "...1587666540...",
    "..146775665520..",
    "..146655655430..",
    "..145666545230..",
    "..134545553230..",
    "...1345543220...",
    "...0233222230...",
    "....00333300....",
    "......0000......",
    "................",
    "................",
]
LATEX_RAMP = ["8a8578", "aaa598", "bab5a8", "cac6b9", "dad6ca", "e5e2d7", "efede5", "fbfaf5", "ffffff"]


def rubber_item():
    """A roll of dark rubber sheet lying on its side: spiral end on the left, the free end of the
    sheet unrolling flat toward the lower right."""
    import math
    ramp = [hexc(h) for h in ["0c0c0e", "17171a", "212124", "2b2b30", "37373d", "46464e", "5c5c66", "7a7a86"]]
    img = blank()
    # Free end first (the roll sits on top of it).
    for x in range(6, 16):
        img.putpixel((x, 12), ramp[4] if x < 15 else ramp[2])
        img.putpixel((x, 13), ramp[0])
    img.putpixel((15, 11), ramp[3])
    # Cylinder body, axis horizontal, lit from the top-left.
    shade_rows = [2, 6, 5, 5, 4, 4, 3, 3, 2, 1]
    for y in range(2, 12):
        for x in range(4, 14):
            img.putpixel((x, y), ramp[shade_rows[y - 2]])
        img.putpixel((14, y), ramp[0])
    for x in range(4, 14):
        img.putpixel((x, 11), ramp[0])
    for x in (6, 9, 12):
        img.putpixel((x, 3), ramp[7])
    # Spiral end cap.
    for y in range(2, 12):
        for x in range(0, 8):
            dx, dy = (x + 0.5 - 4.0) / 3.1, (y + 0.5 - 6.5) / 4.9
            rr = math.hypot(dx, dy)
            if rr > 1.0:
                continue
            ring = int(rr * 3.4) % 2
            c = (ramp[6] if dx + dy < 0 else ramp[5]) if ring else ramp[2]
            if rr < 0.2:
                c = ramp[0]
            elif rr > 0.84:
                c = ramp[2] if dx + dy < 0 else ramp[0]
            img.putpixel((x, y), c)
    return img


def tree_tap():
    """Wooden spout: a chunky plug (driven into the bark) on the right, an open trough running to
    the nozzle on the left with latex in it, and a drop falling from the nozzle."""
    w = [hexc(h) for h in ["3d2c14", "5e4422", "7e6237", "9c7f4e", "b8945f", "d0ae78"]]
    img = blank()
    # Plug block x9..14, y3..11 (end grain faces us).
    for y in range(3, 12):
        for x in range(9, 15):
            c = w[3] if (x + y * 2) % 5 else w[2]
            if x == 9 or y == 3:
                c = w[4]
            if x == 14 or y == 11:
                c = w[0]
            img.putpixel((x, y), c)
    for x, y in [(11, 6), (12, 6), (11, 7), (12, 8)]:
        img.putpixel((x, y), w[2])          # growth ring
    img.putpixel((10, 4), w[5])
    # Trough x1..8, y6..10.
    for x in range(1, 9):
        img.putpixel((x, 6), w[4])          # far lip, lit
        img.putpixel((x, 7), w[1])          # channel floor
        img.putpixel((x, 8), w[1] if x < 3 else w[2])
        img.putpixel((x, 9), w[3])          # near wall
        img.putpixel((x, 10), w[0])         # bottom rim
    for x in range(3, 9):
        img.putpixel((x, 7), LATEX)
    img.putpixel((2, 7), hexc("cfcabb"))
    for y in range(6, 11):
        img.putpixel((0, y), w[2] if y < 10 else w[0])
    img.putpixel((0, 6), w[3])
    # Drop under the nozzle.
    img.putpixel((1, 12), LATEX)
    img.putpixel((1, 13), hexc("cfcabb"))
    img.putpixel((2, 13), hexc("aaa598"))
    img.putpixel((1, 11), hexc("e4e1d8"))
    return img


CIRCUIT_ADV = [
    "................",
    "................",
    "................",
    ".rrrrrrrrrrrrrr.",
    ".rbsssbblllbpbR.",
    ".rbbbsbKKKKbsbR.",
    ".rpssbbKkkKbsbR.",
    ".rbbbsbKkkKbsbR.",
    ".rbmbsbKKKKbsbR.",
    ".rbbbsbblllbsbR.",
    ".rbLbssssssssbR.",
    ".rbbbbbbbbbbbbR.",
    ".rgbgbgbgbgbgbR.",
    ".RRRRRRRRRRRRRR.",
    "................",
    "................",
]


def advanced_circuit():
    pal = {"r": hexc("1d3c78"), "R": hexc("0d1a38"), "b": hexc("2c5bb0"), "s": hexc("c4ced8"),
           "p": hexc("f2f6fa"), "K": hexc("17181b"), "k": hexc("2e3036"), "l": hexc("9aa3ab"),
           "m": hexc("e8e8e8"), "L": hexc("4fe6ff"), "g": hexc("dfe6ec")}
    img = pixmap(blank(), CIRCUIT_ADV, pal)
    jitter(img, 82, 0.05, mask={(x, y) for x in range(2, 14) for y in range(4, 13)
                                if img.getpixel((x, y)) == pal["b"]})
    img.putpixel((8, 6), hexc("44464e"))
    return img


STEEL_GEAR_P = metal("b3b9be", "868c92", "686d73", "4e5358", "34383c", "16181a")


def heating_coil():
    """Orange-red nichrome helix wound on a white ceramic former with steel terminals."""
    ceramic = [hexc(h) for h in ["f2eee4", "d9d2c3", "b5ac9a", "8c8473", "5f594e"]]
    wire = [hexc(h) for h in ["ffb070", "f0703a", "d8522a", "b23a1c", "7a2410"]]
    img = blank()
    rows = {4: 0, 5: 0, 6: 1, 7: 1, 8: 2, 9: 3, 10: 4}
    # Former (core) and end beads.
    for y in range(6, 10):
        for x in range(2, 14):
            img.putpixel((x, y), ceramic[rows[y] if y < 9 else 3])
    for x in (1, 2, 13, 14):
        for y in range(4, 12):
            c = ceramic[0] if y == 4 else ceramic[1] if y < 8 else ceramic[2] if y < 11 else ceramic[4]
            if x in (2, 14):
                c = shade(c, 0.88)
            img.putpixel((x, y), c)
    # Helix: slanted turns, one every 2px.
    for x0 in range(3, 13, 2):
        for y in range(5, 11):
            x = x0 + (1 if y < 7 else 0)
            k = y - 5
            c = wire[0] if k == 0 else wire[1] if k < 3 else wire[2] if k < 5 else wire[4]
            if x < 13:
                img.putpixel((x, y), c)
    # Steel terminal studs.
    for y in (7, 8):
        img.putpixel((0, y), hexc("c3ccd3") if y == 7 else hexc("6f767d"))
        img.putpixel((15, y), hexc("8a939a") if y == 7 else hexc("4a5056"))
    return img


SPOOL_MAP = [
    "................",
    "................",
    "....ffffffff....",
    "...fFFFFFFFFf...",
    "...gffffffffg...",
    "....12443210....",
    "....23443210w...",
    "....12443210.w..",
    "....23443210.w..",
    "....12443210..w.",
    "....23443210..w.",
    "....12443210....",
    "...hhhhhhhhhh...",
    "...ghhhhhhhhG...",
    "....GGGGGGGG....",
    "................",
]


def wire_spool(stops):
    w = ramp_at(stops, [0.08, 0.3, 0.5, 0.7, 0.92])
    pal = {"0": hexc(w[0]), "1": hexc(w[1]), "2": hexc(w[2]), "3": hexc(w[3]), "4": hexc(w[4]),
           "w": hexc(w[2]),
           "f": hexc("5a6168"), "F": hexc("8a939a"), "g": hexc("3a4045"),
           "h": hexc("4a5056"), "G": hexc("24282b")}
    img = pixmap(blank(), SPOOL_MAP, pal)
    # Odd rows of windings sit a touch deeper so the fine turns read.
    for y in (6, 8, 10):
        for x in range(4, 12):
            img.putpixel((x, y), shade(img.getpixel((x, y)), 0.86))
    return img


COPPER_STOPS = ["6d3421", "9c4529", "c15a36", "e77c56", "fc9982", "fbc3b6"]


# ------------------------------------------------------------------ machines
def anim(frames):
    sheet = Image.new("RGBA", (16, 16 * len(frames)))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, i * 16))
    return sheet


def alloy_furnace_front(glow=None):
    """Copper induction coil wound round a ceramic crucible; the melt glows when running."""
    img = steel_plate(70)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 13, 11), fill=hexc("1d2023"))
    bg = hexc("2a1408") if glow else hexc("0f1112")
    d.rectangle((3, 3, 12, 10), fill=bg)
    if glow:
        for x in range(4, 12):
            img.putpixel((x, 3), hexc("4a2410"))
    cer = [hexc("cfc8ba"), hexc("a39b8c"), hexc("7a7366"), hexc("57514a")]
    if glow:
        # The crucible itself is heated through: ceramic glowing yellow to orange.
        cer = [hexc("fff0b0"), hexc("ffc860"), hexc("e88a30"), hexc("a8501a")]
    cru = {4: (5, 10), 5: (5, 10), 6: (5, 10), 7: (5, 10), 8: (6, 9), 9: (6, 9), 10: (6, 9)}
    for y, (a, b) in cru.items():
        for x in range(a, b + 1):
            k = (x - a) / max(1, b - a)
            img.putpixel((x, y), cer[0] if k < 0.2 else cer[1] if k < 0.6 else cer[2] if k < 0.9 else cer[3])
    # Melt surface inside the rim.
    melt = glow or [hexc("3a3430"), hexc("2c2724"), hexc("332d29")]
    for x in range(6, 10):
        img.putpixel((x, 4), melt[(x * 3) % len(melt)])
    if glow:
        for x in (6, 7, 8, 9):
            img.putpixel((x, 3), glow[(x + 1) % len(glow)] if x in (7, 8) else hexc("8a4a1a"))
    # Coil: three front turns, the back of each turn peeking out at the sides.
    for y in (5, 7, 9):
        for x in range(4, 12):
            c = COPPER_P["1"] if x < 6 else COPPER_P["2"] if x < 10 else COPPER_P["4"]
            img.putpixel((x, y), c)
        img.putpixel((3, y + 1), COPPER_P["5"])
        img.putpixel((12, y + 1), COPPER_P["5"])
    if glow:
        for y in (5, 7, 9):
            img.putpixel((7, y), hexc("ffd0a0"))
    for x in range(2, 14):
        img.putpixel((x, 2), STEEL_LIGHT)
    # Control strip: vents and a status lamp.
    for x in (3, 5, 7, 9):
        img.putpixel((x, 13), hexc("1d2023"))
    d.rectangle((11, 12, 13, 14), fill=hexc("222222"))
    img.putpixel((12, 13), hexc("ffb000") if glow else hexc("3a3220"))
    rivets(img, [(0, 0), (14, 0)])
    return img


def alloy_furnace_front_on():
    frames = [
        [hexc("fff6c8"), hexc("ffe066"), hexc("ffd24a"), hexc("ffb43a")],
        [hexc("ffe066"), hexc("fffbe0"), hexc("ffc845"), hexc("ffd24a")],
        [hexc("ffd24a"), hexc("ffb43a"), hexc("fff6c8"), hexc("ffe066")],
    ]
    return anim([alloy_furnace_front(p) for p in frames])


def alloy_furnace_side():
    img = steel_plate(71)
    # Refractory brick band and copper cooling pipes running vertically.
    for y in range(5, 11):
        for x in range(1, 15):
            mortar = y in (7, 10) or (x + (3 if y < 7 else 0)) % 6 == 0
            img.putpixel((x, y), hexc("6b5d52") if mortar else shade(hexc("a5523a"), 1 + ((x * 5 + y * 3) % 7 - 3) * 0.03))
    for y in range(5, 11):
        img.putpixel((1, y), hexc("8a4535"))
    for x in (4, 11):
        for y in range(1, 15):
            if not 5 <= y <= 10:
                img.putpixel((x, y), COPPER_P["2"])
                img.putpixel((x + 1, y), COPPER_P["4"])
        img.putpixel((x, 4), COPPER_P["5"])
        img.putpixel((x + 1, 4), COPPER_P["5"])
        img.putpixel((x, 11), COPPER_P["1"])
        img.putpixel((x + 1, 11), COPPER_P["3"])
    for x in range(1, 15):
        img.putpixel((x, 4), STEEL_DARK if x not in (4, 5, 11, 12) else img.getpixel((x, 4)))
        img.putpixel((x, 11), STEEL_LIGHT if x not in (4, 5, 11, 12) else img.getpixel((x, 11)))
    return img


def alloy_furnace_top():
    img = steel_plate(72)
    d = ImageDraw.Draw(img)
    # Two feed hoppers for the two inputs.
    for ox in (2, 9):
        d.rectangle((ox, 3, ox + 4, 7), fill=hexc("2b2e31"))
        d.rectangle((ox + 1, 4, ox + 3, 6), fill=hexc("0d0e0f"))
        img.putpixel((ox + 2, 5), hexc("050505"))
        edges(img, (ox, 3, ox + 4, 7), STEEL_DARK, STEEL_LIGHT)
    # Exhaust vent slots.
    for y in (10, 12):
        d.line((3, y, 12, y), fill=hexc("1d2023"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


ARC_FRAMES = [
    # white-hot core path between the electrode tips (x6 / x9, y6) and down into the bath
    [(6, 7), (7, 8), (8, 7), (9, 7), (6, 8), (5, 9)],
    [(6, 7), (7, 7), (7, 8), (8, 8), (9, 7), (9, 8), (10, 9)],
    [(6, 7), (6, 8), (7, 7), (8, 7), (8, 8), (9, 7), (7, 9)],
    [(6, 7), (7, 8), (7, 9), (8, 8), (9, 7), (9, 9), (8, 7)],
]


def arc_furnace_front(frame=None):
    img = steel_plate(73)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 1, 13, 12), fill=hexc("17191b"))
    d.rectangle((3, 2, 12, 11), fill=hexc("0a0b0c") if frame is None else hexc("101826"))
    # Molten bath.
    if frame is None:
        bath = [hexc("2a2320"), hexc("3a2a1e"), hexc("4a2c16")]
    else:
        bath = [hexc("ff8a2a"), hexc("ffb040"), hexc("e85a1a"), hexc("ffd070")]
    for x in range(3, 13):
        img.putpixel((x, 11), bath[(x + (frame or 0)) % len(bath)])
        img.putpixel((x, 10), shade(bath[(x * 3 + (frame or 0)) % len(bath)], 0.7) if frame is not None else bath[0])
    # Two graphite electrodes with copper clamps.
    for x0 in (5, 9):
        for y in range(1, 6):
            img.putpixel((x0, y), hexc("5d626b"))
            img.putpixel((x0 + 1, y), hexc("2b2e33"))
        img.putpixel((x0, 6), hexc("3a3e45"))
        img.putpixel((x0 + 1, 6), hexc("1f2125"))
        for y in (2, 3):
            img.putpixel((x0, y), COPPER_P["1"] if y == 2 else COPPER_P["3"])
            img.putpixel((x0 + 1, y), COPPER_P["2"] if y == 2 else COPPER_P["4"])
    if frame is not None:
        core = ARC_FRAMES[frame]
        # Blue glow two pixels out, brighter next to the arc.
        for x, y in core:
            for ddx in range(-2, 3):
                for ddy in range(-2, 3):
                    p = (x + ddx, y + ddy)
                    if 3 <= p[0] <= 12 and 2 <= p[1] <= 9 and img.getpixel(p) in (hexc("101826"), hexc("1f3a70")):
                        img.putpixel(p, hexc("4a7cff") if abs(ddx) + abs(ddy) == 1 else hexc("1f3a70"))
        for i, (x, y) in enumerate(core):
            img.putpixel((x, y), hexc("ffffff") if i % 2 == 0 else hexc("bfe6ff"))
        # Electrode tips glow.
        img.putpixel((6, 6), hexc("ffe0a0"))
        img.putpixel((9, 6), hexc("ffe0a0"))
        img.putpixel((5, 6), hexc("ff9a40"))
        img.putpixel((10, 6), hexc("ff9a40"))
    for x in range(2, 14):
        img.putpixel((x, 1), STEEL_LIGHT if x not in (5, 6, 9, 10) else img.getpixel((x, 1)))
    hazard(img, 1, 14, 13, 14)
    return img


def arc_furnace_front_on():
    return anim([arc_furnace_front(i) for i in range(4)])


def arc_furnace_side():
    img = steel_plate(74)
    # Water-cooled panel: a serpentine coolant pipe.
    pipe = [hexc("7fb0e0"), hexc("3a6ea5"), hexc("244a73")]
    for i, y in enumerate((2, 5, 8)):
        for x in range(2, 14):
            img.putpixel((x, y), pipe[0])
            img.putpixel((x, y + 1), pipe[1])
        xe = 13 if i % 2 == 0 else 2
        for yy in range(y, y + 4):
            if yy < 11:
                img.putpixel((xe, yy), pipe[1] if xe == 13 else pipe[0])
    for x in range(2, 14):
        img.putpixel((x, 10), pipe[2])
    # Heavy copper bus feeding the electrodes.
    for x in range(1, 15):
        img.putpixel((x, 12), COPPER_P["1"])
        img.putpixel((x, 13), COPPER_P["3"])
        img.putpixel((x, 14), COPPER_P["5"])
    for x in (3, 8, 12):
        img.putpixel((x, 13), RIVET)
    return img


def arc_furnace_top():
    import math
    img = steel_plate(75)
    # Refractory roof dome.
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            rr = math.hypot(dx, dy)
            if rr <= 6.6:
                c = shade(hexc("6e6a66"), 1 + ((x * 7 + y * 5) % 5 - 2) * 0.03)
                if rr > 5.8:
                    c = hexc("3e3b38") if dx + dy > 0 else hexc("9a948e")
                img.putpixel((x, y), c)
    # Three electrode tops in a triangle, each in a copper clamp ring.
    for cx, cy in [(7, 3), (3, 10), (11, 10)]:
        for dx in range(-1, 3):
            for dy in range(-1, 3):
                edge = dx in (-1, 2) or dy in (-1, 2)
                if edge and (dx, dy) in [(-1, -1), (2, -1), (-1, 2), (2, 2)]:
                    continue
                if edge:
                    c = COPPER_P["1"] if dx == -1 or dy == -1 else COPPER_P["4"]
                else:
                    c = hexc("5d626b") if (dx, dy) == (0, 0) else hexc("2b2e33") if (dx, dy) == (1, 1) else hexc("3a3e45")
                img.putpixel((cx + dx, cy + dy), c)
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


def wire_drawer_front(frame=0):
    """Die block upper-left; drawn copper wire runs off it onto a spool whose spokes turn."""
    import math
    img = steel_plate(76)
    d = ImageDraw.Draw(img)
    # Die block.
    d.rectangle((1, 2, 5, 7), fill=hexc("8a939a"))
    edges(img, (1, 2, 5, 7), STEEL_LIGHT, STEEL_DARK)
    img.putpixel((3, 4), hexc("1b1d1f"))
    img.putpixel((4, 4), hexc("2c2f32"))
    img.putpixel((3, 5), hexc("2c2f32"))
    img.putpixel((4, 5), STEEL_LIGHT)
    # Wire leaving the die.
    for x in range(5, 10):
        img.putpixel((x, 4), COPPER_P["1"])
    # Spool, face-on.
    cx, cy = 10.0, 9.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            rr = math.hypot(dx, dy)
            if rr > 5.1:
                continue
            ang = math.atan2(dy, dx) - frame * math.pi / 3
            if rr > 4.3:
                c = hexc("b9c2c9") if dx + dy < 0 else hexc("3a4045")
            elif rr > 2.3:
                c = COPPER_P["2"] if int(rr * 2) % 2 else COPPER_P["3"]
                if dx + dy > 2.5:
                    c = shade(c, 0.8)
            elif rr > 0.9:
                spoke = (ang * 3 / (2 * math.pi)) % 1 < 0.34
                c = hexc("6f767d") if spoke else hexc("1b1d1f")
            else:
                c = hexc("c3ccd3")
            img.putpixel((x, y), c)
    for x, y in [(10, 4), (10, 5)]:
        img.putpixel((x, y), COPPER_P["1"])
    # Motor housing under the die.
    d.rectangle((1, 10, 4, 14), fill=hexc("3a4045"))
    for y in (11, 13):
        d.line((1, y, 4, y), fill=STEEL_LIGHT)
    img.putpixel((2, 9), hexc("5cff6a") if frame else hexc("1f3a24"))
    return img


def wire_drawer_front_on():
    return anim([wire_drawer_front(1), wire_drawer_front(2)])


def wire_drawer_side():
    img = steel_plate(77)
    d = ImageDraw.Draw(img)
    # A rack of three dies, reducing in size, with the wire threaded through.
    for x, r_ in ((3, 2), (8, 1), (12, 1)):
        d.rectangle((x - 1, 5, x + 1 + (1 if r_ == 2 else 0), 9), fill=hexc("8a939a"))
        edges(img, (x - 1, 5, x + 1 + (1 if r_ == 2 else 0), 9), STEEL_LIGHT, STEEL_DARK)
    for x in range(1, 15):
        if img.getpixel((x, 7)) not in (STEEL_LIGHT, STEEL_DARK):
            img.putpixel((x, 7), COPPER_P["2"] if x < 8 else COPPER_P["1"])
    for y in (11, 13):
        d.line((3, y, 12, y), fill=hexc("202326"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    rivets(img, [(1, 1), (13, 1)])
    return img


def wire_drawer_top():
    img = steel_plate(78)
    d = ImageDraw.Draw(img)
    # Feed guide: a slotted steel bar with a wire roller.
    d.rectangle((2, 6, 13, 9), fill=hexc("2b2e31"))
    d.line((3, 7, 12, 7), fill=hexc("0d0e0f"))
    d.line((3, 8, 12, 8), fill=hexc("1b1d1f"))
    edges(img, (2, 6, 13, 9), STEEL_DARK, STEEL_LIGHT)
    for x in range(5, 11):
        img.putpixel((x, 7), COPPER_P["2"])
    for x, y in [(4, 3), (11, 3), (4, 12), (11, 12)]:
        img.putpixel((x, y), hexc("1d2023"))
        img.putpixel((x + 1, y), hexc("1d2023"))
    rivets(img, [(1, 1), (13, 1), (1, 13), (13, 13)])
    return img


def rolling_mill_front(o=0):
    """Two big knurled rollers squeezing a sheet; their stripes counter-rotate 1px a frame."""
    img = steel_plate(79)
    d = ImageDraw.Draw(img)
    d.rectangle((1, 1, 14, 14), fill=hexc("1d2023"))
    roll = [hexc("5a6168"), hexc("c3ccd3"), hexc("a3acb3"), hexc("8a939a"), hexc("6f767d"), hexc("4a5056")]
    for oy, sgn in ((2, 1), (9, -1)):
        for i in range(6):
            y = oy + i - (1 if oy == 9 else 0)
            for x in range(2, 14):
                c = roll[i]
                if 1 <= i <= 4 and (x + sgn * o) % 3 == 0:
                    c = shade(c, 0.78)
                img.putpixel((x, y), c)
        # Bearing blocks at the roller ends.
        for y in range(oy, oy + 5):
            img.putpixel((1, y), hexc("8a939a"))
            img.putpixel((14, y), hexc("3a4045"))
    # The sheet being rolled out between them.
    for x in range(0, 16):
        img.putpixel((x, 7), hexc("eef2f4") if x < 8 else hexc("d6dde2"))
        img.putpixel((x, 8), hexc("7d858c"))
    return img


def rolling_mill_front_on():
    return anim([rolling_mill_front(0), rolling_mill_front(1)])


def rolling_mill_side():
    import math
    img = steel_plate(80)
    ImageDraw.Draw(img).ellipse((1, 1, 14, 14), fill=hexc("1d2023"))
    # Drive gear in a guard.
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            rr = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = (ang * 10 / (2 * math.pi)) % 1 < 0.5
            if rr <= (6.0 if tooth else 5.0):
                lit = -(dx + dy) / max(rr, 0.01)
                c = hexc("9aa3ab") if lit > 0.3 else hexc("6f767d") if lit > -0.4 else hexc("4a5056")
                if rr < 2.0:
                    c = hexc("2b2e31") if rr > 1.0 else hexc("c3ccd3")
                elif 3.0 < rr < 3.9:
                    c = shade(c, 0.85)
                img.putpixel((x, y), c)
    hazard(img, 1, 14, 14, 14)
    rivets(img, [(0, 0), (14, 0)])
    return img


def rolling_mill_top():
    img = steel_plate(81)
    d = ImageDraw.Draw(img)
    # Feed slot and the top roller's housing.
    d.rectangle((2, 6, 13, 9), fill=hexc("1d2023"))
    for x in range(3, 13):
        img.putpixel((x, 7), hexc("c3ccd3") if x % 3 else hexc("8a939a"))
        img.putpixel((x, 8), hexc("6f767d"))
    edges(img, (2, 6, 13, 9), STEEL_DARK, STEEL_LIGHT)
    for y in (3, 12):
        d.line((3, y, 12, y), fill=hexc("202326"))
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


def ore_washer_front(frame=None):
    """Porthole onto the washing drum; the water sloshes when running."""
    import math
    img = steel_plate(82)
    cx, cy = 8.0, 7.5
    water = [hexc("8fd0ff"), hexc("3f8fd0"), hexc("2f6fb0"), hexc("234f86")]
    if frame is None:
        surf = lambda x: 7
    elif frame == 0:
        surf = lambda x: 6 + (1 if x < 6 else 0) + (1 if x < 4 else 0)
    else:
        surf = lambda x: 6 + (1 if x > 9 else 0) + (1 if x > 11 else 0)
    bits = {None: [(6, 11), (9, 10)], 0: [(5, 10), (9, 9), (7, 12)], 1: [(10, 11), (6, 9), (8, 12)]}[frame]
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            rr = math.hypot(dx, dy)
            if rr > 6.2:
                continue
            if rr > 5.0:
                c = BRASS_P["2"] if dx + dy < 0 else BRASS_P["4"]
                if rr > 5.7:
                    c = BRASS_P["5"] if dx + dy > 0 else BRASS_P["3"]
            elif y < surf(x):
                c = hexc("1a2228")
            elif y == surf(x):
                c = water[0]
            else:
                c = water[1] if y < surf(x) + 2 else water[2] if rr < 4.2 else water[3]
            img.putpixel((x, y), c)
    for x, y in bits:
        img.putpixel((x, y), hexc("8a6a4a"))
    # Glass glints and porthole bolts.
    img.putpixel((5, 4), hexc("dfefff"))
    img.putpixel((6, 3), hexc("9fb7c9"))
    for x, y in [(8, 1), (2, 7), (13, 7), (8, 13)]:
        img.putpixel((x, y), RIVET)
    img.putpixel((13, 1), hexc("5cff6a") if frame is not None else hexc("1f3a24"))
    for x in range(3, 13):
        img.putpixel((x, 14), hexc("2b2e31") if x % 2 else STEEL_DARK)
    return img


def ore_washer_front_on():
    return anim([ore_washer_front(0), ore_washer_front(1)])


def ore_washer_side():
    img = steel_plate(83)
    d = ImageDraw.Draw(img)
    # Sight glass showing the water level, and a water inlet pipe.
    d.rectangle((10, 2, 13, 13), fill=hexc("2b2e31"))
    for y in range(3, 13):
        for x in (11, 12):
            img.putpixel((x, y), hexc("1a2228") if y < 6 else (hexc("3f8fd0") if x == 11 else hexc("2f6fb0")))
    img.putpixel((11, 6), hexc("8fd0ff"))
    img.putpixel((12, 6), hexc("8fd0ff"))
    for y in range(5, 8):
        for x in range(1, 9):
            img.putpixel((x, y), [hexc("b9c2c9"), hexc("8a939a"), hexc("5a6168")][y - 5])
    for y in range(4, 9):
        img.putpixel((7, y), BRASS_P["2"] if y < 6 else BRASS_P["4"])
    for y in (10, 12):
        d.line((2, y, 8, y), fill=hexc("202326"))
        d.line((2, y + 1, 8, y + 1), fill=STEEL_LIGHT)
    rivets(img, [(1, 1), (6, 1)])
    return img


def ore_washer_top():
    img = steel_plate(84)
    d = ImageDraw.Draw(img)
    # Hopper grate and a water inlet flange.
    d.rectangle((2, 2, 9, 9), fill=hexc("0d0e0f"))
    for i in range(2, 10, 2):
        d.line((i, 2, i, 9), fill=hexc("5a6168"))
        d.line((2, i, 9, i), fill=hexc("5a6168"))
    edges(img, (2, 2, 9, 9), STEEL_DARK, STEEL_LIGHT)
    d.ellipse((10, 9, 14, 13), fill=BRASS_P["3"])
    d.ellipse((11, 10, 13, 12), fill=hexc("234f86"))
    img.putpixel((11, 9), BRASS_P["1"])
    img.putpixel((10, 10), BRASS_P["1"])
    rivets(img, [(12, 2), (2, 12)])
    return img


PUMP_BLUE = [hexc("8fbbe6"), hexc("4f86c0"), hexc("3a6ea5"), hexc("2a5482"), hexc("1d3a5c")]


def electric_pump_front():
    import math
    img = steel_plate(85)
    # Blue volute casing with the impeller hub, outlet pipe up top.
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 7.5, y + 0.5 - 9
            rr = math.hypot(dx, dy)
            if rr <= 5.4:
                lit = -(dx + dy) / max(rr, 0.01)
                c = PUMP_BLUE[1] if lit > 0.4 else PUMP_BLUE[2] if lit > -0.3 else PUMP_BLUE[3]
                if rr > 4.6:
                    c = PUMP_BLUE[0] if dx + dy < 0 else PUMP_BLUE[4]
                if rr < 2.2:
                    c = hexc("c3ccd3") if rr < 0.9 else hexc("6f767d") if dx + dy < 0 else hexc("3a4045")
                img.putpixel((x, y), c)
    for y in range(1, 5):
        img.putpixel((6, y), hexc("b9c2c9"))
        img.putpixel((7, y), hexc("8a939a"))
        img.putpixel((8, y), hexc("5a6168"))
    for x in range(5, 10):
        img.putpixel((x, 1), BRASS_P["2"] if x < 8 else BRASS_P["4"])
    # Pressure gauge, top right.
    d = ImageDraw.Draw(img)
    d.ellipse((10, 1, 14, 5), fill=hexc("2a2a2a"))
    d.ellipse((11, 2, 13, 4), fill=hexc("e8e4d8"))
    img.putpixel((12, 3), hexc("cc2222"))
    img.putpixel((13, 2), hexc("cc2222"))
    rivets(img, [(1, 1), (1, 13), (13, 13)])
    return img


def electric_pump_side():
    img = steel_plate(86)
    d = ImageDraw.Draw(img)
    # Motor with cooling fins, coupled to a pipe stub.
    d.rectangle((2, 3, 10, 12), fill=hexc("3a4045"))
    for y in range(4, 12):
        fin = y % 2 == 0
        for x in range(3, 10):
            img.putpixel((x, y), PUMP_BLUE[1] if fin else PUMP_BLUE[3])
    for x in range(2, 11):
        img.putpixel((x, 3), PUMP_BLUE[0])
        img.putpixel((x, 12), PUMP_BLUE[4])
    for y in range(6, 10):
        for x in range(11, 15):
            img.putpixel((x, y), [hexc("b9c2c9"), hexc("8a939a"), hexc("6f767d"), hexc("4a5056")][y - 6])
    for y in range(5, 11):
        img.putpixel((13, y), BRASS_P["2"] if y < 8 else BRASS_P["4"])
    rivets(img, [(1, 13), (12, 13)])
    return img


def electric_pump_top():
    img = steel_plate(87)
    d = ImageDraw.Draw(img)
    # Outlet flange: brass ring with bolts around a dark bore.
    d.ellipse((3, 3, 12, 12), fill=BRASS_P["3"])
    d.ellipse((3, 3, 11, 11), fill=BRASS_P["2"])
    d.ellipse((4, 4, 11, 11), fill=BRASS_P["3"])
    d.ellipse((5, 5, 10, 10), fill=hexc("1b1d1f"))
    img.putpixel((6, 6), hexc("2c2f32"))
    for x, y in [(7, 3), (3, 7), (12, 8), (8, 12)]:
        img.putpixel((x, y), BRASS_P["5"])
    rivets(img, CORNERS)
    return img


def electric_pump_bottom():
    img = steel_plate(88)
    d = ImageDraw.Draw(img)
    # Intake strainer: dark mouth behind a grid of bars.
    d.rectangle((2, 2, 13, 13), fill=hexc("0d0e0f"))
    for i in range(3, 13, 3):
        d.line((i, 2, i, 13), fill=hexc("6f767d"))
        d.line((2, i, 13, i), fill=hexc("6f767d"))
        d.line((i + 1, 2, i + 1, 13), fill=hexc("2b2e31"))
    edges(img, (2, 2, 13, 13), STEEL_DARK, STEEL_LIGHT)
    return img


# ------------------------------------------------------------------ fluid logistics
def fluid_tank(top=False):
    img = blank()
    for y in range(16):
        for x in range(16):
            k = min(x, y, 15 - x, 15 - y)
            if k == 0:
                img.putpixel((x, y), STEEL_LIGHT if (x == 0 or y == 0) and x < 15 and y < 15 else STEEL_DARK)
            elif k == 1:
                inner_dark = x == 14 or y == 14
                img.putpixel((x, y), shade(STEEL, 0.85) if inner_dark else STEEL)
            elif k == 2:
                # glass edge just inside the frame: a faint line so the pane reads
                img.putpixel((x, y), (255, 255, 255, 28))
    streaks = [(4, 6), (5, 5), (6, 4), (7, 3), (4, 8), (5, 7), (9, 12), (10, 11), (11, 10), (12, 9)] if not top else \
        [(4, 5), (5, 4), (6, 3), (3, 6), (9, 12), (10, 11), (11, 10), (12, 9), (8, 12)]
    for x, y in streaks:
        img.putpixel((x, y), (255, 255, 255, 60))
    for x, y in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        img.putpixel((x, y), RIVET)
    return img


def pipe_tile(base, light, dark, band, inner=None, rings=None):
    """For thin pipe models whose faces sample the 5..11 window on either axis: transpose-symmetric
    shading (lit at 5, shadowed at 10), flange bands at the block edges and optional rings."""
    img = Image.new("RGBA", (16, 16))
    r = random.Random(90)
    for y in range(16):
        for x in range(16):
            c = shade(base, 1 + r.uniform(-0.03, 0.03))
            for p in (x, y):
                if p in (5, 6):
                    c = light
                elif p in (10, 11):
                    c = dark
            for p in (x, y):
                if p in (0, 15):
                    c = band[1] if p == 0 else band[2]
                elif p in (1, 14):
                    c = band[0]
                if rings and p in rings:
                    c = rings[p]
            img.putpixel((x, y), c)
    if inner:
        for i in range(16):
            for (x, y) in ((i, 8), (8, i)):
                if 2 <= i <= 13:
                    img.putpixel((x, y), inner)
    return img


def pipe_extract():
    orange = {2: hexc("ffb347"), 3: hexc("e8781e"), 12: hexc("ffb347"), 13: hexc("e8781e")}
    return pipe_tile(hexc("7d858c"), hexc("b9c2c9"), hexc("4f565c"), [hexc("5a6168"), hexc("9aa3ab"), hexc("2b2e31")],
                     rings=orange)


# ------------------------------------------------------------------ GUIs
SILHOUETTE = hexc("7a7a7a")
BUCKET_MASK = [
    ".....######.....",
    "...##########...",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "..############..",
    "...##########...",
    "...##########...",
    "....########....",
    ".....######.....",
]


def bucket_silhouette(img, ox, oy):
    """Outline of vanilla's bucket (its alpha mask's edge) plus the rim band, drawn in a slot."""
    m = BUCKET_MASK
    on = lambda x, y: 0 <= y < len(m) and 0 <= x < 16 and m[y][x] == "#"
    for y in range(len(m)):
        for x in range(16):
            if on(x, y) and (not on(x - 1, y) or not on(x + 1, y) or not on(x, y - 1) or not on(x, y + 1) or y == 2):
                img.putpixel((ox + x, oy + y + 1), SILHOUETTE)


def fluid_gauge(img, d, x, y, w, h):
    bevel(d, x, y, w, h, SLOT_DARK, WHITE, hexc("1a1a1a"))
    for frac in (0.25, 0.5, 0.75):
        ty = y + h - 1 - round((h - 2) * frac)
        d.line((x + 1, ty, x + 3, ty), fill=hexc("4a4a4a"))
        d.line((x + w - 4, ty, x + w - 2, ty), fill=hexc("4a4a4a"))


def gui_alloy_furnace():
    img, d = machine_gui()
    slot(d, 38, 35)
    slot(d, 56, 35)
    slot(d, 116, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 79, 34)
    return img


def gui_arc_furnace():
    img, d = machine_gui()
    slot(d, 38, 26)
    slot(d, 56, 26)
    slot(d, 17, 35)
    slot(d, 116, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 79, 34)
    # Faint electrode silhouette in the electrode slot (left of the inputs, clear of the readout line).
    for y in range(36, 50):
        for x in (24, 25):
            if y <= 39 and y % 2 == 0:
                continue
            img.putpixel((x, y), SILHOUETTE)
    return img


def gui_single_machine():
    img, d = machine_gui()
    slot(d, 56, 35)
    slot(d, 116, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 79, 34)
    return img


def gui_ore_washer():
    img, d = machine_gui()
    slot(d, 44, 35)
    slot(d, 104, 35)
    slot(d, 130, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 70, 34)
    fluid_gauge(img, d, 8, 16, 16, 54)
    return img


def gui_fluid_tank():
    img, d = machine_gui()
    fluid_gauge(img, d, 62, 16, 52, 54)
    slot(d, 26, 20)
    slot(d, 26, 52)
    bucket_silhouette(img, 26, 20)
    bucket_silhouette(img, 26, 52)
    return img


def gui_electric_pump():
    img, d = machine_gui()
    fluid_gauge(img, d, 80, 16, 16, 54)
    return img


def phase_b():
    for m, spec in METALS.items():
        ing = ingot_ramp(m)
        save(recolor(INGOT_MAP, ing), f"item/{m}_ingot.png")
        save(recolor(spec["nugget"], ramp_at(spec["stops"], NUGGET_T)), f"item/{m}_nugget.png")
        save(recolor(DUST_MAP, ramp_at(spec["stops"], DUST_T)), f"item/{m}_dust.png")
        save(recolor(PLATE_MAP, ing), f"item/{m}_plate.png")
        save(metal_block_vanilla(spec["stops"], spec["block"]), f"block/{m}_block.png")
    for m, ramp in [("iron", IRON_INGOT), ("copper", COPPER_INGOT), ("gold", GOLD_INGOT),
                    ("zinc", ZINC_INGOT), ("brass", BRASS_INGOT)]:
        save(recolor(PLATE_MAP, ramp), f"item/{m}_plate.png")

    for kind in ORES:
        save(vanilla_ore(kind), f"block/{kind}_ore.png")
        save(vanilla_ore(kind, deep=True), f"block/deepslate_{kind}_ore.png")
    for kind, layout in [("tin", "iron"), ("lead", "iron"), ("silver", "gold"), ("bauxite", "gold")]:
        st = RAW_STOPS[kind]
        name = "bauxite" if kind == "bauxite" else f"raw_{kind}"
        save(recolor(RAW_MAP, ramp_at(st, RAW_T)), f"item/{name}.png")
        save(raw_block_vanilla(st, layout), f"block/{name}_block.png")

    save(recolor(INGOT_MAP, GRAPHITE_INGOT), "item/graphite.png")
    save(graphite_electrode(), "item/graphite_electrode.png")
    save(silicon(), "item/silicon.png")
    save(mica(), "item/mica.png")
    save(recolor(SLIME_MAP, LATEX_RAMP), "item/latex.png")
    save(rubber_item(), "item/rubber.png")
    save(tree_tap(), "item/tree_tap.png")
    save(advanced_circuit(), "item/advanced_circuit.png")
    save(gear(STEEL_GEAR_P, 62), "item/steel_gear.png")
    save(heating_coil(), "item/heating_coil.png")
    save(wire_spool(COPPER_STOPS), "item/fine_copper_wire.png")
    save(wire_spool(METALS["silver"]["stops"]), "item/fine_silver_wire.png")
    save(wire_spool(METALS["aluminum"]["stops"]), "item/fine_aluminum_wire.png")

    save(rubber_log(), "block/rubber_log.png")
    save(rubber_log_top(), "block/rubber_log_top.png")
    save(rubber_log(tapped=True), "block/rubber_log_tapped.png")
    save(rubber_leaves(), "block/rubber_leaves.png")
    save(rubber_sapling(), "block/rubber_sapling.png")
    save(rubber_planks(), "block/rubber_planks.png")

    save(alloy_furnace_front(), "block/alloy_furnace_front.png")
    save(alloy_furnace_front_on(), "block/alloy_furnace_front_on.png")
    write_mcmeta("block/alloy_furnace_front_on.png", 3)
    save(alloy_furnace_side(), "block/alloy_furnace_side.png")
    save(alloy_furnace_top(), "block/alloy_furnace_top.png")
    save(arc_furnace_front(), "block/arc_furnace_front.png")
    save(arc_furnace_front_on(), "block/arc_furnace_front_on.png")
    write_mcmeta("block/arc_furnace_front_on.png", 2)
    save(arc_furnace_side(), "block/arc_furnace_side.png")
    save(arc_furnace_top(), "block/arc_furnace_top.png")
    save(wire_drawer_front(0), "block/wire_drawer_front.png")
    save(wire_drawer_front_on(), "block/wire_drawer_front_on.png")
    write_mcmeta("block/wire_drawer_front_on.png", 4)
    save(wire_drawer_side(), "block/wire_drawer_side.png")
    save(wire_drawer_top(), "block/wire_drawer_top.png")
    save(rolling_mill_front(0), "block/rolling_mill_front.png")
    save(rolling_mill_front_on(), "block/rolling_mill_front_on.png")
    write_mcmeta("block/rolling_mill_front_on.png", 3)
    save(rolling_mill_side(), "block/rolling_mill_side.png")
    save(rolling_mill_top(), "block/rolling_mill_top.png")
    save(ore_washer_front(), "block/ore_washer_front.png")
    save(ore_washer_front_on(), "block/ore_washer_front_on.png")
    write_mcmeta("block/ore_washer_front_on.png", 5)
    save(ore_washer_side(), "block/ore_washer_side.png")
    save(ore_washer_top(), "block/ore_washer_top.png")
    save(electric_pump_front(), "block/electric_pump_front.png")
    save(electric_pump_side(), "block/electric_pump_side.png")
    save(electric_pump_top(), "block/electric_pump_top.png")
    save(electric_pump_bottom(), "block/electric_pump_bottom.png")

    save(fluid_tank(), "block/fluid_tank_side.png")
    save(fluid_tank(top=True), "block/fluid_tank_top.png")
    save(pipe_extract(), "block/pipe_extract.png")

    save(gui_alloy_furnace(), "gui/alloy_furnace.png")
    save(gui_arc_furnace(), "gui/arc_furnace.png")
    save(gui_single_machine(), "gui/single_machine.png")
    save(gui_ore_washer(), "gui/ore_washer.png")
    save(gui_fluid_tank(), "gui/fluid_tank.png")
    save(gui_electric_pump(), "gui/electric_pump.png")

# ================================================================== phases C-F: displays, arc weapons, ender energy, grid
# Zinc ore now uses the vanilla-based ore pipeline: the vanilla coal_ore / deepslate_coal_ore cluster
# layout (letters = vanilla coal roles by brightness; upper case = coal's olive-tinted pair).
ORE_MASKS["coal"] = ([
    "................",
    "......000....0..",
    ".....0aAc0..bc..",
    ".022.bAcBba.....",
    ".ac...0baa022...",
    "..4.2.444..00...",
    ".4.202.23.AbA0..",
    ".020aAb0..0AB.44",
    ".23aABcb32.222..",
    ".420bc022443300.",
    "..42003232.20aA1",
    "..00203.2.002222",
    ".0aBcaa440BAa..1",
    ".20Aa02..0ca0..3",
    "...2..cb..0.33..",
    "..222.4.........",
], [
    "................",
    "......000.......",
    "......acc...bc..",
    ".....bccAba.....",
    ".ac..33baa4.....",
    "..3....344.00...",
    "....0.0...cbc...",
    "....acb0...cA4..",
    "...acAcb...442..",
    "...3bc44........",
    "....44......0ac.",
    "..100033..00.3..",
    "..aAcaa...Aca...",
    "..3ca44...ca4...",
    "...44.cb.344....",
    "................",
])
# Zinc: cool mid blue-grey clusters (darker and bluer than tin's near-white, greyer than silver).
ZINC_ORE_PAL = {"a": "2e3f52", "b": "405a73", "c": "557591", "A": "7c9cb8", "B": "a9c6dc"}
ZINC_DEEP_ORE_PAL = {"a": "4a5d6e", "b": "5f778b", "c": "7a93a8", "A": "a9bfcd"}


def zinc_ore_vanilla(deep=False):
    """Same construction as vanilla_ore(), with its own palette for the deepslate variant (vanilla
    coal's deepslate clusters are darker, so zinc is lifted there to stay readable)."""
    mask = ORE_MASKS["coal"][1 if deep else 0]
    pal = ZINC_DEEP_ORE_PAL if deep else ZINC_ORE_PAL
    greys = DEEP_GREYS if deep else STONE_GREYS
    base = DEEPSLATE_GRID if deep else STONE_GRID
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ch = mask[y][x]
            h = greys[int(base[y][x])] if ch == "." else greys[int(ch)] if ch.isdigit() else pal[ch]
            img.putpixel((x, y), hexc(h))
    return img


# ------------------------------------------------------------------ SCADA display
def scada_display_front():
    """Switched-off glass screen: near-black, a faint green glare streak, 1px steel bezel."""
    r = random.Random(100)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc("0b100d"), 1 + r.uniform(-0.08, 0.08)))
    for x, y in [(2, 6), (3, 5), (4, 4), (5, 3), (6, 2), (3, 6), (4, 5), (5, 4), (6, 3), (7, 2),
                 (9, 13), (10, 12), (11, 11), (12, 10)]:
        img.putpixel((x, y), hexc("16241b"))
    for x, y in [(4, 4), (5, 3)]:
        img.putpixel((x, y), hexc("1d3024"))
    for i in range(16):
        img.putpixel((i, 0), STEEL_LIGHT)
        img.putpixel((0, i), STEEL_LIGHT)
        img.putpixel((i, 15), STEEL_DARK)
        img.putpixel((15, i), STEEL_DARK)
    return img


def scada_display_side():
    img = steel_plate(101)
    d = ImageDraw.Draw(img)
    for y in range(3, 13, 3):
        d.line((3, y, 12, y), fill=hexc("202326"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    for y in range(1, 15):
        img.putpixel((14, y), STEEL_DARK)
    rivets(img, [(1, 1), (1, 13)])
    return img


# ------------------------------------------------------------------ arc weapons
def _rim(img, factor=0.55, keep=()):
    """Vanilla convention: darken the bottom/right silhouette edge instead of a black outline."""
    src = img.copy()
    on = lambda x, y: 0 <= x < 16 and 0 <= y < 16 and src.getpixel((x, y))[3] > 0
    for y in range(16):
        for x in range(16):
            c = src.getpixel((x, y))
            if c[3] and c not in keep and (not on(x + 1, y) or not on(x, y + 1)):
                img.putpixel((x, y), shade(c, factor))
    return img


def arc_gun():
    """Tesla arc gun: ceramic barrel wrapped in copper coils, brass grip, glowing cyan tip."""
    import math
    img = blank()
    cer = [hexc("f2eee4"), hexc("d0c8b8"), hexc("a39b8c")]
    cop = [COPPER_P["1"], COPPER_P["2"], COPPER_P["3"], COPPER_P["4"]]
    brass = [BRASS_P["1"], BRASS_P["2"], BRASS_P["3"], BRASS_P["4"]]
    steel = [hexc("b9c2c9"), hexc("7d858c"), hexc("4a5056"), hexc("2b2e31")]
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 7.4, y + 0.5 - 8.6
            u = (dx - dy) / math.sqrt(2)
            v = (dx + dy) / math.sqrt(2)
            c = None
            k = lambda half: (v + half) / (2 * half)
            if 6.0 < u <= 8.6 and abs(v) < 1.0:
                c = hexc("e8ffff") if u > 7.2 and abs(v) < 0.6 else hexc("4fe6ff")
            elif 0.0 < u <= 6.0 and abs(v) < 2.5 and (u * 0.75) % 1.0 < 0.5:
                t = k(2.5)
                c = cop[0] if t < 0.25 else cop[1] if t < 0.55 else cop[2] if t < 0.8 else cop[3]
            elif 0.0 < u <= 6.0 and abs(v) < 1.3:
                c = cer[0] if v < -0.4 else cer[1] if v < 0.5 else cer[2]
            elif -4.4 < u <= 0.0 and -2.0 < v < 2.2:
                t = k(2.1)
                c = steel[0] if t < 0.3 else steel[1] if t < 0.7 else steel[2]
                if abs(u + 2.0) < 0.5:
                    c = brass[1] if t < 0.5 else brass[2]
            elif -4.4 < u < -1.4 and 2.2 <= v < 6.2:
                c = brass[0] if u < -3.6 else brass[1] if u < -2.4 else brass[2]
                if int(v * 1.4) % 2 and -3.6 <= u:
                    c = brass[3]
            elif -6.0 < u <= -4.4 and abs(v) < 1.4:
                c = cop[1] if v < 0 else cop[3]
            if c:
                img.putpixel((x, y), c)
    _rim(img, keep=(hexc("e8ffff"), hexc("4fe6ff")))
    # Glow halo around the tip.
    for x, y in [(14, 1), (15, 1), (14, 0), (13, 0), (15, 2)]:
        if not img.getpixel((x, y))[3]:
            img.putpixel((x, y), hexc("a6f5ff", 150))
    return img


def arc_saber_hilt():
    """Saber hilt only, from the bottom-left corner toward the centre (the blade is a 3D element)."""
    import math
    img = blank()
    steel = [hexc("d8dee2"), hexc("a8b1b8"), hexc("7d858c"), hexc("4a5056"), hexc("2b2e31")]
    brass = [BRASS_P["1"], BRASS_P["2"], BRASS_P["3"], BRASS_P["5"]]
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            u = (dx - dy) / math.sqrt(2)   # toward the upper right (blade end)
            v = (dx + dy) / math.sqrt(2)
            c = None
            half = 1.75
            if -9.8 < u <= -8.6 and abs(v) < 1.9:
                c = steel[3] if v > 0 else steel[2]                  # pommel
            elif -8.6 < u <= -3.6 and abs(v) < half:
                t = (v + half) / (2 * half)
                c = steel[0] if t < 0.3 else steel[1] if t < 0.65 else steel[2]
                if (u * 1.4) % 1.0 < 0.45:
                    c = steel[3] if t > 0.3 else steel[2]           # grip ridges
            elif -3.6 < u <= -1.6 and abs(v) < half:
                t = (v + half) / (2 * half)
                c = steel[1] if t < 0.5 else steel[2]
            elif -1.6 < u <= 0.6 and abs(v) < 2.3:
                t = (v + 2.3) / 4.6
                c = brass[0] if t < 0.3 else brass[1] if t < 0.65 else brass[2]   # emitter shroud
                if u > 0.0:
                    c = hexc("1b1d1f") if abs(v) < 1.0 else brass[3]          # emitter mouth
            if c:
                img.putpixel((x, y), c)
    _rim(img)
    # Activation buttons on the lit side of the body.
    img.putpixel((5, 9), hexc("ff3b30"))
    img.putpixel((4, 10), hexc("3fdc4a"))
    return img


def _grey(v, a=255):
    v = max(0, min(255, int(v)))
    return (v, v, v, a)


def saber_blade_solid():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = abs(x + 0.5 - 8) / 8
            img.putpixel((x, y), _grey(255 - 75 * d ** 1.6))
    return img


def saber_blade_plasma():
    import math
    sheet = Image.new("RGBA", (16, 64))
    for f in range(4):
        for y in range(16):
            for x in range(16):
                d = abs(x + 0.5 - 8) / 8
                ph = 2 * math.pi * (y / 16 + f / 4)
                w = math.sin(ph * 2 + x * 0.7) * 0.5 + math.sin(ph - x * 0.45 + 1.3) * 0.5
                v = 255 - 70 * d ** 1.4 - (24 + 30 * d) * (0.5 + 0.5 * w)
                sheet.putpixel((x, f * 16 + y), _grey(v))
    return sheet


def saber_blade_arc():
    sheet = Image.new("RGBA", (16, 64))
    for f in range(4):
        r = random.Random(110 + f)
        for y in range(16):
            for x in range(16):
                d = abs(x + 0.5 - 8) / 8
                sheet.putpixel((x, f * 16 + y), _grey(200 - 40 * d + r.uniform(-6, 6)))
        for _ in range(2):
            x = r.randint(3, 12)
            for y in range(16):
                x = max(1, min(14, x + r.choice((-1, 0, 0, 1))))
                sheet.putpixel((x, f * 16 + y), _grey(255))
                c = sheet.getpixel((x + 1, f * 16 + y))
                sheet.putpixel((x + 1, f * 16 + y), _grey(max(c[0], 222)))
    return sheet


def saber_blade_rainbow():
    import colorsys
    sheet = Image.new("RGBA", (16, 128))
    for f in range(8):
        for y in range(16):
            for x in range(16):
                d = abs(x + 0.5 - 8) / 8
                h = (f / 8 + y / 32) % 1.0
                s = 0.25 if d < 0.15 else 0.9 if d < 0.6 else 0.7
                v = 1.0 if d < 0.6 else 0.85 - 0.2 * (d - 0.6)
                rr, gg, bb = colorsys.hsv_to_rgb(h, s, v)
                sheet.putpixel((x, f * 16 + y), (int(rr * 255), int(gg * 255), int(bb * 255), 255))
    return sheet


def write_mcmeta_interp(path, frametime):
    full = os.path.join(ROOT, path + ".mcmeta")
    with open(full, "w") as f:
        f.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": true\n  }\n}\n' % frametime)


# Drill modules: dark steel cartridges (distinct from the green upgrade cards) with gold contacts,
# a coloured LED strip under a recessed emblem window.
MODULE = [
    "....g.g.g.g.....",
    "...gGgGgGgG.....",
    "..1111111111....",
    ".122222222223...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".122........3...",
    ".12LLLLLLLLLD3..",
    ".1hhhhhhhhhhD3..",
    "..333333333333..",
    "................",
]
MODULE_EMBLEMS = {
    "area": ["##.##.##", "##.##.##", "........", "##.##.##", "##.##.##", "........", "##.##.##", "##.##.##"],
    "speed": ["#...#...", ".#...#..", "..#...#.", "...#...#", "..#...#.", ".#...#..", "#...#...", "........"],
    "silk": ["....###.", "...#####", "..####+#", ".####+#.", "..#+##..", ".+......", "+.......", "........"],
    "fortune": ["........", ".######.", "#++#####", "#+######", ".######.", "..####..", "...##...", "........"],
    "magnet": [".######.", "########", "##....##", "##....##", "##....##", "++....++", "++....++", "........"],
    "battery": ["...++...", ".######.", ".#....#.", ".#++++#.", ".#++++#.", ".#++++#.", ".######.", "........"],
}
MODULE_COLORS = {
    "area": ("ff9a2a", "ffd08a"), "speed": ("ff4a3a", "ff9a8a"), "silk": ("f4f4f4", "b9c2c9"),
    "fortune": ("3fdc6a", "b0ffc8"), "magnet": ("e83a2a", "d8dee2"), "battery": ("b06aff", "e0c0ff"),
}


def drill_module(kind):
    return module_sprite(MODULE_EMBLEMS[kind], *(hexc(h) for h in MODULE_COLORS[kind]))


def module_sprite(emblem, main, accent):
    """Dark steel module cartridge: gold contacts, recessed 8x8 emblem window, LED strip in `main`."""
    pal = {"g": BRASS_P["2"], "G": BRASS_P["4"], "1": hexc("5a6168"), "2": hexc("3b4146"),
           "3": hexc("1b1d20"), "D": hexc("2b2f33"), "h": hexc("2b2f33"), "L": main}
    img = pixmap(blank(), MODULE, pal)
    for y in range(4, 12):
        for x in range(4, 12):
            img.putpixel((x, y), hexc("15171a"))
    for y, row in enumerate(emblem):
        for x, ch in enumerate(row):
            if ch == "#":
                img.putpixel((4 + x, 4 + y), main)
            elif ch == "+":
                img.putpixel((4 + x, 4 + y), accent)
    img.putpixel((3, 12), shade(main, 1.3))
    return img


DRILL_SIL = [
    "....######............",
    "...########...........",
    "...##########.........",
    "...################...",
    "...##################.",
    "...################...",
    "...##########.........",
    "...########...........",
    "....######............",
    ".....####.............",
    ".....####.............",
    "....####..............",
    "....####..............",
    "...####...............",
    "...####...............",
]


def gui_drill_modules():
    img = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(img)
    panel(img, d, 176, 166)
    inventory(d)
    for x in (53, 71, 89, 107):
        slot(d, x, 35)
    pixmap(img, DRILL_SIL, {"#": hexc("8b8b8b")}, 14, 26)
    return img


# ------------------------------------------------------------------ ender energy
OBSIDIAN = [hexc("0f0b18"), hexc("1a1229"), hexc("261a3a"), hexc("34244f"), hexc("46326a")]
ENDER_TEAL = [hexc("0d3a30"), hexc("1a7a62"), hexc("2fd6a0"), hexc("8affd8"), hexc("e0fff4")]


def obsidian(seed, w=16, h=16):
    n = value_noise(w, h, seed, 4)
    r = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), ramp_pick(OBSIDIAN, n[y][x] * 0.7 + r.random() * 0.35))
    return img


def ender_eye(img, cx, cy, rad):
    import math
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if dd <= rad:
                t = dd / rad
                c = ENDER_TEAL[3] if t < 0.35 else ENDER_TEAL[2] if t < 0.7 else ENDER_TEAL[1]
                if abs(x + 0.5 - cx) < 0.6 and t < 0.75:
                    c = hexc("0a1a14")          # slit pupil
                img.putpixel((x, y), c)
            elif dd <= rad + 1.0:
                img.putpixel((x, y), ENDER_TEAL[0])


def steel_bands(img, rows):
    for y in rows:
        for x in range(16):
            img.putpixel((x, y), STEEL_LIGHT if y == rows[0] else STEEL_DARK)
    for x in (2, 13):
        img.putpixel((x, rows[0]), RIVET)


def ender_transmitter_top():
    import math
    img = steel_plate(120)
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if dd <= 6.4:
                c = OBSIDIAN[3] if dd > 5.4 else OBSIDIAN[2] if dd > 4.4 else OBSIDIAN[1]
                if dd > 5.4 and (x + 0.5 - 8) + (y + 0.5 - 8) < 0:
                    c = OBSIDIAN[4]
                img.putpixel((x, y), c)
    ender_eye(img, 8, 8, 2.6)
    for x, y in [(8, 2), (2, 8), (13, 8), (8, 13)]:
        img.putpixel((x, y), ENDER_TEAL[2])
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


RUNES = {
    "t": [(3, 5), (4, 5), (4, 6), (4, 7), (11, 5), (11, 6), (12, 6), (11, 7), (7, 8), (8, 8), (7, 9), (8, 10)],
    "r": [(3, 6), (3, 7), (4, 8), (11, 5), (12, 6), (12, 7), (7, 5), (8, 6), (7, 7), (8, 9), (8, 10)],
}


def ender_side(kind):
    img = obsidian(121 if kind == "t" else 125)
    steel_bands(img, (1, 2))
    steel_bands(img, (13, 14))
    for x, y in RUNES[kind]:
        img.putpixel((x, y), ENDER_TEAL[2])
        if y < 12 and not (x, y + 1) in RUNES[kind]:
            img.putpixel((x, y + 1), ENDER_TEAL[0])
    if kind == "r":
        # Receiver: a copper pickup coil band.
        for x in range(16):
            img.putpixel((x, 11), COPPER_P["2"] if x % 2 else COPPER_P["4"])
    return img


def ender_bottom(seed):
    img = obsidian(seed)
    edges(img, (0, 0, 15, 15), STEEL_LIGHT, STEEL_DARK)
    edges(img, (1, 1, 14, 14), STEEL, STEEL_DARK)
    rivets(img, [(2, 2), (12, 2), (2, 12), (12, 12)])
    return img


def ender_receiver_top():
    import math
    img = steel_plate(123)
    # Receiving coil: a copper winding ring around a smaller, dimmer eye.
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 4.0 < dd <= 6.4:
                ring = int(dd * 2) % 2
                c = COPPER_P["2"] if ring else COPPER_P["4"]
                if (x + 0.5 - 8) + (y + 0.5 - 8) < -3:
                    c = COPPER_P["1"] if ring else COPPER_P["3"]
                img.putpixel((x, y), c)
            elif dd <= 4.0:
                img.putpixel((x, y), OBSIDIAN[1])
    ender_eye(img, 8, 8, 1.9)
    rivets(img, [(0, 0), (14, 0), (0, 14), (14, 14)])
    return img


def ender_core():
    """32x32 entity sheet: glowing core cube (4^3 at 0,0) and translucent shell (6^3 at 0,8)."""
    import math
    img = Image.new("RGBA", (32, 32), CLEAR)

    def core(face, fw, fh):
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                a = math.atan2(y + 0.5 - fh / 2, x + 0.5 - fw / 2)
                dd = math.hypot(y + 0.5 - fh / 2, x + 0.5 - fw / 2)
                sw = (a / (2 * math.pi) + dd * 0.35) % 1
                c = ENDER_TEAL[3] if dd < 0.8 else ENDER_TEAL[2] if sw < 0.5 else hexc("8a4ad0")
                t.putpixel((x, y), c)
        return t

    def shell(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("b48ae8", 50))
        for y in range(fh):
            for x in range(fw):
                if x in (0, fw - 1) or y in (0, fh - 1):
                    t.putpixel((x, y), hexc("8a4ad0", 210) if x + y < fw else hexc("5a2a8a", 210))
        t.putpixel((0, 0), hexc("e0c8ff", 230))
        if fw > 2 and fh > 2:
            t.putpixel((1, 1), hexc("ffffff", 120))
        return t

    box_uv(img, 0, 0, 4, 4, 4, core)
    box_uv(img, 0, 8, 6, 6, 6, shell)
    return img


def gui_ender_link():
    img, d = machine_gui()
    bevel(d, 30, 16, 117, 55, SLOT_DARK, WHITE, hexc("141018"))
    return img


# ------------------------------------------------------------------ grid equipment
GREEN_PAINT = [hexc("a5b4a0"), hexc("8a9a85"), hexc("75856f"), hexc("5d6c58"), hexc("435040")]
TINY_FONT = {
    "H": ["#.#", "#.#", "###", "#.#", "#.#"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "L": ["#..", "#..", "#..", "#..", "###"], "I": ["#", "#", "#", "#", "#"], "O": ["###", "#.#", "#.#", "#.#", "###"],
}


def text(img, s, x, y, color):
    for ch in s:
        for yy, row in enumerate(TINY_FONT[ch]):
            for xx, c in enumerate(row):
                if c == "#":
                    img.putpixel((x + xx, y + yy), color)
        x += len(TINY_FONT[ch][0]) + 1


def painted(seed, base, w=16, h=16):
    r = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), shade(base, 1 + r.uniform(-0.03, 0.03)))
    edges(img, (0, 0, w - 1, h - 1), shade(base, 1.2), shade(base, 0.62))
    return img


def transformer_side():
    img = painted(130, GREEN_PAINT[2])
    # Radiator: vertical fins between top and bottom headers.
    for x in range(2, 14):
        for y in range(3, 13):
            img.putpixel((x, y), GREEN_PAINT[1] if x % 2 == 0 else GREEN_PAINT[4])
        img.putpixel((x, 2), GREEN_PAINT[0])
        img.putpixel((x, 13), GREEN_PAINT[3])
    for x in range(2, 14, 2):
        img.putpixel((x, 3), GREEN_PAINT[0])
    rivets(img, [(1, 14), (13, 14)])
    return img


def bushing_top(img, cx, cy):
    for dx in range(-2, 2):
        for dy in range(-2, 2):
            if abs(dx + 0.5) + abs(dy + 0.5) > 3:
                continue
            ring = max(abs(dx + 0.5), abs(dy + 0.5))
            c = hexc("f2f2ea") if ring > 1 and dx + dy < 0 else hexc("b9b9b0") if ring > 1 else hexc("d9d9d0")
            img.putpixel((cx + dx, cy + dy), c)
    img.putpixel((cx - 1, cy - 1), COPPER_P["1"])
    img.putpixel((cx, cy), COPPER_P["3"])
    img.putpixel((cx - 1, cy), COPPER_P["2"])
    img.putpixel((cx, cy - 1), COPPER_P["2"])


def transformer_top():
    img = painted(131, GREEN_PAINT[1])
    for cx in (4, 8, 12):
        bushing_top(img, cx, 8)
    for x in range(2, 14):
        img.putpixel((x, 12), GREEN_PAINT[3])
        img.putpixel((x, 4), GREEN_PAINT[3])
    return img


def transformer_face(hv):
    img = painted(132 if hv else 133, GREEN_PAINT[2])
    sheds = range(2, 10) if hv else range(4, 10)
    for y in sheds:
        wide = (y - sheds[0]) % 2 == 0
        x0, x1 = (5, 10) if wide and hv else (6, 9) if wide or hv else (7, 8)
        for x in range(x0, x1 + 1):
            k = (x - x0) / max(1, x1 - x0)
            img.putpixel((x, y), hexc("f6f6ee") if k < 0.3 else hexc("d6d6cc") if k < 0.75 else hexc("a5a59a"))
    top = sheds[0] - 1
    img.putpixel((7, top), COPPER_P["1"])
    img.putpixel((8, top), COPPER_P["3"])
    stripe = hexc("c8302a") if hv else hexc("2a5ec8")
    for y in range(11, 16):
        for x in range(16):
            img.putpixel((x, y), stripe if y < 15 else shade(stripe, 0.6))
    text(img, "HV" if hv else "LV", 5, 10, hexc("ffffff"))
    return img


def breaker_front(closed):
    img = steel_plate(140)
    d = ImageDraw.Draw(img)
    # Door seam, lamps, lever slot.
    d.rectangle((5, 2, 10, 13), fill=hexc("2b2e31"))
    d.rectangle((7, 3, 8, 12), fill=hexc("0d0e0f"))
    edges(img, (5, 2, 10, 13), STEEL_DARK, STEEL_LIGHT)
    # Lever: pivot in the middle, arm up (closed) or down (open), black handle with a red grip.
    ys = range(3, 8) if closed else range(8, 13)
    for y in ys:
        img.putpixel((7, y), hexc("3a3f44"))
        img.putpixel((8, y), hexc("24282b"))
    grip = (3, 4) if closed else (11, 12)
    for y in grip:
        for x in (6, 7, 8, 9):
            img.putpixel((x, y), hexc("e04a3a") if x < 8 else hexc("9a2a20"))
    img.putpixel((7, 7 if closed else 8), RIVET)
    text(img, "I", 12, 2, hexc("2a2e32"))
    text(img, "O", 11, 9, hexc("2a2e32"))
    green = hexc("5cff6a") if closed else hexc("1f3a24")
    red = hexc("ff3b30") if not closed else hexc("4a1d1b")
    for (x, y), c in (((2, 3), green), ((2, 11), red)):
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=hexc("222222"))
        img.putpixel((x, y), c)
    return img


def breaker_side():
    img = steel_plate(141)
    d = ImageDraw.Draw(img)
    for y in range(3, 10, 2):
        d.line((3, y, 12, y), fill=hexc("202326"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    hazard(img, 1, 14, 12, 14)
    return img


def power_meter_front():
    import math
    img = steel_plate(142)
    d = ImageDraw.Draw(img)
    d.rectangle((1, 1, 14, 9), fill=hexc("2a2a2a"))
    d.rectangle((2, 2, 13, 8), fill=hexc("ece8dc"))
    # Scale arc with a red zone at the right, and the needle.
    seen = set()
    for i in range(40):
        a = math.pi * (1 - i / 39)
        p = (int(round(7.5 + 4.6 * math.cos(a))), int(round(8.4 - 4.6 * math.sin(a))))
        if p not in seen and 2 <= p[1] <= 7:
            seen.add(p)
            img.putpixel(p, hexc("cc2222") if i > 32 else hexc("3a3a3a"))
    for x, y in [(8, 7), (9, 6), (10, 5)]:
        img.putpixel((x, y), hexc("111111"))
    img.putpixel((7, 8), hexc("111111"))
    img.putpixel((8, 8), hexc("111111"))
    # Digital readout.
    d.rectangle((3, 11, 12, 14), fill=hexc("1d2023"))
    d.rectangle((4, 12, 11, 13), fill=hexc("0c2412"))
    for x in (5, 6, 8, 9, 10):
        img.putpixel((x, 12), SCADA_GREEN)
    img.putpixel((7, 13), SCADA_GREEN)
    return img


def power_meter_side():
    img = steel_plate(143)
    # Conduit entering, with a seal sticker.
    for x in range(1, 15):
        for y, c in zip(range(6, 10), [hexc("b9c2c9"), hexc("8a939a"), hexc("6f767d"), hexc("4a5056")]):
            img.putpixel((x, y), c)
    for y in range(5, 11):
        img.putpixel((4, y), STEEL_DARK)
        img.putpixel((11, y), STEEL_DARK)
    img.putpixel((7, 12), hexc("c8302a"))
    img.putpixel((8, 12), hexc("c8302a"))
    rivets(img, [(1, 1), (13, 1), (1, 13), (13, 13)])
    return img


BATT_BLUE = [hexc("8fb3d9"), hexc("5d86b5"), hexc("3d6696"), hexc("2a4a70"), hexc("1a2f4a")]


def battery_bank_side():
    img = dark_steel(150)
    for x0 in (1, 8):
        for y in range(3, 15):
            for i, x in enumerate(range(x0, x0 + 7)):
                c = BATT_BLUE[[0, 1, 1, 2, 2, 3, 4][i]]
                img.putpixel((x, y), c)
        for i, x in enumerate(range(x0, x0 + 7)):
            img.putpixel((x, 3), BATT_BLUE[0])
            img.putpixel((x, 7), hexc("e8efe9") if i < 6 else hexc("b9c2bd"))
        # Terminals: red + and black - posts.
        img.putpixel((x0 + 1, 2), hexc("d03a2f"))
        img.putpixel((x0 + 1, 1), hexc("ff6a5a"))
        img.putpixel((x0 + 5, 2), hexc("222222"))
        img.putpixel((x0 + 5, 1), hexc("4a4a4a"))
    for x in range(16):
        img.putpixel((x, 15), hexc("24282b"))
    return img


def battery_bank_top():
    img = dark_steel(151)
    for ox in (1, 8):
        for oy in (1, 8):
            for y in range(oy, oy + 7):
                for x in range(ox, ox + 7):
                    img.putpixel((x, y), BATT_BLUE[2])
            edges(img, (ox, oy, ox + 6, oy + 6), BATT_BLUE[1], BATT_BLUE[4])
            img.putpixel((ox + 2, oy + 3), hexc("d03a2f"))
            img.putpixel((ox + 4, oy + 3), hexc("222222"))
    # Copper inter-cell links.
    for x in range(3, 13):
        img.putpixel((x, 7), COPPER_P["2"])
    return img


def battery_bank_front(on):
    img = steel_plate(152)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 13, 8), fill=hexc("2b2e31"))
    for i in range(5):
        x = 3 + i * 2
        for y in range(3, 8):
            img.putpixel((x, y), (hexc("5cff6a") if y > 3 else hexc("b8ffc0")) if on else hexc("1f3a24"))
        img.putpixel((x + 1, 3), hexc("17191b"))
    img.putpixel((12, 3), hexc("d03a2f"))
    img.putpixel((12, 7), hexc("222222"))
    for y in (10, 12):
        d.line((3, y, 12, y), fill=hexc("202326"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    return img


def solar_panel_top():
    """2x2 monocrystalline cells with thin silver busbars, dark gaps, and an aluminium frame."""
    r = random.Random(155)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(hexc("26427c"), 1 + r.uniform(-0.05, 0.05))
            if (x + y) % 9 in (0, 1) and x not in (8,) and y not in (8,):
                c = hexc("33569c")              # faint sheen
            if x in (3, 6, 11, 13) and y != 8:
                c = hexc("9aa6b8")              # busbars
            if x == 8 or y == 8:
                c = hexc("0f1a33")
            img.putpixel((x, y), c)
    edges(img, (0, 0, 15, 15), hexc("eef2f4"), hexc("8a939a"))
    return img


def solar_panel_side():
    img = Image.new("RGBA", (16, 16))
    rows = [hexc("eef2f4"), hexc("d4dbe0"), hexc("c3cbd1"), hexc("a8b1b8"), hexc("7c858c"), hexc("c3cbd1"),
            hexc("b0b8be"), hexc("98a1a8")]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rows[y % 8])
    return img


GENSET = [hexc("ffd04a"), hexc("e8b923"), hexc("c99a18"), hexc("9a7410"), hexc("5a440a")]


def genset_plate(seed):
    img = painted(seed, GENSET[1])
    return img


def diesel_front(frame=None):
    img = genset_plate(160)
    d = ImageDraw.Draw(img)
    # Control panel: oil-pressure gauge, run lamp, start button.
    d.rectangle((2, 2, 13, 7), fill=hexc("2b2e31"))
    d.ellipse((3, 3, 7, 7), fill=hexc("1a1a1a"))
    d.ellipse((4, 3, 6, 6), fill=hexc("e8e4d8"))
    needle = (6, 4) if frame == 0 else (6, 5) if frame == 1 else (4, 5)
    img.putpixel((5, 5), hexc("cc2222"))
    img.putpixel(needle, hexc("cc2222"))
    img.putpixel((10, 4), hexc("5cff6a") if frame is not None else hexc("1f3a24"))
    img.putpixel((12, 4), hexc("d03a2f"))
    img.putpixel((12, 5), hexc("8a2a20"))
    # Radiator grille, lit warm from behind when running.
    for y in range(9, 14):
        for x in range(2, 14):
            if y % 2:
                c = hexc("17191b")
                if frame is not None and (x + frame) % 3 == 0:
                    c = hexc("6a2a10")
            else:
                c = GENSET[3]
            img.putpixel((x, y), c)
    edges(img, (2, 8, 13, 14), GENSET[3], GENSET[0])
    return img


def diesel_side():
    img = genset_plate(161)
    for y in range(3, 13, 2):
        for x in range(3, 13):
            img.putpixel((x, y), GENSET[4])
            img.putpixel((x, y + 1), GENSET[0])
    hazard(img, 1, 14, 14, 14)
    return img


def diesel_top():
    img = genset_plate(162)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 13, 7), fill=hexc("17191b"))
    for x in range(3, 13, 2):
        d.line((x, 2, x, 7), fill=GENSET[3])
    d.ellipse((8, 8, 13, 13), fill=hexc("3a3f44"))
    d.ellipse((9, 9, 12, 12), fill=hexc("0d0e0f"))
    img.putpixel((9, 9), hexc("6f767d"))
    img.putpixel((8, 10), hexc("8a939a"))
    rivets(img, [(2, 10), (5, 12)])
    return img


HYDRO_BLUE = [hexc("8fbbe6"), hexc("4f86c0"), hexc("3a6ea5"), hexc("2a5482"), hexc("1d3a5c")]


def hydro_side():
    img = painted(170, HYDRO_BLUE[2])
    d = ImageDraw.Draw(img)
    # Flanged inlet pipe.
    d.ellipse((3, 3, 12, 12), fill=hexc("6f767d"))
    d.ellipse((3, 3, 11, 11), fill=hexc("9aa3ab"))
    d.ellipse((4, 4, 11, 11), fill=hexc("6f767d"))
    d.ellipse((5, 5, 10, 10), fill=hexc("17191b"))
    img.putpixel((6, 6), hexc("2a4a6a"))
    for x, y in [(7, 3), (3, 7), (12, 8), (8, 12)]:
        img.putpixel((x, y), RIVET)
    return img


def hydro_top():
    import math
    img = painted(171, HYDRO_BLUE[2])
    for y in range(16):
        for x in range(16):
            dd = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if dd <= 5.0:
                c = HYDRO_BLUE[1] if (x + y) < 15 else HYDRO_BLUE[3]
                if dd <= 4.0:
                    c = HYDRO_BLUE[2]
                if dd <= 1.6:
                    c = hexc("c3ccd3") if dd < 0.8 else hexc("6f767d")
                img.putpixel((x, y), c)
    for x, y in [(8, 4), (4, 8), (11, 8), (8, 11)]:
        img.putpixel((x, y), RIVET)
    return img


def hydro_front():
    import math
    img = painted(172, HYDRO_BLUE[2])
    # Spiral casing: radius grows with angle, around a dark draft-tube outlet.
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            dd = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi)
            outer = 3.2 + 3.6 * a
            if dd <= 2.6:
                img.putpixel((x, y), hexc("0d1a26") if dd > 1 else hexc("1f3a5a"))
            elif dd <= outer:
                lit = -(dx + dy) / max(dd, 0.01)
                c = HYDRO_BLUE[1] if lit > 0.3 else HYDRO_BLUE[2] if lit > -0.4 else HYDRO_BLUE[3]
                if outer - dd < 0.9:
                    c = HYDRO_BLUE[4]
                img.putpixel((x, y), c)
    return img




CONDENSER = [hexc("4f6fa8"), hexc("3a5590"), hexc("2c4373"), hexc("1f3157"), hexc("141f38")]


def condenser_side():
    img = painted(190, CONDENSER[2])
    for y in range(2, 14):
        for x in range(1, 15):
            img.putpixel((x, y), CONDENSER[1] if y % 2 == 0 else CONDENSER[4])
    for x in range(1, 15):
        img.putpixel((x, 2), CONDENSER[0])
    for y in range(1, 15):
        img.putpixel((4, y), CONDENSER[3])
        img.putpixel((11, y), CONDENSER[3])
    rivets(img, [(1, 14), (13, 14)])
    return img


def condenser_end():
    import math
    img = painted(191, CONDENSER[3])
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            dd = math.hypot(dx, dy)
            if dd <= 7.0:
                lit = -(dx + dy) / max(dd, 0.01)
                c = CONDENSER[0] if lit > 0.4 else CONDENSER[1] if lit > -0.3 else CONDENSER[3]
                if 4.0 < dd <= 4.8:
                    c = CONDENSER[4]
                if dd <= 3.0:
                    c = hexc("8a939a") if lit > 0 else hexc("4a5056")     # bearing housing
                if dd <= 1.6:
                    c = hexc("d8dee2") if dd < 0.8 else hexc("6f767d")    # shaft
                img.putpixel((x, y), c)
    for a in range(6):
        x = int(8 + 5.9 * math.cos(a * math.pi / 3))
        y = int(8 + 5.9 * math.sin(a * math.pi / 3))
        img.putpixel((x, y), RIVET)
    return img


def hv_pylon():
    img = blank()
    g = [hexc("d0d6da"), hexc("a9b0b5"), hexc("8a9298"), hexc("6d757b")]
    for y in range(16):
        for x in (0, 1, 14, 15):
            img.putpixel((x, y), g[[0, 2, 1, 3][(0, 1, 14, 15).index(x)]])
    for i in range(16):
        for x, c in ((i, g[1]), (15 - i, g[2])):
            if 1 < x < 14:
                img.putpixel((x, i), c)
                if 1 < x + 1 < 14:
                    img.putpixel((x + 1, i), g[3] if c == g[1] else g[1])
    for x in range(16):
        img.putpixel((x, 0), g[0])
        img.putpixel((x, 15), g[3])
    for x, y in [(0, 0), (15, 0), (0, 15), (15, 15), (7, 7), (8, 8)]:
        img.putpixel((x, y), hexc("eef2f4"))
    return img



BRONZE_STOPS = ["3a220e", "663d1a", "8f5a26", "b07634", "cc9348", "e3b46b"]


def hydro_runner():
    img = Image.new("RGBA", (32, 32), CLEAR)
    br = [hexc(h) for h in ramp_at(BRONZE_STOPS, [0.1, 0.3, 0.5, 0.7, 0.9])]

    def hub(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), br[2])
        edges(t, (0, 0, fw - 1, fh - 1), br[3], br[0])
        if face in ("north", "south"):
            t.putpixel((fw // 2, fh // 2), br[4])
            t.putpixel((fw // 2 - 1, fh // 2 - 1), br[1])
        return t

    def vane(face, fw, fh):
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                k = x / max(1, fw - 1)
                t.putpixel((x, y), br[4] if k < 0.25 else br[3] if k < 0.55 else br[2] if k < 0.85 else br[1])
        return t

    box_uv(img, 0, 0, 4, 4, 4, hub)
    box_uv(img, 0, 8, 1, 5, 3, vane)
    return img


def condenser_rotor():
    import math
    img = Image.new("RGBA", (32, 32), CLEAR)

    def disc(face, fw, fh):
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                c = hexc("8a939a") if (x + y) % 3 else hexc("7d858c")
                if face in ("north", "south"):
                    dx, dy = x + 0.5 - fw / 2, y + 0.5 - fh / 2
                    dd = math.hypot(dx, dy)
                    c = hexc("9aa3ab") if dd > 3.6 else hexc("8a939a")
                    if dd < 1.4:
                        c = hexc("4a5056")
                    if abs(dx) < 0.6 and dy < -3.0:
                        c = hexc("f2f6f8")          # bright index stripe at the rim
                    for hx, hy in ((2.2, 2.2), (-2.2, 2.2), (2.2, -2.2), (-2.2, -2.2)):
                        if math.hypot(dx - hx, dy - hy) < 0.75:
                            c = hexc("2b2e31")      # balancing holes
                elif x % fw == 0:
                    c = hexc("f2f6f8")
                t.putpixel((x, y), c)
        return face_edges(t, hexc("5a6168"))

    box_uv(img, 0, 0, 10, 10, 2, disc)
    return img


CONDUCTOR_SPOOL = [
    "................",
    "..wW.......wW...",
    ".wWWw.....wWWw..",
    ".WWWW.....WWWd..",
    ".WWW1232123WWd..",
    ".WWW2321232WWd..",
    ".WWW3212321WWd..",
    ".WWW2123212WWd..",
    ".WWW1232123WWd..",
    ".WWW2321232WWd..",
    ".WWW3212321WWd..",
    ".WWW4444444WWd..",
    ".wWWd.....wWdd..",
    "..wdd......dd...",
    "................",
    "................",
]


def conductor_spool():
    al = ramp_at(METALS["aluminum"]["stops"], [0.1, 0.95, 0.6, 0.3, 0.05])
    pal = {"1": hexc(al[1]), "2": hexc(al[2]), "3": hexc(al[3]), "4": hexc(al[4]),
           "w": OAK[1], "W": OAK[3], "d": hexc("4a3518")}
    img = pixmap(blank(), CONDUCTOR_SPOOL, pal)
    for x, y in [(2, 5), (2, 8), (11, 5), (11, 9)]:
        img.putpixel((x, y), OAK[0])
    return img


BOOK_MAP = [  # vanilla book.png role grid: 0-5 cover, 6-9 pages
    "................",
    "........111.....",
    "......113541....",
    "....113555551...",
    "..113555255551..",
    "113455555255541.",
    "1345525555255521",
    "113555255555226.",
    "118355555522787.",
    "0398345522788712",
    ".039832278871200",
    "..039878871200..",
    "...039872200....",
    "....032200......",
    ".....000........",
    "................",
]


def handbook():
    img = recolor(BOOK_MAP, ["0a1a0e", "123020", "1a4029", "225032", "2a5c3a", "336a42",
                             "6b6455", "a8a08a", "c8c0a8", "e6dfc8"])
    for x, y in [(9, 3), (8, 4), (7, 5), (8, 5), (9, 5), (8, 6), (7, 7)]:
        img.putpixel((x, y), COPPER_P["1"] if (x + y) % 2 else COPPER_P["2"])
    return img


def gui_battery_bank():
    img = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(img)
    panel(img, d, 176, 166)
    inventory(d)
    fe_gauge(img, d, 76, 16, 24, 54)
    return img


def gui_diesel_generator():
    img, d = machine_gui()
    fluid_gauge(img, d, 44, 16, 16, 54)
    return img


def gui_power_meter():
    img = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(img)
    panel(img, d, 176, 166)
    inventory(d)
    return img


def phaseC_F():
    save(zinc_ore_vanilla(), "block/zinc_ore.png")
    save(zinc_ore_vanilla(deep=True), "block/deepslate_zinc_ore.png")

    save(scada_display_front(), "block/scada_display_front.png")
    save(scada_display_side(), "block/scada_display_side.png")

    save(arc_gun(), "item/arc_gun.png")
    save(arc_saber_hilt(), "item/arc_saber_hilt.png")
    save(saber_blade_solid(), "block/arc_saber_blade_solid.png")
    save(saber_blade_plasma(), "block/arc_saber_blade_plasma.png")
    write_mcmeta_interp("block/arc_saber_blade_plasma.png", 2)
    save(saber_blade_arc(), "block/arc_saber_blade_arc.png")
    write_mcmeta("block/arc_saber_blade_arc.png", 1)
    save(saber_blade_rainbow(), "block/arc_saber_blade_rainbow.png")
    write_mcmeta_interp("block/arc_saber_blade_rainbow.png", 3)
    for kind in MODULE_EMBLEMS:
        save(drill_module(kind), f"item/drill_{kind}_module.png")
    save(gui_drill_modules(), "gui/drill_modules.png")

    save(ender_transmitter_top(), "block/ender_transmitter_top.png")
    save(ender_side("t"), "block/ender_transmitter_side.png")
    save(ender_bottom(122), "block/ender_transmitter_bottom.png")
    save(ender_receiver_top(), "block/ender_receiver_top.png")
    save(ender_side("r"), "block/ender_receiver_side.png")
    save(ender_bottom(126), "block/ender_receiver_bottom.png")
    save(ender_core(), "entity/ender_core.png")
    save(gui_ender_link(), "gui/ender_link.png")

    save(transformer_side(), "block/transformer_side.png")
    save(transformer_top(), "block/transformer_top.png")
    save(transformer_face(True), "block/transformer_primary.png")
    save(transformer_face(False), "block/transformer_secondary.png")
    save(breaker_front(True), "block/circuit_breaker_front_closed.png")
    save(breaker_front(False), "block/circuit_breaker_front_open.png")
    save(breaker_side(), "block/circuit_breaker_side.png")
    save(power_meter_front(), "block/power_meter_front.png")
    save(power_meter_side(), "block/power_meter_side.png")
    save(battery_bank_side(), "block/battery_bank_side.png")
    save(battery_bank_top(), "block/battery_bank_top.png")
    save(battery_bank_front(False), "block/battery_bank_front.png")
    save(battery_bank_front(True), "block/battery_bank_front_on.png")
    save(solar_panel_top(), "block/solar_panel_top.png")
    save(solar_panel_side(), "block/solar_panel_side.png")
    save(diesel_front(), "block/diesel_generator_front.png")
    save(anim([diesel_front(0), diesel_front(1)]), "block/diesel_generator_front_on.png")
    write_mcmeta("block/diesel_generator_front_on.png", 4)
    save(diesel_side(), "block/diesel_generator_side.png")
    save(diesel_top(), "block/diesel_generator_top.png")
    save(hydro_side(), "block/hydro_turbine_side.png")
    save(hydro_top(), "block/hydro_turbine_top.png")
    save(hydro_front(), "block/hydro_turbine_front.png")
    save(condenser_side(), "block/synchronous_condenser_side.png")
    save(condenser_end(), "block/synchronous_condenser_end.png")
    save(hv_pylon(), "block/hv_pylon.png")
    save(wind_turbine_nacelle(), "entity/wind_turbine.png")
    save(wind_tower(), "block/wind_turbine_tower.png")
    save(wind_foundation(), "block/wind_turbine_foundation.png")
    save(wind_door(), "block/wind_turbine_door.png")
    save(wind_bearing(), "block/wind_turbine_bearing.png")
    save(wind_turbine_icon(), "item/wind_turbine.png")
    save(crusher_jaws(), "entity/crusher_jaws.png")
    save(crusher_inner(), "block/crusher_inner.png")
    save(hydro_runner(), "entity/hydro_runner.png")
    save(condenser_rotor(), "entity/condenser_rotor.png")
    save(conductor_spool(), "item/conductor_spool.png")
    save(handbook(), "item/powerfactor_handbook.png")
    save(gui_battery_bank(), "gui/battery_bank.png")
    save(gui_diesel_generator(), "gui/diesel_generator.png")
    save(gui_power_meter(), "gui/power_meter.png")

    save(electric_chainsaw(), "item/electric_chainsaw.png")
    save(electric_chainsaw_running(), "item/electric_chainsaw_running.png")
    write_mcmeta("item/electric_chainsaw_running.png", 1)
    save(electric_jetpack(), "item/electric_jetpack.png")
    save(clamp_meter(), "item/clamp_meter.png")
    save(jetpack_entity(), "entity/jetpack.png")
    save(tesla_coil_base(), "block/tesla_coil_base.png")
    save(tesla_coil_primary(), "block/tesla_coil_primary.png")
    save(tesla_coil_secondary(), "block/tesla_coil_secondary.png")
    save(tesla_coil_toroid(), "block/tesla_coil_toroid.png")
    save(grow_lamp_housing(), "block/grow_lamp_housing.png")
    save(grow_lamp_led(), "block/grow_lamp_led.png")
    save(grow_lamp_led(on=True), "block/grow_lamp_led_on.png")
    save(charging_pad_side(), "block/charging_pad_side.png")
    save(charging_pad_top(), "block/charging_pad_top.png")
    save(anim([charging_pad_top(f) for f in range(8)]), "block/charging_pad_top_on.png")
    write_mcmeta("block/charging_pad_top_on.png", 2)
    save(electrolyzer_front(), "block/electrolyzer_front.png")
    save(electrolyzer_front_on(), "block/electrolyzer_front_on.png")
    write_mcmeta("block/electrolyzer_front_on.png", 2)
    save(electrolyzer_side(), "block/electrolyzer_side.png")
    save(electrolyzer_top(), "block/electrolyzer_top.png")
    save(fuel_cell_front(), "block/fuel_cell_front.png")
    save(fuel_cell_front(on=True), "block/fuel_cell_front_on.png")
    save(fuel_cell_side(), "block/fuel_cell_side.png")
    save(fuel_cell_top(), "block/fuel_cell_top.png")
    save(hydrogen_still(), "block/hydrogen_still.png")
    write_mcmeta_interp("block/hydrogen_still.png", 3)
    save(hydrogen_flow(), "block/hydrogen_flow.png")
    write_mcmeta("block/hydrogen_flow.png", 2)
    save(gui_electrolyzer(), "gui/electrolyzer.png")
    save(gui_fuel_cell(), "gui/fuel_cell.png")
    phase_g()
    phase_h()

    phase_drill_big()

def crusher_jaws():
    """Jaw plate (9x4x2) at (0,0) and a tooth (1x4x1) at (0,8): manganese steel, worn bright on the crushing faces."""
    img = Image.new("RGBA", (32, 32), CLEAR)

    def plate(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("3a3f44"))
        for y in range(fh):
            for x in range(fw):
                n = ((x * 7 + y * 13) % 5) * 3
                t.putpixel((x, y), tuple(min(255, c + n) for c in hexc("3a3f44")[:3]) + (255,))
        if face == "south":   # crushing face
            for x in range(fw):
                t.putpixel((x, 0), hexc("8a9399"))
        face_edges(t, hexc("24282c"))
        return t

    def tooth(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("9aa3a9"))
        for y in range(fh):
            for x in range(fw):
                if (x + y) % 3 == 0:
                    t.putpixel((x, y), hexc("c4ccd1"))
        if face in ("top", "bottom"):
            t = Image.new("RGBA", (fw, fh), hexc("6c757b"))
        return t

    box_uv(img, 0, 0, 9, 4, 2, plate)
    box_uv(img, 0, 8, 1, 4, 1, tooth)
    return img


def crusher_inner():
    """Walls and floor of the crusher's feed hopper: dark scuffed steel with grit."""
    img = Image.new("RGBA", (16, 16), hexc("2c3034"))
    import random
    r = random.Random(7)
    for y in range(16):
        for x in range(16):
            v = r.randint(-6, 6)
            base = hexc("2c3034")
            img.putpixel((x, y), tuple(max(0, min(255, c + v)) for c in base[:3]) + (255,))
    for _ in range(14):
        img.putpixel((r.randrange(16), r.randrange(16)), hexc("6b5a4a"))
    for x in range(16):
        img.putpixel((x, 15), hexc("1d2023"))
    return img


# ---------------------------------------------------------------- utility wind turbine

GRP = hexc("eef1f1")          # nacelle glass-fibre skin
GRP_SEAM = hexc("c8cfd2")
GRP_SHADE = hexc("dde2e4")
WIND_TEAL = hexc("1f8a6a")


def wind_turbine_nacelle():
    """Nacelle (20x18x44 box at 0,0), bearing housing, roof cooler, masts, wind vane, cup anemometer,
    and a pure white patch at (120,120) that the generated blades and spinner tint by vertex colour."""
    img = Image.new("RGBA", (128, 128), CLEAR)
    r = random.Random(4401)

    def skin(fw, fh, seed):
        rr = random.Random(seed)
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                t.putpixel((x, y), shade(GRP, 1 + rr.uniform(-0.012, 0.012)))
        return t

    def body(face, fw, fh):
        t = skin(fw, fh, sum(map(ord, face)))
        d = ImageDraw.Draw(t)
        if face in ("east", "west"):                       # long sides, 44 x 18
            for x in (11, 22, 33):
                d.line((x, 0, x, fh - 1), fill=GRP_SEAM)
            d.line((0, 4, fw - 1, 4), fill=GRP_SEAM)
            d.rectangle((0, 12, fw - 1, 13), fill=WIND_TEAL)  # brand stripe
            d.line((0, 14, fw - 1, 14), fill=shade(WIND_TEAL, 0.7))
            for x0 in (3, 36):                              # cooling louvres toward each end
                for y in range(6, 11):
                    d.line((x0, y, x0 + 5, y), fill=hexc("9aa3a9") if y % 2 == 0 else hexc("5c646a"))
            d.line((0, fh - 1, fw - 1, fh - 1), fill=GRP_SHADE)
        elif face == "top":                                 # roof, 20 x 44
            d.rectangle((0, 0, fw - 1, fh - 1), outline=GRP_SEAM)
            for y in range(2, fh - 2, 3):                   # grip strip walkway
                d.line((8, y, 11, y), fill=hexc("b3bbbf"))
            d.rectangle((3, 18, 16, 25), outline=hexc("a9b2b7"))   # service hatch
            d.line((4, 19, 15, 19), fill=hexc("ffffff"))
            for y in range(1, fh - 1, 4):                   # handrail stanchions
                t.putpixel((1, y), hexc("8f989e"))
                t.putpixel((fw - 2, y), hexc("8f989e"))
        elif face == "bottom":
            t = skin(fw, fh, 77)
            d = ImageDraw.Draw(t)
            for y in range(fh):
                for x in range(fw):
                    t.putpixel((x, y), shade(t.getpixel((x, y)), 0.86))
            d.ellipse((4, 14, 15, 25), fill=hexc("3c4247"), outline=hexc("6d757b"))
        elif face == "south":                              # rear: louvred grille and door
            d.rectangle((0, 0, fw - 1, fh - 1), outline=GRP_SEAM)
            for y in range(3, 13):
                d.line((3, y, 11, y), fill=hexc("8d969c") if y % 2 else hexc("4a5157"))
            d.rectangle((13, 4, 17, 15), fill=hexc("d9dfe1"), outline=hexc("a9b2b7"))
            t.putpixel((16, 10), hexc("6d757b"))
        else:                                               # front, behind the bearing housing
            d.rectangle((0, 0, fw - 1, fh - 1), outline=GRP_SEAM)
        return t

    box_uv(img, 0, 0, 20, 18, 44, body)

    def housing(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("d6dcdf"))
        ImageDraw.Draw(t).rectangle((0, 0, fw - 1, fh - 1), outline=hexc("a9b2b7"))
        return t

    box_uv(img, 0, 62, 14, 14, 2, housing)

    def cooler(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("c3cacd"))
        d = ImageDraw.Draw(t)
        if face == "top":
            for y in range(fh):
                d.line((1, y, fw - 2, y), fill=hexc("7e878d") if y % 2 else hexc("b9c1c5"))
        else:
            for x in range(1, fw - 1, 2):
                d.line((x, 0, x, fh - 1), fill=hexc("7e878d"))
        d.rectangle((0, 0, fw - 1, fh - 1), outline=hexc("9aa3a9"))
        return t

    box_uv(img, 32, 62, 14, 4, 10, cooler)
    box_uv(img, 80, 62, 1, 9, 1, lambda f, w, h: Image.new("RGBA", (w, h), hexc("9da6ab")))
    box_uv(img, 84, 62, 1, 3, 6, lambda f, w, h: Image.new("RGBA", (w, h), hexc("e8ecec")))
    box_uv(img, 100, 62, 2, 2, 2, lambda f, w, h: Image.new("RGBA", (w, h), hexc("50585e")))
    box_uv(img, 100, 66, 4, 1, 1, lambda f, w, h: Image.new("RGBA", (w, h), hexc("8a9399")))
    box_uv(img, 110, 62, 2, 2, 2, lambda f, w, h: Image.new("RGBA", (w, h), hexc("2a2e31")))
    ImageDraw.Draw(img).rectangle((120, 120, 127, 127), fill=(255, 255, 255, 255))
    return img


def wind_tower():
    """Tubular tower steel: off-white with a faint weathering streak."""
    r = random.Random(4411)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(hexc("f1f3f3"), 1 + r.uniform(-0.01, 0.01) - (0.025 if x in (5, 11) and y > 8 else 0))
            img.putpixel((x, y), c)
    return img


def wind_foundation():
    """Poured concrete footing."""
    r = random.Random(4412)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc("a7aaa8"), 1 + r.uniform(-0.06, 0.06)))
    for _ in range(10):
        img.putpixel((r.randrange(16), r.randrange(16)), hexc("8d908e"))
    ImageDraw.Draw(img).rectangle((0, 0, 15, 15), outline=hexc("8b8e8c"))
    return img


def wind_door():
    """Steel service door at the foot of the tower: porthole, handle, warning label."""
    img = Image.new("RGBA", (16, 16), hexc("dfe3e4"))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=hexc("8f989e"))
    d.rectangle((5, 2, 10, 5), fill=hexc("4d6f86"), outline=hexc("8f989e"))
    d.rectangle((11, 8, 12, 9), fill=hexc("50585e"))
    d.rectangle((4, 11, 11, 13), fill=hexc("f2c230"))
    for x in range(4, 12, 2):
        img.putpixel((x, 12), hexc("222222"))
    return img


def wind_bearing():
    """Yaw bearing ring under the nacelle: dark steel with a bolt circle."""
    img = Image.new("RGBA", (16, 16), hexc("5a6167"))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc("5a6167"), 1.08 if y < 2 else 0.9 if y > 13 else 1.0))
    for x in range(1, 16, 3):
        img.putpixel((x, 7), hexc("b9c2c9"))
        img.putpixel((x, 8), hexc("3a4045"))
    return img


def wind_turbine_icon():
    """Inventory icon: the turbine seen from upwind."""
    img = blank()
    hub = (7.5, 6.5)
    import math
    for k, ang in enumerate((90, 210, 330)):
        a = math.radians(ang)
        for i in range(2, 26):
            t = i / 4
            x, y = hub[0] + math.cos(a) * t, hub[1] - math.sin(a) * t
            px, py = int(round(x - 0.5)), int(round(y - 0.5))
            if 0 <= px < 16 and 0 <= py < 16:
                img.putpixel((px, py), hexc("d2352b") if t > 5.1 else hexc("f3f5f5") if k == 0 else hexc("e3e8ea"))
    for y in range(8, 15):
        img.putpixel((7, y), hexc("f3f5f5"))
        img.putpixel((8, y), hexc("c4cbcf"))
        if y > 11:
            img.putpixel((6, y), hexc("e7ebec"))
            img.putpixel((9, y), hexc("b3bbbf"))
    for x in range(4, 12):
        img.putpixel((x, 15), hexc("8b8e8c"))
    for x, y, c in ((7, 6, "ffffff"), (8, 6, "d5dbde"), (7, 7, "d5dbde"), (8, 7, "a9b2b7")):
        img.putpixel((x, y), hexc(c))
    # Dark outline so it reads on any background.
    out = img.copy()
    for x in range(16):
        for y in range(16):
            if img.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), hexc("3a4045", 200))
    return out


# ---------------------------------------------------------------- chainsaw, jetpack, clamp meter, tesla coil, grow lamp,
# ---------------------------------------------------------------- charging pad, electrolyzer, fuel cell, hydrogen
BRAND = hexc("1f8a6a")
FE_RED = hexc("ff5a4a")
EE_TEAL = hexc("33e0b0")
H2_CYAN = [hexc("e4fbff"), hexc("bfeaf2"), hexc("8fcbd8"), hexc("5f9aa8")]
SAW_ORANGE = [hexc("ffb35c"), hexc("f08a24"), hexc("c8661a"), hexc("86400f")]
CHAIN = [hexc("eef2f4"), hexc("3a3f44"), hexc("7d858c"), hexc("4a5056")]


CHAINSAW_BODY = [
    "...KKKk.......",
    "..K...KOOOQ...",
    ".KK.OOOOOOPQ..",
    ".K.OOORROPPQ..",
    "KK.OPPRRPPQQ..",
    "K..PPPPPPQQ...",
    "K..QQQQQQQ....",
    "...kkTkkkk....",
]


def electric_chainsaw(frame=0):
    """Battery chainsaw, guide bar pointing up-right; `frame` walks the chain one pixel along the bar."""
    img = blank()
    for y in range(2, 10):                                   # bar: chain | steel | steel | chain
        x0 = 13 - y
        img.putpixel((x0, y), CHAIN[(x0 - y - frame) % 4])
        img.putpixel((x0 + 1, y), hexc("d4dbe0"))
        img.putpixel((x0 + 2, y), hexc("98a1a8"))
        img.putpixel((x0 + 3, y), CHAIN[(x0 + 3 - y + frame) % 4])
    for x, y in [(12, 1), (13, 1), (14, 1)]:                 # rounded nose
        img.putpixel((x, y), CHAIN[(x - y + frame) % 4])
    img.putpixel((9, 5), hexc("5a6066"))                     # bar rivet
    pal = {"K": hexc("26282a"), "k": hexc("3a3d40"), "T": EE_TEAL, "R": FE_RED,
           "O": SAW_ORANGE[0], "P": SAW_ORANGE[1], "Q": SAW_ORANGE[2]}
    pixmap(img, CHAINSAW_BODY, pal, 0, 7)
    return outline(img, hexc("16120e"))


def electric_chainsaw_running():
    return anim([electric_chainsaw(f) for f in range(4)])


def electric_jetpack():
    """Seen from behind: battery pack between two ducted fans, shoulder straps over the top."""
    img = blank()
    d = ImageDraw.Draw(img)
    duct = [hexc("e4e9ec"), hexc("c3cbd1"), hexc("9aa3ab"), hexc("6f777e")]
    for x0 in (1, 11):
        for y in range(2, 13):
            for i, x in enumerate(range(x0, x0 + 4)):
                c = duct[i]
                if y in (5, 9):
                    c = shade(c, 0.78)                      # duct bands
                img.putpixel((x, y), c)
        d.line((x0 + 1, 1, x0 + 2, 1), fill=hexc("3a3f44"))    # intake lip
        img.putpixel((x0 + 1, 2), hexc("2a2e31"))
        img.putpixel((x0 + 2, 2), hexc("2a2e31"))
        d.rectangle((x0, 13, x0 + 3, 13), fill=hexc("3d4247"))  # nozzle
        d.line((x0 + 1, 14, x0 + 2, 14), fill=hexc("23272a"))
    for x in (5, 10):                                          # struts
        img.putpixel((x, 6), hexc("6f777e"))
        img.putpixel((x, 7), hexc("4a5056"))
    d.rectangle((5, 3, 10, 12), fill=hexc("3a3f44"))
    d.line((5, 3, 10, 3), fill=hexc("5a6066"))
    d.line((5, 3, 5, 12), fill=hexc("4d5359"))
    d.line((6, 12, 10, 12), fill=hexc("26292c"))
    d.line((7, 5, 8, 5), fill=FE_RED)
    for y in range(7, 11):                                     # charge light
        img.putpixel((7, y), hexc("bff6ff") if y < 8 else CYAN)
        img.putpixel((8, y), CYAN if y < 8 else hexc("2fb8d8"))
    for x in (6, 9):                                           # strap loops over the top
        img.putpixel((x, 0), hexc("2a2a2a"))
        img.putpixel((x, 1), hexc("1e1e1e"))
        img.putpixel((x, 2), hexc("1e1e1e"))
    d.line((6, 0, 9, 0), fill=hexc("2a2a2a"))
    img.putpixel((7, 1), CLEAR[:3] + (0,))
    img.putpixel((8, 1), CLEAR[:3] + (0,))
    return outline(img, hexc("1a1c1e"))


CLAMP_METER = [
    ".....12..21.....",
    "....12....23....",
    "...12......23...",
    "...12......34...",
    "...22......34...",
    "....23....34....",
    ".....233334.....",
    "....YYYYYYYY....",
    "....YKKKKKKy....",
    "....YKLlLlKyT...",
    "....YKlLlLKyT...",
    "....YKKKKKKy....",
    "....YKDDDDKy....",
    "....YKDwrDKy....",
    "....YKKKKKKy....",
    "....zzzzzzzz....",
]


def clamp_meter():
    """Yellow clamp meter: open copper jaw ring on top, LCD and selector dial on a black face."""
    pal = {"1": COPPER_P["1"], "2": COPPER_P["2"], "3": COPPER_P["3"], "4": COPPER_P["4"],
           "Y": hexc("ffe36a"), "y": hexc("c99a12"), "z": hexc("8a6a0c"), "K": hexc("26282a"),
           "L": hexc("9fc79a"), "l": hexc("3a6a40"), "D": hexc("5a6066"), "w": hexc("f4f4f4"), "r": FE_RED,
           "T": hexc("3a3d40")}
    img = pixmap(blank(), CLAMP_METER, pal)
    return outline(img, hexc("2a1e08"))


def box_uv_f(img, u, v, w, h, d, paint):
    """box_uv for fractional model sizes: Minecraft maps the unwrap with float edges, so each face
    covers floor(start)..ceil(end) texels; sides are pasted after top/bottom."""
    import math
    faces = [
        ("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d),
        ("east", u, v + d, d, h), ("north", u + d, v + d, w, h),
        ("west", u + d + w, v + d, d, h), ("south", u + 2 * d + w, v + d, w, h),
    ]
    for name, x, y, fw, fh in faces:
        x0, y0 = math.floor(x), math.floor(y)
        pw, ph = math.ceil(x + fw) - x0, math.ceil(y + fh) - y0
        if pw and ph:
            img.paste(paint(name, pw, ph), (x0, y0))


def jetpack_entity():
    """64x32 sheet for JetpackLayer's boxes (float sizes, see box_uv_f)."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    solid = lambda c: (lambda f, w, h: Image.new("RGBA", (w, h), c))

    def back_plate(face, fw, fh):
        t = painted(5201, hexc("4a5056"), fw, fh)
        if face == "north" and fw >= 7:
            for x in (1, fw - 2):
                t.putpixel((x, 1), RIVET)
                t.putpixel((x, fh - 2), RIVET)
        return t

    def battery(face, fw, fh):
        t = painted(5202, hexc("2e3236"), fw, fh)
        if face == "south":
            for y in range(1, fh - 1):                        # charge LED strip
                t.putpixel((fw // 2 - 1, y), hexc("bff6ff") if y < 3 else CYAN)
                t.putpixel((fw // 2, y), CYAN if y < 3 else hexc("2fb8d8"))
            t.putpixel((1, 1), FE_RED)
            t.putpixel((fw - 2, 1), FE_RED)
        elif face in ("east", "west"):
            for y in range(1, fh - 1):
                t.putpixel((fw // 2, y), FE_RED if y < 2 else shade(FE_RED, 0.6))
        return t

    def duct(face, fw, fh):
        if face == "top":                                     # intake grille ring, open centre
            t = Image.new("RGBA", (fw, fh), hexc("2a2e31"))
            for x in range(1, fw - 1):
                for y in range(1, fh - 1):
                    t.putpixel((x, y), CLEAR)
            t.putpixel((0, 0), hexc("50585e"))
            return t
        if face == "bottom":
            return Image.new("RGBA", (fw, fh), hexc("2a2e31"))
        t = Image.new("RGBA", (fw, fh), hexc("c3cbd1"))
        for y in range(fh):
            for x in range(fw):
                c = hexc("dfe5e9") if x == 0 else hexc("c3cbd1") if x < fw - 1 else hexc("9aa3ab")
                if y in (0, 3, fh - 3):
                    c = shade(c, 0.72)
                t.putpixel((x, y), c)
        return t

    def nozzle(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("3d4247"))
        if face == "bottom":
            t = Image.new("RGBA", (fw, fh), hexc("15171a"))
            ImageDraw.Draw(t).rectangle((0, 0, fw - 1, fh - 1), outline=hexc("3d4247"))
        elif face != "top":
            ImageDraw.Draw(t).line((0, 0, fw - 1, 0), fill=hexc("5a6066"))
        return t

    def strap(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("1e1f21"))
        if fh >= 6:
            for y in range(1, fh, 2):
                t.putpixel((fw // 2, y), hexc("4a4d50"))      # stitch line
        return t

    def chest(face, fw, fh):
        t = strap(face, fw, fh)
        if face in ("north", "south") and fw >= 5:
            t.putpixel((fw // 2, 0), hexc("d4dbe0"))           # buckle
            t.putpixel((fw // 2, fh - 1), hexc("9aa3ab"))
        return t

    def glow(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("4fd8ff", 200))
        for y in range(fh):
            for x in range(fw):
                if 0 < x < fw - 1 and (0 < y < fh - 1 or face in ("top", "bottom")):
                    t.putpixel((x, y), hexc("bff6ff", 210))
        if face in ("top", "bottom"):
            t.putpixel((fw // 2, fh // 2), hexc("ffffff", 220))
        return t

    box_uv_f(img, 0, 0, 7, 9, 1.5, back_plate)
    box_uv_f(img, 18, 0, 4, 7, 2.5, battery)
    box_uv_f(img, 0, 12, 3.4, 9, 3.4, duct)
    box_uv_f(img, 14, 12, 4, 1.2, 4, nozzle)
    box_uv_f(img, 32, 0, 0.8, 1.5, 1, solid(hexc("7d858c")))
    box_uv_f(img, 36, 0, 1.2, 8, 0.4, strap)
    box_uv_f(img, 40, 0, 1.2, 0.4, 4.6, strap)
    box_uv_f(img, 36, 10, 6.8, 0.8, 0.5, chest)
    box_uv_f(img, 30, 16, 1, 0.6, 1, solid(hexc("d4dbe0")))
    box_uv_f(img, 30, 20, 1.2, 0.3, 0.7, solid(hexc("b9c2c9")))
    box_uv_f(img, 48, 16, 3, 2.5, 3, glow)
    return img


def tesla_coil_base():
    """Dark steel plinth with corner bolts and a copper ground strap."""
    img = dark_steel(5301)
    edges(img, (0, 0, 15, 15), hexc("5a6167"), hexc("202427"))
    edges(img, (2, 2, 13, 13), hexc("2a2e31"), hexc("4d545a"))
    for x, y in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        img.putpixel((x, y), RIVET)
        img.putpixel((x + 1, y + 1), hexc("1e2124"))
    for x in range(4, 12):
        img.putpixel((x, 12), COPPER_P["2"] if x % 3 else COPPER_P["1"])
        img.putpixel((x, 13), COPPER_P["4"])
    return img


def tesla_coil_primary():
    """Flat copper spiral (seen from above) that also reads as tubing on the element edges."""
    import math
    img = Image.new("RGBA", (16, 16), hexc("23272a"))
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            s = (r / 2.5 - a) % 1.0                           # Archimedean spiral, 2.5px pitch
            if r < 7.8 and r > 1.4 and s < 0.62:
                c = COPPER_P["1"] if s < 0.18 else COPPER_P["2"] if s < 0.42 else COPPER_P["4"]
                img.putpixel((x, y), c)
            elif r <= 1.4:
                img.putpixel((x, y), hexc("3a3f44"))
    return img


def tesla_coil_secondary():
    """Enamelled magnet wire: 1px turns in alternating copper tones with a vertical specular column."""
    tones = [hexc("b4684d"), hexc("d98c5f"), hexc("8c4a35"), hexc("c97a55")]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = tones[y % 4]
            if x in (4, 5):
                c = shade(c, 1.28)
            elif x == 3 or x == 6:
                c = shade(c, 1.12)
            elif x >= 13:
                c = shade(c, 0.82)
            img.putpixel((x, y), c)
    return img


def tesla_coil_toroid():
    """Polished spun aluminium: pale silver with soft horizontal highlight bands."""
    rows = [hexc("c9d1d6"), hexc("dfe5e9"), hexc("f4f7f8"), hexc("ffffff"), hexc("eef2f4"), hexc("d4dbe0"),
            hexc("c3cbd1"), hexc("b4bdc4"), hexc("aab3ba"), hexc("b4bdc4"), hexc("c9d1d6"), hexc("dfe5e9"),
            hexc("d4dbe0"), hexc("bec7cd"), hexc("a8b1b8"), hexc("98a1a8")]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(rows[y], 1.0 + (0.02 if (x * 5 + y) % 7 == 0 else 0)))
    return img


def grow_lamp_housing():
    """Anodised dark aluminium heatsink with vertical fins."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = [hexc("4a4f55"), hexc("3a3e43"), hexc("26292d")][x % 3]
            img.putpixel((x, y), c)
    edges(img, (0, 0, 15, 15), hexc("5c6268"), hexc("1c1e21"))
    return img


def grow_lamp_led(on=False):
    """4x4 grid of 2x2 LEDs on a dark board; lit mostly magenta with a few blue and white."""
    img = Image.new("RGBA", (16, 16), hexc("1c1d22"))
    edges(img, (0, 0, 15, 15), hexc("3a3d44"), hexc("101114"))
    blue = {(1, 2), (3, 0), (0, 3)}
    white = {(2, 1)}
    for j in range(4):
        for i in range(4):
            x, y = 2 + i * 3 + (1 if i > 1 else 0), 2 + j * 3 + (1 if j > 1 else 0)
            if on:
                if (i, j) in white:
                    cs = [hexc("ffffff"), hexc("f4ecff"), hexc("e6dcff")]
                elif (i, j) in blue:
                    cs = [hexc("b8c8ff"), hexc("6a8cff"), hexc("4a64d8")]
                else:
                    cs = [hexc("ffd2f2"), hexc("ff9be6"), hexc("ff5ad2")]
                img.putpixel((x, y), cs[0])
                img.putpixel((x + 1, y), cs[1])
                img.putpixel((x, y + 1), cs[1])
                img.putpixel((x + 1, y + 1), cs[2])
            else:
                c = hexc("3a2f52") if (i, j) in blue else hexc("4e4a52") if (i, j) in white else hexc("4a2a44")
                img.putpixel((x, y), shade(c, 1.35))
                img.putpixel((x + 1, y), c)
                img.putpixel((x, y + 1), c)
                img.putpixel((x + 1, y + 1), shade(c, 0.75))
    return img


def charging_pad_side():
    """Dark rubber-edged plate with the teal brand stripe."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hexc("2e3236") if y < 13 else hexc("1a1b1c"))
    for x in range(16):
        img.putpixel((x, 13), BRAND)
        img.putpixel((x, 12), hexc("4a5056"))
        img.putpixel((x, 15), hexc("111213"))
    return img


def charging_pad_top(pulse=None):
    """Matte pad with concentric copper coil rings and an FE dot; `pulse` (0..7) adds a cyan ring travelling outward."""
    import math
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            c = hexc("26292c")
            if r < 7.0 and int(r * 1.0) % 2 == 1 and r > 1.6:
                c = hexc("5a3a2c")                               # copper turns under the matte skin
            if pulse is not None:
                pr = 1.5 + pulse * 0.85
                k = abs(r - pr)
                fade = 1.0 - pulse / 8.0
                if k < 0.7:
                    c = hexc("bff6ff") if fade > 0.7 and k < 0.35 else hexc("4fe6ff")
                    c = mix_rgb(c, hexc("26292c"), 1 - fade * 0.95)
                elif k < 1.3:
                    c = mix_rgb(hexc("1f6a78"), c, 1 - fade * 0.8)
            img.putpixel((x, y), c)
    for p in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        img.putpixel(p, FE_RED if pulse is not None else shade(FE_RED, 0.55))
    edges(img, (0, 0, 15, 15), hexc("4a5056"), hexc("17191b"))
    for x in (0, 15):
        for y in (0, 15):
            img.putpixel((x, y), hexc("1a1b1c"))
    return img


def mix_rgb(a, b, t):
    """Linear blend from a (t=0) to b (t=1), opaque."""
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


def electrolyzer_front(frame=None):
    """Steel front with a window into the water cell: grey and copper electrodes, an H2 label; `frame` (0..7) adds rising bubbles."""
    img = steel_plate(5401)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 1, 9, 4), fill=hexc("2b2e31"))              # label plate
    pixmap(img, ["#.#", "###", "#.#", "#.#"], {"#": shade(EE_TEAL, 0.8)}, 3, 1)
    pixmap(img, ["##", ".#", "#.", "##"], {"#": shade(EE_TEAL, 0.6)}, 7, 1)
    edges(img, (2, 5, 13, 14), STEEL_DARK, STEEL_LIGHT)
    water = [hexc("8cc4ec"), hexc("5fa8e0"), hexc("4a94d2"), hexc("3f86c8"), hexc("3576b8"), hexc("2f6aa8"),
             hexc("2a5f9a"), hexc("24548a")]
    for y in range(6, 14):
        for x in range(3, 13):
            img.putpixel((x, y), water[y - 6])
    for x, y in [(3, 7), (3, 8), (4, 7)]:                      # glass glint
        img.putpixel((x, y), hexc("b8def6"))
    for y in range(5, 14):
        img.putpixel((5, y), hexc("b9c2c9") if y > 5 else hexc("2a2e31"))
        img.putpixel((6, y), hexc("7d858c") if y > 5 else hexc("2a2e31"))
        img.putpixel((10, y), COPPER_P["1"] if y > 5 else hexc("2a2e31"))
        img.putpixel((11, y), COPPER_P["3"] if y > 5 else hexc("2a2e31"))
    if frame is not None:
        # (column, phase): three streams off the grey cathode (H2), two off the copper anode (O2).
        for bx, ph in [(4, 0), (7, 3), (4, 5), (7, 6), (9, 2), (12, 6)]:
            y = 13 - (frame + ph) % 8
            if y >= 6:
                img.putpixel((bx, y), hexc("eef9ff") if bx < 8 else hexc("cfe9fb"))
    return img


def electrolyzer_front_on():
    return anim([electrolyzer_front(f) for f in range(8)])


PIPE_FLANGE = [
    ".aaaaaa.",
    "arbbbbrc",
    "absssshc",
    "abshhhbc",
    "abshhhbc",
    "abshhhbc",
    "arbbbbrc",
    ".cccccc.",
]


def electrolyzer_side():
    """Steel side: flanged pipe port and a pale-cyan hydrogen hazard stripe."""
    img = steel_plate(5402)
    pixmap(img, PIPE_FLANGE, {"a": STEEL_LIGHT, "b": hexc("8a939a"), "c": STEEL_DARK, "r": RIVET,
                              "h": hexc("17191b"), "s": hexc("33383c")}, 4, 2)
    for x in range(1, 15):
        for y in (12, 13):
            img.putpixel((x, y), H2_CYAN[1] if (x + y) // 2 % 2 == 0 else hexc("2b2e31"))
    return img


def gas_cap(img, cx, cy, ramp):
    """Round outlet cap seen from above (3x3 dome on a dark collar)."""
    d = ImageDraw.Draw(img)
    d.rectangle((cx - 2, cy - 2, cx + 2, cy + 2), fill=hexc("2a2e31"))
    for p in [(cx - 2, cy - 2), (cx + 2, cy - 2), (cx - 2, cy + 2), (cx + 2, cy + 2)]:
        img.putpixel(p, STEEL)
    d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=ramp[1])
    img.putpixel((cx - 1, cy - 1), ramp[0])
    img.putpixel((cx + 1, cy + 1), ramp[2])
    img.putpixel((cx + 1, cy - 1), ramp[1])


def electrolyzer_top():
    """Two gas outlets: pale cyan H2 and steel O2."""
    img = steel_plate(5403)
    gas_cap(img, 4, 5, H2_CYAN)
    gas_cap(img, 11, 5, [hexc("d4dbe0"), hexc("9aa3ab"), hexc("6f777e")])
    for x in range(2, 14):
        img.putpixel((x, 11), hexc("2a2e31"))
        img.putpixel((x, 12), STEEL_LIGHT)
    rivets(img, [(2, 13), (12, 13)])
    return img


def fuel_cell_front(on=False):
    """PEM stack: thin graphite and steel plates in a steel frame, with a status lamp."""
    img = steel_plate(5501)
    edges(img, (2, 2, 13, 13), STEEL_DARK, STEEL_LIGHT)
    for y in range(3, 13):
        for x in range(3, 13):
            if y % 2:
                c = hexc("2a2c2f")
                if on and 4 <= x <= 11:
                    c = mix_rgb(hexc("2a2c2f"), hexc("ff6a20"), max(0.0, 0.34 - abs(x - 7.5) * 0.08))
            else:
                c = hexc("8a939a") if x > 3 else hexc("b0b8be")
                if x == 12:
                    c = hexc("5a6167")
            img.putpixel((x, y), c)
    for y in (3, 12):                                         # end plates' tie rods
        for x in (3, 12):
            img.putpixel((x, y), RIVET)
    img.putpixel((13, 0), hexc("1e2124"))
    d = ImageDraw.Draw(img)
    d.rectangle((11, 0, 13, 1), fill=hexc("26292c"))
    img.putpixel((12, 0), hexc("5cff6a") if on else hexc("1f3a24"))
    img.putpixel((12, 1), hexc("2fb840") if on else hexc("17291a"))
    for x in range(3, 9):
        img.putpixel((x, 14), BRAND)
    return img


def fuel_cell_side():
    """Steel side with a cooling fan grille and the teal stripe."""
    import math
    img = steel_plate(5502)
    for y in range(1, 12):
        for x in range(2, 13):
            dx, dy = x - 7, y - 6
            r = math.hypot(dx, dy)
            if r > 5.4:
                continue
            c = hexc("17191b")
            if r > 4.4:
                c = STEEL_DARK if dx + dy > 0 else hexc("b0b8be")    # rim, lit top-left
            elif r < 1.5:
                c = hexc("9aa3ab")                                  # hub
            elif dx == 0 or dy == 0:
                c = hexc("6f777e")                                  # spokes
            elif 2.5 < r < 3.4:
                c = hexc("4a5056")                                  # grille ring
            img.putpixel((x, y), c)
    for x in range(1, 15):
        img.putpixel((x, 13), BRAND)
        img.putpixel((x, 14), shade(BRAND, 0.7))
    return img


def fuel_cell_top():
    """H2 inlet cap and a vent grille."""
    img = steel_plate(5503)
    gas_cap(img, 4, 4, H2_CYAN)
    d = ImageDraw.Draw(img)
    for y in range(9, 14, 2):
        d.line((3, y, 12, y), fill=hexc("1e2124"))
        d.line((3, y + 1, 12, y + 1), fill=STEEL_LIGHT)
    d.line((8, 2, 13, 2), fill=BRAND)
    return img


def hydrogen_frames(size, n, stretch, speed, seed):
    """Pale gas wisps: two tileable noise fields drifting in different directions, alpha ~150-235."""
    a = value_noise(size, size, seed, cell=size // 4)
    b = value_noise(size, size, seed + 1, cell=size // 8)
    sheet = Image.new("RGBA", (size, size * n))
    for f in range(n):
        for y in range(size):
            for x in range(size):
                ya = (y - f * speed) % size
                v = a[ya][(x + (f * size // n) // stretch) % size] * 0.65 + b[(y - f * speed * 2) % size][(x - f * size // n) % size] * 0.35
                t = max(0.0, min(1.0, (v - 0.35) / 0.35))
                alpha = int(150 + 85 * t)
                c = mix_rgb(hexc("c8ecfa"), hexc("ffffff"), t)
                sheet.putpixel((x, f * size + y), c[:3] + (alpha,))
    return sheet


def hydrogen_still():
    return hydrogen_frames(16, 16, 1, 1, 5601)


def hydrogen_flow():
    return hydrogen_frames(32, 16, 1, 2, 5602)


def gui_electrolyzer():
    img, d = machine_gui()
    fluid_gauge(img, d, 26, 16, 16, 54)
    fluid_gauge(img, d, 130, 16, 16, 54)
    return img


def gui_fuel_cell():
    img, d = machine_gui()
    fluid_gauge(img, d, 44, 16, 16, 54)
    bevel(d, 69, 63, 80, 6, SLOT_DARK, WHITE, hexc("1a1a1a"))
    return img


# ---------------------------------------------------------------- wires and cables (whole tile = one face of a thin cable,
# ---------------------------------------------------------------- U = along the cable, so features are bold and run along x)
AL_RAMP = [hexc("7d858c"), hexc("9aa3ab"), hexc("b4bcc2"), hexc("c9cfd4")]
CU_RAMP = [COPPER_P["4"], COPPER_P["3"], COPPER_P["2"], COPPER_P["1"]]
RUBBER = hexc("2e2e31")


def stranded(ramp, seed):
    """Bare stranded conductor: 45 degree strand bands (4px period), lit along the top, specular line at rows 3-4."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        f = 1.16 - y * 0.026
        for x in range(16):
            c = ramp[(0, 1, 3, 2)[(x + y) % 4]]
            if y in (3, 4) and (x + y) % 4 in (1, 2):
                c = shade(ramp[3], 1.18)
            img.putpixel((x, y), shade(c, f + r.uniform(-0.03, 0.03)))
    return img


def copper_wire():
    return stranded(CU_RAMP, 2101)


def aluminum_cable():
    return stranded(AL_RAMP, 2102)


def rubber_jacket(seed):
    """Matte rubber: faint noise, lighter top rows, darker bottom rows."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        f = 1.22 if y < 2 else 1.1 if y < 4 else 0.82 if y > 12 else 1.0
        for x in range(16):
            img.putpixel((x, y), shade(RUBBER, f + r.uniform(-0.04, 0.04)))
    return img


def copper_wire_coated():
    img = rubber_jacket(2103)
    for x in range(16):
        img.putpixel((x, 7), COPPER_P["2"])
        img.putpixel((x, 8), COPPER_P["4"])
    return img


def insulated_copper_cable():
    """Heavy cable: black jacket, red phase stripe, printed ticks every 8px."""
    img = rubber_jacket(2104)
    for x in range(16):
        for y in range(6, 10):
            img.putpixel((x, y), hexc("e0402f") if y == 6 else hexc("c02a20") if y < 9 else hexc("8a1e17"))
    for x0 in (2, 10):
        for x in range(x0, x0 + 3):
            img.putpixel((x, 11), hexc("5e5e64"))
    return img


def aluminum_cable_coated():
    """MV cable: black jacket, yellow MV bands near each end and a yellow stripe along the length."""
    img = rubber_jacket(2105)
    yel, dark = hexc("e8c23a"), hexc("6a5410")
    for x in range(16):
        img.putpixel((x, 6), yel)
        img.putpixel((x, 7), yel)
        img.putpixel((x, 8), shade(yel, 0.75))
    for y in range(16):
        f = 1.15 if y < 3 else 0.8 if y > 12 else 1.0
        for x in (1, 2, 13, 14):
            img.putpixel((x, y), shade(yel, f))
        for x in (0, 3, 12, 15):
            img.putpixel((x, y), dark)
    return img


def wire_joint():
    """Crimped junction sleeve: dark metal with two raised ribs and a brass rivet."""
    img = Image.new("RGBA", (16, 16))
    r = random.Random(2106)
    for y in range(16):
        f = 1.15 if y < 3 else 0.82 if y > 12 else 1.0
        for x in range(16):
            c = hexc("4a5056")
            if x in (3, 11):
                c = hexc("8a939a")
            elif x in (4, 12):
                c = hexc("6f777e")
            elif x in (5, 13):
                c = hexc("2a2e31")
            elif (x + y) % 4 == 0 and 6 <= x <= 10:
                c = hexc("40464b")                              # crimp dimples
            img.putpixel((x, y), shade(c, f + r.uniform(-0.03, 0.03)))
    edges(img, (0, 0, 15, 15), hexc("6f777e"), hexc("1e2124"))
    for p, c in [((7, 7), BRASS_P["1"]), ((8, 7), BRASS_P["2"]), ((7, 8), BRASS_P["3"]), ((8, 8), BRASS_P["4"])]:
        img.putpixel(p, c)
    return img


# ---------------------------------------------------------------- glass pipes (translucent render type)
def glass_pipe(tint, band):
    """Glass tube face: faint tint, two glint streaks, opaque metal collars at both ends along U (columns 0-1, 14-15)."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            a = 60 if y < 2 or y > 13 else 44
            img.putpixel((x, y), tint[:3] + (a,))
    for x in range(2, 14):
        for y in range(16):
            k = x - y
            if k in (5, 6) or k == -3:
                img.putpixel((x, y), hexc("ffffff", 150 if k != 6 else 110))
    for y in range(16):
        c = band[0] if y < 4 else band[1] if y < 12 else band[2]
        img.putpixel((0, y), band[3])
        img.putpixel((1, y), c)
        img.putpixel((14, y), c)
        img.putpixel((15, y), band[3])
    return img


ITEM_PIPE_BAND = [hexc("e6c56a"), hexc("c9a24a"), hexc("a8842e"), hexc("8a6a2a")]
FLUID_PIPE_BAND = [COPPER_P["2"], COPPER_P["3"], COPPER_P["4"], hexc("5a3020")]


def item_pipe():
    return glass_pipe(hexc("e8f4ff"), ITEM_PIPE_BAND)


def fluid_pipe():
    return glass_pipe(hexc("bfdcff"), FLUID_PIPE_BAND)


def pipe_flange(band):
    """Opaque flange plate: metal ring, four bolts, a dark centre where the tube enters."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), band[1] if (x + y) < 15 else band[2])
    edges(img, (0, 0, 15, 15), band[0], band[3])
    ImageDraw.Draw(img).rectangle((4, 4, 11, 11), fill=shade(band[3], 0.45))
    edges(img, (3, 3, 12, 12), band[3], band[0])
    for x, y in [(1, 1), (13, 1), (1, 13), (13, 13)]:
        img.putpixel((x, y), hexc("d4dbe0"))
        img.putpixel((x + 1, y), hexc("8a939a"))
        img.putpixel((x, y + 1), hexc("8a939a"))
        img.putpixel((x + 1, y + 1), hexc("4a5056"))
    return img


# ---------------------------------------------------------------- 3D tool part textures (stretched over small cuboids)
TOOL_ORANGE = [hexc("ffaa55"), hexc("e87a1e"), hexc("c25e12"), hexc("8a400a")]


def tool_plastic(seed, ramp):
    """Moulded plastic: vertical light-to-dark gradient with a bevel."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        base = ramp[0] if y < 2 else ramp[1] if y < 12 else ramp[2]
        for x in range(16):
            img.putpixel((x, y), shade(base, 1 + r.uniform(-0.025, 0.025)))
    edges(img, (0, 0, 15, 15), ramp[0], ramp[3])
    return img


def drill_body():
    img = tool_plastic(2201, TOOL_ORANGE)
    for x in (4, 7, 10):
        for y in range(4, 10):
            img.putpixel((x, y), hexc("2a1a0e"))
            img.putpixel((x + 1, y), hexc("4a2a12") if y < 9 else TOOL_ORANGE[0])
    for x in range(2, 14):
        img.putpixel((x, 12), hexc("8a939a"))
        img.putpixel((x, 13), BRAND)
    return img


def rubber_grip(seed, ridge):
    """Black overmould rubber; ridge(x, y) marks raised ridge pixels."""
    img = rubber_jacket(seed)
    for y in range(16):
        for x in range(16):
            if ridge(x, y):
                img.putpixel((x, y), shade(img.getpixel((x, y)), 1.6))
    edges(img, (0, 0, 15, 15), hexc("4a4a4e"), hexc("121214"))
    return img


def drill_grip():
    return rubber_grip(2202, lambda x, y: (x + y) % 4 == 0)


def chrome(bands, tint=None):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        c = hexc(bands[y])
        if tint:
            c = mix_rgb(c, tint, 0.12)
        for x in range(16):
            img.putpixel((x, y), shade(c, 1.04) if x < 2 else shade(c, 0.9) if x > 13 else c)
    return img


def drill_chrome():
    return chrome(["8a939a", "d4dbe0", "ffffff", "f4f7f8", "b9c2c9", "7d858c", "4a5056", "5a6167",
                   "8a939a", "c9d1d6", "f4f7f8", "dfe5e9", "aab3ba", "7d858c", "5a6167", "3a3f44"])


def drill_bit():
    """HSS bit: dark steel with a bright helical flute."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = (x + y) % 6
            c = hexc("eef2f4") if k == 0 else hexc("b9c2c9") if k == 1 else hexc("2b2f33") if k == 3 else hexc("4a5056")
            img.putpixel((x, y), shade(c, 1.1 - y * 0.015))
    return img


def drill_battery():
    img = tool_plastic(2203, [hexc("5a6066"), hexc("3a3e42"), hexc("2a2d30"), hexc("16181a")])
    for x in range(1, 15):
        img.putpixel((x, 5), hexc("8a2a20"))
        for y in (6, 7, 8):
            img.putpixel((x, y), FE_RED if y < 8 else hexc("d8352b"))
        img.putpixel((x, 9), hexc("8a2a20"))
    for x in (2, 5, 9, 12):
        img.putpixel((x, 12), hexc("8affa0"))
        img.putpixel((x + 1, 12), hexc("3fdc6a"))
        img.putpixel((x, 13), hexc("3fdc6a"))
        img.putpixel((x + 1, 13), hexc("1f8a3a"))
    return img


def saw_body():
    img = tool_plastic(2204, TOOL_ORANGE)
    for y in (4, 7, 10):
        for x in range(3, 13):
            img.putpixel((x, y), hexc("2a1a0e"))
            img.putpixel((x, y + 1), TOOL_ORANGE[0])
    return img


def saw_grip():
    return rubber_grip(2205, lambda x, y: x % 4 == 1 and 2 < y < 13)


def saw_bar():
    """Brushed steel guide bar with a rivet hole and a faint logo line."""
    r = random.Random(2206)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        streak = r.uniform(-0.05, 0.05)
        for x in range(16):
            img.putpixel((x, y), shade(hexc("aab3ba"), 1.08 - y * 0.012 + streak + r.uniform(-0.02, 0.02)))
    edges(img, (0, 0, 15, 15), hexc("d4dbe0"), hexc("6f777e"))
    for p, c in [((3, 6), "6f777e"), ((4, 6), "6f777e"), ((3, 7), "1e2124"), ((4, 7), "1e2124"),
                 ((3, 8), "1e2124"), ((4, 8), "3a3f44"), ((5, 7), "d4dbe0"), ((5, 8), "d4dbe0")]:
        img.putpixel(p, hexc(c))
    for x in range(8, 14):
        img.putpixel((x, 10), hexc("8a939a"))
    img.putpixel((8, 10), BRAND)
    img.putpixel((9, 10), BRAND)
    return img


def saw_chain(frame=0):
    """Chain links with a bright cutter tooth every 4px along U; frame shifts it 1px."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = (x - frame) % 4
            c = hexc("3a3f44") if y % 8 in (3, 4) else hexc("4a5056")
            if k == 2 and y % 8 in (3, 4):
                c = hexc("8a939a")                                   # link rivet
            if k in (0, 1) and y < 5:
                c = hexc("eef2f4") if (k == 0 and y < 4) or y == 0 else hexc("aab3ba")   # cutter tooth
            if y > 13:
                c = hexc("26292c")
            img.putpixel((x, y), c)
    return img




WRENCH = [
    "................",
    "..........AB....",
    ".........ABBC...",
    ".........ABC..A.",
    "........ABBC.ABC",
    "........ABBBBBC.",
    ".......ABBkkBC..",
    "......ABBBBBC...",
    ".....ABBC.......",
    "....OOPC........",
    "...OOPQ.........",
    "..KKKQ..........",
    ".OOPQ...........",
    "KOPQ............",
    "KKQ.............",
    "................",
]


def wrench_item():
    """A classic crescent (adjustable) wrench, held like a vanilla tool: chrome head with an open
    jaw at the top right, the worm screw under it, and a long flat handle with a hanging hole."""
    rows = [
        "...........oo...",
        "..........oHHo..",
        ".........oHh.Ho.",
        ".........oHo..o.",
        "........oHHHo...",
        ".......oHhKHHo..",
        "......oHhKKhHo..",
        ".....oHhhhho....",
        "....oHhhho......",
        "...oHhhho.......",
        "..oHhhho........",
        ".oHhhho.........",
        "oHh.ho..........",
        "oHhhho..........",
        ".oooo...........",
        "................",
    ]
    pal = {"o": hexc("23272b"), "H": hexc("eef2f5"), "h": hexc("aab4bc"), "K": hexc("6d757b")}
    return pixmap(blank(), rows, pal)


def upgrade_table_top():
    """Gunmetal bench top with a glowing cyan circuit inlay around a projector ring."""
    import math
    img = painted(2301, GUNMETAL)
    line, node = hexc("2aa8c0"), hexc("c8fbff")
    for i in range(2, 14):
        for p in ((i, 2), (i, 13), (2, i), (13, i)):
            img.putpixel(p, line)
    for i in range(3, 5):
        for p in ((i, 7), (15 - i, 8), (8, i), (7, 15 - i)):
            img.putpixel(p, line)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 3.3 < r < 4.4:
                img.putpixel((x, y), HOLO)
            elif r <= 3.3:
                img.putpixel((x, y), hexc("0e1a20") if r > 1.2 else hexc("1d5a66"))
    for p in [(2, 2), (13, 2), (2, 13), (13, 13), (2, 7), (13, 8), (8, 2), (7, 13)]:
        img.putpixel(p, node)
    return img


def upgrade_table_panel(seed):
    img = painted(seed, GUNMETAL)
    for x in range(1, 15):
        img.putpixel((x, 2), BRAND)
        img.putpixel((x, 3), shade(BRAND, 0.7))
    return img


def upgrade_table_side():
    img = upgrade_table_panel(2302)
    for y in (7, 10):
        for x in range(3, 13):
            img.putpixel((x, y), hexc("101316"))
            img.putpixel((x, y + 1), hexc("4a535b"))
    return img


def upgrade_table_front():
    """Two drawers with brass pulls, a cyan status light and the teal stripe."""
    img = upgrade_table_panel(2303)
    for y0 in (5, 10):
        edges(img, (2, y0, 13, y0 + 4), hexc("4a535b"), hexc("15191d"))
        for x in range(6, 10):
            img.putpixel((x, y0 + 2), BRASS_P["2"])
        img.putpixel((6, y0 + 2), BRASS_P["1"])
        img.putpixel((9, y0 + 2), BRASS_P["4"])
    img.putpixel((13, 2), HOLO)
    img.putpixel((12, 2), hexc("0e1a20"))
    img.putpixel((14, 2), hexc("0e1a20"))
    return img


def upgrade_table_leg():
    img = dark_steel(2304)
    edges(img, (0, 0, 15, 15), hexc("6a737b"), hexc("1e2226"))
    return img


def socket_frame(img, ox, oy, fill, light, dark):
    """20x20 socket around a 16x16 slot whose item origin is (ox, oy): chamfered corners, recessed 18x18 well."""
    x0, y0 = ox - 2, oy - 2
    for y in range(20):
        for x in range(20):
            k = min(x, y, 19 - x, 19 - y)
            corner = min(x, 19 - x) + min(y, 19 - y)
            if corner < 2:
                continue
            if k == 0 or corner == 2:
                c = light if x + y < 19 else dark
            else:
                c = fill
                if k == 1:
                    c = hexc("04070a") if x == 1 or y == 1 else hexc("1f2c38")
            img.putpixel((x0 + x, y0 + y), c)


def dark_slot(img, x, y):
    """18x18 recessed slot for an item at (x, y): dark well, black top-left, lighter bottom-right."""
    d = ImageDraw.Draw(img)
    bevel(d, x - 1, y - 1, 18, 18, hexc("04070a"), hexc("2a3a48"), hexc("0a1016"))


def gui_upgrade_table():
    """Holographic tech bench, 248x218, plus sprites below it."""
    import math
    W, H = 248, 218
    img = Image.new("RGBA", (256, 256), CLEAR)
    d = ImageDraw.Draw(img)
    top, bot = hexc("0b1118"), hexc("111c26")
    for y in range(H):
        base = mix_rgb(top, bot, y / H)
        for x in range(W):
            c = base
            if y > 15 and (x % 8 == 0 or y % 8 == 0):
                c = mix_rgb(base, hexc("12303a"), 0.7)
            img.putpixel((x, y), c)
    # Title bar and underline.
    d.rectangle((2, 2, W - 3, 13), fill=hexc("17222c"))
    d.line((2, 14, W - 3, 14), fill=hexc("2a8ea0"))
    # Gunmetal 2px bevelled frame with rounded corners.
    edges(img, (0, 0, W - 1, H - 1), hexc("6a7682"), hexc("1a2028"))
    edges(img, (1, 1, W - 2, H - 2), hexc("4a5562"), hexc("2a333d"))
    for p in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:
        img.putpixel(p, CLEAR)
    for (cx, cy, sx, sy) in [(1, 1, 1, 1), (W - 2, 1, -1, 1), (1, H - 2, 1, -1), (W - 2, H - 2, -1, -1)]:
        for i in range(6):
            img.putpixel((cx + i * sx, cy), HOLO)
            img.putpixel((cx, cy + i * sy), HOLO)
    # Central bay.
    CX, CY = 84, 66
    for y in range(CY - 31, CY + 32):
        for x in range(CX - 31, CX + 32):
            r = math.hypot(x - CX, y - CY)
            if r <= 30.5:
                c = hexc("070c11")
                if abs(r - 29.5) < 0.6:
                    c = HOLO_DIM
                elif abs(r - 22) < 0.5:
                    c = hexc("164650")
                elif abs(r - 30.3) < 0.3:
                    c = hexc("2a8ea0")
                elif 25.5 < r < 27.5 and math.degrees(math.atan2(y - CY, x - CX)) % 15 < 3:
                    c = hexc("164650")                          # bearing ticks
                img.putpixel((x, y), c)
    # Circuit traces from each socket to the bay rim.
    traces = [[(74, 30), (64, 30), (64, 40)], [(93, 30), (104, 30), (104, 40)],
              [(74, 118), (64, 118), (64, 90)], [(93, 118), (104, 118), (104, 90)],
              [(120, 64), (120, 72), (100, 72)], [(120, 84), (120, 78), (100, 78)],
              [(48, 64), (48, 72), (68, 72)], [(48, 84), (48, 78), (68, 78)]]
    for pts in traces:
        end = None
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            n = max(abs(bx - ax), abs(by - ay))
            for i in range(n + 1):
                x, y = ax + (bx - ax) * i // n, ay + (by - ay) * i // n
                if math.hypot(x - CX, y - CY) <= 30.5:
                    break
                img.putpixel((x, y), hexc("23707e"))
                end = (x, y)
            else:
                continue
            break
        if end:
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                img.putpixel((end[0] + dx - (end[0] > CX), end[1] + dy - (end[1] > CY)), HOLO)
    sockets = [(76, 24), (112, 45), (112, 87), (76, 108), (40, 87), (40, 45)]
    for ox, oy in sockets:
        socket_frame(img, ox, oy, hexc("0a1016"), hexc("2f8090"), hexc("163a44"))
        img.putpixel((ox - 1, oy - 1), HOLO_DIM)
    dark_slot(img, 76, 58)
    # Stats panel.
    d.rectangle((160, 18, 240, 124), fill=hexc("070c11"), outline=HOLO_DIM)
    for x in (160, 240):
        for y in (18, 124):
            img.putpixel((x, y), hexc("2a8ea0"))
    # Player inventory in the dark style.
    for row in range(3):
        for col in range(9):
            dark_slot(img, 44 + col * 18, 136 + row * 18)
    for col in range(9):
        dark_slot(img, 44 + col * 18, 194)
    # Sprites: socket glow (0,224), socket locked (20,224), energy pulse (40,224).
    glow = Image.new("RGBA", (20, 20), CLEAR)
    locked = Image.new("RGBA", (20, 20), CLEAR)
    for y in range(20):
        for x in range(20):
            k = min(x, y, 19 - x, 19 - y)
            corner = min(x, 19 - x) + min(y, 19 - y)
            if corner < 2:
                continue
            if k == 0 or corner == 2:
                glow.putpixel((x, y), HOLO)
                locked.putpixel((x, y), hexc("c0392b", 230))
            elif k == 1 or corner == 3:
                glow.putpixel((x, y), hexc("3fe0ff", 120))
                locked.putpixel((x, y), hexc("6a1a14", 170))
            elif k == 2:
                glow.putpixel((x, y), hexc("3fe0ff", 40))
            if k >= 1 and (x == y or x == 19 - y) and 2 <= x <= 17:
                locked.putpixel((x, y), hexc("e04a3a", 200))
    img.paste(glow, (0, 224))
    img.paste(locked, (20, 224))
    for y in range(7):
        for x in range(7):
            r = math.hypot(x - 3, y - 3)
            if r < 3.6:
                t = r / 3.6
                c = mix_rgb(hexc("ffffff"), HOLO, min(1.0, t * 1.6))
                img.putpixel((40 + x, 224 + y), c[:3] + (int(255 * (1 - t) ** 1.2),))
    return img


def upgrade_table_hologram():
    """64x64 glowing cyan ring with tick marks (transparent elsewhere)."""
    import math
    img = Image.new("RGBA", (64, 64), CLEAR)
    for y in range(64):
        for x in range(64):
            dx, dy = x + 0.5 - 32, y + 0.5 - 32
            r = math.hypot(dx, dy)
            a = math.degrees(math.atan2(dy, dx)) % 360
            c = None
            if abs(r - 27) < 0.75:
                c = hexc("bff6ff")
            elif abs(r - 27) < 1.8:
                c = hexc("3fe0ff", 130)
            elif abs(r - 27) < 2.8:
                c = hexc("3fe0ff", 45)
            elif 29.0 < r < 31.5 and a % 15 < 2.4:
                c = hexc("3fe0ff", 230 if a % 45 < 2.4 else 150)
            elif 22.5 < r < 24 and a % 30 < 12 and (a // 30) % 2 == 0:
                c = hexc("3fe0ff", 90)
            if c:
                img.putpixel((x, y), c)
    return img


UPGRADE_MODULE_EMBLEMS = {
    "efficiency": ("5adc3a", "c8ffb0", ["..####..", "..#++#..", "..#++#..", "###++###", ".#++++#.", "..#++#..",
                                         "...##...", "........"]),
    "thruster": ("3a8cff", "b0e0ff", ["...##...", "..####..", "..####..", ".######.", ".#.##.#.", "..+..+..",
                                       "..++++..", "...++..."]),
    "plating": ("a8b1b8", "eef2f4", [".######.", "########", "##++####", "##+#####", ".######.", ".######.",
                                      "..####..", "...##..."]),
    "overcharge": ("ffd02a", "fff4b0", ["....###.", "...###..", "..+##...", ".######.", "...##+..", "..###...",
                                         ".##.....", ".#......"]),
    "chain": ("c04aff", "f0c8ff", ["...##...", "...#....", "..##....", "..#.#...", ".#...#..", ".+...##.",
                                    "+.....#.", "......+."]),
    "woodcutter": ("c88a3a", "ffe0a8", ["..##....", ".####...", "#####+..", "####.+..", ".##...+.", ".......+",
                                         "........", "........"]),
}


def phase_g():
    save(copper_wire(), "block/copper_wire.png")
    save(copper_wire_coated(), "block/copper_wire_coated.png")
    save(insulated_copper_cable(), "block/insulated_copper_cable.png")
    save(aluminum_cable(), "block/aluminum_cable.png")
    save(aluminum_cable_coated(), "block/aluminum_cable_coated.png")
    save(wire_joint(), "block/wire_joint.png")
    save(item_pipe(), "block/item_pipe.png")
    save(fluid_pipe(), "block/fluid_pipe.png")
    save(pipe_flange(ITEM_PIPE_BAND), "block/item_pipe_flange.png")
    save(pipe_flange(FLUID_PIPE_BAND), "block/fluid_pipe_flange.png")
    save(drill_body(), "item/model/drill_body.png")
    save(drill_grip(), "item/model/drill_grip.png")
    save(drill_chrome(), "item/model/drill_chrome.png")
    save(drill_bit(), "item/model/drill_bit.png")
    save(drill_battery(), "item/model/drill_battery.png")
    save(saw_body(), "item/model/saw_body.png")
    save(saw_grip(), "item/model/saw_grip.png")
    save(saw_bar(), "item/model/saw_bar.png")
    save(saw_chain(), "item/model/saw_chain.png")
    save(anim([saw_chain(f) for f in range(4)]), "item/model/saw_chain_running.png")
    write_mcmeta("item/model/saw_chain_running.png", 1)
    save(wrench_item(), "item/wrench.png")
    save(upgrade_table_top(), "block/upgrade_table_top.png")
    save(upgrade_table_side(), "block/upgrade_table_side.png")
    save(upgrade_table_front(), "block/upgrade_table_front.png")
    save(upgrade_table_leg(), "block/upgrade_table_leg.png")
    save(gui_upgrade_table(), "gui/upgrade_table.png")
    save(upgrade_table_hologram(), "gui/upgrade_table_hologram.png")
    for kind, (main, accent, emblem) in UPGRADE_MODULE_EMBLEMS.items():
        save(module_sprite(emblem, hexc(main), hexc(accent)), f"item/{kind}_module.png")


# ---------------------------------------------------------------- laser engraver
HAZARD_YEL = hexc("e8b923")


def engraver_plate(seed):
    img = dark_steel(seed)
    edges(img, (0, 0, 15, 15), hexc("5a6167"), hexc("1e2124"))
    return img


def laser_engraver_side():
    """Dark cabinet: teal stripe, class-4 laser warning triangle, hazard strip."""
    img = engraver_plate(2401)
    for x in range(1, 15):
        img.putpixel((x, 2), BRAND)
        img.putpixel((x, 3), shade(BRAND, 0.7))
    pixmap(img, ["...k...", "..kYk..", "..kKk..", ".kYKYk.", ".kYYYk.", "kYYKYYk", "kkkkkkk"],
           {"k": hexc("15171a"), "Y": HAZARD_YEL, "K": hexc("15171a")}, 5, 5)
    hazard(img, 1, 14, 13, 14)
    return img


def laser_engraver_front(on=False):
    """Control panel: green display, red/green buttons, key switch, red viewing slit."""
    img = engraver_plate(2402)
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 8, 6), fill=hexc("0a0f0a"))
    edges(img, (1, 1, 9, 7), hexc("1e2124"), hexc("6f777e"))
    g = hexc("6aff7a") if on else hexc("2f8a3a")
    g2 = hexc("3fd04f") if on else hexc("1f5a26")
    for x, y in [(3, 3), (4, 3), (6, 3), (3, 5), (4, 4), (5, 5), (6, 4), (7, 3)]:
        img.putpixel((x, y), g if y == 3 else g2)
    for (x, c, lc) in [(11, "d03a2f", "ff8a80"), (13, "2fb840", "8affa0")]:
        img.putpixel((x, 2), hexc(lc))
        img.putpixel((x, 3), hexc(c))
        img.putpixel((x + 1, 3), shade(hexc(c), 0.6))
    img.putpixel((12, 5), hexc("8a939a"))                       # key switch
    img.putpixel((12, 6), BRASS_P["2"])
    img.putpixel((13, 6), hexc("1e2124"))
    d.rectangle((2, 10, 13, 11), fill=hexc("ff7a3a") if on else hexc("3a1414"))
    if on:
        for x in range(2, 14):
            img.putpixel((x, 10), hexc("ffd0a0") if x % 4 == 1 else hexc("ff9a5a"))
            img.putpixel((x, 11), hexc("e04a2a"))
    edges(img, (1, 9, 14, 12), hexc("1e2124"), hexc("6f777e"))
    for x in range(1, 15):
        img.putpixel((x, 14), BRAND)
    return img


def laser_engraver_bed():
    """Vacuum chuck: anodised plate with a 2px hole grid, raised chuck ring and three alignment pins."""
    import math
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            c = hexc("2a2f35")
            if x % 2 == 0 and y % 2 == 0:
                c = hexc("121417")
            if r < 4.6:
                c = hexc("3a4148") if (x + y) % 2 else hexc("353c42")
            elif r < 5.6:
                c = hexc("7d858c") if y < 8 else hexc("4f565c")
            img.putpixel((x, y), c)
    edges(img, (0, 0, 15, 15), hexc("b9c2c9"), hexc("6f777e"))
    for x, y in [(8, 1), (2, 12), (13, 12)]:
        img.putpixel((x, y), hexc("f4f7f8"))
    return img


def laser_engraver_gantry():
    """Brushed aluminium rail with a linear-guide groove and bolt heads."""
    r = random.Random(2403)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        streak = r.uniform(-0.04, 0.04)
        for x in range(16):
            img.putpixel((x, y), shade(hexc("c3cbd1"), 1.06 - y * 0.012 + streak + r.uniform(-0.015, 0.015)))
    for x in range(16):
        img.putpixel((x, 7), hexc("4a5056"))
        img.putpixel((x, 8), hexc("8a939a"))
    for x, y in [(2, 3), (12, 3), (2, 11), (12, 11)]:
        img.putpixel((x, y), hexc("f4f7f8"))
        img.putpixel((x + 1, y), hexc("8a939a"))
        img.putpixel((x, y + 1), hexc("8a939a"))
        img.putpixel((x + 1, y + 1), hexc("5a6167"))
    edges(img, (0, 0, 15, 15), hexc("e4e9ec"), hexc("7d858c"))
    return img


def laser_head_entity():
    """32x16: carriage 4x2x4 at (0,0), lens nozzle 2x1.5x2 at (0,8)."""
    img = Image.new("RGBA", (32, 16), CLEAR)

    def carriage(face, fw, fh):
        if face == "top":
            t = Image.new("RGBA", (fw, fh), hexc("5a6470"))
            edges(t, (0, 0, fw - 1, fh - 1), hexc("7a8590"), hexc("3a4148"))
            return t
        t = Image.new("RGBA", (fw, fh), hexc("3a4148"))
        if face != "bottom":
            for x in range(fw):
                t.putpixel((x, 0), hexc("4a535c"))
                t.putpixel((x, fh - 1), hexc("d8352b") if fh > 1 else hexc("3a4148"))
        return t

    def nozzle(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("17181a"))
        if face == "bottom":
            t.putpixel((0, 0), hexc("ff6a6a"))
            for p in [(1, 0), (0, 1), (1, 1)]:
                t.putpixel(p, hexc("c0102a"))
        elif face != "top":
            for x in range(fw):
                t.putpixel((x, 0), hexc("2c2e31"))
        return t

    box_uv(img, 0, 0, 4, 2, 4, carriage)
    box_uv_f(img, 0, 8, 2, 1.5, 2, nozzle)
    return img


PHOTOMASK_SIL = [
    "############",
    "#..........#",
    "#.#.#..#.#.#",
    "#..........#",
    "#.#.#..#.#.#",
    "#..........#",
    "#..........#",
    "#.#.#..#.#.#",
    "#..........#",
    "#.#.#..#.#.#",
    "#..........#",
    "############",
]


def gui_laser_engraver():
    img, d = machine_gui()
    slot(d, 38, 26)
    slot(d, 56, 26)
    slot(d, 17, 35)
    slot(d, 116, 35)
    bevel(d, 79, 27, 28, 28, SLOT_DARK, WHITE, hexc("141618"))
    pixmap(img, PHOTOMASK_SIL, {"#": SILHOUETTE}, 19, 37)     # ghost photomask in the tool slot
    return img


# ---------------------------------------------------------------- semiconductor items
def silicon_wafer():
    """Polished wafer: blue-grey mirror disc with a thin-film sheen streak and the flat at the bottom."""
    import math
    img = blank()
    sheen = [hexc("e88ac8"), hexc("9ad8ff"), hexc("8affc8"), hexc("ffe08a")]
    for y in range(1, 15):
        for x in range(1, 15):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 7.5)
            if r > 6.6 or y > 13:
                continue
            t = (x + y) / 28
            c = mix_rgb(hexc("6a7e98"), hexc("2a3446"), t)
            k = x - y
            if k in (1, 2, 3):
                c = mix_rgb(c, sheen[(k + y // 3) % 4], 0.45)
            if k == 2 and y < 6:
                c = hexc("e8f2ff")
            if r > 5.8:
                c = shade(c, 0.8 if x + y > 15 else 1.15)
            img.putpixel((x, y), c)
    return outline(img, hexc("141a24"))


PCB = [
    "................",
    "................",
    "..############..",
    ".#gG..o.ggGg..#.",
    ".#.g..g.g..G..#.",
    ".#.gggg.g.oG..#.",
    ".#......g..g..#.",
    ".#.o.GGGg..gg.#.",
    ".#...g.....og.#.",
    ".#.ggg.oGGg...#.",
    ".#.g......g.o.#.",
    ".#.o..GGG.g...#.",
    ".#........o...#.",
    "..############..",
    "................",
    "................",
]


def printed_circuit_board():
    img = blank()
    pal = {"#": hexc("2f8a3a"), "g": hexc("e6c56a"), "G": hexc("c9a13b"), "o": hexc("1a1c1a")}
    for y, row in enumerate(PCB):
        for x, ch in enumerate(row):
            if ch == "." and 2 <= y <= 13 and 1 <= x <= 14 and (x, y) not in [(1, 2), (14, 2), (1, 13), (14, 13)]:
                img.putpixel((x, y), hexc("3aa04a") if (x + y) % 5 else hexc("43ad52"))
            elif ch in pal:
                img.putpixel((x, y), pal[ch])
    for x in range(2, 14):
        img.putpixel((x, 2), hexc("5ac46a"))
        img.putpixel((x, 13), hexc("1f6a2a"))
    for y in range(3, 13):
        img.putpixel((1, y), hexc("4ab85a"))
        img.putpixel((14, y), hexc("1f6a2a"))
    for x, y in [(6, 3), (3, 7), (10, 5), (7, 9), (11, 8), (3, 11), (12, 10), (10, 12)]:
        img.putpixel((x, y), hexc("15171a"))                   # through-holes in their pads
        for dx, dy in ((1, 0), (0, 1)):
            if img.getpixel((x + dx, y + dy))[:3] != (21, 23, 26):
                img.putpixel((x + dx, y + dy), hexc("c9a13b"))
    return outline(img, hexc("0f2a14"))


def microprocessor():
    """CPU package: green substrate, gold edge contacts, silver lid with a tiny logo."""
    img = blank()
    for y in range(2, 14):
        for x in range(2, 14):
            c = hexc("2f7a3a")
            if x in (2, 13) or y in (2, 13):
                c = hexc("e6c56a") if (x + y) % 2 == 0 else hexc("1f5a26")
            img.putpixel((x, y), c)
    for p in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel(p, hexc("1f5a26"))
    for y in range(4, 12):
        for x in range(4, 12):
            img.putpixel((x, y), hexc("dfe5e9") if x + y < 12 else hexc("b9c2c9") if x + y < 19 else hexc("8a939a"))
    edges(img, (4, 4, 11, 11), hexc("f4f7f8"), hexc("6f777e"))
    for p in [(6, 7), (7, 6), (8, 7), (9, 8), (7, 8)]:
        img.putpixel(p, hexc("8a939a"))
    img.putpixel((3, 3), hexc("e6c56a"))                       # pin-1 marker
    return outline(img, hexc("10261a"))


def memory_chip():
    """Black DRAM package with silver legs along the top and bottom edges."""
    img = blank()
    for x in range(2, 14, 2):
        for y in (3, 4, 11, 12):
            img.putpixel((x, y), hexc("dfe5e9") if y in (3, 12) else hexc("9aa3ab"))
    for y in range(5, 11):
        for x in range(1, 15):
            img.putpixel((x, y), hexc("3a3d42") if y == 5 else hexc("15161a") if y == 10 else hexc("24262a"))
    img.putpixel((2, 7), hexc("4a4d52"))                       # pin-1 dimple
    for x in range(6, 12):
        img.putpixel((x, 7), hexc("5a5e64") if x % 2 else hexc("44474c"))
    return outline(img, hexc("0c0d0f"))


def power_transistor():
    """TO-247: metal tab with mounting hole, black body, three legs."""
    img = blank()
    for y in range(1, 6):
        for x in range(4, 12):
            img.putpixel((x, y), hexc("eef2f4") if x == 4 or y == 1 else hexc("b9c2c9") if x < 10 else hexc("8a939a"))
    for p in [(7, 2), (8, 2), (7, 3), (8, 3)]:
        img.putpixel(p, CLEAR)
    for y in range(6, 12):
        for x in range(3, 13):
            img.putpixel((x, y), hexc("3a3d42") if x == 3 or y == 6 else hexc("15161a") if x == 12 or y == 11 else hexc("24262a"))
    for x, y in [(6, 8), (7, 8), (8, 8), (9, 8)]:
        img.putpixel((x, y), hexc("44474c"))
    for x in (5, 8, 11):
        for y in range(12, 16):
            img.putpixel((x - 1 if x == 11 else x, y), hexc("dfe5e9") if y < 14 else hexc("b9c2c9"))
    return outline(img, hexc("0c0d0f"))


def sensor_chip():
    """Purple ceramic package with a glass window over a reflective die."""
    img = blank()
    for y in range(2, 14):
        for x in range(2, 14):
            c = hexc("7a5a9a") if x + y < 14 else hexc("63487e")
            if x in (2, 13) or y in (2, 13):
                c = hexc("9a7ab8") if x == 2 or y == 2 else hexc("4a3462")
            img.putpixel((x, y), c)
    for i in range(4, 12, 2):
        for p in [(i, 3), (i, 12), (3, i), (12, i)]:
            img.putpixel(p, hexc("e6c56a"))
    for y in range(5, 11):
        for x in range(5, 11):
            img.putpixel((x, y), hexc("2a3a5a"))
    for y in range(6, 10):
        for x in range(6, 10):
            img.putpixel((x, y), hexc("9ad8ff") if x + y < 14 else hexc("5a8ac8"))
    img.putpixel((6, 6), hexc("ffffff"))
    img.putpixel((7, 6), hexc("dff4ff"))
    edges(img, (5, 5, 10, 10), hexc("1a2236"), hexc("c8d4e8"))
    return outline(img, hexc("241a30"))


PHOTOMASK_PATTERNS = {
    "logic": ("e6c56a", ["##.##.##", "##.##.##", "........", "##.##.##", "##.##.##", "........", "##.##.##", "##.##.##"]),
    "memory": ("5a8cff", ["########", "........", "########", "........", "########", "........", "########", "........"]),
    "power": ("ff8a3a", ["###..###", "###..###", "###..###", "........", "........", "###..###", "###..###", "###..###"]),
    "sensor": ("b06aff", ["########", "#......#", "#.####.#", "#.#..#.#", "#.#..#.#", "#.####.#", "#......#", "########"]),
}


def photomask(kind):
    """Quartz photomask: chrome border, pale clear glass, a tinted pattern per type."""
    tint, pattern = PHOTOMASK_PATTERNS[kind]
    img = blank()
    for y in range(1, 15):
        for x in range(1, 15):
            k = min(x - 1, y - 1, 14 - x, 14 - y)
            if k < 2:
                c = hexc("dfe5e9") if x + y < 15 else hexc("8a939a")
                if k == 1:
                    c = shade(c, 0.85)
            else:
                c = hexc("e8f6fa", 220) if (x + y) % 7 else hexc("ffffff", 230)
            img.putpixel((x, y), c)
    pixmap(img, pattern, {"#": hexc(tint)}, 4, 4)
    return outline(img, hexc("3a4148"))


def phase_h():
    save(laser_engraver_side(), "block/laser_engraver_side.png")
    save(laser_engraver_front(), "block/laser_engraver_front.png")
    save(laser_engraver_front(on=True), "block/laser_engraver_front_on.png")
    save(laser_engraver_bed(), "block/laser_engraver_bed.png")
    save(laser_engraver_gantry(), "block/laser_engraver_gantry.png")
    save(laser_head_entity(), "entity/laser_head.png")
    save(gui_laser_engraver(), "gui/laser_engraver.png")
    save(silicon_wafer(), "item/silicon_wafer.png")
    save(printed_circuit_board(), "item/printed_circuit_board.png")
    save(microprocessor(), "item/microprocessor.png")
    save(memory_chip(), "item/memory_chip.png")
    save(power_transistor(), "item/power_transistor.png")
    save(sensor_chip(), "item/sensor_chip.png")
    for kind in PHOTOMASK_PATTERNS:
        save(photomask(kind), f"item/{kind}_photomask.png")


# ---------------------------------------------------------------- big mining drill bit

def mining_drill_bit(frames=1, shift=0):
    """Conical auger bit: hardened steel with two bright helical flutes and carbide edges. Each frame
    slides the flutes along U so a strip of frames reads as the bit spinning."""
    img = Image.new("RGBA", (16, 16 * frames))
    steel = [hexc("2b3035"), hexc("3d444b"), hexc("58616a"), hexc("7d8790"), hexc("aab4bc"), hexc("dfe6ea")]
    for f in range(frames):
        off = f * shift
        for y in range(16):
            for x in range(16):
                # Helix: bands at 45 degrees with period 8 (two flutes per face).
                p = (x + y + off) % 8
                c = steel[1]
                if p in (0, 1):
                    c = steel[5] if p == 0 else steel[4]      # bright cutting edge
                elif p in (2, 3):
                    c = steel[3]
                elif p == 4:
                    c = steel[2]
                elif p == 7:
                    c = steel[0]                              # deep flute shadow
                # Light from the top of the face.
                c = shade(c, 1.08 - y * 0.015)
                img.putpixel((x, f * 16 + y), c)
    return img


def mining_drill_body():
    """Heavy industrial housing: safety-orange cast shell with deep cooling fins and a black hazard band."""
    img = Image.new("RGBA", (16, 16))
    base = [hexc("8a3f0c"), hexc("b85a14"), hexc("e07a1e"), hexc("f29a3c"), hexc("ffc070")]
    for y in range(16):
        for x in range(16):
            c = base[2]
            if y in (0, 15) or x in (0, 15):
                c = base[1]
            if y == 1 or x == 1:
                c = base[3]
            if 4 <= y <= 10 and x % 3 == 0 and 2 <= x <= 13:
                c = hexc("2a2420")                            # cooling fin gaps
            elif 4 <= y <= 10 and x % 3 == 1 and 2 <= x <= 13:
                c = base[3]
            if y in (12, 13):
                c = hexc("f2c230") if (x // 2) % 2 == 0 else hexc("202020")   # hazard band
            img.putpixel((x, y), c)
    return img


def phase_drill_big():
    save(mining_drill_bit(), "item/model/mining_drill_bit.png")
    save(mining_drill_bit(8, 1), "item/model/mining_drill_bit_running.png")
    write_mcmeta("item/model/mining_drill_bit_running.png", 1)
    save(mining_drill_body(), "item/model/mining_drill_body.png")


if __name__ == "__main__":
    save(side(), "block/coal_turbine_side.png")
    save(top(), "block/coal_turbine_top.png")
    save(bottom(), "block/coal_turbine_bottom.png")
    save(front(), "block/coal_turbine_front.png")
    save(front_on(), "block/coal_turbine_front_on.png")
    save(gui(), "gui/coal_turbine.png")
    save(turbine_back_inner(), "block/coal_turbine_back_inner.png")
    save(turbine_frame(), "block/coal_turbine_frame.png")

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
    phase_b()
    phaseC_F()
    print("textures written to", os.path.normpath(ROOT))
