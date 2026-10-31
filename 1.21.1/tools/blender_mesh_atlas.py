"""Shared Blender / native OBJ export: padded material atlas and triangulated caps."""
import bpy, math

def face_uv(o,face):
 """Model-space projections: 16 texels/metre, consistent fibres, cap-only engravings."""
 mesh=o.data;points=[mesh.vertices[i].co for i in face.vertices];tile=o['atlas_tile']
 normal=face.normal;axis=max(range(3),key=lambda i:abs(normal[i]))
 if o['kind']=='box':
  sizes=[abs(v) for v in o.scale];grain=max(range(3),key=lambda i:sizes[i]);axes=[i for i in range(3) if i!=axis]
  if tile==0 and axis==grain:tile=12
  if grain in axes:axes=[grain,next(i for i in axes if i!=grain)]
  coords=[(.5+p[axes[0]]*sizes[axes[0]]/2,.5+p[axes[1]]*sizes[axes[1]]/2) for p in points]
 elif o['kind']=='cylinder' and abs(normal.z)>.9:
  if o.name.startswith('board') and normal.z>0:
   radius=max(v.co.xy.length for v in mesh.vertices)
   coords=[(.5+p.x/(2*radius),.5+p.y/(2*radius)) for p in points]
  else:
   if o.name.startswith('board'):tile={4:0,5:1,6:2}[tile]
   coords=[(.5+p.x/2,.5+p.y/2) for p in points]
 elif o['kind']=='cylinder':
  if o.name.startswith('board'):tile={4:0,5:1,6:2}[tile]
  angles=[math.atan2(p.y,p.x) for p in points]
  if max(angles)-min(angles)>math.pi:angles=[a+math.tau if a<0 else a for a in angles]
  radius=max(v.co.xy.length for v in mesh.vertices)
  coords=[(.5+(a-math.pi)*radius/2,.5+p.z/2) for a,p in zip(angles,points)]
 else:
  axes=[i for i in range(3) if i!=axis];coords=[(.5+p[axes[0]]/2,.5+p[axes[1]]/2) for p in points]
 # Cylindrical U may exceed a tile for a large circumference: one face uses a
 # translated contiguous segment, never crossing into a different atlas tile.
 umin=min(u for u,v in coords);umax=max(u for u,v in coords)
 if umin<0 or umax>1:
  offset=math.floor(umin);coords=[(u-offset,v) for u,v in coords]
  if max(u for u,v in coords)>1:coords=[(.5+(u-(umin+umax)/2),v) for u,v in coords]
 content=int(o.get('atlas_content',32));tile_size=content*2;gutter=content//2;size=tile_size*4
 return [(((tile%4)*tile_size+gutter+.5+max(0,min(1,u))*(content-1))/size,1-((tile//4)*tile_size+gutter+.5+max(0,min(1,v))*(content-1))/size) for u,v in coords]

def export_mesh(name,selected,out):
 bpy.context.view_layer.update();lines=['# Blender authored; padded Aseprite atlas','mtllib atlas.mtl'];v=uv=faces=0
 for o in selected:
  mesh=o.data;matrix=o.matrix_world;lines+=['g '+o.name,'usemtl atlas']
  for vert in mesh.vertices:
   p=matrix@vert.co;lines.append(f'v {p.x:.6f} {p.z:.6f} {-p.y:.6f}')
  for face in mesh.polygons:
   coords=face_uv(o,face)
   pieces=[tuple(range(len(face.vertices)))] if len(face.vertices)<=4 else [(0,i,i+1) for i in range(1,len(face.vertices)-1)]
   for piece in pieces:
    ids=[]
    for k in piece:
     a,b=coords[k];lines.append(f'vt {a:.7f} {b:.7f}');uv+=1;ids.append(f'{v+face.vertices[k]+1}/{uv}')
    lines.append('f '+' '.join(ids));faces+=1
  v+=len(mesh.vertices)
 (out/(name+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
 return {'model':name,'vertices':v,'faces':faces}

