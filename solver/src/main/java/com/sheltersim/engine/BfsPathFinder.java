package com.sheltersim.engine;

public class BfsPathFinder {

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = { 0, 0, -1, 1};

    // Generation counter: avoids clearing the whole visited array before each BFS call.
    private final int[] visitedGen = new int[Grid.ROWS * Grid.COLS];
    private int currentGen = 0;

    // Flat boolean array marking cells orthogonally adjacent to target solid cells.
    private final boolean[] targetAdj = new boolean[Grid.ROWS * Grid.COLS];
    // Tracks which indices were set this call so we can clear them afterwards.
    private final int[] targetAdjSet = new int[Grid.ROWS * Grid.COLS];
    private int targetAdjCount = 0;

    // Primitive int queue — avoids Integer autoboxing on every enqueue.
    // Each cell is visited at most once per BFS call, so ROWS*COLS entries suffice.
    private final int[] queueBuf = new int[Grid.ROWS * Grid.COLS];
    private int qHead, qTail;

    /**
     * Returns the shortest path length (number of empty cells traversed) from
     * any empty cell adjacent to sourceSolids to any empty cell adjacent to
     * targetSolids, or -1 if no path exists.
     *
     * Returns 0 when a single empty cell is adjacent to both source and target
     * (i.e. source and target are separated by exactly one empty cell).
     */
    public int shortestPath(Grid grid, int[] sourceSolids, int[] targetSolids) {
        currentGen++;
        buildTargetAdjacency(grid, targetSolids);

        qHead = qTail = 0;

        // Seed: empty cells orthogonally adjacent to any source solid cell.
        for (int i = 0; i < sourceSolids.length; i += 2) {
            int sr = sourceSolids[i], sc = sourceSolids[i + 1];
            for (int d = 0; d < 4; d++) {
                int r = sr + DR[d], c = sc + DC[d];
                if (!grid.inBounds(r, c) || !grid.isEmpty(r, c)) continue;
                int idx = r * Grid.COLS + c;
                if (visitedGen[idx] == currentGen) continue;
                visitedGen[idx] = currentGen;
                // Seed is adjacent to target: path length 0 (no intermediate cells).
                if (targetAdj[idx]) {
                    clearTargetAdj();
                    return 0;
                }
                // Encode level 1 in the queue as (idx << 20 | level).
                // Max idx = 21*27-1 = 566, max level fits in 20 bits easily.
                queueBuf[qTail++] = idx << 20 | 1;
            }
        }

        while (qHead < qTail) {
            int entry = queueBuf[qHead++];
            int idx   = entry >>> 20;
            int level = entry & 0xFFFFF;
            int r = idx / Grid.COLS, c = idx % Grid.COLS;

            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (!grid.inBounds(nr, nc)) continue;
                int nidx = nr * Grid.COLS + nc;
                if (targetAdj[nidx]) {
                    clearTargetAdj();
                    return level + 1;
                }
                if (!grid.isEmpty(nr, nc)) continue;
                if (visitedGen[nidx] == currentGen) continue;
                visitedGen[nidx] = currentGen;
                queueBuf[qTail++] = nidx << 20 | (level + 1);
            }
        }

        clearTargetAdj();
        return -1;
    }

    private void buildTargetAdjacency(Grid grid, int[] targetSolids) {
        targetAdjCount = 0;
        for (int i = 0; i < targetSolids.length; i += 2) {
            int tr = targetSolids[i], tc = targetSolids[i + 1];
            for (int d = 0; d < 4; d++) {
                int r = tr + DR[d], c = tc + DC[d];
                if (!grid.inBounds(r, c) || !grid.isEmpty(r, c)) continue;
                int idx = r * Grid.COLS + c;
                if (!targetAdj[idx]) {
                    targetAdj[idx] = true;
                    targetAdjSet[targetAdjCount++] = idx;
                }
            }
        }
    }

    private void clearTargetAdj() {
        for (int i = 0; i < targetAdjCount; i++)
            targetAdj[targetAdjSet[i]] = false;
        targetAdjCount = 0;
    }
}
