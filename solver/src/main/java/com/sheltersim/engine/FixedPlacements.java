package com.sheltersim.engine;

import com.sheltersim.model.InvalidPlacementException;
import com.sheltersim.model.ObjectType;
import com.sheltersim.model.Orientation;
import com.sheltersim.model.PlacedObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class FixedPlacements {

    public final Optional<PlacedObject> bathroom;
    public final Optional<PlacedObject> kitchen;
    public final List<PlacedObject> shelters; // unmodifiable, sorted by canonicalKey

    public static final FixedPlacements NONE = new FixedPlacements(
            Optional.empty(), Optional.empty(), List.of());

    private FixedPlacements(Optional<PlacedObject> bathroom,
                             Optional<PlacedObject> kitchen,
                             List<PlacedObject> shelters) {
        this.bathroom = bathroom;
        this.kitchen  = kitchen;
        this.shelters = shelters;
    }

    // Used by ParallelSolver to partition work across threads.
    FixedPlacements withBathroom(PlacedObject p) {
        return new FixedPlacements(Optional.of(p), kitchen, shelters);
    }

    FixedPlacements withKitchen(PlacedObject p) {
        return new FixedPlacements(bathroom, Optional.of(p), shelters);
    }

    FixedPlacements withShelter(PlacedObject p) {
        List<PlacedObject> next = new ArrayList<>(shelters);
        next.add(p);
        next.sort(Comparator.comparingInt(PlacedObject::canonicalKey));
        return new FixedPlacements(bathroom, kitchen, Collections.unmodifiableList(next));
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(OrientationCache cache) {
        return new Builder(cache);
    }

    public static final class Builder {

        private final OrientationCache cache;
        private PlacedObject bathroom;
        private PlacedObject kitchen;
        private final List<PlacedObject> shelters = new ArrayList<>();

        private Builder(OrientationCache cache) {
            this.cache = cache;
        }

        public Builder bathroom(String orientId, int row, int col) {
            Orientation o = find(cache.getBathroomOrientations(), orientId, "bathroom");
            bathroom = new PlacedObject(ObjectType.BATHROOM, o, row, col);
            return this;
        }

        public Builder kitchen(String orientId, int row, int col) {
            Orientation o = find(cache.getKitchenOrientations(), orientId, "kitchen");
            kitchen = new PlacedObject(ObjectType.KITCHEN, o, row, col);
            return this;
        }

        public Builder shelter(String orientId, int row, int col) {
            Orientation o = find(cache.getShelterOrientations(), orientId, "shelter");
            shelters.add(new PlacedObject(ObjectType.SHELTER, o, row, col));
            return this;
        }

        public FixedPlacements build() {
            shelters.sort(Comparator.comparingInt(PlacedObject::canonicalKey));

            List<PlacedObject> all = new ArrayList<>();
            if (bathroom != null) all.add(bathroom);
            if (kitchen  != null) all.add(kitchen);
            all.addAll(shelters);

            // R6: every fixed object must be within grid bounds
            for (PlacedObject p : all) {
                if (!ConstraintChecker.checkBounds(p.orientation, p.topLeftRow, p.topLeftCol))
                    throw new InvalidPlacementException(
                            p.type + " at (" + p.topLeftRow + "," + p.topLeftCol +
                            ") with orientation " + p.orientation.id + " is out of grid bounds");
            }

            // R7: no solid-cell overlap among fixed objects
            Grid g = new Grid();
            for (PlacedObject p : all) {
                if (!ConstraintChecker.checkNoOverlap(g, p.orientation, p.topLeftRow, p.topLeftCol))
                    throw new InvalidPlacementException(
                            p.type + " at (" + p.topLeftRow + "," + p.topLeftCol +
                            ") overlaps another fixed object");
                g.place(p);
            }

            // R1: all other objects must be >= 3 Chebyshev from any bathroom solid cell
            if (bathroom != null) {
                List<PlacedObject> others = new ArrayList<>();
                if (kitchen != null) others.add(kitchen);
                others.addAll(shelters);
                for (PlacedObject other : others) {
                    if (!ConstraintChecker.checkBuffer(other.solidCells, bathroom.solidCells, 3))
                        throw new InvalidPlacementException(
                                other.type + " at (" + other.topLeftRow + "," + other.topLeftCol +
                                ") violates R1: too close to bathroom (buffer dist=3)");
                }
            }

            // R2: all other objects must be >= 2 Chebyshev from any kitchen solid cell
            if (kitchen != null) {
                List<PlacedObject> others = new ArrayList<>();
                if (bathroom != null) others.add(bathroom);
                others.addAll(shelters);
                for (PlacedObject other : others) {
                    if (!ConstraintChecker.checkBuffer(other.solidCells, kitchen.solidCells, 2))
                        throw new InvalidPlacementException(
                                other.type + " at (" + other.topLeftRow + "," + other.topLeftCol +
                                ") violates R2: too close to kitchen (buffer dist=2)");
                }
            }

            return new FixedPlacements(
                    Optional.ofNullable(bathroom),
                    Optional.ofNullable(kitchen),
                    Collections.unmodifiableList(new ArrayList<>(shelters)));
        }

        private static Orientation find(List<Orientation> list, String id, String typeName) {
            for (Orientation o : list)
                if (o.id.equals(id)) return o;
            throw new InvalidPlacementException("Unknown " + typeName + " orientation '" + id + "'");
        }
    }
}
