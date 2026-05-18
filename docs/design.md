# Design Document: Grid Object Placement Solver

**Version:** 1.1  
**Date:** 2026-05-16  
**Based on:** requirements.md v1.0

---

## 1. Project Structure

```
shelter-sim/
├── solver/                          # Java solver (Maven project)
│   ├── pom.xml
│   └── src/main/java/com/sheltersim/
│       ├── Main.java
│       ├── model/
│       │   ├── CellLabel.java            # enum for all cell symbols
│       │   ├── ObjectType.java           # enum SHELTER / BATHROOM / KITCHEN
│       │   ├── Orientation.java          # one pre-computed object orientation
│       │   ├── PlacedObject.java         # object + position + orientation
│       │   ├── Configuration.java        # complete 6-object assignment + score
│       │   ├── SolverResult.java         # result record returned by each Solver task
│       │   └── InvalidPlacementException.java  # thrown by FixedPlacements.build()
│       ├── engine/
│       │   ├── OrientationCache.java  # builds & stores all orientations at startup
│       │   ├── Grid.java              # mutable 27×21 grid, place/remove
│       │   ├── BufferZone.java        # precomputed Chebyshev exclusion overlay
│       │   ├── ConstraintChecker.java # bounds, buffer, overlap checks
│       │   ├── BfsPathFinder.java     # flood-fill returning path length or -1
│       │   ├── FixedPlacements.java   # validated set of pre-placed objects
│       │   ├── Solver.java            # single-bathroom subtree backtracking
│       │   └── ParallelSolver.java    # partitions over outermost unfixed object
│       └── output/
│           └── JsonSerializer.java  # writes result JSON + stats
├── visualizer/                      # Python visualizer
│   ├── requirements.txt
│   ├── visualizer.py                # CLI entry point
│   └── renderer.py                  # matplotlib grid rendering
└── design.md
```

---

## 2. Java Solver

### 2.1 `model/CellLabel.java`

```java
public enum CellLabel {
    EMPTY('0'),
    ROOM_A('a'), ROOM_B('b'), ROOM_C('c'), ROOM_D('d'), ROOM_E('e'),
    PORCH('2'), COURTYARD('3'),
    KITCHEN_ROOM('4'), GARDEN('5'),
    BATHROOM('B');   // used internally for Bathroom solid cells

    public final char symbol;
    public boolean isSolid();   // false only for EMPTY
    public static CellLabel fromChar(char c);
}
```

### 2.1a `model/ObjectType.java`

```java
public enum ObjectType { SHELTER, BATHROOM, KITCHEN }
```

---

### 2.1b `model/SolverResult.java`

```java
public record SolverResult(
    List<Configuration> configs,
    long evaluated,
    long valid
) {}
```

---

### 2.1c `model/InvalidPlacementException.java`

```java
public class InvalidPlacementException extends RuntimeException {
    public InvalidPlacementException(String message) { super(message); }
}
```

Thrown by `FixedPlacements.build()` when a user-supplied fixed placement violates any of R1, R2, R6, or R7.

---

### 2.2 `model/Orientation.java`

Immutable snapshot of one pre-computed orientation.

```java
public final class Orientation {
    public final String id;          // "R0", "R90M", etc.
    public final boolean[][] mask;   // [rows][cols], true = solid
    public final char[][] cellMap;   // [rows][cols], CellLabel symbols
    public final int rows;
    public final int cols;
}
```

Transformations operate on both `mask` and `cellMap` simultaneously.  
**Rotation 90° CW:** `result[c][rows-1-r] = src[r][c]`  
**Horizontal mirror:** `result[r][cols-1-c] = src[r][c]`

### 2.3 `model/PlacedObject.java`

```java
public final class PlacedObject {
    public final ObjectType type;         // SHELTER, BATHROOM, KITCHEN
    public final Orientation orientation;
    public final int topLeftRow;
    public final int topLeftCol;

    // Precomputed flat cell arrays: [r0, c0, r1, c1, ...], length = 2 × cellCount.
    // Flat int[] avoids pointer-chasing versus List<int[]> in the inner constraint loop.
    public final int[] solidCells;    // all solid cells
    public final int[] courtyard;     // '3' cells (Shelters only; empty array otherwise)
    public final int[] kitchenRoom;   // '4' cells (Kitchen only; empty array otherwise)

    // Constructor derives all three cell arrays from orientation.cellMap + topLeft offset.
    // courtyard and kitchenRoom are empty arrays (not null) for non-shelter/non-kitchen types.
    public PlacedObject(ObjectType type, Orientation orientation,
                        int topLeftRow, int topLeftCol);

    // Canonical sort key: topLeftRow * Grid.COLS + topLeftCol
    public int canonicalKey();
}
```

