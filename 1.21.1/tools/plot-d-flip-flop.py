"""Render the recorded real-VM trace; never invent or smooth simulation values."""
from pathlib import Path
import csv
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties

root = Path(__file__).resolve().parent.parent
trace = root / "docs/experiments/d-flip-flop/timing.csv"
with trace.open(encoding="utf-8", newline="") as file:
    rows = [{k: (v if k == "phase" else int(v)) for k, v in row.items()} for row in csv.DictReader(file)]
rows = [row for row in rows if 90 <= row["tick"] <= 220]
font_path = Path("C:/Windows/Fonts/msyh.ttc")
font = FontProperties(fname=font_path) if font_path.exists() else FontProperties()
plt.rcParams.update({"svg.fonttype": "none", "axes.unicode_minus": False})
fig, axes = plt.subplots(7, 1, figsize=(13.5, 8.5), sharex=True, gridspec_kw={"height_ratios": [1] * 6 + [1.4]})
fig.patch.set_facecolor("#f5f7fa")
signals = [("D", "D · 元素选择", "#606c85"), ("CLK", "CLK · 写入时钟", "#606c85"),
           ("Q", "Q · 0火 / 1水", "#227da8"), ("Qbar", "!Q · 互补状态", "#86579d"),
           ("fire_gate", "火支路阀门", "#bf642e"), ("water_gate", "水支路阀门", "#227da8")]
ticks = [row["tick"] for row in rows]
for ax, (key, title, color) in zip(axes, signals):
    ax.step(ticks, [row[key] for row in rows], where="post", color=color, linewidth=2)
    ax.set_ylim(-0.2, 1.2)
    ax.set_yticks([0, 1])
    ax.set_ylabel(title, rotation=0, ha="right", va="center", fontproperties=font, labelpad=20)
    ax.grid(axis="x", color="#dbe0e6", linewidth=0.6)
    ax.axvline(157, color="#64748b", linestyle="--", linewidth=1)
    ax.spines[["top", "right"]].set_visible(False)
    ax.tick_params(axis="x", labelbottom=False)
ax = axes[-1]
for key, y, color, marker in [("fire_casts", 1, "#bf642e", "^"), ("water_casts", 0, "#227da8", "v")]:
    xs = [row["tick"] for row in rows if row[key] > 0]
    ax.scatter(xs, [y] * len(xs), color=color, s=65, marker=marker)
ax.set_yticks([0, 1], ["水交付", "火交付"], fontproperties=font)
ax.set_ylim(-0.5, 1.5)
ax.axvline(157, color="#64748b", linestyle="--", linewidth=1)
ax.annotate("旧火尾流 t=157", xy=(157, 1), xytext=(139, 1.35), fontproperties=font, fontsize=10,
            arrowprops={"arrowstyle": "->", "color": "#bf642e"})
ax.annotate("新水首次交付 t=167", xy=(167, 0), xytext=(172, 0.4), fontproperties=font, fontsize=10,
            arrowprops={"arrowstyle": "->", "color": "#227da8"})
ax.grid(axis="x", color="#dbe0e6", linewidth=0.6)
ax.spines[["top", "right"]].set_visible(False)
ax.set_xlabel("法阵逻辑步（本步完成后的观察值；虚线为CLK刺激首次为高的步）", fontproperties=font, labelpad=12)
ax.set_xlim(90, 220)
fig.suptitle("D触发器：D先变为水，Q保持火；CLK上升后保存水", fontproperties=font, fontsize=17, y=0.98)
fig.text(0.5, 0.015, "真实TickCircuit轨迹 · 默认规则 / 黄铜 · 测试台驱动D与CLK · 独立输出支路，不合并元素包", ha="center", fontproperties=font, fontsize=10, color="#475569")
fig.subplots_adjust(left=0.22, right=0.97, top=0.92, bottom=0.11, hspace=0.20)
output = root / "docs/experiments/d-flip-flop"
fig.savefig(output / "timing.png", dpi=150, facecolor=fig.get_facecolor())
fig.savefig(output / "timing.svg", facecolor=fig.get_facecolor())
plt.close(fig)
print(output / "timing.png")
