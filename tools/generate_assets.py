"""Generate original 16x16 vanilla-style textures, block/item models and recipes. Requires Pillow."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'
for path in ['textures/blocks','textures/items','models/block','models/item','blockstates','recipes','lang']:
    folder=root/path; folder.mkdir(parents=True,exist_ok=True)
    for f in folder.iterdir():
        if f.is_file() and f.suffix in ('.json','.png','.mcmeta','.lang'):f.unlink()
def js(path,obj): (root/path).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
rng=random.Random(208)
def base(color):
    im=Image.new('RGBA',(16,16));p=im.load()
    for y in range(16):
        for x in range(16):
            delta=rng.choice([-3,0,0,0,3]);p[x,y]=tuple(max(0,min(255,c+delta)) for c in color)+(255,)
    return im
def texture(name,color,draw=None):
    im=base(color); d=ImageDraw.Draw(im)
    if draw: draw(d)
    im.save(root/f'textures/blocks/{name}.png');return im
def rect(d,box,c):d.rectangle(box,fill=c)
def steel(d):
    d.line((0,0,15,0),fill='#72787b');d.line((0,0,0,15),fill='#62696c');d.line((15,1,15,15),fill='#343a3c');d.line((1,15,15,15),fill='#343a3c')
def side(d):
    steel(d);rect(d,(2,2,13,13),'#454b4e');d.line((2,2,13,2),fill='#555c5e');d.line((2,13,13,13),fill='#303638');d.point((3,3),fill='#858b8d');d.point((12,12),fill='#858b8d')
def vent(d):
    steel(d)
    for y in (3,6,9,12):rect(d,(3,y,12,y+1),'#303537');d.line((3,y+2,12,y+2),fill='#666c6e')
def rail(d):
    steel(d)
    for y in (2,5,8,11,14):rect(d,(6,y,9,y),'#282d30')
texture('steel',(77,83,86),steel);texture('side',(63,69,71),side);texture('vent',(65,71,74),vent);texture('rail',(81,87,90),rail);texture('dark',(32,36,38))
# Front face uses exactly twelve by three texels. On state has a constant green power LED and a subtle activity blink.
def serverface(gpu,on,blink=False):
    im=Image.new('RGBA',(16,16),'#40474a');d=ImageDraw.Draw(im)
    rect(d,(0,0,11,2),'#555c5f');rect(d,(1,1,10,1),'#2f3537');rect(d,(0,0,0,2),'#7a8285');rect(d,(11,0,11,2),'#71797c')
    if gpu:
        for x in (2,4,6):d.point((x,1),fill='#8a9294')
    else:rect(d,(2,1,7,1),'#757e81')
    d.point((9,1),fill='#76e18b' if on else '#304437');d.point((10,1),fill=('#91eac2' if blink else '#4c9566') if on else '#333c39')
    return im
for kind,gpu in [('cpu',False),('gpu',True)]:
    serverface(gpu,False).save(root/f'textures/blocks/{kind}_face_off.png')
    im=Image.new('RGBA',(16,32));im.paste(serverface(gpu,True,False),(0,0));im.paste(serverface(gpu,True,True),(0,16));im.save(root/f'textures/blocks/{kind}_face_on.png')
    js(f'textures/blocks/{kind}_face_on.png.mcmeta',{'animation':{'frametime':10,'frames':[0,0,1,0]}})
im=Image.new('RGBA',(16,16),(0,0,0,0));d=ImageDraw.Draw(im);d.rectangle((0,0,15,15),outline='#778083');d.line((1,1,5,1),fill='#a4abad');d.line((1,2,1,4),fill='#939a9d');d.line((14,11,14,14),fill='#697477');im.save(root/'textures/blocks/glass.png')
def screen(d,on):
    rect(d,(0,0,15,15),'#4e5659');rect(d,(1,1,14,14),'#171e20' if not on else '#1b302c')
    if on:
        for y,n in [(3,8),(5,11),(7,6),(10,10)]:rect(d,(3,y,n,y),'#78bfa0')
        d.point((12,12),fill='#87dfaa')
for on in (False,True):texture('screen_'+('on' if on else 'off'),(48,54,56),lambda d,on=on:screen(d,on))
def keys(d):
    steel(d)
    for y in (3,6,9):
        for x in (2,5,8,11):rect(d,(x,y,x+1,y+1),'#303638')
    rect(d,(5,12,11,13),'#343a3c')
texture('keyboard',(83,89,91),keys)
def battery(d):
    steel(d);rect(d,(3,3,12,12),'#2d3637');rect(d,(4,4,11,6),'#75c692');
    for x in (4,7,10):rect(d,(x,8,x+1,10),'#8fae9d')
    rect(d,(5,13,10,13),'#b5a278')
texture('battery',(63,70,72),battery)
def workbench(d):
    steel(d);rect(d,(3,3,12,12),'#374548');d.rectangle((4,4,11,11),outline='#84989a');rect(d,(6,6,9,9),'#242c2f')
texture('workbench',(72,79,82),workbench)
def ports(d,on):
    steel(d);rect(d,(2,3,13,12),'#333a3c')
    for x in (3,6,9,12):
        rect(d,(x,5,x+1,6),'#171d1f');rect(d,(x,9,x+1,10),'#171d1f');d.point((x,4),fill='#75d58d' if on else '#3e5143');d.point((x,8),fill='#5b9c72' if on else '#3e5143')
for on in (False,True):texture('switch_'+('on' if on else 'off'),(75,81,84),lambda d,on=on:ports(d,on))
def station(d,on):
    steel(d);rect(d,(3,3,12,12),'#343b3e');rect(d,(4,4,11,7),'#20292b');rect(d,(5,5,10,5),'#7cda99' if on else '#48584b');rect(d,(5,9,10,10),'#555e60')
for on in (False,True):texture('station_'+('on' if on else 'off'),(75,81,84),lambda d,on=on:station(d,on))
texture('data',(70,85,90),lambda d:(steel(d),rect(d,(2,6,13,9),'#72909b')))
texture('power',(83,79,68),lambda d:(steel(d),rect(d,(2,6,13,9),'#b6a16c')))
def grate(d):
    steel(d);rect(d,(2,2,13,13),'#292e30')
    for x in (3,6,9,12):rect(d,(x,2,x+1,13),'#878e90')
    for y in (4,11):rect(d,(2,y,13,y),'#60676a')
texture('floor_grate',(96,101,103),grate);texture('wall_panel',(78,83,85),side)

# Block geometry deliberately kept simple, using whole-pixel block coordinates where practical.
faces=('north','south','east','west','up','down')
texnames=[p.stem for p in (root/'textures/blocks').glob('*.png')]
textures={n:'odc:blocks/'+n for n in sorted(texnames)}
def cube(a,b,t='steel',uv=None,**over):
    return {'from':a,'to':b,'faces':{f:{'texture':'#'+over.get(f,t),'uv':(uv or {}).get(f,[0,0,16,16])} for f in faces}}
def model(name,parts):js(f'models/block/{name}.json',{'parent':'block/block','ambientocclusion':True,'textures':{'particle':'odc:blocks/steel',**textures},'elements':parts})
def itemparent(name,modelname):js(f'models/item/{name}.json',{'parent':f'odc:block/{modelname}'})
rotations=[('north',0),('east',90),('south',180),('west',270)]
def rack(glass):
    p=[cube([0,0,1],[16,1,16],'steel'),cube([0,15,1],[16,16,16],'steel'),cube([0,1,1],[2,15,16],'side',north='rail'),cube([14,1,1],[16,15,16],'side',north='rail'),cube([2,1,14],[14,15,16],'vent',north='dark')]
    if glass:p.extend([cube([1,1,0],[2,15,1],'steel'),cube([14,1,0],[15,15,1],'steel'),cube([2,1,0],[14,15,.5],'glass'),cube([13,6,0],[14,10,.6],'rail')])
    return p
def tray(bay,value):
    y=11.5-bay*3.5;kind='gpu' if value in (2,4) else 'cpu';state='on' if value>=3 else 'off'
    return [cube([2,y,1.5],[14,y+3,14],'steel',uv={'north':[0,0,12,3]},north=f'{kind}_face_{state}',up='vent')]
for i in range(4):
    for value in range(1,5):model(f'bay{i}_{value}',tray(i,value))
for glass in (False,True):
    name='rack_glass' if glass else 'rack';model(name,rack(glass));model(name+'_item',rack(glass)+sum([tray(i,3 if i%2==0 else 4) for i in range(4)],[]));itemparent(name,name+'_item')
    multi=[]
    for facing,rot in rotations:
        def entry(cond,m):return {'when':{'facing':facing,**cond},'apply':{'model':'odc:'+m,'y':rot}}
        multi.append(entry({},name))
        for i in range(4):
            for value in range(1,5):multi.append(entry({f'bay{i}':str(value)},f'bay{i}_{value}'))
    js(f'blockstates/{name}.json',{'multipart':multi})
# Simple machine models with power-dependent fronts. All orientation metadata remains compatible with 0.1.
def machine(name,parts):
    for active in (False,True):model(name+('_on' if active else '_off'),parts(active))
    js(f'blockstates/{name}.json',{'variants':{f'active={str(a).lower()},facing={f}':{'model':'odc:'+name+('_on' if a else '_off'),'y':r} for a in (False,True) for f,r in rotations}})
    itemparent(name,name+'_on')
machine('assembler',lambda on:[cube([0,0,0],[16,10,16],'side',north='vent'),cube([0,10,0],[16,12,16],'steel',up='workbench'),cube([2,12,12],[14,15,15],'steel',north='workbench')])
machine('computer',lambda on:[cube([1,0,1],[15,9,15],'side',north='vent'),cube([0,9,0],[16,10,16],'steel'),cube([7,10,10],[9,12,12],'steel'),cube([3,11,9],[13,16,12],'steel',north='screen_'+('on' if on else 'off')),cube([4,10,2],[12,11,6],'keyboard')])
machine('creative_battery',lambda on:[cube([0,0,0],[16,16,16],'side',north='battery',up='vent')])
machine('network_switch',lambda on:[cube([0,0,0],[16,16,16],'side',north='switch_'+('on' if on else 'off'),up='vent')])
machine('network_station',lambda on:[cube([0,0,0],[16,10,16],'side',north='station_'+('on' if on else 'off'),up='vent'),cube([7,10,7],[9,16,9],'steel'),cube([3,14,7],[13,15,9],'rail'),cube([4,13,7],[5,16,9],'steel'),cube([11,13,7],[12,16,9],'steel')])
for name,t in [('data_cable','data'),('power_cable','power')]:
    model(name+'_core',[cube([6,6,6],[10,10,10],t)]);model(name+'_arm',[cube([7,7,0],[9,9,6],t),cube([6,6,0],[10,10,2],'steel')]);model(name+'_item',[cube([7,7,0],[9,9,16],t),cube([6,6,0],[10,10,3],'steel'),cube([6,6,13],[10,10,16],'steel')]);itemparent(name,name+'_item')
    mp=[{'apply':{'model':'odc:'+name+'_core'}}]
    for f,x,y in [('north',0,0),('east',0,90),('south',0,180),('west',0,270),('up',270,0),('down',90,0)]:mp.append({'when':{f:'true'},'apply':{'model':'odc:'+name+'_arm','x':x,'y':y}})
    js('blockstates/'+name+'.json',{'multipart':mp})
for name,t in [('raised_floor','floor_grate'),('wall_panel','wall_panel')]:
    js('models/block/'+name+'.json',{'parent':'block/cube_all','textures':{'all':'odc:blocks/'+t}});js('blockstates/'+name+'.json',{'variants':{'normal':{'model':'odc:'+name}}});itemparent(name,name)
# Items remain clear even at vanilla 16px resolution.
def item(name,kind,pro=False):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);gold='#c7ae6b';board='#4d6860' if not pro else '#476572';dark='#273437';metal='#a2aaac'
    if kind=='cpu':
        rect(d,(3,3,12,12),dark);rect(d,(4,4,11,11),board);rect(d,(5,5,10,10),metal);rect(d,(6,6,9,9),'#718993' if pro else '#737d80');d.point((9,6),fill='#c6d0d4' if pro else '#737d80')
        for x in (4,7,10):rect(d,(x,2,x+1,3),gold);rect(d,(x,12,x+1,13),gold);rect(d,(2,x,3,x+1),gold);rect(d,(12,x,13,x+1),gold)
    elif kind=='gpu':
        rect(d,(1,4,14,11),dark);rect(d,(2,5,13,10),board);rect(d,(1,3,2,12),metal);rect(d,(5,11,11,12),gold)
        for x in ((5,10) if pro else (8,)):
            rect(d,(x-2,5,x+2,9),'#343b3e');d.point((x,7),fill=metal);d.point((x-1,6),fill='#7a8589');d.point((x+1,8),fill='#7a8589')
    elif kind=='board':
        rect(d,(2,1,13,14),dark);rect(d,(3,2,12,13),board);rect(d,(5,3,9,7),metal);rect(d,(6,4,8,6),'#5e6a71')
        for x in (4,11):rect(d,(x,9,x,12),gold)
        for y in (9,11):rect(d,(6,y,9,y),'#20282b')
        d.point((3,3),fill=gold);d.point((12,12),fill=gold)
    elif kind=='ram':
        rect(d,(1,5,14,10),dark);rect(d,(2,6,13,9),board)
        for x in (3,6,9,12):rect(d,(x,6,x+1,8),'#20282b')
        for x in range(2,14,2):d.point((x,10),fill=gold)
    elif kind=='disk':
        rect(d,(3,2,12,13),dark);rect(d,(4,3,11,12),metal if not pro else '#566166')
        if pro:rect(d,(5,4,10,10),'#30383b');rect(d,(6,5,9,6),board)
        else:d.ellipse((5,4,10,9),fill='#707b80',outline='#b7bfc0');d.point((7,6),fill='#323a3c');d.line((9,9,7,7),fill='#dde0df')
        rect(d,(5,12,10,12),gold)
    elif kind.startswith('chassis') or kind=='server':
        h={'chassis1':4,'chassis2':6,'chassis4':8,'server':6}[kind];y=8-h//2;rect(d,(1,y,14,y+h),dark);rect(d,(2,y+1,13,y+h-1),'#656e71');rect(d,(4,y+1,11,y+h-1),'#363e41');rect(d,(2,y+1,2,y+h-1),metal);rect(d,(13,y+1,13,y+h-1),metal)
        if kind=='server':rect(d,(5,y+2,9,y+2),'#858e91');d.point((11,y+2),fill='#70d58d')
    else:
        rect(d,(4,4,11,11),dark);rect(d,(5,5,10,10),board);rect(d,(6,6,9,9),metal)
        for x in (4,7,10):rect(d,(x,3,x+1,4),gold);rect(d,(x,11,x+1,12),gold)
    im.save(root/f'textures/items/{name}.png');js('models/item/'+name+'.json',{'parent':'item/generated','textures':{'layer0':'odc:items/'+name}})
for n,k,p in [('cpu_basic','cpu',False),('cpu_pro','cpu',True),('gpu_basic','gpu',False),('gpu_pro','gpu',True),('chassis_1u','chassis1',False),('chassis_2u','chassis2',False),('chassis_4u','chassis4',False),('precision_chip','chip',True),('server','server',True),('board_basic','board',False),('board_pro','board',True),('ram_basic','ram',False),('ram_pro','ram',True),('hdd_basic','disk',False),('ssd_pro','disk',True)]:item(n,k,p)
def recipe(name,pattern,keys,count=1):js('recipes/'+name+'.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':v} for k,v in keys.items()},'result':{'item':'odc:'+name,'count':count}})
i='minecraft:iron_ingot';g='minecraft:gold_ingot';r='minecraft:redstone';s='minecraft:glass';c='odc:precision_chip'
recipe('rack',['IRI','I I','IRI'],{'I':i,'R':'minecraft:iron_bars'});recipe('rack_glass',['IGI','IRI','IGI'],{'I':i,'G':s,'R':'odc:rack'});recipe('assembler',['IRI','IDI','III'],{'I':i,'R':r,'D':'minecraft:crafting_table'});recipe('computer',['IGI','CRC','III'],{'I':i,'G':s,'C':'odc:cpu_basic','R':r});recipe('data_cable',['III','RRR','III'],{'I':i,'R':r},12);recipe('power_cable',['III','GGG','III'],{'I':i,'G':g},12);recipe('cpu_basic',['GRG','RIR','GRG'],{'G':g,'R':r,'I':i});recipe('cpu_pro',['GCG','RBR','GCG'],{'G':g,'R':r,'C':c,'B':'odc:cpu_basic'});recipe('gpu_basic',['III','GCG','IRI'],{'I':i,'G':g,'C':'odc:cpu_basic','R':r});recipe('gpu_pro',['ICI','GBG','IRI'],{'I':i,'C':c,'G':g,'B':'odc:gpu_basic','R':r});recipe('chassis_1u',['III','I I'],{'I':i});recipe('chassis_2u',['III','I I','III'],{'I':i});recipe('chassis_4u',['IRI','I I','IRI'],{'I':i,'R':r});recipe('raised_floor',['BBB','III'],{'B':'minecraft:iron_bars','I':i},8);recipe('wall_panel',['III','ISI','III'],{'I':i,'S':'minecraft:stone'},8)
recipe('network_switch',['III','RCR','IGI'],{'I':i,'R':r,'C':'odc:cpu_basic','G':g});recipe('network_station',[' B ','BRB','ICI'],{'B':'minecraft:iron_bars','R':r,'I':i,'C':'odc:cpu_basic'})
recipe('board_basic',['GRG','RIR','GGG'],{'G':g,'R':r,'I':i})
recipe('board_pro',['GCG','RBR','GRG'],{'G':g,'C':c,'R':r,'B':'odc:board_basic'})
recipe('ram_basic',['RRR','GIG'],{'R':r,'G':g,'I':i})
recipe('ram_pro',['CRC','GBG'],{'C':c,'R':r,'G':g,'B':'odc:ram_basic'})
recipe('hdd_basic',['III','IRI','IGI'],{'I':i,'R':r,'G':g})
recipe('ssd_pro',['ICI','GRG','III'],{'I':i,'C':c,'G':g,'R':r})
blocks={'rack':('Open Server Rack','Открытая серверная стойка'),'rack_glass':('Glass Server Rack','Стеклянная серверная стойка'),'assembler':('Server Assembly Station','Станок сборки серверов'),'computer':('DataOS Control Computer','Управляющий компьютер DataOS'),'creative_battery':('Creative Energy Source','Творческий источник энергии'),'data_cable':('Data Cable','Сетевой кабель'),'power_cable':('Power Cable','Силовой кабель'),'raised_floor':('Raised Floor Grate','Решётка фальшпола'),'wall_panel':('Industrial Wall Panel','Промышленная стеновая панель'),'network_switch':('Network Switch','Сетевой коммутатор'),'network_station':('Network Station','Сетевая станция')}
items={'cpu_basic':('CPU T1','Процессор T1'),'cpu_pro':('CPU T2','Процессор T2'),'gpu_basic':('GPU T1','Видеокарта T1'),'gpu_pro':('GPU T2','Видеокарта T2'),'chassis_1u':('Server Chassis 1U','Корпус сервера 1U'),'chassis_2u':('Server Chassis 2U','Корпус сервера 2U'),'chassis_4u':('Server Chassis 4U','Корпус сервера 4U'),'precision_chip':('Precision Chip','Прецизионный чип'),'server':('Assembled Server','Собранный сервер')}
items.update({'board_basic':('Basic Motherboard','Базовая материнская плата'),'board_pro':('Pro Motherboard','Продвинутая материнская плата'),'ram_basic':('RAM 4 GB','ОЗУ 4 ГБ'),'ram_pro':('RAM 16 GB','ОЗУ 16 ГБ'),'hdd_basic':('HDD 128 GB','HDD 128 ГБ'),'ssd_pro':('SSD 512 GB','SSD 512 ГБ')})
gui={
'rack':('ODC / SERVER RACK','ODC / СЕРВЕРНАЯ СТОЙКА'),'assembly':('ODC / SERVER ASSEMBLY','ODC / СБОРКА СЕРВЕРА'),'control':('DataOS / TASK MANAGER','DataOS / ДИСПЕТЧЕР ЗАДАЧ'),'market':('Market','Магазин'),'switch':('ODC / NETWORK SWITCH','ODC / КОММУТАТОР'),'station':('ODC / NETWORK STATION','ODC / СЕТЕВАЯ СТАНЦИЯ'),
'assemble':('Assemble','Собрать'),'link':('Select PC','Выбрать ПК'),'start':('Start','Пуск'),'stop':('Stop','Стоп'),'back':('Back','Назад'),'buy':('Buy','Купить'),'unlinked':('No controller','Нет контроллера'),'link_lost':('Controller unreachable','Нет связи с ПК'),'linked':('PC: %s, %s, %s','ПК: %s, %s, %s'),'occupied':('Bay %s','Слот %s'),'chassis':('CHASSIS','КОРПУС'),'output':('OUTPUT','ВЫХОД'),'gpu_slots':('GPU EXPANSION','СЛОТЫ GPU'),'assembly_hint':('1U = 1 GPU / 2U = 2 / 4U = 4','1U = 1 GPU / 2U = 2 / 4U = 4'),
'online':('SYSTEM ONLINE','СИСТЕМА В СЕТИ'),'offline':('NO POWER','НЕТ ПИТАНИЯ'),'wallet':('Wallet: %s NTC','Баланс: %s NTC'),'cluster':('Racks: %s / servers: %s','Стойки: %s / серверы: %s'),'network':('WAN %s | LAN %s | used %s Mb/s','WAN %s | LAN %s | занято %s Mb/s'),'assign_hint':('Assign servers in the rack GUI.','Назначай серверы в интерфейсе стоек.'),'market_hint':('Select an offer below.','Выбери товар ниже.'),'market_price':('PRICE IN NTC','ЦЕНА В NTC'),'capacity':('Bandwidth: %s / %s Mb/s','Пропускная способность: %s / %s Mb/s'),'switch_hint':('Adds shared local switching capacity.','Добавляет пропускную способность LAN.'),'station_hint':('Adds global uplink bandwidth.','Добавляет пропускную способность WAN.'),'network_hint':('Connect with data and power cables.','Подключи сетевой и силовой кабели.'),
 'task.0':('Idle','Простой'),'task.1':('Mining','Майнинг'),'task.2':('Bank','Банк'),'task.3':('AI','ИИ'),
 'status.0':('Idle','Простой'),'status.1':('No controller','Нет ПК'),'status.2':('PC offline','ПК выключен'),'status.3':('Task stopped','Задача стоп'),'status.4':('Incompatible','Нет ресурса'),'status.5':('No bandwidth','Нет сети'),'status.6':('No power','Нет энергии'),'status.7':('Running','Работает'),'status.8':('Throttled','Ограничен')}
gui.update({'hardware':('SERVER HARDWARE','КОМПЛЕКТУЮЩИЕ'),'edit':('Parts','Детали'),'board':('BOARD','ПЛАТА'),'drives':('DRIVES','ДИСКИ'),'choose_chassis':('Insert a chassis first.','Установите корпус.'),'assemble_help':('Incomplete servers can be fitted in a rack.','Неполный сервер можно поставить в стойку.'),'maintenance_help':('Maintenance: tasks resume after closing.','Обслуживание: задачи возобновятся при выходе.'),'boot.0':('Hardware ready','Комплектующие готовы'),'boot.1':('Missing motherboard','Нет материнской платы'),'boot.2':('Missing CPU','Нет процессора'),'boot.3':('Missing RAM','Нет оперативной памяти'),'boot.4':('Missing drive','Нет накопителя'),'boot.5':('Invalid component','Неверный компонент'),'boot.6':('Slot capacity exceeded','Превышена вместимость слотов'),'status.9':('Service','Сервис'),'status.10':('Incomplete','Не собран')})
for lang,idx in [('en_us',0),('ru_ru',1)]:
    lines=[f'tile.odc.{k}.name={v[idx]}' for k,v in blocks.items()]+[f'item.odc.{k}.name={v[idx]}' for k,v in items.items()]+[f'gui.odc.{k}={v[idx]}' for k,v in gui.items()];(root/f'lang/{lang}.lang').write_text('\n'.join(lines)+'\n',encoding='utf8')
