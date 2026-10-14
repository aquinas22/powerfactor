"""Exotic ores, metals, quantum components and tools: models, worldgen, loot, tags, recipes and names.

Called from gen_data.py with its globals. Later quantum machines and weapons append here too.
"""

MATERIALS = ["adamantium", "faradaenium", "cortanium", "quantum"]
KINDS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
# ore id: (base texture, overlay glow, raw drop, c: ore name)
ORES = {
    "deepslate_adamantium_ore": ("minecraft:block/deepslate", 8, "raw_adamantium", "adamantium"),
    "faradaenium_ore": ("minecraft:block/end_stone", 15, "raw_faradaenium", "faradaenium"),
    "cortanium_ore": ("minecraft:block/deepslate", 12, "cortanium_shard", "cortanium"),
    "qubit_ore": ("minecraft:block/deepslate", 15, "qubit_crystal", "qubit"),
}


def generate(g):
    write, asset, data, pf, name, item_def, flat_item, NS = (g[k] for k in ("write", "asset", "data", "pf", "name", "item_def", "flat_item", "NS"))
    shaped, shapeless, drops_self, ore_loot, tag, LANG = (g[k] for k in ("shaped", "shapeless", "drops_self", "ore_loot", "tag", "LANG"))
    cooking, machine_recipe, crushing, out = (g[k] for k in ("cooking", "machine_recipe", "crushing", "out"))
    six = ("north", "south", "east", "west", "up", "down")

    # ------------------------------------------------------------ ores: vanilla stone with an emissive crystal overlay
    for ore, (base, glow, drop, cname) in ORES.items():
        overlay = pf(f"block/{ore}_overlay")
        write(asset("models", "block", f"{ore}.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"base": base, "overlay": overlay, "particle": base}, "elements": [
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#base", "cullface": d} for d in six}},
                {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": glow,
                 "faces": {d: {"texture": "#overlay", "cullface": d} for d in six}}]})
        write(asset("blockstates", f"{ore}.json"), {"variants": {"": {"model": pf(f"block/{ore}")}}})
        item_def(ore)
        ore_loot(ore, drop)
        tag("block", "c", f"ores/{cname}", [pf(ore)])
        tag("item", "c", f"ores/{cname}", [pf(ore)])
        tag("block", "c", "ores", [f"#c:ores/{cname}"])
        tag("item", "c", "ores", [f"#c:ores/{cname}"])
        tag("block", "minecraft", "mineable/pickaxe", [pf(ore)])
        tag("block", "minecraft", "needs_diamond_tool" if cname in ("adamantium", "cortanium") else "needs_iron_tool", [pf(ore)])
        tag("block", "c", "ores_in_ground/end_stone" if "end_stone" in base else "ores_in_ground/deepslate", [pf(ore)])
        tag("item", "c", "ores_in_ground/end_stone" if "end_stone" in base else "ores_in_ground/deepslate", [pf(ore)])

    # ------------------------------------------------------------ worldgen
    gen = {
        # name: (ore block, replace tag or block, vein size, placement, biomes, air discard)
        "adamantium": ("deepslate_adamantium_ore", {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, 3,
                       [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -64},
                                                                      "max_inclusive": {"absolute": -36}}}], "#minecraft:is_overworld", 1.0),
        "cortanium": ("cortanium_ore", {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, 2,
                      [{"type": "minecraft:rarity_filter", "chance": 16}, {"type": "minecraft:in_square"},
                       {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -60},
                                                                     "max_inclusive": {"absolute": -48}}}], "#minecraft:is_overworld", 0.0),
        "qubit": ("qubit_ore", {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, 5,
                  [{"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
                   {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -64},
                                                                 "max_inclusive": {"absolute": 0}}}], "#minecraft:is_overworld", 0.3),
        "faradaenium": ("faradaenium_ore", {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}, 6,
                        [{"type": "minecraft:count", "count": 7}, {"type": "minecraft:in_square"},
                         {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 10},
                                                                       "max_inclusive": {"absolute": 72}}}], "#minecraft:is_end", 0.0),
    }
    for key, (block, target, size, placement, biomes, discard) in gen.items():
        write(data(NS, "worldgen", "configured_feature", f"ore_{key}.json"), {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": discard, "size": size, "targets": [{"state": {"Name": pf(block)}, "target": target}]}})
        write(data(NS, "worldgen", "placed_feature", f"ore_{key}.json"), {"feature": pf(f"ore_{key}"),
                                                                          "placement": placement + [{"type": "minecraft:biome"}]})
        write(data(NS, "neoforge", "biome_modifier", f"add_{key}_ore.json"), {"type": "neoforge:add_features", "biomes": biomes,
            "features": pf(f"ore_{key}"), "step": "underground_ores", "neoforge:conditions": [{"type": pf("worldgen_enabled"), "feature": key}]})

    # ------------------------------------------------------------ materials
    for item in ("raw_adamantium", "adamantium_ingot", "raw_faradaenium", "faradaenium_ingot", "cortanium_shard", "cortanium_ingot",
                 "qubit_crystal", "quantum_alloy_ingot", "quantum_dust", "entangled_pair", "quantum_photomask", "quantum_processor",
                 "exotic_matter", "singularity"):
        flat_item(item)
    for block in ("adamantium", "faradaenium", "cortanium", "qubit_crystal", "quantum_alloy"):
        b = block + "_block"
        write(asset("models", "block", f"{b}.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf(f"block/{b}")}})
        write(asset("blockstates", f"{b}.json"), {"variants": {"": {"model": pf(f"block/{b}")}}})
        item_def(b)
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
        tag("block", "minecraft", "needs_diamond_tool", [pf(b)])
        cname = block.replace("_crystal", "")
        tag("block", "c", f"storage_blocks/{cname}", [pf(b)])
        tag("item", "c", f"storage_blocks/{cname}", [pf(b)])
        unit = {"qubit_crystal": "qubit_crystal", "quantum_alloy": "quantum_alloy_ingot"}.get(block, block + "_ingot")
        shaped(b, ["III", "III", "III"], {"I": pf(unit)}, file=b)
        shapeless(unit, [pf(b)], 9, file=f"{unit}_from_block")
    for metal, item in (("adamantium", "adamantium_ingot"), ("faradaenium", "faradaenium_ingot"), ("cortanium", "cortanium_ingot"),
                        ("quantum_alloy", "quantum_alloy_ingot")):
        tag("item", "c", f"ingots/{metal}", [pf(item)])
        tag("item", "c", "ingots", [f"#c:ingots/{metal}"])
    tag("item", "c", "raw_materials/adamantium", [pf("raw_adamantium")])
    tag("item", "c", "raw_materials/faradaenium", [pf("raw_faradaenium")])
    tag("item", "c", "raw_materials", ["#c:raw_materials/adamantium", "#c:raw_materials/faradaenium"])
    tag("item", "c", "gems/qubit", [pf("qubit_crystal")])
    tag("item", "c", "gems/cortanium", [pf("cortanium_shard")])
    tag("item", "c", "gems", ["#c:gems/qubit", "#c:gems/cortanium"])
    tag("item", "c", "dusts/quantum", [pf("quantum_dust")])
    tag("item", "c", "dusts", ["#c:dusts/quantum"])
    for m, unit in (("adamantium", "#c:ingots/adamantium"), ("faradaenium", "#c:ingots/faradaenium"), ("cortanium", "#c:ingots/cortanium"),
                    ("quantum", "#c:ingots/quantum_alloy")):
        tag("item", NS, f"repairs/{m}", [unit])

    # Processing: arc furnace for the refractory metals, blast furnace for faradaenium.
    machine_recipe("arc_smelting", "adamantium_ingot", [("#c:raw_materials/adamantium", 1), (pf("graphite"), 1)], [out("adamantium_ingot")], time=400)
    cooking(pf("faradaenium_ingot"), "#c:raw_materials/faradaenium", "faradaenium_ingot", 2.0)
    machine_recipe("arc_smelting", "cortanium_ingot", [("#c:gems/cortanium", 4), ("minecraft:netherite_scrap", 1)], [out("cortanium_ingot")],
                   time=800)
    crushing("qubit_crystal", "#c:gems/qubit", out("quantum_dust", 2), bonus=[out("quantum_dust", 1, 0.25)])
    crushing("deepslate_adamantium_ore", pf("deepslate_adamantium_ore"), out("raw_adamantium", 2), bonus=[out("raw_adamantium", 1, 0.25)])
    crushing("faradaenium_ore", pf("faradaenium_ore"), out("raw_faradaenium", 2), bonus=[out("raw_faradaenium", 1, 0.35)])
    crushing("qubit_ore", pf("qubit_ore"), out("qubit_crystal", 3), bonus=[out("quantum_dust", 1, 0.5)])
    machine_recipe("alloying", "quantum_alloy_ingot", [("#c:ingots/adamantium", 1), ("#c:dusts/quantum", 4)], [out("quantum_alloy_ingot")], time=600)
    shaped("quantum_photomask", ["GQG", "QMQ", "GQG"], {"G": "#c:glass_panes", "Q": "#c:gems/qubit", "M": pf("logic_photomask")})
    for product, inputs in (("entangled_pair", [("#c:gems/qubit", 2), ("#c:ender_pearls", 1)]),
                            ("quantum_processor", [(pf("microprocessor"), 1), (pf("entangled_pair"), 1)])):
        write(data(NS, "recipe", "laser_engraving", f"{product}.json"), {"type": pf("laser_engraving"),
            "inputs": [{"ingredient": i, "count": c} for i, c in inputs], "tool": pf("quantum_photomask"),
            "results": [{"item": pf(product), "count": 1}], "time": 600})

    # ------------------------------------------------------------ tools
    handles = {"adamantium": "#c:rods/wooden", "faradaenium": "#c:rods/blaze", "cortanium": "#c:rods/blaze", "quantum": "minecraft:end_rod"}
    heads = {"adamantium": "#c:ingots/adamantium", "faradaenium": "#c:ingots/faradaenium", "cortanium": "#c:ingots/cortanium",
             "quantum": "#c:ingots/quantum_alloy"}
    patterns = {"sword": ["H", "H", "S"], "pickaxe": ["HHH", " S ", " S "], "axe": ["HH", "HS", " S"], "shovel": ["H", "S", "S"],
                "hoe": ["HH", " S", " S"]}
    pretty_m = {"adamantium": "Adamantium", "faradaenium": "Faradaenium", "cortanium": "Cortanium", "quantum": "Quantum"}
    for m in MATERIALS:
        for k in KINDS:
            item = f"{m}_{k}"
            flat_item(item, "minecraft:item/handheld")
            key = {"H": heads[m], "S": handles[m]}
            pattern = patterns[k]
            if m == "quantum":
                # The processor sits in the haft, where the head meets the handle.
                # (On the sword, the only handle row is the hilt itself.)
                at = max(i for i, row in enumerate(pattern) if "S" in row and (i == len(pattern) - 2 or k == "sword"))
                pattern = [row.replace("S", "P", 1) if i == at else row for i, row in enumerate(pattern)]
                key["P"] = pf("quantum_processor")
                if not any("S" in row for row in pattern):
                    del key["S"]
            shaped(item, pattern, key, category="equipment")
            name(f"item.powerfactor.{item}", f"{pretty_m[m]} {k.capitalize()}")
            tag("item", "minecraft", {"sword": "swords", "pickaxe": "pickaxes", "axe": "axes", "shovel": "shovels", "hoe": "hoes"}[k], [pf(item)])
            tag("item", "c", "tools/" + {"sword": "melee_weapon", "pickaxe": "mining_tool", "axe": "mining_tool", "shovel": "mining_tool",
                                          "hoe": "mining_tool"}[k], [pf(item)])

    # ------------------------------------------------------------ names
    names = {"deepslate_adamantium_ore": "Deepslate Adamantium Ore", "faradaenium_ore": "Faradaenium Ore", "cortanium_ore": "Cortanium Ore",
             "qubit_ore": "Qubit Crystal Ore", "adamantium_block": "Block of Adamantium", "faradaenium_block": "Block of Faradaenium",
             "cortanium_block": "Block of Cortanium", "qubit_crystal_block": "Block of Qubit Crystal", "quantum_alloy_block": "Block of Quantum Alloy"}
    for k, v in names.items():
        name(f"block.powerfactor.{k}", v)
    items = {"raw_adamantium": "Raw Adamantium", "adamantium_ingot": "Adamantium Ingot", "raw_faradaenium": "Raw Faradaenium",
             "faradaenium_ingot": "Faradaenium Ingot", "cortanium_shard": "Cortanium Shard", "cortanium_ingot": "Cortanium Ingot",
             "qubit_crystal": "Qubit Crystal", "quantum_alloy_ingot": "Quantum Alloy Ingot", "quantum_dust": "Quantum Dust",
             "entangled_pair": "Entangled Qubit Pair", "quantum_photomask": "Quantum Photomask", "quantum_processor": "Quantum Processor",
             "exotic_matter": "Exotic Matter", "singularity": "Singularity"}
    for k, v in items.items():
        name(f"item.powerfactor.{k}", v)
    LANG.update({
        "tooltip.powerfactor.exotic.excavate": "Excavates 3×3 (Quantum: 5×5) facing you",
        "tooltip.powerfactor.exotic.vein": "Fells whole ore veins and trees (up to 64 blocks)",
        "tooltip.powerfactor.exotic.magnet": "Magnetic: drops and experience go straight to you",
        "tooltip.powerfactor.exotic.sneak": "Sneak to mine a single block",
        "tooltip.powerfactor.exotic.sword.adamantium": "Crushing weight: heavy knockback and a staggering slow",
        "tooltip.powerfactor.exotic.sword.faradaenium": "Chain lightning leaps to three more foes",
        "tooltip.powerfactor.exotic.sword.cortanium": "Unbreakable · wounds wither the struck",
        "tooltip.powerfactor.exotic.sword.quantum": "Unbreakable · tears a rift: arcs, splash damage and lift",
    })
    # ------------------------------------------------------------ exotic armor
    armor_shapes = {"helmet": ["HHH", "H H"], "chestplate": ["H H", "HHH", "HHH"], "leggings": ["HHH", "H H", "H H"], "boots": ["H H", "H H"]}
    armor_tags = {"helmet": "head_armor", "chestplate": "chest_armor", "leggings": "leg_armor", "boots": "foot_armor"}
    for m in MATERIALS:
        write(asset("equipment", f"{m}.json"), {"layers": {
            "humanoid": [{"texture": pf(m)}], "humanoid_leggings": [{"texture": pf(m)}], "humanoid_baby": [{"texture": pf(m)}]}})
        for k, shape in armor_shapes.items():
            item = f"{m}_{k}"
            flat_item(item)
            key = {"H": heads[m]}
            if m == "quantum":
                # A processor ties each quantum piece together, in place of one alloy ingot.
                shape = [row.replace("H", "P", 1) if i == 0 else row for i, row in enumerate(shape)]
                key["P"] = pf("quantum_processor")
            shaped(item, shape, key, category="equipment")
            name(f"item.powerfactor.{item}", f"{pretty_m[m]} {k.capitalize()}")
            tag("item", "minecraft", armor_tags[k], [pf(item)])
            tag("item", "c", "armors", [pf(item)])
    LANG.update({
        "tooltip.powerfactor.exotic_armor.set.adamantium": "Full set: Bulwark",
        "tooltip.powerfactor.exotic_armor.bonus.adamantium": "Resistance · heavy knockback resistance",
        "tooltip.powerfactor.exotic_armor.set.faradaenium": "Full set: Dynamo",
        "tooltip.powerfactor.exotic_armor.bonus.faradaenium": "Speed · attackers are struck by chain lightning",
        "tooltip.powerfactor.exotic_armor.set.cortanium": "Full set: Crucible",
        "tooltip.powerfactor.exotic_armor.bonus.cortanium": "Unbreakable · fire immunity and strength · attackers burn",
        "tooltip.powerfactor.exotic_armor.set.quantum": "Full set: Phase",
        "tooltip.powerfactor.exotic_armor.bonus.quantum": "All of the above, night vision, no fall damage · a phase shift voids one blow every 8 s",
    })
    LANG["handbook.powerfactor.exotic.title"] = "Exotic Ores & Tools"
    LANG["handbook.powerfactor.exotic.text"] = (
        "Four fictional materials lie beyond iron and diamond. All use common c: tags, so other mods can use them.\n\n"
        "Adamantium: very rare, deep in deepslate (y -64 to -36), never touching caves. Arc-smelt raw adamantium with graphite. "
        "Adamantium pickaxes and shovels excavate 3×3.\n\n"
        "Faradaenium: glowing teal veins in End stone. Blast or smelt it. Faradaenium tools are magnetic, and the sword chains lightning.\n\n"
        "Cortanium: one nearly indestructible crimson block in roughly every sixteen chunks, between y -60 and -48. "
        "Arc-smelt four shards with netherite scrap. Cortanium tools never break and fell whole veins and trees.\n\n"
        "Qubit Crystal: violet crystals deep underground, the root of the quantum chain. Crush crystals into Quantum Dust. "
        "Alloy dust with adamantium into Quantum Alloy. Laser-engrave crystals and an ender pearl into an Entangled Pair, "
        "then engrave that onto a microprocessor to make a Quantum Processor. Quantum Alloy tools do everything at once: "
        "5×5 excavation, vein mining, magnetism and an unbreakable rift-tearing blade.\n\nSneak to mine single blocks. "
        "Each ore can be disabled in the server config's worldgen section.\n\n"
        "Armor: each material makes a full suit. Wearing all four pieces of one material grants its set bonus. Adamantium "
        "Bulwark gives resistance. Faradaenium Dynamo gives speed and arcs lightning into attackers. Cortanium Crucible never "
        "breaks, grants fire immunity and strength, and sets attackers alight. Quantum Phase does all of that, adds night "
        "vision and removes fall damage, and a phase shift lets one blow pass through you every eight seconds.")


def quarries(g):
    """Industrial and Quantum Quarry: models, recipes, loot and text."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    horizontal = {k: v for k, v in FACING.items() if k not in ("up", "down")}

    def model(model_name, elements, textures, render="minecraft:cutout"):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: (v if ":" in v else pf("block/" + v)) for k, v in textures.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render, "textures": tex,
                                                                "elements": elements})

    def states(block):
        variants = {}
        for facing, rot in horizontal.items():
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = dict({"model": pf(f"block/{block}{'_on' if running == 'true' else ''}")}, **rot)
        write(asset("blockstates", block + ".json"), {"variants": variants})

    T = {"hazard": "hazard_block", "steel": "finish_steel", "dark": "finish_dark", "chrome": "finish_chrome", "lamp": "industrial_lamp",
         "lamp_on": "industrial_lamp_on", "rivet": "riveted_steel", "casing": "storage_casing", "lit": "storage_casing_lit",
         "glass": "storage_glass", "pcb": "quantum_processor_side", "plate": "storage_controller_plate", "particle": "riveted_steel"}
    for suffix in ("", "_on"):
        on = bool(suffix)
        # Industrial: hazard-striped plinth, laser head facing the pit, cable drum on top.
        e = [box([0, 0, 0], [16, 9, 16], "hazard", {"up": "rivet", "down": "rivet"}),
             box([1.5, 9, 1.5], [14.5, 14, 14.5], "dark", {"up": "rivet"}),
             box([4, 14, 4], [12, 16, 12], "steel"), box([3.5, 14.6, 7], [12.5, 15.4, 9], "chrome"),
             box([4, 3, -1], [12, 8, 0], "steel"), box([5.5, 4, -1.6], [10.5, 7, -1], "chrome"),
             box([6.5, 4.6, -1.8], [9.5, 6.4, -1.6], "lamp_on" if on else "lamp", glow=15 if on else 0, full=SIDES),
             box([1, 9.5, 0.5], [3, 13.5, 1.5], "lamp_on" if on else "lamp", glow=12 if on else 0, full=SIDES),
             box([13, 9.5, 0.5], [15, 13.5, 1.5], "lamp_on" if on else "lamp", glow=12 if on else 0, full=SIDES)]
        model("quarry" + suffix, e, T)
        # Quantum: a dark field-coil plinth, glowing seams, and a glass bell where the gyroscope spins (renderer).
        e = [box([0, 0, 0], [16, 8, 16], "pcb", {"up": "plate", "down": "plate"}, full=SIDES),
             box([1, 8, 1], [15, 10, 15], "casing", {"up": "plate"}, full=("up",)),
             box([3, 10, 3], [13, 16, 13], "glass", full=SIDES)]
        for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
            e.append(box([x, 8, z], [x + 1, 16, z + 1], "lit" if on else "casing", glow=15 if on else 0))
        e.append(box([2, 15.5, 2], [14, 16, 3], "chrome"))
        e.append(box([2, 15.5, 13], [14, 16, 14], "chrome"))
        model("quantum_quarry" + suffix, e, T, "minecraft:translucent")
    for b in ("quarry", "quantum_quarry"):
        states(b)
        item_def(b, pf(f"block/{b}_on"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("quarry", ["PDP", "MCM", "PGP"], {"P": "#c:plates/steel", "D": pf("drill_head"), "M": pf("electric_motor"),
                                            "C": pf("advanced_circuit"), "G": pf("steel_gear")})
    shaped("quantum_quarry", ["AQA", "PXP", "AEA"], {"A": "#c:ingots/quantum_alloy", "Q": pf("quantum_processor"), "P": pf("entangled_pair"),
                                                     "X": pf("quarry"), "E": "#c:ender_pearls"})
    name("block.powerfactor.quarry", "Industrial Quarry")
    name("block.powerfactor.quantum_quarry", "Quantum Quarry")
    statuses = {"idle": "Idle", "mining": "Mining", "no_power": "Needs power", "output_full": "Output full: connect storage",
                "done": "Finished: reached the floor", "disabled": "Stopped (redstone/SCADA)", "waiting_chunks": "Waiting for chunks to load"}
    for k, v in statuses.items():
        LANG[f"gui.powerfactor.quarry.status.{k}"] = v
    LANG.update({
        "gui.powerfactor.quarry.area": "Area %s×%s · Layer %s/%s", "gui.powerfactor.quarry.mined": "Mined %s · %s FE/t",
        "gui.powerfactor.quarry.depth": "Depth %s of %s layers",
        "gui.powerfactor.quarry.smaller": "Shrink the area (restarts at the top)", "gui.powerfactor.quarry.bigger": "Grow the area (restarts at the top)",
        "gui.powerfactor.quarry.silk": "Silk", "gui.powerfactor.quarry.silk.tip": "Silk Touch: 4× energy per block",
        "gui.powerfactor.quarry.fortune.tip": "Fortune level: 1.585× energy per level",
        "gui.powerfactor.quarry.void": "Void", "gui.powerfactor.quarry.void.tip": "Void common rubble (stone, dirt, gravel, netherrack…)",
        "gui.powerfactor.quarry.restart": "Restart from the top",
        "scada.powerfactor.quarry": "%s · %s blocks mined",
    })
    LANG["handbook.powerfactor.quarry.title"] = "Quarries"
    LANG["handbook.powerfactor.quarry.text"] = (
        "Quarries dig a square in front of them, layer by layer, down to just above the world floor. Products go into a touching "
        "storage network (via any network block next to the quarry) or any adjacent inventory; when nothing has room the quarry waits.\n\n"
        "The Industrial Quarry drives a laser gantry over squares of 9 to 33 blocks, one block every four ticks. "
        "The Quantum Quarry needs no frame. It folds space under a floating projector, reaches 65×65, takes four blocks a tick "
        "and costs 40% less per block. Speed cards double the rate each.\n\n"
        "Energy per block: 25 FE × hardness, ×1.585 per Fortune level, ×4 with Silk Touch. Fluids are cleared, and blocks with "
        "block entities (chests, spawners, machines) are left alone. Void mode discards rubble so storage fills only with ores and gems.")


def weapons(g):
    """The arsenal: 3D in-hand models (flat icons in inventories), recipes and text."""
    write, asset, pf, name, shaped, LANG, el, TOOL_DISPLAY = (g[k] for k in ("write", "asset", "pf", "name", "shaped", "LANG", "el", "TOOL_DISPLAY"))
    tex = {"body": pf("item/model/weapon_body"), "steel": pf("item/model/weapon_steel"), "chrome": pf("item/model/drill_chrome"),
           "grip": pf("item/model/drill_grip"), "cyan": pf("item/model/weapon_glow_cyan"), "green": pf("item/model/weapon_glow_green"),
           "violet": pf("item/model/weapon_glow_violet"), "orange": pf("item/model/weapon_glow_orange")}
    grip_rot = {"angle": -22.5, "axis": "z", "origin": [3, 7, 8]}
    gun_display = dict(TOOL_DISPLAY,
                       firstperson_righthand={"rotation": [0, 95, 3], "translation": [2.5, 3, -1], "scale": [0.55, 0.55, 0.55]},
                       firstperson_lefthand={"rotation": [0, -85, -3], "translation": [2.5, 3, -1], "scale": [0.55, 0.55, 0.55]},
                       thirdperson_righthand={"rotation": [0, 90, 0], "translation": [0, 2.5, -1], "scale": [0.75, 0.75, 0.75]},
                       thirdperson_lefthand={"rotation": [0, 90, 0], "translation": [0, 2.5, -1], "scale": [0.75, 0.75, 0.75]})
    models = {
        "railgun": [el([-6, 6.5, 7], [0, 10, 9], "#grip"), el([0, 7, 6], [9, 11.5, 10], "#body"),
                    el([2, 2.5, 7.25], [4.5, 7, 8.75], "#grip", grip_rot),
                    el([9, 10, 6.5], [24, 11, 7.5], "#chrome"), el([9, 10, 8.5], [24, 11, 9.5], "#chrome"),
                    el([9, 7, 6.5], [24, 8, 7.5], "#chrome"), el([9, 7, 8.5], [24, 8, 9.5], "#chrome"),
                    el([9, 8.6, 7.6], [24, 9.4, 8.4], "#cyan", emissive=True),
                    el([3, 11.5, 7.25], [8, 13, 8.75], "#body"), el([8, 11.8, 7.5], [8.3, 12.7, 8.5], "#cyan", emissive=True),
                    el([24, 6.5, 6], [25, 11.5, 10], "#steel")]
                   + [el([x, 7.6, 6.9], [x + 1.2, 10.4, 9.1], "#cyan", emissive=True) for x in (10.5, 13.5, 16.5, 19.5, 22.2)],
        "plasma_rifle": [el([-5, 6, 7], [0, 10, 9], "#grip"), el([0, 6, 5.5], [11, 12, 10.5], "#body"),
                         el([2, 1.5, 7.25], [4.5, 6, 8.75], "#grip", grip_rot), el([4, 12, 6.5], [10, 14.5, 9.5], "#green", emissive=True),
                         el([3.5, 14.5, 6], [10.5, 15, 10], "#chrome"), el([11, 7.5, 6.5], [18, 10.5, 9.5], "#steel"),
                         el([18, 8, 7], [18.6, 10, 9], "#green", emissive=True)]
                        + [el([x, 10.5, 6.2], [x + 0.6, 12, 9.8], "#chrome") for x in (12, 13.5, 15, 16.5)],
        "singularity_launcher": [el([-4, 6, 5], [18, 13, 11], "#body"), el([18, 5, 4], [20, 14, 12], "#chrome"),
                                 el([19.6, 7, 6], [20.3, 12, 10], "#orange", emissive=True), el([2, 5.8, 4.8], [3, 13.2, 11.2], "#violet", emissive=True),
                                 el([12, 5.8, 4.8], [13, 13.2, 11.2], "#violet", emissive=True), el([2, 1, 7.25], [4.5, 6, 8.75], "#grip", grip_rot),
                                 el([10, 2, 7.25], [11.5, 6, 8.75], "#grip"), el([6, 13, 7.5], [9, 14.5, 8.5], "#steel"),
                                 el([-5, 7, 6], [-4, 12, 10], "#steel")],
        "quantum_disassembler": [el([-3, 6.5, 7], [0, 10, 9], "#grip"), el([0, 6, 6], [9, 11, 10], "#body"),
                                 el([2, 1.5, 7.25], [4.5, 6, 8.75], "#grip", grip_rot), el([2, 11, 7.5], [5, 12, 8.5], "#cyan", emissive=True),
                                 el([9, 10, 7], [16, 11, 9], "#chrome"), el([9, 6, 7], [16, 7, 9], "#chrome"),
                                 el([9, 7.5, 7.5], [12, 9.5, 8.5], "#violet", emissive=True),
                                 el([14.2, 7, 7.7], [15.5, 10, 8.3], "#violet", emissive=True)],
    }
    for item, elements in models.items():
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        write(asset("models", "item", f"{item}_3d.json"), {"textures": dict({k: v for k, v in tex.items() if k in used}, particle=tex["body"]),
                                                          "gui_light": "side", "display": gun_display, "elements": elements})
    blade = [el([7, 0, 7], [9, 1.5, 9], "#chrome"), el([7.25, 1.5, 7.25], [8.75, 6, 8.75], "#grip"),
             el([5, 6, 6.5], [11, 7, 9.5], "#violet", emissive=True), el([7.3, 7, 7.7], [8.7, 22, 8.3], "#violet", emissive=True),
             el([7.75, 7, 7.6], [8.25, 22.5, 8.4], "#cyan", emissive=True), el([7.6, 22, 7.75], [8.4, 23.5, 8.25], "#cyan", emissive=True)]
    blade_display = {"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
                     "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
                     "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                     "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}
    write(asset("models", "item", "quantum_blade_3d.json"), {"textures": {"chrome": tex["chrome"], "grip": tex["grip"], "violet": tex["violet"],
                                                                         "cyan": tex["cyan"], "particle": tex["violet"]},
                                                            "gui_light": "side", "display": blade_display, "elements": blade})
    for item in ("railgun", "plasma_rifle", "singularity_launcher", "quantum_disassembler", "quantum_blade"):
        write(asset("models", "item", f"{item}.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": pf(f"item/{item}")}})
        write(asset("items", f"{item}.json"), {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": pf(f"item/{item}")}}],
            "fallback": {"type": "minecraft:model", "model": pf(f"item/{item}_3d")}}})

    P, Q = "#c:plates/steel", pf("quantum_processor")
    shaped("railgun", ["CCC", "FMS", "PG "], {"C": pf("copper_coil"), "F": "#c:ingots/faradaenium", "M": pf("microprocessor"),
                                             "S": pf("sensor_chip"), "P": P, "G": pf("capacitor")}, category="equipment")
    shaped("plasma_rifle", [" HF", "PBP", "G  "], {"H": pf("heating_coil"), "F": "#c:ingots/faradaenium", "P": P, "B": pf("fluid_tank"),
                                                  "G": pf("capacitor")}, category="equipment")
    shaped("singularity_launcher", ["AQA", "SXS", "AGA"], {"A": "#c:ingots/quantum_alloy", "Q": Q, "S": pf("singularity"),
                                                           "X": pf("railgun"), "G": pf("capacitor")}, category="equipment")
    shaped("quantum_blade", ["  A", "EA ", "QE "], {"A": "#c:ingots/quantum_alloy", "E": pf("entangled_pair"), "Q": Q}, category="equipment")
    shaped("quantum_disassembler", ["AQA", " C ", " G "], {"A": "#c:ingots/quantum_alloy", "Q": Q, "C": pf("electric_drill"),
                                                           "G": pf("capacitor")}, category="equipment")
    for k, v in {"railgun": "Railgun", "plasma_rifle": "Plasma Rifle", "singularity_launcher": "Singularity Launcher",
                 "quantum_blade": "Quantum Blade", "quantum_disassembler": "Quantum Disassembler"}.items():
        name(f"item.powerfactor.{k}", v)
    LANG.update({
        "entity.powerfactor.plasma_bolt": "Plasma Bolt", "entity.powerfactor.singularity": "Singularity",
        "tooltip.powerfactor.railgun": "Hold to charge, release to fire · pierces creatures and glass",
        "tooltip.powerfactor.railgun.cost": "Up to %s FE per shot",
        "tooltip.powerfactor.plasma_rifle": "Hold to fire plasma bolts · overheats past 100",
        "tooltip.powerfactor.plasma_rifle.heat": "Coil heat: %s/%s",
        "tooltip.powerfactor.singularity_launcher": "Fires a black hole that drags everything in, then collapses",
        "tooltip.powerfactor.singularity_launcher.ammo": "Uses a Singularity and %s FE per shot",
        "tooltip.powerfactor.quantum_blade": "Right-click: phase dash · cuts through everything in the way",
        "tooltip.powerfactor.disassembler": "Sneak-use to change mode · drops go to your inventory",
        "gui.powerfactor.disassembler.mode": "Mode: %s", "gui.powerfactor.disassembler.mode.single": "Single",
        "gui.powerfactor.disassembler.mode.area_3": "3×3", "gui.powerfactor.disassembler.mode.area_5": "5×5",
        "gui.powerfactor.disassembler.mode.vein": "Vein",
        "gui.powerfactor.railgun.ready": "FULL CHARGE", "gui.powerfactor.plasma.vent": "VENTING",
        "gui.powerfactor.launcher.no_ammo": "No Singularity to load", "gui.powerfactor.launcher.no_power": "Launcher capacitors are empty",
    })
    LANG["handbook.powerfactor.arsenal.title"] = "The Arsenal"
    LANG["handbook.powerfactor.arsenal.text"] = (
        "All arsenal weapons run on FE: charge them in a Charger or on a Charging Pad.\n\n"
        "Railgun: hold to charge. The view zooms and the reticle fills. Release to fire a hypersonic slug up to 160 blocks. "
        "It passes through every creature in line and punches through glass, leaves and ice. A full charge costs 12,000 FE.\n\n"
        "Plasma Rifle: hold to stream plasma bolts that burst on impact without breaking blocks. Each shot heats the coils; "
        "at 100 the rifle vents for three seconds.\n\n"
        "Singularity Launcher: consumes a Singularity and 250,000 FE. The black hole pulls every creature and loose item within "
        "14 blocks for five seconds, harms anything at its horizon, then collapses in a blinding detonation. Terrain is safe unless "
        "the server enables super-weapon block damage.\n\n"
        "Quantum Blade: an unbreakable energy sword. Right-click to phase-dash ten blocks, cutting everything you pass through.\n\n"
        "Quantum Disassembler: pickaxe, axe, shovel and hoe in one. Sneak-use cycles Single, 3×3, 5×5 and Vein modes. "
        "The affected blocks are outlined, and drops go to your inventory.")


def quantum_machines(g):
    """Particle Collider and Zero-Point Generator."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"pcb": "quantum_processor_side", "plate": "storage_controller_plate", "ring": "assembler_base", "casing": "storage_casing",
         "lit": "storage_casing_lit", "glass": "storage_glass", "chrome": "finish_chrome", "dark": "finish_dark", "particle": "quantum_processor_side"}

    def model(model_name, elements, render="minecraft:translucent"):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render, "textures": tex,
                                                                "elements": elements})

    for suffix in ("", "_on"):
        on = bool(suffix)
        lit = "lit" if on else "casing"
        glow = 15 if on else 0
        # Collider: a plinth carrying four ring-support emitters; the ring itself floats above (renderer).
        e = [box([0, 0, 0], [16, 6, 16], "pcb", {"up": "ring", "down": "plate"}, full=SIDES),
             box([1, 6, 1], [15, 8, 15], "casing", {"up": "ring"}, full=("up",))]
        for x, z in ((0.5, 7), (13.5, 7), (7, 0.5), (7, 13.5)):
            e.append(box([x, 8, z], [x + 2, 14, z + 2], "chrome"))
            e.append(box([x + 0.3, 14, z + 0.3], [x + 1.7, 15, z + 1.7], lit, glow=glow))
        e.append(box([6, 8, 6], [10, 13, 10], "glass", full=SIDES))
        model("particle_collider" + suffix, e)
        # Zero-point: an armoured base with a containment cradle and four electrode pylons the arcs strike.
        e = [box([0, 0, 0], [16, 5, 16], "pcb", {"up": "plate", "down": "plate"}, full=SIDES),
             box([2, 5, 2], [14, 8, 14], "dark"), box([4, 8, 4], [12, 9, 12], "casing", {"up": "ring"}, full=("up",))]
        for x, z in ((0.5, 0.5), (14, 0.5), (0.5, 14), (14, 14)):
            e.append(box([x, 5, z], [x + 1.5, 15, z + 1.5], "chrome"))
            e.append(box([x - 0.2, 15, z - 0.2], [x + 1.7, 16.5, z + 1.7], lit, glow=glow))
        model("zero_point_generator" + suffix, e)
    for b in ("particle_collider", "zero_point_generator"):
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = dict({"model": pf(f"block/{b}{'_on' if running == 'true' else ''}")}, **rot)
        write(asset("blockstates", b + ".json"), {"variants": variants})
        item_def(b, pf(f"block/{b}_on"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("particle_collider", ["AEA", "QSQ", "ACA"], {"A": "#c:ingots/quantum_alloy", "E": pf("electric_motor"), "Q": pf("quantum_processor"),
                                                       "S": "#c:storage_blocks/faradaenium", "C": pf("capacitor_bank")})
    shaped("zero_point_generator", ["CQC", "ASA", "CXC"], {"C": "#c:ingots/cortanium", "Q": pf("quantum_processor"), "A": "#c:ingots/quantum_alloy",
                                                          "S": pf("singularity"), "X": pf("particle_collider")})
    name("block.powerfactor.particle_collider", "Particle Collider")
    name("block.powerfactor.zero_point_generator", "Zero-Point Energy Generator")
    LANG.update({
        "gui.powerfactor.collider.idle": "Idle", "gui.powerfactor.collider.needs": "%s per run · %s FE",
        "gui.powerfactor.collider.recipe.qubit": "→ Qubit Crystals", "gui.powerfactor.collider.recipe.faradaenium": "→ Faradaenium",
        "gui.powerfactor.collider.recipe.adamantium": "→ Adamantium", "gui.powerfactor.collider.recipe.cortanium": "→ Cortanium",
        "gui.powerfactor.collider.recipe.exotic_matter": "→ Exotic Matter", "gui.powerfactor.collider.recipe.singularity": "→ Singularity",
        "gui.powerfactor.collider.recipe.nether_star": "→ Nether Star",
        "gui.powerfactor.zero_point.stability": "Field %s%%", "gui.powerfactor.zero_point.fuel": "Matter",
        "scada.powerfactor.collider": "Collision energy %s%%", "scada.powerfactor.zero_point": "Vacuum %s%% · %s s of matter left",
    })
    LANG["handbook.powerfactor.quantum.title"] = "Quantum Machines"
    LANG["handbook.powerfactor.quantum.text"] = (
        "Particle Collider: accelerates matter around a superconducting ring until it banks the collision energy (up to 250,000 FE/t). "
        "Diamond becomes two Qubit Crystals (4M FE). Two Ender Pearls become Raw Faradaenium (8M FE). Eight Iron Ingots become Raw "
        "Adamantium (16M FE). Netherite Scrap becomes a Cortanium Shard (30M FE). Four Qubit Crystals become Exotic Matter (25M FE), "
        "and four Exotic Matter become a Singularity (80M FE). Three Wither Skeleton Skulls become a Nether Star.\n\n"
        "Zero-Point Energy Generator: Exotic Matter holds a pocket of false vacuum open. The generator takes thirty seconds to "
        "stabilise, then supplies up to 50,000 FE/t to every adjacent receiver. One Exotic Matter lasts twenty minutes. Arcs strike "
        "its electrodes, and the gyroscope spins faster as the vacuum stabilises.\n\nQuantum Crafting Processors, Quantum Storage Cells, "
        "the Quantum Quarry, Quantum Wireless Terminal and Quantum Data Cable all use Quantum Processors and Quantum Alloy.")
    # The quantum tier of the storage network uses the quantum chain.
    shaped("quantum_crafting_processor", ["AQA", "PCP", "AQA"], {"A": "#c:ingots/quantum_alloy", "Q": pf("quantum_processor"),
                                                                "P": pf("crafting_processor"), "C": pf("entangled_pair")})
    shaped("quantum_storage_cell", ["EQE", "ACA", "EQE"], {"E": pf("entangled_pair"), "Q": pf("quantum_processor"), "A": "#c:ingots/quantum_alloy",
                                                          "C": pf("storage_cell_256k")})
    shaped("quantum_wireless_terminal", [" E ", "QWQ", " A "], {"E": pf("entangled_pair"), "Q": pf("quantum_processor"), "W": pf("wireless_terminal"),
                                                               "A": "#c:ingots/quantum_alloy"})


def starforge(g):
    """The Starforge multiblock and the paxels only it can make."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, flat_item, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "tag", "LANG"))
    T = {"frame": "starforge_frame", "pylon": "starforge_pylon", "crucible": "starforge_crucible", "molten": "starforge_crucible_top",
         "front": "starforge_front", "front_on": "starforge_front_on", "chrome": "finish_chrome", "dark": "finish_dark",
         "lit": "storage_casing_lit", "casing": "storage_casing", "particle": "starforge_frame"}

    def model(model_name, elements, render="minecraft:cutout"):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render, "textures": tex,
                                                                "elements": elements})

    def casing_states(block, off, on):
        write(asset("blockstates", block + ".json"), {"variants": {"formed=false": {"model": pf("block/" + off)},
                                                                   "formed=true": {"model": pf("block/" + on)}}})

    # Frame: a solid girder block; formed frames light their seams.
    model("starforge_frame", [box([0, 0, 0], [16, 16, 16], "frame", full=SIDES)])
    model("starforge_frame_formed", [box([0, 0, 0], [16, 16, 16], "frame", full=SIDES),
                                     box([-0.01, 7.5, -0.01], [16.01, 8.5, 16.01], "lit", glow=12)])
    casing_states("starforge_frame", "starforge_frame", "starforge_frame_formed")
    # Pylon: a column with coil rings and an emitter tip.
    for suffix, glow in (("", 0), ("_formed", 15)):
        e = [box([4, 0, 4], [12, 2, 12], "dark"), box([5, 2, 5], [11, 14, 11], "pylon", full=("north", "south", "east", "west")),
             box([4.5, 5, 4.5], [11.5, 6, 11.5], "chrome"), box([4.5, 9, 4.5], [11.5, 10, 11.5], "chrome"),
             box([6, 14, 6], [10, 16, 10], "lit" if glow else "casing", glow=glow)]
        model("starforge_pylon" + suffix, e)
    casing_states("starforge_pylon", "starforge_pylon", "starforge_pylon_formed")
    # Crucible: a refractory bowl with a molten pool.
    for suffix, glow in (("", 6), ("_formed", 15)):
        e = [box([1, 0, 1], [15, 2, 15], "dark"), box([1, 2, 1], [15, 12, 2.5], "crucible", full=("north", "south")),
             box([1, 2, 13.5], [15, 12, 15], "crucible", full=("north", "south")),
             box([1, 2, 2.5], [2.5, 12, 13.5], "crucible", full=("east", "west")), box([13.5, 2, 2.5], [15, 12, 13.5], "crucible", full=("east", "west")),
             box([2.5, 2, 2.5], [13.5, 10, 13.5], "molten", {"north": None, "south": None, "east": None, "west": None, "down": None}, glow=glow,
                 full=("up",))]
        model("starforge_crucible" + suffix, e)
    casing_states("starforge_crucible", "starforge_crucible", "starforge_crucible_formed")
    # Controller: frame block with the forge door.
    for suffix, front in (("", "front"), ("_on", "front_on")):
        model("starforge" + suffix, [box([0, 0, 0], [16, 16, 16], "frame", {"north": front}, glow=0, full=SIDES)])
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            for formed in ("false", "true"):
                variants[f"facing={facing},formed={formed},running={running}"] = dict(
                    {"model": pf("block/starforge" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "starforge.json"), {"variants": variants})
    for b, m in (("starforge", "starforge_on"), ("starforge_frame", "starforge_frame_formed"), ("starforge_pylon", "starforge_pylon_formed"),
                 ("starforge_crucible", "starforge_crucible_formed")):
        item_def(b, pf("block/" + m))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
        tag("block", "minecraft", "needs_diamond_tool", [pf(b)])
    # Deliberately expensive: the forge is a late-game project in itself.
    shaped("starforge_frame", ["APA", "PDP", "APA"], {"A": "#c:ingots/adamantium", "P": "#c:plates/steel", "D": "#c:dusts/quantum"}, 2)
    shaped("starforge_pylon", ["CFC", "CQC", "CFC"], {"C": "#c:ingots/cortanium", "F": "#c:ingots/faradaenium", "Q": pf("quantum_processor")})
    shaped("starforge_crucible", ["CNC", "CXC", "CCC"], {"C": "#c:ingots/cortanium", "N": "minecraft:nether_star", "X": "#c:storage_blocks/adamantium"})
    shaped("starforge", ["FQF", "ZXZ", "FAF"], {"F": pf("starforge_frame"), "Q": pf("quantum_processor"), "Z": pf("singularity"),
                                              "X": "minecraft:smithing_table", "A": "#c:ingots/quantum_alloy"})
    # Paxels: no crafting recipe at all, only the Starforge.
    for item in ("diamond_paxel", "netherite_paxel", "adamantium_paxel", "faradaenium_paxel", "cortanium_paxel", "quantum_paxel"):
        flat_item(item, "minecraft:item/handheld")
        for t in ("pickaxes", "axes", "shovels", "hoes"):
            tag("item", "minecraft", t, [pf(item)])
        tag("item", "c", "tools/mining_tool", [pf(item)])
        tag("item", "powerfactor", "paxels", [pf(item)])
        name(f"item.powerfactor.{item}", item.split("_")[0].capitalize() + " Paxel")
    names = {"starforge": "Starforge", "starforge_frame": "Starforge Frame", "starforge_pylon": "Starforge Pylon",
             "starforge_crucible": "Starforge Crucible"}
    for k, v in names.items():
        name(f"block.powerfactor.{k}", v)
    LANG.update({
        "tooltip.powerfactor.paxel": "Pickaxe, axe, shovel and hoe in one · forged only in a Starforge",
        "tooltip.powerfactor.exotic.paxel": "Pickaxe, axe, shovel and hoe in one",
        "gui.powerfactor.starforge.unformed": "Incomplete · sneak-use to build",
        "gui.powerfactor.starforge.load": "Load pickaxe, axe, shovel + catalyst",
        "gui.powerfactor.starforge.forging": "Forging… %s%%",
        "scada.powerfactor.starforge": "Forging %s%%",
    })
    LANG.update({
        "jei.powerfactor.particle_collider": "Particle Collision", "jei.powerfactor.starforge": "Starforge Fusion",
        "jei.powerfactor.energy": "%s FE",
        "jei.powerfactor.starforge.catalyst": "Catalyst: consumed by the fusion",
        "jei.powerfactor.starforge.merge": "Keeps the best enchantments from all three tools",
        "jei.powerfactor.transfer.grid": "Use a Crafting or Pattern Terminal",
        "jei.powerfactor.transfer.not_crafting": "Only crafting-table recipes fit this grid; encode others in a Pattern Terminal",
        "jei.powerfactor.transfer.no_items": "This recipe has no item inputs",
    })
    LANG["handbook.powerfactor.starforge.title"] = "The Starforge & Paxels"
    LANG["handbook.powerfactor.starforge.text"] = (
        "Paxels (pickaxe, axe, shovel and hoe in one) cannot be crafted. They are fused in the Starforge, a 3×3×3 structure:\n\n"
        "Bottom layer: the Starforge controller front-centre, a Block of Adamantium in the middle, Starforge Frames all around.\n"
        "Middle layer: four Starforge Pylons at the corners and the Starforge Crucible in the centre.\n"
        "Top layer: Starforge Frames with a Block of Qubit Crystal in the centre as the focusing lens.\n\n"
        "Holograms show missing parts; sneak-use the controller with the materials in your inventory to auto-build.\n\n"
        "Load a pickaxe, axe and shovel of one material plus its catalyst. The catalyst is a Diamond Block, Netherite Block, or "
        "Block of Adamantium, Faradaenium or Cortanium; Quantum tools take a Singularity. The forge draws up to 400,000 FE/t and "
        "needs 10M FE (diamond) to 200M FE (quantum). The paxel keeps the best enchantments from all three tools, and exotic "
        "paxels keep their material's abilities.")


def turrets(g):
    """Plasma and railgun turrets: a pedestal block (the head is drawn by the renderer) and an item
    model with the head in place, so the inventory icon shows the whole turret."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"plate": "storage_controller_plate", "casing": "storage_casing", "lit": "storage_casing_lit", "chrome": "finish_chrome",
         "dark": "finish_dark", "ring": "assembler_base", "pcb": "quantum_processor_side", "particle": "storage_casing"}

    def model(model_name, elements, render="minecraft:cutout", display=None):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        m = {"parent": "minecraft:block/block", "render_type": render, "textures": tex, "elements": elements}
        if display:
            m["display"] = display
        write(asset("models", "block", model_name + ".json"), m)

    def pedestal(lit):
        glow = 12 if lit else 0
        return [box([1, 0, 1], [15, 3, 15], "dark", {"up": "plate"}),
                box([2, 3, 2], [14, 6, 14], "casing", {"up": "ring"}, full=("up",)),
                box([5, 6, 5], [11, 8, 11], "chrome"),
                # Status lights on each face of the plinth.
                box([6.5, 1, 0.8], [9.5, 2, 1], "lit" if lit else "dark", glow=glow),
                box([6.5, 1, 15], [9.5, 2, 15.2], "lit" if lit else "dark", glow=glow),
                box([0.8, 1, 6.5], [1, 2, 9.5], "lit" if lit else "dark", glow=glow),
                box([15, 1, 6.5], [15.2, 2, 9.5], "lit" if lit else "dark", glow=glow)]

    def head(rail):
        # Matches the renderer's model at rest (pivot 15.2 px up, barrels toward -Z = north in the item view).
        e = [box([3, 9, 3], [13, 11, 13], "dark", {"up": "plate"}),
             box([2.5, 11, 6], [4, 18, 10], "chrome"), box([12, 11, 6], [13.5, 18, 10], "chrome")]
        if rail:
            e += [box([4, 12.2, 5], [12, 18.2, 14], "casing"),
                  box([5.5, 13.7, -9], [6.5, 16.7, 5], "chrome"), box([9.5, 13.7, -9], [10.5, 16.7, 5], "chrome")]
            for z in (-7, -3, 1):
                e.append(box([5, 12.7, z], [11, 17.7, z + 1], "lit", glow=10))
        else:
            e += [box([4, 12.2, 3], [12, 18.2, 12], "casing"), box([5, 18.2, 4], [11, 19.2, 9], "dark"),
                  box([5, 14.2, -4], [7, 16.2, 4], "chrome"), box([9, 14.2, -4], [11, 16.2, 4], "chrome"),
                  box([4.5, 13.7, -5], [7.5, 16.7, -3.5], "lit", glow=10), box([8.5, 13.7, -5], [11.5, 16.7, -3.5], "lit", glow=10)]
        return e

    gui = {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
           "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
           "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
           "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
           "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}
    for b, rail in (("plasma_turret", False), ("railgun_turret", True)):
        model(b, pedestal(False))
        model(b + "_on", pedestal(True))
        model(b + "_item", pedestal(True) + head(rail), display=gui)
        variants = {}
        for facing, rot in FACING.items():
            if facing in ("up", "down"):
                continue
            for running in ("false", "true"):
                variants[f"facing={facing},running={running}"] = {"model": pf(f"block/{b}{'_on' if running == 'true' else ''}")}
        write(asset("blockstates", b + ".json"), {"variants": variants})
        item_def(b, pf(f"block/{b}_item"))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("plasma_turret", ["BEB", "MCM", "PSP"], {"B": pf("plasma_rifle"), "E": "#c:ingots/faradaenium", "M": pf("electric_motor"),
                                                   "C": pf("advanced_circuit"), "P": "#c:plates/steel", "S": "#c:storage_blocks/adamantium"})
    shaped("railgun_turret", ["QRQ", "MTM", "CSC"], {"Q": pf("quantum_processor"), "R": pf("railgun"), "M": pf("electric_motor"),
                                                    "T": pf("plasma_turret"), "C": pf("capacitor_bank"), "S": "#c:storage_blocks/cortanium"})
    name("block.powerfactor.plasma_turret", "Plasma Turret")
    name("block.powerfactor.railgun_turret", "Railgun Turret")
    LANG.update({
        "gui.powerfactor.turret.tracking": "Tracking", "gui.powerfactor.turret.scanning": "Scanning",
        "gui.powerfactor.turret.no_power": "No power", "gui.powerfactor.turret.kills": "Kills: %s",
        "gui.powerfactor.turret.range": "Reach %sm", "gui.powerfactor.turret.charge": "Rail charge",
        "gui.powerfactor.turret.animals": "Animals", "gui.powerfactor.turret.players": "Players",
        "gui.powerfactor.turret.animals.tip": "Also engage farm animals (named animals are always spared)",
        "gui.powerfactor.turret.players.tip": "Also engage players in survival (never the turret's owner)",
        "scada.powerfactor.turret": "%s kills",
    })
    LANG["handbook.powerfactor.turrets.title"] = "Defence Turrets"
    LANG["handbook.powerfactor.turrets.text"] = (
        "Turrets guard an area on FE. They engage hostile mobs by default. Animals and players are opt-in from the console. "
        "A turret never fires on whoever placed it, on villagers, on tamed pets or on armour stands, and it only shoots what it can see.\n\n"
        "Plasma Turret: 20 m range, a bolt every 0.3 s at 1,500 FE each. Bolts burst without splash damage.\n\n"
        "Railgun Turret: 40 m range. It charges for 1.5 s, then fires a slug that pierces every valid target along the line, "
        "for 40,000 FE a shot. Walls stop it.\n\n"
        "The console's radar plots every creature in range, with hostiles in red. A redstone signal or SCADA can switch a turret off.")


def teleporter(g):
    """Quantum Teleporter pad: a low plinth with an emitter ring; the field is drawn by the renderer."""
    from storage_data import box, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"pcb": "quantum_processor_side", "plate": "storage_controller_plate", "ring": "assembler_base", "lit": "storage_casing_lit",
         "casing": "storage_casing", "chrome": "finish_chrome", "particle": "storage_casing"}

    def model(model_name, elements):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                "textures": tex, "elements": elements})
    for suffix, lit in (("", False), ("_on", True)):
        glow = 12 if lit else 0
        e = [box([0, 0, 0], [16, 2, 16], "pcb", {"up": "plate", "down": "plate"}, full=SIDES),
             box([1, 2, 1], [15, 3, 15], "casing", {"up": "ring"}, full=("up",)),
             box([3, 3, 3], [13, 3.5, 13], "lit" if lit else "casing", glow=glow)]
        for x, z in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
            e.append(box([x, 2, z], [x + 2, 4, z + 2], "chrome"))
        model("quantum_teleporter" + suffix, e)
    variants = {f"facing={f},running={r}": {"model": pf(f"block/quantum_teleporter{'_on' if r == 'true' else ''}")}
                for f in ("north", "south", "east", "west") for r in ("false", "true")}
    write(asset("blockstates", "quantum_teleporter.json"), {"variants": variants})
    item_def("quantum_teleporter", pf("block/quantum_teleporter_on"))
    drops_self("quantum_teleporter")
    tag("block", "minecraft", "mineable/pickaxe", [pf("quantum_teleporter")])
    shaped("quantum_teleporter", ["PEP", "QCQ", "AAA"], {"P": "#c:ender_pearls", "E": pf("entangled_pair"), "Q": pf("quantum_processor"),
                                                        "C": pf("capacitor_bank"), "A": "#c:ingots/adamantium"})
    name("block.powerfactor.quantum_teleporter", "Quantum Teleporter")
    LANG.update({
        "gui.powerfactor.teleporter.unlinked": "Unlinked: use an Entangled Qubit Pair on this pad, then on its partner",
        "gui.powerfactor.teleporter.status": "Linked to %s · %s FE per jump · %s FE stored",
        "gui.powerfactor.teleporter.bound": "First half entangled: now use the pair on the partner pad",
        "gui.powerfactor.teleporter.linked": "Entangled with the pad at %s",
        "gui.powerfactor.teleporter.missing": "The pad this pair remembers is gone",
        "gui.powerfactor.teleporter.broken": "The partner pad is gone; link broken",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nQuantum Teleporter: entangle two pads by using an Entangled Qubit Pair on one, then on the other (the pair is used up). "
        "Stand still on a linked, powered pad for a moment-and-a-half to jump to its partner, in any dimension. A jump costs "
        "5,000 FE plus 20 FE per block of distance, or 500,000 FE between dimensions. Step off the arrival pad before jumping back.")


def fusion(g):
    """Fusion Reactor: controller, shielding casing, field magnets and viewport glass."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))

    def cube(model_name, tex, render="minecraft:solid", glow=0):
        textures = {"all": pf("block/" + tex), "particle": pf("block/" + tex)}
        e = [box([0, 0, 0], [16, 16, 16], "all", glow=glow, full=SIDES)]
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": render, "textures": textures,
                                                                "elements": e})

    def states(block, off, on):
        write(asset("blockstates", block + ".json"), {"variants": {"formed=false": {"model": pf("block/" + off)},
                                                                   "formed=true": {"model": pf("block/" + on)}}})
    cube("fusion_casing", "fusion_casing")
    cube("fusion_casing_formed", "fusion_casing", glow=4)
    cube("fusion_magnet", "fusion_magnet")
    cube("fusion_magnet_formed", "fusion_magnet", glow=10)
    cube("fusion_glass", "fusion_glass", render="minecraft:cutout")
    for b in ("fusion_casing", "fusion_magnet"):
        states(b, b, b + "_formed")
    states("fusion_glass", "fusion_glass", "fusion_glass")
    for suffix, front in (("", "fusion_front"), ("_on", "fusion_front_on")):
        write(asset("models", "block", "fusion_reactor" + suffix + ".json"), {
            "parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"side": pf("block/fusion_casing"), "front": pf("block/" + front), "particle": pf("block/fusion_casing")},
            "elements": [box([0, 0, 0], [16, 16, 16], "side", {"north": "front"}, full=SIDES)]})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            for formed in ("false", "true"):
                variants[f"facing={facing},formed={formed},running={running}"] = dict(
                    {"model": pf("block/fusion_reactor" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "fusion_reactor.json"), {"variants": variants})
    for b, m in (("fusion_reactor", "fusion_reactor_on"), ("fusion_casing", "fusion_casing_formed"), ("fusion_magnet", "fusion_magnet_formed"),
                 ("fusion_glass", "fusion_glass")):
        item_def(b, pf("block/" + m))
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])
    shaped("fusion_casing", ["PAP", "ALA", "PAP"], {"P": "#c:plates/steel", "A": "#c:ingots/adamantium", "L": "minecraft:obsidian"}, 8)
    shaped("fusion_magnet", ["FCF", "CIC", "FCF"], {"F": "#c:ingots/faradaenium", "C": "minecraft:copper_block", "I": "#c:storage_blocks/iron"}, 2)
    shaped("fusion_glass", ["GAG", "AGA", "GAG"], {"G": "#c:glass_blocks", "A": "#c:ingots/adamantium"}, 4)
    shaped("fusion_reactor", ["MQM", "ZXZ", "CCC"], {"M": pf("fusion_magnet"), "Q": pf("quantum_processor"), "Z": pf("exotic_matter"),
                                                    "X": pf("capacitor_bank"), "C": pf("fusion_casing")})
    for k, v in {"fusion_reactor": "Fusion Reactor", "fusion_casing": "Fusion Shielding", "fusion_magnet": "Toroidal Field Magnet",
                 "fusion_glass": "Fusion Viewport"}.items():
        name(f"block.powerfactor.{k}", v)
    LANG.update({
        "gui.powerfactor.fusion.cold": "Cold", "gui.powerfactor.fusion.igniting": "Igniting", "gui.powerfactor.fusion.burning": "Burning",
        "gui.powerfactor.fusion.unformed": "Structure incomplete", "gui.powerfactor.fusion.temp": "%s MK",
        "gui.powerfactor.fusion.fuel": "H₂ %s", "gui.powerfactor.fusion.output": "+%s/t", "gui.powerfactor.fusion.fuel_tip": "Hydrogen: %s / %s mB · needs 1,000 mB and 50M FE to ignite", "gui.powerfactor.fusion.ignite_hint": "Needs 1,000 mB H₂ and 50M FE to ignite",
        "scada.powerfactor.fusion": "Plasma %s MK · %s mB hydrogen",
    })
    LANG["handbook.powerfactor.fusion.title"] = "Fusion Reactor"
    LANG["handbook.powerfactor.fusion.text"] = (
        "A 5×5×3 tokamak. Sneak-use the controller with materials in your inventory to build it automatically.\n\n"
        "Bottom layer: 25 Fusion Shielding. Middle: a ring of Toroidal Field Magnets with the controller in the front middle "
        "and a hollow 3×3 chamber inside. Top: Shielding around a 3×3 Fusion Viewport.\n\n"
        "Pipe hydrogen (from Electrolyzers) into any casing and feed it FE. With 1,000 mB of hydrogen stored, the reactor pours "
        "a 50M FE ignition pulse into the plasma. At ignition it burns up to 8 mB of hydrogen a tick (two Electrolyzers keep it fed) and delivers up to "
        "250,000 FE/t through the controller and every casing. If the hydrogen runs dry, the plasma cools and quenches, and "
        "you have to ignite it again.")


def meteors(g):
    """Meteorite block, its crushing recipe, and the meteor messages."""
    write, asset, pf, name, item_def, drops_self, tag, LANG, crushing, out = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "drops_self", "tag", "LANG", "crushing", "out"))
    write(asset("models", "block", "meteorite.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf("block/meteorite")}})
    write(asset("blockstates", "meteorite.json"), {"variants": {"": {"model": pf("block/meteorite")}}})
    item_def("meteorite")
    drops_self("meteorite")
    tag("block", "minecraft", "mineable/pickaxe", [pf("meteorite")])
    tag("block", "minecraft", "needs_iron_tool", [pf("meteorite")])
    crushing("meteorite", pf("meteorite"), out("minecraft:raw_iron", 2),
             bonus=[out("raw_adamantium", 1, 0.15), out("raw_faradaenium", 1, 0.1), out("qubit_crystal", 1, 0.1), out("cortanium_shard", 1, 0.04)])
    name("block.powerfactor.meteorite", "Meteorite")
    name("entity.powerfactor.meteor", "Meteor")
    LANG["message.powerfactor.meteor"] = "A meteor streaks across the sky to the %s!"
    for k, v in {"north": "north", "north_east": "north-east", "east": "east", "south_east": "south-east", "south": "south",
                 "south_west": "south-west", "west": "west", "north_west": "north-west"}.items():
        LANG[f"direction.powerfactor.compass.{k}"] = v
    LANG["handbook.powerfactor.exotic.text"] = LANG["handbook.powerfactor.exotic.text"] + (
        "\n\nMeteors: on some nights a meteor streaks down within a few dozen blocks of you. Its crater holds a core of Meteorite "
        "studded with exotic ore. Crush Meteorite for raw iron and a chance of adamantium, faradaenium, qubit crystal or cortanium. "
        "Servers can turn meteors or their craters off in the config.")


def quantum_labs(g):
    """Abandoned quantum labs: the feature's placement, its config gate, and the researcher's chest."""
    write, data, pf, NS, LANG = (g[k] for k in ("write", "data", "pf", "NS", "LANG"))
    write(data(NS, "worldgen", "configured_feature", "quantum_lab.json"), {"type": pf("quantum_lab"), "config": {}})
    write(data(NS, "worldgen", "placed_feature", "quantum_lab.json"), {"feature": pf("quantum_lab"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 160},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -52},
                                                      "max_inclusive": {"absolute": -12}}},
        {"type": "minecraft:biome"}]})
    write(data(NS, "neoforge", "biome_modifier", "add_quantum_labs.json"), {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": pf("quantum_lab"),
        "step": "underground_structures", "neoforge:conditions": [{"type": pf("worldgen_enabled"), "feature": "quantum_labs"}]})
    journal = [
        "Day 1. The collider ran clean at 4M FE. Two qubits per diamond, exactly as predicted. The team is giddy.",
        "Day 9. We pushed it to 80M FE to try for a stable singularity. The ring sang. Something sang back.",
        "Day 10. There is a hum in the walls that the instruments cannot place. Crystals are growing in the panels overnight.",
        "Day 12. Small grey things came through the north corner where the field pinched. Endermites, or something wearing their shape.",
        "Day 13. We cannot close the rift; it feeds on the collider's residual charge. Sealing the lab. If you find this: do not power the ring.",
    ]
    book = {"title": {"raw": "Lab Journal"}, "author": "Dr. E. Voss", "generation": 2, "pages": [{"raw": p} for p in journal]}
    write(data(NS, "loot_table", "chests", "quantum_lab.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:written_book",
                                  "functions": [{"function": "minecraft:set_components",
                                                 "components": {"minecraft:written_book_content": book}}]}]},
        {"rolls": {"min": 3, "max": 6}, "entries": [
            {"type": "minecraft:item", "name": pf("qubit_crystal"), "weight": 20,
             "functions": [{"function": "minecraft:set_count", "count": {"min": 2, "max": 8}}]},
            {"type": "minecraft:item", "name": pf("quantum_dust"), "weight": 18,
             "functions": [{"function": "minecraft:set_count", "count": {"min": 2, "max": 6}}]},
            {"type": "minecraft:item", "name": pf("entangled_pair"), "weight": 10,
             "functions": [{"function": "minecraft:set_count", "count": {"min": 1, "max": 2}}]},
            {"type": "minecraft:item", "name": pf("raw_adamantium"), "weight": 10,
             "functions": [{"function": "minecraft:set_count", "count": {"min": 1, "max": 3}}]},
            {"type": "minecraft:item", "name": pf("data_core"), "weight": 10,
             "functions": [{"function": "minecraft:set_count", "count": {"min": 1, "max": 4}}]},
            {"type": "minecraft:item", "name": pf("quantum_processor"), "weight": 4},
            {"type": "minecraft:item", "name": pf("exotic_matter"), "weight": 2},
            {"type": "minecraft:item", "name": pf("fluid_cell_4k"), "weight": 4},
            {"type": "minecraft:item", "name": pf("storage_cell_16k"), "weight": 4}]}]})
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nAbandoned labs: rare sealed rooms deep in the deepslate hold a dead collider, qubit crystal growing through the walls, "
        "a rift that still leaks endermites, and the last researcher's chest. Servers can turn them off in the worldgen config.")


def robot_arm(g):
    """Robot arm base (the arm itself is drawn by the renderer) and an item model with the arm folded up."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"casing": "storage_casing", "lit": "storage_casing_lit", "dark": "finish_dark", "chrome": "finish_chrome", "hazard": "hazard_block",
         "particle": "storage_casing"}

    def model(model_name, elements, display=None):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": elements}
        if display:
            m["display"] = display
        write(asset("models", "block", model_name + ".json"), m)

    def base(lit):
        return [box([2, 0, 2], [14, 3, 14], "dark", {"up": "hazard"}), box([3, 3, 3], [13, 6, 13], "casing"),
                box([6.5, 4, 2.8], [9.5, 5, 3], "lit" if lit else "dark", glow=12 if lit else 0)]
    model("robot_arm", base(False))
    model("robot_arm_on", base(True))
    arm = [box([5, 6, 5], [11, 10, 11], "dark"), box([6.5, 10, 6.5], [9.5, 20, 9.5], "hazard"),
           box([6, 19, 6], [10, 21, 10], "dark"), box([6.75, 20, 4], [9.25, 22.5, 12], "hazard"), box([7, 20, 3], [9, 22, 4], "chrome")]
    model("robot_arm_item", base(True) + arm, display={"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.55] * 3},
                                                       "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3] * 3},
                                                       "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.5] * 3}})
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/robot_arm" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "robot_arm.json"), {"variants": variants})
    item_def("robot_arm", pf("block/robot_arm_item"))
    drops_self("robot_arm")
    tag("block", "minecraft", "mineable/pickaxe", [pf("robot_arm")])
    shaped("robot_arm", [" PG", " M ", "SCS"], {"P": "minecraft:piston", "G": "#c:plates/steel", "M": pf("electric_motor"),
                                               "S": "#c:plates/steel", "C": pf("microprocessor")})
    name("block.powerfactor.robot_arm", "Robot Arm")
    LANG.update({
        "gui.powerfactor.robot_arm.any": "anything", "gui.powerfactor.robot_arm.filter": "Robot arm now moves only %s",
        "gui.powerfactor.robot_arm.cleared": "Robot arm filter cleared",
        "gui.powerfactor.robot_arm.status": "Moving %s · %s moved · %s FE",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nRobot Arm: place it facing the inventory to deliver to; it reaches into the inventory behind it, swings round and "
        "sets stacks of up to 16 down in front, using 250 FE a move. Use the arm with an item to move only that item; sneak-use "
        "with an empty hand to clear the filter.")


def orbital_collector(g):
    """Orbital Collector: pedestal block (the dish is drawn by the renderer), Cosmic Dust and its recipes."""
    from storage_data import box, FACING
    write, asset, pf, name, item_def, flat_item, shaped, shapeless, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "flat_item", "shaped", "shapeless", "drops_self", "tag", "LANG"))
    T = {"casing": "storage_casing", "lit": "storage_casing_lit", "dark": "finish_dark", "chrome": "finish_chrome", "ring": "assembler_base",
         "pcb": "quantum_processor_side", "particle": "storage_casing"}

    def model(model_name, elements, display=None):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": elements}
        if display:
            m["display"] = display
        write(asset("models", "block", model_name + ".json"), m)

    def base(lit):
        return [box([0, 0, 0], [16, 4, 16], "pcb", {"up": "ring", "down": "dark"}, full=("north", "south", "east", "west", "up")),
                box([3, 4, 3], [13, 10, 13], "casing"), box([5, 10, 5], [11, 12, 11], "chrome"),
                box([6.5, 6, 2.8], [9.5, 8, 3], "lit" if lit else "dark", glow=12 if lit else 0)]
    model("orbital_collector", base(False))
    model("orbital_collector_on", base(True))
    dish = [box([4, 12, 4], [12, 13, 12], "chrome"), box([2, 13, 2], [14, 14, 14], "chrome"), box([0, 14, 0], [16, 15, 16], "chrome"),
            box([7.5, 15, 7.5], [8.5, 22, 8.5], "dark"), box([6.5, 22, 6.5], [9.5, 24, 9.5], "lit", glow=12)]
    model("orbital_collector_item", base(True) + dish, display={"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5] * 3},
                                                                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3] * 3},
                                                                "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.45] * 3}})
    variants = {f"facing={f},running={r}": dict({"model": pf("block/orbital_collector" + ("_on" if r == "true" else ""))}, **FACING[f])
                for f in ("north", "south", "east", "west") for r in ("false", "true")}
    write(asset("blockstates", "orbital_collector.json"), {"variants": variants})
    item_def("orbital_collector", pf("block/orbital_collector_item"))
    drops_self("orbital_collector")
    tag("block", "minecraft", "mineable/pickaxe", [pf("orbital_collector")])
    shaped("orbital_collector", ["GTG", "QCQ", "AFA"], {"G": "#c:glass_panes", "T": pf("quantum_teleporter"), "Q": pf("quantum_processor"),
                                                       "C": pf("capacitor_bank"), "A": "#c:ingots/adamantium", "F": "#c:ingots/faradaenium"})
    flat_item("cosmic_dust")
    shapeless("exotic_matter", [pf("cosmic_dust")] * 9, file="exotic_matter_from_cosmic_dust")
    name("block.powerfactor.orbital_collector", "Orbital Collector")
    name("item.powerfactor.cosmic_dust", "Cosmic Dust")
    LANG.update({
        "gui.powerfactor.orbital.state.0": "Collecting", "gui.powerfactor.orbital.state.1": "Waiting for night",
        "gui.powerfactor.orbital.state.2": "Sky blocked", "gui.powerfactor.orbital.state.3": "Needs power",
        "gui.powerfactor.orbital.state.4": "Output full", "gui.powerfactor.orbital.state.5": "Switched off",
        "gui.powerfactor.orbital.pass": "Collection pass %s%%",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nOrbital Collector: give it open sky and power. At night it tracks the heavens and pulls drifting matter down a "
        "collection beam. Each minute-long pass costs 3M FE and yields Cosmic Dust (nine fuse into Exotic Matter), "
        "often qubit crystal or raw faradaenium, sometimes Meteorite, and very rarely a Nether Star.")


def hover_bike(g):
    """Hover-bike item, recipe and text."""
    pf, name, flat_item, shaped, LANG = (g[k] for k in ("pf", "name", "flat_item", "shaped", "LANG"))
    flat_item("hover_bike")
    shaped("hover_bike", ["  G", "FCF", "RBR"], {"G": "#c:glass_panes", "F": "#c:ingots/faradaenium", "C": pf("electric_rover"),
                                               "R": pf("thruster_module"), "B": pf("capacitor_bank")})
    name("item.powerfactor.hover_bike", "Hover-Bike")
    name("entity.powerfactor.hover_bike", "Hover-Bike")
    LANG.update({
        "tooltip.powerfactor.hover_bike.controls": "Ride: W/S throttle, A/D steer, Sprint boost, Jump lifts over obstacles · skims water",
        "gui.powerfactor.hover_bike.title": "HOVER-BIKE", "gui.powerfactor.hover_bike.help": "Sprint: boost · Jump: lift",
        "gui.powerfactor.hover_bike.boost": "BOOST", "gui.powerfactor.hover_bike.no_field": "Field down: no charge",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nHover-Bike: a repulsor bike that rides a cushion a block above land or water. W/S throttle, A/D steer (it banks into "
        "turns), Sprint boosts, and Jump lifts it over obstacles. It runs on a 1M FE battery; park it over a Charging Pad to top up, "
        "and sneak-use a wrench to pack it into an item, charge and hull intact.")


def gravity_lift(g):
    """Gravity Lift pad: the column itself is drawn by the renderer."""
    from storage_data import box, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"pcb": "quantum_processor_side", "plate": "storage_controller_plate", "ring": "assembler_base", "lit": "storage_casing_lit",
         "casing": "storage_casing", "chrome": "finish_chrome", "hazard": "hazard_block", "particle": "storage_casing"}

    def model(model_name, elements):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                "textures": tex, "elements": elements})
    for suffix, lit in (("", False), ("_on", True)):
        e = [box([0, 0, 0], [16, 2, 16], "hazard", {"up": "plate", "down": "plate"}, full=SIDES),
             box([2, 2, 2], [14, 3, 14], "casing", {"up": "ring"}, full=("up",)),
             box([4, 3, 4], [12, 4, 12], "lit" if lit else "casing", glow=14 if lit else 0)]
        model("gravity_lift" + suffix, e)
    variants = {f"facing={f},running={r}": {"model": pf(f"block/gravity_lift{'_on' if r == 'true' else ''}")}
                for f in ("north", "south", "east", "west") for r in ("false", "true")}
    write(asset("blockstates", "gravity_lift.json"), {"variants": variants})
    item_def("gravity_lift", pf("block/gravity_lift_on"))
    drops_self("gravity_lift")
    tag("block", "minecraft", "mineable/pickaxe", [pf("gravity_lift")])
    shaped("gravity_lift", ["FEF", "PMP", "SSS"], {"F": "#c:ingots/faradaenium", "E": "minecraft:ender_pearl", "P": "#c:plates/steel",
                                                  "M": pf("electric_motor"), "S": "#c:plates/steel"})
    name("block.powerfactor.gravity_lift", "Gravity Lift")
    LANG["gui.powerfactor.gravity_lift.status"] = "Column %s blocks high · %s FE"
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nGravity Lift: a pad that projects a field column straight up to the first solid block (up to 24). Step in to float "
        "up; sneak to sink gently. Nobody takes fall damage inside it. Items ride it too. 20 FE per entity carried each tick.")


