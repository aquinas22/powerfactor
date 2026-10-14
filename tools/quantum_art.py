"""Exotic-materials family: adamantium -> faradaenium -> cortanium -> quantum alloy, plus qubit crystal.

Ingots, raw ores, dusts, metal blocks and tools are stamped from role grids taken from vanilla
sprites (see gen_textures.py: INGOT_MAP, RAW_MAP, DUST_MAP, IRON/GOLD_BLOCK_MAP), so every
silhouette matches vanilla exactly; each material then gets hand-placed flair that escalates
with tier (mirror glints -> lightning cracks -> molten seams -> animated iridescence).

Run directly or call generate_textures().
"""
import json
import math
import os

from PIL import Image

from gen_textures import (DUST_MAP, DUST_T, GOLD_BLOCK_MAP, GOLD_BLOCK_T, INGOT_MAP, INGOT_T,
                          IRON_BLOCK_MAP, IRON_BLOCK_T, ORE_MASKS, RAW_MAP, RAW_T, grad, ramp_at)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                    "assets", "powerfactor", "textures")
CLEAR = (0, 0, 0, 0)
K36 = "0123456789abcdefghijklmnopqrstuvwxyz"


# ---------------------------------------------------------------- primitives
def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def new(w=16, h=16):
    return Image.new("RGBA", (w, h), CLEAR)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def get(img, x, y):
    if 0 <= x < img.width and 0 <= y < img.height:
        return img.getpixel((x, y))
    return CLEAR


def solid(img, x, y):
    return get(img, x, y)[3] > 0


