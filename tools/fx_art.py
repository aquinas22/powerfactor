"""Effect textures for additive renderers (glow sprites, rings, beams, sparks). Run: python3 tools/fx_art.py

Additive blending ignores alpha, so brightness lives in RGB; alpha mirrors it so the pipeline's
cut-out threshold trims the faint fringe cleanly.
"""
import math, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "powerfactor", "textures", "entity", "fx")


def save(img, name):
    os.makedirs(ROOT, exist_ok=True)
    img.save(os.path.join(ROOT, name))


def px(v):
    v = max(0.0, min(1.0, v))
    c = int(round(255 * v))
    return (c, c, c, c)


def glow(size=64):
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + .5 - size / 2, y + .5 - size / 2) / (size / 2)
            # Hot core, long soft falloff.
            v = max(0.0, 1 - d) ** 2.2 + 0.9 * max(0.0, 1 - d * 3.2) ** 2
            img.putpixel((x, y), px(v))
    return img


def ring(w=64, h=16):
    """Segmented band: bright core lines, soft edges, a gap every quarter."""
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        edge = 1 - abs((y + .5) / h * 2 - 1)
        for x in range(w):
            seg = (x % 16)
            gap = 0.15 if seg in (0, 15) else 1.0
            tick = 1.25 if seg in (4, 11) else 1.0
            img.putpixel((x, y), px((edge ** 1.6) * gap * tick))
    return img


def beam(w=16, h=32):
    """Bright centre column fading to the sides, with travelling pulses down its length."""
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        pulse = 0.75 + 0.25 * math.cos(y / h * math.pi * 4)
        for x in range(w):
            d = abs((x + .5) / w * 2 - 1)
            v = (1 - d) ** 1.8 * pulse + (0.6 if d < 0.15 else 0)
            img.putpixel((x, y), px(v))
    return img


def spark(size=16):
    img = Image.new("RGBA", (size, size))
    c = size / 2
    for y in range(size):
        for x in range(size):
            dx, dy = abs(x + .5 - c), abs(y + .5 - c)
            v = max(0.0, 1 - math.hypot(dx, dy) / c) ** 3 + max(0.0, 1 - dx / 1.2) * max(0.0, 1 - dy / c) * .8 \
                + max(0.0, 1 - dy / 1.2) * max(0.0, 1 - dx / c) * .8
            img.putpixel((x, y), px(v))
    return img


def weapon_glow(rgb):
    """Emissive weapon parts (coils, plasma chambers, blades): a bright core band with soft edges."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        d = abs((y + .5) / 16 * 2 - 1)
        for x in range(16):
            pulse = 0.85 + 0.15 * math.cos(x / 16 * math.pi * 4)
            k = (1 - d) ** 0.7 * pulse
            w = max(0.0, 1 - d * 2.5) * 0.8
            img.putpixel((x, y), tuple(int(min(255, c * k + 255 * w)) for c in rgb) + (255,))
    return img


def metal(base, light, dark, seams):
    """Weapon plating for the items atlas: brushed metal with panel seams and bevel highlights."""
    import random
    r = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        streak = r.uniform(-6, 6)
        for x in range(16):
            v = tuple(max(0, min(255, int(c + streak + r.uniform(-3, 3)))) for c in base)
            img.putpixel((x, y), v + (255,))
    for i in range(16):
        img.putpixel((i, 0), light + (255,))
        img.putpixel((0, i), light + (255,))
        img.putpixel((i, 15), dark + (255,))
        img.putpixel((15, i), dark + (255,))
    if seams:
        for i in range(1, 15):
            img.putpixel((i, 7), dark + (255,))
            img.putpixel((i, 8), light + (255,))
        for y in (3, 12):
            img.putpixel((3, y), light + (255,))
            img.putpixel((12, y), light + (255,))
    return img


def generate_textures():
    item = os.path.join(ROOT, "..", "..", "item", "model")
    os.makedirs(item, exist_ok=True)
    metal((52, 60, 70), (96, 108, 120), (24, 28, 34), True).save(os.path.join(item, "weapon_body.png"))
    metal((150, 160, 170), (210, 218, 226), (92, 100, 110), False).save(os.path.join(item, "weapon_steel.png"))
    os.makedirs(item, exist_ok=True)
    for name, rgb in (("cyan", (111, 227, 255)), ("green", (124, 255, 154)), ("violet", (177, 140, 255)), ("orange", (255, 179, 71))):
        weapon_glow(rgb).save(os.path.join(item, f"weapon_glow_{name}.png"))
    save(glow(), "glow.png")
    save(ring(), "ring.png")
    save(beam(), "beam.png")
    save(spark(), "spark.png")


if __name__ == "__main__":
    generate_textures()
