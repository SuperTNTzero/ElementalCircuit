"""Blender authoring: padded Aseprite UVs and one source for meshes and collision."""
from pathlib import Path
import bpy, math, json, sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
ASSET=ROOT/'src/main/resources/assets/elementalcircuits'
OUT=ASSET/'models/block/workstations';ART=ROOT/'art/blender'
OUT.mkdir(parents=True,exist_ok=True);ART.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
atlas=bpy.data.images.load(str(ASSET/'textures/block/workstations.png'))
mats=[]
for i in range(16):
 m=bpy.data.materials.new('atlas_'+str(i));m.use_nodes=True
 bs=m.node_tree.nodes.get('Principled BSDF');tex=m.node_tree.nodes.new('ShaderNodeTexImage')
 tex.image=atlas;tex.interpolation='Closest';m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color'])
 bs.inputs['Roughness'].default_value=.6;mats.append(m)
objects=[]
TILT=math.radians(12);DESK_Y=.73;THICKNESS=.09
SURFACE=DESK_Y+THICKNESS/(2*math.cos(TILT))
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def finish(obj,name,tile,kind):
 obj.name=name;obj.data.materials.append(mats[tile]);obj['atlas_tile']=tile;obj['kind']=kind;objects.append(obj);return obj
def box(name,x,y,z,sx,sy,sz,tile,tilt=0):
 bpy.ops.mesh.primitive_cube_add(size=1,location=(x,-z,y))
 o=finish(bpy.context.object,name,tile,'box');o.scale=(sx,sz,sy);o.rotation_euler.x=tilt;return o
def cylinder(name,x,y,z,r,h,tile,sides=8,r2=None):
 bpy.ops.mesh.primitive_cone_add(vertices=sides,radius1=r,radius2=r if r2 is None else r2,depth=h,location=(x,-z,y))
 return finish(bpy.context.object,name,tile,'cylinder')
def torus(name,x,y,z,r,tile):
 bpy.ops.mesh.primitive_torus_add(major_segments=16,minor_segments=4,location=(x,-z,y),major_radius=r,minor_radius=.012)
 return finish(bpy.context.object,name,tile,'torus')
def surface(z):return SURFACE+(.5-z)*math.tan(TILT)
def resting_box(name,x,z,sx,sy,sz,tile):
 # Local bottom face lies exactly on the same tilted plane as the desktop.
 o=box(name,x,surface(z)+sy/(2*math.cos(TILT))+.001,z,sx,sy,sz,tile,TILT)
 o['resting']=True;return o

from blender_mesh_atlas import face_uv, export_mesh

def export(name,selected):return export_mesh(name,selected,OUT)

