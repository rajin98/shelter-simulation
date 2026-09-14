package com.sheltersim.model;

public final class Orientation {
    public final String id;
    public final boolean[][] mask;   // [rows][cols], true = solid
    public final char[][] cellMap;   // [rows][cols], CellLabel symbols
    public final int rows;
    public final int cols;

    // Flat relative solid-cell coordinates [dr0,dc0, dr1,dc1, ...].
    // Avoids iterating transparent cells in buffer and overlap checks.
    public final int[] solidRelCells;

    // Flat relative courtyard-cell coordinates [dr0,dc0, dr1,dc1, ...] for '3'-labeled cells.
    public final int[] courtyardRelCells;

    // Flat relative house-cell coordinates [dr0,dc0, ...] for cells labeled a–e.
    public final int[] houseRelCells;

    // Flat relative opening-cell coordinates [dr0,dc0, ...] for the special transparent
    // gap cells that must have ≤1 house-cell neighbor on the grid.
    public final int[] openingRelCells;

    public Orientation(String id, boolean[][] mask, char[][] cellMap) {
        this(id, mask, cellMap, new int[0]);
    }

    public Orientation(String id, boolean[][] mask, char[][] cellMap, int[] openingRelCells) {
        this.id             = id;
        this.mask           = mask;
        this.cellMap        = cellMap;
        this.rows           = mask.length;
        this.cols           = mask.length > 0 ? mask[0].length : 0;
        this.openingRelCells = openingRelCells;

        int solidCount = 0, courtyardCount = 0, houseCount = 0;
        for (int r = 0; r < this.rows; r++)
            for (int c = 0; c < this.cols; c++) {
                if (mask[r][c]) solidCount++;
                char ch = cellMap[r][c];
                if (ch == '3') courtyardCount++;
                if (ch == 'a' || ch == 'b' || ch == 'c' || ch == 'd' || ch == 'e') houseCount++;
            }

        solidRelCells     = new int[solidCount * 2];
        courtyardRelCells = new int[courtyardCount * 2];
        houseRelCells     = new int[houseCount * 2];
        int i = 0, j = 0, k = 0;
        for (int r = 0; r < this.rows; r++)
            for (int c = 0; c < this.cols; c++) {
                if (mask[r][c]) { solidRelCells[i++] = r; solidRelCells[i++] = c; }
                char ch = cellMap[r][c];
                if (ch == '3') { courtyardRelCells[j++] = r; courtyardRelCells[j++] = c; }
                if (ch == 'a' || ch == 'b' || ch == 'c' || ch == 'd' || ch == 'e') {
                    houseRelCells[k++] = r; houseRelCells[k++] = c;
                }
            }
    }
}
