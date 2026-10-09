package tametsimod;

import game.Game;
import game.State;
import java.awt.event.KeyEvent;

/** Session-only toggle; never writes the original held-key state or game saves. */
public final class NeighborToggle {
    private static boolean latched, down;
    private NeighborToggle() {}

    private static boolean eligible() {
        return State.onLevel() && !State.LOADING && !State.INACTIVE && State.INACTIVE_FRAMES == 0
                && !State.WON_GAME && !State.LOST_GAME && !State.DRAWING
                && !State.CURRENTLY_DRAWING && !State.SHOW_CANVAS;
    }

    public static boolean visible() {
        return State.SHOW_NEIGHBORS || (RuntimeOptions.enabled(Feature.NEIGHBOR_TOGGLE) && latched && eligible());
    }

    public static boolean pressed(Game game, KeyEvent event) {
        if (!RuntimeOptions.enabled(Feature.NEIGHBOR_TOGGLE) || event.getKeyCode() != KeyEvent.VK_P) return false;
        if (down) return true; // Ignore the operating system's held-key repeats.
        if (event.isAltDown() || event.isControlDown() || event.isMetaDown() || !eligible()) return false;
        down = true; latched = !latched;
        // Refresh the original hover calculation even if the mouse has not moved.
        if (Game.mh != null && Game.mh.game == game && State.MOUSE_X != null && State.MOUSE_Y != null)
            Game.mh.mouseMoved(new java.awt.event.MouseEvent(game, java.awt.event.MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(), 0, State.MOUSE_X, State.MOUSE_Y, 0, false));
        game.repaint();
        return true;
    }

    public static boolean released(Game game, KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.VK_P) return false;
        boolean consumed = down;
        down = false;
        return consumed;
    }

    static void focusLost() { down = false; }
    static void resetForTests() { down = false; latched = false; }
}