def split_desk():
 # Keep each block's baked geometry inside that block: native chunk culling,
 # break overlays and neighbour occlusion must not depend on an empty second half.
 source=(OUT/'wand_workbench.obj').read_text(encoding='utf-8').splitlines()
 for part in (0,1):
  points=[];uvs=[];lines=['# Half of the same authored desk; continuous interpolated UVs','mtllib atlas.mtl'];count=faces=0
  for line in source:
   f=line.split()
   if not f:continue
   if f[0]=='v':points.append(tuple(map(float,f[1:4])))
   elif f[0]=='vt':uvs.append(tuple(map(float,f[1:3])))
   elif f[0] in ('g','usemtl'):lines.append(line)
   elif f[0]=='f':
    polygon=[]
    for pair in f[1:]:
     vi,ui=map(int,pair.split('/'));polygon.append(points[vi-1]+uvs[ui-1])
    clipped=[]
    for i,b in enumerate(polygon):
     a=polygon[i-1];inside_a=a[0]>=1-1e-8 if part else a[0]<=1+1e-8;inside_b=b[0]>=1-1e-8 if part else b[0]<=1+1e-8
     if inside_a!=inside_b:
      t=(1-a[0])/(b[0]-a[0]);clipped.append(tuple(a[j]+(b[j]-a[j])*t for j in range(5)))
     if inside_b:clipped.append(b)
    polygon=[]
    for v in clipped:
     if not polygon or sum((v[j]-polygon[-1][j])**2 for j in range(3))>1e-14:polygon.append(v)
    if len(polygon)>1 and sum((polygon[0][j]-polygon[-1][j])**2 for j in range(3))<1e-14:polygon.pop()
    if len(polygon)<3:continue
    if (Vector(polygon[1][:3])-Vector(polygon[0][:3])).cross(Vector(polygon[2][:3])-Vector(polygon[0][:3])).length<1e-10:continue
    pieces=[polygon] if len(polygon)<=4 else [[polygon[0],polygon[i],polygon[i+1]] for i in range(1,len(polygon)-1)]
    for piece in pieces:
     ids=[]
     for x,y,z,u,v in piece:
      lines.append(f'v {x-part:.7f} {y:.7f} {z:.7f}');lines.append(f'vt {u:.7f} {v:.7f}');count+=1;ids.append(f'{count}/{count}')
     lines.append('f '+' '.join(ids));faces+=1
  name='wand_workbench_'+('head' if part else 'foot');(OUT/(name+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf-8');stats.append({'model':name,'vertices':count,'faces':faces})

def bounds(o):
 p=[o.matrix_world@v.co for v in o.data.vertices]
 return [min(v.x for v in p),min(v.z for v in p),min(-v.y for v in p),max(v.x for v in p),max(v.z for v in p),max(-v.y for v in p)]

stats=[]
# A complete one-block envelope leaves the space above available to vanilla placement.
cylinder('octagonal_foot',.5,.08,.5,.48,.16,8)
cylinder('brass_lower_rim',.5,.185,.5,.45,.05,1)
cylinder('pedestal',.5,.335,.5,.35,.25,1)
cylinder('upper_rim',.5,.475,.5,.43,.03,13)
cylinder('inset_surface',.5,.493,.5,.38,.014,8)
for a in range(6):
 t=a*math.tau/6;cylinder('alignment_pin',.5+.35*math.cos(t),.548,.5+.35*math.sin(t),.023,.096,1,6)
torus('focusing_collar',.5,.58,.5,.29,1)
cylinder('crystal_lower',.5,.705,.5,0,.11,2,6,r2=.10)
cylinder('crystal_body',.5,.805,.5,.10,.09,2,6)
cylinder('crystal_tip',.5,.92,.5,.10,.14,2,6,r2=0)
for i in range(3):torus('coil',.19,.52+i*.028,.76,.065,1)
cylinder('coil_core',.19,.56,.76,.03,.15,3)
box('bracket',.80,.522,.73,.18,.04,.11,3)
cylinder('small_vial',.78,.60,.21,.04,.18,10,6)
cylinder('vial_cap',.78,.705,.21,.045,.03,1,6)
cylinder('dial',.21,.518,.22,.08,.036,5,12)
bpy.context.view_layer.update();altar_collision=[bounds(o) for o in objects]
for mat,index in [('empty',None),('wood',4),('brass',5),('crystal',6)]:
 board=None
 if index is not None:board=cylinder('board_'+mat,.5,.523,.5,.265,.044,index,6)
 stats.append(export('inscription_'+mat,list(objects)))
 if board is not None:objects.remove(board);bpy.data.objects.remove(board,do_unlink=True)
altar=list(objects);objects=[]
# Legs meet the underside of a single continuous slab. Plank seams are painted,
# rather than gaps through which terrain can be seen.
for x in (.13,1.87):
 for z in (.15,.85):
  height=DESK_Y+(.5-z+.07)*math.tan(TILT)-THICKNESS/(2*math.cos(TILT))+.002
  box('spruce_leg',x,height/2,z,.14,height,.14,0)
for z in (.17,.83):box('long_brace',1,.23,z,1.86,.08,.07,0)
for x in (.13,1.87):box('end_brace',x,.23,.5,.07,.08,.7,0)
box('sloped_top',1,DESK_Y,.5,1.98,THICKNESS,.96/math.cos(TILT),0,TILT)
for x in (.035,1.965):resting_box('edge_strip',x,.5,.025,.012,.94/math.cos(TILT),1)
resting_box('front_lip',1,.971,1.98,.03,.025,0)
# All tools rest on the same surface, with axes aligned to that surface.
resting_box('mallet_handle',.30,.16,.30,.025,.035,7)
resting_box('mallet_head',.17,.16,.09,.055,.13,3)
resting_box('graver_handle',.40,.28,.17,.025,.035,7)
resting_box('graver_tip',.545,.28,.12,.016,.018,3)
resting_box('wrench',.58,.13,.21,.018,.035,3)
for z in (.1,.16):resting_box('wrench_jaw',.70,z,.05,.022,.022,3)
tray=resting_box('parts_tray',.20,.40,.15,.018,.09,1)
for x,z in [(.18,.39),(.23,.40),(.20,.42)]:
 resting_box('loose_pin',x,z,.018,.018,.018,3).location.z+=.019
bpy.context.view_layer.update();desk=list(objects)
# Generated collision uses the actual bounds of solid members plus 32 slices of
# the sloped surface, so there is no obsolete full-height invisible cube.
desk_collision=[]
for o in desk:
 if o.name.startswith(('sloped_top','edge_strip','front_lip')):continue
 desk_collision.append(bounds(o))
slab_bounds=bounds(next(o for o in desk if o.name.startswith("sloped_top")))
for i in range(32):
 z0=slab_bounds[2]+(slab_bounds[5]-slab_bounds[2])*i/32;z1=slab_bounds[2]+(slab_bounds[5]-slab_bounds[2])*(i+1)/32
 desk_collision.append([.01,surface(z1)-THICKNESS/math.cos(TILT),z0,1.99,surface(z0),z1])
# Thin perimeter strips and the front stop have their own small sloped bounds.
for o in desk:
 if o.name.startswith(('edge_strip','front_lip')):
  b=bounds(o)
  for i in range(16):
   z0=b[2]+(b[5]-b[2])*i/16;z1=b[2]+(b[5]-b[2])*(i+1)/16
   desk_collision.append([b[0],surface(z1),z0,b[3],surface(z0)+(.04 if o.name.startswith('front_lip') else .014),z1])
stats.append(export('wand_workbench',desk));split_desk()
layout={'version':1,'desk':{'tiltDegrees':12,'surfaceY':SURFACE,'previewX':1,'previewZ':.54,'preferredScale':.95,'clearance':.003,'ceiling':.99,'collision':desk_collision},'inscription':{'collision':altar_collision}}
write(ASSET/'geometry/workstations.json',layout)
(OUT/'atlas.mtl').write_text('newmtl atlas\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #atlas\n',encoding='utf-8')
display={'gui':{'rotation':[25,225,0],'translation':[-3,-2,0],'scale':[.5,.5,.5]},'ground':{'scale':[.4,.4,.4]},'fixed':{'scale':[.55,.55,.55]}}
for s in stats:
 name=s['model'];write(OUT/f'{name}.json',{'loader':'neoforge:obj','model':f'elementalcircuits:models/block/workstations/{name}.obj','automatic_culling':False,'flip_v':True,'shade_quads':True,'textures':{'particle':'elementalcircuits:block/workstations','atlas':'elementalcircuits:block/workstations'},'display':display})
write(OUT/'empty.json',{'textures':{'particle':'elementalcircuits:block/workstations'},'elements':[]})
variants={}
for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
 for board in ('empty','wood','brass','crystal'):variants[f'facing={facing},board={board}']={'model':f'elementalcircuits:block/workstations/inscription_{board}','y':angle}
write(ASSET/'blockstates/inscription_table.json',{'variants':variants});variants={}
for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
 for part in ('foot','head'):variants[f'facing={facing},part={part}']={'model':'elementalcircuits:block/workstations/'+('wand_workbench_foot' if part=='foot' else 'wand_workbench_head'),'y':angle}
write(ASSET/'blockstates/wand_workbench.json',{'variants':variants})
for item,model in [('wand_workbench','wand_workbench'),('inscription_table','inscription_wood')]:write(ASSET/f'models/item/{item}.json',{'parent':f'elementalcircuits:block/workstations/{model}'})
# Blender and Minecraft consume exactly the same face UV function.
for o in altar+desk:
 mesh=o.data;uvs=mesh.uv_layers.new(name='Padded Aseprite atlas');uvs.active_render=True;mesh.uv_layers.active=uvs
 for face in mesh.polygons:
  for li,coord in zip(face.loop_indices,face_uv(o,face)):uvs.data[li].uv=coord
for o in desk:o.location.x+=1.7
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24;scene.render.resolution_x=1440;scene.render.resolution_y=900;scene.render.resolution_percentage=100;scene.world.color=(.10,.12,.15)
bpy.ops.object.camera_add(location=(5,-6,4));camera=bpy.context.object;camera.rotation_euler=(Vector((1.8,-.4,.55))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=4.8;scene.camera=camera
for loc,energy in [((1,-3,5),600),((4,1,3),450),((-2,-1,2),250)]:
 bpy.ops.object.light_add(type='AREA',location=loc);o=bpy.context.object;o.data.energy=energy;o.data.size=4;o.rotation_euler=(Vector((1,-.5,.7))-o.location).to_track_quat('-Z','Y').to_euler()
atlas.pack();bpy.ops.wm.save_as_mainfile(filepath=str(ART/'workstations.blend'))
write(ART/'workstation-metrics.json',{'blender':bpy.app.version_string,'atlas':'Aseprite 1.3.15.5 / 256x256 with 16px gutters','models':stats})
scene.render.filepath=str(ART/'workstations-studio.png');bpy.ops.render.render(write_still=True);print('BLENDER_WORKSTATIONS_PASS',stats)
