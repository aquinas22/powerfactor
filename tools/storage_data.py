"""Storage network and autocrafting: models, blockstates, recipes, loot, tags and English names.

Called from gen_data.py with its globals (write/asset/pf/shaped/...). Edit here, not the output.
"""

SIDES = ("north", "south", "east", "west", "up", "down")
FACING = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def uv(a, b, side):
    """World-scale UVs so small parts sample a proportional patch of their texture."""
    dx, dy, dz = (min(16, max(.1, b[i] - a[i])) for i in range(3))
    x0, y0, z0 = (min(16 - d, max(0, v)) for d, v in zip((dx, dy, dz), a))
    if side in ("up", "down"):
        return [x0, z0, x0 + dx, z0 + dz]
    if side in ("east", "west"):
        return [z0, 16 - y0 - dy, z0 + dz, 16 - y0]
    return [x0, 16 - y0 - dy, x0 + dx, 16 - y0]


def box(a, b, tex, faces=None, glow=0, full=()):
    """A cuboid; {@code faces} overrides per-face textures, {@code full} faces map the whole tile."""
    f = {}
    for side in SIDES:
        t = (faces or {}).get(side, tex)
        if t is None:
            continue
        f[side] = {"texture": "#" + t, "uv": [0, 0, 16, 16] if side in full else uv(a, b, side)}
    e = {"from": a, "to": b, "faces": f}
    if glow:
        e["light_emission"] = glow
    return e


