package tametsimod;

import game.Game;
import game.GameLogic;
import game.State;
import graph.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Java adaptation of Board.Chord / Board.CanChord in TF0119/minesweeper.
 * Copyright (c) 2026 Takeru Fukuda. MIT License; see vendor/tf0119-minesweeper/LICENSE.
 * Upstream commit: d94bddadddf587e05017262ba1ab6e1924a69f83.
 * Neighbors use Tametsi's graph instead of a rectangular grid; reveals use its engine.
 */
public final class Chord {
    private Chord() {}

    public static List<Node> targets(Node clue) {
        return targets(clue, false);
    }

    /** Complementary local deduction: every remaining covered neighbor is a mine. */
    public static List<Node> flagTargets(Node clue) {
        return targets(clue, true);
    }

    private static List<Node> targets(Node clue, boolean flagAll) {
        if (clue == null || !clue.revealed || clue.flagged || clue.secret
                || clue.hasMine || clue.numMineNeighbors <= 0) {
            return Collections.emptyList();
        }
        return targets(clue.getNeighbors(), clue.numMineNeighbors, flagAll);
    }

    static List<Node> targets(Iterable<Node> neighbors, int number, boolean flagAll) {
        int flags = 0;
        List<Node> hidden = new ArrayList<Node>();
        for (Node neighbor : neighbors) {
            if (neighbor.flagged) flags++;
            else if (!neighbor.revealed) hidden.add(neighbor);
        }
        if ((flagAll ? flags + hidden.size() : flags) != number)
            return Collections.emptyList();
        // Upstream follows coordinate order. Stable node order is the graph equivalent.
        Collections.sort(hidden, (left, right) -> Integer.compare(left.id, right.id));
        return hidden;
    }

    /** The same flag-count chord, applied to an original hint's member IDs. */
    static List<Node> targetsByIds(puzzle.Puzzle puzzle, Iterable<Integer> ids, int number) {
        if (puzzle == null || puzzle.G == null || ids == null || number < 0)
            return Collections.emptyList();
        List<Node> members = new ArrayList<Node>();
        for (Integer id : ids) {
            if (id == null) return Collections.emptyList();
            Node node = puzzle.G.get(id);
            if (node == null) return Collections.emptyList();
            members.add(node);
        }
        return targets(members, number, false);
    }

    /** Normal reveal rules, including zero expansion and losing on incorrect flags. */
    public static Result reveal(Node clue, Game game) {
        return reveal(targets(clue), game);
    }

    static Result reveal(List<Node> targets, Game game) {
        Result result = new Result();
        if (State.LOST_GAME || State.WON_GAME) return result;
        for (Node target : targets) {
            if (target.revealed || target.flagged) continue;
            HashSet<Integer> revealed = GameLogic.playerClick(target, game);
            if (State.LOST_GAME) {
                result.lost = true;
                break;
            }
            if (revealed != null) result.safeReveals.addAll(revealed);
        }
        return result;
    }

    public static final class Result {
        public final HashSet<Integer> safeReveals = new HashSet<Integer>();
        public boolean lost;
        public boolean changed() { return lost || !safeReveals.isEmpty(); }
    }
}
