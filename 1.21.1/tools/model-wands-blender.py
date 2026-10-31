"""Run inside Blender through MCP. Author nine modular parts; export static native NeoForge OBJ assets."""
from pathlib import Path
import bpy
import math
import json
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'src/main/resources/assets/elementalcircuits'
ART=ROOT/'art/blender'
ART.mkdir(parents=True,exist_ok=True)
OUT=ASSET/'models/item/wand3d'
OUT.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
colors=['32291f','805d39','bc945d','c8a769','f1d699','536168','a0b4b9','d8e4dc','8b4e24','eda342','fff0ae','4d3467','a780d3','e3cfff','286b72','72d2c7']
mats=[]
for i,c in enumerate(colors):
    m=bpy.data.materials.new(f'palette_{i:02}')
    srgb=[int(c[j:j+2],16)/255 for j in (0,2,4)]
    m.diffuse_color=(*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4 for v in srgb),1)
    m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=m.diffuse_color
    bs.inputs['Metallic'].default_value=.65 if i in (3,4,5,6) else .10
    bs.inputs['Roughness'].default_value=.3 if i>=11 else .48
    mats.append(m)
groups={}
current=[]
def finish(obj,name,mat):
    obj.name=name;obj.data.materials.append(mats[mat]);current.append(obj)
    return obj
def rod(name,z,depth,radius,mat,sides=8,r2=None,x=0,y=0):
    bpy.ops.mesh.primitive_cone_add(vertices=sides,radius1=radius,radius2=radius if r2 is None else r2,depth=depth,location=(x,y,z))
    return finish(bpy.context.object,name,mat)
def bar(name,loc,scale,mat,rotation=(0,0,0)):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc,rotation=rotation)
    o=finish(bpy.context.object,name,mat);o.scale=scale
    return o
def hoop(z,r=.081,depth=.028,mat=3):return rod('brass_binding',z,depth,r,mat,8)

for slot in ('head','body','tail'):
    for variant in range(3):
        current=[]
        if slot=='body':
            material=(1,5,8)[variant]
            rod('tapered_core',.47,.64,.043,material,8,r2=.058)
            rod('grip',.32,.24,.055,0 if variant==0 else 5,8)
            for z in (.205,.25,.295,.34,.385,.43):hoop(z,.057,.012,2 if variant==0 else 6 if variant==1 else 3)
            for z in (.17,.48,.76):hoop(z,.067,.035)
            for a in range(4):
                angle=a*math.pi/2
                bar('inlay',(.046*math.cos(angle),.046*math.sin(angle),.62),(.012,.012,.19),3 if variant!=2 else 9)
            if variant==2:
                for z in (.53,.60,.67,.72):hoop(z,.058,.016,9)
            if variant==1:
                for z in (.54,.70):hoop(z,.069,.022,6)
        elif slot=='head':
            hoop(.815,.082,.048);rod('socket',.86,.08,.08,5,8,r2=.06)
            if variant<2:
                rod('crystal_lower',.956,.11,.066,7 if variant==0 else 12,6,r2=.096)
                rod('crystal_tip',1.09,.16,.096,7 if variant==0 else 12,6,r2=0)
                for a in range(3):
                    angle=a*math.tau/3
                    rod('claw',.955,.16,.012,3,4,x=.071*math.cos(angle),y=.071*math.sin(angle))
                if variant==1:
                    hoop(.9,.12,.022,3)
                    for a in range(3):
                        angle=a*math.tau/3+.4
                        rod('satellite_crystal',1.005,.115,.028,11,5,r2=0,x=.115*math.cos(angle),y=.115*math.sin(angle))
            else:
                rod('copper_heart',.98,.13,.061,15,6,r2=.025)
                for a in range(3):
                    angle=a*math.tau/3
                    rod('fork_tine',.995,.22,.021,3,6,r2=.012,x=.101*math.cos(angle),y=.101*math.sin(angle))
                    rod('fork_tip',1.118,.028,.023,4,6,r2=0,x=.101*math.cos(angle),y=.101*math.sin(angle))
        else:
            hoop(.135,.068,.035)
            if variant==0:
                rod('wood_pommel',.075,.09,.057,1,8,r2=.062);hoop(.04,.059,.02)
            elif variant==1:
                rod('iron_pommel',.07,.1,.079,6,8,r2=.061);rod('iron_point',.0,.05,0,5,8,r2=.064)
            else:
                rod('diamond_pommel',.075,.07,.06,15,6,r2=.075);rod('diamond_point',.015,.05,0,14,6,r2=.06)
                for a in range(3):
                    ang=a*math.tau/3;rod('pommel_claw',.08,.10,.012,3,4,x=.068*math.cos(ang),y=.068*math.sin(ang))
        groups[f'{slot}_{variant}']=list(current)

