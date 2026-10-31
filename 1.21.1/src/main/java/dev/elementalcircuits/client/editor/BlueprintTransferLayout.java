package dev.elementalcircuits.client.editor;

/** Layout is independent of widgets and painting. */
public record BlueprintTransferLayout(int width,int height,int left,int top,int boxWidth,int boxHeight,int actions) {
    public static BlueprintTransferLayout of(int width,int height){
        int w=Math.min(680,width-24),left=(width-w)/2;
        return new BlueprintTransferLayout(width,height,left,90,w,Math.max(36,height-172),height-34);
    }
}
