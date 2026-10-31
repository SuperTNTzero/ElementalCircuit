package dev.elementalcircuits.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** GPU adapter for the pure cosmetic geometry planner. One atlas, one bounded draw. */
public final class SpellVisualRenderer {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("elementalcircuits","textures/effect/spells.png");
    private static int lastQuads,lastDraws;
    private SpellVisualRenderer(){}
    public static int lastQuads(){return lastQuads;}
    public static int lastDraws(){return lastDraws;}
    public static void render(PoseStack poses,Vec3 camera,Quaternionf rotation,List<VisualTimeline.Frame> frames,float partial){
        lastQuads=lastDraws=0;if(frames.isEmpty())return;
        var out=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,TEXTURE);RenderSystem.setShaderColor(1,1,1,1);
        try{
            var matrix=poses.last().pose();
            lastQuads=SpellGeometry.emit(frames,new SpellGeometry.V(camera.x,camera.y,camera.z),vector(new Vector3f(1,0,0).rotate(rotation)),vector(new Vector3f(0,1,0).rotate(rotation)),partial,
                (a,b,c,d,sprite,color,alpha)->{
                    float u=(sprite%4)/4f,v=(sprite/4)/4f,inset=.5f/512;
                    vertex(out,matrix,a,u+inset,v+.25f-inset,color,alpha);vertex(out,matrix,b,u+.25f-inset,v+.25f-inset,color,alpha);
                    vertex(out,matrix,c,u+.25f-inset,v+inset,color,alpha);vertex(out,matrix,d,u+inset,v+inset,color,alpha);
                });
            var mesh=out.build();if(mesh!=null){BufferUploader.drawWithShader(mesh);lastDraws=1;}
        }finally{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.setShaderColor(1,1,1,1);}
    }
    private static SpellGeometry.V vector(Vector3f v){return new SpellGeometry.V(v.x,v.y,v.z);}
    private static void vertex(BufferBuilder out,Matrix4f m,SpellGeometry.V p,float u,float v,int color,int alpha){out.addVertex(m,(float)p.x(),(float)p.y(),(float)p.z()).setColor(color>>16&255,color>>8&255,color&255,alpha).setUv(u,v);}
}
