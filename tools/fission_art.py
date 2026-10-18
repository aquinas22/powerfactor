"""Fission art: uranium ore and metals, the reactor's blocks, fuel rods, waste and the Hazmat Suit.

Run directly or call generate_textures().
"""
import math
import random

from PIL import Image

import quantum_art as qa
from gen_textures import INGOT_MAP, RAW_MAP, ORE_MASKS, recolor
from quantum_art import C, mix, new, outline, px, save, save_anim, solid, stamp

B, I, E = "block/", "item/", "entity/"
GREEN = ["1e3a10", "2e5a16", "3f7a1c", "58a024", "7cc832", "a6e84a", "d2ff7a", "f4ffd0"]


def noise(w, h, base, var, seed):
    r = random.Random(seed)
    img = new(w, h)
    for y in range(h):
        for x in range(w):
            k = 1 + r.uniform(-var, var)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    return img


# ---------------------------------------------------------------- ore and metals
def uranium_overlay():
    """Glowing green pitchblende flecks in the iron-ore pattern, rimmed dark."""
    mask = ORE_MASKS["iron"][1]
    ramp = {"a": C("2e5a16"), "b": C("58a024"), "c": C("8ae03a"), "d": C("c8ff6a"), "e": C("f4ffd0")}
    img = new()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch in ramp:
                px(img, x, y, ramp[ch])
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch.isdigit() and any(solid(img, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px(img, x, y, C("10200a"))
    return img


def raw_uranium():
    return recolor(RAW_MAP, ["1a2a12", "2e4a1c", "445e26", "5e7a30", "7a9a3a", "9ab84a", "bcd864", "e0f49a"])


def ingot(ramp):
    return recolor(INGOT_MAP, ramp)


# ---------------------------------------------------------------- reactor blocks
STEEL = (150, 158, 164)
# The reactor's hull: dark gunmetal, so the hazard band and the Cherenkov glow stand out.
HULL = (58, 62, 70)


def plate():
    """Dark gunmetal plate with a bevelled edge and corner bolts: the face of ports and adapters."""
    img = noise(16, 16, HULL, 0.07, 81)
    for i in range(16):
        px(img, i, 0, C("7c848e"))
        px(img, 0, i, C("7c848e"))
        px(img, i, 15, C("121418"))
        px(img, 15, i, C("121418"))
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, *p, C("a8b0b8"))
        px(img, p[0] + 1, p[1] + 1, C("0e1012"))
    return img


def casing():
    """Reactor Casing: the plate with a yellow-black band. A formed reactor joins up (connected_art.py)."""
    img = plate()
    for x in range(1, 15):
        for y in (6, 7, 8, 9):
            px(img, x, y, C("e8bc2a") if ((x + y) // 2) % 2 == 0 else C("141619"))
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, *p, C("a8b0b8"))
        px(img, p[0] + 1, p[1] + 1, C("0e1012"))
    return img


def glass():
    """Reactor Glass: leaded borosilicate in a steel frame; the pane is mostly clear."""
    img = new()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), C("50565e") if x == 0 or y == 0 else C("1e2226"))
            else:
                img.putpixel((x, y), (120, 170, 205, 40))
    for k in range(3, 8):
        px(img, k, 12 - k, (230, 245, 255, 150))
    return img


def port(mode_col):
    img = plate()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.5 < d < 5.5:
                px(img, x, y, C("2a3036"))
            if d <= 3.5:
                px(img, x, y, mode_col if d < 2.2 else C("1a1e22"))
    return img


def adapter():
    img = plate()
    for x in range(3, 13):
        for y in range(3, 13):
            px(img, x, y, C("1a2420"))
    for x, y in ((4, 5), (5, 5), (6, 5), (6, 6), (6, 7), (7, 7), (8, 7), (9, 7), (9, 8), (9, 9), (10, 9), (11, 9), (4, 10), (5, 10), (6, 10)):
        px(img, x, y, C("3affb0"))
    for p in ((11, 4), (12, 4), (11, 5), (12, 5)):
        px(img, *p, C("ff3a3a"))
    return img


