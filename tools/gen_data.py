"""Generates PowerFactor's JSON: blockstates, models, item definitions, loot tables, recipes, tags,
worldgen and English names. Run: python3 tools/gen_data.py

Everything this writes is generated; edit this script, not the output.
"""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
NS = "powerfactor"
ASSETS = os.path.join(ROOT, "assets", NS)
DATA = os.path.join(ROOT, "data")
LANG = {}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def pf(name):
    return f"{NS}:{name}"


def asset(*parts):
    return os.path.join(ASSETS, *parts)


def data(ns, *parts):
    return os.path.join(DATA, ns, *parts)


def name(key, english):
    LANG[key] = english


# ======================================================================== models and blockstates

def item_def(item, model=None):
    write(asset("items", f"{item}.json"), {"model": {"type": "minecraft:model", "model": model or pf(f"block/{item}")}})


def flat_item(item, parent="minecraft:item/generated"):
    write(asset("models", "item", f"{item}.json"), {"parent": parent, "textures": {"layer0": pf(f"item/{item}")}})
    item_def(item, pf(f"item/{item}"))


def simple_block(block):
    write(asset("models", "block", f"{block}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf(f"block/{block}")}})
    write(asset("blockstates", f"{block}.json"), {"variants": {"": {"model": pf(f"block/{block}")}}})
    item_def(block)


HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}


def machine_states(block, model_off, model_on):
    variants = {}
    for facing, y in HORIZONTAL.items():
        for running in ("false", "true"):
            v = {"model": model_on if running == "true" else model_off}
            if y:
                v["y"] = y
            variants[f"facing={facing},running={running}"] = v
    write(asset("blockstates", f"{block}.json"), {"variants": variants})


def orientable_machine(block, top=None, bottom=None, side=None, front=None, front_on=None):
    tex = lambda t: pf(f"block/{t}")
    top, side = top or f"{block}_top", side or f"{block}_side"
    bottom = bottom or top
    front, front_on = front or f"{block}_front", front_on or f"{block}_front_on"
    for suffix, f in (("", front), ("_on", front_on)):
        write(asset("models", "block", f"{block}{suffix}.json"), {"parent": "minecraft:block/orientable_with_bottom", "textures": {
            "top": tex(top), "bottom": tex(bottom), "side": tex(side), "front": tex(f)}})
    machine_states(block, pf(f"block/{block}"), pf(f"block/{block}_on"))
    item_def(block)


def face(texture, cull=None):
    f = {"texture": texture}
    if cull:
        f["cullface"] = cull
    return f


def coal_turbine():
    """Firebox at the front (north); a 12x12, 3-deep recess at the back where the rotor spins."""
    t = {"front": pf("block/coal_turbine_front"), "side": pf("block/coal_turbine_side"), "top": pf("block/coal_turbine_top"),
         "bottom": pf("block/coal_turbine_bottom"), "inner": pf("block/coal_turbine_back_inner"),
         "frame": pf("block/coal_turbine_frame"), "particle": pf("block/coal_turbine_side")}
    body = {"from": [0, 0, 0], "to": [16, 16, 13], "faces": {
        "north": face("#front", "north"), "east": face("#side", "east"), "west": face("#side", "west"),
        "up": face("#top", "up"), "down": face("#bottom", "down"), "south": face("#inner")}}
    def bar(frm, to):
        return {"from": frm, "to": to, "faces": {d: face("#frame") for d in ("north", "east", "south", "west", "up", "down")}}
    frame = [bar([0, 0, 13], [16, 2, 16]), bar([0, 14, 13], [16, 16, 16]), bar([0, 2, 13], [2, 14, 16]), bar([14, 2, 13], [16, 14, 16])]
    # Outer faces of the frame match the rest of the casing.
    frame[0]["faces"]["down"] = face("#bottom", "down")
    frame[1]["faces"]["up"] = face("#top", "up")
    frame[2]["faces"]["west"] = face("#side", "west")
    frame[3]["faces"]["east"] = face("#side", "east")
    for b in frame:
        b["faces"]["south"] = face("#frame", "south")
    for suffix, front in (("", "coal_turbine_front"), ("_on", "coal_turbine_front_on")):
        textures = dict(t, front=pf(f"block/{front}"))
        write(asset("models", "block", f"coal_turbine{suffix}.json"), {"parent": "minecraft:block/block", "textures": textures,
                                                                       "elements": [body] + frame})
    machine_states("coal_turbine", pf("block/coal_turbine"), pf("block/coal_turbine_on"))
    item_def("coal_turbine")


CAN_X = (3.5, 8.0, 12.5)
CAN_Z = (4.5, 11.5)


def capacitor_bank_model(name, lit, controller=False):
    """
    A pad-mounted capacitor bank: steel base, corner posts and top rails, six cylindrical cans
    (an upright square plus a smaller one turned 45 degrees reads as round), porcelain bushings and
    copper bus bars. The first `lit` cans glow. Faces map pixel-for-pixel onto capacitor_bank_parts.
    """
    els = []
    sides = ("north", "east", "south", "west")

    def box(frm, to, faces, rotation=None):
        e = {"from": frm, "to": to, "faces": faces}
        if rotation:
            e["rotation"] = rotation
        els.append(e)

    frame = lambda cull=None: face("#frame", cull)
    box([0, 0, 0], [16, 2, 16], {"down": frame("down"), "up": frame(), **{d: frame(d) for d in sides}})
    for x0, z0 in ((0, 0), (15, 0), (0, 15), (15, 15)):
        box([x0, 2, z0], [x0 + 1, 15, z0 + 1], {d: frame() for d in sides})
    box([0, 15, 0], [16, 16, 1], {"up": frame("up"), **{d: frame() for d in sides}, "down": frame()})
    box([0, 15, 15], [16, 16, 16], {"up": frame("up"), **{d: frame() for d in sides}, "down": frame()})
    box([0, 15, 1], [1, 16, 15], {"up": frame("up"), **{d: frame() for d in sides}, "down": frame()})
    box([15, 15, 1], [16, 16, 15], {"up": frame("up"), **{d: frame() for d in sides}, "down": frame()})

    def uv_face(uv):
        return {"texture": "#parts", "uv": uv}

    n = 0
    for cz in CAN_Z:
        for cx in CAN_X:
            on = n < lit
            n += 1
            side_uv = [4, 0, 8, 9] if on else [0, 0, 4, 9]
            box([cx - 2, 2, cz - 2], [cx + 2, 11, cz + 2], {**{d: uv_face(side_uv) for d in sides}, "up": uv_face([8, 0, 12, 4])})
            r_uv = [side_uv[0] + 0.3, 0, side_uv[0] + 3.7, 9]
            box([cx - 1.7, 2, cz - 1.7], [cx + 1.7, 11, cz + 1.7], {**{d: uv_face(r_uv) for d in sides}, "up": uv_face([8.3, 0.3, 11.7, 3.7])},
                rotation={"angle": 45, "axis": "y", "origin": [cx, 2, cz]})
            box([cx - 1, 11, cz - 1], [cx + 1, 14, cz + 1], {**{d: uv_face([12, 0, 14, 3]) for d in sides}, "up": uv_face([14, 0, 16, 2])})
        # Bus bar across the row's terminals.
        box([1.5, 14, cz - 0.5], [14.5, 15, cz + 0.5], {"north": uv_face([0, 10, 13, 11]), "south": uv_face([0, 10, 13, 11]),
                                                        "east": uv_face([0, 10, 1, 11]), "west": uv_face([0, 10, 1, 11]),
                                                        "up": uv_face([0, 11, 13, 12]), "down": uv_face([0, 10, 13, 11])})
    # Tie between the two rows, then the feeder down to the base.
    box([7.5, 14, CAN_Z[0] + 0.5], [8.5, 15, CAN_Z[1] - 0.5], {"east": uv_face([0, 10, 6, 11]), "west": uv_face([0, 10, 6, 11]),
                                                              "up": uv_face([0, 11, 1, 6])})
    if controller:
        box([4, 2, 0], [12, 8, 2], {"north": uv_face([8, 4, 16, 10]), "east": frame(), "west": frame(), "up": frame(), "south": frame()})
    write(asset("models", "block", f"{name}.json"), {"parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"parts": pf("block/capacitor_bank_parts"), "frame": pf("block/coal_turbine_frame"),
                     "particle": pf("block/coal_turbine_frame")}, "elements": els})


def capacitor_banks():
    capacitor_bank_model("capacitor_bank", 0)
    capacitor_bank_model("capacitor_bank_on", 6)
    machine_states("capacitor_bank", pf("block/capacitor_bank"), pf("block/capacitor_bank_on"))
    item_def("capacitor_bank", pf("block/capacitor_bank_on"))

    # The automatic bank lights one can per switched-in step.
    for steps in range(7):
        capacitor_bank_model(f"auto_capacitor_bank_{steps}", steps, controller=True)
    variants = {}
    for facing, y in HORIZONTAL.items():
        for running in ("false", "true"):
            for steps in range(7):
                v = {"model": pf(f"block/auto_capacitor_bank_{steps}")}
                if y:
                    v["y"] = y
                variants[f"facing={facing},running={running},steps={steps}"] = v
    write(asset("blockstates", "auto_capacitor_bank.json"), {"variants": variants})
    item_def("auto_capacitor_bank", pf("block/auto_capacitor_bank_2"))


def cable(block):
    """Multipart: a 4px core plus an arm per connected side."""
    tex = pf(f"block/{block}")
    all_faces = lambda: {d: face("#t") for d in ("north", "east", "south", "west", "up", "down")}
    write(asset("models", "block", f"{block}_core.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                                           "elements": [{"from": [6, 6, 6], "to": [10, 10, 10], "faces": all_faces()}]})
    arm_faces = all_faces()
    arm_faces["north"] = face("#t", "north")
    del arm_faces["south"]
    write(asset("models", "block", f"{block}_arm.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                                          "elements": [{"from": [6, 6, 0], "to": [10, 10, 6], "faces": arm_faces}]})
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": pf(f"block/{block}_core")}}]
    for d, rot in rotations.items():
        parts.append({"when": {d: "true"}, "apply": dict({"model": pf(f"block/{block}_arm"), "uvlock": True}, **rot)})
    write(asset("blockstates", f"{block}.json"), {"multipart": parts})
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [6, 6, 0], "to": [10, 10, 16], "faces": all_faces()}],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(block, pf(f"item/{block}"))


def engine(block):
    """Static base plate for facing=up; the plate, bellows and trunk are drawn by EngineRenderer."""
    base = pf(f"block/{block}_base")
    write(asset("models", "block", block + ".json"), {"parent": "minecraft:block/block", "textures": {"base": base, "particle": base},
        "elements": [{"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
            "down": face("#base", "down"), "up": face("#base"), "north": face("#base", "north"), "south": face("#base", "south"),
            "east": face("#base", "east"), "west": face("#base", "west")}}]})
    rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
    write(asset("blockstates", f"{block}.json"), {"variants": {f"facing={d}": dict({"model": pf(f"block/{block}")}, **r) for d, r in rot.items()}})
    # Inventory icon: a still engine with a blue trunk, built from block textures.
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block",
        "textures": {"base": base, "trunk": "minecraft:block/light_blue_terracotta", "particle": base},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {d: face("#base") for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [1, 4, 1], "to": [15, 6, 15], "faces": {d: face("#base") for d in ("north", "east", "south", "west")}},
            {"from": [0, 6, 0], "to": [16, 10, 16], "faces": {d: face("#base") for d in ("north", "east", "south", "west", "up", "down")}},
            {"from": [4, 10, 4], "to": [12, 16, 12], "faces": {d: face("#trunk") for d in ("north", "east", "south", "west", "up")}}],
        "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]}}})
    item_def(block, pf(f"item/{block}"))


def models():
    for b in ("zinc_ore", "deepslate_zinc_ore", "raw_zinc_block", "zinc_block", "brass_block"):
        simple_block(b)
    coal_turbine()
    crusher_model()
    orientable_machine("charger", bottom="charger_side")
    orientable_machine("electric_furnace", bottom="electric_furnace_side")
    orientable_machine("scada_terminal", top="scada_terminal_side")
    capacitor_banks()
    cable("copper_wire")
    cable("fiber_optic_cable")
    engine("redstone_engine")
    engine("stirling_engine")
    for item in ITEMS:
        flat_item(item)
    flat_item("electric_drill", "minecraft:item/handheld")


UPGRADES = ["speed", "efficiency", "capacitor", "vfd", "surge", "capacity"]
ITEMS = [f"{u}_upgrade" for u in UPGRADES] + ["raw_zinc", "zinc_ingot", "zinc_nugget", "brass_ingot", "brass_nugget", "iron_dust", "gold_dust", "copper_dust",
         "zinc_dust", "brass_dust", "coal_dust", "copper_coil", "electric_motor", "brass_gear", "iron_gear", "turbine_blade",
         "capacitor", "optical_fiber", "control_circuit", "drill_head"]

# ======================================================================== loot

