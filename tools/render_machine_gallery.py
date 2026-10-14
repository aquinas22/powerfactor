"""Render the actual shipped block JSON and baked animated parts with a small CPU rasterizer.
This is an asset review sheet, not an in-game screenshot. Run the native model validator first.
"""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'powerfactor/src/main/resources/assets/powerfactor'
PARTS=json.loads((ROOT/'build/release/machine-parts.json').read_text())
OUT=ROOT/'powerfactor/docs/previews/machine-finish-gallery.png'
CACHE={}


def texture(path):
    if path not in CACHE:
        p=ASSETS/'textures'/(path.split(':',1)[1]+'.png')
        im=Image.open(p).convert('RGBA')
        # Animated atlas: show its first frame, as Minecraft does at load.
        if p.with_suffix('.png.mcmeta').exists(): im=im.crop((0,0,im.width,im.width))
        CACHE[path]=im
    return CACHE[path]


def dot(a,b): return sum(x*y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def normalized(a):
    length=math.sqrt(dot(a,a))
    return tuple(x/length for x in a) if length else (0,0,0)
def rotation(v,axis,angle):
    a=math.radians(angle);c,s=math.cos(a),math.sin(a);x,y,z=v
    return {'x':(x,c*y-s*z,s*y+c*z),'y':(c*x+s*z,y,-s*x+c*z),'z':(c*x-s*y,s*x+c*y,z)}[axis]


def block(name):
    model=json.loads((ASSETS/'models/block'/(name+'.json')).read_text());quads=[]
    for e in model['elements']:
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        points={
            'north':[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)],
            'south':[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)],
            'west':[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)],
            'east':[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)],
            'up':[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
            'down':[(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)]}
        for side,f in e['faces'].items():
            t=f['texture']
            while t.startswith('#'): t=model['textures'][t[1:]]
            vertices=points[side]
            if r:=e.get('rotation'):
                origin=r['origin'];vertices=[tuple(x+y for x,y in zip(rotation(sub(v,origin),r['axis'],r['angle']),origin)) for v in vertices]
                # No current finished models use rescale; other tilted generators retain their shape.
            u0,v0,u1,v1=[v/16 for v in f.get('uv',[0,0,16,16])]
            quads.append((vertices,[[u0,v0],[u1,v0],[u1,v1],[u0,v1]],texture(t),e.get('light_emission',0)))
    return quads


def part(name, xyz, *, flip=False, rotate=None):
    atlas='workshop' if name.startswith('workshop:') else 'machine_motion' if name.startswith('industrial:') else 'laser_head'
    out=[]
    for polygon in PARTS[name]:
        verts=[v[:3] for v in polygon]
        if flip: verts=[(v[0],-v[1],-v[2]) for v in verts]
        if rotate: verts=[rotation(v,*rotate) for v in verts]
        out.append(([tuple(a+b for a,b in zip(v,xyz)) for v in verts],[v[3:] for v in polygon],texture('powerfactor:entity/'+atlas),0))
    return out


def canonical_mechanism(name):
    if name=='mechanical_press': return part('workshop:press',[8,10.1,8])
    if name=='mixer': return part('workshop:paddle',[8,5.44,8])
    if name=='centrifuge': return part('workshop:rotor',[8,10.4,8],rotate=('y',35))
    if name=='sawmill': return part('workshop:saw',[8,10.4,8],rotate=('x',12))
    if name=='laser_engraver': return part('laser:head',[8,14,8],flip=True)+part('laser:bridge',[8,16,8],flip=True)
    if name in ('electric_furnace','alloy_furnace','charger','fuel_cell'):
        return part('industrial:fan',[8,10.08,.64],flip=True,rotate=('z',20))
    if name=='wire_drawer': return part('industrial:wheel',[8,9.76,13.44],flip=True,rotate=('z',10))
    if name=='rolling_mill': return part('industrial:roll',[8,7,11.2],flip=True)+part('industrial:roll',[8,12,11.2],flip=True)
    if name=='ore_washer': return part('industrial:drum',[8,9,13.6],flip=True,rotate=('z',20))
    if name=='arc_furnace': return sum([part('industrial:electrode',[8+i*4,11.2,8],flip=True) for i in (-1,0,1)],[])
    if name=='diesel_generator': return part('industrial:wheel',[8,8.64,13.44],flip=True)+sum([part('industrial:piston',[8+i*3.52,10.88,7.04],flip=True) for i in (-1,0,1)],[])
    return []