def drive_top():
    """Control Rod Drive from above: a hex nut round the rod port."""
    img = plate()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5) * 0.87 + abs(y - 7.5) * 0.5, abs(y - 7.5))
            if d < 6:
                px(img, x, y, C("8a929c") if d > 4.5 else C("4e555e") if d > 2 else C("0e1012"))
    return img


def drive_side():
    img = noise(16, 16, (70, 74, 82), 0.05, 82)
    for x in range(16):
        px(img, x, 0, C("8a929c"))
        px(img, x, 15, C("121418"))
        if x % 4 == 0:
            for y in range(2, 14):
                px(img, x, y, C("2a2e34"))
    for y in (3, 12):
        for x in range(16):
            px(img, x, y, C("e8bc2a"))
    return img


def assembly():
    """Fuel rod cladding: zircaloy tubes, a 4px profile repeated across the sheet (each rod face shows one period),
    faint seams where the pellets inside stack."""
    img = new()
    prof = [C("4e565e"), C("b4bec6"), C("8a949c"), C("5e666e")]
    for y in range(16):
        for x in range(16):
            c = prof[x % 4]
            if y % 4 == 3:
                c = mix(c, C("2a3036"), 0.35)
            px(img, x, y, c)
    return img


def assembly_top():
    """Rod end plugs, a 4px tile repeated: a bevelled cap with a dimple."""
    img = new()
    for y in range(16):
        for x in range(16):
            tx, ty = x % 4, y % 4
            edge = tx in (0, 3) or ty in (0, 3)
            px(img, x, y, C("5e666e") if edge else C("d0d8de") if (tx, ty) == (1, 1) else C("9aa4ac"))
    return img


def assembly_grid():
    """Spacer grid strap: Inconel, a warm grey-gold with dimples where the springs press on the rods."""
    img = new()
    for y in range(16):
        for x in range(16):
            px(img, x, y, C("c8b88a") if y % 4 == 0 else C("a8986a") if (x + y) % 3 else C("8a7a52"))
    return img


def cherenkov():
    """The Cherenkov sheath for the additive light columns (an entity texture, so it can scroll): bright down the
    middle, falling away to the sides, with rippling bands along it; tiles vertically."""
    w, h = 32, 64
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            across = math.exp(-((x - 15.5) / 7.5) ** 2)
            ripple = 0.72 + 0.16 * math.sin(y / h * 2 * math.pi * 3 + math.sin(x * 0.4) * 1.3) + 0.12 * math.sin(y / h * 2 * math.pi * 7 + x * 0.2)
            v = int(255 * max(0.0, min(1.0, across * ripple)))
            img.putpixel((x, y), (v, v, v, v))
    return img


def fx_glow():
    """Plain white, tinted by the renderer: the Cherenkov wash over a pane."""
    return Image.new("RGBA", (16, 16), (255, 255, 255, 255))


def fx_caustics(frame, frames=16):
    """One frame of drifting caustics (block atlas sprites can't scroll, so they animate): a web of bright lines
    whose brightness is its alpha."""
    n = 32
    img = Image.new("RGBA", (n, n))
    t = 2 * math.pi * frame / frames
    for y in range(n):
        for x in range(n):
            a, b = 2 * math.pi * x / n, 2 * math.pi * y / n
            f = (math.sin(a * 2 + math.sin(b * 3 + t) * 1.4 + t) + math.sin(b * 2 + math.sin(a * 3 - t) * 1.4 - t) + math.sin((a + b) * 3 + t)) / 3
            line = max(0.0, 1 - abs(f) * 4.5) ** 2
            v = int(255 * min(1.0, 0.12 + 0.88 * line))
            img.putpixel((x, y), (255, 255, 255, v))
    return img


def graphite():
    """Graphite Moderator: dark grey bricks with a sheen."""
    img = noise(16, 16, (54, 56, 60), 0.08, 83)
    for x in range(16):
        px(img, x, 7, C("1e2022"))
        px(img, x, 15, C("1e2022"))
    for y in range(16):
        px(img, 7 if y < 8 else 15, y, C("1e2022"))
    for x, y in ((2, 2), (3, 2), (10, 10), (11, 10)):
        px(img, x, y, C("8a8e94"))
    return img


