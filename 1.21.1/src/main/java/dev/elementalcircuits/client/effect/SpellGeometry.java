package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.Element;
import java.util.List;

/** Pure cosmetic geometry. No world, entity, RNG, collision or graphics API dependency. */
public final class SpellGeometry {
    public static final int QUAD_LIMIT=8192;
    public record V(double x,double y,double z){
        public V add(V v){return new V(x+v.x,y+v.y,z+v.z);}
        public V sub(V v){return new V(x-v.x,y-v.y,z-v.z);}
        public V mul(double n){return new V(x*n,y*n,z*n);}
        public double length(){return Math.sqrt(x*x+y*y+z*z);}
        public V unit(){double n=length();return n<1e-9?new V(0,1,0):mul(1/n);}
        public V cross(V v){return new V(y*v.z-z*v.y,z*v.x-x*v.z,x*v.y-y*v.x);}
        public static V of(SpellVisual.Point p){return new V(p.x(),p.y(),p.z());}
    }
    @FunctionalInterface public interface Sink{void quad(V a,V b,V c,V d,int sprite,int color,int alpha);}
    private final V camera,right,up;
    private final Sink sink;
    private int count;
    private SpellGeometry(V camera,V right,V up,Sink sink){this.camera=camera;this.right=right;this.up=up;this.sink=sink;}
    public static int emit(List<VisualTimeline.Frame> frames,V camera,V right,V up,float partial,Sink sink){
        var mesh=new SpellGeometry(camera,right,up,sink);var observer=new SpellVisual.Point(camera.x,camera.y,camera.z);
        // Contacts and cast silhouettes remain visible when trails flood the cosmetic budget.
        for(int pass=0;pass<2;pass++)for(var frame:frames){
            if(mesh.count>=QUAD_LIMIT)return mesh.count;var e=frame.event();boolean important=e.kind()==SpellVisual.Kind.IMPACT || e.kind()==SpellVisual.Kind.CAST || e.kind()==SpellVisual.Kind.START;
            if(important!=(pass==0) || !e.near(observer,80))continue;
            float p=Math.min(1,Math.max(0,(frame.age()+Math.max(0,Math.min(1,partial)))/frame.life()));
            mesh.event(e,p,frame.age()+partial);
        }
        return mesh.count;
    }
    private void quad(V a,V b,V c,V d,int sprite,int color,double opacity){
        if(count>=QUAD_LIMIT || opacity<=.002)return;
        sink.quad(a,b,c,d,sprite,color,(int)Math.round(Math.min(1,opacity)*255));count++;
    }
    private void plane(V at,V r,V u,int sprite,int color,double alpha){quad(at.sub(r).sub(u),at.add(r).sub(u),at.add(r).add(u),at.sub(r).add(u),sprite,color,alpha);}
    private void billboard(V at,double size,int sprite,int color,double alpha,double angle){
        V r=right.mul(Math.cos(angle)*size).add(up.mul(Math.sin(angle)*size)),u=up.mul(Math.cos(angle)*size).sub(right.mul(Math.sin(angle)*size));plane(at,r,u,sprite,color,alpha);
    }
    private void ribbon(V a,V b,double width,int color,double alpha){
        V axis=b.sub(a);if(axis.length()<1e-6)return;V side=axis.cross(a.add(b).mul(.5));if(side.length()<1e-6)side=axis.cross(up);if(side.length()<1e-6)side=right;
        side=side.unit().mul(width);quad(a.sub(side),b.sub(side),b.add(side),a.add(side),2,color,alpha);
    }
    private void ring(V at,V r,V u,double size,double angle,int sprite,int color,double alpha){
        V x=r.mul(Math.cos(angle)*size).add(u.mul(Math.sin(angle)*size)),y=u.mul(Math.cos(angle)*size).sub(r.mul(Math.sin(angle)*size));plane(at,x,y,sprite,color,alpha);
    }
    private void event(SpellVisual e,float p,float age){
        var s=VisualStyles.of(e.kind());var art=VisualStyles.of(e.element());
        V a=V.of(e.from()).sub(camera),b=V.of(e.to()).sub(camera);double alpha=s.opacity()*Math.pow(1-p,1.3),size=e.size();
        switch(e.kind()){
            case BEAM,TRAIL -> flight(e,a,b,s,art,p,alpha);
            case IMPACT -> impact(e,a,s,art,p,alpha);
            case START,CAST -> {
                V normal=V.of(e.to()),r=normal.cross(Math.abs(normal.y)>.98?new V(1,0,0):new V(0,1,0)).unit(),u=r.cross(normal).unit();
                double radius=s.width()*size*(e.kind()==SpellVisual.Kind.START?.65+.5*p:1+.6*p);
                if(a.length()<1.6){radius*=.55;alpha*=.65;}
                ring(a,r,u,radius,age*.12,7,art.color(),alpha);
                ring(a,r,u,radius*.73,-age*.2,3,art.core(),alpha*.8);
                billboard(a,radius*.5,0,art.color(),alpha*.8,0);
                if(p<.35)billboard(a,radius*.5*(1-p/.35),4,art.core(),alpha,age*.05);
                for(int i=0;i<s.copies();i++){double angle=i*Math.PI*2/s.copies()+age*.1;V at=a.add(r.mul(Math.cos(angle)*radius*.9)).add(u.mul(Math.sin(angle)*radius*.9));billboard(at,.055*size,12,art.core(),alpha,angle);}
            }
            case SHIELD -> {
                V normal=V.of(e.to()),r=normal.cross(Math.abs(normal.y)>.99?new V(1,0,0):new V(0,1,0)).unit(),u=r.cross(normal).unit();
                ring(a,r,u,size,0,7,art.color(),alpha*.75);ring(a,r,u,size*1.04,.15,3,art.core(),alpha*.7);
                // A faint interior gives the barrier an actual surface; the bright pattern stays legible.
                ring(a,r,u,size,0,0,art.color(),alpha*.12);
                for(int i=0;i<6;i++){double t=i*Math.PI/3;V center=a.add(r.mul(Math.cos(t)*size*.48)).add(u.mul(Math.sin(t)*size*.48));ring(center,r,u,size*.22,t,7,art.core(),alpha*.45);}
            }
            case FAN,WALL,RING,COLUMN,CROSS,DOME -> {
                V n=V.of(e.to()),r=n.cross(Math.abs(n.y)>.99?new V(1,0,0):new V(0,1,0)).unit(),u=r.cross(n).unit();
                java.util.function.Function<V,V> world=v->a.add(r.mul(v.x())).add(u.mul(v.y())).add(n.mul(v.z()));
                var volume=new dev.elementalcircuits.domain.ShapeVolume(dev.elementalcircuits.domain.SpellSpec.Shape.valueOf(e.kind().name()),Math.round(e.size()),dev.elementalcircuits.adapter.ShapeResources.rules());
                final double opacity=alpha;
                VolumeGeometry.emit(volume,(x,y)->{V start=world.apply(x),end=world.apply(y);ribbon(start,end,s.width()*2,art.color(),opacity*.35);ribbon(start,end,s.width()*.48,art.core(),opacity);},
                    (x,y,z,w)->quad(world.apply(x),world.apply(y),world.apply(z),world.apply(w),0,art.color(),opacity*.12));
                var bounds=volume.bounds();int motes=0;
                for(int i=0;i<96 && motes<s.copies();i++){
                    double fx=(i*.61803398875+.13)%1,fy=(i*.41421356237+.37)%1,fz=(i*.73205080757+.53)%1;
                    V local=new V(bounds.minX()+fx*(bounds.maxX()-bounds.minX()),bounds.minY()+fy*(bounds.maxY()-bounds.minY()),bounds.minZ()+fz*(bounds.maxZ()-bounds.minZ()));
                    if(!volume.contains(local.x(),local.y(),local.z()))continue;
                    billboard(world.apply(local),.07*size*(1+.2*Math.sin(age*.5+i)),art.sprite(),art.color(),opacity*.8,age*.05+i);motes++;
                }
            }
            case STEAM -> {
                for(int i=0;i<s.copies();i++){double angle=i*2.39996;V at=a.add(right.mul(Math.cos(angle)*(.15+p*.5))).add(up.mul(.1+i*.06+p*.8));billboard(at,s.width()*(1+p)*(.8+i*.07),1,0xcbdde5,alpha*.5,angle*.1);}
            }
        }
    }
    private void flight(SpellVisual e,V a,V b,VisualStyles.Style s,VisualStyles.ElementStyle art,float p,double alpha){
        double width=s.width()*e.size(),length=b.sub(a).length();
        ribbon(a,b,width*2.2,art.color(),alpha*.25);
        if(e.element()==Element.LIGHTNING){
            int segments=Math.max(3,Math.min(20,(int)(length*2)));V axis=b.sub(a),last=a;
            for(int i=1;i<=segments;i++){double t=i/(double)segments,j=i==segments?0:Math.sin(i*19.7+Math.floor(p*4)*.7)*width*1.8;V next=a.add(axis.mul(t)).add(right.mul(j)).add(up.mul(j*.6));ribbon(last,next,width*.45,art.color(),alpha);ribbon(last,next,width*.13,art.core(),alpha);last=next;}
        }else{
            ribbon(a,b,width*.70,art.color(),alpha);ribbon(a,b,width*.21,art.core(),alpha);
        }
        int motes=Math.max(2,Math.min(s.copies(),(int)(length*1.4)+2));
        for(int i=0;i<motes;i++){
            double t=(i+.4)/motes,angle=i*2.39996+p*art.twist()*3;V at=a.add(b.sub(a).mul(t)).add(right.mul(Math.cos(angle)*width*1.8)).add(up.mul(Math.sin(angle)*width*1.8));
            billboard(at,width*(e.element()==Element.EARTH?1.4:.75),art.sprite(),i%3==0?art.core():art.color(),alpha*.8,angle);
        }
        if(e.kind()==SpellVisual.Kind.TRAIL){billboard(b,width*2.5,0,art.color(),alpha*.8,0);billboard(b,width*.95,art.sprite(),art.core(),alpha,0);}
        if(e.element()==Element.ARCANE || e.element()==Element.WIND || e.element()==Element.WATER)billboard(b,width*2.8,e.element()==Element.WATER?13:11,art.color(),alpha*.8,p*3);
    }
    private void impact(SpellVisual e,V a,VisualStyles.Style s,VisualStyles.ElementStyle art,float p,double alpha){
        double size=Math.min(2,e.size())*(.8+Math.sqrt(e.intensity())*.06),expansion=1-Math.pow(1-p,3),radius=size*art.spread()*(.15+expansion*1.6);
        billboard(a,radius,3,art.color(),alpha*.85,p*.2);
        billboard(a,radius*.63,e.element()==Element.ARCANE?7:e.element()==Element.WIND?11:3,art.core(),alpha*.55,-p*1.5);
        if(p<.4){double burst=1-p/.4;billboard(a,size*(.5+expansion)*burst,0,art.color(),burst*.8,0);billboard(a,size*.52*burst,4,art.core(),burst,0);}
        if(e.element()==Element.SHADOW)billboard(a,size*(.7-p*.5),0,0x160c25,alpha*.95,0);
        int n=Math.min(s.copies(),art.particles());
        for(int i=0;i<n;i++){
            double angle=i*2.399963+e.from().x()*.37+e.from().z()*.17,spread=(.35+(i%5)*.13)*radius;
            if(e.element()==Element.SHADOW)spread=(1-p)*size*1.4;
            double turn=angle+p*art.twist(),dx=Math.cos(turn)*spread,dy=Math.sin(turn)*spread;
            V at=a.add(right.mul(dx)).add(up.mul(dy)).add(new V(0,art.rise()*p*p*size,0));
            double particleSize=s.width()*size*(.6+(i%3)*.2)*(1-p*.65);
            billboard(at,particleSize,art.sprite(),i%3==0?art.core():art.color(),alpha,turn);
            if(i%2==0){V tail=at.sub(right.mul(Math.cos(turn)*.24*size)).sub(up.mul(Math.sin(turn)*.24*size));ribbon(tail,at,particleSize*.17,art.core(),alpha*.85);}
        }
        if(e.element()==Element.LIGHTNING){for(int i=0;i<4;i++){double angle=i*Math.PI/2+p*.6;V end=a.add(right.mul(Math.cos(angle)*radius)).add(up.mul(Math.sin(angle)*radius));V kink=a.add(end.sub(a).mul(.5)).add(up.mul(size*.13));ribbon(a,kink,.025*size,art.core(),alpha);ribbon(kink,end,.016*size,art.core(),alpha);}}
        if(e.element()==Element.EARTH)billboard(a,size*(.7+expansion),14,art.color(),alpha*.65,0);
        if(e.element()==Element.FIRE)billboard(a.add(new V(0,p*.6,0)),size*(.4+p*.4),1,0x593528,alpha*.35,0);
    }
}
