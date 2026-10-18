"""Connected textures for formed multiblocks: one seamless shell instead of a grid of cubes.

Each connected block gets a 128x128 sheet of sixteen 16x16 cells (each padded by an 8 px gutter
of its own edge pixels), one per combination of the face's four edges. A cell draws trim only on edges whose neighbour is outside the structure, so trim runs
round the silhouette of the whole machine and the panels between join up. Fills tile every 16
pixels, so seams, hazard bands and energy lines run straight across block boundaries.

Cell index: bit 1 = top edge connected, 2 = right, 4 = bottom, 8 = left; slot (i % 4, i // 4).

The blocks carry six booleans (down, up, north, south, west, east) set by Connected.java; each of
their 64 formed models picks the right cell for every face. Run directly for textures, or call
generate_data(globals()) from gen_data.py for models and blockstates.
"""
import math
import os
import random

from PIL import Image

from quantum_art import C, COPPER, FUSION_CYAN, HEAT, STEEL, mix, new, save, save_anim

B = "block/ctm/"
TOP, RIGHT, BOTTOM, LEFT = 1, 2, 4, 8


# ---------------------------------------------------------------- painting helpers
def noise(base, var, seed):
    r = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-var, var)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    return img


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def bevel(rows):
    """Trim from a list of (top/left colour, bottom/right colour) pairs, outermost first."""
    def trim(edge, depth, along):
        if depth >= len(rows):
            return None
        return rows[depth][0 if edge in "tl" else 1]
    return trim


