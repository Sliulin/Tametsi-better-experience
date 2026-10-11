package tametsimod;

import game.State;
import puzzle.ColumnHint;
import puzzle.Puzzle;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/** Draw around the original text using its font, baseline, scale and rotation. */
public final class ColumnIndicators {
    private ColumnIndicators() {}

    public static void drawText(Graphics2D graphics, String text, float x, float y,
            Puzzle puzzle, ColumnHint hint) {
        graphics.drawString(text, x, y);
        if (!RuntimeOptions.enabled(Feature.COLUMNS) || !State.onLevel() || State.LOADING
                || State.DRAWING || State.CURRENTLY_DRAWING || State.SHOW_CANVAS
                || !ColumnChord.ready(hint, puzzle)) return;
        Rectangle2D bounds = graphics.getFontMetrics().getStringBounds(text, graphics);
        double radius = Math.hypot(bounds.getWidth(), bounds.getHeight()) / 2.0
                + Math.max(2.0, graphics.getFont().getSize2D() * 0.08);
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(draw.ColorPresets.AUX_HINT_COLOR);
            copy.setStroke(new BasicStroke(Math.max(1.0f, graphics.getFont().getSize2D() / 18f)));
            copy.draw(new Ellipse2D.Double(x + bounds.getCenterX() - radius,
                    y + bounds.getCenterY() - radius, radius * 2, radius * 2));
        } finally { copy.dispose(); }
    }
}
