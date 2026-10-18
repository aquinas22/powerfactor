"""Ore multiplication data: the Wash Plant line and the chemistry route (models, states, loot, recipes, text).

Called from gen_data.py with its globals.
"""
METALS = ("iron", "gold", "copper", "zinc", "tin", "lead", "silver", "aluminum")   # chem/OreMetal.java
INGOT = {"iron": "minecraft:iron_ingot", "gold": "minecraft:gold_ingot", "copper": "minecraft:copper_ingot"}


def process_line(g):
    from storage_data import box, FACING, SIDES
    write, asset, data, pf, name, item_def, flat_item, shaped, drops_self, tag, LANG, cooking, machine_recipe, out, crushing = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "tag", "LANG", "cooking", "machine_recipe", "out",
        "crushing"))
    T = {"yellow": "mining_yellow", "galv": "galvanized_steel", "riffle": "sluice_riffle", "mesh": "trommel_mesh", "hopper": "hopper_steel",
         "deck": "shaker_deck", "glass": "chem_glass", "hazard": "acid_hazard", "dark": "industrial_dark", "chrome": "finish_chrome",
         "lit": "storage_casing_lit", "steel": "industrial_steel", "particle": "mining_yellow"}

    def model(model_name, elements, render="minecraft:cutout", particle="yellow"):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used}
        tex["particle"] = pf("block/" + T[particle])
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render, "textures": tex,
                                                                "elements": elements})

    def horizontal(block, models, extra=("running",)):
        """facing x (running/formed) variants; models maps a state suffix key to a model name."""
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            keys = [{}]
            for prop in extra:
                keys = [dict(k, **{prop: v}) for k in keys for v in ("false", "true")]
            for k in keys:
                props = ",".join(f"{p}={k[p]}" for p in sorted(k))
                state = f"facing={facing}" + ("," + props if props else "")
                variants[",".join(sorted(state.split(",")))] = dict({"model": pf("block/" + models(k))}, **rot)
        write(asset("blockstates", block + ".json"), {"variants": variants})

    def formed_states(block, off, on):
        write(asset("blockstates", block + ".json"), {"variants": {"formed=false": {"model": pf("block/" + off)},
                                                                   "formed=true": {"model": pf("block/" + on)}}})

    # ------------------------------------------------------------ wash plant
    legs = [box([1, 0, 1], [3, 9, 3], "yellow"), box([13, 0, 1], [15, 9, 3], "yellow"), box([1, 0, 13], [3, 9, 13 + 2], "yellow"),
            box([13, 0, 13], [15, 9, 15], "yellow")]
    table = legs + [box([0.5, 7, 0.5], [15.5, 9, 15.5], "yellow", {"up": "galv"}), box([4, 3, 4], [12, 7, 12], "dark", {"north": "steel"}),
                    box([0, 9, 0], [16, 11, 1], "galv"), box([0, 9, 15], [16, 11, 16], "galv")]
    model("wash_plant", table + [box([1, 9, 1], [15, 9.8, 15], "deck", full=("up",))])
    model("wash_plant_formed", table)
    horizontal("wash_plant", lambda k: "wash_plant_formed" if k.get("formed") == "true" else "wash_plant", ("formed", "running"))
    model("wash_plant_frame", [box([4, 0, 4], [12, 16, 12], "yellow", {"up": "galv", "down": "galv"}, full=SIDES),
                               box([3, 14, 3], [13, 16, 13], "galv")])
    formed_states("wash_plant_frame", "wash_plant_frame", "wash_plant_frame")
    sluice = [box([1, 5, 0], [15, 6, 16], "galv", {"up": "riffle"}, full=("up",)),
              box([1, 6, 0], [2, 9, 16], "galv"), box([14, 6, 0], [15, 9, 16], "galv"),
              box([2, 0, 6], [4, 5, 10], "yellow"), box([12, 0, 6], [14, 5, 10], "yellow")]
    model("sluice_box", sluice)
    # Formed, the Wash Plant renderer draws the whole line as one continuous incline instead.
    write(asset("models", "block", "sluice_box_formed.json"), {"parent": "minecraft:block/block",
                                                               "textures": {"particle": pf("block/galvanized_steel")}, "elements": []})
    formed_states("sluice_box", "sluice_box", "sluice_box_formed")
    cradle = [box([2, 0, 1], [14, 2, 4], "yellow"), box([2, 0, 12], [14, 2, 15], "yellow"),
              box([3, 2, 1.5], [5, 4, 3.5], "chrome"), box([11, 2, 1.5], [13, 4, 3.5], "chrome"),
              box([3, 2, 12.5], [5, 4, 14.5], "chrome"), box([11, 2, 12.5], [13, 4, 14.5], "chrome")]
    model("trommel", cradle + [box([3, 3, 0], [13, 13, 16], "mesh", full=SIDES)])
    # Formed: side rails carrying four trunnion rollers that cradle the drum the renderer turns above the sluice.
    model("trommel_formed", [box([0, 4, 0], [1, 8, 16], "yellow"), box([15, 4, 0], [16, 8, 16], "yellow"),
                             box([1, 7, 1.5], [3.5, 9.5, 4], "chrome"), box([12.5, 7, 1.5], [15, 9.5, 4], "chrome"),
                             box([1, 7, 12], [3.5, 9.5, 14.5], "chrome"), box([12.5, 7, 12], [15, 9.5, 14.5], "chrome")])
    formed_states("trommel", "trommel", "trommel_formed")
    model("feed_hopper", [box([0, 10, 0], [16, 16, 16], "hopper", {"up": "dark"}), box([3, 4, 3], [13, 10, 13], "hopper"),
                          box([6, 0, 6], [10, 4, 10], "galv")], particle="hopper")
    formed_states("feed_hopper", "feed_hopper", "feed_hopper")
    for b, m in (("wash_plant", "wash_plant"), ("wash_plant_frame", "wash_plant_frame"), ("sluice_box", "sluice_box"), ("trommel", "trommel"),
                 ("feed_hopper", "feed_hopper")):
        item_def(b, pf("block/" + m))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("wash_plant", ["MPM", "GCG", "SDS"], {"M": pf("electric_motor"), "P": "#c:plates/steel", "G": pf("brass_gear"),
                                                 "C": pf("advanced_circuit"), "S": "#c:ingots/steel", "D": pf("sluice_box")})
    shaped("trommel", ["PBP", "BMB", "PBP"], {"P": "#c:plates/steel", "B": "minecraft:iron_bars", "M": pf("electric_motor")})
    shaped("sluice_box", ["P P", "PWP", "SSS"], {"P": "#c:plates/steel", "W": "minecraft:green_wool", "S": "#c:ingots/steel"}, 2)
    shaped("feed_hopper", ["P P", "PHP", " P "], {"P": "#c:plates/steel", "H": "minecraft:hopper"})
    shaped("wash_plant_frame", ["SYS", "S S", "SYS"], {"S": "#c:ingots/steel", "Y": "minecraft:yellow_dye"}, 4)
    for b, label in (("wash_plant", "Wash Plant"), ("wash_plant_frame", "Wash Plant Frame"), ("sluice_box", "Sluice Box"), ("trommel", "Trommel"),
                     ("feed_hopper", "Feed Hopper")):
        name(f"block.powerfactor.{b}", label)

    # ------------------------------------------------------------ chemistry
    for suffix in ("", "_on"):
        on = bool(suffix)
        base = [box([0, 0, 0], [16, 6, 16], "hazard", {"up": "dark", "down": "dark"}, full=SIDES)]
        model("chemical_reactor" + suffix, base + [
            box([2, 6, 2], [14, 15, 14], "glass", full=SIDES), box([1.5, 15, 1.5], [14.5, 16, 14.5], "steel"),
            box([6, 16, 6], [10, 18, 10], "dark", {"up": "lit" if on else "dark"}, glow=10 if on else 0)], render="minecraft:translucent", particle="hazard")
        model("ore_dissolver" + suffix, base + [
            box([1, 6, 1], [15, 7, 15], "galv"),
            box([1, 7, 1], [15, 13, 2], "galv"), box([1, 7, 14], [15, 13, 15], "galv"), box([1, 7, 2], [2, 13, 14], "galv"),
            box([14, 7, 2], [15, 13, 14], "galv"), box([0.5, 12, 0.5], [15.5, 13, 1.5], "yellow"),
            box([6, 1, -0.2], [10, 4, 0], "lit" if on else "dark", glow=12 if on else 0)], particle="hazard")
    for b in ("chemical_reactor", "ore_dissolver"):
        horizontal(b, lambda k, b=b: b + ("_on" if k["running"] == "true" else ""))
        item_def(b, pf(f"block/{b}"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("chemical_reactor", ["PGP", "GMG", "PCP"], {"P": "#c:plates/steel", "G": "#c:glass_blocks", "M": pf("electric_motor"),
                                                      "C": pf("advanced_circuit")})
    shaped("ore_dissolver", ["LGL", "PKP", "PMP"], {"L": "#c:ingots/lead", "G": "#c:glass_blocks", "K": "minecraft:cauldron",
                                                    "P": "#c:plates/steel", "M": pf("electric_motor")})
    shaped("quantum_catalyst", ["QGQ", "GXG", "QGQ"], {"Q": pf("qubit_crystal"), "G": "#c:ingots/gold", "X": pf("exotic_matter")})
    name("block.powerfactor.chemical_reactor", "Chemical Reactor")
    name("block.powerfactor.ore_dissolver", "Ore Dissolver")
    for i in ("sulfur_dust", "quantum_catalyst"):
        flat_item(i)
    name("item.powerfactor.sulfur_dust", "Sulfur Dust")
    name("item.powerfactor.quantum_catalyst", "Quantum Catalyst")
    tag("item", "c", "dusts/sulfur", [pf("sulfur_dust")])
    tag("item", "c", "dusts", [pf("sulfur_dust")])
    # Sulfur: spun out of coal and netherrack.
    machine_recipe("centrifuging", "sulfur_from_coal", [("minecraft:coal", 2)], [out("coal_dust", 1), out("sulfur_dust", 1, 0.5)])
    machine_recipe("centrifuging", "sulfur_from_netherrack", [("minecraft:netherrack", 4)], [out("sulfur_dust", 1), out("minecraft:gravel", 1)])
    # Bauxite is aluminium's raw ore.
    tag("item", "c", "raw_materials/aluminum", [pf("bauxite")])
    for m in METALS:
        label = m.capitalize()
        for kind in ("crystal", "concentrate"):
            flat_item(f"{m}_{kind}")
            name(f"item.powerfactor.{m}_{kind}", f"{label} {kind.capitalize()}")
        crushing(f"{m}_crystal", pf(f"{m}_crystal"), {"item": pf(f"{m}_dust")})
        cooking(INGOT.get(m, pf(f"{m}_ingot")), pf(f"{m}_concentrate"), f"{m}_ingot_from_concentrate", 0.5)

    LANG.update({
        "fluid.powerfactor.sulfuric_acid": "Sulfuric Acid", "block.powerfactor.sulfuric_acid": "Sulfuric Acid",
        "item.powerfactor.sulfuric_acid_bucket": "Sulfuric Acid Bucket",
        "gui.powerfactor.reactor.recipe": "1 sulfur dust + 500 mB water + 250 mB hydrogen + 12,000 FE → 500 mB sulfuric acid",
        "gui.powerfactor.reactor.state.0": "Idle", "gui.powerfactor.reactor.state.1": "Reacting", "gui.powerfactor.reactor.state.2": "No Power",
        "gui.powerfactor.reactor.state.3": "Acid Tank Full", "gui.powerfactor.reactor.state.4": "Needs Sulfur, Water and Hydrogen",
        "gui.powerfactor.reactor.scada": "Acid %s mB",
        "gui.powerfactor.dissolver.state.0": "Idle", "gui.powerfactor.dissolver.state.1": "Dissolving", "gui.powerfactor.dissolver.state.2": "No Power",
        "gui.powerfactor.dissolver.state.3": "Output Full", "gui.powerfactor.dissolver.state.4": "Needs Sulfuric Acid",
        "gui.powerfactor.dissolver.yield": "%s crystals per ore", "gui.powerfactor.dissolver.catalyst": "Quantum Catalyst: 5 crystals instead of 4",
        "gui.powerfactor.dissolver.scada": "Acid %s mB",
        "gui.powerfactor.wash.state.0": "Idle", "gui.powerfactor.wash.state.1": "Washing", "gui.powerfactor.wash.state.2": "No Power",
        "gui.powerfactor.wash.state.3": "Catch Full", "gui.powerfactor.wash.state.4": "Needs Water", "gui.powerfactor.wash.state.5": "Frame Incomplete",
        "gui.powerfactor.wash.scada": "%s loads washed", "gui.powerfactor.wash.washed": "%s loads washed",
        "jei.powerfactor.wash.cost": "%s FE/t · %s s · 1,000 mB water per load", "jei.powerfactor.energy_time": "%s FE · %s s",
        "gui.powerfactor.wash.feed": "Feed: raw ore, ore blocks, gravel or sand",
        "gui.powerfactor.wash.yield": "4 concentrate per raw ore, plus placer finds",
        "gui.powerfactor.upgrades.bay": "Upgrades: Speed (+50% each, +15% energy) and Efficiency (-15% energy each)",
        "subtitles.powerfactor.wash_plant_run": "Wash Plant Rumbles", "subtitles.powerfactor.chemical_reactor_run": "Reactor Bubbles",
        "subtitles.powerfactor.ore_dissolver_run": "Acid Fizzes",
        "handbook.powerfactor.processing.title": "Ore Processing",
        "handbook.powerfactor.processing.text": (
            "Every step up the ore ladder pays more metal per ore.\n\n"
            "Crusher: 2 dust. Ore Washer: 3 dust with water.\n\n"
            "Wash Plant (4x): a placer-mine wash line you build: the Wash Plant shaker table at the front, a six-block Sluice Box line "
            "running back on Wash Plant Frame legs, three Trommel sections over its back half and a Feed Hopper on a frame column at the end. "
            "Sneak-use the table to see the hologram or build it from your inventory. Pipe in water (1,000 mB a load) and 400 FE/t; feed raw "
            "ore or ore blocks into the hopper or the table. Each load becomes four concentrate, each of which smelts to an ingot, with "
            "the heavies a sluice catches: gold nuggets, flint, lapis and the odd diamond or emerald. Gravel and sand pan out gold.\n\n"
            "Chemistry (4x, 5x): spin sulfur out of coal or netherrack in a Centrifuge. The Chemical Reactor burns it with hydrogen from an "
            "Electrolyser and absorbs it in water to make sulfuric acid. The Ore Dissolver leaches raw ore in acid and grows four crystals; "
            "a Quantum Catalyst in its catalyst slot makes it five. Crystals crush to dust."),
    })


def process_states_fix(g):
    """Machine blocks carry facing + running; keep the generated keys in the game's canonical order."""
    return None


def utility(g):
    """Chunk Loader, wireless redstone pads, the item magnet and the Range Upgrade."""
    from storage_data import box, SIDES
    write, asset, data, pf, name, item_def, flat_item, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "tag", "LANG"))
    write(asset("models", "block", "chunk_loader.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"base": "minecraft:block/obsidian", "gold": "minecraft:block/gold_block", "crying": "minecraft:block/crying_obsidian",
                     "particle": "minecraft:block/obsidian"},
        "elements": [box([1, 0, 1], [15, 4, 15], "base", {"up": "crying"}, full=SIDES), box([2, 4, 2], [14, 5, 14], "gold"),
                     box([2, 5, 2], [4, 8, 4], "gold"), box([12, 5, 2], [14, 8, 4], "gold"), box([2, 5, 12], [4, 8, 14], "gold"),
                     box([12, 5, 12], [14, 8, 14], "gold")]})
    write(asset("blockstates", "chunk_loader.json"), {"variants": {"": {"model": pf("block/chunk_loader")}}})
    item_def("chunk_loader")
    drops_self("chunk_loader")
    tag("block", "minecraft", "mineable/pickaxe", [pf("chunk_loader")])
    shaped("chunk_loader", ["OGO", "GEG", "OGO"], {"O": "minecraft:obsidian", "G": "#c:ingots/gold", "E": "minecraft:ender_eye"})
    shaped("range_upgrade", ["GRG", "RPR", "GRG"], {"G": "#c:ingots/gold", "R": "#c:dusts/redstone", "P": "minecraft:ender_pearl"})
    shaped("item_magnet", ["I I", "R L", "III"], {"I": "#c:ingots/iron", "R": "#c:dusts/redstone", "L": "#c:gems/lapis"}, category="equipment")
    flat_item("item_magnet")
    flat_item("range_upgrade")
    for kind in ("transmitter", "receiver"):
        b = f"wireless_{kind}"
        variants = {}
        for ch in range(16):
            for lit in ("false", "true"):
                m = f"{b}_{ch}" + ("_on" if lit == "true" else "")
                lamp = f"wireless_lamp_{ch}" + ("_on" if lit == "true" else "")
                tex = {"base": pf("block/industrial_steel"), "dark": pf("block/industrial_dark"), "lamp": pf("block/" + lamp),
                       "chrome": pf("block/finish_chrome"), "particle": pf("block/industrial_steel")}
                els = [box([2, 0, 2], [14, 3, 14], "base", {"up": "dark"}), box([5, 3, 5], [11, 5, 11], "lamp", glow=12 if lit == "true" else 0, full=SIDES)]
                if kind == "transmitter":
                    els += [box([7.5, 5, 7.5], [8.5, 13, 8.5], "chrome"), box([6.5, 13, 6.5], [9.5, 14, 9.5], "lamp", glow=12 if lit == "true" else 0, full=SIDES)]
                else:
                    els += [box([4, 5, 7.5], [12, 6, 8.5], "chrome"), box([3, 6, 7], [5, 9, 9], "chrome"), box([11, 6, 7], [13, 9, 9], "chrome")]
                write(asset("models", "block", m + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                              "textures": tex, "elements": els})
                variants[f"channel={ch},lit={lit}"] = {"model": pf("block/" + m)}
        write(asset("blockstates", b + ".json"), {"variants": variants})
        item_def(b, pf(f"block/{b}_14"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("wireless_transmitter", [" T ", "RCR", "PPP"], {"T": "minecraft:redstone_torch", "R": "#c:dusts/redstone", "C": pf("advanced_circuit"),
                                                          "P": "#c:plates/iron"})
    shaped("wireless_receiver", [" L ", "RCR", "PPP"], {"L": "minecraft:lever", "R": "#c:dusts/redstone", "C": pf("advanced_circuit"),
                                                       "P": "#c:plates/iron"})
    for k, v in (("block.powerfactor.chunk_loader", "Chunk Loader"), ("block.powerfactor.wireless_transmitter", "Wireless Transmitter"),
                 ("block.powerfactor.wireless_receiver", "Wireless Receiver"), ("item.powerfactor.item_magnet", "Item Magnet"),
                 ("item.powerfactor.range_upgrade", "Range Upgrade")):
        name(k, v)
    LANG.update({
        "gui.powerfactor.chunk_loader.area": "Keeping %s×%s chunks loaded",
        "gui.powerfactor.wireless.channel": "Channel: %s",
        "gui.powerfactor.magnet.on": "Magnet On", "gui.powerfactor.magnet.off": "Magnet Off",
        "tooltip.powerfactor.magnet.on": "On: pulls items and XP within 8 blocks (sneak to pause)",
        "tooltip.powerfactor.magnet.off": "Off: use to switch on",
        "handbook.powerfactor.utility.title": "Handy Devices",
        "handbook.powerfactor.utility.text": (
            "Chunk Loader: obsidian, gold and an eye of ender. It keeps the 3x3 chunks around it loaded and ticking. "
            "Right-click it with Range Upgrades to grow the area (up to three: 9x9); sneak-use with an empty hand to take one back. "
            "Sneak near it to see the loaded area.\n\n"
            "Wireless Redstone: a powered Wireless Transmitter switches on every Wireless Receiver on its channel, in any "
            "dimension, while the transmitter is loaded. Use a dye on either to set its channel; the lamp shows it.\n\n"
            "Item Magnet: use it to switch on. While on, anywhere in your inventory, it draws loose items and experience within "
            "8 blocks to you, except items still under a pickup delay. Sneak to pause it."),
    })


def time_field(g):
    """The Time Dilation Field: model, recipe, text."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    tex = {"base": pf("block/quantum_processor_side"), "plate": pf("block/storage_controller_plate"), "gold": "minecraft:block/gold_block",
           "chrome": pf("block/finish_chrome"), "lit": pf("block/storage_casing_lit"), "dark": pf("block/finish_dark"), "particle": "minecraft:block/gold_block"}
    for suffix in ("", "_on"):
        lit = "lit" if suffix else "dark"
        e = [box([0, 0, 0], [16, 4, 16], "base", {"up": "plate", "down": "plate"}, full=SIDES), box([2, 4, 2], [14, 6, 14], "gold"),
             box([4, 6, 4], [12, 7, 12], lit, glow=15 if suffix else 0)]
        for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
            e.append(box([x, 4, z], [x + 2, 12, z + 2], "chrome"))
            e.append(box([x + 0.5, 12, z + 0.5], [x + 1.5, 13, z + 1.5], lit, glow=12 if suffix else 0))
        write(asset("models", "block", "time_dilation_field" + suffix + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                                  "textures": tex, "elements": e})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/time_dilation_field" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "time_dilation_field.json"), {"variants": variants})
    item_def("time_dilation_field", pf("block/time_dilation_field_on"))
    drops_self("time_dilation_field")
    tag("block", "minecraft", "mineable/pickaxe", [pf("time_dilation_field")])
    shaped("time_dilation_field", ["QCQ", "XSX", "QAQ"], {"Q": pf("quantum_processor"), "C": "minecraft:clock", "X": pf("exotic_matter"),
                                                          "S": pf("singularity"), "A": "#c:ingots/quantum_alloy"})
    name("block.powerfactor.time_dilation_field", "Time Dilation Field")
    LANG.update({
        "gui.powerfactor.time_field.speed": "×%s: %s FE/t, plus %s FE/t per machine inside",
        "gui.powerfactor.time_field.on": "Dilating", "gui.powerfactor.time_field.idle": "Normal Time",
        "gui.powerfactor.time_field.no_power": "Not Enough Power", "gui.powerfactor.time_field.targets": "%s machines accelerated",
        "gui.powerfactor.time_field.cards": "Range Upgrades widen the field: 5³, 9³, 13³",
        "gui.powerfactor.time_field.scada": "×%s · %s machines",
        "handbook.powerfactor.time_field.title": "Time Dilation",
        "handbook.powerfactor.time_field.text": (
            "The Time Dilation Field runs everything in its bubble faster: machines tick N times a tick and crops, saplings and cane grow "
            "N times as fast. Pick ×2, ×4, ×8 or ×16 on its console. Time is expensive: the field costs (N² − 1) × 40 FE/t, plus "
            "(N − 1)² × 8 FE/t for every machine inside, so ×16 with a room full of machines takes a fusion plant. Range Upgrades grow "
            "the bubble from 5 to 9 to 13 blocks across. Sneak past it at night to see its edges."),
    })


def tesseract(g):
    """The Tesseract: frame model, recipe, text."""
    from storage_data import box, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    tex = {"frame": pf("block/finish_chrome"), "dark": pf("block/finish_dark"), "glass": pf("block/chem_glass"), "lit": pf("block/storage_casing_lit"),
           "particle": pf("block/finish_chrome")}
    e = []
    for x in (0, 14):
        for z in (0, 14):
            e.append(box([x, 0, z], [x + 2, 16, z + 2], "frame"))
    for y in (0, 14):
        e += [box([2, y, 0], [14, y + 2, 2], "frame"), box([2, y, 14], [14, y + 2, 16], "frame"), box([0, y, 2], [2, y + 2, 14], "frame"),
              box([14, y, 2], [16, y + 2, 14], "frame")]
    for x in (0, 14):
        for z in (0, 14):
            e.append(box([x + 0.5, 15.5, z + 0.5], [x + 1.5, 16.2, z + 1.5], "lit", glow=12))
    write(asset("models", "block", "tesseract.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": e})
    write(asset("blockstates", "tesseract.json"), {"variants": {"": {"model": pf("block/tesseract")}}})
    item_def("tesseract")
    drops_self("tesseract")
    tag("block", "minecraft", "mineable/pickaxe", [pf("tesseract")])
    shaped("tesseract", ["AXA", "PSP", "AXA"], {"A": "#c:ingots/quantum_alloy", "X": pf("exotic_matter"), "P": pf("quantum_processor"),
                                               "S": pf("singularity")}, 2)
    name("block.powerfactor.tesseract", "Tesseract")
    LANG.update({
        "gui.powerfactor.tesseract.linked": "%s other tesseracts on this frequency",
        "gui.powerfactor.tesseract.frequency": "Frequency: tesseracts on the same one are joined, in any dimension",
        "gui.powerfactor.tesseract.channel": "%s: %s (click: off, send, receive)",
        "gui.powerfactor.tesseract.shared": "Shared", "gui.powerfactor.tesseract.private": "Private",
        "gui.powerfactor.tesseract.lock_tip": "Private: only your tesseracts share this frequency. Shared: anyone's on it do.",
        "gui.powerfactor.tesseract.hint": "Send → Receive",
        "handbook.powerfactor.tesseract.title": "Tesseract",
        "handbook.powerfactor.tesseract.text": (
            "Tesseracts on the same frequency are one block folded through space, in any dimension. Set each channel (items, fluids, "
            "energy) to Send or Receive. Push into a sending tesseract with pipes, cables or machines and it hands everything straight "
            "to the blocks touching the receiving ones, in the same transaction, so nothing is lost when they're full. Frequencies are "
            "private to you unless you unlock them. Tesseracts never feed each other, so frequencies can't loop. Both ends must be loaded "
            "(a Chunk Loader helps)."),
    })


def chargers(g):
    """Wireless and Dimensional Wireless Chargers: models, recipes, text."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    for b, metal in (("wireless_charger", "faradaenium_block"), ("dimensional_charger", "quantum_alloy_block")):
        tex = {"base": pf("block/industrial_steel"), "dark": pf("block/finish_dark"), "coil": pf("block/industrial_coil"), "metal": pf("block/" + metal),
               "lit": pf("block/storage_casing_lit"), "particle": pf("block/industrial_steel")}
        for suffix in ("", "_on"):
            on = bool(suffix)
            e = [box([1, 0, 1], [15, 4, 15], "base", {"up": "dark"}), box([4, 4, 4], [12, 12, 12], "coil", full=SIDES),
                 box([3, 12, 3], [13, 13, 13], "metal"), box([6, 13, 6], [10, 15, 10], "lit" if on else "dark", glow=15 if on else 0)]
            if b == "dimensional_charger":
                e += [box([1, 4, 1], [3, 14, 3], "metal"), box([13, 4, 1], [15, 14, 3], "metal"), box([1, 4, 13], [3, 14, 15], "metal"),
                      box([13, 4, 13], [15, 14, 15], "metal")]
            write(asset("models", "block", b + suffix + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                   "textures": tex, "elements": e})
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = dict({"model": pf("block/" + b + ("_on" if running == "true" else ""))}, **rot)
        write(asset("blockstates", b + ".json"), {"variants": variants})
        item_def(b, pf(f"block/{b}_on"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("wireless_charger", ["FCF", "ARA", "FPF"], {"F": "#c:ingots/faradaenium", "C": pf("charger"), "A": pf("advanced_circuit"),
                                                      "R": pf("radio_tower"), "P": pf("charging_pad")})
    shaped("dimensional_charger", ["QTQ", "SWS", "QXQ"], {"Q": "#c:ingots/quantum_alloy", "T": pf("tesseract"), "S": pf("singularity"),
                                                         "W": pf("wireless_charger"), "X": pf("exotic_matter")})
    name("block.powerfactor.wireless_charger", "Wireless Charger")
    name("block.powerfactor.dimensional_charger", "Dimensional Wireless Charger")
    LANG.update({
        "gui.powerfactor.charger.wireless.status": "Charging players within %s blocks · %s FE stored",
        "gui.powerfactor.charger.dimensional.status": "%s players bound · %s FE stored",
        "gui.powerfactor.charger.bound": "Bound: this charger now powers your gear anywhere",
        "gui.powerfactor.charger.unbound": "Unbound from this charger",
        "handbook.powerfactor.chargers.title": "Wireless Charging",
        "handbook.powerfactor.chargers.text": (
            "The Wireless Charger tops up the FE gear of every player within 16 blocks (8 more per Range Upgrade, up to 40): power armour, "
            "tools, weapons, anything with an energy store, up to 8,000 FE/t shared. Arcs of power reach out to whoever it's charging.\n\n"
            "The Dimensional Wireless Charger charges the players bound to it (right-click to bind or unbind) wherever they are, in any "
            "dimension, at up to 40,000 FE/t. A quarter is lost crossing between worlds. A pillar of light rises from it while it sends."),
    })


def farm(g):
    """Farm Gantry: the control cabinet model (the crane is drawn), recipe, text."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    tex = {"yellow": pf("block/mining_yellow"), "galv": pf("block/galvanized_steel"), "dark": pf("block/industrial_dark"), "lit": pf("block/storage_casing_lit"),
           "particle": pf("block/mining_yellow")}
    for suffix in ("", "_on"):
        e = [box([1, 0, 1], [15, 12, 15], "yellow", {"up": "galv", "north": "dark"}, full=SIDES),
             box([4, 4, 0.5], [12, 10, 1], "lit" if suffix else "dark", glow=12 if suffix else 0), box([3, 12, 3], [13, 14, 13], "galv")]
        write(asset("models", "block", "farm_gantry" + suffix + ".json"), {"parent": "minecraft:block/block", "textures": tex, "elements": e})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/farm_gantry" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "farm_gantry.json"), {"variants": variants})
    item_def("farm_gantry")
    drops_self("farm_gantry")
    tag("block", "minecraft", "mineable/pickaxe", [pf("farm_gantry")])
    shaped("farm_gantry", ["SHS", "MCM", "SDS"], {"S": "#c:ingots/steel", "H": "minecraft:diamond_hoe", "M": pf("electric_motor"),
                                                  "C": pf("advanced_circuit"), "D": "minecraft:dispenser"})
    name("block.powerfactor.farm_gantry", "Farm Gantry")
    LANG.update({
        "gui.powerfactor.farm.seeds": "Seeds: anything that plants a crop or a stem", "gui.powerfactor.farm.harvest": "Harvest",
        "gui.powerfactor.farm.harvested": "%s crops harvested", "gui.powerfactor.farm.on": "Working", "gui.powerfactor.farm.off": "Off or Out of Power",
        "gui.powerfactor.farm.scada": "%s harvested",
        "handbook.powerfactor.farming.title": "Farm Gantry",
        "handbook.powerfactor.farming.text": (
            "The Farm Gantry runs an overhead crane over the 9x9 field in front of it. Watch the bridge roll down the rails and the "
            "carriage cross it, square by square: the tool head drops to till dirt and grass into farmland, plant seeds from its "
            "seed slots, and cut ripe crops (and melons and pumpkins off their stems). It keeps a seed from each harvest to replant "
            "and sends the rest to its harvest slots, ready for pipes. Pipe water into it and it irrigates every square it passes, "
            "so the field never dries. 60 FE per job and 10 FE/t while moving."),
    })


def mobs(g):
    """Mob farming: Soul Snare, Spawner Cage and Mob Grinder."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    # Snare: shows the full vial once it holds a soul (custom_data has "soul").
    write(asset("models", "item", "soul_snare.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": pf("item/soul_snare")}})
    write(asset("models", "item", "soul_snare_full.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": pf("item/soul_snare_full")}})
    write(asset("items", "soul_snare.json"), {"model": {"type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:custom_data",
                                                        "on_true": {"type": "minecraft:model", "model": pf("item/soul_snare_full")},
                                                        "on_false": {"type": "minecraft:model", "model": pf("item/soul_snare")}}})
    shaped("soul_snare", [" G ", "GSG", " G "], {"G": "#c:glass_blocks", "S": "minecraft:soul_sand"})
    name("item.powerfactor.soul_snare", "Soul Snare")

    def machine(mid, elements, tex):
        for suffix in ("", "_on"):
            write(asset("models", "block", mid + suffix + ".json"), {"parent": "minecraft:block/block", "textures": tex, "elements": elements(bool(suffix))})
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = dict({"model": pf("block/" + mid + ("_on" if running == "true" else ""))}, **rot)
        write(asset("blockstates", mid + ".json"), {"variants": variants})
        item_def(mid)
        drops_self(mid)
        tag("block", "minecraft", "mineable/pickaxe", [pf(mid)])

    # Cage: a dark base and cap joined by corner posts and bars; the inside is open for the mob.
    def cage(on):
        e = [box([0, 0, 0], [16, 3, 16], "base", {"up": "lit" if on else "dark"}, full=SIDES), box([0, 13, 0], [16, 16, 16], "base", {"down": "lit" if on else "dark"}, full=SIDES)]
        for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
            e.append(box([x, 3, z], [x + 2, 13, z + 2], "bars"))
        for k in (4, 7, 10):  # three bars per side
            e += [box([k, 3, 0.5], [k + 1, 13, 1.5], "bars"), box([k + 1, 3, 14.5], [k + 2, 13, 15.5], "bars"),
                  box([0.5, 3, k], [1.5, 13, k + 1], "bars"), box([14.5, 3, k + 1], [15.5, 13, k + 2], "bars")]
        return e
    machine("spawner_cage", cage, {"base": pf("block/industrial_dark"), "bars": pf("block/cage_bars"), "dark": pf("block/industrial_dark"),
                                    "lit": pf("block/storage_casing_lit"), "particle": pf("block/cage_bars")})

    # Grinder: hazard-striped housing, a recessed dark front where the saw spins (drawn).
    def grinder(on):
        return [box([0, 0, 1], [16, 16, 16], "hazard", {"up": "galv", "down": "galv", "north": "dark"}, full=SIDES),
                box([0, 0, 0], [16, 2, 1], "galv"), box([0, 14, 0], [16, 16, 1], "galv"),
                box([0, 2, 0], [2, 14, 1], "galv"), box([14, 2, 0], [16, 14, 1], "galv")]
    machine("mob_grinder", grinder, {"hazard": pf("block/saw_hazard"), "galv": pf("block/galvanized_steel"), "dark": pf("block/industrial_dark"),
                                     "particle": pf("block/saw_hazard")})

    shaped("spawner_cage", ["IEI", "BSB", "INI"], {"I": "minecraft:iron_bars", "E": "minecraft:echo_shard", "B": "minecraft:blaze_rod",
                                                   "S": "minecraft:soul_lantern", "N": "minecraft:netherite_ingot"})
    shaped("mob_grinder", ["SDS", "DMD", "SCS"], {"S": "#c:ingots/steel", "D": "minecraft:diamond_sword", "M": pf("electric_motor"),
                                                  "C": pf("advanced_circuit")})
    name("block.powerfactor.spawner_cage", "Spawner Cage")
    name("block.powerfactor.mob_grinder", "Mob Grinder")
    LANG.update({
        "gui.powerfactor.snare.captured": "Soul Snare Holds: %s", "tooltip.powerfactor.snare.empty": "Use on a mob to take its imprint",
        "tooltip.powerfactor.snare.holds": "Holds: %s",
        "gui.powerfactor.cage.empty": "Put a filled Soul Snare in the slot", "gui.powerfactor.cage.snare": "Soul Snare",
        "gui.powerfactor.cage.state.0": "No Soul", "gui.powerfactor.cage.state.1": "Spawning", "gui.powerfactor.cage.state.2": "Low Power",
        "gui.powerfactor.cage.state.3": "Crowded", "gui.powerfactor.cage.cost": "%s FE per copy", "gui.powerfactor.cage.spawned": "%s copies made",
        "gui.powerfactor.cage.scada": "%s spawned",
        "gui.powerfactor.grinder.zone": "Kill zone: the 5x3x5 space in front", "gui.powerfactor.grinder.kills": "%s kills",
        "gui.powerfactor.grinder.draw": "Take the stored experience (%s points)", "gui.powerfactor.grinder.scada": "%s kills, %s XP",
        "handbook.powerfactor.mobs.title": "Mob Farming",
        "handbook.powerfactor.mobs.text": (
            "Use a Soul Snare on a mob to take an imprint of its kind; the mob is unharmed. Put the filled snare in a Spawner Cage "
            "and you'll see the mob turning inside the bars, soul fire rising round it until a copy steps out two blocks in front. "
            "Each copy costs 2,000 FE plus 400 per point of the mob's health, and the cage waits while eight of them crowd the "
            "space. Speed and Efficiency cards apply. Bosses can't be snared.\n\n"
            "Point a Mob Grinder at the same space. Its saw spins up and cuts down anything in the 5x3x5 zone in front of it as a "
            "player would, so rare drops and experience fall. It gathers the drops into its grid and the experience into a store "
            "you draw from its console. Named mobs and pets are spared."),
    })


def pockets(g):
    """Pocket dimensions: the dimension, the door, the shell, the port and the expander."""
    from storage_data import box, FACING, SIDES
    write, asset, data, pf, name, item_def, shaped, drops_self, tag, LANG, NS = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG", "NS"))
    write(data(NS, "dimension_type", "pocket.json"), {
        "ambient_light": 1.0, "has_fixed_time": True, "has_skylight": False, "has_ceiling": True, "has_ender_dragon_fight": False,
        "coordinate_scale": 1.0, "min_y": 0, "height": 256, "logical_height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
        "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": 0, "skybox": "none",
        "attributes": {"minecraft:visual/fog_color": "#0e1430", "minecraft:visual/sky_color": "#000000",
                       "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "explodes": False},
                       "minecraft:gameplay/respawn_anchor_works": False}})
    write(data(NS, "dimension", "pocket.json"), {"type": pf("pocket"), "generator": {"type": "minecraft:flat", "settings": {
        "biome": "minecraft:the_void", "features": False, "lakes": False, "layers": [], "structure_overrides": []}}})

    # Door: twelve violet edges and lit corner nodes; the inside is drawn (nested rooms receding).
    e = []
    for a, b in (([0, 0, 0], [16, 2, 2]), ([0, 14, 0], [16, 16, 2]), ([0, 0, 14], [16, 2, 16]), ([0, 14, 14], [16, 16, 16]),
                 ([0, 0, 0], [2, 2, 16]), ([14, 0, 0], [16, 2, 16]), ([0, 14, 0], [2, 16, 16]), ([14, 14, 0], [16, 16, 16]),
                 ([0, 2, 0], [2, 14, 2]), ([14, 2, 0], [16, 14, 2]), ([0, 2, 14], [2, 14, 16]), ([14, 2, 14], [16, 14, 16])):
        e.append(box(a, b, "frame"))
    for x in (0, 13):
        for y in (0, 13):
            for z in (0, 13):
                e.append(box([x, y, z], [x + 3, y + 3, z + 3], "node", glow=15))
    write(asset("models", "block", "dimensional_pocket.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
          "textures": {"frame": pf("block/pocket_frame"), "node": pf("block/storage_casing_lit"), "particle": pf("block/pocket_frame")}, "elements": e})
    write(asset("blockstates", "dimensional_pocket.json"), {"variants": {"": {"model": pf("block/dimensional_pocket")}}})
    item_def("dimensional_pocket")
    # The room travels with the block.
    write(data(NS, "loot_table", "blocks", "dimensional_pocket.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/dimensional_pocket"),
          "pools": [{"rolls": 1.0, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": pf("dimensional_pocket"), "functions": [
              {"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:custom_data"]}]}]}]})
    tag("block", "minecraft", "mineable/pickaxe", [pf("dimensional_pocket"), pf("pocket_port")])

    write(asset("models", "block", "pocket_wall.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf("block/pocket_wall")}})
    write(asset("blockstates", "pocket_wall.json"), {"variants": {"": {"model": pf("block/pocket_wall")}}})

    write(asset("models", "block", "pocket_port.json"), {"parent": "minecraft:block/block", "textures": {
        "front": pf("block/pocket_port"), "side": pf("block/pocket_wall"), "particle": pf("block/pocket_wall")},
        "elements": [box([0, 0, 0], [16, 16, 16], "side", {"north": "front"}, full=SIDES)]})
    write(asset("blockstates", "pocket_port.json"), {"variants": {f"facing={f}": dict({"model": pf("block/pocket_port")}, **rot) for f, rot in FACING.items()}})
    item_def("pocket_port")
    drops_self("pocket_port")

    write(asset("models", "item", "spatial_expander.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": pf("item/spatial_expander")}})
    write(asset("items", "spatial_expander.json"), {"model": {"type": "minecraft:model", "model": pf("item/spatial_expander")}})

    shaped("dimensional_pocket", ["OEO", "EPE", "OEO"], {"O": "minecraft:crying_obsidian", "E": "minecraft:ender_eye", "P": pf("exotic_matter")})
    shaped("pocket_port", ["G", "P", "G"], {"G": "minecraft:glass", "P": "minecraft:ender_pearl"})
    shaped("spatial_expander", ["SNS", "NXN", "SNS"], {"S": "minecraft:shulker_shell", "N": "minecraft:nether_star", "X": pf("exotic_matter")})

    name("block.powerfactor.dimensional_pocket", "Dimensional Pocket")
    name("block.powerfactor.pocket_wall", "Pocket Wall")
    name("block.powerfactor.pocket_port", "Pocket Port")
    name("item.powerfactor.spatial_expander", "Spatial Expander")
    LANG.update({
        "gui.powerfactor.pocket.size": "Room: %s x %s x %s", "gui.powerfactor.pocket.port_outside": "A Pocket Port only works inside a pocket room",
        "tooltip.powerfactor.pocket.size": "Room: %s x %s x %s", "tooltip.powerfactor.pocket.new": "A new room is made on first use",
        "tooltip.powerfactor.pocket.room": "Room #%s, and everything in it, goes where this goes",
        "handbook.powerfactor.pocket.title": "Pocket Dimensions",
        "handbook.powerfactor.pocket.text": (
            "A Dimensional Pocket is bigger on the inside. Use it to step into a private room, 7 blocks across, in a dimension of "
            "its own; use any wall panel to step back out beside the door. Break the pocket and the room goes with it, contents and "
            "all, wherever you place it next. Use a Spatial Expander on it to grow the room to 13 and then 21 blocks across; what's "
            "inside stays put.\n\nThe room stays loaded while its door is, so machines inside keep running. Place a Pocket Port "
            "inside, against a machine or chest, and anything piped into the pocket outside (items, fluids or energy) goes to it, "
            "and can be pulled back out."),
    })


def vein(g):
    """Vein mining: the matrix item, its recipe and text."""
    write, asset, pf, name, shaped, LANG = (g[k] for k in ("write", "asset", "pf", "name", "shaped", "LANG"))
    write(asset("models", "item", "vein_mining_matrix.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": pf("item/vein_mining_matrix")}})
    write(asset("items", "vein_mining_matrix.json"), {"model": {"type": "minecraft:model", "model": pf("item/vein_mining_matrix")}})
    shaped("vein_mining_matrix", ["DXD", "QNQ", "DXD"], {"D": "minecraft:diamond_block", "X": pf("exotic_matter"), "Q": pf("qubit_crystal"),
                                                          "N": "minecraft:nether_star"})
    name("item.powerfactor.vein_mining_matrix", "Vein Mining Matrix")
    LANG.update({
        "key.powerfactor.vein_mine": "Vein Mine (Hold)", "gui.powerfactor.vein.count": "%s Blocks",
        "tooltip.powerfactor.vein_miner": "Vein Mining: hold the Vein Mine key while breaking",
        "handbook.powerfactor.vein.title": "Vein Mining",
        "handbook.powerfactor.vein.text": (
            "Combine any digging tool with a Vein Mining Matrix in an anvil (30 levels) and it learns vein mining. Hold the Vein Mine "
            "key (` by default) and the whole vein under your crosshair lights up. Break one block and the rest come away in a wave, "
            "up to 64 blocks within 16: every touching block of the same kind, or the same ore in other stone. Each costs the tool's "
            "durability or charge as if mined by hand, plus hunger, and it stops a block short of breaking the tool. The drops "
            "gather where you struck."),
    })


