package com.sheltersim.model;

import com.sheltersim.engine.Grid;

public final class PlacedObject {
    public final ObjectType type;
    public final Orientation orientation;
    public final int topLeftRow;
    public final int topLeftCol;

    // Flat interleaved [r0, c0, r1, c1, ...] arrays derived from cellMap at construction time.
    public final int[] solidCells;
    public final int[] courtyard;   // '3' cells; empty array for non-Shelter types
    public final int[] kitchenRoom; // '4' cells; empty array for non-Kitchen types

    public PlacedObject(ObjectType type, Orientation orientation, int topLeftRow, int topLeftCol) {
        this.type = type;
        this.orientation = orientation;
        this.topLeftRow = topLeftRow;
        this.topLeftCol = topLeftCol;

        int rows = orientation.rows;
        int cols = orientation.cols;

        // Count cells first so we can allocate exact-size arrays.
        int solidCount = 0, courtyardCount = 0, kitchenRoomCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = orientation.cellMap[r][c];
                if (ch != '0') solidCount++;
                if (ch == '3') courtyardCount++;
                if (ch == '4') kitchenRoomCount++;
            }
        }

        solidCells = new int[solidCount * 2];
        courtyard = new int[courtyardCount * 2];
        kitchenRoom = new int[kitchenRoomCount * 2];

        int si = 0, ci = 0, ki = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = orientation.cellMap[r][c];
                if (ch == '0') continue;
                int gr = topLeftRow + r;
                int gc = topLeftCol + c;
                solidCells[si++] = gr;
                solidCells[si++] = gc;
                if (ch == '3') { courtyard[ci++] = gr; courtyard[ci++] = gc; }
                if (ch == '4') { kitchenRoom[ki++] = gr; kitchenRoom[ki++] = gc; }
            }
        }
    }

    public int canonicalKey() {
        return topLeftRow * Grid.COLS + topLeftCol;
    }
}
