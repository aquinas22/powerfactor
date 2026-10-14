"""Quantum-logistics storage family: casings, drive bays, terminals, cells and patterns.

Hand-placed 16x16 pixel art in the graphite / cyan / violet palette. Run directly or call
generate_textures() from gen_textures.py.
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                    "assets", "powerfactor", "textures")


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


def alpha(c, a):
    return c[:3] + (a,)


# ---------------------------------------------------------------- palettes
G0, G1, G2, G3, G4, G5 = (C(h) for h in ("0e1418", "161d23", "1e272e", "26313a", "2d3943", "34424d"))
GHL = C("4a5a66")      # chamfer highlight
GSPEC = C("62737f")    # screw / specular
VOID = C("06080a")
SLOT = C("07090b")

C0, C1, C2, C3, C4 = (C(h) for h in ("0d3a48", "1a6f88", "2fb4d6", "5fe0ff", "b8f4ff"))
V0, V1, V2, V3, V4 = (C(h) for h in ("2a1a52", "4a2f8f", "6f52d6", "9c7cff", "d9c8ff"))
A0, A1, A2, A3, A4 = (C(h) for h in ("5a3410", "a8641e", "e08a2a", "ffb347", "ffe0a8"))
WHITE = C("ffffff")
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------- primitives
def new(w=16, h=16, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):
    """Filled rectangle, inclusive corners."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def hline(img, x0, x1, y, c):
    rect(img, x0, y, x1, y, c)


def vline(img, x, y0, y1, c):
    rect(img, x, y0, x, y1, c)


def frame(img, x0, y0, x1, y1, light, dark, corner=None):
    """1px bevel: light top/left, dark bottom/right."""
    hline(img, x0, x1, y0, light)
    vline(img, x0, y0, y1, light)
    hline(img, x0, x1, y1, dark)
    vline(img, x1, y0, y1, dark)
    if corner is not None:
        px(img, x1, y0, corner)
        px(img, x0, y1, corner)


def sunken(img, x0, y0, x1, y1, fill, shadow=G0, lip=GHL):
    """Recess: dark top/left inner edge, lit lip bottom/right."""
    rect(img, x0, y0, x1, y1, fill)
    hline(img, x0, x1, y0 - 1, shadow)
    vline(img, x0 - 1, y0 - 1, y1, shadow)
    hline(img, x0 - 1, x1 + 1, y1 + 1, lip)
    vline(img, x1 + 1, y0, y1 + 1, lip)


def screw(img, x, y, light=GSPEC, dark=G0):
    px(img, x, y, light)
    px(img, x + 1, y + 1, dark)
    px(img, x + 1, y, mix(light, dark, .55))
    px(img, x, y + 1, mix(light, dark, .45))


