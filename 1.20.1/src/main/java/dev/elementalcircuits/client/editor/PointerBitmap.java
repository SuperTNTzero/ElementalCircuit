package dev.elementalcircuits.client.editor;

/** Deterministic bitmap for OS cursors; shape, scale and hotspot are independent of GUI frame rate. */
public final class PointerBitmap {
    private final int size,hotX,hotY;
    private final int[] pixels;
    public PointerBitmap(EditorPointer pointer,int scale){
        if(scale<1 || scale>3)throw new IllegalArgumentException("指针缩放超限");
        size=24*scale;pixels=new int[size*size];
        hotX=(pointer.kind()==EditorPointer.Kind.SELECT?5:pointer.kind()==EditorPointer.Kind.ROUTE?4:12)*scale;
        hotY=(pointer.kind()==EditorPointer.Kind.SELECT?3:pointer.kind()==EditorPointer.Kind.ROUTE?20:12)*scale;
        // Black outline remains legible against both bright terrain and the dark editor.
        for(var s:pointer.strokes())line(s,scale,0xff081018,scale);
        for(var s:pointer.strokes())line(s,scale,pointer.color(),Math.max(0,(scale-1)/2));
        if(pointer.kind()!=EditorPointer.Kind.SELECT){dot(hotX,hotY,scale+1,0xff081018);dot(hotX,hotY,Math.max(0,scale/2),pointer.color());}
    }
    public int size(){return size;}
    public int hotX(){return hotX;}
    public int hotY(){return hotY;}
    public int[] pixels(){return pixels.clone();}
    private void line(RuneShapes.Stroke stroke,int scale,int color,int thickness){
        double x1=(12+stroke.x1()*10)*scale,y1=(12+stroke.y1()*10)*scale,x2=(12+stroke.x2()*10)*scale,y2=(12+stroke.y2()*10)*scale;
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.abs(x2-x1),Math.abs(y2-y1))));
        for(int i=0;i<=steps;i++)dot((int)Math.round(x1+(x2-x1)*i/steps),(int)Math.round(y1+(y2-y1)*i/steps),thickness,color);
    }
    private void dot(int x,int y,int radius,int color){for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)if(x+dx>=0 && x+dx<size && y+dy>=0 && y+dy<size)pixels[(y+dy)*size+x+dx]=color;}
}
