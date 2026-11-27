"""Generate simplified 0.3.1 textures and geometry. Requires Pillow, not needed for build."""
from pathlib import Path
from PIL import Image, ImageDraw
import json,itertools
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'; B=R/'models/block'; I=R/'models/item'; S=R/'blockstates'; T=R/'textures/blocks'
F=['down','up','north','south','west','east']; H=['north','east','south','west']
def dump(p,v):p.write_text(json.dumps(v,separators=(',',':'))+'\n',encoding='utf8')
def tex(name,draw,size=16,base=(0,0,0,0)):
 im=Image.new('RGBA',(size,size),base);draw(ImageDraw.Draw(im),size);im.save(T/(name+'.png'));return im
def framed(d,n,base=(56,57,64),border=(32,33,39),light=(83,84,94)):
 d.rectangle((0,0,n-1,n-1),fill=base,outline=border);d.line((1,1,n-2,1),fill=light);d.line((1,n-2,n-2,n-2),fill=border)
def plate(d,n):
 framed(d,n,(52,53,60));d.rectangle((3,3,n-4,n-4),outline=(40,41,47));d.line((4,4,n-5,4),fill=(62,63,72))
tex('rack_side',lambda d,n:(d.rectangle((0,0,n-1,n-1),fill=(48,49,56)),d.rectangle((0,0,1,n-1),fill=(35,36,42)),d.rectangle((2,0,3,n-1),fill=(67,69,78)),d.rectangle((n-3,0,n-1,n-1),fill=(37,38,44))));tex('rack_frame',lambda d,n:framed(d,n,(52,53,61),(37,38,44),(77,79,88)))
tex('rack_back',lambda d,n:(framed(d,n,(41,42,48)),d.rectangle((4,3,11,12),fill=(30,31,36)),[d.line((5,y,10,y),fill=(64,65,73)) for y in (4,6,8,10)]))
tex('module_side',lambda d,n:framed(d,n,(50,52,59),(42,43,50),(69,70,79)))
def panel(d,n,u,ready):
 framed(d,n,(70,71,80),(34,35,41),(101,102,112));d.rectangle((2,3,n-3,n-4),fill=(52,53,60));d.rectangle((2,3,3,n-4),fill=(91,93,102));d.rectangle((n-4,3,n-3,n-4),fill=(91,93,102))
 if not ready:d.rectangle((5,5,n-7,n-6),fill=(26,28,34));return
 if u==1:
  for x in range(5,24,4):d.rectangle((x,10,x+2,22),fill=(28,29,35))
 elif u==2:
  for y in (7,18):
   for x in (5,15):d.rectangle((x,y,x+7,y+6),fill=(29,30,36))
 else:
  for x in (5,9,13,17):d.rectangle((x,5,x+2,26),fill=(28,29,35))
  d.rectangle((21,8,24,23),fill=(91,92,102))
for u in (1,2,4):
 for state in ('empty','ready'):tex(f'server_{u}_{state}',lambda d,n,u=u,state=state:panel(d,n,u,state=='ready'),32)
def switch(d,n,t):
 framed(d,n,(64,66,73),(30,32,37),(91,95,103));count=4 if t==1 else 8
 for p in range(count):
  x=(2*p+1)*16//count;d.rectangle((x-1,12,x,19),fill=(18,23,28));d.rectangle((x-1,14,x,14),fill=(111,101,71))
for t in (1,2):tex('switch_panel_'+str(t),lambda d,n,t=t:switch(d,n,t),32)
tex('led_on',lambda d,n:d.rectangle((0,0,n-1,n-1),fill=(117,224,72)));tex('led_off',lambda d,n:d.rectangle((0,0,n-1,n-1),fill=(44,52,45)))
def anim(name,frames,time):
 n=frames[0].width;im=Image.new('RGBA',(n,n*len(frames)));[im.paste(f,(0,i*n)) for i,f in enumerate(frames)];im.save(T/(name+'.png'));dump(T/(name+'.png.mcmeta'),{'animation':{'frametime':time,'frames':list(range(len(frames)))}})
