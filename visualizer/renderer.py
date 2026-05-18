import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

CELL_PX: int = 20
ROWS: int = 21
COLS: int = 27

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


# T11.2
def build_label_grid(config: dict) -> list[list[str]]:
    grid = [['0'] * COLS for _ in range(ROWS)]
    for obj in config['objects']:
        for cell in obj['cells']:
            grid[cell['row']][cell['col']] = cell['label']
    return grid


# T11.3
def draw_cells(ax, label_grid: list[list[str]]) -> None:
    # Pass 1: non-room cells with 1px border
    for r in range(ROWS):
        for c in range(COLS):
            label = label_grid[r][c]
            if label not in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor=FILL.get(label, '#ffffff'),
                    edgecolor=CELL_BORDER_COLOR,
                    linewidth=1,
                ))
    # Pass 2: room cells — fill only, no individual border
    for r in range(ROWS):
        for c in range(COLS):
            label = label_grid[r][c]
            if label in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor='#ffffff',
                    edgecolor='none',
                    linewidth=0,
                ))


# T11.4
def draw_room_borders(ax, config: dict, label_grid: list[list[str]]) -> None:
    # Group cells by (object index, room label).
    groups: dict[tuple, list[dict]] = {}
    for obj in config['objects']:
        idx = obj['index']
        for cell in obj['cells']:
            lbl = cell['label']
            if lbl in ROOM_LABELS:
                key = (idx, lbl)
                groups.setdefault(key, []).append(cell)

    for cells in groups.values():
        min_row = min(c['row'] for c in cells)
        max_row = max(c['row'] for c in cells)
        min_col = min(c['col'] for c in cells)
        max_col = max(c['col'] for c in cells)
        ax.add_patch(patches.Rectangle(
            (min_col * CELL_PX, min_row * CELL_PX),
            (max_col - min_col + 1) * CELL_PX,
            (max_row - min_row + 1) * CELL_PX,
            fill=False,
            edgecolor=ROOM_BORDER_COLOR,
            linewidth=3,
        ))


# T11.5
def render_configuration(config: dict, output_path: str | None = None) -> None:
    fig, ax = plt.subplots(figsize=(COLS * CELL_PX / 72, ROWS * CELL_PX / 72))
    ax.set_aspect('equal')
    ax.set_xlim(0, COLS * CELL_PX)
    ax.set_ylim(0, ROWS * CELL_PX)
    ax.invert_yaxis()
    ax.axis('off')

    label_grid = build_label_grid(config)
    draw_cells(ax, label_grid)
    draw_room_borders(ax, config, label_grid)

    rank = config.get('rank', '?')
    score = config.get('score', '?')
    ax.set_title(f'Rank {rank}  |  Score {score}', fontsize=9, pad=4)

    plt.tight_layout()
    if output_path:
        plt.savefig(output_path, dpi=96, bbox_inches='tight')
        plt.close(fig)
    else:
        plt.show()
