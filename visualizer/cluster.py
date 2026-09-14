"""
cluster.py — group solver configurations by placement similarity using two-pass DBSCAN.

Hierarchy:
  Category    = bathroom + kitchen placement (6-D: row, col, ori each)
  Subcategory = shelter arrangement within a category (12-D: row, col, ori × 4)

Orientation is encoded as an integer scaled by ORI_SCALE (default 30), which
exceeds any realistic eps value, so differently-oriented objects can never land
in the same cluster regardless of how close their top-left corners are.

Usage:
    python cluster.py output.json
    python cluster.py output.json --eps-cat 4 --eps-sub 3 --min-samples 2
    python cluster.py output.json --output clusters.json
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np

try:
    from sklearn.cluster import DBSCAN
except ImportError:
    sys.exit("scikit-learn is required: pip install scikit-learn")


_ORI_INT: dict[str, int] = {
    "R0": 0, "R90": 1, "R180": 2, "R270": 3,
    "R0M": 4, "R90M": 5, "R180M": 6, "R270M": 7,
    "Portrait": 0, "Landscape": 1,
}
ORI_SCALE = 30  # any orientation mismatch produces distance ≥ 30, above any realistic eps


def _ori(obj: dict) -> int:
    return _ORI_INT.get(obj.get("orientationId", ""), 0) * ORI_SCALE


def build_anchor_vector(cfg: dict) -> list[int]:
    """6-D: bathroom (row, col, ori) + kitchen (row, col, ori)."""
    bathroom = kitchen = None
    for obj in cfg["objects"]:
        if obj["type"] == "Bathroom":
            bathroom = [obj["topLeftRow"], obj["topLeftCol"], _ori(obj)]
        elif obj["type"] == "Kitchen":
            kitchen = [obj["topLeftRow"], obj["topLeftCol"], _ori(obj)]
    return bathroom + kitchen


def build_shelter_vector(cfg: dict) -> list[int]:
    """12-D: 4 shelters (row, col, ori), sorted row-major for canonical order."""
    shelters = []
    for obj in cfg["objects"]:
        if obj["type"] == "Shelter":
            shelters.append((obj["topLeftRow"], obj["topLeftCol"], _ori(obj)))
    shelters.sort()
    vec = []
    for row, col, ori in shelters:
        vec.extend([row, col, ori])
    return vec


def anchor_label(cfg: dict) -> str:
    """Human-readable category label from bathroom + kitchen."""
    bathroom = kitchen = None
    for obj in cfg["objects"]:
        if obj["type"] == "Bathroom":
            bathroom = obj
        elif obj["type"] == "Kitchen":
            kitchen = obj
    b_ori = bathroom.get("orientationId", "?")
    k_ori = kitchen.get("orientationId", "?")
    return (
        f"bath {b_ori} ({bathroom['topLeftRow']},{bathroom['topLeftCol']})"
        f"  kit {k_ori} ({kitchen['topLeftRow']},{kitchen['topLeftCol']})"
    )


def fmt_ranks(ranks: list, cap: int = 10) -> str:
    unique = sorted(set(ranks))
    s = ", ".join(str(r) for r in unique[:cap])
    if len(unique) > cap:
        s += ", ..."
    return s


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Cluster configurations hierarchically (DBSCAN, Chebyshev)."
    )
    parser.add_argument("output_json", help="Solver output JSON file")
    parser.add_argument(
        "--eps-cat", type=float, default=3.0, metavar="EPS",
        help="Category eps: max per-axis displacement for bathroom+kitchen (default: 3)",
    )
    parser.add_argument(
        "--eps-sub", type=float, default=3.0, metavar="EPS",
        help="Subcategory eps: max per-axis displacement for shelters (default: 3)",
    )
    parser.add_argument(
        "--min-samples", type=int, default=2,
        help="Min configs to form a cluster core at both levels (default: 2)",
    )
    parser.add_argument(
        "--output", metavar="FILE", default=None,
        help="Write cluster assignments to this JSON file",
    )
    args = parser.parse_args()

    path = Path(args.output_json)
    if not path.exists():
        sys.exit(f"Error: file not found: {path}")

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    configs = data.get("configurations", [])
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
    print(
        f"eps_cat={args.eps_cat}  eps_sub={args.eps_sub}"
        f"  min_samples={args.min_samples}  metric=chebyshev\n"
    )

    # Collect all category ids, putting noise last
    cat_ids = sorted(set(cat_labels) - {-1})
    if n_cat_noise:
        cat_ids.append(-1)

    # For JSON output
    assignments: list[dict] = [{}] * len(configs)

    total_subcats = 0
    total_sub_noise = 0

    for cid in cat_ids:
        indices = [i for i, lbl in enumerate(cat_labels) if lbl == cid]
        members = [configs[i] for i in indices]

        # Representative label from first member
        cat_str = f"Cat {cid}" if cid != -1 else "Cat (uncategorised)"
        rep_label = anchor_label(members[0])
        scores = [c.get("score", float("nan")) for c in members]
        score_rng = f"{min(scores):.0f}–{max(scores):.0f}"

        print(f"{cat_str}  [{rep_label}]  size={len(members)}  scores={score_rng}")

        # ── Pass 2: subcategory clustering on shelters within this category ──
        if len(members) < 2:
            # Single config: treat as its own sub-cluster
            print(f"    Sub 0  size=1  score={scores[0]:.0f}  rank={members[0].get('rank','?')}")
            assignments[indices[0]] = {"category": int(cid), "subcategory": 0}
            total_subcats += 1
        else:
            X_shelter = np.array(
                [build_shelter_vector(members[k]) for k in range(len(members))],
                dtype=np.float32,
            )
            sub_labels = DBSCAN(
                eps=args.eps_sub, min_samples=args.min_samples, metric="chebyshev"
            ).fit_predict(X_shelter)

            sub_ids = sorted(set(sub_labels) - {-1})
            n_sub_noise = int((sub_labels == -1).sum())
            total_subcats += len(sub_ids)
            total_sub_noise += n_sub_noise

            for sid in sub_ids:
                sub_members = [members[k] for k, sl in enumerate(sub_labels) if sl == sid]
                sub_scores = [c.get("score", float("nan")) for c in sub_members]
                sub_ranks = [c.get("rank", "?") for c in sub_members]
                print(
                    f"    Sub {sid}  size={len(sub_members)}"
                    f"  scores={min(sub_scores):.0f}–{max(sub_scores):.0f}"
                    f"  ranks: {fmt_ranks(sub_ranks)}"
                )
                for k, sl in enumerate(sub_labels):
                    if sl == sid:
                        assignments[indices[k]] = {"category": int(cid), "subcategory": int(sid)}

            if n_sub_noise:
                noise_members = [members[k] for k, sl in enumerate(sub_labels) if sl == -1]
                noise_scores = [c.get("score", float("nan")) for c in noise_members]
                noise_ranks = [c.get("rank", "?") for c in noise_members]
                print(
                    f"    Sub (noise)  size={len(noise_members)}"
                    f"  scores={min(noise_scores):.0f}–{max(noise_scores):.0f}"
                    f"  ranks: {fmt_ranks(noise_ranks)}"
                )
                for k, sl in enumerate(sub_labels):
                    if sl == -1:
                        assignments[indices[k]] = {"category": int(cid), "subcategory": -1}

        print()

    print(f"Total subcategories: {total_subcats}  (+{total_sub_noise} sub-noise)")

    if args.output:
        out = {
            "params": {
                "eps_cat": args.eps_cat,
                "eps_sub": args.eps_sub,
                "min_samples": args.min_samples,
                "metric": "chebyshev",
            },
            "stats": {
                "n_configs": len(configs),
                "n_categories": n_cats,
                "n_cat_noise": n_cat_noise,
                "n_subcategories": total_subcats,
                "n_sub_noise": total_sub_noise,
            },
            "assignments": [
                {
                    "rank": c.get("rank"),
                    "score": c.get("score"),
                    **asgn,
                }
                for c, asgn in zip(configs, assignments)
            ],
        }
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump(out, f, indent=2)
        print(f"\nAssignments written to {args.output}")


if __name__ == "__main__":
    main()
