# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Grid Object Placement Solver — places 6 objects (4 Shelters, 1 Bathroom, 1 Kitchen) onto a 27×21 grid, enumerates all unique valid configurations, and ranks them by a proximity score. Stack: Java (solver engine) + Python (visualizer).

## Project Structure

```
shelter-sim/
├── CLAUDE.md
├── .gitignore
├── docs/
│   ├── requirements.md       # problem specification
│   ├── design.md             # technical design (v1.1)
│   └── task.md               # sequential task checklist
├── solver/                   # Java Maven project
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/sheltersim/
│       │   ├── Main.java
│       │   ├── model/        # CellLabel, ObjectType, Orientation, PlacedObject, Configuration, ...
│       │   ├── engine/       # OrientationCache, Grid, BufferZone, Solver, ParallelSolver, ...
│       │   └── output/       # JsonSerializer
│       └── test/java/com/sheltersim/
└── visualizer/               # Python project
    ├── requirements.txt
    ├── .venv/                # virtual environment (not committed)
    ├── visualizer.py         # CLI entry point
    └── renderer.py           # matplotlib grid rendering
```

## Commands

### Prerequisites
- Java 25
- Maven 3.8+
- Python 3.14 (installed at `visualizer/.venv`)

### Java Solver (run from `solver/`)

```bash
mvn compile                        # compile only
mvn test                           # compile + run unit tests
mvn package                        # produce solver/target/solver.jar + lib/
java -jar target/solver.jar output.json --threads 8
java -jar target/solver.jar output.json --fix bathroom Portrait 2 3 --limit 10
```

### Python Visualizer (run from `visualizer/`)

```bash
# activate venv first (Windows)
.venv\Scripts\activate
# or Unix
source .venv/bin/activate

pip install -r requirements.txt    # first-time setup (venv already created at visualizer/.venv)

python visualizer.py output.json --rank 1
python visualizer.py output.json --top 5 --export-dir ./out/
python visualizer.py output.json --all --export-dir ./out/
```

### Docs

Reference documents live in `docs/`:
- `docs/requirements.md` — problem specification
- `docs/design.md` — technical design (v1.1)
- `docs/task.md` — sequential task checklist

## Architecture

### Data Flow

```
Java Solver → JSON output file → Python Visualizer → PNG images
```

### Java Solver (`solver/`)

Entry point enumerates all object placement combinations, applies constraint pruning in order (bounds → buffers → overlap → paths), collects valid configurations, scores and sorts them, serializes to JSON.

**Object model** — every object type maintains two parallel representations throughout all transformations:
1. `boolean[][] placementMask` — solid cells only, used for collision/buffer checking
2. `char[][] cellMap` — preserves room labels (a–e, 2, 3 for Shelter; 4, 5 for Kitchen), used for visualization and BFS scoring

**Pre-computed orientations** — at solver startup, all orientations for each type are pre-computed and cached (both representations). The search loop reads from this cache; it never applies rotation/mirror arithmetic at search time.

| Object | Orientations | Notes |
|---|---|---|
| Shelter | 8 (4 rotations × 2 mirror states) | 11×9 footprint; `0` cells are transparent (may be overlapped by other solids) |
| Kitchen | 4 (rotations only) | 6×4 footprint; mirroring produces duplicates due to horizontal symmetry |
| Bathroom | 2 (portrait 8×4, landscape 4×8) | Fully solid rectangle |

**Uniqueness** — the 4 Shelter instances are interchangeable. Before storing or comparing a configuration, sort the 4 Shelter placements by a deterministic key (top-left corner in row-major order) to form the canonical form.

### Python Visualizer (`visualizer/`)

Reads the solver JSON output; renders configurations using the granular cell map (room labels), not bounding boxes. Each label (a, b, c, d, e, 2, 3, 4, 5, empty) must use a distinct color. Supports: render by rank, render top-N, export to PNG.

## Key Domain Rules

### Buffer Rules (Chebyshev distance, 8-directional)
- **R1 Bathroom buffer:** All other objects' solid cells must be ≥ 3 Chebyshev distance from any Bathroom solid cell.
- **R2 Kitchen buffer:** All other objects' solid cells must be ≥ 2 Chebyshev distance from any Kitchen solid cell.
- Buffer zones extend beyond the grid boundary — proximity to an edge does not reduce the effective buffer.

### Path Rules (BFS/DFS, 4-directional only)
- **R3:** Each Shelter's courtyard (`3` cells) must have an empty-cell path to the Kitchen room zone (`4` cells).
- **R4:** Each Shelter's courtyard must have an empty-cell path to the Bathroom.
- Paths traverse only empty cells (no solid from any object). Buffer zones do not block paths. Diagonal movement is not allowed. Out-of-bounds cells are not traversable.

### Overlap Rule
- Solid cells from different objects must never share a grid position.
- Shelter `0` (transparent) cells may be overlapped by solid cells of other objects.

### Pruning Order (apply in this sequence to fail fast)
1. Grid bounds check (reject immediately)
2. Buffer rule violations (R1, R2)
3. Solid-cell overlap (R7)
4. Path connectivity (R3, R4) — most expensive, run last

## Scoring

```
Score = Σ bfs_path_length(Courtyard_i → Bathroom)    for i in 1..4
      + Σ bfs_path_length(Courtyard_i → KitchenRoom)  for i in 1..4
```

`bfs_path_length` counts empty cells traversed (boundary cells of source/target excluded). Lower score = better. The BFS result from path existence checking (R3/R4) is reused directly for scoring at no extra cost.

## JSON Output Schema

```json
{
  "stats": { "evaluated": 1234567, "valid": 89, "runtimeMs": 4521 },
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
          "cells": [{ "row": 3, "col": 5, "label": "a" }]
        }
      ]
    }
  ]
}
```

`index` is 1-based, assigned by the serializer from the object's position in the sorted list — not stored in the model. Stats (`evaluated`, `valid`, `runtimeMs`) appear in a top-level `stats` block.

## Working Copy

During a search run, the solver writes `output.working.json` next to the output file every 20 new valid configurations found (unsorted, no rank field). It is deleted automatically on clean exit. If this file exists when the solver is not running, it is a leftover from an interrupted run — safe to delete or inspect for partial results.

## Grid Coordinate System

- 27 columns × 21 rows, origin (0,0) at top-left, indexed as (row, col).
- All placements are 0-indexed. Out-of-bounds is not empty space.