def drops_self(block):
    write(data(NS, "loot_table", "blocks", f"{block}.json"), {"type": "minecraft:block", "random_sequence": pf(f"blocks/{block}"),
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": pf(block)}]}]})


def ore_loot(block, drop):
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write(data(NS, "loot_table", "blocks", f"{block}.json"), {"type": "minecraft:block", "random_sequence": pf(f"blocks/{block}"),
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": pf(block), "conditions": [silk]},
            {"type": "minecraft:item", "name": pf(drop), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})


SELF_DROPPING = ["raw_zinc_block", "zinc_block", "brass_block", "coal_turbine", "crusher", "electric_furnace", "charger", "capacitor_bank", "auto_capacitor_bank",
                 "copper_wire", "fiber_optic_cable", "scada_terminal", "redstone_engine", "stirling_engine"]


def loot():
    for b in SELF_DROPPING:
        drops_self(b)
    ore_loot("zinc_ore", "raw_zinc")
    ore_loot("deepslate_zinc_ore", "raw_zinc")

# ======================================================================== recipes

def ing(x):
    """'#c:ingots/iron' for a tag, 'minecraft:x' for an item."""
    return x


def shaped(out, pattern, key, count=1, category="misc", file=None):
    write(data(NS, "recipe", f"{file or out}.json"), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": key, "result": {"id": pf(out) if ":" not in out else out, "count": count}})


def shapeless(out, ingredients, count=1, file=None, category="misc"):
    write(data(NS, "recipe", f"{file or out}.json"), {"type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": ingredients, "result": {"id": pf(out) if ":" not in out else out, "count": count}})


def cooking(out, ingredient, file, xp=0.7):
    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(data(NS, "recipe", f"{file}_{kind}.json"), {"type": f"minecraft:{kind}", "category": "misc", "cookingtime": time,
            "experience": xp, "ingredient": ingredient, "result": {"id": out}})


def crushing(file, ingredient, result, bonus=(), time=None, conditions=None):
    r = {"type": pf("crushing"), "ingredient": ingredient, "result": result}
    if bonus:
        r["bonus"] = list(bonus)
    if time:
        r["time"] = time
    if conditions:
        r["neoforge:conditions"] = conditions
    write(data(NS, "recipe", "crushing", f"{file}.json"), r)


def out(item, count=1, chance=None):
    o = {"item": item if ":" in item else pf(item)}
    if count != 1:
        o["count"] = count
    if chance is not None:
        o["chance"] = chance
    return o


def tag_out(tag, count=1, chance=None):
    o = {"tag": tag}
    if count != 1:
        o["count"] = count
    if chance is not None:
        o["chance"] = chance
    return o


def not_empty(tag):
    return {"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "tag": tag}}


# Other mods' metals: doubled through c: tags, only when some mod provides both the ore and the dust.
FOREIGN_METALS = ["nickel", "osmium", "uranium", "platinum"]


def recipes():
    # ---------------------------------------------------------------- storage blocks and nuggets
    for metal, block in (("zinc", "zinc_block"), ("brass", "brass_block")):
        shaped(block, ["###", "###", "###"], {"#": f"#c:ingots/{metal}"}, category="building")
        shapeless(f"{metal}_ingot", [pf(block)], 9, file=f"{metal}_ingot_from_block")
        shaped(f"{metal}_ingot", ["###", "###", "###"], {"#": f"#c:nuggets/{metal}"}, file=f"{metal}_ingot_from_nuggets")
        shapeless(f"{metal}_nugget", [f"#c:ingots/{metal}"], 9)
    shaped("raw_zinc_block", ["###", "###", "###"], {"#": pf("raw_zinc")}, category="building")
    shapeless("raw_zinc", [pf("raw_zinc_block")], 9, file="raw_zinc_from_block")

    # ---------------------------------------------------------------- smelting
    cooking(pf("zinc_ingot"), "#c:raw_materials/zinc", "zinc_ingot_from_raw")
    cooking(pf("zinc_ingot"), "#c:ores/zinc", "zinc_ingot_from_ore")
    for metal, ingot in (("iron", "minecraft:iron_ingot"), ("gold", "minecraft:gold_ingot"), ("copper", "minecraft:copper_ingot"),
                         ("zinc", pf("zinc_ingot")), ("brass", pf("brass_ingot"))):
        cooking(ingot, f"#c:dusts/{metal}", f"{metal}_ingot_from_dust", xp=0.1)

    # Brass is an alloy: mix dusts, then smelt. Making dust takes a crusher, so brass is mid-game.
    shapeless("brass_dust", ["#c:dusts/copper", "#c:dusts/copper", "#c:dusts/copper", "#c:dusts/zinc"], 4)

    # ---------------------------------------------------------------- components
    shaped("iron_gear", [" I ", "INI", " I "], {"I": "#c:ingots/iron", "N": "#c:nuggets/iron"})
    shaped("brass_gear", [" B ", "BGB", " B "], {"B": "#c:ingots/brass", "G": "#c:gears/iron"})
    shaped("copper_coil", ["WWW", "WIW", "WWW"], {"W": pf("copper_wire"), "I": "#c:ingots/iron"})
    shaped("electric_motor", [" K ", "IGI", "IRI"], {"K": pf("copper_coil"), "I": "#c:ingots/iron", "G": "#c:gears/iron", "R": "#c:dusts/redstone"})
    shaped("turbine_blade", ["  I", " I ", "I  "], {"I": "#c:ingots/iron"}, 2)
    shaped("capacitor", ["ZPZ", "ZPZ", "W W"], {"Z": "#c:ingots/zinc", "P": "minecraft:paper", "W": pf("copper_wire")}, 2)
    shaped("optical_fiber", ["PPP", "QDQ", "PPP"], {"P": "#c:glass_panes", "Q": "#c:gems/quartz", "D": "#c:dusts/glowstone"}, 6)
    shaped("control_circuit", ["WRW", "QBQ", "WRW"], {"W": pf("copper_wire"), "R": "#c:dusts/redstone", "Q": "#c:gems/quartz",
                                                      "B": "#c:ingots/brass"}, 2)
    shaped("drill_head", [" I ", "IDI", "BIB"], {"I": "#c:ingots/iron", "D": "#c:gems/diamond", "B": "#c:ingots/brass"})

    # ---------------------------------------------------------------- blocks
    shapeless("copper_wire", ["#c:wires/copper"], 1, category="redstone")
    shaped("fiber_optic_cable", ["KKK", "FFF", "KKK"], {"K": "minecraft:dried_kelp", "F": pf("optical_fiber")}, 6, category="redstone")
    shaped("coal_turbine", ["IBI", "KFK", "IRI"], {"I": "#c:ingots/iron", "B": pf("turbine_blade"), "K": pf("copper_coil"),
                                                   "F": "minecraft:furnace", "R": "#c:dusts/redstone"}, category="redstone")
    shaped("crusher", ["FFF", "IMI", "IGI"], {"F": "minecraft:flint", "I": "#c:ingots/iron", "M": pf("electric_motor"),
                                              "G": "#c:gears/iron"}, category="redstone")
    shaped("electric_furnace", ["ICI", "KFK", "IRI"], {"I": "#c:ingots/iron", "C": "#c:ingots/copper", "K": pf("copper_coil"),
                                                       "F": "minecraft:furnace", "R": "#c:dusts/redstone"}, category="redstone")
    # Upgrade cards: a control circuit plus whatever the card is about.
    card = {"K": pf("control_circuit")}
    shaped("speed_upgrade", ["RGR", "GKG", "RGR"], {**card, "R": "#c:dusts/redstone", "G": "#c:ingots/gold"})
    shaped("efficiency_upgrade", ["LZL", "ZKZ", "LZL"], {**card, "L": "#c:gems/lapis", "Z": "#c:ingots/zinc"})
    shaped("capacitor_upgrade", [" C ", "CKC", " C "], {**card, "C": pf("capacitor")})
    shaped("vfd_upgrade", ["BQB", "MKM", "BQB"], {**card, "B": "#c:ingots/brass", "Q": "#c:gems/quartz", "M": pf("electric_motor")})
    shaped("surge_upgrade", ["WZW", "ZKZ", "WZW"], {**card, "W": pf("copper_wire"), "Z": "#c:ingots/zinc"})
    shaped("capacity_upgrade", ["ZCZ", "CKC", "ZCZ"], {**card, "Z": "#c:ingots/zinc", "C": "#c:ingots/copper"})
    shaped("charger", ["IKI", "WCW", "IRI"], {"I": "#c:ingots/iron", "K": pf("copper_coil"), "W": pf("copper_wire"),
                                              "C": pf("capacitor"), "R": "#c:dusts/redstone"}, category="redstone")
    shaped("capacitor_bank", ["CCC", "CIC", "IWI"], {"C": pf("capacitor"), "I": "#c:ingots/iron", "W": pf("copper_wire")}, category="redstone")
    # Automatic version: a controller circuit and brass contactors on top of a plain bank.
    shaped("auto_capacitor_bank", ["BKB", "RXR", "BKB"], {"B": "#c:ingots/brass", "K": pf("control_circuit"), "R": "#c:dusts/redstone",
                                                          "X": pf("capacitor_bank")}, category="redstone")
    shaped("scada_terminal", ["IGI", "CDC", "IFI"], {"I": "#c:ingots/iron", "G": "#c:glass_panes", "C": pf("control_circuit"),
                                                     "D": "#c:dusts/glowstone", "F": pf("optical_fiber")}, category="redstone")
    # BuildCraft's engine recipes, near enough: planks/cobble, glass, a gear and a piston.
    shaped("redstone_engine", ["PPP", " G ", "IXI"], {"P": "#minecraft:planks", "G": "#c:glass_blocks", "I": "#c:gears/iron",
                                                      "X": "minecraft:piston"}, category="redstone")
    shaped("stirling_engine", ["CCC", " G ", "IXI"], {"C": "#c:cobblestones", "G": "#c:glass_blocks", "I": "#c:gears/iron",
                                                      "X": "minecraft:piston"}, category="redstone")
    shaped("electric_drill", [" H ", "BMB", "BCB"], {"H": pf("drill_head"), "M": pf("electric_motor"), "B": "#c:ingots/brass",
                                                     "C": pf("capacitor")}, category="equipment")

    # ---------------------------------------------------------------- crushing: ore doubling
    for metal in ("iron", "gold", "copper", "zinc"):
        dust = f"{metal}_dust"
        crushing(f"{metal}_ore", f"#c:ores/{metal}", out(dust, 2), [out(dust, 1, 0.25)])
        crushing(f"raw_{metal}", f"#c:raw_materials/{metal}", out(dust, 2))
        crushing(f"raw_{metal}_block", f"#c:storage_blocks/raw_{metal}", out(dust, 18), time=400)
        crushing(f"{metal}_ingot", f"#c:ingots/{metal}", out(dust))
    crushing("brass_ingot", "#c:ingots/brass", out("brass_dust"))
    for metal in FOREIGN_METALS:
        dusts = f"c:dusts/{metal}"
        crushing(f"{metal}_ore", f"#c:ores/{metal}", tag_out(dusts, 2), [tag_out(dusts, 1, 0.25)],
                 conditions=[not_empty(f"c:ores/{metal}"), not_empty(dusts)])
        crushing(f"raw_{metal}", f"#c:raw_materials/{metal}", tag_out(dusts, 2),
                 conditions=[not_empty(f"c:raw_materials/{metal}"), not_empty(dusts)])
        crushing(f"{metal}_ingot", f"#c:ingots/{metal}", tag_out(dusts),
                 conditions=[not_empty(f"c:ingots/{metal}"), not_empty(dusts)])

    # ---------------------------------------------------------------- crushing: gems and odds
    crushing("coal_ore", "#c:ores/coal", out("minecraft:coal", 2), [out("coal_dust", 1, 0.5)])
    crushing("coal", "minecraft:coal", out("coal_dust"))
    crushing("redstone_ore", "#c:ores/redstone", out("minecraft:redstone", 6), [out("minecraft:redstone", 2, 0.5)])
    crushing("lapis_ore", "#c:ores/lapis", out("minecraft:lapis_lazuli", 8), [out("minecraft:lapis_lazuli", 2, 0.5)])
    crushing("diamond_ore", "#c:ores/diamond", out("minecraft:diamond", 2), time=200)
    crushing("emerald_ore", "#c:ores/emerald", out("minecraft:emerald", 2), time=200)
    crushing("quartz_ore", "#c:ores/quartz", out("minecraft:quartz", 2), [out("minecraft:quartz", 1, 0.5)])
    crushing("stone", "minecraft:stone", out("minecraft:cobblestone"), time=60)
    crushing("cobblestone", "#c:cobblestones/normal", out("minecraft:gravel"), time=80)
    crushing("gravel", "#c:gravels", out("minecraft:sand"), [out("minecraft:flint", 1, 0.1)], time=80)
    crushing("sandstone", "#c:sandstone/uncolored_blocks", out("minecraft:sand", 2), time=60)
    crushing("glass", "#c:glass_blocks/colorless", out("minecraft:sand"), time=60)
    crushing("glowstone", "minecraft:glowstone", out("minecraft:glowstone_dust", 4), time=60)
    crushing("bone", "#c:bones", out("minecraft:bone_meal", 5), time=60)
    crushing("blaze_rod", "#c:rods/blaze", out("minecraft:blaze_powder", 4), [out("minecraft:blaze_powder", 1, 0.5)])
    crushing("wool", "#minecraft:wool", out("minecraft:string", 4), time=60)
    crushing("clay", "minecraft:clay", out("minecraft:clay_ball", 4), time=60)
    crushing("sugar_cane", "minecraft:sugar_cane", out("minecraft:sugar", 2), time=40)

# ======================================================================== tags

TAGS = {}


def tag(registry, ns, path, values):
    """Accumulates; the same tag may be added to from several places. Written by flush_tags()."""
    entries = TAGS.setdefault((registry, ns, path), [])
    for v in values:
        if v not in entries:
            entries.append(v)


def flush_tags():
    for (registry, ns, path), values in TAGS.items():
        write(data(ns, "tags", registry, f"{path}.json"), {"replace": False, "values": values})


def tags():
    ores = [pf("zinc_ore"), pf("deepslate_zinc_ore")]
    storage = {"zinc": pf("zinc_block"), "brass": pf("brass_block"), "raw_zinc": pf("raw_zinc_block")}
    for reg in ("block", "item"):
        tag(reg, "c", "ores/zinc", ores)
        tag(reg, "c", "ores", ["#c:ores/zinc"])
        tag(reg, "c", "ores_in_ground/stone", [pf("zinc_ore")])
        tag(reg, "c", "ores_in_ground/deepslate", [pf("deepslate_zinc_ore")])
        tag(reg, "c", "ore_rates/singular", ores)
        for k, v in storage.items():
            tag(reg, "c", f"storage_blocks/{k}", [v])
        tag(reg, "c", "storage_blocks", [f"#c:storage_blocks/{k}" for k in storage])

    tag("item", "c", "raw_materials/zinc", [pf("raw_zinc")])
    tag("item", "c", "raw_materials", ["#c:raw_materials/zinc"])
    for metal in ("zinc", "brass"):
        tag("item", "c", f"ingots/{metal}", [pf(f"{metal}_ingot")])
        tag("item", "c", f"nuggets/{metal}", [pf(f"{metal}_nugget")])
    tag("item", "c", "ingots", ["#c:ingots/zinc", "#c:ingots/brass"])
    tag("item", "c", "nuggets", ["#c:nuggets/zinc", "#c:nuggets/brass"])
    dusts = ("iron", "gold", "copper", "zinc", "brass", "coal")
    for metal in dusts:
        tag("item", "c", f"dusts/{metal}", [pf(f"{metal}_dust")])
    tag("item", "c", "dusts", [f"#c:dusts/{m}" for m in dusts])
    tag("item", "c", "gears/iron", [pf("iron_gear")])
    tag("item", "c", "gears/brass", [pf("brass_gear")])
    tag("item", "c", "gears", ["#c:gears/iron", "#c:gears/brass"])
    tag("item", "c", "tools/mining_tool", [pf("electric_drill")])
    tag("item", "minecraft", "pickaxes", [pf("electric_drill")])
    tag("item", "minecraft", "shovels", [pf("electric_drill")])
    tag("item", "minecraft", "beacon_payment_items", [pf("zinc_ingot"), pf("brass_ingot")])
    tag("block", "minecraft", "beacon_base_blocks", [pf("zinc_block"), pf("brass_block")])

    tag("item", NS, "turbine_fuels", ["#minecraft:coals", "minecraft:coal_block", pf("coal_dust")])
    write(data("neoforge", "data_maps", "item", "furnace_fuels.json"), {"values": {pf("coal_dust"): {"burn_time": 1600}}})

    pickaxe = ores + list(storage.values()) + [pf(b) for b in ("coal_turbine", "crusher", "electric_furnace", "charger", "capacitor_bank", "auto_capacitor_bank", "copper_wire",
                                                                "scada_terminal", "stirling_engine")]
    tag("block", "minecraft", "mineable/pickaxe", pickaxe)
    tag("block", "minecraft", "mineable/axe", [pf("redstone_engine")])
    tag("block", "minecraft", "needs_stone_tool", ores + [pf("raw_zinc_block"), pf("zinc_block"), pf("brass_block")])

# ======================================================================== worldgen

def worldgen():
    write(data(NS, "worldgen", "configured_feature", "ore_zinc.json"), {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.0, "size": 9, "targets": [
            {"state": {"Name": pf("zinc_ore")}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": pf("deepslate_zinc_ore")}, "target": {"predicate_type": "minecraft:tag_match",
                                                                     "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    write(data(NS, "worldgen", "placed_feature", "ore_zinc.json"), {"feature": pf("ore_zinc"), "placement": [
        {"type": "minecraft:count", "count": 10},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                      "min_inclusive": {"absolute": -32}, "max_inclusive": {"absolute": 80}}},
        {"type": "minecraft:biome"}]})
    write(data(NS, "neoforge", "biome_modifier", "add_zinc_ore.json"), {"type": "neoforge:add_features",
        "biomes": "#minecraft:is_overworld", "features": pf("ore_zinc"), "step": "underground_ores"})

# ======================================================================== English

def lang():
    blocks = {"coal_turbine": "Coal Turbine", "crusher": "Crusher", "charger": "Charger", "electric_furnace": "Electric Furnace", "capacitor_bank": "Capacitor Bank", "auto_capacitor_bank": "Auto Capacitor Bank",
              "copper_wire": "Copper Wire", "fiber_optic_cable": "Fiber Optic Cable", "scada_terminal": "SCADA Terminal",
              "redstone_engine": "Redstone Engine", "stirling_engine": "Stirling Engine", "zinc_ore": "Zinc Ore",
              "deepslate_zinc_ore": "Deepslate Zinc Ore", "raw_zinc_block": "Block of Raw Zinc", "zinc_block": "Block of Zinc",
              "brass_block": "Block of Brass"}
    for k, v in blocks.items():
        name(f"block.{NS}.{k}", v)
    items = {"raw_zinc": "Raw Zinc", "zinc_ingot": "Zinc Ingot", "zinc_nugget": "Zinc Nugget", "brass_ingot": "Brass Ingot",
             "brass_nugget": "Brass Nugget", "iron_dust": "Iron Dust", "gold_dust": "Gold Dust", "copper_dust": "Copper Dust",
             "zinc_dust": "Zinc Dust", "brass_dust": "Brass Dust", "coal_dust": "Coal Dust", "copper_coil": "Copper Coil",
             "electric_motor": "Electric Motor", "brass_gear": "Brass Gear", "iron_gear": "Iron Gear", "turbine_blade": "Turbine Blade",
             "capacitor": "Capacitor", "optical_fiber": "Optical Fiber", "control_circuit": "Control Circuit", "drill_head": "Drill Head", "speed_upgrade": "Speed Upgrade", "efficiency_upgrade": "Efficiency Upgrade",
             "capacitor_upgrade": "Built-in Capacitor Upgrade", "vfd_upgrade": "VFD Upgrade", "surge_upgrade": "Surge Protector Upgrade",
             "capacity_upgrade": "Capacity Upgrade",
             "electric_drill": "Electric Mining Drill"}
    for k, v in items.items():
        name(f"item.{NS}.{k}", v)
    LANG.update({
        "itemGroup.powerfactor": "PowerFactor",
        "gui.powerfactor.turbine.online": "● ONLINE",
        "gui.powerfactor.turbine.syncing": "SYNCING",
        "gui.powerfactor.turbine.coasting": "COASTING",
        "gui.powerfactor.turbine.offline": "OFFLINE",
        "gui.powerfactor.buffer": "%s / %s FE",
        "gui.powerfactor.unit.fe": "FE: Forge Energy (real power)",
        "gui.powerfactor.unit.fe.desc": "The power that does work. Every FE machine runs on it.",
        "gui.powerfactor.unit.ee": "EE: Ender Energy (reactive power)",
        "gui.powerfactor.unit.ee.desc": "Imaginary power motors use for their magnetic fields. It does no work but takes up generator capacity.",
        "gui.powerfactor.unit.ce": "CE: Creative Energy (apparent power)",
        "gui.powerfactor.unit.ce.desc": "Total load on the generator: √(FE² + EE²). Generators are rated in CE.",
        "gui.powerfactor.unit.pf": "PF: Power Factor",
        "gui.powerfactor.unit.pf.desc": "FE ÷ CE. 1.00 means all of the generator's capacity is doing work.",
        "gui.powerfactor.engine.seized": "SEIZED",
        "gui.powerfactor.engine.needs_redstone": "Needs redstone",
        "gui.powerfactor.engine.heat": "Heat: %s%%",
        "gui.powerfactor.engine.heat.desc": "Rises when nothing takes the engine's power. At 100% it seizes until it cools.",
        "gui.powerfactor.scada.title": "SCADA · HMI",
        "gui.powerfactor.scada.live": "LINK OK · %s devices",
        "gui.powerfactor.scada.no_link": "NO LINK",
        "gui.powerfactor.scada.generation": "Generation",
        "gui.powerfactor.scada.load": "Load",
        "gui.powerfactor.scada.device": "Device",
        "gui.powerfactor.scada.on": "ON",
        "gui.powerfactor.scada.off": "OFF",
        "gui.powerfactor.scada.empty": "No devices. Run fiber optic cable to your machines.",
        "gui.powerfactor.scada.connecting": "Connecting…",
        "scada.powerfactor.turbine": "%s Hz · %s fuel · burning %s%%",
        "scada.powerfactor.working": "%s · %s%% · %s%% speed",
        "gui.powerfactor.panel.no_power": "WAITING FOR POWER",
        "gui.powerfactor.panel.heater_running": "HEATING %s%%",
        "gui.powerfactor.panel.heater_off": "ELEMENTS OFF",
        "gui.powerfactor.tab.upgrades": "Upgrades",
        "gui.powerfactor.tab.upgrades.empty": "Install upgrade cards. Shift-click works.",
        "gui.powerfactor.tab.config": "Configuration",
        "gui.powerfactor.config.redstone": "Redstone",
        "gui.powerfactor.config.redstone.low": "Run unless powered",
        "gui.powerfactor.config.redstone.high": "Run only when powered",
        "gui.powerfactor.config.redstone.ignore": "Always run",
        "gui.powerfactor.config.eject": "Outputs",
        "gui.powerfactor.config.sides": "Faces · L / R click",
        "gui.powerfactor.config.mode.none": "Closed",
        "gui.powerfactor.config.mode.input": "Input",
        "gui.powerfactor.config.mode.output": "Output",
        "gui.powerfactor.config.mode.both": "Both",
        "gui.powerfactor.config.face.front": "F",
        "gui.powerfactor.config.face.back": "B",
        "gui.powerfactor.config.face.left": "L",
        "gui.powerfactor.config.face.right": "R",
        "gui.powerfactor.config.face.top": "T",
        "gui.powerfactor.config.face.bottom": "D",
        "gui.powerfactor.config.input.on": "Auto-input: ON",
        "gui.powerfactor.config.input.off": "Auto-input: OFF",
        "gui.powerfactor.config.eject.on": "Auto-eject: ON",
        "gui.powerfactor.config.eject.off": "Auto-eject: OFF",
        "gui.powerfactor.upgrade_summary.speed": "Speed %s",
        "gui.powerfactor.upgrade_summary.efficiency": "FE per job %s",
        "gui.powerfactor.upgrade_summary.capacitor": "Motor EE %s",
        "gui.powerfactor.upgrade_summary.vfd": "VFD fitted",
        "gui.powerfactor.upgrade_summary.surge": "Trips below %s",
        "gui.powerfactor.upgrade_summary.capacity": "Buffer %s",
        "tooltip.powerfactor.upgrade.speed": "+50% speed per card; uses more FE and EE",
        "tooltip.powerfactor.upgrade.efficiency": "-15% FE per job per card",
        "tooltip.powerfactor.upgrade.capacitor": "Cancels 5 EE of the motor's own EE per card",
        "tooltip.powerfactor.upgrade.vfd": "Motor ignores frequency and voltage sag, quarter EE, no idling",
        "tooltip.powerfactor.upgrade.surge": "Rides through dips down to 0.6 pu for 3 s",
        "tooltip.powerfactor.upgrade.capacity": "+100% FE buffer per card",
        "tooltip.powerfactor.upgrade.max": "Stacks to %s in one slot",
        "subtitles.powerfactor.turbine_run": "Turbine whines",
        "subtitles.powerfactor.turbine_breaker_close": "Generator breaker closes",
        "subtitles.powerfactor.turbine_breaker_open": "Generator breaker trips",
        "subtitles.powerfactor.crusher_run": "Crusher grinds",
        "subtitles.powerfactor.crusher_idle": "Motor hums",
        "subtitles.powerfactor.motor_trip": "Motor trips",
        "subtitles.powerfactor.electric_furnace_run": "Electric furnace buzzes",
        "subtitles.powerfactor.charger_run": "Charger whines",
        "subtitles.powerfactor.engine_chuff": "Engine chuffs",
        "subtitles.powerfactor.engine_seize": "Engine seizes",
        "subtitles.powerfactor.capacitor_step_in": "Contactor closes",
        "subtitles.powerfactor.capacitor_step_out": "Contactor opens",
        "subtitles.powerfactor.scada_click": "SCADA click",
        "subtitles.powerfactor.scada_alarm": "SCADA alarm",
        "subtitles.powerfactor.arc_zap": "Arc zaps",
        "scada.powerfactor.no_power": "Waiting for power",
        "scada.powerfactor.idle": "Idle",
        "scada.powerfactor.charger.empty": "Empty",
        "scada.powerfactor.charger.charging": "%s · %s%%",
        "scada.powerfactor.capacitor.in": "Switched in · supplying EE",
        "scada.powerfactor.capacitor.tripped": "OVERVOLTAGE TRIP: waiting to reclose",
        "scada.powerfactor.auto_capacitor": "%s/%s steps · grid PF %s %s",
        "scada.powerfactor.motor.tripped": "UNDERVOLTAGE TRIP at %s pu",
        "scada.powerfactor.motor.idle": "Motor idling unloaded (poor PF)",
        "gui.powerfactor.lag": "lag",
        "gui.powerfactor.lead": "lead",
        "gui.powerfactor.panel.dead": "DEAD BUS",
        "gui.powerfactor.panel.generating": "GENERATING",
        "gui.powerfactor.panel.overloaded": "OVERLOADED: too much EE",
        "gui.powerfactor.panel.breaker_open": "BREAKER OPEN",
        "gui.powerfactor.panel.motor_running": "MOTOR %s%% SPEED",
        "gui.powerfactor.panel.motor_idle": "MOTOR IDLE: POOR PF",
        "gui.powerfactor.panel.motor_tripped": "UNDERVOLTAGE TRIP",
        "gui.powerfactor.panel.motor_off": "MOTOR OFF",
        "gui.powerfactor.autocap.disabled": "DISABLED",
        "gui.powerfactor.autocap.no_grid": "NOT ON A GRID",
        "gui.powerfactor.autocap.tripped": "OVERVOLTAGE TRIP",
        "gui.powerfactor.autocap.auto": "AUTO · target PF 1.0",
        "gui.powerfactor.autocap.steps": "Steps in: %s/%s  (%s EE/t)",
        "gui.powerfactor.autocap.grid_pf": "Grid PF: %s",
        "gui.powerfactor.autocap.target": "Switches a step every 2 s",
        "gui.powerfactor.autocap.next": "Next decision",
        "gui.powerfactor.scada.trend": "60 s trend",
        "scada.powerfactor.capacitor.out": "Switched out",
        "scada.powerfactor.engine": "Heat %s · %s%%",
        "scada.powerfactor.engine.seized": "SEIZED: overheated, cooling down",
        "scada.powerfactor.heat.blue": "blue",
        "scada.powerfactor.heat.green": "green",
        "scada.powerfactor.heat.yellow": "yellow",
        "scada.powerfactor.heat.red": "RED",
        "message.powerfactor.capacitor.on": "Capacitor bank switched in: supplying %s EE/t",
        "message.powerfactor.capacitor.off": "Capacitor bank switched out",
        "tooltip.powerfactor.energy": "%s / %s FE",
        "tooltip.powerfactor.drill.cost": "%s FE per block",
    })
    write(asset("lang", "en_us.json"), dict(sorted(LANG.items())))


# ======================================================================== Phase B: materials, machines, logistics

NEW_METALS = ["tin", "lead", "silver", "aluminum", "steel", "bronze", "electrum"]
MINED = {"tin": "raw_tin", "lead": "raw_lead", "silver": "raw_silver", "bauxite": "bauxite"}
RAW_BLOCK = {"tin": "raw_tin_block", "lead": "raw_lead_block", "silver": "raw_silver_block", "bauxite": "bauxite_block"}
DUST_OF_ORE = {"tin": "tin", "lead": "lead", "silver": "silver", "bauxite": "aluminum"}
PLATE_METALS = ["iron", "copper", "gold", "zinc", "brass"] + NEW_METALS
INGOT_REF = {"iron": "#c:ingots/iron", "copper": "#c:ingots/copper", "gold": "#c:ingots/gold"}
NEW_ITEMS = ["graphite", "graphite_electrode", "silicon", "mica", "latex", "rubber", "tree_tap", "advanced_circuit", "steel_gear",
             "heating_coil", "fine_copper_wire", "fine_silver_wire", "fine_aluminum_wire"]
WORKSHOP_MACHINES = ["mechanical_press", "mixer", "centrifuge", "sawmill"]
RECIPE_MACHINES = ["alloy_furnace", "arc_furnace", "wire_drawer", "rolling_mill", "ore_washer"] + WORKSHOP_MACHINES


def workshop_models():
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to, texture):
        return {"from": frm, "to": to, "faces": {f: {"texture": texture, "uv": [0, 0, 16, 16]} for f in six}}
    for machine in WORKSHOP_MACHINES:
        body = [box([0, 0, 0], [16, 5, 16], "#body")]
        if machine == "mechanical_press":
            body += [box([1, 5, 1], [4, 16, 4], "#steel"), box([12, 5, 1], [15, 16, 4], "#steel"),
                     box([1, 5, 12], [4, 16, 15], "#steel"), box([12, 5, 12], [15, 16, 15], "#steel"),
                     box([0, 15, 0], [16, 18, 16], "#body"), box([4, 5, 4], [12, 6, 12], "#steel")]
        elif machine in ("mixer", "centrifuge"):
            body += [box([0, 5, 0], [16, 11, 2], "#body"), box([0, 5, 14], [16, 11, 16], "#body"),
                     box([0, 5, 2], [2, 11, 14], "#body"), box([14, 5, 2], [16, 11, 14], "#body")]
            if machine == "mixer":
                body += [box([1, 11, 6], [3, 16, 10], "#steel"), box([13, 11, 6], [15, 16, 10], "#steel"),
                         box([1, 15, 6], [15, 18, 10], "#body")]
        else:
            body += [box([0, 5, 0], [6, 11, 16], "#steel"), box([10, 5, 0], [16, 11, 16], "#steel"),
                     box([6, 5, 0], [10, 9, 3], "#body"), box([6, 5, 13], [10, 9, 16], "#body"),
                     box([2, 11, 0], [3, 12, 16], "#body"), box([13, 11, 0], [14, 12, 16], "#body")]
        for suffix in ("", "_on"):
            textures = {"body": pf("block/" + machine + "_body" + suffix), "steel": pf("block/workshop_steel"),
                        "particle": pf("block/" + machine + "_body")}
            write(asset("models", "block", machine + suffix + ".json"), {"parent": "minecraft:block/block", "textures": textures, "elements": body})
        machine_states(machine, pf("block/" + machine), pf("block/" + machine + "_on"))
        item_def(machine)


def phase_b_models():
    for ore in MINED:
        simple_block(f"{ore}_ore")
        simple_block(f"deepslate_{ore}_ore")
        simple_block(RAW_BLOCK[ore])
        flat_item(MINED[ore])
    for m in NEW_METALS:
        simple_block(f"{m}_block")
        for form in ("ingot", "nugget", "dust"):
            flat_item(f"{m}_{form}")
    for m in PLATE_METALS:
        flat_item(f"{m}_plate")
    for i in NEW_ITEMS:
        flat_item(i, "minecraft:item/handheld" if i == "tree_tap" else "minecraft:item/generated")
    for m in RECIPE_MACHINES:
        if m not in WORKSHOP_MACHINES:
            orientable_machine(m, bottom=f"{m}_side")
    workshop_models()
    # Pump: intake grate underneath, no animation.
    write(asset("models", "block", "electric_pump.json"), {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "top": pf("block/electric_pump_top"), "bottom": pf("block/electric_pump_bottom"), "side": pf("block/electric_pump_side"),
        "front": pf("block/electric_pump_front")}})
    machine_states("electric_pump", pf("block/electric_pump"), pf("block/electric_pump"))
    item_def("electric_pump")
    rubber_tree_models()
    tank_model()
    pipe_models("fluid_pipe")
    pipe_models("item_pipe")


def rubber_tree_models():
    for name, side in (("rubber_log", "rubber_log"), ("rubber_log_tapped", "rubber_log_tapped")):
        write(asset("models", "block", f"{name}.json"), {"parent": "minecraft:block/cube_column",
            "textures": {"end": pf("block/rubber_log_top"), "side": pf(f"block/{side}")}})
        write(asset("models", "block", f"{name}_horizontal.json"), {"parent": "minecraft:block/cube_column_horizontal",
            "textures": {"end": pf("block/rubber_log_top"), "side": pf(f"block/{side}")}})
    variants = {}
    for axis in ("x", "y", "z"):
        for natural in ("false", "true"):
            for latex in ("false", "true"):
                base = "rubber_log_tapped" if latex == "true" else "rubber_log"
                v = {"model": pf(f"block/{base}" + ("" if axis == "y" else "_horizontal"))}
                if axis == "x":
                    v.update({"x": 90, "y": 90})
                elif axis == "z":
                    v["x"] = 90
                variants[f"axis={axis},latex={latex},natural={natural}"] = v
    write(asset("blockstates", "rubber_log.json"), {"variants": variants})
    item_def("rubber_log")
    # Already colored, so no tint; cutout for the holes.
    write(asset("models", "block", "rubber_leaves.json"), {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
        "textures": {"all": pf("block/rubber_leaves")}})
    write(asset("blockstates", "rubber_leaves.json"), {"variants": {"": {"model": pf("block/rubber_leaves")}}})
    item_def("rubber_leaves")
    write(asset("models", "block", "rubber_sapling.json"), {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
        "textures": {"cross": pf("block/rubber_sapling")}})
    write(asset("blockstates", "rubber_sapling.json"), {"variants": {"": {"model": pf("block/rubber_sapling")}}})
    write(asset("models", "item", "rubber_sapling.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": pf("block/rubber_sapling")}})
    item_def("rubber_sapling", pf("item/rubber_sapling"))
    simple_block("rubber_planks")


def tank_model():
    """Steel corner posts and caps around a glass shell; the fluid inside is drawn by FluidTankRenderer."""
    els = []
    f = lambda: {d: face("#side") for d in ("north", "east", "south", "west", "up", "down")}
    els.append({"from": [1, 0, 1], "to": [15, 16, 15], "faces": {
        "north": face("#side"), "east": face("#side"), "south": face("#side"), "west": face("#side"),
        "up": face("#top", "up"), "down": face("#top", "down")}})
    write(asset("models", "block", "fluid_tank.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
        "textures": {"side": pf("block/fluid_tank_side"), "top": pf("block/fluid_tank_top"), "particle": pf("block/fluid_tank_side")},
        "elements": els})
    write(asset("blockstates", "fluid_tank.json"), {"variants": {"": {"model": pf("block/fluid_tank")}}})
    item_def("fluid_tank")


def pipe_models(block):
    tex, ext = pf(f"block/{block}"), pf("block/pipe_extract")
    allf = lambda t: {d: face(t) for d in ("north", "east", "south", "west", "up", "down")}
    write(asset("models", "block", f"{block}_core.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": allf("#t")}]})
    arm = allf("#t")
    del arm["south"]
    arm["north"] = face("#t", "north")
    write(asset("models", "block", f"{block}_arm.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": arm}]})
    # Endpoint arms get a flange; extract arms get orange rings.
    flange = {"from": [4, 4, 0], "to": [12, 12, 2], "faces": allf("#t")}
    write(asset("models", "block", f"{block}_insert.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [5, 5, 2], "to": [11, 11, 5], "faces": arm}, flange]})
    write(asset("models", "block", f"{block}_extract.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "e": ext, "particle": tex},
        "elements": [{"from": [5, 5, 2], "to": [11, 11, 5], "faces": arm}, {"from": [3.5, 3.5, 0], "to": [12.5, 12.5, 2.5], "faces": allf("#e")}]})
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": pf(f"block/{block}_core")}}]
    for d, rot in rotations.items():
        for state, model in (("pipe", "arm"), ("insert", "insert"), ("extract", "extract")):
            parts.append({"when": {d: state}, "apply": dict({"model": pf(f"block/{block}_{model}"), "uvlock": True}, **rot)})
    write(asset("blockstates", f"{block}.json"), {"multipart": parts})
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [5, 5, 0], "to": [11, 11, 16], "faces": allf("#t")}],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(block, pf(f"item/{block}"))


def phase_b_loot():
    for ore, raw in MINED.items():
        ore_loot(f"{ore}_ore", raw)
        ore_loot(f"deepslate_{ore}_ore", raw)
        drops_self(RAW_BLOCK[ore])
    for m in NEW_METALS:
        drops_self(f"{m}_block")
    for b in RECIPE_MACHINES + ["electric_pump", "item_pipe", "fluid_pipe", "rubber_planks", "rubber_sapling"]:
        drops_self(b)
    # Logs drop as the plain item; leaves need shears/silk, otherwise saplings and sticks.
    drops_self("rubber_log")
    silk_or_shears = {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}
    write(data(NS, "loot_table", "blocks", "rubber_leaves.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/rubber_leaves"),
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": pf("rubber_leaves"), "conditions": [silk_or_shears]},
            {"type": "minecraft:item", "name": pf("rubber_sapling"), "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": [0.05, 0.0625, 0.083333336, 0.1]}]}]}]}]})
    # The tank keeps its fluid.
    write(data(NS, "loot_table", "blocks", "fluid_tank.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/fluid_tank"),
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": pf("fluid_tank"), "functions": [
                       {"function": "minecraft:copy_components", "source": "block_entity", "include": [pf("fluid")]}]}]}]})


