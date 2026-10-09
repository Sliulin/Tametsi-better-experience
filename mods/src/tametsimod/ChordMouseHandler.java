package tametsimod;

import game.Game;
import game.GameLogic;
import game.State;
import graph.Node;
import io.Binding;
import io.MouseHandler;
import io.SoundHandler;
import java.awt.event.MouseEvent;
import util.Util;

/** Overrides only the number-cell reveal action; all other input stays original. */
public final class ChordMouseHandler extends MouseHandler {
    public ChordMouseHandler(MouseHandler original) {
        super(original.game, original.G);
    }

    private static boolean matches(Binding binding, MouseEvent event) {
        return binding != null && binding.matches(event);
    }

    boolean eligible(MouseEvent event) {
        if (!State.onLevel() || State.LOADING || State.LOST_GAME || State.WON_GAME
                || State.INACTIVE || State.INACTIVE_FRAMES > 0 || State.DRAWING
                || State.CURRENTLY_DRAWING || State.SHOW_CANVAS || G == null) return false;
        // Respect the original action priority and the existing reveal binding.
        if (matches(State.showCanvasBinding1, event) || matches(State.showCanvasBinding2, event)
                || matches(State.showNeighborsBinding1, event) || matches(State.showNeighborsBinding2, event)
                || matches(State.flagBinding1, event) || matches(State.flagBinding2, event)) return false;
        if (!matches(State.revealBinding1, event) && !matches(State.revealBinding2, event)) return false;
        if (event.getButton() == MouseEvent.BUTTON1) {
            int x = event.getX(), y = event.getY();
            if (x + y < 60 || Util.inSoundButton(x, y) || Util.inResetButton(x, y)
                    || (State.OPTION_ALLOW_DRAWING && Util.inDrawingButton(x, y))) return false;
        }
        return true;
    }

    @Override public void mousePressed(MouseEvent event) {
        if (eligible(event)) {
            Node clue = G.pointInNode(event.getX(), event.getY());
            if (clue == null && RuntimeOptions.enabled(Feature.COLUMNS)) {
                java.util.List<Node> targets = ColumnChord.targets(
                        ColumnChord.hit(game.puzzle, event.getX(), event.getY()), game.puzzle);
                if (!targets.isEmpty() && finishReveal(Chord.reveal(targets, game))) return;
            }
            if (RuntimeOptions.enabled(Feature.REVEAL) && clue != null && !Chord.targets(clue).isEmpty()) {
                Chord.Result result = Chord.reveal(clue, game);
                if (finishReveal(result)) return;
            }
            java.util.List<Node> flagTargets = Chord.flagTargets(clue);
            if (RuntimeOptions.enabled(Feature.FLAG) && !flagTargets.isEmpty()) {
                for (Node target : flagTargets) {
                    target.flagged = true;
                    G.numFlags++;
                    game.handleFlag(target.id);
                }
                if (GameLogic.checkWon(G)) {
                    game.handleWin();
                    SoundHandler.playWin();
                } else SoundHandler.playClick();
                game.repaint();
                return;
            }
        }
        super.mousePressed(event);
    }

    private boolean finishReveal(Chord.Result result) {
        if (!result.changed()) return false;
        if (!result.safeReveals.isEmpty()) game.handleReveal(result.safeReveals);
        if (result.lost) { SoundHandler.playLose(); game.handleLoss(); }
        else if (GameLogic.checkWon(G)) { SoundHandler.playWin(); game.handleWin(); }
        else SoundHandler.playClick();
        game.repaint();
        return true;
    }
}
