package com.sheltersim.engine;

import com.sheltersim.model.PlacedObject;

public class Grid {
    public static final int ROWS = 21;
    public static final int COLS = 27;

    private final char[][] cells = new char[ROWS][COLS];

    public Grid() {
        for (int r = 0; r < ROWS; r++)
            java.util.Arrays.fill(cells[r], '0');
    }

    public void place(PlacedObject obj) {
        int rows = obj.orientation.rows;
        int cols = obj.orientation.cols;
        for (int dr = 0; dr < rows; dr++)
            for (int dc = 0; dc < cols; dc++) {
                char ch = obj.orientation.cellMap[dr][dc];
                if (ch != '0')
                    cells[obj.topLeftRow + dr][obj.topLeftCol + dc] = ch;
            }
    }

    public void remove(PlacedObject obj) {
        int rows = obj.orientation.rows;
        int cols = obj.orientation.cols;
        for (int dr = 0; dr < rows; dr++)
            for (int dc = 0; dc < cols; dc++)
                if (obj.orientation.cellMap[dr][dc] != '0')
                    cells[obj.topLeftRow + dr][obj.topLeftCol + dc] = '0';
    }

    public boolean isEmpty(int r, int c)  { return cells[r][c] == '0'; }
    public char    getLabel(int r, int c) { return cells[r][c]; }
    public boolean inBounds(int r, int c) { return r >= 0 && r < ROWS && c >= 0 && c < COLS; }

    public String toFlatString() {
        StringBuilder sb = new StringBuilder(ROWS * COLS);
        for (int r = 0; r < ROWS; r++)
            sb.append(cells[r]);
        return sb.toString();
    }
}
