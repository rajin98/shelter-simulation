"""
Score distribution comparison across fitness function variants.
Usage: python compare_distributions.py [--output path/to/plot.png]
Reads solver/output-{mean,rms,max,blend}.json relative to this script's parent.
"""

import json
import argparse
from pathlib import Path
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

VARIANTS = [
    ("mean",  "Mean (current)",         "#888888"),
    ("rms",   "RMS",                    "#4C72B0"),
    ("max",   "Minimax",                "#DD8452"),
    ("blend", "Blend α=0.5 (rec.)",     "#55A868"),
]

def load_scores(path: Path) -> list[float]:
    with open(path) as f:
        data = json.load(f)
    return [c["score"] for c in data["configurations"]]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=None, help="Save plot to file instead of showing")
    args = parser.parse_args()

    solver_dir = Path(__file__).parent.parent / "solver"

    fig, axes = plt.subplots(2, 2, figsize=(11, 7), sharey=False)
    fig.suptitle("Score Distribution by Fitness Function Variant", fontsize=13, fontweight="bold")
    axes = axes.flatten()

    all_scores = {}
    for key, label, _ in VARIANTS:
        path = solver_dir / f"output-{key}.json"
        all_scores[key] = load_scores(path)

    global_min = min(min(s) for s in all_scores.values())
    global_max = max(max(s) for s in all_scores.values())

    for ax, (key, label, color) in zip(axes, VARIANTS):
        scores = all_scores[key]
        n = len(scores)
        bins = min(40, n // 10 or 10)

        ax.hist(scores, bins=bins, color=color, edgecolor="white", linewidth=0.4, alpha=0.85)
        ax.set_title(label, fontsize=10, fontweight="bold")
        ax.set_xlim(global_min - 0.02, global_max + 0.02)
        ax.xaxis.set_major_formatter(ticker.FormatStrFormatter("%.2f"))
        ax.tick_params(axis="both", labelsize=8)
        ax.set_xlabel("Score (lower = better)", fontsize=8)
        ax.set_ylabel("Count", fontsize=8)

        mean_val = np.mean(scores)
        ax.axvline(mean_val, color="red", linewidth=1.2, linestyle="--", alpha=0.8)
        ax.text(mean_val + 0.005, ax.get_ylim()[1] * 0.92,
                f"μ={mean_val:.3f}", color="red", fontsize=7.5)

        p25, p75 = np.percentile(scores, [25, 75])
        ax.text(0.97, 0.93, f"n={n}\nIQR [{p25:.2f}, {p75:.2f}]",
                transform=ax.transAxes, fontsize=7, ha="right", va="top",
                bbox=dict(boxstyle="round,pad=0.3", fc="white", ec="#cccccc", alpha=0.8))

    fig.tight_layout(rect=[0, 0, 1, 0.95])

    if args.output:
        fig.savefig(args.output, dpi=150)
        print(f"Saved to {args.output}")
    else:
        plt.show()

if __name__ == "__main__":
    main()
