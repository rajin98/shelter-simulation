package com.sheltersim;

import com.sheltersim.engine.*;
import com.sheltersim.model.ObjectType;
import com.sheltersim.model.Orientation;
import com.sheltersim.model.PlacedObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintEngineTest {

    // --- Helpers ---

    private static Orientation solidRect(int rows, int cols) {
        char[][] cellMap = new char[rows][cols];
        boolean[][] mask = new boolean[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) { cellMap[r][c] = 'B'; mask[r][c] = true; }
        return new Orientation("test", mask, cellMap);
    }

    private static PlacedObject placed(int row, int col, int rows, int cols) {
        return new PlacedObject(ObjectType.BATHROOM, solidRect(rows, cols), row, col);
    }

    // --- ConstraintChecker.checkBounds ---

    @Test
    void checkBounds_fitsAtOrigin() {
        assertTrue(ConstraintChecker.checkBounds(solidRect(1, 1), 0, 0));
    }

    @Test
    void checkBounds_fitsAtBottomRight() {
        assertTrue(ConstraintChecker.checkBounds(solidRect(1, 1), 20, 26));
    }

    @Test
    void checkBounds_overflowsRow() {
        assertFalse(ConstraintChecker.checkBounds(solidRect(2, 1), 20, 0));
    }

    @Test
    void checkBounds_overflowsCol() {
        assertFalse(ConstraintChecker.checkBounds(solidRect(1, 2), 0, 26));
    }

    @Test
    void checkBounds_negativeRow() {
        assertFalse(ConstraintChecker.checkBounds(solidRect(1, 1), -1, 0));
    }

    // --- ConstraintChecker.checkBuffer ---

    @Test
    void checkBuffer_exactlyAtDist_passes() {
        // Two cells at Chebyshev distance exactly 3: should pass for dist=3
        int[] a = {0, 0};
        int[] b = {0, 3};
        assertTrue(ConstraintChecker.checkBuffer(a, b, 3));
    }

    @Test
    void checkBuffer_oneLess_fails() {
        // Chebyshev distance 2 with dist=3: violation
        int[] a = {0, 0};
        int[] b = {0, 2};
        assertFalse(ConstraintChecker.checkBuffer(a, b, 3));
    }

    @Test
    void checkBuffer_diagonal_exactlyAtDist_passes() {
        // Chebyshev distance = max(3,3) = 3 with dist=3
        int[] a = {0, 0};
        int[] b = {3, 3};
        assertTrue(ConstraintChecker.checkBuffer(a, b, 3));
    }

    @Test
    void checkBuffer_nearEdge_colZero() {
        // Bathroom at col 0 (row 5); Kitchen solid at col 2 (row 5): Chebyshev=2 < 3, fail
        int[] bathroom = {5, 0};
        int[] kitchen  = {5, 2};
        assertFalse(ConstraintChecker.checkBuffer(kitchen, bathroom, 3));
    }

    @Test
    void checkBuffer_nearEdge_colZero_distanceSufficient() {
        // Bathroom at col 0 (row 5); Kitchen solid at col 3 (row 5): Chebyshev=3 >= 3, pass
        int[] bathroom = {5, 0};
        int[] kitchen  = {5, 3};
        assertTrue(ConstraintChecker.checkBuffer(kitchen, bathroom, 3));
    }

    // --- ConstraintChecker.checkNoOverlap ---

    @Test
    void checkNoOverlap_emptyGrid_passes() {
        Grid g = new Grid();
        assertTrue(ConstraintChecker.checkNoOverlap(g, solidRect(2, 2), 0, 0));
    }

    @Test
    void checkNoOverlap_occupied_fails() {
        Grid g = new Grid();
        g.place(placed(0, 0, 2, 2));
        assertFalse(ConstraintChecker.checkNoOverlap(g, solidRect(2, 2), 0, 0));
    }

    @Test
    void checkNoOverlap_adjacentNoOverlap_passes() {
        Grid g = new Grid();
        g.place(placed(0, 0, 2, 2));
        assertTrue(ConstraintChecker.checkNoOverlap(g, solidRect(2, 2), 0, 2));
    }

    // --- BufferZone ---

    @Test
    void bufferZone_excludesNearbyCell() {
        int[] source = {5, 5};
        BufferZone bz = BufferZone.build(source, 3);
        assertTrue(bz.isExcluded(5, 7));  // Chebyshev distance 2 < 3
    }

    @Test
    void bufferZone_doesNotExcludeDistantCell() {
        int[] source = {5, 5};
        BufferZone bz = BufferZone.build(source, 3);
        assertFalse(bz.isExcluded(5, 8)); // Chebyshev distance 3, not excluded
    }

    @Test
    void bufferZone_violates_nearCell() {
        int[] source = {5, 5};
        BufferZone bz = BufferZone.build(source, 3);
        int[] candidate = {5, 7}; // distance 2 < 3
        assertTrue(bz.violates(candidate));
    }

    @Test
    void bufferZone_violates_exactlyAtDist_noViolation() {
        int[] source = {5, 5};
        BufferZone bz = BufferZone.build(source, 3);
        int[] candidate = {5, 8}; // distance 3 >= 3
        assertFalse(bz.violates(candidate));
    }

    @Test
    void bufferZone_nearEdge_violates() {
        // Bathroom at col 0; candidate at col 2 row 0: Chebyshev=2 < 3
        int[] source    = {0, 0};
        int[] candidate = {0, 2};
        BufferZone bz = BufferZone.build(source, 3);
        assertTrue(bz.violates(candidate));
    }

    @Test
    void bufferZone_nearEdge_sufficient() {
        int[] source    = {0, 0};
        int[] candidate = {0, 3};
        BufferZone bz = BufferZone.build(source, 3);
        assertFalse(bz.violates(candidate));
    }

    // --- BfsPathFinder ---

    @Test
    void bfs_directlyAdjacent_returnsZero() {
        // Source solid at (0,0); target solid at (0,2); single empty cell (0,1) between them.
        Grid g = new Grid();
        int[] src = {0, 0};
        int[] tgt = {0, 2};
        // Place solid sentinels so BFS sees them as occupied
        g.place(placed(0, 0, 1, 1));
        g.place(placed(0, 2, 1, 1));
        // (0,1) is empty — adjacent to both
        BfsPathFinder bfs = new BfsPathFinder();
        assertEquals(0, bfs.shortestPath(g, src, tgt));
    }

    @Test
    void bfs_fiveCellCorridor() {
        // Source solid row=0,col=0; empty cells cols 1-5; target solid col=6, all row 0.
        Grid g = new Grid();
        g.place(placed(0, 0, 1, 1)); // source solid
        g.place(placed(0, 6, 1, 1)); // target solid
        // cells (0,1)..(0,5) are empty
        int[] src = {0, 0};
        int[] tgt = {0, 6};
        BfsPathFinder bfs = new BfsPathFinder();
        assertEquals(5, bfs.shortestPath(g, src, tgt));
    }

    @Test
    void bfs_blockedCorridor_returnsMinusOne() {
        Grid g = new Grid();
        // Solid wall across entire row 1 from col 0 to col 26
        for (int c = 0; c < Grid.COLS; c++)
            g.place(placed(1, c, 1, 1));
        // Source top half (row 0), target bottom half (row 2) — no path through row 1
        int[] src = {0, 0};
        int[] tgt = {2, 0};
        BfsPathFinder bfs = new BfsPathFinder();
        assertEquals(-1, bfs.shortestPath(g, src, tgt));
    }

    @Test
    void bfs_consecutiveCallsAreIndependent() {
        Grid g = new Grid();
        g.place(placed(0, 0, 1, 1));
        g.place(placed(0, 2, 1, 1));
        BfsPathFinder bfs = new BfsPathFinder();
        int[] src = {0, 0};
        int[] tgt = {0, 2};
        assertEquals(0, bfs.shortestPath(g, src, tgt));
        assertEquals(0, bfs.shortestPath(g, src, tgt)); // second call same result
    }
}
