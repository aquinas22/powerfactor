"""Final art pass for the machine family. Geometry remains compatible with existing tooling."""
import json, re
from pathlib import Path
from PIL import Image, ImageDraw

BASE = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/powerfactor'
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')
PAINT = {'blue': '#426777', 'green': '#427569', 'gold': '#A18448', 'cream': '#AFB6A7'}


def uv(a, b, side):
    """World-scale grain on small pieces, rather than squeezing a whole sheet onto each bolt."""
    dx, dy, dz = (min(16, max(.1, b[i] - a[i])) for i in range(3))
    return [0, 0, dx, dz] if side in ('up', 'down') else [0, 0, dz if side in ('east', 'west') else dx, dy]


def box(a, b, material='steel', face=None, glow=0):
    faces = {side: {'texture': '#' + material, 'uv': uv(a, b, side)} for side in SIDES}
    if face:
        faces['north'] = {'texture': '#' + face, 'uv': [0, 0, 16, 16]}
    return {'from': a, 'to': b, 'faces': faces, **({'light_emission': glow} if glow else {})}


def feet():
    return [box([x, 0, z], [x+2, 2, z+2], 'rubber') for x in (1, 13) for z in (1, 13)] + [box([1, 2, 1], [15, 3.5, 15])]


def collar(x, y, z, width=2):
    return [box([x-.25,y,z-.25], [x+width+.25,y+.6,z+width+.25], 'steel')]


def panel(y=3.6, x=3, width=10):
    return [box([x,y,.65], [x+width,y+2.7,1.2], 'dark', 'display'),
            box([x+width+.4,y+.5,.5], [x+width+1.1,y+1.2,.9], 'lamp')]


def service(x=2, z=14.1):
    return [box([x,5,z], [x+4,11,z+.45], 'dark'), box([x+3,7,z+.45], [x+3.4,9,z+.7], 'copper')]


def ports(y=6):
    out=[]
    for x in (0, 14.8):
        out += [box([x,y,7], [x+1.2,y+3,10], 'steel'), box([x-.1 if x else x,y+.65,7.65], [x+1.3,y+2.35,9.35], 'dark')]
    return out


