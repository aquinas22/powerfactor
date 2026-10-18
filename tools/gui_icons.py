"""PowerFactor GUI icon set: hand-drawn 9x9 pixel icons on a 10 px grid (docs/gui-style-guide.md, "Show,
don't tell"). One palette, a 1 px darker rim on coloured shapes, a top-left highlight.

Writes textures/gui/icons.png (128x128, icon i at ((i % 12) * 10, (i // 12) * 10)) and prints the Java
enum order. The order below IS the atlas index: append new icons at the end, never reorder.
"""
import math
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "powerfactor", "textures",
                    "gui")


def hexc(h, a=255):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


WHITE = hexc("f4fbff")
INK = hexc("14202a")


def stamp(rows, base, extra=None):
    """Mask letters: # base, + highlight, - shadow, o rim (dark base), w white, k ink, . clear; extra maps more."""
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    pal = {"#": base, "+": shade(base, 1.35), "-": shade(base, 0.72), "o": shade(base, 0.42), "w": WHITE, "k": INK}
    pal.update(extra or {})
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch])
    return img


def plot(fn, base):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    fn(img, base)
    return img


def sine(img, c):
    prev = None
    for x in range(9):
        y = 4 - round(3 * math.sin((x + 0.5) * 2 * math.pi / 8))
        lo, hi = (y, y) if prev is None else (min(prev, y), max(prev, y))
        for yy in range(lo, hi + 1):
            img.putpixel((x, yy), c)
        if y + 1 < 9:
            img.putpixel((x, y + 1), shade(c, 0.55))
        prev = y


def atom(img, c):
    """Two crossed orbits round a bright nucleus."""
    for y in range(9):
        for x in range(9):
            dx, dy = x - 4, y - 4
            for ang in (math.pi / 4, -math.pi / 4):
                u = dx * math.cos(ang) + dy * math.sin(ang)
                v = -dx * math.sin(ang) + dy * math.cos(ang)
                if abs(math.hypot(u / 4.2, v / 1.7) - 1) < 0.22:
                    img.putpixel((x, y), shade(c, 1.3 if x + y < 8 else 0.95))
    for x, y in ((4, 4), (3, 4), (5, 4), (4, 3), (4, 5)):
        img.putpixel((x, y), WHITE if (x, y) == (4, 4) else shade(c, 0.6))


def ring(img, c, r0=2.6, r1=3.9, dot=True):
    for y in range(9):
        for x in range(9):
            d = math.hypot(x - 4, y - 4)
            if r0 <= d <= r1:
                img.putpixel((x, y), shade(c, 1.25 if x + y < 7 else 0.85))
    if dot:
        img.putpixel((4, 4), WHITE)