def hazard_trim(rows, light, dark, step, depth_px=3):
    """Bevelled trim whose bottom edge is a diagonal hazard band, so a machine's foot is striped all round."""
    base = bevel(rows)

    def trim(edge, depth, along):
        if edge == "b":
            if depth < depth_px:
                return light if ((along + depth) // step) % 2 == 0 else dark
            if depth == depth_px:
                return rows[0][1]
            return None
        return base(edge, depth, along)
    return trim


def cell(fill, mask, trim, bolt=None, extra=None):
    """One 16x16 cell: the fill, trim on every open edge (corners mitred), bolts in open corners."""
    img = fill.copy()
    open_ = {"t": not mask & TOP, "r": not mask & RIGHT, "b": not mask & BOTTOM, "l": not mask & LEFT}
    for y in range(16):
        for x in range(16):
            dist = {"t": y, "l": x, "b": 15 - y, "r": 15 - x}
            near = [(d, e) for e, d in dist.items() if open_[e]]
            if not near:
                continue
            dmin = min(d for d, _ in near)
            cols = [trim(e, dmin, x if e in "tb" else y) for d, e in near if d == dmin]
            cols = [c for c in cols if c is not None]
            if not cols:
                continue
            c = cols[0] if len(cols) == 1 else mix(cols[0], cols[1], 0.5)
            img.putpixel((x, y), c)
    if bolt:
        width, colours = bolt
        for (cx, cy, a, b) in ((width, width, "t", "l"), (15 - width, width, "t", "r"),
                               (width, 15 - width, "b", "l"), (15 - width, 15 - width, "b", "r")):
            if open_[a] and open_[b]:
                px(img, cx, cy, colours[0])
                px(img, cx + (1 if cx < 8 else -1), cy + (1 if cy < 8 else -1), colours[1])
    if extra:
        extra(img, mask)
    return img


SLOT, GUTTER = 32, 8


def padded(img):
    """A 16x16 cell in a 32x32 slot, its edge pixels repeated into an 8 px gutter: filtering and mipmaps at a distance
    then only ever reach the cell's own edge colour, never the neighbouring cell's trim."""
    out = new(SLOT, SLOT)
    for y in range(SLOT):
        for x in range(SLOT):
            out.putpixel((x, y), img.getpixel((min(15, max(0, x - GUTTER)), min(15, max(0, y - GUTTER)))))
    return out


def sheet(fill, trim, bolt=None, extra=None):
    out = new(4 * SLOT, 4 * SLOT)
    for i in range(16):
        out.paste(padded(cell(fill, i, trim, bolt, extra)), ((i % 4) * SLOT, (i // 4) * SLOT))
    return out


def glint(colour, length=4, start=(3, 9)):
    """A single glint for the top-left pane of each window, not every pane."""
    def draw(img, mask):
        if mask & (TOP | LEFT):
            return
        x0, y0 = start
        for k in range(length):
            px(img, x0 + k, y0 - k, colour)
    return draw


def pulse(x, t, period=16, width=3.0):
    """0..1 brightness of an energy pulse travelling along a line; tiles every 16 px."""
    d = (x - t) % period
    return max(0.0, 1 - min(d, period - d) / width)


# ---------------------------------------------------------------- black hole
VIOLET = [C("140e20"), C("2a2142"), C("3c2e5c"), C("523c86"), C("7a5ab8"), C("b18cff"), C("e8dcff")]
CONTAINMENT_TRIM = bevel([(VIOLET[4], C("0c0912")), (VIOLET[2], VIOLET[1]), (C("120e1a"), C("2e2640"))])
CONTAINMENT_BOLT = (2, (VIOLET[5], VIOLET[0]))


def containment_fill(frame):
    """Near-black alloy with a violet field lattice; pulses run along it while the field is up."""
    img = noise((30, 26, 40), 0.08, 91)
    t = frame * 2
    for i in range(16):
        for (x, y, along) in ((i, 7, i), (7, i, i)):
            k = pulse(along, t) if x != 7 or y != 7 else 1
            px(img, x, y, mix(VIOLET[3], VIOLET[5], k))
        px(img, i, 8, VIOLET[1])
        px(img, 8, i, VIOLET[1])
    px(img, 7, 7, VIOLET[6])
    px(img, 8, 8, VIOLET[2])
    return img


def coil_fill(frame):
    img = noise((140, 80, 40), 0.06, 92)
    for y in range(16):
        for x in range(16):
            if y % 3 == 0:
                px(img, x, y, C("6a3a1a"))
            elif y % 3 == 1:
                px(img, x, y, C("e8a05a"))
    for x in range(16):
        v = 0.5 + 0.5 * math.sin(2 * math.pi * (x / 16 + frame / 8))
        px(img, x, 7, mix(C("6a4aa0"), C("e8dcff"), v))
        px(img, x, 8, mix(C("4a2e8a"), C("b18cff"), v))
    return img


COIL_TRIM = bevel([(VIOLET[4], C("0c0912")), (C("1e1a28"), C("1e1a28"))])


def horizon_glass_fill():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (60, 40, 90, 28))
    return img


HORIZON_TRIM = bevel([(C("9a7ad8"), C("3a2a60")), (C("5a4290"), C("4a3478"))])


# ---------------------------------------------------------------- fission
REACTOR = (58, 62, 70)   # dark gunmetal
REACTOR_ROWS = [(C("7c848e"), C("121418")), (C("50565e"), C("2a2e34")), (C("1e2125"), C("646a72"))]
REACTOR_TRIM = bevel(REACTOR_ROWS)
REACTOR_SIDE_TRIM = hazard_trim(REACTOR_ROWS, C("e8bc2a"), C("141619"), 2, 4)
REACTOR_BOLT = (3, (C("a8b0b8"), C("0e1012")))


def brushed(base, seed, var=0.05, streak=0.07):
    """Brushed plate: each row has its own tone, so the grain runs straight across joined blocks."""
    r = random.Random(seed)
    rows = [1 + r.uniform(-streak, streak) for _ in range(16)]
    img = new()
    for y in range(16):
        for x in range(16):
            k = rows[y] * (1 + r.uniform(-var, var))
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    return img


def reactor_side():
    """Heavy brushed plate; the yellow-black band runs round the reactor's foot (in the trim)."""
    return brushed(REACTOR, 81)


def reactor_top():
    """Roof deck: tread plate."""
    img = noise(REACTOR, 0.05, 82)
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 8 == 0 and x % 4 in (1, 2):
                px(img, x, y, C("6e7680"))
                px(img, x + 1, y + 1, C("202328"))
    return img


def reactor_glass_fill():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), (120, 170, 205, 40))
    return img


REACTOR_GLASS_TRIM = bevel([(C("6a7078"), C("1e2226")), (C("3e444c"), C("2c3036"))])


# ---------------------------------------------------------------- fusion
FUSION_TRIM = bevel([(STEEL[5], STEEL[1]), (STEEL[4], STEEL[2])])
FUSION_BOLT = (2, (STEEL[0], STEEL[6]))


def fusion_side(frame):
    """Shielding plate; the cyan containment seam runs right round the reactor, pulsing."""
    img = new()
    for y in range(16):
        for x in range(16):
            px(img, x, y, STEEL[3] if (x // 4 + y // 4) % 2 == 0 else STEEL[2])
    for x in range(16):
        px(img, x, 7, STEEL[1])
        px(img, x, 8, mix(FUSION_CYAN[2], FUSION_CYAN[4], pulse(x, frame * 2, width=4)))
        px(img, x, 9, STEEL[4])
    return img


def fusion_top():
    img = new()
    for y in range(16):
        for x in range(16):
            px(img, x, y, STEEL[3] if (x // 4 + y // 4) % 2 == 0 else STEEL[2])
    return img


def magnet_fill(frame):
    """Toroidal coil windings, the field line running unbroken through every magnet."""
    img = new()
    for y in range(16):
        for x in range(16):
            band = y % 3
            c = COPPER[4] if band == 0 else COPPER[3] if band == 1 else COPPER[1]
            if (x + y) % 7 == 0:
                c = COPPER[5]
            px(img, x, y, c)
    for x in range(16):
        px(img, x, 7, STEEL[1])
        px(img, x, 8, mix(FUSION_CYAN[2], FUSION_CYAN[4], pulse(x, frame * 2, width=5)))
    return img


MAGNET_TRIM = bevel([(STEEL[5], STEEL[1]), (STEEL[3], STEEL[2])])
FUSION_GLASS_TRIM = bevel([(STEEL[4], STEEL[1]), (C("aef2ff"), C("5fb8cc"))])


# ---------------------------------------------------------------- starforge
def starforge_fill(seam=True):
    """Recessed dark web with a continuous truss brace and (on the sides) the heat seam."""
    img = new()
    for y in range(16):
        for x in range(16):
            px(img, x, y, STEEL[1] if (x + y) % 5 else STEEL[0])
    for i in range(16):
        px(img, i, i, STEEL[4])
        px(img, (i + 1) % 16, i, STEEL[2])
        px(img, i, (i + 1) % 16, STEEL[5])
    for x in range(16 if seam else 0):
        px(img, x, 12, HEAT[1] if x % 3 else HEAT[2])
    return img


STARFORGE_TRIM = bevel([(STEEL[5], STEEL[1]), (STEEL[4], STEEL[4]), (STEEL[5], STEEL[3])])
STARFORGE_BOLT = (1, (STEEL[0], STEEL[6]))


# ---------------------------------------------------------------- refinery and pumpjack
def deck(base, seed, hi, lo):
    """Tread-plate deck that tiles."""
    img = noise(base, 0.05, seed)
    for y in range(16):
        for x in range(16):
            if (x + 2 * y) % 8 == 0 and x % 4 in (1, 2):
                px(img, x, y, hi)
                px(img, x + 1, y + 1, lo)
    return img


REFINERY = (74, 82, 90)
REFINERY_ROWS = [(C("8a949c"), C("23292e")), (C("6f7880"), C("39414a")), (C("2c3238"), C("5a646c"))]
REFINERY_TRIM = hazard_trim(REFINERY_ROWS, C("f2c230"), C("1e1e1e"), 2)


def refinery_side():
    """Riveted steel with the teal status seam lit along the whole base."""
    img = noise(REFINERY, 0.06, 4801)
    for x in range(16):
        px(img, x, 6, C("39414a"))
        px(img, x, 7, C("2fd0a8") if x % 4 else C("7cffd0"))
        px(img, x, 8, C("1f8a6a"))
        px(img, x, 9, C("6f7880"))
    return img


PUMPJACK = (72, 85, 99)
PUMPJACK_ROWS = [(C("899aa5"), C("1f2a32")), (C("5f6f7b"), C("2e3a44")), (C("28343d"), C("56656f"))]
PUMPJACK_TRIM = hazard_trim(PUMPJACK_ROWS, C("e7bb43"), C("252a31"), 2)


def pumpjack_side():
    img = noise(PUMPJACK, 0.06, 4810)
    for y in (4, 7, 10):
        for x in range(16):
            px(img, x, y, C("1f2a32"))
            px(img, x, y + 1, C("5f6f7b"))
    return img


# ---------------------------------------------------------------- the blocks
def sheets():
    """Texture name -> list of 64x64 frames."""
    out = {
        "containment_casing": [sheet(containment_fill(f), CONTAINMENT_TRIM, CONTAINMENT_BOLT) for f in range(8)],
        "containment_coil": [sheet(coil_fill(f), COIL_TRIM) for f in range(8)],
        "event_horizon_glass": [sheet(horizon_glass_fill(), HORIZON_TRIM, extra=glint((220, 200, 255, 110)))],
        "reactor_casing": [sheet(reactor_side(), REACTOR_SIDE_TRIM, REACTOR_BOLT)],
        "reactor_casing_top": [sheet(reactor_top(), REACTOR_TRIM, REACTOR_BOLT)],
        "reactor_glass": [sheet(reactor_glass_fill(), REACTOR_GLASS_TRIM, extra=glint((230, 245, 255, 150), 5, (3, 10)))],
        "fusion_casing": [sheet(fusion_side(f), FUSION_TRIM, FUSION_BOLT) for f in range(8)],
        "fusion_casing_top": [sheet(fusion_top(), FUSION_TRIM, FUSION_BOLT)],
        "fusion_magnet": [sheet(magnet_fill(f), MAGNET_TRIM) for f in range(8)],
        "fusion_glass": [sheet(new(), FUSION_GLASS_TRIM, extra=glint(C("e6fcff"), 4, (3, 7)))],
        "starforge_frame": [sheet(starforge_fill(), STARFORGE_TRIM, STARFORGE_BOLT)],
        "starforge_frame_top": [sheet(starforge_fill(False), STARFORGE_TRIM, STARFORGE_BOLT)],
        "refinery_casing": [sheet(refinery_side(), REFINERY_TRIM, (3, (C("b9c2c9"), C("2c3238"))))],
        "refinery_casing_top": [sheet(deck(REFINERY, 4802, C("8a949c"), C("2c3238")), bevel(REFINERY_ROWS), (3, (C("b9c2c9"), C("2c3238"))))],
        "pumpjack_casing": [sheet(pumpjack_side(), PUMPJACK_TRIM, (3, (C("c8d2d8"), C("1f2a32"))))],
        "pumpjack_casing_top": [sheet(deck(PUMPJACK, 4811, C("899aa5"), C("28343d")), bevel(PUMPJACK_ROWS), (3, (C("c8d2d8"), C("1f2a32"))))],
    }
    return out


def generate_textures():
    for name, frames in sheets().items():
        if len(frames) == 1:
            save(frames[0], B + name + ".png")
        else:
            save_anim(frames, B + name + ".png", 3)


# block id -> (side sheet, top/bottom sheet or None, unformed model, render type, light emission)
BLOCKS = {
    "containment_casing": ("containment_casing", None, "containment_casing", "minecraft:solid", 0),
    "containment_coil": ("containment_coil", None, "containment_coil", "minecraft:solid", 10),
    "event_horizon_glass": ("event_horizon_glass", None, "event_horizon_glass", "minecraft:translucent", 4),
    "reactor_casing": ("reactor_casing", "reactor_casing_top", "reactor_casing", "minecraft:solid", 0),
    "reactor_glass": ("reactor_glass", None, "reactor_glass", "minecraft:translucent", 0),
    "fusion_casing": ("fusion_casing", "fusion_casing_top", "fusion_casing", "minecraft:solid", 0),
    "fusion_magnet": ("fusion_magnet", None, "fusion_magnet", "minecraft:solid", 9),
    "fusion_glass": ("fusion_glass", None, "fusion_glass", "minecraft:cutout", 0),
    "starforge_frame": ("starforge_frame", "starforge_frame_top", "starforge_frame", "minecraft:cutout", 0),
    "refinery_casing": ("refinery_casing", "refinery_casing_top", "refinery_casing", "minecraft:solid", 0),
    "pumpjack_casing": ("pumpjack_casing", "pumpjack_casing_top", "pumpjack_casing", "minecraft:solid", 0),
}

# Texture-space edges (top, right, bottom, left) of each face, as the neighbour direction each borders.
FACE_EDGES = {
    "north": ("up", "west", "down", "east"),
    "south": ("up", "east", "down", "west"),
    "east": ("up", "north", "down", "south"),
    "west": ("up", "south", "down", "north"),
    "up": ("north", "east", "south", "west"),
    "down": ("south", "east", "north", "west"),
}
DIRS = ("down", "up", "north", "south", "west", "east")


def cell_uv(connected, face):
    t, r, b, l = (connected[d] for d in FACE_EDGES[face])
    i = (TOP if t else 0) | (RIGHT if r else 0) | (BOTTOM if b else 0) | (LEFT if l else 0)
    # Sheet is 128 px = 16 UV units; each cell sits 8 px (1 unit) into its 32 px (4 unit) slot.
    x, y = (i % 4) * 4 + 1, (i // 4) * 4 + 1
    return [x, y, x + 2, y + 2]


def generate_data(g):
    write, asset, pf = g["write"], g["asset"], g["pf"]
    for block, (side, top, unformed, render, glow) in BLOCKS.items():
        variants = {"formed=false": {"model": pf("block/" + unformed)}}
        for mask in range(64):
            connected = {d: bool(mask >> i & 1) for i, d in enumerate(DIRS)}
            faces = {}
            for face in FACE_EDGES:
                tex = "top" if top and face in ("up", "down") else "side"
                faces[face] = {"texture": "#" + tex, "uv": cell_uv(connected, face), "cullface": face}
            element = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
            if glow:
                element["light_emission"] = glow
            elements = [element]
            textures = {"side": pf(B + side), "particle": pf("block/" + unformed)}
            if top:
                textures["top"] = pf(B + top)
            if block == "starforge_frame":
                # The lit girder belt of the formed frame, as before.
                textures["lit"] = pf("block/storage_casing_lit")
                belt = {f: {"texture": "#lit", "uv": [0, 7.5, 16, 8.5]} for f in ("north", "south", "east", "west")}
                elements.append({"from": [-0.01, 7.5, -0.01], "to": [16.01, 8.5, 16.01], "faces": belt, "light_emission": 12})
            model = "ctm/" + block + "_" + format(mask, "02d")
            write(asset("models", "block", model + ".json"), {"parent": "minecraft:block/block", "render_type": render,
                                                               "textures": textures, "elements": elements})
            key = ",".join(f"{d}={str(connected[d]).lower()}" if d != "formed" else "formed=true"
                           for d in sorted(DIRS + ("formed",)))
            variants[key] = {"model": pf("block/" + model)}
        write(asset("blockstates", block + ".json"), {"variants": variants})


if __name__ == "__main__":
    generate_textures()
    print("Connected textures written.")
