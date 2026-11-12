"""Texture sheets for the oil machinery: the pumpjack's moving parts and base, the refinery's plant
and its casings. Tiles are (u, v, width, height) in texels; the renderers in
com.powerfactor.client and the block models in gen_data.py address the same rectangles.
"""
import math
from machine_art import *  # noqa: F401,F403 - the toolkit is this module's vocabulary

# ------------------------------------------------------------------ pumpjack: moving parts (entity/pumpjack.png, 128x64)
PUMPJACK = {
    "BEAM_WEB": (0, 0, 32, 4), "BEAM_FLANGE": (0, 5, 32, 5), "LEG": (0, 11, 32, 2), "BRACE": (0, 14, 32, 2),
    "PITMAN_ARM": (0, 17, 16, 2), "EQUALIZER": (0, 20, 24, 2), "ROD": (0, 23, 24, 1), "CABLE": (0, 25, 24, 1),
    "BELT": (0, 27, 48, 2), "HEAD_RIM": (0, 30, 64, 3), "WEIGHT_RIM": (0, 34, 40, 3), "WHEEL_RIM": (0, 38, 32, 2),
    "HEAD_SIDE": (34, 0, 10, 20), "WEIGHT_SIDE": (46, 0, 10, 6), "CRANK_ARM": (58, 0, 4, 14), "BEARING": (64, 0, 8, 6),
    "SHEAVE": (74, 0, 10, 10), "PULLEY": (86, 0, 4, 4), "SHAFT": (92, 0, 4, 4), "FAN": (98, 0, 6, 6),
    "CARRIER": (64, 8, 8, 3), "CLAMP": (64, 12, 4, 3), "CHEEK": (86, 6, 5, 6), "PLATFORM": (64, 16, 16, 8),
}
# The horsehead's arc: radius about the saddle bearing and the angles it spans (PumpjackRenderer.beam).
HEAD_RADIUS, HEAD_TOP, HEAD_BOTTOM, HEAD_BACK = 19.0, 31.0, -27.0, 9.5


def _rows(w, colours):
    img = blank(w, len(colours))
    for y, c in enumerate(colours):
        hline(img, 0, w - 1, y, c)
    return img


