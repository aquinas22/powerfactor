"""Second texture pass over the machine casings, steam plant, workshop machines and building blocks.

These replace earlier flat versions (a tinted fill, a rectangle outline and a few lines). Each
family now shares one construction language from machine_art: a bevelled frame with corner bolts
around a recessed panel, a family colour, and lights that change when a structure forms or a
machine runs. Run last from gen_textures.py so these win over the earlier phases.
"""
import math
from machine_art import *  # noqa: F401,F403


def casing(mat, seed, inner="plain", lamp=None, lamp_on=False, hazard=False, inner_mat=None):
    """A 16x16 structural casing: raised frame with four corner bolts round a recessed centre panel."""
    img = plate(16, 16, mat, seed, wear=0.4)
    bevel(img, 0, 0, 15, 15, mat)
    im = inner_mat or mat
    img.paste(plate(10, 10, im, seed + 1, tone=2, wear=0.3), (3, 3))
    bevel(img, 2, 2, 13, 13, im, 2, raised=False)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(img, x, y, mat)
    if inner == "vent":
        vent(img, 4, 4, 11, 11, im)
    elif inner == "brace":
        for i in range(10):
            put(img, 3 + i, 3 + i, im[4]); put(img, 4 + i, 3 + i, im[1])
            put(img, 12 - i, 3 + i, im[4]); put(img, 11 - i, 3 + i, im[1])
        rect(img, 7, 7, 8, 8, im[3]); put(img, 7, 7, im[5])
    elif inner == "grid":
        for i in (6, 9):
            hline(img, 3, 12, i, im[1]); vline(img, i, 3, 12, im[1])
            hline(img, 3, 12, i + 1, im[4]); vline(img, i + 1, 3, 12, im[4])
    elif inner == "ribs":
        for x in range(4, 12, 2):
            vline(img, x, 3, 12, im[4]); vline(img, x + 1, 3, 12, im[1])
    if hazard:
        stripes(img, 1, 14, 14, 14, width=2)
    if lamp:
        # A status light bar across the panel: lit when the structure is formed.
        rect(img, 5, 6, 10, 9, BLACK)
        for x in range(6, 10):
            for y in (7, 8):
                put(img, x, y, lamp[2] if lamp_on else lamp[0])
        if lamp_on:
            hline(img, 6, 9, 7, lamp[3])
    return weather(img, seed + 7, 0.5)


TEAL_LIGHT = [hexc("173a33"), hexc("1f8a6a"), hexc("2fd0a8"), hexc("9dffe0")]
GREEN_LIGHT = [hexc("17331f"), hexc("2f8a45"), hexc("55d66f"), hexc("c4ffcf")]
AMBER_LIGHT = [hexc("3a2408"), hexc("9a5a12"), hexc("f0a030"), hexc("ffe08a")]


def control_face(mat, seed, running, frame=0, screen_colours=LCD, accent=AMBER_LIGHT):
    """A controller front: casing frame, an LCD trace, a dial, run/stop buttons."""
    img = plate(16, 16, mat, seed, wear=0.3)
    bevel(img, 0, 0, 15, 15, mat)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(img, x, y, mat)
    panel(img, 2, 2, 13, 13, DARK, 2, raised=False)

    def trace(x, y, f):
        if not running:
            return 1 if y == 3 and x % 3 == 0 else 0           # standby: a dotted baseline
        wave = int(round(2.5 + 1.6 * math.sin((x + f) * 1.05)))
        return 3 if y == wave else 2 if abs(y - wave) == 1 and x % 2 == 0 else 1 if y == 5 else 0
    screen(img, 3, 3, 12, 9, lit=True, colours=screen_colours, pattern=trace, frame=frame)
    lamp(img, 4, 11, GREEN_LIGHT, running)
    lamp(img, 7, 11, accent, not running)
    rect(img, 10, 10, 12, 12, CAST[3])                      # key switch, turned to RUN when running
    bevel(img, 10, 10, 12, 12, CAST, 3)
    put(img, 11 + (1 if running else 0), 11 - (1 if running else 0), STEEL[6])
    put(img, 11, 11, STEEL[4])
    stripes(img, 2, 14, 13, 14, width=2)
    return img


