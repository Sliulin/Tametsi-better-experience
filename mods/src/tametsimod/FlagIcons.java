package tametsimod;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import graph.Node;
import puzzle.Puzzle;

/** Twemoji 14.0.2 triangular flag, CC-BY-4.0. See THIRD_PARTY.md. */
public final class FlagIcons {
    private static final BufferedImage flag = load();
    private FlagIcons() {}
    private static BufferedImage load() {
        try (java.io.InputStream input = FlagIcons.class.getResourceAsStream("/tametsimod/flag.png")) {
            BufferedImage result = javax.imageio.ImageIO.read(input);
            if (result == null) throw new IllegalStateException("旗标图像无效。");
            return result;
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    static void preflight() { if (flag.getWidth() <= 0) throw new IllegalStateException("旗标图像缺失。"); }
    static int size(Node node) {
        return Math.max(1, (int) Math.floor(Math.min(node.poly.getWidth(), node.poly.getHeight()) * 0.48));
    }
    public static void draw(Graphics2D graphics, Puzzle puzzle) {
        if (!RuntimeOptions.enabled(Feature.FLAG_ICON) || !game.State.onLevel()) return;
        for (Node node : puzzle.G.V.values()) {
            if (!node.flagged || node.revealed || node.poly == null) continue;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                int size = size(node);
                draw.Point center = node.poly.getCenter();
                int x = (int) Math.round(node.pos.x + center.x - size / 2.0);
                int y = (int) Math.round(node.pos.y + center.y - size / 2.0);
                g.clip(node.poly.toPath2D(node.pos.x, node.pos.y));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(flag, x, y, size, size, null);
            } finally { g.dispose(); }
        }
    }
}
