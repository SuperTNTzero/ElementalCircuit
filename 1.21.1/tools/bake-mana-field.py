"""Original pixel artwork and native JSON models for mana crystals, ore and nodes."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parents[1]/'src/main/resources'
assets=root/'assets/elementalcircuits'
data=root/'data'
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def stone(seed,deep=False):
    rng=random.Random(seed);im=Image.new('RGB',(32,32));px=im.load()
    for y in range(32):
        for x in range(32):
            v=(58 if deep else 114)+rng.randint(-12,12);px[x,y]=(v,v+3,v+5)
    return im
def save(im,name):
    path=assets/'textures'/name;path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
for name,deep in [('mana_crystal_ore',False),('deepslate_mana_crystal_ore',True)]:
    im=stone(193,deep);d=ImageDraw.Draw(im)
    for x,y in [(5,5),(21,4),(13,15),(26,23),(5,26)]:
        d.polygon([(x,y-3),(x+3,y),(x+1,y+4),(x-2,y+1)],fill='#235b79',outline='#132d40')
        d.line([(x,y-2),(x+1,y),(x,y+3)],fill='#76dbef',width=2);d.point((x-1,y),fill='#d5f6ff')
    save(im,'block/'+name+'.png')
    write(assets/'models/block'/f'{name}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'elementalcircuits:block/'+name}})
    write(assets/'blockstates'/f'{name}.json',{'variants':{'':{'model':'elementalcircuits:block/'+name}}})
    write(assets/'models/item'/f'{name}.json',{'parent':'elementalcircuits:block/'+name})

im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(14,2),(23,10),(21,25),(13,30),(6,21),(7,10)],fill='#16394f',outline='#0c2135')
d.polygon([(14,3),(18,12),(13,28),(7,20),(8,11)],fill='#3b9dc3')
d.polygon([(14,3),(22,10),(18,12)],fill='#c4f5ff');d.polygon([(18,12),(22,10),(20,24),(13,28)],fill='#236688')
d.line([(14,5),(10,12),(10,20)],fill='#89e4f3',width=2)
save(im,'item/mana_crystal.png')
write(assets/'models/item/mana_crystal.json',{'parent':'minecraft:item/generated','textures':{'layer0':'elementalcircuits:item/mana_crystal'}})
for lit in (False,True):
    suffix='_on' if lit else '_off'
    blue='#62d4f0' if lit else '#2e5967';dark='#245b76' if lit else '#20303a'
    im=stone(975,True);d=ImageDraw.Draw(im)
    d.polygon([(4,0),(13,8),(10,17),(24,25),(28,31),(19,27),(6,19),(8,10),(0,4)],fill=dark)
    d.line([(5,0),(11,9),(8,17),(23,26),(25,31)],fill=blue,width=2)
    d.polygon([(23,4),(27,10),(22,16),(19,10)],fill=dark,outline=blue)
    save(im,'block/natural_mana_node'+suffix+'.png')
    write(assets/'models/block'/f'natural_mana_node{suffix}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'elementalcircuits:block/natural_mana_node'+suffix}})
    im=Image.new('RGB',(32,32),'#364047');d=ImageDraw.Draw(im)
    d.rectangle((1,1,30,30),outline='#bc9658',width=2);d.rectangle((4,4,27,27),outline='#57452c')
    for x in (4,26):
        for y in (4,26):d.rectangle((x,y,x+1,y+1),fill='#eed5a1')
    d.polygon([(16,7),(22,16),(16,25),(10,16)],fill=dark,outline=blue);d.line((16,2,16,6),fill='#c0a16b')
    save(im,'block/mana_node'+suffix+'.png')
    crystal=Image.new('RGB',(16,16),dark);cd=ImageDraw.Draw(crystal);cd.polygon([(0,0),(9,0),(4,15),(0,15)],fill=blue);cd.line((1,0,1,15),fill='#a5edf9' if lit else '#46717a')
    save(crystal,'block/mana_node_crystal'+suffix+'.png')
    texture='elementalcircuits:block/mana_node'+suffix
    faces={face:{'texture':'#body'} for face in ['north','south','east','west','up','down']}
    gemfaces={face:{'texture':'#gem'} for face in faces}
    write(assets/'models/block'/f'mana_node{suffix}.json',{'parent':'minecraft:block/block','textures':{'particle':texture,'body':texture,'gem':'elementalcircuits:block/mana_node_crystal'+suffix},'elements':[
        {'from':[0,0,0],'to':[16,7,16],'faces':faces},
        {'from':[5,7,5],'to':[11,16,11],'rotation':{'origin':[8,8,8],'axis':'y','angle':45},'faces':gemfaces},
        {'from':[2,7,2],'to':[4,13,4],'faces':faces},{'from':[12,7,12],'to':[14,13,14],'faces':faces}
    ]})
for name in ('natural_mana_node','mana_node'):
    write(assets/'blockstates'/f'{name}.json',{'variants':{'lit=false':{'model':f'elementalcircuits:block/{name}_off'},'lit=true':{'model':f'elementalcircuits:block/{name}_on'}}})
    write(assets/'models/item'/f'{name}.json',{'parent':f'elementalcircuits:block/{name}_on'})

# Native datapack worldgen; only newly generated Overworld terrain receives deposits.
targets=lambda stone,deep:[{'target':{'predicate_type':'minecraft:tag_match','tag':tag},'state':{'Name':'elementalcircuits:'+block,**({'Properties':{'lit':'true'}} if block=='natural_mana_node' else {})}} for tag,block in [('minecraft:stone_ore_replaceables',stone),('minecraft:deepslate_ore_replaceables',deep)]]
for name,size,count,low,high in [('mana_crystal_ore',6,6,-48,48),('natural_mana_node',1,1,-40,16)]:
    natural=name=='natural_mana_node'
    config={'targets':targets(name,name if natural else 'deepslate_mana_crystal_ore')}
    if not natural:config.update(size=size,discard_chance_on_air_exposure=0)
    write(data/'elementalcircuits/worldgen/configured_feature'/f'{name}.json',{'type':'minecraft:replace_single_block' if natural else 'minecraft:ore','config':config})
    placement=[{'type':'minecraft:rarity_filter','chance':12}] if natural else [{'type':'minecraft:count','count':count}]
    placement += [{'type':'minecraft:in_square'},{'type':'minecraft:height_range','height':{'type':'minecraft:uniform' if natural else 'minecraft:trapezoid','min_inclusive':{'absolute':low},'max_inclusive':{'absolute':high},**({} if natural else {'plateau':0})}},{'type':'minecraft:biome'}]
    write(data/'elementalcircuits/worldgen/placed_feature'/f'{name}.json',{'feature':'elementalcircuits:'+name,'placement':placement})
write(data/'elementalcircuits/neoforge/biome_modifier/mana_deposits.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':['elementalcircuits:mana_crystal_ore','elementalcircuits:natural_mana_node'],'step':'underground_ores'})
for name in ('mana_crystal_ore','deepslate_mana_crystal_ore'):
    silk={'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}
    entry={'type':'minecraft:alternatives','children':[{'type':'minecraft:item','name':'elementalcircuits:'+name,'conditions':[silk]},{'type':'minecraft:item','name':'elementalcircuits:mana_crystal','functions':[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'function':'minecraft:explosion_decay'}]}]}
    write(data/'elementalcircuits/loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry]}]})
write(data/'elementalcircuits/recipe/mana_node.json',{'type':'minecraft:crafting_shaped','category':'redstone','pattern':['CAC','S S','CSC'],'key':{'C':{'item':'minecraft:copper_ingot'},'A':{'item':'minecraft:amethyst_shard'},'S':{'item':'minecraft:stone'}},'result':{'id':'elementalcircuits:mana_node','count':1}})
write(data/'elementalcircuits/advancement/recipes/mana_node.json',{'parent':'minecraft:recipes/root','criteria':{'has_crystal':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'elementalcircuits:mana_crystal'}]}},'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'elementalcircuits:mana_node'}}},'requirements':[['has_crystal','has_the_recipe']],'rewards':{'recipes':['elementalcircuits:mana_node']}})
write(data/'minecraft/tags/block/mineable/pickaxe.json',{'replace':False,'values':['elementalcircuits:mana_crystal_ore','elementalcircuits:deepslate_mana_crystal_ore','elementalcircuits:natural_mana_node','elementalcircuits:mana_node']})
write(data/'minecraft/tags/block/needs_iron_tool.json',{'replace':False,'values':['elementalcircuits:mana_crystal_ore','elementalcircuits:deepslate_mana_crystal_ore','elementalcircuits:natural_mana_node']})
print('Mana field assets baked: crystals, two ores, natural/artificial nodes and worldgen')
