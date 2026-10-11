package tametsimod;

import game.Game;
import game.GameLogic;
import game.Profile;
import game.State;
import graph.Graph;
import graph.Node;
import puzzle.ColumnHint;
import puzzle.Hint;
import puzzle.Puzzle;
import puzzle.PuzzleIn;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;

/** Exercise transformed original hint rendering and the real mouse/save paths. */
final class HintTests {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void state() {
        State.CURRENT_SCREEN = State.SCREEN_TYPE.EXTRA_LEVEL; State.CURRENT_LEVEL = "hint-test";
        State.LOADING = State.INACTIVE = State.LOST_GAME = State.WON_GAME = false;
        State.DRAWING = State.CURRENTLY_DRAWING = State.SHOW_CANVAS = false;
        State.INACTIVE_FRAMES = 0; State.HOVER_HINT = null; State.HOVER_TILE = null;
        State.HOVER_NEIGHBORS = null; State.SHOW_NEIGHBORS = false;
        State.revealBinding1 = new io.Binding(true, 1, io.Binding.BoundAction.REVEAL);
        State.revealBinding2 = null;
        State.flagBinding1 = new io.Binding(true, 3, io.Binding.BoundAction.FLAG);
        State.flagBinding2 = null; State.showCanvasBinding1 = State.showCanvasBinding2 = null;
        State.showNeighborsBinding1 = State.showNeighborsBinding2 = null;
        RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS, Feature.COLORS));
        NeighborToggle.resetForTests();
    }
    private static void bind(Game game, Puzzle puzzle) {
        game.puzzle = puzzle; game.profile = new Profile(); game.profile.id = 2;
        puzzle.id = "hint-test"; Game.mh.setGraph(puzzle.G);
    }
    private static void click(Game game, int x, int y, int button) {
        game.dispatchEvent(new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, button));
    }
    private static BufferedImage drawHints(Puzzle puzzle, boolean columns) throws Exception {
        BufferedImage image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Method method = draw.Drawer.class.getDeclaredMethod(columns ? "drawColumnHints" : "drawHintMineCount",
                    columns ? new Class<?>[] {Graphics2D.class, Puzzle.class}
                            : new Class<?>[] {int.class, int.class, Graphics2D.class, Puzzle.class});
            method.setAccessible(true);
            if (columns) method.invoke(null, g, puzzle);
            else { ColorChord.begin(puzzle); method.invoke(null, Game.w, Game.h, g, puzzle); }
        } finally { g.dispose(); }
        return image;
    }
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
    private static int[] counter(Puzzle puzzle, Hint hint) {
        for (int y = 0; y < Math.min(Game.h, 800); y++)
            for (int x = Math.max(0, Game.w - 180); x < Game.w; x++)
                if (ColorChord.hit(puzzle, x, y) == hint) return new int[] {x, y};
        return null;
    }
    private static BufferedImage render(Puzzle puzzle) {
        draw.Drawer.clearCache();
        BufferedImage image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        draw.Drawer.draw(Game.w, Game.h, image.createGraphics(), puzzle);
        return image;
    }
    static int run(Game game) throws Exception {
        checks = 0; int w = Game.w, h = Game.h;
        int font = State.MINE_COUNT_FONT_SIZE;
        boolean grey = State.OPTION_GREY_IS_A_COLOR, hide = State.HIDE_GREY_COUNT;
        boolean blind = State.COLORBLIND, countdown = State.OPTION_COLUMN_COUNTDOWN;
        try {
            circles(game);
            for (String file : new String[] {"2018_01_29_A.puz", "2018_01_30_A.puz"})
                colors(game, file);
            guards(game);
        } finally {
            Game.w = w; Game.h = h; State.MINE_COUNT_FONT_SIZE = font;
            State.OPTION_GREY_IS_A_COLOR = grey; State.HIDE_GREY_COUNT = hide;
            State.COLORBLIND = blind; State.OPTION_COLUMN_COUNTDOWN = countdown;
            state(); RuntimeOptions.setForTests(EnumSet.allOf(Feature.class));
        }
        return checks;
    }
    private static void circles(Game game) throws Exception {
        for (String file : new String[] {"2018_01_15_A.puz", "2018_01_29_A.puz", "2018_01_30_C.puz"}) {
            state(); Puzzle puzzle = PuzzleIn.readXMLPuzzle(file); bind(game, puzzle);
            ColumnHint hint = null;
            for (ColumnHint candidate : puzzle.columnHints)
                if (candidate.numMines > 0 && candidate.ids.size() > candidate.numMines
                        && (hint == null || Math.abs(candidate.textRotation) > Math.abs(hint.textRotation))) hint = candidate;
            check(hint != null, "real column clue exists: " + file);
            for (Node node : puzzle.G.V.values()) { node.flagged = false; node.revealed = false; }
            for (int id : hint.ids) { Node node = puzzle.G.get(id); node.flagged = node.hasMine; }
            check(ColumnChord.ready(hint, puzzle) && !ColumnChord.targets(hint, puzzle).isEmpty(), "circle ready before safe tiles are revealed");
            for (boolean countdown : new boolean[] {false, true}) {
                State.OPTION_COLUMN_COUNTDOWN = countdown;
                RuntimeOptions.setForTests(EnumSet.noneOf(Feature.class)); BufferedImage off = drawHints(puzzle, true);
                RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS)); BufferedImage on = drawHints(puzzle, true);
                check(!Arrays.equals(pixels(on), pixels(off)), "transformed original draw adds circles with countdown=" + countdown);
            }
            ImageIO.write(render(puzzle), "png", Paths.get("hint-circle-" + file + ".png").toFile());
            Node flagged = null; Node safe = null;
            for (int id : hint.ids) { Node n = puzzle.G.get(id); if (n.flagged) flagged = n; else safe = n; }
            flagged.flagged = false; check(!ColumnChord.ready(hint, puzzle), "missing flag removes readiness");
            flagged.flagged = true; safe.flagged = true; check(!ColumnChord.ready(hint, puzzle), "extra flag removes readiness");
            Puzzle isolated = new Puzzle(); isolated.G = puzzle.G; isolated.columnHints.add(hint);
            RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS)); BufferedImage unmatched = drawHints(isolated, true);
            RuntimeOptions.setForTests(EnumSet.noneOf(Feature.class)); BufferedImage original = drawHints(isolated, true);
            check(Arrays.equals(pixels(unmatched), pixels(original)), "actual overflagged column renders no circle");
            RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS));
            safe.flagged = false; hint.hidden = true;
            check(!ColumnChord.ready(hint, puzzle), "hidden column does not disclose its count"); hint.hidden = false;
            // Count equality is the condition, not whether the flags match hidden mines.
            flagged.flagged = false; safe.flagged = true;
            check(ColumnChord.ready(hint, puzzle), "wrong but equal flag count still indicates a chord");
            hint.ids.add(Integer.MAX_VALUE); check(!ColumnChord.ready(hint, puzzle), "invalid hint member cannot enable circle");
        }
        state(); Puzzle zero = fixture(game);
        ColumnHint hint = new ColumnHint(new HashSet<Integer>(Arrays.asList(1)), zero.G); hint.numMines = 0;
        check(ColumnChord.ready(hint, zero), "zero-total column is ready");
    }
    private static void colors(Game game, String file) throws Exception {
        state(); Puzzle puzzle = PuzzleIn.readXMLPuzzle(file); GameLogic.setMineNeighbors(puzzle.G); bind(game, puzzle);
        Hint hint = null;
        for (Hint candidate : puzzle.hints)
            if (candidate.numMines > 0 && candidate.ids.size() > candidate.numMines) { hint = candidate; break; }
        check(hint != null, "real color count exists: " + file);
        for (Node n : puzzle.G.V.values()) { n.flagged = false; n.revealed = false; }
        for (int id : hint.ids) { Node n = puzzle.G.get(id); n.flagged = n.hasMine; }
        puzzle.G.numFlags = hint.numMines;
        List<Node> targets = ColorChord.targets(hint, puzzle);
        check(!targets.isEmpty(), "real color targets use the original hint members");
        HashSet<Integer> members = new HashSet<Integer>(hint.ids);
        for (int variation = 0; variation < 4; variation++) {
            Game.w = 1100 + variation * 100; Game.h = 900;
            State.MINE_COUNT_FONT_SIZE = 24 + variation * 4;
            State.COLORBLIND = (variation & 1) != 0; puzzle.cornerFlag = (variation & 2) != 0;
            State.OPTION_GREY_IS_A_COLOR = (variation & 1) != 0; State.HIDE_GREY_COUNT = (variation & 2) != 0;
            drawHints(puzzle, false);
            check(counter(puzzle, hint) != null, "actual counter hit works with font/width/corner/grey/colorblind variation " + variation);
        }
        ImageIO.write(render(puzzle), "png", Paths.get("color-zero-" + file + ".png").toFile());
        int[] position = counter(puzzle, hint);
        check(position != null, "full Drawer path registers visible zero counter");
        Node marked = puzzle.G.get(hint.ids.stream().filter(id -> puzzle.G.get(id).flagged).findFirst().get());
        marked.flagged = false;
        check(ColorChord.hit(puzzle, position[0], position[1]) == null && ColorChord.targets(hint, puzzle).isEmpty(), "click rechecks count after stale zero text");
        marked.flagged = true;
        RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS));
        check(ColorChord.hit(puzzle, position[0], position[1]) == null, "color feature independently disabled");
        RuntimeOptions.setForTests(EnumSet.of(Feature.COLORS));
        check(ColorChord.hit(puzzle, position[0], position[1]) == hint, "color feature independent of columns");
        click(game, position[0], position[1], 1);
        check(targets.stream().allMatch(n -> n.revealed), "real mouse expands remaining color members");
        check(marked.flagged && !marked.revealed && !State.LOST_GAME, "flags retained and safe expansion uses original engine");
        check(hint.ids.equals(members), "color membership never changes");
        if (!State.WON_GAME) check(game.profile.progress.get(puzzle.id).get(1).containsAll(ids(targets)), "color reveals persisted through original profile");
        Puzzle next = new Puzzle(); next.G = puzzle.G; next.hints.add(hint);
        check(ColorChord.hit(next, position[0], position[1]) == null, "old counter cannot apply to a new puzzle");
        ColorChord.begin(puzzle);
        check(counter(puzzle, hint) == null, "next render clears stale counter positions");
    }
    private static HashSet<Integer> ids(List<Node> nodes) {
        HashSet<Integer> result = new HashSet<Integer>(); for (Node node : nodes) result.add(node.id); return result;
    }
    private static final class OverlayGraph extends Graph {
        Node hit;
        @Override public Node pointInNode(int x, int y) { return hit; }
    }
    private static Puzzle fixture(Game game) {
        state(); Puzzle puzzle = new Puzzle(); puzzle.G = new OverlayGraph();
        Node mine = puzzle.G.addNode(0); mine.hasMine = true; mine.flagged = true;
        puzzle.G.addNode(1); puzzle.G.addNode(2); puzzle.G.addNode(20);
        puzzle.G.addEdge(0, 1); puzzle.G.addEdge(0, 2);
        puzzle.G.numMines = 1; puzzle.G.numFlags = 1; GameLogic.setMineNeighbors(puzzle.G);
        puzzle.addHint(new int[] {0, 1, 2}, Hint.TileColor.RED, false); bind(game, puzzle);
        return puzzle;
    }
    private static void guards(Game game) throws Exception {
        for (int guard = 0; guard < 10; guard++) {
            Puzzle puzzle = fixture(game); Hint hint = puzzle.hints.get(0); drawHints(puzzle, false);
            int[] pos = counter(puzzle, hint); check(pos != null, "fixture zero counter visible");
            switch (guard) {
                case 0: State.CURRENT_SCREEN = State.SCREEN_TYPE.LEVEL_SELECT; break;
                case 1: State.CURRENT_SCREEN = State.SCREEN_TYPE.PROFILE_SELECT; break;
                case 2: State.LOADING = true; break;
                case 3: State.INACTIVE = true; break;
                case 4: State.INACTIVE_FRAMES = 2; break;
                case 5: State.WON_GAME = true; break;
                case 6: State.LOST_GAME = true; break;
                case 7: State.DRAWING = true; break;
                case 8: State.SHOW_CANVAS = true; break;
                case 9: State.CURRENTLY_DRAWING = true; break;
            }
            click(game, pos[0], pos[1], 1);
            check(!puzzle.G.get(1).revealed && !puzzle.G.get(2).revealed, "color click suppressed in guarded state " + guard);
        }
        Puzzle puzzle = fixture(game); Hint hint = puzzle.hints.get(0); drawHints(puzzle, false); int[] pos = counter(puzzle, hint);
        Node flagged = puzzle.G.get(0);
        flagged.flagged = false; drawHints(puzzle, false);
        check(counter(puzzle, hint) == null, "positive counter never registers as clickable");
        flagged.flagged = true; puzzle.G.get(2).flagged = true; drawHints(puzzle, false);
        check(counter(puzzle, hint) == null, "negative counter never registers as clickable");
        puzzle.G.get(2).flagged = false; drawHints(puzzle, false); pos = counter(puzzle, hint);
        Game.w += 10;
        check(ColorChord.hit(puzzle, pos[0], pos[1]) == null, "resize invalidates the previous frame's click coordinates");
        Game.w -= 10;
        puzzle.G.get(2).flagged = true;
        check(ColorChord.hit(puzzle, pos[0], pos[1]) == null, "overflagged color rechecked at click time");
        puzzle.G.get(2).flagged = false;
        click(game, pos[0], pos[1], 3); check(!puzzle.G.get(1).revealed, "flag mouse button does not expand counter");
        State.flagBinding1 = new io.Binding(true, 1, io.Binding.BoundAction.FLAG);
        State.revealBinding1 = new io.Binding(true, 3, io.Binding.BoundAction.REVEAL);
        click(game, pos[0], pos[1], 3); check(puzzle.G.get(1).revealed && puzzle.G.get(2).revealed, "color click follows swapped reveal binding");
        puzzle = fixture(game); hint = puzzle.hints.get(0);
        puzzle.G.get(0).flagged = false; puzzle.G.get(2).flagged = true;
        drawHints(puzzle, false); pos = counter(puzzle, hint);
        click(game, pos[0], pos[1], 1);
        check(State.LOST_GAME && puzzle.G.get(0).revealed && !puzzle.G.get(1).revealed, "wrong color flag hits mine and stops batch");
        check(game.profile.hasMistake(puzzle.id), "color mistake uses original loss recording");
        game.doUndo(); check(!State.LOST_GAME && !puzzle.G.get(0).revealed, "original undo of mine works after color loss");
        puzzle = fixture(game); hint = puzzle.hints.get(0); hint.ids.add(Integer.MAX_VALUE);
        check(ColorChord.targets(hint, puzzle).isEmpty(), "invalid color members cannot produce partial targets");
        puzzle = fixture(game); Hint zero = new Hint(new HashSet<Integer>(Arrays.asList(20)), puzzle.G, 1, false);
        puzzle.hints.add(zero); drawHints(puzzle, false); pos = counter(puzzle, zero);
        check(pos != null, "zero-mine color can be clicked"); click(game, pos[0], pos[1], 1);
        check(puzzle.G.get(20).revealed && !puzzle.G.get(1).revealed, "zero color reveals only its disconnected original members");
        puzzle = fixture(game); hint = puzzle.hints.get(0);
        puzzle.G.addEdge(2, 20); // Original flood fill may also reveal a connected safe region of another color.
        puzzle.G.get(2).edges.remove(0); puzzle.G.get(0).edges.remove(2); GameLogic.setMineNeighbors(puzzle.G);
        drawHints(puzzle, false); pos = counter(puzzle, hint); click(game, pos[0], pos[1], 1);
        check(puzzle.G.get(20).revealed, "color chord preserves original zero-region flood fill");
        check(State.WON_GAME && game.profile.extraSolved.contains(puzzle.id), "color completion uses original win recording");
        puzzle = fixture(game); hint = puzzle.hints.get(0);
        ((OverlayGraph)puzzle.G).hit = puzzle.G.get(20);
        drawHints(puzzle, false); pos = counter(puzzle, hint); click(game, pos[0], pos[1], 1);
        check(puzzle.G.get(1).revealed && puzzle.G.get(2).revealed && !puzzle.G.get(20).revealed,
                "visible color counter wins over a polygon underneath it");
    }
}