### 2.4 `model/Configuration.java`

```java
public final class Configuration {
    public final List<PlacedObject> objects; // length 6, sorted by canonical key (shelters first)
    public int score;  // always an integer — sum of 8 BFS path lengths; requirements showing
                       // a float score (42.17) is an error; BFS counts are integer cell counts
    public int rank;   // assigned after sorting; mutable so not final
}
```

---

### 2.5 `engine/OrientationCache.java`

Computes all orientations once at startup. Called before the search begins.

```java
public class OrientationCache {
    private static final char[][] SHELTER_BASE = { /* 11×9 char grid */ };
    private static final char[][] KITCHEN_BASE = { /* 6×4 char grid */ };

    public List<Orientation> getShelterOrientations();  // 8 entries
    public List<Orientation> getKitchenOrientations();  // 4 entries
    public List<Orientation> getBathroomOrientations(); // 2 entries

    private Orientation rotate90(Orientation o, String newId);
    private Orientation mirror(Orientation o, String newId);
    private boolean[][] extractMask(char[][] cellMap);
}
```

Shelter orientation generation:

```
R0 → rotate → R90 → rotate → R180 → rotate → R270
R0 → mirror → R0M → rotate → R90M → rotate → R180M → rotate → R270M
```

Kitchen: only R0, R90, R180, R270 — mirroring produces duplicates due to horizontal symmetry (verified by comparing masks after generation).

Bathroom: two orientations, both all-solid, cell map uses `'B'` throughout.

| Orientation ID | Dimensions (rows × cols) |
|---|---|
| `"Portrait"` | 8 × 4 |
| `"Landscape"` | 4 × 8 |

---

### 2.6 `engine/Grid.java`

27×21 mutable grid backed by `char[21][27]` where `'0'` = empty.

```java
public class Grid {
    public static final int ROWS = 21;
    public static final int COLS = 27;

    private final char[][] cells = new char[ROWS][COLS]; // init all '0'

    public void place(PlacedObject obj);      // writes non-'0' cells
    public void remove(PlacedObject obj);     // restores '0' at those cells
    public boolean isEmpty(int r, int c);     // cells[r][c] == '0'
    public char getLabel(int r, int c);
    public boolean inBounds(int r, int c);

    // Flattens cells into a 567-char string (21 rows × 27 cols).
    // Used as the dedup key in Solver's seen set. Correct by construction:
    // does not record which shelter instance placed which cell, so
    // interchangeable shelter permutations that produce identical layouts
    // hash to the same string automatically.
    public String toFlatString();
}
```

`place`/`remove` only touch cells where `mask[dr][dc]` is true (solid cells only).

---

### 2.7 `engine/BufferZone.java`

Precomputed boolean grid overlay for fast buffer violation checks. Rebuilt whenever a Bathroom or Kitchen is placed/removed during backtracking.

```java
public class BufferZone {
    private final boolean[][] exclusionZone = new boolean[Grid.ROWS][Grid.COLS];
    private final List<int[]> sourceSolids;
    private final int bufferDist;

    public static BufferZone build(List<int[]> solidCells, int bufferDist);

    public boolean isExcluded(int r, int c);

    // Returns true if any candidate solid cell violates this buffer.
    // Near-edge cells are handled by direct Chebyshev comparison against
    // sourceSolids, because the virtual buffer extends beyond grid bounds.
    public boolean violates(List<int[]> candidateSolidCells);
}
```

For each source solid cell `(r, c)`, `build()` marks all `(r±d, c±d)` within grid bounds as excluded. `violates()` also performs a direct `max(|Δr|, |Δc|) <= bufferDist` check for any candidate cell within `bufferDist` rows/cols of the grid edge, ensuring the infinite-buffer-at-edge rule is respected.

