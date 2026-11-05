package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;

/** Pixel-distance hit test; crossings select the nearest line, exact ties the last drawn line. */
public final class RouteHitTest {
    private RouteHitTest() {}

    public static Route at(
            Blueprint board, EditorLayout l, Route.Payload payload, double x, double y) {
        if (!l.onBoard(x, y)) return null;
        for (Rune r : board.runes())
            if (Math.hypot(x - l.x(r.cell()), y - l.y(r.cell())) < l.size() * .65) return null;
        Route found = null;
        double best = Math.pow(Math.min(4, l.size() * .32), 2);
        for (Route route : board.routes())
            if (route.payload() == payload) {
                var path = route.path();
                for (int i = 1; i < path.size(); i++) {
                    double ax = l.x(path.get(i - 1)),
                            ay = l.y(path.get(i - 1)),
                            dx = l.x(path.get(i)) - ax,
                            dy = l.y(path.get(i)) - ay;
                    double t =
                            Math.max(
                                    0,
                                    Math.min(
                                            1,
                                            ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
                    double distance = Math.pow(x - ax - t * dx, 2) + Math.pow(y - ay - t * dy, 2);
                    if (distance <= best) {
                        best = distance;
                        found = route;
                    }
                }
            }
        return found;
    }
}
