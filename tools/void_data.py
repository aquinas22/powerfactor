"""Data for bedrock fragments, Essence of Bedrock, the Pocket Voids, fulgurite, the elevator and the Chunk Loader card.
Called from gen_data.py as generate_data(globals())."""

DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
        "green", "red", "black"]


def generate_data(g):
    write, asset, data, pf, name, flat_item, shaped, shapeless, tag, LANG, NS = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "flat_item", "shaped", "shapeless", "tag", "LANG", "NS"))

    for item in ("bedrock_fragment", "essence_of_bedrock", "fulgurite", "chunk_loader_upgrade", "void_core"):
        flat_item(item)

    # The Pocket Voids: a frame cube (this model) round a box of End starfield with a black hole in it (the special
    # renderer). The bars span 2..14 between corner caps so no two faces meet in the same plane.
    for key, deep in (("pocket_void", False), ("deep_pocket_void", True)):
        frame, corner = pf(f"item/{key}_frame"), pf(f"item/{key}_corner")
        elements = []
        bar = {d: {"texture": "#frame"} for d in ("north", "south", "east", "west", "up", "down")}
        for a in (0, 14):
            for b in (0, 14):
                elements.append({"from": [2, a, b], "to": [14, a + 2, b + 2], "faces": bar})
                elements.append({"from": [a, 2, b], "to": [a + 2, 14, b + 2], "faces": bar})
                elements.append({"from": [a, b, 2], "to": [a + 2, b + 2, 14], "faces": bar})
        cap = {d: {"uv": [0, 0, 16, 16], "texture": "#corner"} for d in ("north", "south", "east", "west", "up", "down")}
        for x in (-0.25, 13.75):
            for y in (-0.25, 13.75):
                for z in (-0.25, 13.75):
                    elements.append({"from": [x, y, z], "to": [x + 2.5, y + 2.5, z + 2.5], "faces": cap})
        # Turned a little off the block angle, so the near edge doesn't stand in front of the hole.
        display = {"gui": {"rotation": [24, 207, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
                   "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]},
                   "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1.5, 0], "scale": [0.3, 0.3, 0.3]}}
        write(asset("models", "item", f"{key}.json"), {"parent": "minecraft:block/block", "textures": {
            "frame": frame, "corner": corner, "particle": corner}, "display": display, "elements": elements})
        write(asset("items", f"{key}.json"), {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": pf(f"item/{key}")},
            {"type": "minecraft:special", "base": pf(f"item/{key}"), "model": {"type": pf("pocket_void"), "deep": deep}}]}})

    # Fused sand: lightning's glass. Silk touch keeps the block; otherwise it breaks into fulgurite.
    write(asset("models", "block", "fused_sand.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": pf("block/fused_sand")}})
    write(asset("blockstates", "fused_sand.json"), {"variants": {"": {"model": pf("block/fused_sand")}}})
    g["item_def"]("fused_sand")
    silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write(data(NS, "loot_table", "blocks", "fused_sand.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/fused_sand"),
        "pools": [{"rolls": 1.0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": pf("fused_sand"), "conditions": [silk]},
            {"type": "minecraft:item", "name": pf("fulgurite"), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                 "parameters": {"bonusMultiplier": 1}}]}]}]}]})
    tag("block", "minecraft", "mineable/shovel", [pf("fused_sand")])

    # Elevator: one block, sixteen dyes; the dye is kept when it's broken.
    for dye in DYES:
        write(asset("models", "block", f"elevator/{dye}.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": pf(f"block/elevator/{dye}_top"), "bottom": pf(f"block/elevator/{dye}_top"), "side": pf(f"block/elevator/{dye}_side")}})
    write(asset("blockstates", "elevator.json"), {"variants": {f"color={d}": {"model": pf(f"block/elevator/{d}")} for d in DYES}})
    write(asset("items", "elevator.json"), {"model": {"type": "minecraft:select", "property": "minecraft:block_state", "block_state_property": "color",
        "cases": [{"when": d, "model": {"type": "minecraft:model", "model": pf(f"block/elevator/{d}")}} for d in DYES],
        "fallback": {"type": "minecraft:model", "model": pf("block/elevator/white")}}})
    write(data(NS, "loot_table", "blocks", "elevator.json"), {"type": "minecraft:block", "random_sequence": pf("blocks/elevator"),
        "pools": [{"rolls": 1.0, "conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item",
            "name": pf("elevator"), "functions": [{"function": "minecraft:copy_state", "block": pf("elevator"), "properties": ["color"]}]}]}]})
    tag("block", "minecraft", "mineable/axe", [pf("elevator")])

    # Recipes: bedrock chipped off by TNT goes into everything that has to hold space or a place still.
    shapeless("essence_of_bedrock", [pf("bedrock_fragment")] * 4 + ["minecraft:glowstone_dust"], count=2)
    shaped("pocket_void", [" S ", "LEL", "LCL"], {"S": "minecraft:string", "L": "minecraft:leather", "E": pf("essence_of_bedrock"),
                                                  "C": "#c:chests/wooden"})
    shaped("void_core", ["OEO", "EPE", "OEO"], {"O": "minecraft:crying_obsidian", "E": pf("essence_of_bedrock"), "P": "minecraft:ender_eye"})
    # Upgrading keeps everything inside.
    write(data(NS, "recipe", "deep_pocket_void.json"), {"type": "minecraft:crafting_transmute", "category": "equipment",
        "input": pf("pocket_void"), "material": pf("void_core"), "result": {"id": pf("deep_pocket_void")}})
    shaped("elevator", ["WWW", "WFW", "WWW"], {"W": "#minecraft:wool", "F": pf("bedrock_fragment")}, count=2)
    shaped("chunk_loader_upgrade", [" F ", "PKP", " F "], {"F": pf("bedrock_fragment"), "P": "minecraft:ender_pearl", "K": pf("control_circuit")})
    # The Dimensional Pocket is held open by bedrock now.
    shaped("dimensional_pocket", ["BEB", "EPE", "BEB"], {"B": pf("essence_of_bedrock"), "E": "minecraft:ender_eye", "P": pf("exotic_matter")})

    for key, english in {"bedrock_fragment": "Bedrock Fragment", "essence_of_bedrock": "Essence of Bedrock", "fulgurite": "Fulgurite",
                         "pocket_void": "Pocket Void", "deep_pocket_void": "Deep Pocket Void", "void_core": "Void Core",
                         "chunk_loader_upgrade": "Chunk Loader Card"}.items():
        name(f"item.powerfactor.{key}", english)
    name("block.powerfactor.fused_sand", "Fused Sand")
    name("block.powerfactor.elevator", "Elevator")
    LANG.update({
        "tooltip.powerfactor.upgrade.chunk_loader": "Keeps this machine's chunk loaded",
        "gui.powerfactor.upgrade_summary.chunk_loader": "Chunk Kept Loaded",
        "tooltip.powerfactor.pocket_void.capacity": "Nine slots of %s",
        "tooltip.powerfactor.pocket_void.mode": "Pick Up: %s",
        "gui.powerfactor.pocket_void.mode.off": "Off",
        "gui.powerfactor.pocket_void.mode.matching": "Matching",
        "gui.powerfactor.pocket_void.mode.all": "Everything",
        "gui.powerfactor.pocket_void.mode.void": "Void Excess",
        "gui.powerfactor.pocket_void.mode.off.tip": "Picks nothing up",
        "gui.powerfactor.pocket_void.mode.matching.tip": "Pulls in what it already holds as you pick it up",
        "gui.powerfactor.pocket_void.mode.all.tip": "Pulls in anything it has room for",
        "gui.powerfactor.pocket_void.mode.void.tip": "Pulls in what it holds and deletes the excess once a slot is full",
        "gui.powerfactor.pocket_void.fill": "%s items, %s kinds",
        "gui.powerfactor.pocket_void.count": "%s / %s",
        "gui.powerfactor.pocket_void.locked": "Locked to this kind",
        "gui.powerfactor.pocket_void.empty": "Empty",
        "gui.powerfactor.pocket_void.selected.tip": "Builds when you use the void on a block",
        "gui.powerfactor.pocket_void.help": "Click: take or put · Right: half or one · Shift: to inventory · Ctrl: lock · Alt or middle: build with",
        "handbook.powerfactor.pocket_void.title": "Bedrock and the Pocket Void",
        "handbook.powerfactor.pocket_void.text": (
            "Bedrock can't be broken, but it can be chipped. Set off TNT right against it and a Bedrock Fragment may fly off "
            "(the closer the blast, the better the odds; two at most a blast). Four fragments and a glowstone dust make two "
            "Essence of Bedrock.\n\n"
            "A Pocket Void is a pouch held open on nothing by that essence: nine slots, each holding one kind of item by the "
            "thousand. Use it to open it. Click a cell to take a stack or put what you carry; right-click for half or one; "
            "shift-click to send a stack to your inventory; ctrl-click to lock a cell to its kind; alt- or middle-click to choose "
            "the cell it builds with when you use it on a block. Its pickup mode pulls in matching items, anything, or matching "
            "items with the excess deleted (cobblestone, dirt). A Void Core turns it into a Deep Pocket Void, everything inside "
            "kept.\n\n"
            "Lightning fuses sand: a bolt landing on sand (a lightning rod standing in it works) turns some into Fused Sand, "
            "which breaks into Fulgurite.\n\n"
            "Elevators: stand on one and jump to rise to the next of the same colour above, sneak to drop to the one below, "
            "up to 32 blocks with two clear blocks over the landing. Dye them to run separate shafts side by side. A Chunk "
            "Loader Card in any machine's upgrade slots keeps that machine's chunk loaded."),
    })