def corium(frame):
    """Corium: black slag split by molten orange veins that crawl."""
    r = random.Random(84)
    img = new()
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.9 + frame * 0.6) + math.sin(y * 1.3 - frame * 0.4) + math.sin((x + y) * 0.5 + frame * 0.3)
            if v > 1.4:
                c = mix(C("ff7a1a"), C("fff0a0"), min(1, (v - 1.4) * 1.5))
            elif v > 0.9:
                c = C("c03a0a")
            else:
                k = 0.8 + r.uniform(-0.15, 0.15)
                c = tuple(int(ch * k) for ch in (40, 32, 30)) + (255,)
            img.putpixel((x, y), c)
    return img


def controller_front(lit, frame=0):
    """The controller's face: a console screen with a radiation trefoil and a power trace."""
    img = plate()
    for y in range(2, 14):
        for x in range(2, 14):
            px(img, x, y, C("0e1a14"))
    col = C("7cff8a") if lit else C("2e5a3a")
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.degrees(math.atan2(y - 7.5, x - 7.5)) % 120
            if 1.6 < d < 4.6 and 20 < a < 100:
                px(img, x, y, col)
            if d < 1.2:
                px(img, x, y, col)
    if lit:
        for x in range(3, 13):
            y = 12 - int(1.5 + 1.2 * math.sin(x * 0.9 + frame))
            px(img, x, y, C("ffd84a"))
    return img


def centrifuge_side():
    """Isotope Centrifuge: a glass window down a steel column, hoops above and below."""
    img = new()
    for y in range(16):
        for x in range(16):
            if 4 <= x <= 11 and 2 <= y <= 13:
                img.putpixel((x, y), (140, 200, 170, 60))
            else:
                k = 1 + ((x * 7 + y * 3) % 5 - 2) * 0.03
                img.putpixel((x, y), tuple(int(c * k) for c in STEEL) + (255,))
    for x in range(16):
        for y in (1, 14):
            px(img, x, y, C("e8bc2a"))
    for y in range(2, 14):
        px(img, 3, y, C("5a6268"))
        px(img, 12, y, C("d8dee2"))
    return img


# ---------------------------------------------------------------- items
def rod(core, glow, cap="b8c0c6"):
    rows = [
        "..........cc....",
        ".........cCCc...",
        "........cCggc...",
        ".......cggGg....",
        "......cggGg.....",
        ".....cggGg......",
        "....cggGg.......",
        "...cggGg........",
        "..cggGg.........",
        ".cCgGg..........",
        ".cCCc...........",
        "..cc............",
        "................",
        "................",
        "................",
        "................",
    ]
    img = stamp(rows, {"c": C("5a6268"), "C": C(cap), "g": C(core), "G": C(glow), ".": (0, 0, 0, 0)})
    img = outline(img, C("14181c"))
    return img


def waste():
    rows = [
        "................",
        "....yyyyyyyy....",
        "...yYYYYYYYYy...",
        "...ykkkkkkkky...",
        "...yYYYYYYYYy...",
        "...yYYkYYkYYy...",
        "...yYYYkkYYYy...",
        "...yYkkkkkkYy...",
        "...yYYYkkYYYy...",
        "...yYYkYYkYYy...",
        "...yYYYYYYYYy...",
        "...ykkkkkkkky...",
        "...yYYYYYYYYy...",
        "....yyyyyyyy....",
        "................",
        "................",
    ]
    return outline(stamp(rows, {"y": C("8a6a10"), "Y": C("e8bc2a"), "k": C("1e2226"), ".": (0, 0, 0, 0)}), C("14181c"))


def pellet(ramp):
    rows = [
        "................",
        "................",
        "................",
        "......1221......",
        ".....134431.....",
        "....13455431....",
        "....24566542....",
        "....24566542....",
        "....23455432....",
        "....12344321....",
        ".....123321.....",
        "......0110......",
        "................",
        "................",
        "................",
        "................",
    ]
    return recolor(rows, ramp)