def machine_recipe(kind, file, inputs, results, fluid=None, time=None, conditions=None, tool=None):
    r = {"type": pf(kind), "inputs": [{"ingredient": i[0], "count": i[1]} for i in inputs], "results": results}
    if fluid:
        r["fluid"] = {"ingredient": fluid[0], "amount": fluid[1]}
    if time:
        r["time"] = time
    if conditions:
        r["neoforge:conditions"] = conditions
    if tool:
        r["tool"] = tool
    write(data(NS, "recipe", kind, f"{file}.json"), r)


def phase_b_recipes():
    for m in PLATE_METALS:
        machine_recipe("pressing", f"{m}_plate", [(f"#c:ingots/{m}", 1)], [out(f"{m}_plate")], tool=pf("plate_die"))
    for metal, ingredient in (("iron", "#c:ingots/iron"), ("brass", "#c:ingots/brass"), ("steel", "#c:ingots/steel")):
        machine_recipe("pressing", f"{metal}_gear", [(ingredient, 4)], [out(f"{metal}_gear")], time=160, tool=pf("gear_die"))
    for alloy, components in (("brass", (("copper", 3), ("zinc", 1))), ("bronze", (("copper", 3), ("tin", 1))),
                              ("electrum", (("gold", 1), ("silver", 1)))):
        machine_recipe("mixing", f"{alloy}_dust", [(f"#c:dusts/{m}", n) for m, n in components],
                       [out(f"{alloy}_dust", sum(n for _, n in components))])
    machine_recipe("centrifuging", "clay_from_dirt", [("minecraft:dirt", 4)], [out("minecraft:clay_ball", 1), out("minecraft:sand", 1)])
    machine_recipe("centrifuging", "gravel", [("minecraft:gravel", 4)], [out("minecraft:flint", 1), out("minecraft:sand", 2)])
    for wood in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "crimson", "warped"):
        machine_recipe("sawing", wood, [(f"#minecraft:{wood}_" + ("stems" if wood in ("crimson", "warped") else "logs"), 1)],
                       [out(f"minecraft:{wood}_planks", 6), out("minecraft:stick", 1)])
    machine_recipe("sawing", "rubber", [(pf("rubber_log"), 1)], [out("rubber_planks", 6), out("minecraft:stick", 1)])
    for m in WORKSHOP_MACHINES:
        shaped(m, ["PXP", "GMG", "PKP"], {"P": "#c:plates/steel", "X": "minecraft:piston" if m == "mechanical_press" else
               "minecraft:iron_bars" if m == "centrifuge" else "minecraft:cauldron" if m == "mixer" else "#c:gears/iron",
               "G": "#c:gears/iron", "M": pf("electric_motor"), "K": pf("control_circuit")}, category="redstone")
    for pattern in ("plate", "gear"):
        flat_item(pattern + "_die")
        shaped(pattern + "_die", ["PPP", "PXP", "PPP"], {"P": "#c:plates/steel", "X": "#c:plates/iron" if pattern == "plate" else "#c:gears/iron"}, category="equipment")
        LANG[f"item.{NS}.{pattern}_die"] = pattern.title() + " Press Die"
    LANG.update({"tooltip.powerfactor.press_die": "Fit in the press's purple tool slot; lasts 512 jobs.",
                 "tooltip.powerfactor.press_die.plate": "Press ingots into plates",
                 "tooltip.powerfactor.press_die.gear": "Press four ingots into a gear"})
    for m in NEW_METALS:
        shaped(f"{m}_block", ["###", "###", "###"], {"#": f"#c:ingots/{m}"}, category="building")
        shapeless(f"{m}_ingot", [pf(f"{m}_block")], 9, file=f"{m}_ingot_from_block")
        shaped(f"{m}_ingot", ["###", "###", "###"], {"#": f"#c:nuggets/{m}"}, file=f"{m}_ingot_from_nuggets")
        shapeless(f"{m}_nugget", [f"#c:ingots/{m}"], 9)
        cooking(pf(f"{m}_ingot"), f"#c:dusts/{m}", f"{m}_ingot_from_dust", xp=0.1) if m not in ("aluminum", "steel") else None
    for ore, raw in MINED.items():
        shaped(RAW_BLOCK[ore], ["###", "###", "###"], {"#": pf(raw)}, category="building")
        shapeless(raw, [pf(RAW_BLOCK[ore])], 9, file=f"{raw}_from_block")
        metal = DUST_OF_ORE[ore]
        if ore != "bauxite":
            cooking(pf(f"{metal}_ingot"), f"#c:raw_materials/{metal}", f"{metal}_ingot_from_raw")
            cooking(pf(f"{metal}_ingot"), f"#c:ores/{metal}", f"{metal}_ingot_from_ore")
        # Crusher doubles, washer triples.
        dust = f"{metal}_dust"
        crushing(f"{ore}_ore", f"#c:ores/{ore}", out(dust, 2), [out(dust, 1, 0.25)])
        crushing(f"{raw}", pf(raw) if ore == "bauxite" else f"#c:raw_materials/{metal}", out(dust, 2))
        crushing(f"{RAW_BLOCK[ore]}", pf(RAW_BLOCK[ore]) if ore == "bauxite" else f"#c:storage_blocks/raw_{metal}", out(dust, 18), time=400)
    for m in ["tin", "lead", "silver", "aluminum", "steel", "bronze", "electrum"]:
        crushing(f"{m}_ingot", f"#c:ingots/{m}", out(f"{m}_dust"))

    # Ore washer: raw ore + water -> three dusts, with a chance at a byproduct.
    byproduct = {"iron": "gold_nugget", "gold": "silver_nugget", "copper": "gold_nugget", "zinc": "lead_nugget", "tin": "silver_nugget",
                 "lead": "silver_nugget", "silver": "lead_nugget"}
    for metal, by in byproduct.items():
        by_item = by if ":" in by or by.startswith(("silver", "lead")) else "minecraft:" + by
        machine_recipe("washing", f"raw_{metal}", [(f"#c:raw_materials/{metal}", 1)],
                       [out(f"{metal}_dust", 3), out(by_item, 1, 0.15)], fluid=("minecraft:water", 250))
    machine_recipe("washing", "bauxite", [(pf("bauxite"), 1)], [out("aluminum_dust", 3), out("minecraft:iron_nugget", 2, 0.3)],
                   fluid=("minecraft:water", 250))

    # Alloy furnace (induction): alloys from ingots or dusts.
    alloys = [("brass", [("copper", 3), ("zinc", 1)], 4), ("bronze", [("copper", 3), ("tin", 1)], 4), ("electrum", [("gold", 1), ("silver", 1)], 2)]
    for name, parts, count in alloys:
        for form in ("ingots", "dusts"):
            machine_recipe("alloying", f"{name}_from_{form}", [(f"#c:{form}/{m}", n) for m, n in parts], [out(f"{name}_ingot", count)])
    machine_recipe("alloying", "rubber", [(pf("latex"), 2)], [out("rubber", 3)], time=100)
    machine_recipe("alloying", "graphite_from_coal", [("#c:dusts/coal", 4)], [out("graphite", 1)], time=300)

    # Arc furnace: steel, aluminum, silicon, graphite.
    machine_recipe("arc_smelting", "steel_from_ingot", [("#c:ingots/iron", 1), ("#c:dusts/coal", 1)], [out("steel_ingot")])
    machine_recipe("arc_smelting", "steel_from_dust", [("#c:dusts/iron", 1), ("#c:dusts/coal", 1)], [out("steel_ingot")])
    machine_recipe("arc_smelting", "aluminum", [("#c:dusts/aluminum", 1)], [out("aluminum_ingot")], time=240)
    machine_recipe("arc_smelting", "silicon", [("#c:gems/quartz", 1), ("#c:dusts/coal", 1)], [out("silicon")])
    machine_recipe("arc_smelting", "graphite", [("#c:dusts/coal", 4)], [out("graphite")], time=200)

    # Wire drawer and rolling mill.
    for metal in ("copper", "silver", "aluminum"):
        machine_recipe("wire_drawing", f"fine_{metal}_wire", [(f"#c:ingots/{metal}", 1)], [out(f"fine_{metal}_wire", 3)])
    for m in PLATE_METALS:
        machine_recipe("rolling", f"{m}_plate", [(f"#c:ingots/{m}", 1)], [out(f"{m}_plate")])
        shapeless(f"{m}_plate", [f"#c:ingots/{m}", f"#c:ingots/{m}", "#c:tools/hammers"], file=f"{m}_plate_by_hand")

    cooking(pf("rubber"), pf("latex"), "rubber", xp=0.1)
    crushing("granite", "minecraft:granite", out("minecraft:red_sand"), [out("mica", 1, 0.25)], time=80)
    shapeless("rubber_planks", [pf("rubber_log")], 4, category="building")

    # Tools, parts and machines.
    shaped("tree_tap", [" P ", "PPP", "P  "], {"P": "#minecraft:planks"}, category="equipment")
    shaped("graphite_electrode", ["G", "G", "G"], {"G": pf("graphite")})
    shaped("steel_gear", [" S ", "SGS", " S "], {"S": "#c:ingots/steel", "G": "#c:gears/iron"})
    shaped("heating_coil", ["WWW", "WMW", "WWW"], {"W": pf("fine_copper_wire"), "M": pf("mica")})
    shaped("advanced_circuit", ["WSW", "KRK", "WSW"], {"W": pf("fine_silver_wire"), "S": pf("silicon"), "K": pf("control_circuit"),
                                                       "R": "#c:dusts/redstone"}, 2)
    shaped("alloy_furnace", ["PCP", "HFH", "PKP"], {"P": "#c:plates/iron", "C": pf("copper_coil"), "H": pf("heating_coil"),
                                                    "F": pf("electric_furnace"), "K": pf("control_circuit")}, category="redstone")
    shaped("arc_furnace", ["EGE", "SFS", "SKS"], {"E": pf("graphite_electrode"), "G": "#c:glass_blocks", "S": "#c:plates/iron",
                                                  "F": pf("alloy_furnace"), "K": pf("control_circuit")}, category="redstone")
    shaped("wire_drawer", ["PDP", "GMG", "PKP"], {"P": "#c:plates/iron", "D": "#c:gems/diamond", "G": "#c:gears/iron",
                                                  "M": pf("electric_motor"), "K": pf("control_circuit")}, category="redstone")
    shaped("rolling_mill", ["PSP", "GMG", "PKP"], {"P": "#c:plates/iron", "S": "#c:storage_blocks/iron", "G": "#c:gears/brass",
                                                   "M": pf("electric_motor"), "K": pf("control_circuit")}, category="redstone")
    shaped("ore_washer", ["PBP", "GMG", "PKP"], {"P": "#c:plates/tin", "B": "minecraft:bucket", "G": "#c:gears/iron",
                                                 "M": pf("electric_motor"), "K": pf("control_circuit")}, category="redstone")
    shaped("electric_pump", ["PFP", "GMG", "PBP"], {"P": "#c:plates/iron", "F": pf("fluid_pipe"), "G": "#c:gears/iron",
                                                    "M": pf("electric_motor"), "B": "minecraft:bucket"}, category="redstone")
    shaped("fluid_tank", ["PGP", "G G", "PGP"], {"P": "#c:plates/iron", "G": "#c:glass_blocks"}, category="redstone")
    shaped("fluid_pipe", ["PGP"], {"P": "#c:plates/bronze", "G": "#c:glass_blocks"}, 8, category="redstone")
    shaped("item_pipe", ["PGP"], {"P": "#c:plates/tin", "G": "#c:glass_blocks"}, 8, category="redstone")


def phase_b_tags():
    for reg in ("block", "item"):
        for ore in MINED:
            tag(reg, "c", f"ores/{ore}", [pf(f"{ore}_ore"), pf(f"deepslate_{ore}_ore")])
            tag(reg, "c", "ores_in_ground/stone", [pf(f"{ore}_ore")])
            tag(reg, "c", "ores_in_ground/deepslate", [pf(f"deepslate_{ore}_ore")])
            tag(reg, "c", "ore_rates/singular", [pf(f"{ore}_ore"), pf(f"deepslate_{ore}_ore")])
        tag(reg, "c", "ores", [f"#c:ores/{o}" for o in MINED])
        # Aluminum ore is bauxite.
        tag(reg, "c", "ores/aluminum", ["#c:ores/bauxite"])
        for ore in ("tin", "lead", "silver"):
            tag(reg, "c", f"storage_blocks/raw_{ore}", [pf(RAW_BLOCK[ore])])
        tag(reg, "c", "storage_blocks/bauxite", [pf("bauxite_block")])
        for m in NEW_METALS:
            tag(reg, "c", f"storage_blocks/{m}", [pf(f"{m}_block")])
        tag(reg, "c", "storage_blocks", [f"#c:storage_blocks/{m}" for m in NEW_METALS] + [f"#c:storage_blocks/raw_{o}" for o in ("tin", "lead", "silver")])
        tag(reg, "minecraft", "logs_that_burn", [pf("rubber_log")])
        tag(reg, "minecraft", "logs", [pf("rubber_log")])
        tag(reg, "minecraft", "planks", [pf("rubber_planks")])
        tag(reg, "minecraft", "leaves", [pf("rubber_leaves")])
        tag(reg, "minecraft", "saplings", [pf("rubber_sapling")])
    for ore in ("tin", "lead", "silver"):
        tag("item", "c", f"raw_materials/{ore}", [pf(f"raw_{ore}")])
    tag("item", "c", "raw_materials", [f"#c:raw_materials/{o}" for o in ("tin", "lead", "silver")])
    for m in NEW_METALS:
        for form, suffix in (("ingots", "ingot"), ("nuggets", "nugget"), ("dusts", "dust")):
            tag("item", "c", f"{form}/{m}", [pf(f"{m}_{suffix}")])
        tag("item", "c", "ingots", [f"#c:ingots/{m}"])
        tag("item", "c", "nuggets", [f"#c:nuggets/{m}"])
        tag("item", "c", "dusts", [f"#c:dusts/{m}"])
    for m in PLATE_METALS:
        tag("item", "c", f"plates/{m}", [pf(f"{m}_plate")])
    tag("item", "c", "plates", [f"#c:plates/{m}" for m in PLATE_METALS])
    tag("item", "c", "gears/steel", [pf("steel_gear")])
    tag("item", "c", "gears", ["#c:gears/steel"])
    tag("item", "c", "silicon", [pf("silicon")])
    tag("item", "c", "rubbers", [pf("rubber")])
    tag("item", "c", "gems/graphite", [pf("graphite")])
    tag("item", "c", "wires/copper", [pf("fine_copper_wire")])
    tag("item", "c", "wires/silver", [pf("fine_silver_wire")])
    tag("item", "c", "wires/aluminum", [pf("fine_aluminum_wire")])
    ores = [pf(f"{o}_ore") for o in MINED] + [pf(f"deepslate_{o}_ore") for o in MINED]
    metal_blocks = [pf(f"{m}_block") for m in NEW_METALS] + [pf(RAW_BLOCK[o]) for o in MINED]
    machines = [pf(b) for b in RECIPE_MACHINES + ["electric_pump", "fluid_tank", "item_pipe", "fluid_pipe"]]
    tag("block", "minecraft", "mineable/pickaxe", ores + metal_blocks + machines)
    tag("block", "minecraft", "mineable/axe", [pf("rubber_log"), pf("rubber_planks")])
    tag("block", "minecraft", "mineable/hoe", [pf("rubber_leaves")])
    tag("block", "minecraft", "needs_stone_tool", [pf(f"{o}_ore") for o in ("tin", "lead", "bauxite")]
        + [pf(f"deepslate_{o}_ore") for o in ("tin", "lead", "bauxite")] + metal_blocks)
    tag("block", "minecraft", "needs_iron_tool", [pf("silver_ore"), pf("deepslate_silver_ore")])
    tag("block", "minecraft", "beacon_base_blocks", [pf(f"{m}_block") for m in NEW_METALS])
    tag("item", "minecraft", "beacon_payment_items", [pf(f"{m}_ingot") for m in ("tin", "lead", "silver", "aluminum", "steel", "bronze", "electrum")])
    write(data("neoforge", "data_maps", "item", "furnace_fuels.json"), {"values": {pf("coal_dust"): {"burn_time": 1600},
                                                                                   pf("rubber_log"): {"burn_time": 300},
                                                                                   pf("rubber_planks"): {"burn_time": 300},
                                                                                   pf("rubber_sapling"): {"burn_time": 100}}})


ORE_GEN = {
    # ore: (vein size, veins per chunk, min y, max y, shape)
    "zinc": (9, 10, -32, 80, "trapezoid"),
    "tin": (9, 12, -16, 96, "trapezoid"),
    "lead": (8, 8, -48, 48, "trapezoid"),
    "silver": (6, 5, -64, 32, "uniform"),
    "bauxite": (12, 6, 40, 128, "uniform"),
}


def phase_b_worldgen():
    for ore, (size, count, lo, hi, shape) in ORE_GEN.items():
        write(data(NS, "worldgen", "configured_feature", f"ore_{ore}.json"), {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": 0.0, "size": size, "targets": [
                {"state": {"Name": pf(f"{ore}_ore")}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
                {"state": {"Name": pf(f"deepslate_{ore}_ore")}, "target": {"predicate_type": "minecraft:tag_match",
                                                                           "tag": "minecraft:deepslate_ore_replaceables"}}]}})
        write(data(NS, "worldgen", "placed_feature", f"ore_{ore}.json"), {"feature": pf(f"ore_{ore}"), "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": f"minecraft:{shape}", "min_inclusive": {"absolute": lo},
                                                          "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"}]})
        write(data(NS, "neoforge", "biome_modifier", f"add_{ore}_ore.json"), {"type": "neoforge:add_features",
            "biomes": "#minecraft:is_overworld", "features": pf(f"ore_{ore}"), "step": "underground_ores",
            "neoforge:conditions": [{"type": pf("worldgen_enabled"), "feature": ore}]})
    # Rubber trees: a tall straight trunk, a narrow crown, some logs weeping latex.
    log = lambda latex: {"Name": pf("rubber_log"), "Properties": {"axis": "y", "natural": "true", "latex": latex}}
    write(data(NS, "worldgen", "configured_feature", "rubber_tree.json"), {"type": "minecraft:tree", "config": {
        "ignore_vines": True, "force_dirt": False,
        "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
        "below_trunk_provider": {"type": "minecraft:rule_based_state_provider", "fallback": {"type": "minecraft:simple_state_provider",
                                 "state": {"Name": "minecraft:dirt"}}, "rules": []},
        "trunk_provider": {"type": "minecraft:weighted_state_provider", "entries": [
            {"data": log("false"), "weight": 3}, {"data": log("true"), "weight": 1}]},
        "trunk_placer": {"type": "minecraft:fancy_trunk_placer", "base_height": 6, "height_rand_a": 3, "height_rand_b": 1},
        "foliage_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": pf("rubber_leaves"),
                             "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}},
                    "foliage_placer": {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4},
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "decorators": []}})
    write(data(NS, "worldgen", "placed_feature", "rubber_tree.json"), {"feature": pf("rubber_tree"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": {"Name": pf("rubber_sapling"),
                                                                                                             "Properties": {"stage": "0"}}}}]})
    write(data(NS, "neoforge", "biome_modifier", "add_rubber_trees.json"), {"type": "neoforge:add_features",
        "biomes": {"type": "neoforge:or", "values": ["#c:is_jungle", "#c:is_swamp",
                                                     ["minecraft:forest", "minecraft:birch_forest", "minecraft:river"]]},
        "features": pf("rubber_tree"), "step": "vegetal_decoration",
        "neoforge:conditions": [{"type": pf("worldgen_enabled"), "feature": "rubber_trees"}]})


def phase_b_lang():
    pretty = lambda s: " ".join(w.capitalize() for w in s.split("_"))
    for ore in MINED:
        name(f"block.{NS}.{ore}_ore", f"{pretty(ore)} Ore")
        name(f"block.{NS}.deepslate_{ore}_ore", f"Deepslate {pretty(ore)} Ore")
    name(f"block.{NS}.raw_tin_block", "Block of Raw Tin")
    name(f"block.{NS}.raw_lead_block", "Block of Raw Lead")
    name(f"block.{NS}.raw_silver_block", "Block of Raw Silver")
    name(f"block.{NS}.bauxite_block", "Block of Bauxite")
    for raw in ("raw_tin", "raw_lead", "raw_silver", "bauxite"):
        name(f"item.{NS}.{raw}", pretty(raw))
    for m in NEW_METALS:
        name(f"block.{NS}.{m}_block", f"Block of {pretty(m)}")
        for form in ("ingot", "nugget", "dust"):
            name(f"item.{NS}.{m}_{form}", f"{pretty(m)} {form.capitalize()}")
    for m in PLATE_METALS:
        name(f"item.{NS}.{m}_plate", f"{pretty(m)} Plate")
    for i in NEW_ITEMS:
        name(f"item.{NS}.{i}", pretty(i))
    for b in RECIPE_MACHINES + ["electric_pump", "fluid_tank", "fluid_pipe", "item_pipe", "rubber_log", "rubber_planks", "rubber_leaves",
                                "rubber_sapling"]:
        name(f"block.{NS}.{b}", pretty(b))
    LANG.update({
        "gui.powerfactor.water": "Water: %s / %s mB",
        "message.powerfactor.tank": "%s: %s / %s mB",
        "message.powerfactor.tank.empty": "Empty tank",
        "message.powerfactor.pipe.extract": "%s arm: EXTRACT (pulls into the pipe)",
        "message.powerfactor.pipe.disabled": "%s arm: disabled",
        "message.powerfactor.pipe.enabled": "%s arm: reconnected",
        "direction.powerfactor.north": "North", "direction.powerfactor.south": "South", "direction.powerfactor.east": "East",
        "direction.powerfactor.west": "West", "direction.powerfactor.up": "Top", "direction.powerfactor.down": "Bottom",
    })


def phase_b():
    phase_b_models()
    phase_b_loot()
    phase_b_recipes()
    phase_b_tags()
    phase_b_worldgen()
    phase_b_lang()


# ======================================================================== Phases C-F: SCADA walls, tools, Ender, grid, generators

HANDHELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]}}
SABER_STYLES = ["solid", "plasma", "arc", "rainbow"]
GENERATOR_BLOCKS = ["solar_panel", "wind_turbine", "hydro_turbine", "diesel_generator", "synchronous_condenser", "battery_bank", "power_meter"]
DRILL_MODULES = ["area", "speed", "silk", "fortune", "magnet", "battery"]


def facing_states(block, model):
    write(asset("blockstates", f"{block}.json"), {"variants": {f"facing={f}": dict({"model": model}, **({"y": y} if y else {}))
                                                              for f, y in HORIZONTAL.items()}})


def cube_faces(name, north, south, east, west, up, down):
    write(asset("models", "block", f"{name}.json"), {"parent": "minecraft:block/cube", "textures": {
        "particle": east, "north": north, "south": south, "east": east, "west": west, "up": up, "down": down}})


