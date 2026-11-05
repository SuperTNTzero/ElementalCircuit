-- Original Aseprite pixel paintings. No runtime texture generation; fixed limited palettes.
local root=app.params.root or app.fs.currentPath
app.fs.makeDirectory(root..'/art/aseprite/parts')
local rgba=app.pixelColor.rgba
local s,img,W,H
local function C(hex) return {tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16)} end
local colors={edge=C('15272d'),dark=C('263b40'),brass=C('b58a48'),gold=C('d9b873'),light=C('f2d99c'),shadow=C('655131'),teal=C('3e929d'),ice=C('9be1dc'),white=C('e6f5ed')}
local function pixel(x,y,c) x=math.floor(x+.5) y=math.floor(y+.5) if x>=0 and y>=0 and x<W and y<H then img:drawPixel(x,y,rgba(c[1],c[2],c[3],255)) end end
local function line(x,y,u,v,c,w) local n=math.ceil(math.max(math.abs(u-x),math.abs(v-y))) for i=0,n do local t=n==0 and 0 or i/n for k=0,(w or 1)-1 do pixel(x+(u-x)*t+k,y+(v-y)*t,c) end end end
local function rect(x,y,w,h,c) for j=y,y+h-1 do for i=x,x+w-1 do pixel(i,j,c) end end end
local function poly(points,c) for y=0,H-1 do local xs={} for i,p in ipairs(points) do local q=points[i%#points+1] if (p[2]<=y and q[2]>y) or (q[2]<=y and p[2]>y) then xs[#xs+1]=p[1]+(y-p[2])*(q[1]-p[1])/(q[2]-p[2]) end end table.sort(xs) for j=1,#xs-1,2 do for x=math.ceil(xs[j]),math.floor(xs[j+1]) do pixel(x,y,c) end end end end
local function outline(points,c) for i,p in ipairs(points) do local q=points[i%#points+1] line(p[1],p[2],q[1],q[2],c) end end
local function ring(cx,cy,r,c) for i=0,180 do local a=i*math.pi/90 pixel(cx+r*math.cos(a),cy+r*math.sin(a),c) end end
local function hex(r) local p={} for i=0,5 do local a=-math.pi/2+i*math.pi/3 p[#p+1]={31.5+r*math.cos(a),31.5+r*math.sin(a)} end return p end
local function inside(x,y,p) local hit=false local j=#p for i=1,#p do local a,b=p[i],p[j] if ((a[2]>y)~=(b[2]>y)) and x<(b[1]-a[1])*(y-a[2])/(b[2]-a[2])+a[1] then hit=not hit end j=i end return hit end
local function begin(name,w,h) W=w or 64 H=h or 64 s=Sprite(W,H,ColorMode.RGB) s.layers[1].name=name img=s.cels[1].image end
local function layer(name) local l=s:newLayer() l.name=name img=s:newCel(l,s.frames[1],Image(W,H,ColorMode.RGB)).image end
local function finish(id,folder) s:saveAs(root..'/art/aseprite/parts/'..id..'.aseprite') s:saveCopyAs(root..'/src/main/resources/assets/elementalcircuits/textures/'..(folder or 'item')..'/'..id..'.png') end
local function band(y,w) rect(32-w//2,y,w,4,colors.edge) rect(33-w//2,y,w-2,2,colors.brass) line(34-w//2,y,30+w//2,y,colors.light) end
-- Hexagonal blank plate: broad usable center, raised machined rim, restrained engraving.
for rank,id in ipairs({'wood_plate','brass_plate','crystal_plate'}) do
 begin('基材与六角轮廓') local border=hex(29) poly(border,colors.edge) poly(hex(27),rank==3 and C('779da4') or colors.shadow) poly(hex(25),rank==3 and C('415b66') or colors.brass) poly(hex(23),rank==1 and C('785035') or rank==2 and C('806137') or C('246577'))
 local mask=hex(22)
 for y=8,55 do for x=8,55 do if inside(x,y,mask) then
  local c
  if rank==1 then local grain=math.floor(math.sin(y*.9+math.sin(x*.2))*4) local seam=y%11==0 and -15 or 0 c={117+grain+seam,79+grain+seam,46+grain+seam}
  elseif rank==2 then local n=(x*13+y*7)%5-2 local v=(y%8==0 and -5 or 0) c={133+n+v,103+n+v,57+n+v}
  else local n=x<32-y*.15 and 9 or -3 c={29+n,100+n,119+n} end pixel(x,y,c)
 end end end
 layer('镶边、固定铆钉与晶面') outline(hex(27),rank==3 and C('c4d9d6') or colors.gold) outline(hex(23),colors.shadow)
 for i=0,5 do local a=-math.pi/2+i*math.pi/3 local x,y=31.5+25*math.cos(a),31.5+25*math.sin(a) rect(math.floor(x)-1,math.floor(y)-1,3,3,colors.shadow) pixel(x-1,y-1,colors.light) pixel(x,y,rank==3 and colors.ice or colors.gold) end
 if rank==3 then line(32,11,24,28,C('53a6b4')) line(24,28,38,40,C('2c7b93')) line(38,40,32,52,C('2e829a')) line(24,28,13,34,C('3d91a4')) end
 layer('精细刻线与反光') outline(hex(19),rank==1 and C('b08b51') or rank==2 and C('d2af69') or C('4eafbc'))
 for i=0,5 do local a=-math.pi/2+i*math.pi/3 local x,y=31.5+21*math.cos(a),31.5+21*math.sin(a) local u,v=31.5+17*math.cos(a),31.5+17*math.sin(a) line(x,y,u,v,rank==3 and colors.ice or colors.light) end
 line(20,18,27,14,rank==3 and colors.ice or colors.light) line(13,26,13,35,rank==3 and C('80b8c1') or colors.gold)
 -- Three broken calibration marks leave the center blank for inscription.
 for k=0,2 do rect(26+k*5,43,2,1,rank==3 and colors.ice or colors.gold) end
 finish(id)
end
-- Distinct head silhouettes: clear quartz lance, caged amethyst, vented copper condenser.
for _,id in ipairs({'quartz_head','amethyst_head','copper_head'}) do
 begin('杖端轮廓与晶体')
 if id=='copper_head' then
  poly({{24,11},{39,11},{46,24},{43,41},{36,49},{27,49},{20,40},{18,24}},colors.edge)
  poly({{25,13},{38,13},{43,25},{40,39},{35,45},{28,45},{23,38},{21,25}},C('9a5836'))
  poly({{29,16},{35,16},{39,27},{36,38},{28,38},{25,27}},C('327782'))
  poly({{30,17},{33,17},{35,28},{31,36},{28,28}},colors.ice)
 else
  local purple=id=='amethyst_head'
  local p={{32,5},{43,18},{40,37},{32,46},{24,37},{21,18}} poly(p,colors.edge)
  poly({{32,7},{40,19},{37,36},{32,42},{26,36},{24,19}},purple and C('8c61b0') or C('9dc5cc'))
  poly({{32,7},{32,40},{26,35},{24,19}},purple and C('c1a0df') or C('e1ece0'))
  poly({{33,12},{39,20},{36,35},{33,39}},purple and C('61427f') or C('638d9d'))
 end
 layer('黄铜夹持与线圈') band(45,18) band(51,14) rect(27,55,10,4,colors.edge) rect(29,55,6,3,colors.brass)
 if id=='copper_head' then for y=20,37,6 do rect(20,y,24,3,colors.shadow) line(21,y,42,y,C('d79755')) line(22,y+1,41,y+1,C('b8713e')) end
 else line(23,31,26,45,colors.brass,2) line(40,31,37,45,colors.brass,2) end
 layer('晶面高光与紧固螺丝') line(30,12,27,20,id=='amethyst_head' and C('ead7fa') or colors.white) pixel(32,7,colors.white) pixel(25,44,colors.light) pixel(38,44,colors.light) pixel(30,52,colors.light)
 if id=='amethyst_head' then line(35,25,35,32,C('cbb0e5')) end finish(id)
end
-- Shafts share scale and end collars; their material grain and light channels differ.
for _,id in ipairs({'oak_body','iron_body','blaze_body'}) do
 begin('杖身与握柄') rect(25,6,14,52,colors.edge) rect(27,7,10,49,id=='oak_body' and C('91633c') or id=='iron_body' and C('72848c') or C('81512c'))
 for y=10,54 do
  if id=='oak_body' then pixel(28+math.floor(math.sin(y*.25)*1.4),y,C('bb9058')) pixel(34+math.floor(math.sin(y*.18)),y,C('5b3e29'))
  elseif id=='iron_body' then pixel(28,y,C('b4c9cc')) pixel(35,y,C('475e67'))
  else pixel(29,y,C('f3b951')) pixel(33,y,C('b86b28')) end
 end
 layer('套箍与握柄缠带') for _,y in ipairs({7,20,43,54}) do band(y,18) end
 for y=26,38,4 do line(26,y,36,y+3,C('3f3430'),2) line(27,y,35,y+2,C('77604c')) end
 layer('铆钉与能量刻槽') if id=='blaze_body' then for y=12,18,3 do rect(30,y,3,2,C('fff0ac')) end rect(30,47,3,4,C('e9a63f')) elseif id=='iron_body' then rect(31,12,2,6,colors.teal) rect(31,47,2,4,colors.ice) else line(29,12,29,17,C('d3ac71')) end pixel(25,21,colors.light) pixel(38,44,colors.light) finish(id)
end
for _,id in ipairs({'wood_tail','iron_tail','diamond_tail'}) do
 begin('杖尾配重与切面')
 poly({{26,14},{37,14},{41,29},{45,40},{40,53},{31,59},{22,53},{18,40},{23,29}},colors.edge)
 local c=id=='wood_tail' and C('8c613e') or id=='iron_tail' and C('78939b') or C('399da8')
 poly({{27,16},{36,16},{38,30},{42,40},{37,51},{31,55},{25,51},{21,40},{26,30}},c)
 if id=='diamond_tail' then poly({{32,24},{40,40},{32,53},{24,40}},C('75d4cd')) poly({{32,24},{32,51},{24,40}},C('c2eee1')) line(25,40,39,40,C('2d7589'))
 elseif id=='iron_tail' then line(25,34,25,45,C('bbd3d2'),2) line(37,34,37,45,C('455f6a'),2) line(28,50,34,50,C('ceddd7'))
 else for y=30,48 do pixel(28+math.floor(math.sin(y*.3)*1.3),y,C('bd925c')) pixel(34,y,C('5a3b2c')) end end
 layer('黄铜套环与包边') band(13,18) band(23,18) if id~='diamond_tail' then band(39,24) end
 layer('切面反光') pixel(28,18,colors.light) pixel(36,24,colors.light) if id=='diamond_tail' then line(30,31,27,38,colors.white) pixel(32,26,colors.white) else line(25,32,23,38,colors.gold) end finish(id)
end
-- UV material tiles remain opaque and seamless at their outer pixels.
local bases={'32291f','805d39','bc945d','c8a769','f1d699','536168','a0b4b9','d8e4dc','8b4e24','eda342','fff0ae','4d3467','a780d3','e3cfff','286b72','72d2c7'}
for i=0,15 do
 begin('材质基色',32,32) local base=C(bases[i+1])
 for y=0,31 do for x=0,31 do local n=((x*7+y*11)%5-2)
  if i<=1 then n=n+math.floor(math.sin(x*.9+math.sin(y*.14))*6)
  elseif i>=11 then n=n+math.floor((x-y)*.18) elseif y%7==0 then n=n-3 end
  pixel(x,y,{math.max(0,math.min(255,base[1]+n)),math.max(0,math.min(255,base[2]+n)),math.max(0,math.min(255,base[3]+n))})
 end end
 layer('局部纹理与细刻线')
 if i<=1 then line(10,5,10,15,C(i==0 and '59402c' or 'a27848')) line(23,19,22,28,C(i==0 and '201d1b' or '5b402a'))
 elseif i<=4 or i==8 then line(5,11,10,11,{math.min(255,base[1]+18),math.min(255,base[2]+15),math.min(255,base[3]+10)}) line(6,12,10,12,{math.max(0,base[1]-19),math.max(0,base[2]-17),math.max(0,base[3]-12)})
 elseif i<=7 then line(9,7,21,7,{math.min(255,base[1]+12),math.min(255,base[2]+12),math.min(255,base[3]+10)})
 elseif i>=11 then line(6,7,15,17,{math.min(255,base[1]+17),math.min(255,base[2]+17),math.min(255,base[3]+17)}) end
 finish('palette_'..i,'item/wand')
end
begin('魔力回复状态图标',18,18)
poly({{9,1},{15,5},{15,12},{9,17},{3,12},{3,5}},colors.edge)
poly({{9,3},{13,6},{13,11},{9,15},{5,11},{5,6}},colors.teal)
poly({{9,4},{9,13},{6,10},{6,7}},colors.ice)
line(3,12,3,6,colors.light) line(3,6,5,8,colors.light) line(3,6,1,8,colors.light)
line(15,6,15,12,colors.light) line(15,12,13,10,colors.light) line(15,12,17,10,colors.light)
finish('mana_recovery','mob_effect')
print('ASEPRITE_PLATES_PARTS_PASS: 3 plates, 9 components, 16 UV materials, effect icon; editable layers saved')