ICONS = [
    ("fe", stamp([".....oo..", "....o+o..", "...o+#o..", "..o+##ooo", ".o+####o.", "ooo##+o..", "..o#+o...", "..o+o....", "..oo....."],
                 hexc("ffc23a"))),
    ("ee", plot(sine, hexc("4fe0c0"))),
    ("ce", stamp(["........w", ".......w#", "......w#.", ".....w#..", "....w#...", "...w#....", "..w#.....", ".w#......", "wwwwwwwww"],
                 hexc("b18cff"))),
    ("pf", stamp([".........", "..ooooo..", ".o+++++o.", "o+k####-o", "o+#k###-o", "o+##k##-o", "ooooooooo", ".........", "........."],
                 hexc("5cd66a"))),
    ("heat", stamp(["....o....", "...o+o...", "...o#o.o.", "..o#+#o+o", ".o##w##+o", ".o#wwww#o", ".o#wwww#o", "..o####o.", "...oooo.."],
                   hexc("ff7a2a"), {"w": hexc("ffe07a")})),
    ("temp", stamp(["...ooo...", "...owo...", "...owo...", "...o#o...", "...o#o...", "..o###o..", "..o#+#o..", "..o###o..", "...ooo..."],
                   hexc("ff4a4a"))),
    ("fluid", stamp(["....o....", "....o....", "...o#o...", "...o+o...", "..o+##o..", ".o+####o.", ".o#####o.", "..o##-o..", "...ooo..."],
                    hexc("3f9cff"))),
    ("steam", stamp([".........", "..oo.....", ".o++o.oo.", ".o++oo++o", "o+++o+++o", "o#######o", ".ooooooo.", ".........", "........."],
                    hexc("dbe6ec"))),
    ("item", stamp(["...ooo...", ".oo+++oo.", "o+++++++o", "o##+++--o", "o####---o", "o####---o", "o####---o", ".oo##-oo.", "...ooo..."],
                   hexc("d9a65a"))),
    ("speed", stamp([".........", "o...o....", "+o..+o...", ".+o..+o..", "..+o..+o.", ".+o..+o..", "+o..+o...", "o...o....", "........."],
                    hexc("6fe3ff"))),
    ("efficiency", stamp([".......oo", ".....o++o", "...o++#o.", "..o+##-o.", ".o+#-#o..", ".o#-#o...", "..o-o....", ".o.o.....", "o........"],
                         hexc("5cd66a"))),
    ("priority", stamp([".........", "......ooo", "......o+o", "...ooo#+o", "...o+o##o", "ooo#+o##o", "o+o##o##o", "o##o#o#-o", "ooooooooo"],
                       hexc("ffb347"))),
    ("allow", stamp([".........", ".......oo", "......o+o", ".oo..o+o.", "o+o.o+o..", ".o+o+o...", "..o+o....", "...o.....", "........."],
                    hexc("5cff8a"))),
    ("deny", stamp(["..ooooo..", ".o+++++o.", "o+o...#-o", "o+.o..#-o", "o+..o.#-o", "o+...o#-o", "o+....o-o", ".o#####o.", "..ooooo.."],
                   hexc("ff5a4a"))),
    ("filter", stamp(["ooooooooo", "o+++++++o", ".o+###-o.", "..o##-o..", "...o#o...", "...o#o...", "...o#o...", "...o#o...", "....o...."],
                     hexc("6fe3ff"))),
    ("exact", stamp(["oooooo...", "o++++#o..", "o+ko##-o.", "o+####-o.", "o+####o..", "o####o...", "o###o....", "o##o.....", "oooo....."],
                    hexc("b18cff"))),
    ("link", stamp([".........", ".ooo.....", "o+++o....", "o+.oooo..", "o+o+#.#o.", ".oo#.o#-o", "...o-...o", "....oo-o.", "......oo."],
                   hexc("6fe3ff"))),
    ("range", plot(lambda img, c: ring(img, c), hexc("6fe3ff"))),
    ("kills", stamp(["..ooooo..", ".o+++++o.", "o+++++++o", "o+kk+kk+o", "o+kk+kk+o", "o+++k+++o", ".o+++++o.", "..o+o+o..", "..ooooo.."],
                    hexc("e8eef2"))),
    ("animal", stamp([".oo...oo.", "o++o.o++o", "o##o.o##o", ".oo.o.oo.", "...o+o...", "..o+##o..", ".o+####o.", ".o####-o.", "..ooooo.."],
                     hexc("ff9cb0"))),
    ("player", stamp(["..ooooo..", "..o+++o..", "..o+#+o..", "..o###o..", "...ooo...", ".ooooooo.", "o+#####o.", "o+#####-o", "ooooooooo"],
                     hexc("4f8bff"))),
    ("hostile", stamp(["ooooooooo", "o+++++++o", "o+kk+kk#o", "o+kk+kk#o", "o+++kk##o", "o++kkkk#o", "o++k##k#o", "o#######o", "ooooooooo"],
                      hexc("4ec94a"))),
    ("clock", stamp(["..ooooo..", ".owwwwwo.", "owwwkwwwo", "owwwkwwwo", "owwwkkkwo", "owwwwwwwo", "owwwwwwwo", ".owwwwwo.", "..ooooo.."],
                    hexc("8fa6b5"))),
    ("redstone", stamp(["....o....", "...o+o...", "..o+#-o..", ".o+##--o.", "..o#-oo..", "...oo....", "..o...o..", ".o+o.o+o.", "..o...o.."],
                       hexc("ff3a2a"))),
    ("power", stamp(["....o....", "..o.+.o..", ".o+.#.#o.", "o+..#..#o", "o+..o..#o", "o+.....#o", ".o#...#o.", "..o###o..", "...ooo..."],
                    hexc("5cff8a"))),
    ("warning", stamp(["....o....", "...o+o...", "...o+o...", "..o+k#o..", "..o#k#o..", ".o##k##o.", ".o#####o.", "o###k###o", "ooooooooo"],
                      hexc("ffc23a"))),
    ("error", stamp(["..ooooo..", ".o+++++o.", "o+w+++w#o", "o++w+w##o", "o+++w###o", "o++w#w##o", "o+w###w#o", ".o#####o.", "..ooooo.."],
                    hexc("ff4a4a"))),
    ("ok", stamp(["..ooooo..", ".o+++++o.", "o++++++wo", "o+++++w#o", "ow++ww##o", "o#ww#w##o", "o##w####o", ".o#####o.", "..ooooo.."],
                 hexc("33c96a"))),
    ("wait", stamp(["ooooooooo", ".o+++++o.", "..o+++o..", "...o#o...", "....o....", "...o#o...", "..o###o..", ".o#####o.", "ooooooooo"],
                   hexc("ffb347"))),
    ("night", stamp(["...ooo...", ".oo+o....", "o++o.....", "o+o......", "o+o......", "o##o.....", "o###oo..o", ".oo####o.", "...oooo.."],
                    hexc("e6e0a0"))),
    ("day", stamp(["....o....", ".o..o..o.", "..ooooo..", "..o+++o..", "ooo+##ooo", "..o###o..", "..ooooo..", ".o..o..o.", "....o...."],
                  hexc("ffd23a"))),
    ("area", stamp(["ooo...ooo", "o+.....+o", "o.......o", ".........", "....w....", ".........", "o.......o", "o#.....#o", "ooo...ooo"],
                   hexc("ffb347"))),
    ("depth", stamp(["...ooo...", "...o+o...", "...o#o...", ".ooo#ooo.", "..o###o..", "...o#o...", "....o....", "ooooooooo", "-#-#-#-#-"],
                    hexc("6fe3ff"), {"-": hexc("6b5a44"), "#": hexc("8c7458")})),
    ("fortune", stamp([".oo...oo.", "o++o.o++o", "o+#o.o#-o", ".oo#o#oo.", "...o#o...", ".oo#o#oo.", "o+#o.o#-o", "o##o.o--o", ".oo...oo."],
                      hexc("33d96a"))),
    ("silk", stamp([".......oo", "......o+o", ".....o+#o", "....o+#o.", "...o+#o..", "..o+#o...", ".o+#o....", ".oo-o....", "o........"],
                   hexc("e8f6ff"))),
    ("void", stamp(["...ooo...", "ooooooooo", "o+++++++o", ".o+#-#-o.", ".o+#-#-o.", ".o+#-#-o.", ".o+#-#-o.", ".o#####o.", "..ooooo.."],
                   hexc("9aa8b2"))),
    ("restart", stamp(["..oooo.o.", ".o++++o+o", "o+o..o++o", "o+o.oooo.", "o+o......", "o+o....oo", ".o#o..o#o", "..o####o.", "...oooo.."],
                      hexc("e8f6ff"))),
    ("plus", stamp([".........", "...ooo...", "...o+o...", ".ooo#ooo.", ".o+###-o.", ".ooo#ooo.", "...o-o...", "...ooo...", "........."],
                   hexc("e8f6ff"))),
    ("minus", stamp([".........", ".........", ".........", ".ooooooo.", ".o+###-o.", ".ooooooo.", ".........", ".........", "........."],
                    hexc("e8f6ff"))),
    ("shield", stamp(["ooooooooo", "o+++++++o", "o++###-#o", "o+####-#o", "o+####-#o", ".o###--o.", ".o###-#o.", "..o##-o..", "...ooo..."],
                     hexc("6fe3ff"))),
    ("hit", stamp(["o...o...o", ".o.o+o.o.", "..o+#+o..", ".o+###+o.", "o+##w##+o", ".o+###+o.", "..o+#+o..", ".o.o+o.o.", "o...o...o"],
                  hexc("ff9a3a"))),
    ("output", stamp(["oooo.....", "o++o..o..", "o+.o..oo.", "o+.ooo+#o", "o+.o+###o", "o+.ooo#o.", "o+.o..o..", "o##o.....", "oooo....."],
                     hexc("f09a2a"))),
    ("input", stamp([".....oooo", "..o..o++o", ".oo..o+.o", "o#+ooo+.o", "o###+o+.o", ".o#ooo+.o", "..o..o+.o", ".....o##o", ".....oooo"],
                    hexc("2e9be6"))),
    ("matter", plot(atom, hexc("b18cff"))),
    ("battery", stamp(["...ooo...", "..oo+oo..", "..o+++o..", "..o+..o..", "..o####..", "..o###o..", "..o###o..", "..o#-#o..", "..ooooo.."],
                      hexc("5cd66a"), {".": (0, 0, 0, 0)})),
    ("fuel", stamp(["..ooo....", ".o+o.ooo.", "ooooo+#oo", "o++++###o", "o+##o##-o", "o+#o#o#-o", "o+o###o-o", "o#######o", "ooooooooo"],
                   hexc("e2553c"))),
    ("rotor", stamp(["...oo....", "...o+o...", "o..o#o...", "+oo.o.ooo", ".o#owo#o.", "ooo.o.oo+", "...o#o..o", "...o-o...", "....oo..."],
                    hexc("b8c4cc"))),
    ("mined", stamp(["..oooo...", ".o++++oo.", "o+oooo+#o", "oo..o#o-o", "...o#o.oo", "..o#o....", ".o#o.....", "o#o......", "oo......."],
                     hexc("c6d0d8"), {"#": hexc("9a6a3a"), "o": hexc("3a2a1a")})),
    ("network", stamp(["oo.....oo", "o+o...o+o", ".o#o.o#o.", "..o.o.o..", "...o#o...", "...o-o...", "...o#o...", "..oo#oo..", "..o+#-o.."],
                      hexc("6fe3ff"))),
    ("upgrade", stamp(["ooooooooo", "o+++w+++o", "o++www++o", "o+wwwww#o", "o+++w+##o", "o+++w+##o", "o+++w###o", "o#######o", "ooooooooo"],
                      hexc("9c74f0"))),
    ("hz", plot(sine, hexc("ffd040"))),
    ("lock", stamp(["..ooooo..", ".o+...+o.", ".o.....o.", "ooooooooo", "o+++++#-o", "o+++k+#-o", "o++#k##-o", "o#######o", "ooooooooo"],
                   hexc("ffc23a"))),
    ("unlock", stamp(["..ooooo..", ".o+...+o.", ".o.....o.", ".o.......", "ooooooooo", "o+++k+#-o", "o++#k##-o", "o#######o", "ooooooooo"],
                     hexc("8fa6b5"))),
    ("vision", stamp([".........", "..ooooo..", ".o+++++o.", "o++kkk+#o", "o+kwkk##o", "o++kkk#-o", ".o#####o.", "..ooooo..", "........."],
                     hexc("e8f6ff"))),
    ("channel", stamp(["oooo.oooo", "o++o.o++o", "o+#o.o+#o", "oooo.oooo", ".........", "oooo.oooo", "o++o.o++o", "o+#o.o+#o", "oooo.oooo"],
                      hexc("6fe3ff"))),
]


def main():
    atlas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for i, (name, img) in enumerate(ICONS):
        atlas.paste(img, ((i % 12) * 10, (i // 12) * 10), img)
    os.makedirs(ROOT, exist_ok=True)
    atlas.save(os.path.join(ROOT, "icons.png"))
    print(len(ICONS), "icons:", ", ".join(n.upper() for n, _ in ICONS))


if __name__ == "__main__":
    main()
