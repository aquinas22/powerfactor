"""Black Hole Power Plant art: containment casing, field coil, event-horizon glass, injector, collector, controller face."""
import math
import random

from quantum_art import C, mix, new, px, save, save_anim

B = "block/"


def noise(base, var, seed):
    r = random.Random(seed)
    img = new()
    for y in range(16):
        for x in range(16):
            k = 1 + r.uniform(-var, var)
            img.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base) + (255,))
    return img


def casing():
    """Containment casing: near-black alloy with violet seams and corner studs."""
    img = noise((30, 26, 40), 0.08, 91)
    for i in range(16):
        for p in ((i, 0), (0, i)):
            px(img, *p, C("4a3a6a"))
        for p in ((i, 15), (15, i)):
            px(img, *p, C("100c18"))
    for i in range(3, 13):
        px(img, i, 7, C("6a4aa0"))
        px(img, 7, i, C("6a4aa0"))
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, *p, C("b18cff"))
    return img


def coil(lit, frame=0):
    """Field coil: copper windings round a glowing violet core band."""
    img = noise((140, 80, 40), 0.06, 92)
    for y in range(16):
        for x in range(16):
            if y % 3 == 0:
                px(img, x, y, C("6a3a1a"))
            elif y % 3 == 1:
                px(img, x, y, C("e8a05a"))
    if lit:
        for x in range(16):
            v = 0.5 + 0.5 * math.sin(x * 0.8 + frame)
            px(img, x, 7, mix(C("6a4aa0"), C("e8dcff"), v))
            px(img, x, 8, mix(C("4a2e8a"), C("b18cff"), v))
    return img


def glass():
    """Event-horizon glass: a dark smoked pane in a violet frame."""
    img = new()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                img.putpixel((x, y), C("6a4aa0"))
            else:
                img.putpixel((x, y), (60, 40, 90, 28))
    for k in range(3, 7):
        px(img, k, 10 - k, (220, 200, 255, 90))
    return img


def injector():
    """Accretion injector: an orange-lit intake throat."""
    img = casing()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.5:
                px(img, x, y, mix(C("ffe07a"), C("3a1408"), min(1, d / 5.5)) if d < 4.5 else C("2a2030"))
    return img


def collector(frame=0):
    """Hawking collector: golden radiator fins that shimmer."""
    img = casing()
    for x in range(2, 14):
        for y in range(2, 14):
            fin = (x % 3) == 0
            v = 0.5 + 0.5 * math.sin(y * 0.6 + frame + x * 0.2)
            px(img, x, y, mix(C("8a6420"), C("fff0a8"), v) if fin else C("2a2030"))
    return img


def front(lit, frame=0):
    """Controller face: a black disc ringed with light on a dark screen."""
    img = casing()
    for y in range(2, 14):
        for x in range(2, 14):
            px(img, x, y, C("05060a"))
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.6 < d < 3.6:
                px(img, x, y, C("fff4d6") if lit else C("4a3a6a"))
            elif lit and 3.6 <= d < 5 and abs(y - 7.5) < 1.5:
                a = math.atan2(y - 7.5, x - 7.5) + frame
                px(img, x, y, mix(C("ffb347"), C("c0301a"), (math.sin(a * 2) + 1) / 2))
    return img


def generate_textures():
    save(casing(), B + "containment_casing.png")
    save(coil(False), B + "containment_coil.png")
    save_anim([coil(True, i * 0.8) for i in range(8)], B + "containment_coil_on.png", 2)
    save(glass(), B + "event_horizon_glass.png")
    save(injector(), B + "accretion_injector.png")
    save(collector(), B + "hawking_collector.png")
    save_anim([collector(i * 0.8) for i in range(8)], B + "hawking_collector_on.png", 2)
    save(front(False), B + "black_hole_front.png")
    save_anim([front(True, i * 0.8) for i in range(8)], B + "black_hole_front_on.png", 2)


if __name__ == "__main__":
    generate_textures()
    print("Black hole textures written.")
