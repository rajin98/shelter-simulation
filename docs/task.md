# Task List: Grid Object Placement Solver

**Version:** 1.0  
**Date:** 2026-05-16  
**Based on:** requirements.md v1.0, design.md v1.1

Tasks are ordered sequentially. Each group must be complete before the next group begins. Within a group, tasks with no inter-dependency may be done in parallel.

---

## Phase 1 — Project Scaffolding

- [x] **T1.1** Create Maven project under `solver/` with `pom.xml` defining:
  - Group ID `com.sheltersim`, artifact ID `solver`, Java 25
  - Dependency: Gson (e.g. `com.google.code.gson:gson:2.10.1`)
  - `maven-exec-plugin` configured with `mainClass=com.sheltersim.Main`
  - Source directory `src/main/java/com/sheltersim/`

- [x] **T1.2** Create Python project under `visualizer/` with:
  - `requirements.txt` containing `matplotlib>=3.7` and `Pillow>=10.0`
  - Empty `visualizer.py` and `renderer.py` placeholder files

- [x] **T1.3** Create Python virtual environment under `visualizer/.venv`:
  - Run `python -m venv visualizer/.venv`
  - Activate: `visualizer\.venv\Scripts\activate` (Windows) or `source visualizer/.venv/bin/activate` (Unix)
  - Install dependencies: `pip install -r visualizer/requirements.txt`
  - Add `visualizer/.venv/` to `.gitignore`

- [x] **T1.4** Verify Maven compiles a hello-world `Main.java` (`mvn compile` passes) — confirmed, BUILD SUCCESS

- [x] **T1.5** Verify Python environment installs cleanly — matplotlib 3.10.9, Pillow 12.2.0 confirmed in venv

---

## Phase 2 — Model Layer (Java)

> All model classes are pure data containers with no engine logic. Can be written in any order once Phase 1 is done.

- [x] **T2.1** Implement `model/CellLabel.java`
  - Enum values: `EMPTY('0')`, `ROOM_A('a')` through `ROOM_E('e')`, `PORCH('2')`, `COURTYARD('3')`, `KITCHEN_ROOM('4')`, `GARDEN('5')`, `BATHROOM('B')`
  - Field: `public final char symbol`
  - Method: `public boolean isSolid()` — false only for `EMPTY`
  - Method: `public static CellLabel fromChar(char c)` — throws on unknown char

- [x] **T2.2** Implement `model/ObjectType.java`
  - Enum: `SHELTER`, `BATHROOM`, `KITCHEN`

- [x] **T2.3** Implement `model/Orientation.java`
  - Immutable final class with fields: `String id`, `boolean[][] mask`, `char[][] cellMap`, `int rows`, `int cols`
  - No methods beyond a constructor

- [x] **T2.4** Implement `model/PlacedObject.java`
  - Fields: `ObjectType type`, `Orientation orientation`, `int topLeftRow`, `int topLeftCol`
  - Flat `int[]` fields (interleaved row/col pairs): `solidCells`, `courtyard`, `kitchenRoom`
  - Constructor derives all three arrays from `orientation.cellMap` + top-left offset
  - `courtyard` and `kitchenRoom` must be empty arrays (not null) for non-applicable types
  - Method: `public int canonicalKey()` returning `topLeftRow * Grid.COLS + topLeftCol`

- [x] **T2.5** Implement `model/Configuration.java`
  - Fields: `List<PlacedObject> objects` (length 6), `int score`, `int rank`
  - `rank` is mutable (not final) — assigned after sorting

- [x] **T2.6** Implement `model/SolverResult.java`
  - Java record: `List<Configuration> configs`, `long evaluated`, `long valid`

- [x] **T2.7** Implement `model/InvalidPlacementException.java`
  - Extends `RuntimeException`; single constructor taking a `String message`

---

## Phase 3 — Orientation Cache (Java)

> Depends on T2.1, T2.2, T2.3.

- [x] **T3.1** Implement `engine/OrientationCache.java` — skeleton with private base arrays
  - Define `SHELTER_BASE` as the 11×9 `char[][]` from the requirements
  - Define `KITCHEN_BASE` as the 6×4 `char[][]` from the requirements
  - Private helpers: `rotate90(Orientation, String)`, `mirror(Orientation, String)`, `extractMask(char[][])`
  - Rotation formula: `result[c][rows-1-r] = src[r][c]`
  - Mirror formula: `result[r][cols-1-c] = src[r][c]`

- [x] **T3.2** Generate and cache all 8 Shelter orientations
  - Chain: R0 → R90 → R180 → R270; R0 → mirror → R0M → R90M → R180M → R270M
  - Expose via `public List<Orientation> getShelterOrientations()`

