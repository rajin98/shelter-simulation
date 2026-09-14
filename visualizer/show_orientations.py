import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches

# Same constants as renderer.py
CELL_PX = 20
FILL = {
    '0': '#ffffff',
    'a': '#dddddd', 'b': '#dddddd', 'c': '#dddddd', 'd': '#dddddd', 'e': '#dddddd',
    '3': '#e4e7dc',
    '2': '#eacbb7',
    '4': '#c78888',
    'B': '#9e8988',
}
ROOM_LABELS = frozenset('abcde')
CELL_BORDER_COLOR = '#5a5a5a'
ROOM_BORDER_COLOR = '#000000'
SHELTER_FILLS = ['#b8b8b8', '#cacaca', '#dcdcdc', '#eeeeee']

SHELTER_BASE = [
    ['a','a','a','0','0','0','b','b','b'],
    ['a','a','a','2','2','2','b','b','b'],
    ['a','a','a','2','2','2','b','b','b'],
    ['c','c','c','2','3','3','3','3','3'],
    ['c','c','c','2','3','3','3','3','3'],
    ['c','c','c','2','3','3','3','3','3'],
    ['0','2','2','2','3','3','3','3','3'],
    ['0','2','2','2','3','3','3','3','3'],
    ['d','d','d','e','e','e','0','0','0'],
    ['d','d','d','e','e','e','0','0','0'],
    ['d','d','d','e','e','e','0','0','0'],
]


def rotate90(grid):
    rows, cols = len(grid), len(grid[0])
    result = [[None] * rows for _ in range(cols)]
    for r in range(rows):
        for c in range(cols):
            result[c][rows - 1 - r] = grid[r][c]
    return result


def mirror(grid):
    return [row[::-1] for row in grid]


def make_orientations():
    r0    = [row[:] for row in SHELTER_BASE]
    r90   = rotate90(r0)
    r180  = rotate90(r90)
    r270  = rotate90(r180)
    r0m   = mirror(r0)
    r90m  = rotate90(r0m)
    r180m = rotate90(r90m)
    r270m = rotate90(r180m)
    return [
        ('R0',    r0),
        ('R90',   r90),
        ('R180',  r180),
        ('R270',  r270),
        ('R0M',   r0m),
        ('R90M',  r90m),
        ('R180M', r180m),
        ('R270M', r270m),
    ]


def draw_orientation(ax, grid, title):
    rows, cols = len(grid), len(grid[0])
    w, h = cols * CELL_PX, rows * CELL_PX

    # 1. Non-room cells (same as renderer draw_cells)
    for r in range(rows):
        for c in range(cols):
            ch = grid[r][c]
            if ch not in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor=FILL.get(ch, '#ffffff'),
                    edgecolor='none',
                ))

    # 2. Grid lines (same as renderer draw_grid_lines)
    ax.hlines([r * CELL_PX for r in range(rows + 1)], 0, w,
              colors=CELL_BORDER_COLOR, linewidths=0.5)
    ax.vlines([c * CELL_PX for c in range(cols + 1)], 0, h,
              colors=CELL_BORDER_COLOR, linewidths=0.5)

    # 3. Room fills — white base (same as renderer draw_room_fills)
    for r in range(rows):
        for c in range(cols):
            if grid[r][c] in ROOM_LABELS:
                ax.add_patch(patches.Rectangle(
                    (c * CELL_PX, r * CELL_PX), CELL_PX, CELL_PX,
                    facecolor='#ffffff',
                    edgecolor='none',
                ))

    # 4. Room bounding boxes per label (same as renderer draw_room_borders)
    fill = SHELTER_FILLS[0]
    for label in 'abcde':
        cells = [(r, c) for r in range(rows) for c in range(cols) if grid[r][c] == label]
        if not cells:
            continue
        min_r = min(r for r, _ in cells)
        max_r = max(r for r, _ in cells)
        min_c = min(c for _, c in cells)
        max_c = max(c for _, c in cells)
        ax.add_patch(patches.Rectangle(
            (min_c * CELL_PX, min_r * CELL_PX),
            (max_c - min_c + 1) * CELL_PX,
            (max_r - min_r + 1) * CELL_PX,
            facecolor=fill,
            edgecolor=ROOM_BORDER_COLOR,
            linewidth=3,
            zorder=10,
        ))

    ax.set_xlim(0, w)
    ax.set_ylim(0, h)
    ax.set_aspect('equal')
    ax.invert_yaxis()
    ax.axis('off')
    ax.set_title(f'{title}  ({rows}r × {cols}c)', fontsize=8, pad=3)


def main():
    orientations = make_orientations()
    fig, axes = plt.subplots(2, 4, figsize=(18, 10))
    fig.suptitle('Shelter — all 8 orientations', fontsize=13, fontweight='bold', y=1.01)

    for ax, (label, grid) in zip(axes.flat, orientations):
        draw_orientation(ax, grid, label)

    plt.tight_layout()
    out = 'shelter_orientations.png'
    plt.savefig(out, dpi=110, bbox_inches='tight')
    print(f'Saved {out}')


if __name__ == '__main__':
    main()
