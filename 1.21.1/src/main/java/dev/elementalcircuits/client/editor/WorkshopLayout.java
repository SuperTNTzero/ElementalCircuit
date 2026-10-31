package dev.elementalcircuits.client.editor;

/** Shared layout for preview and part widgets. Resizing changes no selection or inventory. */
public record WorkshopLayout(int left,int span,int previewWidth,int partsX,int partsWidth,int top,int bottom) {
    public static WorkshopLayout of(int width,int height){
        int left=Math.max(8,(width-530)/2),span=width-2*left,preview=Math.min(156,Math.max(76,span/3));
        return new WorkshopLayout(left,span,preview,left+preview+8,span-preview-8,102,height-60);
    }
    public int wandSize(){return Math.max(24,Math.min(previewWidth-22,bottom-top-40));}
}
