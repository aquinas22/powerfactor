"""Bedrock fragments and the things they make (Essence of Bedrock, Pocket Voids), fulgurite and fused sand,
the 16 elevator colours, and the Chunk Loader card.

Run directly or call generate_textures().
"""
import colorsys
import math
import random

from quantum_art import C, mix, new, outline, px, save, save_anim, stamp

B, I = "block/", "item/"


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


# ---------------------------------------------------------------- bedrock
BEDROCK = {"0": C("101010"), "1": C("2a2a2a"), "2": C("474747"), "3": C("6a6a6a"), "4": C("979797"), "5": C("c4c4c4")}


def bedrock_fragment():
    """A jagged chip of bedrock: vanilla bedrock's mottled greys, a bright fractured face catching the light."""
    rows = [
        "................",
        "................",
        "........0.......",
        "......00410.....",
        ".....0145310....",
        "....012454210...",
        "...0123432100...",
        "...01231232210..",
        "..012121232310..",
        "..0112123132100.",
        "...0111212221...",
        "....00111211....",
        "......001110....",
        "........000.....",
        "................",
        "................",
    ]
    return stamp(rows, BEDROCK)


def essence_of_bedrock(frame=0):
    """A stoppered vial of bedrock smoke: grey murk swirling round a violet heart, glass catching the light."""
    img = new()
    cx, cy, r = 7.5, 10.0, 4.6
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < r:
                a = math.atan2(y - cy, x - cx) + d * 0.9 + frame * 0.6
                swirl = 0.5 + 0.5 * math.sin(a * 2)
                core = max(0.0, 1 - d / 2.4)
                c = mix(mix(C("1a1622"), C("4a4258"), swirl), C("b18cff"), core * 0.85)
                px(img, x, y, c)
            elif d < r + 1.0:
                px(img, x, y, C("d6e2ee") if (x < cx and y < cy + 1) else C("6a7480"))
    # Neck and cork.
    for y in range(3, 6):
        px(img, 6, y, C("d6e2ee"))
        px(img, 9, y, C("6a7480"))
        px(img, 7, y, C("2a2434"))
        px(img, 8, y, C("3a3448"))
    for x in range(6, 10):
        px(img, x, 2, C("8a5a34") if x < 9 else C("5a3a20"))
        px(img, x, 1, C("b07a48") if x < 9 else C("8a5a34"))
    # Glints: one on the glass, sparks in the murk.
    px(img, 5, 8, C("ffffff"))
    px(img, 5, 9, C("e8f2fa"))
    px(img, 9, 11, C("e8dcff"))
    return outline(img, C("14101c"))


# ---------------------------------------------------------------- lightning
def fulgurite():
    """A branching glass tube fused by lightning: sandy crust outside, a glassy white-blue core."""
    rows = [
        "................",
        "............cc..",
        "...........cgc..",
        "..........cgc...",
        "......c..cgc....",
        ".....cgccgc.....",
        "......cggc......",
        ".....cggc.......",
        "....cggc........",
        "...cgwc.........",
        "...cgc..........",
        "..cggc..........",
        "..cgc...........",
        ".cgc............",
        ".cc.............",
        "................",
    ]
    img = stamp(rows, {"c": C("b8925a"), "g": C("dfeef6"), "w": C("ffffff")})
    # Crust shading: lower-right edges darker.
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] and p[:3] == (0xb8, 0x92, 0x5a) and (x + y) % 3 == 0:
                img.putpixel((x, y), C("8a6a3a"))
    return outline(img, C("3a2a16"))