**Lifecycle within a `Solver` task:**
- `BathroomBufferZone` — built once in `Solver.call()` from `taskFixed.bathroom`'s solid cells. Lives for the entire duration of the task.
- `KitchenBufferZone` — built when a Kitchen placement is selected (inside `placeKitchen()`), passed down into `placeShelters()`, and goes out of scope when `placeKitchen()` backtracks to the next Kitchen candidate.

---

### 2.8 `engine/ConstraintChecker.java`

```java
// All methods are static — no state, no instantiation.
public final class ConstraintChecker {

    // R6: all solid cells within [0, ROWS) × [0, COLS)
    public static boolean checkBounds(Orientation o, int topR, int topC);

    // R1/R2: direct Chebyshev check — used only by FixedPlacements.build() where no
    // precomputed BufferZone exists yet. At search time, Solver uses BufferZone.violates()
    // which is faster (overlay lookup vs. O(n×m) distance computation).
    public static boolean checkBuffer(int[] candidateSolids,
                                      int[] bufferedSolids, int dist);

    // R7: no overlap between candidate solid cells and occupied grid cells
    public static boolean checkNoOverlap(Grid grid, Orientation o, int topR, int topC);
}
```

Path checking lives in `BfsPathFinder` and is invoked directly from `Solver` after all objects are placed.

---

### 2.9 `engine/BfsPathFinder.java`

Multi-source BFS. Seeds the queue with empty cells orthogonally adjacent to any source solid cell. Stops when any queued cell is orthogonally adjacent to any target solid cell. Returns the BFS level (empty cells traversed) at that point, or `-1` if unreachable.

```java
public class BfsPathFinder {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = { 0, 0,-1, 1};

    // Generation counter — increment instead of clearing the visited array.
    // visitedGen[r * COLS + c] == currentGen means cell was visited this call.
    private final int[] visitedGen = new int[Grid.ROWS * Grid.COLS];
    private int currentGen = 0;

    // targetAdj[r * COLS + c] == true means cell is adjacent to a target solid cell.
    // boolean[] avoids string allocation and hash lookup in the BFS inner loop.
    private final boolean[] targetAdj = new boolean[Grid.ROWS * Grid.COLS];

    public int shortestPath(Grid grid, int[] sourceSolids, int[] targetSolids);

    // Populates targetAdj in-place; cleared by overwriting with false after each call.
    private void buildTargetAdjacency(Grid grid, int[] targetSolids);
}
```

**Generation counter:** `currentGen++` replaces `Arrays.fill` before each call. `visitedGen[idx] == currentGen` is the visited check — zero array clearing regardless of how early BFS terminates. The BFS result from R3/R4 validity checking is the score component — no second pass needed.

---

### 2.10 `engine/FixedPlacements.java`

Immutable container for zero or more pre-placed objects. Constructed via a fluent builder; validation runs at `build()` time so the solver never receives an invalid starting state.

```java
public final class FixedPlacements {
    public final Optional<PlacedObject> bathroom;   // absent = solver enumerates
    public final Optional<PlacedObject> kitchen;    // absent = solver enumerates
    public final List<PlacedObject> shelters;       // 0–4 fixed shelter placements

    public static final FixedPlacements NONE = new FixedPlacements(
            Optional.empty(), Optional.empty(), List.of());

    public static Builder builder(OrientationCache cache);

    public static class Builder {
        // orientId: "R0", "R90M", "Portrait", "Landscape", etc. — looked up in OrientationCache
        public Builder bathroom(String orientId, int row, int col);
        public Builder kitchen(String orientId, int row, int col);
        public Builder shelter(String orientId, int row, int col); // call up to 4×

        // Validates all provided placements before returning:
        //   R6  bounds check on every fixed object
        //   R1  Bathroom buffer vs Kitchen and fixed Shelters
        //   R2  Kitchen buffer vs Bathroom and fixed Shelters
        //   R7  no overlap among fixed Shelters, or with Kitchen/Bathroom
        // Throws InvalidPlacementException with a descriptive message on any violation.
        public FixedPlacements build();
    }

    // Package-private — used by ParallelSolver to inject the partition-assigned
    // placement into a task's fixed set without re-running full user-input validation.
    FixedPlacements withBathroom(PlacedObject bath);
    FixedPlacements withKitchen(PlacedObject kit);
    FixedPlacements withShelter(PlacedObject shelter); // appends to shelters list
}
```

