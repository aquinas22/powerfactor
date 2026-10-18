"""Ore-processing art: the Wash Plant line (mining-yellow steel, trommel mesh, sluice riffles, shaker deck),
the chemistry machines, and the items they make (sulfur, catalyst, crystals and concentrates per metal).

Run directly or call generate_textures().
"""
import math
import os
import random

from PIL import Image, ImageDraw

from gen_textures import DUST_MAP, DUST_T, RAW_MAP, RAW_T, ramp_at
from quantum_art import C, mix, outline, ramp_cols, save, stamp

METALS = {  # ramp dark -> light, per OreMetal (keep in step with chem/OreMetal.java)
    "iron": ["3a2a22", "6b4c3e", "9a735e", "c4a08a", "e2c8b4", "fff0e4"],
    "gold": ["5a3a08", "9a6a10", "d29a1a", "f2c63a", "ffe27a", "fff8d0"],
    "copper": ["4a1e10", "8a3a1e", "c25a30", "e88a5a", "f8b890", "fff0e0"],
    "zinc": ["2a343a", "4a5a62", "70848e", "98acb6", "c4d4dc", "f0f8fc"],
    "tin": ["343a40", "5a646c", "848e96", "aeb8c0", "d8e0e8", "ffffff"],
    "lead": ["1a1c2c", "2e3250", "4a5078", "6a72a0", "9aa2c8", "d0d6f0"],
    "silver": ["3a4048", "6a727e", "9aa4b2", "c6d0dc", "e8f0ff", "ffffff"],
    "aluminum": ["3a3a46", "6a6a7e", "9a9cb4", "c0c8dc", "dce6f4", "ffffff"],
}
YELLOW = ["5a4208", "8a6610", "c49a1c", "e8bc2a", "f8d65a", "fff0a8"]
B, I, E = "block/", "item/", "entity/"


def noise_fill(w, h, base, var, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            k = 1 + r.uniform(-var, var)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    return img


def rivets(img, pts, light="f0f4f6", dark="2a3034"):
    for x, y in pts:
        img.putpixel((x, y), C(light))
        if x + 1 < img.width and y + 1 < img.height:
            img.putpixel((x + 1, y + 1), C(dark))


# ---------------------------------------------------------------- items
CRYSTAL = [
    "................",
    ".......4........",
    "......454.......",
    "......353...4...",
    ".....3453..454..",
    "..4..3452..353..",
    ".454.24521.3452.",
    ".3452245213452..",
    ".24522452132452.",
    "..2411345111241.",
    "..1311234111131.",
    "...11123411011..",
    "....111231101...",
    ".....0011100....",
    "................",
    "................",
]


def crystal(ramp):
    cols = ramp_cols(ramp)
    img = stamp(CRYSTAL, cols)
    for x, y in ((7, 2), (12, 4), (2, 6)):
        img.putpixel((x, y), C("ffffff"))
    return outline(img, C(ramp[0]))


def concentrate(ramp, seed):
    """Wet heavy-mineral concentrate: the dust silhouette, dark and glinting with the metal."""
    dark = ["0e0c0a", "1e1a16", "2e2822"] + ramp[2:5]
    img = stamp(DUST_MAP, ramp_cols(ramp_at(dark, DUST_T)))
    r = random.Random(seed)
    for _ in range(9):
        x, y = r.randint(3, 12), r.randint(5, 13)
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), C(ramp[5] if r.random() < 0.4 else ramp[4]))
    return img


def sulfur_dust():
    img = stamp(DUST_MAP, ramp_cols(ramp_at(["4a4208", "7a6c10", "b8a418", "e2d030", "f6ea6a", "fffab8"], DUST_T)))
    return img


def quantum_catalyst():
    """A small qubit-crystal lattice in a slim gold cage (11 px, centred): the 5x catalyst."""
    rows = [
        "................",
        "................",
        "................",
        "......ggg.......",
        ".....gvwvg......",
        "....gvwvvvg.....",
        "....gvvcvvg.....",
        "....gvcccvg.....",
        "....gvvcvdg.....",
        "....gvvvddg.....",
        ".....gvddg......",
        "......ggg.......",
        "................",
        "................",
        "................",
        "................",
    ]
    img = stamp(rows, {"g": C("e2b23a"), "v": C("8f6af0"), "w": C("ffffff"), "c": C("6fe3ff"), "d": C("4a2c9a")})
    return outline(img, C("1a0f3a"))


