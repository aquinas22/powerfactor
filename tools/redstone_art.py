"""Project-Red-style redstone art: red alloy, insulated and bundled wire (lit and dark), sixteen gate plates with
their symbols and traces, the gate parts (lamps, output torch), Illumar lamps (off, on, halo), red alloy ingot and
screwdriver. Run directly or call generate_textures()."""
import math
import random

from quantum_art import C, mix, new, outline, px, save, stamp

B, I = "block/redstone/", "item/"
DYES = {"white": "f9fffe", "orange": "f9801d", "magenta": "c74ebd", "light_blue": "3ab3da", "yellow": "fed83d", "lime": "80c71f",
        "pink": "f38baa", "gray": "474f52", "light_gray": "9d9d97", "cyan": "169c9c", "purple": "8932b8", "blue": "3c44aa",
        "brown": "835432", "green": "5e7c16", "red": "b02e26", "black": "1d1d21"}


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


# ---------------------------------------------------------------- wires
def alloy(on):
    """Red alloy: a bright rolled strip, glowing when live."""
    img = new()
    base = C("ff4a3a") if on else C("8a1a14")
    for y in range(16):
        for x in range(16):
            k = 1.15 if y in (6, 7) else 0.85 if y in (9, 10) else 1.0
            px(img, x, y, shade(base, k + (0.06 if (x + y) % 5 == 0 else 0)))
    if on:
        for x in range(16):
            px(img, x, 7, C("ffd0c0"))
    return img


def insulated(color, on):
    """Insulated wire: woven sleeve in its colour, a copper core showing in a slit that glows when live."""
    base = C(color)
    img = new()
    for y in range(16):
        for x in range(16):
            weave = 0.92 + 0.12 * ((x + 2 * y) % 4 == 0) - 0.08 * ((2 * x + y) % 5 == 0)
            px(img, x, y, shade(base, weave))
    for x in range(16):
        px(img, x, 7, C("ffe0a0") if on else C("b06a2a"))
        px(img, x, 8, C("ff9a3a") if on else C("7a4418"))
    return img


def bundled():
    """Bundled cable: a black jacket with sixteen coloured threads twisted through it."""
    img = new()
    cols = list(DYES.values())
    for y in range(16):
        for x in range(16):
            px(img, x, y, C("1c1e22") if (x + y) % 4 else C("2a2d33"))
    for i in range(16):
        x = i
        y = 3 + (i * 7) % 10
        px(img, x, y, C(cols[i]))
        px(img, x, y + 1, shade(C(cols[i]), 0.6))
    return img


# ---------------------------------------------------------------- gates
GLYPHS = {
    # 8x8 symbols in the middle of the plate.
    "and": ["##########"[:8], "#.....##", "#......#", "#......#", "#......#", "#......#", "#.....##", "########"],
    "or": ["##......", ".###....", "..####..", "...#####", "...#####", "..####..", ".###....", "##......"],
    "not": ["#.......", "###.....", "#####...", "#######.", "#######.", "#####.##", "###...##", "#.......".replace(".", ".")],
    "nand": ["######..", "#....#..", "#.....#.", "#.....##", "#.....##", "#.....#.", "#....#..", "######.."],
    "nor": ["#.......", ".##.....", "..###...", "...####.", "...#####", "..###.##", ".##.....", "#......."],
    "xor": ["#.##....", ".#.##...", "..#.###.", "..#.####", "..#.####", "..#.###.", ".#.##...", "#.##...."],
    "xnor": ["#.##....", ".#.##...", "..#.##..", "..#.###.", "..#.####", "..#.##.#", ".#.##...", "#.##...."],
    "buffer": ["#.......", "###.....", "#####...", "#######.", "#######.", "#####...", "###.....", "#......."],
    "multiplexer": ["##....##", "##....##", ".##..##.", "..####..", "...##...", "...##...", "..####..", "..####.."],
    "pulse": ["........", "...##...", "...##...", "...##...", "...##...", "...##...", "########", "........"],
    "repeater": ["...#....", "..##....", ".#######", "########", ".#######", "..##....", "...#....", "........"],
    "randomizer": ["######..", "#....#..", "#.#..###", "#...#..#", "#.#.#.##", "#####..#", "....#.##", "....####"],
    "rs_latch": ["#####...", "#...#...", "#####...", "#..#....", "#...##..", "....####", "....#...", "....####"],
    "toggle_latch": ["########", "...##...", "...##...", "........", ".######.", "#......#", "#......#", ".######."],
    "timer": ["..####..", ".#....#.", "#...#..#", "#...#..#", "#..##..#", "#......#", ".#....#.", "..####.."],
    "counter": ["##...##.", ".#..#..#", ".#.....#", ".#....#.", ".#...#..", ".#..#...", "###.####", "........"],
}


