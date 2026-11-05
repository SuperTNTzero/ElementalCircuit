"""Blender source + native humanoid cuboid export; no runtime mesh construction per frame."""
import bpy, json, math, pathlib, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'art/blender/wizard';OUT.mkdir(parents=True,exist_ok=True)
ASSETS=ROOT/'src/main/resources/assets/elementalcircuits'
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
palette={'navy':(18,31,64),'blue':(29,53,97),'brass':(174,135,68),'crystal':(72,190,237),'leather':(53,45,49),'fold':(36,64,106)}
uv={'navy':(0,0),'blue':(64,0),'brass':(0,64),'crystal':(64,64),'leather':(0,96),'fold':(64,96)}
materials={}
for name,rgb in palette.items():
 m=bpy.data.materials.new(name);m.diffuse_color=tuple(c/255 for c in rgb)+(1,);m.use_nodes=True;m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=m.diffuse_color;m.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.7;materials[name]=m
bones={'head':(0,0,0),'hat':(0,0,0),'body':(0,0,0),'right_arm':(-5,2,0),'left_arm':(5,2,0),'right_leg':(-1.9,12,0),'left_leg':(1.9,12,0)}
parts=[]
def box(piece,bone,name,xyz,size,material='navy',rotation=(0,0,0),pivot=(0,0,0)):
 center=[xyz[i]+size[i]/2 for i in range(3)]
 if material=='crystal':
  pivot=tuple(center);rotation=(0,0,0);coords=[-size[i]/2 for i in range(3)]
 else:coords=list(xyz)
 parts.append(dict(piece=piece,bone=bone,name=name,box=coords+list(size),uv=list(uv[material]),rotation=list(rotation),pivot=list(pivot)))
 # Match Minecraft's y-down bone coordinates to Blender's z-up presentation.
 offset=bones[bone];center=[xyz[i]+size[i]/2 for i in range(3)]
 bpy.ops.mesh.primitive_cube_add(size=1,location=((offset[0]+center[0])/16,-(offset[2]+center[2])/16,(24-offset[1]-center[1])/16))
 obj=bpy.context.object;obj.name=piece+'/'+bone+'/'+name;obj.dimensions=(size[0]/16,size[2]/16,size[1]/16);obj.data.materials.append(materials[material]);obj['bone']=bone;obj['piece']=piece
 bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 obj.rotation_euler=(0,-rotation[2],0)
# Broad brim and offset tapered crown create a recognizable crooked wizard hat.
box('HEAD','head','brim',(-6.4,-8.8,-6.4),(12.8,.8,12.8))
box('HEAD','head','brim_binding',(-6.5,-8.65,-6.5),(13,.25,13),'brass')
for i in range(7):
 w=8.7-i*1.08;shift=i*i*.055
 box('HEAD','head','crown_'+str(i),(-w/2+shift,-10.5-i*1.65,-w/2),(w,1.8,w),'navy' if i%2==0 else 'blue')
box('HEAD','head','hat_band',(-4.5,-10.25,-4.5),(9,.75,9),'brass')
box('HEAD','head','hat_crystal',(-.8,-11,-4.8),(1.6,2.6,.6),'crystal')
# Upper robe, shoulder panels and tailored sleeves.
box('CHEST','body','robe',(-4.4,-.25,-2.5),(8.8,12.6,5))
box('CHEST','body','collar',(-3,-.8,-2.9),(6,1.4,1),'blue')
for side in (-1,1):
 box('CHEST','body','lapel_'+str(side),(side*2.2-.45,.2,-2.8),(.9,10.9,.4),'brass')
box('CHEST','body','belt',(-4.6,9.5,-2.7),(9.2,1.1,5.4),'leather')
box('CHEST','body','clasp',(-1,9.3,-3.05),(2,1.5,.55),'brass')
box('CHEST','body','heart_crystal',(-.7,2,-3),(1.4,2.3,.7),'crystal')
for side,bone in ((-1,'right_arm'),(1,'left_arm')):
 x=-3.15 if side<0 else -1.15
 box('CHEST',bone,'sleeve',(x,-2.3,-2.55),(4.3,10.5,5.1))
 box('CHEST',bone,'shoulder',(x-.2,-2.7,-2.75),(4.7,1.5,5.5),'blue')
 box('CHEST',bone,'cuff',(x-.1,6.3,-2.65),(4.5,1,5.3),'brass')
 box('CHEST',bone,'cuff_crystal',(-.5,5.6,-2.95),(1,1.5,.55),'crystal')
for side,bone in ((-1,'right_leg'),(1,'left_leg')):
 # Split panels follow each leg, with central clearance at the stride.
 x=-2.65 if side<0 else -1.95
 box('CHEST',bone,'split_robe',(x,.1,-2.65),(4.6,8.9,5.3))
 box('CHEST',bone,'hem',(x-.08,8.25,-2.8),(4.76,.65,5.6),'brass')
 box('CHEST',bone,'front_fold',(x+.8,.2,-2.85),(1,7.8,.25),'fold')
 box('LEGS',bone,'trousers',(-2.18,-.1,-2.18),(4.36,11.9,4.36),'blue')
 box('LEGS',bone,'knee_pad',(-1.7,5,-2.4),(3.4,2.4,.4))
 box('LEGS',bone,'knee_crystal',(-.4,5.7,-2.5),(.8,1.2,.35),'crystal')
 box('FEET',bone,'boot',(-2.45,7,-2.55),(4.9,2.7,5.4),'leather')
 box('FEET',bone,'toe',(-2.45,9.7,-3.25),(4.9,2.4,6.1))
 box('FEET',bone,'boot_trim',(-2.55,9.1,-2.65),(5.1,.7,5.6),'brass')
 box('FEET',bone,'boot_crystal',(-.5,7.9,-2.9),(1,1.4,.5),'crystal')
model=ASSETS/'models/armor/wizard.json';model.parent.mkdir(parents=True,exist_ok=True)
model.write_text(json.dumps({'version':1,'textureWidth':128,'textureHeight':128,'parts':parts},indent=2),encoding='utf-8')
# Studio and retained Blender authoring project.
world=bpy.context.scene.world;world.color=(.12,.14,.2)
bpy.ops.object.camera_add(location=(3.2,-5.6,3.2));cam=bpy.context.object
from mathutils import Vector
cam.rotation_euler=(Vector((0,0,1.4))-cam.location).to_track_quat('-Z','Y').to_euler();bpy.context.scene.camera=cam;cam.data.type='ORTHO';cam.data.ortho_scale=3.5
for location,power,size in (((-3,-4,5),900,4),((3,1,3),1200,3)):
 bpy.ops.object.light_add(type='AREA',location=location);bpy.context.object.data.energy=power;bpy.context.object.data.shape='DISK';bpy.context.object.data.size=size
scene=bpy.context.scene;scene.render.engine='BLENDER_EEVEE_NEXT' if bpy.app.version<(5,0,0) else 'CYCLES'
if scene.render.engine=='CYCLES':scene.cycles.samples=24
scene.render.resolution_x=800;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
bpy.context.preferences.filepaths.save_version=0
bpy.context.preferences.filepaths.file_preview_type='NONE'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'wizard-equipment.blend'))
scene.render.filepath=str(OUT/'wizard-studio.png');bpy.ops.render.render(write_still=True)
(OUT/'metrics.json').write_text(json.dumps({'cuboids':len(parts),'pieces':4,'source':'Blender native cuboid export','bones':bones},indent=2),encoding='utf-8')
