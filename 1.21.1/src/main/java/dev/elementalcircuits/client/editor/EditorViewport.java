package dev.elementalcircuits.client.editor;

/** Local camera only. Pixel input and rendering share exactly the same transformed layout. */
public final class EditorViewport {
    private double zoom = 1, panX, panY;

    public EditorLayout apply(EditorLayout base) {
        return new EditorLayout(
                base.width(),
                base.height(),
                base.left(),
                base.right(),
                base.top(),
                base.bottom(),
                base.size() * zoom,
                base.cx() + panX,
                base.cy() + panY,
                base.radius());
    }

    public void zoom(EditorLayout base, double x, double y, double scroll) {
        double next = Math.clamp(zoom * Math.pow(1.2, Math.clamp(scroll, -4, 4)), .25, 64),
                factor = next / zoom;
        panX = x + (base.cx() + panX - x) * factor - base.cx();
        panY = y + (base.cy() + panY - y) * factor - base.cy();
        zoom = next;
        bound(base);
    }

    public void pan(EditorLayout base, double dx, double dy) {
        panX += dx;
        panY += dy;
        bound(base);
    }

    public void reset() {
        zoom = 1;
        panX = panY = 0;
    }

    private void bound(EditorLayout base) {
        double extent = (base.radius() + 2) * base.size() * zoom * 2;
        panX = Math.clamp(panX, -extent, extent);
        panY = Math.clamp(panY, -extent, extent);
    }
}
