"""Three static, low-poly fixed arrays. Blender geometry shares padded UV export with workstations."""
from pathlib import Path
import sys, math, json, bpy
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from blender_mesh_atlas import face_uv,export_mesh
ROOT=Path(__file__).resolve().parents[2];ASSET=ROOT/'src/main/resources/assets/elementalcircuits'
OUT=ASSET/'models/block/fixed';OUT.mkdir(parents=True,exist_ok=True)
ART=ROOT/'art/blender';bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
atlas=bpy.data.images.load(str(ASSET/'textures/block/fixed-circuits.png'));mats=[]
for i in range(16):
 m=bpy.data.materials.new('array_material_'+str(i));m.use_nodes=True
 bs=m.node_tree.nodes.get('Principled BSDF');tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=atlas;tex.interpolation='Closest'
 m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color']);bs.inputs['Roughness'].default_value=.65;mats.append(m)
objects=[];all_objects=[];stats=[]
def write(p,data):p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def finish(o,name,tile,kind):
 o.name=name;o['atlas_tile']=tile;o['atlas_content']=128;o['kind']=kind;o.data.materials.append(mats[tile]);objects.append(o);return o
def cylinder(name,x,y,z,r,h,tile,sides=6,r2=None):
 bpy.ops.mesh.primitive_cone_add(vertices=sides,radius1=r,radius2=r if r2 is None else r2,depth=h,location=(x,-z,y))
 return finish(bpy.context.object,name,tile,'cylinder')
def box(name,x,y,z,sx,sy,sz,tile):
 bpy.ops.mesh.primitive_cube_add(size=1,location=(x,-z,y));o=finish(bpy.context.object,name,tile,'box');o.scale=(sx,sz,sy);return o
for material,face,body in [('wood',4,0),('brass',5,1),('crystal',6,2)]:
 objects=[]
 cylinder('dark_hex_foot',.5,.028,.5,.475,.056,8)
 cylinder('lower_brass_bevel',.5,.067,.5,.475,.022,13,r2=.454)
 cylinder('layered_material_body',.5,.112,.5,.454,.068,body)
 cylinder('brass_upper_bevel',.5,.155,.5,.454,.018,1,r2=.433)
 cylinder('board_'+material,.5,.169,.5,.411,.01,face)
 for i in range(6):
  a=-math.pi/2+i*math.tau/6;x=.5+.414*math.cos(a);z=.5+.414*math.sin(a)
  cylinder('brass_lock',x,.157,z,.025,.026,1)
  cylinder('lock_head',x,.174,z,.014,.008,3)
  # Recessed cyan contact between neighbouring locks, well separated from the etching.
  a+=(math.pi/6);x=.5+.364*math.cos(a);z=.5+.364*math.sin(a)
  cylinder('radial_contact',x,.165,z,.016,.006,2,6)
 # Physical nozzle points north, aligned with the fixed array's firing direction.
 box('north_socket',.5,.134,.098,.13,.07,.07,13)
 box('north_light',.5,.135,.060,.07,.025,.012,2)
 # The material identity also changes the centre and the six support pads.
 if material=='wood':cylinder('centre_brass_inlay',.5,.177,.5,.04,.006,1)
 elif material=='brass':
  cylinder('centre_socket',.5,.18,.5,.052,.012,13)
  cylinder('centre_blue_inlay',.5,.189,.5,.035,.006,2)
 else:
  cylinder('centre_crystal_bevel',.5,.194,.5,.056,.04,2,r2=.023)
  cylinder('centre_crystal_tip',.5,.217,.5,.023,.006,6,r2=0)
 bpy.context.view_layer.update();stats.append(export_mesh('fixed_'+material,objects,OUT))
 for o in objects:
  layer=o.data.uv_layers.new(name='Padded Aseprite atlas');layer.active_render=True;o.data.uv_layers.active=layer
  for f in o.data.polygons:
   for li,uv in zip(f.loop_indices,face_uv(o,f)):layer.data[li].uv=uv
  o.location.x+=(len(stats)-1)*1.2
 all_objects+=objects
 display={'gui':{'rotation':[25,225,0],'scale':[.92,.92,.92],'translation':[0,1,0]},'ground':{'translation':[0,2,0],'scale':[.6,.6,.6]},'fixed':{'rotation':[90,0,0],'translation':[0,0,-3],'scale':[.75,.75,.75]},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,3,0],'scale':[.65,.65,.65]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,3,0],'scale':[.55,.55,.55]}}
 write(OUT/f'fixed_{material}.json',{'loader':'neoforge:obj','model':f'elementalcircuits:models/block/fixed/fixed_{material}.obj','automatic_culling':False,'flip_v':True,'shade_quads':True,'textures':{'particle':'elementalcircuits:block/fixed-circuits','atlas':'elementalcircuits:block/fixed-circuits'},'display':display})
 write(ASSET/f'models/block/fixed_circuit_{material}.json',{'parent':f'elementalcircuits:block/fixed/fixed_{material}'})
 write(ASSET/f'models/item/fixed_circuit_{material}.json',{'parent':f'elementalcircuits:block/fixed/fixed_{material}'})
(OUT/'atlas.mtl').write_text('newmtl atlas\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #atlas\n',encoding='utf-8')
variants={f'facing={facing},material={material}':{'model':f'elementalcircuits:block/fixed/fixed_{material}','y':angle} for facing,angle in [('north',0),('east',90),('south',180),('west',270)] for material in ['wood','brass','crystal']}
write(ASSET/'blockstates/fixed_circuit.json',{'variants':variants})
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=24;scene.render.resolution_x=1440;scene.render.resolution_y=700;scene.render.resolution_percentage=100;scene.world.color=(.12,.14,.18)
bpy.ops.object.camera_add(location=(3,-4,3.7));camera=bpy.context.object;camera.rotation_euler=(Vector((1.7,-.5,.06))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=4.1;scene.camera=camera
for loc,energy in [((0,-3,4),500),((4,-1,3),350),((1,2,3),450)]:
 bpy.ops.object.light_add(type='AREA',location=loc);o=bpy.context.object;o.data.energy=energy;o.data.size=4;o.rotation_euler=(Vector((1.6,-.5,0))-o.location).to_track_quat('-Z','Y').to_euler()
atlas.pack();bpy.ops.wm.save_as_mainfile(filepath=str(ART/'fixed-circuits.blend'));write(ART/'fixed-circuit-metrics.json',{'blender':bpy.app.version_string,'models':stats})
scene.render.filepath=str(ART/'fixed-circuits-studio.png');bpy.ops.render.render(write_still=True);print('BLENDER_FIXED_CIRCUITS_PASS',stats)