def pumpjack_sheet():
    sheet = blank(128, 64)
    t = PUMPJACK

    # Walking beam: the web sits in the shade of the top flange, with stiffener plates along it.
    web = _rows(32, [RED[1], RED[3], RED[3], RED[2]])
    for x in range(3, 32, 7):
        vline(web, x, 0, 3, RED[5])
        vline(web, x + 1, 0, 3, RED[1])
    for x in (0, 31):
        vline(web, x, 0, 3, RED[2])
    paste(sheet, t["BEAM_WEB"], web)
    flange = _rows(32, [RED[5], RED[4], RED[3], RED[4], RED[2]])
    for x in range(2, 32, 7):
        put(flange, x, 1, RED[6])
        put(flange, x, 3, RED[6])
        put(flange, x + 1, 3, RED[1])
    paste(sheet, t["BEAM_FLANGE"], flange)

    leg = _rows(32, [DARK[5], DARK[2]])
    for x in range(5, 32, 8):
        put(leg, x, 0, DARK[6])
        put(leg, x + 1, 1, DARK[0])
    paste(sheet, t["LEG"], leg)
    paste(sheet, t["BRACE"], _rows(32, [IRON[5], IRON[2]]))

    arm = _rows(16, [CHROME[4], CHROME[2]])
    for x in (0, 1, 14, 15):                              # forged eyes at both ends
        put(arm, x, 0, CAST[5])
        put(arm, x, 1, CAST[2])
    paste(sheet, t["PITMAN_ARM"], arm)
    bar = _rows(24, [CAST[5], CAST[2]])
    for x in (0, 1, 22, 23):
        put(bar, x, 0, YELLOW[4])
        put(bar, x, 1, YELLOW[2])
    paste(sheet, t["EQUALIZER"], bar)

    rod = blank(24, 1)
    for x in range(24):
        put(rod, x, 0, CHROME[6] if x % 5 == 1 else CHROME[5] if x % 2 else CHROME[4])
    paste(sheet, t["ROD"], rod)
    cable = blank(24, 1)
    for x in range(24):
        put(cable, x, 0, STEEL[5] if x % 2 else STEEL[2])    # the lay of the rope
    paste(sheet, t["CABLE"], cable)

    belt = blank(48, 2)
    for x in range(48):                                    # cogged V-belt, four texels a tooth
        put(belt, x, 0, [RUBBER[6], RUBBER[4], RUBBER[4], RUBBER[2]][x % 4])
        put(belt, x, 1, [RUBBER[5], RUBBER[3], RUBBER[3], RUBBER[1]][x % 4])
    paste(sheet, t["BELT"], belt)

    # Rim of the horsehead, starting along its top edge: then the arc, where the bridle ropes lie
    # in their grooves, then the underside and the back plate.
    top_edge = math.hypot(HEAD_RADIUS * math.cos(math.radians(HEAD_TOP)) - HEAD_BACK,
                          HEAD_RADIUS * math.sin(math.radians(HEAD_TOP)) - 9.5)
    arc = math.radians(HEAD_TOP - HEAD_BOTTOM) * HEAD_RADIUS
    rim = _rows(64, [RED[5], RED[3], RED[2]])
    for x in range(int(round(top_edge)), int(round(top_edge + arc)) + 1):
        put(rim, x, 0, STEEL[5] if x % 2 else STEEL[3])
        put(rim, x, 1, CAST[1])
        put(rim, x, 2, STEEL[4] if x % 2 else STEEL[2])
    paste(sheet, t["HEAD_RIM"], rim)
    paste(sheet, t["WEIGHT_RIM"], _rows(40, [CAST[5], CAST[3], CAST[1]]))
    wheel_rim = _rows(32, [STEEL[5], STEEL[2]])
    for x in range(0, 32, 4):
        vline(wheel_rim, x, 0, 1, STEEL[0])
    paste(sheet, t["WHEEL_RIM"], wheel_rim)

    # Horsehead side plate, projected flat: z runs left to right, the arc is its right-hand edge.
    w, h = t["HEAD_SIDE"][2:]
    head = blank(w, h, RED[3])
    y_top, y_bottom = HEAD_RADIUS * math.sin(math.radians(HEAD_TOP)), HEAD_RADIUS * math.sin(math.radians(HEAD_BOTTOM))
    z_max = HEAD_RADIUS
    for row in range(h):
        y = y_top - (row + 0.5) * (y_top - y_bottom) / h
        edge = (math.sqrt(HEAD_RADIUS ** 2 - y * y) - HEAD_BACK) / (z_max - HEAD_BACK) * w
        for x in range(w):
            if x + 0.5 > edge:
                put(head, x, row, RED[3])
            elif x + 0.5 > edge - 2.6:                    # warning band following the arc
                put(head, x, row, HAZARD_A if ((x + row) // 2) % 2 == 0 else HAZARD_B)
            elif x + 0.5 > edge - 3.6:
                put(head, x, row, RED[1])
        put(head, 0, row, RED[2])
    hline(head, 0, w - 1, 0, RED[5])
    hline(head, 0, w - 1, 1, RED[4])
    hline(head, 0, w - 1, h - 1, RED[1])
    for row in (4, 9, 14):                                 # bolts onto the beam's end plate
        bolt(head, 1, row, RED)
    rect(head, 2, 6, 4, 7, WHITE[5])                       # unit number stencil
    put(head, 3, 7, WHITE[2])
    paste(sheet, t["HEAD_SIDE"], head)

    weight = blank(10, 6, CAST[3])
    stripes(weight, 0, 0, 9, 1, width=2)
    hline(weight, 0, 9, 2, CAST[5])
    hline(weight, 0, 9, 5, CAST[1])
    bolt(weight, 2, 3, CAST)
    bolt(weight, 6, 3, CAST)
    paste(sheet, t["WEIGHT_SIDE"], weight)

    crank = blank(4, 14)
    for y in range(14):
        for x, c in enumerate((RED[5], RED[3], RED[3], RED[1])):
            put(crank, x, y, c)
    for y in (1, 3, 5, 7):                                 # stroke adjustment holes
        put(crank, 1, y, CAST[0])
        put(crank, 2, y, RED[5])
    rect(crank, 0, 10, 3, 13, CAST[3])                     # hub clamped to the crankshaft
    hline(crank, 0, 3, 10, CAST[5])
    hline(crank, 0, 3, 13, CAST[1])
    paste(sheet, t["CRANK_ARM"], crank)

    bearing = plate(8, 6, CAST, 11, wear=0.2)
    bevel(bearing, 0, 0, 7, 5, CAST)
    rivet(bearing, 1, 1, CAST)
    rivet(bearing, 5, 1, CAST)
    rect(bearing, 3, 3, 4, 4, STEEL[5])
    put(bearing, 4, 4, STEEL[2])
    paste(sheet, t["BEARING"], bearing)

    # Spoked sheave: the gaps between spokes are clear, so the gearbox shows through as it turns.
    sheave = blank(10, 10)
    for y in range(10):
        for x in range(10):
            dx, dy = x + 0.5 - 5, y + 0.5 - 5
            d = math.hypot(dx, dy)
            if d > 5:
                continue
            if d > 3.4:
                put(sheave, x, y, STEEL[5] if dx + dy < -1 else STEEL[2] if dx + dy > 1 else STEEL[4])
            elif d < 1.5:
                put(sheave, x, y, CAST[4] if dx + dy < 0 else CAST[2])
            elif abs(dx) < 1 or abs(dy) < 1:
                put(sheave, x, y, STEEL[3])
    put(sheave, 4, 1, YELLOW[4])                           # timing mark, so its speed reads at a glance
    put(sheave, 5, 1, YELLOW[4])
    paste(sheet, t["SHEAVE"], sheave)

    pulley = blank(4, 4, STEEL[3])
    put(pulley, 0, 0, STEEL[5]); put(pulley, 1, 0, STEEL[5]); put(pulley, 0, 1, STEEL[5])
    put(pulley, 3, 3, STEEL[1]); put(pulley, 2, 3, STEEL[1]); put(pulley, 3, 2, STEEL[1])
    put(pulley, 1, 1, CAST[1]); put(pulley, 2, 2, CAST[1]); put(pulley, 2, 1, YELLOW[4]); put(pulley, 1, 2, CAST[3])
    paste(sheet, t["PULLEY"], pulley)
    shaft = blank(4, 4, CAST[3])
    put(shaft, 0, 0, CAST[5]); put(shaft, 3, 3, CAST[1]); put(shaft, 1, 1, CHROME[5]); put(shaft, 2, 2, CHROME[2])
    put(shaft, 2, 1, CHROME[4]); put(shaft, 1, 2, CHROME[3])
    paste(sheet, t["SHAFT"], shaft)

    fan = blank(6, 6)
    for (x, y, c) in ((2, 0, 5), (3, 0, 4), (3, 1, 4), (5, 2, 5), (5, 3, 4), (4, 3, 4), (3, 5, 5), (2, 5, 4), (2, 4, 4),
                      (0, 3, 5), (0, 2, 4), (1, 2, 4)):
        put(fan, x, y, WHITE[c])
    rect(fan, 2, 2, 3, 3, CAST[3])
    put(fan, 2, 2, CAST[5])
    paste(sheet, t["FAN"], fan)

    carrier = _rows(8, [YELLOW[5], YELLOW[3], YELLOW[1]])
    for x in (0, 7):
        vline(carrier, x, 0, 2, CAST[2])
    rect(carrier, 3, 0, 4, 2, CAST[3])
    paste(sheet, t["CARRIER"], carrier)
    clamp = _rows(4, [CAST[5], CAST[3], CAST[1]])
    put(clamp, 1, 1, STEEL[5])
    paste(sheet, t["CLAMP"], clamp)

    cheek = plate(5, 6, DARK, 12, wear=0.2)
    bevel(cheek, 0, 0, 4, 5, DARK)
    put(cheek, 2, 2, STEEL[5]); put(cheek, 2, 3, STEEL[2])
    paste(sheet, t["CHEEK"], cheek)

    grate = blank(16, 8)
    for y in range(8):
        for x in range(16):
            put(grate, x, y, IRON[4] if (x + y) % 2 == 0 else IRON[1])
    hline(grate, 0, 15, 0, YELLOW[4])
    hline(grate, 0, 15, 7, YELLOW[2])
    vline(grate, 0, 0, 7, YELLOW[4])
    vline(grate, 15, 0, 7, YELLOW[2])
    paste(sheet, t["PLATFORM"], grate)
    return sheet


# ------------------------------------------------------------------ pumpjack: base (block/pumpjack_parts.png, 64x64)
PUMPJACK_BASE = {
    "DECK": (0, 0, 48, 14), "RAIL": (0, 15, 48, 2), "GEAR_SIDE": (0, 18, 12, 11), "GEAR_END": (13, 18, 11, 11),
    "GEAR_TOP": (25, 18, 11, 12), "PEDESTAL": (37, 18, 9, 6), "BOSS": (47, 18, 3, 3), "HATCH": (51, 18, 8, 8),
    "MOTOR_SIDE": (0, 31, 8, 5), "MOTOR_END": (9, 31, 5, 5), "COWL": (15, 31, 5, 5), "TERMINAL": (21, 31, 3, 3),
    "MOTOR_BASE": (25, 31, 8, 6), "WELL_FLANGE": (0, 38, 6, 6), "WELL_BODY": (7, 38, 4, 4), "STUFFING": (12, 38, 3, 3),
    "PIPE": (16, 38, 8, 2), "VALVE": (25, 38, 3, 3), "GAUGE": (29, 38, 3, 3), "STEM": (33, 38, 2, 4),
    "CABINET": (0, 46, 4, 10), "DOOR": (5, 46, 4, 10), "DOOR_ON": (10, 46, 4, 10), "CABINET_TOP": (15, 46, 4, 4),
    "LAMP": (20, 46, 2, 2), "LAMP_ON": (23, 46, 2, 2), "FOOT": (26, 46, 3, 3), "POST": (30, 46, 2, 8),
    "RAIL_TOP": (0, 58, 48, 2),
}


def pumpjack_parts():
    sheet = blank(64, 64)
    t = PUMPJACK_BASE

    # Chequer-plate deck: raised lozenges catching the light, drain slots and a hazard edge at each end.
    deck = blank(48, 14, DARK[3])
    for y in range(14):
        for x in range(48):
            k = (x + 2 * y) % 4
            if k == 0 and y % 2 == 0:
                put(deck, x, y, DARK[5])
            elif k == 1 and y % 2 == 0:
                put(deck, x, y, DARK[1])
    hline(deck, 0, 47, 0, DARK[5])
    hline(deck, 0, 47, 13, DARK[1])
    for x0 in (0, 45):
        stripes(deck, x0, 0, x0 + 2, 13, width=2)
    for x in (12, 24, 36):
        seam(deck, x, 0, x, 13, DARK)
    paste(sheet, t["DECK"], deck)

    rail = _rows(48, [IRON[5], IRON[1]])
    for x in range(4, 48, 6):
        put(rail, x, 0, IRON[6])
        put(rail, x + 1, 1, IRON[0])
    paste(sheet, t["RAIL"], rail)
    rail_top = _rows(48, [IRON[4], IRON[3]])
    for x in range(0, 6):
        put(rail_top, x, 0, HAZARD_A if (x // 2) % 2 == 0 else HAZARD_B)
        put(rail_top, x, 1, HAZARD_B if (x // 2) % 2 == 0 else HAZARD_A)
        put(rail_top, 47 - x, 0, HAZARD_A if (x // 2) % 2 == 0 else HAZARD_B)
        put(rail_top, 47 - x, 1, HAZARD_B if (x // 2) % 2 == 0 else HAZARD_A)
    paste(sheet, t["RAIL_TOP"], rail_top)

    # Gear reducer: a split cast housing in machinery blue, ribbed, with a flanged joint at mid height.
    side = plate(12, 11, BLUE, 21, wear=0.3)
    bevel(side, 0, 0, 11, 10, BLUE)
    hline(side, 1, 10, 5, BLUE[1])                         # housing split
    hline(side, 1, 10, 6, BLUE[5])
    for x in (2, 5, 8):
        put(side, x, 4, STEEL[5]); put(side, x + 1, 5, BLUE[0])
    for x in (3, 8):                                       # cooling ribs on the lower half
        vline(side, x, 7, 9, BLUE[5])
        vline(side, x + 1, 7, 9, BLUE[1])
    paste(sheet, t["GEAR_SIDE"], side)
    end = plate(11, 11, BLUE, 22, wear=0.3)
    bevel(end, 0, 0, 10, 10, BLUE)
    hline(end, 1, 9, 5, BLUE[1])
    hline(end, 1, 9, 6, BLUE[5])
    panel(end, 3, 1, 7, 4, STEEL, 4)                       # maker's plate
    hline(end, 4, 6, 2, STEEL[1])
    hline(end, 4, 5, 3, STEEL[1])
    rect(end, 4, 8, 6, 9, GLASS_DARK)                      # oil sight glass
    put(end, 4, 9, AMBER[2]); put(end, 5, 9, AMBER[2]); put(end, 6, 9, AMBER[1]); put(end, 4, 8, AMBER[3])
    paste(sheet, t["GEAR_END"], end)
    top = plate(11, 12, BLUE, 23, wear=0.3)
    bevel(top, 0, 0, 10, 11, BLUE)
    for (x, y) in ((1, 1), (8, 1), (1, 9), (8, 9)):
        bolt(top, x, y, STEEL)
    paste(sheet, t["GEAR_TOP"], top)
    ped = plate(9, 6, DARK, 24, wear=0.3)
    bevel(ped, 0, 0, 8, 5, DARK)
    vent(ped, 2, 2, 6, 4, DARK)
    paste(sheet, t["PEDESTAL"], ped)
    boss = blank(3, 3, STEEL[3])
    put(boss, 0, 0, STEEL[6]); put(boss, 1, 0, STEEL[5]); put(boss, 0, 1, STEEL[5])
    put(boss, 2, 2, STEEL[0]); put(boss, 1, 2, STEEL[1]); put(boss, 2, 1, STEEL[1]); put(boss, 1, 1, CAST[1])
    paste(sheet, t["BOSS"], boss)
    hatch = plate(8, 8, STEEL, 25, wear=0.3)
    bevel(hatch, 0, 0, 7, 7, STEEL)
    for (x, y) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        rivet(hatch, x, y, STEEL)
    rect(hatch, 3, 3, 4, 4, CAST[2])                       # breather cap
    put(hatch, 3, 3, CAST[5])
    paste(sheet, t["HATCH"], hatch)

    # Motor: finned frame in teal with a terminal box.
    motor = blank(8, 5)
    for y, c in enumerate((TEAL[5], TEAL[2], TEAL[4], TEAL[1], TEAL[3])):
        hline(motor, 0, 7, y, c)
    vline(motor, 0, 0, 4, TEAL[1]); vline(motor, 7, 0, 4, TEAL[1])
    paste(sheet, t["MOTOR_SIDE"], motor)
    bell = blank(5, 5, TEAL[3])
    bevel(bell, 0, 0, 4, 4, TEAL)
    put(bell, 2, 2, CHROME[5]); put(bell, 1, 1, TEAL[5]); put(bell, 3, 3, TEAL[1])
    paste(sheet, t["MOTOR_END"], bell)
    cowl = blank(5, 5, CAST[2])
    bevel(cowl, 0, 0, 4, 4, CAST, 2)
    for y in (1, 3):
        hline(cowl, 1, 3, y, CAST[0])
    hline(cowl, 1, 3, 2, CAST[4])
    paste(sheet, t["COWL"], cowl)
    box = blank(3, 3, TEAL[2])
    bevel(box, 0, 0, 2, 2, TEAL, 2)
    put(box, 1, 1, YELLOW[4])
    paste(sheet, t["TERMINAL"], box)
    base = plate(8, 6, IRON, 26, wear=0.2)
    bevel(base, 0, 0, 7, 5, IRON)
    paste(sheet, t["MOTOR_BASE"], base)

    # Wellhead: bolted flanges, a red casing head, brass stuffing box and the flow line with its valve.
    flange = plate(6, 6, STEEL, 27, wear=0.2)
    bevel(flange, 0, 0, 5, 5, STEEL)
    for (x, y) in ((1, 1), (4, 1), (1, 4), (4, 4)):
        put(flange, x, y, CAST[1])
    paste(sheet, t["WELL_FLANGE"], flange)
    body = blank(4, 4)
    tube_v(body, 0, 0, 3, 3, RED)
    paste(sheet, t["WELL_BODY"], body)
    stuffing = blank(3, 3)
    tube_v(stuffing, 0, 0, 2, 2, BRASS)
    hline(stuffing, 0, 2, 0, BRASS[6])
    paste(sheet, t["STUFFING"], stuffing)
    pipe = blank(8, 2)
    tube_h(pipe, 0, 0, 7, 1, STEEL)
    vline(pipe, 3, 0, 1, STEEL[1])                         # welded joint
    paste(sheet, t["PIPE"], pipe)
    valve = blank(3, 3, RED[3])
    put(valve, 0, 0, RED[5]); put(valve, 2, 2, RED[1]); put(valve, 1, 1, STEEL[5])
    paste(sheet, t["VALVE"], valve)
    dial = blank(3, 3, WHITE[4])
    bevel(dial, 0, 0, 2, 2, CAST, 2)
    put(dial, 1, 1, WHITE[5]); put(dial, 1, 0, RED[3])
    paste(sheet, t["GAUGE"], dial)
    stem = blank(2, 4)
    tube_v(stem, 0, 0, 1, 3, STEEL)
    paste(sheet, t["STEM"], stem)

    # Control cabinet: louvred sides, a door with a status display that lights when the unit runs.
    cab = plate(4, 10, STEEL, 28, grain="v", wear=0.2)
    bevel(cab, 0, 0, 3, 9, STEEL)
    for y in (2, 4, 6):
        hline(cab, 1, 2, y, STEEL[1])
    paste(sheet, t["CABINET"], cab)
    for name, on in (("DOOR", False), ("DOOR_ON", True)):
        door = plate(4, 10, STEEL, 29, grain="v", wear=0.2)
        bevel(door, 0, 0, 3, 9, STEEL)
        rect(door, 1, 1, 2, 3, BLACK)
        put(door, 1, 2, LCD[3] if on else LCD[1]); put(door, 2, 1, LCD[2] if on else LCD[0])
        put(door, 1, 5, FLAME[1] if not on else CAST[1])   # stop / run push-buttons
        put(door, 2, 5, LCD[2] if on else CAST[1])
        put(door, 1, 7, YELLOW[4]); put(door, 2, 7, HAZARD_B)
        vline(door, 3, 4, 6, CAST[1])                      # handle
        paste(sheet, t[name], door)
    roof = blank(4, 4, STEEL[4])
    bevel(roof, 0, 0, 3, 3, STEEL, 4)
    paste(sheet, t["CABINET_TOP"], roof)
    for name, colours in (("LAMP", (AMBER[0], AMBER[1])), ("LAMP_ON", (AMBER[3], AMBER[2]))):
        l = blank(2, 2, colours[1])
        put(l, 0, 0, colours[0] if name == "LAMP_ON" else colours[1])
        put(l, 1, 1, colours[0] if name == "LAMP" else colours[1])
        paste(sheet, t[name], l)
    foot = blank(3, 3, IRON[3])
    bevel(foot, 0, 0, 2, 2, IRON)
    put(foot, 1, 1, STEEL[6])
    paste(sheet, t["FOOT"], foot)
    post = blank(2, 8)
    tube_v(post, 0, 0, 1, 7, IRON)
    paste(sheet, t["POST"], post)
    return sheet


# ------------------------------------------------------------------ refinery plant (entity/refinery.png, 128x128)
REFINERY = {
    "GRATE": (0, 0, 24, 24), "RAIL": (0, 25, 32, 2), "TOE": (0, 28, 32, 2), "POST": (0, 31, 32, 2),
    "LADDER": (0, 34, 48, 1), "RUNG": (0, 36, 8, 1),
    "PIPE_STEEL": (32, 25, 16, 4), "PIPE_HOT": (32, 30, 16, 4), "PIPE_DIESEL": (48, 25, 16, 4), "PIPE_JET": (48, 30, 16, 4),
    "PIPE_CRUDE": (64, 25, 16, 4), "FLANGE": (64, 30, 16, 2), "PIPE_END": (80, 25, 4, 4),
    "DRUM_DIESEL": (24, 0, 32, 24), "DRUM_JET": (56, 0, 32, 24), "DRUM_CRUDE": (88, 0, 32, 24),
    "HEAD": (0, 40, 12, 12), "DOME": (12, 40, 12, 12),
    "HEATER_FRONT": (24, 40, 12, 14), "HEATER_SIDE": (36, 40, 12, 14), "HEATER_TOP": (48, 40, 12, 12), "STACK": (60, 40, 8, 4),
    "FLAME": (68, 40, 8, 16), "FLARE": (76, 40, 8, 16), "GAUGE": (84, 40, 3, 16), "FLUID": (88, 40, 2, 2),
    "PUMP": (92, 40, 8, 6), "MOTOR": (92, 47, 8, 5), "MOTOR_END": (100, 40, 5, 5), "FAN": (106, 40, 12, 12),
    "BUNDLE": (0, 56, 16, 8), "FRAME": (16, 56, 32, 2), "PLENUM": (48, 56, 16, 8), "VALVE": (38, 74, 6, 6),
    "LAMP": (0, 74, 2, 2), "LAMP_ON": (3, 74, 2, 2), "SADDLE": (6, 74, 8, 6), "SKID": (16, 74, 12, 6),
    "PLATE": (30, 74, 6, 4),
}
# Flame strips repeat every 16 texels and are 32 tall, so the renderer can slide a 16-texel window up them.
FLAME_STRIP = (68, 40, 8, 32)
FLARE_STRIP = (76, 40, 8, 32)


def _cols(h, colours):
    img = blank(len(colours), h)
    for x, c in enumerate(colours):
        vline(img, x, 0, h - 1, c)
    return img


def _drum(band, seed):
    """Insulated vessel cladding: aluminium sheets with lap seams, banding straps and a product colour band.
    u runs around the drum (u = 0 faces the front), v along its axis."""
    img = plate(32, 24, INSULATION, seed, wear=0.35)
    for v in (5, 12, 19):                                  # banding straps round the drum
        hline(img, 0, 31, v, INSULATION[1])
        hline(img, 0, 31, v + 1, INSULATION[5])
    for u in (10, 26):                                     # lap seams along it
        vline(img, u, 0, 23, INSULATION[2])
    rect(img, 0, 8, 31, 10, band[3])                       # product colour band
    hline(img, 0, 31, 8, band[5])
    hline(img, 0, 31, 10, band[1])
    for u in range(1, 8):                                  # stencilled tag on the front of the band
        put(img, u, 9, WHITE[6] if u % 2 else band[3])
    return weather(img, seed + 1, 0.6)


def _flame_strip(seed, width, tongues, height=32, period=16):
    """Vertically repeating flame tongues on a clear background (alpha), hottest at their roots."""
    r = random.Random(seed)
    img = blank(width, height)
    for _ in range(tongues):
        x = r.uniform(0.5, width - 0.5)
        root = r.uniform(0, period)
        length = r.uniform(5, 9)
        w = r.uniform(1.0, 2.2)
        for repeat in range(-1, height // period + 2):
            base = root + repeat * period
            for y in range(height):
                t = (base - y) / length                    # 0 at the root, 1 at the tip (flames rise: smaller y)
                if 0 <= t <= 1:
                    half = w * (1 - t) ** 0.7
                    for xx in range(width):
                        if abs(xx + 0.5 - x) <= half:
                            heat = (1 - t) * (1 - abs(xx + 0.5 - x) / (half + 0.01) * 0.6)
                            c = FLAME[min(4, int(heat * 5))]
                            old = img.getpixel((xx, y))
                            if not old[3] or FLAME.index(c) > FLAME.index(old) if old in FLAME else True:
                                put(img, xx, y, c)
    return img


def refinery_sheet():
    sheet = blank(128, 128)
    t = REFINERY

    grate = blank(24, 24)                                  # bar grating: bearing bars with cross rods
    for y in range(24):
        for x in range(24):
            if x % 3 == 0:
                c = IRON[5]
            elif y % 6 == 0:
                c = IRON[3]
            else:
                c = CLEAR if x % 3 == 1 else IRON[1]
            put(grate, x, y, c)
    for x in range(24):
        put(grate, x, 0, YELLOW[4]); put(grate, x, 23, YELLOW[2])
    for y in range(24):
        put(grate, 0, y, YELLOW[4]); put(grate, 23, y, YELLOW[2])
    paste(sheet, t["GRATE"], grate)
    paste(sheet, t["RAIL"], _rows(32, [YELLOW[5], YELLOW[2]]))
    toe = _rows(32, [YELLOW[4], YELLOW[2]])
    for x in range(0, 32, 8):
        put(toe, x, 0, YELLOW[1])
    paste(sheet, t["TOE"], toe)
    paste(sheet, t["POST"], _rows(32, [YELLOW[4], YELLOW[1]]))
    ladder = blank(48, 1)
    for x in range(48):
        put(ladder, x, 0, STEEL[5] if x % 3 else STEEL[3])
    paste(sheet, t["LADDER"], ladder)
    paste(sheet, t["RUNG"], _rows(8, [STEEL[4]]))

    # Pipes: u runs round the pipe, so only u varies; the renderer's normals do the shading.
    for name, mat in (("PIPE_STEEL", STEEL), ("PIPE_DIESEL", YELLOW), ("PIPE_JET", BLUE), ("PIPE_CRUDE", CAST)):
        p = _cols(4, [mat[3]] * 16)
        for u in (3, 11):
            vline(p, u, 0, 3, mat[4])
        vline(p, 7, 0, 3, mat[2])
        paste(sheet, t[name], p)
    hot = _cols(4, [INSULATION[3]] * 16)                   # aluminium-jacketed hot line
    for u in range(0, 16, 4):
        vline(hot, u, 0, 3, INSULATION[5])
    vline(hot, 8, 0, 3, INSULATION[1])
    paste(sheet, t["PIPE_HOT"], hot)
    flange = _cols(2, [CAST[3]] * 16)
    for u in range(1, 16, 4):
        vline(flange, u, 0, 1, STEEL[5])
    paste(sheet, t["FLANGE"], flange)
    end = blank(4, 4, CAST[2])
    rect(end, 1, 1, 2, 2, CAST[0])
    paste(sheet, t["PIPE_END"], end)

    paste(sheet, t["DRUM_DIESEL"], _drum(ORANGE, 61))
    paste(sheet, t["DRUM_JET"], _drum(BLUE, 62))
    paste(sheet, t["DRUM_CRUDE"], _drum(CAST, 63))

    for name, mat in (("HEAD", INSULATION), ("DOME", INSULATION)):
        head = blank(12, 12)
        for y in range(12):
            for x in range(12):
                d = math.hypot(x + 0.5 - 6, y + 0.5 - 6)
                put(head, x, y, mat[5] if d < 2 else mat[4] if d < 4 else mat[3] if d < 5.2 else mat[1])
        for a in range(0, 360, 45):                        # weld ring bolts / cladding segments
            x, y = 6 + math.cos(math.radians(a)) * 4.6, 6 + math.sin(math.radians(a)) * 4.6
            put(head, int(x), int(y), mat[1])
        rect(head, 5, 5, 6, 6, CAST[3])                    # nozzle
        put(head, 5, 5, CAST[5])
        paste(sheet, t[name], head)

    # Fired heater: refractory-lined steel box, three peep windows the flames show through.
    front = plate(12, 14, BRICK, 64, wear=0.4)
    bevel(front, 0, 0, 11, 13, BRICK)
    hline(front, 0, 11, 0, STEEL[4]); hline(front, 0, 11, 13, STEEL[1])
    for x in (1, 5, 9):
        rect(front, x, 6, x + 1, 10, BLACK)                # windows; renderer lays flames over them
        hline(front, x - 1, x + 2, 5, STEEL[4])
        hline(front, x - 1, x + 2, 11, STEEL[2])
    stripes(front, 1, 1, 10, 2, width=2)
    panel(front, 3, 3, 8, 4, STEEL, 4)
    paste(sheet, t["HEATER_FRONT"], front)
    side = plate(12, 14, BRICK, 65, wear=0.4)
    bevel(side, 0, 0, 11, 13, BRICK)
    for y in (4, 9):
        seam(side, 1, y, 10, y, BRICK)
    for (x, y) in ((1, 1), (9, 1), (1, 11), (9, 11)):
        bolt(side, x, y, STEEL)
    paste(sheet, t["HEATER_SIDE"], weather(side, 66, 0.8))
    top = plate(12, 12, STEEL, 67, wear=0.3)
    bevel(top, 0, 0, 11, 11, STEEL)
    disc(top, 6, 6, 3.5, CAST, 2)
    disc(top, 6, 6, 2, CAST, 0, rim=False)
    paste(sheet, t["HEATER_TOP"], top)
    stack = _cols(4, [CAST[3], CAST[4], CAST[3], CAST[2], CAST[3], CAST[4], CAST[3], CAST[2]])
    hline(stack, 0, 7, 0, CAST[1])
    paste(sheet, t["STACK"], stack)

    sheet.paste(_flame_strip(71, 8, 7), FLAME_STRIP[:2])
    flare = _flame_strip(72, 8, 4)
    sheet.paste(flare, FLARE_STRIP[:2])

    gauge = blank(3, 16)
    for y in range(16):
        put(gauge, 0, y, CAST[4]); put(gauge, 1, y, GLASS_DARK); put(gauge, 2, y, CAST[1])
    for y in (0, 15):
        hline(gauge, 0, 2, y, CAST[3])
    put(gauge, 1, 2, hexc("6f8f9a"))                       # glint on the glass
    paste(sheet, t["GAUGE"], gauge)
    paste(sheet, t["FLUID"], blank(2, 2, hexc("ffffff")))

    pump = plate(8, 6, BLUE, 68, wear=0.2)
    bevel(pump, 0, 0, 7, 5, BLUE)
    disc(pump, 4, 3, 2.2, BLUE, 4)
    paste(sheet, t["PUMP"], pump)
    motor = blank(8, 5)
    for y, c in enumerate((TEAL[5], TEAL[2], TEAL[4], TEAL[1], TEAL[3])):
        hline(motor, 0, 7, y, c)
    paste(sheet, t["MOTOR"], motor)
    bell = blank(5, 5, TEAL[3])
    bevel(bell, 0, 0, 4, 4, TEAL)
    put(bell, 2, 2, CHROME[5])
    paste(sheet, t["MOTOR_END"], bell)

    fan = blank(12, 12)                                    # four swept blades on a hub, clear between
    for y in range(12):
        for x in range(12):
            dx, dy = x + 0.5 - 6, y + 0.5 - 6
            d = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) - d * 9) % 90
            if d < 1.6:
                put(fan, x, y, CAST[4] if dx + dy < 0 else CAST[2])
            elif d < 5.8 and a < 34:
                put(fan, x, y, STEEL[5] if a < 12 else STEEL[3])
            elif 5.8 <= d < 6.3:
                put(fan, x, y, CAST[2])
    paste(sheet, t["FAN"], fan)

    bundle = blank(16, 8, CAST[2])                         # finned tubes seen end-on through the side frame
    for y in range(1, 8, 2):
        for x in range(16):
            put(bundle, x, y, COPPER[4] if x % 2 else COPPER[2])
    hline(bundle, 0, 15, 0, STEEL[4]); hline(bundle, 0, 15, 7, STEEL[1])
    paste(sheet, t["BUNDLE"], bundle)
    paste(sheet, t["FRAME"], _rows(32, [DARK[5], DARK[2]]))
    plenum = plate(16, 8, GREEN, 69, wear=0.3)
    bevel(plenum, 0, 0, 15, 7, GREEN)
    for x in (4, 8, 12):
        seam(plenum, x, 1, x, 6, GREEN)
    paste(sheet, t["PLENUM"], plenum)

    valve = blank(6, 6)                                    # handwheel
    for y in range(6):
        for x in range(6):
            d = math.hypot(x + 0.5 - 3, y + 0.5 - 3)
            if 2.1 < d < 3 or abs(x + 0.5 - 3) < 0.6 or abs(y + 0.5 - 3) < 0.6 and d < 3:
                put(valve, x, y, RED[4] if x + y < 5 else RED[2])
    paste(sheet, t["VALVE"], valve)
    paste(sheet, t["LAMP"], blank(2, 2, hexc("5a1410")))
    on = blank(2, 2, hexc("ff3a24"))
    put(on, 0, 0, hexc("ffb09a"))
    paste(sheet, t["LAMP_ON"], on)
    saddle = plate(8, 6, CONCRETE, 70, wear=0.4)
    bevel(saddle, 0, 0, 7, 5, CONCRETE)
    paste(sheet, t["SADDLE"], saddle)
    skid = plate(12, 6, IRON, 71, wear=0.3)
    bevel(skid, 0, 0, 11, 5, IRON)
    stripes(skid, 0, 4, 11, 5, width=2)
    paste(sheet, t["SKID"], skid)
    plate_ = plate(6, 4, STEEL, 72, wear=0.1)
    bevel(plate_, 0, 0, 5, 3, STEEL, 4)
    paste(sheet, t["PLATE"], plate_)
    return sheet