def logic_controller(g):
    """Logic Controller: a slab-height programmable redstone controller."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"pcb": "quantum_processor_side", "dark": "finish_dark", "lit": "storage_casing_lit", "casing": "storage_casing",
         "chrome": "finish_chrome", "particle": "storage_casing"}

    def model(model_name, elements):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                "textures": tex, "elements": elements})
    for suffix, lit in (("", False), ("_on", True)):
        e = [box([0, 0, 0], [16, 4, 16], "casing", {"up": "pcb", "down": "dark"}, full=("up",)),
             box([1, 4, 1], [15, 5, 15], "dark"),
             box([4, 5, 4], [12, 6, 12], "chrome"),
             # Output port on the front (north in the model), input ports on the other three sides.
             box([6, 1, -0.2], [10, 3, 0], "lit" if lit else "dark", glow=15 if lit else 0),
             box([6, 1, 16], [10, 3, 16.2], "dark"), box([-0.2, 1, 6], [0, 3, 10], "dark"), box([16, 1, 6], [16.2, 3, 10], "dark")]
        model("logic_controller" + suffix, e)
    variants = {}
    for facing, rot in FACING.items():
        if facing in ("up", "down"):
            continue
        for running in ("false", "true"):
            variants[f"facing={facing},running={running}"] = dict({"model": pf("block/logic_controller" + ("_on" if running == "true" else ""))}, **rot)
    write(asset("blockstates", "logic_controller.json"), {"variants": variants})
    item_def("logic_controller", pf("block/logic_controller_on"))
    drops_self("logic_controller")
    tag("block", "minecraft", "mineable/pickaxe", [pf("logic_controller")])
    shaped("logic_controller", ["RCR", "PMP"], {"R": "minecraft:repeater", "C": "minecraft:comparator", "P": "#c:plates/steel", "M": pf("microprocessor")})
    name("block.powerfactor.logic_controller", "Logic Controller")
    programs = {"and": ("AND", "On while left AND right inputs are on"), "or": ("OR", "On while any input is on"),
                "xor": ("XOR", "On while exactly one of left and right is on"), "nand": ("NAND", "Off only while left and right are both on"),
                "latch": ("LATCH", "Left sets it on, right resets it off"), "timer": ("TIMER", "While back is on, pulses every N ticks"),
                "delay": ("DELAY", "Repeats the back input N ticks later"), "counter": ("COUNT", "Pulses after every N pulses on the back input")}
    for k, (short, help_) in programs.items():
        LANG[f"gui.powerfactor.logic.program.{k}"] = short
        LANG[f"gui.powerfactor.logic.help.{k}"] = help_
    LANG["gui.powerfactor.logic.n_tip"] = "N: the timer period, delay length or count (1-200)"
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nLogic Controller: a small programmable redstone controller. Inputs on its left, back and right; output from its "
        "front. Programs: AND, OR, XOR, NAND, set/reset LATCH, TIMER (pulses every N ticks while back is on), DELAY (back input "
        "N ticks later) and COUNT (a pulse after every N back-input pulses). Its console shows live lamps and a logic trace.")


def mech(g):
    """Mech suit item, recipe and text."""
    pf, name, flat_item, shaped, LANG = (g[k] for k in ("pf", "name", "flat_item", "shaped", "LANG"))
    flat_item("mech_suit")
    shaped("mech_suit", ["SPS", "AJA", "L L"], {"S": "#c:storage_blocks/steel", "P": pf("power_chestplate"), "A": "#c:ingots/adamantium",
                                               "J": pf("electric_jetpack"), "L": pf("electric_motor")})
    name("item.powerfactor.mech_suit", "Mech Suit")
    name("entity.powerfactor.mech", "Mech Suit")
    LANG.update({
        "tooltip.powerfactor.mech.controls": "Pilot: W/S walk, A/D turn, Sprint runs, Jump fires jump-jets · hard landings stomp",
        "gui.powerfactor.mech.title": "MECH", "gui.powerfactor.mech.help": "Sprint: run · Jump: jets",
        "gui.powerfactor.mech.jets": "JUMP-JETS",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nMech Suit: a three-block walker you climb into. W/S walks, A/D turns, Sprint runs, Jump fires the jump-jets; it "
        "steps up 1.5 blocks, and a landing from more than three blocks slams a shockwave into nearby creatures. 2M FE battery "
        "(Charging Pads reach it); repair with steel; sneak-use a wrench to pack it up.")


def space_elevator(g):
    """Space Elevator anchor (the tether and climber are drawn by the renderer) and its tether segments."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, flat_item, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "flat_item", "shaped", "drops_self", "tag", "LANG"))
    T = {"pcb": "quantum_processor_side", "plate": "storage_controller_plate", "ring": "assembler_base", "lit": "storage_casing_lit",
         "casing": "fusion_casing", "magnet": "fusion_magnet", "chrome": "finish_chrome", "dark": "finish_dark", "particle": "fusion_casing"}

    def model(model_name, elements):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                "textures": tex, "elements": elements})
    for suffix, lit in (("", False), ("_on", True)):
        glow = 14 if lit else 0
        e = [box([0, 0, 0], [16, 8, 16], "casing", {"up": "plate"}, full=SIDES),
             box([3, 8, 3], [13, 12, 13], "magnet", full=("north", "south", "east", "west")),
             box([5, 12, 5], [11, 14, 11], "chrome", {"up": "ring"}, full=("up",)),
             box([7, 14, 7], [9, 16, 9], "lit" if lit else "dark", glow=glow)]
        for x, z in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
            e.append(box([x, 8, z], [x + 2, 15, z + 2], "chrome"))
            e.append(box([x + 0.4, 15, z + 0.4], [x + 1.6, 16, z + 1.6], "lit" if lit else "dark", glow=glow))
        model("space_elevator" + suffix, e)
    variants = {f"facing={f},running={r}": dict({"model": pf("block/space_elevator" + ("_on" if r == "true" else ""))}, **FACING[f])
                for f in ("north", "south", "east", "west") for r in ("false", "true")}
    write(asset("blockstates", "space_elevator.json"), {"variants": variants})
    item_def("space_elevator", pf("block/space_elevator_on"))
    drops_self("space_elevator")
    tag("block", "minecraft", "mineable/pickaxe", [pf("space_elevator")])
    flat_item("nanotube_tether")
    shaped("nanotube_tether", ["GOG", "GQG", "GOG"], {"G": pf("graphite"), "O": pf("optical_fiber"), "Q": "#c:ingots/quantum_alloy"}, 2)
    shaped("space_elevator", ["MZM", "QFQ", "AAA"], {"M": pf("fusion_magnet"), "Z": pf("singularity"), "Q": pf("quantum_processor"),
                                                    "F": pf("fusion_reactor"), "A": "#c:storage_blocks/adamantium"})
    name("block.powerfactor.space_elevator", "Space Elevator Anchor")
    name("item.powerfactor.nanotube_tether", "Nanotube Tether Segment")
    LANG.update({
        "gui.powerfactor.elevator.segments": "%s/%s",
        "gui.powerfactor.elevator.no_sky": "Sky blocked", "gui.powerfactor.elevator.online": "Online",
        "gui.powerfactor.elevator.building": "Raising", "gui.powerfactor.elevator.spool": "Spooling",
        "gui.powerfactor.elevator.tether": "Tether", "gui.powerfactor.elevator.ride": "Riding the climber up the tether…",
    })
    LANG["handbook.powerfactor.quantum.text"] = LANG["handbook.powerfactor.quantum.text"] + (
        "\n\nSpace Elevator: the capstone. Place the anchor under open sky, power it and feed it Nanotube Tether Segments; it raises "
        "one every three seconds (50,000 FE each). With all 64 up, the orbital station beams down 120,000 FE/t of space solar power, "
        "day and night, into every adjacent receiver. Sneak-use the finished anchor to ride the climber up the tether; you drift "
        "home on slow fall.")


