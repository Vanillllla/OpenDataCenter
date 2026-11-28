"""Dependency-free structural checks for generated Minecraft resource files."""
from pathlib import Path
import json,struct,itertools,re
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/odc'
def read(p):return json.loads(p.read_text(encoding='utf8'))
checks=0
def check(ok,msg):
    global checks
    checks+=1
    if not ok: raise AssertionError(msg)
for f in root.rglob('*.json'):read(f)
for f in root.rglob('*.mcmeta'):read(f)
blocks={'rack','rack_glass','workbench','computer','creative_battery','data_cable','power_cable','power_relay','raised_floor','wall_panel','radio_station','satellite_station','fiber_cable','network_terminal','network_terminal_t2','network_terminal_t3','telecom_tower','transformer_t1','transformer_t2','transformer_t3'}
items={'chassis_1u','chassis_2u','chassis_4u','precision_chip','switch_basic','switch_pro','switch_2p_100','switch_2p_500','switch_2p_1000','switch_2p_10000','switch_4p_1000','switch_4p_10000','switch_8p_10000','switch_8p_50000','ethernet_patch','ethernet_patch_t2','ethernet_patch_t3'}
catalog=(root.parents[4]/'src/main/java/odc/Part.java').read_text(encoding='utf8')
for family,drive,name in re.findall(r'^        (board|cpu|ram|gpu|nic|psu|drive|fan|cooler)\((?:Kind\.([A-Z]+), )?"([^"]+)"',catalog,re.M):
    items.add(re.sub('[^a-z0-9]+','_',((drive or family)+'_'+name).lower()))
