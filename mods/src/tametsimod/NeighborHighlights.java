package tametsimod;

import graph.Node;
import java.util.HashSet;

/** A drawing-only snapshot. Never changes the graph or the underlying neighbor set. */
public final class NeighborHighlights {
    private NeighborHighlights() {}

    public static HashSet<Node> neighbors(Node clue) {
        if (!RuntimeOptions.enabled(Feature.HIGHLIGHTS)) return clue.getNeighbors();
        HashSet<Node> visible = new HashSet<Node>();
        for (Node neighbor : clue.getNeighbors()) {
            if (!neighbor.revealed) visible.add(neighbor);
        }
        return visible;
    }
}
