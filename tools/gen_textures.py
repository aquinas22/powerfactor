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


def fluid_pipe():
    return pipe_tile(BRASS_P["3"], BRASS_P["1"], BRASS_P["5"], [BRASS_P["4"], BRASS_P["2"], hexc("5a4512")])


def item_pipe():
    return pipe_tile(hexc("7d858c"), hexc("b9c2c9"), hexc("4f565c"), [hexc("5a6168"), hexc("9aa3ab"), hexc("2b2e31")],
                     inner=hexc("2b2e31"))


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
    slot(d, 47, 52)
    slot(d, 116, 35)
    pixmap(img, ARROW, {"#": hexc("8b8b8b")}, 79, 34)
    # Faint electrode silhouette in the electrode slot.
    for y in range(53, 67):
        for x in (54, 55):
            if y <= 56 and y % 2 == 0:
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
    save(fluid_pipe(), "block/fluid_pipe.png")
    save(item_pipe(), "block/item_pipe.png")
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
    main, accent = (hexc(h) for h in MODULE_COLORS[kind])
    pal = {"g": BRASS_P["2"], "G": BRASS_P["4"], "1": hexc("5a6168"), "2": hexc("3b4146"),
           "3": hexc("1b1d20"), "D": hexc("2b2f33"), "h": hexc("2b2f33"), "L": main}
    img = pixmap(blank(), MODULE, pal)
    for y in range(4, 12):
        for x in range(4, 12):
            img.putpixel((x, y), hexc("15171a"))
    for y, row in enumerate(MODULE_EMBLEMS[kind]):
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


def wind_base():
    img = Image.new("RGBA", (16, 16))
    prof = [0.86, 0.94, 0.98, 1.0, 1.0, 0.99, 0.97, 0.95, 0.93, 0.91, 0.89, 0.87, 0.85, 0.83, 0.8, 0.74]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hexc("f2f4f4"), prof[x]))
    for x in range(16):
        img.putpixel((x, 15), shade(hexc("f2f4f4"), prof[x] * 0.88))
    for x in (2, 6, 10, 14):
        img.putpixel((x, 14), hexc("b9c2c9"))
    return img


def wind_nacelle():
    img = painted(180, hexc("eef0f0"))
    for x in range(1, 15):
        img.putpixel((x, 5), hexc("c3c8ca"))
        img.putpixel((x, 11), hexc("c3c8ca"))
    for y in range(7, 10):
        for x in range(10, 14):
            img.putpixel((x, y), hexc("5a6168") if y % 2 else hexc("a8b1b8"))
    for x in range(2, 7):
        img.putpixel((x, 8), hexc("2a7ad0"))
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


def cable_tile(jacket, light, dark, stripe=None, band=None):
    """Thin cable models sample a central window on either axis, so the tile is transpose-symmetric."""
    img = Image.new("RGBA", (16, 16))
    r = random.Random(200)
    for y in range(16):
        for x in range(16):
            c = shade(jacket, 1 + r.uniform(-0.05, 0.05))
            for p in (x, y):
                if p in (5, 6):
                    c = light
                elif p in (9, 10):
                    c = dark
            for p in (x, y):
                if stripe and p == 8:
                    c = stripe
                if band and p in (1, 2, 13, 14):
                    c = band
            img.putpixel((x, y), c)
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


def wind_turbine_blades():
    img = Image.new("RGBA", (64, 64), CLEAR)

    def hub(face, fw, fh):
        t = Image.new("RGBA", (fw, fh), hexc("eef0f0"))
        face_edges(t, hexc("b9c2c9"))
        if face == "north":
            for y in range(fh):
                for x in range(fw):
                    if abs(x + 0.5 - fw / 2) + abs(y + 0.5 - fh / 2) <= 2.2:
                        t.putpixel((x, y), hexc("d03a2f") if x + y < fw else hexc("9a2a20"))
        return t

    def blade(face, fw, fh):
        t = Image.new("RGBA", (fw, fh))
        for y in range(fh):
            for x in range(fw):
                c = hexc("f4f6f6") if face in ("north", "south", "top", "bottom") else hexc("c3c8ca")
                if face == "east":
                    c = hexc("9aa3ab")          # leading edge
                if face in ("north", "south", "east", "west") and y in (1, 2, 4, 5):
                    c = hexc("d03a2f")          # tip stripes (max-Y end of the box)
                t.putpixel((x, y), c)
        return t

    box_uv(img, 0, 0, 6, 6, 4, hub)
    box_uv(img, 0, 16, 2, 32, 1, blade)
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
    save(wind_base(), "block/wind_turbine_base.png")
    save(wind_nacelle(), "block/wind_turbine_nacelle.png")
    save(condenser_side(), "block/synchronous_condenser_side.png")
    save(condenser_end(), "block/synchronous_condenser_end.png")
    save(cable_tile(hexc("1c1c1e"), hexc("3a3a3e"), hexc("0c0c0d"), stripe=COPPER_P["2"]), "block/insulated_copper_cable.png")
    save(cable_tile(hexc("a8b1b8"), hexc("d4dbe0"), hexc("7c858c"), band=hexc("f0c020")), "block/aluminum_cable.png")
    save(hv_pylon(), "block/hv_pylon.png")
    save(wind_turbine_blades(), "entity/wind_turbine_blades.png")
    save(hydro_runner(), "entity/hydro_runner.png")
    save(condenser_rotor(), "entity/condenser_rotor.png")
    save(conductor_spool(), "item/conductor_spool.png")
    save(handbook(), "item/powerfactor_handbook.png")
    save(gui_battery_bank(), "gui/battery_bank.png")
    save(gui_diesel_generator(), "gui/diesel_generator.png")
    save(gui_power_meter(), "gui/power_meter.png")


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
    phase_b()
    phaseC_F()
    print("textures written to", os.path.normpath(ROOT))