# ---------------------------------------------------------------- heat exchanger and coolants
def exchanger_side():
    """Heat Exchanger shell: dark plate with flanged pipe stubs and a weld seam."""
    img = plate()
    for y in range(4, 12):
        px(img, 7, y, C("2a2e34"))
        px(img, 8, y, C("7c848e"))
    for cx, cy in ((4, 4), (11, 11)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if d < 2.6:
                    px(img, x, y, C("a8b0b8") if d > 1.8 else C("0e1012"))
    return img


def exchanger_top():
    img = plate()
    for y in range(3, 13):
        for x in range(3, 13):
            px(img, x, y, C("2a2e34") if (x + y) % 2 else C("3a3f46"))
    return img


def exchanger_front(lit, frame=0):
    """Window onto the tube bundle: U-tubes that glow hot at the inlet and fade to cold along the run."""
    img = plate()
    for y in range(2, 14):
        for x in range(2, 14):
            px(img, x, y, C("0a0c0e"))
    for i, x in enumerate((3, 5, 7, 9, 11)):
        for y in range(3, 13):
            t = (y - 3) / 9
            if lit:
                flow = 0.5 + 0.5 * math.sin((y - frame * 1.5 + i) * 0.9)
                c = mix(mix(C("ff7a2a"), C("3f76e4"), t), C("fff0c0"), 0.25 * flow)
            else:
                c = mix(C("5a4a40"), C("3a4656"), t)
            px(img, x, y, c)
            px(img, x + 1, y, mix(c, C("0a0c0e"), 0.55))
    for x in range(2, 14):
        px(img, x, 2, C("50565e"))
        px(img, x, 13, C("50565e"))
    return img


def soda_ash():
    """Soda ash: a heap of grey-white powder, dusted like vanilla's sugar."""
    img = new()
    r = random.Random(77)
    heap = [(x, y) for y in range(5, 15) for x in range(2, 14) if (x - 7.5) ** 2 / 36 + (y - 14) ** 2 / 81 < 1]
    for x, y in heap:
        v = r.random()
        px(img, x, y, C("f4f4ee") if v > 0.8 else C("d8d8cc") if v > 0.4 else C("b4b4a8"))
    return outline(img, C("6e6e62"))


def geiger():
    """Geiger counter: a yellow handheld with a dial and the probe on a curly lead."""
    rows = [
        "................",
        "..1111111.......",
        "..1yyyyy1.......",
        "..1ywwwy1.......",
        "..1wd.dw1.......",
        "..1w.n.w1.......",
        "..1ywwwy1.......",
        "..1yyyyy1.......",
        "..1ybyby1...pp..",
        "..1yyyyy1..p..p.",
        "..1111111.p...p.",
        "....c......pppp.",
        ".....c....p.....",
        "......c..p......",
        ".......cp.......",
        "................",
    ]
    return outline(stamp(rows, {"1": C("3a3016"), "y": C("e8bc2a"), "w": C("f4f0e0"), "d": C("2a2e34"), "n": C("ff3a3a"), "b": C("1e2226"),
                                "p": C("8a949e"), "c": C("2a2e34")}), C("14181c"))


def dosimeter():
    """Dosimeter: a clip-on badge with a film window."""
    rows = [
        "................",
        "......1111......",
        ".....1kkkk1.....",
        "....1111111111..",
        "....1gggggggg1..",
        "....1g222222g1..",
        "....1g2tttt2g1..",
        "....1g2tttt2g1..",
        "....1g222222g1..",
        "....1gggggggg1..",
        "....1grrggyyg1..",
        "....1gggggggg1..",
        "....1111111111..",
        "................",
        "................",
        "................",
    ]
    return outline(stamp(rows, {"1": C("2a2e34"), "k": C("8a949e"), "g": C("dfe6ea"), "2": C("5a6268"), "t": C("6fe3ff"), "r": C("ff5a4a"),
                                "y": C("ffd84a")}), C("14181c"))


def generate_textures():
    save(uranium_overlay(), B + "uranium_ore_overlay.png")
    save(raw_uranium(), I + "raw_uranium.png")
    save(ingot(["1e2a14", "2e3e1c", "3a4e22", "4a6228", "5c7830", "76963a", "9ab84a", "c8e07a"]), I + "uranium_ingot.png")
    save(ingot(GREEN), I + "enriched_uranium.png")
    save(ingot(["16181a", "222528", "2a2e32", "363b40", "42484e", "52595f", "6a7278", "8a9298"]), I + "depleted_uranium.png")
    save(pellet(["2a1a40", "4a3070", "6a4aa0", "9a7ad0", "c8b0f0", "eee4ff", "ffffff"]), I + "plutonium.png")
    save(waste(), I + "nuclear_waste.png")
    save(rod("58a024", "c8ff6a"), I + "fuel_rod.png")
    save(rod("6a4aa0", "c8b0f0"), I + "mox_fuel_rod.png")
    save(rod("1a3a6a", "3a8cff", "6a7278"), I + "spent_fuel_rod.png")
    save(rod("2a1a50", "6a8cff", "6a7278"), I + "spent_mox_rod.png")
    save(casing(), B + "reactor_casing.png")
    save(glass(), B + "reactor_glass.png")
    save(port(C("3f76e4")), B + "reactor_port_coolant.png")
    save(port(C("7cc832")), B + "reactor_port_fuel.png")
    save(adapter(), B + "reactor_logic_adapter.png")
    save(exchanger_side(), B + "heat_exchanger_side.png")
    save(exchanger_top(), B + "heat_exchanger_top.png")
    save(exchanger_front(False), B + "heat_exchanger_front.png")
    save_anim([exchanger_front(True, f) for f in range(8)], B + "heat_exchanger_front_on.png", 2)
    save(soda_ash(), I + "soda_ash.png")
    save(geiger(), I + "geiger_counter.png")
    save(dosimeter(), I + "dosimeter.png")
    save(drive_top(), B + "control_rod_drive_top.png")
    save(drive_side(), B + "control_rod_drive_side.png")
    save(assembly(), B + "fuel_assembly.png")
    save(assembly_top(), B + "fuel_assembly_top.png")
    save(assembly_grid(), B + "fuel_assembly_grid.png")
    save(cherenkov(), "entity/fx/cherenkov.png")
    save(fx_glow(), B + "fx/reactor_glow.png")
    save_anim([fx_caustics(i) for i in range(16)], B + "fx/reactor_caustics.png", 3)
    save(graphite(), B + "graphite_moderator.png")
    save_anim([corium(i) for i in range(12)], B + "corium.png", 3)
    save(controller_front(False), B + "reactor_controller_front.png")
    save_anim([controller_front(True, i * 0.8) for i in range(8)], B + "reactor_controller_front_on.png", 3)
    save(centrifuge_side(), B + "isotope_centrifuge_side.png")
    # Hazmat Suit: the exotic armor painter, in safety yellow with a dark visor and green indicator.
    qa.ARMOR_SETS["hazmat"] = (["5a4208", "8a6610", "c49a1c", "e8bc2a", "f8d65a", "fff0a8"], "7cff8a", "1e2226")
    qa.ARMOR_TIER["hazmat"] = 0
    for kind, item in (("helmet", "hazmat_helmet"), ("chestplate", "hazmat_suit"), ("leggings", "hazmat_leggings"), ("boots", "hazmat_boots")):
        save(qa.armor_icon("hazmat", kind), I + f"{item}.png")
    hum, leg = qa.armor_layers("hazmat")
    save(hum, "entity/equipment/humanoid/hazmat.png")
    save(leg, "entity/equipment/humanoid_leggings/hazmat.png")
    save(qa.armor_baby_layer("hazmat"), "entity/equipment/humanoid_baby/hazmat.png")


if __name__ == "__main__":
    generate_textures()
    print("Fission textures written.")