def outline(img, color):
    """Vanilla item outline: paint transparent pixels touching opaque ones."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and src.getpixel((nx, ny))[3] > 100:
                    img.putpixel((x, y), color)
                    break
    return img


def add_light(img, x, y, c, t):
    """Blend a light colour into an existing pixel (for scanlines / glows)."""
    if 0 <= x < img.width and 0 <= y < img.height:
        p = img.getpixel((x, y))
        img.putpixel((x, y), tuple(min(255, int(p[i] + (c[i] - p[i]) * t)) for i in range(3)) + (p[3],))


def save(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)
    return full


def save_anim(frames, path, frametime):
    strip = new(frames[0].width, frames[0].height * len(frames))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * f.height))
    full = save(strip, path)
    with open(full + ".mcmeta", "w") as fh:
        json.dump({"animation": {"frametime": frametime}}, fh)


# ---------------------------------------------------------------- shared bases
def graphite(base=G3, lower=None, seam=None):
    """Chamfered graphite plate. Optional two-tone lower plate with a seam row."""
    img = new(fill=base)
    if lower is not None and seam is not None:
        rect(img, 1, seam + 1, 14, 14, lower)
        hline(img, 1, 14, seam, G1)
        hline(img, 2, 13, seam + 1, mix(lower, G5, .5))
    # inner soft bevel
    hline(img, 1, 14, 1, G4)
    vline(img, 1, 1, 14, G4)
    hline(img, 2, 14, 14, G2)
    vline(img, 14, 2, 14, G2)
    # outer chamfer
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    return img


def casing(lit=False):
    img = graphite(G3, C("232d36"), 6)
    screw(img, 2, 2)
    screw(img, 12, 2)
    # recessed inlay channel
    hline(img, 1, 14, 12, G0)
    hline(img, 1, 14, 14, G5)
    if lit:
        for x in range(1, 15):
            px(img, x, 13, C4 if 3 <= x <= 12 else (C3 if x in (2, 13) else C2))
        halo_in, halo_out = mix(G0, C2, .45), mix(G5, C3, .40)
        for x in range(2, 14):
            px(img, x, 12, halo_in if 3 <= x <= 12 else mix(G0, C2, .25))
            px(img, x, 14, halo_out if 3 <= x <= 12 else mix(G5, C3, .2))
        for x in range(4, 12):  # faint second ring of glow on the plate above
            add_light(img, x, 11, C2, .18)
    else:
        for x in range(1, 15):
            px(img, x, 13, C1 if x in (1, 14) else (C2 if x in (2, 13) else C3))
            if x in (1, 14):
                px(img, x, 13, C0)
    return img


def storage_glass():
    img = new()
    tint = C("9fd8ff")
    for y in range(16):
        for x in range(16):
            a = 60 - int((x + y) * 25 / 30)  # 60 top-left -> 35 bottom-right
            px(img, x, y, alpha(tint, a))
    frame(img, 0, 0, 15, 15, alpha(C("3b4954"), 205), alpha(G1, 210))
    px(img, 15, 0, alpha(G2, 205))
    px(img, 0, 15, alpha(G2, 205))
    # inner frame edge, slightly lit
    for i in range(1, 15):
        px(img, i, 1, alpha(mix(tint, WHITE, .3), 80))
        px(img, 1, i, alpha(mix(tint, WHITE, .3), 75))
    # reflection streaks
    for i in range(5):
        px(img, 3 + i, 7 - i, (255, 255, 255, 110))
    for i in range(3):
        px(img, 3 + i, 10 - i, (255, 255, 255, 85))
        px(img, 7 + i, 6 - i, (255, 255, 255, 70))
    px(img, 2, 8, (255, 255, 255, 60))
    return img


def vent():
    img = new(fill=G3)
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    vline(img, 1, 1, 14, G4)
    vline(img, 14, 1, 14, G2)
    # slot rows: void at 2,5,8,11 ... five slots need tight 2-row pitch at the end, so use
    # 1px void + 1px lip, separated by a single plate row: rows 1..14 exactly.
    for i in range(5):
        y = 1 + i * 3
        if i == 4:
            y = 13
        void_y, lip_y = y, y + 1
        hline(img, 3, 12, void_y, SLOT)
        px(img, 2, void_y, G0)
        px(img, 13, void_y, G1)
        hline(img, 3, 12, lip_y, C("53646f"))
        px(img, 2, lip_y, G4)
        px(img, 13, lip_y, G4)
    return img


def drive_bay_front():
    img = new(fill=G4)
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    vline(img, 1, 1, 14, G5)
    # divider
    vline(img, 8, 1, 14, G1)
    for y in range(1, 15):
        if y % 3 == 0:
            px(img, 8, y, G2)
    for x0 in (2, 9):
        for top in (1, 4, 7, 10, 13):
            rect(img, x0, top, x0 + 5, top + 1, VOID)
            px(img, x0, top, C("040506"))
            lip = top + 2
            if lip <= 15:
                hline(img, x0, x0 + 5, lip, C("566874") if lip < 15 else C("3c4954"))
            if top - 1 >= 1:
                hline(img, x0, x0 + 5, top - 1, G2)
    # tiny status pips on the bezel edges
    for top in (1, 4, 7, 10, 13):
        px(img, 1, top, C1)
    vline(img, 15, 0, 15, G0)
    return img


def terminal_frame():
    base = C("2c3842")
    img = new(fill=base)
    frame(img, 0, 0, 15, 15, C("566874"), G0, corner=G2)
    frame(img, 1, 1, 14, 14, C("3a4954"), G2)
    # recessed groove ring where the screen sits
    frame(img, 2, 2, 13, 13, G1, C("43535e"))
    rect(img, 3, 3, 12, 12, C("29343d"))
    screw(img, 2, 14 - 12, GSPEC)  # top-left
    px(img, 2, 2, G1)
    # status dot
    px(img, 13, 14, C3)
    px(img, 12, 14, C1)
    px(img, 14, 14, G1)
    px(img, 13, 13, mix(C("3a4954"), C2, .35))
    # small vent ticks bottom-left
    for x in (3, 5, 7):
        px(img, x, 14, G1)
    return img


def terminal_screen_off():
    img = new(fill=C("0a1218"))
    frame(img, 0, 0, 15, 15, C("05090c"), C("16222a"))
    for i in range(7):
        px(img, 3 + i, 9 - i, C("15232c"))
        px(img, 4 + i, 9 - i, C("111d25"))
    for i in range(3):
        px(img, 9 + i, 13 - i, C("111d25"))
    px(img, 2, 2, C("1a2a34"))
    return img


SCREEN_BG = C("061016")
SCREEN_BG2 = C("081822")


def screen_base(border=C2, corner=C1):
    img = new(fill=SCREEN_BG)
    for y in range(1, 15):
        if y % 2 == 0:
            hline(img, 1, 14, y, SCREEN_BG2)
    hline(img, 0, 15, 0, border)
    hline(img, 0, 15, 15, mix(border, C0, .4))
    vline(img, 0, 0, 15, border)
    vline(img, 15, 0, 15, mix(border, C0, .4))
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        px(img, x, y, corner)
    return img


ICONS = [C("e85d5d"), C("6fdc6f"), C("f0d264"), C("5f9bff"), C("c27cff"), C("f0985a"),
         C("dfe8ee"), C("4fe0c0"), C("b07a4a"), C("ff8fc8"), C("8a98a4"), C("b4f05a")]


def icon(img, x, y, c, bright=False):
    hi = mix(c, WHITE, .45)
    lo = mix(c, (0, 0, 0, 255), .45)
    if bright:
        c, hi, lo = mix(c, WHITE, .25), mix(c, WHITE, .65), c
    px(img, x, y, hi)
    px(img, x + 1, y, c)
    px(img, x, y + 1, c)
    px(img, x + 1, y + 1, lo)


def scanline(img, y, strength=.35, x0=1, x1=14, y0=1, y1=14, c=C3):
    for dy, t in ((0, strength), (-1, strength * .45), (-2, strength * .18)):
        yy = y + dy
        if y0 <= yy <= y1:
            for x in range(x0, x1 + 1):
                add_light(img, x, yy, c, t)


def grid_screen(frame_i):
    img = screen_base()
    # search field
    rect(img, 1, 1, 14, 2, C("0f3444"))
    hline(img, 1, 14, 3, C1)
    px(img, 2, 1, C4)       # magnifier
    px(img, 3, 2, C3)
    for x in (5, 6, 8):
        px(img, x, 2, C2)
    if (frame_i // 4) % 2 == 0:
        px(img, 10, 1, C4)
        px(img, 10, 2, C4)
    # scrollbar
    vline(img, 14, 4, 14, C("0c2430"))
    thumb = 5 + (frame_i // 8) * 3
    vline(img, 14, thumb, thumb + 3, C2)
    px(img, 14, thumb, C3)
    cells = [(c, r) for r in range(3) for c in range(4)]
    path = [(0, 0), (1, 0), (2, 0), (2, 1), (1, 1), (1, 2), (2, 2), (3, 2)]
    cur = path[frame_i // 2]
    for i, (c, r) in enumerate(cells):
        x, y = 2 + c * 3, 5 + r * 3
        icon(img, x, y, ICONS[i], bright=(c, r) == cur)
    # cursor ring (corners omitted for a soft bracket look)
    cx, cy = 2 + cur[0] * 3, 5 + cur[1] * 3
    ring = C4
    for x in range(cx, cx + 2):
        px(img, x, cy - 1, ring)
        px(img, x, cy + 2, C3)
    for y in range(cy, cy + 2):
        px(img, cx - 1, y, ring)
        px(img, cx + 2, y, C3)
    # status footer
    hline(img, 2, 5, 14, C1)
    px(img, 2, 14, C3)
    scanline(img, frame_i, .30)
    return img


def crafting_screen(frame_i):
    img = screen_base()
    # title bar
    rect(img, 1, 1, 14, 2, C("0f3444"))
    hline(img, 2, 5, 2, C2)
    px(img, 13, 1, A3)
    hline(img, 1, 14, 3, C1)
    # 3x3 grid of ingredient slots
    rect(img, 1, 5, 7, 11, C("0a2430"))
    lit = min(9, frame_i + 1)
    order = [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)]
    for i, (c, r) in enumerate(order):
        x, y = 2 + c * 2, 6 + r * 2
        px(img, x, y, (A3 if (c + r) % 2 == 0 else C3) if i < lit else C("164050"))
    # arrow, shimmering left->right once the grid is full
    pts = [(8, 8), (9, 8), (10, 8), (9, 6), (10, 7), (11, 8), (10, 9), (9, 10)]
    hot = 8 + (frame_i % 4)
    for (x, y) in pts:
        px(img, x, y, C4 if (frame_i >= 9 and x == hot) else C2)
    # result square, pulsing amber
    t = 0.5 + 0.5 * math.sin(frame_i / 16 * 2 * math.pi)
    frame(img, 12, 6, 14, 10, mix(A1, A4, t), mix(A0, A2, t))
    vline(img, 13, 7, 9, mix(A2, A4, t) if frame_i >= 9 else C("3a2410"))
    # progress bar
    hline(img, 2, 13, 13, C("0c2430"))
    fill = 2 + int(frame_i * 12 / 16)
    if fill > 2:
        hline(img, 2, fill - 1, 13, C2)
        px(img, fill - 1, 13, C4)
    scanline(img, (frame_i * 2) % 16, .18)
    return img


BP_BG, BP_GRID, BP_LINE, BP_HI = C("0b2a4a"), C("123a62"), C("6fb8e8"), C("e8f6ff")


def pattern_screen(frame_i):
    img = new(fill=BP_BG)
    for i in range(1, 15):
        if i % 4 == 0:
            hline(img, 1, 14, i, BP_GRID)
            vline(img, i, 1, 14, BP_GRID)
    frame(img, 0, 0, 15, 15, C("cfe9ff"), C("5f8fb8"))
    px(img, 15, 0, BP_LINE)
    px(img, 0, 15, BP_LINE)
    # corner registration ticks
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, BP_LINE)
    sweep = 3 + int(frame_i * 11 / 16)   # y of encoding line, 3..13
    glyph = [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2), (1, 0)]
    for idx in range(4):
        v = 3 + idx * 3
        hline(img, 3, 12, v, BP_LINE if v <= sweep else C("2b5f8e"))
        vline(img, v, 3, 12, BP_LINE if sweep >= 12 else C("2b5f8e"))
    for x in range(3, 13):
        for y in range(3, 13):
            if (x - 3) % 3 == 0 or (y - 3) % 3 == 0:
                px(img, x, y, BP_LINE if y <= sweep else C("2b5f8e"))
    for (c, r) in glyph:
        x, y = 4 + c * 3, 4 + r * 3
        if y + 1 <= sweep:
            rect(img, x, y, x + 1, y + 1, BP_HI)
            px(img, x + 1, y + 1, C3)
        elif y <= sweep + 2:
            rect(img, x, y, x + 1, y + 1, C("2f6f9e"))
    # sweep line
    if 1 <= sweep <= 14:
        hline(img, 1, 14, sweep, C4)
        for x in range(1, 15):
            add_light(img, x, sweep - 1, C3, .35)
            add_light(img, x, sweep + 1, C3, .15)
        px(img, 1, sweep, WHITE)
        px(img, 14, sweep, WHITE)
    return img


def port_body():
    img = new(fill=G3)
    grain = {2: (2, 9), 4: (6, 13), 6: (3, 7), 7: (9, 13), 9: (2, 6), 10: (8, 12), 12: (4, 11)}
    for y in range(2, 14):
        if y % 2 == 0:
            hline(img, 2, 13, y, C("293540"))
    for y, (a, b) in grain.items():
        hline(img, a, b, y, G4)
    for y, (a, b) in ((5, (10, 13)), (8, (3, 5)), (11, (9, 12)), (3, (11, 13))):
        hline(img, a, b, y, G2)
    frame(img, 1, 1, 14, 14, G5, G2)
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    return img


def face_plate():
    img = new(fill=G3)
    frame(img, 1, 1, 14, 14, G5, G1)
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    return img


def corner_clamps(img, metal=C("6b7c88"), hi=C("8ea0ac"), lo=G1):
    for (x, y, dx, dy) in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        px(img, x, y, hi)
        px(img, x + dx, y, metal)
        px(img, x, y + dy, metal)
        px(img, x + 2 * dx, y, lo if dy > 0 else metal)
        px(img, x, y + 2 * dy, lo if dx > 0 else metal)


def storage_bus_face():
    img = face_plate()
    sunken(img, 4, 4, 11, 11, C("0b1014"))
    # cyan rounded square ring
    for i in range(5, 11):
        px(img, i, 5, C3)
        px(img, 5, i, C3)
        px(img, i, 10, C2)
        px(img, 10, i, C2)
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(img, x, y, C("0b1014"))
    px(img, 6, 6, C1)
    px(img, 9, 9, C0)
    rect(img, 7, 7, 8, 8, C1)
    px(img, 7, 7, C4)
    corner_clamps(img)
    return img


def chevron(img, x0, y0, right, hi, mid, lo):
    """Thick chevron 4 wide x 5 tall pointing right (or left)."""
    rows = [(0, 1), (1, 2), (2, 3), (1, 2), (0, 1)]
    for r, (a, b) in enumerate(rows):
        for k in (a, b):
            x = x0 + (k if right else 3 - k)
            col = hi if r < 2 else (mid if r == 2 else lo)
            px(img, x, y0 + r, col)


def arrow_face(inward):
    img = face_plate()
    sunken(img, 3, 4, 12, 11, C("0b1014"))
    hub = (C3, C2, C1) if inward else (A4, A3, A1)
    # center port hole
    rect(img, 7, 7, 8, 8, VOID)
    px(img, 7, 6, hub[2]); px(img, 8, 6, hub[2])
    px(img, 7, 9, hub[2]); px(img, 8, 9, hub[2])
    px(img, 6, 7, hub[2]); px(img, 6, 8, hub[2])
    px(img, 9, 7, hub[2]); px(img, 9, 8, hub[2])
    if inward:
        chevron(img, 3, 5, True, C4, C3, C2)
        chevron(img, 9, 5, False, C4, C3, C2)
    else:
        chevron(img, 2, 5, False, A4, A3, A2)
        chevron(img, 10, 5, True, A4, A3, A2)
    corner_clamps(img)
    return img


def pcb(bg, trace, trace_dark, pin, chip_body, chip_hi, core):
    img = new(fill=bg)
    # traces (hand routed)
    paths = [
        [(2, 3), (4, 3), (4, 6)], [(2, 9), (3, 9), (3, 11), (6, 11)],
        [(13, 4), (11, 4), (11, 6)], [(13, 9), (12, 9), (12, 12), (9, 12)],
        [(6, 2), (6, 4)], [(9, 2), (9, 4)], [(6, 13), (6, 11)],
        [(13, 13), (11, 13)],
    ]
    for p in paths:
        for (a, b) in zip(p, p[1:]):
            for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
                for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
                    px(img, x, y, trace)
    # vias
    for x, y in ((2, 3), (2, 9), (13, 4), (13, 9), (13, 13), (6, 2), (9, 2)):
        px(img, x, y, trace_dark)
    for x, y in ((3, 6), (12, 7), (4, 13), (10, 2)):
        px(img, x, y, mix(bg, trace, .35))
    # chip
    rect(img, 5, 5, 10, 10, chip_body)
    hline(img, 5, 10, 5, chip_hi)
    vline(img, 5, 5, 10, chip_hi)
    hline(img, 6, 10, 10, mix(chip_body, (0, 0, 0, 255), .4))
    vline(img, 10, 6, 10, mix(chip_body, (0, 0, 0, 255), .4))
    for k in (6, 9):
        px(img, k, 4, pin); px(img, k, 11, mix(pin, bg, .3))
        px(img, 4, k, pin); px(img, 11, k, mix(pin, bg, .3))
    for k in (7, 8):
        px(img, k, 4, mix(pin, bg, .55)); px(img, 4, k, mix(pin, bg, .55))
    core(img)
    # graphite frame
    frame(img, 1, 1, 14, 14, G1, G4)
    frame(img, 0, 0, 15, 15, GHL, G0, corner=G2)
    return img


def crafting_processor_side():
    def core(img):
        rect(img, 7, 7, 8, 8, C1)
        px(img, 7, 7, C3)
        px(img, 6, 6, C("3a464f"))
    return pcb(C("0d1a14"), C("c9a24a"), C("6e5520"), C3, C("1b2228"), C("39454e"), core)


def quantum_processor_side():
    def core(img):
        rect(img, 6, 6, 9, 9, V1)
        rect(img, 7, 7, 8, 8, V3)
        px(img, 7, 7, WHITE)
        px(img, 8, 7, V4)
        px(img, 7, 8, V4)
        for x, y in ((3, 4), (12, 11), (10, 3), (3, 12)):
            px(img, x, y, V4)
        px(img, 12, 2, WHITE)
    return pcb(C("120b22"), V2, V0, V4, C("1c1430"), C("3b2f5c"), core)


def pattern_provider_front():
    img = graphite(G3)
    sunken(img, 2, 2, 13, 13, G2, shadow=G0, lip=G5)
    for r in range(3):
        y = 3 + r * 4
        for c in range(3):
            x = 2 + c * 4
            hline(img, x, x + 2, y + 1, VOID)
            hline(img, x, x + 2, y, G1)
            hline(img, x, x + 2, y + 2, C("4a5a66"))
            px(img, x + 3, y + 1, A3)
            px(img, x + 3, y, A1 if (r + c) % 2 else G1)
            px(img, x + 3, y + 2, mix(A1, G3, .5))
    return img


def pattern_provider_side():
    img = graphite(G3)
    for y in (3, 6, 9):
        hline(img, 3, 12, y, SLOT)
        px(img, 2, y, G0)
        hline(img, 3, 12, y + 1, C("53646f"))
    hline(img, 3, 12, 12, G0)
    hline(img, 4, 11, 13, A3)
    px(img, 3, 13, A1)
    px(img, 12, 13, A1)
    hline(img, 5, 10, 13, A3)
    px(img, 4, 13, A2)
    px(img, 11, 13, A2)
    hline(img, 3, 12, 14, G5)
    screw(img, 2, 12 - 11 + 0, GSPEC) if False else None
    return img


def ring_plate(radius, hub, bolts):
    img = graphite(G3)
    cx = cy = 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - cx, y - cy)
            if abs(d - radius) < .55:
                # light from top-left: brighter on upper-left arc
                lit = (x - cx) + (y - cy) < 0
                px(img, x, y, C3 if lit else C2)
            elif abs(d - (radius - 1)) < .5:
                px(img, x, y, G1)
            elif abs(d - (radius + 1)) < .5:
                px(img, x, y, G4 if (x + y) < 15 else G2)
    hub(img)
    for (x, y) in bolts:
        screw(img, x, y)
    return img


def assembler_base():
    def hub(img):
        rect(img, 6, 6, 9, 9, G2)
        frame(img, 6, 6, 9, 9, G0, G5)
        px(img, 7, 7, C1)
        px(img, 8, 8, C0)
        px(img, 8, 7, C0)
        px(img, 7, 8, C0)
    img = ring_plate(5.0, hub, [])
    for x, y in ((7, 1), (8, 1)):
        pass
    return img


def storage_controller_plate():
    def hub(img):
        rect(img, 6, 6, 9, 9, G1)
        frame(img, 6, 6, 9, 9, G0, G5)
        rect(img, 7, 7, 8, 8, C2)
        px(img, 7, 7, C4)
        px(img, 8, 8, C1)
    return ring_plate(4.0, hub, [(2, 2), (12, 2), (2, 12), (12, 12)])


def braid(dark, mid, light, stripe_hi, stripe_lo, sparkle=None):
    """Woven sleeve: diagonal strands in 4-row bands that alternate lean."""
    img = new(fill=dark)
    ramp = (light, mid, mix(mid, dark, .5), dark)
    for y in range(16):
        for x in range(16):
            # herringbone: strand direction flips every 4 rows, like a woven sleeve
            c = ramp[(x + y) % 4] if (y // 4) % 2 == 0 else ramp[(x - y + 1) % 4]
            px(img, x, y, c)
    for x in range(16):
        notch = (x % 4 == 3)
        px(img, x, 7, mix(stripe_hi, stripe_lo, .55) if notch else stripe_hi)
        px(img, x, 8, mix(stripe_lo, dark, .35) if notch else stripe_lo)
        px(img, x, 6, mix(img.getpixel((x, 6)), stripe_lo, .25))
        px(img, x, 9, mix(img.getpixel((x, 9)), dark, .5))
    for (x, y) in (sparkle or []):
        px(img, x, y, C("f4ecff"))
    return img


def data_cable():
    return braid(C("0b0d10"), C("1d2228"), C("2b323a"), C3, C2)


def quantum_data_cable():
    return braid(C("140a2a"), C("2a1a52"), C("3d2875"), C("ec8cff"), C("b04cff"),
                 sparkle=[(5, 7), (11, 8), (8, 4)])


# ---------------------------------------------------------------- entity textures
def core_crystal():
    img = new(32, 32)
    cx, cy = 15.5, 15.5
    for y in range(32):
        for x in range(32):
            # elongated octahedron silhouette
            d = abs(x - cx) / 11.5 + abs(y - cy) / 15.0
            if d > 1:
                continue
            t = d
            if t < .25:
                c = WHITE
            elif t < .55:
                c = mix(WHITE, C("e6fbff"), (t - .25) / .3)
            else:
                c = mix(C("e6fbff"), C("a8e4f2"), (t - .55) / .45)
            a = int(255 - 130 * max(0, t - .5) / .5)
            # lower-right facets slightly darker for form
            if x > cx and y > cy:
                c = mix(c, C("8cc8d8"), .18 * t)
            elif x < cx and y < cy:
                c = mix(c, WHITE, .15)
            px(img, x, y, alpha(c, a))
    # facet lines
    for y in range(1, 31):
        px(img, 15, y, alpha(WHITE, 255 if 6 < y < 25 else 200))
    for k in range(-8, 9):
        x = int(cx + k * 1.0)
        y = int(cy - 5 + abs(k) * .45)
        if img.getpixel((x, y))[3]:
            px(img, x, y, alpha(mix(WHITE, C("d0f4ff"), .3), 235))
    for k in range(1, 10):
        px(img, int(15 - k * .75), int(15.5 - 13 + k * 1.3), alpha(WHITE, 225))
        px(img, int(16 + k * .75), int(15.5 + 13 - k * 1.3), alpha(C("cfeef6"), 210))
    # rim: subtle defined edge
    edge = img.copy()
    for y in range(32):
        for x in range(32):
            if edge.getpixel((x, y))[3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 32 and 0 <= ny < 32) or edge.getpixel((nx, ny))[3] == 0:
                    px(img, x, y, alpha(C("c4eef8") if (dx < 0 or dy < 0) else C("88c0d0"), 150))
                    break
    return img


def ring_band():
    img = new(32, 8)
    rows = {2: (200, 140), 3: (255, 255), 4: (240, 255), 5: (190, 130), 1: (150, 45), 6: (140, 40)}
    for x in range(32):
        k = x % 8
        if k == 7:
            continue
        fade = .6 if k in (0, 6) else 1.0
        for y, (v, a) in rows.items():
            px(img, x, y, (v, v, v, int(a * fade)))
    return img


def drive_cell():
    img = new(fill=C("b8c0c6"))
    hline(img, 0, 15, 0, C("e3e8eb"))
    vline(img, 0, 0, 15, C("d4dade"))
    hline(img, 0, 15, 15, C("6e787f"))
    rect(img, 1, 1, 11, 2, C("cdd3d8"))
    rect(img, 1, 13, 11, 14, C("9aa3aa"))
    # handle notch
    rect(img, 3, 5, 9, 10, C("4d565d"))
    hline(img, 3, 9, 5, C("33393e"))
    hline(img, 3, 9, 11, C("e3e8eb"))
    vline(img, 10, 5, 11, C("dce2e5"))
    rect(img, 4, 7, 8, 8, C("2a3035"))
    # divider + LED window (right 3 columns)
    vline(img, 12, 0, 15, C("3c444a"))
    rect(img, 13, 0, 15, 15, WHITE)
    vline(img, 13, 0, 15, C("eef6fa"))
    return img


def beam():
    img = new()
    for x in range(16):
        d = abs(x - 7.5) / 8.0
        a = int(255 * max(0.0, 1 - d) ** 1.6)
        v = 255 if d < .2 else int(255 - 40 * d)
        for y in range(16):
            px(img, x, y, (v, v, v, a))
    return img


# ---------------------------------------------------------------- items
OUT = C("10161b")
CASE = [C("e4eaee"), C("bcc6cd"), C("94a0a9"), C("6c7882"), C("4a555e")]
CASE_Q = [C("8f86b0"), C("5f577f"), C("453e63"), C("2f2948"), C("201b33")]
TIERS = {"1k": ("8fd3ff", 1), "4k": ("7be3a3", 2), "16k": ("f0d264", 3), "64k": ("f0985a", 4),
         "256k": ("e76ad7", 5)}


def cartridge(case, label=None, pips=0, quantum=False, empty=False):
    """Front face x1..11,y3..14; oblique top (rows 1..2) and right side (cols 12..13)."""
    hi, light, mid, dark, deep = case
    img = new()
    rect(img, 1, 3, 11, 14, mid)
    for d in (1, 2):
        hline(img, 1 + d, 11 + d, 3 - d, light if d == 1 else hi)
        vline(img, 11 + d, 3 - d, 14 - d, dark if d == 1 else deep)
    px(img, 13, 1, dark)
    # front bevel
    hline(img, 1, 11, 3, hi)
    vline(img, 1, 3, 14, light)
    hline(img, 1, 11, 14, deep)
    vline(img, 11, 4, 14, dark)
    # top face groove
    hline(img, 4, 10, 2, mid)
    if label is not None:
        L = C(label) if isinstance(label, str) else label
        lhi, llo, ldd = mix(L, WHITE, .5), mix(L, (0, 0, 0, 255), .3), mix(L, (0, 0, 0, 255), .6)
        rect(img, 2, 5, 10, 8, L)
        hline(img, 2, 10, 5, lhi)
        hline(img, 2, 10, 8, llo)
        vline(img, 10, 5, 8, llo)
        hline(img, 2, 10, 4, dark)       # recess shadow above label
        # wrap label onto side face (oblique)
        for d in (1, 2):
            for y in range(5, 9):
                px(img, 11 + d, y - d, ldd if d == 2 else llo)
        if not quantum:
            # tiny printed text on the label
            for x in (3, 4, 6):
                px(img, x, 7, ldd)
            px(img, 8, 6, ldd)
            px(img, 8, 7, ldd)
        hline(img, 2, 10, 9, deep)
    if empty:
        rect(img, 3, 5, 9, 8, deep)
        hline(img, 3, 9, 5, mix(deep, (0, 0, 0, 255), .4))
        hline(img, 3, 9, 9, light)
        vline(img, 10, 5, 9, light)
        vline(img, 2, 5, 8, dark)
    # pips
    if not quantum:
        for i in range(5):
            x = 2 + i * 2
            if i < pips:
                px(img, x, 11, mix(C(label), WHITE, .35))
                px(img, x, 12, C(label))
            else:
                px(img, x, 11, deep)
                px(img, x, 12, dark)
    # bottom contacts
    for x in range(3, 10, 2):
        px(img, x, 13, C("d9b35a") if not quantum else V3)
    return outline(img, OUT)


def quantum_cell():
    img = cartridge(CASE_Q, label=V2, quantum=True)
    # glowing violet core in the label band
    rect(img, 4, 5, 8, 8, V1)
    rect(img, 5, 6, 7, 7, V3)
    px(img, 6, 6, WHITE)
    px(img, 5, 6, V4)
    px(img, 7, 7, V4)
    px(img, 3, 6, V3)
    px(img, 9, 7, V3)
    # rails with dots instead of pips
    for x in (3, 5, 7, 9):
        px(img, x, 11, V3 if x in (5, 7) else V2)
    hline(img, 2, 10, 12, CASE_Q[3])
    # sparkle
    px(img, 14, 0, V4)
    px(img, 14, 1, WHITE)
    px(img, 15, 1, V4)
    px(img, 13, 1, alpha(V4, 255))
    px(img, 14, 2, V4)
    px(img, 0, 10, V4)
    return img


CARD = [C("eef2f5"), C("c9d3da"), C("a5b2bb"), C("7d8a93"), C("58646d")]


def card():
    hi, light, mid, dark, deep = CARD
    img = new()
    rect(img, 2, 3, 13, 12, light)
    hline(img, 2, 12, 3, hi)
    vline(img, 2, 3, 12, hi)
    hline(img, 2, 13, 12, dark)
    vline(img, 13, 4, 12, mid)
    # corner notch (top-right)
    px(img, 13, 3, CLEAR); px(img, 12, 3, CLEAR); px(img, 13, 4, CLEAR)
    px(img, 12, 4, mid); px(img, 13, 5, mid)
    # contact strip
    rect(img, 4, 10, 11, 11, C("8c6a24"))
    for x in range(4, 12, 2):
        px(img, x, 10, C("f0cf72"))
        px(img, x, 11, C("c9a24a"))
    return img


def blank_pattern():
    img = card()
    hline(img, 4, 9, 5, CARD[2])
    hline(img, 4, 7, 7, CARD[2])
    return outline(img, OUT)


def crafting_pattern():
    img = card()
    rect(img, 4, 4, 10, 8, C("1a3a48"))
    for r in range(3):
        for c in range(3):
            x, y = 5 + c * 2, 4 + r * 2
            px(img, x, y, C4 if (r, c) == (0, 0) else C3)
    for y in (5, 7):
        for x in (5, 7, 9):
            px(img, x, y, C1)
    px(img, 4, 4, C("12303c"))
    return outline(img, OUT)


def processing_pattern():
    img = card()
    rect(img, 3, 4, 11, 8, C("3a2410"))
    gear = ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"]
    for y, row in enumerate(gear):
        for x, ch in enumerate(row):
            if ch == "#":
                px(img, 3 + x, 4 + y, A4 if (x + y) < 3 else A3)
    px(img, 5, 6, C("3a2410"))
    for x, y in ((9, 6), (10, 6), (11, 6), (10, 5), (10, 7)):
        px(img, x, y, A3)
    px(img, 11, 6, A4)
    px(img, 9, 6, A2)
    return outline(img, OUT)


def handheld(body, screen, screen_hi, quantum=False):
    hi, light, mid, dark, deep = body
    img = new()
    rect(img, 3, 3, 11, 14, mid)
    hline(img, 4, 11, 3, light); vline(img, 3, 4, 13, light)
    px(img, 4, 4, hi)
    hline(img, 4, 11, 14, deep); vline(img, 11, 4, 14, dark)
    px(img, 3, 3, CLEAR); px(img, 11, 14, CLEAR); px(img, 3, 14, CLEAR); px(img, 11, 3, CLEAR)
    # screen
    rect(img, 4, 5, 10, 9, screen)
    hline(img, 4, 10, 5, screen_hi)
    hline(img, 4, 10, 9, mix(screen, (0, 0, 0, 255), .35))
    for x in (5, 7, 9):
        px(img, x, 7, screen_hi)
    px(img, 4, 5, WHITE)
    # buttons
    px(img, 5, 11, light); px(img, 7, 11, light); px(img, 9, 11, A3 if not quantum else V4)
    hline(img, 5, 9, 12, dark)
    # antenna
    if quantum:
        vline(img, 10, 1, 2, dark)
        rect(img, 10, 0, 11, 1, V3)
        px(img, 10, 0, V4)
        px(img, 11, 1, V2)
        px(img, 12, 0, alpha(V4, 255))
    else:
        vline(img, 10, 0, 2, dark)
        px(img, 10, 0, C3)
    img = outline(img, OUT)
    if quantum:
        px(img, 13, 1, V4)
    return img


GRAPHITE_ITEM = [C("5b6d7a"), C("43535f"), C("323f49"), C("232d35"), C("161d23")]
VIOLET_ITEM = [C("8a74c8"), C("5e4a9a"), C("432f78"), C("2f1f58"), C("1d123a")]


def data_core():
    img = new()
    # PCB plate
    rect(img, 2, 8, 13, 13, C("123222"))
    hline(img, 2, 13, 8, C("2a5a3c"))
    hline(img, 2, 13, 13, C("0a1a12"))
    for x in (3, 5, 10, 12):
        px(img, x, 12, C("c9a24a"))
    hline(img, 3, 5, 10, C("8a6e2e"))
    hline(img, 10, 12, 10, C("8a6e2e"))
    # crystal (rhombus)
    rows = [(7, 8), (6, 9), (5, 10), (5, 10), (6, 9), (7, 8)]
    for i, (a, b) in enumerate(rows):
        y = 3 + i
        for x in range(a, b + 1):
            if x < 7.5 and y < 6:
                c = C4
            elif x >= 7.5 and y >= 6:
                c = C1
            else:
                c = C3 if x < 7.5 else C2
            px(img, x, y, c)
    px(img, 6, 4, WHITE); px(img, 7, 3, WHITE)
    hline(img, 5, 10, 9, C0)  # chip seat shadow
    img = outline(img, OUT)
    px(img, 3, 1, C4)
    return img


def cell_housing():
    return cartridge(CASE, label=None, empty=True)


# ---------------------------------------------------------------- driver
def generate_textures():
    B, E, I = "block/", "entity/storage/", "item/"
    save(casing(False), B + "storage_casing.png")
    save(casing(True), B + "storage_casing_lit.png")
    save(storage_glass(), B + "storage_glass.png")
    save(vent(), B + "storage_vent.png")
    save(drive_bay_front(), B + "drive_bay_front.png")
    save(terminal_frame(), B + "terminal_frame.png")
    save(terminal_screen_off(), B + "terminal_screen_off.png")
    save_anim([grid_screen(i) for i in range(16)], B + "terminal_screen_grid_on.png", 3)
    save_anim([crafting_screen(i) for i in range(16)], B + "terminal_screen_crafting_on.png", 3)
    save_anim([pattern_screen(i) for i in range(16)], B + "terminal_screen_pattern_on.png", 3)
    save(port_body(), B + "port_body.png")
    save(storage_bus_face(), B + "storage_bus_face.png")
    save(arrow_face(True), B + "importer_face.png")
    save(arrow_face(False), B + "exporter_face.png")
    save(crafting_processor_side(), B + "crafting_processor_side.png")
    save(quantum_processor_side(), B + "quantum_processor_side.png")
    save(pattern_provider_front(), B + "pattern_provider_front.png")
    save(pattern_provider_side(), B + "pattern_provider_side.png")
    save(assembler_base(), B + "assembler_base.png")
    save(data_cable(), B + "data_cable.png")
    save(quantum_data_cable(), B + "quantum_data_cable.png")
    save(storage_controller_plate(), B + "storage_controller_plate.png")

    save(core_crystal(), E + "core.png")
    save(ring_band(), E + "ring.png")
    save(drive_cell(), E + "drive_cell.png")
    save(beam(), E + "beam.png")

    for tier, (col, pips) in TIERS.items():
        save(cartridge(CASE, col, pips), I + f"storage_cell_{tier}.png")
    save(quantum_cell(), I + "quantum_storage_cell.png")
    save(blank_pattern(), I + "blank_pattern.png")
    save(crafting_pattern(), I + "crafting_pattern.png")
    save(processing_pattern(), I + "processing_pattern.png")
    save(handheld(GRAPHITE_ITEM, C("0f4a5c"), C3), I + "wireless_terminal.png")
    save(handheld(VIOLET_ITEM, C("2a1a52"), V3, quantum=True), I + "quantum_wireless_terminal.png")
    save(data_core(), I + "data_core.png")
    save(cell_housing(), I + "cell_housing.png")
    save_anim([fluid_screen(i) for i in range(16)], B + "terminal_screen_fluid_on.png", 3)
    for name, col in FLUID_TIERS.items():
        save(fluid_cell(col), I + name + ".png")
    save(quantum_fluid_cell(), I + "quantum_fluid_cell.png")


# ---------------------------------------------------------------- fluid storage
def fluid_screen(frame_i):
    """Fluid Terminal screen: a liquid level that rolls in a slow wave, bubbles rising through it."""
    import math
    img = new(fill=C("06121c"))
    frame(img, 0, 0, 15, 15, C("cfe9ff"), C("5f8fb8"))
    for x in range(1, 15):
        level = 8 + round(1.2 * math.sin((x + frame_i) * 0.7))
        for y in range(1, 15):
            if y > level:
                px(img, x, y, C("1f6fd0") if y > level + 2 else C("3f9cff"))
            elif y == level:
                px(img, x, y, C("bfe6ff"))
    for k, bx in enumerate((4, 9, 12)):
        by = 14 - (frame_i * 2 + k * 5) % 7
        if by > 9:
            px(img, bx, by, C("dff4ff"))
    # capacity ticks on the left edge
    for y in (4, 7, 10, 13):
        px(img, 2, y, C("6fb8e8"))
    return img


def droplet(img, cx, cy, col):
    """A small water-drop glyph centred on a cartridge label."""
    for (dx, dy) in [(0, -2), (0, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1), (0, 2)]:
        px(img, cx + dx, cy + dy, col)
    px(img, cx - 1, cy, WHITE)


FLUID_TIERS = {"fluid_cell_4k": "4fa8ff", "fluid_cell_64k": "3fe0c8"}


def fluid_cell(label):
    img = cartridge(CASE, label, 0)
    droplet(img, 6, 6, C("0b2a4a"))
    return img


def quantum_fluid_cell():
    img = quantum_cell()
    droplet(img, 6, 6, C("e8f6ff"))
    return img


FILES = [
    "block/storage_casing", "block/storage_casing_lit", "block/storage_glass", "block/storage_vent",
    "block/drive_bay_front", "block/terminal_frame", "block/terminal_screen_off",
    "block/terminal_screen_grid_on", "block/terminal_screen_crafting_on", "block/terminal_screen_pattern_on",
    "block/port_body", "block/storage_bus_face", "block/importer_face", "block/exporter_face",
    "block/crafting_processor_side", "block/quantum_processor_side", "block/pattern_provider_front",
    "block/pattern_provider_side", "block/assembler_base", "block/data_cable", "block/quantum_data_cable",
    "block/storage_controller_plate", "entity/storage/core", "entity/storage/ring",
    "entity/storage/drive_cell", "entity/storage/beam",
    "item/storage_cell_1k", "item/storage_cell_4k", "item/storage_cell_16k", "item/storage_cell_64k",
    "item/storage_cell_256k", "item/quantum_storage_cell", "item/blank_pattern", "item/crafting_pattern",
    "item/processing_pattern", "item/wireless_terminal", "item/quantum_wireless_terminal",
    "item/data_core", "item/cell_housing", "block/terminal_screen_fluid_on",
    "item/fluid_cell_4k", "item/fluid_cell_64k", "item/quantum_fluid_cell",
]


if __name__ == "__main__":
    generate_textures()