**Canonical ordering with fixed shelters:** fixed shelters are sorted by canonical key at `build()` time. When `Solver` enumerates unfixed shelters it starts from canonical key ≥ the maximum fixed shelter canonical key, preserving the deduplication invariant.

---

### 2.11 `engine/Solver.java`

Handles the backtracking subtree for a fully specified `FixedPlacements` task configuration. `ParallelSolver` (§2.12) always ensures `taskFixed.bathroom` is present before constructing a `Solver` — either the user fixed it, or `ParallelSolver` injected the partition placement via `withBathroom()`.

**`call()` startup sequence:**
1. Place all objects present in `taskFixed` onto the grid.
2. Build `BathroomBufferZone` from `taskFixed.bathroom` (always present).
3. If `taskFixed.kitchen` is present, build `KitchenBufferZone` immediately; skip to shelter enumeration.
4. Otherwise enumerate Kitchen placements, building/releasing `KitchenBufferZone` per candidate.

Pruning applied at each level:

| Level | Checks Applied |
|---|---|
| Kitchen | Bounds → R1 (Kitchen vs Bathroom buffer) |
| Shelter i | Bounds → R1 (vs Bathroom) → R2 (vs Kitchen) → R7 (overlap) → canonical key ≥ shelter[i-1] |
| After all shelters placed | R3 + R4 (BFS paths for all 4 shelters) → `grid.toFlatString()` dedup → score → store |

**Canonical shelter ordering with fixed shelters:** fixed shelters (from `taskFixed.shelters`, pre-sorted by canonical key) are treated as the leading elements. Unfixed shelter enumeration starts from canonical key ≥ the maximum fixed shelter canonical key.

Each `Solver` instance owns its own `Grid` and `BfsPathFinder` — no shared mutable state between tasks.

```java
public class Solver implements Callable<SolverResult> {
    private final OrientationCache cache;       // shared, read-only
    private final FixedPlacements taskFixed;    // bathroom always present here
    private final Grid grid = new Grid();       // task-local
    private final List<Configuration> results = new ArrayList<>();
    private final Set<String> seen = new HashSet<>(); // grid.toFlatString() keys
    private long evaluatedCount = 0;

    // taskFixed.bathroom must be present; assertion thrown otherwise
    public Solver(OrientationCache cache, FixedPlacements taskFixed);

    public SolverResult call();

    private void placeKitchen(BufferZone bathBuffer);
    private void placeShelters(int idx, PlacedObject kit,
                               BufferZone bathBuffer, BufferZone kitBuffer,
                               PlacedObject[] shelters);
    private int computeScore(PlacedObject[] shelters,
                             PlacedObject bath, PlacedObject kit);
}
```

---

### 2.12 `engine/ParallelSolver.java`

Enumerates the **outermost unfixed object** placements as tasks and submits one `Solver` per task. When a fixed placement removes the normal partition object, it falls back to the next level.

**Adaptive partition strategy:**

| Scenario | Partition over | ~Task count |
|---|---|---|
| Bathroom unfixed | Bathroom placements | ~1,400 |
| Bathroom fixed, Kitchen unfixed | Kitchen placements | ~2,200 |
| Bathroom + Kitchen fixed | First unfixed Shelter placements | ~1,600 |
| All 6 objects fixed | Single task (validate + score only) | 1 |

```java
public class ParallelSolver {
    private final OrientationCache cache;
    private final FixedPlacements fixed;
    private final int threadCount;    // default: Runtime.getRuntime().availableProcessors()
    private final String workingPath; // e.g. "output.working.json"; null to disable

    private static final int WORKING_COPY_INTERVAL = 20;

    public SolverResult solve() throws InterruptedException, ExecutionException {
        List<Callable<SolverResult>> tasks = buildTasks();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        ExecutorCompletionService<SolverResult> ecs =
                new ExecutorCompletionService<>(pool);
        for (Callable<SolverResult> t : tasks) ecs.submit(t);
        pool.shutdown();
        return collectWithWorkingCopy(ecs, tasks.size());
    }

    // As each Solver task finishes, appends its configs to the accumulated list.
    // Every WORKING_COPY_INTERVAL new valid configs, rewrites workingPath (unsorted).
    private SolverResult collectWithWorkingCopy(
            ExecutorCompletionService<SolverResult> ecs, int taskCount)
            throws InterruptedException, ExecutionException;

    // Determines partition level from userFixed, enumerates that object's valid placements,
    // and creates one Solver per placement. Augments fixed set via with*() methods:
    //
    //   Bathroom partition:  taskFixed = userFixed.withBathroom(bathroomPlacement)
    //   Kitchen partition:   taskFixed = userFixed.withKitchen(kitchenPlacement)
    //   Shelter partition:   taskFixed = userFixed.withShelter(shelterPlacement)
    //                            (bathroom + kitchen already present in userFixed)
    private List<Callable<SolverResult>> buildTasks();

    // Writes configs to path as unsorted JSON (no rank field); overwrites on each call.
    private void writeWorkingCopy(List<Configuration> configs, String path);
}
```