anim('led_activity',[Image.new('RGBA',(16,16),c) for c in ((62,104,49,255),(117,224,72,255))],5)
tex('rack_window',lambda d,n:(d.line((1,1,4,4),fill=(190,205,215,105)),d.line((1,2,3,4),fill=(190,205,215,65)),d.line((n-5,n-3,n-3,n-5),fill=(180,198,210,60))),base=(93,122,145,35))
tex('glass',lambda d,n:(d.line((1,1,4,4),fill=(160,183,201,255)),d.line((n-5,n-3,n-3,n-5),fill=(160,183,201,255))))
tex('patch_cable_mesh',lambda d,n:None,base=(44,116,177,255))
tex('patch_plug_mesh',lambda d,n:(d.line((0,0,n-1,0),fill=(153,159,169,255)),d.line((0,n-1,n-1,n-1),fill=(68,75,86,255))),base=(111,118,129,255))
# Strip texture is aligned along the arm, collars are painted not protruding geometry.
for kind,c in [('data',(36,139,218)),('power',(222,151,39))]:
 def strip(d,n,c=c):
  d.rectangle((0,0,n-1,n-1),fill=(43,45,52));d.line((0,1,n-1,1),fill=(69,71,80),width=2);d.line((0,n-2,n-1,n-2),fill=(29,31,37),width=2);d.rectangle((0,6,n-1,9),fill=c);d.line((0,6,n-1,6),fill=tuple(min(255,i+26) for i in c));d.rectangle((5,0,6,n-1),fill=(78,80,90));d.rectangle((6,2,6,n-3),fill=(59,61,70))
 tex(kind+'_strip',strip)
 tex(kind+'_cap',lambda d,n,c=c:(framed(d,n,(48,50,58)),d.rectangle((4,4,11,11),fill=(28,30,35)),d.rectangle((6,6,9,9),fill=c)))
 tex(kind+'_hub',lambda d,n,c=c:(framed(d,n,(48,50,58)),d.rectangle((6,6,9,9),fill=c)))
for name,on in [('relay_open',False),('relay_closed',True)]:tex(name,lambda d,n,on=on:(framed(d,n,(51,53,60)),d.rectangle((3,3,12,12),fill=(30,32,38)),d.line((4,9,7,9),fill=(221,152,41),width=2),d.line((7,9,11,9 if on else 5),fill=(221,152,41),width=2),d.point((12,3),fill=(119,215,80) if on else (191,71,60))))
# Terminal faces, separate atlas animation used by baked screen.
tex('terminal_lower_off',lambda d,n:(plate(d,n),d.rectangle((3,3,12,12),fill=(65,66,74)),[d.rectangle((x,4,x+1,11),fill=(24,26,32)) for x in (4,7,10)],d.point((12,12),fill=(41,48,43))))
tex('terminal_lower_on',lambda d,n:(plate(d,n),d.rectangle((3,3,12,12),fill=(65,66,74)),[d.rectangle((x,4,x+1,11),fill=(24,26,32)) for x in (4,7,10)],d.point((12,12),fill=(114,216,76))))
tex('terminal_screen_off',lambda d,n:(framed(d,n,(12,16,23),(37,39,45),(62,65,75)),d.rectangle((3,3,n-4,n-4),fill=(8,9,12))))
frames=[]
for i in range(4):
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);framed(d,16,(14,33,63),(37,39,45),(69,75,87));d.rectangle((2,2,13,13),fill=(15,48,94));d.line((4,5,11,5),fill=(64,144,203));d.line((4,7,9,7),fill=(43,98,151));d.rectangle((4,11,11,12),fill=(24,61,108));
 for j in range(4):d.point((5+j*2,11),fill=(95,204,253) if j==i else (40,96,153))
 frames.append(im)