def arc_saber_models():
    write(asset("models", "item", "arc_saber_hilt.json"), {"parent": "minecraft:item/handheld",
                                                           "textures": {"layer0": pf("item/arc_saber_hilt")}})
    for style in SABER_STYLES:
        tex = pf(f"block/arc_saber_blade_{style}")
        tint = {} if style == "rainbow" else {"tintindex": 0}
        faces = {d: dict({"texture": "#blade", "uv": [5, 0, 11, 16]}, **tint) for d in ("north", "east", "south", "west", "up", "down")}
        # The hilt sprite's emitter is at pixel (8, 7), i.e. model (8, 9); the blade leans 45 degrees up-right from there.
        rot = {"angle": -45, "axis": "z", "origin": [8, 9, 8]}
        elements = [{"from": [7.25, 9, 7.25], "to": [8.75, 31.5, 8.75], "faces": faces, "light_emission": 15, "shade": False, "rotation": rot}]
        if style != "rainbow":
            # Untinted white core in a plus shape: each bar pokes just through two opposite faces of the colored glow.
            core = {d: {"texture": "#core", "uv": [7, 0, 9, 16]} for d in ("north", "east", "south", "west", "up", "down")}
            elements += [{"from": [7.7, 9.2, 7.2], "to": [8.3, 31.3, 8.8], "faces": core, "light_emission": 15, "shade": False, "rotation": rot},
                         {"from": [7.2, 9.2, 7.7], "to": [8.8, 31.3, 8.3], "faces": core, "light_emission": 15, "shade": False, "rotation": rot}]
        write(asset("models", "item", f"arc_saber_blade_{style}.json"), {"textures": {"blade": tex, "core": pf("block/arc_saber_blade_solid"), "particle": tex},
            "gui_light": "front", "display": HANDHELD_DISPLAY, "elements": elements})
    blades = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
              "cases": [{"when": st, "model": {"type": "minecraft:model", "model": pf(f"item/arc_saber_blade_{st}"),
                                               "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
                        for st in SABER_STYLES],
              "fallback": {"type": "minecraft:model", "model": pf("item/arc_saber_blade_solid"),
                           "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
    hilt = {"type": "minecraft:model", "model": pf("item/arc_saber_hilt")}
    write(asset("items", "arc_saber.json"), {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": "lit", "model": {"type": "minecraft:composite", "models": [hilt, blades]}}], "fallback": hilt}})


def pylon_model():
    """A lattice tower two blocks tall with a crossarm and three insulators on top."""
    t = {"lattice": pf("block/hv_pylon"), "arm": pf("block/steel_block"), "ins": "minecraft:block/white_terracotta",
         "particle": pf("block/hv_pylon")}
    lattice = lambda rot: {"from": [2, 0, 8], "to": [14, 30, 8], "rotation": {"angle": rot, "axis": "y", "origin": [8, 0, 8]},
                           "faces": {"north": {"texture": "#lattice", "uv": [2, 0, 14, 16]}, "south": {"texture": "#lattice", "uv": [2, 0, 14, 16]}}}
    six = lambda tex, uv: {d: {"texture": tex, "uv": uv} for d in ("north", "east", "south", "west", "up", "down")}
    els = [lattice(45), lattice(-45), {"from": [-4, 29, 7], "to": [20, 31, 9], "faces": six("#arm", [0, 0, 16, 2])}]
    for x in (-3.5, 7, 17.5):
        els.append({"from": [x, 26, 7], "to": [x + 2, 29, 9], "faces": six("#ins", [0, 0, 2, 3])})
    write(asset("models", "block", "hv_pylon.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                      "textures": t, "elements": els, "ambientocclusion": False})
    write(asset("blockstates", "hv_pylon.json"), {"variants": {"": {"model": pf("block/hv_pylon")}}})
    item_def("hv_pylon")


def phase_cf_models():
    # SCADA display: dark glass; the renderer draws over the front when linked.
    write(asset("models", "block", "scada_display.json"), {"parent": "minecraft:block/orientable", "textures": {
        "top": pf("block/scada_display_side"), "side": pf("block/scada_display_side"), "front": pf("block/scada_display_front")}})
    facing_states("scada_display", pf("block/scada_display"))
    item_def("scada_display")
    # Ender links.
    for b in ("ender_transmitter", "ender_receiver"):
        write(asset("models", "block", f"{b}.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": pf(f"block/{b}_top"), "bottom": pf(f"block/{b}_bottom"), "side": pf(f"block/{b}_side")}})
        machine_states(b, pf(f"block/{b}"), pf(f"block/{b}"))
        item_def(b)
    # Breaker: lever up when closed.
    for closed in ("closed", "open"):
        write(asset("models", "block", f"circuit_breaker_{closed}.json"), {"parent": "minecraft:block/orientable", "textures": {
            "top": pf("block/circuit_breaker_side"), "side": pf("block/circuit_breaker_side"),
            "front": pf(f"block/circuit_breaker_front_{closed}")}})
    write(asset("blockstates", "circuit_breaker.json"), {"variants": {
        f"closed={c},facing={f}": dict({"model": pf(f"block/circuit_breaker_{'closed' if c == 'true' else 'open'}")}, **({"y": y} if y else {}))
        for c in ("true", "false") for f, y in HORIZONTAL.items()}})
    item_def("circuit_breaker", pf("block/circuit_breaker_closed"))
    # Transformer: blue LV secondary on the front, red HV primary on the back.
    cube_faces("transformer", pf("block/transformer_secondary"), pf("block/transformer_primary"), pf("block/transformer_side"),
               pf("block/transformer_side"), pf("block/transformer_top"), pf("block/transformer_side"))
    machine_states("transformer", pf("block/transformer"), pf("block/transformer"))
    item_def("transformer")
    pylon_model()
    # Cables.
    cable("insulated_copper_cable")
    cable("aluminum_cable", thick=6)
    # Generators.
    write(asset("models", "block", "solar_panel.json"), {"parent": "minecraft:block/block", "textures": {
        "top": pf("block/solar_panel_top"), "side": pf("block/solar_panel_side"), "particle": pf("block/solar_panel_side")},
        "elements": [{"from": [0, 0, 0], "to": [16, 6, 16], "faces": {"up": face("#top"), "down": face("#side", "down"),
            "north": face("#side", "north"), "south": face("#side", "south"), "east": face("#side", "east"), "west": face("#side", "west")}}]})
    machine_states("solar_panel", pf("block/solar_panel"), pf("block/solar_panel"))
    item_def("solar_panel")
    wind_turbine_models()
    for b, front, side, top in (("hydro_turbine", "hydro_turbine_front", "hydro_turbine_side", "hydro_turbine_top"),
                                ("synchronous_condenser", "synchronous_condenser_end", "synchronous_condenser_side", "synchronous_condenser_side"),
                                ("power_meter", "power_meter_front", "power_meter_side", "power_meter_side")):
        write(asset("models", "block", f"{b}.json"), {"parent": "minecraft:block/orientable", "textures": {
            "top": pf(f"block/{top}"), "side": pf(f"block/{side}"), "front": pf(f"block/{front}")}})
        machine_states(b, pf(f"block/{b}"), pf(f"block/{b}"))
        item_def(b)
    orientable_machine("diesel_generator", bottom="diesel_generator_side")
    orientable_machine("battery_bank", top="battery_bank_top", bottom="battery_bank_top")
    # Items.
    arc_saber_models()
    flat_item("arc_gun", "minecraft:item/handheld")
    flat_item("conductor_spool")
    for m in DRILL_MODULES:
        flat_item(f"drill_{m}_module")


def cable(block, thick=4):  # noqa: F811 - overrides the earlier copper-only version with a thickness
    lo, hi = 8 - thick / 2, 8 + thick / 2
    tex = pf(f"block/{block}")
    all_faces = lambda: {d: face("#t") for d in ("north", "east", "south", "west", "up", "down")}
    write(asset("models", "block", f"{block}_core.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                                           "elements": [{"from": [lo, lo, lo], "to": [hi, hi, hi], "faces": all_faces()}]})
    arm_faces = all_faces()
    arm_faces["north"] = face("#t", "north")
    del arm_faces["south"]
    write(asset("models", "block", f"{block}_arm.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                                                          "elements": [{"from": [lo, lo, 0], "to": [hi, hi, lo], "faces": arm_faces}]})
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": pf(f"block/{block}_core")}}]
    for d, rot in rotations.items():
        parts.append({"when": {d: "true"}, "apply": dict({"model": pf(f"block/{block}_arm"), "uvlock": True}, **rot)})
    write(asset("blockstates", f"{block}.json"), {"multipart": parts})
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
        "elements": [{"from": [lo, lo, 0], "to": [hi, hi, 16], "faces": all_faces()}],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(block, pf(f"item/{block}"))


def phase_cf_data():
    new_blocks = ["scada_display", "ender_transmitter", "ender_receiver", "circuit_breaker", "transformer", "hv_pylon",
                  "insulated_copper_cable", "aluminum_cable", "wind_turbine_tower"] + GENERATOR_BLOCKS
    for b in new_blocks:
        drops_self(b)
    tag("block", "minecraft", "mineable/pickaxe", [pf(b) for b in new_blocks])
    card = {"P": "#c:plates/steel", "K": pf("advanced_circuit")}
    recipes = [
        ("scada_display", ["GGG", "GCG", "IFI"], {"G": "#c:glass_panes", "C": pf("control_circuit"), "I": "#c:ingots/iron", "F": pf("optical_fiber")}, 4),
        ("ender_transmitter", ["EPE", "OKO", "OCO"], {"E": "minecraft:ender_pearl", "P": "minecraft:ender_eye", "O": "minecraft:obsidian",
                                                      "K": pf("advanced_circuit"), "C": pf("copper_coil")}, 1),
        ("ender_receiver", ["OPO", "EKE", "OCO"], {"E": "minecraft:ender_pearl", "P": "minecraft:ender_eye", "O": "minecraft:obsidian",
                                                   "K": pf("advanced_circuit"), "C": pf("copper_coil")}, 1),
        ("insulated_copper_cable", ["RRR", "WWW", "RRR"], {"R": "#c:rubbers", "W": pf("copper_wire")}, 6),
        ("aluminum_cable", ["RRR", "AAA", "RRR"], {"R": "#c:rubbers", "A": pf("fine_aluminum_wire")}, 6),
        ("circuit_breaker", ["PKP", "CLC", "PWP"], {"P": "#c:plates/iron", "K": pf("control_circuit"), "C": pf("copper_coil"),
                                                   "L": "minecraft:lever", "W": pf("copper_wire")}, 1),
        ("transformer", ["PKP", "SIS", "PKP"], {"P": "#c:plates/steel", "K": pf("copper_coil"), "S": pf("silicon"), "I": "#c:storage_blocks/iron"}, 1),
        ("hv_pylon", ["S S", "SPS", "S S"], {"S": "#c:ingots/steel", "P": "#c:plates/steel"}, 2),
        ("conductor_spool", [" A ", "AWA", " A "], {"A": pf("fine_aluminum_wire"), "W": "#minecraft:planks"}, 4),
        ("solar_panel", ["GGG", "SSS", "PWP"], {"G": "#c:glass_blocks", "S": pf("silicon"), "P": "#c:plates/aluminum", "W": pf("copper_wire")}, 1),
        ("wind_turbine", ["BBB", "MGM", "PKP"], {"B": pf("turbine_blade"), "M": pf("electric_motor"), "P": "#c:storage_blocks/steel",
                                                 "G": pf("generator_magnet"), "K": pf("advanced_circuit")}, 1),
        ("wind_turbine_tower", ["P P", "PSP", "P P"], {"P": "#c:plates/steel", "S": "#c:ingots/steel"}, 2),
        ("hydro_turbine", ["PBP", "BMB", "PKP"], {"P": "#c:plates/bronze", "B": pf("turbine_blade"), "M": pf("electric_motor"),
                                                  "K": pf("control_circuit")}, 1),
        ("diesel_generator", ["PXP", "GMG", "PKP"], {"P": "#c:plates/steel", "X": "minecraft:piston", "G": "#c:gears/steel",
                                                     "M": pf("electric_motor"), "K": pf("control_circuit")}, 1),
        ("synchronous_condenser", ["PCP", "CMC", "PKP"], {"P": "#c:plates/steel", "C": pf("copper_coil"), "M": pf("electric_motor"),
                                                          "K": pf("advanced_circuit")}, 1),
        ("battery_bank", ["LZL", "LCL", "PWP"], {"L": "#c:plates/lead", "Z": "#c:ingots/zinc", "C": pf("capacitor"), "P": "#c:plates/steel",
                                                 "W": pf("copper_wire")}, 1),
        ("power_meter", [" G ", "PKP", " W "], {"G": "#c:glass_panes", "P": "#c:plates/iron", "K": pf("control_circuit"), "W": pf("copper_wire")}, 1),
        ("arc_gun", ["  E", "KCK", "B  "], {"E": pf("graphite_electrode"), "K": pf("copper_coil"), "C": pf("capacitor"), "B": "#c:ingots/brass"}, 1),
        ("arc_saber", [" D ", "KCK", " P "], {"D": "#c:gems/diamond", "K": pf("capacitor"), "C": pf("advanced_circuit"), "P": "#c:plates/steel"}, 1),
        ("drill_area_module", ["PDP", "DKD", "PDP"], {**card, "D": "#c:gems/diamond"}, 1),
        ("drill_speed_module", ["PRP", "RKR", "PRP"], {**card, "R": "#c:dusts/redstone"}, 1),
        ("drill_silk_module", ["PSP", "EKE", "PSP"], {**card, "S": "minecraft:string", "E": "#c:gems/emerald"}, 1),
        ("drill_fortune_module", ["PLP", "GKG", "PLP"], {**card, "L": "#c:gems/lapis", "G": "#c:ingots/gold"}, 1),
        ("drill_magnet_module", ["PRP", "IKI", "PRP"], {**card, "R": "#c:dusts/redstone", "I": "#c:ingots/iron"}, 1),
        ("drill_battery_module", ["PZP", "CKC", "PZP"], {**card, "Z": "#c:ingots/zinc", "C": pf("capacitor")}, 1),
    ]
    for out_name, pattern, key, count in recipes:
        cat = "equipment" if out_name.startswith(("arc_", "drill_")) else "redstone"
        shaped(out_name, pattern, key, count, category=cat)

    pretty = lambda s: " ".join(w.capitalize() for w in s.split("_"))
    for b in new_blocks:
        name(f"block.{NS}.{b}", pretty(b))
    name(f"block.{NS}.hv_pylon", "HV Transmission Pylon")
    name(f"block.{NS}.scada_display", "SCADA Display")
    for i in ["arc_gun", "arc_saber", "conductor_spool"] + [f"drill_{m}_module" for m in DRILL_MODULES]:
        name(f"item.{NS}.{i}", pretty(i))
    name(f"item.{NS}.arc_gun", "Tesla Arc Gun")
    # Subtitles for every synthesized sound.
    sounds = json.load(open(os.path.join(ASSETS, "sounds.json")))
    for event in sounds:
        name(f"subtitles.{NS}.{event}", pretty(event).replace("Run", "runs").replace("Scada", "SCADA"))
    LANG.update({
        "message.powerfactor.display.page": "Display: %s (%sx%s)",
        "gui.powerfactor.display.page.auto": "auto layout", "gui.powerfactor.display.page.summary": "system summary",
        "gui.powerfactor.display.page.list": "device list", "gui.powerfactor.display.page.trend": "trends",
        "gui.powerfactor.display.page.diagram": "one-line diagram", "gui.powerfactor.display.page.device": "device cycle",
        "gui.powerfactor.drill.modules": "Drill Modules", "gui.powerfactor.drill.hint": "Silk & Fortune act as enchants",
        "tooltip.powerfactor.drill.module": "%s x%s", "tooltip.powerfactor.drill.open": "Sneak-use to change modules",
        "tooltip.powerfactor.drill_module.install": "Sneak-use an Electric Drill to install",
        "tooltip.powerfactor.drill_module.area": "Mines a 3x3 face (sneak to mine one block)",
        "tooltip.powerfactor.drill_module.speed": "+50% mining speed per module",
        "tooltip.powerfactor.drill_module.silk": "Silk Touch", "tooltip.powerfactor.drill_module.fortune": "Fortune, one level per module",
        "tooltip.powerfactor.drill_module.magnet": "Pulls nearby drops to you", "tooltip.powerfactor.drill_module.battery": "+100% capacity per module",
        "tooltip.powerfactor.arc_gun": "%s FE per shot; arcs to %s more targets",
        "tooltip.powerfactor.saber.style": "Blade: %s", "tooltip.powerfactor.saber.effect": "Effect: %s",
        "tooltip.powerfactor.saber.help": "Use: ignite. Sneak-use: customize.",
        "gui.powerfactor.saber.title": "Arc Saber", "gui.powerfactor.saber.presets": "Presets", "gui.powerfactor.saber.apply": "Apply",
        "gui.powerfactor.saber.style": "Blade: %s", "gui.powerfactor.saber.effect": "Effect: %s",
        "gui.powerfactor.saber.style.solid": "Solid", "gui.powerfactor.saber.style.plasma": "Plasma",
        "gui.powerfactor.saber.style.arc": "Crackling Arc", "gui.powerfactor.saber.style.rainbow": "Rainbow",
        "gui.powerfactor.saber.effect.none": "None", "gui.powerfactor.saber.effect.stun": "Stun", "gui.powerfactor.saber.effect.ignite": "Ignite",
        "gui.powerfactor.saber.effect.chain": "Chain Arc", "gui.powerfactor.saber.effect.drain": "Drain",
        "gui.powerfactor.ender.sending": "TX %s FE/t", "gui.powerfactor.ender.receiving": "RX %s FE/t",
        "gui.powerfactor.ender.channel": "Ch %s", "gui.powerfactor.ender.linked": "LINKED", "gui.powerfactor.ender.idle": "IDLE",
        "gui.powerfactor.ender.noisy": "INTERFERENCE", "gui.powerfactor.ender.severe": "SEVERE INTERFERENCE",
        "gui.powerfactor.ender.interference": "Interference here: %s%%",
        "gui.powerfactor.ender.interference.desc": "Busy Ender links make nearby grids flicker and motors trip.",
        "scada.powerfactor.ender.tx": "Channel %s · interference %s%%", "scada.powerfactor.ender.rx": "Channel %s · interference %s%%",
        "message.powerfactor.breaker.closed": "Breaker CLOSED", "message.powerfactor.breaker.open": "Breaker OPEN",
        "scada.powerfactor.breaker.closed": "Closed · %s%% current", "scada.powerfactor.breaker.open": "Open",
        "scada.powerfactor.breaker.tripped": "TRIPPED on overcurrent",
        "message.powerfactor.transformer": "Transformer: %s FE/t, %s EE/t, %s%% loaded",
        "message.powerfactor.transformer.tripped": "Transformer TRIPPED on overload (reset)",
        "scada.powerfactor.transformer": "%s%% loaded", "scada.powerfactor.transformer.tripped": "TRIPPED: overload",
        "message.powerfactor.spool.start": "Conductor starts at %s", "message.powerfactor.spool.linked": "Strung a %s-block span",
        "message.powerfactor.spool.too_far": "Too far: spans are at most %s blocks", "message.powerfactor.spool.full": "Pylon has no free arms",
        "message.powerfactor.spool.cut": "Conductors cut",
        "scada.powerfactor.solar": "%s%% of peak", "scada.powerfactor.wind": "%s m/s · %s RPM · pitch %s°", "scada.powerfactor.wind.blocked": "ROTOR OBSTRUCTED: needs a clear 13-block disc",
        "scada.powerfactor.wind.low": "Waiting for wind · %s m/s", "scada.powerfactor.wind.cut_out": "STORM CUT-OUT · %s m/s · feathered",
        "scada.powerfactor.wind.stopped": "Stopped · blades feathered",
        "scada.powerfactor.hydro": "%s faces of flowing water", "scada.powerfactor.diesel": "%s Hz · %s mB fuel",
        "scada.powerfactor.condenser.synced": "Synchronized · %s RPM", "scada.powerfactor.condenser.spinning": "Spinning up · %s RPM",
        "scada.powerfactor.battery": "%s%% charged", "scada.powerfactor.battery.support": "SUPPORTING GRID · %s%%",
        "scada.powerfactor.meter": "%s FE · PF %s · %s%% loaded · %s FE lost",
        "gui.powerfactor.battery.support": "SUPPORTING GRID", "gui.powerfactor.battery.standby": "STANDBY / CHARGING",
        "gui.powerfactor.meter.loading": "%s%% LOADED", "gui.powerfactor.meter.no_grid": "Not on a grid",
        "gui.powerfactor.meter.loss": "Line loss %s FE/t · %s%% loaded", "gui.powerfactor.meter.interference": "Ender interference %s%%",
        "gui.powerfactor.meter.tier.copper": "Copper wire", "gui.powerfactor.meter.tier.insulated": "Insulated cable",
        "gui.powerfactor.meter.tier.aluminum": "MV aluminum", "gui.powerfactor.meter.tier.hv": "HV line",
    })


def phase_cf():
    phase_cf_models()
    phase_cf_data()


# ======================================================================== handbook, JEI labels, advancements

HANDBOOK = {
    "start": ("Getting Started",
        "Welcome to PowerFactor: AC power the way real grids work.\n\nBuild a Coal Turbine (iron, copper coils, turbine blades, a furnace) and feed it coal. "
        "It takes about ten seconds to spin up to 3,600 RPM. Its breaker closes at 57 Hz and it starts pushing FE out of every face except the "
        "front. Run Copper Wire from it to your machines.\n\nRight-click any machine for its GUI. The tabs on the right edge hold the "
        "Electrical analyzer, Upgrades and Configuration."),
    "units": ("FE, EE and CE",
        "PowerFactor measures three kinds of power.\n\nFE (Forge Energy) is real power: it does the work, and every FE machine from any mod "
        "runs on it.\n\nEE (Ender Energy) is reactive power. Motors need it to magnetize, but it does no work. It still has to be generated "
        "and carried.\n\nCE (Creative Energy) is apparent power, the total the wires and generators actually see: CE = sqrt(FE^2 + EE^2).\n\n"
        "Power factor is FE / CE. At 1.00 every bit of capacity does work."),
    "grid": ("Wires and Grids",
        "Connected wire of one kind forms a grid, solved every tick like a real AC bus.\n\nVoltage sags as the grid loads up, and EE sags it "
        "three times harder than FE. Line loss goes with current squared, and current follows CE, so a poor power factor wastes FE as heat in "
        "the wires.\n\nTurbines set the frequency and droop slightly under load (60 Hz down to about 58). They also supply the grid's EE. "
        "Solar, wind and batteries can't, so a grid of those plus motors needs capacitors or a synchronous condenser."),
    "power_factor": ("PF Correction",
        "Motors draw EE, and lightly loaded motors draw it worst: a crusher idling between jobs runs at about PF 0.25.\n\nCapacitor Banks "
        "supply EE locally so the generator doesn't have to, which brings voltage back up and cuts losses. Their output goes with V^2, and "
        "too many push the grid to a leading PF and raise the voltage. Above 1.12 pu they trip out.\n\nThe Automatic Capacitor Bank switches "
        "six 20 EE steps in and out every two seconds to hold the grid near PF 1.0. Watch its cans light up."),
    "machines": ("Machines",
        "Crusher: ore to two dusts, plus gravel, sand and more. Electric Furnace: smelts 25% faster on resistive elements, PF 1.0. Alloy "
        "Furnace: induction heating (PF 0.6), makes brass, bronze, electrum and rubber. Arc Furnace: steel, aluminum, silicon and graphite; "
        "it needs graphite electrodes and its arc makes the grid flicker. Wire Drawer, Rolling Mill: fine wire and plates. Ore Washer: three "
        "dusts per ore with water.\n\nMotor-driven machines slow down on low voltage or frequency and trip below 0.75 pu."),
    "upgrades": ("Upgrade Cards",
        "Upgrades go in the Upgrades tab.\n\nSpeed: +50% per card, but a bigger motor uses more FE and EE. Efficiency: -15% FE per job. "
        "Built-in Capacitor: cancels 5 EE of the machine's own motor each; three fix a stock crusher. VFD: the motor ignores frequency and "
        "voltage sag, uses a quarter of the EE and stops instead of idling. Surge Protector: rides through dips down to 0.6 pu. Capacity: "
        "a bigger FE buffer.\n\nThe Configuration tab sets redstone behavior, each face's role (input, output, both, closed) and auto-input "
        "and auto-eject."),
    "ores": ("Ores and Materials",
        "New ores: zinc, tin, lead, silver and bauxite (aluminum). Each can be turned off in the common config if another mod provides it. "
        "PowerFactor uses the shared c: tags, so other mods' ores work in every machine.\n\nSmelting gives one ingot per ore, the crusher two "
        "dusts, and the ore washer three. Alloys: brass (3 copper + zinc), bronze (3 copper + tin), electrum (gold + silver). Steel and "
        "aluminum come from the arc furnace.\n\nRubber trees grow in jungles, swamps and forests. Tap weeping logs with a Tree Tap for latex, "
        "then smelt or alloy it into rubber."),
    "logistics": ("Pipes and Tanks",
        "Item Pipes and Fluid Pipes link up automatically. Arms into inventories insert by default. Right-click an arm with an empty hand "
        "to make it extract (orange rings); right-click again to disable it.\n\nFluid Tanks hold 32 buckets and keep their contents when "
        "broken. The Electric Pump draws water from a source block below it without using it up."),
    "scada": ("SCADA",
        "SCADA is the control room. Run Fiber Optic Cable from a SCADA Terminal to your machines: it lists every device live with ON/OFF "
        "switches, system totals and 60-second frequency and voltage trends. Alarms sound on faults.\n\nSCADA Displays placed side by side "
        "form one wall screen that lays itself out by size, from a single readout up to a full HMI with an automatic one-line diagram of your "
        "grid. Connect any panel to fiber; right-click to change page."),
    "transmission": ("Transmission",
        "Low voltage loses a lot over distance. Step up.\n\nWire tiers: Copper Wire (512 CE), Insulated Cable (1,024), MV Aluminum Cable "
        "(4,096), and HV pylons (16,384) with far lower loss. Transformers join two grids: power enters the red primary face and leaves the "
        "blue secondary face, and they carry EE back upstream. Overload one and it trips; right-click to reset.\n\nString HV conductors "
        "between pylons with a Conductor Spool, up to 32 blocks per span. Circuit Breakers split a grid when open and trip on overcurrent."),
    "generators": ("Generators & Storage",
        "Coal Turbine and Diesel Generator: synchronous, so they supply EE. The diesel starts in two seconds and burns modded diesel or "
        "fuel, or lava at a worse rate. Hydro Turbine: flowing water around it; also synchronous. Solar Panel and Wind Turbine: FE only.\n\n"
        "Wind Turbine: stack Wind Turbine Tower sections (they taper as they rise) and put the turbine on top. "
        "The 13-block rotor needs clear air in front, so build at least 6 sections high; taller towers catch "
        "stronger wind. It starts at 3 m/s, reaches full output at 12 m/s and pitches its blades to hold it there. "
        "Above 25 m/s, in a storm, it feathers and brakes until the wind drops. Cable it at the foot of the tower.\n\nSynchronous Condenser: a spinning machine that only makes EE. "
        "Battery Bank: stores 2M FE and automatically holds the grid up when frequency or voltage sags."),
    "ender": ("Ender Energy",
        "Ender Transmitters turn FE into an EE carrier and beam it to Ender Receivers on the same channel, even across dimensions. The "
        "receiver rectifies it back to FE at 85%.\n\nThere's a catch. Sending makes the transmitter a big reactive load on its own grid, and "
        "busy links radiate interference: every grid within about 48 blocks sees flickering voltage and jittering frequency, so motors slow "
        "and trip. Keep wireless away from your factory, or support those grids with capacitors and condensers."),
    "tools": ("Tools and Weapons",
        "Electric Drill: pickaxe and shovel on FE. Sneak-use to install modules: 3x3 Area, Speed, Silk Touch, Fortune, Magnet, Battery.\n\n"
        "Tesla Arc Gun: lightning that arcs between up to five targets.\n\nArc Saber: use to ignite, sneak-use to pick any RGB color, blade "
        "style (solid, plasma, crackling arc, rainbow) and hit effect (stun, ignite, chain arc, drain). Charge everything in a Charger."),
}

ADVANCEMENTS = [
    ("root", None, "coal_turbine", "PowerFactor", "Build a Coal Turbine", "task", "minecraft:block/stone"),
    ("crusher", "root", "crusher", "Double Trouble", "Crush ore into two dusts", "task", None),
    ("wire", "root", "copper_wire", "On the Grid", "Make some Copper Wire", "task", None),
    ("capacitor_bank", "wire", "capacitor_bank", "Power Factor", "Correct your power factor with a Capacitor Bank", "task", None),
    ("auto_capacitor_bank", "capacitor_bank", "auto_capacitor_bank", "Hold It at Unity", "Build an Automatic Capacitor Bank", "goal", None),
    ("scada", "wire", "scada_terminal", "Control Room", "Build a SCADA Terminal", "task", None),
    ("display", "scada", "scada_display", "Big Board", "Build SCADA Displays", "goal", None),
    ("alloy", "crusher", "brass_ingot", "Brass Tacks", "Alloy brass", "task", None),
    ("steel", "alloy", "steel_ingot", "Arc Light", "Make steel in an Arc Furnace", "goal", None),
    ("transformer", "steel", "transformer", "Step It Up", "Build a Transformer", "task", None),
    ("pylon", "transformer", "hv_pylon", "High Voltage", "Raise an HV Transmission Pylon", "challenge", None),
    ("ender", "scada", "ender_transmitter", "Spooky Action", "Beam power with Ender Energy", "challenge", None),
    ("saber", "steel", "arc_saber", "An Elegant Weapon", "Build an Arc Saber", "challenge", None),
    ("drill", "crusher", "electric_drill", "Cordless", "Build an Electric Drill", "task", None),
    ("renewables", "transformer", "wind_turbine", "Catch the Wind", "Build a Wind Turbine", "goal", None),
]


def finishing():
    flat_item("powerfactor_handbook")
    name(f"item.{NS}.powerfactor_handbook", "PowerFactor Handbook")
    shapeless("powerfactor_handbook", ["minecraft:book", "#c:ingots/copper"], category="misc")
    LANG["handbook.powerfactor.title"] = "PowerFactor"
    for cid, (title, text) in HANDBOOK.items():
        LANG[f"handbook.powerfactor.{cid}.title"] = title
        LANG[f"handbook.powerfactor.{cid}.text"] = text
    LANG.update({"jei.powerfactor.crushing": "Crushing", "jei.powerfactor.alloying": "Alloying", "jei.powerfactor.arc_smelting": "Arc Smelting",
                 "jei.powerfactor.wire_drawing": "Wire Drawing", "jei.powerfactor.rolling": "Rolling", "jei.powerfactor.washing": "Ore Washing",
                 "jei.powerfactor.pressing": "Pressing", "jei.powerfactor.mixing": "Mixing",
                 "jei.powerfactor.centrifuging": "Centrifuging", "jei.powerfactor.sawing": "Sawing",
                 "jei.powerfactor.chance": "%s%% chance"})
    for aid, parent, item, title, desc, frame, bg in ADVANCEMENTS:
        adv = {"display": {"icon": {"id": pf(item) if ":" not in item else item},
                           "title": {"translate": f"advancements.powerfactor.{aid}.title"},
                           "description": {"translate": f"advancements.powerfactor.{aid}.description"},
                           "frame": frame, "show_toast": True, "announce_to_chat": aid != "root"},
               "criteria": {"has": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": pf(item)}]}}}}
        if bg:
            adv["display"]["background"] = bg
            # Hand out the handbook with the first turbine.
            adv["rewards"] = {"loot": [pf("grant/handbook")]}
        if parent:
            adv["parent"] = pf(parent)
        write(data(NS, "advancement", f"{aid}.json"), adv)
        LANG[f"advancements.powerfactor.{aid}.title"] = title
        LANG[f"advancements.powerfactor.{aid}.description"] = desc
    write(data(NS, "loot_table", "grant", "handbook.json"), {"type": "minecraft:advancement_reward", "pools": [{"rolls": 1.0,
        "entries": [{"type": "minecraft:item", "name": pf("powerfactor_handbook")}]}]})


def crusher_model():
    """Crusher with a real 10x10, 5-deep feed hopper on top; the jaws are drawn by CrusherJawRenderer."""
    six = ("north", "east", "south", "west", "up", "down")
    for suffix in ("", "_on"):
        t = {"front": pf(f"block/crusher_front{suffix}"), "side": pf("block/crusher_side"), "top": pf("block/crusher_top"),
             "inner": pf("block/crusher_inner"), "particle": pf("block/crusher_side")}
        outer = {"north": "#front", "east": "#side", "south": "#side", "west": "#side", "up": "#top", "down": "#side"}

        def box(frm, to, inner_faces=(), skip=()):
            faces = {}
            for d in six:
                if d in skip:
                    continue
                tex = "#inner" if d in inner_faces else outer[d]
                on_edge = {"north": frm[2] == 0, "south": to[2] == 16, "west": frm[0] == 0, "east": to[0] == 16,
                           "up": to[1] == 16, "down": frm[1] == 0}[d]
                faces[d] = face(tex, d if on_edge and d not in inner_faces else None)
            return {"from": frm, "to": to, "faces": faces}

        els = [box([0, 0, 0], [16, 11, 16], inner_faces=("up",)),
               box([0, 11, 0], [16, 16, 3], inner_faces=("south",), skip=("down",)),
               box([0, 11, 13], [16, 16, 16], inner_faces=("north",), skip=("down",)),
               box([0, 11, 3], [3, 16, 13], inner_faces=("east",), skip=("down", "north", "south")),
               box([13, 11, 3], [16, 16, 13], inner_faces=("west",), skip=("down", "north", "south"))]
        write(asset("models", "block", f"crusher{suffix}.json"), {"parent": "minecraft:block/block", "textures": t, "elements": els})
    machine_states("crusher", pf("block/crusher"), pf("block/crusher_on"))
    item_def("crusher")


def octagon(width, y0, y1, tex, caps=("up",), uv=None):
    """Four elements whose union is a regular octagonal prism, `width` px flat to flat, centred in the block.
    Pass `uv` for prisms taller than a block (automatic UVs can't exceed the texture)."""
    side = width * (2 ** 0.5 - 1)
    h, q = width / 2, side / 2
    els = []
    for rot in (None, 45):
        for (x0, x1, z0, z1, faces) in ((8 - h, 8 + h, 8 - q, 8 + q, ("east", "west")), (8 - q, 8 + q, 8 - h, 8 + h, ("north", "south"))):
            el = {"from": [round(x0, 4), y0, round(z0, 4)], "to": [round(x1, 4), y1, round(z1, 4)],
                  "faces": {f: dict(face(tex), **({"uv": uv} if uv and f not in ("up", "down") else {})) for f in faces + tuple(caps)}}
            if rot:
                el["rotation"] = {"angle": rot, "axis": "y", "origin": [8, 8, 8]}
            els.append(el)
    return els


def wind_turbine_models():
    tower_tex = {"tube": pf("block/wind_turbine_tower"), "footing": pf("block/wind_turbine_foundation"),
                 "door": pf("block/wind_turbine_door"), "particle": pf("block/wind_turbine_tower")}
    variants = {}
    for taper in range(8):
        width = 7 + taper * 0.5
        for base in (False, True):
            els = octagon(width, 0, 16, "#tube")
            if base:
                els.append({"from": [1, 0, 1], "to": [15, 2, 15], "faces": {d: face("#footing", "down" if d == "down" else None)
                                                                            for d in ("north", "east", "south", "west", "up", "down")}})
                z = 8 - width / 2
                els.append({"from": [6.75, 2, round(z - 0.4, 4)], "to": [9.25, 9.5, z], "faces": {
                    "north": {"texture": "#door", "uv": [2, 0, 14, 16]}, "east": face("#tube"), "west": face("#tube"), "up": face("#tube")}})
            name = f"wind_turbine_tower_{taper}" + ("_base" if base else "")
            write(asset("models", "block", f"{name}.json"), {"parent": "minecraft:block/block", "textures": tower_tex, "elements": els})
            if base:
                connected = els + [{"from": [5.5, 3, round(z - 1.0, 4)], "to": [10.5, 11, round(z - 0.4, 4)], "faces": {
                    d: {"texture": "#cabinet", "uv": [0, 0, 16, 16]} for d in ("north", "east", "south", "west", "up", "down")}}]
                # Junction studs bridge the narrow tower tube to the cable's block-face contact.
                for side in range(4):
                    for low, high, texture in (([7.25,7.25,0.8],[8.75,8.75,8],"#contact"), ([6.5,6.5,0],[9.5,9.5,1.2],"#socket")):
                        points = [(x,z) for x in (low[0],high[0]) for z in (low[2],high[2])]
                        for _ in range(side): points = [(16-z,x) for x,z in points]
                        connected.append({"from":[min(x for x,z in points),low[1],min(z for x,z in points)],
                            "to":[max(x for x,z in points),high[1],max(z for x,z in points)],
                            "faces":{d:{"texture":texture,"uv":[0,0,16,16]} for d in ("north","east","south","west","up","down")}})
                tex = dict(tower_tex, cabinet=pf("block/wind_turbine_connected"),contact=pf("block/industrial_copper"),socket=pf("block/workshop_steel"))
                write(asset("models", "block", name + "_connected.json"), {"parent": "minecraft:block/block", "textures": tex, "elements": connected})
            for plugged in (False, True):
                variants[f"base={'true' if base else 'false'},taper={taper},connected={'true' if plugged else 'false'}"] = {
                    "model": pf(f"block/{name}" + ("_connected" if base and plugged else ""))}
    write(asset("blockstates", "wind_turbine_tower.json"), {"variants": variants})
    item_def("wind_turbine_tower", pf("block/wind_turbine_tower_3_base"))
    # Nacelle block: the top of the tower and the yaw bearing; the nacelle and rotor are drawn by WindTurbineRenderer.
    els = octagon(7, 0, 12, "#tube", caps=()) + octagon(10, 12, 16, "#bearing", caps=("up", "down"))
    write(asset("models", "block", "wind_turbine.json"), {"parent": "minecraft:block/block", "textures": {
        "tube": pf("block/wind_turbine_tower"), "bearing": pf("block/wind_turbine_bearing"), "particle": pf("block/wind_turbine_tower")},
        "elements": els})
    machine_states("wind_turbine", pf("block/wind_turbine"), pf("block/wind_turbine"))
    flat_item("wind_turbine")


# ======================================================================== power tools and devices

def devices_models():
    six = ("north", "east", "south", "west", "up", "down")
    # Tesla coil: plinth, flat primary, tall wound secondary, rounded toroid, terminal. Lower half carries the whole model.
    els = [{"from": [2, 0, 2], "to": [14, 3, 14], "faces": {d: face("#base", "down" if d == "down" else None) for d in six}},
           {"from": [1, 3, 1], "to": [15, 4, 15], "faces": {d: face("#primary") for d in six}}]
    els += octagon(5, 4, 24, "#secondary", caps=(), uv=[0, 0, 16, 16])
    els += octagon(10, 24, 25, "#toroid", caps=("down",)) + octagon(13.5, 25, 27, "#toroid", caps=("up", "down")) \
        + octagon(10, 27, 28, "#toroid", caps=("up",))
    els.append({"from": [7.5, 28, 7.5], "to": [8.5, 30.5, 8.5], "faces": {d: face("#toroid") for d in six}})
    tex = {"base": pf("block/tesla_coil_base"), "primary": pf("block/tesla_coil_primary"), "secondary": pf("block/tesla_coil_secondary"),
           "toroid": pf("block/tesla_coil_toroid"), "particle": pf("block/tesla_coil_toroid")}
    write(asset("models", "block", "tesla_coil.json"), {"parent": "minecraft:block/block", "textures": tex, "elements": els,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -3.5, 0], "scale": [0.42, 0.42, 0.42]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.18, 0.18, 0.18]},
                    "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]}}})
    write(asset("models", "block", "tesla_coil_top.json"), {"textures": {"particle": pf("block/tesla_coil_toroid")}})
    variants = {}
    for f, y in HORIZONTAL.items():
        for half in ("lower", "upper"):
            for running in ("false", "true"):
                variants[f"facing={f},half={half},running={running}"] = {"model": pf("block/tesla_coil" if half == "lower" else "block/tesla_coil_top")}
    write(asset("blockstates", "tesla_coil.json"), {"variants": variants})
    item_def("tesla_coil")

    # Grow lamp: a heatsinked LED panel across the top of the block, LEDs facing down.
    for suffix, glow in (("", False), ("_on", True)):
        led = {"from": [1.5, 12, 1.5], "to": [14.5, 13, 14.5], "faces": {
            "down": {"texture": "#led"}, "north": face("#housing"), "south": face("#housing"), "east": face("#housing"), "west": face("#housing")}}
        if glow:
            led["light_emission"] = 15
        els = [{"from": [1, 13, 1], "to": [15, 16, 15], "faces": {d: face("#housing", "up" if d == "up" else None) for d in six}}, led]
        write(asset("models", "block", f"grow_lamp{suffix}.json"), {"parent": "minecraft:block/block", "textures": {
            "housing": pf("block/grow_lamp_housing"), "led": pf("block/grow_lamp_led" + suffix), "particle": pf("block/grow_lamp_housing")},
            "elements": els})
    machine_states("grow_lamp", pf("block/grow_lamp"), pf("block/grow_lamp_on"))
    item_def("grow_lamp")

    # Charging pad: a 2px plate; the coil rings light up and pulse while charging.
    for suffix, glow in (("", False), ("_on", True)):
        top = {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "up": {"texture": "#top"}, "down": face("#side", "down"), "north": face("#side", "north"), "south": face("#side", "south"),
            "east": face("#side", "east"), "west": face("#side", "west")}}
        if glow:
            top["light_emission"] = 10
        write(asset("models", "block", f"charging_pad{suffix}.json"), {"parent": "minecraft:block/block", "textures": {
            "top": pf("block/charging_pad_top" + suffix), "side": pf("block/charging_pad_side"), "particle": pf("block/charging_pad_side")},
            "elements": [top]})
    machine_states("charging_pad", pf("block/charging_pad"), pf("block/charging_pad_on"))
    item_def("charging_pad")

    orientable_machine("electrolyzer", top="electrolyzer_top", bottom="electrolyzer_side", side="electrolyzer_side")
    orientable_machine("fuel_cell", top="fuel_cell_top", bottom="fuel_cell_side", side="fuel_cell_side")

    # Hand tools. The chainsaw swaps to an animated running chain when charged.
    for item in ("electric_chainsaw", "electric_chainsaw_running", "clamp_meter"):
        write(asset("models", "item", f"{item}.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": pf(f"item/{item}")}})
    write(asset("items", "electric_chainsaw.json"), {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data",
        "index": 0, "on_true": {"type": "minecraft:model", "model": pf("item/electric_chainsaw_running")},
        "on_false": {"type": "minecraft:model", "model": pf("item/electric_chainsaw")}}})
    item_def("clamp_meter", pf("item/clamp_meter"))
    flat_item("electric_jetpack")


def devices_data():
    blocks = ["tesla_coil", "grow_lamp", "charging_pad", "electrolyzer", "fuel_cell"]
    for b in blocks[1:]:
        drops_self(b)
    write(data(NS, "loot_table", "blocks", "tesla_coil.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/tesla_coil"),
        "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"},
            {"condition": "minecraft:block_state_property", "block": pf("tesla_coil"), "properties": {"half": "lower"}}],
            "entries": [{"type": "minecraft:item", "name": pf("tesla_coil")}]}]})
    tag("block", "minecraft", "mineable/pickaxe", [pf(b) for b in blocks])
    tag("fluid", "c", "hydrogen", [pf("hydrogen"), pf("flowing_hydrogen")])
    tag("fluid", "c", "gaseous", [pf("hydrogen"), pf("flowing_hydrogen")])

    recipes = [
        ("electric_chainsaw", [" SS", "PMS", "CP "], {"S": "#c:plates/steel", "P": "#c:plates/aluminum", "M": pf("electric_motor"),
                                                      "C": pf("capacitor")}, "equipment"),
        ("electric_jetpack", ["MPM", "CAC", "M M"], {"M": pf("electric_motor"), "P": "#c:plates/aluminum", "C": pf("capacitor"),
                                                     "A": pf("advanced_circuit")}, "equipment"),
        ("clamp_meter", [" W ", "PKP", " G "], {"W": pf("copper_coil"), "P": "#c:plates/iron", "K": pf("control_circuit"),
                                                "G": "#c:glass_panes"}, "equipment"),
        ("tesla_coil", [" A ", "CWC", "PTP"], {"A": "#c:plates/aluminum", "C": pf("capacitor"), "W": pf("fine_copper_wire"),
                                              "P": "#c:plates/steel", "T": pf("transformer")}, "redstone"),
        ("grow_lamp", ["PPP", "SDS", " W "], {"P": "#c:plates/aluminum", "S": pf("silicon"), "D": "#c:dusts/glowstone",
                                              "W": pf("copper_wire")}, "redstone"),
        ("charging_pad", ["CKC", "PPP"], {"C": pf("copper_coil"), "K": pf("control_circuit"), "P": "#c:plates/steel"}, "redstone"),
        ("electrolyzer", ["PGP", "EKE", "PBP"], {"P": "#c:plates/steel", "G": "#c:glass_panes", "E": "#c:plates/silver",
                                                "K": pf("control_circuit"), "B": "minecraft:bucket"}, "redstone"),
        ("fuel_cell", ["PSP", "MKM", "PCP"], {"P": "#c:plates/steel", "S": "#c:plates/silver", "M": pf("mica"),
                                              "K": pf("advanced_circuit"), "C": pf("capacitor")}, "redstone"),
    ]
    for out, pattern, key, cat in recipes:
        shaped(out, pattern, key, 1, category=cat)

    for k, v in {"electric_chainsaw": "Electric Chainsaw", "electric_jetpack": "Electric Jetpack", "clamp_meter": "Clamp Meter"}.items():
        name(f"item.{NS}.{k}", v)
    for k, v in {"tesla_coil": "Tesla Coil", "grow_lamp": "LED Grow Lamp", "charging_pad": "Induction Charging Pad",
                 "electrolyzer": "Electrolyzer", "fuel_cell": "Hydrogen Fuel Cell"}.items():
        name(f"block.{NS}.{k}", v)
    LANG.update({
        "fluid.powerfactor.hydrogen": "Hydrogen",
        "key.powerfactor.jetpack_hover": "Jetpack Hover",
        "key.category.powerfactor.powerfactor": "PowerFactor",
        "tooltip.powerfactor.chainsaw": "%s FE per log · fells the whole tree",
        "tooltip.powerfactor.chainsaw.sneak": "Sneak to cut a single log",
        "tooltip.powerfactor.clamp_meter": "Look at a wire or machine to measure it",
        "tooltip.powerfactor.jetpack.hover_on": "Hover: ON",
        "tooltip.powerfactor.jetpack.hover_off": "Hover: off",
        "tooltip.powerfactor.jetpack.help": "Hold Jump to climb · Hover key toggles hover · Sneak to sink",
        "message.powerfactor.jetpack.hover_on": "Jetpack hover ON",
        "message.powerfactor.jetpack.hover_off": "Jetpack hover off",
        "meter.powerfactor.flat": "Battery flat: charge the meter",
        "meter.powerfactor.grid": "%s · %s V · %s Hz",
        "meter.powerfactor.loading": "%s%% loaded · %s FE/t line loss",
        "meter.powerfactor.interference": "Ender interference %s%%",
        "meter.powerfactor.supply": "Supply %s V · %s Hz",
        "meter.powerfactor.stored": "Stored %s / %s FE",
        "meter.powerfactor.fluid": "%s: %s / %s mB",
        "meter.powerfactor.nothing": "No reading",
        "scada.powerfactor.tesla_coil": "Charged %s%% · %s strikes",
        "scada.powerfactor.grow_lamp": "%s plants · %s growth boosts",
        "scada.powerfactor.charging_pad": "Charging %s items · %s FE/t",
        "scada.powerfactor.charging_pad.idle": "Idle",
        "scada.powerfactor.electrolyzer": "%s mB water · %s mB H₂",
        "scada.powerfactor.fuel_cell": "%s mB H₂ · %s%% load",
        "gui.powerfactor.electrolyzer.running": "ELECTROLYSING",
        "gui.powerfactor.electrolyzer.idle": "IDLE",
        "gui.powerfactor.fuel_cell.online": "ONLINE · GRID-FORMING",
        "gui.powerfactor.fuel_cell.offline": "OFFLINE",
        "gui.powerfactor.fuel_cell.grid_forming": "Grid-forming 60 Hz",
        "gui.powerfactor.fuel_cell.warming": "Warming up...",
        "gui.powerfactor.fuel_cell.ee": "%s EE/t available",
        "handbook.powerfactor.power_tools.title": "Power Tools",
        "handbook.powerfactor.power_tools.text":
            "Electric Chainsaw: an axe on FE. Cut one log of a tree and the whole tree comes down, and its leaves drop their saplings "
            "soon after. Sneak to cut a single log. It only fells real trees (logs with natural leaves), so your log cabin is safe.\n\n"
            "Electric Jetpack: wear it in the chest slot. Hold Jump to climb; press J by default to toggle hover, which holds you in the air "
            "while you build. Sneak to sink. Thrust costs 50 FE/t, hover 18.\n\n"
            "Clamp Meter: hold it and look at any wire or machine for a live reading of voltage, frequency, FE, EE, CE and power "
            "factor, with a phasor diagram.\n\nInduction Charging Pad: stand on it and everything you carry charges.",
        "handbook.powerfactor.devices.title": "Tesla Coil & Grow Lamp",
        "handbook.powerfactor.devices.text":
            "Tesla Coil: charges a capacitor from the grid, then throws lightning at the nearest hostile mob it can see within 14 "
            "blocks, arcing on to a second one. It never targets players. Each strike costs 2,500 FE and is a sharp reactive pulse "
            "on the grid, so give it a strong grid or a capacitor bank nearby, or the lights will flicker.\n\n"
            "LED Grow Lamp: hang it above crops. It lights them, so they grow underground, and gives every plant in a 9x9 area up "
            "to six blocks below many extra growth ticks: several times faster farms for 24 FE/t.",
        "handbook.powerfactor.hydrogen.title": "Hydrogen",
        "handbook.powerfactor.hydrogen.text":
            "Wind blows when it likes. Store it as hydrogen.\n\nThe Electrolyzer splits water into hydrogen with grid power "
            "(100 FE per 4 mB). Pipe water in and hydrogen out to a Fluid Tank, where it shows as a gas that thickens with pressure.\n\n"
            "The Hydrogen Fuel Cell turns it back into FE (about 11 FE per mB, so roughly 45% round trip, like real systems). Its "
            "inverter is grid-forming: it sets 60 Hz and supplies EE like a synchronous generator, so wind plus hydrogen can run "
            "a whole grid with nothing spinning.",
    })
    ADV = [
        ("timber", "drill", "electric_chainsaw", "Timber!", "Build an Electric Chainsaw", "task"),
        ("jetpack", "drill", "electric_jetpack", "Rocketeer", "Build an Electric Jetpack", "goal"),
        ("tesla_coil", "capacitor_bank", "tesla_coil", "Unlimited Power", "Build a Tesla Coil", "goal"),
        ("hydrogen", "renewables", "fuel_cell", "Hydrogen Economy", "Build a Hydrogen Fuel Cell", "challenge"),
        ("grow_lamp", "wire", "grow_lamp", "Indoor Gardening", "Build an LED Grow Lamp", "task"),
    ]
    for aid, parent, item, title, desc, frame in ADV:
        write(data(NS, "advancement", f"{aid}.json"), {"parent": pf(parent), "display": {"icon": {"id": pf(item)},
            "title": {"translate": f"advancements.powerfactor.{aid}.title"}, "description": {"translate": f"advancements.powerfactor.{aid}.description"},
            "frame": frame, "show_toast": True, "announce_to_chat": True},
            "criteria": {"has": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": pf(item)}]}}}})
        LANG[f"advancements.powerfactor.{aid}.title"] = title
        LANG[f"advancements.powerfactor.{aid}.description"] = desc


def devices():
    devices_models()
    devices_data()


# ======================================================================== wires v2: bare/coated, along-the-length textures

WIRE_STYLE = {
    # block: (bare thickness or None if always coated, coated thickness, bare texture, coated texture)
    "copper_wire": (3, 4, "copper_wire", "copper_wire_coated"),
    "aluminum_cable": (4, 5, "aluminum_cable", "aluminum_cable_coated"),
    "insulated_copper_cable": (None, 5, None, "insulated_copper_cable"),
}


def wire(block):
    """Power wire with bare/coated states; overwrites what cable() wrote for it."""
    bare, coated, bare_tex, coated_tex = WIRE_STYLE[block]
    six = ("north", "east", "south", "west", "up", "down")

    def faces(along_z, skip=()):
        out = {}
        for d in six:
            if d in skip:
                continue
            f = {"texture": "#t", "uv": [0, 0, 16, 16]}
            # Stripes run along U; turn them to follow the cable on the faces where U would run across it.
            if along_z and d in ("up", "down"):
                f["rotation"] = 90
            out[d] = f
        return out

    variants = []
    for is_coated, t, tex in ((False, bare, bare_tex), (True, coated, coated_tex)):
        if t is None:
            continue
        lo, hi = 8 - t / 2, 8 + t / 2
        texture = pf(f"block/{tex}")
        suffix = "_coated" if is_coated and bare is not None else ""
        write(asset("models", "block", f"{block}{suffix}_core.json"), {"parent": "minecraft:block/block",
            "textures": {"t": texture, "particle": texture},
            "elements": [{"from": [lo, lo, lo], "to": [hi, hi, hi], "faces": faces(False)}]})
        write(asset("models", "block", f"{block}{suffix}_arm.json"), {"parent": "minecraft:block/block",
            "textures": {"t": texture, "particle": texture},
            "elements": [{"from": [lo, lo, 0], "to": [hi, hi, lo], "faces": faces(True, skip=("south",))}]})
        variants.append((is_coated, suffix, texture, lo, hi))
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = []
    for is_coated, suffix, texture, lo, hi in variants:
        cond = {"coated": "true" if is_coated else "false"}
        parts.append({"when": cond, "apply": {"model": pf(f"block/{block}{suffix}_core")}})
        for d, rot in rotations.items():
            parts.append({"when": dict(cond, **{d: "true"}), "apply": dict({"model": pf(f"block/{block}{suffix}_arm"), "uvlock": False}, **rot)})
    write(asset("blockstates", f"{block}.json"), {"multipart": parts})
    # Item: the default state's look, a short length of cable.
    is_coated, suffix, texture, lo, hi = variants[0]
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block", "textures": {"t": texture, "particle": texture},
        "elements": [{"from": [lo, lo, 0], "to": [hi, hi, 16], "faces": faces(True)}],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})
    item_def(block, pf(f"item/{block}"))


# ======================================================================== glass pipes, 3D tools, wrench, upgrade table

def glass_pipe(block):
    """Glass tube: translucent so the renderer's items and fluid show through; opaque flanges on endpoints."""
    tex, ext, fl = pf(f"block/{block}"), pf("block/pipe_extract"), pf(f"block/{block}_flange")
    six = ("north", "east", "south", "west", "up", "down")
    rt = "minecraft:translucent"
    def faces(t, along_z=False, skip=(), collars=False):
        out = {}
        for d in six:
            if d in skip:
                continue
            # Arms show clear glass only; the collars sit on the coupling at the middle of each block.
            f = {"texture": t, "uv": [0, 0, 16, 16] if collars or t != "#t" else [3, 0, 13, 16]}
            if along_z and d in ("up", "down"):
                f["rotation"] = 90
            out[d] = f
        return out
    write(asset("models", "block", f"{block}_core.json"), {"parent": "minecraft:block/block", "render_type": rt,
        "textures": {"t": tex, "particle": tex}, "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces("#t", collars=True)}]})
    write(asset("models", "block", f"{block}_arm.json"), {"parent": "minecraft:block/block", "render_type": rt,
        "textures": {"t": tex, "particle": tex}, "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": faces("#t", True, ("south",))}]})
    flange = {"from": [4, 4, 0], "to": [12, 12, 2], "faces": faces("#f")}
    write(asset("models", "block", f"{block}_insert.json"), {"parent": "minecraft:block/block", "render_type": rt,
        "textures": {"t": tex, "f": fl, "particle": tex},
        "elements": [{"from": [5, 5, 2], "to": [11, 11, 5], "faces": faces("#t", True, ("south",))}, flange]})
    write(asset("models", "block", f"{block}_extract.json"), {"parent": "minecraft:block/block", "render_type": rt,
        "textures": {"t": tex, "e": ext, "particle": tex},
        "elements": [{"from": [5, 5, 2], "to": [11, 11, 5], "faces": faces("#t", True, ("south",))},
                     {"from": [3.5, 3.5, 0], "to": [12.5, 12.5, 2.5], "faces": faces("#e")}]})
    write(asset("models", "item", f"{block}.json"), {"parent": "minecraft:block/block", "render_type": rt,
        "textures": {"t": tex, "particle": tex}, "elements": [{"from": [5, 5, 0], "to": [11, 11, 16], "faces": faces("#t", True)}],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.9, 0.9, 0.9]}}})


def el(frm, to, tex, rot=None, emissive=False):
    e = {"from": frm, "to": to, "faces": {d: {"texture": tex, "uv": [0, 0, 16, 16]} for d in ("north", "east", "south", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    if emissive:
        e["light_emission"] = 12
    return e


TOOL_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2, 1], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 2, 1], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, 100, 8], "translation": [1, 3.5, -1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_lefthand": {"rotation": [0, -80, -8], "translation": [1, 3.5, -1], "scale": [0.55, 0.55, 0.55]},
    "gui": {"rotation": [20, -130, 0], "translation": [0, 0, 0], "scale": [0.72, 0.72, 0.72]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 90, 0], "translation": [0, 10, 0], "scale": [0.8, 0.8, 0.8]},
}


