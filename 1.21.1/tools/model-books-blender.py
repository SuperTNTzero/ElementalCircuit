"""Blender offline authoring: two articulated books, native static OBJ parts and source animation."""
from pathlib import Path
import bpy, math, json
from mathutils import Vector, Matrix

ROOT=Path(__file__).resolve().parents[1]
ASSET=ROOT/'src/main/resources/assets/elementalcircuits'
OUT=ASSET/'models/item/book3d'; OUT.mkdir(parents=True,exist_ok=True)
ART=ROOT/'art/blender'; ART.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
atlas=bpy.data.images.load(str(ASSET/'textures/item/books.png'),check_existing=True)
mat=bpy.data.materials.new('Aseprite leather, brass and manuscript');mat.use_nodes=True
bs=mat.node_tree.nodes.get('Principled BSDF');tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=atlas;tex.interpolation='Closest';mat.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color']);bs.inputs['Roughness'].default_value=.6

def box(name,position,size,tile,top=None,bottom=None):
    x,y,z=position;sx,sy,sz=size
    bpy.ops.mesh.primitive_cube_add(size=1,location=(x,-z,y));o=bpy.context.object;o.name=name;o.scale=(sx,sz,sy)
    o.data.materials.append(mat);o['tile']=tile;o['top']=tile if top is None else top;o['bottom']=tile if bottom is None else bottom
    return o

def gem(name,position,radius,depth,tile):
    x,y,z=position;bpy.ops.mesh.primitive_cone_add(vertices=6,radius1=radius,radius2=0,depth=depth,location=(x,-z,y));o=bpy.context.object;o.name=name;o.data.materials.append(mat);o['tile']=o['top']=o['bottom']=tile;return o