anim('terminal_screen_on',frames,6)
tex('terminal_keyboard',lambda d,n:(framed(d,n,(54,56,65)),[d.rectangle((x,y,x+2,y+2),fill=(126,129,141)) for x in (3,7,11) for y in (3,7,11)]))
# Radio silhouette on one double-sided cutout plane (boom + four panels).
def antenna(d,n):
 d.rectangle((1,15,30,17),fill=(63,65,73));d.line((1,15,30,15),fill=(100,102,111));
 for x,y,h in [(3,10,12),(11,8,16),(19,5,22),(27,2,28)]:
  d.rectangle((x,y,x+3,y+h-1),fill=(169,173,181),outline=(100,106,117));d.line((x,y+1,x,y+h-2),fill=(200,202,208))
tex('antenna_flat',antenna,32)
def dish(d,n):
 d.polygon([(8,1),(23,1),(30,8),(30,23),(23,30),(8,30),(1,23),(1,8)],fill=(133,138,148));d.polygon([(9,3),(22,3),(28,9),(28,22),(22,28),(9,28),(3,22),(3,9)],fill=(182,186,193));d.polygon([(9,3),(3,9),(3,22),(9,28),(11,23),(8,18),(8,11)],fill=(150,156,169));d.polygon([(22,3),(28,9),(28,22),(22,28),(20,23),(23,18),(23,11)],fill=(204,207,212));d.rectangle((13,13,18,18),fill=(81,87,100));d.line((15,15,23,24),fill=(105,111,123),width=2);d.rectangle((14,14,17,17),fill=(71,78,91))
tex('dish_flat',dish,32)
for on in (False,True):tex('station_'+('on' if on else 'off'),lambda d,n,on=on:(plate(d,n),d.rectangle((3,5,12,10),fill=(25,29,34)),d.line((4,6,11,6),fill=(68,70,80)),d.rectangle((9,3,12,3),fill=(108,218,72) if on else (43,54,43))))
# JSON helpers. Generate exact exposed surfaces of a union of axis-aligned boxes.
# All hidden interfaces are removed; boundary faces can be excluded on connected sides.
def box(a,b,t='rack_frame'):return (tuple(a),tuple(b),t)
def face_info(t,rot=0):return {'texture':'#'+t,'uv':[0,0,16,16],**({'rotation':rot} if rot else {})}
def elem(a,b,faces,rotation=None):return {'from':list(a),'to':list(b),'faces':faces,**({'rotation':rotation} if rotation else {})}
def model(name,els):
 keys={f['texture'][1:] for e in els for f in e['faces'].values()};dump(B/(name+'.json'),{'parent':'block/block','ambientocclusion':False,'textures':{**{k:'odc:blocks/'+k for k in keys},'particle':'odc:blocks/rack_frame'},'elements':els})
def simple(a,b,t,fs=F,rotation=None):return elem(a,b,{f:face_info(t) for f in fs},rotation)
def union(boxes,omit=()):
 # coordinate compression, faces merged into rectangles within each coplanar material group
 coords=[sorted({v for a,b,t in boxes for v in (a[k],b[k])}) for k in range(3)];dims=[len(c)-1 for c in coords];grid={}
 for idx in itertools.product(*(range(n) for n in dims)):
  p=[(coords[k][i]+coords[k][i+1])/2 for k,i in enumerate(idx)]
  for a,b,t in boxes:
   if all(a[k]<=p[k]<b[k] for k in range(3)):grid[idx]=t;break
 groups={};directions=[(1,-1,'down'),(1,1,'up'),(2,-1,'north'),(2,1,'south'),(0,-1,'west'),(0,1,'east')]
 for idx,t in grid.items():
  for axis,sign,f in directions:
   nb=list(idx);nb[axis]+=sign
   if tuple(nb) in grid:continue
   plane=idx[axis]+(sign>0);value=coords[axis][plane]
   if (f,value) in omit:continue
   axes=[k for k in range(3) if k!=axis];groups.setdefault((axis,sign,f,plane,t),set()).add(tuple(idx[k] for k in axes))
 out=[]
 for (axis,sign,f,plane,t),cells in groups.items():
  axes=[k for k in range(3) if k!=axis]
  while cells:
   i,j=min(cells);i2=i+1
   while (i2,j) in cells:i2+=1
   j2=j+1
   while all((x,j2) in cells for x in range(i,i2)):j2+=1
   cells.difference_update((x,y) for x in range(i,i2) for y in range(j,j2));a=[0]*3;b=[0]*3
   for k,l,h in zip(axes,(i,j),(i2,j2)):a[k]=coords[k][l];b[k]=coords[k][h]
   value=coords[axis][plane];a[axis]=value-(.0625 if sign>0 else 0);b[axis]=value+(.0625 if sign<0 else 0)
   out.append(elem(a,b,{f:face_info(t)}))
 return out
