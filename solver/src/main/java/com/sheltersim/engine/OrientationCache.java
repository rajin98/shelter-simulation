package com.sheltersim.engine;

import com.sheltersim.model.Orientation;

import java.util.List;

public class OrientationCache {

    private static final char[][] SHELTER_BASE = {
        {'a','a','a','0','0','0','b','b','b'},
        {'a','a','a','2','2','2','b','b','b'},
        {'a','a','a','2','2','2','b','b','b'},
        {'c','c','c','2','3','3','3','3','3'},
        {'c','c','c','2','3','3','3','3','3'},
        {'c','c','c','2','3','3','3','3','3'},
        {'0','2','2','2','3','3','3','3','3'},
        {'0','2','2','2','3','3','3','3','3'},
        {'d','d','d','e','e','e','0','0','0'},
        {'d','d','d','e','e','e','0','0','0'},
        {'d','d','d','e','e','e','0','0','0'},
    };


    private final List<Orientation> shelterOrientations;
    private final List<Orientation> kitchenOrientations;
    private final List<Orientation> bathroomOrientations;

    public OrientationCache() {
        shelterOrientations = buildShelterOrientations();
        kitchenOrientations = buildKitchenOrientations();
        bathroomOrientations = buildBathroomOrientations();
    }

    public List<Orientation> getShelterOrientations()  { return shelterOrientations; }
    public List<Orientation> getKitchenOrientations()  { return kitchenOrientations; }
    public List<Orientation> getBathroomOrientations() { return bathroomOrientations; }

    // -------------------------------------------------------------------------
    // Shelter: R0 → R90 → R180 → R270; R0 → mirror → R0M → R90M → R180M → R270M
    // -------------------------------------------------------------------------
    private List<Orientation> buildShelterOrientations() {
        Orientation r0   = base("R0",   SHELTER_BASE);
        Orientation r90  = rotate90(r0,  "R90");
        Orientation r180 = rotate90(r90, "R180");
        Orientation r270 = rotate90(r180,"R270");
        Orientation r0m  = mirror(r0,    "R0M");
        Orientation r90m = rotate90(r0m, "R90M");
        Orientation r180m= rotate90(r90m,"R180M");
        Orientation r270m= rotate90(r180m,"R270M");
        return List.of(r0, r90, r180, r270, r0m, r90m, r180m, r270m);
    }

    // -------------------------------------------------------------------------
    // Kitchen: Portrait (4×6), Landscape (6×4) — all cells solid '4'
    // -------------------------------------------------------------------------
    private List<Orientation> buildKitchenOrientations() {
        Orientation portrait  = solidRect("Portrait",  4, 6, '4');
        Orientation landscape = solidRect("Landscape", 6, 4, '4');
        return List.of(portrait, landscape);
    }

    // -------------------------------------------------------------------------
    // Bathroom: Portrait (8×4), Landscape (4×8) — all cells solid 'B'
    // -------------------------------------------------------------------------
    private List<Orientation> buildBathroomOrientations() {
        Orientation portrait  = solidRect("Portrait",  8, 4, 'B');
        Orientation landscape = solidRect("Landscape", 4, 8, 'B');
        return List.of(portrait, landscape);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // Opening cells in SHELTER_BASE: the transparent gaps whose grid neighbors
    // must contain at most one house cell (a–e) for the placement to be valid.
    private static final int[] SHELTER_OPENING_BASE = {
        0,3, 0,4, 0,5,  // top-middle gap between room-a and room-b
        6,0, 7,0        // left-middle gap below room-c
    };

    private Orientation base(String id, char[][] src) {
        int rows = src.length;
        int cols = src[0].length;
        char[][] cellMap = new char[rows][cols];
        for (int r = 0; r < rows; r++)
            cellMap[r] = src[r].clone();
        return new Orientation(id, extractMask(cellMap), cellMap, SHELTER_OPENING_BASE.clone());
    }

    // 90° clockwise: result[c][rows-1-r] = src[r][c]  →  result is cols×rows
    // Opening cell (r,c) maps to (c, rows-1-r) in the rotated orientation.
    private Orientation rotate90(Orientation o, String newId) {
        int rows = o.rows, cols = o.cols;
        char[][] cellMap = new char[cols][rows];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cellMap[c][rows - 1 - r] = o.cellMap[r][c];
        int[] src = o.openingRelCells;
        int[] newOpening = new int[src.length];
        for (int i = 0; i < src.length; i += 2) {
            newOpening[i]     = src[i + 1];           // newR = c
            newOpening[i + 1] = rows - 1 - src[i];    // newC = rows-1-r
        }
        return new Orientation(newId, extractMask(cellMap), cellMap, newOpening);
    }

    // Horizontal mirror: result[r][cols-1-c] = src[r][c]
    // Opening cell (r,c) maps to (r, cols-1-c) in the mirrored orientation.
    private Orientation mirror(Orientation o, String newId) {
        int rows = o.rows, cols = o.cols;
        char[][] cellMap = new char[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cellMap[r][cols - 1 - c] = o.cellMap[r][c];
        int[] src = o.openingRelCells;
        int[] newOpening = new int[src.length];
        for (int i = 0; i < src.length; i += 2) {
            newOpening[i]     = src[i];                // r unchanged
            newOpening[i + 1] = cols - 1 - src[i + 1]; // newC = cols-1-c
        }
        return new Orientation(newId, extractMask(cellMap), cellMap, newOpening);
    }

    private Orientation solidRect(String id, int rows, int cols, char label) {
        char[][] cellMap = new char[rows][cols];
        boolean[][] mask = new boolean[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                cellMap[r][c] = label;
                mask[r][c] = true;
            }
        return new Orientation(id, mask, cellMap);
    }

    private boolean[][] extractMask(char[][] cellMap) {
        int rows = cellMap.length, cols = cellMap[0].length;
        boolean[][] mask = new boolean[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                mask[r][c] = cellMap[r][c] != '0';
        return mask;
    }
}
