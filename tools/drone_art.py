"""AI mining drone art: the drone item, the hub's pad, sides and console face."""
import math
import random

from quantum_art import C, mix, new, outline, px, save, save_anim, stamp

B, I = "block/", "item/"


def drone_item():
    rows = [
        "................",
        ".rrr........rrr.",
        "rRRRr......rRRRr",
        ".rrrm......mrrr.",
        "....aa....aa....",
        ".....abbbba.....",
        "......bccb......",
        "......bcLb......",
        "......bccb......",
        ".....abbbba.....",
        "....aa....aa....",
        ".rrrm......mrrr.",
        "rRRRr......rRRRr",
        ".rrr........rrr.",
        "................",
        "................",
    ]
    return outline(stamp(rows, {"r": (200, 210, 220, 140), "R": C("e4ecf0"), "m": C("39424a"), "a": C("b8c4cc"), "b": C("2a3036"),
                                "c": C("6fe3ff"), "L": C("ffffff"), ".": (0, 0, 0, 0)}), C("14181c"))


def pad_top(lit, frame=0):
    """The landing pad: a dark deck with a yellow H and a ring of lights that chase when working."""
    r = random.Random(101)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-0.06, 0.06)
            img.putpixel((x, y), tuple(int(c * k) for c in (44, 50, 56)) + (255,))
    for y in range(4, 12):
        px(img, 5, y, C("e8bc2a"))
        px(img, 10, y, C("e8bc2a"))
    for x in range(5, 11):
        px(img, x, 7, C("e8bc2a"))
    for k in range(12):
        a = k * math.pi * 2 / 12
        x, y = int(round(7.5 + math.cos(a) * 6.5)), int(round(7.5 + math.sin(a) * 6.5))
        on = lit and (k - frame) % 4 == 0
        px(img, x, y, C("ffffff") if on else C("6fe3ff") if lit else C("2a5a6a"))
    return img


def hub_side():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), C("3a4248") if (x // 4 + y // 4) % 2 == 0 else C("343c42"))
    for x in range(16):
        px(img, x, 0, C("8a949a"))
        px(img, x, 15, C("1a1e22"))
    for y in range(2, 14, 3):
        px(img, 2, y, C("7cff8a"))
    return img


def hub_front(lit, frame=0):
    img = hub_side()
    for y in range(3, 13):
        for x in range(3, 13):
            px(img, x, y, C("07110c"))
    for k in range(3):
        r = 2 + k * 2
        for i in range(24):
            a = i * math.pi * 2 / 24
            x, y = int(round(8 + math.cos(a) * r)), int(round(8 + math.sin(a) * r))
            if 3 <= x < 13 and 3 <= y < 13:
                px(img, x, y, C("16301f"))
    if lit:
        a = frame * 0.8
        for d in range(5):
            x, y = int(round(8 + math.cos(a) * d)), int(round(8 + math.sin(a) * d))
            px(img, x, y, C("7cff8a"))
        for x, y in ((5, 6), (10, 9), (7, 11)):
            px(img, x, y, C("ffd84a"))
    return img


def generate_textures():
    save(drone_item(), I + "mining_drone.png")
    save(pad_top(False), B + "drone_hub_top.png")
    save_anim([pad_top(True, i) for i in range(4)], B + "drone_hub_top_on.png", 3)
    save(hub_side(), B + "drone_hub_side.png")
    save(hub_front(False), B + "drone_hub_front.png")
    save_anim([hub_front(True, i) for i in range(8)], B + "drone_hub_front_on.png", 2)


if __name__ == "__main__":
    generate_textures()
    print("Drone textures written.")
