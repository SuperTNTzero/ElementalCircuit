-- Original padded leather / brass / manuscript atlas, authored in Aseprite.
local root=app.params.root or app.fs.currentPath
local s=Sprite(512,512,ColorMode.RGB)
s.layers[1].name='皮革、纸页与黄铜'
local image=s.cels[1].image local rgba=app.pixelColor.rgba
local colors={{30,57,66},{91,57,36},{29,58,70},{77,49,32},{191,178,139},{229,213,168},{169,126,63},{59,170,190},{46,40,32},{226,212,174},{228,190,106},{35,113,125},{215,209,170},{39,65,73},{114,163,174},{127,51,46}}
math.randomseed(20261101)
for tile,c in ipairs(colors) do
 local ox=((tile-1)%4)*128+32 local oy=math.floor((tile-1)/4)*128+32
 for y=0,63 do for x=0,63 do
  local noise=math.random(-5,5)
  if tile==5 then noise=noise+(y%4==0 and -24 or 0)
  elseif tile==7 or tile==11 then noise=noise+math.floor((63-x-y)*.14)
  elseif tile==1 or tile==2 or tile==3 or tile==4 or tile==9 then noise=noise+((x*7+y*11)%19==0 and -10 or 0) end
  image:drawPixel(ox+x,oy+y,rgba(math.max(0,c[1]+noise),math.max(0,c[2]+noise),math.max(0,c[3]+noise),255))
 end end
end
local function pixel(tile,x,y,c) image:drawPixel((tile%4)*128+32+x,math.floor(tile/4)*128+32+y,c) end
local function line(tile,x,y,xx,yy,c)
 local steps=math.max(math.abs(xx-x),math.abs(yy-y))
 for i=0,steps do local t=steps==0 and 0 or i/steps pixel(tile,math.floor(x+(xx-x)*t+.5),math.floor(y+(yy-y)*t+.5),c) end
end
local gold=rgba(210,172,93,255) local dark=rgba(71,58,36,255) local blue=rgba(93,195,216,255) local ink=rgba(79,101,102,255)
for tile=2,3 do
 for _,margin in ipairs({4,7}) do
  line(tile,margin,margin,63-margin,margin,gold) line(tile,margin,margin,margin,63-margin,gold)
  line(tile,63-margin,margin,63-margin,63-margin,gold) line(tile,margin,63-margin,63-margin,63-margin,gold)
 end
 for y=11,52,5 do for x=11,52,5 do
  if tile==3 then pixel(tile,x,y,rgba(112,118,96,255)) end
 end end
 for a=0,5 do
  local t=a*math.pi/3 local u=(a+1)*math.pi/3
  line(tile,32+18*math.cos(t),32+18*math.sin(t),32+18*math.cos(u),32+18*math.sin(u),gold)
  line(tile,32+8*math.cos(t),32+8*math.sin(t),32+13*math.cos(t),32+13*math.sin(t),blue)
 end
 for x=14,49 do if x%5<3 then pixel(tile,x,54,gold) end end
end
for tile=5,12,7 do
 for y=10,54,5 do
  for x=10,53 do if (x+math.floor(y/5)*3)%11<7 then pixel(tile,x,y,ink) end end
 end
 for a=0,5 do
  local t=a*math.pi/3 local u=(a+1)*math.pi/3
  line(tile,31+13*math.cos(t),30+13*math.sin(t),31+13*math.cos(u),30+13*math.sin(u),ink)
 end
 line(tile,31,17,31,43,ink) line(tile,18,30,44,30,ink)
end
-- Extend every tile by 32px: four mip levels never sample a neighbouring material.
for tile=0,15 do
 local ox=(tile%4)*128 local oy=math.floor(tile/4)*128
 for y=0,127 do for x=0,127 do
  if x<32 or x>95 or y<32 or y>95 then image:drawPixel(ox+x,oy+y,image:getPixel(ox+math.max(32,math.min(95,x)),oy+math.max(32,math.min(95,y)))) end
 end end
end
s:saveAs(root..'/art/aseprite/books.aseprite')
s:saveCopyAs(root..'/src/main/resources/assets/elementalcircuits/textures/item/books.png')
print('ASEPRITE_BOOKS_PASS: 512px atlas, 64px paintings, padded leather / brass / handwritten pages')