def mechanism(name):
    # Renderer geometry is authored toward south; a NORTH-facing block turns it 180 degrees.
    return [([(16-x,y,16-z) for x,y,z in v],uv,tex,glow) for v,uv,tex,glow in canonical_mechanism(name)]


def render(quads, size=256, *, camera=(.48,.46,-.75), center=(8,8,8), scale=10):
    bg=Image.new('RGBA',(size,size),(20,34,43,255));pixels=bg.load()
    depth=[-1e9]*(size*size)
    camera=normalized(camera);right=normalized(cross(camera,(0,1,0)));up=cross(right,camera)
    light=normalized((-.25,.9,-.35))
    for v,uv,tex,glow in quads:
        screen=[]
        for vertex in v:
            p=sub(vertex,center);screen.append((dot(p,right)*scale+size/2,-dot(p,up)*scale+size/2+8,dot(p,camera)))
        normal=normalized(cross(sub(v[1],v[0]),sub(v[2],v[0])))
        shade=min(1,.67+.33*abs(dot(normal,light))+glow*.015)
        colors=tex.load()
        for inds in ((0,1,2),(0,2,3)):
            tri=[screen[i] for i in inds];tuv=[uv[i] for i in inds]
            x0=max(0,math.floor(min(p[0] for p in tri)));x1=min(size-1,math.ceil(max(p[0] for p in tri)))
            y0=max(0,math.floor(min(p[1] for p in tri)));y1=min(size-1,math.ceil(max(p[1] for p in tri)))
            a,b,c=tri;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(den)<1e-8:continue
            for y in range(y0,y1+1):
                for x in range(x0,x1+1):
                    sx,sy=x+.5,y+.5
                    w0=((b[1]-c[1])*(sx-c[0])+(c[0]-b[0])*(sy-c[1]))/den
                    if w0 < -1e-5: continue
                    w1=((c[1]-a[1])*(sx-c[0])+(a[0]-c[0])*(sy-c[1]))/den;w2=1-w0-w1
                    if w1 < -1e-5 or w2 < -1e-5:continue
                    z=w0*a[2]+w1*b[2]+w2*c[2];index=y*size+x
                    if z<=depth[index]:continue
                    tu=w0*tuv[0][0]+w1*tuv[1][0]+w2*tuv[2][0]
                    tv=w0*tuv[0][1]+w1*tuv[1][1]+w2*tuv[2][1]
                    tx=max(0,min(tex.width-1,int(tu*tex.width)));ty=max(0,min(tex.height-1,int(tv*tex.height)))
                    color=colors[tx,ty]
                    if color[3]<=127:continue
                    pixels[x,y]=tuple(int(n*shade) for n in color[:3])+(255,);depth[index]=z
    return bg


def main():
    names=['mechanical_press','mixer','centrifuge','sawmill','laser_engraver','electric_furnace',
           'arc_furnace','alloy_furnace','wire_drawer','rolling_mill','ore_washer','charger',
           'diesel_generator','electrolyzer','fuel_cell','battery_bank','transformer','geothermal_generator']
    sheet=Image.new('RGB',(1592,1060),'#101B23');d=ImageDraw.Draw(sheet)
    font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    title=ImageFont.truetype(font,28);label=ImageFont.truetype(font,16);small=ImageFont.truetype(font,13)
    d.text((24,16),'POWERFACTOR / MACHINE FINISHES',font=title,fill='#ECF1EB')
    d.text((25,55),'Shipped geometry and textures · Baked tooling shown at a fixed phase · Software asset preview',font=small,fill='#A2B2B7')
    for i,name in enumerate(names):
        x=12+(i%6)*262;y=88+(i//6)*316
        sheet.paste(render(block(name)+mechanism(name)),(x,y))
        d.line((x,y+264,x+255,y+264),fill='#52666E')
        d.text((x+8,y+274),name.replace('_',' ').title(),font=label,fill='#ECF1EB')
    OUT.parent.mkdir(parents=True,exist_ok=True);sheet.save(OUT)
    print(OUT)


if __name__=='__main__':main()