- [x] **T3.3** Generate and cache all 4 Kitchen orientations
  - Chain: R0 → R90 → R180 → R270 (no mirror — mirroring produces duplicates)
  - Verify programmatically that mirrored masks match a rotation; log warning if they differ (unexpected)
  - Expose via `public List<Orientation> getKitchenOrientations()`

- [x] **T3.4** Generate and cache 2 Bathroom orientations
  - Portrait: all-`'B'` 8×4 array; Landscape: all-`'B'` 4×8 array
  - Expose via `public List<Orientation> getBathroomOrientations()`

- [x] **T3.5** Unit test written (`OrientationCacheTest.java`) — deferred to run after Phase 4 resolves `Grid.COLS` compile dependency

---

## Phase 4 — Grid (Java)

> Depends on T2.3, T2.4.

- [x] **T4.1** Implement `engine/Grid.java`
  - Constants: `ROWS = 21`, `COLS = 27`
  - Backing store: `char[ROWS][COLS]`, all initialised to `'0'`
  - `place(PlacedObject)` — writes non-`'0'` cells from `solidCells` array
  - `remove(PlacedObject)` — resets those same cells to `'0'`
  - `isEmpty(int r, int c)` — returns `cells[r][c] == '0'`
  - `getLabel(int r, int c)` — returns `cells[r][c]`
  - `inBounds(int r, int c)` — returns `r >= 0 && r < ROWS && c >= 0 && c < COLS`
  - `toFlatString()` — concatenates all 567 chars row-major into a `String`

- [x] **T4.2** Unit test `Grid`:
  - `place` then `remove` round-trips to empty state
  - `toFlatString()` length is 567; two identical placements produce the same string; different placements produce different strings
  - `isEmpty` / `inBounds` boundary conditions (row 0, row 20, col 0, col 26, and just outside)

---

## Phase 5 — Constraint Engine (Java)

> Depends on T2.4, T4.1.

- [x] **T5.1** Implement `engine/ConstraintChecker.java` (all-static)
  - `checkBounds(Orientation, int topR, int topC)` — true if all solid cells fall within `[0,ROWS) × [0,COLS)`
  - `checkBuffer(int[] candidateSolids, int[] bufferedSolids, int dist)` — true (no violation) if every candidate–source pair has Chebyshev distance > `dist`
  - `checkNoOverlap(Grid, Orientation, int topR, int topC)` — true if no solid cell in the orientation lands on a non-`'0'` grid cell

- [x] **T5.2** Implement `engine/BufferZone.java`
  - Static factory: `build(List<int[]> solidCells, int bufferDist)` — marks all in-bounds cells within Chebyshev distance in `exclusionZone[ROWS][COLS]`
  - `isExcluded(int r, int c)` — direct lookup
  - `violates(List<int[]> candidateSolids)` — returns true if any candidate cell is excluded; near-edge cells additionally checked via direct Chebyshev math against `sourceSolids` to enforce the infinite-buffer-at-edge rule

- [x] **T5.3** Implement `engine/BfsPathFinder.java`
  - Fields: `int[] visitedGen` (size `ROWS*COLS`, init 0), `int currentGen`, `boolean[] targetAdj` (size `ROWS*COLS`)
  - `buildTargetAdjacency(Grid, int[] targetSolids)` — marks cells orthogonally adjacent to each target solid cell that are empty and in-bounds
  - `shortestPath(Grid, int[] sourceSolids, int[] targetSolids)`:
    1. Increment `currentGen`
    2. Call `buildTargetAdjacency`; if any seed cell (adjacent to source solid) is already in `targetAdj`, return 0
    3. BFS with level tracking; return level when any dequeued cell's 4-directional neighbour hits `targetAdj`; return -1 if queue empties
    4. Clear `targetAdj` entries written this call

- [x] **T5.4** Unit tests for constraint engine:
  - `checkBounds`: object at (0,0) fits; object at position that would overflow row 20 or col 26 fails
  - `checkBuffer`: two cells at Chebyshev distance exactly 3 — should pass for d=3; at distance 2 should fail for d=3
  - `checkBuffer`: near-edge scenario where the logical buffer extends off-grid — place a Bathroom at col 0, check that a Kitchen at col 2 fails the d=3 check
  - `BfsPathFinder`: simple 5-cell corridor — returns correct path length; blocked corridor — returns -1; source directly adjacent to target — returns 0

---

## Phase 6 — Fixed Placements (Java)

> Depends on T2.2, T2.3, T2.4, T3.1–T3.4, T4.1, T5.1, T5.2.