**Thread safety summary:**

| Resource | Shared? | Safe? | Notes |
|---|---|---|---|
| `OrientationCache` | Yes | Yes | Read-only after construction |
| `FixedPlacements` | Yes | Yes | Immutable after `build()` |
| `Grid` | No | Yes | One instance per `Solver` task |
| `BfsPathFinder` | No | Yes | One instance per `Solver` task |
| `BufferZone` | No | Yes | Built and used within one task |
| `results` list | No | Yes | Each task has its own; merged post-join |

**Cross-task deduplication:** Not needed. Partitioning over distinct placements of the same object guarantees distinct canonical keys across tasks.

---

### 2.13 `output/JsonSerializer.java`

```java
public class JsonSerializer {
    public void write(List<Configuration> configs,
                      long evaluated, long valid, long runtimeMs,
                      String outputPath) throws IOException;
}
```

Output file structure:

```json
{
  "stats": {
    "evaluated": 1234567,
    "valid": 89,
    "runtimeMs": 4521
  },
  "configurations": [
    {
      "rank": 1,
      "score": 42,
      "objects": [
        {
          "type": "Shelter",
          "index": 1,
          "orientationId": "R90M",
          "topLeftRow": 3,
          "topLeftCol": 5,
          "cells": [
            { "row": 3, "col": 5, "label": "a" }
          ]
        }
      ]
    }
  ]
}
```

The `index` field (1-based) is assigned by the serializer from each object's position in the sorted `objects` list — it is not stored in `PlacedObject`. For Shelter objects this gives a stable human-readable label (Shelter 1, Shelter 2, …) without `instanceId` polluting the model.

Uses Gson (added as Maven dependency).

---

### 2.14 `Main.java`

```
1. Parse CLI args:
     <output.json>                        output file path (default: output.json)
     --threads N                          thread count (default: all CPUs)
     --limit N                            stop after finding N valid configurations (default: unlimited)
     --fix bathroom <orientId> <row> <col>
     --fix kitchen  <orientId> <row> <col>
     --fix shelter  <orientId> <row> <col>   (repeatable, up to 4 times)

2. Instantiate OrientationCache — pre-computes all orientations
3. Build FixedPlacements via its builder (throws InvalidPlacementException on bad input)
4. Instantiate ParallelSolver(cache, fixed, threadCount, workingPath="output.working.json"),
   run solve(), capture wall-clock time
5. Sort merged results ascending by score; assign 1-indexed ranks (ties share rank)
6. Serialize final sorted output via JsonSerializer
7. Delete working copy file if present
8. Print stats summary to stdout: threads used, fixed objects, evaluated, valid, runtime
```

**Example invocations:**

```bash
# unconstrained full search
java -jar solver.jar output.json --threads 8

# fix the Bathroom; search over Kitchen + Shelters
java -jar solver.jar output.json --fix bathroom Portrait 2 3

# fix Bathroom and Kitchen; search over Shelters only
java -jar solver.jar output.json \
  --fix bathroom Portrait 2 3 \
  --fix kitchen R0 10 5

# fix all 6 objects (validate + score a single configuration)
java -jar solver.jar output.json \
  --fix bathroom Portrait 2 3 \
  --fix kitchen R0 10 5 \
  --fix shelter R0 1 1 \
  --fix shelter R90 1 15 \
  --fix shelter R180 8 1 \
  --fix shelter R270M 8 15
```

---

## 3. Python Visualizer

