import argparse
import json
import os
import sys
from collections import defaultdict

from renderer import render_configuration


def load_json(path: str) -> dict:
    try:
        with open(path, encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        sys.exit(f'Error: file not found: {path}')
    except json.JSONDecodeError as e:
        sys.exit(f'Error: invalid JSON in {path}: {e}')


def ensure_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def build_rank_map(configs: list[dict]) -> dict[int, list[dict]]:
    rank_map: dict[int, list[dict]] = defaultdict(list)
    for cfg in configs:
        rank_map[cfg.get('rank', 0)].append(cfg)
    return rank_map


def main() -> None:
    parser = argparse.ArgumentParser(
        prog='visualizer',
        description='Render shelter-sim solver output as PNG images.',
    )
    parser.add_argument('output_json', help='Path to solver output JSON file')

    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--rank', type=int, metavar='N',
                      help='Render the single configuration with this rank')
    mode.add_argument('--top', type=int, metavar='N',
                      help='Render the top N configurations by rank')
    mode.add_argument('--all', action='store_true',
                      help='Render every configuration (requires --export-dir)')

    parser.add_argument('--export-dir', metavar='DIR', default='out',
                        help='Directory to save PNG files (required with --all)')

    args = parser.parse_args()

    if args.all and not args.export_dir:
        parser.error('--all requires --export-dir')

    data = load_json(args.output_json)
    configs = data.get('configurations', [])
    orientations = data.get('orientations', {})
    rank_map = build_rank_map(configs)

    if args.rank is not None:
        # T12.2 — render single rank
        group = rank_map.get(args.rank)
        if not group:
            sys.exit(f'Error: no configuration with rank {args.rank}')
        for idx, cfg in enumerate(group):
            _render(cfg, args.rank, idx, args.export_dir, orientations)

    elif args.top is not None:
        # T12.3 — render top N
        top = configs[:args.top]
        rank_indices: dict[int, int] = defaultdict(int)
        for cfg in top:
            rank = cfg.get('rank', 0)
            _render(cfg, rank, rank_indices[rank], args.export_dir, orientations)
            rank_indices[rank] += 1

    elif args.all:
        # T12.4 — render all
        rank_indices = defaultdict(int)
        for cfg in configs:
            rank = cfg.get('rank', 0)
            _render(cfg, rank, rank_indices[rank], args.export_dir, orientations)
            rank_indices[rank] += 1


def _render(config: dict, rank: int, idx: int, export_dir: str | None,
            orientations: dict) -> None:
    if export_dir:
        ensure_dir(export_dir)
        path = os.path.join(export_dir, f'rank_{rank}_{idx}.png')
    else:
        path = None
    render_configuration(config, orientations, path)
    if path:
        print(f'Saved: {path}')


if __name__ == '__main__':
    main()
