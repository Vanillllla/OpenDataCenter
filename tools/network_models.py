"""Generate Stage 2 network equipment geometry and 16x16 textures (no external assets)."""
import json,random
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'
blocks=root/'models/block'
faces=('north','south','east','west','up','down')
def write(p,d):p.write_text(json.dumps(d,indent=2)+'\n',encoding='utf8')
def tex(name,base,detail=None):
    im=Image.new('RGB',(16,16));r=random.Random(name)
    for y in range(16):
        for x in range(16):
            n=r.choice([-5,-3,0,0,2,5]);im.putpixel((x,y),tuple(max(0,min(255,v+n)) for v in base))
    if detail:detail(ImageDraw.Draw(im))
    im.save(root/'textures/blocks'/f'{name}.png')
def panel(d):
    d.rectangle((0,0,15,15),outline='#46535b');d.line((1,1,14,1),fill='#b6bfc2');d.line((1,1,1,14),fill='#b6bfc2');d.rectangle((3,4,12,11),fill='#313e45');
    for x in range(4,12,2):d.line((x,5,x,10),fill='#74858d')
tex('net_metal',(127,140,146),lambda d:d.line((0,0,15,0),fill='#c3ccd0'))
tex('net_dark',(65,77,85),panel)
tex('net_reflector',(177,187,191),lambda d:d.line((0,0,15,0),fill='#d2dadd'))
tex('net_sector',(202,207,208),lambda d:(d.rectangle((0,0,15,15),outline='#8e9ca3'),d.line((2,1,2,14),fill='#e5e9e9')))
tex('net_led_on',(91,177,106));tex('net_led_off',(54,75,65))
for tier,col in [(1,'#82b9d1'),(2,'#91c78c'),(3,'#c2a1e0')]:
    for active in (False,True):
        def screen(d,tier=tier,active=active,col=col):
            d.rectangle((0,0,15,15),fill='#333e48');d.rectangle((1,1,14,10),fill='#172a35' if active else '#283039');d.rectangle((1,11,14,14),fill='#56656e');
            if active:
                for x,y in [(3,3),(11,3),(7,8)]:d.rectangle((x-1,y-1,x+1,y+1),fill=col)
                d.line((3,4,3,6,11,6,11,4),fill=col);d.line((7,6,7,7),fill=col)
            for i in range(tier):d.rectangle((3+i*3,12,4+i*3,13),fill=col)
        tex(f'net_screen_{tier}_{"on" if active else "off"}',(65,75,83),screen)
        def front(d,active=active,col=col):
            d.rectangle((0,0,15,15),fill='#5c6a73');d.rectangle((1,1,14,14),outline='#a0aab0');
            for x in (2,6,10):d.rectangle((x,4,x+2,7),fill='#202c36');d.point((x+1,3),fill=col if active else '#49534f')
            d.rectangle((2,10,13,12),fill='#303b43');d.line((3,11,12,11),fill='#80909a')
        tex(f'net_ports_{tier}_{"on" if active else "off"}',(65,75,83),front)
def el(a,b,t='metal',special=None,omit=()):
    return {'from':a,'to':b,'faces':{f:{'texture':'#'+(special or {}).get(f,t),'uv':[0,0,16,16]} for f in faces if f not in omit}}
def remove_hidden(els):
    axes={'down':(1,0),'up':(1,1),'north':(2,0),'south':(2,1),'west':(0,0),'east':(0,1)}
    for i,a in enumerate(els):
        if 'rotation' in a:continue
        for b in els[i+1:]:
            if 'rotation' in b:continue
            for fa in list(a['faces']):
                axis,side=axes[fa]; rest=[j for j in range(3) if j!=axis];fb=next(f for f,v in axes.items() if v==(axis,1-side))
                if fb not in b['faces'] or fa not in a['faces'] or abs(a['to' if side else 'from'][axis]-b['from' if side else 'to'][axis])>1e-8:continue
                if all(a['from'][j]>=b['from'][j]-1e-8 and a['to'][j]<=b['to'][j]+1e-8 for j in rest):
                    del a['faces'][fa]
                    if all(abs(a[k][j]-b[k][j])<1e-8 for k in ('from','to') for j in rest):del b['faces'][fb]
                elif all(b['from'][j]>=a['from'][j]-1e-8 and b['to'][j]<=a['to'][j]+1e-8 for j in rest):del b['faces'][fb]
    return els
def model(name,els,active,extra=None):
    els=remove_hidden(els)
    ts={'particle':'odc:blocks/net_dark','metal':'odc:blocks/net_metal','dark':'odc:blocks/net_dark','reflector':'odc:blocks/net_reflector','sector':'odc:blocks/net_sector','led':'odc:blocks/net_led_'+('on' if active else 'off')};ts.update(extra or {})
    write(blocks/f'{name}.json',{'parent':'block/block','textures':ts,'elements':els})