def tool_models():
    m = lambda n: pf(f"item/model/{n}")
    # Big mining drill: heavy motor housing with a carry handle and pistol grip, battery under the
    # nose, a geared neck and a long conical auger bit along +X. The bit spins when charged.
    for running in (False, True):
        bit = "#bit"
        drill = [el([-5, 4.5, 3.5], [6, 13.5, 12.5], "#body"),                     # motor housing
                 el([-6, 5.5, 4.5], [-5, 12.5, 11.5], "#grip"),                     # rear cap
                 el([-3.5, 13.5, 7.25], [-2, 16.5, 8.75], "#grip"),                 # carry handle
                 el([3, 13.5, 7.25], [4.5, 16.5, 8.75], "#grip"),
                 el([-3.5, 16.5, 7.1], [4.5, 18, 8.9], "#grip"),
                 el([-3, 0, 6.25], [0.5, 4.5, 9.75], "#grip", {"angle": -22.5, "axis": "z", "origin": [-1, 4.5, 8]}),   # pistol grip
                 el([0.5, 1.5, 5.25], [6, 4.5, 10.75], "#battery"),                 # battery pack
                 el([6, 5.5, 4.5], [8.5, 12.5, 11.5], "#chrome"),                   # gearbox neck
                 el([8.5, 4.75, 3.75], [9.5, 13.25, 12.25], "#chrome"),            # chuck collar
                 el([9.5, 5.25, 4.25], [13, 12.75, 11.75], bit),                    # auger, tapering in steps
                 el([13, 6.25, 5.25], [16.5, 11.75, 10.75], bit),
                 el([16.5, 7.25, 6.25], [20, 10.75, 9.75], bit),
                 el([20, 8, 7], [23, 10, 9], bit),
                 el([23, 8.6, 7.6], [25, 9.4, 8.4], bit)]
        name = "electric_drill_3d" + ("_running" if running else "")
        display = dict(TOOL_DISPLAY,
                       # Tuned in game: carried at the hip, auger reaching toward the crosshair.
                       # Tuned in game: the auger points away from the player, toward what you're digging.
                       # Noah's pick of the first-person pose: big and close, auger forward-left.
                       firstperson_righthand={"rotation": [0, 95, 5], "translation": [2, 2, -2], "scale": [0.48, 0.48, 0.48]},
                       firstperson_lefthand={"rotation": [0, -85, -5], "translation": [2, 2, -2], "scale": [0.48, 0.48, 0.48]},
                       thirdperson_righthand={"rotation": [0, 90, 0], "translation": [0, 3, -2], "scale": [0.7, 0.7, 0.7]},
                       thirdperson_lefthand={"rotation": [0, 90, 0], "translation": [0, 3, -2], "scale": [0.7, 0.7, 0.7]},
                       gui={"rotation": [20, -135, 0], "translation": [0, -1, 0], "scale": [0.52, 0.52, 0.52]},
                       ground={"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
                       fixed={"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]})
        write(asset("models", "item", f"{name}.json"), {"textures": {"body": m("mining_drill_body"), "grip": m("drill_grip"),
            "chrome": m("drill_chrome"), "bit": m("mining_drill_bit_running" if running else "mining_drill_bit"),
            "battery": m("drill_battery"), "particle": m("mining_drill_body")},
            "gui_light": "side", "display": display, "elements": drill})
    write(asset("items", "electric_drill.json"), {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data",
        "index": 0, "on_true": {"type": "minecraft:model", "model": pf("item/electric_drill_3d_running")},
        "on_false": {"type": "minecraft:model", "model": pf("item/electric_drill_3d")}}})
    # Chainsaw: motor body, rear handle, top handle loop, battery, guide bar with the chain running round it.
    for running in (False, True):
        chain = "#chain"
        saw = [el([2, 6, 6], [10, 11, 10], "#body"), el([-1, 7, 7.25], [2, 9.5, 8.75], "#grip"),
               el([3, 11, 7.4], [8.5, 12.2, 8.6], "#grip"), el([3, 12, 7.4], [4.2, 13.5, 8.6], "#grip"),
               el([7.3, 12, 7.4], [8.5, 13.5, 8.6], "#grip"), el([3, 13.5, 7.4], [8.5, 14.6, 8.6], "#grip"),
               el([3, 4, 6.75], [8, 6, 9.25], "#battery"), el([10, 5.5, 5.75], [10.8, 11.5, 10.25], "#grip"),
               el([10.8, 7.5, 7.6], [22, 10, 8.4], "#bar"),
               el([10.8, 10, 7.5], [22, 10.7, 8.5], chain), el([10.8, 6.8, 7.5], [22, 7.5, 8.5], chain),
               el([22, 6.8, 7.5], [22.8, 10.7, 8.5], chain)]
        name = "electric_chainsaw_3d" + ("_running" if running else "")
        write(asset("models", "item", f"{name}.json"), {"textures": {"body": m("saw_body"), "grip": m("saw_grip"), "bar": m("saw_bar"),
            "chain": m("saw_chain_running" if running else "saw_chain"), "battery": m("drill_battery"), "particle": m("saw_body")},
            "gui_light": "side", "display": TOOL_DISPLAY, "elements": saw})
    write(asset("items", "electric_chainsaw.json"), {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data",
        "index": 0, "on_true": {"type": "minecraft:model", "model": pf("item/electric_chainsaw_3d_running")},
        "on_false": {"type": "minecraft:model", "model": pf("item/electric_chainsaw_3d")}}})
    # Crescent wrench: a plain handheld tool sprite, like vanilla tools.
    flat_item("wrench", "minecraft:item/handheld")


def upgrade_table_model():
    t = {"top": pf("block/upgrade_table_top"), "side": pf("block/upgrade_table_side"), "front": pf("block/upgrade_table_front"),
         "leg": pf("block/upgrade_table_leg"), "particle": pf("block/upgrade_table_side")}
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to, tex_for):
        return {"from": frm, "to": to, "faces": {d: {"texture": tex_for(d)} for d in six}}
    els = [box([0, 10, 0], [16, 14, 16], lambda d: "#top" if d == "up" else "#leg" if d == "down" else "#side"),
           box([1, 0, 1], [15, 10, 15], lambda d: "#front" if d == "north" else "#leg" if d in ("up", "down") else "#side")]
    write(asset("models", "block", "upgrade_table.json"), {"parent": "minecraft:block/block", "textures": t, "elements": els})
    write(asset("blockstates", "upgrade_table.json"), {"variants": {f"facing={f}": dict({"model": pf("block/upgrade_table")}, **({"y": y} if y else {}))
                                                                    for f, y in HORIZONTAL.items()}})
    item_def("upgrade_table")


