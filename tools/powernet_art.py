"""Wireless power network art: Plug and Point nodes and their arms, three Cells, the Controller, the Configurator.

The network's own colour is added in the game by the renderer (glows, sparks, charge windows), so the blocks
themselves stay a neutral dark gunmetal with crisp light trim and role colours (green in, coral out).
Run directly or call generate_textures().
"""
import math
import random

from quantum_art import C, mix, new, outline, px, save, stamp

B, I = "block/powernet/", "item/"
HULL = [C("16191e"), C("23272e"), C("323840"), C("454c56"), C("5e6772"), C("8a949e"), C("c8d0d8")]
IN, OUT = [C("1a5a3a"), C("2fae6a"), C("6affb0")], [C("6a2418"), C("e0603a"), C("ffb08a")]


def plate(seed, base=2, var=0.05):
    r = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-var, var)
            c = HULL[base]
            px(img, x, y, tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,))
    return img


def bevel(img, light=5, dark=0, inset=0):
    for i in range(inset, 16 - inset):
        px(img, i, inset, HULL[light])
        px(img, inset, i, HULL[light])
        px(img, i, 15 - inset, HULL[dark])
        px(img, 15 - inset, i, HULL[dark])
    return img


def node(role):
    """The node core's faces: a framed crystal socket with chevrons in the role's colour."""
    col = IN if role == "plug" else OUT
    img = plate(31 if role == "plug" else 32, 1)
    bevel(img, 4, 0)
    # Crystal socket: a diamond of glass in the middle, which the renderer lights in the network colour.
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 3.2:
                px(img, x, y, mix(C("0a0f14"), C("6fe3ff"), max(0, 1 - d / 3.2) * 0.6))
            elif d < 4.2:
                px(img, x, y, HULL[5])
    # Chevrons: pointing in for a Plug, out for a Point.
    pts = [(3, 2), (4, 3), (12, 2), (11, 3), (3, 13), (4, 12), (12, 13), (11, 12)]
    if role == "point":
        pts = [(2, 3), (3, 4), (13, 3), (12, 4), (2, 12), (3, 11), (13, 12), (12, 11)]
    for i, p in enumerate(pts):
        px(img, *p, col[2] if i % 2 == 0 else col[1])
    return img


def arm():
    """The arm: a ribbed conduit."""
    img = plate(33, 2)
    for y in range(16):
        for x in range(16):
            if y % 4 == 0:
                px(img, x, y, HULL[4])
            elif y % 4 == 3:
                px(img, x, y, HULL[1])
    return img


def flange():
    """Where the arm meets the neighbour: a bolted collar."""
    img = plate(34, 3)
    bevel(img, 5, 0)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, *p, HULL[6])
    for y in range(5, 11):
        for x in range(5, 11):
            px(img, x, y, HULL[1])
    return img


CELL_TIERS = {"power_cell": C("2fae6a"), "dense_power_cell": C("3a8aff"), "quantum_power_cell": C("b18cff")}


def cell_side(accent):
    """A Cell's side: dark plate, accent-trimmed, with a tall glass window (lit by the renderer as it fills)."""
    img = plate(40, 2)
    bevel(img, 5, 0)
    for y in range(1, 15):
        px(img, 1, y, accent)
        px(img, 14, y, mix(accent, C("000000"), 0.4))
    # Window: x 5..10, y 2..13, dark glass with tick marks.
    for y in range(2, 14):
        for x in range(5, 11):
            px(img, x, y, C("0a0f14") if x not in (5, 10) else HULL[4])
    for y in range(4, 13, 2):
        px(img, 4, y, HULL[5])
        px(img, 11, y, HULL[5])
    return img


def cell_top(accent):
    img = plate(41, 3)
    bevel(img, 5, 0)
    for y in range(4, 12):
        for x in range(4, 12):
            px(img, x, y, HULL[1])
    for i in range(4, 12):
        px(img, i, 4, accent)
        px(img, 4, i, accent)
    for p in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px(img, *p, accent)
    return img


def controller_side():
    """The Controller: a dark console face with a ring emblem and status pips."""
    img = plate(50, 2)
    bevel(img, 5, 0)
    for y in range(3, 13):
        for x in range(3, 13):
            px(img, x, y, C("0a0f14"))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.0 < d < 4.2:
                px(img, x, y, C("6fe3ff"))
            elif d < 1.4:
                px(img, x, y, C("e8fbff"))
    for i, c in enumerate(("56e39a", "ffb347", "ff5a4a")):
        px(img, 4 + i * 2, 12, C(c))
    return img


def controller_top():
    img = plate(51, 3)
    bevel(img, 5, 0)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5:
                px(img, x, y, HULL[1] if d > 2 else C("6fe3ff"))
    return img


def configurator():
    """Net Configurator: a little handheld with a screen and a copy/paste key."""
    rows = [
        "................",
        "....1111111.....",
        "...123333321....",
        "...13ssssss31...",
        "...13ssssss31...",
        "...13ssssss31...",
        "...133333331....",
        "...13gg3rr31....",
        "...133333331....",
        "....1333331.....",
        ".....13331......",
        ".....13331......",
        ".....13331......",
        ".....12221......",
        "......111.......",
        "................",
    ]
    img = stamp(rows, {"1": HULL[0], "2": HULL[5], "3": HULL[3], "s": C("19c6d8"), "g": C("56e39a"), "r": C("ff5a4a")})
    px(img, 6, 3, C("e8fbff"))
    return img


def generate_textures():
    save(node("plug"), B + "plug.png")
    save(node("point"), B + "point.png")
    save(arm(), B + "arm.png")
    save(flange(), B + "flange.png")
    for name, accent in CELL_TIERS.items():
        save(cell_side(accent), B + name + "_side.png")
        save(cell_top(accent), B + name + "_top.png")
    save(controller_side(), B + "controller_side.png")
    save(controller_top(), B + "controller_top.png")
    save(configurator(), I + "net_configurator.png")


if __name__ == "__main__":
    generate_textures()
    print("Power network textures written.")
