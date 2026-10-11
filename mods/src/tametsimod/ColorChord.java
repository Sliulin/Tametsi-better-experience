package tametsimod;

import graph.Graph;
import graph.Node;
import puzzle.Hint;
import puzzle.Puzzle;
import game.State;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Capture the original visible counter bounds; never infer colors from RGB or geometry. */
public final class ColorChord {
    private static Puzzle paintedPuzzle;
    private static Graph paintedGraph;
    private static int paintedWidth, paintedHeight;
    private static final List<Counter> counters = new ArrayList<Counter>();
    private ColorChord() {}

    public static synchronized void begin(Puzzle puzzle) {
        begin(puzzle, game.Game.w, game.Game.h);
    }

    public static synchronized void begin(Puzzle puzzle, int width, int height) {
        paintedPuzzle = puzzle;
        paintedGraph = puzzle == null ? null : puzzle.G;
        paintedWidth = width; paintedHeight = height;
        counters.clear();
    }

    public static void drawText(Graphics2D graphics, String text, float x, float y,
            Puzzle puzzle, Hint hint) {
        graphics.drawString(text, x, y);
        if (!RuntimeOptions.enabled(Feature.COLORS) || !State.onLevel() || State.LOADING
                || State.DRAWING || State.CURRENTLY_DRAWING || State.SHOW_CANVAS
                || hint.numMines < 0 || hint.numFlagged(puzzle.G) != hint.numMines) return;
        Rectangle2D bounds = graphics.getFontMetrics().getStringBounds(text, graphics);
        // A small margin makes the visible number easy to click without spanning adjacent rows.
        Area area = new Area(new Rectangle2D.Double(x + bounds.getX() - 3,
                y + bounds.getY() - 3, bounds.getWidth() + 6, bounds.getHeight() + 6));
        Shape clip = graphics.getClip();
        if (clip != null) area.intersect(new Area(clip));
        Shape screenArea = graphics.getTransform().createTransformedShape(area);
        synchronized (ColorChord.class) {
            if (paintedPuzzle == puzzle && paintedGraph == puzzle.G)
                counters.add(new Counter(hint, screenArea));
        }
    }

    static synchronized Hint hit(Puzzle puzzle, int x, int y) {
        if (!RuntimeOptions.enabled(Feature.COLORS) || paintedPuzzle != puzzle
                || puzzle == null || paintedGraph != puzzle.G
                || paintedWidth != game.Game.w || paintedHeight != game.Game.h) return null;
        for (Counter counter : counters) {
            Hint hint = counter.hint;
            if (puzzle.hints.contains(hint) && counter.area.contains(x, y)
                    && hint.numMines >= 0 && hint.numFlagged(puzzle.G) == hint.numMines)
                return hint;
        }
        return null;
    }

    static List<Node> targets(Hint hint, Puzzle puzzle) {
        if (hint == null || puzzle == null || !puzzle.hints.contains(hint))
            return Collections.emptyList();
        return Chord.targetsByIds(puzzle, hint.ids, hint.numMines);
    }

    private static final class Counter {
        final Hint hint;
        final Shape area;
        Counter(Hint hint, Shape area) { this.hint = hint; this.area = area; }
    }
}
