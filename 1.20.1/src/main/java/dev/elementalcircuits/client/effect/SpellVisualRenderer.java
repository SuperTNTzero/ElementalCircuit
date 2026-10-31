package dev.elementalcircuits.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.elementalcircuits.application.observation.SpellVisual;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** All visible spell quads share one texture, one shader and one draw. Never traces the world. */
public final class SpellVisualRenderer {
    private static final ResourceLocation TEXTURE=new ResourceLocation("elementalcircuits","textures/effect/spells.png");
    private static final int QUAD_LIMIT=8192;
    private static int lastQuads,lastDraws;
    private SpellVisualRenderer(){}
    public static int lastQuads(){return lastQuads;}
    public static int lastDraws(){return lastDraws;}
    private static Vec3 point(SpellVisual.Point p){return new Vec3(p.x(),p.y(),p.z());}
    public static void render(PoseStack poses,Vec3 camera,Quaternionf rotation,List<VisualTimeline.Frame> frames,float partial){
        lastQuads=lastDraws=0;if(frames.isEmpty())return;
        Vec3 right=vector(new Vector3f(1,0,0).rotate(rotation)),up=vector(new Vector3f(0,1,0).rotate(rotation));
        var out=Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);RenderSystem.setShaderTexture(0,TEXTURE);RenderSystem.setShaderColor(1,1,1,1);
        out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
        try{
            var matrix=poses.last().pose();var observer=new SpellVisual.Point(camera.x,camera.y,camera.z);
            for(var frame:frames){
                if(lastQuads>=QUAD_LIMIT)break;var e=frame.event();if(!e.near(observer,80))continue;
                var style=VisualStyles.of(e.kind());float progress=Math.min(1,(frame.age()+Math.max(0,Math.min(1,partial)))/frame.life());
                int alpha=Math.round(style.opacity()*(1-progress)*255),color=e.kind()==SpellVisual.Kind.STEAM?0xcbd4dc:e.element().color;
                Vec3 a=point(e.from()).subtract(camera),b=point(e.to()).subtract(camera);
                if(e.kind()==SpellVisual.Kind.BEAM || e.kind()==SpellVisual.Kind.TRAIL){
                    Vec3 axis=b.subtract(a);Vec3 side=axis.cross(a.add(b).scale(.5));if(side.lengthSqr()<1e-12)side=axis.cross(up);if(side.lengthSqr()<1e-12)side=axis.cross(right);if(side.lengthSqr()<1e-12)side=right;
                    side=side.normalize().scale(style.width()*e.size());quad(out,matrix,a.subtract(side),b.subtract(side),b.add(side),a.add(side),style.sprite(),color,alpha);
                }else if(e.kind()==SpellVisual.Kind.SHIELD){
                    Vec3 normal=point(e.to()),r=normal.cross(Math.abs(normal.y)>.99?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(e.size()),u=r.normalize().cross(normal).scale(e.size());
                    quad(out,matrix,a.subtract(r).subtract(u),a.add(r).subtract(u),a.add(r).add(u),a.subtract(r).add(u),style.sprite(),color,alpha);
                }else{
                    int count=Math.min(e.intensity(),style.copies());
                    for(int i=0;i<count && lastQuads<QUAD_LIMIT;i++){
                        // Stable cosmetic offsets. No gameplay RNG, object lookup or collision query.
                        double angle=i*2.399963229728653;double spread=(.08+progress*.65)*Math.sqrt(i+1)*e.size();
                        Vec3 center=a.add(right.scale(Math.cos(angle)*spread)).add(up.scale(Math.sin(angle)*spread+(e.kind()==SpellVisual.Kind.STEAM?progress*.5:0)));
                        double size=style.width()*(e.kind()==SpellVisual.Kind.STEAM?1+progress:1-progress*.4);Vec3 r=right.scale(size),u=up.scale(size);
                        quad(out,matrix,center.subtract(r).subtract(u),center.add(r).subtract(u),center.add(r).add(u),center.subtract(r).add(u),style.sprite(),color,alpha);
                    }
                }
            }
            BufferUploader.drawWithShader(out.end());if(lastQuads>0)lastDraws=1;
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static Vec3 vector(Vector3f v){return new Vec3(v.x,v.y,v.z);}
    private static void quad(BufferBuilder out,Matrix4f m,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int sprite,int color,int alpha){
        float u=sprite*.25f,v=u+.25f;vertex(out,m,a,u,1,color,alpha);vertex(out,m,b,v,1,color,alpha);vertex(out,m,c,v,0,color,alpha);vertex(out,m,d,u,0,color,alpha);lastQuads++;
    }
    private static void vertex(BufferBuilder out,Matrix4f m,Vec3 p,float u,float v,int color,int alpha){out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color>>16&255,color>>8&255,color&255,alpha).uv(u,v).endVertex();}
}
