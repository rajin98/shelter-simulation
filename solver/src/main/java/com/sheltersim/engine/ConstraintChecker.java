package com.sheltersim.engine;

import com.sheltersim.model.Orientation;

public final class ConstraintChecker {

    private ConstraintChecker() {}

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

    // R7: iterate only solid cells (via solidRelCells), not the full bounding box.
    public static boolean checkNoOverlap(Grid grid, Orientation o, int topR, int topC) {
        int[] rel = o.solidRelCells;
        for (int i = 0; i < rel.length; i += 2)
            if (!grid.isEmpty(topR + rel[i], topC + rel[i + 1]))
                return false;
        return true;
    }
}