- [x] **T6.1** Implement `engine/FixedPlacements.java`
  - Fields: `Optional<PlacedObject> bathroom`, `Optional<PlacedObject> kitchen`, `List<PlacedObject> shelters`
  - `NONE` constant
  - Package-private augmentation methods: `withBathroom(PlacedObject)`, `withKitchen(PlacedObject)`, `withShelter(PlacedObject)` (appends to shelter list)
  - Inner `Builder` class with `bathroom(String, int, int)`, `kitchen(String, int, int)`, `shelter(String, int, int)` methods
  - `build()` validates: R6 bounds, R1 bathroom buffer vs kitchen + fixed shelters, R2 kitchen buffer vs bathroom + fixed shelters, R7 no overlap among all fixed objects; throws `InvalidPlacementException` with descriptive message on any violation
  - Fixed shelters are sorted by canonical key at `build()` time

- [x] **T6.2** Unit tests for `FixedPlacements`:
  - Valid single-object fixed set builds without exception
  - `build()` throws `InvalidPlacementException` when bathroom and kitchen are within 3 Chebyshev distance
  - `build()` throws when a fixed shelter overlaps with the bathroom
  - `withBathroom()` / `withKitchen()` / `withShelter()` return new objects leaving original unchanged
  - Fixed shelters are sorted by canonical key

---

## Phase 7 — Solver (Java)

> Depends on Phase 5 complete, Phase 6 complete, T2.5, T2.6.

- [ ] **T7.1** Implement `engine/Solver.java` — skeleton and `call()` startup sequence
  - Constructor asserts `taskFixed.bathroom` is present; throws otherwise
  - `call()`: place all `taskFixed` objects onto grid, build `BathroomBufferZone`
  - If `taskFixed.kitchen` present, build `KitchenBufferZone` and go directly to shelter placement
  - Otherwise call `placeKitchen(bathBuffer)`

- [ ] **T7.2** Implement `placeKitchen()` — Kitchen enumeration with pruning
  - Iterate all Kitchen orientations × all grid positions
  - Prune: bounds check → R1 Chebyshev (Kitchen vs Bathroom via `BathroomBufferZone.violates()`)
  - Build `KitchenBufferZone`; call `placeShelters(0, kit, bathBuffer, kitBuffer, new PlacedObject[4])`
  - On return (backtrack), release `KitchenBufferZone`

- [ ] **T7.3** Implement `placeShelters()` — recursive shelter placement with pruning
  - Base case (idx == 4): invoke leaf validation + scoring
  - Per candidate:
    1. Bounds check
    2. R1: `BathroomBufferZone.violates(shelter.solidCells)`
    3. R2: `KitchenBufferZone.violates(shelter.solidCells)`
    4. R7: `ConstraintChecker.checkNoOverlap(grid, orientation, row, col)`
    5. Canonical key ≥ `shelters[idx-1].canonicalKey()` (skip if idx == 0 or key equal-or-greater)
    6. If all pass: place, recurse, remove

- [ ] **T7.4** Implement leaf validation (`computeScore`) — BFS paths + dedup + store
  - Run `BfsPathFinder.shortestPath` for each of the 8 courtyard→bathroom and courtyard→kitchenRoom pairs
  - If any returns -1, configuration is invalid (pruned)
  - Sum all 8 lengths → `score`
  - Compute `grid.toFlatString()`; if key already in `seen`, skip
  - Add key to `seen`, build `Configuration`, add to `results`

- [ ] **T7.5** Unit/integration test for `Solver` with a simple known-valid fixed configuration
  - Fix all 6 objects to positions that satisfy all rules (construct manually from the domain)
  - Run `Solver.call()` — expect at least 1 result
  - Verify the returned configuration's score matches a hand-computed expected value
  - Verify the `seen` set prevents duplicate results

---

## Phase 8 — Parallel Solver (Java)

> Depends on T7.1–T7.4, T2.6.

- [ ] **T8.1** Implement `engine/ParallelSolver.java` — `buildTasks()` with adaptive partitioning
  - Determine partition level from `fixed`: bathroom unfixed → partition over Bathroom placements; bathroom fixed + kitchen unfixed → partition over Kitchen placements; both fixed → partition over first Shelter placements; all fixed → single task
  - For each partition placement, create a `Solver` with `fixed.withBathroom(...)` / `withKitchen(...)` / `withShelter(...)` as appropriate

