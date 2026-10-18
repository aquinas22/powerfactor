"""Data for the wireless power network: models (multipart nodes with arms), blockstates, loot, recipes and text.
Called from gen_data.py as generate_data(globals())."""

DIRS = {"down": ([6, 0, 6], [10, 4, 10]), "up": ([6, 12, 6], [10, 16, 10]), "north": ([6, 6, 0], [10, 10, 4]),
        "south": ([6, 6, 12], [10, 10, 16]), "west": ([0, 6, 6], [4, 10, 10]), "east": ([12, 6, 6], [16, 10, 10])}
FLANGE = {"down": ([5, 0, 5], [11, 1, 11]), "up": ([5, 15, 5], [11, 16, 11]), "north": ([5, 5, 0], [11, 11, 1]),
          "south": ([5, 5, 15], [11, 11, 16]), "west": ([0, 5, 5], [1, 11, 11]), "east": ([15, 5, 5], [16, 11, 11])}
SIX = ("north", "south", "east", "west", "up", "down")


def generate_data(g):
    write, asset, data, pf, name, flat_item, shaped, shapeless, drops_self, tag, LANG = (g[k] for k in (
        "write", "asset", "data", "pf", "name", "flat_item", "shaped", "shapeless", "drops_self", "tag", "LANG"))
    T = lambda t: pf("block/powernet/" + t)

    def faces(tex):
        return {d: {"texture": tex} for d in SIX}

    for node in ("plug", "point"):
        block = "power_" + node
        write(asset("models", "block", f"powernet/{node}_core.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"core": T(node), "particle": T(node)},
            "elements": [{"from": [4, 4, 4], "to": [12, 12, 12], "faces": {d: {"texture": "#core", "uv": [0, 0, 16, 16]} for d in SIX}}],
            "display": {"gui": {"rotation": [30, 225, 0], "scale": [1.0, 1.0, 1.0]}}})
        parts = [{"apply": {"model": pf(f"block/powernet/{node}_core")}}]
        for d, (a, b) in DIRS.items():
            fa, fb = FLANGE[d]
            write(asset("models", "block", f"powernet/arm_{d}.json"), {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                "textures": {"arm": T("arm"), "flange": T("flange"), "particle": T("arm")},
                "elements": [{"from": a, "to": b, "faces": faces("#arm")}, {"from": fa, "to": fb, "faces": faces("#flange")}]})
            parts.append({"when": {d: "true"}, "apply": {"model": pf(f"block/powernet/arm_{d}")}})
        write(asset("blockstates", f"{block}.json"), {"multipart": parts})
        write(asset("items", f"{block}.json"), {"model": {"type": "minecraft:model", "model": pf(f"block/powernet/{node}_core")}})

    for cell in ("power_cell", "dense_power_cell", "quantum_power_cell"):
        write(asset("models", "block", f"powernet/{cell}.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "side": T(cell + "_side"), "top": T(cell + "_top"), "bottom": T(cell + "_top")}})
        write(asset("blockstates", f"{cell}.json"), {"variants": {"": {"model": pf(f"block/powernet/{cell}")}}})
        write(asset("items", f"{cell}.json"), {"model": {"type": "minecraft:model", "model": pf(f"block/powernet/{cell}")}})
    write(asset("models", "block", "powernet/controller.json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": T("controller_side"), "top": T("controller_top"), "bottom": T("controller_top")}})
    write(asset("blockstates", "power_net_controller.json"), {"variants": {"": {"model": pf("block/powernet/controller")}}})
    write(asset("items", "power_net_controller.json"), {"model": {"type": "minecraft:model", "model": pf("block/powernet/controller")}})
    flat_item("net_configurator")

    blocks = ("power_plug", "power_point", "power_cell", "dense_power_cell", "quantum_power_cell", "power_net_controller")
    for b in blocks:
        drops_self(b)
        tag("block", "minecraft", "mineable/pickaxe", [pf(b)])

    # Lightning-fused glass carries the power; bedrock anchors the controller.
    shaped("power_plug", [" F ", "KPK", " F "], {"F": pf("fulgurite"), "K": pf("copper_coil"), "P": "minecraft:ender_pearl"}, count=2)
    shaped("power_point", [" F ", "KTK", " F "], {"F": pf("fulgurite"), "K": pf("copper_coil"), "T": "minecraft:redstone_torch"}, count=2)
    shapeless("power_point", [pf("power_plug"), "minecraft:redstone_torch"], file="power_point_from_plug")
    shapeless("power_plug", [pf("power_point"), "#c:ingots/copper"], file="power_plug_from_point")
    shaped("power_net_controller", ["FDF", "PBP", "FCF"], {"F": pf("fulgurite"), "D": "minecraft:diamond", "P": pf("power_plug"),
                                                          "B": pf("bedrock_fragment"), "C": pf("capacitor_bank")})
    shaped("power_cell", ["QFQ", "FCF", "QFQ"], {"Q": "#c:gems/quartz", "F": pf("fulgurite"), "C": pf("capacitor_bank")})
    shaped("dense_power_cell", ["CDC", "DED", "CDC"], {"C": pf("power_cell"), "D": "minecraft:diamond", "E": pf("essence_of_bedrock")})
    shaped("quantum_power_cell", ["CXC", "XNX", "CXC"], {"C": pf("dense_power_cell"), "X": pf("exotic_matter"), "N": "minecraft:nether_star"})
    shaped("net_configurator", [" F ", "RCR", " I "], {"F": pf("fulgurite"), "R": "#c:dusts/redstone", "C": pf("control_circuit"), "I": "#c:ingots/iron"})

    for key, english in {"power_plug": "Power Plug", "power_point": "Power Point", "power_cell": "Power Cell", "dense_power_cell": "Dense Power Cell",
                         "quantum_power_cell": "Quantum Power Cell", "power_net_controller": "Power Net Controller"}.items():
        name(f"block.powerfactor.{key}", english)
    name("item.powerfactor.net_configurator", "Net Configurator")
    tabs = {"network": ("Net", "Choose, create or edit a network"), "device": ("Device", "This device's name, priority and limit"),
            "links": ("Links", "Every device on the network"), "stats": ("Stats", "The last minute of power in and out"),
            "people": ("Users", "Who may use the network, and wireless charging")}
    for k, (label, tip) in tabs.items():
        LANG[f"gui.powerfactor.power_net.tab.{k}"] = label
        LANG[f"gui.powerfactor.power_net.tab.{k}.tip"] = tip
    LANG.update({
        "gui.powerfactor.power_net.name_hint": "Network Name",
        "gui.powerfactor.power_net.password_hint": "Password",
        "gui.powerfactor.power_net.device_hint": "Device Name",
        "gui.powerfactor.power_net.limit_hint": "FE/t",
        "gui.powerfactor.power_net.member_hint": "Player Name",
        "gui.powerfactor.power_net.no_networks.hint": "No networks yet: name one and press Create",
        "gui.powerfactor.power_net.no_network": "Not on a network",
        "gui.powerfactor.power_net.access.public": "Public",
        "gui.powerfactor.power_net.access.private": "Private",
        "gui.powerfactor.power_net.access.password": "Password",
        "gui.powerfactor.power_net.access.public.tip": "Anyone can connect devices to it",
        "gui.powerfactor.power_net.access.private.tip": "Only you and the people you add",
        "gui.powerfactor.power_net.access.password.tip": "Anyone who knows the password",
        "gui.powerfactor.power_net.join": "Join",
        "gui.powerfactor.power_net.join.tip": "Join with the password",
        "gui.powerfactor.power_net.create": "Create",
        "gui.powerfactor.power_net.create.tip": "Make a new network with this name, colour and access, and put this device on it",
        "gui.powerfactor.power_net.save": "Save",
        "gui.powerfactor.power_net.save.tip": "Rename and recolour this network",
        "gui.powerfactor.power_net.delete": "Delete",
        "gui.powerfactor.power_net.delete_now": "Confirm",
        "gui.powerfactor.power_net.delete.tip": "Delete the network: every device on it is disconnected",
        "gui.powerfactor.power_net.leave": "Disconnect",
        "gui.powerfactor.power_net.leave.tip": "Take this device off the network",
        "gui.powerfactor.power_net.owner": "Owner: %s",
        "gui.powerfactor.power_net.status.running": "Running",
        "gui.powerfactor.power_net.status.no_controller": "No Controller",
        "gui.powerfactor.power_net.status.two_controllers": "Two Controllers",
        "gui.powerfactor.power_net.status.none": "Not Connected",
        "gui.powerfactor.power_net.priority.tip": "Higher priority is served first; Cells give up charge from the lowest",
        "gui.powerfactor.power_net.limit": "Transfer Limit",
        "gui.powerfactor.power_net.unlimited": "No Limit",
        "gui.powerfactor.power_net.unlimited.tip": "Ignore the transfer limit",
        "gui.powerfactor.power_net.surge": "Surge",
        "gui.powerfactor.power_net.surge.tip": "Always served first, whatever the priorities",
        "gui.powerfactor.power_net.chunk": "Keep Chunk Loaded",
        "gui.powerfactor.power_net.chunk.tip": "This device keeps its own chunk loaded",
        "gui.powerfactor.power_net.apply": "Apply",
        "gui.powerfactor.power_net.apply.tip": "Save the name and limit",
        "gui.powerfactor.power_net.controller_note": "The controller runs the network; one per network.",
        "gui.powerfactor.power_net.role.owner": "Owner",
        "gui.powerfactor.power_net.role.admin": "Admin",
        "gui.powerfactor.power_net.role.user": "User",
        "gui.powerfactor.power_net.remove": "Remove",
        "gui.powerfactor.power_net.add_user": "User",
        "gui.powerfactor.power_net.add_user.tip": "Let this player use the network",
        "gui.powerfactor.power_net.add_admin": "Admin",
        "gui.powerfactor.power_net.add_admin.tip": "Let this player use and edit the network",
        "gui.powerfactor.power_net.charging": "Wireless Charging",
        "gui.powerfactor.power_net.charge.hotbar": "Hotbar",
        "gui.powerfactor.power_net.charge.armor": "Armor",
        "gui.powerfactor.power_net.charge.offhand": "Offhand",
        "gui.powerfactor.power_net.charge.inventory": "Pack",
        "gui.powerfactor.power_net.charge.tip": "Charge these items of every member, anywhere, from the network",
        "gui.powerfactor.power_net.charged": "Wireless charging now",
        "gui.powerfactor.power_net.copied": "Settings copied",
        "gui.powerfactor.power_net.pasted": "Settings pasted",
        "handbook.powerfactor.power_net.title": "Wireless Power Networks",
        "handbook.powerfactor.power_net.text": (
            "A power network moves FE without wires, any distance and between dimensions. Every network needs one Power Net "
            "Controller. Power Plugs take power from whatever touches them (generators, batteries, cables) and Power Points "
            "give it to whatever touches them; Power Cells store it (4M, 64M and 1G FE).\n\n"
            "Open any device to choose or create a network: name it, pick its colour (the devices glow in it) and say who may use "
            "it: Public, Private (you and the people you add) or Password. Each device has a name, a priority (higher is served "
            "first, and Cells give up their charge from the lowest), a transfer limit (800,000 FE/t by default, or none), Surge "
            "(always served first) and can keep its own chunk loaded. The Links tab lists every device with its flow; Stats "
            "graphs the last minute; People adds members and admins and sets the controller's wireless charging of members' "
            "hotbar, armour, offhand and pack.\n\n"
            "Plugs and Points are made from Fulgurite (sand fused by lightning). A Net Configurator copies a device's settings "
            "(sneak-use) and pastes them on another (use). New devices you place join the network you last used."),
    })
