package com.sheltersim.engine;

import com.sheltersim.model.InvalidPlacementException;
import com.sheltersim.model.ObjectType;
import com.sheltersim.model.PlacedObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixedPlacementsTest {

    private static OrientationCache cache;

    @BeforeAll
    static void setup() {
        cache = new OrientationCache();
    }

    // --- Valid builds ---

    @Test
    void build_validBathroom_noException() {
        FixedPlacements fp = FixedPlacements.builder(cache)
                .bathroom("Portrait", 0, 0)
                .build();
        assertTrue(fp.bathroom.isPresent());
        assertTrue(fp.kitchen.isEmpty());
        assertTrue(fp.shelters.isEmpty());
    }

    @Test
    void build_validKitchen_noException() {
        FixedPlacements fp = FixedPlacements.builder(cache)
                .kitchen("Portrait", 0, 0)
                .build();
        assertTrue(fp.kitchen.isPresent());
    }

    @Test
    void build_bathroomAndKitchenFarEnough_noException() {
        // Bathroom Portrait at (0,0): solid cols 0-3
        // Kitchen Portrait at (0,7): solid cols 7-12
        // Nearest pair: (0,3)–(0,7) Chebyshev = 4 — both buffers satisfied
        assertDoesNotThrow(() ->
                FixedPlacements.builder(cache)
                        .bathroom("Portrait", 0, 0)
                        .kitchen("Portrait", 0, 7)
                        .build());
    }

    // --- R6 bounds ---

    @Test
    void build_outOfBounds_throwsR6() {
        // Bathroom Portrait is 8 rows tall; placing at row 20 → needs rows 20-27, out of bounds
        assertThrows(InvalidPlacementException.class, () ->
                FixedPlacements.builder(cache)
                        .bathroom("Portrait", 20, 0)
                        .build());
    }

    // --- R1 bathroom buffer ---

    @Test
    void build_bathroomKitchenTooClose_throwsR1() {
        // Bathroom Portrait at (0,0): nearest solid cell at col 3
        // Kitchen Portrait at (0,5): nearest solid cell at col 5
        // Chebyshev distance = 2 < 4 → R1 violation
        assertThrows(InvalidPlacementException.class, () ->
                FixedPlacements.builder(cache)
                        .bathroom("Portrait", 0, 0)
                        .kitchen("Portrait", 0, 5)
                        .build());
    }

    // --- R7 overlap ---

    @Test
    void build_shelterOverlapsBathroom_throwsR7() {
        // Shelter R0 at (0,0) has solid cells at (0,0),(0,1),(0,2) in its first row
        // Bathroom Portrait at (0,0) occupies rows 0-7, cols 0-3 — same cells
        assertThrows(InvalidPlacementException.class, () ->
                FixedPlacements.builder(cache)
                        .bathroom("Portrait", 0, 0)
                        .shelter("R0", 0, 0)
                        .build());
    }

    // --- Unknown orientation ID ---

    @Test
    void build_unknownOrientationId_throws() {
        assertThrows(InvalidPlacementException.class, () ->
                FixedPlacements.builder(cache)
                        .kitchen("BOGUS", 0, 10)
                        .build());
    }

    // --- Augmentation methods leave original unchanged ---

    @Test
    void withBathroom_leavesOriginalUnchanged() {
        FixedPlacements original = FixedPlacements.NONE;
        PlacedObject bath = new PlacedObject(ObjectType.BATHROOM,
                cache.getBathroomOrientations().get(0), 0, 0);
        FixedPlacements modified = original.withBathroom(bath);
        assertTrue(original.bathroom.isEmpty());
        assertTrue(modified.bathroom.isPresent());
    }

    @Test
    void withKitchen_leavesOriginalUnchanged() {
        FixedPlacements original = FixedPlacements.NONE;
        PlacedObject kitchen = new PlacedObject(ObjectType.KITCHEN,
                cache.getKitchenOrientations().get(0), 0, 0);
        FixedPlacements modified = original.withKitchen(kitchen);
        assertTrue(original.kitchen.isEmpty());
        assertTrue(modified.kitchen.isPresent());
    }

    @Test
    void withShelter_leavesOriginalUnchanged() {
        FixedPlacements original = FixedPlacements.NONE;
        PlacedObject shelter = new PlacedObject(ObjectType.SHELTER,
                cache.getShelterOrientations().get(0), 0, 0);
        FixedPlacements modified = original.withShelter(shelter);
        assertEquals(0, original.shelters.size());
        assertEquals(1, modified.shelters.size());
    }

    // --- Canonical key ordering ---

    @Test
    void sheltersAreSortedByCanonicalKey() {
        // Add shelters out-of-order: (0,18) first, then (0,0)
        // canonicalKey(0,18) = 18, canonicalKey(0,0) = 0 → after sort: (0,0) first
        // Both fit in bounds (11×9 shelter; (0,18) spans cols 18-26 = exactly col 26 max)
        FixedPlacements fp = FixedPlacements.builder(cache)
                .shelter("R0", 0, 18)
                .shelter("R0", 0, 0)
                .build();
        List<PlacedObject> shelters = fp.shelters;
        assertEquals(2, shelters.size());
        assertTrue(shelters.get(0).canonicalKey() < shelters.get(1).canonicalKey());
        assertEquals(0, shelters.get(0).canonicalKey());   // (0,0)
        assertEquals(18, shelters.get(1).canonicalKey());  // (0,18)
    }

    // --- NONE constant ---

    @Test
    void none_hasNoObjects() {
        assertTrue(FixedPlacements.NONE.bathroom.isEmpty());
        assertTrue(FixedPlacements.NONE.kitchen.isEmpty());
        assertTrue(FixedPlacements.NONE.shelters.isEmpty());
    }
}
