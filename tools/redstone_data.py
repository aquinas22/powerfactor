"""Data for Project-Red-style redstone: wire, gate and lamp models (multipart), blockstates, loot, recipes, tags, text.
Called from gen_data.py as generate_data(globals())."""

DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
        "green", "red", "black"]
GATES = ["and", "or", "not", "nand", "nor", "xor", "xnor", "buffer", "multiplexer", "pulse", "repeater", "randomizer", "rs_latch",
         "toggle_latch", "timer", "counter"]
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
SIX = ("north", "south", "east", "west", "up", "down")
# Wire cross-section: width and height in pixels.
SIZE = {"alloy": (2, 1), "insulated": (4, 2), "bundled": (6, 3)}
GATE_KEYS = {"and": "minecraft:redstone_torch", "or": "minecraft:redstone", "not": "minecraft:stone_button", "nand": "minecraft:lever",
             "nor": "minecraft:tripwire_hook", "xor": "minecraft:quartz", "xnor": "minecraft:lapis_lazuli", "buffer": "minecraft:glass_pane",
             "multiplexer": "minecraft:hopper", "pulse": "minecraft:string", "repeater": "minecraft:repeater", "randomizer": "minecraft:amethyst_shard",
             "rs_latch": "minecraft:iron_nugget", "toggle_latch": "minecraft:gold_nugget", "timer": "minecraft:clock", "counter": "minecraft:comparator"}
GATE_NAMES = {"and": "AND Gate", "or": "OR Gate", "not": "NOT Gate", "nand": "NAND Gate", "nor": "NOR Gate", "xor": "XOR Gate", "xnor": "XNOR Gate",
              "buffer": "Buffer Gate", "multiplexer": "Multiplexer", "pulse": "Pulse Former", "repeater": "Repeater Gate", "randomizer": "Randomizer",
              "rs_latch": "RS Latch", "toggle_latch": "Toggle Latch", "timer": "Timer", "counter": "Counter"}
GATE_TIPS = {"and": "On when every input it uses is on", "or": "On when any input it uses is on", "not": "On while the back is off",
             "nand": "Off only when every input it uses is on", "nor": "On only when no input it uses is on", "xor": "On when exactly one of left and right is on",
             "xnor": "On when left and right agree", "buffer": "Repeats the back input", "multiplexer": "Back off: passes the right input; back on: the left",
             "pulse": "A short pulse each time the back turns on", "repeater": "Repeats the back after a delay (use it to set the delay)",
             "randomizer": "Flickers at random while the back is on", "rs_latch": "Left sets it on, right resets it off",
             "toggle_latch": "Each pulse on any input flips it", "timer": "Pulses every interval while the back is off (use it to set the interval)",
             "counter": "Left counts up, right down, back resets; on at the target (use it to set the target)"}


