package dev.elementalcircuits.client.editor;
import dev.elementalcircuits.domain.Hex;

/** Shared read-only page geometry; has no GUI or Minecraft dependency. */
public record BookLayout(int width,int height,int left,int span,int list,int previewX,int previewY,int previewWidth,int previewHeight) {
    public static BookLayout of(int width,int height){int left=Math.max(6,(width-640)/2),span=width-2*left,list=Math.max(90,Math.min(156,span/3));return new BookLayout(width,height,left,span,list,left+list+8,36,span-list-8,Math.max(45,height-132));}
    public double size(int radius){return Math.max(.1,Math.min((previewWidth-12)/(Math.sqrt(3)*(2*radius+1)),(previewHeight-12)/(3.0*radius+2)));}
    public double x(Hex h,int radius){return previewX+previewWidth/2.0+Math.sqrt(3)*size(radius)*(h.q()+h.r()/2.0);}
    public double y(Hex h,int radius){return previewY+previewHeight/2.0+1.5*size(radius)*h.r();}
}