def generate_models(g):
    write, asset, pf = (g[k] for k in ('write', 'asset', 'pf'))
    # Retain the individual mechanisms, replace their generic stretched stock material.
    directory = BASE/'models/block'
    existing = ['electric_furnace','charger','alloy_furnace','arc_furnace','wire_drawer','rolling_mill','ore_washer',
                'diesel_generator','battery_bank','power_meter','hydro_turbine','synchronous_condenser','electrolyzer','fuel_cell',
                'electric_pump','geothermal_generator','thermoelectric_generator','ender_transmitter','ender_receiver','coal_turbine',
                'transformer','scada_terminal','grow_lamp','charging_pad','fluid_tank']
    for name in existing:
        for suffix in ('','_on'):
            path=directory/(name+suffix+'.json')
            if not path.exists(): continue
            model=json.loads(path.read_text())
            if name in ('wire_drawer','rolling_mill','ore_washer','fuel_cell','diesel_generator'):
                model['textures']['body']=pf('block/finish_paint_'+('gold' if name in ('wire_drawer','rolling_mill') else 'blue'))
            if name=='transformer': model['textures']['glass']=pf('block/finish_ceramic')
            for key, tile in [('steel','steel'),('dark','dark'),('copper','copper'),('coil','coil')]:
                if key in model['textures']: model['textures'][key]=pf('block/finish_'+tile)
            for element in model.get('elements',[]):
                for side, face in element['faces'].items():
                    if face['texture'] in ('#steel','#dark','#copper','#coil') or (face['texture']=='#body' and model['textures'].get('body','').split('/')[-1].startswith('finish_')):
                        face['uv']=uv(element['from'],element['to'],side)
                if suffix != '_on' and any(face['texture']=='#lamp' for face in element['faces'].values()):
                    element.pop('light_emission',None)
            # Quiet painted trim, raised side service hatch and exposed utility sockets.
            if name in ('electric_furnace','alloy_furnace','arc_furnace','wire_drawer','rolling_mill','ore_washer','diesel_generator','fuel_cell'):
                color='cream' if 'furnace' in name else 'gold' if name in ('wire_drawer','rolling_mill') else 'blue'
                model['textures']['trim']=pf('block/finish_paint_'+color)
                for detail in [box([1,3.45,2],[15,4.1,3],'trim'), box([1.2,6,14.9],[4.6,10,15.2],'dark')]:
                    if detail not in model['elements']: model['elements'].append(detail)
            if name in ('alloy_furnace','charger','fuel_cell'):
                # The rear impeller has a bolted mount and a cage inside the block footprint.
                cage=[box([3,6,13.4],[13,15.6,14.3],'dark'),box([2.8,5.8,15.7],[3.5,15.8,16],'steel'),
                      box([12.5,5.8,15.7],[13.2,15.8,16],'steel'),box([3.5,5.8,15.7],[12.5,6.5,16],'steel'),
                      box([3.5,15.1,15.7],[12.5,15.8,16],'steel')]
                for detail in cage:
                    if detail not in model['elements']: model['elements'].append(detail)
            write(path, model)

    colors = {'mechanical_press':'gold','mixer':'green','centrifuge':'blue','sawmill':'gold','laser_engraver':'cream'}
    for name,color in colors.items():
        for active in (False,True):
            suffix='_on' if active else ''
            e=feet()
            if name=='mechanical_press':
                for x in (1.5,12.5):
                    for z in (2,12):
                        e += [box([x,3.5,z],[x+2,15.5,z+2]), box([x+.4,5,z+.4],[x+1.6,14.8,z+1.6],'chrome')]+collar(x,4,z)
                e += [box([1,15.5,1],[15,18,15],'paint'), box([3,5,4],[13,6,12],'dark'), box([4,6,5],[12,6.5,11],'chrome'),
                      box([4,14.8,4],[12,15.5,12],'dark'), box([6,16,14.8],[10,17.3,15.5],'copper')]
                e += panel()+service(3)+ports(7)
            elif name=='mixer':
                # Octagonal open bowl: sides do not occlude the stirring paddles.
                e += g['octagon'](12,3.5,4.7,'#steel')
                e += [box([2,4.7,2],[14,10,3.2],'paint'),box([2,4.7,12.8],[14,10,14],'paint'),
                      box([2,4.7,3.2],[3.2,10,12.8],'paint'),box([12.8,4.7,3.2],[14,10,12.8],'paint')]
                for x in (1,13):
                    e += [box([x,3.5,6],[x+2,15.4,10]),box([x-.15,14,5.8],[x+2.15,14.7,10.2],'copper')]
                e += [box([1,15.2,6],[15,17,10],'paint'),box([5,16.8,5.5],[11,18,10.5],'dark')]
                e += panel()+service()+[box([13,5,11],[16,7,13],'copper')]
            elif name=='centrifuge':
                e += [box([2,3.5,2],[14,5.5,14],'paint')]
                # Segmented safety rim keeps the rotor and buckets visible from above.
                e += [box([1.5,5.5,2],[3,13,14],'paint'),box([13,5.5,2],[14.5,13,14],'paint'),
                      box([3,5.5,12.5],[13,13,14],'paint'),box([3,5.5,2],[13,7.5,3.5],'paint'),
                      box([3,12.3,2],[13,13,3],'chrome'),box([6.5,5.5,6.5],[9.5,8,9.5],'dark')]
                for x in (1.4,13): e += [box([x,13,2],[x+1.6,13.6,14],'chrome')]
                e += panel(3.8)+service()+ports(8)
            elif name=='sawmill':
                # Separate infeed/outfeed tables leave the circular blade exposed.
                e += [box([2,3.5,2],[5.5,9.2,14],'paint'),box([10.5,3.5,2],[14,9.2,14],'paint'),
                      box([1,9.2,1],[5.8,10,15],'chrome'),box([10.2,9.2,1],[15,10,15],'chrome'),
                      box([1.5,10,1],[2.2,11.3,15],'dark'),box([13.8,10,1],[14.5,11.3,15],'dark'),
                      box([4,3.5,6],[12,5.5,10],'dark'),box([10.5,5.5,6],[14.5,8.5,10],'copper')]
                for z in (2,6,10,14):
                    e += [box([2.2,10,z],[5.5,10.4,z+.5]), box([10.5,10,z],[13.8,10.4,z+.5])]
                e += panel(4)+service()+ports(5)
            else: # The moving laser and wafer retain the original bed height of 11/16.
                e += [box([1,3.5,1],[15,10.6,15],'paint'),box([2.5,10.6,3],[13.5,11,13],'dark')]
                for x in (.5,14):
                    for z in (1,13.5): e += [box([x,10.6,z],[x+1.5,16,z+1.5],'chrome')]
                    e += [box([x,15,2.5],[x+1.5,16,13.5],'dark'),box([x+.25,16,2.5],[x+1.25,16.3,13.5],'chrome')]
                e += panel(5)+service()+[box([2,3.5,.4],[14,4.2,1],'copper')]
            textures={k:pf('block/finish_'+k) for k in ('steel','dark','copper','chrome','rubber')}
            textures.update(paint=pf('block/finish_paint_'+color),display=pf('block/finish_display'+suffix),
                            lamp=pf('block/industrial_lamp'+suffix),particle=pf('block/finish_paint_'+color))
            if active:
                for element in e:
                    if any(f['texture']=='#lamp' for f in element['faces'].values()): element['light_emission']=8
            used={f['texture'][1:] for element in e for f in element['faces'].values()}
            textures={k:v for k,v in textures.items() if k in used or k=='particle'}
            write(asset('models','block',name+suffix+'.json'),{'parent':'minecraft:block/block','render_type':'minecraft:cutout',
                'textures':textures,'elements':e})


