-- Hand-drawn pixel layers for the three fixed-array materials. 128px paintings,
-- separated by 64px edge extrusion; all output stays opaque and mip-safe.
local root=app.params.root or app.fs.currentPath
local sprite=Sprite(1024,1024,ColorMode.RGB)
sprite.layers[1].name='01 木纹、拉丝与晶面'
local image=sprite.cels[1].image
local rgba=app.pixelColor.rgba
local ox,oy=0,0
local function tile(i) ox=(i%4)*256+64 oy=math.floor(i/4)*256+64 end
local function color(r,g,b) return rgba(math.max(0,math.min(255,math.floor(r))),math.max(0,math.min(255,math.floor(g))),math.max(0,math.min(255,math.floor(b))),255) end
local function put(x,y,c) x=math.floor(x+.5) y=math.floor(y+.5) if x>=0 and x<128 and y>=0 and y<128 then image:drawPixel(ox+x,oy+y,c) end end
local function line(x1,y1,x2,y2,c,width)
 local steps=math.ceil(math.max(math.abs(x2-x1),math.abs(y2-y1))) local r=math.floor((width or 1)/2)
 for j=0,steps do local t=steps==0 and 0 or j/steps for dy=-r,r do for dx=-r,r do put(x1+(x2-x1)*t+dx,y1+(y2-y1)*t+dy,c) end end end
end
local function polar(r,a) return 63.5+r*math.cos(a),63.5+r*math.sin(a) end
local function radial(r1,r2,a,c,w) local x,y=polar(r1,a) local u,v=polar(r2,a) line(x,y,u,v,c,w) end
local function hex(r,c,w)
 for i=0,5 do local x,y=polar(r,-math.pi/2+i*math.pi/3) local u,v=polar(r,-math.pi/2+(i+1)*math.pi/3) line(x,y,u,v,c,w) end
end
local function arc(r,a,b,c,w)
 local n=math.ceil((b-a)*r*1.5)
 for i=0,n do local x,y=polar(r,a+(b-a)*i/n) put(x,y,c) if w==2 then put(x,y+1,c) end end