chassis_catalog=(root.parents[4]/'src/main/java/odc/ChassisSpec.java').read_text(encoding='utf8')
items.update(re.findall(r'^    [A-Z_0-9]+\("(chassis_[a-z0-9_]+)"',chassis_catalog,re.M))
check(set(p.stem for p in (root/'models/item').glob('*.json')) == blocks|items|{'chassis_1u_ready','chassis_2u_ready','chassis_4u_ready'},'item model registry')
for f in root.rglob('*.png'):
    b=f.read_bytes();check(b[:8]==b'\x89PNG\r\n\x1a\n',f'PNG header {f.name}');w,h=struct.unpack('>II',b[16:24]);check(w in (16,32,64) and h%w==0 and h<=64,f'texture resolution {f.name}');
    meta=f.with_name(f.name+'.mcmeta')
    if h>w:
        check(meta.exists(),f'animation metadata {f.name}');anim=read(meta)['animation'];check(all(isinstance(i,int) and 0<=i<h//w for i in anim['frames']),f'animation frames {f.name}')
    else:check(not meta.exists(),f'unexpected animation metadata {f.name}')
def model_ref(name):
    if name.startswith('odc:'):
        local=name.split(':',1)[1];local=local if '/' in local else 'block/'+local
        check((root/'models'/f'{local}.json').exists(),f'missing model {name}')
def tex_ref(name):
    if name.startswith('odc:'):check((root/'textures'/f'{name[4:]}.png').exists(),f'missing texture {name}')
for f in (root/'models').rglob('*.json'):
    m=read(f);model_ref(m.get('parent',''));textures=m.get('textures',{})
    for t in textures.values():tex_ref(t)
    for override in m.get('overrides',[]):model_ref(override['model'])
    for e in m.get('elements',[]):
        check(all(-16<=a<b<=32 for a,b in zip(e['from'],e['to'])),f'cuboid bounds {f.name}')
        for face,info in e['faces'].items():
            tex=info['texture'];check(not tex.startswith('#') or tex[1:] in textures,f'undefined model texture {f.name}:{tex}')
            check(len(info.get('uv',[]))==4 and all(0<=v<=16 for v in info['uv']),f'UV bounds {f.name}')
for f in (root/'blockstates').glob('*.json'):
    data=read(f)
    for variant in data.get('variants',{}).values():model_ref(variant['model'])
    for part in data.get('multipart',[]):model_ref(part['apply']['model'])
for name in ('rack','rack_glass'):
    data=read(root/'blockstates'/f'{name}.json')['variants']
    check(set(data)=={f'facing={f},part={r}' for f in ('north','east','south','west') for r in range(3)},'rack state coverage')
    for role in range(3):
        model=read(root/'models/block'/f'{name}_{role}.json');els=model['elements']
        if role==1:check(not any('up' in e['faces'] and e['to'][1]==16 for e in els),'no top seam faces')
        if role==2:check(not any('down' in e['faces'] and e['from'][1]==0 for e in els),'no bottom seam faces')
for name in ('data_cable','power_cable','power_relay','fiber_cable'):
    data=read(root/'blockstates'/f'{name}.json')['variants']
    props=sorted(('down','up','north','south','west','east','active'))
    for bits in itertools.product(('true','false'),repeat=7):
        key=','.join(f'{p}={b}' for p,b in zip(props,bits));check(key in data,f'cable state {name} {key}')
        m=read(root/'models/block'/(data[key]['model'][4:]+'.json'))
        for face,axis,side in [('down',1,0),('up',1,16),('north',2,0),('south',2,16),('west',0,0),('east',0,16)]:
            if bits[props.index(face)]=='true':check(not any(face in e['faces'] and e['from' if side==0 else 'to'][axis]==side for e in m['elements']),f'no cap at connected end {name}:{key}:{face}')
for name in ('computer','creative_battery','radio_station','satellite_station','network_terminal','network_terminal_t2','network_terminal_t3'):
    data=read(root/'blockstates'/f'{name}.json')['variants'];expected={f'active={a},facing={f}'+(f',upper={u}' if name=='computer' else '') for a in ('true','false') for f in ('north','east','south','west') for u in ('false','true')};check(set(data)==expected,f'machine states {name}')
tower=read(root/'blockstates/telecom_tower.json')['variants']
check(set(tower)=={f'active={a},facing={f},part={p}' for a in ('true','false') for f in ('north','east','south','west') for p in range(3)},'tower state coverage')
for part in range(3):
    for active in ('on','off'):
        m=read(root/'models/block'/f'telecom_tower_{part}_{active}.json')
        check(all(0<=v<=16 for e in m['elements'] for k in ('from','to') for v in e[k]),'tower segment bounds')
# The workbench is empty in inventory and has exactly one model for each case size and rotation.
bench=read(root/'blockstates/workbench.json')['variants']
check(set(bench)=={f'chassis={i},facing={f}' for i in range(4) for f in ('north','east','south','west')},'workbench state coverage')
base=read(root/'models/block/workbench_empty.json')['elements']
heights=[]
for i,u in enumerate((1,2,4),1):
    name=f'workbench_{u}u'
    check(all(bench[f'chassis={i},facing={f}']['model']==f'odc:{name}' for f in ('north','east','south','west')),'workbench model routing')
    elements=read(root/'models/block'/f'{name}.json')['elements']
    check(elements[:len(base)]==base,'workbench base preserved')
    extra=elements[len(base):];check(len(extra)==3 and all(e['from'][1]==11 and e['to'][1]<=16 for e in extra),'case rests on mat')
    heights.append(max(e['to'][1] for e in extra))
check(heights==sorted(set(heights)),'distinct case heights')
# Geometric regression: no positive-area coplanar faces in redesigned axis-aligned meshes.
# Rotated elements are checked separately by their bounding data, not by this plane test.
mesh_count=0;quad_count=0;max_cable=0
for p in (root/'models/block').glob('*.json'):
    if not p.stem.startswith(('rack_','fiber_cable_','data_cable_','power_cable_','power_relay_','computer_','radio_station_','satellite_station_','workbench_')):continue
    mesh_count+=1;m=read(p);planes={};quad_count+=sum(len(e['faces']) for e in m['elements'])
    if p.stem.startswith(('fiber_cable_','data_cable_','power_cable_')):max_cable=max(max_cable,sum(len(e['faces']) for e in m['elements']))
    for e in m['elements']:
        if 'rotation' in e:continue
        for f in e['faces']:
            axis,sign={'down':(1,0),'up':(1,1),'north':(2,0),'south':(2,1),'west':(0,0),'east':(0,1)}[f];pos=e['to' if sign else 'from'][axis];k=(axis,pos);rest=[j for j in range(3) if j!=axis];rect=tuple((e['from'][j],e['to'][j]) for j in rest)
            for old in planes.get(k,[]):check(not all(min(a[1],b[1])-max(a[0],b[0])>1e-8 for a,b in zip(rect,old)),f'coplanar overlap {p.stem} {f} {pos}')
            planes.setdefault(k,[]).append(rect)
check(max_cable<=24,'cable quad budget')
print(f'Geometry: {mesh_count} meshes, {quad_count} authored faces, max {max_cable} quads per cable; no positive-area coplanar axis-aligned overlaps.')
for u in (1,2,4):
    for state in ('ready','empty'):tex_ref(f'odc:blocks/server_{u}_{state}')
for name in ('switch_panel_1','switch_panel_2','switch_panel_2port','led_on','led_off','rack_window','led_activity','module_side','patch_cable_mesh','patch_cable_mesh_t1','patch_cable_mesh_t3','patch_plug_mesh'):tex_ref('odc:blocks/'+name)
# The switch PNG socket spans two centered pixels; inspect exact colors via optional Pillow test separately.
check(not (root/'textures/blocks/patch_up.png').exists() and not (root/'textures/blocks/patch_down.png').exists(),'legacy flat patch sprites removed')
recipes=set()
for f in (root/'recipes').glob('*.json'):
    data=read(f);pattern=data['pattern'];key=data['key'];check(len(pattern)<=3 and len(set(map(len,pattern)))==1 and len(pattern[0])<=3,f'recipe dimensions {f.name}')
    check(set(''.join(pattern).replace(' ',''))==set(key),f'recipe key mapping {f.name}')
    canonical=tuple(tuple(key[c]['item'] if c!=' ' else '' for c in row) for row in pattern);mirror=tuple(tuple(reversed(row)) for row in canonical);signature=min(canonical,mirror)
    check(signature not in recipes,f'duplicate recipe {f.name}');recipes.add(signature)
    for item in [v['item'] for v in key.values()]+[data['result']['item']]:
        if item.startswith('odc:'):check(item[4:] in blocks|items,f'unknown recipe item {item}')
check(not (root/'recipes/creative_battery.json').exists(),'creative battery must be creative-only')
langs=[]
for lang in ('en_us','ru_ru'):
    entries=dict(line.split('=',1) for line in (root/'lang'/f'{lang}.lang').read_text(encoding='utf8').splitlines() if line)
    check(all(f'tile.odc.{b}.name' in entries for b in blocks),f'block translations {lang}');check(all(f'item.odc.{i}.name' in entries for i in items),f'item translations {lang}');langs.append(entries)
check(set(langs[0])==set(langs[1]),'EN/RU translation parity')
for role in range(3):
    elements=read(root/'models/block'/f'rack_glass_{role}.json')['elements']
    check(not any(e['from'][0]>=13 and e['to'][0]<=13.75 and e['from'][2]<.16 and e['to'][2]<=.15 for e in elements),'glass rack handle rendered dynamically')
for name in sorted(i for i in items if i.startswith('switch_')):
    model=read(root/'models/item'/f'{name}.json')
    check(model.get('display',{}).get('gui',{}).get('scale')==[0.8,0.8,0.8],f'compact switch inventory icon {name}')
check(all('tooltip.odc.board.onboard_nic' in lang for lang in langs),'onboard NIC tooltip translations')
print(f'PASS: {checks} resource checks; {len(blocks)} block types; {len(items)} item types; {len(recipes)} recipes; {sum(1 for _ in root.rglob("*.png"))} PNG textures; 24 rack frame variants; 512 cable/relay states.')

for tier in range(1,4):
    name=f'transformer_t{tier}'
    on=read(root/'models/block'/f'{name}_on.json');off=read(root/'models/block'/f'{name}_off.json')
    check(len(on['elements'])==len(off['elements']) and len(on['elements'])>25,f'transformer detail {tier}')
    check(on['textures']['lamp']!=off['textures']['lamp'],f'transformer switch states {tier}')
    check(all(0<=v<=16 for e in on['elements'] for k in ('from','to') for v in e[k]),f'transformer bounds {tier}')
    check(read(root/'models/item'/f'{name}.json')['parent']==f'odc:block/{name}_off',f'transformer inventory {tier}')
print('PASS: transformer model and state checks')

# Rear openings and decorative PDU. World geometry is rendered from the master TE.
for name in ('rack','rack_glass'):
    for role in ('0','1','2','item'):
        model=read(root/'models/block'/f'{name}_{role}.json')
        els=model['elements']
        check(not any(e['from'][0]<=1.5 and e['to'][0]>=14.5 and e['from'][2]>=15 and e['to'][2]<=16 and e['to'][1]-e['from'][1]>=14 for e in els),'open rear '+name+role)
        check('rack_back' not in model['textures'],'unused rear texture '+name+role)
        if role=='item':
            check(sum(e['faces'].get('north',{}).get('texture')=='#pdu_rail' for e in els)==1,'one inventory PDU '+name)
            check(sum(e['faces'].get('north',{}).get('texture')=='#pdu_socket' for e in els)==4,'four inventory sockets '+name)
        else:
            check(not any('pdu_rail' in x for x in model['textures']),'world PDU not duplicated '+name+role)
for name in ('pdu_rail','pdu_socket','power_plug','power_lead','psu_rear','psu_blank','server_rear','switch_rear'):
    tex_ref('odc:blocks/'+name)
print('PASS: open rack backs and PDU assets')

for tier in range(1,4):
    name=f'transformer_t{tier}'
    variants=read(root/'blockstates'/f'{name}.json')['variants']
    check(set(variants)=={f'facing={f},indicator={i}' for f in ('north','east','south','west') for i in range(4)},'transformer indicator coverage')
    states=('off','armed','on','tripped')
    colors=('off','off','green','red')
    for i,state in enumerate(states):
        model=read(root/'models/block'/f'{name}_{state}.json')
        check(model['textures']['lamp']==f'odc:blocks/power_lamp_{colors[i]}','transformer lamp texture')
        for direction in ('north','east','south','west'):
            check(variants[f'facing={direction},indicator={i}']['model']==f'odc:{name}_{state}','transformer model selection')
for color in ('off','green','red'):tex_ref('odc:blocks/power_lamp_'+color)
print('PASS: three-state power indicator assets')