def column(frame=None):
    """Distillation column cladding: aluminium jacket bands, tray flange every four rows, a level trace."""
    img = blank(16, 16)
    for y in range(16):
        for x in range(16):
            # Shaded as a cylinder: the light falls from the upper left across the octagon's faces.
            k = 1 - abs((x + 0.5) / 16 - 0.35) * 1.6
            img.putpixel((x, y), INSULATION[max(1, min(4, int(round(1 + k * 3.4))))])
    for y in range(0, 16, 4):
        hline(img, 0, 15, y, INSULATION[1])               # banding strap
        hline(img, 0, 15, y + 1, INSULATION[5])
    vline(img, 9, 0, 15, INSULATION[2])                    # jacket lap seam
    for y in (2, 10):
        rivet(img, 3, y, INSULATION)
    # Level trace: a teal-banded sight strip whose light runs up the column when it's working.
    for y in range(16):
        put(img, 13, y, CAST[2])
        lit = frame is not None and (y + frame * 2) % 16 < 4
        put(img, 14, y, TEAL_LIGHT[3] if lit else TEAL_LIGHT[1] if frame is not None else TEAL_LIGHT[0])
        put(img, 15, y, CAST[1])
    return img


def body(mat, seed, running, accent):
    """Workshop machine body: painted steel panel with a readout window and a hazard kick plate."""
    img = plate(16, 16, mat, seed, wear=0.4)
    bevel(img, 0, 0, 15, 15, mat)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(img, x, y, mat)
    seam(img, 1, 10, 14, 10, mat)

    heights = (2, 4, 3, 4, 1, 3)

    def bars(x, y, f):
        h = heights[(x + seed) % len(heights)]
        if y >= 4 - h:
            return (3 if y == 4 - h else 2) if running else 1
        return 0
    screen(img, 4, 3, 11, 8, lit=running, colours=[BLACK, accent[1], accent[2], accent[3]], pattern=bars)
    lamp(img, 12, 4, GREEN_LIGHT, running)
    stripes(img, 2, 12, 13, 13, width=2)
    return weather(img, seed + 3, 0.6)


def steam_controller(mat, seed, running, kind):
    img = control_face(mat, seed, running, screen_colours=[BLACK, hexc("1d4a52"), hexc("4fc6d6"), hexc("c9f6ff")])
    if running and kind == "boiler":
        for x in range(4, 12):                             # firebox glow under the panel
            put(img, x, 15, FLAME[2 + (x % 2)])
    return img


