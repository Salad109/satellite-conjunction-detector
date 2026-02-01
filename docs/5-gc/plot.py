import pandas as pd
import matplotlib.pyplot as plt

df_g1 = pd.read_csv('conjunction_benchmark_G1GC.csv')
df_zgc = pd.read_csv('conjunction_benchmark_ZGC.csv')
df_parallel = pd.read_csv('conjunction_benchmark_Parallel.csv')
df_shenandoah = pd.read_csv('conjunction_benchmark_Shenandoah.csv')

datasets = [
    ('G1GC', df_g1, '#2E86AB'),
    ('ZGC', df_zgc, '#D62839'),
    ('Parallel', df_parallel, '#06A77D'),
    ('Shenandoah', df_shenandoah, '#F77F00')
]

timing_columns = ['pair_reduction_s', 'filter_s', 'propagator_s', 'propagate_s', 'check_s', 'grouping_s', 'refine_s']
colors_stack = ['#1f77b4', '#ff7f0e', '#2ca02c', '#06A77D', '#17becf', '#9467bd', '#D62839']
labels_stack = ['Pair Reduction', 'Filter', 'Propagator Build', 'Propagate', 'Check Pairs', 'Grouping', 'Refine']

# Plot 1 - Total time comparison
fig, ax = plt.subplots(figsize=(10, 6))
for name, df, color in datasets:
    ax.plot(df['tolerance_km'], df['total_s'], 'o-', color=color, linewidth=2, markersize=8, label=name)
ax.set_xlabel('Tolerance (km)', fontsize=12)
ax.set_ylabel('Total Time (s)', fontsize=12)
ax.set_title('Total Processing Time vs Tolerance', fontsize=14, fontweight='bold')
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('1_total_time.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 2 - Stacked area per GC
fig, axes = plt.subplots(1, 4, figsize=(24, 6))
for i, (name, df, _) in enumerate(datasets):
    y_stack = [df[col].values for col in timing_columns]
    axes[i].stackplot(df['tolerance_km'], y_stack, labels=labels_stack, colors=colors_stack, alpha=0.8)
    axes[i].set_xlabel('Tolerance (km)', fontsize=12)
    axes[i].set_ylabel('Time (s)', fontsize=12)
    axes[i].set_title(f'{name} Time Breakdown', fontsize=14, fontweight='bold')
    axes[i].legend(fontsize=8, loc='upper left', ncol=2)
    axes[i].grid(True, alpha=0.3)
plt.tight_layout()
plt.savefig('2_time_breakdown_stacked.png', dpi=300, bbox_inches='tight')
plt.close()

# Plot 3 - Conjunctions
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 6))
for name, df, color in datasets:
    ax1.plot(df['tolerance_km'], df['conj'], 'o-', color=color, linewidth=2, markersize=8, label=name)
ax1.set_xlabel('Tolerance (km)', fontsize=12)
ax1.set_ylabel('Conjunctions', fontsize=12)
ax1.set_title('Conjunctions Detected', fontsize=14, fontweight='bold')
ax1.legend(fontsize=11)
ax1.grid(True, alpha=0.3)

gcs = [name for name, _, _ in datasets]
avg_conj = [df['conj'].mean() for _, df, _ in datasets]
gc_colors = [color for _, _, color in datasets]
bars = ax2.bar(range(len(gcs)), avg_conj, color=gc_colors, alpha=0.7)
ax2.set_xticks(range(len(gcs)))
ax2.set_xticklabels(gcs)
ax2.set_ylabel('Average Conjunctions', fontsize=12)
ax2.set_title('Average Conjunctions by GC', fontsize=14, fontweight='bold')
ax2.set_ylim(bottom=min(avg_conj) * 0.95)
ax2.grid(True, alpha=0.3, axis='y')
for bar, val in zip(bars, avg_conj):
    height = bar.get_height()
    ax2.text(bar.get_x() + bar.get_width()/2., height,
             f'{val:.1f}', ha='center', va='bottom', fontsize=10)

plt.tight_layout()
plt.savefig('3_conjunctions.png', dpi=300, bbox_inches='tight')
plt.close()