- [ ] **T8.2** Implement `solve()` and `collectWithWorkingCopy()`
  - Construct `ExecutorService` (fixed thread pool)
  - Submit all tasks via `ExecutorCompletionService`
  - `collectWithWorkingCopy()`: as each task completes via `ecs.take()`, append its `configs` to accumulated list; every `WORKING_COPY_INTERVAL` (20) new valid configs, call `writeWorkingCopy()`
  - Shutdown pool; return merged `SolverResult`

- [ ] **T8.3** Implement `writeWorkingCopy()`
  - Serialize accumulated configs (unsorted, no rank field) to JSON at `workingPath`
  - Overwrite on each call

- [ ] **T8.4** Integration test: run `ParallelSolver` with 2 threads on a fully-fixed 6-object config
  - Verify it produces the same result as a single `Solver` call for the same fixed input
  - Verify working copy file is written if `workingPath` is set

---

## Phase 9 — Output Serializer (Java)

> Depends on T2.4, T2.5, T7.1.

- [ ] **T9.1** Add Gson to `pom.xml` dependency block (if not done in T1.1)

- [ ] **T9.2** Implement `output/JsonSerializer.java`
  - `write(List<Configuration> configs, long evaluated, long valid, long runtimeMs, String outputPath)`
  - Top-level structure: `{ "stats": {...}, "configurations": [...] }`
  - Stats block: `evaluated`, `valid`, `runtimeMs`
  - Each configuration: `rank`, `score`, `objects` array
  - Each object: `type`, `index` (1-based position in `objects` list, assigned here — not from model), `orientationId`, `topLeftRow`, `topLeftCol`, `cells` array of `{row, col, label}` for all solid cells
  - Working copy variant (called by `ParallelSolver`): omits `rank` field, writes only `configurations` array (no stats)

- [ ] **T9.3** Unit test `JsonSerializer`:
  - Output is valid JSON (parse round-trip with Gson)
  - `index` is 1-based and matches object's position in the list
  - `stats` block values match the inputs

---

## Phase 10 — Main Entry Point (Java)

> Depends on all Phase 7, 8, 9 tasks.

- [ ] **T10.1** Implement `Main.java` CLI argument parsing
  - Positional: `<output.json>` (default: `output.json`)
  - `--threads N` (default: `Runtime.getRuntime().availableProcessors()`)
  - `--limit N` (default: unlimited — note: wire `--limit` through to `Solver` stopping condition)
  - `--fix bathroom <orientId> <row> <col>`
  - `--fix kitchen <orientId> <row> <col>`
  - `--fix shelter <orientId> <row> <col>` (repeatable)
  - Print usage and exit on parse error

- [ ] **T10.2** Implement `Main.java` execution sequence
  1. Instantiate `OrientationCache`
  2. Build `FixedPlacements` via builder; catch `InvalidPlacementException`, print error and exit
  3. Instantiate `ParallelSolver(cache, fixed, threadCount, "output.working.json")`
  4. Record start time; call `solve()`; record elapsed time
  5. Sort merged results ascending by score; assign 1-indexed ranks (ties share rank)
  6. Serialize final output via `JsonSerializer.write(...)`
  7. Delete `output.working.json` if it exists
  8. Print stats to stdout: threads, fixed objects, evaluated, valid, runtime

- [ ] **T10.3** Wire `--limit N` into the search: `Solver` should check an `AtomicLong` shared counter across tasks and stop enumerating once the limit is reached

- [ ] **T10.4** Smoke test: run `java -jar solver.jar --fix bathroom Portrait 0 0 --fix kitchen R0 5 0 --limit 1` and confirm it produces a valid JSON output file with at least one configuration (or zero with a clear stats output), and no working copy remains

---

## Phase 11 — Python Renderer

> Depends on T1.2, T1.4. Independent of all Java phases.

- [ ] **T11.1** Implement `renderer.py` — constants and data structures
  - `CELL_PX = 20`, `ROWS = 21`, `COLS = 27`
  - `FILL` dict mapping label chars to hex colors (per design §3.2)
  - `ROOM_LABELS = frozenset('abcde')`
  - `CELL_BORDER_COLOR = '#5a5a5a'`, `ROOM_BORDER_COLOR = '#000000'`

- [ ] **T11.2** Implement `build_label_grid(config)` in `renderer.py`
  - Returns 21×27 nested list of chars, defaulting to `'0'`
  - Iterates `config['objects']`, reads each object's `cells` list, writes `label` at `[row][col]`

- [ ] **T11.3** Implement `draw_cells(ax, label_grid)` in `renderer.py`
  - Pass 1: non-room cells — `Rectangle` with facecolor from `FILL`, edgecolor `#5a5a5a`, linewidth 1
  - Pass 2: room cells — `Rectangle` with facecolor `#ffffff`, edgecolor `none`, linewidth 0
  - Rectangle position: `(col * CELL_PX, row * CELL_PX)`, size `CELL_PX × CELL_PX`

