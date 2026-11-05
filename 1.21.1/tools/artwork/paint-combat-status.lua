local root=app.params["root"]
assert(root,"root required")
local s=Sprite(18,18,ColorMode.RGB)
s.layers[1].name="水滴与金属边缘"
local img=s.cels[1].image
local function p(x,y,c) img:drawPixel(x,y,Color{r=c[1],g=c[2],b=c[3],a=255}) end
local rim={31,57,75} local edge={191,158,98} local blue={65,155,202} local pale={153,230,239}
for y=2,15 do for x=3,14 do
 local dx=math.abs(x-8.5) local w=math.min(5,(y-1)*.65,16-y)
 if dx<=w then p(x,y,dx>w-1 and rim or blue) end
end end
for y=7,12 do p(6,y,pale) end p(7,5,pale) p(7,13,pale)
for _,xy in ipairs({{2,6},{1,7},{2,8},{15,10},{16,11},{15,12}}) do p(xy[1],xy[2],edge) end
s:saveAs(root.."/art/aseprite/drenched.aseprite")
s:saveCopyAs(root.."/src/main/resources/assets/elementalcircuits/textures/mob_effect/drenched.png")