def base():return [el([2,0,2],[14,2,14],'dark'),el([5,2,5],[11,4,11]),el([7,4,7],[9,13,9])]
def radio():
    e=base()+[el([6,12,2],[10,14,14],'metal'),el([3,12.5,3],[6,13.5,4]),el([10,12.5,3],[13,13.5,4]),el([2,12.5,7],[6,13.5,8]),el([10,12.5,7],[14,13.5,8]),el([1,12.5,12],[6,13.5,13]),el([10,12.5,12],[15,13.5,13]),el([6,14,10],[10,15,13],'dark'),el([3,1,1.75],[4.5,1.6,2],'led')]
    # central mast joins boom at its underside, not overlapping coplanar surfaces
    e[2]['to'][1]=12
    return e
def dish():
    e=[el([2,0,2],[14,2,14],'dark'),el([5,2,5],[11,4,11]),el([7,4,8],[9,8,10.6]),el([6.75,7.8,10.5],[9.25,10,12.5],'dark')]
    # five-by-five stepped circular reflector, front facing north; rim closer to feed
    for iy in range(5):
        for ix in range(5):
            dx,dy=ix-2,iy-2;r2=dx*dx+dy*dy
            if r2>5:continue
            z=10 if r2==0 else 9.5 if r2<=2 else 8.5
            e.append(el([2+ix*2.4,4.3+iy*2.3,z],[2+(ix+1)*2.4,4.3+(iy+1)*2.3,11.0],'reflector'))
    e += [el([7.6,9.6,4],[8.4,10.4,10],'metal'),el([7,9,3],[9,11,4],'dark'),el([7.4,9.4,2.7],[8.6,10.6,3],'led'),el([3,1,1.75],[4.5,1.6,2],'led')]
    return e
def tower(part):
    e=[]
    if part==0:e.extend([el([2,0,2],[14,3,14],'dark'),el([3,3,3],[13,7,13],'dark'),el([4,4,2.7],[6,5,3],'led')]);bottom=7
    else:bottom=0
    top=16 if part!=2 else 14
    for x in (5,10):
        for z in (5,10):e.append(el([x,bottom,z],[x+1,top,z+1],omit=tuple(f for f in ('down','up') if (f=='down' and part>0) or (f=='up' and part<2))))
    for y in range(bottom+2,top-1,4):
        for z in (5,10):e.append(el([6,y,z],[10,y+1,z+1]))
        for x in (5,10):e.append(el([x,y,6],[x+1,y+1,10]))
    for z in (5,10):
        for y in range(bottom,top-4,5):
            # diagonal thin bracing, legal block-model rotation
            b=el([7.2,y+.2,z+.15],[8.1,y+4.6,z+.85]);b['rotation']={'origin':[7.65,y+2.4,z+.5],'axis':'z','angle':45,'rescale':False};e.append(b)
    if part==2:
        e.extend([el([4,4,3],[12,5,13]),el([6,5,2],[10,14,3],'sector'),el([6,5,13],[10,14,14],'sector'),el([2,5,6],[3,14,10],'sector'),el([13,5,6],[14,14,10],'sector'),el([6,14,6],[10,15,10],'dark'),el([7,15,7],[9,16,9],'led')])
    return e
def terminal(tier):
    return [el([1,0,1],[15,2,15],'dark'),el([2,2,3],[14,11,14],'dark',{'north':'ports'}),el([3,11,5],[13,12,13]),el([3,12,6],[13,16,12],'dark',{'north':'screen'}),el([3,11,2],[13,11.6,5],'metal')]
rot={'north':0,'east':90,'south':180,'west':270}
for id,fun in [('radio_station',radio),('satellite_station',dish),('telecom_tower',None),('network_terminal',lambda:terminal(1)),('network_terminal_t2',lambda:terminal(2)),('network_terminal_t3',lambda:terminal(3))]:
    variants={};parts=range(3) if fun is None else [None]
    tier=1 if id=='network_terminal' else 2 if id.endswith('_t2') else 3
    for active in (False,True):
        state='on' if active else 'off'
        for part in parts:
            name=id+('' if part is None else f'_{part}')+'_'+state
            extra={}
            if id.startswith('network_terminal'):extra={k:f'odc:blocks/net_{k}_{tier}_{state}' for k in ('screen','ports')}
            model(name,fun() if fun else tower(part),active,extra)
            for f,angle in rot.items():variants[f'active={str(active).lower()},facing={f}'+('' if part is None else f',part={part}') ]={'model':'odc:'+name,'y':angle}
    write(root/'blockstates'/f'{id}.json',{'variants':variants})
    write(root/'models/item'/f'{id}.json',{'parent':'odc:block/'+id+('_0' if fun is None else '')+'_off','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]}}})
# Compact tower inventory model, shows all three segments scaled to one block.
import copy
els=[]
for part in range(3):
    for e in tower(part):
        e=copy.deepcopy(e)
        for key in ('from','to'):e[key]=[8+(e[key][0]-8)/2,(e[key][1]+part*16)/3,8+(e[key][2]-8)/2]
        # anisotropic rescale of rotated braces is not exact; use simple straight braces for item only
        e.pop('rotation',None);els.append(e)
model('telecom_tower_item',els,False)
write(root/'models/item/telecom_tower.json',{'parent':'odc:block/telecom_tower_item','display':{'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.9,.9,.9]}}})