def phase_h():
    glass_pipe("item_pipe")
    glass_pipe("fluid_pipe")
    tool_models()
    upgrade_table_model()
    for mod in ("efficiency_module", "thruster_module", "plating_module", "overcharge_module", "chain_module", "woodcutter_module"):
        flat_item(mod)
    drops_self("upgrade_table")
    tag("block", "minecraft", "mineable/pickaxe", [pf("upgrade_table")])
    card = {"P": "#c:plates/steel", "K": pf("advanced_circuit")}
    shaped("upgrade_table", ["GKG", "PCP", "P P"], {"G": "#c:glass_panes", "K": pf("advanced_circuit"), "C": "minecraft:crafting_table",
                                                     "P": "#c:plates/steel"}, 1, category="redstone")
    shaped("wrench", ["I I", " I ", " I "], {"I": "#c:ingots/iron"}, 1, category="equipment")
    shaped("efficiency_module", ["PGP", "GKG", "PGP"], {**card, "G": "#c:plates/gold"}, 1, category="equipment")
    shaped("thruster_module", ["PMP", "AKA", "PMP"], {**card, "M": pf("electric_motor"), "A": "#c:plates/aluminum"}, 1, category="equipment")
    shaped("plating_module", ["PSP", "SKS", "PSP"], {**card, "S": "#c:storage_blocks/steel"}, 1, category="equipment")
    shaped("overcharge_module", ["PCP", "RKR", "PCP"], {**card, "C": pf("capacitor"), "R": "#c:storage_blocks/redstone"}, 1, category="equipment")
    shaped("woodcutter_module", ["PAP", "AKA", "PAP"], {**card, "A": "minecraft:diamond_axe"}, 1, category="equipment")
    shaped("chain_module", ["PWP", "QKQ", "PWP"], {**card, "W": pf("fine_silver_wire"), "Q": "#c:gems/quartz"}, 1, category="equipment")
    write(data(NS, "damage_type", "electrocution.json"), {"message_id": "powerfactor.electrocution", "exhaustion": 0.1, "scaling": "never",
                                                          "effects": "burning"})
    tag("damage_type", "minecraft", "bypasses_armor", [pf("electrocution")])
    pretty = lambda s: " ".join(w.capitalize() for w in s.split("_"))
    for t in ("area", "speed", "silk", "fortune", "magnet", "battery"):
        name(f"item.{NS}.drill_{t}_module", pretty(t) + " Module")
    name(f"item.{NS}.drill_silk_module", "Silk Touch Module")
    for mod in ("efficiency_module", "thruster_module", "plating_module", "overcharge_module", "chain_module", "woodcutter_module"):
        name(f"item.{NS}.{mod}", pretty(mod))
    name(f"item.{NS}.chain_module", "Chain Arc Module")
    name(f"item.{NS}.wrench", "Wrench")
    name(f"block.{NS}.upgrade_table", "Upgrade Table")
    LANG.update({
        "death.attack.powerfactor.electrocution": "%1$s was electrocuted",
        "death.attack.powerfactor.electrocution.player": "%1$s was electrocuted while fleeing %2$s",
        "tooltip.powerfactor.wrench.rotate": "Right-click: turn a machine",
        "tooltip.powerfactor.wrench.strip": "Right-click a coated wire: strip it",
        "tooltip.powerfactor.wrench.dismantle": "Sneak + right-click: pick the block up",
        "tooltip.powerfactor.module.fits": "Fits: %s",
        "tooltip.powerfactor.module.max": "Up to %s per tool",
        "tooltip.powerfactor.drill_module.install": "Install at an Upgrade Table",
        "tooltip.powerfactor.drill_module.efficiency": "-20% energy per use, each",
        "tooltip.powerfactor.drill_module.thruster": "+35% jetpack thrust and top speed, each",
        "tooltip.powerfactor.drill_module.plating": "+2 armour, +1 toughness, each",
        "tooltip.powerfactor.drill_module.overcharge": "More damage per hit, each",
        "tooltip.powerfactor.drill_module.chain": "Arcs leap to more targets",
        "tooltip.powerfactor.drill_module.woodcutter": "Cuts wood fast and fells whole trees",
        "tooltip.powerfactor.drill_module.speed": "+50% cutting speed per module",
        "tool_kind.powerfactor.drill": "Electric Mining Drill", "tool_kind.powerfactor.chainsaw": "Electric Chainsaw",
        "tool_kind.powerfactor.jetpack": "Electric Jetpack", "tool_kind.powerfactor.saber": "Arc Saber",
        "tool_kind.powerfactor.arc_gun": "Tesla Arc Gun",
        "tool_kind.powerfactor.drill.short": "DRILL", "tool_kind.powerfactor.chainsaw.short": "CHAINSAW",
        "tool_kind.powerfactor.jetpack.short": "JETPACK", "tool_kind.powerfactor.saber.short": "ARC SABER",
        "tool_kind.powerfactor.arc_gun.short": "ARC GUN",
        "gui.powerfactor.upgrade.title": "MOD BENCH",
        "gui.powerfactor.upgrade.standby": "STANDBY", "gui.powerfactor.upgrade.linked": "LINKED",
        "gui.powerfactor.upgrade.insert": "INSERT", "gui.powerfactor.upgrade.device": "DEVICE",
        "gui.powerfactor.upgrade.no_device": "NO DEVICE",
        "gui.powerfactor.upgrade.help1": "Place a powered tool in the centre bay.",
        "gui.powerfactor.upgrade.help2": "Fit modules in the six sockets.",
        "gui.powerfactor.upgrade.modules": "MODULES", "gui.powerfactor.upgrade.inventory": "INVENTORY",
        "handbook.powerfactor.upgrades_tools.title": "Upgrade Table",
        "handbook.powerfactor.upgrades_tools.text":
            "Put a Drill, Chainsaw, Jetpack, Arc Saber or Arc Gun in the middle of an Upgrade Table and fit up to six modules round "
            "it. Battery and Efficiency fit everything. Speed and Magnet suit the drill and chainsaw; Area, Silk Touch and Fortune "
            "the drill; Thruster and Plating the jetpack; Overcharge the blades and the gun; Chain Arc the saber and gun. Sockets light "
            "up for modules that fit. Modules travel inside the tool.\n\nWrench: turns machines, strips wire coating, and sneak-use "
            "dismantles any PowerFactor block into your inventory.",
        "handbook.powerfactor.wire_safety.title": "Wire Safety",
        "handbook.powerfactor.wire_safety.text":
            "Bare wire on a live grid shocks whatever touches it: copper (480 V) stings, bare MV aluminium (13.8 kV) throws you "
            "back hard. Right-click a wire with Rubber to coat it; coated and insulated cable are safe to walk on. A wrench strips "
            "the coat off again. Mobs that wander onto bare live wire get the same treatment.",
    })


# ======================================================================== laser engraving and semiconductors

CHIPS = ["silicon_wafer", "printed_circuit_board", "microprocessor", "memory_chip", "power_transistor", "sensor_chip"]
MASKS = ["logic", "memory", "power", "sensor"]


def laser_engraver_model():
    six = ("north", "east", "south", "west", "up", "down")
    for suffix in ("", "_on"):
        t = {"front": pf(f"block/laser_engraver_front{suffix}"), "side": pf("block/laser_engraver_side"), "bed": pf("block/laser_engraver_bed"),
             "gantry": pf("block/laser_engraver_gantry"), "particle": pf("block/laser_engraver_side")}
        body = {"from": [0, 0, 0], "to": [16, 11, 16], "faces": {d: {"texture": "#bed" if d == "up" else "#front" if d == "north" else "#side",
                                                                     **({"cullface": d} if d != "up" else {})} for d in six}}
        g = lambda frm, to: {"from": frm, "to": to, "faces": {d: {"texture": "#gantry"} for d in six}}
        els = [body, g([0.5, 11, 1], [2, 16, 2.5]), g([14, 11, 1], [15.5, 16, 2.5]), g([0.5, 11, 13.5], [2, 16, 15]),
               g([14, 11, 13.5], [15.5, 16, 15]), g([0.5, 15, 2.5], [2, 16, 13.5]), g([14, 15, 2.5], [15.5, 16, 13.5])]
        write(asset("models", "block", f"laser_engraver{suffix}.json"), {"parent": "minecraft:block/block", "textures": t, "elements": els})
    machine_states("laser_engraver", pf("block/laser_engraver"), pf("block/laser_engraver_on"))
    item_def("laser_engraver")


def phase_i():
    laser_engraver_model()
    for c in CHIPS:
        flat_item(c)
    for m in MASKS:
        flat_item(f"{m}_photomask")
    drops_self("laser_engraver")
    tag("block", "minecraft", "mineable/pickaxe", [pf("laser_engraver")])
    tag("item", "powerfactor", "photomasks", [pf(f"{m}_photomask") for m in MASKS])
    tag("item", "c", "silicon_wafers", [pf("silicon_wafer")])
    shaped("laser_engraver", ["PDP", "GKG", "PMP"], {"P": "#c:plates/steel", "D": "#c:gems/diamond", "G": "#c:glass_panes",
                                                       "K": pf("control_circuit"), "M": pf("electric_motor")}, 1, category="redstone")
    machine_recipe("rolling", "silicon_wafer", [("#c:silicon", 1)], [{"item": pf("silicon_wafer"), "count": 2}], time=120)
    tag("item", "c", "silicon", [pf("silicon")])
    mask_core = {"logic": "#c:plates/gold", "memory": "#c:dusts/redstone", "power": "#c:plates/copper", "sensor": "#c:gems/amethyst"}
    for m, core in mask_core.items():
        shaped(f"{m}_photomask", ["QPQ", "PCP", "QPQ"], {"Q": "#c:gems/quartz", "P": "#c:glass_panes", "C": core}, 1, category="misc")
    def engrave(out, count, dopant, mask, time=200):
        r = {"type": pf("laser_engraving"), "inputs": [{"ingredient": pf("silicon_wafer"), "count": 1}, {"ingredient": dopant[0], "count": dopant[1]}],
             "tool": pf(f"{mask}_photomask"), "results": [{"item": pf(out), "count": count}], "time": time}
        write(data(NS, "recipe", "laser_engraving", f"{out}.json"), r)
    engrave("microprocessor", 1, ("#c:nuggets/gold", 2), "logic", 300)
    engrave("memory_chip", 2, ("#c:dusts/redstone", 2), "memory")
    engrave("power_transistor", 2, ("#c:dusts/copper", 1), "power", 160)
    engrave("sensor_chip", 1, ("#c:gems/quartz", 1), "sensor")
    shaped("printed_circuit_board", ["WWW", "RPR"], {"W": pf("fine_copper_wire"), "R": "#c:rubbers", "P": "#c:plates/copper"}, 2, category="misc")
    # Chips in the higher-tech recipes.
    shaped("advanced_circuit", ["WMW", "KBK", "WCW"], {"W": pf("fine_silver_wire"), "M": pf("microprocessor"), "K": pf("memory_chip"),
                                                       "B": pf("printed_circuit_board"), "C": pf("control_circuit")})
    card = {"P": "#c:ingots/zinc", "K": pf("control_circuit")}
    shaped("vfd_upgrade", ["BTB", "MKM", "BTB"], {"K": pf("control_circuit"), "B": "#c:ingots/brass", "T": pf("power_transistor"),
                                                   "M": pf("electric_motor")})
    shaped("clamp_meter", [" W ", "PSP", " G "], {"W": pf("copper_coil"), "P": "#c:plates/iron", "S": pf("sensor_chip"),
                                                  "G": "#c:glass_panes"}, 1, category="equipment")
    shaped("upgrade_table", ["GMG", "PCP", "P P"], {"G": "#c:glass_panes", "M": pf("microprocessor"), "C": "minecraft:crafting_table",
                                                     "P": "#c:plates/steel"}, 1, category="redstone")
    name(f"block.{NS}.laser_engraver", "Laser Engraver")
    for c in CHIPS:
        name(f"item.{NS}.{c}", " ".join(w.capitalize() for w in c.split("_")))
    for m in MASKS:
        name(f"item.{NS}.{m}_photomask", f"{m.capitalize()} Photomask")
    LANG.update({
        "jei.powerfactor.laser_engraving": "Laser Engraving",
        "jei.powerfactor.tool": "Not consumed: wears a little each use",
        "tooltip.powerfactor.photomask": "Goes in a Laser Engraver's mask slot",
        "tooltip.powerfactor.photomask.logic": "Patterns logic dies: microprocessors",
        "tooltip.powerfactor.photomask.memory": "Patterns memory dies",
        "tooltip.powerfactor.photomask.power": "Patterns power transistors",
        "tooltip.powerfactor.photomask.sensor": "Patterns sensor dies",
        "handbook.powerfactor.chips.title": "Chips",
        "handbook.powerfactor.chips.text":
            "Electronics start as silicon. Roll silicon into wafers in the Rolling Mill. The Laser Engraver patterns a wafer through a "
            "photomask: the mask in its tool slot decides the chip. It isn't used up, but every die wears it a little.\n\n"
            "Logic mask + 2 gold nuggets: Microprocessor. Memory mask + 2 redstone: 2 Memory Chips. Power mask + copper dust: 2 Power "
            "Transistors. Sensor mask + quartz: Sensor Chip.\n\nAdvanced Circuits now need a microprocessor and memory on a printed "
            "circuit board. VFD upgrades use power transistors, the clamp meter a sensor chip, the Upgrade Table a microprocessor.",
    })


# ======================================================================== radio

def phase_radio():
    six = ("north", "east", "south", "west", "up", "down")
    t = {"mast": pf("block/radio_tower_mast"), "base": pf("block/steel_block"), "light": "minecraft:block/redstone_block",
         "particle": pf("block/radio_tower_mast")}
    for suffix, lit in (("", False), ("_on", True)):
        light = {"from": [7.25, 15, 7.25], "to": [8.75, 16.5, 8.75], "faces": {d: {"texture": "#light"} for d in six}}
        if lit:
            light["light_emission"] = 15
        els = [{"from": [3, 0, 3], "to": [13, 2, 13], "faces": {d: {"texture": "#base"} for d in six}},
               {"from": [5, 2, 5], "to": [11, 15, 11], "faces": {d: {"texture": "#mast"} for d in six if d not in ("up", "down")}},
               {"from": [7.5, 13, 7.5], "to": [8.5, 15, 8.5], "faces": {d: {"texture": "#base"} for d in six}},
               {"from": [4, 10, 7.5], "to": [12, 10.8, 8.5], "faces": {d: {"texture": "#base"} for d in six}},
               light]
        write(asset("models", "block", f"radio_tower{suffix}.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": t, "elements": els})
    machine_states("radio_tower", pf("block/radio_tower"), pf("block/radio_tower_on"))
    item_def("radio_tower")
    flat_item("walkie_talkie", "minecraft:item/handheld")
    drops_self("radio_tower")
    tag("block", "minecraft", "mineable/pickaxe", [pf("radio_tower")])
    shaped("walkie_talkie", [" W ", "PSP", "PKP"], {"W": pf("copper_wire"), "P": "#c:plates/aluminum", "S": "minecraft:note_block",
                                                    "K": pf("control_circuit")}, 1, category="equipment")
    shaped("radio_tower", [" A ", "SKS", "SPS"], {"A": pf("fine_copper_wire"), "S": "#c:ingots/steel", "K": pf("advanced_circuit"),
                                                  "P": "#c:plates/steel"}, 1, category="redstone")
    name(f"item.{NS}.walkie_talkie", "Walkie-Talkie")
    name(f"block.{NS}.radio_tower", "Radio Tower")
    LANG.update({
        "message.powerfactor.walkie.channel": "Walkie-talkie: channel %s",
        "message.powerfactor.walkie.on": "Walkie-talkie ON (channel %s)",
        "message.powerfactor.walkie.off": "Walkie-talkie off",
        "tooltip.powerfactor.walkie.channel": "Channel %s",
        "tooltip.powerfactor.walkie.off": "Switched off",
        "tooltip.powerfactor.walkie.help": "Hold and talk · Use: next channel · Sneak-use: on/off",
        "tooltip.powerfactor.walkie.needs_voicechat": "Needs the Simple Voice Chat mod",
        "scada.powerfactor.radio_tower.on": "On the air", "scada.powerfactor.radio_tower.off": "OFF AIR",
        "handbook.powerfactor.radio.title": "Radio",
        "handbook.powerfactor.radio.text":
            "Walkie-talkies use your real microphone through the Simple Voice Chat mod. Hold one and talk: everyone carrying a "
            "switched-on walkie-talkie on the same channel hears you through its little speaker, with more static the further "
            "away they are, out to about 190 blocks. Use the walkie-talkie to change channel (1-16); sneak-use to switch it off. "
            "Talking uses a little charge; charge it in a charger.\\n\\nRadio Towers relay calls: when both people are within about "
            "96 blocks of a powered tower, they can talk at any distance, crystal clear. A blackout takes the towers off the air.",
    })


