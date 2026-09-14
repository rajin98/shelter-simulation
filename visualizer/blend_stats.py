import json, statistics
import numpy as np

d = json.load(open("../solver/output-blend.json"))
stats = d["stats"]
cfgs = d["configurations"]
print("=== STATS ===")
print(stats)

scores = [c["score"] for c in cfgs]
print("\n=== SCORE RANGE ===")
print("min:", round(min(scores), 3), "  max:", round(max(scores), 3))
print("mean:", round(statistics.mean(scores), 3))
p25 = float(np.percentile(scores, 25))
p75 = float(np.percentile(scores, 75))
print("IQR:", round(p25, 3), "-", round(p75, 3))

print("\n=== TOP 5 ===")
for c in cfgs[:5]:
    print("rank", c["rank"], " score", round(c["score"], 3),
          " bathDists", c["bathroomDists"], " kitDists", c["kitchenDists"])

print("\n=== DISTRIBUTION BUCKETS ===")
for lo, hi in [(0.35, 0.55), (0.40, 0.60), (0.45, 0.65), (0.50, 0.65)]:
    n = sum(1 for s in scores if lo <= s < hi)
    print(f"  [{lo:.2f}, {hi:.2f}): {n}  ({100*n/len(scores):.1f}%)")
