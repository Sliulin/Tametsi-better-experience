package tametsimod;

import game.State;
import graph.Node;
import puzzle.ColumnHint;
import puzzle.Puzzle;
import java.util.Collections;
import java.util.List;

/** Reuse the original column member IDs and hint hit radius, including rotated/bent rows. */
final class ColumnChord {
    private ColumnChord() {}
    static ColumnHint hit(Puzzle puzzle, int x, int y) {
        if (puzzle == null || puzzle.G == null || puzzle.G.pointInNode(x, y) != null) return null;
        ColumnHint best = null; double distance = Double.MAX_VALUE;
        double radius = State.TOGGLE_HINT_DISTANCE + State.TILE_FONT_SIZE / 2;
        for (ColumnHint hint : puzzle.columnHints) {
            double current = util.Util.d2(x, y, hint.textLocation.x, hint.textLocation.y);
            if (current <= radius * radius && current < distance) { best = hint; distance = current; }
        }
        return best;
    }
    static List<Node> targets(ColumnHint hint, Puzzle puzzle) {
        if (hint == null || hint.hidden || hint.numMines < 0) return Collections.emptyList();
        return Chord.targetsByIds(puzzle, hint.ids, hint.numMines);
    }
    static boolean ready(ColumnHint hint, Puzzle puzzle) {
        if (hint == null || hint.hidden || hint.numMines < 0 || puzzle == null || puzzle.G == null)
            return false;
        int flags = 0;
        for (Integer id : hint.ids) {
            if (id == null) return false;
            Node node = puzzle.G.get(id);
            if (node == null) return false;
            if (node.flagged) flags++;
        }
        return flags == hint.numMines;
    }
}