def fused_sand():
    """Sand with lightning's glassy veins branching through it and a few scorched grains."""
    r = random.Random(41)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.07, 0.07)
            img.putpixel((x, y), shade((219, 207, 163), k))
    # Branching veins from a strike at the top.
    def vein(x, y, dx, steps, depth):
        for _ in range(steps):
            px(img, int(x), int(y), C("eef8fc"))
            px(img, int(x) + 1, int(y), C("b8d4e0"))
            x += dx + r.uniform(-0.4, 0.4)
            y += 1
            if depth < 2 and r.random() < 0.18:
                vein(x, y, -dx * 0.8, steps // 2, depth + 1)
    vein(7, 0, 0.3, 16, 0)
    vein(3, 4, -0.2, 9, 1)
    for _ in range(7):
        px(img, r.randrange(16), r.randrange(16), C("5a4a2e"))
    return img


# ---------------------------------------------------------------- pocket void
# The Pocket Void is a cube: a frame of bars round a box whose walls open onto the End's starfield (drawn by its
# special renderer, with the black hole inside). The bars use automatic UVs, so every bar face samples a 4px border
# strip of this 32px sheet: the outer edge at the rim, the trim facing the void at the inside.
VOID_TRIM = {False: ("6a4aa0", "b18cff", "efe6ff"), True: ("8a6420", "e6c34a", "fff4c8")}


def pocket_void_frame(deep, phase):
    """One frame of the bar sheet: a bevelled dark metal rim, obsidian speckle and a glowing trim on the inner edge
    with a bright pulse running round it."""
    r = random.Random(41 + deep)
    dim, mid, hot = (C(h) for h in VOID_TRIM[deep])
    metal = [C("0e0a14"), C("1c1626"), C("2c2438"), C("4a3e5c")] if not deep else [C("120e0a"), C("241c14"), C("3a2e20"), C("5e4c34")]
    img = new(32, 32)
    for y in range(32):
        for x in range(32):
            d = min(x, y, 31 - x, 31 - y)
            if d >= 4:
                px(img, x, y, metal[0])
                continue
            # Distance round the border, for the travelling pulse.
            if y == d:
                s = x
            elif x == 31 - d:
                s = 32 + y
            elif y == 31 - d:
                s = 64 + (31 - x)
            else:
                s = 96 + (31 - y)
            if d == 0:
                c = metal[3]
            elif d == 3:
                pulse = max(0.0, 1 - abs(((s - phase * 16) % 128) - 8) / 8)
                c = mix(mix(dim, mid, 0.55 + 0.45 * math.sin(phase * math.pi / 4)), hot, pulse)
            else:
                c = metal[1 + (r.random() < 0.18)] if d == 1 else metal[1 + (r.random() < 0.08)]
            px(img, x, y, c)
    return img


def pocket_void_corner(deep):
    """A corner cap: a faceted dark block set with a gem of the trim colour."""
    dim, mid, hot = (C(h) for h in VOID_TRIM[deep])
    metal = [C("0e0a14"), C("1c1626"), C("2c2438"), C("4a3e5c")] if not deep else [C("120e0a"), C("241c14"), C("3a2e20"), C("5e4c34")]
    img = new()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            px(img, x, y, metal[3] if d == 0 else metal[2] if d == 1 else metal[1])
    for y in range(16):
        for x in range(16):
            g = abs(x - 7.5) + abs(y - 7.5)
            if g < 5.5:
                px(img, x, y, hot if g < 1.5 else mid if g < 3.5 else dim)
    px(img, 6, 6, C("ffffff"))
    return img


# ---------------------------------------------------------------- elevator
DYES = {"white": "f9fffe", "orange": "f9801d", "magenta": "c74ebd", "light_blue": "3ab3da", "yellow": "fed83d", "lime": "80c71f",
        "pink": "f38baa", "gray": "474f52", "light_gray": "9d9d97", "cyan": "169c9c", "purple": "8932b8", "blue": "3c44aa",
        "brown": "835432", "green": "5e7c16", "red": "b02e26", "black": "1d1d21"}


def wool(color, seed):
    """Woollen weave: soft diagonal fibres in the dye's colour."""
    r = random.Random(seed)
    base = C(color)
    img = new()
    for y in range(16):
        for x in range(16):
            fibre = ((x + y * 2) % 5 == 0) * 0.08 - ((x * 3 + y) % 7 == 0) * 0.07
            px(img, x, y, shade(base, 0.86 + fibre + r.uniform(-0.04, 0.04)))
    return img


def elevator_side(color):
    img = wool(color, 7)
    base = C(color)
    for i in range(16):
        px(img, i, 0, shade(base, 1.12))
        px(img, i, 15, shade(base, 0.55))
        px(img, 0, i, shade(base, 1.05))
        px(img, 15, i, shade(base, 0.62))
    # A brass band near the top: the lift's landing plate seen edge-on.
    for x in range(1, 15):
        px(img, x, 1, C("c8a24a"))
        px(img, x, 2, C("7a5a20"))
    return img


def elevator_top(color):
    """The landing: a brass-framed plate with an up and a down chevron."""
    img = wool(color, 9)
    base = C(color)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            px(img, x, y, C("e6c34a"))
        for (x, y) in ((i, 15), (15, i)):
            px(img, x, y, C("7a5a20"))
    for y in range(3, 13):
        for x in range(3, 13):
            px(img, x, y, shade(base, 0.62))
    up = [(7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6)]
    down = [(5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10), (7, 11), (8, 11)]
    light = C("ffffff") if color not in ("f9fffe", "fed83d", "9d9d97", "f38baa") else C("1d1d21")
    for p in up:
        px(img, *p, light)
    for p in down:
        px(img, *p, shade(light, 0.7) if light[0] > 100 else C("474f52"))
    return img


def void_core():
    """A crying-obsidian shell cracked open on a spinning black core ringed with violet."""
    img = new()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 2.2:
                px(img, x, y, C("040208"))
            elif d < 3.4:
                a = math.atan2(y - cy, x - cx)
                px(img, x, y, mix(C("6a4aa0"), C("f0e6ff"), 0.5 + 0.5 * math.sin(a * 2 + 1)))
            elif d < 6.6:
                k = (x * 7 + y * 13) % 5
                px(img, x, y, [C("1a0f2a"), C("2a1846"), C("3a2266"), C("5a2e9a"), C("2a1846")][k])
    px(img, 4, 4, C("b18cff"))
    px(img, 11, 10, C("8a5ad8"))
    return outline(img, C("0a0612"))


def chunk_card():
    import gen_textures
    return gen_textures.upgrade_card("chunk_loader")


def generate_textures():
    save(bedrock_fragment(), I + "bedrock_fragment.png")
    save(essence_of_bedrock(), I + "essence_of_bedrock.png")
    save(fulgurite(), I + "fulgurite.png")
    save(fused_sand(), B + "fused_sand.png")
    for deep, key in ((False, "pocket_void"), (True, "deep_pocket_void")):
        save_anim([pocket_void_frame(deep, i) for i in range(8)], I + f"{key}_frame.png", 2)
        save(pocket_void_corner(deep), I + f"{key}_corner.png")
    save(chunk_card(), I + "chunk_loader_upgrade.png")
    save(void_core(), I + "void_core.png")
    for name, hexcol in DYES.items():
        save(elevator_side(hexcol), B + f"elevator/{name}_side.png")
        save(elevator_top(hexcol), B + f"elevator/{name}_top.png")


if __name__ == "__main__":
    generate_textures()
    print("Void textures written.")
