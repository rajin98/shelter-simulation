"""
cluster_visualize.py — cluster configurations by placement, then render each
into a per-category/subcategory folder.

Output layout:
    <out-dir>/
        cat_00/  [bath Portrait (2,5)  kit R0 (10,3)]
            sub_0/  rank_1.png  rank_3.png  ...
            sub_1/  rank_7.png  ...
            sub_noise/  ...
        cat_01/
            ...
        cat_noise/
            ...

Usage:
    python cluster_visualize.py output.json
    python cluster_visualize.py output.json --eps-cat 6 --eps-sub 3 --out-dir ./grouped
"""

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

try:
    from sklearn.cluster import DBSCAN
except ImportError:
    sys.exit("scikit-learn is required: pip install scikit-learn")

from cluster import build_anchor_vector, build_shelter_vector, anchor_label
from renderer import render_configuration


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Cluster + visualize configurations grouped into category/subcategory folders."
    )
    parser.add_argument("output_json", help="Solver output JSON file")
    parser.add_argument("--eps-cat", type=float, default=3.0, metavar="EPS",
                        help="Category eps: bathroom+kitchen displacement (default: 3)")
    parser.add_argument("--eps-sub", type=float, default=3.0, metavar="EPS",
                        help="Subcategory eps: shelter displacement (default: 3)")
    parser.add_argument("--min-samples", type=int, default=2,
                        help="Min configs to form a cluster core at both levels (default: 2)")
    parser.add_argument("--out-dir", metavar="DIR", default="clustered",
                        help="Root output directory (default: clustered/)")
    args = parser.parse_args()

    path = Path(args.output_json)
    if not path.exists():
        sys.exit(f"Error: file not found: {path}")

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    configs = data.get("configurations", [])
    orientations = data.get("orientations", {})
    if not configs:
        sys.exit("No configurations found in JSON.")

    # ── Pass 1: category clustering on bathroom + kitchen ──────────────────
    X_anchor = np.array([build_anchor_vector(c) for c in configs], dtype=np.float32)
    cat_labels = DBSCAN(
        eps=args.eps_cat, min_samples=args.min_samples, metric="chebyshev"
    ).fit_predict(X_anchor)

    n_cats = len(set(cat_labels)) - (1 if -1 in cat_labels else 0)
    n_cat_noise = int((cat_labels == -1).sum())
    print(f"Configurations  : {len(configs)}")
    print(f"Categories      : {n_cats}  (+{n_cat_noise} uncategorised)")
    print(f"eps_cat={args.eps_cat}  eps_sub={args.eps_sub}  min_samples={args.min_samples}\n")

    root = Path(args.out_dir)
    cat_ids = sorted(set(cat_labels) - {-1})
    if n_cat_noise:
        cat_ids.append(-1)

    for cid in cat_ids:
        indices = [i for i, lbl in enumerate(cat_labels) if lbl == cid]
        members = [configs[i] for i in indices]
        cat_dir_name = "cat_noise" if cid == -1 else f"cat_{cid:02d}"
        cat_folder = root / cat_dir_name

        rep = anchor_label(members[0])
        print(f"{cat_dir_name}/  [{rep}]  size={len(members)}")

        # ── Pass 2: subcategory clustering on shelters ──────────────────────
        if len(members) < 2:
            sub_labels = np.array([0])
        else:
            X_shelter = np.array(
                [build_shelter_vector(c) for c in members], dtype=np.float32
            )
            sub_labels = DBSCAN(
                eps=args.eps_sub, min_samples=args.min_samples, metric="chebyshev"
            ).fit_predict(X_shelter)

        sub_ids = sorted(set(sub_labels) - {-1})
        if any(sl == -1 for sl in sub_labels):
            sub_ids.append(-1)

        for sid in sub_ids:
            sub_dir_name = "sub_noise" if sid == -1 else f"sub_{sid}"
            sub_folder = cat_folder / sub_dir_name
            sub_folder.mkdir(parents=True, exist_ok=True)

            sub_members = [(indices[k], members[k]) for k, sl in enumerate(sub_labels) if sl == sid]
            print(f"  {sub_dir_name}/  size={len(sub_members)}")

            rank_count: dict[int, int] = defaultdict(int)
            for _, cfg in sub_members:
                rank = cfg.get("rank", 0)
                idx = rank_count[rank]
                rank_count[rank] += 1
                suffix = f"_{idx}" if idx > 0 else ""
                out_path = sub_folder / f"rank_{rank}{suffix}.png"
                render_configuration(cfg, orientations, str(out_path))

            print(f"    -> saved to {sub_folder}/")

        print()

    print(f"Done. All images written under {root}/")


if __name__ == "__main__":
    main()
