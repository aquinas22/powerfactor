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
    orientable_machine("crusher", bottom="crusher_side")
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
FOREIGN_METALS = ["tin", "lead", "silver", "nickel", "aluminum", "osmium", "uranium", "platinum"]


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
    shaped("copper_wire", ["CCC"], {"C": "#c:ingots/copper"}, 8, category="redstone")
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

def tag(registry, ns, path, values):
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
             "electric_drill": "Electric Drill"}
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
        "gui.powerfactor.config.sides": "Sides · R-click: back",
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
        "gui.powerfactor.autocap.no_grid": "NO GRID: connect to wire",
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


if __name__ == "__main__":
    models()
    loot()
    recipes()
    tags()
    worldgen()
    lang()
    print("data written to", os.path.normpath(ROOT))
