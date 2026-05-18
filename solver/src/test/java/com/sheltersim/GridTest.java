package com.sheltersim;

import com.sheltersim.engine.Grid;
import com.sheltersim.model.ObjectType;
import com.sheltersim.model.Orientation;
import com.sheltersim.model.PlacedObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    // 2×2 all-solid rectangle labelled 'B' — simplest possible fixture
    private static PlacedObject solidRect(int row, int col) {
        char[][] cellMap = {{'B','B'},{'B','B'}};
        boolean[][] mask = {{true,true},{true,true}};
        Orientation o = new Orientation("test", mask, cellMap);
        return new PlacedObject(ObjectType.BATHROOM, o, row, col);
    }

    // 2×2 with top-left transparent: 0 B / B B
    private static PlacedObject mixedRect(int row, int col) {
        char[][] cellMap = {{'0','B'},{'B','B'}};
        boolean[][] mask = {{false,true},{true,true}};
        Orientation o = new Orientation("mixed", mask, cellMap);
        return new PlacedObject(ObjectType.BATHROOM, o, row, col);
    }

    @Test
    void newGridIsAllEmpty() {
        Grid g = new Grid();
        for (int r = 0; r < Grid.ROWS; r++)
            for (int c = 0; c < Grid.COLS; c++)
                assertTrue(g.isEmpty(r, c), "Expected empty at (" + r + "," + c + ")");
    }

    @Test
    void placeWritesCells() {
        Grid g = new Grid();
        g.place(solidRect(0, 0));
        assertFalse(g.isEmpty(0, 0));
        assertFalse(g.isEmpty(0, 1));
        assertFalse(g.isEmpty(1, 0));
        assertFalse(g.isEmpty(1, 1));
        assertEquals('B', g.getLabel(0, 0));
    }

    @Test
    void placeSkipsTransparentCells() {
        Grid g = new Grid();
        g.place(mixedRect(0, 0));
        assertTrue(g.isEmpty(0, 0),  "Transparent cell should stay empty");
        assertFalse(g.isEmpty(0, 1));
        assertFalse(g.isEmpty(1, 0));
        assertFalse(g.isEmpty(1, 1));
    }

    @Test
    void placeRemoveRoundTrip() {
        Grid g = new Grid();
        String before = g.toFlatString();
        PlacedObject obj = solidRect(3, 5);
        g.place(obj);
        g.remove(obj);
        assertEquals(before, g.toFlatString(), "Grid should be restored after place+remove");
    }

    @Test
    void toFlatStringLength() {
        assertEquals(Grid.ROWS * Grid.COLS, new Grid().toFlatString().length());
        assertEquals(567, new Grid().toFlatString().length());
    }

    @Test
    void identicalPlacementsProduceSameFlatString() {
        Grid g1 = new Grid();
        Grid g2 = new Grid();
        PlacedObject obj = solidRect(2, 2);
        g1.place(obj);
        g2.place(obj);
        assertEquals(g1.toFlatString(), g2.toFlatString());
    }

    @Test
    void differentPlacementsProduceDifferentFlatString() {
        Grid g1 = new Grid();
        Grid g2 = new Grid();
        g1.place(solidRect(0, 0));
        g2.place(solidRect(5, 5));
        assertNotEquals(g1.toFlatString(), g2.toFlatString());
    }

    @Test
    void inBoundsBoundaryConditions() {
        Grid g = new Grid();
        assertTrue(g.inBounds(0, 0));
        assertTrue(g.inBounds(20, 26));
        assertTrue(g.inBounds(0, 26));
        assertTrue(g.inBounds(20, 0));
        assertFalse(g.inBounds(-1, 0));
        assertFalse(g.inBounds(0, -1));
        assertFalse(g.inBounds(21, 0));
        assertFalse(g.inBounds(0, 27));
    }

    @Test
    void isEmptyBoundaryConditions() {
        Grid g = new Grid();
        assertTrue(g.isEmpty(0, 0));
        assertTrue(g.isEmpty(20, 26));
        g.place(solidRect(19, 25)); // 2×2 at (19,25) occupies rows 19-20, cols 25-26
        assertFalse(g.isEmpty(19, 25));
        assertFalse(g.isEmpty(20, 26));
    }
}
