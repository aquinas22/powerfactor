"""Review native Minecraft-posed armor and actual multipart logistics assets with the CPU renderer."""
import json
from PIL import Image,ImageDraw,ImageFont
from render_machine_gallery import ROOT,ASSETS,block,render,rotation,sub,texture

FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
OUT=ROOT/'powerfactor/docs/previews'

def translated(quads,offset):
    return [([tuple(a+b for a,b in zip(v,offset)) for v in verts],uv,tex,glow) for verts,uv,tex,glow in quads]

def multipart(name,state):
    definition=json.loads((ASSETS/'blockstates'/(name+'.json')).read_text());quads=[]
    def matches(condition):
        if 'OR' in condition:return any(matches(c) for c in condition['OR'])
        if 'AND' in condition:return all(matches(c) for c in condition['AND'])
        return all(str(state.get(k,'none')).lower() in str(v).split('|') for k,v in condition.items())
    for entry in definition['multipart']:
        if not matches(entry.get('when',{})):continue
        application=entry['apply'];application=application[0] if isinstance(application,list) else application
        model=application['model'].split('block/',1)[1]
        for vertices,uv,tex,glow in block(model):
            transformed=[]
            for v in vertices:
                p=sub(v,(8,8,8))
                for axis in ['x','y']:p=rotation(p,axis,-application.get(axis,0))
                transformed.append(tuple(n+8 for n in p))
            quads.append((transformed,uv,tex,glow))
    return quads

def armor():
    meshes=json.loads((ROOT/'build/release/armor-parts.json').read_text())
    sheet=Image.new('RGB',(1472,906),'#101B23');d=ImageDraw.Draw(sheet)
    d.text((24,16),'POWERFACTOR / FOUNDRY SUIT',font=ImageFont.truetype(FONT,27),fill='#ECF1EB')
    d.text((24,54),'Native baked and posed geometry · Shipped 256px atlas · Software asset preview',font=ImageFont.truetype(FONT,14),fill='#A2B2B7')
    entries=[('adult_standing','Front three-quarter',(.3,.2,-1)),('adult_standing','Rear power pack',(-.3,.2,1)),
        ('adult_walking','Walking',(.48,.22,-1)),('adult_crouching','Crouching',(.5,.2,-1)),
        ('baby_standing','Baby wearer',(.3,.2,-1)),('baby_walking','Baby walking',(.48,.22,-1)),
        ('baby_crouching','Baby crouching',(.5,.2,-1)),('adult_standing','Side profile',(1,.2,-.15))]
    for i,(name,label,camera) in enumerate(entries):
        quads=[([(8+x,16-y,8+z) for x,y,z,*_ in polygon],[v[3:] for v in polygon],texture('powerfactor:entity/foundry_suit'),0) for polygon in meshes[name]]
        x=12+(i%4)*364;y=88+(i//4)*402
        sheet.paste(render(quads,352,camera=camera,center=(8,7.5,8),scale=8.6),(x,y))
        d.text((x+12,y+365),label,font=ImageFont.truetype(FONT,17),fill='#ECF1EB')
    OUT.mkdir(parents=True,exist_ok=True);path=OUT/'armor-finish-gallery.png';sheet.save(path);print(path)

def tank_grid(cells):
    directions={'west':(-1,0,0),'east':(1,0,0),'up':(0,1,0),'down':(0,-1,0),'north':(0,0,-1),'south':(0,0,1)}
    quads=[]
    for cell in cells:
        state={face:tuple(a+b for a,b in zip(cell,delta)) in cells for face,delta in directions.items()}
        quads+=translated(multipart('fluid_tank',state),tuple(v*16 for v in cell))
    return quads

def logistics():
    sheet=Image.new('RGB',(1244,910),'#101B23');d=ImageDraw.Draw(sheet)
    d.text((24,16),'POWERFACTOR / PIPES AND RESERVOIRS',font=ImageFont.truetype(FONT,27),fill='#ECF1EB')
    d.text((24,54),'Actual multipart shells and couplings · Internal panes disappear on joining · Software asset preview',font=ImageFont.truetype(FONT,13),fill='#A2B2B7')
    examples=[('Item routing',multipart('item_pipe',{'north':'extract','east':'pipe','up':'insert'}),(8,8,8),12),
        ('Fluid routing',multipart('fluid_pipe',{'north':'pipe','east':'insert','west':'extract','up':'pipe'}),(8,8,8),12),
        ('Steam routing',multipart('steam_pipe',{'north':'extract','east':'pipe','up':'insert'}),(8,8,8),12),
        ('Single 32,000 mB cell',tank_grid({(0,0,0)}),(8,8,8),12),
        ('Joined 2 x 2 reservoir',tank_grid({(0,0,0),(1,0,0),(0,1,0),(1,1,0)}),(16,16,8),7.5),
        ('Joined 2 x 2 x 2 reservoir',tank_grid({(x,y,z) for x in range(2) for y in range(2) for z in range(2)}),(16,16,16),6.8)]
    for i,(name,quads,center,scale) in enumerate(examples):
        x=12+(i%3)*410;y=88+(i//3)*402
        sheet.paste(render(quads,398,center=center,scale=scale),(x,y))
        d.text((x+12,y+366),name,font=ImageFont.truetype(FONT,17),fill='#ECF1EB')
    path=OUT/'logistics-finish-gallery.png';sheet.save(path);print(path)

if __name__=='__main__':armor();logistics()