def generate_textures():
    """Authored pixel materials: painted enamel, machine marks, rubber and metal at 32 px."""
    path=BASE/'textures/block'
    path.mkdir(parents=True,exist_ok=True)
    palette={'ceramic':'#D2D4BE','steel':'#8DABA9','chrome':'#B7C8C5','dark':'#243B48','copper':'#BC8C58','coil':'#8B6747','rubber':'#192A31'}
    palette.update({'paint_'+k:v for k,v in PAINT.items()})
    for kind,color in palette.items():
        base=tuple(bytes.fromhex(color[1:]));im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
        for y in range(32):
            grain=(((y*19+7)%9)-4)*(.65 if kind.startswith('paint') else 1.2)
            for x in range(32):
                f=grain+(((x*3+y*7)%5)-2)*.35
                im.putpixel((x,y),tuple(max(0,min(255,round(c+f))) for c in base)+(255,))
        if kind=='coil':
            for y in range(0,32,4): d.line((0,y,31,y),fill='#D8B275');d.line((0,y+3,31,y+3),fill='#654A36')
        elif kind=='rubber':
            for y in range(1,32,4): d.line((0,y,31,y),fill='#2D4148')
        elif kind.startswith('paint'):
            d.line((0,0,31,0),fill=tuple(min(255,c+32) for c in base));d.line((0,31,31,31),fill=tuple(max(0,c-24) for c in base))
        elif kind=='chrome':
            for y in (3,4,19): d.line((0,y,31,y),fill='#D9E1D8')
        im.save(path/('finish_'+kind+'.png'))
    # Reserved atlas space for the raised ram collars, spindle hubs and bucket caps.
    atlas=BASE/'textures/entity/workshop.png'
    with Image.open(atlas) as original: im=original.convert('RGBA')
    d=ImageDraw.Draw(im)
    d.rectangle((32,40,63,63),fill='#86A5A5')
    for y in range(41,64,4): d.line((32,y,63,y),fill='#C6D5CA')
    im.save(atlas)
    for active in (False,True):
        im=Image.new('RGBA',(32,32),'#1B2D37');d=ImageDraw.Draw(im)
        d.rectangle((1,1,30,30),outline='#708F96');d.rectangle((3,3,20,18),fill='#0C1F27')
        d.line((5,6,18,6),fill='#73DBC0' if active else '#46686C')
        for x,h in ((5,3),(8,7),(11,5),(14,9),(17,6)):
            d.rectangle((x,16-h,x+1,16),fill='#8BE2CA' if active else '#45666B')
        d.rectangle((24,4,27,7),fill='#81DEC0' if active else '#496B6B');d.rectangle((24,12,27,15),fill='#E4B36C')
        for x in (6,13,23):
            d.ellipse((x,23,x+4,27),fill='#111F27',outline='#A5B8B5');d.point((x+2,24),fill='#D6E0D7')
        im.save(path/('finish_display'+('_on' if active else '')+'.png'))


def generate_lang(lang):
    # The familiar status words remain, but scan as sentences rather than a wall of capitals.
    for key,value in list(lang.items()):
        if key.startswith('gui.powerfactor.') and any(s in key for s in ('.work.','.panel.','.boiler.','.turbine.','.steam_turbine.','.cooling.','.geothermal.','.thermoelectric.','.rain.','.fuel_cell.','.battery.','.autocap.','.pumpjack.','.refinery.')):
            if not key.endswith(('.help','.desc','_help')) and value.upper()==value and len(value)>3:
                lang[key]=re.sub(r'\b(pf|ee|fe|rpm)\b', lambda match: match.group().upper(), value[:1]+value[1:].lower())
    lang.update({
        'gui.powerfactor.turbine.online':'Online',
        'gui.powerfactor.console.cycle':'Cycle %s%%', 'gui.powerfactor.console.tank':'Tank %s%%',
        'gui.powerfactor.console.progress':'Cycle progress: %s%%',
        'gui.powerfactor.console.remaining':'About %s s remaining at this speed',
        'gui.powerfactor.console.power':'Real: %s FE/t · Reactive: %s EE/t',
        'gui.powerfactor.console.supply':'Supply: %s V · %s Hz',
        'gui.powerfactor.console.slot.input':'Input · Place recipe ingredients here',
        'gui.powerfactor.console.slot.output':'Output · Collect the finished product',
        'gui.powerfactor.console.slot.tool':'Tooling · Fit the matching die, mask or electrode',
        'gui.powerfactor.console.trip_help':'Restore a stable supply, then allow the drive to recover. A surge upgrade tolerates lower voltage.',
        'gui.powerfactor.panel.motor_running':'Running at %s%% speed',
        'gui.powerfactor.panel.heater_running':'Heating at %s%% speed',
    })


if __name__=='__main__':
    generate_textures()
    import gen_data
    generate_models(vars(gen_data))
    path=BASE/'lang/en_us.json';lang=json.loads(path.read_text());generate_lang(lang)
    path.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n')
    print('Machine finishes, workshop models and console labels generated.')
