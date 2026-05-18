# Requirements Document: Grid Object Placement Solver

**Version:** 1.0  
**Date:** 2026-05-16  
**Stack:** Java (simulation engine) · Python (visualization)

---

## 1. Problem Summary

Place 6 objects (4 Shelters, 1 Bathroom, 1 Kitchen) onto a 27×21 rectangular grid such that all placement rules are satisfied. Enumerate every unique valid configuration and rank them by a proximity score between service objects (Bathroom, Kitchen) and Shelter courtyards.

---

## 2. Grid Specification

| Property | Value |
|---|---|
| Width (columns) | 27 |
| Height (rows) | 21 |
| Origin | (0, 0) at top-left |
| Coordinate system | (row, col), 0-indexed |
| Out-of-bounds | Not considered empty space |
| Initial state | All cells start as `0` (empty); objects are placed onto this blank grid |

All objects must be placed fully within the grid boundaries. No part of any solid cell (non-empty cell) may fall outside the 27×21 area.

---

## 3. Object Definitions

### 3.1 Shelter (×4)

Four identical instances. Each Shelter is defined by the following 11×9 character grid:

```
aaa000bbb
aaa222bbb
aaa222bbb
ccc233333
ccc233333
ccc233333
022233333
022233333
dddeee000
dddeee000
dddeee000
```

#### Cell Legend

| Symbol | Meaning | Solid? |
|---|---|---|
| `0` | Empty space | No — may overlap with other objects |
| `a` | Room A | Yes |
| `b` | Room B | Yes |
| `c` | Room C | Yes |
| `d` | Room D | Yes |
| `e` | Room E | Yes |
| `2` | Porch | Yes |
| `3` | Courtyard | Yes |

**Key rule:** Cells marked `0` within the Shelter footprint are transparent — other objects' solid cells may occupy those grid positions.

#### Bounding Box

- Footprint: 11 rows × 9 columns (including empty cells)
- Solid bounding box must be determined per orientation after rotation/mirroring

#### Transformations Allowed

All 8 orientations of the Shelter produce distinct solid-cell patterns and are all valid placements:

| ID | Description |
|---|---|
| R0 | Original (0° rotation) |
| R90 | 90° clockwise |
| R180 | 180° rotation |
| R270 | 270° clockwise |
| R0M | Original, horizontally mirrored |
| R90M | 90° CW, mirrored |
| R180M | 180°, mirrored |
| R270M | 270° CW, mirrored |

**Pre-computation requirement:** Both the placement mask and the granular cell map for all 8 orientations must be computed once at solver startup and cached. The search must reference these pre-computed orientations rather than computing transformations on the fly.

#### Internal Structure Requirement

Two representations must be maintained at all times:

1. **Bounding representation** — a transformed bounding box used for placement collision checking.
2. **Granular representation** — the full per-cell room/porch/courtyard map, used for visualization and distance calculations. This must preserve the original cell labels (a–e, 2, 3) after transformation.

---

### 3.2 Bathroom (×1)

A solid 4×8 rectangle. No internal empty cells.

#### Orientations

| Orientation | Dimensions (rows × cols) |
|---|---|
| Portrait | 8 rows × 4 cols |
| Landscape | 4 rows × 8 cols |

The Bathroom has no internal structure beyond its solid footprint.

---

### 3.3 Kitchen (×1)

Defined by the following 6×4 character grid:

```
4444
4444
4444
4444
4444
5555
```

#### Cell Legend

| Symbol | Meaning | Solid? |
|---|---|---|
| `4` | Kitchen room | Yes |
| `5` | Garden | Yes |

All cells are solid (no transparent cells).

#### Transformations Allowed

The Kitchen has **4 distinct orientations** (2 rotations × 2 mirror states that collapse due to symmetry, yielding 4 unique patterns):

| ID | Description |
|---|---|
| R0 | Original — kitchen room on top, garden on bottom |
| R90 | 90° clockwise — garden on left |
| R180 | 180° — kitchen room on bottom, garden on top |
| R270 | 270° clockwise — garden on right |

