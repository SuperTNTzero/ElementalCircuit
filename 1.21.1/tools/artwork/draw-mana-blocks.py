"""16px original pixel assets + native Minecraft cuboid models; Aseprite sources are exported separately."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, random
ROOT=Path(__file__).resolve().parents[2]
RES=ROOT/'src/main/resources';ASSETS=RES/'assets/elementalcircuits';TEX=ASSETS/'textures/block'
ART=ROOT/'art/aseprite/mana-blocks';ART.mkdir(parents=True,exist_ok=True)
def write(path,obj):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def save(im,name):
 TEX.mkdir(parents=True,exist_ok=True);im.save(TEX/(name+'.png'));im.save(ART/(name+'.png'))
def rock(deep=False):
 rng=random.Random(3907 if deep else 3901);palette=['#434448','#4d4d50','#56565a','#626164'] if deep else ['#777777','#828282','#8b8b8b','#929292']
 im=Image.new('RGB',(16,16),palette[1]);d=ImageDraw.Draw(im)
 for i in range(48):
  x=rng.randrange(16);y=rng.randrange(16);w=rng.choice([2,3,4]);h=rng.choice([1,2]);d.rectangle((x,y,x+w,y+h),fill=rng.choice(palette))
 if deep:
  for y in [3,7,11,15]:d.line((0,y,15,y),fill=palette[0]);d.line((0,y-1,15,y-1),fill=palette[2])
 return im
for deep,name in [(False,'mana_crystal_ore'),(True,'deepslate_mana_crystal_ore')]:
 im=rock(deep);d=ImageDraw.Draw(im)
 for x,y in [(2,3),(10,2),(6,7),(12,11),(2,12)]:
  d.rectangle((x-1,y-1,x+2,y+2),fill='#415d67');d.rectangle((x,y,x+1,y+1),fill='#55a4bd');d.point((x,y),fill='#b2e8eb');d.point((x+2,y+1),fill='#286780')
 save(im,name);write(ASSETS/'models/block'/(name+'.json'),{'parent':'minecraft:block/cube_all','textures':{'all':'elementalcircuits:block/'+name}})
for lit in [False,True]:
 suffix='_on' if lit else '_off';light='#a8e9ed' if lit else '#63858e';blue='#49a9c3' if lit else '#355569';dark='#22526e' if lit else '#243c4b'
 im=rock(True);d=ImageDraw.Draw(im)
 d.polygon([(4,1),(11,1),(14,5),(14,11),(10,15),(4,14),(1,10),(1,5)],fill='#263d4a')
 d.polygon([(7,2),(12,5),(12,11),(8,14),(4,11),(4,5)],fill=dark)
 d.polygon([(7,2),(8,7),(4,11),(4,5)],fill=blue);d.polygon([(8,7),(12,5),(12,11),(8,14)],fill='#33778e' if lit else '#314956');d.line((7,3,5,5,5,9),fill=light)
 save(im,'natural_mana_node'+suffix)
 top=im.transpose(Image.Transpose.ROTATE_90);save(top,'natural_mana_node_top'+suffix)
 write(ASSETS/'models/block'/('natural_mana_node'+suffix+'.json'),{'parent':'minecraft:block/cube','textures':{'particle':'elementalcircuits:block/natural_mana_node'+suffix,**{face:'elementalcircuits:block/natural_mana_node'+suffix for face in ['north','south','east','west','down']},'up':'elementalcircuits:block/natural_mana_node_top'+suffix}})
 im=Image.new('RGB',(16,16),'#514c3e');d=ImageDraw.Draw(im);d.rectangle((0,0,15,15),outline='#352f27');d.rectangle((1,1,14,14),outline='#b89557');d.rectangle((2,2,13,13),outline='#6a5636');d.rectangle((4,4,11,11),fill='#263641');d.line((4,4,11,4),fill='#4e6570');d.rectangle((6,6,9,9),fill=blue)
 for x in [2,12]:
  for y in [2,12]:d.point((x,y),fill='#dec58c');d.point((x+1,y+1),fill='#836536')
 save(im,'mana_node'+suffix)
 gem=Image.new('RGB',(16,16),dark);d=ImageDraw.Draw(gem);d.polygon([(0,0),(6,0),(9,15),(0,15)],fill=blue);d.polygon([(6,0),(13,0),(9,15)],fill='#377e9e' if lit else '#365569');d.line((1,1,2,14),fill=light);d.line((14,0,14,15),fill='#143b50');save(gem,'mana_node_crystal'+suffix)
 textures={'particle':'elementalcircuits:block/mana_node'+suffix,'body':'elementalcircuits:block/mana_node'+suffix,'gem':'elementalcircuits:block/mana_node_crystal'+suffix,'brass':'elementalcircuits:block/mana_node_brass'}
 parts=[]
 def cube(a,b,t,rotation=None):
  p={'from':a,'to':b,'faces':{f:{'texture':'#'+t} for f in ['up','down','north','south','east','west']}}
  if rotation:p['rotation']=rotation
  parts.append(p)
 cube([0,0,0],[16,2,16],'brass');cube([1,2,1],[15,6,15],'body');cube([2,6,2],[14,7,14],'brass')
 for x in [2,12]:
  for z in [2,12]:cube([x,7,z],[x+2,10,z+2],'brass')
 cube([5,7,5],[11,13,11],'gem',{'origin':[8,8,8],'axis':'y','angle':45});cube([6,13,6],[10,15,10],'gem',{'origin':[8,8,8],'axis':'y','angle':45});cube([7,15,7],[9,16,9],'gem')
 write(ASSETS/'models/block'/('mana_node'+suffix+'.json'),{'parent':'minecraft:block/block','textures':textures,'elements':parts})
im=Image.new('RGB',(16,16),'#907345');d=ImageDraw.Draw(im);d.line((0,0,15,0),fill='#dfc38a');d.line((0,1,15,1),fill='#b29760');d.line((0,14,15,14),fill='#604d31');d.line((0,15,15,15),fill='#3d3326')
for x,y in [(3,5),(11,10),(6,12),(13,3)]:d.rectangle((x,y,x+1,y),fill='#a98c58')
save(im,'mana_node_brass')
for side in [True,False]:
 im=Image.new('RGB',(16,16),'#28617d');d=ImageDraw.Draw(im)
 d.polygon([(0,0),(8,0),(5,7),(0,11)],fill='#4c9eb5');d.polygon([(8,0),(15,0),(15,8),(9,6)],fill='#367c97');d.polygon([(0,11),(5,7),(11,15),(0,15)],fill='#337e9b');d.polygon([(9,6),(15,8),(15,15),(11,15)],fill='#1c4c68')
 d.line((0,10,5,6,8,0),fill='#72bfcf');d.line((6,8,10,14,14,15),fill='#4489a3');d.line((0,15,15,15),fill='#153b54')
 for x in [0,14]:
  for y in [0,14]:d.rectangle((x,y,x+1,y+1),fill='#997b48');d.point((x,y),fill='#d7bd80')
 if not side:d.rectangle((5,5,10,10),outline='#6bb8ca');d.rectangle((7,7,8,8),fill='#b1e0e4')
 save(im,'mana_crystal_block'+('' if side else '_top'))
write(ASSETS/'models/block/mana_crystal_block.json',{'parent':'minecraft:block/cube_column','textures':{'side':'elementalcircuits:block/mana_crystal_block','end':'elementalcircuits:block/mana_crystal_block_top'}})
write(ASSETS/'models/item/mana_crystal_block.json',{'parent':'elementalcircuits:block/mana_crystal_block'})
write(ASSETS/'blockstates/mana_crystal_block.json',{'variants':{'':{'model':'elementalcircuits:block/mana_crystal_block'}}})
write(RES/'data/elementalcircuits/recipe/mana_crystal_block.json',{'type':'minecraft:crafting_shaped','category':'building','pattern':['CCC','CCC','CCC'],'key':{'C':{'item':'elementalcircuits:mana_crystal'}},'result':{'id':'elementalcircuits:mana_crystal_block','count':1}})
write(RES/'data/elementalcircuits/recipe/mana_crystal_from_block.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'elementalcircuits:mana_crystal_block'}],'result':{'id':'elementalcircuits:mana_crystal','count':9}})
write(RES/'data/elementalcircuits/loot_table/blocks/mana_crystal_block.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'elementalcircuits:mana_crystal_block'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
p=RES/'data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(p.read_text(encoding='utf-8'));v='elementalcircuits:mana_crystal_block'
if v not in tag['values']:tag['values'].append(v)
write(p,tag)
# Nearest-neighbour overview of source pixels; no painted smoothing or dynamic asset generation.
files=['mana_crystal_ore','deepslate_mana_crystal_ore','natural_mana_node_on','natural_mana_node_off','mana_node_on','mana_node_off','mana_node_crystal_on','mana_crystal_block','mana_crystal_block_top']
sheet=Image.new('RGB',(len(files)*100,116),'#18262e');draw=ImageDraw.Draw(sheet)
for i,name in enumerate(files):sheet.paste(Image.open(TEX/(name+'.png')).resize((80,80),Image.Resampling.NEAREST),(i*100+10,8));draw.text((i*100+6,94),name[:14],fill='#c9b78b')
p=ROOT/'docs/images/crystal-casting';p.mkdir(parents=True,exist_ok=True);sheet.save(p/'pixel-assets.png')
print('Mana block assets generated at 16x16; no audio, worldgen or unrelated item textures changed.')

for recipe,ingredient in [('mana_crystal_block','mana_crystal'),('mana_crystal_from_block','mana_crystal_block')]:
 write(RES/('data/elementalcircuits/advancement/recipes/'+recipe+'.json'),{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['elementalcircuits:'+ingredient]}]}}},'rewards':{'recipes':['elementalcircuits:'+recipe]}})
