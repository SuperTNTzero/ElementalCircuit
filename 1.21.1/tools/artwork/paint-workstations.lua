-- Original pixel texture authored in Aseprite. Run from the 1.21.1 project directory.
local root=app.params.root or app.fs.currentPath
if app.params.fixed=='true' then dofile(root..'/tools/artwork/paint-fixed-circuits.lua') return end
local sprite=Sprite(256,256,ColorMode.RGB)
sprite.layers[1].name='黄铜、云杉与刻印材料'
local image=sprite.cels[1].image
local rgba=app.pixelColor.rgba
local palette={{88,65,42},{160,119,56},{47,136,165},{112,126,128},{113,81,44},{185,143,70},{87,170,183},{77,46,32},{27,36,39},{221,205,157},{108,85,147},{196,173,112},{70,54,37},{132,99,49},{41,88,104},{62,75,79}}
math.randomseed(20301031)
for i,c in ipairs(palette) do
 local ox=((i-1)%4)*64+16 local oy=math.floor((i-1)/4)*64+16
 for y=0,31 do for x=0,31 do
  local n=math.random(-5,5)
  if i==1 or i==5 or i==13 then n=n+math.floor(6*math.sin(y*.52+x*.08)) if y%8==0 then n=-20 end
  elseif i==3 or i==7 or i==11 or i==15 then n=n+math.floor((x-y)*.8) if x<7 and y<24 then n=n+24 end
  elseif i==2 or i==6 or i==14 then n=n+math.floor(y/4)%2*5 if x==1 or y==1 then n=30 end if x==30 or y==30 then n=-24 end end
  image:drawPixel(ox+x,oy+y,rgba(math.max(0,math.min(255,c[1]+n)),math.max(0,math.min(255,c[2]+n)),math.max(0,math.min(255,c[3]+n)),255))
 end end
end
local function line(x1,y1,x2,y2,color)
 local steps=math.max(math.abs(x2-x1),math.abs(y2-y1))
 for j=0,steps do local t=steps==0 and 0 or j/steps image:drawPixel(math.floor(x1+(x2-x1)*t+.5),math.floor(y1+(y2-y1)*t+.5),color) end
end
for tile=4,6 do
 local ox=(tile%4)*64+16 local oy=math.floor(tile/4)*64+16 local gold=rgba(231,205,137,255)
 for a=0,5 do
  local t=a*math.pi/3 local u=(a+1)*math.pi/3
  line(ox+16+12*math.cos(t),oy+16+12*math.sin(t),ox+16+12*math.cos(u),oy+16+12*math.sin(u),gold)
  line(ox+16+4*math.cos(t),oy+16+4*math.sin(t),ox+16+9*math.cos(t),oy+16+9*math.sin(t),gold)
 end
 line(ox+11,oy+16,ox+16,oy+11,gold) line(ox+16,oy+11,ox+21,oy+16,gold) line(ox+21,oy+16,ox+16,oy+21,gold) line(ox+16,oy+21,ox+11,oy+16,gold)
end
-- Visible spruce fibres, following the same long-axis UVs on slab and legs.
for base=2,30,4 do
 for x=1,30 do
  local y=base+math.floor(math.sin(x*.21+base*.73)*.9)
  image:drawPixel(16+x,16+y,rgba(73,53,33,255))
  if y+1<31 then image:drawPixel(16+x,17+y,rgba(98,74,48,255)) end
 end
end
-- End grain is separate from the long fibres used on plank and leg sides.
for y=0,31 do for x=0,31 do
 local r=math.sqrt((x-15.5)^2+(y-15.5)^2*.82)
 local n=math.floor(r*1.7)%4==0 and -18 or 5
 image:drawPixel(16+x,208+y,rgba(94+n,70+n,43+n,255))
end end
-- Extrude each 32px painting into a 16px gutter on every side. Level-4 mipmaps
-- can sample the tile edge without introducing another material's colour.
for tile=0,15 do
 local ox=(tile%4)*64 local oy=math.floor(tile/4)*64
 for y=0,63 do for x=0,63 do
  if x<16 or x>47 or y<16 or y>47 then
   image:drawPixel(ox+x,oy+y,image:getPixel(ox+math.max(16,math.min(47,x)),oy+math.max(16,math.min(47,y))))
  end
 end end
end
local stem='workstations'
sprite:saveAs(root..'/art/aseprite/'..stem..'.aseprite')
sprite:saveCopyAs(root..'/src/main/resources/assets/elementalcircuits/textures/block/'..stem..'.png')
print('ASEPRITE_WORKSTATIONS_PASS: original 256x256 atlas with padded 32px paintings and editable source saved')
