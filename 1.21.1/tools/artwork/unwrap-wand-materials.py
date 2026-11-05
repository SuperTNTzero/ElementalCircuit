"""Apply bounded per-face planar UVs to the native Blender OBJ exports; geometry is unchanged."""
from pathlib import Path
import math,json
ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'src/main/resources/assets/elementalcircuits/models/item/wand3d'

def unwrap(path):
    lines=path.read_text(encoding='utf-8').splitlines();vertices=[];groups={};faces=[];group='default'
    for line in lines:
        if line.startswith('v '):vertices.append(tuple(map(float,line.split()[1:4])))
        elif line.startswith('g '):group=line[2:]
        elif line.startswith('f '):
            ids=[int(x.split('/')[0])-1 for x in line.split()[1:]]
            groups.setdefault(group,set()).update(ids);faces.append((group,ids))
    bounds={g:([min(vertices[i][k] for i in ids) for k in range(3)],[max(vertices[i][k] for i in ids) for k in range(3)]) for g,ids in groups.items()}
    uvs=[];rewritten=[]
    for group,ids in faces:
        a,b,c=(vertices[i] for i in ids[:3]);ab=[b[k]-a[k] for k in range(3)];ac=[c[k]-a[k] for k in range(3)]
        n=[ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0]]
        axis=max(range(3),key=lambda k:abs(n[k]));axes=(0,2) if axis==1 else (2,1) if axis==0 else (0,1);lo,hi=bounds[group];parts=[]
        for i in ids:
            uv=tuple(.03125+.9375*((vertices[i][k]-lo[k])/(hi[k]-lo[k]) if hi[k]-lo[k]>1e-8 else .5) for k in axes)
            assert all(math.isfinite(v) and .03<=v<=.97 for v in uv)
            uvs.append(uv);parts.append(f'{i+1}/{len(uvs)}')
        rewritten.append('f '+' '.join(parts))
    result=[];index=0;inserted=False
    for line in lines:
        if line.startswith('vt '):continue
        if line.startswith('g ') and not inserted:
            result.extend(f'vt {u:.6f} {v:.6f}' for u,v in uvs);inserted=True
        if line.startswith('f '):result.append(rewritten[index]);index+=1
        else:result.append(line)
    assert index==len(faces)
    path.write_text('\n'.join(result)+'\n',encoding='utf-8')
    return {'file':path.name,'vertices':len(vertices),'faces':len(faces),'uvs':len(uvs),'boundedUVs':True}

def main():
    stats=[unwrap(p) for p in sorted(FOLDER.glob('wand_*.obj'))];assert len(stats)==27
    (ROOT/'art/aseprite/parts/uv-metrics.json').write_text(json.dumps({'version':1,'method':'per-face dominant-normal projection inside each original Blender group','geometryChanged':False,'variants':stats},indent=2)+'\n',encoding='utf-8')
    print('WAND_UV_PASS: 27 variants; original vertices and faces preserved')
if __name__=='__main__':main()