def fission(g):
    """Fission: uranium, the reactor, the centrifuge, fuel and the Hazmat Suit."""
    from storage_data import box, FACING, SIDES
    write, asset, data, pf, name, item_def, flat_item, shaped, drops_self, ore_loot, tag, cooking, LANG, NS = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "ore_loot", "tag", "cooking", "LANG", "NS"))
    six = ("north", "south", "east", "west", "up", "down")

    # Ores: vanilla stone with a glowing pitchblende overlay.
    for ore, base in (("uranium_ore", "minecraft:block/stone"), ("deepslate_uranium_ore", "minecraft:block/deepslate")):
        write(asset("models", "block", f"{ore}.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"base": base, "overlay": pf("block/uranium_ore_overlay"), "particle": base}, "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#base", "cullface": d} for d in six}},
                {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 7, "faces": {d: {"texture": "#overlay", "cullface": d} for d in six}}]})
        write(asset("blockstates", f"{ore}.json"), {"variants": {"": {"model": pf(f"block/{ore}")}}})
        item_def(ore)
        ore_loot(ore, "raw_uranium")
        tag("block", "c", "ores/uranium", [pf(ore)])
        tag("item", "c", "ores/uranium", [pf(ore)])
        tag("block", "minecraft", "mineable/pickaxe", [pf(ore)])
        tag("block", "minecraft", "needs_iron_tool", [pf(ore)])
        ground = "deepslate" if "deepslate" in ore else "stone"
        tag("block", "c", f"ores_in_ground/{ground}", [pf(ore)])
        tag("item", "c", f"ores_in_ground/{ground}", [pf(ore)])
    tag("block", "c", "ores", ["#c:ores/uranium"])
    tag("item", "c", "ores", ["#c:ores/uranium"])
    write(data(NS, "worldgen", "configured_feature", "ore_uranium.json"), {"type": "minecraft:ore", "config": {
        "discard_chance_on_air_exposure": 0.3, "size": 6, "targets": [
            {"state": {"Name": pf("uranium_ore")}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
            {"state": {"Name": pf("deepslate_uranium_ore")}, "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]}})
    write(data(NS, "worldgen", "placed_feature", "ore_uranium.json"), {"feature": pf("ore_uranium"), "placement": [
        {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -56}, "max_inclusive": {"absolute": 24}}},
        {"type": "minecraft:biome"}]})
    write(data(NS, "neoforge", "biome_modifier", "add_uranium_ore.json"), {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": pf("ore_uranium"), "step": "underground_ores", "neoforge:conditions": [{"type": pf("worldgen_enabled"), "feature": "uranium"}]})

    # Materials.
    for item in ("raw_uranium", "uranium_ingot", "enriched_uranium", "depleted_uranium", "plutonium", "nuclear_waste", "fuel_rod", "mox_fuel_rod",
                 "spent_fuel_rod", "spent_mox_rod", "hazmat_helmet", "hazmat_suit", "hazmat_leggings", "hazmat_boots"):
        flat_item(item)
        write(asset("items", f"{item}.json"), {"model": {"type": "minecraft:model", "model": pf(f"item/{item}")}})
    tag("item", "c", "raw_materials/uranium", [pf("raw_uranium")])
    tag("item", "c", "raw_materials", ["#c:raw_materials/uranium"])
    tag("item", "c", "ingots/uranium", [pf("uranium_ingot")])
    tag("item", "c", "ingots", ["#c:ingots/uranium"])
    cooking(pf("uranium_ingot"), "#c:raw_materials/uranium", "uranium_ingot_from_raw")
    cooking(pf("uranium_ingot"), "#c:ores/uranium", "uranium_ingot_from_ore", 1.0)
    tag("item", NS, "repairs/hazmat", [pf("rubber")])
    for piece, slot in (("hazmat_helmet", "head_armor"), ("hazmat_suit", "chest_armor"), ("hazmat_leggings", "leg_armor"), ("hazmat_boots", "foot_armor")):
        tag("item", "minecraft", slot, [pf(piece)])
    write(asset("equipment", "hazmat.json"), {"layers": {"humanoid": [{"texture": pf("hazmat")}], "humanoid_leggings": [{"texture": pf("hazmat")}],
                                                         "humanoid_baby": [{"texture": pf("hazmat")}]}})

    # Reactor blocks.
    def cube(block, tex, render=None):
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": pf(f"block/{tex}")}}
        if render:
            m["render_type"] = render
        write(asset("models", "block", f"{block}.json"), m)
        write(asset("blockstates", f"{block}.json"), {"variants": {"": {"model": pf(f"block/{block}")}}})
        item_def(block)
    cube("reactor_casing", "reactor_casing")
    cube("reactor_glass", "reactor_glass", "minecraft:translucent")
    cube("reactor_logic_adapter", "reactor_logic_adapter")
    cube("graphite_moderator", "graphite_moderator")
    cube("corium", "corium")
    for mode in ("coolant", "fuel"):
        write(asset("models", "block", f"reactor_port_{mode}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf(f"block/reactor_port_{mode}")}})
    write(asset("blockstates", "reactor_port.json"), {"variants": {"fuel=false": {"model": pf("block/reactor_port_coolant")},
                                                                   "fuel=true": {"model": pf("block/reactor_port_fuel")}}})
    item_def("reactor_port", pf("block/reactor_port_coolant"))
    write(asset("blockstates", "reactor_logic_adapter.json"), {"variants": {"fuel=false": {"model": pf("block/reactor_logic_adapter")},
                                                                            "fuel=true": {"model": pf("block/reactor_logic_adapter")}}})
    # Fuel Assembly: an open bundle of eight zircaloy rods round an empty guide tube (the absorber rod the
    # controller draws slides down it), held by two spacer grids. Water shows between the rods, and the
    # Cherenkov light round them. It glows brighter (real light) as the reactor's power rises.
    rod_faces = {d: {"uv": [4, 0, 8, 16], "texture": "#rod"} for d in ("north", "south", "east", "west")}
    rod_faces.update({d: {"uv": [4, 4, 8, 8], "texture": "#end"} for d in ("up", "down")})
    elements = [{"from": [x, 0, z], "to": [x + 2, 16, z + 2], "faces": rod_faces}
                for x in (3, 7, 11) for z in (3, 7, 11) if (x, z) != (7, 7)]
    grid = {d: {"texture": "#grid"} for d in ("north", "south", "east", "west", "up", "down")}
    for y in (3, 12):
        elements += [{"from": [2, y, 2], "to": [14, y + 1, 2.5], "faces": grid}, {"from": [2, y, 13.5], "to": [14, y + 1, 14], "faces": grid},
                     {"from": [2, y, 2.5], "to": [2.5, y + 1, 13.5], "faces": grid}, {"from": [13.5, y, 2.5], "to": [14, y + 1, 13.5], "faces": grid},
                     {"from": [2.5, y, 5.75], "to": [13.5, y + 1, 6.25], "faces": grid}, {"from": [2.5, y, 9.75], "to": [13.5, y + 1, 10.25], "faces": grid},
                     {"from": [5.75, y, 2.5], "to": [6.25, y + 1, 5.75], "faces": grid}, {"from": [5.75, y, 10.25], "to": [6.25, y + 1, 13.5], "faces": grid},
                     {"from": [9.75, y, 2.5], "to": [10.25, y + 1, 5.75], "faces": grid}, {"from": [9.75, y, 10.25], "to": [10.25, y + 1, 13.5], "faces": grid}]
    write(asset("models", "block", "fuel_assembly.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"rod": pf("block/fuel_assembly"), "end": pf("block/fuel_assembly_top"), "grid": pf("block/fuel_assembly_grid"),
                     "particle": pf("block/fuel_assembly")}, "elements": elements})
    write(asset("blockstates", "fuel_assembly.json"), {"variants": {f"glow={i}": {"model": pf("block/fuel_assembly")} for i in range(4)}})
    item_def("fuel_assembly")
    for block, top, side, render in (("control_rod_drive", "control_rod_drive_top", "control_rod_drive_side", None),):
        m = {"parent": "minecraft:block/cube_column", "textures": {"end": pf(f"block/{top}"), "side": pf(f"block/{side}")}}
        if render:
            m["render_type"] = render
        write(asset("models", "block", f"{block}.json"), m)
        write(asset("blockstates", f"{block}.json"), {"variants": {"": {"model": pf(f"block/{block}")}}})
        item_def(block)
    for b in ("reactor_casing", "reactor_glass", "reactor_port", "reactor_logic_adapter", "control_rod_drive", "fuel_assembly", "graphite_moderator",
              "reactor_controller", "isotope_centrifuge"):
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])

    def machine(mid, models):
        for suffix, model in models.items():
            write(asset("models", "block", mid + suffix + ".json"), model)
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = dict({"model": pf("block/" + mid + ("_on" if running == "true" else ""))}, **rot)
        write(asset("blockstates", mid + ".json"), {"variants": variants})
        item_def(mid)

    machine("reactor_controller", {suffix: {"parent": "minecraft:block/cube", "textures": {
        "north": pf("block/reactor_controller_front" + suffix), "south": pf("block/reactor_casing"), "east": pf("block/reactor_casing"),
        "west": pf("block/reactor_casing"), "up": pf("block/reactor_casing"), "down": pf("block/reactor_casing"), "particle": pf("block/reactor_casing")}}
        for suffix in ("", "_on")})
    cent = {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "textures": {
        "side": pf("block/isotope_centrifuge_side"), "cap": pf("block/reactor_casing"), "particle": pf("block/reactor_casing")}, "elements": [
        box([0, 0, 0], [16, 3, 16], "cap", full=SIDES), box([0, 13, 0], [16, 16, 16], "cap", full=SIDES),
        box([1, 3, 1], [15, 13, 15], "side", {"up": None, "down": None})]}
    machine("isotope_centrifuge", {"": cent, "_on": cent})

    # High-Pressure Turbine: the steam turbine's controller look, a power-station-sized structure.
    import json as _json, os as _os
    src = _json.load(open(asset("blockstates", "steam_turbine.json")))
    write(asset("blockstates", "high_pressure_turbine.json"), src)
    item_def("high_pressure_turbine", pf("block/steam_turbine"))
    drops_self("high_pressure_turbine")
    tag("block", "minecraft", "mineable/pickaxe", [pf("high_pressure_turbine")])
    shaped("high_pressure_turbine", ["TKT", "RXR", "TMT"], {"T": pf("steam_turbine"), "K": pf("advanced_circuit"), "R": pf("steam_rotor"),
                                                             "X": pf("steel_block"), "M": pf("generator_magnet")})
    name("block.powerfactor.high_pressure_turbine", "High-Pressure Turbine")

    # Recipes.
    S, L = "#c:ingots/steel", "#c:ingots/lead"
    shaped("reactor_casing", ["SLS", "L L", "SLS"], {"S": S, "L": L}, count=4)
    shaped("reactor_glass", ["CGC", "GLG", "CGC"], {"C": pf("reactor_casing"), "G": "#c:glass_blocks", "L": L}, count=4)
    shaped("reactor_port", [" H ", "BCB", " H "], {"H": "minecraft:hopper", "B": "minecraft:bucket", "C": pf("reactor_casing")})
    shaped("reactor_logic_adapter", [" R ", "QCQ", " R "], {"R": "minecraft:redstone", "Q": "minecraft:comparator", "C": pf("reactor_casing")})
    shaped("control_rod_drive", ["SPS", "SMS", "SCS"], {"S": S, "P": "minecraft:piston", "M": pf("electric_motor"), "C": pf("reactor_casing")})
    shaped("fuel_assembly", ["SIS", "I I", "SIS"], {"S": S, "I": "minecraft:iron_bars"}, count=2)
    shaped("graphite_moderator", ["GG", "GG"], {"G": pf("graphite")})
    shaped("reactor_controller", ["CAC", "ADA", "CAC"], {"C": pf("reactor_casing"), "A": pf("advanced_circuit"), "D": "minecraft:diamond"})
    shaped("isotope_centrifuge", ["SGS", "MCM", "SAS"], {"S": S, "G": "#c:glass_blocks", "M": pf("electric_motor"), "C": pf("reactor_casing"),
                                                          "A": pf("advanced_circuit")})
    shaped("fuel_rod", ["SES", "SES", "SES"], {"S": S, "E": pf("enriched_uranium")})
    shaped("mox_fuel_rod", ["SPS", "SDS", "SPS"], {"S": S, "P": pf("plutonium"), "D": pf("depleted_uranium")})
    shaped("hazmat_helmet", ["RLR", "RGR"], {"R": pf("rubber"), "L": L, "G": "minecraft:glass_pane"}, category="equipment")
    shaped("hazmat_suit", ["R R", "RLR", "RLR"], {"R": pf("rubber"), "L": L}, category="equipment")
    shaped("hazmat_leggings", ["RLR", "R R", "R R"], {"R": pf("rubber"), "L": L}, category="equipment")
    shaped("hazmat_boots", ["R R", "L L"], {"R": pf("rubber"), "L": L}, category="equipment")

    for key, english in {
        "uranium_ore": "Uranium Ore", "deepslate_uranium_ore": "Deepslate Uranium Ore", "reactor_casing": "Reactor Casing", "reactor_glass": "Reactor Glass",
        "reactor_port": "Reactor Port", "reactor_logic_adapter": "Reactor Logic Adapter", "control_rod_drive": "Control Rod Drive",
        "fuel_assembly": "Fuel Assembly", "graphite_moderator": "Graphite Moderator", "reactor_controller": "Fission Reactor Controller",
        "corium": "Corium", "isotope_centrifuge": "Isotope Centrifuge"}.items():
        name(f"block.powerfactor.{key}", english)
    for key, english in {
        "raw_uranium": "Raw Uranium", "uranium_ingot": "Uranium Ingot", "enriched_uranium": "Enriched Uranium", "depleted_uranium": "Depleted Uranium",
        "plutonium": "Plutonium", "nuclear_waste": "Nuclear Waste", "fuel_rod": "Uranium Fuel Rod", "mox_fuel_rod": "MOX Fuel Rod",
        "spent_fuel_rod": "Spent Fuel Rod", "spent_mox_rod": "Spent MOX Rod", "hazmat_helmet": "Hazmat Hood", "hazmat_suit": "Hazmat Suit",
        "hazmat_leggings": "Hazmat Trousers", "hazmat_boots": "Hazmat Boots"}.items():
        name(f"item.powerfactor.{key}", english)
    alarms = {"high_power": "High Power", "high_temp": "High Temp", "low_coolant": "Low Coolant", "steam_full": "Steam Full",
              "fast_period": "Fast Period", "high_xenon": "High Xenon", "core_damage": "Core Damage", "fuel_low": "Fuel Low",
              "spent_full": "Spent Full", "scrammed": "Scrammed"}
    LANG.update({f"gui.powerfactor.fission.alarm.{k}": v for k, v in alarms.items()})
    tiles = {"high_power": "Power", "high_temp": "Temp", "low_coolant": "Coolant", "steam_full": "Steam", "fast_period": "Period",
             "high_xenon": "Xenon", "core_damage": "Damage", "fuel_low": "Fuel", "spent_full": "Spent", "scrammed": "SCRAM"}
    LANG.update({f"gui.powerfactor.fission.tile.{k}": v for k, v in tiles.items()})
    LANG.update({
        "gui.powerfactor.fission.waiting": "Waiting for the Reactor...",
        "gui.powerfactor.fission.tripped": "Tripped: %s", "gui.powerfactor.fission.trip.high_temp": "High Temperature",
        "gui.powerfactor.fission.trip.high_power": "High Power", "gui.powerfactor.fission.trip.low_coolant": "Coolant Lost",
        "gui.powerfactor.fission.trip.core_damage": "Core Damage", "gui.powerfactor.fission.trip.operator": "Operator SCRAM",
        "gui.powerfactor.fission.trip.scada": "SCADA SCRAM", "gui.powerfactor.fission.trip.adapter": "Logic Adapter",
        "gui.powerfactor.fission.trip.test": "Test", "gui.powerfactor.fission.trip.structure": "Structure Broken",
        "gui.powerfactor.fission.point.power": "Power", "gui.powerfactor.fission.point.temp": "Fuel Temp",
        "gui.powerfactor.fission.point.reactivity": "Reactivity", "gui.powerfactor.fission.point.period": "Period",
        "gui.powerfactor.fission.point.xenon": "Xenon", "gui.powerfactor.fission.point.steam": "Steam Out",
        "gui.powerfactor.fission.point.water": "Coolant", "gui.powerfactor.fission.point.rods": "Rods In",
        "gui.powerfactor.fission.point.damage": "Core Damage", "gui.powerfactor.fission.control.auto": "Auto",
        "gui.powerfactor.fission.control.target": "Target %", "gui.powerfactor.fission.control.rods": "Rods %",
        "gui.powerfactor.fission.control.trip_temp": "Trip K", "gui.powerfactor.fission.control.trip_power": "Trip %",
        "scada.powerfactor.point.temperature": "Temperature", "scada.powerfactor.point.steam_rate": "Steam Rate",
        "scada.powerfactor.point.water": "Water", "scada.powerfactor.point.steam": "Steam", "scada.powerfactor.point.fuel": "Fuel",
        "scada.powerfactor.point.rpm": "Shaft Speed", "scada.powerfactor.point.hertz": "Frequency", "scada.powerfactor.point.output": "Output",
        "scada.powerfactor.point.condensate": "Condensate", "scada.powerfactor.point.breaker": "Breaker",
        "gui.powerfactor.scada.filter.all": "All (%s ⚠)", "gui.powerfactor.scada.filter.alarms": "Alarms: %s",
        "gui.powerfactor.scada.back": "‹ Back", "gui.powerfactor.scada.confirm": "Confirm?",
        "gui.powerfactor.fission.problem.size": "The reactor must be a box 5 to 18 blocks on each side, with the controller in one wall",
        "gui.powerfactor.fission.problem.edge": "Edges and corners must be Reactor Casing",
        "gui.powerfactor.fission.problem.wall": "Walls may only hold casing, glass, ports, logic adapters and rod drives",
        "gui.powerfactor.fission.problem.drive": "Control Rod Drives belong in the roof",
        "gui.powerfactor.fission.problem.controllers": "One controller per reactor",
        "gui.powerfactor.fission.problem.inside": "Inside, only fuel assemblies, water, graphite and air",
        "gui.powerfactor.fission.problem.no_drive": "Every fuel column needs a Control Rod Drive above it in the roof",
        "gui.powerfactor.fission.problem.column": "Fuel columns must run from the floor to the roof",
        "gui.powerfactor.fission.problem.no_fuel": "No fuel columns inside",
        "gui.powerfactor.fission.scram": "SCRAM", "gui.powerfactor.fission.scram_now": "Confirm SCRAM",
        "gui.powerfactor.fission.scram.tip": "Drive every rod home and trip the reactor. Press once to lift the guard, again to SCRAM",
        "gui.powerfactor.fission.reset": "Reset", "gui.powerfactor.fission.reset.tip": "Clear a SCRAM once nothing would trip it again",
        "gui.powerfactor.fission.ack": "Ack", "gui.powerfactor.fission.ack.tip": "Acknowledge the alarms (stops the flashing)",
        "gui.powerfactor.fission.auto": "Auto %s%%", "gui.powerfactor.fission.auto.tip": "Let the controller hold the target power with the rods",
        "gui.powerfactor.fission.target": "Target power for Auto",
        "gui.powerfactor.fission.rods_out": "Withdraw rods %s%% (more power)", "gui.powerfactor.fission.rods_in": "Insert rods %s%% (less power)",
        "gui.powerfactor.fission.rods_all": "All Rods", "gui.powerfactor.fission.rods_column": "Column %s",
        "gui.powerfactor.fission.column": "Column %s: %s K, rods %s%% in, flux %s%%",
        "gui.powerfactor.fission.thermal": "Thermal power", "gui.powerfactor.fission.temp": "Fuel temperature",
        "gui.powerfactor.fission.reactivity": "Reactivity (k = %s)", "gui.powerfactor.fission.period": "Reactor period: how fast power is rising",
        "gui.powerfactor.fission.xenon": "Xenon poisoning", "gui.powerfactor.fission.steam": "Steam made (%s mB stored)",
        "gui.powerfactor.fission.water": "Coolant water", "gui.powerfactor.fission.fuel": "Fuel rods loaded / assemblies (%s spent waiting)",
        "gui.powerfactor.fission.trip_temp": "Trip: SCRAM above this fuel temperature", "gui.powerfactor.fission.trip_power": "Trip: SCRAM above this power",
        "gui.powerfactor.fission.trend.power": "Power", "gui.powerfactor.fission.trend.temp": "Fuel Temperature",
        "gui.powerfactor.fission.trend.reactivity": "Reactivity",
        "gui.powerfactor.fission.adapter": "Logic Adapter Signals: %s", "gui.powerfactor.fission.port.coolant": "Port: Coolant (water in, steam out)",
        "gui.powerfactor.fission.port.fuel": "Port: Fuel (rods in, spent rods out)",
        "gui.powerfactor.fission.scada": "%s%% power, %s K, %s mB/t steam",
        "gui.powerfactor.centrifuge.input": "Uranium ingots (9) or a spent fuel rod", "gui.powerfactor.centrifuge.output": "Separated isotopes",
        "gui.powerfactor.centrifuge.scada": "%s%% through a batch",
        "tooltip.powerfactor.fuel_rod.burnup": "Burnup: %s%%",
        "subtitles.powerfactor.reactor_alarm": "Reactor alarm sounds", "subtitles.powerfactor.reactor_scram": "Reactor SCRAMs",
        "gui.powerfactor.high_pressure_turbine": "High-Pressure Turbine",
        "handbook.powerfactor.fission.title": "Fission Reactor",
        "handbook.powerfactor.fission.text": (
            "Mine uranium deep underground and smelt it into ingots. An Isotope Centrifuge separates nine ingots into one Enriched "
            "Uranium and eight Depleted; three enriched make a Uranium Fuel Rod.\n\n"
            "Build the reactor as a box, 5 to 18 blocks on each side, framed in Reactor Casing along every edge and corner; the walls "
            "between can be Casing or Reactor Glass, so you can glaze every face. Reactor Ports and Logic Adapters go in the walls (not the edges), and the controller in one wall. Inside, stand columns of Fuel Assemblies from "
            "floor to roof, each with a Control Rod Drive above it in the roof, and fill the rest with water, which moderates and cools "
            "(or with Graphite Moderator). Each assembly holds one rod.\n\n"
            "In water a running core glows Cherenkov blue: a shimmering sheath round every fuel column, light rippling over the glass, "
            "and a blue flash when the power surges. The fuel lights the room more brightly as power rises, and a hot core boils "
            "streams of bubbles. Fuel in graphite makes no blue light; it glows red with heat instead. Glass lets gamma out as well "
            "as light: stand close to a glass reactor at power and the Geiger counter climbs and your view speckles. Casing and "
            "the pool stop it.\n\n"
            "Ports switch between Coolant (pipe coolant in and steam or hot coolant out) and Fuel (rods in, spent rods out).\n\n"
            "Coolant: plain water boils into steam right in the core, and its steam voids hold the power down. Three loop coolants stay "
            "liquid and come out hot instead: pipe them through a Heat Exchanger, which boils water into steam and sends the coolant "
            "back cold. Heavy Water (spun out of a water bucket in the centrifuge) moderates better and makes fuel last a third "
            "longer. Liquid Sodium (soda ash from smelted dried kelp, reduced with coal) takes heat three times as well, so the core "
            "runs far denser, but losing sodium adds reactivity. Molten Salt (soda ash, blaze powder and amethyst) takes heat twice as "
            "well and lets the fuel run 400 K hotter. The coolant tank holds one kind at a time: Purge it on the console (shut down "
            "and cool) to change.\n\n"
            "On the console, pull the rods out slowly until the period settles, or set a target and press Auto. Watch the "
            "temperature: hot fuel brakes itself, but run it dry or yank the rods and it melts down. After a shutdown from high "
            "power, xenon builds and holds the core down for a while (the iodine pit). The interlocks SCRAM it at the trip "
            "setpoints; a Logic Adapter outputs any alarm as redstone, and a redstone signal into one SCRAMs the reactor.\n\n"
            "A reactor makes far more steam than ordinary turbines can take. Build a High-Pressure Turbine: 5 wide, 3 tall and 9 "
            "long, Turbine Casing all round with fifteen Steam Rotors and six Generator Magnets inside, the controller in the middle "
            "of the front. It turns 2,500 mB of steam a tick into 50,000 FE/t.\n\n"
            "Spent rods are fiercely radioactive: carry them only in a full Hazmat Suit. A Geiger Counter clicks and reads the dose rate where "
            "you stand; a Dosimeter keeps the dose you have taken in all. The centrifuge reprocesses them into "
            "Plutonium, Depleted Uranium and Nuclear Waste; plutonium and depleted uranium make MOX rods."),
    })


def blackhole(g):
    """Black Hole Power Plant: containment sphere parts and controller."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))

    def cube(model_name, tex, render="minecraft:solid", glow=0):
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render,
              "textures": {"all": pf("block/" + tex), "particle": pf("block/" + tex)},
              "elements": [box([0, 0, 0], [16, 16, 16], "all", glow=glow, full=SIDES)]})

    def states(block, off, on):
        write(asset("blockstates", block + ".json"), {"variants": {"formed=false": {"model": pf("block/" + off)},
                                                                   "formed=true": {"model": pf("block/" + on)}}})
    cube("containment_casing", "containment_casing")
    cube("containment_coil", "containment_coil")
    cube("containment_coil_formed", "containment_coil_on", glow=10)
    cube("event_horizon_glass", "event_horizon_glass", render="minecraft:translucent")
    cube("accretion_injector", "accretion_injector", glow=6)
    cube("hawking_collector", "hawking_collector")
    cube("hawking_collector_formed", "hawking_collector_on", glow=12)
    states("containment_casing", "containment_casing", "containment_casing")
    states("containment_coil", "containment_coil", "containment_coil_formed")
    states("event_horizon_glass", "event_horizon_glass", "event_horizon_glass")
    states("accretion_injector", "accretion_injector", "accretion_injector")
    states("hawking_collector", "hawking_collector", "hawking_collector_formed")
    for suffix, front in (("", "black_hole_front"), ("_on", "black_hole_front_on")):
        write(asset("models", "block", "black_hole_controller" + suffix + ".json"), {
            "parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"side": pf("block/containment_casing"), "front": pf("block/" + front), "particle": pf("block/containment_casing")},
            "elements": [box([0, 0, 0], [16, 16, 16], "side", {"north": "front"}, glow=10 if suffix else 0, full=SIDES)]})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            for formed in ("false", "true"):
                variants[f"facing={facing},formed={formed},running={running}"] = dict(
                    {"model": pf("block/black_hole_controller" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "black_hole_controller.json"), {"variants": variants})
    for b, m in (("black_hole_controller", "black_hole_controller_on"), ("containment_casing", "containment_casing"),
                 ("containment_coil", "containment_coil_formed"), ("event_horizon_glass", "event_horizon_glass"),
                 ("accretion_injector", "accretion_injector"), ("hawking_collector", "hawking_collector_formed")):
        item_def(b, pf("block/" + m))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("containment_casing", ["NAN", "AOA", "NAN"], {"N": "minecraft:netherite_ingot", "A": "#c:ingots/adamantium", "O": "minecraft:crying_obsidian"}, 8)
    shaped("containment_coil", ["FQF", "QMQ", "FQF"], {"F": "#c:ingots/faradaenium", "Q": pf("quantum_processor"), "M": pf("fusion_magnet")}, 2)
    shaped("event_horizon_glass", ["GCG", "CEC", "GCG"], {"G": pf("fusion_glass"), "C": "#c:ingots/cortanium", "E": pf("exotic_matter")}, 4)
    shaped("accretion_injector", ["C C", "HXH", "CCC"], {"C": pf("containment_casing"), "H": "minecraft:hopper", "X": pf("exotic_matter")})
    shaped("hawking_collector", ["CGC", "GXG", "CGC"], {"C": pf("containment_casing"), "G": "minecraft:gold_block", "X": pf("capacitor_bank")})
    shaped("black_hole_controller", ["QSQ", "XNX", "CCC"], {"Q": pf("quantum_processor"), "S": pf("singularity"), "X": pf("exotic_matter"),
                                                           "N": "minecraft:nether_star", "C": pf("containment_casing")})
    for k, v in {"black_hole_controller": "Black Hole Containment Controller", "containment_casing": "Containment Casing",
                 "containment_coil": "Containment Field Coil", "event_horizon_glass": "Event Horizon Glass",
                 "accretion_injector": "Accretion Injector", "hawking_collector": "Hawking Collector"}.items():
        name(f"block.powerfactor.{k}", v)
    LANG.update({
        "gui.powerfactor.black_hole.scada": "Mass %s, %s FE/t net", "gui.powerfactor.black_hole.point.mass": "Mass",
        "gui.powerfactor.black_hole.point.hawking": "Hawking Radiation", "gui.powerfactor.black_hole.point.accretion": "Accretion",
        "gui.powerfactor.black_hole.point.containment": "Containment Field", "gui.powerfactor.black_hole.point.net": "Net Output",
        "gui.powerfactor.black_hole.point.stored": "Stored",
        "gui.powerfactor.black_hole.dormant": "Dormant: put a Singularity in the hopper and Ignite",
        "gui.powerfactor.black_hole.ignite": "Ignite", "gui.powerfactor.black_hole.ignite.tip": "Spin up a hole from a Singularity (500M FE)",
        "gui.powerfactor.black_hole.auto": "Auto", "gui.powerfactor.black_hole.auto.tip": "Feed to hold the target mass (now needs %s items/s)",
        "gui.powerfactor.black_hole.target": "Target mass: smaller holes give far more power but must be fed much faster",
        "gui.powerfactor.black_hole.rate": "Manual feed rate", "gui.powerfactor.black_hole.target_value": "Mass %s",
        "gui.powerfactor.black_hole.rate_value": "%s /s", "gui.powerfactor.black_hole.feed": "%s / %s /s",
        "handbook.powerfactor.black_hole.title": "Black Hole Power Plant",
        "handbook.powerfactor.black_hole.text": (
            "The last word in power: a micro black hole held in a 5x5x5 containment sphere. The controller sits in the middle of the "
            "front of the bottom layer. The top and bottom layers are Containment Casing round a 3x3 of Field Coils; the four walls "
            "between are Event Horizon Glass with casing up the corners, an Accretion Injector in the middle of each wall's lower row and a "
            "Hawking Collector in the middle of the centre row of the side and back walls (the front's is a window). The 3x3x3 inside stays empty.\n\n"
            "Load a Singularity into the hopper and press Ignite: 500M FE spins up a hole of mass 1,000. From then on it radiates "
            "(Hawking radiation grows as the hole shrinks: a quarter the mass, sixteen times the power) and evaporates, so it must "
            "be fed: any item is one unit of mass, Exotic Matter a hundred, a Singularity a thousand. Matter falling in shines as "
            "accretion. Holding it costs a containment field that grows with its size.\n\n"
            "Auto feeds the hole to hold a target mass. The curve on the console shows the bargain: at 1,000 it nets about 1.3M FE/t "
            "on 1.4 items a second; at 200, 36M FE/t, but on 36 items a second, and if the feed stops it is gone in seconds, in a "
            "burst. Let the stored energy run below what the field needs and the hole escapes as a wandering singularity."),
    })


def drones(g):
    """AI mining drones: the hub and the drone item."""
    from storage_data import FACING
    write, asset, pf, name, item_def, flat_item, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "tag", "LANG"))
    for suffix in ("", "_on"):
        write(asset("models", "block", "drone_hub" + suffix + ".json"), {"parent": "minecraft:block/cube", "textures": {
            "north": pf("block/drone_hub_front" + suffix), "south": pf("block/drone_hub_side"), "east": pf("block/drone_hub_side"),
            "west": pf("block/drone_hub_side"), "up": pf("block/drone_hub_top" + suffix), "down": pf("block/drone_hub_side"),
            "particle": pf("block/drone_hub_side")}})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/drone_hub" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "drone_hub.json"), {"variants": variants})
    item_def("drone_hub")
    drops_self("drone_hub")
    tag("block", "minecraft", "mineable/pickaxe", [pf("drone_hub")])
    flat_item("mining_drone")
    write(asset("items", "mining_drone.json"), {"model": {"type": "minecraft:model", "model": pf("item/mining_drone")}})
    shaped("drone_hub", ["QAQ", "CMC", "SBS"], {"Q": pf("quantum_processor"), "A": pf("sensor_chip"), "C": pf("advanced_circuit"),
                                                "M": pf("charging_pad"), "S": "#c:ingots/steel", "B": pf("capacitor_bank")})
    shaped("mining_drone", ["R R", "PLP", "R R"], {"R": pf("electric_motor"), "P": "#c:plates/steel", "L": "minecraft:diamond"})
    name("block.powerfactor.drone_hub", "Drone Hub")
    name("item.powerfactor.mining_drone", "Mining Drone")
    LANG.update({
        "entity.powerfactor.mining_drone": "Mining Drone",
        "gui.powerfactor.drone.log": "The hub AI's log of its decisions", "gui.powerfactor.drone.log_title": "AI Log",
        "gui.powerfactor.drone.vein": "%s blocks, value %s, at %s, %s, %s from the hub",
        "gui.powerfactor.drone.out": "%s drones out", "gui.powerfactor.drone.veins": "%s veins known", "gui.powerfactor.drone.mined": "%s blocks mined",
        "gui.powerfactor.drone.surveying": "Surveying the ground", "gui.powerfactor.drone.scada": "%s drones out, %s veins, %s mined",
        "gui.powerfactor.drone.log.survey_start": "Survey #%s: scanning a %s-wide column down to the deep.",
        "gui.powerfactor.drone.log.survey_done": "Survey done: %s ore blocks in %s veins.",
        "gui.powerfactor.drone.log.best": "Best prospect: %s ×%s, score %s.",
        "gui.powerfactor.drone.log.hazards": "%s veins touch lava; holding those back.",
        "gui.powerfactor.drone.log.no_power": "Not enough charge to launch. Waiting for power.",
        "gui.powerfactor.drone.log.full": "Output full. Drones grounded until it's emptied.",
        "gui.powerfactor.drone.log.assign": "Drone %s → %s ×%s at %s (score %s, ETA %s s).",
        "gui.powerfactor.drone.log.risk": "Drone %s: nothing safer left; accepting a lava vein.",
        "gui.powerfactor.drone.log.returning": "Drone %s returning: %s mined, battery %s%%.",
        "gui.powerfactor.drone.log.vein_done": "Drone %s docked: vein worked out (%s blocks).",
        "gui.powerfactor.drone.log.unfinished": "Drone %s docked; vein unfinished, back in the queue.",
        "handbook.powerfactor.drones.title": "AI Mining Drones",
        "handbook.powerfactor.drones.text": (
            "The Drone Hub's AI surveys the ground in a 49-block-wide column under and around it, clusters the ore it finds into "
            "veins and scores each one: what the ore is worth, how big the vein is, how far away, and whether it touches lava. "
            "Put up to four Mining Drones in its bay and it sends each to the best unclaimed vein. The drones fly out (phasing "
            "through stone in a bore field), cut each ore block with a laser, and fly the haul home; the hub's console maps the "
            "veins and drones live and keeps a log of every decision the AI makes. Each launch charges a drone's 30,000 FE "
            "battery; Speed cards make them fly faster. Re-surveys every two minutes."),
    })


def coolants(g):
    """Reactor coolants (heavy water, liquid sodium, molten salt and their hot sides), soda ash and the Heat Exchanger."""
    from storage_data import FACING
    write, asset, pf, name, item_def, flat_item, shaped, shapeless, drops_self, tag, cooking, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "flat_item", "shaped", "shapeless", "drops_self", "tag", "cooking", "LANG"))
    liquids = {"heavy_water": "Heavy Water", "hot_heavy_water": "Hot Heavy Water", "liquid_sodium": "Liquid Sodium", "hot_sodium": "Hot Sodium",
               "molten_salt": "Molten Salt", "hot_molten_salt": "Hot Molten Salt"}
    for lid, label in liquids.items():
        write(asset("models", "block", f"{lid}.json"), {"textures": {"particle": "minecraft:block/water_still"}})
        write(asset("blockstates", f"{lid}.json"), {"variants": {"": {"model": pf(f"block/{lid}")}}})
        write(asset("items", f"{lid}_bucket.json"), {"model": {"type": "neoforge:fluid_container", "fluid": pf(lid),
            "textures": {"base": "minecraft:item/bucket", "fluid": "neoforge:item/mask/bucket_fluid", "cover": "neoforge:item/mask/bucket_fluid_cover"}}})
        name(f"block.powerfactor.{lid}", label)
        name(f"fluid.powerfactor.{lid}", label)
        name(f"item.powerfactor.{lid}_bucket", f"{label} Bucket")
        tag("fluid", "c", lid, [pf(lid), pf("flowing_" + lid)])
        tag("item", "c", f"buckets/{lid}", [pf(f"{lid}_bucket")])
    tag("fluid", "c", "sodium", ["#c:liquid_sodium"])

    # Soda ash: kelp burnt down. Reduced with coal it gives sodium; with blaze powder and amethyst (the fluoride) it fluxes molten salt.
    flat_item("soda_ash")
    write(asset("items", "soda_ash.json"), {"model": {"type": "minecraft:model", "model": pf("item/soda_ash")}})
    tag("item", "c", "dusts/soda_ash", [pf("soda_ash")])
    cooking(pf("soda_ash"), "minecraft:dried_kelp_block", "soda_ash_from_kelp", 0.3)
    shapeless("liquid_sodium_bucket", ["minecraft:bucket", "#c:dusts/soda_ash", "#c:dusts/soda_ash", "#c:dusts/soda_ash", "#c:dusts/soda_ash",
                                        "minecraft:coal", "minecraft:coal", "minecraft:coal", "minecraft:coal"])
    shapeless("molten_salt_bucket", ["minecraft:bucket", "#c:dusts/soda_ash", "#c:dusts/soda_ash", "#c:dusts/soda_ash", "#c:dusts/soda_ash",
                                      "minecraft:blaze_powder", "minecraft:blaze_powder", "minecraft:amethyst_shard", "minecraft:amethyst_shard"])
    name("item.powerfactor.soda_ash", "Soda Ash")

    # Radiation meters.
    for item in ("geiger_counter", "dosimeter"):
        flat_item(item)
    shaped("geiger_counter", ["IGI", "ICI", "IRI"], {"I": "#c:ingots/iron", "G": "minecraft:glass_pane", "C": pf("control_circuit"),
                                                     "R": "#c:dusts/redstone"})
    shaped("dosimeter", [" I ", "GPG", " I "], {"I": "#c:ingots/iron", "G": "minecraft:glass_pane", "P": "minecraft:paper"})
    name("item.powerfactor.geiger_counter", "Geiger Counter")
    name("item.powerfactor.dosimeter", "Dosimeter")
    LANG.update({
        "tooltip.powerfactor.geiger_counter": "Hold it to hear and read the dose rate where you stand",
        "tooltip.powerfactor.dosimeter": "Hold it to read the dose you have taken in all",
        "gui.powerfactor.dosimeter": "%s mSv",
    })

    # Heat Exchanger: a tube bundle in a dark steel shell; its window glows with the coolant crossing it.
    for suffix in ("", "_on"):
        write(asset("models", "block", f"heat_exchanger{suffix}.json"), {"parent": "minecraft:block/cube", "textures": {
            "north": pf(f"block/heat_exchanger_front{suffix}"), "south": pf("block/heat_exchanger_side"), "east": pf("block/heat_exchanger_side"),
            "west": pf("block/heat_exchanger_side"), "up": pf("block/heat_exchanger_top"), "down": pf("block/heat_exchanger_top"),
            "particle": pf("block/heat_exchanger_side")}})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/heat_exchanger" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "heat_exchanger.json"), {"variants": variants})
    item_def("heat_exchanger")
    drops_self("heat_exchanger")
    tag("block", "minecraft", "mineable/pickaxe", [pf("heat_exchanger")])
    shaped("heat_exchanger", ["SPS", "CBC", "SPS"], {"S": "#c:ingots/steel", "P": pf("fluid_pipe"), "C": "#c:ingots/copper",
                                                     "B": pf("reactor_casing")})
    name("block.powerfactor.heat_exchanger", "Heat Exchanger")

    LANG.update({
        "gui.powerfactor.fission.coolant": "%s: %s",
        "gui.powerfactor.fission.coolant.water": "Water",
        "gui.powerfactor.fission.coolant.water.tip": "boils into steam in the core; steam voids hold the power down",
        "gui.powerfactor.fission.coolant.heavy_water": "Heavy Water",
        "gui.powerfactor.fission.coolant.heavy_water.tip": "a closed loop; better moderation (+2%) and fuel lasts a third longer",
        "gui.powerfactor.fission.coolant.liquid_sodium": "Liquid Sodium",
        "gui.powerfactor.fission.coolant.liquid_sodium.tip": "a closed loop; takes heat three times as well, but losing it adds reactivity",
        "gui.powerfactor.fission.coolant.molten_salt": "Molten Salt",
        "gui.powerfactor.fission.coolant.molten_salt.tip": "a closed loop; takes heat twice as well and lets fuel run 400 K hotter",
        "gui.powerfactor.fission.hot": "Hot %2$s out (%1$s mB stored): pipe it to a Heat Exchanger",
        "gui.powerfactor.fission.point.hot": "Hot Coolant Out",
        "gui.powerfactor.fission.purge": "Purge",
        "gui.powerfactor.fission.purge_now": "Confirm",
        "gui.powerfactor.fission.purge.tip": "Drain the coolant and output tanks, to change coolant",
        "gui.powerfactor.fission.purge.locked": "Purge needs the reactor shut down and below 450 K",
        "gui.powerfactor.heat_exchanger.running": "Steaming",
        "gui.powerfactor.heat_exchanger.no_hot": "No Heat",
        "gui.powerfactor.heat_exchanger.no_water": "No Water",
        "gui.powerfactor.heat_exchanger.backed_up": "Full",
        "gui.powerfactor.heat_exchanger.short.heavy_water": "D2O", "gui.powerfactor.heat_exchanger.short.liquid_sodium": "Sodium",
        "gui.powerfactor.heat_exchanger.short.molten_salt": "Salt",
        "gui.powerfactor.heat_exchanger.heat": "Heat Moved",
        "gui.powerfactor.heat_exchanger.hot": "Hot Coolant",
        "scada.powerfactor.heat_exchanger": "%s mB/t steam from %s mB/t hot coolant",
    })

