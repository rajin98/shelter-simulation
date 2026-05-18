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

    private static final boolean[][] SHELTER_EDGE = {
        {true,true,true,false,false,true,true,true,true},
        {true,false,true,true,true,true,false,false,true},
        {true,false,false,false,false,false,false,false,true},
        {true,false,false,false,false,false,false,false,true},
        {true,false,false,false,false,false,false,false,true},
        {true,true,false,false,false,false,false,false,true},
        {false,true,false,false,false,false,false,false,true},
        {false,true,false,false,false,true,true,true,true},
        {true,true,false,false,false,true,false,false,false},
        {true,false,false,false,false,true,false,false,false},
        {true,true,true,true,true,true,false,false,false},
    };


    private static final char[][] KITCHEN_BASE = {
        {'4','4','4','4'},
        {'4','4','4','4'},
        {'4','4','4','4'},
        {'4','4','4','4'},
        {'4','4','4','4'},
        {'5','5','5','5'},
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
    // Kitchen: R0 → R90 → R180 → R270 (mirroring produces duplicates)
    // -------------------------------------------------------------------------
    private List<Orientation> buildKitchenOrientations() {
        Orientation r0   = base("R0",   KITCHEN_BASE);
        Orientation r90  = rotate90(r0,  "R90");
        Orientation r180 = rotate90(r90, "R180");
        Orientation r270 = rotate90(r180,"R270");
        return List.of(r0, r90, r180, r270);
    }

    // -------------------------------------------------------------------------
    // Bathroom: Portrait (8×4), Landscape (4×8) — all cells solid 'B'
    // -------------------------------------------------------------------------
    private List<Orientation> buildBathroomOrientations() {
        Orientation portrait  = solidRect("Portrait",  8, 4);
        Orientation landscape = solidRect("Landscape", 4, 8);
        return List.of(portrait, landscape);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Orientation base(String id, char[][] src) {
        int rows = src.length;
        int cols = src[0].length;
        char[][] cellMap = new char[rows][cols];
        for (int r = 0; r < rows; r++)
            cellMap[r] = src[r].clone();
        return new Orientation(id, extractMask(cellMap), cellMap);
    }

    // 90° clockwise: result[c][rows-1-r] = src[r][c]  →  result is cols×rows
    private Orientation rotate90(Orientation o, String newId) {
        int rows = o.rows, cols = o.cols;
        char[][] cellMap = new char[cols][rows];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cellMap[c][rows - 1 - r] = o.cellMap[r][c];
        return new Orientation(newId, extractMask(cellMap), cellMap);
    }

    // Horizontal mirror: result[r][cols-1-c] = src[r][c]
    private Orientation mirror(Orientation o, String newId) {
        int rows = o.rows, cols = o.cols;
        char[][] cellMap = new char[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                cellMap[r][cols - 1 - c] = o.cellMap[r][c];
        return new Orientation(newId, extractMask(cellMap), cellMap);
    }

    private Orientation solidRect(String id, int rows, int cols) {
        char[][] cellMap = new char[rows][cols];
        boolean[][] mask = new boolean[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) {
                cellMap[r][c] = 'B';
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