- [ ] **T11.4** Implement `draw_room_borders(ax, config, label_grid)` in `renderer.py`
  - Group cells by `(object_index, label)` where label is in `ROOM_LABELS`
  - For each group, compute `min_row`, `min_col`, `max_row`, `max_col`
  - Add a `Rectangle` at `(min_col * CELL_PX, min_row * CELL_PX)` with `width = (max_col - min_col + 1) * CELL_PX`, `height = (max_row - min_row + 1) * CELL_PX`, `fill=False`, `edgecolor='#000000'`, `linewidth=3`

- [ ] **T11.5** Implement `render_configuration(config, output_path=None)` in `renderer.py`
  - Create figure + axes
  - Call `build_label_grid` → `draw_cells` → `draw_room_borders`
  - Set title: `f"Rank {config['rank']}  |  Score {config['score']}"`
  - Set axis limits: x `[0, COLS * CELL_PX]`, y `[0, ROWS * CELL_PX]`; invert y-axis
  - If `output_path` given, `plt.savefig(output_path, dpi=96, bbox_inches='tight')`; else `plt.show()`

---

## Phase 12 — Python Visualizer CLI

> Depends on T11.1–T11.5.

- [ ] **T12.1** Implement `visualizer.py` — argument parsing via `argparse`
  - Positional: `output.json` path
  - Mutually exclusive group: `--rank <n>`, `--top <n>`, `--all`
  - Optional: `--export-dir <dir>` (required when `--all` is given; used with `--top` to save files)

- [ ] **T12.2** Implement `--rank <n>` mode
  - Load JSON, find configuration with `rank == n`, render it
  - If not found, print error and exit

- [ ] **T12.3** Implement `--top <n>` mode
  - Load JSON, take first `min(n, total)` configurations by rank, render each
  - If `--export-dir` given, save as `rank_<rank>.png` in that directory

- [ ] **T12.4** Implement `--all` mode
  - Render every configuration; `--export-dir` is required for this mode

- [ ] **T12.5** Manual smoke test: run solver to produce a small `output.json` (use `--limit 3`), then run `python visualizer.py output.json --top 3 --export-dir ./out/` and visually inspect the three PNG files for correctness

---

## Phase 13 — Integration & Validation

> Depends on all previous phases.

- [ ] **T13.1** End-to-end run with `--limit 5` (no fixed objects)
  - Confirm JSON output is well-formed and contains exactly 5 configurations
  - Confirm stats block is present and `valid == 5`
  - Confirm working copy is deleted on clean exit

- [ ] **T13.2** Verify uniqueness invariant: for the 5 results from T13.1, confirm no two `grid.toFlatString()` values collide (re-derive from cells in JSON)

- [ ] **T13.3** Verify scoring correctness: for one configuration from T13.1, manually compute the 8 BFS path lengths from the JSON cell maps and confirm they sum to the reported score

- [ ] **T13.4** Verify buffer rules: for one configuration from T13.1, confirm minimum Chebyshev distance between Bathroom solid cells and any other object's solid cells is ≥ 3; and Kitchen solid cells to any other is ≥ 2

- [ ] **T13.5** Verify path rules: for one configuration from T13.1, confirm by inspection (or script) that each Shelter's courtyard has a 4-directional empty-cell path to both the Bathroom and the Kitchen room zone

- [ ] **T13.6** Test `--fix` with an invalid placement: provide a bathroom and kitchen within distance 2 — confirm solver exits with a clear `InvalidPlacementException` message before starting the search

- [ ] **T13.7** Test `--limit` early exit: run with `--limit 1`, confirm it stops after finding 1 valid config and that the stats `evaluated` count is plausible

- [ ] **T13.8** Test parallelism: run the same fixed config with `--threads 1` and `--threads 4`; confirm both produce the same set of configurations (same `toFlatString()` keys, same scores)

---

## Phase 14 — READMEs

> Depends on all phases complete and passing.

- [ ] **T14.1** Write `solver/README.md`:
  - Prerequisites (Java 17+, Maven 3.8+)
  - Build: `mvn package -f solver/pom.xml`
  - Run: `java -jar solver/target/solver.jar <output.json> [options]`
  - All CLI flags documented with examples
  - Output file format summary

- [ ] **T14.2** Write `visualizer/README.md`:
  - Prerequisites (Python 3.10+)
  - Install: `pip install -r visualizer/requirements.txt`
  - Run examples for `--rank`, `--top`, `--all`
  - Notes on `--export-dir`
