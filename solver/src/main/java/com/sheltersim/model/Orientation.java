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

    public Orientation(String id, boolean[][] mask, char[][] cellMap) {
        this.id      = id;
        this.mask    = mask;
        this.cellMap = cellMap;
        this.rows    = mask.length;
        this.cols    = mask.length > 0 ? mask[0].length : 0;

        int count = 0;
        for (int r = 0; r < this.rows; r++)
            for (int c = 0; c < this.cols; c++)
                if (mask[r][c]) count++;

        solidRelCells = new int[count * 2];
        int i = 0;
        for (int r = 0; r < this.rows; r++)
            for (int c = 0; c < this.cols; c++)
                if (mask[r][c]) { solidRelCells[i++] = r; solidRelCells[i++] = c; }
    }
}
