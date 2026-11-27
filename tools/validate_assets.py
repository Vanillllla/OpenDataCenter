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
blocks={'rack','rack_glass','workbench','computer','creative_battery','data_cable','power_cable','power_relay','raised_floor','wall_panel','radio_station','satellite_station'}
items={'cpu_basic','cpu_pro','gpu_basic','gpu_pro','chassis_1u','chassis_2u','chassis_4u','precision_chip','board_basic','board_pro','board_advanced','ram_basic','ram_pro','ram_advanced','hdd_basic','hdd_pro','ssd_basic','ssd_pro','nvme_basic','nvme_pro','psu_basic','psu_pro','psu_advanced','nic_basic','nic_pro','switch_basic','switch_pro','ethernet_patch'}
check({p.stem for p in (root/'blockstates').glob('*.json')}==blocks,'block state registry')
check({p.stem for p in (root/'models/item').glob('*.json')}==blocks|items|{'chassis_1u_ready','chassis_2u_ready','chassis_4u_ready'},'item model registry')
for f in root.rglob('*.png'):
    b=f.read_bytes();check(b[:8]==b'\x89PNG\r\n\x1a\n',f'PNG header {f.name}');w,h=struct.unpack('>II',b[16:24]);check(w==16 and h%16==0,f'16px texture {f.name}');
    meta=f.with_name(f.name+'.mcmeta')
    if h>16:
        check(meta.exists(),f'animation metadata {f.name}');anim=read(meta)['animation'];check(all(isinstance(i,int) and 0<=i<h//16 for i in anim['frames']),f'animation frames {f.name}')
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
        check(all(0<=a<b<=16 for a,b in zip(e['from'],e['to'])),f'cuboid bounds {f.name}')
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
        check(any(e['from']==[0,0,1] and e['to']==[16,1,16] for e in els)==(role!=2),'bottom plate')
        check(any(e['from']==[0,15,1] and e['to']==[16,16,16] for e in els)==(role!=1),'top plate')
for name in ('data_cable','power_cable','power_relay'):
    data=read(root/'blockstates'/f'{name}.json')['multipart']
    for bits in itertools.product(('true','false'),repeat=7):
        props=dict(zip(('down','up','north','south','west','east','active'),bits));found=[x for x in data if all(props.get(k)==v for k,v in x.get('when',{}).items())]
        check(len(found)==1+bits[:6].count('true'),f'cable state {name}')
for name in ('computer','creative_battery','workbench','radio_station','satellite_station'):
    data=read(root/'blockstates'/f'{name}.json')['variants'];check(set(data)=={f'active={a},facing={f}' for a in ('true','false') for f in ('north','east','south','west')},f'machine states {name}')
for u in (1,2,4):
    for state in ('ready','empty'):tex_ref(f'odc:blocks/server_{u}_{state}')
for name in ('switch_panel_1','switch_panel_2','led_on','led_off','rack_window'):tex_ref('odc:blocks/'+name)
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
print(f'PASS: {checks} resource checks; {len(blocks)} block types; {len(items)} item types; {len(recipes)} recipes; {sum(1 for _ in root.rglob("*.png"))} PNG textures; 24 rack frame variants; 384 cable/relay states.')
