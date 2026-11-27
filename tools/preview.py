"""Offline preview of actual baked-model JSON and texture assets, with simulated rack module mesh.
No shaders; not a screenshot or a benchmark. Requires numpy/Pillow."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json, numpy as np, math
root=Path(__file__).resolve().parents[1];assets=root/'src/main/resources/assets/odc'
def font(size,b=False):
 try:return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if b else '')+'.ttf',size)
 except OSError:return ImageFont.load_default()
def render(instances,scale=8):
 def proj(p):x,y,z=p;return np.array([(.866*x+.5*z)*scale,(.30*x-.30*z-y)*scale])
 view=np.array([.5,.4098,-.866]);faces=[];allp=[]
 for name,offset in instances:
  m=name if isinstance(name,dict) else json.loads((assets/f'models/block/{name}.json').read_text());offset=np.array(offset)
  for e in m['elements']:
   x,y,z=e['from'];X,Y,Z=e['to']
   candidates={'north':([(x,Y,z),(X,Y,z),(x,y,z)],(0,0,-1),1.0),'south':([(X,Y,Z),(x,Y,Z),(X,y,Z)],(0,0,1),.65),'east':([(X,Y,z),(X,Y,Z),(X,y,z)],(1,0,0),.73),'west':([(x,Y,Z),(x,Y,z),(x,y,Z)],(-1,0,0),.80),'up':([(x,Y,Z),(X,Y,Z),(x,Y,z)],(0,1,0),1.12),'down':([(x,y,z),(X,y,z),(x,y,Z)],(0,-1,0),.55)}
   for f,info in e['faces'].items():
    pts,normal,shade=candidates[f];pts=np.array(pts,dtype=float);normal=np.array(normal,dtype=float)
    if 'rotation' in e:
     r=e['rotation'];a=math.radians(r['angle']);c,s=math.cos(a),math.sin(a);mat={'x':np.array([[1,0,0],[0,c,-s],[0,s,c]]),'y':np.array([[c,0,s],[0,1,0],[-s,0,c]]),'z':np.array([[c,-s,0],[s,c,0],[0,0,1]])}[r['axis']];o=np.array(r['origin']);pts=(pts-o)@mat.T+o;normal=mat@normal
    if normal@view<=0:continue
    pts+=offset;tex=m['textures'][info['texture'][1:]].split(':',1)[1];im=Image.open(assets/'textures'/f'{tex}.png').convert('RGBA');im=im.crop((0,0,im.width,im.width)) if im.height>im.width else im
    if info.get('rotation',0):im=im.rotate(-info['rotation'])
    pixels=np.asarray(im);p=np.array([proj(v) for v in pts]);dep=pts@view;allp.extend([*p,p[1]+p[2]-p[0]]);faces.append((p,dep,pixels,shade,tex.endswith('rack_window')))
 allp=np.array(allp);lo=np.floor(allp.min(axis=0)).astype(int)-2;hi=np.ceil(allp.max(axis=0)).astype(int)+2;w,h=hi-lo;out=np.zeros((h,w,4),dtype=np.uint8);zbuf=np.full((h,w),-np.inf)
 for p,dep,pixels,shade,glass in sorted(faces,key=lambda t:t[-1]):
  p=p-lo;quad=np.vstack([p,p[1]+p[2]-p[0]]);x0,y0=np.maximum(0,np.floor(quad.min(axis=0)).astype(int));x1,y1=np.minimum([w,h],np.ceil(quad.max(axis=0)).astype(int)+1)
  if x0>=x1 or y0>=y1:continue
  mat=np.column_stack([p[1]-p[0],p[2]-p[0]])
  if abs(np.linalg.det(mat))<1e-9:continue
  xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5);coords=np.linalg.inv(mat)@np.stack([xx-p[0,0],yy-p[0,1]]).reshape(2,-1);u,v=coords.reshape(2,y1-y0,x1-x0)
  mask=(u>=0)&(u<1)&(v>=0)&(v<1);sx=np.clip((u*pixels.shape[1]).astype(int),0,pixels.shape[1]-1);sy=np.clip((v*pixels.shape[0]).astype(int),0,pixels.shape[0]-1);rgba=pixels[sy,sx].copy();rgba[:,:,:3]=np.clip(rgba[:,:,:3].astype(float)*shade,0,255).astype(np.uint8);depth=dep[0]+u*(dep[1]-dep[0])+v*(dep[2]-dep[0]);region=zbuf[y0:y1,x0:x1];mask&=(rgba[:,:,3]>(0 if glass else 127))&(depth>=region);target=out[y0:y1,x0:x1]
  if glass:
   a=rgba[:,:,3:4].astype(float)/255;rgb=target[:,:,:3]*(1-a)+rgba[:,:,:3]*a;target[mask,:3]=rgb[mask].astype(np.uint8);target[mask,3]=np.maximum(target[mask,3],rgba[mask,3]);
  else:target[mask]=rgba[mask];region[mask]=depth[mask]
 return Image.fromarray(out,'RGBA')
def dynamic(start,units,total,kind='server',active=True,connected=()):
 pitch=30/9 if total==9 else 14/4;b=1+start*pitch+.192;t=1+(start+units)*pitch-.192;h=t-b
 tex={'side':'odc:blocks/module_side','front':'odc:blocks/'+(f'server_{units}_ready' if kind=='server' else 'switch_panel_2'),'led':'odc:blocks/'+('led_on' if active else 'led_off'),'up':'odc:blocks/patch_up','down':'odc:blocks/patch_down'}
 def e(a,b,tx,fs):return {'from':a,'to':b,'faces':{f:{'texture':'#'+(tx if f=='north' else 'side'),'uv':[0,0,16,16]} for f in fs}}
 els=[e([2,b,2.4],[14,t,14.4],'front',['north','east','west','up','down','south'])]
 if kind=='server':
  for l,r in [(12.86,13.6),(12,12.58)]:els.append(e([l,t-h*.27,2.368],[r,t-h*.10,2.369],'led',['north']))
 else:
  for p in range(8):
   cx=2+(p+.5)*12/8;els.append(e([cx-.192,t-h*.2,2.368],[cx+.192,t-h*.1,2.369],'led',['north']))
  for p,up in enumerate(connected):
   cx=2+(p+.5)*12/8;py=b+h*.47;low=py-h*.12 if up else b-.576;high=t+.576 if up else py+h*.12;els.append(e([cx-.496,low,1.36],[cx+.496,high,1.361],'up' if up else 'down',['north']))
 return {'textures':tex,'elements':els}
def rack(total,modules,offset=(0,0,0),glass=False):
 name='rack_glass' if glass else 'rack';x,y,z=offset;instances=[(name+'_0',offset)] if total==4 else [(name+'_1',offset),(name+'_2',(x,y+16,z))];instances += [(dynamic(*m[:2],total,*m[2:]),offset) for m in modules]
 if glass:instances.append(({'textures':{'g':'odc:blocks/rack_window'},'elements':[{'from':[1.6,1.04,.608],'to':[14.4,(32 if total==9 else 16)-1.04,.61],'faces':{'north':{'texture':'#g','uv':[0,0,16,16]}}}]},offset))
 return instances
canvas=Image.new('RGB',(1500,1080),'#202228');d=ImageDraw.Draw(canvas);d.rectangle((0,0,1499,5),fill='#46a8d5');d.text((32,22),'OPEN DATA CENTER  /  0.3.1',font=font(39,True),fill='#f0f0f4');d.text((34,79),'VISUAL UPDATE • OFFLINE RENDER OF PROJECT ASSETS',font=font(18),fill='#b6bac7')
def panel(x,y,w,h):d.rectangle((x,y,x+w,y+h),fill='#30333b',outline='#494e5a',width=2)
def paste(im,x,y,w,h):
 if im.width>w or im.height>h:im.thumbnail((w,h),Image.Resampling.NEAREST)
 canvas.paste(im,(x+(w-im.width)//2,y+(h-im.height)//2),im)
panel(30,120,720,630)
a=rack(9,[(0,4,'server',True),(4,1,'switch',True,(True,False)),(5,4,'server',True)])+rack(9,[(0,1,'server',True),(1,2,'server',True),(3,4,'server',False),(7,1,'switch',True,(False,False,False)),(8,1,'server',True)],(17,0,0),True)+[('computer_lower_on',(34,0,0)),('computer_upper_on',(34,16,0))]
paste(render(a,11),44,140,690,510);d.text((50,680),'9U racks / glass / blue control terminal',font=font(24,True),fill='#eef0f4');d.text((50,712),'Module faces and patch leads: simulated rack renderer',font=font(16),fill='#b6bac7')
for i,(name,label,sub) in enumerate([('radio_station_on','Radio antenna','Two-sided cutout plane'),('satellite_station_on','Satellite dish','Flat tilted reflector'),('power_relay_item','Power relay','Redstone controlled'),('computer_item','Control terminal','Two-block cabinet')]):
 x=775+i%2*354;y=120+i//2*315;panel(x,y,340,300);paste(render([(name,(0,0,0))],8),x+8,y+8,324,235);d.text((x+12,y+242),label,font=font(22,True),fill='#eef0f4');d.text((x+12,y+272),sub,font=font(15),fill='#b6bac7')
for i,(kind,label) in enumerate([('data_cable','Data / blue'),('power_cable','Power / amber')]):
 x=30+i*735;y=776;panel(x,y,720,272)
 a=[(kind+'_item',(0,0,0)),(kind+'_0c',(19,0,0)),(kind+'_14',(38,0,0)),(kind+'_3c',(57,0,0))];paste(render(a,8),x+8,y+9,700,210);d.text((x+15,y+226),label+' — straight / corner / junction',font=font(21,True),fill='#eef0f4')
canvas.save(root/'preview.png')