# ======================================================================== oil

def phase_oil():
    six = ("north", "east", "south", "west", "up", "down")
    liquids = {"crude_oil": "Crude Oil", "diesel": "Diesel", "jet_fuel": "Jet Fuel"}
    for lid, label in liquids.items():
        write(asset("models", "block", f"{lid}.json"), {"textures": {"particle": "minecraft:block/water_still"}})
        write(asset("blockstates", f"{lid}.json"), {"variants": {"": {"model": pf(f"block/{lid}")}}})
        write(asset("items", f"{lid}_bucket.json"), {"model": {"type": "neoforge:fluid_container", "fluid": pf(lid),
            "textures": {"base": "minecraft:item/bucket", "fluid": "neoforge:item/mask/bucket_fluid", "cover": "neoforge:item/mask/bucket_fluid_cover"}}})
        name(f"block.{NS}.{lid}", label)
        name(f"fluid.{NS}.{lid}", label)
        name(f"item.{NS}.{lid}_bucket", f"{label} Bucket")
        tag("fluid", "c", lid.replace("crude_oil", "oil"), [pf(lid), pf("flowing_" + lid)])
    tag("fluid", "c", "crude_oil", [pf("crude_oil"), pf("flowing_crude_oil")])
    tag("fluid", "c", "kerosene", [pf("jet_fuel"), pf("flowing_jet_fuel")])
    tag("item", "c", "buckets/crude_oil", [pf("crude_oil_bucket")])
    tag("item", "c", "buckets/diesel", [pf("diesel_bucket")])
    tag("item", "c", "buckets/jet_fuel", [pf("jet_fuel_bucket")])

    # Pumpjack: concrete pad, wellhead, motor box; the moving parts are drawn by PumpjackRenderer.
    t = {"pad": pf("block/pumpjack_skid"), "steel": pf("block/pumpjack_wellhead"), "motor": pf("block/pumpjack_motor"),
         "particle": pf("block/pumpjack_skid")}
    box = lambda frm, to, tex: {"from": frm, "to": to, "faces": {d: {"texture": tex} for d in six}}
    els = [box([-3, 0, -5], [19, 2, 22], "#pad"),
           box([5, 2, -5], [11, 4, 1], "#steel"), box([6, 4, -4], [10, 6, 0], "#steel"),
           box([7, 6, -3], [9, 8, -1], "#steel"),
           box([3, 2, 15], [13, 7, 21], "#motor"), box([2, 2, 7], [14, 3, 9], "#steel"),
           box([2, 3, 16], [4, 9, 18], "#steel"), box([12, 3, 16], [14, 9, 18], "#steel"),
           box([0, 2, 4], [2, 3, 13], "#steel"), box([14, 2, 4], [16, 3, 13], "#steel")]
    # Explicit UVs keep the extended skid within the atlas's 0..16 UV range.
    for el in els:
        for f in el["faces"].values():
            f["uv"] = [0, 0, 16, 16]
    write(asset("models", "block", "pumpjack.json"), {"parent": "minecraft:block/block", "textures": t, "elements": els,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.45, 0.45, 0.45]}}})
    # Refinery multiblock: controller (front-centre of a 3x3 casing base) and a three-block column.
    for formed in (False, True):
        suf = "_formed" if formed else ""
        casing = pf("block/refinery_casing" + suf)
        write(asset("models", "block", f"refinery_casing{suf}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": casing}})
        write(asset("models", "block", f"refinery{suf}.json"), {"parent": "minecraft:block/orientable", "textures": {
            "top": casing, "side": casing, "front": pf("block/refinery_controller_front")}})
    write(asset("models", "block", "refinery_on.json"), {"parent": pf("block/refinery_formed"), "textures": {
        "front": pf("block/refinery_controller_front_on")}})
    write(asset("blockstates", "refinery_casing.json"), {"variants": {f"formed={f}": {"model": pf("block/refinery_casing" + ("_formed" if f == "true" else ""))}
                                                                      for f in ("false", "true")}})
    item_def("refinery_casing")
    for formed in (False, True):
        suffix = "_formed" if formed else ""
        t = {"col": pf("block/refinery_column" + suffix), "steel": pf("block/pumpjack_wellhead"), "particle": pf("block/refinery_column")}
        els = octagon(10, 0, 16, "#col", caps=("up", "down"))
        els += octagon(12, 1, 2, "#steel", caps=("up", "down"))
        els += octagon(12, 14, 15, "#steel", caps=("up", "down"))
        # Service ladder and an external return pipe, joining seamlessly between column sections.
        els += [box([4, 0, 14], [5, 16, 15], "#steel"), box([11, 0, 14], [12, 16, 15], "#steel"),
                box([14, 0, 7], [15, 16, 9], "#steel")]
        els += [box([5, y, 14], [11, y + 1, 15], "#steel") for y in (2, 6, 10, 14)]
        write(asset("models", "block", "distillation_column" + suffix + ".json"), {"parent": "minecraft:block/block", "textures": t, "elements": els})
    write(asset("blockstates", "distillation_column.json"), {"variants": {f"formed={f}": {"model": pf("block/distillation_column" + ("_formed" if f == "true" else ""))}
                                                                          for f in ("false", "true")}})
    item_def("distillation_column")
    variants = {}
    for f, y in HORIZONTAL.items():
        for formed in ("false", "true"):
            for running in ("false", "true"):
                m = {"model": pf("block/refinery_on" if running == "true" and formed == "true" else "block/refinery" + ("_formed" if formed == "true" else ""))}
                if y:
                    m["y"] = y
                variants[f"facing={f},formed={formed},running={running}"] = m
    write(asset("blockstates", "refinery.json"), {"variants": variants})
    item_def("refinery")
    for b in ("refinery", "refinery_casing", "distillation_column"):
        drops_self(b)
    tag("block", "minecraft", "mineable/pickaxe", [pf("refinery_casing"), pf("distillation_column")])
    shaped("refinery_casing", ["PBP", "B B", "PBP"], {"P": "#c:plates/steel", "B": "minecraft:bricks"}, 4, category="building")
    shaped("distillation_column", ["PGP", "P P", "PGP"], {"P": "#c:plates/steel", "G": "#c:glass_panes"}, 2, category="building")
    name(f"block.{NS}.refinery_casing", "Refinery Casing")
    name(f"block.{NS}.distillation_column", "Distillation Column")
    for formed in (False, True):
        suffix = "_formed" if formed else ""
        write(asset("models", "block", "pumpjack_casing" + suffix + ".json"), {"parent": "minecraft:block/cube_all", "textures": {
            "all": pf("block/pumpjack_casing" + suffix)}})
    write(asset("blockstates", "pumpjack_casing.json"), {"variants": {f"formed={f}": {
        "model": pf("block/pumpjack_casing" + ("_formed" if f == "true" else ""))} for f in ("false", "true")}})
    item_def("pumpjack_casing")
    drops_self("pumpjack_casing")
    tag("block", "minecraft", "mineable/pickaxe", [pf("pumpjack_casing")])
    shaped("pumpjack_casing", ["PPP", "SCS", "PPP"], {"P": "#c:plates/steel", "S": "#c:ingots/steel", "C": "minecraft:smooth_stone"}, 4, category="building")
    name(f"block.{NS}.pumpjack_casing", "Pumpjack Foundation")
    LANG.update({
        "scada.powerfactor.multiblock.incomplete": "STRUCTURE INCOMPLETE",
        "gui.powerfactor.multiblock.incomplete": "STRUCTURE INCOMPLETE",
        "gui.powerfactor.multiblock.short": "NO FRAME",
        "gui.powerfactor.pumpjack.foundation": "Place nine Pumpjack Foundation blocks in a 3×3 layer directly below the pumpjack.",
        "gui.powerfactor.multiblock.hint": "Build the shape shown by the hologram",
        "gui.powerfactor.multiblock.auto_hint": "Sneak-use the controller with an empty hand or a structure part to build from your inventory.",
        "gui.powerfactor.multiblock.complete": "Structure is already complete",
        "gui.powerfactor.multiblock.built": "Placed %s structure parts",
        "gui.powerfactor.multiblock.blocked": "Build blocked: clear the hologram and check placement permissions",
        "gui.powerfactor.multiblock.materials": "Need %s more %s to build this structure",
        "gui.powerfactor.fuel": "Fuel",
        "scada.powerfactor.wind.short": "TOWER TOO SHORT: %s/%s sections",
    })
    for b in ("pumpjack",):
        write(asset("models", "block", f"{b}_top.json"), {"textures": {"particle": pf("block/steel_block")}})
        variants = {}
        for f, y in HORIZONTAL.items():
            for half in ("lower", "upper"):
                for running in ("false", "true"):
                    m = {"model": pf(f"block/{b}" if half == "lower" else f"block/{b}_top")}
                    if y and half == "lower":
                        m["y"] = y
                    variants[f"facing={f},half={half},running={running}"] = m
        variants = {f"{key},formed={formed}": model for key, model in variants.items() for formed in ("false", "true")}
        write(asset("blockstates", f"{b}.json"), {"variants": variants})
        item_def(b)
        write(data(NS, "loot_table", "blocks", f"{b}.json"), {"type": "minecraft:block", "random_sequence": pf(f"blocks/{b}"),
            "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:block_state_property", "block": pf(b), "properties": {"half": "lower"}}],
                "entries": [{"type": "minecraft:item", "name": pf(b)}]}]})
    tag("block", "minecraft", "mineable/pickaxe", [pf("pumpjack"), pf("refinery")])
    shaped("pumpjack", ["SBS", "GMG", "CPC"], {"S": "#c:ingots/steel", "B": pf("steel_gear"), "G": "#c:gears/steel",
                                                "M": pf("electric_motor"), "C": "minecraft:smooth_stone", "P": pf("electric_pump")}, 1, category="redstone")
    shaped("refinery", ["PTP", "PKP", "HFH"], {"P": "#c:plates/steel", "T": pf("fluid_tank"), "K": pf("control_circuit"),
                                               "H": pf("heating_coil"), "F": "minecraft:blast_furnace"}, 1, category="redstone")
    name(f"block.{NS}.pumpjack", "Pumpjack")
    name(f"block.{NS}.refinery", "Oil Refinery")
    LANG.update({
        "scada.powerfactor.pumpjack": "%s blocks of oil below · %s mB",
        "scada.powerfactor.pumpjack.dry": "DRY: no oil below",
        "scada.powerfactor.refinery": "Crude %s · diesel %s · jet %s mB",
        "gui.powerfactor.pumpjack.pumping": "PUMPING", "gui.powerfactor.pumpjack.idle": "IDLE", "gui.powerfactor.pumpjack.dry": "WELL DRY",
        "gui.powerfactor.pumpjack.blocks": "%s left",
        "gui.powerfactor.refinery.running": "DISTILLING", "gui.powerfactor.refinery.idle": "IDLE",
        "gui.powerfactor.fluid_amount": "%s: %s / %s mB",
        "gui.powerfactor.speed": "Speed",
        "gui.powerfactor.config.face_tip": "%s: %s (click to change, drag to turn)",
        "gui.powerfactor.config.face_name.front": "Front", "gui.powerfactor.config.face_name.back": "Back",
        "gui.powerfactor.config.face_name.left": "Left", "gui.powerfactor.config.face_name.right": "Right",
        "gui.powerfactor.config.face_name.top": "Top", "gui.powerfactor.config.face_name.bottom": "Bottom",
        "handbook.powerfactor.oil.title": "Oil",
        "handbook.powerfactor.oil.text":
            "Crude oil lies in reservoirs deep underground, and sometimes seeps up as tar pits in deserts and badlands. Build a "
            "Pumpjack above a 3x3 layer of nine Pumpjack Foundation blocks, over a reservoir (it searches straight down, then up to 8 blocks sideways): the nodding donkey "
            "lifts 25 mB of crude per tick for 60 FE/t until the reservoir runs dry.\n\nPipe the crude to an Oil Refinery, a multiblock: the "
            "controller sits front-centre of a 3x3 base of Refinery Casing, with three Distillation Column blocks stacked on the "
            "middle casing; a hologram shows what's missing. Sneak-use the controller with an empty hand or a structure part to build from inventory. "
            "The wind turbine can build its six tower sections the same way. Clear the hologram first; assembly never replaces obstructions. "
            "Cables and pipes attach to any casing. The distillation column splits 10 mB of crude into 5 mB of diesel and 4 mB of jet fuel every tick (100 FE/t). Diesel "
            "runs the Diesel Generator; jet fuel is available for future aircraft. Buckets work for all three.",
    })
    # Worldgen: deep reservoirs everywhere in the overworld, tar pits in dry biomes.
    oil_lake = {"type": "minecraft:lake", "config": {
        "barrier": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:stone"}},
        "can_place_feature": {"type": "minecraft:true"},
        "can_replace_with_air_or_fluid": {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:features_cannot_replace"}},
        "can_replace_with_barrier": {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:lava_pool_stone_cannot_replace"}},
        "fluid": {"type": "minecraft:simple_state_provider", "state": {"Name": pf("crude_oil"), "Properties": {"level": "0"}}}}}
    write(data(NS, "worldgen", "configured_feature", "oil_lake.json"), oil_lake)
    write(data(NS, "worldgen", "placed_feature", "oil_reservoir.json"), {"feature": pf("oil_lake"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -48}, "max_inclusive": {"absolute": 20}}},
        {"type": "minecraft:biome"}]})
    write(data(NS, "worldgen", "placed_feature", "tar_pit.json"), {"feature": pf("oil_lake"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 40}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    cond = [{"type": pf("worldgen_enabled"), "feature": "oil"}]
    write(data(NS, "neoforge", "biome_modifier", "add_oil_reservoirs.json"), {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": pf("oil_reservoir"), "step": "lakes", "neoforge:conditions": cond})
    write(data(NS, "neoforge", "biome_modifier", "add_tar_pits.json"), {"type": "neoforge:add_features",
        "biomes": {"type": "neoforge:or", "values": ["#c:is_desert", "#minecraft:is_badlands"]},
        "features": pf("tar_pit"), "step": "lakes", "neoforge:conditions": cond})


def industrial_building():
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to):
        return {"from": frm, "to": to, "faces": {f: {"texture": "#steel", "uv": [0, 0, 16, 16]} for f in six}}
    for axis in ("x", "y", "z"):
        elements = []
        for frm, to in (([3, 0, 3], [13, 16, 5]), ([3, 0, 11], [13, 16, 13]), ([7, 0, 5], [9, 16, 11])):
            if axis == "x":
                frm, to = [frm[1], frm[0], frm[2]], [to[1], to[0], to[2]]
            elif axis == "z":
                frm, to = [frm[0], frm[2], frm[1]], [to[0], to[2], to[1]]
            elements.append(box(frm, to))
        write(asset("models", "block", "steel_beam" + ("" if axis == "y" else "_" + axis) + ".json"), {
            "parent": "minecraft:block/block", "textures": {"steel": pf("block/industrial_steel"), "particle": pf("block/industrial_steel")},
            "elements": elements})
    write(asset("blockstates", "steel_beam.json"), {"variants": {
        "axis=" + axis: {"model": pf("block/steel_beam" + ("" if axis == "y" else "_" + axis))} for axis in ("x", "y", "z")}})
    item_def("steel_beam")
    grid = [box([0, 0, 0], [16, 2, 1]), box([0, 0, 15], [16, 2, 16]),
            box([0, 0, 1], [1, 2, 15]), box([15, 0, 1], [16, 2, 15])]
    for p in (3, 6, 9, 12):
        grid += [box([p, 0.5, 1], [p + 1, 1.5, 15]), box([1, 0.5, p], [15, 1.5, p + 1])]
    write(asset("models", "block", "steel_catwalk.json"), {"parent": "minecraft:block/block", "textures": {
        "steel": pf("block/industrial_steel"), "particle": pf("block/industrial_steel")}, "elements": grid})
    write(asset("blockstates", "steel_catwalk.json"), {"variants": {"": {"model": pf("block/steel_catwalk")}}})
    item_def("steel_catwalk")
    for block in ("riveted_steel", "hazard_block", "warning_block"):
        simple_block(block)
    blocks = ("steel_beam", "steel_catwalk", "riveted_steel", "hazard_block", "warning_block")
    for block in blocks:
        drops_self(block)
        name(f"block.{NS}.{block}", block.replace("_", " ").title())
    tag("block", "minecraft", "mineable/pickaxe", [pf(b) for b in blocks])
    tag("block", "minecraft", "needs_iron_tool", [pf(b) for b in blocks])
    shaped("steel_beam", ["SSS", " S ", "SSS"], {"S": "#c:ingots/steel"}, 8, category="building")
    shaped("steel_catwalk", ["BBB", "P P"], {"B": "minecraft:iron_bars", "P": "#c:plates/steel"}, 8, category="building")
    shaped("riveted_steel", ["PP", "PP"], {"P": "#c:plates/steel"}, 4, category="building")
    shaped("hazard_block", ["YBY", "SSS"], {"Y": "#c:dyes/yellow", "B": "#c:dyes/black", "S": pf("riveted_steel")}, 3, category="building")
    shaped("warning_block", [" Y ", "YRY", " S "], {"Y": "#c:dyes/yellow", "R": "#c:dyes/red", "S": pf("riveted_steel")}, 1, category="building")
    LANG["handbook.powerfactor.machines.text"] += (
        "\n\nMechanical Press: fit a Plate Press Die for ingots to plates, or a Gear Press Die for four ingots to a gear. Dies wear once per job and last 512 jobs. Mixer: copper and zinc dust to brass, copper and tin to bronze, "
        "or gold and silver to electrum; smelt the blended dust into ingots. Centrifuge: four dirt to a clay ball and sand, or four gravel "
        "to flint and two sand. Sawmill: a log to six planks and a stick. All four accept upgrades and side configuration, and support automatic input and output.")
    LANG["handbook.powerfactor.ores.text"] += (
        "\n\nFactory construction: Steel Beams form columns and girders (place against a face to choose their axis). Steel Catwalks are thin grated floors. "
        "Riveted Steel, Hazard Blocks and Warning Blocks finish walls and mark dangerous machinery. Find their crafting recipes in JEI.")


STEAM_PARTS = ["boiler_casing", "boiler_firebox", "steam_drum", "turbine_casing", "steam_rotor", "generator_magnet"]
THERMAL_MACHINES = ["steam_boiler", "steam_turbine", "cooling_tower", "geothermal_generator", "thermoelectric_generator", "rain_collector"]


def steam_plant():
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to, texture):
        return {"from": frm, "to": to, "faces": {f: {"texture": texture, "uv": [0, 0, 16, 16]} for f in six}}
    for part in STEAM_PARTS:
        write(asset("models", "block", part + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf("block/" + part)}})
        # The controller renderer builds the assembled vessel and skid as one model.
        # Rendering each part's frame left poles and instruments floating around the shell.
        write(asset("models", "block", part + "_formed.json"), {"parent": "minecraft:block/block", "textures": {
            "particle": pf("block/" + part)}, "elements": []})
        write(asset("blockstates", part + ".json"), {"variants": {f"formed={f}": {
            "model": pf("block/" + part + ("_formed" if f == "true" else ""))} for f in ("false", "true")}})
        item_def(part)
    for controller in ("steam_boiler", "steam_turbine"):
        casing = "boiler_casing" if controller == "steam_boiler" else "turbine_casing"
        for formed in (False, True):
            for running in (False, True):
                suffix = ("_formed" if formed else "") + ("_on" if running else "")
                textures = {"frame": pf("block/" + casing), "front": pf("block/" + controller + "_front" + ("_on" if running else "")),
                            "particle": pf("block/" + casing)}
                elements = [box([3, 0, 0], [13, 10, 6], "#frame"), box([7, 0, 6], [9, 16, 8], "#frame")] if formed else [box([0, 0, 0], [16, 16, 16], "#frame")]
                elements[0]["faces"]["north"]["texture"] = "#front"
                write(asset("models", "block", controller + suffix + ".json"), {"parent": "minecraft:block/block", "textures": textures, "elements": elements})
        variants = {}
        for facing, rotation in HORIZONTAL.items():
            for formed in ("false", "true"):
                for running in ("false", "true"):
                    suffix = ("_formed" if formed == "true" else "") + ("_on" if running == "true" else "")
                    variants[f"facing={facing},formed={formed},running={running}"] = {"model": pf("block/" + controller + suffix), "y": rotation}
        write(asset("blockstates", controller + ".json"), {"variants": variants})
        item_def(controller)
    for machine in ("geothermal_generator", "thermoelectric_generator"):
        orientable_machine(machine, bottom=machine + "_side")
    for on in (False, True):
        textures = {"body": pf("block/cooling_tower_side"), "metal": pf("block/workshop_steel"), "particle": pf("block/cooling_tower_side")}
        elements = [box([1, 0, 1], [15, 16, 15], "#body"), box([2, 16, 2], [4, 29, 14], "#body"),
                    box([12, 16, 2], [14, 29, 14], "#body"), box([4, 16, 2], [12, 29, 4], "#body"),
                    box([4, 16, 12], [12, 29, 14], "#body")]
        elements += octagon(14, 29, 32, "#metal", caps=(), uv=[0, 0, 16, 16])
        write(asset("models", "block", "cooling_tower" + ("_on" if on else "") + ".json"), {
            "parent": "minecraft:block/block", "textures": textures, "elements": elements,
            "display": {"gui": {"rotation": [25, 225, 0], "translation": [0, -3, 0], "scale": [0.45, 0.45, 0.45]}}})
    write(asset("models", "block", "cooling_tower_top.json"), {"textures": {"particle": pf("block/cooling_tower_side")}})
    variants = {}
    for facing, rotation in HORIZONTAL.items():
        for half in ("lower", "upper"):
            for running in ("false", "true"):
                model = "cooling_tower_top" if half == "upper" else "cooling_tower" + ("_on" if running == "true" else "")
                variants[f"facing={facing},half={half},running={running}"] = {"model": pf("block/" + model), "y": rotation if half == "lower" else 0}
    write(asset("blockstates", "cooling_tower.json"), {"variants": variants})
    item_def("cooling_tower")
    for on in (False, True):
        elements = [box([0, 0, 0], [16, 4, 16], "#body"), box([0, 4, 0], [2, 12, 16], "#body"),
                    box([14, 4, 0], [16, 12, 16], "#body"), box([2, 4, 0], [14, 12, 2], "#body"), box([2, 4, 14], [14, 12, 16], "#body")]
        write(asset("models", "block", "rain_collector" + ("_on" if on else "") + ".json"), {"parent": "minecraft:block/block",
            "textures": {"body": pf("block/rain_collector_side"), "particle": pf("block/rain_collector_side")}, "elements": elements})
    machine_states("rain_collector", pf("block/rain_collector"), pf("block/rain_collector_on"))
    item_def("rain_collector")
    pipe_models("steam_pipe")
    glass_pipe("steam_pipe")
    for block in STEAM_PARTS + THERMAL_MACHINES + ["steam_pipe"]:
        if block != "cooling_tower": drops_self(block)
        name(f"block.{NS}.{block}", block.replace("_", " ").title())
    write(data(NS, "loot_table", "blocks", "cooling_tower.json"), {"type": "minecraft:block", "pools": [{"rolls": 1,
        "conditions": [{"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:block_state_property", "block": pf("cooling_tower"), "properties": {"half": "lower"}}],
        "entries": [{"type": "minecraft:item", "name": pf("cooling_tower")}]}]})
    tag("block", "minecraft", "mineable/pickaxe", [pf(b) for b in STEAM_PARTS + THERMAL_MACHINES + ["steam_pipe"]])
    tag("block", "minecraft", "needs_iron_tool", [pf(b) for b in STEAM_PARTS + THERMAL_MACHINES])
    tag("fluid", "c", "steam", [pf("steam"), pf("flowing_steam")])
    tag("fluid", "c", "hot_water", [pf("hot_water"), pf("flowing_hot_water")])
    for gas in ("steam", "hot_water"):
        write(asset("models", "block", gas + ".json"), {"textures": {"particle": pf("block/" + gas + "_still")}})
        name(f"fluid.{NS}.{gas}", gas.replace("_", " ").title())
    shaped("boiler_casing", ["PPP", "B B", "PPP"], {"P": "#c:plates/steel", "B": "minecraft:bricks"}, 4, category="building")
    shaped("turbine_casing", ["PPP", "G G", "PPP"], {"P": "#c:plates/steel", "G": "#c:glass_blocks"}, 4, category="building")
    shaped("boiler_firebox", ["PBP", "HFH", "PBP"], {"P": "#c:plates/steel", "B": "minecraft:bricks", "H": pf("heating_coil"), "F": "minecraft:furnace"})
    shaped("steam_drum", ["PPP", "PTP", "PPP"], {"P": "#c:plates/steel", "T": pf("fluid_tank")})
    shaped("steam_rotor", [" B ", "BGB", " B "], {"B": pf("turbine_blade"), "G": "#c:gears/steel"})
    shaped("generator_magnet", ["WCW", "CIC", "WCW"], {"W": pf("fine_copper_wire"), "C": pf("copper_coil"), "I": "#c:ingots/iron"})
    shaped("steam_boiler", ["PKP", "BFB", "PTP"], {"P": "#c:plates/steel", "K": pf("advanced_circuit"), "B": pf("boiler_casing"), "F": pf("boiler_firebox"), "T": pf("steam_drum")})
    shaped("steam_turbine", ["PKP", "BRB", "PMP"], {"P": "#c:plates/steel", "K": pf("advanced_circuit"), "B": pf("turbine_casing"), "R": pf("steam_rotor"), "M": pf("generator_magnet")})
    shaped("steam_pipe", ["PGP"], {"P": "#c:plates/steel", "G": "#c:glass_blocks"}, 8)
    shaped("cooling_tower", ["PGP", "MTM", "PKP"], {"P": "#c:plates/steel", "G": "minecraft:iron_bars", "M": pf("electric_motor"), "T": pf("fluid_tank"), "K": pf("control_circuit")})
    shaped("geothermal_generator", ["PCP", "HTH", "PKP"], {"P": "#c:plates/steel", "C": pf("copper_coil"), "H": pf("heating_coil"), "T": pf("fluid_tank"), "K": pf("control_circuit")})
    shaped("thermoelectric_generator", ["PWP", "CHC", "PKP"], {"P": "#c:plates/iron", "W": pf("fine_copper_wire"), "C": pf("copper_coil"), "H": pf("heating_coil"), "K": pf("control_circuit")})
    shaped("rain_collector", ["P P", "PTP", "PFP"], {"P": "#c:plates/iron", "T": "minecraft:cauldron", "F": pf("fluid_pipe")})
    LANG.update({
        "gui.powerfactor.heat": "Heat", "gui.powerfactor.pressure": "Pressure",
        "gui.powerfactor.boiler.tripped": "THERMAL TRIP", "gui.powerfactor.boiler.boiling": "MAKING STEAM", "gui.powerfactor.boiler.warming": "WARMING UP",
        "gui.powerfactor.boiler.dry": "NO WATER", "gui.powerfactor.boiler.off": "BURNER OFF",
        "gui.powerfactor.boiler.pressure_hold": "PRESSURE HOLD", "scada.powerfactor.boiler.pressure_hold": "Pressure hold · burner paused",
        "gui.powerfactor.boiler.trip_help": "Stop the burner, supply water and clear the steam outlet. The trip resets after the vessel cools below 70 °C.",
        "gui.powerfactor.boiler.help": "Cold water and fuel in; steam out. The burner pauses at 60% pressure and resumes at 40%. Cool turbine condensate before returning it.",
        "scada.powerfactor.boiler": "%s °C · steam %s mB · %s mB/t", "scada.powerfactor.boiler.tripped": "THERMAL TRIP: cool and clear the outlet",
        "scada.powerfactor.steam_turbine": "%s RPM · steam %s · hot water %s mB",
        "gui.powerfactor.steam_turbine.backed_up": "CONDENSATE FULL", "gui.powerfactor.steam_turbine.no_steam": "NO STEAM",
        "gui.powerfactor.steam_turbine.idling": "SYNCHRONIZED",
        "gui.powerfactor.cooling.blocked": "VENT BLOCKED", "gui.powerfactor.cooling.running": "COOLING", "gui.powerfactor.cooling.idle": "STANDBY",
        "scada.powerfactor.cooling.blocked": "VENT BLOCKED: clear the space above the tower", "scada.powerfactor.cooling": "%s mB/t cooled · cold water %s mB",
        "gui.powerfactor.geothermal.no_heat": "NO HEAT SOURCE", "gui.powerfactor.thermoelectric.no_gradient": "NO TEMPERATURE GAP",
        "scada.powerfactor.geothermal": "Heat source available: %s FE/t", "scada.powerfactor.thermoelectric": "Thermal gradient available: %s FE/t",
        "gui.powerfactor.thermal.available": "Max %s FE/t", "gui.powerfactor.rain.collecting": "COLLECTING", "gui.powerfactor.rain.waiting": "WAITING FOR RAIN",
        "scada.powerfactor.rain_collector": "%s mB/t collected · water %s mB",
        "subtitles.powerfactor.boiler_run": "Boiler burns and hisses", "subtitles.powerfactor.steam_turbine_run": "Steam turbine spins",
        "subtitles.powerfactor.cooling_tower_run": "Cooling fan hums",
    })
    LANG["handbook.powerfactor.generators.text"] += (
        "\n\nSteam power: form a 3x3x3 Steam Boiler with 23 Boiler Casings, one Firebox and two Steam Drums. Feed cold water and furnace fuel through its controller or formed ports. "
        "Once hot it produces up to 80 mB/t of steam. Steam Pipes carry only steam, up to 4000 mB per extraction every ten ticks; sneak-use an endpoint to extract, as with fluid pipes. "
        "The 3x2x5 Steam Turbine needs 26 Turbine Casings, two Steam Rotors and a Generator Magnet. It synchronizes over ten seconds and generates up to 800 FE/t, with EE support. "
        "Use the hologram or sneak-use assembly for either structure. Hot condensate must leave the turbine: pipe it through a powered Cooling Tower, then return its cold water to the boiler. "
        "Cooling towers need an open vent to the sky. The boiler pauses its burner at 60% pressure and resumes at 40%; blocked outlets hold safely. Dry firing can trip it: supply water and let it cool.\n\n"
        "Geothermal Generator: any adjacent lava gives 64 FE/t; magma gives 24. Thermoelectric Generator: put a hot source and water or ice on opposite faces, for up to 48 FE/t. "
        "A Rain Collector catches 2 mB/t of actual rain, or 6 during thunderstorms, without FE. A roof stops collection. Pipe its water into your boiler to replenish the loop.")


def power_armor():
    for item in ("power_helmet", "power_chestplate", "power_leggings", "power_boots", "jump_servo_module", "shock_absorber_module", "night_vision_module", "rebreather_module"):
        flat_item(item)
        name(f"item.{NS}.{item}", item.replace("_", " ").title())
    write(asset("equipment", "power_armor.json"), {"layers": {
        "humanoid": [{"texture": pf("power_armor")}], "humanoid_leggings": [{"texture": pf("power_armor")}]}})
    shaped("power_helmet", ["PSP", "CIC", " P "], {"P": "#c:plates/steel", "S": pf("sensor_chip"), "C": pf("capacitor"), "I": "minecraft:iron_helmet"})
    shaped("power_chestplate", ["PMP", "CIC", "PPP"], {"P": "#c:plates/steel", "M": pf("microprocessor"), "C": pf("capacitor"), "I": "minecraft:iron_chestplate"})
    shaped("power_leggings", ["PKP", "MIM", "P P"], {"P": "#c:plates/steel", "K": pf("advanced_circuit"), "M": pf("electric_motor"), "I": "minecraft:iron_leggings"})
    shaped("power_boots", ["P P", "MIM", "CSC"], {"P": "#c:plates/steel", "M": pf("electric_motor"), "I": "minecraft:iron_boots", "C": pf("capacitor"), "S": pf("sensor_chip")})
    shaped("jump_servo_module", [" M ", "PKP", " M "], {"M": pf("electric_motor"), "P": "#c:plates/steel", "K": pf("control_circuit")})
    shaped("shock_absorber_module", ["RPR", "PCP", "RPR"], {"R": "#c:rubbers", "P": "#c:plates/steel", "C": pf("capacitor")})
    shaped("night_vision_module", ["GSG", "PKP", " W "], {"G": "#c:glass_blocks", "S": pf("sensor_chip"), "P": "#c:plates/steel", "K": pf("advanced_circuit"), "W": pf("fine_copper_wire")})
    shaped("rebreather_module", ["PFP", "RKR", " P "], {"P": "#c:plates/steel", "F": pf("fluid_tank"), "R": "#c:rubbers", "K": pf("control_circuit")})
    armor = [pf(i) for i in ("power_helmet", "power_chestplate", "power_leggings", "power_boots")]
    tag("item", "minecraft", "enchantable/armor", armor)
    tag("item", "minecraft", "armor", armor)
    for role, item in (("head", "power_helmet"), ("chest", "power_chestplate"), ("leg", "power_leggings"), ("foot", "power_boots")):
        tag("item", "minecraft", role + "_armor", [pf(item)])
    LANG.update({
        "key.powerfactor.armor_settings": "Power Armor Settings",
        "tooltip.powerfactor.armor.shield": "Powered shield: absorbs up to %s%% of the hit · consumes FE",
        "tooltip.powerfactor.armor.helmet": "Helmet accepts night vision and rebreather modules",
        "tooltip.powerfactor.armor.chest": "Fit an Electric Jetpack in an upgrade-table socket",
        "tooltip.powerfactor.armor.legs": "Powered servos: speed, higher jumps and one-block steps; sneak for normal movement",
        "tooltip.powerfactor.armor.boots": "Powered landing cushion; shock modules absorb longer falls",
        "tooltip.powerfactor.armor.jetpack": "Integrated jetpack · flight draws from the chest battery",
        "tooltip.powerfactor.armor.controls": "B opens suit settings · charge at a Charger or Charging Pad",
        "tooltip.powerfactor.drill_module.jump": "+0.08 jump strength per module · uses more FE per jump",
        "tooltip.powerfactor.drill_module.shock": "+3 blocks of landing absorption per module",
        "tooltip.powerfactor.drill_module.night_vision": "Powered night vision · 4 FE/t before efficiency",
        "tooltip.powerfactor.drill_module.rebreather": "Restores air underwater · 2 FE/t before efficiency",
        "tooltip.powerfactor.drill_module.speed": "+50% tool speed; +15% exoskeleton movement speed, each",
        "tooltip.powerfactor.drill_module.plating": "+2 jetpack armor or +2% powered-suit shielding, each",
        "gui.powerfactor.armor.hud": "POWER ARMOR",
        "gui.powerfactor.armor.title": "POWER ARMOR · SUIT CONTROL",
        "gui.powerfactor.armor.help": "Charge batteries · fit modules at an Upgrade Table",
        "gui.powerfactor.armor.helmet": "Helmet", "gui.powerfactor.armor.chest": "Chest plate",
        "gui.powerfactor.armor.legs": "Leggings", "gui.powerfactor.armor.boots": "Boots",
        "gui.powerfactor.armor.helmet.short": "HEAD", "gui.powerfactor.armor.chest.short": "BODY",
        "gui.powerfactor.armor.legs.short": "LEGS", "gui.powerfactor.armor.boots.short": "FEET",
        "gui.powerfactor.armor.empty": "%s · not equipped",
        "gui.powerfactor.armor.assists": "Movement assists: %s", "gui.powerfactor.armor.visor": "Night vision: %s",
        "gui.powerfactor.armor.hover": "Jetpack hover: %s", "gui.powerfactor.armor.helmet.modules": "Night vision %s · Breathing %s",
        "gui.powerfactor.armor.chest.jetpack": "Jetpack fitted · Jump climbs · %s toggles hover",
        "gui.powerfactor.armor.chest.socket": "Fit an Electric Jetpack in the Upgrade Table",
        "gui.powerfactor.armor.legs.stats": "Speed +%s%% · Jump %s · Step +0.5",
        "gui.powerfactor.armor.boots.stats": "Absorbs up to %s blocks of a fall · uses FE",
    })
    for role in ("helmet", "chest", "legs", "boots"):
        LANG[f"tool_kind.{NS}.power_{role}"] = "Power Armor " + {"helmet": "Helmet", "chest": "Chest Plate", "legs": "Leggings", "boots": "Boots"}[role]
        LANG[f"tool_kind.{NS}.power_{role}.short"] = {"helmet": "POWER HELMET", "chest": "POWER CHEST", "legs": "EXOSKELETON", "boots": "POWER BOOTS"}[role]
    LANG["handbook.powerfactor.power_tools.text"] += (
        "\n\nPower Armor: helmet, chest plate, exoskeleton leggings and boots have individual FE batteries. Charge them in a Charger or by standing on a powered Charging Pad. "
        "The hull supplies iron-level armor without power. Each charged piece shields 8% of damage remaining after armor, paying 120 FE per point absorbed; Plating adds 2% per module, capped at 50% for the suit. "
        "Leg servos add 25% movement speed, higher jumps and one-block steps. Speed modules add 15% each; Jump Servo modules add height. Sneak or disable movement assists to move normally. "
        "Boots spend FE to cushion five blocks of a fall beyond the safe distance; Shock Absorbers add three blocks each. Empty batteries stop powered abilities. "
        "Open suit settings with B to see batteries and toggle movement assists, night vision or jetpack hover.\n\n"
        "Put a chest plate in the Upgrade Table and fit an actual Electric Jetpack into a socket. It remains removable with its stored charge and upgrades; flight uses the chest battery. "
        "Thruster and Efficiency modules in the chest and fitted jetpack combine up to the usual three-module limit. Hold Jump to climb, J toggles hover by default and Sneak descends. "
        "All armor takes Battery, Efficiency and Plating modules. Helmets also take Night Vision and Rebreather modules for powered vision and underwater air. "
        "The helmet utilities, locomotion, landing cushion and shield all consume FE; keep an eye on the four-battery HUD.")


def movement_kit():
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to, texture):
        return {"from": frm, "to": to, "faces": {face: {"texture": texture, "uv": [0, 0, 16, 16]} for face in six}}
    write(asset("models", "item", "grapple_hook.json"), {"parent": "minecraft:block/block", "textures": {
        "body": pf("block/grapple_body"), "spool": pf("block/grapple_spool"), "particle": pf("block/grapple_body")}, "elements": [
        box([4, 5, 2], [12, 10, 14], "#body"), box([3, 6, 4], [13, 9, 10], "#spool"),
        box([6, 10, 3], [10, 13, 11], "#body"), box([5, 6, 0], [11, 9, 2], "#body")], "display": {
        "gui": {"rotation": [30, 225, 0], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.2, 3.4, 1], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.2, 3.4, 1], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_righthand": {"rotation": [-90, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_lefthand": {"rotation": [-90, 0, 0], "translation": [0, 2, 0], "scale": [0.7, 0.7, 0.7]}}})
    item_def("grapple_hook", pf("item/grapple_hook")); flat_item("wingsuit")
    shaped("grapple_hook", ["PIP", "WMW", "CKC"], {"P": "#c:plates/steel", "I": pf("iron_gear"), "W": pf("fine_aluminum_wire"),
        "M": pf("electric_motor"), "C": pf("capacitor"), "K": pf("control_circuit")})
    shaped("wingsuit", ["SLS", "WRW", " S "], {"S": "minecraft:string", "L": "minecraft:leather", "W": "#minecraft:wool", "R": "#c:plates/aluminum"})
    LANG.update({
        "item.powerfactor.grapple_hook": "Powered Grapple", "item.powerfactor.wingsuit": "Wingsuit",
        "entity.powerfactor.grapple_hook": "Grapple Hook",
        "key.powerfactor.grapple": "Grapple: Cast / Retract", "key.powerfactor.wingsuit": "Wingsuit: Deploy / Fold",
        "tool_kind.powerfactor.grapple": "Powered Grapple", "tool_kind.powerfactor.grapple.short": "GRAPPLE",
        "tooltip.powerfactor.grapple.range": "%s-block cable · powered reel",
        "tooltip.powerfactor.grapple.controls": "G or right-click casts / retracts · Jump releases",
        "tooltip.powerfactor.grapple.swing": "Reels by default · hold Sneak to swing with a fixed cable",
        "tooltip.powerfactor.wingsuit.controls": "U deploys / folds in the air · Sneak airbrakes",
        "tooltip.powerfactor.wingsuit.momentum": "Dive for speed · look up to trade speed for lift · A/D bank",
        "tooltip.powerfactor.wingsuit.socket": "Wear in the chest slot, or fit a Power Chestplate socket",
        "tooltip.powerfactor.armor.wingsuit": "Wingsuit fitted · deploy in the air with U",
        "gui.powerfactor.grapple.ready": "GRAPPLE READY", "gui.powerfactor.grapple.firing": "FIRING",
        "gui.powerfactor.grapple.reel": "REELING", "gui.powerfactor.grapple.swing": "SWING",
        "gui.powerfactor.grapple.hud_help": "Jump off · Sneak swing",
        "gui.powerfactor.wingsuit.open": "GLIDING", "gui.powerfactor.wingsuit.ready": "WINGSUIT READY",
        "gui.powerfactor.wingsuit.hud_help": "%s deploy · Sneak brake",
        "gui.powerfactor.armor.hover": "Hover: %s", "gui.powerfactor.armor.glide": "Wingsuit: %s",
        "gui.powerfactor.armor.chest.both": "Jetpack + wingsuit · %s hover · %s glide",
        "gui.powerfactor.armor.chest.wingsuit": "Wingsuit fitted · %2$s deploys / folds",
        "gui.powerfactor.armor.chest.socket": "Fit a jetpack and/or wingsuit in the Upgrade Table",
        "tooltip.powerfactor.drill_module.speed": "+50% tool speed; +15% leg speed; +20% grapple reel speed, each",
        "subtitles.powerfactor.grapple_fire": "Grapple launches", "subtitles.powerfactor.grapple_attach": "Grapple catches",
        "subtitles.powerfactor.wingsuit_open": "Wingsuit unfolds", "subtitles.powerfactor.wingsuit_wind": "Air rushes past wingsuit",
    })
    LANG["handbook.powerfactor.power_tools.text"] += (
        "\n\nMovement kit: the Powered Grapple has a 64-block cable and 120,000 FE battery. G casts or retracts from your inventory; right-click works when held. "
        "The hook attaches to solid terrain or a moving entity. It reels you toward the anchor for 12 FE/t; hold Sneak to stop the reel and swing on a fixed cable. "
        "Jump releases without killing momentum. Broken anchors, obstructed cables, excessive range, empty batteries or dropping the device release the hook. "
        "Fit Battery, Efficiency or Speed modules in the Upgrade Table to improve the grapple.\n\n"
        "A Wingsuit can be worn directly or fitted into a Power Chestplate socket alongside a jetpack. Press U in the air to deploy or fold. "
        "Look down to dive for speed; look up to trade speed for lift. A/D bank and Sneak airbrakes. Low airspeed stalls: the wingsuit needs altitude and momentum. "
        "Release a swinging or reeling grapple into a glide to keep your speed. A fitted jetpack can regain altitude: Jump folds the wingsuit and resumes thrust. "
        "Wingsuit fabric folds smoothly, stretches between the arms and hips, and has a leg membrane. Its gliding pose, camera bank and wind sound follow motion. "
        "The suit screen controls both flight modes, and the movement HUD shows cable mode, charge, distance and airspeed.")


