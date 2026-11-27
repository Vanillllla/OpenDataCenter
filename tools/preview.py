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
        m=name if isinstance(name,dict) else json.loads((assets/f'models/block/{name}.json').read_text());offset=np.array(offset)
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

def dynamic_module(start,units,total,kind='server',ready=True,active=True):
    pitch=30/9 if total==9 else 14/4
    b=1+start*pitch+.144;t=1+(start+units)*pitch-.144
    textures={'steel':'odc:blocks/steel','front':'odc:blocks/'+(f'server_{units}_'+('ready' if ready else 'empty') if kind=='server' else 'switch_panel_2'),'led':'odc:blocks/'+('led_on' if active else 'led_off')}
    def elem(a,b,tex):return {'from':a,'to':b,'faces':{f:{'texture':'#'+(tex if f=='north' else 'steel'),'uv':[0,0,16,16]} for f in ('north','east','up')}}
    elements=[elem([1.92,b,1.76],[14.08,t,14.4],'front')]
    if kind=='server':
        elements+=[elem([12.96,t-(t-b)*.22,1.72],[13.6,t-(t-b)*.10,1.75],'led'),elem([11.84,t-(t-b)*.22,1.72],[12.48,t-(t-b)*.10,1.75],'led')]
    else:
        for i in range(8):elements.append(elem([1.92+(2+i*1.65)*.76,t-(t-b)*.20,1.72],[1.92+(3+i*1.65)*.76,t-(t-b)*.08,1.75],'led'))
    return {'textures':textures,'elements':elements}

def rack(total,modules,offset=(0,0,0),glass=False):
    name='rack_glass' if glass else 'rack'
    x,y,z=offset
    instances=[(name+'_0',offset)] if total==4 else [(name+'_1',offset),(name+'_2',(x,y+16,z))]
    return instances+[(dynamic_module(*m[:2],total,*m[2:]),offset) for m in modules]

canvas=Image.new('RGB',(1500,1050),'#23282b');d=ImageDraw.Draw(canvas)
d.rectangle((0,0,1499,5),fill='#83b996');d.text((35,23),'OPEN DATA CENTER',font=font(40,True),fill='#edf0f1');d.text((38,79),'0.3.0 ALPHA / HARDWARE & INFRASTRUCTURE / OFFLINE MODEL PREVIEW',font=font(19),fill='#bfc9cc')
def panel(x,y,w,h):d.rectangle((x,y,x+w,y+h),fill='#30383c',outline='#4a575d',width=2)
def paste_center(im,x,y,w,h):canvas.paste(im,(x+(w-im.width)//2,y+(h-im.height)//2),im)
panel(30,122,690,725)
# 4U+1U+4U, middle-spanning 4U, and distinct 1U/2U units in a glass rack frame.
a=rack(9,[(0,4,'server',True,True),(4,1,'switch',True,True),(5,4,'server',True,True)],(0,0,0))
a+=rack(9,[(0,1,'switch',True,True),(1,2,'server',True,False),(3,4,'server',True,True),(7,2,'server',False,False)],(17,0,0))
a+=rack(4,[(0,1,'server',True,True),(1,2,'server',True,False),(3,1,'switch',True,True)],(34,0,0),True)
im=render(a,10);paste_center(im,35,145,680,550)
d.text((52,700),'4U / 9U modular racks',font=font(27,True),fill='#edf0f1');d.text((52,745),'Left: 4U + switch + 4U',font=font(19),fill='#bfc9cc');d.text((52,773),'Middle: a 4U server crosses the block seam',font=font(19),fill='#bfc9cc');d.text((52,800),'Right: 4U glass frame (window rendered in-game)',font=font(18),fill='#bfc9cc')
# model previews, exact block cuboids and texture UVs
for idx,(name,label,subtitle) in enumerate([('radio_station_on','Radio station','128 Mb/s WAN'),('satellite_station_on','Satellite station','512 Mb/s WAN'),('power_relay_off','Power relay','Closed / no redstone'),('workbench_off','Server workbench','Hardware editing'),('data_cable_item','Data cable','Bundled conductors'),('power_cable_item','Power cable','Armored jacket')]):
    x=745+(idx%2)*363;y=122+(idx//2)*240;panel(x,y,347,224)
    im=render([(name,(0,0,0))],7.5);paste_center(im,x+8,y+7,330,160)
    d.text((x+13,y+176),label,font=font(20,True),fill='#edf0f1');d.text((x+13,y+201),subtitle,font=font(15),fill='#bfc9cc')
panel(30,870,1430,151);d.text((49,882),'HARDWARE ICONS / ACTUAL 16px SPRITES',font=font(19,True),fill='#edf0f1')
icons=['board_basic','board_pro','board_advanced','ram_advanced','hdd_pro','ssd_pro','nvme_pro','psu_basic','psu_pro','nic_pro','switch_basic','switch_pro','ethernet_patch','chassis_4u','chassis_4u_ready']
for i,n in enumerate(icons):
    im=Image.open(assets/f'textures/items/{n}.png').convert('RGBA').resize((48,48),Image.Resampling.NEAREST);x=48+i*94;canvas.paste(im,(x+14,915),im);d.text((x,973),n.replace('_',' ')[:14],font=font(11),fill='#bfc9cc')
canvas.save(root/'preview.png')
