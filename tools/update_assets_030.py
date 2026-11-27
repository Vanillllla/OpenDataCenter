"""Create 0.3.0 resources using 16px industrial textures and block cuboids."""
from pathlib import Path
import json, shutil
from PIL import Image, ImageDraw
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'
B=R/'models/block'; I=R/'models/item'; T=R/'textures/blocks'; IT=R/'textures/items'; S=R/'blockstates'; C=R/'recipes'
def dump(p,x):p.write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
def read(p):return json.loads(p.read_text())
def rename(old,new):
    for folder in (B,I,S,C):
        for p in list(folder.glob(old+'*.json')):
            q=p.with_name(p.name.replace(old,new,1));txt=p.read_text().replace('odc:'+old,'odc:'+new).replace('odc:block/'+old,'odc:block/'+new);q.write_text(txt);p.unlink()
rename('assembler','workbench');rename('network_station','radio_station')
for folder in (B,I,S,C):
    for p in list(folder.glob('network_switch*.json')):p.unlink()
for p in B.glob('bay*.json'):p.unlink()
(I/'server.json').unlink(missing_ok=True);(IT/'server.png').unlink(missing_ok=True)

def model(name,boxes):
    keys={t for _,_,t in boxes}; tex={k:'odc:blocks/'+k for k in keys};tex['particle']='odc:blocks/steel'
    elems=[]
    for a,b,t in boxes:
        elems.append({'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}})
    dump(B/(name+'.json'),{'parent':'block/block','ambientocclusion':True,'textures':tex,'elements':elems})
def box(a,b,t='steel'):return (a,b,t)
def item_block(name,modelname=None):dump(I/(name+'.json'),{'parent':'odc:block/'+(modelname or name)})
faces=['north','east','south','west']
# Solid sides, open front, thin rails; no duplicate central top/bottom plate.
for glass in (False,True):
    name='rack_glass' if glass else 'rack'
    for role in range(3):
        a=[]
        if role!=2:a.append(box([0,0,1],[16,1,16]))
        if role!=1:a.append(box([0,15,1],[16,16,16]))
        a += [box([0,0,1],[1,16,16],'side'),box([15,0,1],[16,16,16],'side'),box([1,0,15],[15,16,16],'vent'),box([1,0,1],[2,16,2],'rail'),box([14,0,1],[15,16,2],'rail')]
        if glass:
            # Door edging, glass rendered separately. Handle lies fully outside panel z=.033 (~.53px).
            a += [box([0,0,0],[1.5,16,0.5]),box([14.5,0,0],[16,16,.5]),box([13,6,0],[14.2,10,.35],'rail')]
            if role!=2:a.append(box([1.5,0,0],[14.5,1,.5]))
            if role!=1:a.append(box([1.5,15,0],[14.5,16,.5]))
        model(name+'_'+str(role),a)
        if role==0:
            # Inventory preview has perforated cutout glass, TESR supplies translucent panel in world.
            item_model=a+([box([1.5,1,.25],[14.5,15,.5],'glass')] if glass else [])
            model(name+'_item',item_model)
    dump(S/(name+'.json'),{'variants':{f'facing={f},part={r}':{'model':'odc:'+name+'_'+str(r),'y':i*90} for i,f in enumerate(faces) for r in range(3)}})
    item_block(name,name+'_item')
    (B/(name+'.json')).unlink(missing_ok=True)
# Cable bundle and armored cable. Each arm canonical north (rotated by old multipart).
for name in ('data_cable','power_cable'):
    if name=='data_cable':
        core=[box([6,6,6],[10,10,10],'data'),box([6,6,6],[10,10,7],'rail'),box([6,9,6],[10,10,10],'rail')]
        arm=[box([6,6,0],[7.5,7.5,6],'data'),box([8.5,6,0],[10,7.5,6],'data'),box([7.25,8.25,0],[8.75,9.75,6],'data'),box([6,6,2],[10,10,3],'rail')]
    else:
        core=[box([6,6,6],[10,10,10],'power')];arm=[box([6,6,0],[10,10,6],'power'),box([6,6,2],[10,10,3],'rail')]
    model(name+'_core',core);model(name+'_arm',arm);opposite=[([a[0],a[1],16-b[2]],[b[0],b[1],16-a[2]],t) for a,b,t in arm];model(name+'_item',core+arm+opposite);item_block(name,name+'_item')
for active in (False,True):
    model('power_relay_'+('on' if active else 'off'),[box([4,4,4],[12,12,12],'steel'),box([5,5,3.8],[11,11,4],'relay_open' if active else 'relay_closed'),box([5,12,5],[11,12.5,11],'relay_open' if active else 'relay_closed')])
parts=read(S/'power_cable.json')['multipart']; arms=[x for x in parts if 'when' in x];dump(S/'power_relay.json',{'multipart':[{'when':{'active':a},'apply':{'model':'odc:power_relay_'+m}} for a,m in [('false','off'),('true','on')]]+arms});item_block('power_relay','power_relay_off')
# antenna: separate multi-element Yagi, electronics base and feed; angular offset dish built from stepped facets.
for name in ('radio_station','satellite_station'):
    for active in (False,True):
        a=[box([2,0,2],[14,1,14],'steel'),box([4,1,4],[12,4,12],'side'),box([5,2,3.75],[11,3.5,4],'station_on' if active else 'station_off'),box([7,4,7],[9,12,9],'rail')]
        if name=='radio_station':
            a += [box([7.25,11,1],[8.75,12,15],'rail'),box([6,10,6],[10,13,10],'side')]
            for z,w in [(2,12),(5,11),(8,10),(11,9),(14,8)]:a.append(box([8-w/2,12,z-.4],[8+w/2,13,z+.4],'steel'))
            a += [box([8.5,4,9],[9,10,9.5],'data'),box([5,4,7],[7,6,9],'steel'),box([9,4,7],[11,6,9],'steel')]
        else:
            # Octagonal, stepped concave reflector: dark outer rim, inner facets and a recessed centre.
            a += [box([6,8,7],[10,12,10],'rail'),box([5,8,5],[11,12,7],'dish_center')]
            for x0,y0,x1,y1 in [(3,8,5,12),(11,8,13,12),(5,6,11,8),(5,12,11,14),(3,6,5,8),(11,6,13,8),(3,12,5,14),(11,12,13,14)]:a.append(box([x0,y0,3.5],[x1,y1,6],'dish'))
            for x0,y0,x1,y1 in [(1,8,3,12),(13,8,15,12),(5,14,11,16),(5,4,11,6),(3,6,5,7),(11,6,13,7),(3,13,5,14),(11,13,13,14)]:a.append(box([x0,y0,2],[x1,y1,3.5],'dish_rim'))
            a += [box([7.5,5.5,0],[8.5,6.5,5],'rail'),box([7.5,6.5,0],[8.5,9,1],'rail'),box([6.5,9,0],[9.5,11,2],'side'),box([7,9.5,2],[9,10.5,3],'rail')]
        model(name+('_on' if active else '_off'),a)
    dump(S/(name+'.json'),{'variants':{f'active={a},facing={f}':{'model':'odc:'+name+'_'+m,'y':i*90} for a,m in [('false','off'),('true','on')] for i,f in enumerate(faces)}});item_block(name,name+'_off')
# New textures in strict vanilla scale.
def texture(name,base,draw):
    im=Image.new('RGBA',(16,16),base);d=ImageDraw.Draw(im);draw(d);im.save(T/(name+'.png'))
def frame(d):d.rectangle((0,0,15,15),outline=(42,49,53));d.line((1,1,14,1),fill=(133,145,149));d.line((1,14,14,14),fill=(43,51,55))
def server(d,u,ready):
    frame(d)
    if not ready:d.rectangle((2,3,13,12),fill=(32,40,43));d.rectangle((3,4,12,11),outline=(81,91,96));return
    if u==1:
        for x in (2,5,8):d.rectangle((x,4,x+2,11),fill=(38,45,48));d.line((x,5,x+2,5),fill=(92,104,109))
    elif u==2:
        for x in (2,6,10):d.rectangle((x,3,x+3,12),fill=(35,43,47));d.ellipse((x,4,x+3,11),fill=(74,87,92));d.line((x+1,6,x+2,9),fill=(29,36,40))
    else:
        for x in (2,8):
            for y in (2,8):d.rectangle((x,y,x+5,y+5),fill=(31,38,41));d.ellipse((x+1,y+1,x+4,y+4),fill=(83,94,99));d.point((x+2,y+2),fill=(147,156,159))
    d.rectangle((14,3,14,12),fill=(126,137,141))
for u in (1,2,4):
    for ready in (False,True):texture(f'server_{u}_'+('ready' if ready else 'empty'),(87,98,104,255),lambda d,u=u,r=ready:server(d,u,r))
for st in (1,2):
    def sw(d,st=st):
        frame(d)
        for p in range(4 if st==1 else 8):
            x=int(2+p*(3 if st==1 else 1.65));d.rectangle((x,5,x+(1 if st==2 else 2),11),fill=(23,30,34));d.point((x,6),fill=(161,145,96))
    texture('switch_panel_'+str(st),(80,94,99,255),sw)
texture('led_on',(112,218,136,255),lambda d:d.rectangle((0,0,15,15),outline=(60,136,76)))
texture('led_off',(42,56,49,255),lambda d:d.rectangle((0,0,15,15),outline=(27,37,33)))
texture('rack_window',(112,164,178,38),lambda d:(d.line((0,0,15,15),fill=(185,219,224,60),width=1),d.line((0,1,14,15),fill=(185,219,224,35))))
texture('dish',(146,157,163,255),lambda d:(d.line((0,0,15,0),fill=(189,198,202)),d.line((0,15,15,15),fill=(101,113,118)),d.line((7,0,7,15),fill=(130,143,149))))
texture('dish_rim',(96,110,118,255),lambda d:(d.line((0,0,15,0),fill=(157,172,178)),d.line((0,15,15,15),fill=(57,69,76))))
texture('dish_center',(113,126,133,255),lambda d:(d.rectangle((0,0,15,15),outline=(79,91,99)),d.line((7,0,7,15),fill=(95,108,115)),d.line((0,7,15,7),fill=(95,108,115))))
for name,active in [('relay_open',True),('relay_closed',False)]:
    def relay(d,active=active):
        frame(d);d.rectangle((2,4,13,12),fill=(29,37,41));d.line((3,9,7,9),fill=(182,160,95),width=2);d.line((7,9,12,5 if active else 9),fill=(192,198,201),width=2);d.rectangle((12,2,13,3),fill=(201,81,73) if active else (98,193,120))
    texture(name,(80,90,95,255),relay)
# Create icons with clear silhouettes, using existing tier icons as foundations where appropriate.
new={'board_advanced':('board_pro',(76,126,107)),'ram_advanced':('ram_pro',(80,141,115)),'hdd_pro':('hdd_basic',(150,160,169)),'ssd_basic':('ssd_pro',(88,108,117)),'nvme_basic':('ram_basic',(63,112,81)),'nvme_pro':('ram_pro',(68,134,108)),'psu_basic':('hdd_basic',(90,97,103)),'psu_pro':('hdd_basic',(108,120,126)),'psu_advanced':('hdd_basic',(135,150,158)),'nic_basic':('gpu_basic',(72,111,92)),'nic_pro':('gpu_pro',(90,138,116)),'switch_basic':('chassis_1u',(79,109,114)),'switch_pro':('chassis_1u',(104,141,145)),'ethernet_patch':('ram_basic',(79,128,170))}
for name,(base,col) in new.items():
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    if name.startswith('psu'):
        d.rectangle((2,3,13,12),fill=col,outline=(31,38,41));d.rectangle((4,5,9,10),fill=(30,38,42));d.ellipse((5,6,8,9),fill=(118,130,135));d.rectangle((11,6,12,8),fill=(180,160,100))
    elif name.startswith('switch'):
        d.rectangle((1,5,14,10),fill=col,outline=(36,46,51));n=4 if name.endswith('basic') else 6
        for j in range(n):d.rectangle((2+j*2,7,3+j*2,8),fill=(22,30,35));d.point((2+j*2,6),fill=(105,180,121))
    elif name.startswith('nic'):
        d.rectangle((2,4,13,11),fill=col,outline=(31,48,39));d.rectangle((6,6,9,9),fill=(32,38,40));d.rectangle((12,5,14,9),fill=(148,160,165));d.line((4,12,10,12),fill=(191,168,96))
    elif name.startswith('nvme'):
        d.rectangle((1,5,14,10),fill=col,outline=(27,48,38));d.rectangle((4,6,7,9),fill=(35,42,45));d.rectangle((9,6,12,9),fill=(35,42,45));d.line((14,6,14,9),fill=(190,163,95));d.point((2,7),fill=(192,196,187))
    elif name=='ethernet_patch':
        d.line([(2,3),(2,11),(4,13),(11,13),(13,11),(13,5)],fill=(36,57,73),width=3);d.line([(2,3),(2,11),(4,13),(11,13),(13,11),(13,5)],fill=col,width=1);d.rectangle((1,1,3,4),fill=(157,173,177));d.rectangle((12,2,14,5),fill=(157,173,177))
    else:
        im=Image.open(IT/(base+'.png')).convert('RGBA');d=ImageDraw.Draw(im);d.rectangle((12,1,14,3),fill=col,outline=(46,60,60))
    im.save(IT/(name+'.png'));dump(I/(name+'.json'),{'parent':'item/generated','textures':{'layer0':'odc:items/'+name}})
for u,y0,y1 in [(1,6,10),(2,5,11),(4,4,12)]:
    name=f'chassis_{u}u';im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.rectangle((1,y0,14,y1),fill=(54,66,72),outline=(25,34,39));d.line((2,y0+1,13,y0+1),fill=(130,142,148));d.rectangle((3,y0+2,12,y1-1),fill=(26,35,40));d.line((2,y0+1,2,y1-1),fill=(141,152,156));d.line((13,y0+1,13,y1-1),fill=(141,152,156));im.save(IT/(name+'.png'))
    ready=im.copy();d=ImageDraw.Draw(ready);d.rectangle((3,y0+2,12,y1-1),fill=(78,91,98))
    for y in range(y0+2,y1,2):d.line((4,y,10,y),fill=(33,43,49))
    d.point((12,y0+2),fill=(118,213,139));ready.save(IT/(name+'_ready.png'));dump(I/(name+'_ready.json'),{'parent':'item/generated','textures':{'layer0':'odc:items/'+name+'_ready'}});m=read(I/(name+'.json'));m['overrides']=[{'predicate':{'odc:ready':1.0},'model':'odc:item/'+name+'_ready'}];dump(I/(name+'.json'),m)
# New recipes. All use ordinary Minecraft ingredients or ODC hardware, never generated electricity.
def recipe(name,pattern,keys,count=1):dump(C/(name+'.json'),{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v if ':' in v else 'odc:'+v} for k,v in keys.items()},'result':{'item':'odc:'+name,'count':count}})
recipe('board_advanced',['DCD','RBR','GCG'],{'D':'minecraft:diamond','C':'precision_chip','R':'minecraft:redstone','B':'board_pro','G':'minecraft:gold_ingot'})
recipe('ram_advanced',['GCG','RPR','GCG'],{'G':'minecraft:gold_ingot','C':'precision_chip','R':'minecraft:redstone','P':'ram_pro'})
recipe('hdd_pro',['III','HDH','IRI'],{'I':'minecraft:iron_ingot','H':'hdd_basic','D':'minecraft:gold_ingot','R':'minecraft:redstone'})
recipe('ssd_basic',['ICI','RGR','III'],{'I':'minecraft:iron_ingot','C':'precision_chip','R':'minecraft:redstone','G':'minecraft:gold_ingot'})
recipe('nvme_basic',['GCG','RSR',' G '],{'G':'minecraft:gold_ingot','C':'precision_chip','R':'minecraft:redstone','S':'ssd_basic'})
recipe('nvme_pro',['DCD','RNR','GCG'],{'D':'minecraft:diamond','C':'precision_chip','R':'minecraft:redstone','N':'nvme_basic','G':'minecraft:gold_ingot'})
recipe('psu_basic',['III','CRC','III'],{'I':'minecraft:iron_ingot','C':'power_cable','R':'minecraft:redstone'})
recipe('psu_pro',['IGI','CPC','IGI'],{'I':'minecraft:iron_ingot','G':'minecraft:gold_ingot','C':'power_cable','P':'psu_basic'})
recipe('psu_advanced',['ICI','DPD','ICI'],{'I':'minecraft:iron_ingot','C':'precision_chip','D':'minecraft:diamond','P':'psu_pro'})
recipe('nic_basic',['GRG','ICI',' E '],{'G':'minecraft:gold_ingot','R':'minecraft:redstone','I':'minecraft:iron_ingot','C':'cpu_basic','E':'ethernet_patch'})
recipe('nic_pro',['GCG','RNR',' E '],{'G':'minecraft:gold_ingot','C':'precision_chip','R':'minecraft:redstone','N':'nic_basic','E':'ethernet_patch'})
recipe('switch_basic',['III','ECE','IRI'],{'I':'minecraft:iron_ingot','E':'ethernet_patch','C':'cpu_basic','R':'minecraft:redstone'})
recipe('switch_pro',['IGI','NSN','ICI'],{'I':'minecraft:iron_ingot','G':'minecraft:gold_ingot','N':'nic_pro','S':'switch_basic','C':'precision_chip'})
recipe('ethernet_patch',['G G','RRR','G G'],{'G':'minecraft:gold_ingot','R':'minecraft:redstone'},4)
recipe('power_relay',['IRI','CPC','IRI'],{'I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'power_cable','P':'minecraft:lever'})
recipe('satellite_station',['IBI','BCB','IRI'],{'I':'minecraft:iron_ingot','B':'minecraft:iron_bars','C':'precision_chip','R':'radio_station'})
# Existing translations retained where still applicable. New GUI labels and part names localized.
entries={'workbench':('Server Workbench','Рабочий стол серверов'),'radio_station':('Radio Network Station','Радиосетевая станция'),'satellite_station':('Satellite Network Station','Спутниковая станция'),'power_relay':('Power Relay','Силовое реле'),'board_advanced':('Advanced Server Motherboard','Продвинутая серверная плата'),'ram_advanced':('RAM T3','Оперативная память T3'),'hdd_pro':('HDD T2','Жёсткий диск T2'),'ssd_basic':('SATA SSD T1','SATA SSD T1'),'nvme_basic':('NVMe T1','NVMe T1'),'nvme_pro':('NVMe T2','NVMe T2'),'psu_basic':('PSU T1','Блок питания T1'),'psu_pro':('PSU T2','Блок питания T2'),'psu_advanced':('PSU T3','Блок питания T3'),'nic_basic':('NIC T1','Сетевая карта T1'),'nic_pro':('NIC T2','Сетевая карта T2'),'switch_basic':('Rack Switch T1','Стоечный коммутатор T1'),'switch_pro':('Rack Switch T2','Стоечный коммутатор T2'),'ethernet_patch':('Ethernet Patch Cable','Короткий Ethernet-кабель')}
blocks={'workbench','radio_station','satellite_station','power_relay'}
for idx,lang in enumerate(('en_us','ru_ru')):
    p=R/'lang'/(lang+'.lang');old=dict(line.split('=',1) for line in p.read_text(encoding='utf8').splitlines() if '=' in line)
    for k in ('tile.odc.assembler.name','tile.odc.network_station.name','tile.odc.network_switch.name','item.odc.server.name'):old.pop(k,None)
    old['item.odc.hdd_basic.name']=('HDD T1 (512 GB)','HDD T1 (512 ГБ)')[idx]
    old['item.odc.ssd_pro.name']=('SATA SSD T2 (1 TB)','SATA SSD T2 (1 ТБ)')[idx]
    old['item.odc.board_pro.name']=('Server Motherboard','Серверная материнская плата')[idx]
    old['itemGroup.odc']='OpenDataCenter'
    for n,labels in entries.items():old[('tile' if n in blocks else 'item')+'.odc.'+n+'.name']=labels[idx]
    for k,labels in {'assembly':('Server Workbench','Рабочий стол'),'no_switch':('Install a rack switch to connect servers','Установите коммутатор'),'boot.7':('Missing PSU','Нет блока питания'),'start_all':('Mine all','Запуск'),'stop_all':('Stop all','Стоп'),'refresh':('Refresh','Обнов.'),'port_hint':('Select port, then Link','Порт, затем +P / -P'),'on_short':('On','Вкл'),'off_short':('Off','Выкл'),'mine_short':('Mine','Майн'),'stop_short':('Stop','Стоп')}.items():old['gui.odc.'+k]=labels[idx]
    p.write_text('\n'.join(k+'='+v for k,v in sorted(old.items()))+'\n',encoding='utf8')