def shield_generator(g):
    """Shield Generator: an emitter pylon on a heavy base; the dome is drawn by the renderer."""
    from storage_data import box, FACING, SIDES
    write, asset, pf, name, item_def, shaped, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "pf", "name", "item_def", "shaped", "drops_self", "tag", "LANG"))
    T = {"casing": "storage_casing", "lit": "storage_casing_lit", "dark": "finish_dark", "chrome": "finish_chrome", "magnet": "fusion_magnet",
         "glass": "fusion_glass", "plate": "storage_controller_plate", "particle": "storage_casing"}

    def model(model_name, elements):
        used = {f["texture"][1:] for e in elements for f in e["faces"].values()}
        tex = {k: pf("block/" + v) for k, v in T.items() if k in used or k == "particle"}
        write(asset("models", "block", model_name + ".json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                                "textures": tex, "elements": elements})
    for suffix, lit in (("", False), ("_on", True)):
        glow = 15 if lit else 0
        e = [box([0, 0, 0], [16, 5, 16], "casing", {"up": "plate"}, full=SIDES),
             box([4, 5, 4], [12, 10, 12], "magnet", full=("north", "south", "east", "west")),
             box([5.5, 10, 5.5], [10.5, 14, 10.5], "chrome"),
             box([6.5, 14, 6.5], [9.5, 16, 9.5], "lit" if lit else "dark", glow=glow)]
        for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
            e.append(box([x, 5, z], [x + 2, 8, z + 2], "lit" if lit else "dark", glow=glow))
        model("shield_generator" + suffix, e)
    variants = {f"facing={f},running={r}": dict({"model": pf("block/shield_generator" + ("_on" if r == "true" else ""))}, **FACING[f])
                for f in ("north", "south", "east", "west") for r in ("false", "true")}
    write(asset("blockstates", "shield_generator.json"), {"variants": variants})
    item_def("shield_generator", pf("block/shield_generator_on"))
    drops_self("shield_generator")
    tag("block", "minecraft", "mineable/pickaxe", [pf("shield_generator")])
    shaped("shield_generator", ["GQG", "MCM", "AAA"], {"G": pf("fusion_glass"), "Q": pf("quantum_processor"), "M": pf("fusion_magnet"),
                                                      "C": pf("capacitor_bank"), "A": "#c:ingots/adamantium"})
    name("block.powerfactor.shield_generator", "Shield Generator")
    LANG.update({
        "gui.powerfactor.shield.up": "Up", "gui.powerfactor.shield.down": "Down",
        "gui.powerfactor.shield.radius": "R %s m", "gui.powerfactor.shield.hits": "Hits %s",
        "gui.powerfactor.shield.radius_tip": "Dome radius (4-16 m); upkeep grows with its area",
        "gui.powerfactor.shield.scada": "Dome %s m · %s repulses",
    })
    LANG["handbook.powerfactor.turrets.text"] = LANG["handbook.powerfactor.turrets.text"] + (
        "\n\nShield Generator: projects a dome of force 4 to 16 m in radius. Hostile mobs inside are shoved back out, and "
        "projectiles fired by hostiles are burned away as they cross the surface. Upkeep is 3 FE/t per square metre of radius, "
        "plus 2,000 FE per repulse.")