end
local function polygon(points,c)
 local ymin,ymax=128,0 for _,p in ipairs(points) do ymin=math.min(ymin,p[2]) ymax=math.max(ymax,p[2]) end
 for y=math.max(0,math.ceil(ymin)),math.min(127,math.floor(ymax)) do
  local xs={} for i,p in ipairs(points) do local q=points[i%#points+1] if (p[2]<=y and q[2]>y) or (q[2]<=y and p[2]>y) then xs[#xs+1]=p[1]+(y-p[2])*(q[1]-p[1])/(q[2]-p[2]) end end
  table.sort(xs) for j=1,#xs-1,2 do for x=math.ceil(xs[j]),math.floor(xs[j+1]) do put(x,y,c) end end
 end
end
math.randomseed(211031)
local palette={{87,58,33},{156,109,49},{30,101,122},{100,119,123},{99,66,36},{159,113,51},{31,103,125},{64,39,28},{25,34,37},{217,197,149},{100,78,139},{187,155,91},{68,48,29},{116,79,36},{29,71,88},{52,66,72}}
for i=0,15 do
 tile(i) local base=palette[i+1]
 for y=0,127 do for x=0,127 do
  local n=math.random(-2,2)
  if i==0 or i==4 or i==12 then
   local grain=math.sin(y*.65+math.sin(x*.045)*2.3)+.6*math.sin(y*1.9+x*.025)
   n=n+grain*3.5+math.sin(x*.017+y*.021)*3
   if math.floor(y+math.sin(x*.05)*2)%23==0 then n=n-9 end
   if i==12 then n=n+math.sin(math.sqrt((x-64)^2+(y-64)^2*.8)*1.1)*5 end
  elseif i==1 or i==5 or i==13 then
   n=n+math.sin(y*1.71)*2.7+math.sin((x+y)*.018)*5+(127-y)*.035
   if y%29==0 then n=n-4 end
  elseif i==2 or i==6 or i==14 then n=n+(.55*x-.4*y)*.10+math.sin((x+y)*.045)*3 end
  put(x,y,color(base[1]+n,base[2]+n,base[3]+n))
 end end
end
-- Large, quiet facets under the crystalline inlay; no noisy crack web.
tile(6)
polygon({{0,0},{68,0},{48,47},{0,83}},color(39,116,136))
polygon({{68,0},{127,0},{127,63},{80,79},{48,47}},color(34,106,127))
polygon({{0,83},{48,47},{80,79},{58,127},{0,127}},color(25,89,111))
polygon({{80,79},{127,63},{127,127},{58,127}},color(38,111,132))
line(68,0,48,47,color(63,140,154)) line(48,47,0,83,color(40,115,136)) line(48,47,80,79,color(47,126,144)) line(80,79,58,127,color(25,84,105))
-- Sparse wood knots and slim plank seams, below the engraving.
tile(4)
for y=18,33 do for x=25,53 do local d=math.sqrt(((x-39)/1.7)^2+(y-25)^2) if math.floor(d*1.5)%5==0 then put(x,y,color(76,50,28)) end end end
for y=0,127 do if y%4~=0 then put(40,y,color(86,56,30)) put(41,y,color(111,76,41)) end end
local function newLayer(name)
 local layer=sprite:newLayer() layer.name=name
 local cel=sprite:newCel(layer,1,Image(1024,1024,ColorMode.RGB),Point(0,0)) image=cel.image
end
newLayer('02 沉入盘面的双层刻槽')
for material=4,6 do
 tile(material)
 local shadow=material==4 and color(46,31,22) or material==5 and color(64,49,28) or color(12,52,68)
 local shoulder=material==4 and color(132,91,47) or material==5 and color(198,153,79) or color(60,138,155)
 for _,r in ipairs({55,51,36,13}) do hex(r+1,shoulder,1) hex(r,shadow,3) end
 for i=0,5 do
  local a=math.pi/6+i*math.pi/3
  radial(16,29,a,shadow,3)
  local x,y=polar(29,a) local u,v=polar(33,a+.11) line(x,y,u,v,shadow,3)
  local p,q=polar(36,a+.11) line(u,v,p,q,shadow,3)
 end
end
newLayer('03 黄铜镶线、刻度与六路符印')
-- Each path is a small, deliberate angular rune, repeated with six rotations.
local runes={{{-3,-5},{-3,5},{3,1},{-3,-2},{3,-5}},{{-3,-5},{3,-5},{0,0},{3,5},{-3,5}},{{-3,4},{0,-5},{3,4},{-3,0},{3,0}},{{-3,-4},{3,4},{3,-4},{-3,4}},{{-3,-5},{3,-1},{-3,3},{3,5}},{{-3,-5},{3,-5},{-1,1},{3,5},{-3,5},{-1,1}}}
for material=4,6 do
 tile(material)
 local metal=material==4 and color(192,144,72) or material==5 and color(224,181,99) or color(159,205,199)
 local bright=material==4 and color(221,179,99) or material==5 and color(245,213,143) or color(206,239,228)
 local flow=material==4 and color(195,151,81) or material==5 and color(79,184,191) or color(118,211,222)
 hex(55,metal) hex(51,metal) hex(36,metal) hex(13,metal)
 -- 36 calibration ticks; major marks remain visible at normal viewing distance.
 for i=0,35 do
  local a=math.pi/6+i*math.pi/18
  radial(i%3==0 and 46 or 48,49,a,i%3==0 and bright or metal)
 end
 for i=0,5 do
  local a=math.pi/6+i*math.pi/3
  radial(16,29,a,flow)
  local x,y=polar(29,a) local u,v=polar(33,a+.11) local p,q=polar(36,a+.11)
  line(x,y,u,v,flow) line(u,v,p,q,flow)
  arc(27,a+.22,a+.82,metal)
  -- Tangential glyph, sitting clear of both the scale and the circuit ring.
  local cx,cy=polar(42,a) local path=runes[i+1]
  local function rp(p) return cx+p[1]*math.cos(a+math.pi/2)-p[2]*math.sin(a+math.pi/2),cy+p[1]*math.sin(a+math.pi/2)+p[2]*math.cos(a+math.pi/2) end
  for j=2,#path do local x1,y1=rp(path[j-1]) local x2,y2=rp(path[j]) line(x1,y1,x2,y2,bright) end
  -- Contact pads deliberately wider than the fine engraved paths.
  local px,py=polar(19,a) polygon({{px-2,py},{px,py-2},{px+2,py},{px,py+2}},flow)
 end
 -- Central seal remains legible around the actual model's centre fitting.
 for i=0,5 do local a=-math.pi/2+i*math.pi/3 radial(8,11,a,bright) end
 -- North arrow uses the same UV direction as the physical nozzle.
 line(60,116,63.5,121,bright) line(63.5,121,67,116,bright) line(63.5,112,63.5,120,bright)
end
newLayer('04 边缘磨痕与导流触点')
for material=4,6 do
 tile(material)
 local dark=material==4 and color(68,45,25) or material==5 and color(82,67,39) or color(28,83,102)
 for i=0,11 do
  local a=i*math.pi/6+.12 local x,y=polar(57,a) line(x,y,x+2,y+1,dark)
 end
 -- Six tiny cool glints, with an enclosed dark seat rather than a painted glow.
 for i=0,5 do
  local a=-math.pi/2+i*math.pi/3 local x,y=polar(53,a)
  local seat=color(39,62,62) polygon({{x-3,y},{x,y-3},{x+3,y},{x,y+3}},seat)
  polygon({{x-1.5,y},{x,y-1.5},{x+1.5,y},{x,y+1.5}},material==4 and color(204,171,101) or color(114,221,229))
  put(x,y-1,color(222,242,224))
 end
end
-- Extrude each layer independently. Far mip levels see its own material edge.
for _,layer in ipairs(sprite.layers) do
 image=layer:cel(1).image
 for i=0,15 do
  local tx=(i%4)*256 local ty=math.floor(i/4)*256
  for y=0,255 do for x=0,255 do
   if x<64 or x>191 or y<64 or y>191 then image:drawPixel(tx+x,ty+y,image:getPixel(tx+math.max(64,math.min(191,x)),ty+math.max(64,math.min(191,y)))) end
  end end
 end
end
sprite:saveAs(root..'/art/aseprite/fixed-circuits.aseprite')
sprite:saveCopyAs(root..'/src/main/resources/assets/elementalcircuits/textures/block/fixed-circuits.png')
print('ASEPRITE_FIXED_DETAIL_PASS: 1024 atlas, 128px paintings, four editable layers, 64px gutters')
