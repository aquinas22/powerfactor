"""Modular tank shells and machined pipe joints. Called after the machine art pass."""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from machine_finish import BASE, box


def model(g,name,elements,textures,render='minecraft:cutout'):
    used={f['texture'][1:] for e in elements for f in e['faces'].values()}
    g['write'](g['asset']('models','block',name+'.json'),{'parent':'minecraft:block/block','render_type':render,
        'textures':{k:v for k,v in textures.items() if k in used or k=='particle'},'elements':elements})


def generate_models(g):
    pf,asset,write=(g[k] for k in ('pf','asset','write'))
    textures={k:pf('block/finish_'+k) for k in ('steel','dark','copper','chrome')}
    textures.update(glass=pf('block/reservoir_glass'),gauge=pf('block/reservoir_scale'),particle=pf('block/finish_steel'))
    multipart=[];all_item=[]
    def part(name,elements,when,render='minecraft:cutout'):
        model(g,'reservoir_'+name,elements,textures,render)
        multipart.append({'when':when,'apply':{'model':pf('block/reservoir_'+name)}})
        all_item.extend(elements)
    bounds={'north':([0,0,.4],[16,16,.55]),'south':([0,0,15.45],[16,16,15.6]),
            'west':([.4,0,0],[.55,16,16]),'east':([15.45,0,0],[15.6,16,16])}
    for face,(a,b) in bounds.items():
        pane=box(a,b,'glass')
        # Each exposed pane gets the whole subtle reflection artwork, without opaque internal walls.
        for f in pane['faces'].values(): f['uv']=[0,0,16,16]
        part('glass_'+face,[pane],{face:'false'},'minecraft:translucent')
    for face,(a,b) in {'up':([0,15.35,0],[16,15.5,16]),'down':([0,0,0],[16,.8,16])}.items():
        part(face,[box(a,b,'glass' if face=='up' else 'steel')],{face:'false'},'minecraft:translucent' if face=='up' else 'minecraft:cutout')
    for n,(x,z,side1,side2) in enumerate([(0,0,'north','west'),(15,0,'north','east'),(0,15,'south','west'),(15,15,'south','east')]):
        elements=[box([x,0,z],[x+1,16,z+1],'steel'),box([x+.15,2,z+.15],[x+.85,14,z+.85],'chrome')]
        part('corner_'+str(n),elements,{side1:'false',side2:'false'})
    for face,(a,b) in {'north':([0,0,0],[16,.8,.9]),'south':([0,0,15.1],[16,.8,16]),
                       'west':([0,0,0],[.9,.8,16]),'east':([15.1,0,0],[16,.8,16])}.items():
        part('bottom_'+face,[box(a,b)],{face:'false','down':'false'})
        a=[a[0],15.2,a[2]];b=[b[0],16,b[2]]
        part('top_'+face,[box(a,b)],{face:'false','up':'false'})
    part('gauge',[box([12.3,2.5,.15],[14.2,13.5,.45],'dark','gauge'),
                  box([2,6,.0],[4.5,9,.6],'copper'),box([2.5,6.5,-.15],[4,8.5,.05],'dark')],{'north':'false'})
    write(asset('blockstates','fluid_tank.json'),{'multipart':multipart})
    model(g,'fluid_tank',all_item,textures,'minecraft:translucent')
    # These are the same arm states used by logistics, so filters and traffic keep their meaning.
    directions={'up':{},'down':{'x':180},'north':{'x':90},'south':{'x':90,'y':180},'west':{'x':90,'y':270},'east':{'x':90,'y':90}}
    for name,color in [('item_pipe','gold'),('fluid_pipe','blue'),('steam_pipe','cream')]:
        tex=dict(textures,glass=pf('block/pipe_window'),accent=pf('block/finish_paint_'+color),
                 extract=pf('block/pipe_extract_badge'),insert=pf('block/pipe_insert_badge'))
        core=[box([5,5,5],[11,11,11],'glass')]
        for x in (4.8,10.3):
            for y in (4.8,10.3):
                for z in (4.8,10.3):core.append(box([x,y,z],[x+.9,y+.9,z+.9],'steel'))
        arm=[box([5,11,5],[11,16,11],'glass')]
        # Four narrow ribs keep the moving fluid and transported items visible.
        for x in (4.9,10.5):
            for z in (4.9,10.5): arm.append(box([x,11,z],[x+.6,16,z+.6],'steel'))
        for y in (12.1,15):
            arm.extend([box([4.5,y,4.5],[11.5,y+.65,5.15],'accent'),box([4.5,y,10.85],[11.5,y+.65,11.5],'accent'),
                        box([4.5,y,5.15],[5.15,y+.65,10.85],'accent'),box([10.85,y,5.15],[11.5,y+.65,10.85],'accent')])
        model(g,name+'_core',core,tex)
        for kind in ('arm','insert','extract'):
            e=list(arm)
            if kind!='arm':
                for x in (4,11):
                    for z in (4,11):e.append(box([x,14.7,z],[x+1,16,z+1],'chrome'))
                e += [box([5.5,12.8,4.1],[10.5,14.8,4.8],'dark',kind)]
                if kind=='extract':
                    e += [box([3.8,11.7,6],[4.7,14.4,10],'dark'),box([11.3,11.7,6],[12.2,14.4,10],'dark')]
            model(g,name+'_'+kind,e,tex)
        mp=[{'apply':{'model':pf('block/'+name+'_core')}}]
        for direction,rotation in directions.items():
            for state,suffix in [('pipe','arm'),('insert','insert'),('extract','extract')]:
                mp.append({'when':{direction:state},'apply':{'model':pf('block/'+name+'_'+suffix),**rotation}})
        write(asset('blockstates',name+'.json'),{'multipart':mp})
        # A full glass section is an informative inventory icon.
        bottom=json.loads(json.dumps(arm))
        for e in bottom:
            a,b=e['from'],e['to'];e['from']=[a[0],16-b[1],a[2]];e['to']=[b[0],16-a[1],b[2]]
        model(g,name+'_inventory',core+arm+bottom,tex)
        g['item_def'](name,pf('block/'+name+'_inventory'))


