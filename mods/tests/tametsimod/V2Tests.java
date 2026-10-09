package tametsimod;

import game.Game;
import game.GameLogic;
import game.Profile;
import game.State;
import graph.Graph;
import graph.Node;
import io.Binding;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import puzzle.Puzzle;
import puzzle.PuzzleIn;

final class V2Tests {
    private static int count;
    private static void check(boolean value, String description) {
        count++;
        if (!value) throw new AssertionError(description);
    }

    private static final class HitGraph extends Graph {
        Node hit;
        @Override public Node pointInNode(int x, int y) { return hit; }
    }

    private static HitGraph fixture(Game game, int flags, boolean fifthRevealed) {
        State.CURRENT_SCREEN = State.SCREEN_TYPE.EXTRA_LEVEL;
        State.LOST_GAME = false; State.WON_GAME = false;
        State.DRAWING = false; State.CURRENTLY_DRAWING = false;
        State.CURRENT_LEVEL = "flag-test";
        State.revealBinding1 = new Binding(true, 1, Binding.BoundAction.REVEAL);
        State.flagBinding1 = new Binding(true, 3, Binding.BoundAction.FLAG);
        HitGraph graph = new HitGraph();
        Node clue = graph.addNode(0); clue.revealed = true;
        for (int id = 1; id <= 5; id++) {
            Node neighbor = graph.addNode(id); graph.addEdge(0, id);
            neighbor.hasMine = id <= 4;
            neighbor.flagged = id <= flags;
            neighbor.revealed = id == 5 && fifthRevealed;
        }
        graph.addNode(20); // Keep the fixture in progress until the explicit win test.
        graph.numMines = 4; graph.numFlags = flags; graph.hit = clue;
        GameLogic.setMineNeighbors(graph);
        game.puzzle = new Puzzle(); game.puzzle.G = graph; game.puzzle.id = "flag-test";
        game.profile = new Profile(); game.profile.id = 1;
        Game.mh.setGraph(graph);
        for (int id = 1; id <= flags; id++) game.handleFlag(id);
        return graph;
    }

