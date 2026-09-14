package com.sheltersim.engine;

import com.sheltersim.model.Configuration;
import com.sheltersim.model.ObjectType;
import com.sheltersim.model.PlacedObject;
import com.sheltersim.model.SolverResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ParallelSolver {

    private static final int WORKING_COPY_INTERVAL = 20;

    private final OrientationCache cache;
    private final FixedPlacements fixed;
    private final int threadCount;
    private final String workingPath; // null to disable
    private final long limit;         // Long.MAX_VALUE = unlimited

    public ParallelSolver(OrientationCache cache, FixedPlacements fixed,
                          int threadCount, String workingPath) {
        this(cache, fixed, threadCount, workingPath, Long.MAX_VALUE);
    }

    public ParallelSolver(OrientationCache cache, FixedPlacements fixed,
                          int threadCount, String workingPath, long limit) {
        this.cache       = cache;
        this.fixed       = fixed;
        this.threadCount = threadCount;
        this.workingPath = workingPath;
        this.limit       = limit;
    }

    public SolverResult solve() throws InterruptedException, ExecutionException {
        AtomicLong remaining = new AtomicLong(limit);
        List<Callable<SolverResult>> tasks = buildTasks(remaining);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        ExecutorCompletionService<SolverResult> ecs = new ExecutorCompletionService<>(pool);
        for (Callable<SolverResult> t : tasks) ecs.submit(t);
        pool.shutdown();
        return collectWithWorkingCopy(ecs, tasks.size());
    }

    // -------------------------------------------------------------------------
    // T8.1 — adaptive partition: bathroom → kitchen → first shelter → single task
    // -------------------------------------------------------------------------
    private List<Callable<SolverResult>> buildTasks(AtomicLong remaining) {
        List<Callable<SolverResult>> tasks = new ArrayList<>();

        if (fixed.bathroom.isEmpty()) {
            for (var orientation : cache.getBathroomOrientations()) {
                int maxR = Grid.ROWS - orientation.rows;
                int maxC = Grid.COLS - orientation.cols;
                for (int r = 0; r <= maxR; r++)
                    for (int c = 0; c <= maxC; c++) {
                        var bath = new PlacedObject(ObjectType.BATHROOM, orientation, r, c);
                        tasks.add(new Solver(cache, fixed.withBathroom(bath), remaining));
                    }
            }
        } else if (fixed.kitchen.isEmpty()) {
            var bathBuffer = BufferZone.build(fixed.bathroom.get().solidCells, 4);
            for (var orientation : cache.getKitchenOrientations()) {
                int maxR = Grid.ROWS - orientation.rows;
                int maxC = Grid.COLS - orientation.cols;
                for (int r = 0; r <= maxR; r++)
                    for (int c = 0; c <= maxC; c++) {
                        if (bathBuffer.violates(orientation, r, c)) continue;
                        var kit = new PlacedObject(ObjectType.KITCHEN, orientation, r, c);
                        tasks.add(new Solver(cache, fixed.withKitchen(kit), remaining));
                    }
            }
        } else if (fixed.shelters.size() < 4) {
            var bathBuffer = BufferZone.build(fixed.bathroom.get().solidCells, 4);
            var kitBuffer  = BufferZone.build(fixed.kitchen.get().solidCells, 3);
            int minKey = fixed.shelters.isEmpty() ? 0
                    : fixed.shelters.get(fixed.shelters.size() - 1).canonicalKey();
            int startR = minKey / Grid.COLS;
            int startC = minKey % Grid.COLS;
            for (var orientation : cache.getShelterOrientations()) {
                int maxR = Grid.ROWS - orientation.rows;
                int maxC = Grid.COLS - orientation.cols;
                for (int r = startR; r <= maxR; r++)
                    for (int c = (r == startR ? startC : 0); c <= maxC; c++) {
                        if (bathBuffer.violates(orientation, r, c)) continue;
                        if (kitBuffer.violates(orientation, r, c)) continue;
                        if (!ConstraintChecker.checkCourtyardBorder(orientation, r, c)) continue;
                        var shelter = new PlacedObject(ObjectType.SHELTER, orientation, r, c);
                        tasks.add(new Solver(cache, fixed.withShelter(shelter), remaining));
                    }
            }
        } else {
            tasks.add(new Solver(cache, fixed, remaining));
        }

        return tasks;
    }

    // -------------------------------------------------------------------------
    // T8.2 — collect results, write working copy every WORKING_COPY_INTERVAL configs
    // -------------------------------------------------------------------------
    private SolverResult collectWithWorkingCopy(
            ExecutorCompletionService<SolverResult> ecs, int taskCount)
            throws InterruptedException, ExecutionException {

        List<Configuration> accumulated = new ArrayList<>();
        long totalEvaluated = 0;
        int lastWritten = 0;

        for (int i = 0; i < taskCount; i++) {
            SolverResult partial = ecs.take().get();
            accumulated.addAll(partial.configs());
            totalEvaluated += partial.evaluated();

            if (workingPath != null) {
                int newCount = accumulated.size() - lastWritten;
                if (newCount >= WORKING_COPY_INTERVAL) {
                    writeWorkingCopy(accumulated, workingPath);
                    lastWritten = accumulated.size();
                }
            }
        }

        return new SolverResult(accumulated, totalEvaluated, accumulated.size());
    }

    // -------------------------------------------------------------------------
    // T8.3 — write unsorted partial results to disk (no rank field)
    // -------------------------------------------------------------------------
    private void writeWorkingCopy(List<Configuration> configs, String path) {
        try {
            new com.sheltersim.output.JsonSerializer().writeWorkingCopy(configs, path, cache);
        } catch (java.io.IOException e) {
            // Best-effort: working copy failure must not abort the search.
            System.err.println("Warning: could not write working copy to " + path + ": " + e.getMessage());
        }
    }
}
