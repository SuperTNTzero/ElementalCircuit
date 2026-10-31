package dev.elementalcircuits.client.book;

/** Client-only, bounded animation clock; no game state, renderer, wall clock or random source. */
public final class BookMotion {
    public record Pose(float open,float turn,int direction){}
    private float previousOpen,open,previousTurn=-1,turn=-1;
    private int direction=1,pending;
    public void reset(){previousOpen=open=0;previousTurn=turn=-1;pending=0;direction=1;}
    public void flip(int sign){if(sign==0)return;if(turn>=0){pending=sign<0?-1:1;return;}direction=sign<0?-1:1;previousTurn=turn=0;}
    public void tick(boolean opened){
        previousOpen=open;open=Math.clamp(open+(opened?.1f:-.15f),0,1);
        previousTurn=turn;
        if(turn>=0){turn=Math.min(1,turn+1f/12);if(previousTurn>=1){previousTurn=turn=-1;if(pending!=0){int next=pending;pending=0;flip(next);}}}
    }
    public Pose sample(float partial){
        if(!Float.isFinite(partial))throw new IllegalArgumentException("非法动画插值");
        float p=Math.clamp(partial,0,1),value=previousOpen+(open-previousOpen)*p;
        // Smoothstep preserves fixed-tick continuity while easing cover motion.
        return new Pose(value*value*(3-2*value),turn<0?-1:previousTurn+(turn-previousTurn)*p,direction);
    }
}
