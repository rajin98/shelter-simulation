package com.sheltersim.engine;

import com.sheltersim.model.Orientation;
import com.sheltersim.model.PlacedObject;

public final class ConstraintChecker {

    private ConstraintChecker() {}

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private static boolean isHouseCell(char c) {
        return c == 'a' || c == 'b' || c == 'c' || c == 'd' || c == 'e';
    }

    // R6: the bounding box fits when topLeft + dimensions stays within the grid.
    public static boolean checkBounds(Orientation o, int topR, int topC) {
        return topR >= 0 && topC >= 0
                && topR + o.rows <= Grid.ROWS
                && topC + o.cols <= Grid.COLS;
    }

    // R1/R2: direct Chebyshev — used only by FixedPlacements.build().
    // The hot search path uses BufferZone.violates() instead.
    public static boolean checkBuffer(int[] candidateSolids, int[] bufferedSolids, int dist) {
        for (int i = 0; i < candidateSolids.length; i += 2) {
            int cr = candidateSolids[i], cc = candidateSolids[i + 1];
            for (int j = 0; j < bufferedSolids.length; j += 2) {
                int br = bufferedSolids[j], bc = bufferedSolids[j + 1];
                if (Math.max(Math.abs(cr - br), Math.abs(cc - bc)) < dist)
                    return false;
            }
        }
        return true;
    }

    // R5: every courtyard ('3') cell must be > 3 cells from every grid edge.
    public static boolean checkCourtyardBorder(Orientation o, int topR, int topC) {
        int[] rel = o.courtyardRelCells;
        for (int i = 0; i < rel.length; i += 2) {
            int r = topR + rel[i], c = topC + rel[i + 1];
            if (r <= 2 || r >= Grid.ROWS - 3 || c <= 2 || c >= Grid.COLS - 3)
                return false;
        }
        return true;
    }

    // R7: iterate only solid cells (via solidRelCells), not the full bounding box.
    public static boolean checkNoOverlap(Grid grid, Orientation o, int topR, int topC) {
        int[] rel = o.solidRelCells;
        for (int i = 0; i < rel.length; i += 2)
            if (!grid.isEmpty(topR + rel[i], topC + rel[i + 1]))
                return false;
        return true;
    }

    // R8: each shelter opening cell must have ≤1 house-cell (a–e) neighbor on the grid.
    // Checks both directions: the candidate's own openings vs the current grid, and
    // prior shelters' openings vs the candidate's house cells.
    public static boolean checkOpeningNeighbors(Grid grid, Orientation newO, int topR, int topC,
                                                PlacedObject[] priorShelters, int priorCount) {
        // Part 1: candidate's opening cells vs house cells already on the grid.
        int[] opening = newO.openingRelCells;
        for (int i = 0; i < opening.length; i += 2) {
            int r = topR + opening[i], c = topC + opening[i + 1];
            int count = 0;
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (grid.inBounds(nr, nc) && isHouseCell(grid.getLabel(nr, nc)))
                    if (++count > 1) return false;
            }
        }
        // Part 2: prior shelters' opening cells vs house cells the candidate would add.
        int[] newHouse = newO.houseRelCells;
        for (int k = 0; k < priorCount; k++) {
            PlacedObject prior = priorShelters[k];
            int[] priorOpening = prior.orientation.openingRelCells;
            for (int i = 0; i < priorOpening.length; i += 2) {
                int opR = prior.topLeftRow + priorOpening[i];
                int opC = prior.topLeftCol + priorOpening[i + 1];
                int count = 0;
                for (int d = 0; d < 4; d++) {
                    int nr = opR + DR[d], nc = opC + DC[d];
                    if (grid.inBounds(nr, nc) && isHouseCell(grid.getLabel(nr, nc)))
                        count++;
                }
                for (int j = 0; j < newHouse.length; j += 2) {
                    int hR = topR + newHouse[j], hC = topC + newHouse[j + 1];
                    if (Math.abs(hR - opR) + Math.abs(hC - opC) == 1)
                        count++;
                }
                if (count > 1) return false;
            }
        }
        return true;
    }
}
