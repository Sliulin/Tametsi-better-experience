package tametsimod;

import game.Game;
import game.GameLogic;
import game.Profile;
import game.State;
import graph.Node;
import puzzle.PuzzleIn;
import puzzle.Puzzle;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import javax.imageio.ImageIO;

final class V4Tests {
    private static int count;
    private static void check(boolean value, String label) { count++; if (!value) throw new AssertionError(label); }
    private static void state() {
        State.CURRENT_SCREEN = State.SCREEN_TYPE.EXTRA_LEVEL;
        State.LOADING = State.INACTIVE = State.WON_GAME = State.LOST_GAME = false;
        State.DRAWING = State.CURRENTLY_DRAWING = State.SHOW_CANVAS = false;
        State.INACTIVE_FRAMES = 0; State.SHOW_NEIGHBORS = false;
        State.showNeighborsBinding1 = new io.Binding(false, KeyEvent.VK_CONTROL, io.Binding.BoundAction.SHOW_NEIGHBORS);
        State.showNeighborsBinding2 = null;
        RuntimeOptions.setForTests(EnumSet.of(Feature.HIGHLIGHTS, Feature.NEIGHBOR_TOGGLE));
        NeighborToggle.resetForTests();
    }
    private static void press(Game game, int key, int modifiers) {
        game.keyPressed(new KeyEvent(game, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, key, KeyEvent.CHAR_UNDEFINED));
    }
    private static void release(Game game, int key) {
        game.keyReleased(new KeyEvent(game, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
    }
    private static void tap(Game game) { press(game, KeyEvent.VK_P, 0); release(game, KeyEvent.VK_P); }
    private static BufferedImage render(Puzzle puzzle) {
        BufferedImage image = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
        draw.Drawer.clearCache();
        draw.Drawer.draw(Game.w, Game.h, image.createGraphics(), puzzle);
        return image;
    }
    private static int x(Node n) { return (int)Math.round(n.pos.x + n.poly.getCenter().x); }
    private static int y(Node n) { return (int)Math.round(n.pos.y + n.poly.getCenter().y); }
    private static boolean differentAt(BufferedImage a, BufferedImage b, Node n) { return a.getRGB(x(n), y(n)) != b.getRGB(x(n), y(n)); }

    static int run(Game game) throws Exception {
        count = 0;
        menus(game);
        for (String file : new String[] {"2018_01_29_A.puz", "2018_01_30_A.puz"}) toggle(game, file);
        guards(game);
        state(); RuntimeOptions.setForTests(EnumSet.allOf(Feature.class));
        return count;
    }

    private static void menus(Game game) throws Exception {
        state(); game.profile = new Profile(); game.profile.id = 2;
        for (int menu = 0; menu < 2; menu++) {
            if (menu == 0) game.loadLevelSelect(); else game.loadProfileSelect();
            check(State.onMenu(), "actual original menu was loaded");
            Puzzle puzzle = game.puzzle;
            HashSet<Integer> flagged = new HashSet<Integer>();
            for (Node n : puzzle.G.V.values()) if (n.flagged) flagged.add(n.id);
            if (menu == 0) check(!flagged.isEmpty(), "locked level selector tiles use original flagged state");
            RuntimeOptions.setForTests(EnumSet.of(Feature.FLAG_ICON));
            BufferedImage overlay = new BufferedImage(Game.w, Game.h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = overlay.createGraphics(); FlagIcons.draw(graphics, puzzle); graphics.dispose();
            check(java.util.Arrays.stream(overlay.getRGB(0, 0, overlay.getWidth(), overlay.getHeight(), null, 0, overlay.getWidth())).allMatch(p -> p == 0), "menu flag icon overlay is completely empty");
            BufferedImage on = render(puzzle);
            int[] boardOn = draw.Drawer.boardBI.getRGB(0, 0, Game.w, Game.h, null, 0, Game.w);
            RuntimeOptions.setForTests(EnumSet.noneOf(Feature.class)); render(puzzle);
            int[] boardOff = draw.Drawer.boardBI.getRGB(0, 0, Game.w, Game.h, null, 0, Game.w);
            check(java.util.Arrays.equals(boardOn, boardOff), "full menu board rendering equals original without icon feature");
            check(puzzle.G.V.values().stream().allMatch(n -> n.flagged == flagged.contains(n.id)), "all menu locked/unlocked states preserved");
            ImageIO.write(on, "png", Paths.get(menu == 0 ? "v4-level-menu.png" : "v4-profile-menu.png").toFile());
        }
    }

    private static void toggle(Game game, String file) throws Exception {
        state(); Puzzle puzzle = PuzzleIn.readXMLPuzzle(file); GameLogic.setMineNeighbors(puzzle.G);
        game.puzzle = puzzle; Game.mh.setGraph(puzzle.G);
        Node clue = null;
        for (Node n : puzzle.G.V.values()) if (!n.hasMine && !n.secret && n.numMineNeighbors > 0
                && n.getNeighbors().size() >= 3 && puzzle.G.pointInNode(x(n), y(n)) == n) { clue = n; break; }
        check(clue != null, "actual polygon clue exists: " + file);
        Node revealed = null, hidden = null;
        clue.revealed = true;
        for (Node n : clue.getNeighbors()) { n.revealed = false; n.flagged = false; if (revealed == null) revealed = n; else if (hidden == null) hidden = n; }
        revealed.revealed = true;
        HashSet<Integer> edges = new HashSet<Integer>(clue.edges);
        int oldFlags = puzzle.G.numFlags;
        Game.mh.mouseMoved(new MouseEvent(game, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x(clue), y(clue), 0, false));
        BufferedImage off = render(puzzle);
        check(!NeighborToggle.visible(), "new game session starts with P toggle off");
        press(game, KeyEvent.VK_P, 0);
        check(NeighborToggle.visible() && !State.SHOW_NEIGHBORS, "P enables visible overlay without changing native held-key state");
        press(game, KeyEvent.VK_P, 0); press(game, KeyEvent.VK_P, 0);
        check(NeighborToggle.visible(), "held-key repeats do not toggle again");
        release(game, KeyEvent.VK_P);
        check(NeighborToggle.visible(), "P release preserves the latch");
        BufferedImage on = render(puzzle);
        check(differentAt(on, off, hidden), "actual Drawer displays covered neighbor without Ctrl");
        check(!differentAt(on, off, revealed), "actual Drawer excludes revealed neighbor with existing filter");
        ImageIO.write(on, "png", Paths.get("v4-p-on-" + file + ".png").toFile());
        press(game, KeyEvent.VK_CONTROL, 0); release(game, KeyEvent.VK_CONTROL);
        check(!State.SHOW_NEIGHBORS && NeighborToggle.visible(), "Ctrl release does not turn off P latch");
        tap(game); check(!NeighborToggle.visible(), "second P tap turns latch off");
        BufferedImage again = render(puzzle);
        check(!differentAt(again, off, hidden), "actual rendering stops highlighting after P turns off");
        press(game, KeyEvent.VK_CONTROL, 0);
        check(State.SHOW_NEIGHBORS && NeighborToggle.visible(), "original Ctrl hold still works with latch off");
        BufferedImage ctrl = render(puzzle);
        check(!differentAt(ctrl, on, hidden), "P uses the same neighbor overlay as native Ctrl");
        release(game, KeyEvent.VK_CONTROL); check(!NeighborToggle.visible(), "native Ctrl release restores latch-off state");
        RuntimeOptions.setForTests(EnumSet.of(Feature.HIGHLIGHTS)); tap(game);
        check(!NeighborToggle.visible(), "P feature can be disabled independently");
        press(game, KeyEvent.VK_CONTROL, 0); check(NeighborToggle.visible(), "native Ctrl works when P feature is disabled"); release(game, KeyEvent.VK_CONTROL);
        RuntimeOptions.setForTests(EnumSet.of(Feature.NEIGHBOR_TOGGLE)); tap(game);
        BufferedImage all = render(puzzle);
        check(differentAt(all, off, revealed), "P toggle works with revealed-neighbor filtering disabled");
        check(clue.edges.equals(edges) && puzzle.G.numFlags == oldFlags, "P never changes adjacency or mine flags");
        System.out.println("PASS actual P toggle: " + file);
    }

    private static void guards(Game game) {
        state();
        for (int guard = 0; guard < 9; guard++) {
            state();
            switch (guard) {
                case 0: State.CURRENT_SCREEN = State.SCREEN_TYPE.LEVEL_SELECT; break;
                case 1: State.CURRENT_SCREEN = State.SCREEN_TYPE.PROFILE_SELECT; break;
                case 2: State.LOADING = true; break;
                case 3: State.INACTIVE = true; break;
                case 4: State.INACTIVE_FRAMES = 1; break;
                case 5: State.WON_GAME = true; break;
                case 6: State.LOST_GAME = true; break;
                case 7: State.DRAWING = true; break;
                case 8: State.SHOW_CANVAS = true; break;
            }
            tap(game); check(!NeighborToggle.visible(), "P is ignored in guarded state " + guard);
            state(); tap(game);
            switch (guard) {
                case 0: State.CURRENT_SCREEN = State.SCREEN_TYPE.LEVEL_SELECT; break;
                case 1: State.CURRENT_SCREEN = State.SCREEN_TYPE.PROFILE_SELECT; break;
                case 2: State.LOADING = true; break;
                case 3: State.INACTIVE = true; break;
                case 4: State.INACTIVE_FRAMES = 1; break;
                case 5: State.WON_GAME = true; break;
                case 6: State.LOST_GAME = true; break;
                case 7: State.DRAWING = true; break;
                case 8: State.SHOW_CANVAS = true; break;
            }
            check(!NeighborToggle.visible(), "latched overlay is suspended in guarded state " + guard);
        }
        state(); press(game, KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK); release(game, KeyEvent.VK_P);
        check(!NeighborToggle.visible(), "Ctrl-P does not activate latch");
        press(game, KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK); release(game, KeyEvent.VK_P);
        check(!NeighborToggle.visible(), "Alt-P does not activate latch");
        press(game, KeyEvent.VK_P, 0); NeighborToggle.focusLost(); tap(game);
        check(!NeighborToggle.visible(), "focus loss clears held P debounce so the next tap works");
        tap(game); RuntimeOptions.setForTests(EnumSet.noneOf(Feature.class));
        check(!NeighborToggle.visible(), "disabled master/features suppress a prior latch");
    }
}
