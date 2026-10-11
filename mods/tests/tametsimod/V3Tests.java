package tametsimod;

import game.Game;
import game.GameLogic;
import game.Profile;
import game.State;
import graph.Graph;
import graph.Node;
import puzzle.Puzzle;
import puzzle.PuzzleIn;
import puzzle.ColumnHint;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class V3Tests {
    private static int count;
    private static void check(boolean value, String description) {
        count++; if (!value) throw new AssertionError(description);
    }
    private static void state() {
        State.CURRENT_SCREEN = State.SCREEN_TYPE.EXTRA_LEVEL;
        State.CURRENT_LEVEL = "v3-test";
        State.LOST_GAME = false; State.WON_GAME = false; State.DRAWING = false;
        State.CURRENTLY_DRAWING = false; State.LOADING = false; State.SHOW_CANVAS = false;
        State.HOVER_HINT = null;
        State.revealBinding1 = new io.Binding(true, 1, io.Binding.BoundAction.REVEAL);
        State.flagBinding1 = new io.Binding(true, 3, io.Binding.BoundAction.FLAG);
        RuntimeOptions.setForTests(EnumSet.allOf(Feature.class));
    }
    private static void click(Game game, int x, int y) {
        game.dispatchEvent(new MouseEvent(game, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, 1));
    }
    private static final class HitGraph extends Graph {
        Node hit;
        @Override public Node pointInNode(int x, int y) { return hit; }
    }
    private static HitGraph tileFixture(Game game, boolean revealAction) {
        state(); HitGraph graph = new HitGraph();
        graph.hit = graph.addNode(0); graph.hit.revealed = true;
        Node mine = graph.addNode(1); mine.hasMine = true; mine.flagged = revealAction;
        Node safe = graph.addNode(2); safe.revealed = !revealAction;
        graph.addNode(20); graph.addEdge(0, 1); graph.addEdge(0, 2);
        graph.numMines = 1; graph.numFlags = revealAction ? 1 : 0; GameLogic.setMineNeighbors(graph);
        game.puzzle = new Puzzle(); game.puzzle.G = graph; game.puzzle.id = "v3-test";
        game.profile = new Profile(); game.profile.id = 2; Game.mh.setGraph(graph);
        return graph;
    }
    static int game(Game game) throws Exception {
        count = 0;
        HitGraph graph = tileFixture(game, true);
        EnumSet<Feature> selected = EnumSet.allOf(Feature.class); selected.remove(Feature.REVEAL);
        RuntimeOptions.setForTests(selected); click(game, 320, 300);
        check(!graph.get(2).revealed, "reveal feature can be disabled independently");
        RuntimeOptions.setForTests(EnumSet.of(Feature.REVEAL)); click(game, 320, 300);
        check(graph.get(2).revealed, "reveal feature works without other features");
        graph = tileFixture(game, false); RuntimeOptions.setForTests(EnumSet.of(Feature.REVEAL)); click(game, 320, 300);
        check(!graph.get(1).flagged, "flag feature can be disabled independently");
        RuntimeOptions.setForTests(EnumSet.of(Feature.FLAG)); click(game, 320, 300);
        check(graph.get(1).flagged, "flag feature works without reveal chord");

        column(game, "2018_01_15_A.puz", 0);
        column(game, "2018_01_15_A.puz", 1);
        column(game, "2018_01_29_A.puz", 0);
        column(game, "2018_01_30_C.puz", 2);
        icons(game);
        state();
        return count;
    }
    private static void column(Game game, String filename, int direction) throws Exception {
        state(); Puzzle puzzle = PuzzleIn.readXMLPuzzle(filename); GameLogic.setMineNeighbors(puzzle.G);
        game.puzzle = puzzle; game.profile = new Profile(); game.profile.id = 2; Game.mh.setGraph(puzzle.G);
        ColumnHint hint = null;
        for (ColumnHint h : puzzle.columnHints) {
            int x = (int) Math.round(h.textLocation.x), y = (int) Math.round(h.textLocation.y);
            if (h.numMines <= 0 || h.ids.size() <= h.numMines || x < 80 || y < 15
                    || x > Game.w - 70 || y > Game.h - 70 || puzzle.G.pointInNode(x, y) != null) continue;
            if (direction == 0 && y < 80) continue;
            if (direction == 1 && y >= 80) continue;
            if (direction == 2 && Math.abs(h.textRotation) < 0.01) continue;
            if (ColumnChord.hit(puzzle, x, y) == h) { hint = h; break; }
        }
        check(hint != null, filename + ": original line hint is clickable");
        for (Integer id : hint.ids) {
            Node n = puzzle.G.get(id); n.revealed = false;
            if (n.hasMine && !n.flagged) { n.flagged = true; puzzle.G.numFlags++; }
            if (!n.hasMine && n.flagged) { n.flagged = false; puzzle.G.numFlags--; }
        }
        List<Node> targets = ColumnChord.targets(hint, puzzle);
        check(!targets.isEmpty() && hint.numFlagged(puzzle.G) == hint.numMines, "line chord counts flags from original member IDs");
        HashSet<Integer> ids = new HashSet<Integer>(hint.ids);
        int x = (int) Math.round(hint.textLocation.x), y = (int) Math.round(hint.textLocation.y);
        RuntimeOptions.setForTests(EnumSet.of(Feature.REVEAL)); click(game, x, y);
        check(targets.stream().noneMatch(n -> n.revealed), "disabled line chord leaves hidden cells closed");
        RuntimeOptions.setForTests(EnumSet.of(Feature.COLUMNS)); click(game, x, y);
        check(targets.stream().allMatch(n -> n.revealed), "clicking line hint reveals all remaining original members");
        check(!State.LOST_GAME && hint.ids.equals(ids), "line chord preserves membership and uses original reveal engine");
        if (!State.WON_GAME) check(game.profile.progress.get(puzzle.id).get(1).containsAll(idsOf(targets)), "line expansion records original save progress");
        java.lang.reflect.Method render = ModTests.class.getDeclaredMethod("renderGame", Game.class, String.class);
        render.setAccessible(true); render.invoke(null, game, "line-" + direction + "-" + filename + ".png");
        System.out.println("PASS line hint: " + filename + ", rotation=" + hint.textRotation + ", members=" + hint.ids.size());

        state(); for (Integer id : hint.ids) puzzle.G.get(id).revealed = false;
        Node flagged = null; for (Integer id : hint.ids) if (puzzle.G.get(id).flagged) { flagged = puzzle.G.get(id); break; }
        flagged.flagged = false;
        check(ColumnChord.targets(hint, puzzle).isEmpty(), "insufficient line flags do nothing");
        flagged.flagged = true; hint.hidden = true;
        check(ColumnChord.targets(hint, puzzle).isEmpty(), "hidden line hint cannot leak count"); hint.hidden = false;
        Node wrong = targets.get(0); wrong.flagged = true; flagged.flagged = false;
        Chord.Result result = Chord.reveal(ColumnChord.targets(hint, puzzle), game);
        check(result.lost && State.LOST_GAME, "incorrect line flags can still hit a mine");
        // Zero-total line is valid and must not be excluded by the ordinary positive-number rule.
        state(); Puzzle zero = new Puzzle(); zero.G = new Graph();
        Node node = zero.G.addNode(99);
        ColumnHint zeroHint = new ColumnHint(new HashSet<Integer>(java.util.Arrays.asList(99)), zero.G);
        check(ColumnChord.targets(zeroHint, zero).contains(node), "zero-mine line can reveal remaining members");
    }
    private static HashSet<Integer> idsOf(List<Node> nodes) {
        HashSet<Integer> ids = new HashSet<Integer>(); for (Node node : nodes) ids.add(node.id); return ids;
    }
    private static void icons(Game game) throws Exception {
        state(); Puzzle puzzle = PuzzleIn.readXMLPuzzle("2018_01_30_A.puz");
        Node small = null, large = null;
        for (Node n : puzzle.G.V.values()) {
            n.flagged = true; n.revealed = false;
            if (small == null || FlagIcons.size(n) < FlagIcons.size(small)) small = n;
            if (large == null || FlagIcons.size(n) > FlagIcons.size(large)) large = n;
        }
        check(FlagIcons.size(large) > FlagIcons.size(small) * 2, "flag icon scales with the actual polygon size");
        BufferedImage image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); FlagIcons.draw(g, puzzle); g.dispose();
        check(pixelsNear(image, small) > 0 && pixelsNear(image, large) > 0, "small and large flagged cells show central flag icons");
        javax.imageio.ImageIO.write(image, "png", Paths.get("flag-icons-mixed.png").toFile());
        small.flagged = false;
        image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics(); FlagIcons.draw(g, puzzle); g.dispose();
        check(pixelsNear(image, small) == 0, "unflagged cell has no icon");
        RuntimeOptions.setForTests(EnumSet.of(Feature.REVEAL));
        image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics(); FlagIcons.draw(g, puzzle); g.dispose();
        check(pixelsNear(image, large) == 0, "flag icon feature can be disabled independently");
        RuntimeOptions.setForTests(EnumSet.of(Feature.FLAG_ICON));
        game.puzzle = puzzle; Game.mh.setGraph(puzzle.G); draw.Drawer.clearCache();
        java.lang.reflect.Method render = ModTests.class.getDeclaredMethod("renderGame", Game.class, String.class);
        render.setAccessible(true); render.invoke(null, game, "flag-icons-board.png");
    }
    private static int pixelsNear(BufferedImage image, Node n) {
        draw.Point center = n.poly.getCenter();
        int x = (int) Math.round(n.pos.x + center.x), y = (int) Math.round(n.pos.y + center.y);
        int count = 0, radius = Math.max(1, FlagIcons.size(n) / 2);
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++)
            if (x + dx >= 0 && y + dy >= 0 && x + dx < image.getWidth() && y + dy < image.getHeight()
                    && (image.getRGB(x + dx, y + dy) >>> 24) != 0) count++;
        return count;
    }

    private static Map<Feature, Boolean> features(boolean all) {
        Map<Feature, Boolean> selection = new java.util.EnumMap<Feature, Boolean>(Feature.class);
        for (Feature feature : Feature.values()) selection.put(feature, all);
        return selection;
    }
    private static Properties probe(Path root) throws Exception {
        Path marker = root.resolve("mods/native-probe.properties"); FilesSupport.write(marker, new Properties());
        Process process = new ProcessBuilder(root.resolve("tametsi.exe").toString(), "--l4j-debug")
                .directory(root.toFile()).start();
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            Properties result = FilesSupport.read(marker);
            if (result.getProperty("pid") != null) {
                check("true".equals(result.getProperty("native.exe")), "probe was executed through the original exe");
                process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                // Marker precedes process exit by a few milliseconds; wait until its lock is released.
                for (int i = 0; i < 50; i++) {
                    try (GameGuard guard = GameGuard.acquire(root, "game")) {
                        GameGuard.ensureStopped(root);
                        return result;
                    }
                    catch (Exception ex) { Thread.sleep(100); }
                }
                throw new Exception("Native probe did not release its lock.");
            }
            Thread.sleep(100);
        }
        throw new Exception("Native exe probe failed. Inspect " + root.resolve("launch4j.log") + " and mods/runs.");
    }
    static int nativeStartup(Path root) throws Exception {
        count = 0;
        String originalHash = FilesSupport.sha256(root.resolve("tametsi.exe"));
        Path ini = root.resolve("tametsi.l4j.ini");
        Files.write(ini, "# user option\r\n-Dtametsimod.startupProbe=true\r\n-Dcustom.option=preserved\r\n".getBytes("ASCII"));
        Map<Feature, Boolean> selected = features(true);
        NativeIntegration.apply(root, new Settings(root), true, selected);
        String text = new String(Files.readAllBytes(ini), "ASCII");
        check(text.contains("-Dcustom.option=preserved"), "native installation preserves existing JVM options");
        check(Files.exists(root.resolve("mods/installation/tametsi.l4j.ini.original")), "native installation saves original sidecar options");
        Properties first = probe(root);
        check("mod".equals(first.getProperty("mode")) && "true".equals(first.getProperty("input.installed")), "original exe enables mod without a separate game bootstrap");
        check("true".equals(first.getProperty("colorChord")), "native startup loads the color chord switch");
        check(first.getProperty("backup") != null, "first native startup creates an automatic snapshot");

        for (int i = 2; i <= 12; i++) {
            Files.write(root.resolve("profiles/p0.sav"), ("startup-progress-" + i).getBytes("UTF-8"));
            Properties result = probe(root); check(result.getProperty("backup") != null, "native startup backup " + i);
        }
        Path auto = root.resolve("mods/backups/automatic");
        long total; try (java.util.stream.Stream<Path> files = Files.list(auto)) { total = files.filter(p -> p.toString().endsWith(".zip")).count(); }
        check(total == 10, "twelve real exe starts retain exactly ten automatic snapshots");
        HashSet<String> payloads = new HashSet<String>();
        try (java.nio.file.DirectoryStream<Path> slots = Files.newDirectoryStream(auto, "startup-*.zip")) {
            for (Path slot : slots) {
                AutomaticBackups.Snapshot snapshot = AutomaticBackups.read(root, slot);
                payloads.add(new String(snapshot.files.get("profiles/p0.sav"), "UTF-8"));
            }
        }
        check(payloads.size() == 10 && payloads.contains("startup-progress-12") && !payloads.contains("startup-progress-2"), "rotation retains the most recent snapshot contents");
        selected.put(Feature.BACKUP, false); selected.put(Feature.FLAG_ICON, false);
        NativeIntegration.apply(root, new Settings(root), true, selected);
        Properties noBackup = probe(root);
        check(noBackup.getProperty("backup") == null && "false".equals(noBackup.getProperty(Feature.FLAG_ICON.key)), "individual startup features persist and can be disabled");
        NativeIntegration.apply(root, new Settings(root), false, selected);
        Properties vanilla = probe(root);
        check("vanilla".equals(vanilla.getProperty("mode")) && "false".equals(vanilla.getProperty("input.installed")), "original exe disables mod on the next start");
        for (Feature feature : Feature.values()) check("false".equals(vanilla.getProperty(feature.key)), "master switch disables " + feature.key);
        check(vanilla.getProperty("backup") == null, "disabled master switch skips automatic backup");
        check(FilesSupport.sha256(root.resolve("tametsi.exe")).equals(originalHash), "native installation never modifies exe bytes");
        text = new String(Files.readAllBytes(ini), "ASCII");
        check(text.indexOf("# TAMETSI_MOD_BEGIN") == text.lastIndexOf("# TAMETSI_MOD_BEGIN"), "reapplying native settings produces one managed block");
        Backups backups = new Backups(root);
        Path chosen = null;
        for (Path path : backups.list()) if (path.toString().endsWith(".zip") && AutomaticBackups.read(root, path).files.containsKey("profiles/p0.sav")) { chosen = path; break; }
        byte[] expected = AutomaticBackups.read(root, chosen).files.get("profiles/p0.sav");
        Files.write(root.resolve("profiles/p0.sav"), "current-progress-before-restore".getBytes("UTF-8"));
        Backups.RestoreResult restored = backups.restore(chosen);
        check(java.util.Arrays.equals(expected, Files.readAllBytes(root.resolve("profiles/p0.sav"))), "automatic ZIP snapshot restores original save bytes");
        check("current-progress-before-restore".equals(new String(Files.readAllBytes(restored.before.resolve("profiles/p0.sav")), "UTF-8")), "ZIP restore first preserves current progress");
        check(!Files.exists(root.resolve("profiles/p2.sav")), "absent backup slot remains absent");
        Files.write(chosen, new byte[]{1, 2, 3}); boolean failed = false;
        try { backups.restore(chosen); } catch (Exception ex) { failed = true; }
        check(failed, "corrupted automatic snapshot cannot restore");
        check(!backups.list().contains(chosen), "corrupted snapshot is excluded from the restore list");
        return count;
    }
}