def phase_texture_pass(save, anim, write_mcmeta, write_mcmeta_interp):
    # Refinery multiblock.
    save(casing(DARK, 9001, "brace", hazard=True), "block/refinery_casing.png")
    save(casing(DARK, 9001, "brace", lamp=TEAL_LIGHT, lamp_on=True, hazard=True), "block/refinery_casing_formed.png")
    save(control_face(DARK, 9002, False), "block/refinery_controller_front.png")
    save(anim([control_face(DARK, 9002, True, frame=f) for f in range(6)]), "block/refinery_controller_front_on.png")
    write_mcmeta("block/refinery_controller_front_on.png", 5)
    save(column(), "block/refinery_column.png")
    save(anim([column(f) for f in range(8)]), "block/refinery_column_formed.png")
    write_mcmeta_interp("block/refinery_column_formed.png", 4)

    # Pumpjack foundation: a concrete pad with a steel frame; a lamp shows the pumpjack has claimed it.
    for formed in (False, True):
        img = casing(IRON, 9010, "grid", inner_mat=CONCRETE, lamp=GREEN_LIGHT, lamp_on=formed)
        save(img, "block/pumpjack_casing" + ("_formed" if formed else "") + ".png")

    # Steam plant parts.
    save(casing(OLIVE, 9020, "ribs", hazard=True), "block/boiler_casing.png")
    fire = casing(BRICK, 9021, "plain", inner_mat=CAST)
    rect(fire, 4, 6, 11, 11, BLACK)                        # firebox door with a grate
    for x in range(4, 12, 2):
        vline(fire, x, 6, 11, CAST[3])
    hline(fire, 3, 12, 5, STEEL[5]); hline(fire, 3, 12, 12, STEEL[1])
    rect(fire, 11, 8, 12, 9, STEEL[4])                     # latch
    save(fire, "block/boiler_firebox.png")
    drum = blank(16, 16)
    tube_h(drum, 0, 1, 15, 14, ramp("6f8892"))
    hline(drum, 0, 15, 0, CAST[2]); hline(drum, 0, 15, 15, CAST[1])
    for x in (3, 12):
        vline(drum, x, 1, 14, CAST[2])                     # girth welds
        for y in (3, 7, 11):
            put(drum, x, y, STEEL[5])
    gauge(drum, 8, 7, 2.6, needle=0.6)
    save(drum, "block/steam_drum.png")
    save(casing(BLUE, 9023, "vent", hazard=True), "block/turbine_casing.png")
    rotor = casing(STEEL, 9024, "plain", inner_mat=CAST)
    for a in range(0, 360, 30):                            # blade ring seen through the inspection port
        for r in (2.2, 3.4, 4.4):
            put(rotor, int(8 + math.cos(math.radians(a + r * 12)) * r), int(8 + math.sin(math.radians(a + r * 12)) * r),
                CHROME[5] if r < 3 else CHROME[3])
    disc(rotor, 8, 8, 1.6, BRASS, 4)
    save(rotor, "block/steam_rotor.png")
    magnet = casing(COPPER, 9025, "plain", inner_mat=CAST)
    for x in range(4, 12, 2):                              # copper winding over the laminated core
        vline(magnet, x, 3, 12, COPPER[5]); vline(magnet, x + 1, 3, 12, COPPER[2])
    hline(magnet, 3, 12, 7, CAST[1]); hline(magnet, 3, 12, 8, CAST[4])
    save(magnet, "block/generator_magnet.png")
    port = casing(TEAL, 9026, "plain")
    disc(port, 8, 8, 4.5, STEEL, 4)
    disc(port, 8, 8, 3, CAST, 1, rim=False)
    disc(port, 8, 8, 1.5, ramp("8fd0dc"), 4, rim=False)
    for a in range(0, 360, 60):
        put(port, int(8 + math.cos(math.radians(a)) * 3.8), int(8 + math.sin(math.radians(a)) * 3.8), STEEL[1])
    save(port, "block/steam_port.png")
    for name, mat, kind in (("steam_boiler", OLIVE, "boiler"), ("steam_turbine", BLUE, "turbine")):
        save(steam_controller(mat, 9030, False, kind), "block/" + name + "_front.png")
        save(steam_controller(mat, 9030, True, kind), "block/" + name + "_front_on.png")

    # Thermal machines.
    tower = plate(16, 16, ramp("6f8c8c"), 9040, grain="h", wear=0.4)
    bevel(tower, 0, 0, 15, 15, ramp("6f8c8c"))
    for y in range(3, 13, 2):                              # drift eliminator louvres
        hline(tower, 2, 13, y, CAST[1]); hline(tower, 2, 13, y + 1, ramp("6f8c8c")[5])
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(tower, x, y, ramp("6f8c8c"))
    save(weather(tower, 9041, 0.8, ramp("6f8c8c")[0]), "block/cooling_tower_side.png")
    rain = casing(ramp("4f7f8e"), 9042, "plain", inner_mat=ramp("2f5a68"))
    for y in range(4, 12, 3):                              # water level marks
        hline(rain, 4, 6, y, WHITE[4])
    save(rain, "block/rain_collector_side.png")
    for name, hot in (("geothermal_generator", ORANGE), ("thermoelectric_generator", BLUE)):
        save(casing(ramp("566a6c"), 9050, "ribs"), "block/" + name + "_side.png")
        top = casing(STEEL, 9051, "plain", inner_mat=CAST)
        disc(top, 8, 8, 4, STEEL, 4)
        disc(top, 8, 8, 2.5, CAST, 0, rim=False)
        save(top, "block/" + name + "_top.png")
        for running in (False, True):
            face = control_face(ramp("566a6c"), 9052, running, screen_colours=[BLACK, hot[1], hot[4], hot[6]])
            save(face, "block/" + name + "_front" + ("_on" if running else "") + ".png")

    # Workshop machines (press, mixer, centrifuge, sawmill) keep their family colours.
    for name, colour, accent in (("mechanical_press", "8c6a38", AMBER_LIGHT), ("mixer", "3c6a66", TEAL_LIGHT),
                                 ("centrifuge", "4f617d", [BLACK, hexc("244a7a"), hexc("5aa0ff"), hexc("d0e6ff")]),
                                 ("sawmill", "8a623e", AMBER_LIGHT)):
        mat = ramp(colour)
        save(body(mat, 9060 + len(name), False, accent), "block/" + name + "_body.png")
        save(body(mat, 9060 + len(name), True, accent), "block/" + name + "_body_on.png")

    # Building blocks and shared machine materials.
    steel = plate(16, 16, STEEL, 9070, wear=0.5)
    bevel(steel, 0, 0, 15, 15, STEEL)
    for x in (4, 11):
        vline(steel, x, 1, 14, STEEL[1]); vline(steel, x + 1, 1, 14, STEEL[5])
    save(steel, "block/industrial_steel.png")
    riveted = plate(16, 16, STEEL, 9071, wear=0.5)
    bevel(riveted, 0, 0, 15, 15, STEEL)
    seam(riveted, 1, 7, 14, 7, STEEL)
    for x in (2, 6, 10, 13):
        for y in (2, 5, 10, 13):
            if (x, y) not in ((6, 5), (10, 5), (6, 10), (10, 10)):
                rivet(riveted, x, y, STEEL)
    save(weather(riveted, 9072, 0.5), "block/riveted_steel.png")
    hz = plate(16, 16, STEEL, 9073, wear=0.3)
    bevel(hz, 0, 0, 15, 15, STEEL)
    stripes(hz, 2, 2, 13, 13, width=3)
    bevel(hz, 1, 1, 14, 14, STEEL, raised=False)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(hz, x, y, STEEL)
    save(weather(hz, 9074, 0.6), "block/hazard_block.png")
    warn = casing(STEEL, 9075, "plain", inner_mat=YELLOW)
    for row in range(8):                                   # warning triangle with an exclamation mark
        half = row * 0.62
        for x in range(16):
            if abs(x + 0.5 - 8) <= half + 0.5:
                put(warn, x, 4 + row, BLACK if abs(x + 0.5 - 8) > half - 0.5 else YELLOW[4])
    vline(warn, 7, 6, 9, RED[3]); vline(warn, 8, 6, 9, RED[2])
    put(warn, 7, 11, BLACK); put(warn, 8, 11, BLACK)
    save(warn, "block/warning_block.png")
    ws = plate(16, 16, STEEL, 9076, wear=0.5)
    bevel(ws, 0, 0, 15, 15, STEEL)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(ws, x, y, STEEL)
    save(ws, "block/workshop_steel.png")
    dark = plate(16, 16, DARK, 9077, wear=0.5)
    bevel(dark, 0, 0, 15, 15, DARK)
    for (x, y) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        bolt(dark, x, y, DARK)
    save(dark, "block/industrial_dark.png")
    cu = plate(16, 16, COPPER, 9078, wear=0.6)
    bevel(cu, 0, 0, 15, 15, COPPER)
    save(cu, "block/industrial_copper.png")
    coil = blank(16, 16)
    for y in range(16):                                    # wound copper: each turn a lit crest and a shadowed trough
        hline(coil, 0, 15, y, [COPPER[5], COPPER[4], COPPER[2], COPPER[1]][y % 4])
    for x in range(0, 16, 5):
        vline(coil, x, 0, 15, CAST[2])                     # binding tape
    save(coil, "block/industrial_coil.png")
    for on in (False, True):
        lt = plate(16, 16, DARK, 9079, wear=0.2)
        bevel(lt, 0, 0, 15, 15, DARK)
        rect(lt, 2, 2, 13, 13, BLACK)
        lens = GREEN_LIGHT if on else [CAST[0], CAST[1], CAST[2], CAST[3]]
        for y in range(3, 13):
            for x in range(3, 13):
                d = math.hypot(x + 0.5 - 7, y + 0.5 - 7)
                put(lt, x, y, lens[3] if d < 2 and on else lens[2] if d < 4.5 else lens[1])
        for x in range(3, 13, 3):
            vline(lt, x, 3, 12, mix(lens[1], BLACK, 0.3))  # fresnel ribs
        save(lt, "block/industrial_lamp" + ("_on" if on else "") + ".png")
