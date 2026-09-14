import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

CELL_PX: int = 20
ROWS: int = 21
COLS: int = 27

FILL: dict[str, str] = {
    '0': '#ffffff',
    'a': '#dddddd', 'b': '#dddddd', 'c': '#dddddd', 'd': '#dddddd', 'e': '#dddddd',
    '3': '#e4e7dc',
    '2': '#eacbb7', '5': '#eacbb7',
    '4': '#c78888',
    'B': '#9e8988',
}
ROOM_LABELS: frozenset[str] = frozenset('abcde')
CELL_BORDER_COLOR: str = '#5a5a5a'
ROOM_BORDER_COLOR: str = '#000000'
GARDEN_COLOR: str = "#bbc59d"
SHELTER_FILLS: list[str] = ['#b8b8b8', '#cacaca', '#dcdcdc', '#eeeeee']


def _cells(obj: dict, orientations: dict[str, list[str]]) -> list[tuple[int, int, str]]:
    rows = orientations[f"{obj['type']}/{obj['orientationId']}"]
    top_r, top_c = obj['topLeftRow'], obj['topLeftCol']
    return [
        (top_r + dr, top_c + dc, ch)
        for dr, row in enumerate(rows)
        for dc, ch in enumerate(row)
        if ch != '0'
    ]


# T11.2
def build_label_grid(config: dict, orientations: dict[str, list[str]]) -> list[list[str]]:
    grid = [['0'] * COLS for _ in range(ROWS)]
    for obj in config['objects']:
        for r, c, label in _cells(obj, orientations):
            grid[r][c] = label
    return grid


# T11.3
def draw_cells(ax, label_grid: list[list[str]]) -> None:
    for r in range(ROWS):
        for c in range(COLS):
            label = label_grid[r][c]
            if label not in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor=FILL.get(label, '#ffffff'),
                    edgecolor='none',
                ))


def draw_room_fills(ax, label_grid: list[list[str]]) -> None:
    for r in range(ROWS):
        for c in range(COLS):
            if label_grid[r][c] in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor='#ffffff',
                    edgecolor='none',
                ))


# T11.4
def draw_room_borders(ax, config: dict, orientations: dict[str, list[str]]) -> None:
    groups: dict[tuple, list[tuple[int, int]]] = {}
    for obj in config['objects']:
        idx = obj['index']
        for r, c, label in _cells(obj, orientations):
            if label in ROOM_LABELS:
                groups.setdefault((idx, label), []).append((r, c))

    for (idx, _label), cells in groups.items():
        fill = SHELTER_FILLS[(idx - 1) % len(SHELTER_FILLS)]
        min_row = min(r for r, _ in cells)
        max_row = max(r for r, _ in cells)
        min_col = min(c for _, c in cells)
        max_col = max(c for _, c in cells)
        ax.add_patch(patches.Rectangle(
            (min_col * CELL_PX, min_row * CELL_PX),
            (max_col - min_col + 1) * CELL_PX,
            (max_row - min_row + 1) * CELL_PX,
            facecolor=fill,
            edgecolor=ROOM_BORDER_COLOR,
            linewidth=3,
            zorder=10,
        ))


def draw_grid_lines(ax) -> None:
    ax.hlines([r * CELL_PX for r in range(ROWS + 1)], 0, COLS * CELL_PX,
              colors=CELL_BORDER_COLOR, linewidths=1)
    ax.vlines([c * CELL_PX for c in range(COLS + 1)], 0, ROWS * CELL_PX,
              colors=CELL_BORDER_COLOR, linewidths=1)


def draw_kitchen_markers(ax, config: dict, orientations: dict[str, list[str]]) -> None:
    for obj in config['objects']:
        if obj['type'] != 'Kitchen':
            continue
        cell_rows = orientations[f"{obj['type']}/{obj['orientationId']}"]
        n_rows, n_cols = len(cell_rows), len(cell_rows[0])
        x0 = obj['topLeftCol'] * CELL_PX
        y0 = obj['topLeftRow'] * CELL_PX
        half = CELL_PX / 2
        if n_rows > n_cols:  # portrait: longer sides are left and right
            for bx in (x0, x0 + n_cols * CELL_PX - half):
                ax.add_patch(patches.Rectangle(
                    (bx, y0), half, n_rows * CELL_PX,
                    facecolor=GARDEN_COLOR, edgecolor='none',
                ))
        else:  # landscape: longer sides are top and bottom
            for by in (y0, y0 + n_rows * CELL_PX - half):
                ax.add_patch(patches.Rectangle(
                    (x0, by), n_cols * CELL_PX, half,
                    facecolor=GARDEN_COLOR, edgecolor='none',
                ))


# T11.5
GRID_BORDER_PX: int = 2

def render_configuration(config: dict, orientations: dict[str, list[str]],
                          output_path: str | None = None) -> None:
    grid_w = COLS * CELL_PX
    grid_h = ROWS * CELL_PX
    b = GRID_BORDER_PX
    fig, ax = plt.subplots(figsize=((grid_w + b * 2) / 72, (grid_h + b * 2) / 72))
    ax.set_aspect('equal')
    ax.set_xlim(-b, grid_w + b)
    ax.set_ylim(-b, grid_h + b)
    ax.invert_yaxis()
    ax.axis('off')
    ax.add_patch(patches.Rectangle(
        (-b, -b), grid_w + b * 2, grid_h + b * 2,
        facecolor='#000000', edgecolor='none',
    ))

    label_grid = build_label_grid(config, orientations)
    draw_cells(ax, label_grid)
    draw_grid_lines(ax)
    draw_room_fills(ax, label_grid)
    draw_kitchen_markers(ax, config, orientations)
    draw_room_borders(ax, config, orientations)

    rank  = config.get('rank', '?')
    score = config.get('score')
    bath  = config.get('bathroomDistance')
    kit   = config.get('kitchenDistance')
    score_str = f'{score:.3f}' if isinstance(score, (int, float)) else '?'
    dist_str  = (f'  |  Bath {bath}  Kit {kit}' if bath is not None and kit is not None else '')
    ax.set_title(f'Rank {rank}  |  Score {score_str}{dist_str}', fontsize=9, pad=4)

    plt.tight_layout()
    if output_path:
        plt.savefig(output_path, dpi=96, bbox_inches='tight')
        plt.close(fig)
    else:
        plt.show()
