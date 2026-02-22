import pandas as pd
import matplotlib.pyplot as plt
import glob

csv_files = sorted(glob.glob('conjunction_benchmark_*.csv'))
dfs = {}
avgs = []
for f in csv_files:
    name = f.replace('conjunction_benchmark_', '').replace('.csv', '')
    df = pd.read_csv(f)
    avg_by_tol = df.groupby('tolerance_km').mean(numeric_only=True).reset_index()
    dfs[name] = avg_by_tol
    avgs.append((name, avg_by_tol.mean(numeric_only=True)))

gc_names = [name for name, _ in avgs]
gc_colors = ['#2E86AB', '#D62839', '#06A77D', '#F77F00'][:len(avgs)]

timing_columns = ['propagator_s', 'sgp4_s', 'interp_s', 'check_s', 'grouping_s', 'refine_s', 'probability_s']
stack_colors = ['#2ca02c', '#06A77D', '#e377c2', '#17becf', '#9467bd', '#D62839', '#8c564b']
stack_labels = ['Propagator Build', 'SGP4', 'Interpolation', 'Check Pairs', 'Grouping', 'Refine', 'Probability']

# Plot 1 - Total time: line over tolerance (left) + mean bar (right)
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(18, 6))

for name, color in zip(gc_names, gc_colors):
    df = dfs[name]
    ax1.plot(df['tolerance_km'], df['total_s'], 'o-', color=color, linewidth=2, markersize=6, label=name)
ax1.set_xlabel('Tolerance (km)', fontsize=12)
ax1.set_ylabel('Total Time (s)', fontsize=12)
ax1.set_title('Total Time vs Tolerance', fontsize=14, fontweight='bold')
ax1.legend(fontsize=11)
ax1.grid(True, alpha=0.3)

totals = [avg['total_s'] for _, avg in avgs]
bars = ax2.bar(range(len(gc_names)), totals, color=gc_colors, alpha=0.8)
ax2.set_xticks(range(len(gc_names)))
ax2.set_xticklabels(gc_names)
ax2.set_ylabel('Mean Total Time (s)', fontsize=12)
ax2.set_title('Mean Total Processing Time by GC', fontsize=14, fontweight='bold')
ax2.grid(True, alpha=0.3, axis='y')
for bar, val in zip(bars, totals):
    ax2.text(bar.get_x() + bar.get_width()/2., bar.get_height(),
             f'{val:.1f}s', ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Stacked bar breakdown
fig, ax = plt.subplots(figsize=(10, 6))
x = range(len(gc_names))
bottom = [0] * len(gc_names)
for col, color, label in zip(timing_columns, stack_colors, stack_labels):
    vals = [avg[col] for _, avg in avgs]
    ax.bar(x, vals, bottom=bottom, color=color, label=label, alpha=0.8)
    bottom = [b + v for b, v in zip(bottom, vals)]
ax.set_xticks(x)
ax.set_xticklabels(gc_names)
ax.set_ylabel('Time (s)', fontsize=12)
ax.set_title('Mean Time Breakdown by GC', fontsize=14, fontweight='bold')
ax.legend(fontsize=8, loc='upper left', ncol=2)
ax.grid(True, alpha=0.3, axis='y')
plt.tight_layout()
plt.savefig('2_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Conjunctions
fig, ax = plt.subplots(figsize=(10, 6))
conjs = [avg['conj'] for _, avg in avgs]
bars = ax.bar(range(len(gc_names)), conjs, color=gc_colors, alpha=0.8)
ax.set_xticks(range(len(gc_names)))
ax.set_xticklabels(gc_names)
ax.set_ylabel('Conjunctions', fontsize=12)
ax.set_title('Conjunctions Detected by GC', fontsize=14, fontweight='bold')
ax.set_ylim(bottom=min(conjs) * 0.95)
ax.grid(True, alpha=0.3, axis='y')
for bar, val in zip(bars, conjs):
    ax.text(bar.get_x() + bar.get_width()/2., bar.get_height(),
            f'{val:.0f}', ha='center', va='bottom', fontsize=10)
plt.tight_layout()
plt.savefig('3_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