def electric_rover():
    six = ("north", "east", "south", "west", "up", "down")
    def box(frm, to, texture):
        return {"from": frm, "to": to, "faces": {d: {"texture": texture, "uv": [0, 0, 16, 16]} for d in six}}
    write(asset("models", "item", "electric_rover.json"), {"parent": "minecraft:block/block", "textures": {
        "body": pf("block/rover_body"), "tire": pf("block/rover_tire"), "particle": pf("block/rover_body")}, "elements": [
        box([2, 4, 1], [14, 6, 15], "#body"), box([3, 6, 1], [13, 9, 5], "#body"), box([5, 6, 9], [11, 11, 11], "#body"),
        box([1, 2, 2], [4, 7, 6], "#tire"), box([12, 2, 2], [15, 7, 6], "#tire"),
        box([1, 2, 10], [4, 7, 14], "#tire"), box([12, 2, 10], [15, 7, 14], "#tire"),
        box([2, 6, 10], [3, 14, 11], "#body"), box([13, 6, 10], [14, 14, 11], "#body"), box([2, 13, 10], [14, 14, 11], "#body")],
        "display": {"gui": {"rotation": [25, 225, 0], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_righthand": {"rotation": [0, 90, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}})
    item_def("electric_rover", pf("item/electric_rover"))
    shaped("electric_rover", ["PCP", "MBM", "RGR"], {"P": "#c:plates/steel", "C": pf("control_circuit"),
        "M": pf("electric_motor"), "B": pf("capacitor"), "R": "#c:rubbers", "G": pf("steel_gear")})
    LANG.update({"item.powerfactor.electric_rover": "Electric Rover", "entity.powerfactor.electric_rover": "Electric Rover",
        "tooltip.powerfactor.rover.controls": "W/S drive · A/D steer · Jump brake · Sprint boost",
        "tooltip.powerfactor.rover.pack": "Sneak-wrench to pack; charge on a Charging Pad",
        "tooltip.powerfactor.rover.hull": "Hull: %s%% · steel ingot repairs 10 HP",
        "gui.powerfactor.rover.title": "ELECTRIC ROVER", "gui.powerfactor.rover.help": "Jump brake · Sprint boost",
        "gui.powerfactor.rover.braking": "BRAKES · Sneak to exit", "gui.powerfactor.rover.empty": "BATTERY EMPTY · Coast to a pad",
        "gui.powerfactor.rover.hull": "Hull %s%% · Sneak exits", "subtitles.powerfactor.rover_motor": "Rover motor whines"})
    LANG["handbook.powerfactor.power_tools.text"] += (
        "\n\nThe Electric Rover is a fast single-seat off-road vehicle with a 500,000 FE battery. Place it on clear ground and right-click to sit. "
        "W/S accelerate and reverse, A/D steer, Jump brakes and Sprint boosts. Sneak exits. It climbs one-block steps using collision handling, "
        "and brakes automatically if the driver leaves or input stops. Drive uses 20 FE/t, boost 50 FE/t. Empty batteries coast but cannot accelerate. "
        "Park above a powered Charging Pad to recharge; the packed vehicle also charges as an FE item. Sneak-use a wrench on a stopped, empty rover "
        "to pack it without losing battery or hull state. Steel ingots repair 10 HP each. The chassis has 80 HP; a destroyed rover becomes scrap.")


def workshop_progression_polish():
    for tool in ("forge_hammer", "wire_cutters"):
        flat_item(tool, "minecraft:item/handheld")
    shaped("forge_hammer", ["II ", "ISI", " S "], {"I": "#c:ingots/iron", "S": "#c:rods/wooden"}, category="equipment")
    shaped("wire_cutters", ["I I", " I ", "S S"], {"I": "#c:ingots/iron", "S": "#c:rods/wooden"}, category="equipment")
    tag("item", "c", "tools/hammers", [pf("forge_hammer")])
    tag("item", "c", "tools/wire_cutters", [pf("wire_cutters")])
    tag("item", "c", "tools", ["#c:tools/hammers", "#c:tools/wire_cutters"])
    tag("item", "c", "wires", ["#c:wires/copper", "#c:wires/silver", "#c:wires/aluminum"])
    for metal in ("copper", "silver", "aluminum"):
        shapeless("fine_" + metal + "_wire", ["#c:plates/" + metal, "#c:tools/wire_cutters"], 2, file="fine_" + metal + "_wire_by_hand")
    shaped("copper_coil", ["WWW", "WIW", "WWW"], {"W": "#c:wires/copper", "I": "#c:ingots/iron"}, file="copper_coil_from_fine")
    shapeless("rubber", ["#c:slime_balls", "minecraft:clay_ball", "#minecraft:coals"], 2, file="rubber_from_slime")
    LANG.update({"item.powerfactor.forge_hammer": "Forge Hammer", "item.powerfactor.wire_cutters": "Wire Cutters",
        "tooltip.powerfactor.forge_hammer": "Craft with two ingots to flatten one plate",
        "tooltip.powerfactor.wire_cutters": "Craft with a plate to cut wire; right-click cable to dismantle",
        "tooltip.powerfactor.crafting_tool": "%s workshop uses remaining · returned after crafting",
        "gui.powerfactor.config.preview_tip": "%s: %s · Drag to turn"})
    LANG["handbook.powerfactor.machines.text"] += (
        "\n\nEarly workshop: craft a Forge Hammer and Wire Cutters from iron and sticks. Two ingots plus a hammer make one plate; "
        "a plate plus cutters makes two wires. Both tools return to the grid and wear once per operation. Machines improve material yield: "
        "the Rolling Mill or Mechanical Press makes a plate from one ingot, and the Wire Drawer makes three fine wires from one ingot. "
        "Heat four coal dust in the Alloy Furnace to make starting graphite for electrodes. The first Arc Furnace uses iron plates "
        "and a control circuit, so its own steel and silicon are not needed to build it.\n\n"
        "Mix a slimeball, clay ball and coal or charcoal to make two rubber. Natural latex remains renewable. Rubber from other mods "
        "can substitute in tagged insulation recipes. Wind turbines now need three blades, two motors, a generator magnet, two steel blocks "
        "and an advanced circuit; tower sections craft in pairs.")


if __name__ == "__main__":
    models()
    loot()
    recipes()
    tags()
    worldgen()
    phase_b()
    phase_cf()
    finishing()
    devices()
    for w in WIRE_STYLE:
        wire(w)
    phase_h()
    phase_i()
    phase_radio()
    phase_oil()
    industrial_building()
    steam_plant()
    power_armor()
    movement_kit()
    electric_rover()
    workshop_progression_polish()
    from machine_models import generate as polish_models
    polish_models(globals())
    flush_tags()
    lang()
    print("data written to", os.path.normpath(ROOT))
