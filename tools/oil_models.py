"""Block models for the oil machinery, written by gen_data.py's phase_oil.

Parts are laid out in the machine's own frame, the one its renderer uses: pixels, +Z toward the
front, +Y up, origin at the middle of the block's floor. A north-facing block model looks down -Z,
so every box is mirrored into model space here. Faces take their texels from a parts sheet at one
texel per pixel (see oil_art.py for the rectangles).
"""
from oil_art import PUMPJACK_BASE

# Model face -> the side of the part it shows, in the machine's frame.
SIDES = (("north", "front"), ("south", "back"), ("west", "xp"), ("east", "xn"), ("up", "up"), ("down", "down"))


class Parts:
    def __init__(self, tiles, sheet_size, texture="#parts"):
        self.tiles, self.k, self.texture, self.elements = tiles, 16 / sheet_size, texture, []

    def box(self, x0, y0, z0, x1, y1, z1, tile, glow=0, rotation=None, **sides):
        """A cuboid; `sides` overrides the tile of a side (front, back, xp, xn, up, down) or drops it with None."""
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        size = {"front": (w, h), "back": (w, h), "xp": (d, h), "xn": (d, h), "up": (w, d), "down": (w, d)}
        faces = {}
        for face, side in SIDES:
            name = sides.get(side, tile)
            if name is None:
                continue
            u, v, tw, th = self.tiles[name]
            fw, fh = size[side]
            turn = (fh > fw and tw > th) or (fw > fh and th > tw)
            along, across = (fh, fw) if turn else (fw, fh)
            entry = {"texture": self.texture, "uv": [round(c * self.k, 4) for c in (u, v, u + min(along, tw), v + min(across, th))]}
            if turn:
                entry["rotation"] = 90
            faces[face] = entry
        element = {"from": [8 - x1, y0, 8 - z1], "to": [8 - x0, y1, 8 - z0], "faces": faces}
        if glow:
            element["light_emission"] = glow
        if rotation:
            element["rotation"] = rotation
        self.elements.append(element)
        return self


def pumpjack_base(running):
    """Skid, gear reducer, motor, wellhead and control cabinet; PumpjackRenderer adds everything that moves."""
    p = Parts(PUMPJACK_BASE, 64)
    # Skid: two I-beam runners under a chequer-plate deck, with cross members showing at the ends.
    p.box(-7, 1, -24, 7, 2, 23, "RAIL", up="DECK", down="DECK")
    for side in (-1, 1):
        p.box(min(side * 7, side * 9), 0, -24, max(side * 7, side * 9), 2, 23, "RAIL", up="RAIL_TOP")
    for z in (-23, -10, 4, 20):
        p.box(-7, 0, z, 7, 1, z + 2, "RAIL")
    for x in (-6, 6):
        for z in (-8, 8):
            p.box(x - 1.5, 2, z - 1.5, x + 1.5, 2.6, z + 1.5, "FOOT")
    # Gear reducer on its pedestal, the crankshaft's bearing bosses standing proud of both sides.
    p.box(-4.5, 2, -18, 4.5, 8, -9, "PEDESTAL")
    p.box(-5.5, 8, -20, 5.5, 19, -8, "GEAR_SIDE", front="GEAR_END", back="GEAR_END", up="GEAR_TOP", down="GEAR_TOP")
    p.box(-4, 19, -18, 4, 19.6, -10, "HATCH")
    for side in (-1, 1):
        p.box(min(side * 5.5, side * 6.6), 12.5, -14.5, max(side * 5.5, side * 6.6), 15.5, -11.5, "BOSS")
    # Motor on its slide base, shaft toward the belt side, fan cowl on the other end.
    p.box(-6, 2, -24, 3.5, 2.6, -18, "MOTOR_BASE")
    p.box(-5.5, 2.6, -23.5, 2.5, 7.6, -18.5, "MOTOR_SIDE", xp="MOTOR_END", xn="MOTOR_END")
    p.box(2.5, 2.9, -23.2, 4, 7.3, -18.8, "COWL")
    p.box(-2.5, 7.6, -23, 0.5, 8.6, -20.5, "TERMINAL")
    # Wellhead under the horsehead: flanges, casing head, stuffing box, and the flow line with its valve.
    z = 19.3
    p.box(-3, 2, z - 3, 3, 3.5, z + 3, "WELL_FLANGE")
    p.box(-2, 3.5, z - 2, 2, 7, z + 2, "WELL_BODY")
    p.box(-2.5, 7, z - 2.5, 2.5, 8, z + 2.5, "WELL_FLANGE")
    p.box(-1.5, 8, z - 1.5, 1.5, 10.5, z + 1.5, "STUFFING")
    p.box(2, 4.3, z - 1, 7, 6.3, z + 1, "PIPE")
    p.box(7, 2, z - 1, 9, 6.3, z + 1, "PIPE")
    p.box(4, 6.3, z - 0.5, 5, 8, z + 0.5, "STEM")
    p.box(3, 8, z - 1.5, 6, 8.6, z + 1.5, "VALVE")
    p.box(-3.2, 5, z - 0.5, -2, 6, z + 0.5, "STEM")
    p.box(-5, 4, z - 1, -3.2, 7, z + 1, "GAUGE")
    # Control cabinet beside the samson post, door outward.
    p.box(6.5, 2, 11.5, 9.5, 3.5, 14.5, "FOOT")
    p.box(6, 3.5, 11, 10, 13.5, 15, "CABINET", xp="DOOR_ON" if running else "DOOR", up="CABINET_TOP", down="CABINET_TOP")
    p.box(7.5, 13.5, 12.5, 8.5, 14.5, 13.5, "LAMP_ON" if running else "LAMP", glow=15 if running else 0)
    return p.elements


def generate(write, asset, pf):
    for running in (False, True):
        write(asset("models", "block", "pumpjack" + ("_on" if running else "") + ".json"), {
            "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"parts": pf("block/pumpjack_parts"), "particle": pf("block/pumpjack_casing")},
            "elements": pumpjack_base(running),
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.3, 0.3, 0.3]}}})
