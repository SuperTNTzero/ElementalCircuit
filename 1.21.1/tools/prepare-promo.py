"""Package actual clean UI captures and an editable cover draft. Does not create gameplay footage."""
from pathlib import Path
import hashlib
import json
import shutil
from PIL import Image, ImageDraw, ImageFont

PROJECT=Path(__file__).resolve().parents[1]
DEST=PROJECT/'docs/promo/v0.17.0'
DEST.mkdir(parents=True,exist_ok=True)
shots={'70':'inscription','71':'wand-workbench','72':'blueprint-notebook','73':'spellbook','74':'element-switching'}
records=[]
for stage,name in shots.items():
    src=PROJECT/f'build/client-smoke/screenshots/showcase-{stage}.png'
    target=DEST/f'{name}.png'
    shutil.copyfile(src,target)
    with Image.open(target) as im: size=im.size
    records.append({'file':target.name,'size':size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'source':'Minecraft client; detached interface demonstration; not survival footage'})

fontfile=Path('C:/Windows/Fonts/msyh.ttc')
if not fontfile.exists():raise SystemExit('已复制截图；生成中文封面需要在脚本中设置本机中文字体路径。')
def font(size):return ImageFont.truetype(str(fontfile),size)
cover=Image.new('RGBA',(1280,720),'#151a1b')
with Image.open(DEST/'inscription.png') as shot:
    cover.alpha_composite(shot.convert('RGBA').resize((960,600),Image.Resampling.LANCZOS),(320,60))
fade=Image.new('RGBA',(1280,720))
d=ImageDraw.Draw(fade)
for x in range(720):d.line((x,0,x,719),fill=(21,26,27,255 if x<360 else int(255*(720-x)/360)))
cover=Image.alpha_composite(cover,fade);d=ImageDraw.Draw(cover)
d.line((52,176,100,176),fill='#d9ba7a',width=3)
d.text((48,200),'元素法阵',font=font(64),fill='#e5d5ae')
d.text((52,292),'画法阵，造法术',font=font(27),fill='#d7dfd1')
d.text((54,510),'Minecraft 1.21.1 / NeoForge',font=font(19),fill='#a6aea3')
d.text((54,550),'开发预览',font=font(22),fill='#9ac7ba')
cover.convert('RGB').save(DEST/'cover-draft.png')

captions=[(0,3,'元素法阵'),(3,8,'画一张法阵'),(8,14,'给火球加上追踪和分裂'),(14,19,'换成水，再试一次'),(19,24,'组装自己的法杖'),(24,29,'把法阵收进法术书'),(29,34,'固定法阵也能相互连接'),(34,38,'蓝图码，复制即分享'),(38,42,'Minecraft 1.21.1 · NeoForge\n开发预览')]
def timestamp(t):return f'00:00:{t:02},000'
(DEST/'captions.zh-CN.srt').write_text('\n'.join(f'{i}\n{timestamp(a)} --> {timestamp(b)}\n{text}\n' for i,(a,b,text) in enumerate(captions,1)),encoding='utf-8')
(DEST/'sources.json').write_text(json.dumps({'version':'0.17.0','captures':records,'cover':'Layout draft based on the inscription screenshot; not gameplay footage','videoRecorded':False},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Prepared five actual UI references, cover draft, SRT and source manifest')