# ---------------------------------------------------------------- blocks
def mining_yellow(seed=11):
    """Painted mining steel: yellow enamel, worn at the edges, with a hazard band and bolts."""
    img = noise_fill(16, 16, C("e8bc2a"), 0.05, seed)
    r = random.Random(seed)
    for _ in range(10):
        x, y = r.randint(0, 15), r.randint(0, 15)
        img.putpixel((x, y), C("8a7a6a"))
    for x in range(16):
        img.putpixel((x, 0), C("fff0a8"))
        img.putpixel((x, 15), C("8a6610"))
    for x in range(16):
        for y in (12, 13):
            img.putpixel((x, y), C("1e1e22") if (x + y) // 2 % 2 else C("e8bc2a"))
    rivets(img, [(2, 3), (13, 3), (2, 9), (13, 9)])
    return img


def galvanized(seed=12):
    img = noise_fill(16, 16, C("aab4ba"), 0.06, seed)
    r = random.Random(seed)
    for _ in range(18):   # spangle
        x, y = r.randint(0, 14), r.randint(0, 14)
        img.putpixel((x, y), C("cdd6dc"))
        img.putpixel((x + 1, y), C("8e989e"))
    for i in range(16):
        img.putpixel((i, 0), C("dfe6ea"))
        img.putpixel((0, i), C("dfe6ea"))
        img.putpixel((i, 15), C("6e787e"))
        img.putpixel((15, i), C("6e787e"))
    return img


def riffle_mat():
    """Sluice floor: green miner's moss under steel riffle bars (bars across the flow)."""
    img = noise_fill(16, 16, C("2f6a3a"), 0.18, 21)
    for y in range(16):
        if y % 4 == 0:
            for x in range(16):
                img.putpixel((x, y), C("c8d0d4"))
        elif y % 4 == 1:
            for x in range(16):
                img.putpixel((x, y), C("5a6268"))
    return img


def trommel_mesh():
    """Perforated screen plate: punched holes on a steel sheet (holes see through in the drum renderer)."""
    img = noise_fill(16, 16, C("8c969c"), 0.05, 31)
    for y in range(1, 16, 4):
        for x in range(1 + (y // 4) % 2 * 2, 15, 4):
            for dx in (0, 1):
                for dy in (0, 1):
                    img.putpixel((x + dx, y + dy), C("1a1e22"))
    return img


def hopper_steel():
    img = galvanized(41)
    d = ImageDraw.Draw(img)
    d.line((0, 0, 15, 15), fill=C("7e888e"))
    d.line((15, 0, 0, 15), fill=C("7e888e"))
    return img


def shaker_deck_block():
    img = noise_fill(16, 16, C("26302c"), 0.12, 51)
    for k in range(-16, 16, 3):
        for x in range(16):
            y = x + k
            if 0 <= y < 16:
                img.putpixel((x, y), C("3e4a44"))
    return img


def wash_atlas():
    """entity/wash_plant.png: drum mesh (0,0 64x32), deck (0,32 64x32), water sheet twice (64,0 32x64), white (96,0)."""
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    mesh = Image.new("RGBA", (64, 32))
    r = random.Random(7)
    for y in range(32):
        for x in range(64):
            band = x % 16 in (0, 1)
            hole = not band and (x % 4 in (2, 3)) and ((y + (x // 4) % 2 * 2) % 4 in (1, 2))
            if hole:
                mesh.putpixel((x, y), (0, 0, 0, 0))
            elif band:
                mesh.putpixel((x, y), C("5d676d") if x % 16 == 0 else C("7e888e"))
            else:
                k = 1 + r.uniform(-0.05, 0.05)
                mesh.putpixel((x, y), tuple(int(c * k) for c in C("a2acb2")[:3]) + (255,))
    img.paste(mesh, (0, 0))
    deck = Image.new("RGBA", (64, 32))
    for y in range(32):
        for x in range(64):
            c = C("2a3430") if (x - y) % 6 not in (0, 1) else C("4a5850")
            deck.putpixel((x, y), c)
    for x in range(64):
        deck.putpixel((x, 0), C("c8d0d4"))
        deck.putpixel((x, 31), C("5a6268"))
    img.paste(deck, (0, 32))
    sheet = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            streak = 0.5 + 0.5 * math.sin(x * 0.9 + math.sin(y * 0.4) * 2.0)
            a = int(150 + 70 * streak)
            v = int(200 + 55 * streak)
            sheet.putpixel((x, y), (v, v, v, a))
    for k in range(10):
        y = (k * 7) % 32
        for x in range(k * 3 % 32, min(32, k * 3 % 32 + 6)):
            sheet.putpixel((x, y), (255, 255, 255, 240))
    img.paste(sheet, (64, 0))
    img.paste(sheet, (64, 32))
    ImageDraw.Draw(img).rectangle((96, 0, 103, 7), fill=(255, 255, 255, 255))
    # Tiles for the sloped sluice the renderer draws: riffle mat (96,32), galvanized walls (0,64), mining yellow legs (32,64).
    for tile, (ox, oy) in ((riffle_mat(), (96, 32)), (galvanized(), (0, 64)), (mining_yellow(), (32, 64))):
        for dx in (0, 16):
            for dy in (0, 16):
                img.paste(tile, (ox + dx, oy + dy))
    return img


def chem_glass():
    img = Image.new("RGBA", (16, 16), (200, 230, 240, 70))
    for i in range(16):
        img.putpixel((i, 0), C("c8d0d4"))
        img.putpixel((i, 15), C("6e787e"))
        img.putpixel((0, i), C("c8d0d4"))
        img.putpixel((15, i), C("6e787e"))
    for k in range(2, 7):
        img.putpixel((k, k + 1), (255, 255, 255, 180))
    return img


def acid_hazard():
    """Chemical-plant steel: white enamel with a yellow hazard diamond."""
    img = noise_fill(16, 16, C("e8eef2"), 0.03, 61)
    for y in range(16):
        for x in range(16):
            if abs(x - 7.5) + abs(y - 7.5) <= 5:
                img.putpixel((x, y), C("f2d230"))
            if abs(abs(x - 7.5) + abs(y - 7.5) - 5) < 0.6:
                img.putpixel((x, y), C("1e1e22"))
    for (x, y) in ((7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 9), (8, 9)):
        img.putpixel((x, y), C("1e1e22"))
    return img


DYES = ["f9fffe", "f9801d", "c74ebd", "3ab3da", "fed83d", "80c71f", "f38baa", "474f52", "9d9d97", "169c9c", "8932b8", "3c44aa", "835432",
        "5e7c16", "b02e26", "1d1d21"]   # DyeColor order: white..black


def wireless_lamp(hexcol, lit):
    base = C(hexcol)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            k = (1.15 - d / 12) if lit else (0.55 - d / 30)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    if lit:
        for x, y in ((5, 5), (6, 5), (5, 6)):
            img.putpixel((x, y), C("ffffff"))
    return img


def magnet():
    rows = [
        "................",
        "................",
        "..rrrr....bbbb..",
        "..rRRr....bBBb..",
        "..rRRr....bBBb..",
        "..rRRr....bBBb..",
        "..ssss....ssss..",
        "..sSSs....sSSs..",
        "..sSSssssssSSs..",
        "..sSSSSSSSSSSs..",
        "...sSSSSSSSSs...",
        "....ssssssss....",
        "................",
        "................",
        "................",
        "................",
    ]
    img = stamp(rows, {"r": C("a02018"), "R": C("e8463a"), "b": C("1a3a9a"), "B": C("4a7ae8"), "s": C("5a646c"), "S": C("aeb8c0")})
    return outline(img, C("14181c"))


def range_upgrade():
    rows = [
        "................",
        ".gggggggggggggg.",
        ".gddddddddddddg.",
        ".gd..........dg.",
        ".gd.cccccccc.dg.",
        ".gd.c......c.dg.",
        ".gd.c.eeee.c.dg.",
        ".gd.c.eEEe.c.dg.",
        ".gd.c.eEEe.c.dg.",
        ".gd.c.eeee.c.dg.",
        ".gd.c......c.dg.",
        ".gd.cccccccc.dg.",
        ".gd..........dg.",
        ".gddddddddddddg.",
        ".gggggggggggggg.",
        "................",
    ]
    return stamp(rows, {"g": C("c8a040"), "d": C("2a3a2e"), ".": (0, 0, 0, 0), "c": C("3affb0"), "e": C("1a6a4a"), "E": C("8affd8")})


def soul_snare(full):
    """A brass-capped glass vial; captured, a cyan soul wisp curls inside."""
    rows = [
        "................",
        "......bbbb......",
        ".....bBBBBb.....",
        "......bbbb......",
        ".......gg.......",
        "......g..g......",
        ".....g....g.....",
        "....g......g....",
        "...g........g...",
        "...g........g...",
        "...g........g...",
        "...g........g...",
        "....g......g....",
        ".....gggggg.....",
        "................",
        "................",
    ]
    img = stamp(rows, {"b": C("8a6420"), "B": C("e8c25a"), "g": C("a8d8e0"), ".": (0, 0, 0, 0)})
    # The glass body: a faint fill between the walls of each row.
    for y in range(5, 13):
        wall = [x for x in range(16) if rows[y][x] == "g"]
        for x in range(wall[0] + 1, wall[-1]):
            img.putpixel((x, y), (90, 130, 150, 70))
    for y in (7, 8, 9):
        img.putpixel((5, y), C("eafcff"))
    if full:
        for x, y in ((7, 11), (8, 11), (9, 10), (9, 9), (8, 8), (7, 8), (6, 9), (7, 10), (8, 7), (9, 6), (8, 6)):
            img.putpixel((x, y), C("4fe3f0"))
        for x, y in ((8, 10), (7, 9), (9, 7)):
            img.putpixel((x, y), C("d8ffff"))
    return outline(img, C("14181c"))


def cage_bars():
    """Spawner-cage bars: dark iron with soul-blue rivets."""
    img = noise_fill(16, 16, C("2a3036"), 0.12, 44)
    for x in (0, 1, 14, 15):
        for y in range(16):
            img.putpixel((x, y), C("4a545c") if x in (1, 14) else C("1a1e22"))
    for y in (0, 15):
        for x in range(16):
            img.putpixel((x, y), C("1a1e22"))
    for p in ((2, 2), (12, 2), (2, 12), (12, 12)):
        img.putpixel(p, C("4fe3f0"))
    return img


def saw_hazard():
    """Grinder casing: yellow-black hazard chevrons."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 4) % 2 == 0
            k = 1 + ((x * 7 + y * 13) % 5 - 2) * 0.03
            base = C("e8bc2a") if stripe else C("1e2226")
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base[:3]) + (255,))
    return img


def pocket_wall():
    """The shell of a pocket room: deep navy with a glowing cyan seam round every panel and a faint sub-grid."""
    r = random.Random(71)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.06, 0.06)
            base = (14, 20, 48)
            if x % 4 == 0 or y % 4 == 0:
                base = (24, 40, 86)
            img.putpixel((x, y), tuple(int(c * k) for c in base) + (255,))
    for i in range(16):
        for p in ((i, 0), (0, i)):
            img.putpixel(p, C("6fe3ff"))
        for p in ((i, 15), (15, i)):
            img.putpixel(p, C("2a8ab0"))
    for p in ((5, 9), (11, 3), (9, 12)):
        img.putpixel(p, C("b18cff"))
    return img


def pocket_frame():
    """The Dimensional Pocket's edges: dark violet steel with a lit violet strip."""
    img = noise_fill(16, 16, C("2a2440"), 0.1, 72)
    for i in range(16):
        img.putpixel((i, 7), C("b18cff"))
        img.putpixel((i, 8), C("7a5ad0"))
        img.putpixel((i, 0), C("14101e"))
        img.putpixel((i, 15), C("14101e"))
    return img


def pocket_port():
    """Pocket Port face: a cyan ring round a violet core on the shell's navy."""
    img = pocket_wall()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 < d < 6.2:
                img.putpixel((x, y), C("6fe3ff") if d < 5.6 else C("2a8ab0"))
            elif d < 3.2:
                img.putpixel((x, y), C("e8dcff") if d < 1.6 else C("b18cff"))
    return img


def spatial_expander():
    """A violet cube with four arrows pushing out of it."""
    rows = [
        "................",
        ".......c........",
        "......ccc.......",
        ".......c........",
        "....vvvvvvvv....",
        "....vVVVVVVv....",
        ".c..vVwwwwVv..c.",
        "ccc.vVwVVwVv.ccc",
        ".c..vVwVVwVv..c.",
        "....vVwwwwVv....",
        "....vVVVVVVv....",
        "....vvvvvvvv....",
        ".......c........",
        "......ccc.......",
        ".......c........",
        "................",
    ]
    return outline(stamp(rows, {"v": C("4a2e8a"), "V": C("8a5ae0"), "w": C("e8dcff"), "c": C("6fe3ff"), ".": (0, 0, 0, 0)}), C("14181c"))


def vein_matrix():
    """A gold-framed crystal lattice with a vein of cyan light branching through it."""
    rows = [
        "................",
        "...gggggggggg...",
        "..gGddddddddGg..",
        ".gGd..c.....dGg.",
        ".gd...cc....dg..",
        ".gd....c..c.dg..",
        ".gd....ccc..dg..",
        ".gd...cWc...dg..",
        ".gd..cc.c...dg..",
        ".gd.c...cc..dg..",
        ".gd.c....c..dg..",
        ".gd......cc.dg..",
        ".gGd.......dGg..",
        "..gGddddddddGg..",
        "...gggggggggg...",
        "................",
    ]
    return outline(stamp(rows, {"g": C("8a6420"), "G": C("e8c25a"), "d": C("1a2440"), "c": C("6fe3ff"), "W": C("ffffff"), ".": (20, 30, 60, 255)}), C("14181c"))


def generate_utility_textures():
    for i, h in enumerate(DYES):
        save(wireless_lamp(h, False), B + f"wireless_lamp_{i}.png")
        save(wireless_lamp(h, True), B + f"wireless_lamp_{i}_on.png")
    save(magnet(), I + "item_magnet.png")
    save(range_upgrade(), I + "range_upgrade.png")
    save(soul_snare(False), I + "soul_snare.png")
    save(soul_snare(True), I + "soul_snare_full.png")
    save(cage_bars(), B + "cage_bars.png")
    save(saw_hazard(), B + "saw_hazard.png")
    save(pocket_wall(), B + "pocket_wall.png")
    save(pocket_frame(), B + "pocket_frame.png")
    save(pocket_port(), B + "pocket_port.png")
    save(spatial_expander(), I + "spatial_expander.png")
    save(vein_matrix(), I + "vein_mining_matrix.png")


def generate_textures():
    generate_utility_textures()
    for m, ramp in METALS.items():
        save(crystal(ramp), I + f"{m}_crystal.png")
        save(concentrate(ramp, hash(m) & 0xFFFF), I + f"{m}_concentrate.png")
    save(sulfur_dust(), I + "sulfur_dust.png")
    save(quantum_catalyst(), I + "quantum_catalyst.png")
    save(mining_yellow(), B + "mining_yellow.png")
    save(galvanized(), B + "galvanized_steel.png")
    save(riffle_mat(), B + "sluice_riffle.png")
    save(trommel_mesh(), B + "trommel_mesh.png")
    save(hopper_steel(), B + "hopper_steel.png")
    save(shaker_deck_block(), B + "shaker_deck.png")
    save(chem_glass(), B + "chem_glass.png")
    save(acid_hazard(), B + "acid_hazard.png")
    save(wash_atlas(), E + "wash_plant.png")


if __name__ == "__main__":
    generate_textures()
    print("Process textures written.")