for glass in (False,True):
 name='rack_glass' if glass else 'rack'
 for role in range(3):
  # Outer structure; no intersecting face plates and no horizontal frame at join.
  a=[]
  if role!=2:a.append(box([0,0,0],[16,1,16]))
  if role!=1:a.append(box([0,15,0],[16,16,16]))
  y0=0 if role==2 else 1;y1=16 if role==1 else 15
  a.extend([box([0,y0,0],[1.5,y1,16],'rack_side'),box([14.5,y0,0],[16,y1,16],'rack_side'),box([1.5,y0,15],[14.5,y1,16],'rack_back')])
  if glass:
   a += [box([1.5,y0,0],[2,y1,.5]),box([14,y0,0],[14.5,y1,.5]),box([13,y0+3,-.35],[13.75,min(y0+6,y1),.15],'rack_frame')]
  omit=[('up',16)] if role==1 else [('down',0)] if role==2 else []
  model(name+'_'+str(role),union(a,omit))
  if role==0:model(name+'_item',union(a)+([simple([2,1,.6],[14,15,.7],'glass',['north','south'])] if glass else []))
# Cable models: core missing faces where arms meet, arms missing both end faces.
D=[(1,-1),(1,1),(2,-1),(2,1),(0,-1),(0,1)]
def cable(kind,mask,relay=False,opened=False):
 lo,hi=(4,12) if relay else (6,10);base='relay_open' if opened else 'relay_closed';
 # relay has larger core; keep its faces at arm junction since most of the panel is exposed, arm omits its inside end.
 faces={}
 for i,f in enumerate(F):
  if not relay and mask>>i&1:continue
  if not relay and mask in (3,12,48):
   axis=1 if mask==3 else 2 if mask==12 else 0
   faces[f]=face_info(kind+'_strip',90 if axis==1 or axis==2 and i<2 else 0)
  else:faces[f]=face_info(base if relay else kind+'_cap')
 out=[elem([lo]*3,[hi]*3,faces)] if faces else []
 for i,(axis,sign) in enumerate(D):
  if not(mask>>i&1):continue
  a=[6]*3;b=[10]*3;a[axis]=0 if sign<0 else hi;b[axis]=lo if sign<0 else 16
  side={}
  for j,f in enumerate(F):
   if j//2==i//2:continue
   # Vanilla UV axes: top/bottom X/Z; side faces horizontal axis then Y.
   rot=90 if axis==1 or axis==2 and j<2 else 0
   side[f]=face_info(kind+'_strip',rot)
  out.append(elem(a,b,side))
 return out
for name,kind,relay in [('data_cable','data',False),('power_cable','power',False),('power_relay','power',True)]:
 for p in B.glob(name+'_*.json'):p.unlink()
 variants={}
 for mask in range(64):
  for on in ([False,True] if relay else [False]):
   m=f'{name}_{mask:02x}'+('_open' if on else '_closed' if relay else '');model(m,cable(kind,mask,relay,on))
   state=','.join(f'{f}={str(bool(mask>>F.index(f)&1)).lower()}' for f in sorted(F));
   for active in ([on] if relay else [False,True]):variants['active='+str(active).lower()+','+state]={'model':'odc:'+m}
 dump(S/(name+'.json'),{'variants':variants});model(name+'_item',cable(kind,12,relay,False)+[simple([6,6,0],[10,10,.0625],kind+'_cap',['north']),simple([6,6,15.9375],[10,10,16],kind+'_cap',['south'])]);dump(I/(name+'.json'),{'parent':'odc:block/'+name+'_item'})
