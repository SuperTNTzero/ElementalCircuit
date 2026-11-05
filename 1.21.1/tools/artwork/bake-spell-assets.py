"""Deterministic CC0 adaptation. Pillow required; no generated/AI images as inputs.
Shared clip crop prevents frame-to-frame scale jumps. Archives remain source-only.
"""
from pathlib import Path
import hashlib, io, json, zipfile
from PIL import Image, ImageOps, ImageDraw, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/vfx'
OUT = ROOT / 'src/main/resources/assets/elementalcircuits'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    manifest = json.loads((ART/'sources.json').read_text(encoding='utf-8'))
    sources = {s['id']: s for s in manifest['sources']}
    archives = {}
    for key, s in sources.items():
        data = (ART/'sources'/f'{key}.zip').read_bytes()
        if sha(data) != s['sha256']:
            raise ValueError('Source changed: '+key)
        archives[key] = zipfile.ZipFile(io.BytesIO(data))
    atlas = Image.new('RGBA', (1024,1024))
    recipes = []
    def clip(slots, key, files, mode='color', hue=None):
        images = []
        for name in files:
            im = Image.open(io.BytesIO(archives[key].read(name))).convert('RGBA')
            if mode == 'ink':
                alpha = ImageOps.invert(im.convert('L')).point(lambda a: min(255,a*2))
                im = Image.new('RGBA',im.size,'white'); im.putalpha(alpha)
            elif mode == 'mask':
                # Alpha-weighted white masks retain soft edges without dark RGB fringes.
                alpha = im.getchannel('A')
                im = Image.new('RGBA',im.size,'white');im.putalpha(alpha)
            elif hue:
                alpha = im.getchannel('A');im = ImageOps.colorize(im.convert('L'),hue[0],hue[1]).convert('RGBA');im.putalpha(alpha)
            if key == 'fire' and mode == 'color':
                # Published sprites include black rim pixels; alpha-weight luminance
                # before tinting keeps flame edges bright on daylight terrain.
                alpha=ImageChops.multiply(im.getchannel('A'),im.convert('L').point(lambda a:min(255,a*3)))
                im=ImageOps.colorize(im.convert('L'),'#b82400','#fff1a0').convert('RGBA');im.putalpha(alpha)
            images.append(im)
        boxes = [im.getchannel('A').point(lambda a: 255 if a>12 else 0).getbbox() for im in images]
        box = (min(b[0] for b in boxes),min(b[1] for b in boxes),max(b[2] for b in boxes),max(b[3] for b in boxes))
        for i, slot in enumerate(slots):
            index = round(i*(len(images)-1)/max(1,len(slots)-1))
            frame = images[index].crop(box)
            frame.thumbnail((112,112), Image.Resampling.LANCZOS)
            tile = Image.new('RGBA',(128,128));tile.alpha_composite(frame,((128-frame.width)//2,(128-frame.height)//2))
            atlas.alpha_composite(tile,(slot%8*128,slot//8*128))
            name=files[index]
            recipes.append(dict(slot=slot,source=key,file=name,sourceFrameSha256=sha(archives[key].read(name)),operation=mode,unionCrop=box,hue=hue))
    pure=lambda n:f'Files/Pure_{n:02}.png'
    water=lambda group,n:f'{group:02}/Water__{n:02}.png'
    fire=lambda n:f'SD/{(n-1)//5+1}/Fire-Wrath__{n:02}.png'
    cosmic=lambda n:f'{(n-1)//5+1}/Cosmic_{n:02}.png'
    electric=lambda n:f'fx8_lighteningBall/lighteningball_1_20_{n}.png'
    smoke=lambda n:f'fx10_blackExplosion/smoke_black_1_19_{n}.png'
    clip([1],'nyrthos',[smoke(10)],'mask')
    clip([3],'water',[water(4,3)],'mask')
    clip([4],'pure',[pure(21)],'mask')
    clip([5,6,7],'circles',['circle4.png','circle5.png','circle6.png'],'ink')
    clip([8],'fire',[fire(18)],'mask')
    clip([9],'water',[water(1,3)],'mask')
    clip([11],'water',[water(3,3)],'mask')
    clip([12],'pure',[pure(21)],'mask')
    clip([13],'water',[water(4,4)],'mask')
    clip([14],'circles',['circle7.png'],'ink')
    clip([15],'cosmic',[cosmic(24)],'mask')
    clip(range(16,24),'nyrthos',[f'fx5_fire_scissors/spell_twoflames_{n}.png' for n in range(2,10)],'color',('#c02b05','#fff7ad'))
    clip(range(24,32),'water',[water(2,n) for n in range(1,6)])
    clip(range(32,40),'pure',[pure(n) for n in range(11,16)],'color',('#220a44','#ead6ff'))
    clip(range(40,48),'cosmic',[cosmic(n) for n in range(21,26)])
    clip(range(48,52),'nyrthos',[electric(n) for n in [4,7,10,13]],'mask')
    clip(range(52,56),'water',[water(3,n) for n in range(2,6)],'mask')
    clip(range(56,60),'pure',[pure(n) for n in range(21,25)],'mask')
    # Soft glow/line primitives and faceted stones are code-authored static masks.
    glow=Image.new('RGBA',(128,128));px=glow.load()
    for y in range(8,120):
        for x in range(8,120):
            r=((x-63.5)**2+(y-63.5)**2)**.5/55
            px[x,y]=(255,255,255,int(255*max(0,1-r*r)**2))
    atlas.alpha_composite(glow,(0,0));recipes.append(dict(slot=0,source='code',operation='soft radial glow mask'))
    tile=Image.new('RGBA',(128,128));px=tile.load()
    for y in range(8,120):
        for x in range(8,120):
            a=int(255*max(0,1-abs(y-63.5)/28)**1.3*min(1,(x-7)/8,(120-x)/8))
            px[x,y]=(255,255,255,a)
    atlas.alpha_composite(tile,(256,0));recipes.append(dict(slot=2,source='code',operation='soft ribbon mask'))
    for i,slot in enumerate([10,60,61,62,63]):
        tile=Image.new('RGBA',(128,128));d=ImageDraw.Draw(tile)
        pts=[(20,40),(45+i*2,17),(92,27+i*2),(112,65),(88,108),(36,103),(15,74)]
        d.polygon(pts,fill=(164,164,164,255));d.polygon([pts[0],pts[1],pts[2],(63,61)],fill=(243,243,243,255));d.polygon([pts[2],pts[3],pts[4],(63,61)],fill=(194,194,194,255));d.polygon([pts[4],pts[5],pts[6],(63,61)],fill=(100,100,100,255));
        atlas.alpha_composite(tile,(slot%8*128,slot//8*128));recipes.append(dict(slot=slot,source='code',operation='faceted stone mask'))
    target=OUT/'textures/effect/elemental_spells.png';target.parent.mkdir(parents=True,exist_ok=True);atlas.save(target,optimize=True)
    manifest.update(atlas='textures/effect/elemental_spells.png',atlasSha256=sha(target.read_bytes()),tileSize=128,columns=8,recipes=sorted(recipes,key=lambda r:r['slot']))
    (OUT/'art/spell_sources.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    preview=Image.new('RGB',(1024,1088),(21,26,36));preview.paste(atlas,(0,32),atlas);d=ImageDraw.Draw(preview)
    for i in range(64):d.text((i%8*128+4,i//8*128+32),str(i),fill='white')
    preview.save(ART/'atlas-review.png');print('Baked 64 tiles:',target,manifest['atlasSha256'])

if __name__=='__main__':
    main()