def uv(o,face):
    n=face.normal;axis=max(range(3),key=lambda i:abs(n[i]));axes=[i for i in range(3) if i!=axis]
    tile=o['top'] if axis==2 and n.z>0 else o['bottom'] if axis==2 else o['tile']
    points=[o.data.vertices[i].co for i in face.vertices]
    mins=[min(v.co[i] for v in o.data.vertices) for i in axes];maxs=[max(v.co[i] for v in o.data.vertices) for i in axes]
    return [(((tile%4)*128+32.5+(p[axes[0]]-mins[0])/max(.00001,maxs[0]-mins[0])*63)/512,
             1-((tile//4)*128+32.5+(p[axes[1]]-mins[1])/max(.00001,maxs[1]-mins[1])*63)/512) for p in points]

def export(name,objects):
    bpy.context.view_layer.update();lines=['# Original articulated book, authored in Blender','mtllib books.mtl'];v=vt=faces=0
    for o in objects:
        lines+=['g '+o.name,'usemtl books']
        for vertex in o.data.vertices:
            p=o.matrix_world@vertex.co;lines.append(f'v {p.x:.7f} {p.z:.7f} {-p.y:.7f}')
        layer=o.data.uv_layers.active or o.data.uv_layers.new(name='Padded Aseprite paintings')
        for f in o.data.polygons:
            coords=uv(o,f)
            for li,coord in zip(f.loop_indices,coords):layer.data[li].uv=coord
            ids=list(range(len(f.vertices)));pieces=[ids] if len(ids)<=4 else [(0,k,k+1) for k in range(1,len(ids)-1)]
            for piece in pieces:
                refs=[]
                for k in piece:
                    a,b=coords[k];lines.append(f'vt {a:.7f} {b:.7f}');vt+=1;refs.append(f'{v+f.vertices[k]+1}/{vt}')
                lines.append('f '+' '.join(refs));faces+=1
        v+=len(o.data.vertices)
    (OUT/(name+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    model={'loader':'neoforge:obj','model':'elementalcircuits:models/item/book3d/'+name+'.obj','automatic_culling':False,'flip_v':True,'shade_quads':True,'textures':{'particle':'elementalcircuits:item/books','books':'elementalcircuits:item/books'}}
    (OUT/(name+'.json')).write_text(json.dumps(model,indent=2)+'\n',encoding='utf-8')
    return {'name':name,'vertices':v,'faces':faces}

stats=[];stages=[]
for notebook in (False,True):
    skin='notebook' if notebook else 'spellbook'; leather=1 if notebook else 0; cover=3 if notebook else 2
    groups={k:[] for k in ('spine','front_cover','back_cover','front_pages','back_pages','turning_leaf')}
    def b(group,name,pos,size,tile,**kw):o=box(skin+'_'+name,pos,size,tile,**kw);groups[group].append(o);return o
    b('spine','rounded_binding',(.177,.5,.5),(.085,.224,.845),leather)
    for z in (.15,.32,.68,.85):b('spine','brass_rib',(.175,.5,z),(.096,.232,.025),6)
    b('front_cover','embossed_cover',(.52,.618,.5),(.64,.028,.84),leather,top=cover,bottom=8)
    b('back_cover','back_leather',(.52,.382,.5),(.64,.028,.84),leather,top=8)
    for group,y in [('front_cover',.639),('back_cover',.361)]:
        for x in (.235,.805):
            for z in (.115,.885):
                b(group,'corner_long',(x,y,z),(.025,.012,.075),6)
                b(group,'corner_short',(x+(.026 if x<.5 else -.026),y,z+(.025 if z<.5 else -.025)),(.077,.012,.022),6)
    b('front_cover','centre_mount',(.52,.642,.5),(.134,.016,.134),6)
    groups['front_cover'].append(gem(skin+'_blue_crystal',(.52,.668,.5),.065,.035,7 if not notebook else 14))
    b('front_cover','latch',(.835,.63,.5),(.052,.039,.10),6)
    b('back_cover','bookmark',(.43,.389,.92),(.045,.009,.15),11 if notebook else 15)
    b('front_pages','upper_paper',(.514,.553,.5),(.574,.088,.755),4,top=9,bottom=12 if notebook else 5)
    b('back_pages','lower_paper',(.514,.447,.5),(.574,.088,.755),4,top=12 if notebook else 5,bottom=9)
    b('turning_leaf','loose_manuscript',(.513,.499,.5),(.566,.003,.746),9,top=12 if notebook else 5,bottom=5)
    for name,objects in groups.items():stats.append(export(skin+'_'+name,objects))
    closed=[o for k,v in groups.items() if k!='turning_leaf' for o in v];stats.append(export(skin+'_closed',closed))
    # Editable native Blender animation with the exact same .2/.5/.5 spine pivot.
    bpy.ops.object.empty_add(location=(.2,-.5,.5));hinge=bpy.context.object;hinge.name=skin+'_cover_hinge'
    for o in groups['front_cover']+groups['front_pages']:
        matrix=o.matrix_world.copy();o.parent=hinge;o.matrix_world=matrix
    hinge.rotation_euler.y=0;hinge.keyframe_insert('rotation_euler',frame=1);hinge.rotation_euler.y=-math.radians(145);hinge.keyframe_insert('rotation_euler',frame=11)
    for objects in groups.values():
        for o in objects:o.hide_render=True
    # Separate studio instances: one closed, one open for each binding.
    for opened in (False,True):
        for group,objects in groups.items():
            if group=='turning_leaf':continue
            for o in objects:
                clone=o.copy();clone.data=o.data;clone.parent=None;bpy.context.collection.objects.link(clone);clone.name='studio_'+skin+'_'+group;clone.hide_render=False
                # The authoring objects were exported before articulation.
                matrix=o.matrix_world.copy()
                if o.parent: matrix=hinge.matrix_world.inverted()@matrix;matrix=Matrix.Translation(Vector((.2,-.5,.5)))@matrix
                if opened and group in ('front_cover','front_pages'):
                    pivot=Matrix.Translation(Vector((.2,-.5,.5)));matrix=pivot@Matrix.Rotation(-math.radians(145),4,'Y')@pivot.inverted()@matrix
                clone.matrix_world=Matrix.Translation(Vector(((.85 if opened else -.35),1.2 if notebook else 0,0)))@matrix

(OUT/'books.mtl').write_text('newmtl books\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #books\n',encoding='utf-8')
display={'gui':{'rotation':[25,205,0],'translation':[0,0,0],'scale':[1,1,1]},
 'firstperson_righthand':{'rotation':[55,-20,-15],'translation':[-1,0,0],'scale':[.85,.85,.85]},
 'firstperson_lefthand':{'rotation':[55,20,15],'translation':[-1,0,0],'scale':[.85,.85,.85]},
 'thirdperson_righthand':{'rotation':[65,0,0],'translation':[0,1,0],'scale':[.7,.7,.7]},
 'thirdperson_lefthand':{'rotation':[65,0,0],'translation':[0,1,0],'scale':[.7,.7,.7]},
 'ground':{'translation':[0,3,0],'scale':[.55,.55,.55]},'fixed':{'rotation':[90,0,0],'scale':[.8,.8,.8]}}
for item in ('spellbook','blueprint_notebook'):
    (ASSET/f'models/item/{item}.json').write_text(json.dumps({'parent':'builtin/entity','gui_light':'front','textures':{'particle':'elementalcircuits:item/books'},'display':display},indent=2)+'\n',encoding='utf-8')
scene=bpy.context.scene;scene.frame_set(1);scene.render.engine='CYCLES';scene.cycles.samples=24;scene.render.resolution_x=1440;scene.render.resolution_y=1000;scene.render.resolution_percentage=100;scene.world.color=(.1,.12,.15)
bpy.ops.object.camera_add(location=(4,-5,5));camera=bpy.context.object;target=Vector((1,.2,.5));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.type='ORTHO';camera.data.ortho_scale=3.9;scene.camera=camera
for pos,power in [((1,-3,5),650),((4,2,4),450),((-2,0,3),300)]:
    bpy.ops.object.light_add(type='AREA',location=pos);light=bpy.context.object;light.data.energy=power;light.data.size=4;light.rotation_euler=(target-light.location).to_track_quat('-Z','Y').to_euler()
atlas.pack();bpy.ops.wm.save_as_mainfile(filepath=str(ART/'articulated-books.blend'))
(ART/'book-metrics.json').write_text(json.dumps({'version':1,'blender':bpy.app.version_string,'models':stats,'hinge':[.2,.5,.5],'max_angle_degrees':145,'atlas':'Aseprite 512px with padded 64px paintings'},indent=2)+'\n',encoding='utf-8')
scene.render.image_settings.file_format='PNG';scene.render.filepath=str(ART/'books-studio.png');bpy.ops.render.render(write_still=True)
print('BLENDER_BOOKS_PASS: two bindings, six animated rigid parts per book, native OBJ and editable source')
