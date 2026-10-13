"""Detailed models for existing machinery; invoked by gen_data.py after its other phases."""
def generate(g):
    write, asset, pf, states, item, octagon = [g[k] for k in ("write", "asset", "pf", "machine_states", "item_def", "octagon")]
    six = ("north", "east", "south", "west", "up", "down")
    def box(a, b, tex="#steel", front=None, glow=0, rotation=None):
        e = {"from": a, "to": b, "faces": {d: {"texture": front if d == "north" and front else tex, "uv": [0,0,16,16]} for d in six}}
        if glow: e["light_emission"] = glow
        if rotation: e["rotation"] = rotation
        return e
    def cylinder(width, bottom, top, tex, dx=0, dz=0):
        out = octagon(width, bottom, top, tex)
        for e in out:
            for key in ("from", "to"):
                e[key][0] += dx; e[key][2] += dz
            if "rotation" in e:
                e["rotation"]["origin"][0] += dx; e["rotation"]["origin"][2] += dz
        return out
    def feet():
        return [box([x,0,z],[x+2,2,z+2],"#dark") for x in (1,13) for z in (1,13)] + [box([1,2,1],[15,3.5,15])]
    def rails():
        return [box([x,3,z],[x+1,15,z+1]) for x in (1,14) for z in (1,14)]
    def control(y=4):
        return [box([3,y,0.7],[13,y+5,1.7],"#dark", "#front"), box([13.2,y+1,0.6],[14.3,y+2.2,1],"#lamp",glow=8)]
    def fins(x, y, z, height=8, count=6):
        return [box([x,y+i*height/count,z],[x+10,y+i*height/count+0.6,z+2]) for i in range(count)]
    def front_ring():
        return [box([1,4,1],[3,14,3]),box([13,4,1],[15,14,3]),box([3,3,1],[13,5,3]),box([3,13,1],[13,15,3])]
    def save(name, elements, extra=None, suffix="", active=False):
        tex = {"steel": pf("block/workshop_steel"), "dark": pf("block/industrial_dark"), "copper": pf("block/industrial_copper"),
               "glass": pf("block/industrial_glass"), "water": pf("block/water_window"), "coil": pf("block/industrial_coil"),
               "body": pf("block/"+name+"_side"), "front": pf("block/"+name+"_front"+("_on" if active else "")),
               "lamp": pf("block/industrial_lamp_on" if active else "block/industrial_lamp"), "particle": pf("block/workshop_steel")}
        if extra: tex.update(extra)
        used = {f["texture"].lstrip("#") for e in elements for f in e["faces"].values()}
        tex = {k:v for k,v in tex.items() if k in used or k=="particle"}
        write(asset("models","block",name+suffix+".json"),{"parent":"minecraft:block/block", "render_type":"minecraft:cutout", "textures":tex,"elements":elements})
    names = ["electric_furnace", "charger", "alloy_furnace", "arc_furnace", "wire_drawer", "rolling_mill", "ore_washer",
             "diesel_generator", "battery_bank", "power_meter", "hydro_turbine", "synchronous_condenser", "electrolyzer", "fuel_cell",
             "electric_pump", "geothermal_generator", "thermoelectric_generator", "ender_transmitter", "ender_receiver"]
    for name in names:
        for active, suffix in ((False,""),(True,"_on")):
            e = feet()
            extra = {}
            if name == "electric_furnace":
                e += [box([1,3,2],[3,15,14],"#body"),box([13,3,2],[15,15,14],"#body"),box([3,3,12],[13,15,15],"#body"),
                      box([1,14,2],[15,16,15]),box([3,5,3],[13,13,12],"#dark"),box([3.5,5.5,1.8],[12.5,12.5,2.8],"#dark","#front"),
                      box([2.9,7,1.2],[3.5,11,1.8],"#copper"),box([11,7,1.2],[12,8,1.8],"#steel")]
                e += fins(3,4,14,height=8,count=6)
            elif name=="charger":
                e += [box([2,3.5,3],[14,6,12],"#body"),box([3,6,4],[13,7,11],"#dark"),
                      box([3,6,11],[5,15,14],"#steel"),box([11,6,11],[13,15,14],"#steel"),box([3,14,11],[13,16,14],"#coil"),
                      box([6,7,11.5],[10,13,13],"#coil"),box([6,5.7,1],[10,6.7,4],"#copper")]+control(3.5)
            elif name in ("alloy_furnace","arc_furnace"):
                e += rails()+cylinder(10,4,8 if name=="arc_furnace" else 12,"#body")
                for y in ((4,6,8) if name=="arc_furnace" else (4,6,8,10)): e += cylinder(11,y,y+0.8,"#coil")
                e += [box([4,4,3],[12,5,13],"#dark")]
                if name=="arc_furnace":
                    e += [box([1,14,3],[15,16,13]),box([2,3,12],[14,13,15],"#body")]
                    for x in (4,8,12): e += [box([x-1,12,6],[x+1,15,10],"#glass")]
                    e += [box([4,8,4],[12,8.2,12],"#melt",glow=10 if active else 0)]
                    extra["melt"] = pf("block/industrial_heat_on" if active else "block/industrial_dark")
                else: e += [box([4,12,4],[12,13,12]),box([7,13,6],[9,14,10],"#dark")]
                e += control(3.7)
            elif name in ("wire_drawer","rolling_mill"):
                e += [box([1,3,1],[4,15,15],"#body"),box([12,3,1],[15,15,15],"#body"),box([4,3.5,11],[12,11,14],"#body"),
                      box([0.8,13,1],[4.2,14,15]),box([11.8,13,1],[15.2,14,15]),box([4,6,0.8],[12,6.6,5],"#steel")]
                if name=="wire_drawer":
                    e += [box([6,7,3],[10,11,4.5],"#dark"),box([7.5,8.5,0.2],[8.5,9.5,4],"#copper"),box([6,9,13.5],[10,10,15.7],"#copper")]
                e += [box([1.4,4,0.6],[3.6,10,1],"#dark","#front"),box([12.8,4,0.6],[14,5.3,1],"#lamp",glow=8)]
            elif name=="ore_washer":
                e += [box([1,3,4],[3,15,14],"#body"),box([13,3,4],[15,15,14],"#body"),box([3,3,12],[13,15,15],"#body")]+front_ring()
                e += [box([4,13,5],[12,16,12],"#glass"),box([13,5,13],[16,7,15],"#copper")]
                e += [box([2.5,3.1,0.6],[13.5,4.8,1],"#dark","#front")]
            elif name=="diesel_generator":
                e += rails()+[box([3,4,3],[13,8,13],"#body"),box([4,8,6],[12,12,12],"#dark")]
                for x in (4,8,12): e += cylinder(3,8,13,"#body",dx=x-8,dz=1)
                e += [box([2,12,4],[3.5,15,13],"#dark"),box([2,14,3],[7,15.5,4.5],"#dark"),box([3,3,13],[13,13,14],"#dark")]+fins(3,4,13.8)
                e += [box([1.5,4,0.7],[4,9,1.5],"#dark","#front"),box([12.5,4,0.7],[14,5.5,1.5],"#lamp",glow=8)]
            elif name=="battery_bank":
                e += rails()+[box([1,14,1],[15,15,15]),box([1,3,13.5],[15,14,15],"#body")]
                for x in (4,8,12):
                    for z in (5,10):
                        e += cylinder(3.1,3.5,12.5,"#body",dx=x-8,dz=z-8)
                        e += [box([x-1,12.5,z-1],[x+1,13,z+1],"#dark"),box([x-0.5,13,z-0.5],[x+0.5,14,z+0.5],"#copper")]
                e += [box([3,13.7,4.5],[13,14.3,5.5],"#copper"),box([3,13.7,9.5],[13,14.3,10.5],"#copper")]+control(4)
            elif name=="power_meter":
                extra["front"] = pf("block/power_meter_front")
                e = [box([3,0,4],[13,2,12]),box([7,2,6],[9,5,10],"#dark"),box([1,5,3],[15,16,11],"#body"),box([2,6,1.7],[14,15,3],"#dark","#front")]
                e += [box([x,2,4],[x+1.5,5,6],"#copper") for x in (3,11.5)]
            elif name in ("hydro_turbine","synchronous_condenser"):
                extra["body"] = pf("block/"+name+("_side"))
                e += front_ring()+[box([2,4,3],[14,14,14],"#body"),box([3,3.5,12],[13,15,14])]+fins(3,5,13.8,height=8,count=6)
                e += [box([3,3,0.2],[13,4,3],"#copper"),box([3,14,0.2],[13,15,3],"#steel")]
                if name=="hydro_turbine": e += [box([0,6,5],[2,10,11],"#dark"),box([14,6,5],[16,10,11],"#dark")]
            elif name=="electrolyzer":
                e += rails()+[box([1,14,1],[15,16,15],"#body")]
                for x in (4.5,11.5):
                    e += cylinder(5.5,4,13,"#glass",dx=x-8)
                    e += cylinder(5.8,3.5,4.5,"#steel",dx=x-8)+cylinder(5.8,12,13,"#steel",dx=x-8)
                    e += [box([x-0.3,4,7.7],[x+0.3,12,8.3],"#dark")]
                e += [box([2,8,13],[14,10,15],"#copper")]+control(3.6)
            elif name=="fuel_cell":
                e += rails()+[box([2,3.5,3],[4,14,14],"#body"),box([12,3.5,3],[14,14,14],"#body")]
                for x in range(4,12): e += [box([x,4,4],[x+0.6,13,13],"#steel" if x%2 else "#dark")]
                e += [box([3,13.5,4],[13,14.5,5],"#copper"),box([3,13.5,12],[13,14.5,13],"#copper")]+control(4)
            elif name=="electric_pump":
                e += cylinder(9,3.5,11,"#body")+cylinder(10,10,12,"#steel")
                e += [box([6,5,0],[10,9,4],"#copper"),box([6,5,12],[10,9,16],"#copper"),box([4,12,4],[12,15,12],"#dark")]
                e += [box([x,12,4],[x+0.5,15,12]) for x in range(4,13,2)]
            elif name=="geothermal_generator":
                e += cylinder(10,3,14,"#body")+cylinder(11,4,5,"#steel")+cylinder(11,12,13,"#steel")
                for x in (1.5,13): e += [box([x,3.5,3],[x+1.5,12,4.5],"#copper"),box([x,3.5,11.5],[x+1.5,12,13],"#copper")]
                e += control(5)
                extra["body"] = pf("block/geothermal_generator_side")
            elif name=="thermoelectric_generator":
                e += [box([3,4,4],[13,13,12],"#dark")]
                for x in range(1,15,2): e += [box([x,4,1],[x+0.6,14,15],"#steel")]
                e += [box([4,5,0],[12,12,1],"#copper"),box([4,5,15],[12,12,16],"#glass")]+control(3.5)
            elif name.startswith("ender_"):
                extra["body"] = pf("block/"+name+"_side")
                e += rails()+[box([1,14,1],[15,16,15],"#body"),box([2,3.5,12],[14,14,15],"#body")]
                e += [box([2,5,3],[4,12,5],"#coil"),box([12,5,3],[14,12,5],"#coil"),box([4,4,3],[12,5,5],"#coil"),box([4,12,3],[12,13,5],"#coil")]
                e += [box([3,3.5,0.7],[13,4.5,2],"#dark")]
            save(name,e,extra,suffix,active)
        states(name,pf("block/"+name),pf("block/"+name+"_on")); item(name)
    # Coal genset: keep its animated rear rotor, with a raised firebox, service door and heat fins.
    for active, suffix in ((False,""),(True,"_on")):
        e=feet()+[box([1,3.5,1],[15,15,13],"#body"),box([3,5,0.4],[13,12,1.2],"#dark","#front"),
                  box([2.5,7,0],[3.5,10,0.6],"#copper"),box([11.5,7.5,0],[12.5,8.5,0.6],"#steel")]
        for x in (0.3,15):
            e += [box([x,y,3],[x+0.7,y+0.7,12]) for y in (5,7,9,11,13)]
        e += [box([0,0,13],[16,2,16]),box([0,14,13],[16,16,16]),box([0,2,13],[2,14,16]),box([14,2,13],[16,14,16]),
              box([4,15,4],[12,16,11],"#dark"),box([2,4,12],[3,14,13],"#copper")]
        save("coal_turbine",e,{"body":pf("block/coal_turbine_side")},suffix,active)
    # Tilted photovoltaic module, feet and underside wiring. Retains its original six-pixel collision height.
    panel = [box([2,0,2],[4,3,14]),box([12,0,2],[14,3,14]),box([5,1,5],[11,2,11],"#dark")]
    panel += [box([0,3,1],[16,4,15],"#steel",rotation={"origin":[8,3,8],"axis":"x","angle":-22.5}),
              box([0.5,4,1.5],[15.5,4.1,14.5],"#solar",rotation={"origin":[8,3,8],"axis":"x","angle":-22.5})]
    save("solar_panel",panel,{"solar":pf("block/solar_panel_top")}); states("solar_panel",pf("block/solar_panel"),pf("block/solar_panel"));item("solar_panel")
    # Transformer: laminated iron core, copper windings, ceramic terminals and distinct HV/LV ends.
    e = feet()+[box([3,4,3],[5,14,13],"#dark"),box([11,4,3],[13,14,13],"#dark"),box([3,13,3],[13,15,13],"#dark")]
    for x in (5.5,10.5):
        e += cylinder(4.5,4,13,"#coil",dx=x-8)
        e += [box([x-0.6,13,x-0.6],[x+0.6,16,x+0.6],"#glass")]
    e += [box([3,4,0.5],[13,11,2],"#steel","#secondary"),box([3,4,14],[13,11,15.5],"#primary")]
    save("transformer",e,{"secondary":pf("block/transformer_secondary"),"primary":pf("block/transformer_primary")})
    states("transformer",pf("block/transformer"),pf("block/transformer"));item("transformer")
    for closed in (True,False):
        e = feet()+[box([2,3.5,5],[14,5,14],"#dark")]
        for x in (4,8,12):
            e += [box([x-0.8,4,5],[x+0.8,8,7],"#glass"),box([x-0.8,4,11],[x+0.8,8,13],"#glass"),
                  box([x-0.4,8,6],[x+0.4,8.8,12],"#copper",rotation={"origin":[x,8,12],"axis":"x","angle":0 if closed else 45})]
        e += [box([2,8.8,5.5],[14,9.5,7],"#dark",rotation={"origin":[8,8,12],"axis":"x","angle":0 if closed else 45})]
        save("circuit_breaker",e,suffix="_closed" if closed else "_open")
    for active,suffix in ((False,""),(True,"_on")):
        e=feet()+[box([2,3.5,2],[14,5,14],"#body"),box([3,5,9],[13,15,14],"#steel"),box([4,6,8.5],[12,14,9],"#dark","#front"),
                  box([3,5,2],[13,6,8],"#dark")]
        for x in (4,6,8,10):
            for z in (3,5,7): e += [box([x,6,z],[x+1.2,6.4,z+0.9],"#steel")]
        save("scada_terminal",e,{"body":pf("block/scada_terminal_side")},suffix,active)
    states("scada_terminal",pf("block/scada_terminal"),pf("block/scada_terminal_on"));item("scada_terminal")
    # Keep the existing fluid renderer and add physical caps and corner pillars around its glass shell.
    tank=[box([1,0,1],[15,2,15]),box([1,14,1],[15,16,15]),box([1.5,2,1.5],[14.5,14,14.5],"#tank")]
    tank += [box([x,2,z],[x+1,14,z+1]) for x in (1,14) for z in (1,14)]
    save("fluid_tank",tank,{"tank":pf("block/fluid_tank_side")})
    path=asset("models","block","fluid_tank.json")
    import json
    with open(path) as f: model=json.load(f)
    model["render_type"]="minecraft:translucent";write(path,model)
    for active,suffix in ((False,""),(True,"_on")):
        lamp=[box([1,13,1],[15,15,15],"#housing"),box([1.5,12,1.5],[14.5,13,14.5],"#led",glow=15 if active else 0)]
        lamp += [box([x,15,2],[x+0.6,16,14]) for x in range(2,15,2)]
        save("grow_lamp",lamp,{"housing":pf("block/grow_lamp_housing"),"led":pf("block/grow_lamp_led"+suffix)},suffix,active)
        pad=[box([0,0,0],[16,1.5,16],"#padside"),box([0.5,1.5,0.5],[15.5,2,15.5],"#padtop",glow=10 if active else 0)]
        pad += [box([x,1.5,z],[x+1,2.3,z+1],"#steel") for x in (1,14) for z in (1,14)]
        save("charging_pad",pad,{"padside":pf("block/charging_pad_side"),"padtop":pf("block/charging_pad_top"+suffix)},suffix,active)
