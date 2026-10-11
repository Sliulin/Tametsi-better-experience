package tametsimod;

import game.Game;
import game.GameLogic;
import game.Profile;
import game.State;
import graph.Graph;
import graph.Node;
import io.Binding;
import io.MouseHandler;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import javax.swing.SwingUtilities;
import puzzle.Puzzle;
import puzzle.PuzzleIn;

/** Chord scenarios adapted from the upstream tests; extra tests exercise Tametsi itself. */
public final class ModTests {
    private static int assertions;

    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }

    private interface Checked { void run() throws Exception; }
    private static void mustFail(Checked operation, String description) throws Exception {
        boolean failed = false;
        try { operation.run(); } catch (Exception ex) { failed = true; }
        check(failed, description);
    }

    private static void resetState() {
        State.CURRENT_SCREEN = State.SCREEN_TYPE.EXTRA_LEVEL;
        State.CURRENT_LEVEL = "mod-test";
        State.LOST_GAME = false; State.WON_GAME = false; State.LOADING = false;
        State.INACTIVE = false; State.INACTIVE_FRAMES = 0;
        State.DRAWING = false; State.CURRENTLY_DRAWING = false; State.SHOW_CANVAS = false;
        State.SOUND = false; State.LOG_STEAM_ERRORS = false; State.STEAM_CONNECTED = false;
        State.revealBinding1 = new Binding(true, 1, Binding.BoundAction.REVEAL);
        State.revealBinding2 = null;
        State.flagBinding1 = new Binding(true, 3, Binding.BoundAction.FLAG);
        State.flagBinding2 = null;
        State.showCanvasBinding1 = null; State.showCanvasBinding2 = null;
        State.showNeighborsBinding1 = null; State.showNeighborsBinding2 = null;
        State.toggleDrawingBinding1 = null; State.toggleDrawingBinding2 = null;
        State.backBinding1 = null; State.backBinding2 = null;
    }

    private static final class FixtureGraph extends Graph {
        Node hit;
        @Override public Node pointInNode(int x, int y) { return hit; }
    }

    private static final class Fixture {
        final FixtureGraph graph = new FixtureGraph();
        final Node clue = graph.addNode(0);
        final Node safe = graph.addNode(1);
        final Node mine = graph.addNode(2);
        final Node other = graph.addNode(3);
        final Node later = graph.addNode(4);
        final Node distant = graph.addNode(50);
        Fixture() {
            graph.addEdge(0, 1); graph.addEdge(0, 2); graph.addEdge(0, 3); graph.addEdge(0, 4);
            clue.revealed = true; mine.hasMine = true; mine.flagged = true;
            GameLogic.setMineNeighbors(graph);
            graph.numMines = 1; graph.numFlags = 1; graph.hit = clue;
        }
    }

    private static Game newGame(Fixture fixture) {
        Game game = new Game();
        Game.timer.stop();
        resetState();
        game.puzzle = new Puzzle(); game.puzzle.G = fixture.graph; game.puzzle.id = "mod-test";
        game.profile = new Profile(); game.profile.id = 0;
        Game.mh.setGraph(fixture.graph);
        return game;
    }

    private static MouseEvent event(Game game, int button) {
        return new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                320, 300, 1, false, button);
    }
    private static void click(Game game, int button) { game.dispatchEvent(event(game, button)); }

    private static void chordCoreTests() {
        // Upstream TestChordSuccess: correct flag count expands hidden neighbors.
        resetState(); Fixture fixture = new Fixture();
        Chord.Result result = Chord.reveal(fixture.clue, null);
        check(result.safeReveals.contains(1) && result.safeReveals.contains(3) && result.safeReveals.contains(4), "correct flags reveal neighbors");
        check(fixture.mine.flagged && !fixture.mine.revealed, "flagged mine stays closed");
        check(!fixture.distant.revealed, "non-neighbor remains closed");
        check(!result.lost, "correct flags do not lose");
        check(!Chord.reveal(fixture.clue, null).changed(), "repeat chord is a no-op");

        // Upstream TestChordWrongFlag: flag-count mismatch is a no-op.
        resetState(); fixture = new Fixture(); fixture.mine.flagged = false;
        check(!Chord.reveal(fixture.clue, null).changed() && !fixture.safe.revealed, "too few flags do nothing");
        fixture.mine.flagged = true; fixture.other.flagged = true;
        check(!Chord.reveal(fixture.clue, null).changed() && !fixture.safe.revealed, "too many flags do nothing");

        // Upstream TestChordHitsMine: a matching count must not check whether flags are correct.
        resetState(); fixture = new Fixture(); fixture.mine.flagged = false; fixture.other.flagged = true;
        result = Chord.reveal(fixture.clue, null);
        check(result.lost && State.LOST_GAME && fixture.mine.revealed, "wrong flag hits an unflagged mine");
        check(fixture.other.flagged && !fixture.other.revealed, "wrongly flagged safe tile remains protected");
        check(result.safeReveals.contains(1) && !result.safeReveals.contains(2), "mine excluded from saved safe progress");
        check(!fixture.later.revealed, "processing stops on first mine");

        resetState(); fixture = new Fixture(); fixture.clue.secret = true;
        check(Chord.targets(fixture.clue).isEmpty(), "hidden clue cannot leak its number");
        fixture.clue.secret = false; fixture.clue.revealed = false;
        check(Chord.targets(fixture.clue).isEmpty(), "unopened clue cannot chord");
        fixture.clue.revealed = true; fixture.clue.numMineNeighbors = 0;
        check(Chord.targets(fixture.clue).isEmpty(), "zero behavior follows upstream");

        resetState(); fixture = new Fixture(); State.WON_GAME = true;
        check(!Chord.reveal(fixture.clue, null).changed(), "no chord after win");
        State.WON_GAME = false; State.LOST_GAME = true;
        check(!Chord.reveal(fixture.clue, null).changed(), "no chord after loss");

        // Graph neighbors need not be physically adjacent or limited to eight.
        resetState(); fixture = new Fixture();
        for (int i = 100; i < 112; i++) fixture.graph.addEdge(0, fixture.graph.addNode(i).id);
        check(Chord.targets(fixture.clue).size() == 15, "irregular graph with more than eight neighbors");

        // Reuse the original zero-region flood fill, not another implementation.
        fixture.graph.addEdge(1, 50);
        result = Chord.reveal(fixture.clue, null);
        check(fixture.distant.revealed && result.safeReveals.contains(50), "zero expansion uses original reveal engine");
    }

    private static void vanillaTests() throws Exception {
        resetState(); Fixture fixture = new Fixture(); Game game = newGame(fixture);
        click(game, 1);
        check(!(Game.mh instanceof ChordMouseHandler), "vanilla keeps original input handler");
        check(!ModRuntime.installed(), "vanilla does not install hook");
        check(!fixture.safe.revealed, "original numeric click does not expand neighbors");
        fixture.graph.hit = fixture.safe;
        click(game, 1);
        check(fixture.safe.revealed, "original single-cell reveal still works");
        game.handleClose();
        Profile loaded = new Profile(); check(loaded.loadProfile(0), "vanilla reloads save");
        check(loaded.progress.get("mod-test").get(1).contains(1), "vanilla reveal progress is saved");
        assertions += V2Tests.highlights(false);
    }

    private static void modInputTests() throws Exception {
        resetState(); ModRuntime.install(); Fixture fixture = new Fixture(); Game game = newGame(fixture);
        click(game, 1);
        check(Game.mh instanceof ChordMouseHandler, "AWT hook attached before first mouse press");
        check(fixture.safe.revealed && fixture.other.revealed && fixture.later.revealed, "real Game mouse listener expands all targets");
        check(game.profile.progress.get("mod-test").get(1).contains(1), "original profile records expansion");
        game.handleClose();
        Profile loaded = new Profile(); check(loaded.loadProfile(0), "original loader reads mod save");
        check(loaded.progress.get("mod-test").get(1).containsAll(new HashSet<Integer>(java.util.Arrays.asList(1, 3, 4))), "all expanded cells persisted");

        fixture = new Fixture(); game.puzzle.G = fixture.graph; Game.mh.setGraph(fixture.graph);
        ChordMouseHandler handler = (ChordMouseHandler) Game.mh;
        check(handler.G == fixture.graph, "level graph updates after injection");
        resetState();
        State.DRAWING = true; check(!handler.eligible(event(game, 1)), "drawing excluded"); State.DRAWING = false;
        State.SHOW_CANVAS = true; check(!handler.eligible(event(game, 1)), "temporary canvas excluded"); State.SHOW_CANVAS = false;
        State.CURRENT_SCREEN = State.SCREEN_TYPE.LEVEL_SELECT;
        check(!handler.eligible(event(game, 1)), "level menu excluded");
        State.CURRENT_SCREEN = State.SCREEN_TYPE.PROFILE_SELECT;
        check(!handler.eligible(event(game, 1)), "profile menu excluded"); resetState();
        State.LOADING = true; check(!handler.eligible(event(game, 1)), "loading excluded"); State.LOADING = false;
        State.INACTIVE = true; check(!handler.eligible(event(game, 1)), "inactive window excluded"); State.INACTIVE = false;
        State.INACTIVE_FRAMES = 10; check(!handler.eligible(event(game, 1)), "activation grace excluded"); State.INACTIVE_FRAMES = 0;
        State.WON_GAME = true; check(!handler.eligible(event(game, 1)), "win screen excluded"); State.WON_GAME = false;
        State.LOST_GAME = true; check(!handler.eligible(event(game, 1)), "loss screen excluded"); State.LOST_GAME = false;
        State.showNeighborsBinding1 = new Binding(true, 1, Binding.BoundAction.SHOW_NEIGHBORS);
        check(!handler.eligible(event(game, 1)), "neighbor-view binding retains priority"); resetState();

        fixture.graph.hit = fixture.mine; click(game, 3);
        check(!fixture.mine.flagged, "right-click flag still delegates to original");
        resetState(); fixture.mine.flagged = true; fixture.graph.hit = fixture.clue;
        State.flagBinding1 = new Binding(true, 1, Binding.BoundAction.FLAG);
        State.revealBinding1 = new Binding(true, 3, Binding.BoundAction.REVEAL);
        click(game, 3);
        check(fixture.safe.revealed, "chord respects swapped reveal binding");

        resetState(); fixture = new Fixture(); game.puzzle.G = fixture.graph; Game.mh.setGraph(fixture.graph);
        game.profile = new Profile(); game.profile.id = 0;
        fixture.mine.flagged = false; fixture.other.flagged = true;
        click(game, 1);
        check(State.LOST_GAME && game.profile.hasMistake("mod-test"), "original loss handling records mistake");
        loaded = new Profile(); check(loaded.loadProfile(0), "loss save reloads");
        check(loaded.progress.get("mod-test").get(1).contains(1), "safe reveals before loss are persisted");
        check(!loaded.progress.get("mod-test").get(1).contains(2), "unflagged mine not written as safe reveal");
        game.doUndo();
        check(!State.LOST_GAME && !fixture.mine.revealed && fixture.safe.revealed, "original loss recovery remains compatible");

        resetState(); fixture = new Fixture(); fixture.distant.revealed = true;
        game.puzzle.G = fixture.graph; Game.mh.setGraph(fixture.graph); game.profile = new Profile(); game.profile.id = 0;
        click(game, 1);
        check(State.WON_GAME && game.profile.extraSolved.contains("mod-test"), "chord completion uses original win recording");
        realPuzzleTest(game, "2018_01_29_A.puz", 6, "hex");
        realPuzzleTest(game, "2018_01_30_A.puz", 6, "mixed-small");
        realPuzzleTest(game, "2018_01_30_A.puz", 10, "mixed-large");
        assertions += V2Tests.run(game);
        assertions += V2Tests.highlights(true);
        assertions += V3Tests.game(game);
        assertions += V4Tests.run(game);
        assertions += HintTests.run(game);
        // Leave ordinary in-progress data for a fresh, unmodified JVM to reload.
        resetState(); fixture = new Fixture(); game.puzzle = new Puzzle();
        game.puzzle.G = fixture.graph; game.puzzle.id = "mod-test"; Game.mh.setGraph(fixture.graph);
        game.profile = new Profile(); game.profile.id = 0; click(game, 1); game.handleClose();
    }

    private static void renderGame(Game game, String filename) throws Exception {
        // The original renderer consults its frame for cursor offsets, even offscreen.
        java.lang.reflect.Field frameField = Game.class.getDeclaredField("frame");
        frameField.setAccessible(true);
        if (frameField.get(null) == null) {
            javax.swing.JFrame hidden = new javax.swing.JFrame();
            hidden.setSize(Game.w, Game.h);
            hidden.setContentPane(new javax.swing.JPanel() {
                @Override public java.awt.Point getLocationOnScreen() {
                    return new java.awt.Point(100000, 100000);
                }
            });
            frameField.set(null, hidden); // Never made visible.
        }
        game.setSize(Game.w, Game.h);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Game.w, Game.h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try { game.paint(graphics); } finally { graphics.dispose(); }
        javax.imageio.ImageIO.write(image, "png", Paths.get(filename).toFile());
    }

    private static void realPuzzleTest(Game game, String filename, int polygonSides, String label) throws Exception {
        resetState();
        Puzzle puzzle = PuzzleIn.readXMLPuzzle(filename);
        check(puzzle != null && puzzle.G != null, label + ": original XML loader works");
        GameLogic.setMineNeighbors(puzzle.G);
        game.puzzle = puzzle; game.profile = new Profile(); game.profile.id = 0;
        Game.mh.setGraph(puzzle.G);
        Node clue = null;
        for (Node candidate : puzzle.G.V.values()) {
            if (candidate.poly.points.size() != polygonSides || candidate.secret || candidate.hasMine
                    || candidate.numMineNeighbors <= 0) continue;
            int x = (int) Math.round(candidate.pos.x), y = (int) Math.round(candidate.pos.y);
            if (puzzle.G.pointInNode(x, y) != candidate) continue;
            if (x < 90 || x > Game.w - 90 || y < 90 || y > Game.h - 90) continue;
            if (candidate.getNeighbors().stream().noneMatch(n -> !n.revealed && !n.flagged && !n.hasMine)) continue;
            clue = candidate; break;
        }
        check(clue != null, label + ": positive clue with an unflagged safe neighbor exists");
        // Fixture preparation only: knowing mine contents is confined to the test.
        clue.revealed = true;
        for (Node neighbor : clue.getNeighbors()) {
            if (neighbor.hasMine && !neighbor.flagged) { neighbor.flagged = true; puzzle.G.numFlags++; }
        }
        java.util.List<Node> expected = Chord.targets(clue);
        check(!expected.isEmpty(), label + ": graph neighbors selected");
        final Node selectedClue = clue;
        check(expected.stream().allMatch(n -> selectedClue.edges.contains(n.id) && puzzle.G.get(n.id) == n),
                label + ": targets belong to original graph");
        int x = (int) Math.round(clue.pos.x), y = (int) Math.round(clue.pos.y);
        check(puzzle.G.pointInNode(x, y) == clue, label + ": polygon hit test locates actual shape");
        renderGame(game, label + "-before.png");
        game.dispatchEvent(new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
        check(expected.stream().allMatch(n -> n.revealed), label + ": numeric click reveals actual graph neighbors");
        check(!State.LOST_GAME, label + ": correct flags do not lose");
        renderGame(game, label + "-after.png");
        System.out.println("PASS actual puzzle: " + label + ", sides=" + polygonSides + ", neighbors=" + clue.edges.size());
    }

    private static void reloadWithoutMod() throws Exception {
        assertions += V2Tests.reload();
        check(!ModRuntime.installed(), "fresh vanilla JVM has no hook");
        Profile profile = new Profile(); check(profile.loadProfile(0), "fresh vanilla JVM reads mod save");
        check(profile.progress.get("mod-test").get(1).containsAll(java.util.Arrays.asList(1, 3, 4)),
                "fresh vanilla JVM retains mod expansion progress");
    }

    private static void managerPreview() throws Exception {
        javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        Path root = Paths.get("").toRealPath();
        Bootstrap.verifyGame(root);
        check(true, "packaged runtime supports current executable");
        Manager manager = new Manager(root, false);
        java.lang.reflect.Field frameField = Manager.class.getDeclaredField("frame"); frameField.setAccessible(true);
        javax.swing.JFrame frame = (javax.swing.JFrame) frameField.get(manager);
        java.lang.reflect.Field checkboxField = Manager.class.getDeclaredField("enabled"); checkboxField.setAccessible(true);
        javax.swing.JCheckBox checkbox = (javax.swing.JCheckBox) checkboxField.get(manager);
        check(!checkbox.isSelected(), "launcher defaults to vanilla");
        checkbox.doClick(); check(!new Settings(root).enabled(), "manager selection waits for confirmation");
        java.lang.reflect.Field featuresField = Manager.class.getDeclaredField("featureBoxes"); featuresField.setAccessible(true);
        java.util.Map<?, ?> boxes = (java.util.Map<?, ?>) featuresField.get(manager);
        check(boxes.size() == Feature.values().length, "manager exposes all feature switches");
        java.util.Map<Feature, Boolean> selection = new java.util.EnumMap<Feature, Boolean>(Feature.class);
        for (Feature feature : Feature.values()) selection.put(feature, true);
        NativeIntegration.apply(root, new Settings(root), true, selection);
        check(new Settings(root).enabled(), "confirmed manager settings persist");
        javax.swing.JComponent content = (javax.swing.JComponent) frame.getContentPane();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try { content.printAll(graphics); } finally { graphics.dispose(); }
        javax.imageio.ImageIO.write(image, "png", root.resolve("manager-preview.png").toFile());
        checkbox.doClick(); check(new Settings(root).enabled(), "unconfirmed disable keeps previous settings");
        NativeIntegration.apply(root, new Settings(root), false, selection);
        check(!new Settings(root).enabled(), "confirmed disable persists");
        check(!ModRuntime.installed(), "manager switch does not install a hook in its own process");
        check(Manager.gameCommand(root, true, root.resolve("run-test")).get(0).equals(root.resolve("tametsi.exe").toString()),
                "manager opens the original executable");
        frame.dispose();
    }

    private static void backupTests(Path root) throws Exception {
        Path data = root.resolve("backup-fixture"); Files.createDirectories(data.resolve("profiles"));
        Files.write(data.resolve("tametsi.exe"), new byte[]{1, 2, 3});
        Files.write(data.resolve("profiles/p0.sav"), "old-progress".getBytes("UTF-8"));
        Files.write(data.resolve("profiles/p1.sav"), new byte[0]);
        Files.write(data.resolve("config.txt"), "w=1024".getBytes("UTF-8"));
        Backups backups = new Backups(data);
        Path first = backups.create("test-first"); Properties manifest = backups.validate(first);
        check("false".equals(manifest.getProperty("profiles/p2.sav.present")), "missing slot recorded without creating it");
        check("true".equals(manifest.getProperty("profiles/p1.sav.present")), "empty existing save is backed up");
        Files.write(data.resolve("profiles/p0.sav"), "new-progress".getBytes("UTF-8"));
        Files.write(data.resolve("profiles/p2.sav"), "new-slot".getBytes("UTF-8"));
        Backups.RestoreResult restored = backups.restore(first);
        check(new String(Files.readAllBytes(data.resolve("profiles/p0.sav")), "UTF-8").equals("old-progress"), "restore returns selected progress");
        check(new String(Files.readAllBytes(restored.before.resolve("profiles/p0.sav")), "UTF-8").equals("new-progress"), "current progress backed up before restore");
        check(new String(Files.readAllBytes(data.resolve("profiles/p2.sav")), "UTF-8").equals("new-slot"), "missing backup slot preserves current file");
        check(restored.skipped.contains("profiles/p2.sav"), "missing slot reported as skipped");
        check(Files.size(data.resolve("profiles/p1.sav")) == 0, "empty existing slot restored accurately");
        Path damaged = backups.create("test-damaged");
        Files.write(damaged.resolve("profiles/p0.sav"), new byte[]{9});
        String currentHash = FilesSupport.sha256(data.resolve("profiles/p0.sav"));
        mustFail(() -> backups.restore(damaged), "corrupt backup rejected before writes");
        check(currentHash.equals(FilesSupport.sha256(data.resolve("profiles/p0.sav"))), "failed validation leaves current progress intact");
        Path incomplete = data.resolve("mods/backups/incomplete"); Files.createDirectories(incomplete);
        mustFail(() -> backups.restore(incomplete), "incomplete backup rejected");
        Path outside = data.resolve("outside"); Files.createDirectories(outside);
        mustFail(() -> backups.validate(outside), "outside backup path rejected");

        Path blocked = backups.create("test-blocked-target");
        Path renamed = data.resolve("profiles/p0-saved-for-test.sav");
        Files.move(data.resolve("profiles/p0.sav"), renamed);
        Files.createDirectory(data.resolve("profiles/p0.sav"));
        mustFail(() -> backups.restore(blocked), "backup-before-restore failure stops overwrite");
        check(Files.isDirectory(data.resolve("profiles/p0.sav")), "invalid target stays untouched");

        Settings settings = new Settings(root);
        check(!settings.enabled(), "default mode is vanilla");
        settings.setEnabled(true); check(new Settings(root).enabled(), "enable survives new process settings read");
        settings.ensureFirstBackup(root, new Backups(root));
        Properties settingsData = FilesSupport.read(root.resolve("mods/settings.properties"));
        check(settingsData.getProperty("firstBackup") != null, "first enable stores verified backup");
        String saveHash = FilesSupport.sha256(root.resolve("profiles/p0.sav"));
        settings.setEnabled(false); check(!new Settings(root).enabled(), "disable persists");
        check(saveHash.equals(FilesSupport.sha256(root.resolve("profiles/p0.sav"))), "disable never rolls back progress");
        try (GameGuard guard = GameGuard.acquire(root, "game")) {
            mustFail(() -> { try (GameGuard second = GameGuard.acquire(root, "game")) { } }, "second session lock rejected");
        }
        try (GameGuard guard = GameGuard.acquire(root, "game")) { check(true, "lock released for next session"); }
        GameGuard.ensureStopped(root); check(true, "Windows process check succeeds");
    }

    public static void main(String[] args) throws Exception {
        final Throwable[] failure = new Throwable[1];
        try {
            if (args[0].equals("backups")) backupTests(Paths.get("").toAbsolutePath());
            else if (args[0].equals("native")) assertions += V3Tests.nativeStartup(Paths.get("").toRealPath());
            else if (args[0].equals("reload")) reloadWithoutMod();
            else SwingUtilities.invokeAndWait(() -> {
                try {
                    if (args[0].equals("gui")) managerPreview();
                    else {
                        chordCoreTests();
                        if (args[0].equals("mod")) modInputTests(); else vanillaTests();
                    }
                } catch (Throwable ex) { failure[0] = ex; }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
            System.out.println("PASS " + args[0] + ": " + assertions + " assertions");
            System.exit(0);
        } catch (Throwable ex) { ex.printStackTrace(); System.exit(1); }
    }
}
