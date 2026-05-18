import argparse
import json
import os
import sys

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

    parser.add_argument('--export-dir', metavar='DIR',
                        help='Directory to save PNG files (required with --all)')

    args = parser.parse_args()

    if args.all and not args.export_dir:
        parser.error('--all requires --export-dir')

    data = load_json(args.output_json)
    configs = data.get('configurations', [])

    if args.rank is not None:
        # T12.2 — render single rank
        matches = [c for c in configs if c.get('rank') == args.rank]
        if not matches:
            sys.exit(f'Error: no configuration with rank {args.rank}')
        _render(matches[0], args.rank, args.export_dir)

    elif args.top is not None:
        # T12.3 — render top N
        top = configs[:args.top]
        for cfg in top:
            _render(cfg, cfg.get('rank', 0), args.export_dir)

    elif args.all:
        # T12.4 — render all
        for cfg in configs:
            _render(cfg, cfg.get('rank', 0), args.export_dir)


def _render(config: dict, rank: int, export_dir: str | None) -> None:
    if export_dir:
        ensure_dir(export_dir)
        path = os.path.join(export_dir, f'rank_{rank}.png')
    else:
        path = None
    render_configuration(config, path)
    if path:
        print(f'Saved: {path}')


if __name__ == '__main__':
    main()
