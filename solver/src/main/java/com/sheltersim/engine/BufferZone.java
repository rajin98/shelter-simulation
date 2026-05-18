package com.sheltersim.engine;

import com.sheltersim.model.Orientation;

public class BufferZone {

    private final boolean[][] exclusionZone = new boolean[Grid.ROWS][Grid.COLS];
    private final int[] sourceSolids;
    private final int bufferDist;

    private BufferZone(int[] sourceSolids, int bufferDist) {
        this.sourceSolids = sourceSolids;
        this.bufferDist = bufferDist;
        // Mark all in-bounds cells at Chebyshev distance < bufferDist from each source cell.
        // Chebyshev ball of radius (bufferDist-1) is a square: |dr| <= d, |dc| <= d.
        int d = bufferDist - 1;
        for (int i = 0; i < sourceSolids.length; i += 2) {
            int sr = sourceSolids[i], sc = sourceSolids[i + 1];
            for (int dr = -d; dr <= d; dr++) {
                int r = sr + dr;
                if (r < 0 || r >= Grid.ROWS) continue;
                for (int dc = -d; dc <= d; dc++) {
                    int c = sc + dc;
                    if (c >= 0 && c < Grid.COLS)
                        exclusionZone[r][c] = true;
                }
            }
        }
    }

    public static BufferZone build(int[] sourceSolids, int bufferDist) {
        return new BufferZone(sourceSolids, bufferDist);
    }

    public boolean isExcluded(int r, int c) {
        return exclusionZone[r][c];
    }

    // Returns true if any candidate solid cell violates this buffer.
    // Near-edge candidates additionally get a direct Chebyshev check to enforce
    // the rule that the virtual buffer extends beyond grid bounds.
    public boolean violates(int[] candidateSolids) {
        for (int i = 0; i < candidateSolids.length; i += 2) {
            int r = candidateSolids[i], c = candidateSolids[i + 1];
            if (checkCell(r, c)) return true;
        }
        return false;
    }

    // Orientation-aware overload: checks directly from relative solid offsets + topLeft,
    // avoiding PlacedObject allocation in the hot search loop.
    public boolean violates(Orientation o, int topR, int topC) {
        int[] rel = o.solidRelCells;
        for (int i = 0; i < rel.length; i += 2) {
            if (checkCell(topR + rel[i], topC + rel[i + 1])) return true;
        }
        return false;
    }

    private boolean checkCell(int r, int c) {
        if (exclusionZone[r][c]) return true;
        if (r < bufferDist || r >= Grid.ROWS - bufferDist ||
            c < bufferDist || c >= Grid.COLS - bufferDist) {
            for (int j = 0; j < sourceSolids.length; j += 2) {
                int sr = sourceSolids[j], sc = sourceSolids[j + 1];
                if (Math.max(Math.abs(r - sr), Math.abs(c - sc)) < bufferDist)
                    return true;
            }
        }
        return false;
    }
}
