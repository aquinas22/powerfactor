"""Render Skin.java itself through a Java2D adapter, with Minecraft's bitmap font.
Example consoles use sample readings; this is an asset preview, not a running client capture.
"""
import json, re, subprocess, tempfile, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
CLIENT=ROOT/'powerfactor/src/main/java/com/powerfactor/client'
ASSETS=ROOT/'powerfactor/src/main/resources/assets/powerfactor'
OUT=ROOT/'powerfactor/docs/previews/machine-console-finish.png'


def main():
    with tempfile.TemporaryDirectory(prefix='powerfactor-ui-') as directory:
        tmp=Path(directory)
        with zipfile.ZipFile(ROOT/'powerfactor/build/moddev/artifacts/minecraft-patched-26.2.0.90-merged.jar') as z:
            providers=json.loads(z.read('assets/minecraft/font/include/default.json'))['providers']
            provider=next(p for p in providers if p.get('file','').endswith('ascii.png'))
            (tmp/'ascii.png').write_bytes(z.read('assets/minecraft/textures/font/ascii.png'))
        codes=[ord(c) for row in provider['chars'] for c in row]
        skin=(CLIENT/'Skin.java').read_text()
        skin=re.sub(r'^package .*?;\n|^import .*?;\n','',skin,flags=re.M)
        for name,replacement in {'net.minecraft.world.inventory.Slot':'PreviewSlot',
                'com.powerfactor.machine.ProcessingMenu.UpgradeSlot':'UpgradeSlot',
                'net.minecraft.world.entity.player.Inventory':'PreviewInventory',
                'com.powerfactor.machine.MachineMenu.OutputSlotMarker':'PreviewOutput'}.items(): skin=skin.replace(name,replacement)
        (tmp/'Skin.java').write_text(skin)
        gui=re.sub(r'^package .*?;\n|^import .*?;\n','',(CLIENT/'GuiUtil.java').read_text(),flags=re.M)
        (tmp/'GuiUtil.java').write_text('import java.util.List;\n'+gui)
        adapter=r'''
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.*;
class Component {
    final String value;
    Component(String value) { this.value=value; }
    static Component literal(String value) { return new Component(value); }
    static Component translatable(String key,Object... args) { return literal(key.equals("container.inventory") ? "Inventory" : key); }
    String getString() { return value; }
}
class Font {
    static BufferedImage atlas;
    static final int[] codes = CODES;
    static final Map<Integer,Integer> indexes = new HashMap<>();
    static void load(String path) throws Exception {
        atlas=ImageIO.read(new File(path));
        for(int i=0;i<codes.length;i++) if(codes[i]!=0) indexes.put(codes[i],i);
    }
    int advance(int code) {
        if(code==32) return 4;
        Integer i=indexes.get(code);if(i==null)return 6;
        int right=0;
        for(int x=0;x<8;x++)for(int y=0;y<8;y++)if((atlas.getRGB(i%16*8+x,i/16*8+y)>>>24)>0)right=Math.max(right,x+1);
        return right+1;
    }
    int width(String s) { return s.codePoints().map(this::advance).sum(); }
    int width(Component s) { return width(s.value); }
    Component substrByWidth(Component s,int width) {
        StringBuilder result=new StringBuilder();int used=0;
        for(int c:s.value.codePoints().toArray()) { int w=advance(c); if(used+w>width)break;result.appendCodePoint(c);used+=w; }
        return Component.literal(result.toString());
    }
    void draw(GuiGraphicsExtractor g,String text,int x,int y,int color) {
        for(int c:text.codePoints().toArray()) {
            Integer i=indexes.get(c);
            if(i!=null)for(int xx=0;xx<8;xx++)for(int yy=0;yy<8;yy++) {
                int alpha=atlas.getRGB(i%16*8+xx,i/16*8+yy)>>>24;
                if(alpha>0)g.fill(x+xx,y+yy,x+xx+1,y+yy+1,(alpha<<24)|(color&0xffffff));
            }
            else if(c!=32) { g.fill(x,y+3,x+3,y+4,color); }
            x+=advance(c);
        }
    }
}
class GuiGraphicsExtractor {
    final BufferedImage im;
    GuiGraphicsExtractor(int width,int height) { im=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); }
    void fill(int x0,int y0,int x1,int y1,int color) {
        var g=im.createGraphics();g.setColor(new Color(color,true));g.fillRect(x0,y0,Math.max(0,x1-x0),Math.max(0,y1-y0));g.dispose();
    }
    void fillGradient(int x0,int y0,int x1,int y1,int a,int b) {
        for(int y=y0;y<y1;y++) {
            float t=(float)(y-y0)/Math.max(1,y1-y0-1);int c=0;
            for(int shift=0;shift<32;shift+=8)c|=Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;
            fill(x0,y,x1,y+1,c);
        }
    }
    void outline(int x,int y,int w,int h,int c) { fill(x,y,x+w,y+1,c);fill(x,y+h-1,x+w,y+h,c);fill(x,y,x+1,y+h,c);fill(x+w-1,y,x+w,y+h,c); }
    void text(Font f,Component s,int x,int y,int color,boolean shadow) { text(f,s.getString(),x,y,color,shadow); }
    void text(Font f,String s,int x,int y,int color,boolean shadow) { f.draw(this,s,x,y,color); }
    void setComponentTooltipForNextFrame(Font f,List<Component> lines,int x,int y) {}
    void icon(String path,int x,int y) throws Exception {
        var g=im.createGraphics();g.drawImage(ImageIO.read(new File(path)),x,y,16,16,null);g.dispose();
    }
}
class Mth {
    static float clamp(float value,float lo,float hi){ return Math.max(lo,Math.min(hi,value)); }
}
class Util { static long getMillis(){ return 1450; } }
class PreviewInventory {}
class PreviewSlot { Object container; int x,y; }
class UpgradeSlot extends PreviewSlot {}
interface PreviewOutput {}

public class SkinPreview {
    public static void main(String[] args) throws Exception {
        Font.load(args[0]);var f=new Font();var g=new GuiGraphicsExtractor(646,275);
        g.fill(0,0,646,275,Skin.INK);
        g.text(f,"POWERFACTOR / PROCESS CONSOLE",12,8,Skin.TEXT,false);
        String[] names={"Mechanical Press","Ore Washer","Centrifuge"};
        String[] statuses={"Running at 100% speed","Needs water","Output blocked"};
        for(int i=0;i<3;i++) {
            int x=12+i*212,y=28;float cycle=i==0?.42F:i==1?.18F:.77F;int color=i==0?Skin.TEAL:Skin.OUTPUT;
            Skin.panel(g,x,y,176,214);Skin.title(g,f,Component.literal(names[i]),x,y,176);Skin.lamp(g,x+162,y+6,color);
            Skin.scope(g,x+8,y+21,56,24,.7F,i==0);Skin.lcd(g,x+8,y+46,56,31);
            Skin.metric(g,f,x+11,y+49,50,Component.literal("FE"),Component.literal(i==0?"32":"0"),Skin.FE);
            Skin.metric(g,f,x+11,y+58,50,Component.literal("EE"),Component.literal(i==0?"24":"0"),Skin.EE);
            Skin.metric(g,f,x+11,y+67,50,Component.literal("PF"),Component.literal(i==0?"0.80":"--"),Skin.LCD_TEXT);
            Skin.slot(g,x+70,y+34,Skin.INPUT);Skin.bigSlot(g,x+117,y+30,Skin.OUTPUT);
            Skin.chevrons(g,x+93,y+39,3,cycle,i==0);
            if(i==0)Skin.slot(g,x+93,y+57,0xFFC6A1E6);
            else Skin.slot(g,x+123,y+59,Skin.OUTPUT);
            g.icon(args[1]+"/textures/item/"+(i==0?"steel_ingot":"raw_zinc")+".png",x+71,y+35);
            if(i!=1)g.icon(args[1]+"/textures/item/"+(i==0?"steel_plate":"copper_dust")+".png",x+122,y+35);
            if(i==0)g.icon(args[1]+"/textures/item/plate_die.png",x+94,y+58);
            if(i==1)GuiUtil.fluidGauge(g,x+94,y+52,16,24,0,10000,0xFF3F76E4);
            Skin.energyTube(g,x+156,y+21,12,56,7000,10000,i==0);
            Skin.lcd(g,x+8,y+81,160,39);g.fill(x+9,y+83,x+11,y+95,color);
            g.text(f,Skin.fit(f,Component.literal(statuses[i]),147),x+15,y+85,color,false);
            g.text(f,"Cycle "+Math.round(cycle*100)+"%",x+12,y+97,Skin.DIM,false);
            g.text(f,i==0?"x1.00":"x0.00",x+134,y+97,Skin.DIM,false);
            Skin.progress(g,x+12,y+108,152,cycle,color);Skin.inventory(g,f,x+8,y+132);
            for(int t=0;t<3;t++) {
                Skin.control(g,x+175,y+4+t*26,26,24,false,t==1&&i==0);
                g.icon(args[1]+"/textures/item/"+(t==0?"copper_coil":t==1?"speed_upgrade":"control_circuit")+".png",x+180,y+8+t*26);
            }
        }
        g.text(f,"Software UI preview / sample readings / actual Skin.java drawing",12,258,Skin.DIM,false);
        BufferedImage scaled=new BufferedImage(1938,825,BufferedImage.TYPE_INT_ARGB);
        var draw=scaled.createGraphics();draw.drawImage(g.im,0,0,scaled.getWidth(),scaled.getHeight(),null);draw.dispose();
        ImageIO.write(scaled,"png",new File(args[2]));
    }
}
'''.replace('CODES','new int[]{'+','.join(map(str,codes))+'}')
        (tmp/'SkinPreview.java').write_text(adapter)
        subprocess.run(['javac','-d',str(tmp),str(tmp/'Skin.java'),str(tmp/'GuiUtil.java'),str(tmp/'SkinPreview.java')],check=True)
        subprocess.run(['java','-Djava.awt.headless=true','-cp',str(tmp),'SkinPreview',str(tmp/'ascii.png'),str(ASSETS),str(OUT)],check=True)
        print(OUT)


if __name__=='__main__':main()