### 3.1 `visualizer/requirements.txt`

```
matplotlib>=3.7
Pillow>=10.0
```

### 3.2 Visual Style

**Cell rendering rules:**

| Cell type | Labels | Fill | Border |
|---|---|---|---|
| Empty | `0` | `#ffffff` | 1px `#5a5a5a` |
| Room | `a` `b` `c` `d` `e` | `#ffffff` | 3px `#000000` around the 3×3 room group (see below) |
| Courtyard | `3` | `#e4e7dc` | 1px `#5a5a5a` |
| Porch / Garden | `2` `5` | `#eacbb7` | 1px `#5a5a5a` |
| Kitchen room | `4` | `#c78888` | 1px `#5a5a5a` |
| Bathroom | `B` | `#9e8988` | 1px `#5a5a5a` |

**Room group border rule:** Each distinct room label on a placed Shelter (a–e) forms a contiguous 3×3 block of cells. Rather than drawing per-cell borders, the renderer draws a single 3px `#000000` rectangle around the entire 3×3 block's outer perimeter. Individual cell edges within the block carry no border.

All other cell types (everything that is not a room) receive a 1px `#5a5a5a` border on each of their four edges.

### 3.3 `visualizer/renderer.py`

Rendering is done with `matplotlib.patches.Rectangle` patches (not `imshow`), giving precise per-cell fill and border control.

```python
CELL_PX: int = 20          # pixel width/height of one grid cell (configurable)
COLS: int = 27
ROWS: int = 21

FILL: dict[str, str] = {
    '0': '#ffffff',
    'a': '#ffffff', 'b': '#ffffff', 'c': '#ffffff', 'd': '#ffffff', 'e': '#ffffff',
    '3': '#e4e7dc',
    '2': '#eacbb7', '5': '#eacbb7',
    '4': '#c78888',
    'B': '#9e8988',
}
ROOM_LABELS: frozenset[str] = frozenset('abcde')
CELL_BORDER_COLOR: str = '#5a5a5a'
ROOM_BORDER_COLOR: str = '#000000'


def render_configuration(config: dict, output_path: str | None = None) -> None:
    """Entry point. Builds label grid, draws patches, annotates title, saves or shows."""

def build_label_grid(config: dict) -> list[list[str]]:
    """Returns 21×27 grid of label chars (default '0')."""

def draw_cells(ax, label_grid: list[list[str]]) -> None:
    """
    Pass 1 — non-room cells:
      For each cell where label not in ROOM_LABELS, add a Rectangle with
      facecolor=FILL[label], edgecolor=CELL_BORDER_COLOR, linewidth=1.

    Pass 2 — room cells (fill only, no individual border):
      For each cell where label in ROOM_LABELS, add a Rectangle with
      facecolor='#ffffff', edgecolor='none', linewidth=0.
    """

def draw_room_borders(ax, config: dict, label_grid: list[list[str]]) -> None:
    """
    For each (instanceId, room_label) group in config['objects']:
      Compute bounding box: min_row, min_col, max_row, max_col over the group's cells.
      Add a Rectangle at (min_col * CELL_PX, min_row * CELL_PX) with
      width = (max_col - min_col + 1) * CELL_PX,
      height = (max_row - min_row + 1) * CELL_PX,
      fill=False, edgecolor=ROOM_BORDER_COLOR, linewidth=3.
    """
```

Rendering order within `render_configuration`:
1. `draw_cells` — all fills and 1px non-room borders
2. `draw_room_borders` — 3px room outlines drawn on top
3. Title annotation: `Rank {rank}  |  Score {score}`
4. Axis limits set to `(0, COLS * CELL_PX)` × `(0, ROWS * CELL_PX)`, y-axis inverted so row 0 is at the top

### 3.4 `visualizer/visualizer.py` — CLI

```
Usage:
  python visualizer.py <output.json> --rank <n>
  python visualizer.py <output.json> --top <n>
  python visualizer.py <output.json> --all --export-dir <dir>
```

Argument handling via `argparse`. Images saved as `rank_<n>.png` when `--export-dir` is given. `--top N` renders min(N, total) configurations — no error if fewer than N exist.

---

## 4. Performance Notes

**Raw search space** (before pruning, approximate):

