"""Generate the original tutorial structure and synchronize its English captions."""
from pathlib import Path
import gzip, json, re, struct

root = Path(__file__).resolve().parent.parent
assets = root / 'src/main/resources/assets/hololith/ponder'
assets.mkdir(parents=True, exist_ok=True)
blocks = {}
def put(x, y, z, name, props=None, nbt=None):
    state = {'Name': name}
    if props: state['Properties'] = props
    blocks[(x,y,z)] = (state, nbt)
for x in range(11):
    for z in range(11):
        put(x,0,z,'minecraft:stone_bricks')
        if z <= 5:
            height = 1 + (1 if x >= 9 else 0) + (1 if x >= 7 and z <= 2 else 0)
            for y in range(1,height+1):
                put(x,y,z,'minecraft:grass_block' if y == height else 'minecraft:dirt', {'snowy':'false'} if y == height else None)
for x in (5,6):
    for z in range(1,5): put(x,1,z,'minecraft:water',{'level':'0'})
for x in range(1,4):
    for z in range(0,2):
        for y in (2,3): put(x,y,z,'minecraft:oak_planks')
        put(x,4,z,'minecraft:red_terracotta')
put(2,3,1,'minecraft:glass')
for y in (3,4): put(9,y,4,'minecraft:oak_log',{'axis':'y'})
for x in range(8,11):
    for z in range(3,6): put(x,5,z,'minecraft:oak_leaves',{'distance':'1','persistent':'true','waterlogged':'false'})
put(9,6,4,'minecraft:oak_leaves',{'distance':'1','persistent':'true','waterlogged':'false'})
for dx in range(-1,2):
    for dz in range(-1,2):
        part=(dz+1)*3+dx+1
        nbt={'id':'hololith:surveying_table','Span':32,'Relief':1,'Dimension':'minecraft:overworld','Status':'Tutorial model'} if part == 4 else None
        put(3+dx,1,7+dz,'hololith:surveying_table',{'facing':'north','part':str(part)},nbt)
put(8,1,7,'create:andesite_casing')
put(8,2,7,'hololith:local_sensor',nbt={'id':'hololith:survey_sensor'})
put(8,2,3,'hololith:remote_beacon',nbt={'id':'hololith:survey_sensor'})
put(2,2,2,'hololith:reference_marker',{'color':'cyan'},{'id':'hololith:reference_marker'})
palette,indices,entries=[],{},[]
for pos,(state,nbt) in sorted(blocks.items()):
    key=json.dumps(state,sort_keys=True)
    if key not in indices: indices[key]=len(palette); palette.append(state)
    entry={'pos':list(pos),'state':indices[key]}
    if nbt is not None: entry['nbt']=nbt
    entries.append(entry)
def text(s):
    b=s.encode('utf-8'); return struct.pack('>H',len(b))+b
def kind(v):
    if isinstance(v,str): return 8
    if isinstance(v,int): return 3
    if isinstance(v,list): return 9
    if isinstance(v,dict): return 10
    raise TypeError(type(v))
def body(v):
    t=kind(v)
    if t==8: return text(v)
    if t==3: return struct.pack('>i',v)
    if t==9:
        element=kind(v[0]) if v else 10
        assert all(kind(x)==element for x in v)
        return bytes([element])+struct.pack('>i',len(v))+b''.join(body(x) for x in v)
    return b''.join(bytes([kind(value)])+text(key)+body(value) for key,value in v.items())+b'\x00'
structure={'DataVersion':3955,'size':[11,8,11],'palette':palette,'blocks':entries,'entities':[]}
(assets/'survey_workshop.nbt').write_bytes(gzip.compress(b'\x0a\x00\x00'+body(structure),mtime=0))
java=(root/'src/main/java/dev/hololith/client/ponder/HololithScenes.java').read_text(encoding='utf-8')
lang_path=root/'src/main/resources/assets/hololith/lang/en_us.json'
lang=json.loads(lang_path.read_text(encoding='utf-8'))
lang={k:v for k,v in lang.items() if not k.startswith('hololith.ponder.')}
for name in ('table','sources','markers'):
    section=java.split('public static void '+name+'(',1)[1].split('\n    public static void ',1)[0].split('\n    private static void ',1)[0]
    scene,title=re.search(r'scene\.title\("([^"]+)",\s*"([^"]+)"\)',section).groups()
    lang['hololith.ponder.'+scene+'.header']=title
    for index,caption in enumerate(re.findall(r'say\(scene,.*?,\s*"([^"]+)",\s*\d+\);',section)):
        lang[f'hololith.ponder.{scene}.text_{index}']=caption
lang_path.write_text(json.dumps(lang,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(f'Generated {len(entries)} blocks, {len(palette)} states, {sum(k.startswith("hololith.ponder.") for k in lang)} Ponder strings.')
