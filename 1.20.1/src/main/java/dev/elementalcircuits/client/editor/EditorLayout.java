package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.Hex;

/** GUI pixel geometry only, including hit testing; no rendering or rule changes on resize. */
public record EditorLayout(int width,int height,int left,int right,int top,int bottom,double size,double cx,double cy,int radius){
    private static final int[][] NEIGHBORS={{0,0},{1,0},{0,1},{-1,1},{-1,0},{0,-1},{1,-1}};
    public static EditorLayout of(int width,int height,int radius){
        int left=width<440?92:128,right=width<440?100:148,top=60,bottom=height-73;
        double size=Math.max(2,Math.min((width-left-right-20)/(Math.sqrt(3)*(2*radius+1)),(bottom-top-22)/(3.0*radius+2)));
        return new EditorLayout(width,height,left,right,top,bottom,size,left+(width-left-right)/2.0,(top+bottom)/2.0,radius);
    }
    public double x(Hex h){return cx+size*Math.sqrt(3)*(h.q()+h.r()/2.0);}
    public double y(Hex h){return cy+size*1.5*h.r();}
    public boolean onBoard(double x,double y){return x>=left+2 && x<width-right-2 && y>=top && y<bottom;}
    public int visibleRows(){return Math.max(1,(bottom-top-8)/24);}
    public Hex cell(double x,double y){
        if(!onBoard(x,y))return null;Hex closest=null;double best=size*size;
        double r=(y-cy)/(size*1.5),q=(x-cx)/(Math.sqrt(3)*size)-r/2,s=-q-r;
        int rq=(int)Math.round(q),rr=(int)Math.round(r),rs=(int)Math.round(s);
        double dq=Math.abs(rq-q),dr=Math.abs(rr-r),ds=Math.abs(rs-s);
        if(dq>dr && dq>ds)rq=-rr-rs;else if(dr>ds)rr=-rq-rs;
        // Inspect the rounded cell and six neighbors to retain the previous edge footprint
        // and deterministic q/r tie order without scanning all 217 cells per mouse event.
        for(int[] n:NEIGHBORS){Hex h=new Hex(rq+n[0],rr+n[1]);if(!h.inside(radius))continue;
            double dx=x-x(h),dy=y-y(h),distance=dx*dx+dy*dy;
            if(distance<best || closest!=null && distance==best && (h.q()<closest.q() || h.q()==closest.q() && h.r()<closest.r())){best=distance;closest=h;}}
        return closest;
    }
}
