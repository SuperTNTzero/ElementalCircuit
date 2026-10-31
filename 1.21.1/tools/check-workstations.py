"""Check native model format limits and authored workstation resource references."""
import json
from pathlib import Path
from PIL import Image

root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/elementalcircuits'
models=assets/'models/block/workstations'
metrics=json.loads((root/'art/blender/workstation-metrics.json').read_text(encoding='utf-8'))
assert Image.open(assets/'textures/block/workstations.png').size==(256,256)
ase=(root/'art/aseprite/workstations.aseprite').read_bytes()
assert ase[4:6]==bytes((0xe0,0xa5)), 'Editable Aseprite source required'
for item in metrics['models']:
    path=models/(item['model']+'.obj')
    vertices=uvs=faces=0
    positions=[]
    for line in path.read_text(encoding='utf-8').splitlines():
        fields=line.split()
        if not fields:continue
        if fields[0]=='v':
            vertices+=1;point=list(map(float,fields[1:4]));positions.append(point)
            assert all(-1e-5<=point[i]<=((2 if item['model']=='wand_workbench' else 1) if i==0 else 1)+1e-5 for i in range(3)),f'{path}: geometry escapes occupied blocks'
        elif fields[0]=='vt':
            uvs+=1
            u,v=map(float,fields[1:3]);assert 0<=u<=1 and 0<=v<=1,path
            assert 16.49<=u*256%64<=47.51 and 16.49<=(1-v)*256%64<=47.51,f'{path}: UV outside padded content' 
        elif fields[0]=='f':
            assert 3<=len(fields)-1<=4, f'{path}: NeoForge OBJ supports only triangles and quads'
            for value in fields[1:]:
                v,uv=map(int,value.split('/'))
                assert 0<v<=vertices and 0<uv<=uvs, path
            faces+=1
    assert vertices==item['vertices'] and faces==item['faces'], path
    data=json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
    assert data['loader']=='neoforge:obj' and data['flip_v'] is True
for block,count in [('inscription_table',16),('wand_workbench',8),('fixed_circuit',12)]:
    variants=json.loads((assets/f'blockstates/{block}.json').read_text(encoding='utf-8'))['variants']
    assert len(variants)==count
    for state in variants.values():
        namespace,path=state['model'].split(':')
        assert namespace=='elementalcircuits' and (assets/f'models/{path}.json').is_file()
assert not (root/'src/main/resources/data/elementalcircuits/recipe/fixed_circuit.json').exists()
# Every atlas gutter repeats its own edge, never an adjacent material.
im=Image.open(assets/'textures/block/workstations.png')
for tile in range(16):
    ox=(tile%4)*64;oy=(tile//4)*64
    for y in range(64):
        for x in range(64):
            assert im.getpixel((ox+x,oy+y))==im.getpixel((ox+max(16,min(47,x)),oy+max(16,min(47,y))))
# Wand caps had the same n-gon limitation; verify every combination, not only workstations.
for index in range(27):
    for line in (assets/f'models/item/wand3d/wand_{index}.obj').read_text(encoding='utf-8').splitlines():
        if line.startswith('f '):assert 3<=len(line.split())-1<=4
for p in (assets/'textures/item/wand').glob('palette_*.png'):assert min(Image.open(p).size)>=16
layout=json.loads((assets/'geometry/workstations.json').read_text(encoding='utf-8'))
for name in ('desk','inscription'):
    for b in layout[name]['collision']:
        assert all(b[i]<b[i+3] for i in range(3))
        assert 0<=b[0]<b[3]<=(2 if name=='desk' else 1) and 0<=b[1]<b[4]<=1 and 0<=b[2]<b[5]<=1
# Fixed array meshes use the same checked, padded atlas convention.
fixed=json.loads((root/'art/blender/fixed-circuit-metrics.json').read_text(encoding='utf-8'))
assert (root/'art/aseprite/fixed-circuits.aseprite').read_bytes()[4:6]==bytes((0xe0,0xa5))
fixed_image=Image.open(assets/'textures/block/fixed-circuits.png')
assert fixed_image.size==(1024,1024)
for item in fixed['models']:
    path=assets/('models/block/fixed/'+item['model']+'.obj');vertices=uvs=faces=0
    for line in path.read_text(encoding='utf-8').splitlines():
        parts=line.split()
        if not parts:continue
        if parts[0]=='v':
            vertices+=1;x,y,z=map(float,parts[1:]);assert 0<=x<=1 and 0<=y<=.221 and 0<=z<=1
        elif parts[0]=='vt':
            uvs+=1;u,v=map(float,parts[1:]);assert 64.49<=u*1024%256<=191.51 and 64.49<=(1-v)*1024%256<=191.51
        elif parts[0]=='f':
            assert 3<=len(parts)-1<=4
            for pair in parts[1:]:
                vi,ui=map(int,pair.split('/'));assert 0<vi<=vertices and 0<ui<=uvs
            faces+=1
    assert vertices==item['vertices'] and faces==item['faces']
# Sample every gutter pixel in the high resolution atlas, including corners.
for tile in range(16):
    ox=tile%4*256;oy=tile//4*256
    for y in range(256):
        for x in range(256):
            assert fixed_image.getpixel((ox+x,oy+y))==fixed_image.getpixel((ox+max(64,min(191,x)),oy+max(64,min(191,y))))
print('WORKSTATION_ASSETS_PASS: 7 workstation + 3 fixed-array + 27 wand meshes; 128px array paintings and mip-safe gutters, editable sources, occupied bounds and 36 variants')
