package com.sheltersim;

import com.sheltersim.engine.OrientationCache;
import com.sheltersim.model.Orientation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrientationCacheTest {

    private static OrientationCache cache;

    @BeforeAll
    static void setup() {
        cache = new OrientationCache();
    }

    // --- Shelter ---

    @Test
    void shelterHasEightOrientations() {
        assertEquals(8, cache.getShelterOrientations().size());
    }

    @Test
    void shelterR0Dimensions() {
        Orientation r0 = cache.getShelterOrientations().get(0);
        assertEquals("R0", r0.id);
        assertEquals(11, r0.rows);
        assertEquals(9,  r0.cols);
    }

    @Test
    void shelterR90Dimensions() {
        Orientation r90 = cache.getShelterOrientations().get(1);
        assertEquals("R90", r90.id);
        assertEquals(9,  r90.rows);
        assertEquals(11, r90.cols);
    }

    @Test
    void shelterSolidCountPreservedAcrossAllOrientations() {
        int expected = solidCount(cache.getShelterOrientations().get(0));
        for (Orientation o : cache.getShelterOrientations())
            assertEquals(expected, solidCount(o), "Solid count mismatch in " + o.id);
    }

    @Test
    void shelterOrientationIdsAreDistinct() {
        List<Orientation> list = cache.getShelterOrientations();
        long distinct = list.stream().map(o -> o.id).distinct().count();
        assertEquals(8, distinct);
    }

    // --- Kitchen ---

    @Test
    void kitchenHasFourOrientations() {
        assertEquals(4, cache.getKitchenOrientations().size());
    }

    @Test
    void kitchenR0Dimensions() {
        Orientation r0 = cache.getKitchenOrientations().get(0);
        assertEquals("R0", r0.id);
        assertEquals(6, r0.rows);
        assertEquals(4, r0.cols);
    }

    @Test
    void kitchenR90Dimensions() {
        Orientation r90 = cache.getKitchenOrientations().get(1);
        assertEquals("R90", r90.id);
        assertEquals(4, r90.rows);
        assertEquals(6, r90.cols);
    }

    @Test
    void kitchenSolidCountPreservedAcrossAllOrientations() {
        int expected = solidCount(cache.getKitchenOrientations().get(0));
        assertEquals(24, expected); // all cells solid in a 6×4 grid
        for (Orientation o : cache.getKitchenOrientations())
            assertEquals(expected, solidCount(o), "Solid count mismatch in " + o.id);
    }

    // --- Bathroom ---

    @Test
    void bathroomHasTwoOrientations() {
        assertEquals(2, cache.getBathroomOrientations().size());
    }

    @Test
    void bathroomPortraitDimensions() {
        Orientation portrait = cache.getBathroomOrientations().get(0);
        assertEquals("Portrait", portrait.id);
        assertEquals(8, portrait.rows);
        assertEquals(4, portrait.cols);
    }

    @Test
    void bathroomLandscapeDimensions() {
        Orientation landscape = cache.getBathroomOrientations().get(1);
        assertEquals("Landscape", landscape.id);
        assertEquals(4, landscape.rows);
        assertEquals(8, landscape.cols);
    }

    @Test
    void bathroomAllCellsSolid() {
        for (Orientation o : cache.getBathroomOrientations()) {
            int total = o.rows * o.cols;
            assertEquals(total, solidCount(o), "Not all cells solid in " + o.id);
        }
    }

    // --- Mask/cellMap consistency ---

    @Test
    void maskMatchesCellMapSolidnessForAllOrientations() {
        for (Orientation o : cache.getShelterOrientations())  assertMaskConsistent(o);
        for (Orientation o : cache.getKitchenOrientations())  assertMaskConsistent(o);
        for (Orientation o : cache.getBathroomOrientations()) assertMaskConsistent(o);
    }

    // -------------------------------------------------------------------------

    private int solidCount(Orientation o) {
        int count = 0;
        for (int r = 0; r < o.rows; r++)
            for (int c = 0; c < o.cols; c++)
                if (o.mask[r][c]) count++;
        return count;
    }

    private void assertMaskConsistent(Orientation o) {
        for (int r = 0; r < o.rows; r++)
            for (int c = 0; c < o.cols; c++) {
                boolean expectedSolid = o.cellMap[r][c] != '0';
                assertEquals(expectedSolid, o.mask[r][c],
                    String.format("Mask/cellMap mismatch in %s at [%d][%d]", o.id, r, c));
            }
    }
}