# Export evaluated Blender mesh coordinates, mapping Blender Z to Minecraft Y.
# Palette UV is a real shared texture; material factors remain white for vanilla light shading.
bpy.context.view_layer.update()
stats=[]
for h in range(3):
 for b in range(3):
  for t in range(3):
    index=h*9+b*3+t
    selected=groups[f'head_{h}']+groups[f'body_{b}']+groups[f'tail_{t}']
    lines=['# Original Blender authored modular wand','mtllib wand_palette.mtl','vt 0.5 0.5'];n=0;faces=0
    for obj in selected:
        mesh=obj.data;matrix=obj.matrix_world
        lines.append('g '+obj.name.replace('.','_'))
        material=mats.index(obj.data.materials[0])
        lines.append(f'usemtl palette_{material:02}')
        for vertex in mesh.vertices:
            p=matrix@vertex.co
            lines.append(f'v {p.x+.5:.6f} {p.z-.08:.6f} {-p.y+.5:.6f}')
        for polygon in mesh.polygons:
            ids=list(polygon.vertices)
            pieces=[ids] if len(ids)<=4 else [(ids[0],ids[k],ids[k+1]) for k in range(1,len(ids)-1)]
            for piece in pieces:
                lines.append('f '+' '.join(f'{n+i+1}/1' for i in piece));faces+=1
        n+=len(mesh.vertices)
    (OUT/f'wand_{index}.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    stats.append({'index':index,'vertices':n,'faces':faces})
(OUT/'wand_palette.mtl').write_text('\n'.join(f'newmtl palette_{i:02}\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #p{i}\n' for i in range(16)),encoding='utf-8')
display={
 'gui':{'rotation':[18,-35,-38],'translation':[0,0,0],'scale':[.9,.9,.9]},
 'firstperson_righthand':{'rotation':[0,-85,12],'translation':[2.0,1.0,0],'scale':[.85,.85,.85]},
 'firstperson_lefthand':{'rotation':[0,85,-12],'translation':[2.0,1.0,0],'scale':[.85,.85,.85]},
 'thirdperson_righthand':{'rotation':[90,0,0],'translation':[0,2,0],'scale':[.8,.8,.8]},
 'thirdperson_lefthand':{'rotation':[90,0,0],'translation':[0,2,0],'scale':[.8,.8,.8]},
 'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.5,.5,.5]},
 'fixed':{'rotation':[0,0,-35],'translation':[0,0,0],'scale':[.8,.8,.8]}}
for i in range(27):
    model={'loader':'neoforge:obj','model':f'elementalcircuits:models/item/wand3d/wand_{i}.obj','automatic_culling':False,'flip_v':False,'shade_quads':True,'emissive_ambient':False,'gui_light':'front','textures':{'particle':'elementalcircuits:item/wand_palette',**{f'p{k}':f'elementalcircuits:item/wand/palette_{k}' for k in range(16)}},'display':display}
    (ASSET/f'models/item/circuit_wand_{i}.json').write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
base={'parent':'elementalcircuits:item/circuit_wand_0','overrides':[{'predicate':{'elementalcircuits:assembly':i},'model':f'elementalcircuits:item/circuit_wand_{i}'} for i in range(27)]}
(ASSET/'models/item/circuit_wand.json').write_text(json.dumps(base,indent=2)+'\n',encoding='utf-8')

# Keep nine distinct authoring collections and stage three complete examples for the source render.
for key,objects in groups.items():
    collection=bpy.data.collections.new(key);bpy.context.scene.collection.children.link(collection)
    for obj in objects:
        for old in list(obj.users_collection):old.objects.unlink(obj)
        collection.objects.link(obj);obj.hide_render=True;obj.hide_set(True)
for example,(h,b,t) in enumerate(((0,0,0),(1,1,1),(2,2,2))):
    for obj in groups[f'head_{h}']+groups[f'body_{b}']+groups[f'tail_{t}']:
        copy=obj.copy();copy.data=obj.data.copy();bpy.context.scene.collection.objects.link(copy);copy.hide_set(False);copy.hide_render=False;copy.location.x+=(example-1)*.48
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.samples=24
scene.render.resolution_x=1200;scene.render.resolution_y=900;scene.render.resolution_percentage=100
scene.world.color=(.06,.07,.08)
bpy.ops.object.camera_add(location=(1.9,-3.5,1.7));camera=bpy.context.object
camera.rotation_euler=(Vector((0,0,.55))-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=1.85;scene.camera=camera
for loc,power,size in (((1,-2,3),250,3),((-2,-.3,1),180,2),((0,2,2),300,2)):
    bpy.ops.object.light_add(type='AREA',location=loc);light=bpy.context.object;light.data.energy=power;light.data.shape='DISK';light.data.size=size;light.rotation_euler=(-light.location).to_track_quat('-Z','Y').to_euler()
scene.render.image_settings.file_format='PNG';scene.render.filepath=str(ART/'wand-studio.png')
bpy.ops.wm.save_as_mainfile(filepath=str(ART/'modular-wands.blend'))
(ART/'mesh-metrics.json').write_text(json.dumps({'blender':bpy.app.version_string,'variants':stats,'sharedParts':9,'source':'Authored locally in Blender via MCP','palette':colors},indent=2)+'\n',encoding='utf-8')
bpy.ops.render.render(write_still=True)
print('WAND_MODEL_EXPORT_PASS: 9 parts; 27 variants; native OBJ; blend source and render saved')