    private static void click(Game game, int button) {
        game.dispatchEvent(new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 320, 300, 1, false, button));
    }

    static int run(Game game) throws Exception {
        count = 0;
        HitGraph graph = fixture(game, 0, false);
        click(game, 1);
        check(graph.numFlags == 0 && !graph.get(1).flagged, "4 of 5 covered cells is insufficient to flag");
        graph.get(5).revealed = true;
        click(game, 1);
        check(graph.numFlags == 4, "4 of 4 covered cells are automatically flagged");
        for (int id = 1; id <= 4; id++) check(graph.get(id).flagged && !graph.get(id).revealed, "flagged neighbor stays closed " + id);
        check(graph.get(5).revealed && !graph.get(5).flagged && !graph.get(20).flagged,
                "revealed and distant cells are preserved");
        check(game.profile.progress.get("flag-test").get(0).containsAll(java.util.Arrays.asList(1, 2, 3, 4)),
                "all automatic flags use the original progress API");
        click(game, 1); check(graph.numFlags == 4, "repeated flag chord does not duplicate flags");
        game.handleClose();
        Profile loaded = new Profile(); check(loaded.loadProfile(1), "automatic flag save reloads");
        check(loaded.progress.get("flag-test").get(0).containsAll(java.util.Arrays.asList(1, 2, 3, 4)),
                "all four flags survive save/load");

        graph = fixture(game, 1, true); click(game, 1);
        check(graph.numFlags == 4 && graph.get(1).flagged && graph.get(4).flagged,
                "existing flag is deducted: 4 minus 1 flags three cells");
        graph = fixture(game, 0, true); graph.hit.secret = true; click(game, 1);
        check(graph.numFlags == 0, "secret number never triggers automatic flags");
        graph.hit.secret = false; graph.hit.revealed = false;
        check(Chord.flagTargets(graph.hit).isEmpty(), "unopened clue cannot trigger deduction");
        graph.hit.revealed = true; graph.hit.numMineNeighbors = 3;
        check(Chord.flagTargets(graph.hit).isEmpty(), "four unknowns with clue three does nothing");
        graph.hit.numMineNeighbors = 0;
        check(Chord.flagTargets(graph.hit).isEmpty(), "zero clue does not flag");

        graph = fixture(game, 0, true);
        graph.get(4).hasMine = false; // Deliberately inconsistent fixture proves no mine oracle is used.
        click(game, 1);
        check(graph.get(4).flagged, "flag deduction uses visible counts, not actual mine contents");
        graph = fixture(game, 0, true);
        State.flagBinding1 = new Binding(true, 1, Binding.BoundAction.FLAG);
        State.revealBinding1 = new Binding(true, 3, Binding.BoundAction.REVEAL);
        click(game, 3); check(graph.numFlags == 4, "automatic flags respect swapped reveal binding");
        graph = fixture(game, 0, true); State.DRAWING = true; click(game, 1);
        State.DRAWING = false; State.CURRENTLY_DRAWING = false;
        check(graph.numFlags == 0, "drawing mode excludes automatic flags");
        State.LOST_GAME = true; click(game, 1); State.LOST_GAME = false;
        check(graph.numFlags == 0, "loss state excludes automatic flags");
        State.WON_GAME = true; click(game, 1); State.WON_GAME = false;
        check(graph.numFlags == 0, "win state excludes automatic flags");
        graph = fixture(game, 0, true); graph.get(20).revealed = true; click(game, 1);
        check(State.WON_GAME && game.profile.extraSolved.contains("flag-test"), "flag chord uses original victory handling");
        realFlags(game, "2018_01_29_A.puz", 6, "hex");
        realFlags(game, "2018_01_30_A.puz", 6, "mixed-small");
        realFlags(game, "2018_01_30_A.puz", 10, "mixed-large");

        // Preserve a second independent slot for the following unmodified JVM.
        graph = fixture(game, 0, true); click(game, 1); game.handleClose();
        return count;
    }

    private static void realFlags(Game game, String filename, int sides, String label) throws Exception {
        State.LOST_GAME = false; State.WON_GAME = false;
        Puzzle puzzle = PuzzleIn.readXMLPuzzle(filename); GameLogic.setMineNeighbors(puzzle.G);
        game.puzzle = puzzle; game.profile = new Profile(); game.profile.id = 1;
        Game.mh.setGraph(puzzle.G);
        Node clue = null;
        for (Node n : puzzle.G.V.values()) {
            int x = (int) Math.round(n.pos.x), y = (int) Math.round(n.pos.y);
            if (n.poly.points.size() == sides && !n.hasMine && !n.secret && n.numMineNeighbors > 0
                    && x > 90 && x < Game.w - 90 && y > 90 && y < Game.h - 90
                    && puzzle.G.pointInNode(x, y) == n) { clue = n; break; }
        }
        check(clue != null, label + ": actual clue for flag deduction exists");
        clue.revealed = true;
        for (Node n : clue.getNeighbors()) {
            if (!n.hasMine) n.revealed = true;
            else if (n.flagged) { n.flagged = false; puzzle.G.numFlags--; }
        }
        HashSet<Integer> edges = new HashSet<Integer>(clue.edges);
        List<Node> expected = Chord.flagTargets(clue);
        check(expected.size() == clue.numMineNeighbors, label + ": original graph determines remaining mines");
        int before = puzzle.G.numFlags;
        game.dispatchEvent(new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, (int) Math.round(clue.pos.x), (int) Math.round(clue.pos.y), 1, false, 1));
        check(expected.stream().allMatch(n -> n.flagged && !n.revealed), label + ": all actual remaining neighbors flagged");
        check(puzzle.G.numFlags == before + expected.size(), label + ": original flag counter updated");
        check(clue.edges.equals(edges), label + ": adjacency remains intact");
        System.out.println("PASS actual flag chord: " + label + ", neighbors=" + edges.size());
    }

    static int highlights(boolean enabled) throws Exception {
        count = 0;
        for (String filename : new String[]{"2018_01_29_A.puz", "2018_01_30_A.puz"}) {
            Puzzle puzzle = PuzzleIn.readXMLPuzzle(filename); GameLogic.setMineNeighbors(puzzle.G);
            Node clue = null;
            for (Node n : puzzle.G.V.values()) {
                if (!n.hasMine && !n.secret && n.numMineNeighbors > 0 && n.edges.size() >= 3
                        && n.getNeighbors().stream().anyMatch(a -> !a.hasMine && a.numMineNeighbors > 0)) {
                    clue = n; break;
                }
            }
            check(clue != null, "actual numeric neighbor exists for highlight test " + filename);
            Node revealed = null, hidden = null, flag = null;
            for (Node n : clue.getNeighbors()) {
                n.revealed = false; n.flagged = false;
                if (revealed == null && !n.hasMine && n.numMineNeighbors > 0) revealed = n;
            }
            for (Node n : clue.getNeighbors()) if (n != revealed) {
                if (hidden == null) hidden = n; else if (flag == null) flag = n;
            }
            revealed.revealed = true; flag.flagged = true; clue.revealed = true;
            HashSet<Integer> edges = new HashSet<Integer>(clue.edges);
            State.HOVER_NEIGHBORS = clue.id;
            BufferedImage image = overlay(puzzle.G);
            check(painted(image, revealed) == !enabled, "revealed numeric neighbor highlight follows mode");
            check(painted(image, hidden), "covered neighbor is highlighted");
            check(painted(image, flag), "flagged neighbor is highlighted");
            check(!painted(image, clue), "hovered clue is not added to its own neighbors");
            check(clue.edges.equals(edges) && clue.getNeighbors().size() == edges.size(), "drawing never changes original adjacency");
            check(NeighborHighlights.neighbors(clue).size() == edges.size() - 1, "only the single revealed neighbor is filtered");
            for (Node neighbor : clue.getNeighbors())
                check(painted(image, neighbor) == (!enabled || !neighbor.revealed), "each polygon highlight matches its reveal state " + neighbor.id);
            System.out.println("PASS Ctrl overlay: " + filename + ", mode=" + enabled
                    + ", total=" + edges.size() + ", visible=" + NeighborHighlights.neighbors(clue).size());
            ImageIO.write(image, "png", Paths.get((enabled ? "mod" : "vanilla") + "-ctrl-" + filename + ".png").toFile());
            if (enabled) {
                hidden.revealed = true;
                check(!painted(overlay(puzzle.G), hidden), "newly revealed neighbor disappears on next redraw");
                revealed.revealed = false;
                check(painted(overlay(puzzle.G), revealed), "neighbor restored to covered reappears on redraw");
                revealed.revealed = true; revealed.numMineNeighbors = 0;
                check(!painted(overlay(puzzle.G), revealed), "revealed zero cell is excluded as well");
                check(NeighborHighlights.neighbors(clue).contains(flag), "filtered snapshot retains flags");
            }
        }
        if (enabled) {
            boolean failed = false;
            try (java.io.InputStream input = V2Tests.class.getResourceAsStream("/graph/Node.class")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) >= 0) bytes.write(buffer, 0, read);
                try { ModAgent.patch(bytes.toByteArray()); } catch (IllegalStateException ex) { failed = true; }
            }
            check(failed, "unexpected rendering class fails rather than silently skipping the patch");
        }
        return count;
    }

    private static BufferedImage overlay(Graph graph) throws Exception {
        BufferedImage image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        Method method = Class.forName("draw.Drawer").getDeclaredMethod("drawHoverNeighbors",
                int.class, int.class, Graphics2D.class, Graph.class);
        method.setAccessible(true);
        Graphics2D graphics = image.createGraphics();
        try { method.invoke(null, 0, 0, graphics, graph); } finally { graphics.dispose(); }
        return image;
    }

    private static boolean painted(BufferedImage image, Node node) {
        return (image.getRGB((int) Math.round(node.pos.x), (int) Math.round(node.pos.y)) >>> 24) != 0;
    }

    static int reload() {
        count = 0;
        Profile loaded = new Profile(); check(loaded.loadProfile(1), "fresh vanilla JVM reads flag-chord save");
        check(loaded.progress.get("flag-test").get(0).containsAll(java.util.Arrays.asList(1, 2, 3, 4)),
                "fresh vanilla JVM retains all automatic flags");
        return count;
    }
}
