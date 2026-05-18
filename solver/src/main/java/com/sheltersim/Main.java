package com.sheltersim;

import com.sheltersim.engine.FixedPlacements;
import com.sheltersim.engine.OrientationCache;
import com.sheltersim.engine.ParallelSolver;
import com.sheltersim.model.Configuration;
import com.sheltersim.model.InvalidPlacementException;
import com.sheltersim.model.SolverResult;
import com.sheltersim.output.JsonSerializer;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // ----------------------------------------------------------------
        // T10.1 — parse CLI arguments
        // ----------------------------------------------------------------
        String outputPath = "output.json";
        int threadCount   = Runtime.getRuntime().availableProcessors();
        long limit        = Long.MAX_VALUE;
        FixedPlacements.Builder fixBuilder = null; // initialised after cache is ready

        // Collect --fix tokens for deferred parsing (cache needed to look up orientations).
        record FixToken(String kind, String orientId, int row, int col) {}
        List<FixToken> fixTokens = new ArrayList<>();

        int i = 0;
        while (i < args.length) {
            switch (args[i]) {
                case "--threads" -> {
                    if (i + 1 >= args.length) die("--threads requires a value");
                    threadCount = parseInt(args[++i], "--threads");
                    if (threadCount < 1) die("--threads must be >= 1");
                }
                case "--limit" -> {
                    if (i + 1 >= args.length) die("--limit requires a value");
                    limit = parseLong(args[++i], "--limit");
                    if (limit < 1) die("--limit must be >= 1");
                }
                case "--fix" -> {
                    if (i + 3 >= args.length) die("--fix requires: <kind> <orientId> <row> <col>");
                    String kind    = args[++i].toLowerCase();
                    String orientId = args[++i];
                    int row = parseInt(args[++i], "--fix row");
                    int col = parseInt(args[++i], "--fix col");
                    if (!kind.equals("bathroom") && !kind.equals("kitchen") && !kind.equals("shelter"))
                        die("--fix kind must be bathroom, kitchen, or shelter");
                    fixTokens.add(new FixToken(kind, orientId, row, col));
                }
                default -> {
                    if (args[i].startsWith("--")) die("Unknown option: " + args[i]);
                    outputPath = args[i];
                }
            }
            i++;
        }

        // ----------------------------------------------------------------
        // T10.2 — execution sequence
        // ----------------------------------------------------------------

        // 1. Build orientation cache.
        OrientationCache cache = new OrientationCache();

        // 2. Build FixedPlacements.
        fixBuilder = FixedPlacements.builder(cache);
        for (var t : fixTokens) {
            try {
                switch (t.kind()) {
                    case "bathroom" -> fixBuilder.bathroom(t.orientId(), t.row(), t.col());
                    case "kitchen"  -> fixBuilder.kitchen(t.orientId(),  t.row(), t.col());
                    case "shelter"  -> fixBuilder.shelter(t.orientId(),  t.row(), t.col());
                }
            } catch (InvalidPlacementException e) {
                System.err.println("Invalid fixed placement: " + e.getMessage());
                System.exit(1);
            }
        }

        FixedPlacements fixed;
        try {
            fixed = fixBuilder.build();
        } catch (InvalidPlacementException e) {
            System.err.println("Invalid fixed placement: " + e.getMessage());
            System.exit(1);
            return; // unreachable, satisfies compiler
        }

        // Derive working copy path next to output file.
        String workingPath = siblingPath(outputPath, "output.working.json");

        // 3. Run solver.
        ParallelSolver solver = new ParallelSolver(cache, fixed, threadCount, workingPath, limit);
        long startMs = System.currentTimeMillis();

        SolverResult result;
        try {
            result = solver.solve();
        } catch (Exception e) {
            System.err.println("Solver error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            return;
        }
        long runtimeMs = System.currentTimeMillis() - startMs;

        // 4. Sort ascending by score; assign ranks (ties share rank).
        List<Configuration> configs = new ArrayList<>(result.configs());
        configs.sort(Comparator.comparingInt(c -> c.score));
        int rank = 1;
        for (int j = 0; j < configs.size(); j++) {
            if (j > 0 && configs.get(j).score != configs.get(j - 1).score) rank = j + 1;
            configs.get(j).rank = rank;
        }

        // 5. Serialize.
        try {
            new JsonSerializer().write(configs, result.evaluated(), result.valid(), runtimeMs, outputPath);
        } catch (Exception e) {
            System.err.println("Failed to write output: " + e.getMessage());
            System.exit(1);
        }

        // 6. Delete working copy.
        new File(workingPath).delete();

        // 7. Print stats.
        int fixedCount = (fixed.bathroom.isPresent() ? 1 : 0)
                + (fixed.kitchen.isPresent() ? 1 : 0)
                + fixed.shelters.size();
        System.out.printf("Threads:   %d%n", threadCount);
        System.out.printf("Fixed:     %d object(s)%n", fixedCount);
        System.out.printf("Evaluated: %,d%n", result.evaluated());
        System.out.printf("Valid:     %,d%n", result.valid());
        System.out.printf("Runtime:   %,d ms%n", runtimeMs);
        System.out.printf("Output:    %s%n", outputPath);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static int parseInt(String s, String context) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { die(context + " must be an integer, got: " + s); return 0; }
    }

    private static long parseLong(String s, String context) {
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { die(context + " must be an integer, got: " + s); return 0; }
    }

    private static void die(String msg) {
        System.err.println("Error: " + msg);
        System.err.println();
        System.err.println("Usage: solver.jar [output.json] [--threads N] [--limit N]");
        System.err.println("         [--fix bathroom <orientId> <row> <col>]");
        System.err.println("         [--fix kitchen  <orientId> <row> <col>]");
        System.err.println("         [--fix shelter  <orientId> <row> <col>] ...");
        System.exit(1);
    }

    private static String siblingPath(String outputPath, String filename) {
        File parent = new File(outputPath).getParentFile();
        return parent == null ? filename : new File(parent, filename).getPath();
    }
}
