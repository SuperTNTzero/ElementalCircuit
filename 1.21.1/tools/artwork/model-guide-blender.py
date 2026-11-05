"""Original static open field guide; native OBJ is selected only for held perspectives."""
from pathlib import Path
import bpy
import json
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/elementalcircuits'
OUT = ASSETS / 'models/item/guide'
ART = ROOT / 'art/blender/guide'
OUT.mkdir(parents=True, exist_ok=True)
ART.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
atlas = bpy.data.images.load(str(ASSETS / 'textures/item/guide_atlas.png'))
material = bpy.data.materials.new('Deep navy leather, brass corners and field notes')
material.use_nodes = True
shader = material.node_tree.nodes.get('Principled BSDF')
texture = material.node_tree.nodes.new('ShaderNodeTexImage')
texture.image = atlas
texture.interpolation = 'Closest'
material.node_tree.links.new(texture.outputs['Color'], shader.inputs['Base Color'])
shader.inputs['Roughness'].default_value = .72
objects = []

def box(name, x, y, z, w, h, d, tile, top=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, -z, y))
    obj = bpy.context.object
    obj.name = name
    obj.scale = (w, d, h)
    obj.data.materials.append(material)
    obj['tile'] = tile
    obj['top'] = tile if top is None else top
    objects.append(obj)
    return obj

# Open spread lies flat in X/Z; Minecraft Y remains the thickness axis.
for sign in (-1, 1):
    x = .5 + sign * .31
    box(('left' if sign < 0 else 'right') + '_cover', x, .40, .5, .60, .035, .82, 0)
    box('paper_block', x, .445, .5, .55, .065, .75, 1)
    box('written_page', x, .481, .5, .55, .006, .75, 2 if sign < 0 else 3)
    for edge_x in (x - .265, x + .265):
        for edge_z in (.125, .875):
            box('brass_corner', edge_x, .426, edge_z, .027, .012, .065, 4)
            box('corner_return', edge_x + (.023 if edge_x < x else -.023), .426,
                edge_z + (.020 if edge_z < .5 else -.020), .065, .012, .022, 4)
box('leather_spine', .5, .422, .5, .065, .06, .83, 0)
box('brass_binding', .5, .456, .14, .07, .012, .03, 4)
box('brass_binding', .5, .456, .86, .07, .012, .03, 4)
box('blue_bookmark', .86, .484, .90, .035, .007, .17, 5)

bpy.context.view_layer.update()
lines = ['# Original Blender field guide, static open spread', 'mtllib guide.mtl']
vertices = uvs = faces = 0
for obj in objects:
    lines += ['g ' + obj.name, 'usemtl guide']
    for vertex in obj.data.vertices:
        p = obj.matrix_world @ vertex.co
        lines.append(f'v {p.x:.7f} {p.z:.7f} {-p.y:.7f}')
    layer = obj.data.uv_layers.active or obj.data.uv_layers.new(name='Padded pixel atlas')
    for face in obj.data.polygons:
        axis = max(range(3), key=lambda i: abs(face.normal[i]))
        axes = [i for i in range(3) if i != axis]
        tile = obj['top'] if axis == 2 and face.normal.z > 0 else obj['tile']
        minimum = [min(v.co[i] for v in obj.data.vertices) for i in axes]
        maximum = [max(v.co[i] for v in obj.data.vertices) for i in axes]
        refs = []
        for index, loop in zip(face.vertices, face.loop_indices):
            p = obj.data.vertices[index].co
            u = ((tile % 4) * 64 + 8.5 + (p[axes[0]] - minimum[0]) / (maximum[0] - minimum[0]) * 47) / 256
            v = 1 - ((tile // 4) * 64 + 8.5 + (p[axes[1]] - minimum[1]) / (maximum[1] - minimum[1]) * 47) / 128
            layer.data[loop].uv = (u, v)
            lines.append(f'vt {u:.7f} {v:.7f}')
            uvs += 1
            refs.append(f'{vertices + index + 1}/{uvs}')
        lines.append('f ' + ' '.join(refs))
        faces += 1
    vertices += len(obj.data.vertices)
(OUT / 'held.obj').write_text('\n'.join(lines) + '\n', encoding='utf-8')
(OUT / 'guide.mtl').write_text('newmtl guide\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #guide\n', encoding='utf-8')
display = {
    'firstperson_righthand': {'rotation': [15, 0, 0], 'translation': [-4, 5, 0], 'scale': [.46] * 3},
    'firstperson_lefthand': {'rotation': [15, 0, 0], 'translation': [-4, 5, 0], 'scale': [.46] * 3},
    'thirdperson_righthand': {'rotation': [90, 0, 0], 'translation': [0, 1, 0], 'scale': [.64] * 3},
    'thirdperson_lefthand': {'rotation': [90, 0, 0], 'translation': [0, 1, 0], 'scale': [.64] * 3},
}
model = {'loader': 'neoforge:obj', 'model': 'elementalcircuits:models/item/guide/held.obj',
         'automatic_culling': False, 'flip_v': True, 'shade_quads': True,
         'textures': {'particle': 'elementalcircuits:item/guide_atlas', 'guide': 'elementalcircuits:item/guide_atlas'},
         'display': display}
(OUT / 'held.json').write_text(json.dumps(model, indent=2) + '\n', encoding='utf-8')
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 16
scene.render.resolution_x = 1000
scene.render.resolution_y = 700
scene.render.resolution_percentage = 100
scene.world.color = (.15, .18, .22)
bpy.ops.object.camera_add(location=(2, -2, 2))
camera = bpy.context.object
target = Vector((.5, -.5, .43))
camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 1.8
scene.camera = camera
bpy.ops.object.light_add(type='AREA', location=(1, -1, 3))
bpy.context.object.data.energy = 200
bpy.context.object.data.size = 3
atlas.pack()
bpy.ops.wm.save_as_mainfile(filepath=str(ART / 'field-guide.blend'))
(ART / 'metrics.json').write_text(json.dumps({'vertices': vertices, 'quads': faces, 'objects': len(objects),
    'blender': bpy.app.version_string, 'heldOnly': True, 'runtimeAnimation': False}, indent=2) + '\n', encoding='utf-8')
scene.render.filepath = str(ART / 'studio.png')
bpy.ops.render.render(write_still=True)
print(f'BLENDER_GUIDE_PASS: {vertices} vertices, {faces} static quads; hand-only model')
