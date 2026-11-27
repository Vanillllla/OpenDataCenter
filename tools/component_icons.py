from PIL import Image, ImageDraw
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'
# Flat pixel sprites, no perspective render, limited palette and visible tier markers.
colors=['#78b783','#75b9d3','#d3ae62','#b986cb']
ids={'board':['board_basic','board_pro','board_advanced','board_dual'],'cpu':['cpu_basic','cpu_pro','cpu_advanced'],'ram':['ram_basic','ram_pro','ram_advanced','ram_elite'],'gpu':['gpu_basic','gpu_pro','gpu_advanced','gpu_elite'],'hdd':['hdd_basic','hdd_pro','hdd_advanced','hdd_elite'],'ssd':['ssd_basic','ssd_pro','ssd_advanced'],'nvme':['nvme_basic','nvme_pro','nvme_advanced'],'psu':['psu_basic','psu_pro','psu_advanced'],'nic':['nic_basic','nic_pro'],'pcie':['nvme_pcie']}
def icon(kind,t):
 im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im);a=colors[t-1]
 def r(x,y,X,Y,c):
  if X>=x and Y>=y:d.rectangle((x,y,X,Y),fill=c)
 def l(points,c,w=1):d.line(points,fill=c,width=w)
 def board(x,y,X,Y):
  r(x,y,X,Y,'#14291e');r(x+1,y+1,X-1,Y-1,'#315c3a');l([(x+1,Y-1),(x+1,y+1),(X-1,y+1)],'#68966a');r(x+2,y+2,x+3,y+3,'#b8c59b')
 def chip(x,y,w,h):
  r(x,y,x+w-1,y+h-1,'#151c20');r(x+1,y+1,x+w-2,y+h-2,'#39454a');l([(x+1,y+h-2),(x+1,y+1),(x+w-2,y+1)],'#626d6b');
  for k in range(x+1,x+w-1,2):r(k,y-1,k,y-1,'#a8b29b');r(k,y+h,k,y+h,'#a8b29b')
 def socket(x,y,w):
  r(x,y,x+w-1,y+w-1,'#20282d');r(x+1,y+1,x+w-2,y+w-2,'#b7bfc1');r(x+2,y+2,x+w-3,y+w-3,'#707d86');r(x+3,y+3,x+w-4,y+w-4,'#39434c');l([(x+2,y+w-3),(x+2,y+2),(x+w-3,y+2)],'#e0e4df')
 def teeth(x,y,X):
  r(x,y,X,y+2,'#a98740');
  for k in range(x,X+1,2):r(k,y,k,y+2,'#e4ca78')
 def metal(x,y,X,Y):
  r(x,y,X,Y,'#171c22');r(x+1,y+1,X-1,Y-1,'#69727b');l([(x+1,Y-1),(x+1,y+1),(X-1,y+1)],'#b2b7b8');r(x+3,y+3,X-3,Y-3,'#363f48')
 def tiermark(x,y):
  for i in range(t):r(x+3*i,y,x+3*i+1,y+1,a)
 if kind=='board':
  board(2,2,29,29)
  l([(4,15),(8,15),(8,20),(21,20),(21,24),(27,24)],'#9baa68');l([(5,26),(5,18),(13,18),(13,5),(21,5)],'#81ad6b')
  if t==4:socket(5,5,10);socket(5,17,10)
  else:socket(6,7,13);chip(6,23,7,4)
  for i in range(2 if t==1 else 4):r(21+i*2,5,21+i*2,17,'#192a21');r(21+i*2,6,21+i*2,15,'#b8c48a')
  for i in range(min(t,3)):r(17,20+i*3,27,21+i*3,'#1c2224');r(19,20+i*3,26,20+i*3,'#94a4a1')
  if t>=3:r(3,3,5,8,'#c4c9c4')
  tiermark(16,27)
 elif kind=='cpu':
  board(4,4,27,27);socket(7,7,18)
  r(10,10,21,21,'#b4bdc2');l([(10,20),(10,10),(20,10)],'#e2e6e3');r(12,16,19,17,'#7c8a91');tiermark(11,19)
  for i in range(6,27,3):r(i,3,i,4,'#c8b56b');r(i,27,i,28,'#c8b56b')
 elif kind in ('ram','nvme'):
  if kind=='ram':
   board(1,10,30,22);teeth(3,22,28);r(15,22,16,25,'#14291e')
   for x in [4,11,18,25]:chip(x,13,4,6)
   tiermark(3,19)
  else:
   board(3,10,28,21);teeth(25,12,27);r(25,12,28,19,'#b5a05c');r(25,15,28,16,'#27382d');chip(7,12,7,7);chip(16,12,6,7);r(4,14,5,16,'#b6c4be');tiermark(9,19)
 elif kind in ('gpu','nic','pcie'):
  board(4,7,29,24);metal(1,5,4,26);teeth(8,24,22);r(14,24,15,27,'#202a25')
  if kind=='gpu':
   metal(6,8,28,23)
   for x in ([17] if t==1 else [12,22]):
    d.ellipse((x-5,10,x+5,20),fill='#151a20',outline='#98a0a3');r(x-2,13,x+2,17,'#606e76');l([(x-4,12),(x-1,14),(x-4,17)],'#505c64');l([(x+4,12),(x+1,14),(x+4,17)],'#505c64')
   tiermark(7,21)
  elif kind=='nic':
   chip(13,10,10,10);r(5,10,10,17,'#b1b8b8');r(6,11,9,15,'#222b31');r(7,16,8,16,a);tiermark(16,21)
  else:
   board(7,11,27,21);chip(10,13,7,6);chip(19,13,5,6);r(26,13,26,17,'#b9a05f');r(7,19,8,19,'#c0c9bc')
 elif kind in ('hdd','ssd'):
  metal(6,3,25,28);teeth(10,28,21)
  if kind=='hdd':
   d.ellipse((9,6,22,19),fill='#a4b3bc',outline='#d4dede');d.ellipse((13,10,18,15),fill='#5f707a');r(15,12,16,13,'#d5dbda');l([(21,22),(17,14)],'#e0e3d9',2);r(20,22,22,24,'#818d94');tiermark(9,25)
  else:
   r(9,7,22,19,'#b2bec1');r(9,7,22,9,a);r(11,12,20,13,'#4d5961');r(11,15,17,16,'#68757a');tiermark(9,24)
  for x,y in [(7,4),(24,4),(7,27),(24,27)]:r(x,y,x,y,'#cbd0cf')
 elif kind=='psu':
  metal(3,5,28,26);d.ellipse((6,9,20,23),fill='#1b232a',outline='#a0a8a9');d.ellipse((10,13,16,19),outline='#79868b');r(12,15,14,17,'#8e9da0');l([(7,16),(19,16)],'#627078');l([(13,10),(13,22)],'#627078');r(22,10,26,16,'#151b1f');r(23,11,24,12,'#b9aa77');r(23,19,26,20,a);tiermark(5,24)
 return im
for kind,names in ids.items():
 for t,name in enumerate(names,1):icon(kind,t).save(root/'textures/items'/f'{name}.png')
# Preview actual sprites with no smoothing.
rows=list(ids.items());out=Image.new('RGB',(4*112+145,len(rows)*105),'#c6c6c6');dr=ImageDraw.Draw(out)
for y,(kind,names) in enumerate(rows):
 dr.text((10,y*105+44),kind.upper(),fill='#222222')
 for x,name in enumerate(names):
  im=Image.open(root/'textures/items'/f'{name}.png').resize((96,96),Image.Resampling.NEAREST);out.paste(im,(145+x*112,y*105+4),im)
out.save(root.parents[3]/'component-icons-preview.png')
