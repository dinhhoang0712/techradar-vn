import numpy as np
import pandas as pd
import json
import umap
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from pathlib import Path

TAG = "2026-05-10"
BASE = Path(__file__).parent

X = np.load(BASE / f"data/features/{TAG}/X.npy")
tech_ids = pd.read_parquet(BASE / f"data/features/{TAG}/tech_ids.parquet")
labels_df = pd.read_parquet(BASE / f"data/models/{TAG}/best_labels.parquet")
with open(BASE / f"data/labels/{TAG}/cluster_labels.json") as f:
    cluster_meta = json.load(f)

df = tech_ids.merge(labels_df[["tech_id", "cluster_id"]], on="tech_id", how="left")
df["cluster_id"] = df["cluster_id"].fillna(-1).astype(int)

print("Đang chạy UMAP 2D...")
reducer = umap.UMAP(n_components=2, random_state=42, n_jobs=1)
X_2d = reducer.fit_transform(X)

df["cluster"] = df["cluster_id"]
df["x"] = X_2d[:, 0]
df["y"] = X_2d[:, 1]

cluster_ids = sorted([int(k) for k in cluster_meta.keys()])
n_clusters = len(cluster_ids)

cmap = plt.cm.get_cmap("tab20", n_clusters)
colors = {cid: cmap(i) for i, cid in enumerate(cluster_ids)}

fig, ax = plt.subplots(figsize=(16, 12))
fig.patch.set_facecolor("#0f1117")
ax.set_facecolor("#0f1117")

noise = df[df["cluster"] == -1]
ax.scatter(noise["x"], noise["y"], c="#444444", s=25, alpha=0.4, marker="x", label="Noise", zorder=1)

for cid in cluster_ids:
    group = df[df["cluster"] == cid]
    meta = cluster_meta[str(cid)]
    label_short = meta["label_en"][:30]
    color = colors[cid]
    ax.scatter(group["x"], group["y"], c=[color], s=60, alpha=0.85,
               edgecolors="white", linewidths=0.3, zorder=2)
    cx, cy = group["x"].mean(), group["y"].mean()
    ax.annotate(f"C{cid}", (cx, cy), fontsize=7, color="white",
                ha="center", va="center", fontweight="bold",
                bbox=dict(boxstyle="round,pad=0.2", fc=color, alpha=0.7, ec="none"))

name_col = "name" if "name" in df.columns else "tech_id"
for _, row in df.iterrows():
    ax.annotate(str(row[name_col])[:15], (row["x"], row["y"]),
                fontsize=4, color="#aaaaaa", alpha=0.6,
                xytext=(3, 3), textcoords="offset points")

patches = []
for cid in cluster_ids:
    meta = cluster_meta[str(cid)]
    size = meta["member_count"]
    label_text = f"C{cid}: {meta['label_en'][:28]} (n={size})"
    patches.append(mpatches.Patch(color=colors[cid], label=label_text))
patches.append(mpatches.Patch(color="#444444", label=f"Noise (n={len(noise)})"))

legend = ax.legend(handles=patches, loc="upper left", fontsize=7,
                   framealpha=0.3, facecolor="#1a1a2e", edgecolor="#555",
                   labelcolor="white", ncol=1)

noise_pct = len(noise) / len(df) * 100
ax.set_title(f"Tech Cluster Visualization — UMAP 2D | {TAG}\n"
             f"{n_clusters} clusters · {len(df)} techs · Noise: {len(noise)} ({noise_pct:.1f}%)",
             color="white", fontsize=13, pad=15)
ax.tick_params(colors="#555")
ax.spines[:].set_color("#333")

out_path = BASE / f"data/labels/{TAG}/cluster_viz.png"
plt.tight_layout()
plt.savefig(out_path, dpi=150, bbox_inches="tight", facecolor="#0f1117")
print(f"Saved → {out_path}")
plt.show()
