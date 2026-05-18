package com.sheltersim.engine;

import com.sheltersim.model.Configuration;
import com.sheltersim.model.PlacedObject;
import com.sheltersim.model.SolverResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class Solver implements Callable<SolverResult> {

    private final OrientationCache cache;
    private final FixedPlacements taskFixed;
    private final AtomicLong remaining; // shared across tasks; Long.MAX_VALUE = unlimited
    private final Grid grid = new Grid();
    private final BfsPathFinder bfs = new BfsPathFinder();
    private final List<Configuration> results = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();
    private long evaluatedCount = 0;

    public Solver(OrientationCache cache, FixedPlacements taskFixed) {
        this(cache, taskFixed, new AtomicLong(Long.MAX_VALUE));
    }

    public Solver(OrientationCache cache, FixedPlacements taskFixed, AtomicLong remaining) {
        if (taskFixed.bathroom.isEmpty())
            throw new IllegalArgumentException("Solver requires taskFixed.bathroom to be present");
        this.cache     = cache;
        this.taskFixed = taskFixed;
        this.remaining = remaining;
    }

    @Override
    public SolverResult call() {
        // Place all pre-fixed objects onto the grid.
        taskFixed.bathroom.ifPresent(grid::place);
        taskFixed.kitchen.ifPresent(grid::place);
        for (PlacedObject s : taskFixed.shelters) grid.place(s);

        PlacedObject bath = taskFixed.bathroom.get();
        BufferZone bathBuffer = BufferZone.build(bath.solidCells, 3);

        if (taskFixed.kitchen.isPresent()) {
            PlacedObject kit = taskFixed.kitchen.get();
            BufferZone kitBuffer = BufferZone.build(kit.solidCells, 2);
            placeShelters(0, kit, bathBuffer, kitBuffer, new PlacedObject[4]);
        } else {
            placeKitchen(bathBuffer);
        }

        return new SolverResult(results, evaluatedCount, results.size());
    }

    private void placeKitchen(BufferZone bathBuffer) {
        for (var orientation : cache.getKitchenOrientations()) {
            int maxR = Grid.ROWS - orientation.rows;
            int maxC = Grid.COLS - orientation.cols;
            for (int r = 0; r <= maxR; r++) {
                for (int c = 0; c <= maxC; c++) {
                    if (remaining.get() <= 0) return;
                    if (bathBuffer.violates(orientation, r, c)) continue;
                    if (!ConstraintChecker.checkNoOverlap(grid, orientation, r, c)) continue;
                    var kit = new PlacedObject(com.sheltersim.model.ObjectType.KITCHEN, orientation, r, c);
                    grid.place(kit);
                    BufferZone kitBuffer = BufferZone.build(kit.solidCells, 2);
                    placeShelters(0, kit, bathBuffer, kitBuffer, new PlacedObject[4]);
                    grid.remove(kit);
                }
            }
        }
    }

    private void placeShelters(int idx, PlacedObject kit,
                               BufferZone bathBuffer, BufferZone kitBuffer,
                               PlacedObject[] shelters) {
        if (remaining.get() <= 0) return;
        if (idx == 4) {
            evaluatedCount++;
            int score = computeScore(shelters, taskFixed.bathroom.get(), kit);
            if (score >= 0) {
                String key = grid.toFlatString();
                if (seen.add(key)) {
                    List<com.sheltersim.model.PlacedObject> objs = new ArrayList<>();
                    objs.add(taskFixed.bathroom.get());
                    objs.add(kit);
                    for (PlacedObject s : shelters) objs.add(s);
                    results.add(new Configuration(objs, score));
                    remaining.decrementAndGet();
                }
            }
            return;
        }

        // Minimum canonical key for this shelter: >= previous shelter's key (or 0 for first).
        int minKey = (idx == 0) ? 0 : shelters[idx - 1].canonicalKey();
        // Fixed shelters occupy leading slots; if idx < fixed count, they're already placed.
        int fixedCount = taskFixed.shelters.size();
        if (idx < fixedCount) {
            shelters[idx] = taskFixed.shelters.get(idx);
            placeShelters(idx + 1, kit, bathBuffer, kitBuffer, shelters);
            return;
        }

        for (var orientation : cache.getShelterOrientations()) {
            int maxR = Grid.ROWS - orientation.rows;
            int maxC = Grid.COLS - orientation.cols;
            for (int r = 0; r <= maxR; r++) {
                for (int c = 0; c <= maxC; c++) {
                    if (r * Grid.COLS + c < minKey) continue;
                    if (bathBuffer.violates(orientation, r, c)) continue;
                    if (kitBuffer.violates(orientation, r, c)) continue;
                    if (!ConstraintChecker.checkNoOverlap(grid, orientation, r, c)) continue;
                    var shelter = new PlacedObject(com.sheltersim.model.ObjectType.SHELTER, orientation, r, c);
                    shelters[idx] = shelter;
                    grid.place(shelter);
                    placeShelters(idx + 1, kit, bathBuffer, kitBuffer, shelters);
                    grid.remove(shelter);
                }
            }
        }
    }

    private int computeScore(PlacedObject[] shelters, PlacedObject bath, PlacedObject kit) {
        int total = 0;
        for (PlacedObject shelter : shelters) {
            int toBath = bfs.shortestPath(grid, shelter.courtyard, bath.solidCells);
            if (toBath < 0) return -1;
            int toKit  = bfs.shortestPath(grid, shelter.courtyard, kit.kitchenRoom);
            if (toKit  < 0) return -1;
            total += toBath + toKit;
        }
        return total;
    }
}
