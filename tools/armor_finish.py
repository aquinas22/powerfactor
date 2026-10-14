"""Original foundry-suit enamel, metal, gasket and visor atlas and equipment icons."""
import json
from PIL import Image,ImageDraw
from machine_finish import BASE


def generate_textures():
    atlas=Image.new('RGBA',(256,256),'#334D5B');d=ImageDraw.Draw(atlas)
    materials=[(0,0,'#304B5A'),(0,64,'#B7C8BF'),(128,0,'#182C35'),(128,64,'#C6A267'),(0,128,'#90B4B4'),(128,128,'#164950'),(0,192,'#527E8A'),(128,192,'#5DCEB6')]
    for x0,y0,hexcolor in materials:
        rgb=tuple(bytes.fromhex(hexcolor[1:]));d.rectangle((x0,y0,x0+127,y0+63),fill=hexcolor)
        for y in range(y0,y0+64):
            for x in range(x0,x0+128):
                f=((y*11+x*3)%7-3)*.6
                atlas.putpixel((x,y),tuple(max(0,min(255,round(c+f))) for c in rgb)+(255,))
        for y in (y0,y0+15,y0+31,y0+47,y0+63):
            d.line((x0,y,x0+127,y),fill=tuple(max(0,c-24) for c in rgb));d.line((x0,y+1,x0+127,y+1),fill=tuple(min(255,c+18) for c in rgb))
        if (x0,y0)==(128,0):
            for y in range(y0+3,y0+64,4):d.line((x0,y,x0+127,y),fill='#253E49')
        if (x0,y0)==(128,128):
            for y in range(y0+3,y0+64,9):d.line((x0,y,x0+127,y),fill='#66B6B7')
    for layer in ('humanoid','humanoid_leggings','humanoid_baby'):
        path=BASE/'textures/entity/equipment'/layer/'power_armor.png';path.parent.mkdir(parents=True,exist_ok=True);atlas.save(path)
    atlas.save(BASE/'textures/entity/foundry_suit.png')
    # Higher-resolution inventory silhouettes match the physical plates and accents.
    for part in ('helmet','chestplate','leggings','boots'):
        im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
        if part=='helmet':
            d.rounded_rectangle((5,3,26,27),3,fill='#304B5A',outline='#10232D',width=2)
            d.polygon([(7,4),(24,4),(26,9),(5,9)],fill='#BCCBC0')
            d.rectangle((7,11,24,16),fill='#164950');d.line((8,12,23,12),fill='#7EE7CF')
            d.rectangle((5,18,9,25),fill='#BCCBC0');d.rectangle((22,18,26,25),fill='#BCCBC0')
            d.rectangle((11,20,20,27),fill='#182C35')
            for x in (12,15,18):d.line((x,21,x,25),fill='#A2BEBD')
            d.rectangle((2,11,4,19),fill='#C6A267');d.rectangle((27,11,29,19),fill='#C6A267')
        elif part=='chestplate':
            d.polygon([(3,4),(10,2),(13,6),(19,6),(22,2),(29,4),(28,15),(25,15),(24,29),(7,29),(6,15),(3,15)],fill='#304B5A',outline='#10232D')
            d.rectangle((3,4,9,11),fill='#BCCBC0');d.rectangle((22,4,28,11),fill='#BCCBC0')
            d.polygon([(9,9),(23,9),(22,18),(17,20),(10,18)],fill='#BCCBC0')
            d.rectangle((12,12,20,17),fill='#304B5A');d.line((14,14,18,14),fill='#7EE7CF')
            for y in (22,25):d.rectangle((9,y,22,y+1),fill='#BCCBC0')
            d.line((9,28,22,28),fill='#C6A267')
        elif part=='leggings':
            d.rectangle((6,3,25,11),fill='#304B5A',outline='#10232D');d.rectangle((13,5,18,7),fill='#C6A267')
            for x in (6,19):
                d.rectangle((x,10,x+6,29),fill='#182C35',outline='#10232D');d.rectangle((x,11,x+6,19),fill='#BCCBC0')
                d.rectangle((x-1,21,x+7,25),fill='#BCCBC0');d.line((x,23,x+6,23),fill='#C6A267')
        else:
            for x in (6,19):
                d.rectangle((x,8,x+6,25),fill='#304B5A',outline='#10232D');d.line((x,12,x+6,12),fill='#C6A267')
                d.rectangle((x-2,24,x+7,28),fill='#BCCBC0');d.rectangle((x-2,29,x+7,30),fill='#182C35')
        im.save(BASE/'textures/item'/('power_'+part+'.png'))


def generate_lang(lang):
    lang.update({'key.powerfactor.toggle_hud':'Toggle suit and movement overlays',
                 'gui.powerfactor.armor.compact':'SUIT','gui.powerfactor.armor.helmet.letter':'H',
                 'gui.powerfactor.armor.chest.letter':'C','gui.powerfactor.armor.legs.letter':'L','gui.powerfactor.armor.boots.letter':'B',
                 'gui.powerfactor.hud.armor':'Suit: %s','gui.powerfactor.hud.movement':'Move: %s',
                 'gui.powerfactor.hud.help':'Hide or show this overlay. %s toggles both overlays; your choices are saved locally.',
                 'gui.powerfactor.grapple.compact.ready':'Grapple','gui.powerfactor.grapple.compact.firing':'Firing',
                 'gui.powerfactor.grapple.compact.swing':'Swing','gui.powerfactor.grapple.compact.reel':'Reel',
                 'gui.powerfactor.wingsuit.compact.open':'Glide','gui.powerfactor.wingsuit.compact.ready':'Wings ready',
                 'gui.powerfactor.armor.help':'Batteries · Modules · Overlay settings'})


def generate_data(g):
    g['write'](g['asset']('equipment','power_armor.json'),{'layers':{layer:[{'texture':'powerfactor:power_armor'}]
        for layer in ('humanoid','humanoid_leggings','humanoid_baby')}})


if __name__=='__main__':
    import gen_data
    generate_textures();generate_data(vars(gen_data))
