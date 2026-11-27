"""Render the generated JSON block models using their actual UVs and first animation frames.
Offline orthographic preview only: not a Minecraft screenshot. Requires Pillow and NumPy."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,numpy as np
root=Path(__file__).resolve().parents[1];assets=root/'src/main/resources/assets/odc'
def font(size,bold=False):
    p=Path('/usr/share/fonts/truetype/dejavu')/('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf')
    try:return ImageFont.truetype(str(p),size)
    except OSError:return ImageFont.load_default()

def render(instances,scale=8):
    # Camera is above, to the east, facing the north/front side of each block.
    def proj(p):x,y,z=p;return np.array([(.866*x+.5*z)*scale,(.30*x-.30*z-y)*scale])
    view=np.array([.5,.4098,-.866]);face_data=[];allp=[]
    for name,offset in instances:
        m=json.loads((assets/f'models/block/{name}.json').read_text());offset=np.array(offset)
        for e in m['elements']:
            x,y,z=np.array(e['from'])+offset;X,Y,Z=np.array(e['to'])+offset
            for f,points,shade in [('north',[(x,Y,z),(X,Y,z),(x,y,z)],1.0),('east',[(X,Y,z),(X,Y,Z),(X,y,z)],.75),('up',[(x,Y,Z),(X,Y,Z),(x,Y,z)],1.12)]:
                info=e['faces'][f];tex=m['textures'][info['texture'][1:]].split(':',1)[1];im=Image.open(assets/'textures'/f'{tex}.png').convert('RGBA');im=im.crop((0,0,im.width,im.width)) if im.height>im.width else im
                pixels=np.asarray(im);p=np.array([proj(v) for v in points]);depth=np.array(points)@view;quad=np.vstack([p,p[1]+p[2]-p[0]]);allp.extend(quad);face_data.append((p,depth,pixels,info.get('uv',[0,0,16,16]),shade))
    allp=np.array(allp);lo=np.floor(allp.min(axis=0)).astype(int)-2;hi=np.ceil(allp.max(axis=0)).astype(int)+2;w,h=hi-lo
    out=np.zeros((h,w,4),dtype=np.uint8);zbuf=np.full((h,w),-np.inf)
    for p,dep,pixels,uv,shade in face_data:
        p=p-lo;quad=np.vstack([p,p[1]+p[2]-p[0]]);x0,y0=np.maximum(0,np.floor(quad.min(axis=0)).astype(int));x1,y1=np.minimum([w,h],np.ceil(quad.max(axis=0)).astype(int)+1)
        if x0>=x1 or y0>=y1:continue
        mat=np.column_stack([p[1]-p[0],p[2]-p[0]])
        if abs(np.linalg.det(mat))<1e-9:continue
        xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5);coords=np.linalg.inv(mat)@np.stack([xx-p[0,0],yy-p[0,1]]).reshape(2,-1);u,v=coords.reshape(2,y1-y0,x1-x0)
        mask=(u>=0)&(u<1)&(v>=0)&(v<1)
        sx=np.clip(((uv[0]+u*(uv[2]-uv[0]))/16*pixels.shape[1]).astype(int),0,pixels.shape[1]-1);sy=np.clip(((uv[1]+v*(uv[3]-uv[1]))/16*pixels.shape[0]).astype(int),0,pixels.shape[0]-1)
        rgba=pixels[sy,sx].copy();rgba[:,:,:3]=np.clip(rgba[:,:,:3].astype(float)*shade,0,255).astype(np.uint8);depth=dep[0]+u*(dep[1]-dep[0])+v*(dep[2]-dep[0]);region=zbuf[y0:y1,x0:x1];mask&=(rgba[:,:,3]>127)&(depth>=region)
        out[y0:y1,x0:x1][mask]=rgba[mask];region[mask]=depth[mask]
    return Image.fromarray(out,'RGBA')

def rack_state(glass,values,offset=(0,0,0)):
    return [('rack_glass' if glass else 'rack',offset)]+[(f'bay{i}_{v}',offset) for i,v in enumerate(values) if v]

canvas=Image.new('RGB',(1440,930),'#202527');d=ImageDraw.Draw(canvas)
d.rectangle((0,0,1439,5),fill='#7ab58d');d.text((35,24),'OPEN DATA CENTER',font=font(39,True),fill='#e4e7e8');d.text((38,78),'0.2.1-alpha  /  original 16x16 textures  /  model preview, not in-game',font=font(19),fill='#b4babb')
# One large example: same blocks stacked to produce a two-block cabinet wall.
d.rectangle((32,121,695,880),fill='#303638',outline='#495053',width=2)
stack=[]
for col in range(3):
    for row in range(2):stack+=rack_state(col==0,[3,4,0,2] if col==0 else [1,2,3,4] if col==1 else [4,4,4,4],(col*16,row*16,0))
im=render(stack,9);canvas.paste(im,(32+(663-im.width)//2,168),im)
d.text((56,650),'Modular 4U cabinets',font=font(28,True),fill='#e4e7e8');d.text((56,692),'1 Minecraft block per rack; stackable',font=font(20),fill='#b4babb');d.text((56,725),'Dark gray steel, simple pixel geometry',font=font(20),fill='#b4babb');d.text((56,758),'Independent green LEDs per running server',font=font(20),fill='#b4babb')
# Compare off/on LED faces, plus actual new devices.
for idx,(name,label) in enumerate([('network_switch_on','Network switch'),('network_station_on','Network station'),('computer_on','DataOS computer'),('assembler_off','Assembly station'),('creative_battery_on','Creative battery'),('network_switch_off','Switch / off')]):
    x=717+(idx%3)*232;y=121+(idx//3)*240;d.rectangle((x,y,x+218,y+222),fill='#303638',outline='#495053',width=2)
    im=render([(name,(0,0,0))],6.5);canvas.paste(im,(x+(218-im.width)//2,y+10),im);d.text((x+10,y+194),label,font=font(16,True),fill='#dce0e1')
# Bottom panel displays all four actual front textures and component sprites.
x=717;y=601;d.rectangle((x,y,1400,880),fill='#303638',outline='#495053',width=2);d.text((x+15,y+14),'SERVER PANEL STATES / ACTUAL PIXELS',font=font(20,True),fill='#dce0e1')
for idx,name in enumerate(['cpu_face_off','cpu_face_on','gpu_face_off','gpu_face_on']):
    im=Image.open(assets/f'textures/blocks/{name}.png').crop((0,0,12,3)).resize((144,36),Image.Resampling.NEAREST);xx=x+16+idx*165;canvas.paste(im,(xx,y+54),im);d.text((xx,y+99),['CPU off','CPU active','GPU off','GPU active'][idx],font=font(16),fill='#b4babb')
for idx,name in enumerate(['cpu_basic','cpu_pro','gpu_basic','gpu_pro','chassis_1u','chassis_4u']):
    im=Image.open(assets/f'textures/items/{name}.png').resize((64,64),Image.Resampling.NEAREST);xx=x+18+idx*110;canvas.paste(im,(xx,y+139),im);d.text((xx,y+209),['CPU T1','CPU T2','GPU T1','GPU T2','1U','4U'][idx],font=font(14),fill='#b4babb')
canvas.save(root/'preview.png')