def gate_plate(kind):
    """A gate's stone circuit plate: polished stone, gold traces from each input to the chip, the chip with its symbol."""
    r = random.Random(hash(kind) & 0xFFFF)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.05, 0.05)
            px(img, x, y, shade(C("8e9196"), k))
    for i in range(16):
        px(img, i, 0, C("b8bcc2"))
        px(img, 0, i, C("b8bcc2"))
        px(img, i, 15, C("5a5e64"))
        px(img, 15, i, C("5a5e64"))
    # Traces: left, back, right inputs and the front output to the chip.
    gold, dark = C("e6c34a"), C("8a6a1a")
    for x in range(1, 4):
        px(img, x, 8, gold)
        px(img, x, 9, dark)
        px(img, 15 - x, 8, gold)
        px(img, 15 - x, 9, dark)
    for y in range(12, 15):
        px(img, 7, y, gold)
        px(img, 8, y, dark)
    for y in range(1, 4):
        px(img, 7, y, gold)
        px(img, 8, y, dark)
    # The chip: a dark square carrying the symbol in pale gold.
    for y in range(4, 12):
        for x in range(4, 12):
            px(img, x, y, C("23262c"))
    # Symbols are drawn pointing right; turn them to point at the output (the plate's top edge).
    glyph = GLYPHS[kind]
    if kind == "repeater":
        glyph = [row[::-1] for row in glyph]
    rotate = kind not in ("timer", "counter", "randomizer", "rs_latch", "toggle_latch", "pulse", "multiplexer")
    for y, row in enumerate(glyph):
        for x, ch in enumerate(row[:8]):
            if ch == "#":
                gx, gy = (y, 7 - x) if rotate else (x, y)
                px(img, 4 + gx, 4 + gy, C("ffe8a0"))
    return img


def gate_side():
    img = new()
    for y in range(16):
        for x in range(16):
            px(img, x, y, C("6e7176") if y % 4 else C("5a5e64"))
    return img


def lamp_part(on, color="ff3a2a"):
    img = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            px(img, x, y, mix(C(color), C("ffffff"), max(0, 0.6 - d / 12)) if on else shade(C(color), 0.35))
    return img


# ---------------------------------------------------------------- lamps
def illumar(color, on):
    """Illumar lamp: a frosted glass block in its colour; lit, it blazes with a white-hot heart."""
    base = C(color)
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                px(img, x, y, shade(base, 0.55 if on else 0.4))
            elif edge == 1:
                px(img, x, y, shade(base, 1.0 if on else 0.55))
            else:
                d = math.hypot(x - 7.5, y - 7.5) / 7
                if on:
                    px(img, x, y, mix(C("ffffff"), base, min(1, d * 1.2)))
                else:
                    px(img, x, y, shade(base, 0.45 + 0.1 * ((x + y) % 3 == 0)))
    return img


def halo(color):
    """The halo round a lit lamp: soft at the rim, clear in the middle (drawn as a slightly larger shell)."""
    base = C(color)
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            a = max(0, 120 - edge * 22)
            img.putpixel((x, y), base[:3] + (a,))
    return img


# ---------------------------------------------------------------- items
def red_alloy_ingot():
    import gen_textures
    return gen_textures.recolor(gen_textures.INGOT_MAP, ["2a0604", "4a0c08", "6a1410", "9a221a", "c83424", "ee5a44", "ff9a84", "ffd8cc"])


def screwdriver():
    rows = [
        "................",
        ".............##.",
        "............#ss#",
        "...........#ss#.",
        "..........#ss#..",
        ".........#ss#...",
        "........#ss#....",
        ".......#kk#.....",
        "......#rrr#.....",
        ".....#rrr#......",
        "....#rRr#.......",
        "...#rRr#........",
        "..#rrr#.........",
        "..#rr#..........",
        "...##...........",
        "................",
    ]
    return stamp(rows, {"#": C("1a1a1e"), "s": C("c8d0d8"), "k": C("4a4e56"), "r": C("c02a20"), "R": C("ff6a50")})


def coil(color, core=None):
    """A wire's item icon: a loose coil of it, the end trailing off."""
    img = new()
    base = C(color)
    for k, (cx, cy) in enumerate(((7.0, 7.5), (8.0, 8.0), (9.0, 8.5))):
        for a in range(0, 360, 6):
            x = cx + 4.6 * math.cos(math.radians(a))
            y = cy + 3.0 * math.sin(math.radians(a))
            light = 0.75 + 0.35 * math.sin(math.radians(a + 200))
            px(img, int(round(x)), int(round(y)), shade(base, light))
    for i in range(4):
        px(img, 12 + i // 2, 11 + i, shade(base, 0.9))
    if core:
        px(img, 14, 14, C(core))
    return outline(img, C("1a1a1e"))


def generate_textures():
    save(coil("d02a20", "ffd0a0"), I + "red_alloy_wire.png")
    for name, col in DYES.items():
        save(coil(col, "b06a2a"), I + f"insulated/{name}_insulated_wire.png")
    save(coil("2a2d33", "f9801d"), I + "bundled_cable.png")
    save(alloy(False), B + "red_alloy.png")
    save(alloy(True), B + "red_alloy_on.png")
    for name, col in DYES.items():
        save(insulated(col, False), B + f"insulated/{name}.png")
        save(insulated(col, True), B + f"insulated/{name}_on.png")
        save(illumar(col, False), B + f"lamp/{name}.png")
        save(illumar(col, True), B + f"lamp/{name}_on.png")
        save(halo(col), B + f"lamp/{name}_halo.png")
    save(bundled(), B + "bundled.png")
    for kind in GLYPHS:
        save(gate_plate(kind), B + f"gate/{kind}.png")
    save(gate_side(), B + "gate/side.png")
    save(lamp_part(False), B + "gate/lamp.png")
    save(lamp_part(True), B + "gate/lamp_on.png")
    save(red_alloy_ingot(), I + "red_alloy_ingot.png")
    save(screwdriver(), I + "screwdriver.png")


if __name__ == "__main__":
    generate_textures()
    print("Redstone textures written.")