Mirroring the Kitchen produces orientations already covered by rotation (given the shape's horizontal symmetry), so only 4 orientations are pre-computed. All 4 must be cached at solver startup.

#### Structural Zones

For buffer and path rule enforcement, the Kitchen is divided into two zones:

- **Kitchen room zone** — cells labeled `4`
- **Garden zone** — cells labeled `5`

Rules referencing "kitchen room" apply only to the `4` zone; rules referencing "kitchen" generically apply to the full object footprint.

---

## 4. Placement Rules

### 4.1 Definitions

- **Solid cell:** Any cell in an object that is not marked `0` (empty/transparent).
- **Buffer zone of size N around object X:** The set of all grid cells within a Chebyshev distance (or Manhattan distance — see §4.4 for clarification) of N from any solid cell of X, excluding X's own solid cells.
- **Object boundary:** The outermost ring of solid cells of an object.
- **Path:** A contiguous strip of grid cells, 1 cell wide, containing no solid cells from any object.

### 4.2 Rule Table

| # | Rule | Objects Involved | Constraint |
|---|---|---|---|
| R1 | Bathroom buffer | Bathroom ↔ all other objects | All solid cells of every other object must be ≥ 3 cells (Chebyshev) from all solid cells of the Bathroom. Buffer zone extends beyond the grid boundary — placement near an edge does not reduce the effective buffer |
| R2 | Kitchen buffer | Kitchen ↔ all other objects | All solid cells of every other object must be ≥ 2 cells (Chebyshev) from all solid cells of the Kitchen. Buffer zone extends beyond the grid boundary |
| R3 | Courtyard–Kitchen path | Each Shelter's courtyard (`3` cells) ↔ Kitchen room (`4` cells) | There must exist a 1-cell-wide continuous empty path between each courtyard and the Kitchen room zone |
| R4 | Courtyard–Bathroom path | Each Shelter's courtyard (`3` cells) ↔ Bathroom | There must exist a 1-cell-wide continuous empty path between each courtyard and the Bathroom |
| R5 | Full placement | All 6 objects | Every object must be placed; partial configurations are invalid |
| R6 | Grid containment | All 6 objects | No solid cell of any object may be outside the 27×21 grid |
| R7 | No solid-cell overlap | All 6 objects | No two solid cells from different objects may occupy the same grid position. Empty (`0`) cells of Shelters may overlap with solid cells of other objects |

### 4.3 Buffer Distance Metric

Buffer distances (R1, R2) use **Chebyshev distance** (8-directional; a diagonal step counts as 1). Two cells at (r1, c1) and (r2, c2) have Chebyshev distance `max(|r1−r2|, |c1−c2|)`.

**Buffer zones may extend outside the grid boundary.** When checking whether an object violates a buffer zone, any solid cell of that object within the required Chebyshev distance of the buffered object is a violation — regardless of whether the buffer zone cells that define the exclusion region fall inside or outside the 27×21 grid. In effect, placing an object near the grid edge does not reduce its effective buffer; the exclusion zone simply extends into the (impassable) region beyond the grid.

### 4.4 Path Validity (R3, R4)

A valid path between object A and object B exists if there is at least one connected chain of empty grid cells leading from a cell adjacent to A's solid boundary to a cell adjacent to B's solid boundary, subject to:

- **Movement:** Strictly 4-directional — up, down, left, right only. Diagonal movement is not permitted.
- **Empty cell:** A grid cell containing no solid cell from any placed object. Buffer zones do not block path cells.
- **Out-of-bounds:** Grid cells outside the 27×21 boundary are not considered empty and cannot be part of a path.

Implementation: BFS or DFS flood-fill from the courtyard boundary cells, checking reachability of the target object boundary. No width or shape constraint is imposed.

---

## 5. Uniqueness Definition

A configuration is **unique** if no other valid configuration in the result set is equivalent under the following equivalence relation:

Two configurations are equivalent if and only if:
- The same object instance occupies the same grid cells in both, for all 6 objects, **and**
- The 4 Shelters are treated as interchangeable (i.e., swapping which Shelter instance occupies which position does not produce a new unique configuration).

> **Implication:** The solver must canonicalize configurations by treating the 4 Shelter placements as an unordered set, preventing combinatorial inflation from permuting identical Shelter instances.

---

## 6. Scoring and Sorting

### 6.1 Score Definition

Each valid configuration is assigned a scalar score based on **BFS shortest path length** — the minimum number of empty cells that must be traversed to connect each courtyard to the Bathroom and Kitchen room zone.

```
Score = SUM(bfs_path_length(Courtyard_i, Bathroom))    for i in 1..4
      + SUM(bfs_path_length(Courtyard_i, KitchenRoom)) for i in 1..4
```

Where:
- `bfs_path_length(A, B)` is the count of **empty cells traversed** along the shortest 4-directional path from any cell adjacent to A's solid boundary to any cell adjacent to B's solid boundary. The boundary cells of A and B themselves are not counted.
- `Courtyard_i` is the set of `3`-labeled cells in the i-th placed Shelter.
- `Bathroom` is the full solid footprint of the Bathroom.
- `KitchenRoom` is the set of `4`-labeled cells in the placed Kitchen.
- Movement is strictly 4-directional (up, down, left, right). Diagonal steps are not counted.
- Only empty cells (no solid cell from any object) may be traversed.
- Out-of-bounds cells cannot be traversed.

**Implementation note:** Since BFS is already executed for path existence checking (R3, R4), the shortest path length is available at zero additional cost — BFS naturally yields the minimum step count. The same BFS result used for validation is reused for scoring.

### 6.2 Sort Order

Results are sorted in **ascending order** of Score (lowest score = most desirable = ranked first). The computed score is included in the JSON output for every configuration, so consumers can re-sort or filter independently.

### 6.3 Tie-Breaking

No tie-breaking rule is defined. Configurations with equal scores share the same rank value, and their relative order within that rank is undefined (stable sort preserving generation order is acceptable).

---

## 7. System Architecture

### 7.1 Language Responsibilities

| Component | Language | Responsibility |
|---|---|---|
| Solver engine | Java | Object modeling, transformation generation, placement enumeration, constraint checking, scoring, sorting, output |
| Visualizer | Python | Reading solver output, rendering grid configurations, displaying room-level detail |

### 7.2 Data Flow

```
Java Solver
  └─► Enumerate all valid configurations
  └─► Score and sort
  └─► Serialize to output format (JSON / CSV)
        │
        ▼
Python Visualizer
  └─► Read serialized output
  └─► Render each configuration using granular cell maps
  └─► Display/export images
```

---

## 8. Java Solver — Functional Requirements

### 8.1 Object Model

**FR-J1:** Each object type must have two representations:
- A **placement mask** — a 2D boolean array of solid cells used for collision and bounds checking.
- A **cell map** — a 2D char/enum array preserving the original room labels (a–e, 2, 3, 4, 5), updated correctly for each transformation.

**FR-J2:** Transformations (rotation, mirroring) must be applied to both representations simultaneously and consistently.

**FR-J3:** All orientations for each object type must be pre-computed and cached at solver startup — both the placement mask and the granular cell map. The search loop must reference these cached orientations and must not perform transformation arithmetic at search time.

| Object | Orientations |
|---|---|
| Shelter | 8 (4 rotations × 2 mirror states, all distinct) |
| Kitchen | 4 (rotations only; mirroring produces duplicates due to horizontal symmetry) |
| Bathroom | 2 (portrait 8×4, landscape 4×8) |

**FR-J4:** The 4 Shelter instances must be treated as interchangeable during uniqueness checking. Configuration canonicalization must sort Shelter placements by a deterministic key (e.g., top-left corner in row-major order) before comparison or storage.

### 8.2 Placement Engine

**FR-J5:** The solver must iterate over all combinations of:
- Position (row, col) for each object on the 27×21 grid
- Orientation for each object

**FR-J6:** Pruning must be applied as early as possible:
- Reject out-of-bounds placements immediately.
- Reject placements violating buffer rules (R1, R2) before checking path rules (R3, R4).
- Reject placements where solid cells overlap before any further checks.

**FR-J7:** Path checking (R3, R4) must use a BFS or DFS flood-fill on the current grid state, using strictly 4-directional adjacency (up, down, left, right). Diagonal neighbours are not traversable. The check verifies that at least one empty-cell chain connects the courtyard boundary to the target object boundary. Buffer zones do not block traversal — only solid cells do.

**FR-J8:** The solver must guarantee enumeration of all valid unique configurations without duplication.

### 8.3 Output

**FR-J9:** The solver must serialize each valid configuration including:
- For each object: type, instance ID, orientation ID, top-left grid coordinate, full granular cell map with absolute grid positions.
- The computed score.
- The rank (1-indexed, sorted by ascending score).

**FR-J10:** Output format is **JSON**. Each configuration is an object in a top-level array, structured as follows:

```json
{
  "rank": 1,
  "score": 42.17,
  "objects": [
    {
      "type": "Shelter",
      "instanceId": 1,
      "orientationId": "R90M",
      "topLeftRow": 3,
      "topLeftCol": 5,
      "cells": [
        { "row": 3, "col": 5, "label": "a" },
        ...
      ]
    },
    ...
  ]
}
```

**FR-J11:** The solver must report: total configurations evaluated, total valid configurations found, and wall-clock runtime.

---

## 9. Python Visualizer — Functional Requirements

**FR-P1:** The visualizer must read the solver's output file and render each configuration.

**FR-P2:** Rendering must use the granular cell map (room labels), not the bounding box representation. Each distinct label (a, b, c, d, e, 2, 3, 4, 5, empty) must be rendered in a distinct color.

**FR-P3:** The visualizer must display the rank and score for each configuration.

**FR-P4:** The visualizer must support at minimum:
- Rendering a single configuration by rank number.
- Rendering the top-N configurations.
- Exporting rendered configurations to image files (PNG).

**FR-P5:** Empty grid cells must be visually distinct from the `0` (transparent) cells within object footprints, if these overlap with other objects' cells (i.e., the renderer must show what actually occupies each cell).

---

## 10. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | The solver must complete enumeration in a reasonable time. Given grid size and object count, exhaustive search may be infeasible; pruning strategies (§8.2) are mandatory. |
| NFR-2 | Memory usage must remain bounded; configurations should be streamed/written to disk rather than held fully in memory if the count is large. |
| NFR-3 | The solver output file must be human-readable for debugging purposes. |
| NFR-4 | Code must include inline comments explaining each constraint check. |
| NFR-5 | Both Java and Python components must include a README with build/run instructions. |

---

## 11. Open Questions / Decisions Required

| # | Question | Impact |
|---|---|---|
| OQ-1 | ~~Are two Shelter orientations that produce the same solid cell pattern considered duplicates?~~ All 8 orientations of the Shelter (4 rotations × 2 mirror states) produce distinct solid-cell patterns and are all valid. Orientations must be pre-computed once at startup before the search begins. | Resolved |
| OQ-2 | ~~Does the empty-cell path in R3/R4 need to avoid the buffer zones of other objects, or just avoid solid cells?~~ Paths only need to be free of solid cells. Buffer zones do not block paths. Movement along a path is strictly 4-directional (up, down, left, right) — diagonal movement is not permitted. | Resolved |

---

## 12. Glossary

| Term | Definition |
|---|---|
| Solid cell | A cell in an object's footprint that is not marked `0`; participates in collision and buffer checks |
| Transparent cell | A cell marked `0` in a Shelter's footprint; may be occupied by solid cells of other objects |
| Courtyard | The set of cells labeled `3` in a placed Shelter |
| Bounding box | The minimal rectangle enclosing all solid cells of an object in a given orientation |
| Configuration | A complete assignment of position and orientation for all 6 objects |
| Unique configuration | A configuration not equivalent to any other under Shelter instance permutation |
| Score | The sum of BFS shortest path lengths from each Shelter's courtyard boundary to the Bathroom boundary, plus from each courtyard boundary to the Kitchen room zone boundary — all traversing empty cells with 4-directional movement |
| Chebyshev distance | Max of absolute row and column differences between two cells |
| Manhattan distance | Sum of absolute row and column differences between two cells |