# Terminal, 2 actual blocks; upper and lower models meet only at side/back seam.
for on in (False,True):
 suffix='on' if on else 'off';low=union([box([0,0,0],[16,1,16]),box([0,1,1],[2,16,16],'rack_side'),box([14,1,1],[16,16,16],'rack_side'),box([2,1,2],[14,16,16],'terminal_lower_'+suffix)], [('up',16)])
 # Use explicit front face texture; full cuboid side texture is fine, front stays in one plane.
 up=union([box([0,15,0],[16,16,16]),box([0,0,1],[2,15,16],'rack_side'),box([14,0,1],[16,15,16],'rack_side'),box([2,0,3],[14,15,16],'rack_back')],[('down',0)])
 # monitor set in aperture, keyboard slight angled slab inside footprint
 up += [simple([2.5,4,1.5],[13.5,14,2.5],'terminal_screen_'+suffix,['north','east','west','up','down']),simple([2.5,1.2,-.25],[13.5,2.2,3.5],'terminal_keyboard',F,{'origin':[8,1.7,2],'axis':'x','angle':-22.5,'rescale':False})]
 model('computer_lower_'+suffix,low);model('computer_upper_'+suffix,up)
 if not on:
  both=low+[dict(e,**{'from':[e['from'][0],e['from'][1]+16,e['from'][2]],'to':[e['to'][0],e['to'][1]+16,e['to'][2]],**({'rotation':dict(e['rotation'],origin=[e['rotation']['origin'][0],e['rotation']['origin'][1]+16,e['rotation']['origin'][2]])} if 'rotation' in e else {})}) for e in up];model('computer_item',both)
dump(S/'computer.json',{'variants':{f'active={a},facing={f},upper={u}':{'model':'odc:computer_'+('upper' if u=='true' else 'lower')+'_'+m,'y':i*90} for a,m in [('false','off'),('true','on')] for i,f in enumerate(H) for u in ('false','true')}});dump(I/'computer.json',{'parent':'odc:block/computer_item','display':{'gui':{'rotation':[30,225,0],'translation':[0,-4,0],'scale':[.42,.42,.42]},'ground':{'translation':[0,2,0],'scale':[.2,.2,.2]},'fixed':{'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.4,.4,.4]}}})
for name in ('radio_station','satellite_station'):
 for on in (False,True):
  suffix='on' if on else 'off';els=union([box([2,0,2],[14,1,14]),box([3,1,3],[13,6,13],'station_'+suffix),box([5,6,5],[11,7,11]),box([7,7,7],[9,16,9],'rack_frame')])
  if name=='radio_station':els += [simple([7.85,10,-5],[8.15,27,21],'antenna_flat',['east','west'])]
  else:els += [simple([-3,9,6.9],[19,31,7.1],'dish_flat',['north','south'],{'origin':[8,16,7],'axis':'x','angle':-22.5,'rescale':False})]
  model(name+'_'+suffix,els)
 dump(S/(name+'.json'),{'variants':{f'active={a},facing={f}':{'model':'odc:'+name+'_'+m,'y':i*90} for a,m in [('false','off'),('true','on')] for i,f in enumerate(H)}});dump(I/(name+'.json'),{'parent':'odc:block/'+name+'_off','display':{'gui':{'rotation':[30,225,0],'translation':[0,-2,0],'scale':[.55,.55,.55]},'ground':{'scale':[.25,.25,.25]},'fixed':{'scale':[.5,.5,.5]}}})
print('Generated 0.3.1 visual resources')