def generate(g):
    write, asset, pf, name, item_def, flat_item = (g[k] for k in ("write", "asset", "pf", "name", "item_def", "flat_item"))
    shaped, shapeless, drops_self, tag, LANG = (g[k] for k in ("shaped", "shapeless", "drops_self", "tag", "LANG"))

    def model(model_name, elements, textures, render="minecraft:cutout", display=None):
        used = {face["texture"][1:] for e in elements for face in e["faces"].values()}
        tex = {k: (v if ":" in v else pf("block/" + v)) for k, v in textures.items() if k in used or k == "particle"}
        m = {"parent": "minecraft:block/block", "render_type": render, "textures": tex, "elements": elements}
        if display:
            m["display"] = display
        write(asset("models", "block", model_name + ".json"), m)

    def node_states(block, off, on):
        variants = {}
        for facing, rot in FACING.items():
            for active, mdl in (("false", off), ("true", on)):
                variants[f"active={active},facing={facing}"] = dict({"model": pf("block/" + mdl)}, **rot)
        write(asset("blockstates", block + ".json"), {"variants": variants})

    T = {"casing": "storage_casing", "lit": "storage_casing_lit", "glass": "storage_glass", "vent": "storage_vent",
         "plate": "storage_controller_plate", "steel": "finish_steel", "dark": "finish_dark", "chrome": "finish_chrome",
         "particle": "storage_casing"}

    # ------------------------------------------------------------ controller: glass core chamber in a graphite frame
    for suffix, lit in (("", "casing"), ("_on", "lit")):
        glow = 15 if suffix else 0
        e = [box([0, 0, 0], [16, 3, 16], "casing", {"up": "plate", "down": "plate"}, full=("up", "down")),
             box([0, 13, 0], [16, 16, 16], "casing", {"up": "plate", "down": "plate"}, full=("up", "down"))]
        for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
            e.append(box([x, 3, z], [x + 2, 13, z + 2], lit, glow=glow))
        # Accent rails around the chamber mouth.
        for y in (3, 12.4):
            e.append(box([2, y, 0.4], [14, y + .6, 1], "chrome"))
            e.append(box([2, y, 15], [14, y + .6, 15.6], "chrome"))
        panes = [box([2, 3, .6], [14, 13, .8], "glass", full=SIDES), box([2, 3, 15.2], [14, 13, 15.4], "glass", full=SIDES),
                 box([.6, 3, 2], [.8, 13, 14], "glass", full=SIDES), box([15.2, 3, 2], [15.4, 13, 14], "glass", full=SIDES)]
        model("storage_controller" + suffix, e + panes, T, "minecraft:translucent")
    node_states("storage_controller", "storage_controller", "storage_controller_on")
    item_def("storage_controller", pf("block/storage_controller_on"))

    # ------------------------------------------------------------ drive bay: rack front, cells drawn by the renderer
    for suffix, lit in (("", "casing"), ("_on", "lit")):
        tex = dict(T, front="drive_bay_front")
        e = [box([0, 0, 1], [16, 16, 16], "casing", {"north": None}),
             box([0, 0, 0], [16, 16, 1], "front", {"south": None}, full=("north",)),
             box([-.01, 6, 4], [.4, 10, 12], lit, glow=15 if suffix else 0),
             box([15.6, 6, 4], [16.01, 10, 12], lit, glow=15 if suffix else 0)]
        model("drive_bay" + suffix, e, tex)
    node_states("drive_bay", "drive_bay", "drive_bay_on")
    item_def("drive_bay", pf("block/drive_bay_on"))

    # ------------------------------------------------------------ terminals: wall-mounted displays
    for kind in ("grid", "crafting", "pattern", "fluid"):
        block = kind + "_terminal"
        for suffix in ("", "_on"):
            tex = dict(T, frame="terminal_frame", screen=("terminal_screen_" + kind + "_on") if suffix else "terminal_screen_off")
            e = [box([1, 1, 12], [15, 15, 16], "casing"),
                 box([1, 1, 9], [15, 15, 12], "frame", {"north": "frame"}),
                 box([2.5, 2.5, 8.7], [13.5, 13.5, 9], "screen", {"south": None}, glow=15 if suffix else 0, full=("north",)),
                 box([5, 0, 12.5], [11, 1, 15.5], "dark"), box([5, 15, 12.5], [11, 16, 15.5], "dark")]
            model(block + suffix, e, tex, display={"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3}})
        node_states(block, block, block + "_on")
        item_def(block, pf("block/" + block + "_on"))

    # ------------------------------------------------------------ ports: a face plate on the target, a body toward the cable
    for block, face in (("storage_bus", "storage_bus_face"), ("importer", "importer_face"), ("exporter", "exporter_face")):
        for suffix in ("", "_on"):
            tex = dict(T, body="port_body", face=face)
            e = [box([1, 1, 0], [15, 15, 2], "body", {"north": "face"}, glow=12 if suffix else 0, full=("north",)),
                 box([3, 3, 2], [13, 13, 6], "body"),
                 box([5, 5, 6], [11, 11, 8], "dark"),
                 box([2, 2, 1.6], [3, 3, 2.4], "chrome"), box([13, 2, 1.6], [14, 3, 2.4], "chrome"),
                 box([2, 13, 1.6], [3, 14, 2.4], "chrome"), box([13, 13, 1.6], [14, 14, 2.4], "chrome")]
            model(block + suffix, e, tex)
        node_states(block, block, block + "_on")
        item_def(block, pf("block/" + block + "_on"))

    # ------------------------------------------------------------ crafting processors: PCB block with a glass cupola
    for block, side in (("crafting_processor", "crafting_processor_side"), ("quantum_crafting_processor", "quantum_processor_side")):
        for suffix in ("", "_on"):
            tex = dict(T, pcb=side)
            e = [box([0, 0, 0], [16, 11, 16], "pcb", {"up": "plate", "down": "plate"}, full=("north", "south", "east", "west", "up", "down")),
                 box([2, 11, 2], [14, 12, 14], "casing", {"up": "plate"}, full=("up",)),
                 box([3.5, 12, 3.5], [12.5, 15.5, 12.5], "glass", full=SIDES),
                 # A thin dark rim around the cupola's mouth instead of a lid, so the core shows from above.
                 box([3, 15.5, 3], [13, 16, 4], "dark"), box([3, 15.5, 12], [13, 16, 13], "dark"),
                 box([3, 15.5, 4], [4, 16, 12], "dark"), box([12, 15.5, 4], [13, 16, 12], "dark")]
            for x, z in ((2, 2), (13, 2), (2, 13), (13, 13)):
                e.append(box([x, 11, z], [x + 1, 16, z + 1], "lit" if suffix else "casing", glow=15 if suffix else 0))
            model(block + suffix, e, tex, "minecraft:translucent")
        node_states(block, block, block + "_on")
        item_def(block, pf("block/" + block + "_on"))

    # ------------------------------------------------------------ assembler: glass chamber, hologram and lasers by the renderer
    for suffix, lit in (("", "casing"), ("_on", "lit")):
        tex = dict(T, base="assembler_base")
        e = [box([0, 0, 0], [16, 2, 16], "casing", {"up": "base", "down": "base"}, full=("up", "down")),
             box([0, 14, 0], [16, 16, 16], "casing", {"up": "base", "down": "base"}, full=("up", "down"))]
        for x, z in ((0, 0), (14.5, 0), (0, 14.5), (14.5, 14.5)):
            e.append(box([x, 2, z], [x + 1.5, 14, z + 1.5], lit, glow=15 if suffix else 0))
        # Emitter heads in the upper corners; the renderer fires beams from here.
        for x, z in ((1.5, 1.5), (12.5, 1.5), (1.5, 12.5), (12.5, 12.5)):
            e.append(box([x, 12, z], [x + 2, 14, z + 2], "chrome"))
        e += [box([1.5, 2, .4], [14.5, 14, .6], "glass", full=SIDES), box([1.5, 2, 15.4], [14.5, 14, 15.6], "glass", full=SIDES),
              box([.4, 2, 1.5], [.6, 14, 14.5], "glass", full=SIDES), box([15.4, 2, 1.5], [15.6, 14, 14.5], "glass", full=SIDES),
              box([5, 2, 5], [11, 3, 11], "chrome")]
        model("assembler" + suffix, e, tex, "minecraft:translucent")
    node_states("assembler", "assembler", "assembler_on")
    item_def("assembler", pf("block/assembler_on"))

    # ------------------------------------------------------------ pattern provider
    for suffix in ("", "_on"):
        tex = dict(T, front="pattern_provider_front", side="pattern_provider_side")
        e = [box([0, 0, 0], [16, 16, 16], "side", {"north": "front", "up": "casing", "down": "casing"}, full=SIDES)]
        if suffix:
            e.append(box([4, 15.9, 4], [12, 16.05, 12], "lit", glow=15))
        model("pattern_provider" + suffix, e, tex)
    node_states("pattern_provider", "pattern_provider", "pattern_provider_on")
    item_def("pattern_provider", pf("block/pattern_provider_on"))

    # ------------------------------------------------------------ creative power source (creative-only test supply)
    model("creative_power_source", [box([0, 0, 0], [16, 16, 16], "pcb", {"up": "plate", "down": "plate"}, glow=15, full=SIDES)],
          dict(T, pcb="quantum_processor_side"))
    write(asset("blockstates", "creative_power_source.json"), {"variants": {"": {"model": pf("block/creative_power_source")}}})
    item_def("creative_power_source")
    name("block.powerfactor.creative_power_source", "Creative Power Source")

    # ------------------------------------------------------------ cables
    g["cable"]("data_cable", 4)
    g["cable"]("quantum_data_cable", 6)

    # ------------------------------------------------------------ items
    for item in ("storage_cell_1k", "storage_cell_4k", "storage_cell_16k", "storage_cell_64k", "storage_cell_256k", "quantum_storage_cell",
                 "blank_pattern", "crafting_pattern", "processing_pattern", "wireless_terminal", "quantum_wireless_terminal",
                 "data_core", "cell_housing", "fluid_cell_4k", "fluid_cell_64k", "quantum_fluid_cell"):
        flat_item(item)

    # ------------------------------------------------------------ loot and tags
    blocks = ["storage_controller", "data_cable", "quantum_data_cable", "drive_bay", "grid_terminal", "crafting_terminal",
              "pattern_terminal", "fluid_terminal", "storage_bus", "importer", "exporter", "crafting_processor", "quantum_crafting_processor",
              "assembler", "pattern_provider"]
    for b in blocks:
        drops_self(b)
    tag("block", "minecraft", "mineable/pickaxe", [pf(b) for b in blocks])
    tag("item", "c", "storage_cells", [pf(c) for c in ("storage_cell_1k", "storage_cell_4k", "storage_cell_16k", "storage_cell_64k",
                                                         "storage_cell_256k", "quantum_storage_cell")])

    # ------------------------------------------------------------ recipes
    P = "#c:plates/steel"
    shaped("data_core", ["GQG", "QMQ", "GQG"], {"G": "#c:dusts/glowstone", "Q": "#c:gems/quartz", "M": pf("memory_chip")}, 2)
    shaped("cell_housing", ["PGP", "G G", "PPP"], {"P": "#c:plates/aluminum", "G": "#c:glass_panes"}, 2)
    shapeless("storage_cell_1k", [pf("cell_housing"), pf("data_core"), "#c:dusts/redstone"])
    shapeless("storage_cell_4k", [pf("cell_housing"), pf("data_core"), pf("data_core"), pf("microprocessor")])
    shapeless("storage_cell_16k", [pf("cell_housing"), pf("data_core"), pf("data_core"), pf("data_core"), "#c:gems/diamond"])
    shapeless("storage_cell_64k", [pf("cell_housing"), pf("data_core"), pf("data_core"), pf("data_core"), pf("data_core"),
                                   "#c:gems/diamond", "#c:gems/diamond", pf("microprocessor")])
    shapeless("storage_cell_256k", [pf("storage_cell_64k"), pf("data_core"), pf("data_core"), pf("data_core"), pf("data_core"),
                                    "#c:gems/emerald", "#c:ingots/electrum", pf("advanced_circuit")])
    shaped("data_cable", ["RRR", "FOF", "RRR"], {"R": "#c:rubbers", "F": pf("fine_copper_wire"), "O": pf("optical_fiber")}, 12)
    shaped("quantum_data_cable", ["CCC", "CEC", "CCC"], {"C": pf("data_cable"), "E": "#c:ender_pearls"}, 8)
    shaped("storage_controller", ["PDP", "CMC", "PEP"], {"P": P, "D": pf("data_core"), "C": pf("control_circuit"),
                                                         "M": pf("microprocessor"), "E": pf("capacitor")})
    shaped("drive_bay", ["PCP", "H H", "PCP"], {"P": P, "C": pf("control_circuit"), "H": pf("cell_housing")})
    shaped("grid_terminal", ["GGG", "DMD", "PCP"], {"G": "#c:glass_panes", "D": pf("data_core"), "M": pf("microprocessor"),
                                                    "P": P, "C": pf("control_circuit")})
    shapeless("crafting_terminal", [pf("grid_terminal"), "minecraft:crafting_table", pf("advanced_circuit")])
    shapeless("pattern_terminal", [pf("crafting_terminal"), pf("blank_pattern"), pf("microprocessor")])
    shapeless("fluid_terminal", [pf("grid_terminal"), "minecraft:bucket", pf("microprocessor")])
    shapeless("fluid_cell_4k", [pf("cell_housing"), pf("data_core"), pf("data_core"), "minecraft:bucket", "#c:gems/lapis"])
    shapeless("fluid_cell_64k", [pf("cell_housing"), pf("data_core"), pf("data_core"), pf("data_core"), pf("data_core"), "minecraft:bucket",
                                 "#c:gems/diamond"])
    shapeless("quantum_fluid_cell", [pf("fluid_cell_64k"), pf("quantum_processor"), pf("exotic_matter"), "#c:ingots/quantum_alloy"])
    shaped("storage_bus", [" C ", "PDP", " H "], {"C": "#c:chests", "P": P, "D": pf("data_core"), "H": "minecraft:hopper"})
    shaped("importer", ["PHP", " D "], {"P": P, "H": "minecraft:hopper", "D": pf("data_core")})
    shaped("exporter", ["PDP", " X "], {"P": P, "D": pf("data_core"), "X": "minecraft:dispenser"})
    shaped("blank_pattern", ["QGQ", "GSG", "PPP"], {"Q": "#c:gems/quartz", "G": "#c:dusts/glowstone", "S": pf("silicon_wafer"),
                                                     "P": "#c:plates/aluminum"}, 4)
    shaped("crafting_processor", ["PMP", "DAD", "PCP"], {"P": P, "M": pf("microprocessor"), "D": pf("data_core"),
                                                          "A": pf("advanced_circuit"), "C": pf("control_circuit")})
    shaped("assembler", ["PGP", "CTC", "PMP"], {"P": P, "G": "#c:glass_blocks", "C": pf("control_circuit"),
                                                "T": "minecraft:crafting_table", "M": pf("electric_motor")})
    shaped("pattern_provider", ["PCP", "TDT", "PCP"], {"P": P, "C": pf("control_circuit"), "T": "#c:chests", "D": pf("data_core")})
    shapeless("wireless_terminal", [pf("grid_terminal"), "#c:ender_pearls", pf("capacitor"), pf("sensor_chip")])

    # ------------------------------------------------------------ names and interface text
    names = {
        "storage_controller": "Storage Controller", "data_cable": "Data Cable", "quantum_data_cable": "Quantum Data Cable",
        "drive_bay": "Drive Bay", "grid_terminal": "Grid Terminal", "crafting_terminal": "Crafting Terminal",
        "pattern_terminal": "Pattern Terminal", "fluid_terminal": "Fluid Terminal", "storage_bus": "Storage Bus", "importer": "Importer", "exporter": "Exporter",
        "crafting_processor": "Crafting Processor", "quantum_crafting_processor": "Quantum Crafting Processor",
        "assembler": "Molecular Assembler", "pattern_provider": "Pattern Provider"}
    for k, v in names.items():
        name(f"block.powerfactor.{k}", v)
    items = {"storage_cell_1k": "1k Storage Cell", "storage_cell_4k": "4k Storage Cell", "storage_cell_16k": "16k Storage Cell",
             "storage_cell_64k": "64k Storage Cell", "storage_cell_256k": "256k Storage Cell", "quantum_storage_cell": "Quantum Storage Cell",
             "blank_pattern": "Blank Pattern", "crafting_pattern": "Crafting Pattern", "processing_pattern": "Processing Pattern",
             "wireless_terminal": "Wireless Terminal", "quantum_wireless_terminal": "Quantum Wireless Terminal",
             "data_core": "Data Core", "cell_housing": "Cell Housing", "fluid_cell_4k": "4k Fluid Cell", "fluid_cell_64k": "64k Fluid Cell",
             "quantum_fluid_cell": "Quantum Fluid Cell"}
    for k, v in items.items():
        name(f"item.powerfactor.{k}", v)
    LANG.update({
        "itemGroup.powerfactor.quantum": "PowerFactor: Quantum & Logistics",
        "item.powerfactor.crafting_pattern.of": "Crafting Pattern: %s", "item.powerfactor.processing_pattern.of": "Processing Pattern: %s",
        "tooltip.powerfactor.cell.bytes": "%s / %s bytes", "tooltip.powerfactor.cell.types": "%s of %s types",
        "tooltip.powerfactor.cell.more": "…and %s more",
        "tooltip.powerfactor.fluid_cell.capacity": "Holds %s buckets of one fluid · up to %s fluids",
        "gui.powerfactor.fluid_terminal.empty": "No fluids stored",
        "gui.powerfactor.fluid_terminal.hint": "Click with a bucket to fill or empty it",
        "gui.powerfactor.port.speed_upgrades": "Speed",
        # GUI icon names (Skin.Icon tooltips)
        "icon.powerfactor.fe": "Real Power (FE)",
        "icon.powerfactor.ee": "Reactive Power (EE)",
        "icon.powerfactor.ce": "Apparent Power (CE)",
        "icon.powerfactor.pf": "Power Factor",
        "icon.powerfactor.heat": "Heat",
        "icon.powerfactor.temp": "Temperature",
        "icon.powerfactor.fluid": "Fluid",
        "icon.powerfactor.steam": "Steam",
        "icon.powerfactor.item": "Items",
        "icon.powerfactor.speed": "Speed",
        "icon.powerfactor.efficiency": "Efficiency",
        "icon.powerfactor.priority": "Priority",
        "icon.powerfactor.allow": "Allow List",
        "icon.powerfactor.deny": "Deny List",
        "icon.powerfactor.filter": "Filter",
        "icon.powerfactor.exact": "Exact Match (Components)",
        "icon.powerfactor.link": "Links",
        "icon.powerfactor.range": "Range",
        "icon.powerfactor.kills": "Kills",
        "icon.powerfactor.animal": "Animals",
        "icon.powerfactor.player": "Players",
        "icon.powerfactor.hostile": "Hostiles",
        "icon.powerfactor.clock": "Time",
        "icon.powerfactor.redstone": "Redstone",
        "icon.powerfactor.power": "Power",
        "icon.powerfactor.warning": "Warning",
        "icon.powerfactor.error": "Fault",
        "icon.powerfactor.ok": "Running",
        "icon.powerfactor.wait": "Waiting",
        "icon.powerfactor.night": "Night",
        "icon.powerfactor.day": "Day",
        "icon.powerfactor.area": "Area",
        "icon.powerfactor.depth": "Depth",
        "icon.powerfactor.fortune": "Fortune",
        "icon.powerfactor.silk": "Silk Touch",
        "icon.powerfactor.void": "Void Stone",
        "icon.powerfactor.restart": "Restart",
        "icon.powerfactor.plus": "More",
        "icon.powerfactor.minus": "Less",
        "icon.powerfactor.shield": "Shield",
        "icon.powerfactor.hit": "Hits",
        "icon.powerfactor.output": "Output",
        "icon.powerfactor.input": "Input",
        "icon.powerfactor.matter": "Exotic Matter",
        "icon.powerfactor.battery": "Stored Energy",
        "icon.powerfactor.fuel": "Fuel",
        "icon.powerfactor.rotor": "Rotor Speed",
        "icon.powerfactor.mined": "Mined",
        "icon.powerfactor.network": "Network",
        "icon.powerfactor.upgrade": "Upgrades",
        "icon.powerfactor.hz": "Frequency",
        "icon.powerfactor.lock": "Blocking",
        "icon.powerfactor.unlock": "Free",
        "icon.powerfactor.vision": "Vision",
        "icon.powerfactor.channel": "Channel",

        "gui.powerfactor.fluid_terminal.fill_tip": "Click with an empty container to fill",
        "tooltip.powerfactor.pattern.blank": "Encode in a Pattern Terminal", "tooltip.powerfactor.pattern.makes": "Produces:",
        "tooltip.powerfactor.pattern.from": "From:",
        "tooltip.powerfactor.pattern.crafting": "Crafting pattern · runs in a Molecular Assembler · sneak-use to wipe",
        "tooltip.powerfactor.pattern.processing": "Processing pattern · runs through a Pattern Provider · sneak-use to wipe",
        "tooltip.powerfactor.wireless.unlinked": "Not linked · sneak-use on a Storage Controller",
        "tooltip.powerfactor.wireless.linked": "Linked to controller at %s",
        "tooltip.powerfactor.wireless.range": "Range: 64 blocks",
        "tooltip.powerfactor.wireless.quantum": "Entangled: unlimited range in the same dimension",
        "gui.powerfactor.wireless.linked": "Terminal linked to this controller",
        "gui.powerfactor.wireless.unlinked": "Not linked: sneak-use the terminal on a Storage Controller",
        "gui.powerfactor.wireless.dimension": "The linked controller is in another dimension",
        "gui.powerfactor.wireless.range": "Out of range of the linked controller",
        "gui.powerfactor.wireless.missing": "Linked controller not found (or its chunk is unloaded)",
        "gui.powerfactor.wireless.empty": "Wireless terminal battery is empty",
        "gui.powerfactor.storage.starting": "Starting…", "gui.powerfactor.storage.online": "Online",
        "gui.powerfactor.storage.no_power": "NO POWER · feed the controller FE",
        "gui.powerfactor.storage.conflict": "CONFLICT · one controller per network",
        "gui.powerfactor.storage.no_network": "NOT CONNECTED · no Storage Controller",
        "gui.powerfactor.storage.priority": "Priority: %s",
        "gui.powerfactor.storage.priority.tip": "Higher priority storage fills first and empties last",
        "gui.powerfactor.terminal.search": "Search", "gui.powerfactor.terminal.search_hint": "Search · @mod #tag",
        "gui.powerfactor.terminal.stored": "Stored: %s",
        "gui.powerfactor.terminal.craftable_hint": "Craftable · Ctrl-click or middle-click to order",
        "gui.powerfactor.terminal.bytes": "%s / %s bytes · %s types",
        "gui.powerfactor.terminal.sort.name": "Sort: name", "gui.powerfactor.terminal.sort.count": "Sort: amount",
        "gui.powerfactor.terminal.sort.mod": "Sort: mod", "gui.powerfactor.terminal.ascending": "Ascending",
        "gui.powerfactor.terminal.descending": "Descending",
        "gui.powerfactor.terminal.view.all": "Show: stored and craftable", "gui.powerfactor.terminal.view.stored": "Show: stored only",
        "gui.powerfactor.terminal.view.craftable": "Show: craftable only",
        "gui.powerfactor.terminal.jobs": "Crafting status · %s running",
        "gui.powerfactor.terminal.clear_grid": "Return the crafting grid to the network",
        "gui.powerfactor.pattern.mode.crafting": "Crafting pattern (click for processing)",
        "gui.powerfactor.pattern.mode.processing": "Processing pattern (click for crafting) · click ghosts with stacks to set amounts",
        "gui.powerfactor.pattern.clear": "Clear the recipe", "gui.powerfactor.pattern.encode": "Encode onto the blank pattern",
        "gui.powerfactor.craft.amount": "Amount", "gui.powerfactor.craft.title": "Craft %s",
        "gui.powerfactor.craft.next": "Next", "gui.powerfactor.craft.planning": "Planning…", "gui.powerfactor.craft.start": "Start",
        "gui.powerfactor.craft.plan": "%s × %s · %s operations",
        "gui.powerfactor.craft.use": "Use %s", "gui.powerfactor.craft.make": "Craft %s", "gui.powerfactor.craft.missing": "Missing %s",
        "gui.powerfactor.craft.ready": "Ready to craft", "gui.powerfactor.craft.cannot": "Missing ingredients",
        "gui.powerfactor.craft.no_cpu": "All crafting processors are busy",
        "gui.powerfactor.craft.changed": "Storage changed while planning; plan again",
        "gui.powerfactor.craft.done": "Crafted %s × %s",
        "gui.powerfactor.jobs.title": "Crafting Status", "gui.powerfactor.jobs.none": "No crafting jobs",
        "gui.powerfactor.jobs.cpus": "%s processors · %s free", "gui.powerfactor.jobs.running": "Running",
        "gui.powerfactor.jobs.finishing": "Returning products", "gui.powerfactor.jobs.stalled": "Waiting: no machine accepted the inputs",
        "gui.powerfactor.drive.capacity": "%s / %s bytes",
        "gui.powerfactor.port.allow": "Allow-list", "gui.powerfactor.port.deny": "Deny-list",
        "gui.powerfactor.port.allow.short": "Allow", "gui.powerfactor.port.deny.short": "Deny",
        "gui.powerfactor.port.mode.tip": "Whether the filter allows or blocks the listed items",
        "gui.powerfactor.port.exact": "Exact", "gui.powerfactor.port.fuzzy": "Any NBT",
        "gui.powerfactor.port.exact.tip": "Exact: match components (enchantments, damage…) too",
        "gui.powerfactor.port.importing": "Pulling in", "gui.powerfactor.port.exporting": "Pushing out",
        "gui.powerfactor.assembler.hint": "Crafting patterns", "gui.powerfactor.provider.hint": "Processing patterns",
        "gui.powerfactor.provider.blocking": "Blocking", "gui.powerfactor.provider.free": "Free",
        "gui.powerfactor.provider.blocking.tip": "Blocking: wait until the machine has used the previous batch",
    })
    LANG["handbook.powerfactor.storage.title"] = "Storage Network & Autocrafting"
    LANG["handbook.powerfactor.storage.text"] = (
        "A Storage Controller powers a network of Data Cables. Feed it FE from your grid; every device draws a little power, shown as the "
        "controller's load. Drive Bays hold Storage Cells (1k to Quantum). Each cell stores 8 items per byte and reserves bytes for every "
        "distinct item type. Storage Buses turn an adjacent chest or any inventory into network storage; priority decides where items go.\n\n"
        "Terminals show everything on the network. Left-click takes a stack, right-click half, Shift-click sends it to your inventory; "
        "click with an item to store it. Search with words, @mod or #tag. The Crafting Terminal refills its grid from the network.\n\n"
        "Autocrafting: encode Crafting Patterns in the Pattern Terminal and put them in a Molecular Assembler; encode Processing Patterns "
        "and put them in a Pattern Provider next to a machine. The provider pushes inputs into the machine; set the machine to eject its "
        "products back into the provider. Add a Crafting Processor, then Ctrl-click a craftable item to plan and start a craft. "
        "Quantum Crafting Processors run four jobs at once.\n\n"
        "Fluids: Fluid Cells (4k, 64k, Quantum) go in Drive Bays beside item cells; each byte holds eight buckets. Pipes can push fluid "
        "into the Storage Controller and pull it back out. The Fluid Terminal lists every fluid: click a fluid with an empty bucket or "
        "tank to fill it, or click with a full one to pour it in.")