def outline(img, color):
    """Vanilla item outline: paint transparent pixels touching opaque ones (4-neighbourhood)."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if solid(src, x + dx, y + dy):
                    img.putpixel((x, y), color)
                    break
    return img


def stamp(rows, cols):
    """Role grid -> image. cols maps a role character to a colour tuple."""
    img = new(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), cols[ch])
    return img


def stamp_fn(rows, fn):
    """Role grid -> image, colour computed per pixel: fn(role_char, x, y)."""
    img = new(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), fn(ch, x, y))
    return img


def ramp_cols(rows_ramp):
    """List of hex strings -> {'0': colour, '1': ...}."""
    return {K36[i]: C(h) for i, h in enumerate(rows_ramp)}


def save(img, path):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)
    mc = full + ".mcmeta"
    if os.path.exists(mc):
        os.remove(mc)
    return full


def save_anim(frames, path, frametime):
    strip = new(frames[0].width, frames[0].height * len(frames))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * f.height))
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    strip.save(full)
    with open(full + ".mcmeta", "w") as fh:
        json.dump({"animation": {"frametime": frametime}}, fh, indent=2)
    return full


# ---------------------------------------------------------------- material ramps (dark -> light)
ADAM = ["141a24", "283446", "41546e", "62799a", "93abcc", "e8f2ff"]
FARA = ["0b3b3a", "0f5a57", "15837c", "22b3a6", "3ff2e0", "e6fffb"]
CORT = ["130305", "26060a", "3a0a10", "520e16", "731420", "b0202c"]
QUBIT = ["1c1038", "2a1a52", "432c86", "6248bb", "7f62e6", "9c7cff", "bea8ff", "f0e8ff"]
# Iridescent quantum alloy: two ramps of equal lightness, blended by a travelling hue phase.
QV = ["140b30", "2e1a6a", "5232b0", "6a3fd0", "9468f0", "b18cff", "e2d6ff"]
QC = ["0a1c38", "143e74", "1f6aa8", "2c95d0", "48c4f0", "6fe3ff", "e0fbff"]

WHITE = C("ffffff")
MOLTEN = [C("7a0c14"), C("c41e2a"), C("ff4a3a"), C("ff8a5a"), C("ffd0b8")]
EMBER = C("ffb0a8")


def irid(t, w):
    """Quantum-alloy colour: lightness t in [0,1], hue phase w in [0,1] (0 violet, 1 cyan)."""
    w = round(w * 3) / 3.0          # four deliberate hue steps, no smooth gradient noise
    return mix(C(grad(QV, t)), C(grad(QC, t)), w)


def hue_w(x, y, phase, period=14.0):
    return 0.5 + 0.5 * math.sin(2 * math.pi * ((x + y) / period - phase))


# ---------------------------------------------------------------- vanilla-derived role grids
# Vanilla diamond tool silhouettes. Head roles 0-7 by luminance (0 bottom/right rim, 1 top/left
# rim, 7 glint); handle roles a (shadow) b (top/left rim) c d (lit).
TOOL_T = [0.0, 0.118, 0.25, 0.40, 0.55, 0.62, 0.75, 1.0]
TOOLS = {
    "sword": [
        ".............111",
        "............1770",
        "...........17570",
        "..........17570.",
        ".........17560..",
        "........17560...",
        "..11...16560....",
        "..121.16560.....",
        "...1306360......",
        "...133260.......",
        "....1210........",
        "...bc0110.......",
        "..bda.0010......",
        "11ca....00......",
        "120.............",
        "000.............",
    ],
    "pickaxe": [
        "................",
        "................",
        "......11111.....",
        ".....1654451bc..",
        "......100044da..",
        "..........b540..",
        ".........bca450.",
        "........bda.040.",
        ".......bca..040.",
        "......bda...050.",
        ".....bca....060.",
        "....bda......0..",
        "...bca..........",
        "..bda...........",
        "..aa............",
        "................",
    ],
    "axe": [
        "................",
        ".........11.....",
        "........1661....",
        ".......16451....",
        "......16444bc...",
        "......065434a...",
        ".......00b4340..",
        "........bca440..",
        ".......bda.00...",
        "......bca.......",
        ".....bca........",
        "....bda.........",
        "...bca..........",
        "..bda...........",
        "..aa............",
        "................",
    ],
    "shovel": [
        "................",
        "................",
        "...........110..",
        "..........16640.",
        ".........165460.",
        "........1654560.",
        ".........b4560..",
        "........bda60...",
        ".......bda.0....",
        "......bca.......",
        ".....bda........",
        "....bca.........",
        "..bbda..........",
        "..bda...........",
        "...aa...........",
        "................",
    ],
    "hoe": [
        "................",
        ".......111......",
        "......16551.....",
        ".......00451bc..",
        ".........044da..",
        "..........b540..",
        ".........bca0...",
        "........bda.....",
        ".......bca......",
        "......bda.......",
        ".....bca........",
        "....bda.........",
        "...bca..........",
        "..bda...........",
        "..aa............",
        "................",
    ],
}

# Vanilla diamond.png (10 roles) - the facet layout of the qubit crystal.
GEM_MAP = [
    "................",
    "................",
    "......1111......",
    ".....199981.....",
    "....19667840....",
    "...1967787640...",
    "...1969898660...",
    "..196966773430..",
    "..196867772430..",
    "..199477762330..",
    "..186633334420..",
    "...0634444340...",
    "...0534444370...",
    "....02667740....",
    ".....000000.....",
    "................",
]
# Vanilla netherite_block.png (10 roles, luminance 32..88).
NETHERITE_BLOCK_MAP = [
    "3233333333323320",
    "2799999999989980",
    "3947788857667780",
    "3957668878865480",
    "3975686888875370",
    "3957567898885372",
    "3955578889777470",
    "3975488888757480",
    "3978888778575472",
    "3988985557557472",
    "2888685555443380",
    "3986867445443180",
    "2847775445431180",
    "2843444311111382",
    "2877788878888870",
    "2000022000220000",
]
NETHERITE_BLOCK_T = [(v - 32) / 56.0 for v in (32, 42, 44, 53, 58, 65, 69, 70, 75, 88)]
# Vanilla amethyst_block.png (7 roles).
AMETHYST_BLOCK_MAP = [
    "1232212566431230",
    "2343123234002331",
    "1321134214014221",
    "1231002112145322",
    "2352011123123102",
    "3463212245322011",
    "5664422123121113",
    "2341124232232131",
    "1241345321121341",
    "2223423100232453",
    "2234532012343231",
    "2344543123454321",
    "1456644211240142",
    "1233501102221253",
    "2123412125212564",
    "0112322256320250",
]
# Project microprocessor.png roles: 0 outline, 1-2 board, 3/8 die bevel, 4/5/7 die, 6 gold pin.
CHIP_MAP = [
    "................",
    "..000000000000..",
    ".01161616161610.",
    ".01622222222260.",
    ".06288888883210.",
    ".01287755553260.",
    ".06287545553210.",
    ".01285454553260.",
    ".06285545453210.",
    ".01285555543260.",
    ".06285555443210.",
    ".01233333333260.",
    ".06222222222210.",
    ".01616161616110.",
    "..000000000000..",
    "................",
]
# Project logic_photomask.png roles: 0 outline, 5 lit rim, 3 inner rim, 1/2 shadow rims, 6/7 field.
MASK_MAP = [
    ".00000000000000.",
    "0555555555555520",
    "0533333333333120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0536666666666120",
    "0511111111111120",
    "0222222222222220",
    ".00000000000000.",
]


# ---------------------------------------------------------------- ingots / raw / dust
def ingot(stops):
    return stamp(INGOT_MAP, ramp_cols(ramp_at(stops, INGOT_T)))


def adamantium_ingot():
    img = ingot(ADAM)
    # Mirror finish: a hard white reflection band across the top face, a cold blue under-glint.
    for (x, y) in [(5, 5), (6, 5), (4, 6), (5, 6), (8, 4), (9, 4)]:
        px(img, x, y, WHITE)
    for (x, y) in [(3, 7), (4, 7)]:
        px(img, x, y, C("d4e4fa"))
    return img


def faradaenium_ingot():
    img = ingot(FARA)
    # Lightning crack running down the front face, a tiny fork on the top face.
    hot, glow = C("f4fffd"), C("7dfff0")
    for (x, y) in [(12, 8), (11, 9), (10, 9), (9, 10), (8, 10), (7, 11)]:
        px(img, x, y, hot)
    for (x, y) in [(10, 6), (9, 7), (8, 7)]:
        px(img, x, y, glow)
    return img


def cortanium_ingot():
    img = ingot(CORT)
    # Molten seam along the top-face bevel and a glowing fracture across the front face.
    for y, row in enumerate(INGOT_MAP):
        for x, ch in enumerate(row):
            if ch == "5":
                px(img, x, y, MOLTEN[1])
            elif ch == "7":
                px(img, x, y, EMBER)
    for (x, y), c in [((12, 8), MOLTEN[1]), ((11, 9), MOLTEN[2]), ((10, 9), MOLTEN[3]),
                      ((9, 10), MOLTEN[2]), ((8, 10), MOLTEN[1])]:
        px(img, x, y, c)
    return img


def quantum_ingot(frame):
    ph = frame / 8.0
    s = -3 + frame * 5          # sheen band position along x+y
    sparkles = {0: [(13, 6)], 1: [(13, 6)], 2: [(6, 6)], 3: [], 4: [(9, 5)], 5: [(9, 5)], 6: [], 7: [(3, 9)]}

    def fn(ch, x, y):
        t = INGOT_T[int(ch)]
        d = x + y - s
        if abs(d) <= 1 and ch not in "01":
            t = min(1.0, t + (0.35 if d == 0 else 0.18))
        return irid(t, hue_w(x, y, ph))
    img = stamp_fn(INGOT_MAP, fn)
    for (x, y) in sparkles[frame]:
        px(img, x, y, WHITE)
    return img


def raw(stops):
    return stamp(RAW_MAP, ramp_cols(ramp_at(stops, RAW_T)))


def raw_adamantium():
    img = raw(["141a24", "283446", "41546e", "62799a", "93abcc", "dce9fb"])
    px(img, 3, 3, WHITE)
    px(img, 9, 9, WHITE)
    return img


def raw_faradaenium():
    img = raw(["0b2e2e", "103f3e", "17605b", "1f8a82", "2fb8aa", "6ff0e2"])
    # Crevices glow: the dark seams in the rock carry a live charge.
    for y, row in enumerate(RAW_MAP):
        for x, ch in enumerate(row):
            if ch == "1" and 2 <= x <= 13 and 4 <= y <= 12:
                px(img, x, y, C("3ff2e0") if (x + y) % 3 else C("e6fffb"))
    return img


def quantum_dust():
    img = stamp(DUST_MAP, ramp_cols(ramp_at(["1a0f3a", "3a2380", "6a3fd0", "8f6af0", "b18cff", "e6dcff"], DUST_T)))
    for (x, y), c in [((6, 5), WHITE), ((9, 8), C("6fe3ff")), ((5, 9), C("6fe3ff")), ((10, 6), WHITE)]:
        px(img, x, y, c)
    return img


def cosmic_dust():
    """Deep-space dust: midnight blue to violet, scattered with white and gold star points."""
    img = stamp(DUST_MAP, ramp_cols(ramp_at(["0a0c24", "1a2058", "2c3a90", "4a5ac8", "7a8cf0", "c8d4ff"], DUST_T)))
    for (x, y), c in [((5, 6), WHITE), ((9, 7), C("ffe8a0")), ((7, 10), WHITE), ((11, 9), C("b18cff")), ((4, 9), C("ffe8a0"))]:
        if solid(img, x, y):
            px(img, x, y, c)
    return img


# ---------------------------------------------------------------- shards & gems
# Cortanium shard: body roles 1 (shadow) .. 6 (ember glint); outline added afterwards.
SHARD_MAP = [
    "................",
    ".............1..",
    "............1650",
    "...........15640",
    "..........155420",
    ".........1556420",
    "........1453420.",
    "........045220..",
    "......1.0452120.",
    ".....131.0420...",
    "....12253120....",
    "...1232310......",
    "..1231210.......",
    "..0121..........",
    "...00...........",
    "................",
]
SHARD_COLS = {"0": C("2a060c"), "1": C("4e0b14"), "2": C("7e1420"), "3": C("b81f2e"), "4": C("e8343e"),
              "5": C("ff8a70"), "6": C("ffd0c0")}


def cortanium_shard():
    """A jagged crystal splinter: faceted ridge, notched lit edge, snapped base and a loose chip."""
    return outline(stamp(SHARD_MAP, SHARD_COLS), C("1a0508"))


QGEM = ["1c1040", "2a1760", "3a2290", "4e30b4", "6640d4", "7f58ec", "9a78ff", "b89cff", "dccdff", "ffffff"]


def qubit_crystal(frame):
    img = stamp(GEM_MAP, ramp_cols(QGEM))
    # A four-point twinkle gliding across the facets top-left -> bottom-right, then resting.
    path = [(5, 6), (6, 7), (7, 8), (8, 9), (9, 10), (10, 11), None, None]
    size = [1, 2, 2, 2, 2, 1, 0, 0]
    p = path[frame]
    if p:
        x, y = p
        for k in range(1, size[frame] + 1):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                gx, gy = x + dx * k, y + dy * k
                if GEM_MAP[gy][gx] not in ".0":
                    px(img, gx, gy, C("e6dcff") if k == 1 else C("b89cff"))
        px(img, x, y, WHITE)
    return img


# ---------------------------------------------------------------- tools
def tool(kind, head_fn, handle_fn):
    rows = TOOLS[kind]

    def fn(ch, x, y):
        if ch.isdigit():
            return head_fn(int(ch), x, y)
        return handle_fn(ch, x, y)
    return stamp_fn(rows, fn)


def head_ramp(stops):
    cols = [C(h) for h in ramp_at(stops, TOOL_T)]
    return lambda r, x, y: cols[r]


def handle_ramp(hexes):
    cols = dict(zip("abcd", (C(h) for h in hexes)))
    return lambda ch, x, y: cols[ch]


def head_pixels(kind, roles):
    for y, row in enumerate(TOOLS[kind]):
        for x, ch in enumerate(row):
            if ch in roles:
                yield x, y


def adamantium_tool(kind):
    return tool(kind, head_ramp(["141a24", "2a3648", "485c78", "6e88ac", "a2bad8", "f4f9ff"]),
                handle_ramp(["15181c", "2b3036", "424850", "5c636d"]))


def faradaenium_tool(kind):
    # Clean teal metal on a gold haft; the charge shows in-world (glow and arcs), not as speckle.
    return tool(kind, head_ramp(["0b3b3a", "0f5a57", "15837c", "1fa89c", "2fd0c2", "8ff8ec"]),
                handle_ramp(["6b3400", "b86a00", "f0a800", "ffe46a"]))


def cortanium_tool(kind):
    rows = TOOLS[kind]
    head = head_ramp(CORT[:5] + ["c8303a"])
    dark = dict(zip("abcd", (C(h) for h in ["0c090a", "1c1517", "2c2326", "3e3236"])))
    wrap = dict(zip("abcd", (C(h) for h in ["3a060c", "6e0f18", "a81c26", "e0303c"])))

    def handle(ch, x, y):
        return wrap[ch] if y % 4 == 1 else dark[ch]
    img = tool(kind, head, handle)
    # Molten cutting edge: the outer (bottom/right) rim of the head glows.
    for (x, y) in head_pixels(kind, "0"):
        if kind == "sword" and (y > 9 or x < 7):
            continue
        if kind != "sword" and y > 10:
            continue
        px(img, x, y, MOLTEN[1])
    for (x, y) in head_pixels(kind, "7"):
        px(img, x, y, EMBER)
    return img


def quantum_tool(kind):
    def head(r, x, y):
        return irid(TOOL_T[r], hue_w(x, y, 0.0, 12.0))
    rod = dict(zip("abcd", (C(h) for h in ["7a5ad8", "efe8ff", "b18cff", "ffffff"])))
    img = tool(kind, head, lambda ch, x, y: rod[ch])
    for (x, y) in head_pixels(kind, "7"):
        px(img, x, y, WHITE)
    # One extra sparkle on the brightest body facet.
    pts = list(head_pixels(kind, "6"))
    if pts:
        x, y = pts[len(pts) // 2]
        px(img, x, y, WHITE)
    return img


# ---------------------------------------------------------------- misc items
def disc(img, cx, cy, r, ramp, rim):
    """Shaded orb: ramp[0] shadow .. ramp[-1] highlight, light from the top-left."""
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                lt = 1 - (d / r) * 0.55 - (dx + dy) / (2.6 * r)
                i = int(max(0, min(len(ramp) - 1, round(lt * (len(ramp) - 1)))))
                px(img, x, y, ramp[i])
    return img


def entangled_pair():
    img = new()
    a, b = (3.5, 12.5), (12.5, 3.5)
    # Sine link between the orbs: sample densely, keep it 8-connected, colour violet -> white -> cyan.
    last = None
    for i in range(161):
        t = i / 160.0
        x = a[0] + (b[0] - a[0]) * t
        y = a[1] + (b[1] - a[1]) * t
        off = 1.6 * math.sin(t * 2 * math.pi)
        x += off * 0.7071
        y += off * 0.7071
        p = (int(x), int(y))
        if p != last:
            c = mix(C("b18cff"), WHITE, 1 - abs(t - .5) * 4) if t < .5 else mix(WHITE, C("6fe3ff"), (t - .5) * 4)
            px(img, p[0], p[1], c)
            last = p
    vi = [C("2a1a52"), C("4a2f8f"), C("6a3fd0"), C("9c7cff"), C("d9c8ff")]
    cy = [C("0d3a48"), C("1a6f88"), C("2fb4d6"), C("6fe3ff"), C("d8fbff")]
    disc(img, a[0], a[1], 2.6, vi, None)
    disc(img, b[0], b[1], 2.6, cy, None)
    px(img, 2, 11, WHITE)
    px(img, 11, 2, WHITE)
    return outline(img, C("120a24"))


def quantum_photomask():
    cols = {"0": C("2a2440"), "5": C("c4bed6"), "3": C("8e86a8"), "1": C("5e5878"), "2": C("7a7494"),
            "6": C("1c1238")}
    img = stamp(MASK_MAP, cols)
    bond, node, core = C("5a3fb8"), C("6fe3ff"), WHITE
    pads = [4, 7, 10]
    for py in pads:
        for qx in pads:
            px(img, qx, py, node)
            px(img, qx + 1, py, C("3fb6e0"))
            px(img, qx, py + 1, C("3fb6e0"))
            px(img, qx + 1, py + 1, C("1f6aa8"))
    for p in pads[:2]:
        for q in pads:
            # horizontal and vertical bonds through the 1px gaps
            px(img, p + 2, q, bond)
            px(img, p + 2, q + 1, C("3a2380"))
            px(img, q, p + 2, bond)
            px(img, q + 1, p + 2, C("3a2380"))
    # The centre qubit is the entangled one: brighter, white core.
    px(img, 7, 7, core)
    px(img, 8, 7, node)
    px(img, 7, 8, node)
    # Field glints like the other masks.
    px(img, 4, 3, C("2e1f5a"))
    return img


def quantum_processor(frame):
    pulse = 0.5 - 0.5 * math.cos(2 * math.pi * frame / 8)    # 0 -> 1 -> 0
    cols = {"0": C("120826"), "1": C("2e1a6a"), "2": C("432a8f"), "6": C("e6c56a"),
            "8": C("7a62c8"), "3": C("1a1040"), "4": C("221450"), "5": C("221450"), "7": C("2c1c64")}
    img = stamp(CHIP_MAP, cols)
    # Pins: alternate gold shades so they read as metal, not dots.
    for y, row in enumerate(CHIP_MAP):
        for x, ch in enumerate(row):
            if ch == "6":
                px(img, x, y, C("f2d27a") if (x < 2 or y < 3) else C("c9a23e"))
    # Die interior x5..10, y5..10: dark well, glowing core at 7..8 with a pulsing halo.
    halo = mix(C("2c1c64"), C("6a3fd0"), 0.35 + 0.65 * pulse)
    ring = mix(C("6a3fd0"), C("b18cff"), pulse)
    for (x, y) in [(6, 6), (9, 6), (6, 9), (9, 9)]:
        px(img, x, y, halo)
    for (x, y) in [(7, 6), (8, 6), (6, 7), (6, 8), (9, 7), (9, 8), (7, 9), (8, 9)]:
        px(img, x, y, ring)
    core_hi = mix(C("6fe3ff"), WHITE, pulse)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px(img, x, y, mix(C("2c95d0"), C("6fe3ff"), pulse))
    px(img, 7, 7, core_hi)
    # Trace lines leading from core to die edge light up at peak.
    tr = mix(C("2c1c64"), C("5a3fb8"), pulse)
    for (x, y) in [(5, 7), (10, 8), (7, 5), (8, 10)]:
        px(img, x, y, tr)
    return img


def exotic_matter(frame):
    img = new()
    ph = 2 * math.pi * frame / 8
    cx, cy = 8.0, 8.0
    mag = [C("4a0a4a"), C("a01c8c"), C("ff4fd8"), C("ffc8f4")]
    cya = [C("0a3050"), C("1a7fa8"), C("4fe6ff"), C("d8fbff")]
    inside = {}
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            a = math.atan2(dy, dx)
            r = 5.6 + 0.6 * math.sin(3 * a + ph) + 0.4 * math.sin(2 * a - 2 * ph)
            d = math.hypot(dx, dy)
            if d <= r:
                inside[(x, y)] = a

    def ring_dist(x, y):
        for k in range(1, 4):
            if any((x + dx * k, y + dy * k) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                return k
        return 4
    for (x, y), a in inside.items():
        k = ring_dist(x, y)
        w = 0.5 + 0.5 * math.sin(a * 2 + ph)               # rim hue swirls magenta <-> cyan
        ramp = mag if w > 0.5 else cya
        lit = -(math.cos(a) + math.sin(a)) * 0.7071         # +1 at top-left
        if k == 1:
            c = ramp[3] if lit > 0.8 else ramp[2] if lit > -0.5 else ramp[1]
        elif k == 2:
            c = ramp[1] if lit > 0.2 else ramp[0]
        elif k == 3:
            c = C("12061e")
        else:
            c = C("030106")
        px(img, x, y, c)
    # Negative-energy specks drifting through the void core.
    for k, (bx, by) in enumerate([(6, 7), (9, 9), (8, 6)]):
        sx = bx + int(round(1.4 * math.sin(ph + k * 2.1)))
        sy = by + int(round(1.0 * math.cos(ph + k * 1.3)))
        if (sx, sy) in inside and ring_dist(sx, sy) >= 4:
            px(img, sx, sy, C("7a3ac0") if k == 0 else C("3a1a6a"))
    return outline(img, C("12041c"))


def singularity(frame):
    img = new()
    ph = 2 * math.pi * frame / 8
    cx, cy = 8.0, 8.0
    tilt = math.radians(-20)
    ca, sa = math.cos(tilt), math.sin(tilt)
    A, B = 7.6, 3.4
    fire = [C("7a2208"), C("d4561a"), C("ff8f2e"), C("ffc46e"), C("fff2d8")]
    HOLE = 3.0
    ring = {}
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            u = dx * ca + dy * sa
            v = -dx * sa + dy * ca
            e = math.hypot(u / A, v / B)
            if 0.58 <= e <= 1.0:
                a = math.atan2(v / B, u / A)
                dop = 0.5 - 0.5 * math.cos(a)          # approaching (left) side is brighter
                band = 0.5 + 0.5 * math.sin(3 * a - ph)
                inner = 1 - (e - 0.58) / 0.42
                lv = 0.0 + 0.32 * dop + 0.5 * band + 0.25 * inner
                ring[(x, y)] = (fire[int(max(0, min(4, lv * 4.6)))], v > 0)
    # Back layer: violet lensing halo, then the far half of the disc, then the hole, then the
    # lensed photon arc hugging the top of the hole, then the near half of the disc.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            if HOLE + 1 < d <= HOLE + 2.2:
                px(img, x, y, C("2e1a6a"))
            elif HOLE < d <= HOLE + 1:
                px(img, x, y, C("5a36c0"))
    for (x, y), (c, front) in ring.items():
        if not front:
            px(img, x, y, c)
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            d = math.hypot(dx, dy)
            if d <= HOLE:
                px(img, x, y, C("000000"))
            elif d <= HOLE + 1 and dy < -0.2:
                px(img, x, y, C("ffe2b0") if abs(dx) < 1.6 else C("ff9a3a"))
    for (x, y), (c, front) in ring.items():
        if front:
            px(img, x, y, c)
    return img


# ---------------------------------------------------------------- blocks
def adamantium_block():
    img = stamp(IRON_BLOCK_MAP, ramp_cols(ramp_at(["141a24", "283446", "41546e", "5a7090", "8aa2c4", "d8e6fa"], IRON_BLOCK_T)))
    # Mirror reflection: a hard diagonal streak pair across the plates.
    for y in range(16):
        for x in range(16):
            s = x + y
            if s in (8, 9) or s == 12 or s == 22:
                p = get(img, x, y)
                if IRON_BLOCK_MAP[y][x] not in "0123":
                    px(img, x, y, mix(p, C("f4f9ff"), 0.6 if s in (8, 9) else 0.4))
    return img


def faradaenium_block():
    img = stamp(GOLD_BLOCK_MAP, ramp_cols(ramp_at(["0b3b3a", "0f5a57", "15837c", "22b3a6", "3ff2e0", "e6fffb"], GOLD_BLOCK_T)))
    # A forked lightning crack: bright core, charred teal shadow down-right.
    bolt = [(3, 2), (4, 3), (4, 4), (5, 5), (6, 5), (7, 6), (7, 7), (6, 8), (7, 9), (8, 9), (9, 10),
            (10, 11), (10, 12), (11, 13), (8, 6), (9, 6), (10, 5), (11, 5), (12, 4)]
    for (x, y) in bolt:
        for dx, dy in ((1, 0), (0, 1), (1, 1)):
            if (x + dx, y + dy) not in bolt:
                q = get(img, x + dx, y + dy)
                px(img, x + dx, y + dy, mix(q, C("0b3b3a"), 0.5))
    for (x, y) in bolt:
        px(img, x, y, C("e6fffb"))
    for (x, y) in [(4, 3), (7, 6), (9, 10), (10, 5)]:
        px(img, x, y, WHITE)
    return img


def cortanium_block():
    img = stamp(NETHERITE_BLOCK_MAP, ramp_cols(ramp_at(["120305", "22070b", "360b11", "4a0f17", "66141f"], NETHERITE_BLOCK_T)))
    seam = [(2, 6), (3, 6), (4, 7), (5, 7), (6, 8), (7, 8), (8, 8), (9, 9), (10, 9), (11, 10), (12, 10), (13, 11),
            (7, 9), (7, 10), (6, 11), (6, 12)]
    for (x, y) in seam:
        px(img, x, y, MOLTEN[1])
    for (x, y) in [(4, 7), (7, 8), (8, 8), (10, 9), (7, 10)]:
        px(img, x, y, MOLTEN[3])
    px(img, 8, 8, MOLTEN[4])
    return img


def qubit_crystal_block():
    return stamp(AMETHYST_BLOCK_MAP, ramp_cols(["2a1a52", "3e2a7a", "5440a8", "6e58d0", "8a6ef0", "b49cff", "f0e8ff"]))


def quantum_alloy_block(frame):
    ph = frame / 8.0
    s = -4 + frame * 5

    def fn(ch, x, y):
        t = 0.02 + GOLD_BLOCK_T[int(ch, 36)] * 0.68
        d = x + y - s
        if abs(d) <= 1:
            t = min(1.0, t + (0.3 if d == 0 else 0.12))
        return irid(t, hue_w(x, y, ph, 24.0))
    return stamp_fn(GOLD_BLOCK_MAP, fn)


# ---------------------------------------------------------------- ore overlays
def deepslate_adamantium_ore_overlay():
    mask = ORE_MASKS["iron"][1]
    ramp = {"a": C("4a5870"), "b": C("7f93b0"), "c": C("b0c4de"), "d": C("dbe8f8"), "e": C("ffffff")}
    img = new()
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch in ramp:
                px(img, x, y, ramp[ch])
    out = C("141a24")
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch.isdigit() and any(solid(img, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px(img, x, y, out)
    return img


def prism(img, x, y, h, lean, ramp):
    """Three-wide crystal prism growing up from base-left (x, y), h rows tall, leaning `lean` px
    sideways every two rows. ramp: [shadow, mid, lit, bright, tip]. Lit face left, pointed tip."""
    for k in range(h):
        o = (k * lean) // 2
        top = k >= h - 2
        px(img, x + o, y - k, ramp[3] if top else ramp[2])
        px(img, x + o + 1, y - k, ramp[3] if k == h - 1 else ramp[1])
        px(img, x + o + 2, y - k, ramp[1] if top else ramp[0])
    o = (h * lean) // 2
    px(img, x + o + 1, y - h, ramp[4])


def faradaenium_ore_overlay():
    img = new()
    ramp = [C("0f5a57"), C("15837c"), C("2fd0c2"), C("7dfff0"), C("ffffff")]
    for (x, y, h, lean) in [(2, 7, 3, -1), (4, 7, 4, 0), (9, 13, 4, 1), (11, 13, 2, 1), (10, 4, 2, 0)]:
        prism(img, x, y, h, lean, ramp)
    # Live veins arcing between the crystal clusters.
    for (x, y) in [(6, 7), (7, 8), (8, 8), (8, 9), (8, 10), (9, 10), (12, 4), (12, 5), (13, 6), (13, 7),
                   (13, 8), (12, 9), (12, 10), (2, 8), (1, 9), (1, 10), (2, 11), (3, 12), (4, 12)]:
        if not solid(img, x, y):
            px(img, x, y, C("3ff2e0"))
    for (x, y) in [(8, 9), (13, 7), (1, 10)]:
        px(img, x, y, C("e6fffb"))
    return outline(img, C("0b2e2e"))


def cortanium_ore_overlay():
    img = new()
    ramp = [C("4a0a10"), C("8a1420"), C("d0283a"), C("ff6a4a"), C("ffd0b8")]
    for (x, y, h, lean) in [(2, 9, 4, 1), (9, 13, 5, -1), (10, 5, 3, 1)]:
        prism(img, x, y, h, lean, ramp)
    for (x, y) in [(3, 8), (9, 11), (11, 4)]:
        px(img, x, y, C("ffb0a8"))
    return outline(img, C("1a0508"))


def qubit_ore_overlay():
    img = new()
    ramp = [C("3a2478"), C("6248bb"), C("9c7cff"), C("d9c8ff"), C("ffffff")]
    for (x, y, h, lean) in [(1, 8, 3, -1), (3, 8, 5, 0), (5, 8, 3, 1),
                            (9, 14, 3, -1), (11, 14, 4, 1), (11, 4, 2, 0)]:
        prism(img, x, y, h, lean, ramp)
    return outline(img, C("140a2c"))


# ---------------------------------------------------------------- super-weapon inventory icons
# Drawn on the 45-degree lattice used by vanilla tools: a = x - y runs along the weapon
# (bottom-left grip -> top-right muzzle), c = x + y runs across it (low c = lit top-left edge).
GUN = [C("1b2129"), C("2a323c"), C("3c4652"), C("56626f"), C("7a8794"), C("a9b5c0")]
GRIP = [C("15181d"), C("22262c"), C("30353c")]


def lattice(paint):
    """paint(a, c) -> colour or None for every pixel; returns the image (no outline)."""
    img = new()
    for y in range(16):
        for x in range(16):
            col = paint(x - y, x + y)
            if col is not None:
                px(img, x, y, col)
    return img


def band(c, lo, hi, ramp):
    """Shade across a part: top-left edge brightest, bottom-right edge darkest."""
    if c == lo:
        return ramp[-1]
    if c == hi:
        return ramp[0]
    t = (c - lo) / max(1, hi - lo)
    return ramp[max(1, min(len(ramp) - 2, int(round((1 - t) * (len(ramp) - 2)))))]


def grip(a, a0):
    return GRIP[2] if a == a0 else GRIP[1] if a == a0 + 1 else GRIP[0]


def railgun():
    cyan = [C("1f6aa8"), C("6fe3ff"), C("e6fbff")]

    def paint(a, c):
        if 1 <= a <= 13 and 13 <= c <= 16:                          # twin rails, coils between
            if c == 13:
                return GUN[5]
            if c == 16:
                return GUN[2] if a < 13 else None
            if a == 13:
                return GUN[4]
            if a % 2 == 0:
                return GUN[0]
            return cyan[2] if c == 14 else cyan[1]
        if -3 <= a <= 0 and 9 <= c <= 10:                           # scope
            return cyan[1] if (a, c) == (0, 9) else GUN[4] if c == 9 else GUN[2]
        if a == -2 and c == 11:
            return GUN[1]
        if -5 <= a <= 0 and 12 <= c <= 17:                          # receiver
            if (a, c) == (-4, 14):
                return cyan[1]
            return band(c, 12, 17, GUN)
        if -4 <= a <= -3 and 18 <= c <= 21:
            return grip(a, -4)
        if -12 <= a <= -6 and 13 <= c <= 16:                        # stock
            if -10 <= a <= -8 and c in (14, 15):
                return GUN[0]
            return GRIP[1] if a == -12 else band(c, 13, 16, GUN[:5])
        return None
    return outline(lattice(paint), C("0b0e12"))


def plasma_rifle():
    green = [C("1f7a3a"), C("3fd46a"), C("7cff9a"), C("e8ffe8")]

    def paint(a, c):
        if 7 <= a <= 12 and 13 <= c <= 17:                          # vented muzzle shroud
            if a == 12:
                return GUN[1] if 14 <= c <= 16 else None
            if a in (8, 10) and 14 <= c <= 16:
                return C("ff8a3a") if c == 15 else C("0e1116")
            return band(c, 13, 17, GUN)
        if -7 <= a <= 6 and 11 <= c <= 19:                          # chunky rounded body
            if (a in (-7, 6) and c in (11, 19)) or (a in (-7, 6) and c in (12, 18)) and False:
                return None
            if (a, c) in ((-7, 11), (6, 11), (-7, 19), (6, 19)):
                return None
            if -3 <= a <= 3 and 13 <= c <= 17:                      # plasma tank window
                if a in (-3, 3) or c in (13, 17):
                    return GUN[1]
                if (a, c) == (-2, 14):
                    return green[3]
                return green[2] if c == 14 or a == -2 else green[1] if c == 15 else green[0]
            return band(c, 11, 19, GUN)
        if -5 <= a <= -4 and 20 <= c <= 23:
            return grip(a, -5)
        if -13 <= a <= -8 and 14 <= c <= 17:                        # stock
            return GRIP[1] if a == -13 else band(c, 14, 17, GUN[:5])
        return None
    return outline(lattice(paint), C("0b0e12"))


def singularity_launcher():
    body = [C("0e0818"), C("1a1030"), C("2a1a48"), C("3c2862"), C("54397e"), C("6f52a0")]
    fire = [C("b8501a"), C("ff8f2e"), C("ffb347"), C("fff0d0")]

    def paint(a, c):
        if 7 <= a <= 11 and 10 <= c <= 21:                          # muzzle ring around a black core
            if 9 <= a <= 10 and 13 <= c <= 18:
                return C("000000")
            if a == 11 and not (12 <= c <= 19):
                return None
            lit = c <= 13 or a == 7
            return fire[3] if (lit and c <= 12) else fire[2] if lit else fire[1] if c <= 18 else fire[0]
        if -10 <= a <= 6 and 12 <= c <= 19:                         # heavy tube
            if a in (-7, 2, 6):                                     # violet trim bands
                return C("b18cff") if c <= 14 else C("6a3fd0")
            return band(c, 12, 19, body)
        if -4 <= a <= -3 and 20 <= c <= 23:
            return grip(a, -4)
        if -13 <= a <= -11 and 13 <= c <= 18:                       # shoulder pad
            return band(c, 13, 18, body[:5])
        return None
    return outline(lattice(paint), C("07040c"))


def quantum_blade():
    def paint(a, c):
        if -2 <= a <= 14 and 13 <= c <= 17:                         # energy blade
            if a >= 13 and not (14 <= c <= 16):
                return None
            if a == 14 and c != 15:
                return None
            t = (a + 2) / 15.0
            if c == 15:
                return WHITE
            if c == 14:
                return mix(C("c8b0ff"), C("b0f4ff"), t)
            if c == 16:
                return mix(C("9468f0"), C("48c4f0"), t)
            return mix(C("6a3fd0"), C("2c95d0"), t) if c == 13 else mix(C("432c86"), C("1f6aa8"), t)
        if -4 <= a <= -3 and 10 <= c <= 20:                         # violet guard
            if c in (10, 20):
                return C("e6dcff") if c == 10 else C("4a2f8f")
            return C("b18cff") if a == -4 else C("6a3fd0")
        if -11 <= a <= -5 and 14 <= c <= 16:                        # wrapped hilt
            if a % 2 == 0:
                return C("4a2f8f") if c == 14 else C("2a1a52")
            return GRIP[2] if c == 14 else GRIP[0]
        if -13 <= a <= -12 and 14 <= c <= 16:                       # pommel gem
            return C("6fe3ff") if c == 14 else C("2c95d0")
        return None
    img = outline(lattice(paint), C("120a24"))
    for (x, y) in [(15, 3), (9, 2), (3, 6)]:                         # free sparkles
        if not solid(img, x, y) or get(img, x, y)[:3] == (18, 10, 36):
            px(img, x, y, WHITE)
    return img


def quantum_disassembler():
    arc = {6: 14, 7: 16, 8: 13, 9: 16, 10: 14}

    def paint(a, c):
        if 4 <= a <= 10 and c in (11, 12, 18, 19):                  # two prongs
            if a == 10:
                return C("b18cff") if c in (11, 18) else C("6a3fd0")
            return GUN[5] if c in (11, 18) else GUN[2] if c == 12 else GUN[1]
        if 5 <= a <= 10 and 13 <= c <= 17:                          # violet arc between them
            k = arc.get(a)
            if k is not None and abs(c - k) <= 1:
                return WHITE if c == k else C("c8b0ff")
            return C("4a2f8f") if a in (5, 6) else None
        if 2 <= a <= 3 and 10 <= c <= 20:                           # emitter head
            if a == 3 and 13 <= c <= 17:
                return C("6a3fd0")
            return band(c, 10, 20, GUN)
        if -10 <= a <= 1 and 13 <= c <= 17:                         # body
            if (a, c) == (-3, 14):
                return C("e6fbff")
            if (a, c) in ((-2, 14), (-3, 15)):
                return C("6fe3ff")
            return band(c, 13, 17, GUN)
        if -8 <= a <= -7 and 18 <= c <= 21:
            return grip(a, -8)
        return None
    return outline(lattice(paint), C("0b0e12"))


WEAPONS = {"railgun": railgun, "plasma_rifle": plasma_rifle, "singularity_launcher": singularity_launcher,
           "quantum_blade": quantum_blade, "quantum_disassembler": quantum_disassembler}


# ---------------------------------------------------------------- paxels
# Drawn as one multitool rather than three heads stacked, in the style paxel mods use: a broad
# axe-and-spade blade rising to a point above the collar and a pick arm sweeping down the right.
# Shading follows the vanilla tools (light from the upper left, no black outline). Head roles index
# the 8-shade ramps (0 darkest, 7 glint); handle roles a-d run dark to light.
PAXEL_MASK = [
    ".........##.....",
    "........###.....",
    ".......####.....",
    "......######....",
    ".....########...",
    "....##########..",
    "....###..kk###..",
    ".........hk####.",
    "........hhh.###.",
    ".......hhh...##.",
    "......hhh....##.",
    ".....hhh......#.",
    "....hhh.......#.",
    "...hhh..........",
    "..hhh...........",
    "..hh............",
]


def paxel_roles():
    g = {(x, y): ch for y, row in enumerate(PAXEL_MASK) for x, ch in enumerate(row) if ch != "."}
    metal = {k for k, ch in g.items() if ch in "#k"}
    head, handle = {}, {}
    for (x, y), ch in g.items():
        if ch == "h":
            # Vanilla haft: lit left edge, shadowed right edge, a core with a highlight every third row.
            left, right = g.get((x - 1, y)) != "h", g.get((x + 1, y)) != "h"
            handle[(x, y)] = "b" if left else "a" if right else ("d" if y % 3 == 0 else "c")
            continue
        if ch == "k":
            head[(x, y)] = 2                                        # collar band
            continue
        up, lf = (x, y - 1) not in metal, (x - 1, y) not in metal
        dn, rt = (x, y + 1) not in metal, (x + 1, y) not in metal
        if dn and rt:
            r = 0                                                   # outer shadow corner
        elif dn or rt:
            r = 1                                                   # shadow rim
        else:
            # One smooth fall-off from the upper left; lit rims sit a step above the body.
            r = 6 if x + y <= 9 else 5 if x + y <= 14 else 4
            if (up or lf) and r < 6:
                r += 1
        if (up or lf) and not (dn or rt) and x + y <= 9:
            r = 7                                                   # honed blade edge
        head[(x, y)] = r
    return head, handle


def paxel(head_cols, handle_cols, flair=None):
    head, handle = paxel_roles()
    img = new()
    for (x, y), r in head.items():
        px(img, x, y, head_cols[r])
    for (x, y), ch in handle.items():
        px(img, x, y, handle_cols[ch](x, y) if callable(handle_cols[ch]) else handle_cols[ch])
    if flair:
        flair(img, head)
    return img


def _hx(hexes):
    return [C(h) for h in hexes]


def paxel_set():
    diamond = _hx(["082520", "0e3f36", "156355", "1e8a77", "27b29a", "2bc7ac", "33ebcb", "a4fdf0"])
    wood = dict(zip("abcd", _hx(["281e0b", "493615", "684e1e", "896727"])))
    neth = _hx(["1a1416", "2a2022", "3b393b", "434043", "5d565d", "706770", "867b86", "b0a6b0"])
    neth_h = dict(zip("abcd", _hx(["231012", "322727", "4f3c3e", "603432"])))
    adam = _hx(ramp_at(["141a24", "2a3648", "485c78", "6e88ac", "a2bad8", "f4f9ff"], TOOL_T))
    adam_h = dict(zip("abcd", _hx(["15181c", "2b3036", "424850", "5c636d"])))
    fara = _hx(ramp_at(["0b3b3a", "0f5a57", "15837c", "1fa89c", "2fd0c2", "8ff8ec"], TOOL_T))
    fara_h = dict(zip("abcd", _hx(["6b3400", "b86a00", "f0a800", "ffe46a"])))
    cort = _hx(ramp_at(CORT[:5] + ["c8303a"], TOOL_T))
    dark = dict(zip("abcd", _hx(["0c090a", "1c1517", "2c2326", "3e3236"])))
    wrap = dict(zip("abcd", _hx(["3a060c", "6e0f18", "a81c26", "e0303c"])))
    cort_h = {ch: (lambda x, y, ch=ch: wrap[ch] if y % 4 == 1 else dark[ch]) for ch in "abcd"}
    rod = dict(zip("abcd", _hx(["7a5ad8", "efe8ff", "b18cff", "ffffff"])))

    def cort_fx(img, head):
        # A molten seam along the pick arm's underside; the blade stays clean black-red.
        for (x, y), r in head.items():
            if r <= 1 and x >= 12:
                px(img, x, y, MOLTEN[1] if r == 1 else MOLTEN[0])
            elif r == 7:
                px(img, x, y, EMBER)

    def quantum_fx(img, head):
        # Violet blade shading into cyan along the pick arm: one sweep, no speckle.
        for (x, y), r in head.items():
            w = min(1.0, max(0.0, (x + y - 12) / 9.0))
            px(img, x, y, WHITE if r == 7 else irid(TOOL_T[r], w))

    return {
        "diamond": paxel(diamond, wood),
        "netherite": paxel(neth, neth_h),
        "adamantium": paxel(adam, adam_h),
        "faradaenium": paxel(fara, fara_h),
        "cortanium": paxel(cort, cort_h, cort_fx),
        "quantum": paxel(diamond, rod, quantum_fx),
    }


# ---------------------------------------------------------------- starforge multiblock
STEEL = _hx(["1a212c", "2e3a4a", "4a5c74", "7088a8", "9cb2cc", "c8d8ec", "eef6ff"])
HEAT = _hx(["5a1608", "b8400e", "ff7a1a", "ffb347", "ffe0a0", "fff8e8"])
CERAMIC = _hx(["17120f", "241c18", "322722", "40332c", "4e4038"])


def starforge_frame():
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge <= 2:                                            # girder flange, brushed
                v = 4 + (1 if (y * 7 + x * 3) % 11 == 0 else 0)
                if edge == 0:
                    v = 5 if (x == 0 or y == 0) else 1
                elif edge == 2:
                    v = 3 if (x >= 13 or y >= 13) else 5
                px(img, x, y, STEEL[v])
            else:                                                    # recessed dark web
                px(img, x, y, STEEL[1] if (x + y) % 5 else STEEL[0])
    # Diagonal brace across the web.
    for i in range(3, 13):
        px(img, i, i, STEEL[4])
        px(img, i + 1, i, STEEL[2])
        px(img, i, i + 1, STEEL[5] if i < 12 else STEEL[2])
    # Heat seam along the inner lower edge of the flange.
    for x in range(3, 13):
        px(img, x, 12, HEAT[1] if x % 3 else HEAT[2])
    # Rivets.
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (1, 7), (14, 8), (8, 14)]:
        px(img, x, y, STEEL[0])
        px(img, x - 1 if x > 7 else x + 1, y, STEEL[6] if y < 8 else STEEL[5])
    return img


def starforge_pylon():
    img = new()
    body = _hx(["0e0204", "1c0508", "2e0a0f", "441016", "5e1820"])
    cyan = [C("0d3a48"), C("1a6f88"), C("2fb4d6"), C("6fe3ff"), C("e6fbff")]
    for y in range(16):
        for x in range(16):
            if x in (0, 15):
                c = body[0]
            elif x in (1, 14):
                c = body[1]
            elif x in (2, 3):
                c = body[3] if x == 3 else body[2]
            elif x >= 12:
                c = body[1]
            else:
                c = body[4] if x == 4 else body[3] if x < 8 else body[2]
            px(img, x, y, c)
    for y0 in (1, 6, 11):                                            # coil rings
        for x in range(1, 15):
            t = x / 14.0
            px(img, x, y0, cyan[4] if 4 <= x <= 6 else cyan[3] if t < .7 else cyan[2])
            px(img, x, y0 + 1, cyan[2] if t < .7 else cyan[1])
            px(img, x, y0 + 2, cyan[0])
        px(img, 0, y0, cyan[1])
        px(img, 15, y0, cyan[0])
    return img


def starforge_crucible():
    img = new()
    for y in range(16):
        for x in range(16):
            brick = (y // 4) % 2
            joint = y % 4 == 3 or (x + (4 if brick else 0)) % 8 == 7
            v = 0 if joint else 3 if (y % 4 == 0 or (x + (4 if brick else 0)) % 8 == 0) else 2
            if not joint and (x * 5 + y * 3) % 13 == 0:
                v = 1
            px(img, x, y, CERAMIC[v])
    for x in range(16):                                              # hot rim band
        px(img, x, 0, HEAT[4])
        px(img, x, 1, HEAT[3])
        px(img, x, 2, HEAT[2])
        px(img, x, 3, HEAT[1] if x % 4 else HEAT[0])
    for (x, h) in [(3, 2), (10, 3), (13, 1)]:                        # glow bleeding into joints
        for k in range(h):
            px(img, x, 4 + k, HEAT[0])
    for x in range(16):
        px(img, x, 15, CERAMIC[0])
    return img


def starforge_crucible_top(frame):
    img = new()
    ph = 2 * math.pi * frame / 8
    melt = [HEAT[0], HEAT[1], HEAT[2], HEAT[3], HEAT[4]]
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                px(img, x, y, CERAMIC[1] if (x + y) % 2 else CERAMIC[2])
                continue
            if x in (1, 14) or y in (1, 14):
                px(img, x, y, HEAT[0] if (x in (1, 14) and y in (1, 14)) else C("7a1e0a"))
                continue
            dx, dy = x + .5 - 8, y + .5 - 8
            r = math.hypot(dx, dy) / 6.5
            a = math.atan2(dy, dx)
            v = (1 - r) + 0.16 * math.sin(2 * a + 5 * r - ph)
            px(img, x, y, melt[int(max(0, min(4, v * 5.2)))])
    for k in range(3):                                               # white-hot specks orbiting
        ang = ph + k * 2.1
        rr = 1.6 + k * 1.1
        px(img, int(8 + rr * math.cos(ang)), int(8 + rr * math.sin(ang)), HEAT[5])
    return img


def starforge_front(glow=0.0, frame=0):
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = STEEL[4] if (x == 0 or y == 0) else STEEL[0]
            elif edge == 1:
                c = STEEL[3] if (x == 1 or y == 1) else STEEL[1]
            else:
                c = STEEL[2] if (y % 4) else STEEL[1]                 # ribbed door plate
            px(img, x, y, c)
    # Status display (top), cyan on dark.
    for x in range(4, 12):
        px(img, x, 3, C("06090c"))
        px(img, x, 4, C("06090c"))
    lit = [C("1a6f88"), C("6fe3ff")]
    for i, x in enumerate(range(5, 11)):
        on = glow > 0 and (i <= 2 + frame % 3)
        px(img, x, 3, lit[1] if on and i < 3 else lit[0] if glow > 0 else C("12232c"))
        px(img, x, 4, lit[0] if glow > 0 and i % 2 == 0 else C("0e171e"))
    # Porthole: steel bezel, glowing window.
    cx, cy = 8.0, 10.0
    hot = [C("3a0e06"), C("7a2008"), C("c44a10"), C("ff8a2a")] if glow == 0 else \
          [HEAT[2], HEAT[3], HEAT[4], HEAT[5]]
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            if d <= 2.6:
                k = 3 if d < 0.9 else 2 if d < 1.8 else 1
                if glow and frame % 2 and k == 1:
                    k = 2
                px(img, x, y, hot[k] if not (glow and d < 1.8 and frame in (2, 5)) else HEAT[5])
            elif d <= 3.6:
                lt = (x + .5 - cx) + (y + .5 - cy) < 0
                px(img, x, y, STEEL[5] if lt else STEEL[0])
    for (x, y) in [(3, 7), (12, 7), (3, 13), (12, 13)]:              # bezel bolts
        px(img, x, y, STEEL[0])
        px(img, x, y - 1, STEEL[5])
    return img


STARFORGE_FILES = ["block/starforge_frame", "block/starforge_pylon", "block/starforge_crucible",
                   "block/starforge_crucible_top", "block/starforge_front", "block/starforge_front_on"]
PAXEL_FILES = [f"item/{m}_paxel" for m in ("diamond", "netherite", "adamantium", "faradaenium",
                                            "cortanium", "quantum")]


# ---------------------------------------------------------------- entry point
def generate_textures():
    B, I = "block/", "item/"
    save(deepslate_adamantium_ore_overlay(), B + "deepslate_adamantium_ore_overlay.png")
    save(faradaenium_ore_overlay(), B + "faradaenium_ore_overlay.png")
    save(cortanium_ore_overlay(), B + "cortanium_ore_overlay.png")
    save(qubit_ore_overlay(), B + "qubit_ore_overlay.png")

    save(adamantium_block(), B + "adamantium_block.png")
    save(faradaenium_block(), B + "faradaenium_block.png")
    save(cortanium_block(), B + "cortanium_block.png")
    save(qubit_crystal_block(), B + "qubit_crystal_block.png")
    save_anim([quantum_alloy_block(i) for i in range(8)], B + "quantum_alloy_block.png", 4)

    save(raw_adamantium(), I + "raw_adamantium.png")
    save(raw_faradaenium(), I + "raw_faradaenium.png")
    save(adamantium_ingot(), I + "adamantium_ingot.png")
    save(faradaenium_ingot(), I + "faradaenium_ingot.png")
    save(cortanium_ingot(), I + "cortanium_ingot.png")
    save_anim([quantum_ingot(i) for i in range(8)], I + "quantum_alloy_ingot.png", 3)
    save(cortanium_shard(), I + "cortanium_shard.png")
    save_anim([qubit_crystal(i) for i in range(8)], I + "qubit_crystal.png", 4)
    save(quantum_dust(), I + "quantum_dust.png")
    save(cosmic_dust(), I + "cosmic_dust.png")
    save(entangled_pair(), I + "entangled_pair.png")
    save(quantum_photomask(), I + "quantum_photomask.png")
    save_anim([quantum_processor(i) for i in range(8)], I + "quantum_processor.png", 3)
    save_anim([exotic_matter(i) for i in range(8)], I + "exotic_matter.png", 3)
    save_anim([singularity(i) for i in range(8)], I + "singularity.png", 2)

    makers = {"adamantium": adamantium_tool, "faradaenium": faradaenium_tool,
              "cortanium": cortanium_tool, "quantum": quantum_tool}
    for mat, fn in makers.items():
        for kind in TOOLS:
            save(fn(kind), I + f"{mat}_{kind}.png")
    for name, fn in WEAPONS.items():
        save(fn(), I + f"{name}.png")
    for mat, img in paxel_set().items():
        save(img, I + f"{mat}_paxel.png")
    save(starforge_frame(), B + "starforge_frame.png")
    save(starforge_pylon(), B + "starforge_pylon.png")
    save(starforge_crucible(), B + "starforge_crucible.png")
    save_anim([starforge_crucible_top(i) for i in range(8)], B + "starforge_crucible_top.png", 4)
    save(starforge_front(), B + "starforge_front.png")
    save_anim([starforge_front(1.0, i) for i in range(6)], B + "starforge_front_on.png", 3)
    for mat in ARMOR_SETS:
        for kind in ARMOR_KINDS:
            save(armor_icon(mat, kind), I + f"{mat}_{kind}.png")
        hum, leg = armor_layers(mat)
        save(hum, f"entity/equipment/humanoid/{mat}.png")
        save(leg, f"entity/equipment/humanoid_leggings/{mat}.png")
        save(armor_baby_layer(mat), f"entity/equipment/humanoid_baby/{mat}.png")
    save(turret_entity(), "entity/turret.png")
    save(robot_arm_entity(), "entity/robot_arm.png")
    save(hover_bike_entity(), "entity/hover_bike.png")
    save(mech_entity(), "entity/mech.png")
    save(mech_icon(), I + "mech_suit.png")
    save(nanotube_tether(), I + "nanotube_tether.png")
    save(hover_bike_icon(), I + "hover_bike.png")
    save(meteor_rock(), "entity/meteor.png")
    save(meteor_glow(), "entity/meteor_glow.png")
    save(meteorite_block(), B + "meteorite.png")
    save(fusion_casing(), B + "fusion_casing.png")
    save(fusion_magnet(), B + "fusion_magnet.png")
    save(fusion_glass(), B + "fusion_glass.png")
    save(fusion_front(), B + "fusion_front.png")
    save_anim([fusion_front(1.0, i) for i in range(8)], B + "fusion_front_on.png", 2)


# ---------------------------------------------------------------- exotic armor
# Icons use role grids taken from the vanilla diamond armor sprites (0 shadow rim .. 6 glint).
# Worn layers are painted box by box on the 64x32 humanoid layout: bevelled plates (lit top/left,
# shadowed bottom/right), panel seams, and accent lights that multiply with tier: a visor slit for
# all, then chest core, then limb conduits, then an iridescent finish on quantum alloy.
ARMOR_ICONS = {
    "helmet": [
        "................", "................", "................", ".....111111.....",
        "....13444420....", "...1356544320...", "...1455443330...", "...1441110230...",
        "...1410000030...", "...1410000020...", "...1300000020...", "....10....00....",
        "................", "................", "................", "................"],
    "chestplate": [
        "................", "................", ".11111....11111.", ".16541....16541.",
        ".154431..135441.", ".14445311344441.", ".12346555444321.", ".00255544444200.",
        "...0554444440...", "...0454444430...", "...0444444430...", "...0344444330...",
        "...0233333320...", "....02333320....", ".....000000.....", "................"],
    "leggings": [
        "................", "................", "....11111110....", "...1566554420...",
        "...1554444430...", "...1544334430...", "...1543002430...", "...1440..0430...",
        "...1430..1430...", "...1430..1430...", "...1330..0330...", "...1320..0320...",
        "...0220..0220...", "...0000..0000...", "................", "................"],
    "boots": [
        "................", "................", "................", "....111..111....",
        "...1650..1650...", "...1550..1540...", "...1540..1440...", "...1440..1440...",
        "...1430..1440...", "..14430..13430..", ".144320..123430.", ".133200..003320.",
        ".1000......0000.", "................", "................", "................"],
}
ARMOR_T = [0.0, 0.14, 0.42, 0.55, 0.7, 0.86, 1.0]
ARMOR_SETS = {
    # ramp (dark -> light), accent light, trim
    "adamantium": (ADAM, "bfe4ff", "93abcc"),
    "faradaenium": (FARA, "8ff8ec", "f0a800"),
    "cortanium": (CORT[:5] + ["c8303a"], "ff4a3a", "ff8a5a"),
    "quantum": (["140b30", "2e1a6a", "5232b0", "9468f0", "b18cff", "e2d6ff"], "6fe3ff", "ffffff"),
}
ARMOR_TIER = {"adamantium": 0, "faradaenium": 1, "cortanium": 2, "quantum": 3}
# Accent pixels on each icon (lights that read even at 16 px).
ARMOR_ICON_ACCENT = {
    "helmet": [(5, 7), (6, 7), (7, 7), (8, 7)],
    "chestplate": [(7, 8), (8, 8), (7, 9), (8, 9)],
    "leggings": [(5, 4), (10, 4)],
    "boots": [(4, 6), (11, 6)],
}


def armor_icon(mat, kind):
    ramp, accent, trim = ARMOR_SETS[mat]
    cols = [C(h) for h in ramp_at(ramp, ARMOR_T)]
    tier = ARMOR_TIER[mat]

    def fn(ch, x, y):
        r = int(ch)
        if mat == "quantum":
            w = min(1.0, max(0.0, (x + y - 8) / 14.0))
            return C("ffffff") if r == 6 else irid(ARMOR_T[r], w)
        return cols[r]
    img = stamp_fn(ARMOR_ICONS[kind], fn)
    for (x, y) in ARMOR_ICON_ACCENT[kind]:
        if solid(img, x, y):
            px(img, x, y, C(accent))
    if tier >= 1 and kind == "helmet":
        px(img, 6, 4, C(trim))
        px(img, 9, 4, C(trim))
    return img


def _box_faces(u, v, w, h, d):
    """Vanilla cube UV layout -> {face: (x0, y0, fw, fh)}."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def _plate(img, box, cols, rows=None, seams=(), spine=False):
    """Bevelled metal plate over a face rectangle. rows limits painting to a row range of the face.
    Seams are engraved: a dark groove with a lit lip beneath it."""
    x0, y0, fw, fh = box
    r0, r1 = rows if rows else (0, fh)
    for y in range(r0, r1):
        for x in range(fw):
            last_x, last_y = x == fw - 1, y == r1 - 1
            if (x == 0 and last_y) or (last_x and y == r0):
                c = cols[3]                                    # neutral corners where light meets shade
            elif x == 0 or y == r0:
                c = cols[5]
            elif last_x and last_y:
                c = cols[1]
            elif last_x or last_y:
                c = cols[2]
            elif y in seams:
                c = cols[2]
            elif y - 1 in seams:
                c = cols[5]
            else:
                c = cols[4] if (y - r0) < (r1 - r0) * 0.5 else cols[3]
                if spine and x in (fw // 2 - 1, fw // 2):
                    c = cols[3] if c == cols[4] else cols[2]
            px(img, x0 + x, y0 + y, c)


def armor_layers(mat):
    ramp, accent, trim = ARMOR_SETS[mat]
    cols = [C(h) for h in ramp_at(ramp, ARMOR_T)]
    acc, trm = C(accent), C(trim)
    tier = ARMOR_TIER[mat]
    hum, leg = new(64, 32), new(64, 32)

    # Helmet: full helm on the head box.
    head = _box_faces(0, 0, 8, 8, 8)
    for f in ("top", "right", "front", "left", "back"):
        _plate(hum, head[f], cols, seams=(5,) if f != "top" else (), spine=f in ("top", "back"))
    fx, fy, _, _ = head["front"]
    for x in range(1, 7):
        px(hum, fx + x, fy + 3, acc)                          # visor slit
    px(hum, fx + 1, fy + 4, cols[1]); px(hum, fx + 6, fy + 4, cols[1])
    if tier >= 1:
        tx, ty, _, _ = head["top"]
        for y in range(8):
            px(hum, tx + 3, ty + y, trm); px(hum, tx + 4, ty + y, cols[5])   # crest
    if tier >= 2:
        for f in ("right", "left"):
            sx, sy, _, _ = head[f]
            for y in range(2, 5):
                px(hum, sx + 3, sy + y, acc)                  # side vents

    # Chestplate: torso and pauldrons.
    body = _box_faces(16, 16, 8, 12, 4)
    for f in ("top", "right", "front", "left", "back", "bottom"):
        _plate(hum, body[f], cols, seams=(4, 8) if f in ("front", "back") else (6,), spine=f == "back")
    bx, by, _, _ = body["front"]
    core = [(3, 3), (4, 3), (3, 4), (4, 4)]
    for (x, y) in core:
        px(hum, bx + x, by + y, acc if tier >= 1 else trm)    # chest core
    if tier >= 2:
        for y in range(5, 11):
            px(hum, bx + 1, by + y, acc); px(hum, bx + 6, by + y, acc)   # conduits
    kx, ky, _, _ = body["back"]
    for y in range(2, 7):                                     # backpack reactor plate
        for x in range(2, 6):
            edge = x in (2, 5) or y in (2, 6)
            px(hum, kx + x, ky + y, cols[1] if edge else (acc if tier >= 1 and y == 4 and x in (3, 4) else cols[3]))
    arm = _box_faces(40, 16, 4, 12, 4)
    for f in ("top", "right", "front", "left", "back"):
        _plate(hum, arm[f], cols, rows=(0, 5) if f != "top" else None)
    if tier >= 3:
        for f in ("front", "right"):
            ax, ay, _, _ = arm[f]
            for x in range(4):
                px(hum, ax + x, ay + 3, acc)

    # Boots: lower legs of the humanoid layer.
    legb = _box_faces(0, 16, 4, 12, 4)
    for f in ("right", "front", "left", "back"):
        _plate(hum, legb[f], cols, rows=(7, 12))
    _plate(hum, legb["bottom"], cols)
    lx, ly, _, _ = legb["front"]
    px(hum, lx + 1, ly + 8, acc); px(hum, lx + 2, ly + 8, acc)

    # Leggings layer: belt on the body box, thigh/knee plates on the legs.
    for f in ("right", "front", "left", "back"):
        _plate(leg, body[f], cols, rows=(8, 12))
    bx, by, _, _ = body["front"]
    px(leg, bx + 3, by + 9, trm); px(leg, bx + 4, by + 9, trm)   # buckle
    legl = _box_faces(0, 16, 4, 12, 4)
    for f in ("right", "front", "left", "back"):
        _plate(leg, legl[f], cols, rows=(0, 9), seams=(5,))
    lx, ly, _, _ = legl["front"]
    for x in (1, 2):
        px(leg, lx + x, ly + 5, acc if tier >= 1 else trm)   # knee lights
    if tier >= 2:
        for f in ("right", "left"):
            sx, sy, _, _ = legl[f]
            for y in range(1, 5):
                px(leg, sx + 2, sy + y, acc)

    if mat == "quantum":
        # Violet alloy with a cyan sheen that sweeps diagonally across each face; accents stay white-hot.
        for img in (hum, leg):
            for y in range(32):
                for x in range(64):
                    c = img.getpixel((x, y))
                    if not c[3] or c[:3] in (acc[:3], trm[:3]):
                        continue
                    lum = (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255
                    sheen = 0.5 if (x + y) % 11 == 0 else 0.12        # a faint cyan glint band
                    img.putpixel((x, y), irid(min(1.0, lum * 1.15), sheen))
    return hum, leg


# Opaque UV regions of the 26.2 baby humanoid armor layout (64x64; layout only, rows 0-40).
BABY_ARMOR_MASK = [
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "........#########.......................",
    "##################################......",
    "##################################......",
    "##################################......",
    "#########..###..##################......",
    "#########..###..##################......",
    "#########.......##################......",
    "######.............###############......",
    "...........................#####........",
    "........................................",
    ".................................##.....",
    ".................................##.....",
    ".................................##.....",
    "#####..#################################",
    "########################################",
    "########################################",
    "##################......................",
    "##################......................",
    "...######........................##.....",
    "...######........................##.....",
    "...######.........############...##.....",
    "############......######################",
    "...######.........######################",
    "...######.....................##########",
    "...######...............................",
    "############............................",
    "........................................",
    "........................................",
    "........................................",
    "........................................",
    "........................................",
    "........................................",
    "##################......................",
    "##################......................",
]


def armor_baby_layer(mat):
    """Baby models use their own UV layout: the same bevelled plates, painted over its mask."""
    ramp, accent, trim = ARMOR_SETS[mat]
    cols = [C(h) for h in ramp_at(ramp, ARMOR_T)]
    img = new(64, 64)
    on = {(x, y) for y, row in enumerate(BABY_ARMOR_MASK) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in on:
        up, lf = (x, y - 1) not in on, (x - 1, y) not in on
        dn, rt = (x, y + 1) not in on, (x + 1, y) not in on
        r = 5 if (up or lf) and not (dn or rt) else 1 if (dn or rt) else 4 if y % 8 < 4 else 3
        if mat == "quantum":
            lum = ARMOR_T[r]
            px(img, x, y, irid(lum, (y % 16) / 15.0))
        else:
            px(img, x, y, cols[r])
    return img


# ---------------------------------------------------------------- turret head (entity atlas, 64x64)
def turret_entity():
    """Gunmetal plates with panel seams for the yoke and housing, polished steel for barrels and
    rails, and warm-grey coil bands. Every cube's UV lands in a region painted as that material."""
    img = new(64, 64)
    gun = [C(h) for h in ramp_at(["10141a", "1f2730", "34404c", "4f5e6c", "75879a", "b4c4d4"], ARMOR_T)]
    steel = [C(h) for h in ramp_at(ADAM, ARMOR_T)]
    coil = [C(h) for h in ramp_at(["2a2420", "4a3e34", "6e5c4a", "9a8268", "c8ad8a", "f0dcc0"], ARMOR_T)]

    def region(x0, y0, w, h, ramp, seam=6):
        for y in range(h):
            for x in range(w):
                c = ramp[4] if y % seam == 0 else ramp[3] if (x // 4 + y // seam) % 2 else ramp[2]
                if x % 8 == 7:
                    c = ramp[1]
                if y % seam == 1:
                    c = ramp[5] if x % 8 != 7 else ramp[2]
                px(img, x0 + x, y0 + y, c)
    region(0, 0, 32, 24, gun)          # turntable and yoke arms
    region(32, 0, 32, 12, steel, 4)    # barrels
    region(32, 12, 32, 12, coil, 3)    # muzzle collars / coils
    region(0, 24, 34, 16, gun, 5)      # housing / breech
    region(34, 24, 30, 16, gun, 3)     # heat sink / capacitor bank (fine fins)
    region(0, 40, 64, 24, steel, 4)    # rails
    return img


# ---------------------------------------------------------------- fusion reactor blocks
FUSION_CYAN = [C("0b2a36"), C("135a70"), C("1f93b0"), C("48c4f0"), C("aef2ff")]
COPPER = [C("3a1a0e"), C("6a3018"), C("9a4a26"), C("c86a36"), C("e89058"), C("ffc49a")]


def fusion_casing():
    """Heavy shielding plate: bevelled steel with a recessed cyan containment seam and rivets."""
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = STEEL[5] if (x == 0 or y == 0) else STEEL[1]
            elif edge == 1:
                c = STEEL[4] if (x == 1 or y == 1) else STEEL[2]
            else:
                c = STEEL[3] if (x // 4 + y // 4) % 2 == 0 else STEEL[2]
            px(img, x, y, c)
    for i in range(3, 13):
        px(img, i, 7, STEEL[1])
        px(img, i, 8, FUSION_CYAN[2] if i % 3 else FUSION_CYAN[3])
        px(img, i, 9, STEEL[4])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(img, x, y, STEEL[0])
        px(img, x + 1, y, STEEL[6])
    return img


def fusion_magnet():
    """Toroidal field coil: copper windings between steel clamps, a cyan field lamp in the middle."""
    img = new()
    for y in range(16):
        for x in range(16):
            if y <= 1 or y >= 14:
                c = STEEL[5] if y == 0 else STEEL[1] if y == 15 else STEEL[3]
            elif x <= 1 or x >= 14:
                c = STEEL[4] if x == 0 else STEEL[1] if x == 15 else STEEL[2]
            else:
                band = y % 3
                c = COPPER[4] if band == 0 else COPPER[3] if band == 1 else COPPER[1]
                if (x + y) % 7 == 0:
                    c = COPPER[5]
            px(img, x, y, c)
    for x in range(5, 11):
        px(img, x, 7, STEEL[1])
        px(img, x, 8, FUSION_CYAN[3] if 6 <= x <= 9 else FUSION_CYAN[2])
    return img


def fusion_glass():
    """Viewport: a slim reinforced frame around a clear pane with one glint. Cutout, like vanilla glass,
    so the plasma behind it is never hidden by translucent depth writes."""
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                px(img, x, y, STEEL[4] if (x == 0 or y == 0) else STEEL[1])
            elif edge == 1 and (x == 1 or y == 1):
                px(img, x, y, C("aef2ff"))
            elif x + y in (9, 10) and 3 <= x <= 6:
                px(img, x, y, C("e6fcff"))                      # glint; the pane itself is fully clear (cutout)
    return img


def fusion_front(glow=0.0, frame=0):
    """Controller face: plasma-temperature bar graph over a cross-section porthole."""
    img = fusion_casing()
    for y in range(2, 6):
        for x in range(3, 13):
            px(img, x, y, C("06090c"))
    for i, x in enumerate(range(4, 12)):
        h = 0 if glow == 0 else 1 + (i + frame) % 3
        for k in range(h):
            col = FUSION_CYAN[4] if i > 5 else FUSION_CYAN[3] if i > 2 else FUSION_CYAN[2]
            px(img, x, 4 - k, col)
    cx, cy = 8.0, 11.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - cx, (y + .5 - cy) * 1.4)
            if 1.0 < d <= 3.4:
                if glow == 0:
                    c = C("1a1020") if d < 2.6 else STEEL[1]
                else:
                    k = (math.atan2(y + .5 - cy, x + .5 - cx) / (2 * math.pi) * 8 + frame) % 8
                    c = C("ffffff") if k < 1 else C("e2d6ff") if k < 3 else C("b18cff")
                    if d > 2.6:
                        c = C("6a3fd0")
                px(img, x, y, c)
            elif d <= 1.0:
                px(img, x, y, STEEL[2])
    return img


FUSION_FILES = ["block/fusion_casing", "block/fusion_magnet", "block/fusion_glass", "block/fusion_front", "block/fusion_front_on"]


# ---------------------------------------------------------------- meteors
ROCK = [C("0e0c0c"), C("1b1716"), C("2a2421"), C("3a312c"), C("4d423a"), C("6a5c50")]
IRON_FLECK = [C("8a8f96"), C("c4ccd4")]


def _crack(x, y):
    """Shared crack field so the rock texture's dark veins and the glow layer line up exactly."""
    v = (math.sin(x * 0.9 + y * 0.35) + math.sin(y * 1.3 - x * 0.4) * 0.7 + math.sin((x + y) * 0.5) * 0.5)
    return abs(v) < 0.18


def meteor_rock(w=64, h=32):
    img = new(w, h)
    for y in range(h):
        for x in range(w):
            n = (x * 7 + y * 13 + (x * y) % 5) % 6
            c = ROCK[2 + n % 3]
            if _crack(x, y):
                c = ROCK[0]
            elif (x * 31 + y * 17) % 23 == 0:
                c = IRON_FLECK[(x + y) % 2]
            px(img, x, y, c)
    return img


def meteor_glow(w=64, h=32):
    img = new(w, h)
    for y in range(h):
        for x in range(w):
            if _crack(x, y):
                px(img, x, y, C("fff0c0") if (x + y) % 4 == 0 else C("ff8a2a"))
    return img


def meteorite_block():
    """Meteorite: charred fusion crust with iron flecks and faint ember veins."""
    img = meteor_rock(16, 16)
    for y in range(16):
        for x in range(16):
            if _crack(x + 5, y + 3):
                px(img, x, y, C("7a2a10") if (x + y) % 3 else C("c45a1a"))
    edges_c = [C("4d423a"), C("100c0c")]
    for i in range(16):
        px(img, i, 0, edges_c[0]); px(img, 0, i, edges_c[0])
        px(img, i, 15, edges_c[1]); px(img, 15, i, edges_c[1])
    return img


# ---------------------------------------------------------------- robot arm (entity atlas, 64x32)
def robot_arm_entity():
    """Industrial arm: safety-orange segments with black hazard bands, black joints, steel gripper."""
    img = new(64, 32)
    orange = [C(h) for h in ["5a2a08", "8a4410", "c46a1a", "f08a2a", "ffb060", "ffd8a8"]]
    dark = [C(h) for h in ["0c0e10", "16191c", "22262a", "30363c", "444b52"]]
    steel = [C(h) for h in ramp_at(ADAM, ARMOR_T)]

    def region(x0, y0, w, h, fn):
        for y in range(h):
            for x in range(w):
                px(img, x0 + x, y0 + y, fn(x, y, w, h))
    plate = lambda x, y, w, h: dark[3] if (x % 8 == 0 or y % 6 == 0) else dark[2] if (x + y) % 5 else dark[1]
    arm = lambda x, y, w, h: (dark[1] if (y // 2) % 6 == 0 and (x + y) % 4 < 2 else
                              orange[4] if x % 12 == 0 else orange[3] if x % 12 < 6 else orange[2])
    joint = lambda x, y, w, h: dark[4] if y == 0 else dark[2] if (x + y) % 3 else dark[0]
    grip = lambda x, y, w, h: steel[5] if y == 0 else steel[4] if x % 4 else steel[2]
    region(0, 0, 32, 10, plate)
    region(32, 0, 32, 10, lambda x, y, w, h: dark[3] if y < 2 else dark[1])
    region(0, 10, 12, 14, arm)
    region(12, 10, 16, 8, joint)
    region(28, 10, 12, 13, arm)
    region(40, 10, 16, 8, joint)
    region(0, 24, 20, 8, grip)
    return img


# ---------------------------------------------------------------- hover-bike
BIKE_ICON = [
    "................",
    "................",
    "...........cc...",
    "..........cww...",
    "........k.cw....",
    "......kkkkhhh...",
    "..hhhhhhhhhhhhn.",
    ".shhhwwwwwwwhhnn",
    ".shhhhhhhhhhhhn.",
    "..dddddddddddd..",
    "...pp.......pp..",
    "...gg.......gg..",
    "..g..g.....g..g.",
    "................",
    "................",
    "................",
]


def hover_bike_icon():
    cols = {"h": C("e8eef4"), "w": C("19c6d8"), "c": C("8fe8ff"), "k": C("1a1f26"), "n": C("2a5aa8"), "s": C("ffb347"),
            "d": C("4a5562"), "p": C("8a96a2"), "g": C("6fe3ff")}
    img = stamp(BIKE_ICON, cols)
    return outline(img, C("10141a"))


def hover_bike_entity():
    """Gloss-white hull with a cyan racing stripe, blue nose, black seat, tinted screen, steel pods."""
    img = new(64, 64)
    white = [C(h) for h in ["8a96a2", "b8c4ce", "dce4ea", "f2f6f9", "ffffff"]]

    def region(x0, y0, w, h, fn):
        for y in range(h):
            for x in range(w):
                px(img, x0 + x, y0 + y, fn(x, y))
    region(0, 0, 54, 24, lambda x, y: C("19c6d8") if y in (21, 22) or (20 <= x < 27 and 20 <= y < 24 and x % 7 in (3, 4))
           else white[3] if y < 20 else white[2] if (x + y) % 9 else white[1])
    region(0, 24, 28, 12, lambda x, y: C("1a1f26") if (x + y) % 4 else C("2a313a"))
    region(28, 24, 18, 8, lambda x, y: C("2a5aa8") if y > 2 else C("4a7ad0"))
    region(46, 24, 12, 4, lambda x, y: C("8fe8ff") if (x + y) % 5 else C("e6fcff"))
    region(0, 36, 22, 3, lambda x, y: C("22262a"))
    region(0, 40, 32, 10, lambda x, y: C("6fe3ff") if y >= 8 else C("8a96a2") if x % 4 else C("5a6672"))
    region(32, 36, 12, 7, lambda x, y: C("ffb347") if y >= 5 else C("30363c"))
    return img


# ---------------------------------------------------------------- mech (entity atlas, 64x64)
def mech_entity():
    """Gunmetal exo-frame: plated surfaces, black hydraulic joints, safety-yellow trim and hazard bands."""
    img = new(64, 64)
    gun = [C(h) for h in ["161a1f", "242a31", "343c45", "4a545f", "68737f", "98a4b0"]]
    yellow = [C(h) for h in ["6a4a08", "a87a10", "e0b020", "ffd84a"]]

    def region(x0, y0, w, h, fn):
        for y in range(h):
            for x in range(w):
                px(img, x0 + x, y0 + y, fn(x, y))
    plate = lambda x, y: gun[5] if y % 8 == 0 else gun[1] if x % 8 == 7 else gun[3] if (x // 8 + y // 8) % 2 else gun[2]
    trim = lambda x, y: yellow[2] if (x + y) // 3 % 2 else gun[0]
    joint = lambda x, y: gun[0] if (x + y) % 3 else gun[1]
    region(0, 0, 36, 12, lambda x, y: trim(x, y) if y < 2 else plate(x, y))
    region(0, 12, 36, 22, plate)
    region(36, 12, 24, 28, lambda x, y: yellow[2] if x < 2 else plate(x, y))
    region(0, 34, 40, 14, lambda x, y: trim(x, y) if y < 3 else plate(x, y))
    region(0, 48, 28, 4, lambda x, y: C("6fe3ff") if x % 3 else C("aef2ff"))
    region(40, 40, 24, 16, lambda x, y: plate(x, y) if y > 1 else yellow[3])
    region(0, 52, 24, 12, joint)
    region(24, 56, 28, 8, lambda x, y: gun[1] if y > 4 else gun[3])
    region(36, 0, 16, 12, lambda x, y: gun[2] if y < 9 else C("ff8a2a"))
    region(52, 0, 12, 40, lambda x, y: joint(x, y) if y in (0, 1, 13, 14) else plate(x, y))
    return img


MECH_ICON = [
    "................",
    "....kkkkkkkk....",
    "...kyyyyyyyyk...",
    "...kg......gk...",
    "..gkg.cc...gkg..",
    "..g.g......g.g..",
    "..g.gggggggg.g..",
    "..c..gggggg..c..",
    ".....gg..gg.....",
    ".....gg..gg.....",
    ".....kk..kk.....",
    ".....gg..gg.....",
    ".....gg..gg.....",
    "....ggg..ggg....",
    "....kkk..kkk....",
    "................",
]


def mech_icon():
    cols = {"k": C("1a1f26"), "y": C("e0b020"), "g": C("68737f"), "c": C("6fe3ff")}
    return outline(stamp(MECH_ICON, cols), C("0c0f12"))


# ---------------------------------------------------------------- space elevator
TETHER_ICON = [
    "................",
    "............kk..",
    "...........kcwk.",
    "..........kcwk..",
    ".........kcwk...",
    "........kcwk....",
    ".......kcwk.....",
    "......kcwk......",
    ".....kcwk.......",
    "....kcwk........",
    "...kcwk.........",
    "..kcwk..........",
    ".kgggk..........",
    ".kgggk..........",
    "..kkk...........",
    "................",
]


def nanotube_tether():
    """A coil of black nanotube ribbon with a cyan core strand, ending in a steel clamp."""
    cols = {"k": C("10141a"), "c": C("3a4450"), "w": C("6fe3ff"), "g": C("a2bad8")}
    return stamp(TETHER_ICON, cols)


ARMOR_KINDS = ("helmet", "chestplate", "leggings", "boots")
ARMOR_FILES = [f"item/{m}_{k}" for m in ARMOR_SETS for k in ARMOR_KINDS] + \
    [f"entity/equipment/humanoid/{m}" for m in ARMOR_SETS] + [f"entity/equipment/humanoid_leggings/{m}" for m in ARMOR_SETS] +     [f"entity/equipment/humanoid_baby/{m}" for m in ARMOR_SETS]


FILES = [
    "block/deepslate_adamantium_ore_overlay", "block/faradaenium_ore_overlay", "block/cortanium_ore_overlay",
    "block/qubit_ore_overlay", "block/adamantium_block", "block/faradaenium_block", "block/cortanium_block",
    "block/qubit_crystal_block", "block/quantum_alloy_block",
    "item/raw_adamantium", "item/raw_faradaenium", "item/adamantium_ingot", "item/faradaenium_ingot",
    "item/cortanium_ingot", "item/quantum_alloy_ingot", "item/cortanium_shard", "item/qubit_crystal",
    "item/quantum_dust", "item/entangled_pair", "item/quantum_photomask", "item/quantum_processor",
    "item/exotic_matter", "item/singularity",
] + [f"item/{m}_{k}" for m in ("adamantium", "faradaenium", "cortanium", "quantum") for k in TOOLS] + [
    "item/railgun", "item/plasma_rifle", "item/singularity_launcher", "item/quantum_blade",
    "item/quantum_disassembler",
]


FILES += STARFORGE_FILES + PAXEL_FILES + ARMOR_FILES + ["item/nanotube_tether", "item/mech_suit", "entity/mech", "item/hover_bike", "entity/hover_bike", "item/cosmic_dust", "entity/robot_arm", "entity/turret", "entity/meteor", "entity/meteor_glow", "block/meteorite"] + FUSION_FILES


if __name__ == "__main__":
    generate_textures()
