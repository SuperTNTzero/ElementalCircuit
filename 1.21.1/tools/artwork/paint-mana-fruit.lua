-- Original 32px crystal-infused apple, layered editable Aseprite source.
local root=app.params.root or app.fs.currentPath
local s=Sprite(32,32,ColorMode.RGB)
s.layers[1].name='晶蓝果肉'
local img=s.cels[1].image
local rgba=app.pixelColor.rgba
local function p(x,y,c) img:drawPixel(x,y,rgba(c[1],c[2],c[3],255)) end
local edge={22,47,61} local gold={190,140,66} local bright={243,207,117}
for y=8,28 do for x=4,27 do
 local dx=(x-15.5)/11.5 local dy=(y-17)/10.5
 local notch=y<11 and math.abs(x-15.5)<2
 if dx*dx+dy*dy<1 and not notch then
  local t=(x-4+y-8)/43
  local rim=dx*dx+dy*dy>.76
  p(x,y,rim and edge or {math.floor(72-39*t),math.floor(183-88*t),math.floor(211-65*t)})
 end
end end
for y=12,18 do for x=7,10 do if (x-8)^2+(y-14)^2<9 then p(x,y,{153,235,239}) end end end
for y=4,10 do p(15,y,{87,61,36}) p(16,y,{138,102,51}) end
for y=3,8 do for x=17,24 do
 if math.abs((x-20)*.5+(y-5))<2.3 then p(x,y,{65,135,102}) end
end end
s:newLayer().name='黄铜星纹与晶簇'
img=Image(32,32,ColorMode.RGB);s:newCel(s.layers[#s.layers],s.frames[1],img)
img=s.cels[#s.cels].image
for y=16,25 do
 local w=math.max(0,3-math.abs(y-20))
 for x=16-w,16+w do p(x,y,(x==16 or y==20) and bright or gold) end
end
for y=8,15 do
 local w=math.min(y-8,15-y,2)
 for x=24-w,24+w do p(x,y,x<24 and {178,244,247} or {54,151,188}) end
end
for _,v in ipairs({{11,24},{22,18},{8,20},{21,12}}) do p(v[1],v[2],bright) end
app.fs.makeDirectory(root..'/art/aseprite')
s:saveAs(root..'/art/aseprite/mana-fruit.aseprite')
s:saveCopyAs(root..'/src/main/resources/assets/elementalcircuits/textures/item/mana_fruit.png')
print('ASEPRITE_MANA_FRUIT_PASS')