def generate_textures():
    path=BASE/'textures/block'
    for kind in ('reservoir_glass','pipe_window'):
        im=Image.new('RGBA',(32,32),(0,0,0,0));d=ImageDraw.Draw(im)
        # Clear center with restrained edge reflections; neither conceals the fluid.
        if kind=='reservoir_glass':
            d.rectangle((0,0,31,31),fill=(107,178,188,10))
            d.line((4,7,11,0),fill=(176,223,221,58),width=1);d.line((22,31,31,22),fill=(154,208,208,45),width=1)
        else:
            d.rectangle((0,0,31,31),outline=(143,201,198,150));d.line((4,15,15,4),fill=(186,223,216,80));d.line((17,28,28,17),fill=(146,199,196,70))
        im.save(path/(kind+'.png'))
    im=Image.new('RGBA',(32,32),'#223A45');d=ImageDraw.Draw(im)
    d.rectangle((1,1,30,30),outline='#8FAFAF')
    for y in range(3,30,4): d.line((8,y,25,y),fill='#CEE1D6')
    d.rectangle((3,3,5,28),fill='#81D2CF');im.save(path/'reservoir_scale.png')
    for kind,color,reverse in [('extract','#E6B970',False),('insert','#79BDDC',True)]:
        im=Image.new('RGBA',(32,32),'#1A303B');d=ImageDraw.Draw(im)
        d.rectangle((1,1,30,30),outline='#8FA9AD');d.line((6,16,25,16),fill=color,width=3)
        d.polygon([(7,8),(7,24),(16,16)] if reverse else [(24,8),(24,24),(15,16)],fill=color)
        im.save(path/('pipe_'+kind+'_badge.png'))


def generate_lang(lang):
    lang.update({'message.powerfactor.tank.reservoir':'%s · %s / %s mB · %s joined tanks',
                 'message.powerfactor.tank.reservoir_empty':'Empty reservoir · %3$s mB · %4$s joined tanks',
                 'tooltip.powerfactor.reservoir.contents':'%s · %s / %s mB',
                 'tooltip.powerfactor.reservoir.empty':'Empty · %s mB per tank',
                 'tooltip.powerfactor.reservoir.join':'Place touching tanks to join their storage',
                 'tooltip.powerfactor.reservoir.keep':'Breaking a tank keeps its share of fluid in the item'})


if __name__=='__main__':
    import gen_data
    generate_textures();generate_models(vars(gen_data))
    path=BASE/'lang/en_us.json';lang=json.loads(path.read_text());generate_lang(lang)
    path.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n')