| Object | Orientations | Valid positions | Subtotal |
|---|---|---|---|
| Bathroom | 2 | ~700 | ~1,400 |
| Kitchen | 4 | ~550 | ~2,200 |
| Each Shelter (R0, 11×9) | 8 | ~200 | ~1,600 |

Naive 4-shelter combinations: 1,600⁴ ≈ 6.5 × 10¹². With canonical ordering: ~1,600⁴ / 24 ≈ 2.7 × 10¹¹ — still enormous, but aggressive buffer + overlap pruning cuts this drastically in practice.

**Key pruning wins expected:**
- Bathroom buffer (d=3) eliminates a wide band of grid cells from any other object — checked before placing each shelter.
- Overlap detection during shelter placement prunes invalid branches before placing the next shelter.
- Canonical shelter ordering eliminates 24× permutation inflation.
- BFS (most expensive) runs only after all 6 objects pass the cheaper checks.

**Search tractability:** The raw post-canonical-ordering space (~2.7 × 10¹¹) is enormous. Effective runtime depends entirely on how aggressively the buffer and overlap pruning cuts it — this cannot be known without a prototype run. If the search proves intractable, use `--limit N` to stop early. Progress can be tracked via the working copy file written every 20 valid results and the evaluated-count in stdout stats.

**Memory:** the working copy + final output are both written to disk; in-memory accumulation is bounded by the number of valid configurations found so far across completed tasks.

**Parallelization speedup:** the `ExecutorCompletionService` approach processes task results as they complete, enabling incremental writes without blocking. Bathroom subtrees vary in size — `ForkJoinPool.commonPool()` with work-stealing can be substituted if load imbalance is observed in practice.

---

## 5. Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Buffer near edges | Direct Chebyshev math, not overlay only | Grid overlay can't represent the virtual infinite buffer beyond bounds; direct distance check handles edge cases correctly |
| Shelter deduplication (primary) | Canonical position ordering enforced during search | Eliminates 24× permutation inflation at source; no post-hoc cost |
| Shelter deduplication (safety net) | `grid.toFlatString()` in `seen` set after all objects placed | Correct by construction — shelter instance identity not recorded, so all equivalent layouts collide naturally; replaces the fragile metadata-based canonical key |
| BFS reuse | Validity BFS result reused directly as score component | Zero additional cost per requirements §6.1 |
| Placement order | Bathroom → Kitchen → Shelters | Most-constrained first maximizes pruning per level |
| Grid cell type | `char[21][27]` | Single array gives both label readout for BFS and visualization without extra abstraction |
| `solidCells` / `courtyard` / `kitchenRoom` type | Flat `int[]` (interleaved row/col) | Avoids pointer-chasing vs `List<int[]>`; accessed millions of times in inner constraint loop |
| BFS visited tracking | Generation counter (`int[]` + `currentGen++`) | Zero-cost reset — no `Arrays.fill` regardless of how early BFS terminates |
| BFS target adjacency | `boolean[]` flat array | O(1) lookup with no string allocation; replaces `Set<String>` key approach |
| `instanceId` | Removed from model; `index` derived at serialization time | Model stays clean; cells already nested under parent object in JSON so no grouping key needed |
| Incremental output | Working copy written every 20 valid configs via `ExecutorCompletionService` | Provides progress visibility and partial results if search is interrupted; no sorting required |
| Search early exit | `--limit N` CLI flag | Allows stopping after N valid configs if exhaustive search proves intractable |
| JSON library | Gson | Minimal dependency; clean POJO serialization |
| Parallelization unit | One task per outermost-unfixed-object placement (adaptive) | Bathroom subtrees are independent; when Bathroom is fixed, fall back to Kitchen or Shelter partitioning to preserve parallelism |
| Thread pool type | `ExecutorService` (fixed pool), with `ForkJoinPool` as fallback | Fixed pool is simpler; switch to work-stealing if subtree sizes are uneven |
| Cross-task dedup | None needed | Partitioning over distinct placements guarantees distinct canonical keys across tasks |
| Fixed placement validation | Eager, at `FixedPlacements.build()` | Fail fast with a clear error before the search starts; avoids silent incorrect results |
| Fixed shelter canonical ordering | Fixed shelters sorted at `build()`; unfixed shelters enumerate from max fixed key | Preserves the 24× deduplication invariant even when some shelters are pre-placed |
