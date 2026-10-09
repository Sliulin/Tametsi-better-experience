package tametsimod;

import game.Game;
import io.MouseHandler;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;

public final class ModRuntime {
    private static AWTEventListener listener;
    private ModRuntime() {}

    public static void preflight() throws Exception {
        Game.class.getField("mh");
        MouseHandler.class.getField("game");
        MouseHandler.class.getField("G");
        graph.Node.class.getField("numMineNeighbors");
        game.GameLogic.class.getMethod("playerClick", graph.Node.class, Game.class);
    }

    public static void install() throws Exception {
        preflight();
        ModAgent.requirePatched();
        if (listener != null) return;
        listener = new AWTEventListener() {
            @Override public void eventDispatched(AWTEvent event) {
                // AWT notifies this listener before the original Game$1 dispatch.
                // No polling delay and no input listener removal are necessary.
                if (event instanceof MouseEvent && event.getSource() instanceof Game) {
                    attach((Game) event.getSource());
                }
                if (event.getID() == java.awt.event.WindowEvent.WINDOW_LOST_FOCUS
                        || event.getID() == java.awt.event.FocusEvent.FOCUS_LOST) NeighborToggle.focusLost();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK | AWTEvent.FOCUS_EVENT_MASK);
        System.out.println("Tametsi Mod v4: feature hooks installed");
    }

    static void attach(Game game) {
        MouseHandler original = Game.mh;
        if (original != null && original.game == game && !(original instanceof ChordMouseHandler)) {
            Game.mh = new ChordMouseHandler(original);
            System.out.println("Tametsi Mod: mouse handler attached");
        }
    }

    static boolean installed() { return listener != null; }
}