def generate_data(g):
    write, asset, data, pf, name, flat_item, shaped, shapeless, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "flat_item", "shaped", "shapeless", "drops_self", "tag", "LANG"))
    T = lambda t: pf("block/redstone/" + t)

    def box(a, b, tex, faces=SIX, glow=0):
        e = {"from": a, "to": b, "faces": {f: {"texture": tex} for f in faces}}
        if glow:
            e["light_emission"] = glow
        return e

    def model(path, textures, elements, render="minecraft:cutout", display=None):
        m = {"parent": "minecraft:block/block", "render_type": render, "textures": textures, "elements": elements}
        if display:
            m["display"] = display
        write(asset("models", "block", f"redstone/{path}.json"), m)

    # ---------------------------------------------------------------- wire
    def wire(block, kind, tex_off, tex_on, item_tex):
        w, h = SIZE[kind]
        c0, c1 = 8 - w / 2, 8 + w / 2
        for lit, tex in (("off", tex_off), ("on", tex_on)):
            t = {"w": tex, "particle": tex}
            glow = 4 if lit == "on" else 0
            model(f"{block}/center_{lit}", t, [box([c0, 0, c0], [c1, h, c1], "#w", glow=glow)])
            model(f"{block}/arm_{lit}", t, [box([c0, 0, 0], [c1, h, c0], "#w", glow=glow)])
            model(f"{block}/riser_{lit}", t, [box([c0, 0, 0], [c1, 16, h], "#w", glow=glow)])
        parts = []
        for lit, val in (("off", "false"), ("on", "true")):
            parts.append({"when": {"powered": val}, "apply": {"model": pf(f"block/redstone/{block}/center_{lit}")}})
            for d, rot in ROT.items():
                parts.append({"when": {d: "side|up", "powered": val}, "apply": {"model": pf(f"block/redstone/{block}/arm_{lit}"), "y": rot}})
                parts.append({"when": {d: "up", "powered": val}, "apply": {"model": pf(f"block/redstone/{block}/riser_{lit}"), "y": rot}})
        write(asset("blockstates", f"{block}.json"), {"multipart": parts})
        write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": item_tex}})
        write(asset("items", f"{block}.json"), {"model": {"type": "minecraft:model", "model": pf(f"item/{block}")}})
        drops_self(block)

    wire("red_alloy_wire", "alloy", T("red_alloy"), T("red_alloy_on"), pf("item/red_alloy_wire"))
    for d in DYES:
        wire(f"{d}_insulated_wire", "insulated", T(f"insulated/{d}"), T(f"insulated/{d}_on"), pf(f"item/insulated/{d}_insulated_wire"))
    wire("bundled_cable", "bundled", T("bundled"), T("bundled"), pf("item/bundled_cable"))
    tag("item", "powerfactor", "insulated_wires", [pf(f"{d}_insulated_wire") for d in DYES])

    # ---------------------------------------------------------------- gates
    t = {"side": T("gate/side"), "lamp": T("gate/lamp"), "lamp_on": T("gate/lamp_on"), "particle": T("gate/side")}
    for lit in ("off", "on"):
        tex = "#lamp_on" if lit == "on" else "#lamp"
        glow = 10 if lit == "on" else 0
        model(f"gate/in_left_{lit}", t, [box([1, 2, 7], [3, 3, 9], tex, glow=glow)])
        model(f"gate/in_back_{lit}", t, [box([7, 2, 13], [9, 3, 15], tex, glow=glow)])
        model(f"gate/in_right_{lit}", t, [box([13, 2, 7], [15, 3, 9], tex, glow=glow)])
        # Output: a little post with a lit cap.
        model(f"gate/out_{lit}", t, [box([7, 2, 1], [9, 5, 3], "#side", faces=("north", "south", "east", "west")),
                                     box([7, 5, 1], [9, 6, 3], tex, glow=glow)])
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
               "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]}}
    for gate in GATES:
        block = f"{gate}_gate"
        model(f"gate/{gate}", {"top": T(f"gate/{gate}"), "side": T("gate/side"), "particle": T(f"gate/{gate}")},
              [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": {"up": {"texture": "#top"}, "down": {"texture": "#side"},
                **{f: {"texture": "#side", "uv": [0, 14, 16, 16]} for f in ("north", "south", "east", "west")}}}], display=display)
        parts = []
        for f, rot in ROT.items():
            parts.append({"when": {"facing": f}, "apply": {"model": pf(f"block/redstone/gate/{gate}"), "y": rot}})
            for ind in ("in_left", "in_back", "in_right", "out"):
                for lit, val in (("off", "false"), ("on", "true")):
                    parts.append({"when": {"facing": f, ind: val}, "apply": {"model": pf(f"block/redstone/gate/{ind}_{lit}"), "y": rot}})
        write(asset("blockstates", f"{block}.json"), {"multipart": parts})
        write(asset("items", f"{block}.json"), {"model": {"type": "minecraft:model", "model": pf(f"block/redstone/gate/{gate}")}})
        drops_self(block)
        shaped(block, [" X ", "WTW", "PPP"], {"X": GATE_KEYS[gate], "W": pf("red_alloy_wire"), "T": "minecraft:redstone_torch",
                                               "P": "minecraft:smooth_stone_slab"})
        name(f"block.powerfactor.{block}", GATE_NAMES[gate])
        LANG[f"tooltip.powerfactor.gate.{gate}"] = GATE_TIPS[gate]

    # ---------------------------------------------------------------- lamps
    for d in DYES:
        for inv in (False, True):
            block = ("inverted_" if inv else "") + f"{d}_illumar_lamp"
            model(f"lamp/{d}", {"all": T(f"lamp/{d}"), "particle": T(f"lamp/{d}")},
                  [box([0, 0, 0], [16, 16, 16], "#all")], render="minecraft:solid")
            model(f"lamp/{d}_on", {"all": T(f"lamp/{d}_on"), "halo": T(f"lamp/{d}_halo"), "particle": T(f"lamp/{d}_on")},
                  [box([0, 0, 0], [16, 16, 16], "#all", glow=15),
                   {"from": [-1, -1, -1], "to": [17, 17, 17], "light_emission": 15,
                    "faces": {f: {"texture": "#halo", "uv": [0, 0, 16, 16]} for f in SIX}}], render="minecraft:translucent")
            write(asset("blockstates", f"{block}.json"), {"variants": {"lit=false": {"model": pf(f"block/redstone/lamp/{d}")},
                                                                       "lit=true": {"model": pf(f"block/redstone/lamp/{d}_on")}}})
            write(asset("items", f"{block}.json"), {"model": {"type": "minecraft:model",
                                                              "model": pf(f"block/redstone/lamp/{d}" + ("_on" if inv else ""))}})
            drops_self(block)
            label = d.replace("_", " ").title()
            name(f"block.powerfactor.{block}", ("Inverted " if inv else "") + f"{label} Illumar Lamp")
        shaped(f"{d}_illumar_lamp", ["PGP", "GDG", "PRP"], {"P": "minecraft:glass_pane", "G": "minecraft:glowstone_dust", "D": f"minecraft:{d}_dye",
                                                             "R": pf("red_alloy_wire")})
        shapeless(f"inverted_{d}_illumar_lamp", [pf(f"{d}_illumar_lamp"), "minecraft:redstone_torch"])
        shaped(f"{d}_insulated_wire", ["WWW", "WDW", "WWW"], {"W": pf("red_alloy_wire"), "D": f"minecraft:{d}_wool"}, count=8)
        name(f"block.powerfactor.{d}_insulated_wire", d.replace("_", " ").title() + " Insulated Wire")

    flat_item("red_alloy_ingot")
    flat_item("screwdriver", "minecraft:item/handheld")
    tag("item", "c", "ingots/red_alloy", [pf("red_alloy_ingot")])
    shapeless("red_alloy_ingot", ["#c:ingots/copper", "minecraft:redstone", "minecraft:redstone", "minecraft:redstone", "minecraft:redstone"])
    shaped("red_alloy_wire", ["AAA"], {"A": "#c:ingots/red_alloy"}, count=12)
    shaped("bundled_cable", ["SIS", "III", "SIS"], {"S": "minecraft:string", "I": "#powerfactor:insulated_wires"}, count=2)
    shaped("screwdriver", ["  I", " I ", "R  "], {"I": "#c:ingots/iron", "R": "#c:ingots/red_alloy"})
    name("item.powerfactor.red_alloy_ingot", "Red Alloy Ingot")
    name("item.powerfactor.screwdriver", "Screwdriver")
    name("block.powerfactor.red_alloy_wire", "Red Alloy Wire")
    name("block.powerfactor.bundled_cable", "Bundled Cable")
    LANG.update({
        "gui.powerfactor.gate.inputs": "Inputs: %s",
        "gui.powerfactor.gate.delay": "Delay: %s s",
        "gui.powerfactor.gate.interval": "Interval: %s s",
        "gui.powerfactor.gate.counter": "Count %s of %s",
        "handbook.powerfactor.redstone.title": "Red Alloy, Gates and Lamps",
        "handbook.powerfactor.redstone.text": (
            "Red Alloy Wire (copper and redstone) carries a signal at full strength along its whole run, however long, climbs "
            "blocks like redstone and powers what it points at and the block under it. Insulated Wire, in sixteen colours, joins "
            "only its own colour and red alloy and doesn't power the block it lies on, so colours can run side by side. Bundled "
            "Cable carries all sixteen colours at once: run insulated wires into it at one end and out of it at the other.\n\n"
            "Gates sit on stone circuit plates: inputs on the left, back and right, output at the front, and a lamp shows each. "
            "AND, OR, NOT, NAND, NOR, XOR, XNOR, Buffer, Multiplexer, Pulse Former, Repeater, Randomizer, RS Latch, Toggle Latch, "
            "Timer and Counter. A Screwdriver turns a gate; sneak-use it to choose which inputs a logic gate reads. Use a Repeater "
            "or Timer bare-handed to step its delay (sneak for back), and a Counter to set its target.\n\n"
            "Illumar Lamps come in sixteen colours and glow with a soft halo when powered; Inverted lamps are lit until powered. "
            "The wireless antennas carry any signal between them on a dye channel, any distance."),
    })
